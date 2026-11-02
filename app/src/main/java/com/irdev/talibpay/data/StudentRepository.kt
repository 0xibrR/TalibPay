package com.irdev.talibpay.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.StudentLedger
import com.irdev.talibpay.domain.methodFor
import com.irdev.talibpay.model.Charge
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

/**
 * الطالب مع دفتر حسابه (المستحقات ودفعات كل منها).
 * مبالغ [student] محسوبة من الدفتر عند القراءة، فتبقى صحيحة حتى للبيانات القديمة.
 */
data class StudentWithPayments(val student: Student, val ledger: StudentLedger) {
    val dues: List<DueSummary> get() = ledger.dues
    val payments: List<Payment> get() = ledger.payments
    val lastPayment: Payment? get() = payments.lastOrNull()
}

data class StudentInput(
    val name: String,
    val birthYear: String,
    val registrationDate: String,
    val phoneNumber: String,
    val notes: String
)

data class ChargeInput(
    val title: String,
    val amount: Double,
    val dueDate: String,
    val note: String
)

data class PaymentInput(
    val chargeId: String,
    val cash: Double,
    val bank: Double,
    val paymentDate: String,
    val note: String
)

/**
 * كل الوصول لبيانات الطلاب في Firebase يمر من هنا.
 * المسار: TalibPay/{uid}/students/{studentId}/{charges|payments}/{id}
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

    // ---------------- الطلاب ----------------

    suspend fun addStudent(input: StudentInput) {
        val ref = studentsRef().push()
        val id = ref.key ?: error("push() returned no key")
        val now = clock()
        val student = Student(
            id = id,
            name = input.name,
            birthYear = input.birthYear,
            registrationDate = input.registrationDate,
            phoneNumber = input.phoneNumber,
            notes = input.notes,
            addedAt = now,
            updatedAt = now
        )
        ref.setValue(student).await()
    }

    /** يحدّث بيانات الطالب الأساسية فقط، دون المساس بالمستحقات أو الدفعات. */
    suspend fun updateStudent(studentId: String, input: StudentInput) {
        studentRef(studentId).updateChildren(
            mapOf(
                "name" to input.name,
                "birthYear" to input.birthYear,
                "registrationDate" to input.registrationDate,
                "phoneNumber" to input.phoneNumber,
                "notes" to input.notes,
                "updatedAt" to clock()
            )
        ).await()
    }

    suspend fun deleteStudent(studentId: String) {
        studentRef(studentId).removeValue().await()
    }

    /** يستعيد الطالب كما كان، مع مستحقاته ودفعاته (للتراجع عن الحذف). */
    suspend fun restoreStudent(data: StudentWithPayments) {
        val ref = studentRef(data.student.id)
        ref.setValue(data.student).await()
        val charges = data.dues.map { it.charge }
        if (charges.isNotEmpty()) {
            ref.child(CHARGES).setValue(charges.associateBy { it.id }).await()
        }
        if (data.payments.isNotEmpty()) {
            ref.child(PAYMENTS).setValue(data.payments.associateBy { it.id }).await()
        }
    }

    suspend fun deleteAllStudents() {
        studentsRef().removeValue().await()
    }

    // ---------------- المستحقات ----------------

    suspend fun addCharge(studentId: String, input: ChargeInput) {
        val ref = studentRef(studentId)
        val chargeRef = ref.child(CHARGES).push()
        val id = chargeRef.key ?: error("push() returned no key")
        chargeRef.setValue(
            Charge(
                id = id,
                title = input.title,
                amount = input.amount,
                dueDate = input.dueDate,
                note = input.note,
                createdAt = clock()
            )
        ).await()
        recalculate(ref)
    }

    suspend fun updateCharge(studentId: String, chargeId: String, input: ChargeInput) {
        val ref = studentRef(studentId)
        // updateChildren يُنشئ المستحق إن كان مشتقًا من بيانات قديمة ولم يُحفظ بعد
        ref.child(CHARGES).child(chargeId).updateChildren(
            mapOf(
                "id" to chargeId,
                "title" to input.title,
                "amount" to input.amount,
                "dueDate" to input.dueDate,
                "note" to input.note
            )
        ).await()
        recalculate(ref)
    }

    /** يُستدعى فقط لمستحق بلا دفعات (يتحقق منه الـ ViewModel). */
    suspend fun deleteCharge(studentId: String, chargeId: String) {
        val ref = studentRef(studentId)
        ref.child(CHARGES).child(chargeId).removeValue().await()
        recalculate(ref)
    }

    // ---------------- الدفعات ----------------

    suspend fun addPayment(studentId: String, input: PaymentInput) {
        val ref = studentRef(studentId)
        val paymentRef = ref.child(PAYMENTS).push()
        val id = paymentRef.key ?: error("push() returned no key")
        paymentRef.setValue(input.toPayment(id)).await()
        recalculate(ref)
    }

    suspend fun updatePayment(studentId: String, paymentId: String, input: PaymentInput) {
        val ref = studentRef(studentId)
        ref.child(PAYMENTS).child(paymentId).setValue(input.toPayment(paymentId)).await()
        recalculate(ref)
    }

    suspend fun deletePayment(studentId: String, paymentId: String) {
        val ref = studentRef(studentId)
        ref.child(PAYMENTS).child(paymentId).removeValue().await()
        recalculate(ref)
    }

    /**
     * يعيد بناء دفتر الطالب داخل transaction ويكتب القيم المحسوبة:
     * - يحفظ مستحق "الرسوم" القديم إن كان مشتقًا من دفعات بلا مستحق (ترحيل تلقائي).
     * - يربط كل دفعة بمستحقها ويكتب المتبقي بعدها وطريقة الدفع.
     * - يكتب مجاميع الطالب.
     */
    private suspend fun recalculate(studentRef: DatabaseReference) {
        val now = clock()
        studentRef.awaitTransaction { data ->
            val chargesNode = data.child(CHARGES)
            val paymentsNode = data.child(PAYMENTS)
            val charges = chargesNode.children.mapNotNull { it.getValue(Charge::class.java) }
            val payments = paymentsNode.children.mapNotNull { it.getValue(Payment::class.java) }
            val ledger = StudentLedger.build(charges, payments)

            for (due in ledger.dues) {
                if (due.isLegacy && !chargesNode.hasChild(due.charge.id)) {
                    chargesNode.child(due.charge.id).value = due.charge
                }
                for (payment in due.payments) {
                    val node = paymentsNode.child(payment.id)
                    node.child("chargeId").value = payment.chargeId
                    node.child("paid").value = payment.paid
                    node.child("total").value = payment.total
                    node.child("remaining").value = payment.remaining
                    node.child("paymentMethod").value = payment.paymentMethod
                }
            }

            val totals = ledger.totals
            data.child("paidCash").value = totals.paidCash
            data.child("paidBank").value = totals.paidBank
            data.child("paid").value = totals.paid
            data.child("total").value = totals.total
            data.child("remaining").value = totals.remaining
            data.child("updatedAt").value = now
        }
    }

    // القيم المحسوبة هنا مبدئية؛ القيم النهائية تُكتب في recalculate
    private fun PaymentInput.toPayment(id: String): Payment = Payment(
        id = id,
        chargeId = chargeId,
        cash = cash,
        bank = bank,
        paid = Money.sum(listOf(cash, bank)),
        paymentMethod = methodFor(cash, bank).code,
        paymentDate = paymentDate,
        note = note
    )

    private fun DataSnapshot.toStudentWithPayments(): StudentWithPayments? {
        if (!exists()) return null
        val stored = getValue(Student::class.java) ?: return null
        val ledger = StudentLedger.build(
            child(CHARGES).children.mapNotNull { it.getValue(Charge::class.java) },
            child(PAYMENTS).children.mapNotNull { it.getValue(Payment::class.java) }
        )
        val totals = ledger.totals
        val student = stored.copy(
            paidCash = totals.paidCash,
            paidBank = totals.paidBank,
            paid = totals.paid,
            total = totals.total,
            remaining = totals.remaining
        )
        return StudentWithPayments(student, ledger)
    }

    private companion object {
        const val ROOT = "TalibPay"
        const val STUDENTS = "students"
        const val CHARGES = "charges"
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
