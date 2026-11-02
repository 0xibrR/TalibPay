package com.irdev.talibpay.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.irdev.talibpay.domain.PaymentMethod
import com.irdev.talibpay.domain.StudentTotals
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.model.Student
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NotSignedInException : IllegalStateException("User is not signed in")

/** الطالب مع دفعاته مرتبة من الأقدم للأحدث (مفاتيح push زمنية). */
data class StudentWithPayments(val student: Student, val payments: List<Payment>) {
    val lastPayment: Payment? get() = payments.lastOrNull()
}

data class StudentInput(
    val name: String,
    val birthYear: String,
    val phoneNumber: String,
    val notes: String
)

data class PaymentInput(
    val cash: Double,
    val bank: Double,
    val total: Double,
    val method: PaymentMethod,
    val paymentDate: String,
    val note: String
)

/**
 * كل الوصول لبيانات الطلاب والدفعات في Firebase يمر من هنا.
 * المسار: TalibPay/{uid}/students/{studentId}/payments/{paymentId}
 */
class StudentRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val clock: () -> Long = System::currentTimeMillis
) {

    private fun studentsRef(): DatabaseReference {
        val uid = auth.currentUser?.uid ?: throw NotSignedInException()
        return database.getReference(ROOT).child(uid).child(STUDENTS)
    }

    private fun studentRef(studentId: String) = studentsRef().child(studentId)

    fun observeStudents(): Flow<List<StudentWithPayments>> = callbackFlow {
        val ref = studentsRef()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { it.toStudentWithPayments() })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.keepSynced(true)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** يُصدر null عند حذف الطالب. */
    fun observeStudent(studentId: String): Flow<StudentWithPayments?> = callbackFlow {
        val ref = studentRef(studentId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.toStudentWithPayments())
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun getStudent(studentId: String): StudentWithPayments? =
        studentRef(studentId).get().await().toStudentWithPayments()

    suspend fun addStudent(input: StudentInput) {
        val ref = studentsRef().push()
        val id = ref.key ?: error("push() returned no key")
        val now = clock()
        val student = Student(
            id = id,
            name = input.name,
            birthYear = input.birthYear,
            phoneNumber = input.phoneNumber,
            notes = input.notes,
            addedAt = now,
            updatedAt = now
        )
        ref.setValue(student).await()
    }

    /** يحدّث بيانات الطالب الأساسية فقط، دون المساس بالمبالغ أو الدفعات. */
    suspend fun updateStudent(studentId: String, input: StudentInput) {
        studentRef(studentId).updateChildren(
            mapOf(
                "name" to input.name,
                "birthYear" to input.birthYear,
                "phoneNumber" to input.phoneNumber,
                "notes" to input.notes,
                "updatedAt" to clock()
            )
        ).await()
    }

    suspend fun deleteStudent(studentId: String) {
        studentRef(studentId).removeValue().await()
    }

    /** يستعيد الطالب كما كان، مع كل دفعاته (للتراجع عن الحذف). */
    suspend fun restoreStudent(data: StudentWithPayments) {
        val ref = studentRef(data.student.id)
        ref.setValue(data.student).await()
        if (data.payments.isNotEmpty()) {
            ref.child(PAYMENTS).setValue(data.payments.associateBy { it.id }).await()
        }
    }

    suspend fun deleteAllStudents() {
        studentsRef().removeValue().await()
    }

    suspend fun addPayment(studentId: String, input: PaymentInput) {
        val ref = studentRef(studentId)
        val paymentRef = ref.child(PAYMENTS).push()
        val id = paymentRef.key ?: error("push() returned no key")
        paymentRef.setValue(input.toPayment(id)).await()
        recalculateTotals(ref)
    }

    suspend fun updatePayment(studentId: String, paymentId: String, input: PaymentInput) {
        val ref = studentRef(studentId)
        ref.child(PAYMENTS).child(paymentId).setValue(input.toPayment(paymentId)).await()
        recalculateTotals(ref)
    }

    suspend fun deletePayment(studentId: String, paymentId: String) {
        val ref = studentRef(studentId)
        ref.child(PAYMENTS).child(paymentId).removeValue().await()
        recalculateTotals(ref)
    }

    /**
     * يعيد حساب مجاميع الطالب من دفعاته داخل transaction،
     * فتبقى المجاميع مطابقة للدفعات حتى مع التعديل من أكثر من جهاز.
     */
    private suspend fun recalculateTotals(studentRef: DatabaseReference) {
        val now = clock()
        studentRef.awaitTransaction { data ->
            val payments = data.child(PAYMENTS).children.mapNotNull {
                it.getValue(Payment::class.java)
            }
            val totals = StudentTotals.from(payments)
            data.child("paidCash").value = totals.paidCash
            data.child("paidBank").value = totals.paidBank
            data.child("paid").value = totals.paid
            data.child("total").value = totals.total
            data.child("remaining").value = totals.remaining
            data.child("updatedAt").value = now
        }
    }

    private fun PaymentInput.toPayment(id: String): Payment {
        val totals = StudentTotals.from(listOf(Payment(cash = cash, bank = bank, total = total)))
        return Payment(
            id = id,
            cash = cash,
            bank = bank,
            paid = totals.paid,
            total = total,
            remaining = totals.remaining,
            paymentMethod = method.code,
            paymentDate = paymentDate,
            note = note
        )
    }

    private fun DataSnapshot.toStudentWithPayments(): StudentWithPayments? {
        if (!exists()) return null
        val student = getValue(Student::class.java) ?: return null
        val payments = child(PAYMENTS).children.mapNotNull { it.getValue(Payment::class.java) }
        return StudentWithPayments(student, payments)
    }

    private companion object {
        const val ROOT = "TalibPay"
        const val STUDENTS = "students"
        const val PAYMENTS = "payments"
    }
}

/**
 * transaction كـ suspend. إذا لم تكن العقدة موجودة محليًا نتركها كما هي،
 * وسيعيد Firebase المحاولة بالقيمة الحقيقية من الخادم إن وُجدت.
 */
private suspend fun DatabaseReference.awaitTransaction(update: (MutableData) -> Unit) =
    suspendCancellableCoroutine { cont ->
        runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (currentData.value != null) update(currentData)
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                when {
                    error != null -> cont.resumeWithException(error.toException())
                    !committed -> cont.resumeWithException(IllegalStateException("Transaction aborted"))
                    else -> cont.resume(Unit)
                }
            }
        })
    }
