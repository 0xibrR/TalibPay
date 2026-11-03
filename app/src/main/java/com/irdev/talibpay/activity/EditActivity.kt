package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import com.irdev.talibpay.R
import com.irdev.talibpay.data.StudentInput
import com.irdev.talibpay.databinding.ActivityEditBinding
import com.irdev.talibpay.domain.StudentDiscount
import com.irdev.talibpay.domain.discount
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.pastDuesSinceRegistration
import com.irdev.talibpay.domain.shouldAskToBackdate
import com.irdev.talibpay.domain.todaySimpleDate
import com.irdev.talibpay.util.showCurrency
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.setupToolbar
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.money
import com.irdev.talibpay.util.ReminderPrefs
import com.irdev.talibpay.util.countryCodeFor
import com.irdev.talibpay.util.optionalAmount
import com.irdev.talibpay.util.readDiscount
import com.irdev.talibpay.util.setup
import com.irdev.talibpay.util.requireCountryCode
import com.irdev.talibpay.util.setupCountryCodePicker
import com.irdev.talibpay.util.requireText
import com.irdev.talibpay.util.showDatePicker
import com.irdev.talibpay.util.showYearPicker
import com.irdev.talibpay.util.todayDate
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.viewmodel.StudentFormViewModel
import com.irdev.talibpay.viewmodel.UiEvent
import java.util.Locale

class EditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditBinding
    private val viewModel: StudentFormViewModel by viewModels()
    private var prefilled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main, applyTop = false)
        setupToolbar(binding.toolbar)
        title = getString(R.string.edit_student)
        // بعد تدوير الشاشة يحتفظ النظام بما كتبه المستخدم، فلا نعيد التعبئة
        prefilled = savedInstanceState != null

        binding.monthlyFeeLayoutEdit.showCurrency()
        // بعد تدوير الشاشة تبقى القيم المكتوبة، فنكتفي بتفعيل الحقول؛ وإلا تُعبّأ من بيانات الطالب عند تحميلها
        if (savedInstanceState != null) binding.discount.setup(binding.edtMonthlyFeeEdit, StudentDiscount.NONE)
        binding.edtBirthYearEdit.setOnClickListener {
            showYearPicker { binding.edtBirthYearEdit.setText(it.toString()) }
        }
        binding.edtRegistrationDateEdit.setOnClickListener {
            showDatePicker { binding.edtRegistrationDateEdit.setText(it) }
        }
        binding.edtCountryCode.setupCountryCodePicker(ReminderPrefs.countryCode(this))
        binding.btnEditStudent.setOnClickListener { confirmSave() }

        collectWhileStarted(viewModel.existing) { student ->
            if (student == null || prefilled) return@collectWhileStarted
            prefilled = true
            binding.edtStudentNameEdit.setText(student.name)
            binding.edtBirthYearEdit.setText(student.birthYear)
            binding.edtRegistrationDateEdit.setText(student.registrationDate)
            if (student.monthlyFee > 0) {
                binding.edtMonthlyFeeEdit.setText(
                    Money.format(student.monthlyFee, Locale.US).replace(",", "")
                )
            }
            binding.edtPhoneNumberEdit.setText(student.phoneNumber)
            // الطلاب القدامى بلا رمز خاص: يظهر الرمز الافتراضي، ويُحفظ لهم عند الحفظ
            binding.edtCountryCode.setText(countryCodeFor(student), false)
            binding.edtNotesEdit.setText(student.notes)
            binding.discount.setup(binding.edtMonthlyFeeEdit, student.discount)
        }
        collectWhileStarted(viewModel.loading) { binding.btnEditStudent.isEnabled = !it }
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

    private fun confirmSave() {
        val name = binding.edtStudentNameEdit.requireText() ?: return
        val birthYear = binding.edtBirthYearEdit.requireText() ?: return
        // اختياري هنا: الطلاب المضافون قبل هذه الميزة لا تاريخ تسجيل لهم
        val registrationDate = binding.edtRegistrationDateEdit.text?.toString()?.trim().orEmpty()
        val monthlyFee = binding.edtMonthlyFeeEdit.optionalAmount() ?: return
        // الرسوم الشهرية تُحسب من يوم التسجيل، فلا تُفعَّل بدونه
        if (monthlyFee > 0 && registrationDate.isEmpty()) {
            binding.edtRegistrationDateEdit.error = getString(R.string.registration_date_required_for_fee)
            toast(R.string.registration_date_required_for_fee)
            return
        }
        val phone = binding.edtPhoneNumberEdit.requireText() ?: return
        val countryCode = binding.edtCountryCode.requireCountryCode(binding.countryCodeLayout) ?: return
        val discount = binding.discount.readDiscount(monthlyFee) ?: return
        val notes = binding.edtNotesEdit.text?.toString()?.trim().orEmpty()

        // إدخال الرسوم الشهرية لأول مرة: نسأل عن احتساب الأشهر السابقة منذ التسجيل
        val today = todaySimpleDate()
        if (shouldAskToBackdate(viewModel.existing.value, monthlyFee, registrationDate, today)) {
            val months = pastDuesSinceRegistration(registrationDate, today).size
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backdate_title)
                .setMessage(
                    getString(
                        R.string.backdate_message,
                        registrationDate,
                        resources.getQuantityString(R.plurals.months_count, months, months),
                        money(Money.sum(List(months) { discount.netFee(monthlyFee) }))
                    )
                )
                .setPositiveButton(R.string.backdate_yes) { _, _ ->
                    viewModel.save(
                        StudentInput(name, birthYear, registrationDate, monthlyFee, phone, countryCode, notes, discount = discount, backdateMonthlyFees = true)
                    )
                }
                .setNegativeButton(R.string.backdate_no) { _, _ ->
                    viewModel.save(StudentInput(name, birthYear, registrationDate, monthlyFee, phone, countryCode, notes, discount = discount))
                }
                .setNeutralButton(R.string.cancel, null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_edit_title)
            .setMessage(R.string.confirm_edit_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.save(StudentInput(name, birthYear, registrationDate, monthlyFee, phone, countryCode, notes, discount = discount))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
