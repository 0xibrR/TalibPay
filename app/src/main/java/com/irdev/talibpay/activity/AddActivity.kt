package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.irdev.talibpay.R
import com.irdev.talibpay.data.StudentInput
import com.irdev.talibpay.databinding.ActivityAddBinding
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.setupToolbar
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.ReminderPrefs
import com.irdev.talibpay.util.optionalAmount
import com.irdev.talibpay.util.requireCountryCode
import com.irdev.talibpay.util.setupCountryCodePicker
import com.irdev.talibpay.util.requireText
import com.irdev.talibpay.util.showDatePicker
import com.irdev.talibpay.util.showYearPicker
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

        binding.edtBirthYear.setOnClickListener {
            showYearPicker { binding.edtBirthYear.setText(it.toString()) }
        }
        // تاريخ التسجيل: اليوم افتراضيًا، ويمكن تغييره من منتقي التاريخ
        if (savedInstanceState == null) binding.edtRegistrationDate.setText(todayDate())
        // رمز الدولة: الرمز الافتراضي من الإعدادات، ويمكن اختيار غيره لكل طالب
        binding.edtCountryCode.setupCountryCodePicker(ReminderPrefs.countryCode(this))
        binding.edtRegistrationDate.setOnClickListener {
            showDatePicker { binding.edtRegistrationDate.setText(it) }
        }

        binding.btnAddStudent.setOnClickListener {
            val name = binding.edtStudentName.requireText() ?: return@setOnClickListener
            val birthYear = binding.edtBirthYear.requireText() ?: return@setOnClickListener
            val registrationDate = binding.edtRegistrationDate.requireText() ?: return@setOnClickListener
            val monthlyFee = binding.edtMonthlyFee.optionalAmount() ?: return@setOnClickListener
            val phone = binding.edtPhoneNumber.requireText() ?: return@setOnClickListener
            val countryCode = binding.edtCountryCode.requireCountryCode(binding.countryCodeLayout)
                ?: return@setOnClickListener
            val notes = binding.edtNotes.text?.toString()?.trim().orEmpty()
            viewModel.save(StudentInput(name, birthYear, registrationDate, monthlyFee, phone, countryCode, notes))
        }

        collectWhileStarted(viewModel.loading) { binding.btnAddStudent.isEnabled = !it }
        collectWhileStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message -> Snackbar.make(binding.root, event.res, Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.btnAddStudent).show()

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
        binding.edtPhoneNumber.text?.clear()
        binding.edtNotes.text?.clear()
        binding.edtStudentName.requestFocus()
    }
}
