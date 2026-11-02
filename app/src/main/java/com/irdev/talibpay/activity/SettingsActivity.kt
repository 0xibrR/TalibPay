package com.irdev.talibpay.activity

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.irdev.talibpay.R
import com.irdev.talibpay.data.AccountProvider
import com.irdev.talibpay.data.Reauthentication
import com.irdev.talibpay.data.clearGoogleCredentialState
import com.irdev.talibpay.data.requestGoogleIdToken
import com.irdev.talibpay.databinding.ActivitySettingsBinding
import com.irdev.talibpay.databinding.AlertDialogThemeSelectBinding
import com.irdev.talibpay.databinding.DialogChangePasswordBinding
import com.irdev.talibpay.databinding.DilaogEmailPasswordBinding
import com.irdev.talibpay.util.LoadingDialog
import com.irdev.talibpay.util.MIN_PASSWORD_LENGTH
import com.irdev.talibpay.util.ThemePrefs
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.errorMessageRes
import com.irdev.talibpay.util.isUserCancellation
import com.irdev.talibpay.util.requireRawText
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.viewmodel.SettingsViewModel
import com.irdev.talibpay.viewmodel.UiEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()

    // النافذة المفتوحة حاليًا (تغيير كلمة المرور / تأكيد كلمة المرور) لإغلاقها عند النجاح
    private var openDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.settingsLayout)
        title = getString(R.string.settings)

        binding.btnDeleteAccount.setOnClickListener { confirmDeleteAccount() }
        binding.btnLogout.setOnClickListener { confirmSignOut() }
        binding.btnToggleTheme.setOnClickListener { showThemeDialog() }
        binding.btnChangePassword.setOnClickListener { showChangePasswordDialog() }

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        binding.tvVersion.text = getString(R.string.app_version_placeholder, versionName)

        loadProfile()

        val loading = LoadingDialog(this)
        collectWhileStarted(viewModel.loading) { loading.setVisible(it) }
        collectWhileStarted(viewModel.events) { event ->
            when (event) {
                // Toast لأن الرسالة قد تظهر فوق نافذة حوار مفتوحة
                is UiEvent.Message -> toast(event.res, long = true)
                UiEvent.Finished -> {
                    openDialog?.dismiss()
                    openDialog = null
                }

                UiEvent.SignedOut, UiEvent.NotSignedIn -> goToLogin()
                else -> Unit
            }
        }
    }

    private fun loadProfile() {
        val user = viewModel.user
        if (user == null) {
            goToLogin()
            return
        }
        binding.tvUserName.text = user.displayName ?: getString(R.string.user_name)
        binding.tvUserEmail.text = user.email ?: getString(R.string.user_email_com)
        Glide.with(this)
            .load(user.photoUrl)
            .placeholder(R.drawable.outline_account_circle_24)
            .error(R.drawable.outline_account_circle_24)
            .into(binding.imgAvatar)
    }

    private fun goToLogin() {
        lifecycleScope.launch {
            clearGoogleCredentialState(applicationContext)
            startActivity(Intent(this@SettingsActivity, LoginActivity::class.java))
            finishAffinity()
        }
    }

    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sign_out)
            .setMessage(R.string.signed_out_message)
            .setPositiveButton(R.string.ok) { _, _ -> viewModel.signOut() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showThemeDialog() {
        val dialogBinding = AlertDialogThemeSelectBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.deafultRadioButton.setText(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) R.string.system_default
            else R.string.follow_battery_saver
        )
        when (ThemePrefs.get(this)) {
            ThemePrefs.LIGHT -> dialogBinding.lightRadioButton.isChecked = true
            ThemePrefs.DARK -> dialogBinding.darkRadioButton.isChecked = true
            else -> dialogBinding.deafultRadioButton.isChecked = true
        }
        dialogBinding.themeButtonGroup.setOnCheckedChangeListener { _, id ->
            ThemePrefs.set(
                this, when (id) {
                    R.id.lightRadioButton -> ThemePrefs.LIGHT
                    R.id.darkRadioButton -> ThemePrefs.DARK
                    else -> ThemePrefs.SYSTEM
                }
            )
            dialog.dismiss()
        }
        dialog.show()
    }

    // ---------------- حذف الحساب ----------------

    private fun confirmDeleteAccount() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_account)
            .setMessage(R.string.delete_account_message)
            .setPositiveButton(R.string.delete_account) { _, _ ->
                when (viewModel.accountProvider()) {
                    AccountProvider.GOOGLE -> deleteGoogleAccount()
                    AccountProvider.PASSWORD -> showPasswordReauthDialog()
                    AccountProvider.UNKNOWN -> toast(R.string.account_provider_type_unknown)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(false)
            .show()
    }

    /** إعادة التحقق بحساب Google برمز جديد (الرمز القديم ينتهي خلال ساعة). */
    private fun deleteGoogleAccount() {
        lifecycleScope.launch {
            try {
                val token = try {
                    requestGoogleIdToken(this@SettingsActivity, authorizedOnly = true)
                } catch (_: NoCredentialException) {
                    requestGoogleIdToken(this@SettingsActivity, authorizedOnly = false)
                }
                viewModel.deleteAccount(Reauthentication.Google(token))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!e.isUserCancellation()) toast(errorMessageRes(e))
            }
        }
    }

    private fun showPasswordReauthDialog() {
        val dialogBinding = DilaogEmailPasswordBinding.inflate(layoutInflater)
        val dialog = Dialog(this).apply {
            setContentView(dialogBinding.root)
            setCancelable(false)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        // البريد هو بريد الحساب الحالي دائمًا
        dialogBinding.edtEmailDialog.setText(viewModel.user?.email)
        dialogBinding.edtEmailDialog.isEnabled = false

        dialogBinding.btnOkDialog.setOnClickListener {
            val password = dialogBinding.edtPasswordDialog.requireRawText() ?: return@setOnClickListener
            viewModel.deleteAccount(Reauthentication.Password(password))
        }
        dialogBinding.btnCancelDialog.setOnClickListener { dialog.dismiss() }
        openDialog = dialog
        dialog.show()
    }

    // ---------------- كلمة المرور ----------------

    private fun showChangePasswordDialog() {
        val dialogBinding = DialogChangePasswordBinding.inflate(layoutInflater)
        val dialog = Dialog(this).apply {
            setContentView(dialogBinding.root)
            setCancelable(false)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        // حساب Google بلا كلمة مرور: نربط كلمة مرور جديدة بدون طلب القديمة
        val hasPassword = viewModel.hasPassword()
        dialogBinding.edtOldPassword.textInputLayout()?.isVisible = hasPassword
        dialogBinding.edtOldPassword.isVisible = hasPassword

        dialogBinding.btnSaveChangePassword.setOnClickListener {
            val oldPass = dialogBinding.edtOldPassword.text?.toString().orEmpty()
            val newPass = dialogBinding.edtNewPassword.text?.toString().orEmpty()
            val confirm = dialogBinding.edtConfirmPassword.text?.toString().orEmpty()

            when {
                newPass.isEmpty() || confirm.isEmpty() || (hasPassword && oldPass.isEmpty()) ->
                    Snackbar.make(dialogBinding.root, R.string.fill_all_fields, Snackbar.LENGTH_LONG).show()

                newPass.length < MIN_PASSWORD_LENGTH ->
                    dialogBinding.edtNewPassword.error = getString(R.string.password_too_short)

                newPass != confirm ->
                    dialogBinding.edtConfirmPassword.error = getString(R.string.passwords_not_match)

                else -> viewModel.changePassword(oldPass.takeIf { hasPassword }, newPass)
            }
        }
        dialogBinding.btnCancelChangePassword.setOnClickListener { dialog.dismiss() }
        openDialog = dialog
        dialog.show()
    }

    private fun View.textInputLayout(): TextInputLayout? =
        generateSequence(parent) { it.parent }.filterIsInstance<TextInputLayout>().firstOrNull()

    override fun onDestroy() {
        openDialog?.dismiss()
        super.onDestroy()
    }
}
