package com.irdev.talibpay.activity

import android.os.Bundle
import android.util.Patterns
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.snackbar.Snackbar
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ActivityForgotPasswordBinding
import com.irdev.talibpay.util.LoadingDialog
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.requireText
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.viewmodel.AuthViewModel
import com.irdev.talibpay.viewmodel.UiEvent

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        enableEdgeToEdge()
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main)
        showIntroOnce()

        val loading = LoadingDialog(this)
        collectWhileStarted(viewModel.loading) { loading.setVisible(it) }
        collectWhileStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message -> toast(event.res, long = true)
                UiEvent.Finished -> finish()
                else -> Unit
            }
        }

        binding.iBtnBackForgotPassword.setOnClickListener { finish() }
        binding.btnForgot.setOnClickListener { view ->
            val email = binding.edtEmailForgot.requireText() ?: return@setOnClickListener
            if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                viewModel.sendPasswordReset(email)
            } else {
                Snackbar.make(view, getString(R.string.check_email), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    /** شرح الشاشة يظهر أول مرة فقط. */
    private fun showIntroOnce() {
        val prefs = getPreferences(MODE_PRIVATE)
        if (!prefs.getBoolean("isFirstLaunch", true)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.forgot_password_)
            .setMessage(R.string.dialog_forgot)
            .setPositiveButton(R.string.ok, null)
            .show()
        prefs.edit { putBoolean("isFirstLaunch", false) }
    }
}
