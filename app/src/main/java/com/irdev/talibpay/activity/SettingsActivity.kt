package com.irdev.talibpay.activity

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.irdev.talibpay.R
import com.irdev.talibpay.data.AccountProvider
import com.irdev.talibpay.data.Reauthentication
import com.irdev.talibpay.data.clearGoogleCredentialState
import com.irdev.talibpay.data.requestGoogleIdToken
import com.irdev.talibpay.databinding.ActivitySettingsBinding
import com.irdev.talibpay.databinding.DialogChangePasswordBinding
import com.irdev.talibpay.databinding.DilaogEmailPasswordBinding
import com.irdev.talibpay.util.LoadingDialog
import com.irdev.talibpay.util.LanguagePrefs
import com.irdev.talibpay.util.MIN_PASSWORD_LENGTH
import com.irdev.talibpay.util.Currency
import com.irdev.talibpay.util.CurrencyPrefs
import com.irdev.talibpay.util.ThemePrefs
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.setupToolbar
import com.irdev.talibpay.util.cardDialog
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
        applySystemBarInsets(binding.settingsLayout, applyTop = false)
        setupToolbar(binding.toolbar)
        title = getString(R.string.settings)

        binding.btnDeleteAccount.setOnClickListener { confirmDeleteAccount() }
        binding.btnLogout.setOnClickListener { confirmSignOut() }
        setupThemeToggle()
        setupLanguageToggle()
        binding.btnChangePassword.setOnClickListener { showChangePasswordDialog() }
        renderCurrency()
        binding.btnCurrency.setOnClickListener { showCurrencyDialog() }

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
        // حسابات البريد بلا اسم: نُخفي سطر الاسم بدل عرضه فارغًا
        binding.tvUserName.text = user.displayName.orEmpty()
        binding.tvUserName.isVisible = !user.displayName.isNullOrBlank()
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

    /** اختيار المظهر مباشرة من مجموعة الأزرار: النظام / داكن / نهاري. */
    private fun setupThemeToggle() {
        // نحدد الاختيار الحالي قبل ربط المستمع حتى لا يُعاد تطبيق المظهر بلا داعٍ
        binding.themeToggle.check(
            when (ThemePrefs.get(this)) {
                ThemePrefs.LIGHT -> R.id.btnThemeLight
                ThemePrefs.DARK -> R.id.btnThemeDark
                else -> R.id.btnThemeSystem
            }
        )
        binding.themeToggle.addOnButtonCheckedListener { _, id, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (id) {
                R.id.btnThemeLight -> ThemePrefs.LIGHT
                R.id.btnThemeDark -> ThemePrefs.DARK
                else -> ThemePrefs.SYSTEM
            }
            if (mode != ThemePrefs.get(this)) ThemePrefs.set(this, mode)
        }
    }

    private fun renderCurrency() {
        val currency = CurrencyPrefs.current(this)
        binding.btnCurrency.text = getString(R.string.currency_item, currency.symbol, getString(currency.nameRes))
    }

    /** العملة التي تُعرض مع كل المبالغ؛ تغييرها لا يحوّل المبالغ المحفوظة، بل رمزها فقط. */
    private fun showCurrencyDialog() {
        val currencies = Currency.entries
        val labels = currencies.map { getString(R.string.currency_item, it.symbol, getString(it.nameRes)) }
        val selected = currencies.indexOf(CurrencyPrefs.current(this))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_currency)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                CurrencyPrefs.set(this, currencies[which])
                renderCurrency()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** اختيار لغة التطبيق: النظام / العربية / English. */
    private fun setupLanguageToggle() {
        binding.languageToggle.check(
            when (LanguagePrefs.get()) {
                LanguagePrefs.ARABIC -> R.id.btnLangArabic
                LanguagePrefs.ENGLISH -> R.id.btnLangEnglish
                else -> R.id.btnLangSystem
            }
        )
        binding.languageToggle.addOnButtonCheckedListener { _, id, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val language = when (id) {
                R.id.btnLangArabic -> LanguagePrefs.ARABIC
                R.id.btnLangEnglish -> LanguagePrefs.ENGLISH
                else -> LanguagePrefs.SYSTEM
            }
            // يعيد رسم الشاشات تلقائيًا باللغة الجديدة
            if (language != LanguagePrefs.get()) LanguagePrefs.set(language)
        }
    }

    // ---------------- حذف الحساب ----------------

    private fun confirmDeleteAccount() {
        MaterialAlertDialogBuilder(this)
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
        val dialog = cardDialog(dialogBinding.root, cancelable = false)
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
        val dialog = cardDialog(dialogBinding.root, cancelable = false)

        // حساب Google بلا كلمة مرور: نربط كلمة مرور جديدة بدون طلب القديمة
        val hasPassword = viewModel.hasPassword()
        dialogBinding.oldPasswordLayout.isVisible = hasPassword

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

    override fun onDestroy() {
        openDialog?.dismiss()
        super.onDestroy()
    }
}
