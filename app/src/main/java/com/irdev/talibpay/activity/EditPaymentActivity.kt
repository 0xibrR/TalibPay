package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.irdev.talibpay.databinding.ActivityEditPaymentBinding
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.PaymentMethod
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.requireText
import com.irdev.talibpay.util.selectPaymentMethod
import com.irdev.talibpay.util.selectedPaymentMethod
import com.irdev.talibpay.util.setupPaymentMethods
import com.irdev.talibpay.util.setupToolbar
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
        applySystemBarInsets(binding.main, applyTop = false)
        setupToolbar(binding.toolbar)
        // بعد تدوير الشاشة يحتفظ النظام بما كتبه المستخدم، فلا نعيد التعبئة
        prefilled = savedInstanceState != null

        binding.spinnerPaymentMethodEdit.setupPaymentMethods()
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
                method = binding.spinnerPaymentMethodEdit.selectedPaymentMethod(),
                paymentDate = date,
                note = binding.edtNotesPaymentEdit.text?.toString()?.trim().orEmpty()
            )
        }

        collectWhileStarted(viewModel.existing) { payment ->
            if (payment == null || prefilled) return@collectWhileStarted
            prefilled = true
            binding.edtPaidCashEdit.setText(plainAmount(payment.cash))
            binding.edtPaidBankEdit.setText(plainAmount(payment.bank))
            binding.edtTotalEdit.setText(plainAmount(payment.total))
            binding.edtPaymentDateEdit.setText(payment.paymentDate)
            binding.edtNotesPaymentEdit.setText(payment.note)
            binding.spinnerPaymentMethodEdit.selectPaymentMethod(
                PaymentMethod.fromStored(payment.paymentMethod)
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

    // أرقام لاتينية بدون فواصل آلاف حتى يقبلها حقل الإدخال الرقمي
    private fun plainAmount(value: Double) = Money.format(value, Locale.US).replace(",", "")
}
