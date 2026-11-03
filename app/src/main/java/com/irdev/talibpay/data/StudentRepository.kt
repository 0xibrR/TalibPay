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
import com.irdev.talibpay.domain.SimpleDate
import com.irdev.talibpay.domain.billingStartFor
import com.irdev.talibpay.domain.billingStartOnUnarchive
import com.irdev.talibpay.domain.isEnablingMonthlyFee
import com.irdev.talibpay.domain.periodsDue
import com.irdev.talibpay.domain.todaySimpleDate
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.StudentLedger
import com.irdev.talibpay.domain.Wallet
import com.irdev.talibpay.domain.WalletEntryType
import com.irdev.talibpay.domain.methodFor
import com.irdev.talibpay.model.Charge
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.model.Student
import com.irdev.talibpay.model.WalletEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NotSignedInException : IllegalStateException("User is not signed in")

/** الاسترداد أكبر من رصيد المحفظة الحالي (ربما تغيّر من جهاز آخر). */
class InsufficientWalletBalanceException : IllegalStateException("Wallet balance is not enough")

/** دفعة أضافت رصيدًا للمحفظة: لا تُعدَّل ولا تُحذف حتى لا يختل رصيد المحفظة. */
class PaymentLockedByWalletException : IllegalStateException("Payment credited the wallet")

/**
 * الطالب مع دفتر حسابه (المستحقات ودفعات كل منها، وحركات المحفظة).
 * مبالغ [student] محسوبة من الدفتر عند القراءة، فتبقى صحيحة حتى للبيانات القديمة.
 */
data class StudentWithPayments(val student: Student, val ledger: StudentLedger) {
    val dues: List<DueSummary> get() = ledger.dues
    val payments: List<Payment> get() = ledger.payments
    val lastPayment: Payment? get() = payments.lastOrNull()
    val walletEntries: List<WalletEntry> get() = ledger.walletEntries
}

