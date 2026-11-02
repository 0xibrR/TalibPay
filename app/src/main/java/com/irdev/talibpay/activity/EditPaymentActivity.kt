package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ActivityEditPaymentBinding
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.PaymentMethod
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.requireText
import com.irdev.talibpay.util.showDatePicker
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.viewmodel.PaymentFormViewModel
import com.irdev.talibpay.viewmodel.UiEvent
import java.util.Locale

class EditPaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditPaymentBinding
    private val viewModel: PaymentFormViewModel by viewModels()
    private var prefilled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main)
        title = getString(R.string.edit_pyment)
        prefilled = savedInstanceState != null

        binding.edtPaymentDateEdit.setOnClickListener {
            showDatePicker { binding.edtPaymentDateEdit.setText(it) }
        }

        binding.btnEditPayment.setOnClickListener {
            val total = binding.edtTotalEdit.requireText() ?: return@setOnClickListener
            val date = binding.edtPaymentDateEdit.requireText() ?: return@setOnClickListener
            viewModel.save(
                cashText = binding.edtPaidCashEdit.text?.toString().orEmpty().ifBlank { "0" },
                bankText = binding.edtPaidBankEdit.text?.toString().orEmpty().ifBlank { "0" },
                totalText = total,
                methodPosition = binding.spinnerPaymentMethodEdit.selectedItemPosition,
                paymentDate = date,
                note = binding.edtNotesPaymentEdit.text?.toString()?.trim().orEmpty()
            )
        }

        collectWhileStarted(viewModel.existing) { payment ->
            if (payment == null || prefilled) return@collectWhileStarted
            prefilled = true
            // أرقام لاتينية بدون فواصل آلاف حتى يقبلها حقل الإدخال الرقمي
            binding.edtPaidCashEdit.setText(Money.format(payment.cash, Locale.US).replace(",", ""))
            binding.edtPaidBankEdit.setText(Money.format(payment.bank, Locale.US).replace(",", ""))
            binding.edtTotalEdit.setText(Money.format(payment.total, Locale.US).replace(",", ""))
            binding.edtPaymentDateEdit.setText(payment.paymentDate)
            binding.edtNotesPaymentEdit.setText(payment.note)
            binding.spinnerPaymentMethodEdit.setSelection(
                PaymentMethod.spinnerPositionOf(PaymentMethod.fromStored(payment.paymentMethod))
            )
        }
        collectWhileStarted(viewModel.loading) { binding.btnEditPayment.isEnabled = !it }
        collectWhileStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message -> toast(event.res)
                UiEvent.Finished -> finish()
                UiEvent.NotSignedIn -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finishAffinity()
                }

                else -> Unit
            }
        }
    }
}
