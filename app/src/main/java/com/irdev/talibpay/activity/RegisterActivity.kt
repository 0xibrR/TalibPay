package com.irdev.talibpay.activity

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.irdev.talibpay.R

class RegisterActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        supportActionBar?.hide()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        firebaseAuth = FirebaseAuth.getInstance()


        val btnBake = findViewById<ImageView>(R.id.i_btn_back_register)
        val btnSignUp = findViewById<Button>(R.id.btn_register)


        btnBake.setOnClickListener {
            finish()
        }

        btnSignUp.setOnClickListener {
            signUp()
        }

    }

    @SuppressLint("InflateParams", "UseKtx")
    private fun signUp() {

        val edtEmail = findViewById<TextInputEditText>(R.id.edt_email_register)
        val edtPassword = findViewById<TextInputEditText>(R.id.edt_password_register)
        val edtConfirmPassword = findViewById<TextInputEditText>(R.id.edt_confirmPassword_register)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.progress_bar_dialog, null)
        val builder = Dialog(this)
        builder.setContentView(dialogView)
        builder.setCancelable(true)
        builder.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        builder.create()
        builder.setCancelable(false)
        builder.setCanceledOnTouchOutside(false)

        firebaseAuth = FirebaseAuth.getInstance()
        val email = edtEmail.text.toString()
        val password = edtPassword.text.toString()

        if (check(edtEmail) && check(edtPassword) && check(edtConfirmPassword)) {

            if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                if (edtPassword.text.toString() == edtConfirmPassword.text.toString()) {

                    builder.show()

                    firebaseAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener {

                            builder.dismiss()

                            if (it.isSuccessful) {

                                // User registration successful, send verification email
                                val user = firebaseAuth.currentUser
                                user?.sendEmailVerification()
                                    ?.addOnCompleteListener { verificationTask ->
                                        if (verificationTask.isSuccessful) {
                                            Toast.makeText(
                                                this,
                                                getText(R.string.account_created_successfully),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            Toast.makeText(
                                                this,
                                                getString(R.string.verification_email),
                                                Toast.LENGTH_LONG
                                            ).show()

                                            // تسجيل خروج المستخدم فور إنشاء الحساب
                                            firebaseAuth.signOut()
                                            finish()
                                        } else {
                                            Toast.makeText(
                                                this,
                                                verificationTask.exception!!.message.toString(),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                            } else {
                                Toast.makeText(
                                    this,
                                    it.exception!!.message.toString(),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                } else {
                    edtConfirmPassword.error = getString(R.string.Password_does_not_match)
                }
            } else {
                edtEmail.error = getString(R.string.check_email)
            }

        }
    }


    private fun check(edt: EditText): Boolean {
        if (edt.text.isEmpty()) {
            edt.error = getString(R.string.enter_value)
            return false
        }
        return true
    }
}