data class StudentInput(
    val name: String,
    val birthYear: String,
    val registrationDate: String,
    val monthlyFee: Double,
    val phoneNumber: String,
    val phoneCountryCode: String,
    val notes: String,
    /** عند إدخال الرسوم الشهرية لأول مرة: احتساب الأشهر السابقة منذ تاريخ التسجيل. */
    val backdateMonthlyFees: Boolean = false
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
            monthlyFee = input.monthlyFee,
            billingStart = billingStartFor(null, input.monthlyFee, input.registrationDate, today()),
            phoneNumber = input.phoneNumber,
            phoneCountryCode = input.phoneCountryCode,
            notes = input.notes,
            addedAt = now,
            updatedAt = now
        )
        ref.setValue(student).await()
        if (input.monthlyFee > 0) syncLedger(ref)
    }

    /** يحدّث بيانات الطالب الأساسية فقط، دون المساس بالمستحقات أو الدفعات. */
    suspend fun updateStudent(studentId: String, input: StudentInput) {
        val ref = studentRef(studentId)
        val current = ref.get().await().getValue(Student::class.java)
        val enabling = isEnablingMonthlyFee(current, input.monthlyFee)
        val backdate = enabling && input.backdateMonthlyFees
        val billingStart = billingStartFor(
            current, input.monthlyFee, input.registrationDate, today(), backdate
        )
        val updates = mutableMapOf<String, Any>(
            "name" to input.name,
            "birthYear" to input.birthYear,
            "registrationDate" to input.registrationDate,
            "monthlyFee" to input.monthlyFee,
            "billingStart" to billingStart,
            "phoneNumber" to input.phoneNumber,
            "phoneCountryCode" to input.phoneCountryCode,
            "notes" to input.notes,
            "updatedAt" to clock()
        )
        // احتساب الرسوم السابقة: نعيد الفوترة من تاريخ التسجيل. الأشهر الموجودة لا تتكرر
        // لأن لكل شهر معرّفًا ثابتًا ويُتخطى إن وُجد (انظر generateDueCharges).
        if (backdate) updates["lastBilledPeriod"] = ""
        ref.updateChildren(updates).await()
        if (input.monthlyFee > 0) syncLedger(ref)
    }

    /**
     * ينشئ الرسوم الشهرية التي حان موعدها لكل الطلاب (عند فتح التطبيق وفي المهمة اليومية).
     * آمن للتكرار: كل شهر له معرّف ثابت، والإنشاء داخل transaction.
     */
    suspend fun generateMonthlyDues() {
        val today = today()
        val students = studentsRef().get().await().children
        for (snapshot in students) {
            val student = snapshot.getValue(Student::class.java) ?: continue
            // فحص سريع قبل الـ transaction لتجنّب الكتابة لطلاب لا شيء مستحق عليهم الآن
            if (student.periodsDue(today).isNotEmpty()) syncLedger(snapshot.ref)
        }
    }

    private fun today(): SimpleDate = todaySimpleDate(clock())

    /** يخفي الطالب من القائمة ويوقف رسومه الشهرية، مع إبقاء كل سجلاته كما هي. */
    suspend fun archiveStudent(studentId: String) {
        val now = clock()
        studentRef(studentId).updateChildren(
            mapOf("archived" to true, "archivedAt" to now, "updatedAt" to now)
        ).await()
    }

    /**
     * يعيد الطالب للقائمة، وتُستأنف رسومه الشهرية من اليوم (لا تُحتسب أشهر الأرشيف).
     * كله في transaction واحدة: إلغاء الأرشفة ثم إنشاء رسوم اليوم إن كان موعدها اليوم.
     */
    suspend fun unarchiveStudent(studentId: String) {
        val today = today()
        syncLedger(studentRef(studentId)) { data ->
            val student = data.getValue(Student::class.java) ?: return@syncLedger emptyList()
            data.child("archived").value = false
            data.child("archivedAt").value = 0
            data.child("billingStart").value = billingStartOnUnarchive(student, today)
            emptyList()
        }
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
        if (data.walletEntries.isNotEmpty()) {
            ref.child(WALLET).setValue(data.walletEntries.associateBy { it.id }).await()
        }
    }

    suspend fun deleteAllStudents() {
        studentsRef().removeValue().await()
    }

    // ---------------- المستحقات ----------------

    /** مستحق جديد يُسدَّد تلقائيًا من رصيد المحفظة إن وُجد (في نفس الـ transaction). */
    suspend fun addCharge(studentId: String, input: ChargeInput) {
        val ref = studentRef(studentId)
        val id = ref.child(CHARGES).push().key ?: error("push() returned no key")
        val charge = Charge(
            id = id,
            title = input.title,
            amount = input.amount,
            dueDate = input.dueDate,
            note = input.note,
            createdAt = clock()
        )
        syncLedger(ref) { data ->
            val chargesNode = data.child(CHARGES)
            if (chargesNode.hasChild(id)) emptyList()
            else {
                chargesNode.child(id).value = charge
                listOf(id)
            }
        }
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
        syncLedger(ref)
    }

    /** يُستدعى فقط لمستحق بلا دفعات (يتحقق منه الـ ViewModel). */
    suspend fun deleteCharge(studentId: String, chargeId: String) {
        val ref = studentRef(studentId)
        ref.child(CHARGES).child(chargeId).removeValue().await()
        syncLedger(ref)
    }

    // ---------------- الدفعات ----------------

    /**
     * تسجيل دفعة: ما يزيد عن المتبقي على مستحقها يُضاف للمحفظة.
     * الدفعة وحركة الإيداع تُكتبان معًا في transaction واحدة، والمتبقي يُحسب من أحدث البيانات،
     * فلا يتكرر الإيداع ولا يُحسب خطأً حتى لو سُجلت دفعتان لنفس المستحق من جهازين.
     */
    suspend fun addPayment(studentId: String, input: PaymentInput) {
        val ref = studentRef(studentId)
        val id = ref.child(PAYMENTS).push().key ?: error("push() returned no key")
        val now = clock()
        syncLedger(ref) { data ->
            val paymentsNode = data.child(PAYMENTS)
            if (!paymentsNode.hasChild(id)) {
                val dueRemaining = data.ledger().due(input.chargeId)?.remaining ?: 0.0
                val amount = Money.sum(listOf(input.cash, input.bank))
                val allocation = Wallet.allocate(amount, dueRemaining)
                paymentsNode.child(id).value = input.toPayment(id).copy(credited = allocation.credited)
                if (allocation.credited > 0) {
                    val depositId = Wallet.depositId(id)
                    data.child(WALLET).child(depositId).value = WalletEntry(
                        id = depositId,
                        type = WalletEntryType.DEPOSIT.name,
                        amount = allocation.credited,
                        chargeId = input.chargeId,
                        paymentId = id,
                        createdAt = now
                    )
                }
            }
            emptyList()
        }
    }

    /** تعديل دفعة لم تُضف للمحفظة (المبلغ لا يتجاوز المتبقي، يتحقق منه الـ ViewModel). */
    suspend fun updatePayment(studentId: String, paymentId: String, input: PaymentInput) {
        val ref = studentRef(studentId)
        var locked = false
        syncLedger(ref) { data ->
            val node = data.child(PAYMENTS).child(paymentId)
            locked = (node.child("credited").getValue(Double::class.java) ?: 0.0) > 0
            if (!locked) node.value = input.toPayment(paymentId)
            emptyList()
        }
        if (locked) throw PaymentLockedByWalletException()
    }

    suspend fun deletePayment(studentId: String, paymentId: String) {
        val ref = studentRef(studentId)
        var locked = false
        syncLedger(ref) { data ->
            val node = data.child(PAYMENTS).child(paymentId)
            locked = (node.child("credited").getValue(Double::class.java) ?: 0.0) > 0
            if (!locked) node.value = null
            emptyList()
        }
        if (locked) throw PaymentLockedByWalletException()
    }

    // ---------------- المحفظة ----------------

    /** استرداد مبلغ من المحفظة للطالب؛ يُرفض إن تجاوز الرصيد الحالي وقت التنفيذ. */
    suspend fun refundFromWallet(studentId: String, amount: Double, note: String) {
        val ref = studentRef(studentId)
        val id = "ref-" + (ref.child(WALLET).push().key ?: error("push() returned no key"))
        val now = clock()
        var refunded = false
        syncLedger(ref) { data ->
            val walletNode = data.child(WALLET)
            refunded = walletNode.hasChild(id)
            if (!refunded && amount <= data.ledger().walletBalance) {
                walletNode.child(id).value = WalletEntry(
                    id = id,
                    type = WalletEntryType.REFUND.name,
                    amount = amount,
                    note = note,
                    createdAt = now
                )
                refunded = true
            }
            emptyList()
        }
        if (!refunded) throw InsufficientWalletBalanceException()
    }

    /**
     * يعيد بناء دفتر الطالب داخل transaction ويكتب القيم المحسوبة:
     * - يحفظ مستحق "الرسوم" القديم إن كان مشتقًا من دفعات بلا مستحق (ترحيل تلقائي).
     * - يربط كل دفعة بمستحقها ويكتب المتبقي بعدها وطريقة الدفع.
     * - يكتب مجاميع الطالب.
     */
    /**
     * @param mutate تعديل يُطبَّق داخل نفس الـ transaction قبل إعادة الحساب
     * (إضافة دفعة أو مستحق...)، ويعيد معرّفات المستحقات الجديدة التي أنشأها.
     */
    private suspend fun syncLedger(
        studentRef: DatabaseReference,
        mutate: (MutableData) -> List<String> = { emptyList() }
    ) {
        val now = clock()
        val today = today()
        studentRef.awaitTransaction { data ->
            val newCharges = mutate(data) + data.generateDueCharges(today, now)
            data.applyWalletToNewCharges(newCharges, now)

            val chargesNode = data.child(CHARGES)
            val paymentsNode = data.child(PAYMENTS)
            val ledger = data.ledger()

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
            data.child("walletBalance").value = totals.walletBalance
            data.child("updatedAt").value = now
        }
    }

    /** دفتر الطالب من البيانات الحالية داخل الـ transaction. */
    private fun MutableData.ledger(): StudentLedger = StudentLedger.build(
        child(CHARGES).children.mapNotNull { it.getValue(Charge::class.java) },
        child(PAYMENTS).children.mapNotNull { it.getValue(Payment::class.java) },
        child(WALLET).children.mapNotNull { it.getValue(WalletEntry::class.java) }
    )

    /**
     * يسدد المستحقات الجديدة من رصيد المحفظة، الأقدم أولًا، كليًا أو جزئيًا.
     * حركة الخصم معرّفها ثابت لكل مستحق (use-{id})، فلا يُخصم للمستحق نفسه مرتين.
     * الخصم ليس دفعة: لا يظهر في النقدي أو البنكي.
     */
    private fun MutableData.applyWalletToNewCharges(chargeIds: List<String>, now: Long) {
        if (chargeIds.isEmpty()) return
        val walletNode = child(WALLET)
        chargeIds.forEachIndexed { index, chargeId ->
            val useId = Wallet.useId(chargeId)
            if (walletNode.hasChild(useId)) return@forEachIndexed
            val ledger = ledger()
            val due = ledger.due(chargeId) ?: return@forEachIndexed
            val amount = Wallet.useFor(ledger.walletBalance, due.remaining)
            if (amount <= 0) return@forEachIndexed
            walletNode.child(useId).value = WalletEntry(
                id = useId,
                type = WalletEntryType.USE.name,
                amount = amount,
                chargeId = chargeId,
                createdAt = now + index
            )
        }
    }

    /**
     * ينشئ رسوم الأشهر التي حان موعدها داخل الـ transaction، بمعرّف ثابت لكل شهر.
     * @return معرّفات المستحقات التي أُنشئت فعلًا الآن
     */
    private fun MutableData.generateDueCharges(today: SimpleDate, now: Long): List<String> {
        val student = getValue(Student::class.java) ?: return emptyList()
        val periods = student.periodsDue(today)
        if (periods.isEmpty()) return emptyList()
        val chargesNode = child(CHARGES)
        val created = mutableListOf<String>()
        periods.forEachIndexed { index, period ->
            if (!chargesNode.hasChild(period.chargeId)) {
                chargesNode.child(period.chargeId).value = Charge(
                    id = period.chargeId,
                    amount = student.monthlyFee,
                    dueDate = period.dueDate.format(),
                    period = period.month.key,
                    createdAt = now + index
                )
                created += period.chargeId
            }
        }
        child("lastBilledPeriod").value = periods.last().month.key
        return created
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
            child(PAYMENTS).children.mapNotNull { it.getValue(Payment::class.java) },
            child(WALLET).children.mapNotNull { it.getValue(WalletEntry::class.java) }
        )
        val totals = ledger.totals
        val student = stored.copy(
            paidCash = totals.paidCash,
            paidBank = totals.paidBank,
            paid = totals.paid,
            total = totals.total,
            remaining = totals.remaining,
            walletBalance = totals.walletBalance
        )
        return StudentWithPayments(student, ledger)
    }

    private companion object {
        const val ROOT = "TalibPay"
        const val STUDENTS = "students"
        const val CHARGES = "charges"
        const val PAYMENTS = "payments"
        const val WALLET = "wallet"
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
