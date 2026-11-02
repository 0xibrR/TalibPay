package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ActivityAddPaymentBinding
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.requireText
import com.irdev.talibpay.util.showDatePicker
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.viewmodel.PaymentFormViewModel
import com.irdev.talibpay.viewmodel.UiEvent

class AddPayment : AppCompatActivity() {

    private lateinit var binding: ActivityAddPaymentBinding
    private val viewModel: PaymentFormViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAddPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main)
        title = getString(R.string.add_payment)

        binding.edtPaymentDate.setOnClickListener {
            showDatePicker { binding.edtPaymentDate.setText(it) }
        }

        binding.btnAddPayment.setOnClickListener {
            val total = binding.edtTotal.requireText() ?: return@setOnClickListener
            val date = binding.edtPaymentDate.requireText() ?: return@setOnClickListener
            viewModel.save(
                // الحقل الفارغ في النقدي أو البنكي يعني صفرًا
                cashText = binding.edtPaidCash.text?.toString().orEmpty().ifBlank { "0" },
                bankText = binding.edtPaidBank.text?.toString().orEmpty().ifBlank { "0" },
                totalText = total,
                methodPosition = binding.spinnerPaymentMethod.selectedItemPosition,
                paymentDate = date,
                note = binding.edtNotesPayment.text?.toString()?.trim().orEmpty()
            )
        }

        collectWhileStarted(viewModel.loading) { binding.btnAddPayment.isEnabled = !it }
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
