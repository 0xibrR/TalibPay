package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.irdev.talibpay.R
import com.irdev.talibpay.data.StudentInput
import com.irdev.talibpay.databinding.ActivityEditBinding
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.requireText
import com.irdev.talibpay.util.showYearPicker
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.viewmodel.StudentFormViewModel
import com.irdev.talibpay.viewmodel.UiEvent

class EditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditBinding
    private val viewModel: StudentFormViewModel by viewModels()
    private var prefilled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main)
        title = getString(R.string.edit_student)
        // بعد تدوير الشاشة يحتفظ النظام بما كتبه المستخدم، فلا نعيد التعبئة
        prefilled = savedInstanceState != null

        binding.edtBirthYearEdit.setOnClickListener {
            showYearPicker { binding.edtBirthYearEdit.setText(it.toString()) }
        }
        binding.btnEditStudent.setOnClickListener { confirmSave() }

        collectWhileStarted(viewModel.existing) { student ->
            if (student == null || prefilled) return@collectWhileStarted
            prefilled = true
            binding.edtStudentNameEdit.setText(student.name)
            binding.edtBirthYearEdit.setText(student.birthYear)
            binding.edtPhoneNumberEdit.setText(student.phoneNumber)
            binding.edtNotesEdit.setText(student.notes)
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
        val phone = binding.edtPhoneNumberEdit.requireText() ?: return
        val notes = binding.edtNotesEdit.text?.toString()?.trim().orEmpty()

        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_edit_title)
            .setMessage(R.string.confirm_edit_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.save(StudentInput(name, birthYear, phone, notes))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
