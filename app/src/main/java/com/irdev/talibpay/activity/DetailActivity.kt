package com.irdev.talibpay.activity

import android.content.res.ColorStateList
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.irdev.talibpay.R
import com.irdev.talibpay.adapter.DueAdapter
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.databinding.ActivityDetailBinding
import com.irdev.talibpay.databinding.ItemRowInfoBinding
import com.irdev.talibpay.databinding.DialogRefundBinding
import com.irdev.talibpay.databinding.ItemWalletEntryBinding
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.RefundValidation
import com.irdev.talibpay.domain.WalletEntryType
import com.irdev.talibpay.domain.entryType
import com.irdev.talibpay.domain.attendanceDaySet
import com.irdev.talibpay.domain.nextMonthlyDue
import com.irdev.talibpay.domain.overdueDues
import com.irdev.talibpay.domain.todaySimpleDate
import com.irdev.talibpay.domain.validateRefund
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.util.showCurrency
import com.irdev.talibpay.util.Extras
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.attendanceDaysText
import com.irdev.talibpay.util.displayPhone
import com.irdev.talibpay.util.monthlyFeeText
import com.irdev.talibpay.util.dueTitle
import com.irdev.talibpay.util.formatTimestamp
import com.irdev.talibpay.util.money
import com.irdev.talibpay.util.paidPercent
import com.irdev.talibpay.util.reminderMessage
import com.irdev.talibpay.util.shareReceipt
import com.irdev.talibpay.util.shareStatement
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.util.sendReminder
import com.irdev.talibpay.util.setupToolbar
import com.irdev.talibpay.viewmodel.DetailViewModel
import com.irdev.talibpay.viewmodel.UiEvent
import java.util.Locale

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val viewModel: DetailViewModel by viewModels()

    // نموذج الدفعة يعيد معرّف الدفعة الجديدة لعرض إرسال إيصالها
    private val paymentForm = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringExtra(Extras.PAYMENT_ID)?.let(::offerReceipt)
    }
    private val adapter = DueAdapter(
        onPay = { due -> openPaymentForm(dueId = due.charge.id) },
        onDueMenu = ::showDueMenu,
        onPaymentClick = ::showPaymentOptions
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main, applyTop = false)
        setupToolbar(binding.toolbar)
        title = getString(R.string.details_student)

        if (viewModel.studentId.isEmpty()) {
            finish()
            return
        }

        binding.recyclerViewDues.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewDues.adapter = adapter

        binding.btnAddDue.setOnClickListener { openChargeForm(null) }
        binding.btnWalletRefund.setOnClickListener { showRefundDialog() }
        binding.btnRemind.setOnClickListener { sendOverdueReminder() }
        binding.fabAddPayment.setOnClickListener {
            // لا دفعة بلا مستحق مفتوح: نوجّه المستخدم لإضافة مستحق أولًا
            val data = viewModel.student.value
            if (data != null && data.ledger.openDues.isEmpty()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.no_open_dues_title)
                    .setMessage(R.string.no_open_dues)
                    .setPositiveButton(R.string.add_due) { _, _ -> openChargeForm(null) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                openPaymentForm()
            }
        }

        collectWhileStarted(viewModel.student) { it?.let(::render) }
        collectWhileStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message -> Snackbar.make(
                    binding.root,
                    if (event.arg != null) getString(event.res, event.arg) else getString(event.res),
                    Snackbar.LENGTH_LONG
                ).setAnchorView(binding.fabAddPayment).show()

                UiEvent.Finished -> finish()
                UiEvent.NotSignedIn -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finishAffinity()
                }

                else -> Unit
            }
        }
    }

    private fun render(data: StudentWithPayments) {
        val student = data.student

        with(binding) {
            // البطاقة العلوية: الاسم والحالة والتقدم الإجمالي
            tvName.text = student.name
            tvInitial.text = student.name.firstOrNull()?.toString() ?: "?"
            studentProgress.setProgressCompat(paidPercent(student.paid, student.total), true)
            tvProgressLabel.text =
                getString(R.string.paid_of_total, money(student.paid), money(student.total))
            when {
                student.total <= 0 -> setStatus(getString(R.string.status_no_fees), null)
                student.remaining <= 0 -> setStatus(getString(R.string.status_fully_paid), R.color.status_paid)
                else -> setStatus(
                    getString(R.string.status_remaining, money(student.remaining)),
                    R.color.status_due
                )
            }
            tvStatCash.text = money(student.paidCash)
            tvStatBank.text = money(student.paidBank)
            tvStatTotal.text = money(student.total)
            tvStatRemaining.text = money(student.remaining)
            btnRemind.isVisible = data.ledger.overdueDues(todaySimpleDate()).isNotEmpty()
            if (tvArchivedBanner.isVisible != student.archived) {
                tvArchivedBanner.isVisible = student.archived
                invalidateOptionsMenu() // أرشفة أو إلغاء أرشفة: تبديل خيار القائمة
            }

            // المستحقات
            val dueCount = data.dues.size
            val paymentCount = data.payments.size
            tvDuesCount.text = listOf(
                resources.getQuantityString(R.plurals.dues_count, dueCount, dueCount),
                resources.getQuantityString(R.plurals.payments_count, paymentCount, paymentCount)
            ).joinToString(" · ")
            tvNoDues.isVisible = data.dues.isEmpty()

            // معلومات الطالب
            rowPhone.bind(R.drawable.ic_phone, R.string.phone_number, displayPhone(student))
            rowBirthYear.bind(R.drawable.ic_cake, R.string.year_of_birth, student.birthYear)
            rowRegistrationDate.bind(R.drawable.ic_calendar, R.string.registration_date, student.registrationDate)
            rowAttendanceDays.bind(R.drawable.ic_date_range, R.string.attendance_days, attendanceDaysText(student.attendanceDaySet))
            // الرسوم الشهرية وموعد الاستحقاق القادم
            val nextDue = student.nextMonthlyDue(todaySimpleDate())
            rowMonthlyFee.bind(
                R.drawable.ic_cash, R.string.monthly_fee,
                when {
                    nextDue != null -> getString(R.string.monthly_fee_value, monthlyFeeText(student), nextDue.format())
                    student.archived && student.monthlyFee > 0 ->
                        getString(R.string.monthly_fee_paused, monthlyFeeText(student))
                    else -> getString(R.string.monthly_fee_not_set)
                }
            )
            rowAdded.bind(
                R.drawable.ic_schedule, R.string.added,
                formatTimestamp(student.addedAt, student.addedIn)
            )
            rowLastModified.bind(
                R.drawable.ic_schedule, R.string.last_modified,
                formatTimestamp(student.updatedAt, student.lastModified)
            )
            rowNotes.bind(R.drawable.ic_notes, R.string.notes, student.notes)
        }

        adapter.submitList(data.dues)
        renderWallet(data)
    }

    /** المحفظة: الرصيد وسجل الحركات (الأحدث أولًا). */
    private fun renderWallet(data: StudentWithPayments) {
        val balance = data.ledger.walletBalance
        binding.tvWalletBalance.text = money(balance)
        binding.tvWalletBalance.setTextColor(
            ContextCompat.getColor(this, if (balance > 0) R.color.status_paid else R.color.method_none)
        )
        binding.btnWalletRefund.isEnabled = balance > 0
        binding.tvWalletEmpty.isVisible = data.walletEntries.isEmpty()

        val container = binding.walletEntries
        container.removeAllViews()
        data.walletEntries.asReversed().forEach { entry ->
            val row = ItemWalletEntryBinding.inflate(layoutInflater, container, true)
            val type = entry.entryType
            val dueTitle = data.ledger.due(entry.chargeId)?.let { dueTitle(it.charge) }.orEmpty()
            row.tvEntryTitle.text = when (type) {
                WalletEntryType.DEPOSIT -> getString(R.string.wallet_deposit)
                WalletEntryType.USE -> getString(R.string.wallet_use, dueTitle)
                WalletEntryType.REFUND -> getString(R.string.wallet_refund_entry)
                null -> entry.type
            }
            row.tvEntryDetails.text = listOf(formatTimestamp(entry.createdAt), entry.note)
                .filter { it.isNotBlank() }.joinToString(" · ")
            val incoming = type == WalletEntryType.DEPOSIT
            row.tvEntryAmount.text = (if (incoming) "+" else "−") + money(entry.amount)
            val color = ContextCompat.getColor(
                this, if (incoming) R.color.status_paid else R.color.method_mixed
            )
            row.tvEntryAmount.setTextColor(color)
            row.entryDot.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    private fun showRefundDialog() {
        val balance = viewModel.student.value?.ledger?.walletBalance ?: return
        val dialogBinding = DialogRefundBinding.inflate(layoutInflater)
        dialogBinding.edtRefundAmount.setText(Money.format(balance, Locale.US).replace(",", ""))
        dialogBinding.refundAmountLayout.showCurrency()
        dialogBinding.refundAmountLayout.helperText = getString(R.string.wallet_available, money(balance))
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.refund_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.wallet_refund, null)
            .setNegativeButton(R.string.cancel, null)
            .show()
        // التحقق داخل الحوار حتى لا يُغلق ويضيع ما كتبه المستخدم عند الخطأ
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val amountText = dialogBinding.edtRefundAmount.text?.toString().orEmpty()
            val error = when (val result = validateRefund(amountText, balance)) {
                is RefundValidation.Valid -> null
                RefundValidation.InvalidAmount -> getString(R.string.invalid_amount)
                is RefundValidation.ExceedsBalance ->
                    getString(R.string.refund_exceeds_balance, money(result.balance))
            }
            dialogBinding.refundAmountLayout.error = error
            if (error == null) {
                viewModel.refund(amountText, dialogBinding.edtRefundNote.text?.toString()?.trim().orEmpty())
                dialog.dismiss()
            }
        }
    }

    /** @param color null = لون نص البطاقة (حالة محايدة). */
    private fun setStatus(text: String, @ColorRes color: Int?) {
        binding.tvStatus.text = text
        binding.tvStatus.setTextColor(
            if (color != null) ContextCompat.getColor(this, color)
            else MaterialColors.getColor(binding.tvStatus, com.google.android.material.R.attr.colorOnPrimaryContainer)
        )
    }

    private fun ItemRowInfoBinding.bind(@DrawableRes icon: Int, @StringRes label: Int, value: String) {
        ivIcon.setImageResource(icon)
        tvLabel.setText(label)
        tvValue.text = value.ifBlank { getString(R.string.none_value) }
    }

    // ---------------- التنقل ----------------

    private fun openChargeForm(chargeId: String?) {
        startActivity(
            Intent(this, ChargeFormActivity::class.java)
                .putExtra(Extras.STUDENT_ID, viewModel.studentId)
                .apply { if (chargeId != null) putExtra(Extras.CHARGE_ID, chargeId) }
        )
    }

    private fun openPaymentForm(dueId: String? = null, paymentId: String? = null) {
        paymentForm.launch(
            Intent(this, PaymentFormActivity::class.java)
                .putExtra(Extras.STUDENT_ID, viewModel.studentId)
                .apply {
                    if (dueId != null) putExtra(Extras.CHARGE_ID, dueId)
                    if (paymentId != null) putExtra(Extras.PAYMENT_ID, paymentId)
                }
        )
    }

    // ---------------- خيارات المستحق والدفعة ----------------

    private fun showDueMenu(due: DueSummary, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_EDIT, 0, R.string.edit_due)
            menu.add(0, MENU_DELETE, 1, R.string.delete_due)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_EDIT -> openChargeForm(due.charge.id)
                    MENU_DELETE -> confirmDeleteDue(due)
                }
                true
            }
        }.show()
    }

    private fun confirmDeleteDue(due: DueSummary) {
        if (due.payments.isNotEmpty()) {
            // نفس قاعدة الـ ViewModel، لكن نوضحها قبل طلب التأكيد
            viewModel.deleteDue(due)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_due)
            .setMessage(getString(R.string.delete_due_message, dueTitle(due.charge)))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteDue(due) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showPaymentOptions(due: DueSummary, payment: Payment) {
        val title = getString(R.string.payment_of_due, money(payment.paid), dueTitle(due.charge))
        val student = viewModel.student.value?.student ?: return
        // دفعة أضافت رصيدًا للمحفظة مقفلة: تعديلها أو حذفها قد يجعل الرصيد سالبًا، لكن إيصالها متاح
        if (payment.credited > 0) {
            MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(R.string.payment_locked_by_wallet)
                .setPositiveButton(R.string.receipt_send) { _, _ -> shareReceipt(student, due, payment) }
                .setNegativeButton(R.string.ok, null)
                .show()
            return
        }
        val options = listOf(R.string.receipt_send, R.string.edit_pyment, R.string.delete_payment)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(options.map { getString(it) }.toTypedArray()) { _, which ->
                when (options[which]) {
                    R.string.receipt_send -> shareReceipt(student, due, payment)
                    R.string.edit_pyment -> openPaymentForm(paymentId = payment.id)
                    R.string.delete_payment -> confirmDeletePayment(payment)
                }
            }
            .show()
    }

    /** بعد تسجيل دفعة جديدة: رسالة الحفظ مع زر إرسال إيصالها مباشرة. */
    private fun offerReceipt(paymentId: String) {
        Snackbar.make(binding.root, R.string.payment_added_successfully, Snackbar.LENGTH_LONG)
            .setAction(R.string.receipt_send) { sendReceipt(paymentId) }
            .setAnchorView(binding.fabAddPayment)
            .show()
    }

    private fun sendReceipt(paymentId: String) {
        val data = viewModel.student.value ?: return
        val due = data.dues.firstOrNull { due -> due.payments.any { it.id == paymentId } }
        val payment = due?.payments?.firstOrNull { it.id == paymentId }
        if (due == null || payment == null) {
            toast(R.string.receipt_not_ready)
            return
        }
        shareReceipt(data.student, due, payment)
    }

    private fun confirmDeletePayment(payment: Payment) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_payment)
            .setMessage(getString(R.string.delete_payment_message, money(payment.paid), payment.paymentDate))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deletePayment(payment) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- القائمة العلوية ----------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.detail_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val archived = viewModel.student.value?.student?.archived == true
        menu.findItem(R.id.menu_archive)?.isVisible = !archived
        menu.findItem(R.id.menu_unarchive)?.isVisible = archived
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val data = viewModel.student.value
        when (item.itemId) {
            R.id.menu_archive -> {
                data?.let(::confirmArchive)
                return true
            }

            R.id.menu_unarchive -> {
                data?.let(::confirmUnarchive)
                return true
            }

            R.id.menu_edit -> {
                startActivity(
                    Intent(this, EditActivity::class.java)
                        .putExtra(Extras.STUDENT_ID, viewModel.studentId)
                )
                return true
            }

            R.id.menu_delete -> {
                val name = data?.student?.name.orEmpty()
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.delete_title_with_name, name))
                    .setMessage(getString(R.string.delete_message_with_name, name))
                    .setPositiveButton(R.string.ok) { _, _ -> viewModel.deleteStudent() }
                    .setNegativeButton(R.string.cancel, null)
                    .setCancelable(false)
                    .show()
                return true
            }

            R.id.menu_share -> {
                data?.let(::shareAsText)
                return true
            }

            R.id.menu_statement -> {
                data?.let(::shareStatement)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /** الأرشفة لا تحذف شيئًا؛ ننبّه فقط إن بقي على الطالب مبلغ حتى لا يُنسى. */
    private fun confirmArchive(data: StudentWithPayments) {
        val student = data.student
        val message = buildString {
            append(getString(R.string.archive_message))
            if (student.remaining > 0) {
                append("\n\n")
                append(getString(R.string.archive_remaining_warning, money(student.remaining)))
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.archive_title, student.name))
            .setMessage(message)
            .setPositiveButton(R.string.archive_student) { _, _ -> viewModel.archive() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmUnarchive(data: StudentWithPayments) {
        val message = if (data.student.monthlyFee > 0)
            getString(R.string.unarchive_message_with_fee, money(data.student.monthlyFee))
        else
            getString(R.string.unarchive_message)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.unarchive_title, data.student.name))
            .setMessage(message)
            .setPositiveButton(R.string.unarchive_student) { _, _ -> viewModel.unarchive() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** يفتح واتساب على رقم الطالب برسالة جاهزة فيها المستحقات المتأخرة ومجموعها. */
    private fun sendOverdueReminder() {
        val data = viewModel.student.value ?: return
        val overdue = data.ledger.overdueDues(todaySimpleDate())
        if (overdue.isEmpty()) return
        sendReminder(data.student, reminderMessage(data.student, overdue))
    }

    private fun shareAsText(data: StudentWithPayments) {
        val s = data.student
        val dueLines = data.dues.map { due ->
            getString(
                R.string.share_due_line,
                dueTitle(due.charge), money(due.paid), money(due.charge.amount), money(due.remaining)
            )
        }
        val shareText = (listOf(
            getString(R.string.share_name, s.name),
            getString(R.string.share_birth_year, s.birthYear),
            getString(R.string.share_registration_date, s.registrationDate.ifBlank { getString(R.string.none_value) }),
            getString(R.string.share_phone, displayPhone(s)),
            "",
            getString(R.string.share_paid_cash, money(s.paidCash)),
            getString(R.string.share_paid_bank, money(s.paidBank)),
            getString(R.string.share_paid_total, money(s.paid)),
            getString(R.string.share_total_amount, money(s.total)),
            getString(R.string.share_remaining, money(s.remaining)),
            "",
            getString(R.string.dues_title)
        ) + dueLines + listOf(
            "",
            getString(R.string.share_notes, s.notes),
            getString(R.string.share_added_at, formatTimestamp(s.addedAt, s.addedIn)),
            getString(R.string.share_last_modified, formatTimestamp(s.updatedAt, s.lastModified))
        )).joinToString("\n")

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, s.name))
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
    }

    private companion object {
        const val MENU_EDIT = 1
        const val MENU_DELETE = 2
    }
}
