package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.irdev.talibpay.R
import com.irdev.talibpay.data.requestGoogleIdToken
import com.irdev.talibpay.databinding.ActivityLoginBinding
import com.irdev.talibpay.util.LoadingDialog
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.errorMessageRes
import com.irdev.talibpay.util.isUserCancellation
import com.irdev.talibpay.util.requireRawText
import com.irdev.talibpay.util.requireText
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.viewmodel.AuthViewModel
import com.irdev.talibpay.viewmodel.UiEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        applySystemBarInsets(binding.main)

        if (viewModel.hasActiveSession()) {
            openMain()
            return
        }

        val loading = LoadingDialog(this)
        collectWhileStarted(viewModel.loading) { loading.setVisible(it) }
        collectWhileStarted(viewModel.events, ::onEvent)

        binding.textSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.forgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
        binding.btnSignIn.setOnClickListener { signInWithEmail() }
        binding.btnGoogleSignIn.setOnClickListener { signInWithGoogle() }
    }

    private fun signInWithEmail() {
        val email = binding.edtEmail.requireText() ?: return
        val password = binding.edtPassword.requireRawText() ?: return
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.edtEmail.error = getString(R.string.check_email)
            return
        }
        viewModel.signInWithEmail(email, password)
    }

    private fun signInWithGoogle() {
        lifecycleScope.launch {
            try {
                viewModel.signInWithGoogle(requestGoogleIdToken(this@LoginActivity))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!e.isUserCancellation()) toast(errorMessageRes(e))
            }
        }
    }

    private fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.SignedIn -> {
                val message = if (event.displayName != null)
                    "${getString(R.string.login_successfully_google_account)} ${event.displayName}"
                else
                    getString(R.string.login_successfully_email)
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                openMain()
            }

            is UiEvent.Message -> toast(event.res, long = true)
            else -> Unit
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
