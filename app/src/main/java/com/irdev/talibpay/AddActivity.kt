package com.irdev.talibpay

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
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        title = getString(R.string.add_student)

        val edtStudentName = findViewById<EditText>(R.id.edtStudentName)
        val edtBirthYear = findViewById<EditText>(R.id.edtBirthYear)
        val edtPhoneNumber = findViewById<EditText>(R.id.edtPhoneNumber)
        val edtNotes = findViewById<EditText>(R.id.edtNotes)
        val btnAddStudent = findViewById<MaterialButton>(R.id.btnAddStudent)

        edtBirthYear.setOnClickListener {
            showYearPicker(edtBirthYear)
        }

        btnAddStudent.setOnClickListener { view ->
            if (check(edtStudentName) && check(edtBirthYear) && check(edtPhoneNumber)) {

                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    Toast.makeText(this, getString(R.string.user_not_found), Toast.LENGTH_SHORT)
                        .show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    return@setOnClickListener
                }

                val userId = currentUser.uid
                val databaseRef =
                    FirebaseDatabase.getInstance().getReference("TalibPay").child(userId)

                val studentName = edtStudentName.text.toString().trim()
                val birthYear = edtBirthYear.text.toString().trim()
                val phoneNumber = edtPhoneNumber.text.toString().trim()
                val notes = edtNotes.text.toString().trim()
                val newStudentId = databaseRef.child("students").push().key

                if (newStudentId != null) {
                    val student = Student(
                        id = newStudentId,
                        name = studentName,
                        birthYear = birthYear,
                        phoneNumber = phoneNumber,
                        notes = notes,
                        lastModified = getCurrentTime(),
                        addedIn = getCurrentTime()
                    )

                    databaseRef.child("students").child(newStudentId).setValue(student)

                        .addOnSuccessListener {
                            Snackbar.make(
                                view,
                                getString(R.string.added_successfully),
                                Snackbar.LENGTH_LONG
                            ).setAnchorView(R.id.btnAddStudent).show()

                            edtStudentName.text?.clear()
                            edtBirthYear.text?.clear()
                            edtPhoneNumber.text?.clear()
                            edtNotes.text?.clear()
                        }
                        .addOnFailureListener {
                            Snackbar.make(
                                view,
                                getString(R.string.added_failed),
                                Snackbar.LENGTH_LONG
                            )
                                .setAnchorView(R.id.btnAddStudent).show()
                        }
                }
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

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_year_of_birth))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                editText.setText(numberPicker.value.toString())
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.show()
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
