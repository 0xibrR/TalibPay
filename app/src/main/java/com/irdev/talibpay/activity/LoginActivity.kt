package com.irdev.talibpay.activity

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.irdev.talibpay.R

@Suppress("DEPRECATION")
class LoginActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var progressDialog: Dialog  // متغير عام لمربع الحوار

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        supportActionBar?.hide()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // تهيئة مربع الحوار
        initProgressDialog()

        firebaseAuth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val btnSignUp = findViewById<TextView>(R.id.textSign_up)
        val btnForgotPassword = findViewById<TextView>(R.id.forgotPassword)
        val btnSignIn = findViewById<Button>(R.id.btn_sign_in)
        val googleSignIn = findViewById<SignInButton>(R.id.btnGoogleSignIn)


        btnSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        btnSignIn.setOnClickListener {
            signInWithEmail()
        }


        googleSignIn.setOnClickListener {
            signInWithGoogle()

        }

    }

    @SuppressLint("InflateParams", "UseKtx")
    private fun initProgressDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.progress_bar_dialog, null)
        progressDialog = Dialog(this)
        progressDialog.setContentView(dialogView)
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun signInWithEmail() {
        val edtEmail = findViewById<EditText>(R.id.edt_email)
        val edtPassword = findViewById<EditText>(R.id.edt_password)
        val email = edtEmail.text.toString()
        val password = edtPassword.text.toString()

        if (check(edtEmail) && check(edtPassword)) {


            if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

                progressDialog.show()  // إظهار مربع الحوار

                firebaseAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener {

                    progressDialog.dismiss()  // إخفاء مربع الحوار بعد اكتمال العملية

                    if (it.isSuccessful) {
                        val user = firebaseAuth.currentUser
                        if (user != null && user.isEmailVerified) {
                            Toast.makeText(
                                this,
                                getString(R.string.login_successfully_email),
                                Toast.LENGTH_SHORT
                            ).show()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(
                                this,
                                getString(R.string.check_verification_email),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(this, it.exception!!.message.toString(), Toast.LENGTH_LONG)
                            .show()
                    }
                }
            } else {
                edtEmail.error = getString(R.string.check_email)
            }
        }
    }

    private fun signInWithGoogle() {

        progressDialog.show()  // إظهار مربع الحوار

        val signIntent = googleSignInClient.signInIntent
        launcher.launch(signIntent)

    }

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            progressDialog.dismiss()  // إخفاء مربع الحوار بعد اكتمال العملية

            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleResults(task)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.sign_in_failed_try_again_later),
                    Toast.LENGTH_SHORT
                ).show()
            }

        }

    private fun handleResults(task: Task<GoogleSignInAccount>) {

        if (task.isSuccessful) {

            val account: GoogleSignInAccount? = task.result

            if (account != null) {

                progressDialog.show()  // إظهار مربع الحوار أثناء مصادقة Firebase

                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                firebaseAuth.signInWithCredential(credential).addOnCompleteListener {

                    progressDialog.dismiss()  // إخفاء مربع الحوار بعد انتهاء المصادقة

                    if (it.isSuccessful) {
                        Toast.makeText(
                            this,
                            "${getString(R.string.login_successfully_google_account)} ${it.result.user?.displayName}",
                            Toast.LENGTH_SHORT
                        ).show()

                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            "${getString(R.string.firebase_authentication_failed)} ${it.exception}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.sign_in_failed_try_again_later),
                    Toast.LENGTH_SHORT
                ).show()
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

    override fun onStart() {
        super.onStart()
        if (firebaseAuth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}