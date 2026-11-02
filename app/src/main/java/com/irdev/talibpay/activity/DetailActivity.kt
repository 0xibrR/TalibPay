package com.irdev.talibpay.activity

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
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
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.nextMonthlyDue
import com.irdev.talibpay.domain.todaySimpleDate
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.util.Extras
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.dueTitle
import com.irdev.talibpay.util.formatTimestamp
import com.irdev.talibpay.util.money
import com.irdev.talibpay.util.paidPercent
import com.irdev.talibpay.util.setupToolbar
import com.irdev.talibpay.viewmodel.DetailViewModel
import com.irdev.talibpay.viewmodel.UiEvent
import java.io.File
import java.io.FileOutputStream

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val viewModel: DetailViewModel by viewModels()
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
                is UiEvent.Message -> Snackbar.make(binding.root, event.res, Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.fabAddPayment).show()

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

            // المستحقات
            val dueCount = data.dues.size
            val paymentCount = data.payments.size
            tvDuesCount.text = listOf(
                resources.getQuantityString(R.plurals.dues_count, dueCount, dueCount),
                resources.getQuantityString(R.plurals.payments_count, paymentCount, paymentCount)
            ).joinToString(" · ")
            tvNoDues.isVisible = data.dues.isEmpty()

            // معلومات الطالب
            rowPhone.bind(R.drawable.ic_phone, R.string.phone_number, student.phoneNumber)
            rowBirthYear.bind(R.drawable.ic_cake, R.string.year_of_birth, student.birthYear)
            rowRegistrationDate.bind(R.drawable.ic_calendar, R.string.registration_date, student.registrationDate)
            // الرسوم الشهرية وموعد الاستحقاق القادم
            val nextDue = student.nextMonthlyDue(todaySimpleDate())
            rowMonthlyFee.bind(
                R.drawable.ic_cash, R.string.monthly_fee,
                if (nextDue != null) getString(R.string.monthly_fee_value, money(student.monthlyFee), nextDue.format())
                else getString(R.string.monthly_fee_not_set)
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
        startActivity(
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
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.payment_of_due, money(payment.paid), dueTitle(due.charge)))
            .setItems(arrayOf(getString(R.string.edit_pyment), getString(R.string.delete_payment))) { _, which ->
                if (which == 0) openPaymentForm(paymentId = payment.id) else confirmDeletePayment(payment)
            }
            .show()
    }

    private fun confirmDeletePayment(payment: Payment) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_payment)
            .setMessage(getString(R.string.delete_payment_message, money(payment.paid), payment.paymentDate))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deletePayment(payment.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- القائمة العلوية ----------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.detail_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val data = viewModel.student.value
        when (item.itemId) {
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

            R.id.menu_downloadNotification -> {
                shareCardImage()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
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
            getString(R.string.share_phone, s.phoneNumber),
            "",
            getString(R.string.share_paid_cash, s.paidCash),
            getString(R.string.share_paid_bank, s.paidBank),
            getString(R.string.share_paid_total, s.paid),
            getString(R.string.share_total_amount, s.total),
            getString(R.string.share_remaining, s.remaining),
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

    private fun shareCardImage() {
        val card = binding.studentCardLayout
        if (card.width == 0 || card.height == 0) return

        val bitmap = createBitmap(card.width, card.height)
        card.draw(Canvas(bitmap))

        // الصورة في cache الخاص بالتطبيق، وتُشارك عبر FileProvider بصلاحية قراءة مؤقتة
        val file = File(cacheDir, "student_card.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
    }

    private companion object {
        const val MENU_EDIT = 1
        const val MENU_DELETE = 2
    }
}
