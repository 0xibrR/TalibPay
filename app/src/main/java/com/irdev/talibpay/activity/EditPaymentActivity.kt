package com.irdev.talibpay.activity

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EditPaymentActivity : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_edit_payment)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        title = getString(R.string.edit_pyment)

        val id = intent.getStringExtra("id") ?: ""
        val cash = intent.getDoubleExtra("cash", 0.0)
        val bank = intent.getDoubleExtra("bank", 0.0)
        val total = intent.getDoubleExtra("total", 0.0)
        val paymentMethod = intent.getStringExtra("paymentMethod") ?: ""
        val paymentDate = intent.getStringExtra("paymentDate") ?: ""
        val note = intent.getStringExtra("note") ?: ""


        val edtPaidCash = findViewById<TextInputEditText>(R.id.edtPaidCash_edit)
        val edtPaidBank = findViewById<TextInputEditText>(R.id.edtPaidBank_edit)
        val edtTotal = findViewById<TextInputEditText>(R.id.edtTotal_edit)
        val spinnerPaymentMethod = findViewById<Spinner>(R.id.spinnerPaymentMethod_edit)
        val edtPaymentDate = findViewById<TextInputEditText>(R.id.edtPaymentDate_edit)
        val edtNotesPayment = findViewById<TextInputEditText>(R.id.edtNotes_Payment_edit)
        val btnEditPayment = findViewById<MaterialButton>(R.id.btnEditPayment)

        edtPaidCash.setText(cash.toString())
        edtPaidBank.setText(bank.toString())
        edtTotal.setText(total.toString())
        edtPaymentDate.setText(paymentDate)
        edtNotesPayment.setText(note)

        val paymentMethods = resources.getStringArray(R.array.payment_methods)
        val index = paymentMethods.indexOf(paymentMethod)
        if (index >= 0) {
            spinnerPaymentMethod.setSelection(index)
        } else {
            spinnerPaymentMethod.setSelection(0) // الخيار الافتراضي "اختر طريقة الدفع"
        }


        btnEditPayment.setOnClickListener {
            val paidCashStr = edtPaidCash.text?.toString()?.trim().orEmpty()
            val paidBankStr = edtPaidBank.text?.toString()?.trim().orEmpty()
            val totalStr = edtTotal.text?.toString()?.trim().orEmpty()
            val paymentDate = edtPaymentDate.text?.toString()?.trim().orEmpty()
            val selectedPaymentMethod = spinnerPaymentMethod.selectedItem?.toString().orEmpty()

            // التحقق من اختيار طريقة الدفع
            if (selectedPaymentMethod == getString(R.string.select_payment_method)) {
                Toast.makeText(
                    this,
                    getString(R.string.please_choose_payment_method),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // التحقق من الحقول
            val ok =
                check(edtPaidCash) && check(edtPaidBank) && check(edtTotal) && check(edtPaymentDate)
            if (!ok) return@setOnClickListener

            // التحويل لأرقام
            val paidCash = paidCashStr.toDoubleOrNull() ?: 0.0
            val paidBank = paidBankStr.toDoubleOrNull() ?: 0.0
            val totalVal =
                if (totalStr.isNotEmpty()) totalStr.toDoubleOrNull() ?: (paidCash + paidBank)
                else (paidCash + paidBank)

            val paidVal = paidCash + paidBank
            val remainingVal = totalVal - paidVal

            val studentId = intent.getStringExtra("studentId") ?: ""
            val paymentId = intent.getStringExtra("id") ?: ""
            val note = edtNotesPayment.text?.toString().orEmpty()

            val user = FirebaseAuth.getInstance().currentUser
            when {
                user == null -> {
                    Toast.makeText(this, getString(R.string.user_not_found), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    return@setOnClickListener
                }

                studentId.isEmpty() -> {
                    Toast.makeText(this, "لم يتم تمرير معرف الطالب", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                paymentId.isEmpty() -> {
                    Toast.makeText(this, "لم يتم تمرير معرف الدفعة", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }


            // تكوين كائن الدفعة الجديد
            val updatedPayment = Payment(
                id = paymentId,
                cash = paidCash,
                bank = paidBank,
                paid = paidVal,
                total = totalVal,
                remaining = remainingVal,
                paymentMethod = selectedPaymentMethod,
                paymentDate = paymentDate,
                note = note
            )

            // التحديث في Firebase
            FirebaseDatabase.getInstance().getReference("TalibPay")
                .child(user.uid)
                .child("students")
                .child(studentId)
                .child("payments")
                .child(paymentId)
                .setValue(updatedPayment)
                .addOnSuccessListener {
                    Toast.makeText(
                        this,
                        getString(R.string.updated_successfully),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish() // رجوع لصفحة التفاصيل
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "${getString(R.string.updated_failed)} ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("E, MMM dd, yyyy hh:mm:ss a", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun showDatePickerDialog(editText: EditText) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_YEAR)

        val datePickerDialog =
            DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate =
                    "%02d/%02d/%04d".format(selectedDay, selectedMonth + 1, selectedYear)
                editText.setText(formattedDate)
            }, year, month, day)

        datePickerDialog.show()
    }

    private fun check(editText: EditText): Boolean {
        if (editText.text.isEmpty()) {
            editText.error = getString(R.string.enter_value)
            return false
        }
        return true
    }
}