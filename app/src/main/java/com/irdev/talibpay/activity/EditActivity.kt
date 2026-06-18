package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.irdev.talibpay.R
import com.irdev.talibpay.model.Student
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EditActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_edit)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        title = getString(R.string.edit_student)

        val edtStudentName = findViewById<TextInputEditText>(R.id.edtStudentNameEdit)
        val edtBirthYear = findViewById<TextInputEditText>(R.id.edtBirthYearEdit)
        val edtPhoneNumber = findViewById<TextInputEditText>(R.id.edtPhoneNumberEdit)
        val edtNotes = findViewById<TextInputEditText>(R.id.edtNotesEdit)
        val btnEditStudent = findViewById<MaterialButton>(R.id.btnEditStudent)

        val intent = intent
        val studentId = intent.getStringExtra("id") ?: ""
        val name = intent.getStringExtra("name") ?: ""
        val birthYear = intent.getStringExtra("birthYear") ?: ""
        val phoneNumber = intent.getStringExtra("phoneNumber") ?: ""
        val notes = intent.getStringExtra("notes") ?: ""
        val addedIn = intent.getStringExtra("addedIn") ?: ""

        edtStudentName.setText(name)
        edtBirthYear.setText(birthYear)
        edtPhoneNumber.setText(phoneNumber)
        val userNoteOnly = notes
            .lineSequence()
            .drop(1) // يحذف السطر الأول
            .joinToString("\n")
            .trim()

        edtNotes.setText(userNoteOnly)


        edtBirthYear.setOnClickListener {
            showYearPicker(edtBirthYear)
        }

        btnEditStudent.setOnClickListener { view ->


            if (check(edtStudentName) && check(edtBirthYear) && check(edtPhoneNumber)) {

                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.confirm_edit_title))
                    .setMessage(getString(R.string.confirm_edit_message))
                    .setPositiveButton(getString(R.string.ok)) { _, _ ->

                        val currentUser = FirebaseAuth.getInstance().currentUser
                        if (currentUser == null) {
                            Toast.makeText(
                                this,
                                getString(R.string.user_not_found),
                                Toast.LENGTH_SHORT
                            ).show()
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                            return@setPositiveButton
                        }

                        val userId = currentUser.uid
                        val databaseRef =
                            FirebaseDatabase.getInstance().getReference("TalibPay").child(userId)

                        val updatedName = edtStudentName.text.toString().trim()
                        val updatedBirthYear = edtBirthYear.text.toString().trim()
                        val updatedPhoneNumber = edtPhoneNumber.text.toString().trim()


                        val student = Student(
                            id = studentId,
                            name = updatedName,
                            birthYear = updatedBirthYear,
                            phoneNumber = updatedPhoneNumber,
                            lastModified = getCurrentTime(),
                            addedIn = addedIn
                        )

                        databaseRef.child("students").child(studentId).setValue(student)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    this,
                                    getString(R.string.updated_successfully),
                                    Toast.LENGTH_SHORT
                                ).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    this,
                                    getString(R.string.updated_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }
    }

    private fun showYearPicker(editText: EditText) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        val dialogView = layoutInflater.inflate(R.layout.dialog_year_picker, null)
        val numberPicker = dialogView.findViewById<NumberPicker>(R.id.yearPicker)

        numberPicker.minValue = 1980
        numberPicker.maxValue = currentYear
        numberPicker.value = currentYear

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_year_of_birth))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                editText.setText(numberPicker.value.toString())
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .show()
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("E, MMM dd, yyyy hh:mm:ss a", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun check(editText: EditText): Boolean {
        if (editText.text.isEmpty()) {
            editText.error = getString(R.string.enter_value)
            return false
        }
        return true
    }
}
