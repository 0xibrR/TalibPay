package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ActivityChargeBinding
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.todaySimpleDate
import com.irdev.talibpay.util.money
import com.irdev.talibpay.util.showCurrency
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.dueTitle
import com.irdev.talibpay.util.monthName
import com.irdev.talibpay.util.setupToolbar
import com.irdev.talibpay.util.showDatePicker
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.viewmodel.ChargeFormViewModel
import com.irdev.talibpay.viewmodel.UiEvent
import java.util.Locale

/** إضافة مستحق جديد على الطالب أو تعديل مستحق موجود. */
class ChargeFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChargeBinding
    private val viewModel: ChargeFormViewModel by viewModels()
    private var prefilled = false
    private var generatedTitle: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChargeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.amountLayout.showCurrency()
        applySystemBarInsets(binding.main, applyTop = false)
        setupToolbar(binding.toolbar)
        title = getString(if (viewModel.isEditing) R.string.edit_due else R.string.add_due)
        prefilled = savedInstanceState != null

        binding.edtChargeDueDate.setOnClickListener {
            showDatePicker { binding.edtChargeDueDate.setText(it) }
        }
        binding.btnSaveCharge.setOnClickListener {
            viewModel.save(
                title = binding.edtChargeTitle.text?.toString().orEmpty(),
                keepGeneratedTitle = generatedTitle != null &&
                    binding.edtChargeTitle.text?.toString()?.trim() == generatedTitle,
                amountText = binding.edtChargeAmount.text?.toString().orEmpty(),
                dueDate = binding.edtChargeDueDate.text?.toString().orEmpty(),
                note = binding.edtChargeNote.text?.toString()?.trim().orEmpty()
            )
        }

        collectWhileStarted(viewModel.prefill) { prefill ->
            if (prefill == null || prefilled) return@collectWhileStarted
            prefilled = true
            val due = prefill.due
            if (due != null) {
                binding.edtChargeTitle.setText(dueTitle(due.charge))
                // اسم مولَّد (رسوم شهرية أو قديمة): إن لم يغيّره المستخدم يبقى مولَّدًا ويُترجم مع اللغة
                if (due.charge.title.isBlank()) generatedTitle = dueTitle(due.charge)
                binding.edtChargeAmount.setText(plainAmount(due.charge.amount))
                binding.edtChargeDueDate.setText(due.charge.dueDate)
                binding.edtChargeNote.setText(due.charge.note)
                if (due.paid > 0) {
                    binding.amountLayout.helperText =
                        getString(R.string.due_paid_so_far, money(due.paid))
                }
            } else {
                // اقتراح رسوم الشهر الحالي فقط لمن لا تُنشأ رسومه تلقائيًا (انظر ChargeFormPrefill)
                if (prefill.suggestMonthTitle) {
                    val month = monthName(todaySimpleDate().yearMonth)
                    binding.edtChargeTitle.setText(getString(R.string.due_title_month, month))
                }
                prefill.suggestedAmount?.let { binding.edtChargeAmount.setText(plainAmount(it)) }
            }
        }
        collectWhileStarted(viewModel.loading) { binding.btnSaveCharge.isEnabled = !it }
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

    // أرقام لاتينية بدون فواصل آلاف حتى يقبلها حقل الإدخال الرقمي
    private fun plainAmount(value: Double) = Money.format(value, Locale.US).replace(",", "")
}
