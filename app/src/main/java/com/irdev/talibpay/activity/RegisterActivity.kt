package com.irdev.talibpay.activity

import android.os.Bundle
import android.util.Patterns
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ActivityRegisterBinding
import com.irdev.talibpay.util.LoadingDialog
import com.irdev.talibpay.util.MIN_PASSWORD_LENGTH
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.requireRawText
import com.irdev.talibpay.util.requireText
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.viewmodel.AuthViewModel
import com.irdev.talibpay.viewmodel.UiEvent

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        applySystemBarInsets(binding.main)

        val loading = LoadingDialog(this)
        collectWhileStarted(viewModel.loading) { loading.setVisible(it) }
        collectWhileStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message -> toast(event.res, long = true)
                UiEvent.Finished -> finish()
                else -> Unit
            }
        }

        binding.iBtnBackRegister.setOnClickListener { finish() }
        binding.btnRegister.setOnClickListener { signUp() }
    }

    private fun signUp() {
        val email = binding.edtEmailRegister.requireText() ?: return
        val password = binding.edtPasswordRegister.requireRawText() ?: return
        val confirm = binding.edtConfirmPasswordRegister.requireRawText() ?: return

        when {
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                binding.edtEmailRegister.error = getString(R.string.check_email)

            password.length < MIN_PASSWORD_LENGTH ->
                binding.edtPasswordRegister.error = getString(R.string.error_weak_password)

            password != confirm ->
                binding.edtConfirmPasswordRegister.error = getString(R.string.Password_does_not_match)

            else -> viewModel.register(email, password)
        }
    }
}
