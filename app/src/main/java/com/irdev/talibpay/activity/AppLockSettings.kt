package com.irdev.talibpay.activity

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.DialogPinBinding
import com.irdev.talibpay.security.AppLockGate
import com.irdev.talibpay.security.BiometricKey
import com.irdev.talibpay.security.BiometricUnlock
import com.irdev.talibpay.security.LockMode
import com.irdev.talibpay.security.LockState
import com.irdev.talibpay.security.PinResult
import com.irdev.talibpay.security.PinRules
import com.irdev.talibpay.util.ToastKind
import com.irdev.talibpay.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * إعدادات الـ app lock في شاشة الإعدادات: التفعيل، وطريقة الفتح، وتغيير الـ PIN، والتعطيل.
 * كل تغيير بعد التفعيل يتطلب التحقق من الهوية (fingerprint أو الـ PIN الحالي) ويخضع لحدود المحاولات.
 */
class AppLockSettings(private val activity: AppCompatActivity, private val button: MaterialButton) {

    private val lock get() = AppLockGate.lock

    fun setup() {
        render()
        button.setOnClickListener { onClick() }
    }

    private fun render() {
        button.text = when (val state = AppLockGate.state()) {
            is LockState.Enabled -> activity.getString(R.string.app_lock_on, activity.getString(modeLabel(state.mode)))
            else -> activity.getString(R.string.app_lock_off)
        }
    }

    private fun onClick() {
        when (val state = AppLockGate.state()) {
            LockState.NotEnabled -> startEnable()
            LockState.Tampered -> AppLockGate.resetAndSignOut(activity, R.string.lock_tampered)
            is LockState.Enabled -> showOptions(state)
        }
    }

    // ---------------- Enable ----------------

    private fun startEnable() {
        val uid = AppLockGate.currentUid() ?: return
        chooseMode(current = null) { mode ->
            askNewPin(R.string.app_lock_set_pin) { pin ->
                activity.lifecycleScope.launch {
                    withContext(Dispatchers.Default) { lock.enable(uid, pin, mode) }
                    AppLockGate.markUnlocked()
                    if (mode.usesBiometric) enrollBiometric() else finishChange(R.string.app_lock_enabled)
                }
            }
        }
    }

