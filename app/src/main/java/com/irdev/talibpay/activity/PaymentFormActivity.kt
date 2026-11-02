package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ActivityPaymentBinding
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.dueTitle
import com.irdev.talibpay.util.money
import com.irdev.talibpay.util.requireText
import com.irdev.talibpay.util.setupToolbar
import com.irdev.talibpay.util.showDatePicker
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.viewmodel.PaymentFormState
import com.irdev.talibpay.viewmodel.PaymentFormViewModel
import com.irdev.talibpay.viewmodel.UiEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** تسجيل دفعة لمستحق، أو تعديل دفعة موجودة. */
class PaymentFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private val viewModel: PaymentFormViewModel by viewModels()

    private var formState: PaymentFormState? = null
    private var selectedDueId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main, applyTop = false)
        setupToolbar(binding.toolbar)
        title = getString(if (viewModel.isEditing) R.string.edit_pyment else R.string.add_payment)
        selectedDueId = savedInstanceState?.getString(KEY_DUE)

        binding.edtPaymentDate.setOnClickListener {
            showDatePicker { binding.edtPaymentDate.setText(it) }
        }
        binding.edtPaidCash.doAfterTextChanged { updatePreview() }
        binding.edtPaidBank.doAfterTextChanged { updatePreview() }
        binding.dueDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedDueId = formState?.dues?.getOrNull(position)?.charge?.id
            updatePreview()
        }

        binding.btnSavePayment.setOnClickListener {
            val date = binding.edtPaymentDate.requireText() ?: return@setOnClickListener
            viewModel.save(
                dueId = selectedDueId,
                // الحقل الفارغ في النقدي أو البنكي يعني صفرًا
                cashText = binding.edtPaidCash.text?.toString().orEmpty().ifBlank { "0" },
                bankText = binding.edtPaidBank.text?.toString().orEmpty().ifBlank { "0" },
                paymentDate = date,
                note = binding.edtNotesPayment.text?.toString()?.trim().orEmpty()
            )
        }

        collectWhileStarted(viewModel.state) { state ->
            if (state == null) return@collectWhileStarted
            val firstLoad = formState == null
            formState = state
            bindDues(state)
            if (firstLoad && savedInstanceState == null) prefill(state)
            updatePreview()
        }
        collectWhileStarted(viewModel.loading) { binding.btnSavePayment.isEnabled = !it }
        collectWhileStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message -> toast(event)
                UiEvent.Finished -> finish()
                UiEvent.NotSignedIn -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finishAffinity()
                }

                else -> Unit
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_DUE, selectedDueId)
    }

    private fun bindDues(state: PaymentFormState) {
        if (state.dues.isEmpty()) {
            // لا يوجد مستحق مفتوح: لا يمكن تسجيل دفعة
            binding.dueLayout.error = getString(R.string.no_open_dues)
            binding.btnSavePayment.isEnabled = false
            return
        }
        binding.dueDropdown.setSimpleItems(
            state.dues.map { getString(R.string.due_option, dueTitle(it.charge), money(state.availableFor(it.charge.id))) }
                .toTypedArray()
        )
        if (selectedDueId == null) selectedDueId = state.selectedDueId
        val index = state.dues.indexOfFirst { it.charge.id == selectedDueId }
        if (index >= 0) {
            binding.dueDropdown.setText(binding.dueDropdown.adapter.getItem(index).toString(), false)
        }
    }

    private fun prefill(state: PaymentFormState) {
        val existing = state.existing
        if (existing != null) {
            if (existing.cash > 0) binding.edtPaidCash.setText(plainAmount(existing.cash))
            if (existing.bank > 0) binding.edtPaidBank.setText(plainAmount(existing.bank))
            binding.edtPaymentDate.setText(existing.paymentDate)
            binding.edtNotesPayment.setText(existing.note)
        } else {
            // تاريخ اليوم افتراضيًا
            binding.edtPaymentDate.setText(SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date()))
        }
    }

    /** يعرض مجموع الدفعة وما يبقى من المستحق بعدها، وينبّه إن تجاوزت المتبقي. */
    private fun updatePreview() {
        val state = formState ?: return
        val dueId = selectedDueId ?: return
        val due = state.dues.firstOrNull { it.charge.id == dueId } ?: return
        val available = state.availableFor(dueId)
        val cash = Money.parse(binding.edtPaidCash.text?.toString().orEmpty().ifBlank { "0" }) ?: 0.0
        val bank = Money.parse(binding.edtPaidBank.text?.toString().orEmpty().ifBlank { "0" }) ?: 0.0
        val amount = Money.sum(listOf(cash, bank))
        val after = Money.subtract(available, amount)

        binding.dueLayout.error = null
        binding.dueLayout.helperText = getString(
            R.string.due_helper, money(due.charge.amount), money(available)
        )
        binding.tvPaymentPreview.text = if (after < 0) {
            getString(R.string.payment_preview_exceeds, money(amount), money(available))
        } else {
            getString(R.string.payment_preview, money(amount), money(after))
        }
        binding.tvPaymentPreview.setTextColor(
            ContextCompat.getColor(this, if (after < 0) R.color.status_due else R.color.method_none)
        )
    }

    // أرقام لاتينية بدون فواصل آلاف حتى يقبلها حقل الإدخال الرقمي
    private fun plainAmount(value: Double) = Money.format(value, Locale.US).replace(",", "")

    private companion object {
        const val KEY_DUE = "selectedDue"
    }
}
