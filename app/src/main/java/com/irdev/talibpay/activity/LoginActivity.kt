package com.irdev.talibpay.activity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.irdev.talibpay.R
import com.irdev.talibpay.data.addGoogleAccountIntent
import com.irdev.talibpay.data.requestGoogleIdToken
import com.irdev.talibpay.databinding.ActivityLoginBinding
import com.irdev.talibpay.util.LoadingDialog
import com.irdev.talibpay.util.ToastKind
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.errorMessageRes
import com.irdev.talibpay.util.isUserCancellation
import com.irdev.talibpay.util.requireRawText
import com.irdev.talibpay.util.requireText
import com.irdev.talibpay.util.showToast
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.viewmodel.AuthViewModel
import com.irdev.talibpay.viewmodel.UiEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()
    private var googleRequestInProgress = false

    // نتيجة شاشة إضافة الحساب غير موثوقة على كل الأجهزة، لذا نعيد المحاولة دائمًا عند العودة
    private val addGoogleAccount =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { signInWithGoogle() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
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
        // اختيار الحساب مفتوح بالفعل: نتجاهل النقر المتكرر
        if (googleRequestInProgress) return
        googleRequestInProgress = true
        lifecycleScope.launch {
            try {
                viewModel.signInWithGoogle(requestGoogleIdToken(this@LoginActivity))
            } catch (e: CancellationException) {
                throw e
            } catch (_: NoCredentialException) {
                showAddGoogleAccountDialog()
            } catch (e: Exception) {
                if (!e.isUserCancellation()) toast(errorMessageRes(e), ToastKind.ERROR, long = true)
            } finally {
                googleRequestInProgress = false
            }
        }
    }

    /** لا يوجد حساب Google على الجهاز: نفتح شاشة النظام لإضافته ثم نعيد المحاولة. */
    private fun showAddGoogleAccountDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.no_google_account_found)
            .setMessage(R.string.google_add_account_message)
            .setPositiveButton(R.string.google_add_account) { _, _ ->
                try {
                    addGoogleAccount.launch(addGoogleAccountIntent())
                } catch (_: ActivityNotFoundException) {
                    toast(R.string.google_sign_in_failed, ToastKind.ERROR)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.SignedIn -> {
                val message = if (event.displayName != null)
                    "${getString(R.string.login_successfully_google_account)} ${event.displayName}"
                else
                    getString(R.string.login_successfully_email)
                showToast(message, ToastKind.SUCCESS)
                openMain()
            }

            is UiEvent.Message -> toast(event)
            else -> Unit
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
