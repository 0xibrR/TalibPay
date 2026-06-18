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
import com.irdev.talibpay.model.Student
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

                val userId = currentUser.uid
                val studentId = intent.getStringExtra("id") ?: return@setOnClickListener
                val dbRef = FirebaseDatabase.getInstance()
                    .getReference("TalibPay")
                    .child(userId)
                    .child("students")
                    .child(studentId)

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

                dbRef.child("payments").child(paymentId).setValue(payment)

                    .addOnSuccessListener {
                        dbRef.get().addOnSuccessListener { snapshot ->
                            val student = snapshot.getValue(Student::class.java)
                            if (student != null) {

                                //1.       100                 0          100
                                //2.        200                 100          100
                                val newPaidCash = student.paidCash + paidCashValue

                                //1.         100                 0          100
                                //2.        100                 100         0
                                val newPaidBank = student.paidBank + paidBankValue

                                //1.     200          100             100
                                //2.     300          200             100
                                val newPaid = newPaidCash + newPaidBank

                                // ✅ استخدم القيمة الأكبر بين المدخل والموجود
                                val studentTotal = maxOf(student.total, totalValue)

                                // ✅ نضمن دائماً أن المتبقي لا يكون بالسالب
                                val newRemaining = (studentTotal - newPaid).coerceAtLeast(0.0)

                                val updates = mapOf<String, Any>(
                                    "paidCash" to newPaidCash,  // 100
                                    "paidBank" to newPaidBank,  // 100
                                    "paid" to newPaid,
                                    "total" to studentTotal,    // 200
                                    "remaining" to remaining, // ✅ دائماً صفر أو أكثر
                                    "lastModified" to getCurrentTime()
                                )

                                dbRef.updateChildren(updates)
                                    .addOnSuccessListener {
                                        Toast.makeText(
                                            this,
                                            "تمت إضافة الدفعة وتحديث بيانات الطالب",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        startActivity(Intent(this, MainActivity::class.java))
                                        finish()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(
                                            this,
                                            "حدث خطأ أثناء تحديث بيانات الطالب",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                        }.addOnFailureListener {
                            Toast.makeText(this, "تعذر قراءة بيانات الطالب", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "فشل في إضافة الدفعة", Toast.LENGTH_SHORT).show()
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