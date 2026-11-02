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
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.R
import com.irdev.talibpay.data.recalculateStudentTotals
import com.irdev.talibpay.data.studentRef
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddPayment : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_payment)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        title = getString(R.string.add_payment)


        val edtPaidCash = findViewById<TextInputEditText>(R.id.edtPaidCash)
        val edtPaidBank = findViewById<TextInputEditText>(R.id.edtPaidBank)
        val edtTotal = findViewById<TextInputEditText>(R.id.edtTotal)
        val spinnerPaymentMethod = findViewById<Spinner>(R.id.spinnerPaymentMethod)
        val edtPaymentDate = findViewById<TextInputEditText>(R.id.edtPaymentDate)
        val edtNotesPayment = findViewById<TextInputEditText>(R.id.edtNotes_Payment)
        val btnAddPayment = findViewById<MaterialButton>(R.id.btnAddPayment)


        edtPaymentDate.setOnClickListener {
            showDatePickerDialog(edtPaymentDate)
        }

        btnAddPayment.setOnClickListener {
            val paidCash = edtPaidCash.text.toString().trim()
            val paidBank = edtPaidBank.text.toString().trim()
            val total = edtTotal.text.toString().trim()
            val paymentDate = edtPaymentDate.text.toString().trim()
            val selectedPaymentMethod = spinnerPaymentMethod.selectedItem.toString()
            val notes = edtNotesPayment.text.toString().trim()


            // التحقق من اختيار طريقة الدفع
            if (selectedPaymentMethod == getString(R.string.select_payment_method)) {
                Toast.makeText(
                    this,
                    getString(R.string.please_choose_payment_method),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // التحقق من المدخلات
            if (check(edtPaidCash) && check(edtPaidBank) && check(edtTotal) && check(edtPaymentDate)) {
                val paidCashValue = paidCash.toDoubleOrNull() ?: 0.0
                val paidBankValue = paidBank.toDoubleOrNull() ?: 0.0
                val totalValue = total.toDoubleOrNull() ?: 0.0
                val paidValue = paidCashValue + paidBankValue
                val remaining = totalValue - paidValue

                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    Toast.makeText(this, getString(R.string.user_not_found), Toast.LENGTH_SHORT)
                        .show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    return@setOnClickListener
                }

                if (paidValue > totalValue) {
                    Toast.makeText(
                        this,
                        getString(R.string.The_amount_paid_may_not_be_greater_than_the_full_fee),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val studentId = intent.getStringExtra("id") ?: return@setOnClickListener
                val dbRef = studentRef(currentUser.uid, studentId)

                val paymentId = dbRef.child("payments").push().key ?: return@setOnClickListener

                val payment = Payment(
                    id = paymentId,
                    cash = paidCashValue,
                    bank = paidBankValue,
                    paid = paidValue,
                    total = totalValue,
                    remaining = remaining,
                    paymentMethod = selectedPaymentMethod,
                    paymentDate = paymentDate,
                    note = "دفعة جديدة بتاريخ $paymentDate \n $notes"
                )

                // منع النقر المزدوج من إضافة الدفعة مرتين
                btnAddPayment.isEnabled = false
                dbRef.child("payments").child(paymentId).setValue(payment)
                    .addOnSuccessListener {
                        recalculateStudentTotals(dbRef, getCurrentTime()) { ok ->
                            Toast.makeText(
                                this,
                                getString(
                                    if (ok) R.string.payment_added_successfully
                                    else R.string.failed_to_update_student
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                            if (ok) {
                                startActivity(
                                    Intent(this, MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                )
                                finish()
                            } else {
                                btnAddPayment.isEnabled = true
                            }
                        }
                    }
                    .addOnFailureListener {
                        btnAddPayment.isEnabled = true
                        Toast.makeText(this, getString(R.string.failed_to_add_payment), Toast.LENGTH_SHORT).show()
                    }
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
        val day = calendar.get(Calendar.DAY_OF_MONTH)

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