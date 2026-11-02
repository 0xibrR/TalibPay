package com.irdev.talibpay.activity

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.irdev.talibpay.R
import com.irdev.talibpay.adapter.PaymentAdapter
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.databinding.ActivityDetailBinding
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.PaymentMethod
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.util.Extras
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.attachSwipeActions
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.formatTimestamp
import com.irdev.talibpay.viewmodel.DetailViewModel
import com.irdev.talibpay.viewmodel.UiEvent
import java.io.File
import java.io.FileOutputStream

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val viewModel: DetailViewModel by viewModels()
    private val adapter = PaymentAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main)
        title = getString(R.string.details_student)

        if (viewModel.studentId.isEmpty()) {
            finish()
            return
        }

        binding.recyclerViewPayment.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPayment.adapter = adapter
        binding.recyclerViewPayment.attachSwipeActions(
            onEdit = { position -> openEditPayment(adapter.currentList[position]) },
            onDelete = { position -> confirmDeletePayment(adapter.currentList[position]) }
        )

        binding.fabAddPayment.setOnClickListener {
            startActivity(
                Intent(this, AddPayment::class.java)
                    .putExtra(Extras.STUDENT_ID, viewModel.studentId)
            )
        }

        collectWhileStarted(viewModel.student) { it?.let(::render) }
        collectWhileStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message -> Snackbar.make(binding.root, event.res, Snackbar.LENGTH_LONG).show()
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
        val last = data.lastPayment

        with(binding) {
            tvName.text = student.name
            rowBirthYear.tvValue.text = label(R.string.year_of_birth, student.birthYear)
            rowPhone.tvValue.text = label(R.string.phone_number, student.phoneNumber)
            rowPaymentDate.tvValue.text = label(R.string.payment_date, last?.paymentDate.orEmpty())
            rowPaidCash.tvValue.text = amount(R.string.paid_cash_label, student.paidCash)
            rowPaidBank.tvValue.text = amount(R.string.paid_bank_label, student.paidBank)
            rowPaid.tvValue.text = amount(R.string.paid_total, student.paid)
            rowTotalAmount.tvValue.text = amount(R.string.total_amount_label, student.total)
            rowRemaining.tvValue.text = amount(R.string.remaining_total, student.remaining)
            rowPaymentMethod.tvValue.text = label(R.string.payment_method, methodLabel(last))
            rowAdded.tvValue.text =
                label(R.string.added, formatTimestamp(student.addedAt, student.addedIn))
            rowLastModified.tvValue.text =
                label(R.string.last_modified, formatTimestamp(student.updatedAt, student.lastModified))
            tvNotes.text = label(R.string.notes, student.notes)
        }

        // الأحدث أولًا
        adapter.submitList(data.payments.reversed())
    }

    private fun label(res: Int, value: String) = "${getString(res)}: $value"

    private fun amount(res: Int, value: Double) = "${getString(res)} ${Money.format(value)}"

    private fun methodLabel(payment: Payment?): String {
        if (payment == null) return getString(R.string.no_payments)
        return PaymentMethod.fromStored(payment.paymentMethod)?.let { getString(it.label) }
            ?: payment.paymentMethod
    }

    private fun openEditPayment(payment: Payment) {
        startActivity(
            Intent(this, EditPaymentActivity::class.java)
                .putExtra(Extras.STUDENT_ID, viewModel.studentId)
                .putExtra(Extras.PAYMENT_ID, payment.id)
        )
    }

    private fun confirmDeletePayment(payment: Payment) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_title_payment, ""))
            .setMessage(getString(R.string.delete_message_payment, ""))
            .setPositiveButton(R.string.ok) { _, _ -> viewModel.deletePayment(payment.id) }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(false)
            .show()
    }

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
                AlertDialog.Builder(this)
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
        val last = data.lastPayment
        val shareText = listOf(
            getString(R.string.share_name, s.name),
            getString(R.string.share_birth_year, s.birthYear),
            getString(R.string.share_phone, s.phoneNumber),
            getString(R.string.share_payment_date, last?.paymentDate.orEmpty()),
            "",
            getString(R.string.share_paid_cash, s.paidCash),
            getString(R.string.share_paid_bank, s.paidBank),
            getString(R.string.share_paid_total, s.paid),
            getString(R.string.share_total_amount, s.total),
            getString(R.string.share_remaining, s.remaining),
            getString(R.string.share_payment_method, methodLabel(last)),
            "",
            getString(R.string.share_notes, s.notes),
            getString(R.string.share_added_at, formatTimestamp(s.addedAt, s.addedIn)),
            getString(R.string.share_last_modified, formatTimestamp(s.updatedAt, s.lastModified))
        ).joinToString("\n")

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
}
