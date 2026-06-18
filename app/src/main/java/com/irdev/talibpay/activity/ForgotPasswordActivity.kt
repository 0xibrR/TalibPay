package com.irdev.talibpay.activity

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.irdev.talibpay.R

class ForgotPasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        massage()

        val btnBake = findViewById<ImageView>(R.id.i_btn_back_ForgotPassword)
        val btnForgot = findViewById<Button>(R.id.btn_forgot)



        btnBake.setOnClickListener {
            finish()
        }

        btnForgot.setOnClickListener {
            forgotPassword(it)
        }


    }

    @SuppressLint("UseKtx", "InflateParams")
    private fun forgotPassword(it: View) {

        val dialogView = LayoutInflater.from(this).inflate(R.layout.progress_bar_dialog, null)
        val builder = Dialog(this)
        builder.setContentView(dialogView)
        builder.setCancelable(true)
        builder.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        builder.create()
        builder.setCancelable(false)
        builder.setCanceledOnTouchOutside(false)

        val edtEmail = findViewById<TextInputEditText>(R.id.edt_email_forgot)
        val email = edtEmail.text.toString()

        if (check(edtEmail) && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

            val emails = edtEmail.text.toString().trim() { it <= ' ' }

            builder.show()

            FirebaseAuth.getInstance().sendPasswordResetEmail(emails)
                .addOnCompleteListener { task ->

                    builder.dismiss()

                    if (task.isSuccessful) {
                        Toast.makeText(this, getString(R.string.email_sent), Toast.LENGTH_LONG)
                            .show()
                        finish()
                    } else {
                        Toast.makeText(this, task.exception!!.message.toString(), Toast.LENGTH_LONG)
                            .show()
                    }
                }
        } else {
            Snackbar.make(it, getString(R.string.check_email), Snackbar.LENGTH_LONG)
                .setAction("action", null).show()
        }
    }


    private fun check(edt: EditText): Boolean {
        if (edt.text.isEmpty()) {
            edt.error = getText(R.string.enter_value)
            return false
        }
        return true
    }

    @SuppressLint("UseKtx")
    private fun massage() {
        // Check if the message has already been shown
        val prefs = getPreferences(MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("isFirstLaunch", true)

        if (isFirstLaunch) {
            // Show the message
            dialogForgot()

            // Update the flag to indicate that the message has been shown
            val editor = prefs.edit()
            editor.putBoolean("isFirstLaunch", false)
            editor.apply()
        }
    }

    private fun dialogForgot() {
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.forgot_password_)
            .setMessage(R.string.dialog_forgot)

        builder.setPositiveButton(getText(R.string.ok)) { _: DialogInterface, _: Int ->

        }

        builder.create().show()

    }
}