    /** يعرض الطرق المتاحة؛ بدون fingerprint مسجّلة على الجهاز يُستخدم الـ PIN مع توضيح السبب. */
    private fun chooseMode(current: LockMode?, onChosen: (LockMode) -> Unit) {
        if (!BiometricKey.isAvailable(activity)) {
            if (!BiometricKey.hasHardware(activity)) return onChosen(LockMode.PIN)
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.app_lock_method)
                .setMessage(R.string.app_lock_no_fingerprint_enrolled)
                .setPositiveButton(R.string.app_lock_continue_with_pin) { _, _ -> onChosen(LockMode.PIN) }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        val modes = listOf(LockMode.BOTH, LockMode.BIOMETRIC, LockMode.PIN)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.app_lock_method)
            .setSingleChoiceItems(modes.map { activity.getString(modeLabel(it)) }.toTypedArray(), modes.indexOf(current)) { dialog, which ->
                dialog.dismiss()
                onChosen(modes[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** إنشاء مفتاح الـ fingerprint وتأكيده بـ fingerprint فعلية؛ عند الإلغاء يبقى الـ lock بالـ PIN. */
    private fun enrollBiometric() {
        BiometricKey.create()
        BiometricUnlock.prompt(
            activity,
            activity.getString(R.string.app_lock_confirm_fingerprint),
            activity.getString(R.string.cancel)
        ) { outcome ->
            if (outcome == BiometricUnlock.Outcome.Success) {
                lock.setBiometricEnrolled(true)
                finishChange(R.string.app_lock_enabled)
            } else {
                BiometricKey.delete()
                lock.setBiometricEnrolled(false)
                lock.setMode(LockMode.PIN)
                finishChange(R.string.app_lock_fingerprint_skipped)
            }
        }
    }

    // ---------------- After enable ----------------

    private fun showOptions(state: LockState.Enabled) {
        val options = listOf(R.string.app_lock_method, R.string.app_lock_change_pin, R.string.app_lock_disable)
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.app_lock_on, activity.getString(modeLabel(state.mode))))
            .setItems(options.map { activity.getString(it) }.toTypedArray()) { _, which ->
                when (options[which]) {
                    R.string.app_lock_method -> verifyIdentity { changeMode(state) }
                    R.string.app_lock_change_pin -> changePin()
                    R.string.app_lock_disable -> confirmDisable()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun changeMode(state: LockState.Enabled) {
        chooseMode(state.mode) { mode ->
            lock.setMode(mode)
            when {
                mode.usesBiometric && !(state.biometricEnrolled && BiometricKey.exists()) -> enrollBiometric()
                !mode.usesBiometric -> {
                    BiometricKey.delete()
                    lock.setBiometricEnrolled(false)
                    finishChange(R.string.app_lock_method_changed)
                }
                else -> finishChange(R.string.app_lock_method_changed)
            }
        }
    }

    private fun changePin() {
        val dialogBinding = DialogPinBinding.inflate(activity.layoutInflater)
        val dialog = pinDialog(dialogBinding, R.string.app_lock_change_pin, showCurrent = true, showNew = true)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val current = dialogBinding.edtCurrentPin.text?.toString().orEmpty()
            val newPin = readNewPin(dialogBinding) ?: return@setOnClickListener
            submit(dialog, dialogBinding.currentPinLayout, { lock.changePin(current, newPin) }) {
                finishChange(R.string.app_lock_pin_changed)
            }
        }
    }

    private fun confirmDisable() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.app_lock_disable)
            .setMessage(R.string.app_lock_disable_message)
            .setPositiveButton(R.string.app_lock_disable) { _, _ ->
                verifyIdentity {
                    lock.reset()
                    BiometricKey.delete()
                    finishChange(R.string.app_lock_disabled)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- Verify identity ----------------

    /** Fingerprint إن كانت متاحة (مع خيار الـ PIN)، وإلا الـ PIN الحالي. */
    private fun verifyIdentity(onVerified: () -> Unit) {
        val state = AppLockGate.state() as? LockState.Enabled ?: return
        if (AppLockGate.biometricUsable(activity, state)) {
            BiometricUnlock.prompt(
                activity,
                activity.getString(R.string.app_lock_verify),
                activity.getString(R.string.lock_use_pin)
            ) { outcome ->
                if (outcome == BiometricUnlock.Outcome.Success) {
                    lock.onBiometricSuccess()
                    onVerified()
                } else {
                    if (outcome == BiometricUnlock.Outcome.Invalidated) lock.setBiometricEnrolled(false)
                    verifyWithPin(onVerified)
                }
            }
        } else {
            verifyWithPin(onVerified)
        }
    }

    private fun verifyWithPin(onVerified: () -> Unit) {
        val dialogBinding = DialogPinBinding.inflate(activity.layoutInflater)
        val dialog = pinDialog(dialogBinding, R.string.app_lock_verify, showCurrent = true, showNew = false)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val pin = dialogBinding.edtCurrentPin.text?.toString().orEmpty()
            submit(dialog, dialogBinding.currentPinLayout, { lock.verifyPin(pin) }, onVerified)
        }
    }

    /** ينفّذ التحقق في الـ background ويعرض النتيجة على الحقل؛ تجاوز المحاولات أو الـ tampering ينهي الجلسة. */
    private fun submit(dialog: AlertDialog, field: TextInputLayout, check: () -> PinResult, onSuccess: () -> Unit) {
        val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positive.isEnabled = false
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) { check() }
            positive.isEnabled = true
            field.error = null
            when (result) {
                PinResult.Success -> {
                    dialog.dismiss()
                    onSuccess()
                }
                is PinResult.Wrong -> field.error =
                    if (result.lockoutMs > 0) activity.getString(R.string.lock_wait, formatDuration(result.lockoutMs))
                    else activity.resources.getQuantityString(
                        R.plurals.lock_wrong_pin, result.attemptsBeforeLockout, result.attemptsBeforeLockout
                    )
                is PinResult.LockedOut -> field.error = activity.getString(R.string.lock_wait, formatDuration(result.remainingMs))
                PinResult.TooManyAttempts -> {
                    dialog.dismiss()
                    AppLockGate.resetAndSignOut(activity, R.string.lock_too_many_attempts)
                }
                PinResult.Tampered -> {
                    dialog.dismiss()
                    AppLockGate.resetAndSignOut(activity, R.string.lock_tampered)
                }
            }
        }
    }

    // ---------------- PIN dialogs ----------------

    private fun askNewPin(title: Int, onPin: (String) -> Unit) {
        val dialogBinding = DialogPinBinding.inflate(activity.layoutInflater)
        val dialog = pinDialog(dialogBinding, title, showCurrent = false, showNew = true)
        dialogBinding.tvPinHint.isVisible = true
        dialogBinding.tvPinHint.setText(R.string.app_lock_pin_explained)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val pin = readNewPin(dialogBinding) ?: return@setOnClickListener
            dialog.dismiss()
            onPin(pin)
        }
    }

    /** الـ PIN الجديد بعد التحقق من قواعده ومطابقة تأكيده؛ null مع عرض الخطأ. */
    private fun readNewPin(b: DialogPinBinding): String? {
        val pin = b.edtNewPin.text?.toString().orEmpty()
        b.newPinLayout.error = when (PinRules.validate(pin)) {
            PinRules.Validation.Valid -> null
            PinRules.Validation.WrongLength, PinRules.Validation.NotDigits -> activity.getString(R.string.pin_wrong_length)
            PinRules.Validation.TooSimple -> activity.getString(R.string.pin_too_simple)
        }
        if (b.newPinLayout.error != null) return null
        if (b.edtConfirmPin.text?.toString() != pin) {
            b.confirmPinLayout.error = activity.getString(R.string.pin_mismatch)
            return null
        }
        b.confirmPinLayout.error = null
        return pin
    }

    private fun pinDialog(b: DialogPinBinding, title: Int, showCurrent: Boolean, showNew: Boolean): AlertDialog {
        b.currentPinLayout.isVisible = showCurrent
        b.newPinLayout.isVisible = showNew
        b.confirmPinLayout.isVisible = showNew
        return MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(b.root)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun finishChange(message: Int) {
        render()
        activity.toast(message, ToastKind.SUCCESS, long = true)
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms + 999) / 1000
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun modeLabel(mode: LockMode): Int = when (mode) {
        LockMode.BIOMETRIC -> R.string.app_lock_mode_biometric
        LockMode.PIN -> R.string.app_lock_mode_pin
        LockMode.BOTH -> R.string.app_lock_mode_both
    }
}
