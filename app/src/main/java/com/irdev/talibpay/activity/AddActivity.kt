package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.irdev.talibpay.R
import com.irdev.talibpay.data.StudentInput
import com.irdev.talibpay.domain.StudentDiscount
import com.irdev.talibpay.databinding.ActivityAddBinding
import com.irdev.talibpay.util.showCurrency
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.setupToolbar
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.ReminderPrefs
import com.irdev.talibpay.util.optionalAmount
import com.irdev.talibpay.util.clearDays
import com.irdev.talibpay.util.readDiscount
import com.irdev.talibpay.util.selectedDays
import com.irdev.talibpay.util.setup
import com.irdev.talibpay.util.requireCountryCode
import com.irdev.talibpay.util.setupCountryCodePicker
import com.irdev.talibpay.util.requireText
import com.irdev.talibpay.util.showDatePicker
import com.irdev.talibpay.util.showYearPicker
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.util.todayDate
import com.irdev.talibpay.viewmodel.StudentFormViewModel
import com.irdev.talibpay.viewmodel.UiEvent

class AddActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddBinding
    private val viewModel: StudentFormViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAddBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main, applyTop = false)
        setupToolbar(binding.toolbar)
        title = getString(R.string.add_student)

        binding.monthlyFeeLayout.showCurrency()
        binding.discount.setup(binding.edtMonthlyFee, StudentDiscount.NONE)
        binding.attendanceDays.setup(emptySet())
        binding.edtBirthYear.setOnClickListener {
            showYearPicker { binding.edtBirthYear.setText(it.toString()) }
        }
        // تاريخ التسجيل: اليوم افتراضيًا، ويمكن تغييره من الـ date picker
        if (savedInstanceState == null) binding.edtRegistrationDate.setText(todayDate())
        // رمز الدولة: الافتراضي من الإعدادات، ويمكن اختيار غيره لكل طالب
        binding.edtCountryCode.setupCountryCodePicker(ReminderPrefs.countryCode(this))
        binding.edtRegistrationDate.setOnClickListener {
            showDatePicker { binding.edtRegistrationDate.setText(it) }
        }

        binding.btnAddStudent.setOnClickListener {
            val name = binding.edtStudentName.requireText() ?: return@setOnClickListener
            val birthYear = binding.edtBirthYear.requireText() ?: return@setOnClickListener
            val registrationDate = binding.edtRegistrationDate.requireText() ?: return@setOnClickListener
            val monthlyFee = binding.edtMonthlyFee.optionalAmount() ?: return@setOnClickListener
            val discount = binding.discount.readDiscount(monthlyFee) ?: return@setOnClickListener
            val phone = binding.edtPhoneNumber.requireText() ?: return@setOnClickListener
            val countryCode = binding.edtCountryCode.requireCountryCode(binding.countryCodeLayout)
                ?: return@setOnClickListener
            val notes = binding.edtNotes.text?.toString()?.trim().orEmpty()
            viewModel.save(
                StudentInput(
                    name, birthYear, registrationDate, monthlyFee, phone, countryCode, notes,
                    discount = discount, attendanceDays = binding.attendanceDays.selectedDays()
                )
            )
        }

        collectWhileStarted(viewModel.loading) { binding.btnAddStudent.isEnabled = !it }
        collectWhileStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message -> toast(event)

                // نبقى في الشاشة لإضافة طالب آخر بسرعة
                UiEvent.Finished -> clearForm()
                UiEvent.NotSignedIn -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finishAffinity()
                }

                else -> Unit
            }
        }
    }

    private fun clearForm() {
        binding.edtStudentName.text?.clear()
        binding.edtBirthYear.text?.clear()
        binding.edtRegistrationDate.setText(todayDate())
        binding.edtMonthlyFee.text?.clear()
        binding.discount.discountTypeToggle.check(R.id.btnDiscountNone)
        binding.discount.edtDiscountValue.text?.clear()
        binding.discount.edtDiscountReason.text?.clear()
        binding.attendanceDays.clearDays()
        binding.edtPhoneNumber.text?.clear()
        binding.edtNotes.text?.clear()
        binding.edtStudentName.requestFocus()
    }
}
