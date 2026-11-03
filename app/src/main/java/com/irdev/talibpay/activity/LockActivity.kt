package com.irdev.talibpay.activity

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ActivityLockBinding
import com.irdev.talibpay.security.AppLockGate
import com.irdev.talibpay.security.BiometricUnlock
import com.irdev.talibpay.security.LockState
import com.irdev.talibpay.security.PinResult
import com.irdev.talibpay.security.PinRules
import com.irdev.talibpay.security.UnlockMethods
import com.irdev.talibpay.security.unlockMethodsFor
import com.irdev.talibpay.util.applySystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * شاشة قفل التطبيق. تظهر فوق أي شاشة عند الحاجة ([AppLockGate])، ولا يمكن تجاوزها بزر الرجوع.
 * التحقق من الرمز في الخلفية (PBKDF2 مكلف عمدًا)، والانتظار بعد الأخطاء المتكررة يُعرض بعدّ تنازلي.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private val entered = StringBuilder()
    private var methods = UnlockMethods(biometric = false, pin = true)
    private var verifying = false
    private var countdown: Job? = null
    private val digitKeys = mutableListOf<MaterialButton>()
    private lateinit var fingerprintKey: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // لا لقطات شاشة ولا ظهور في التطبيقات الأخيرة لشاشة إدخال الرمز
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main)

        // الرجوع يُخفي التطبيق بدل تجاوز القفل
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }
        buildDots()
        buildKeypad()
        binding.btnForgotPin.setOnClickListener { confirmForgotPin() }
        refresh(autoBiometric = savedInstanceState == null)
    }

    private fun refresh(autoBiometric: Boolean) {
        when (val state = AppLockGate.state()) {
            LockState.NotEnabled -> unlock()
            LockState.Tampered -> AppLockGate.resetAndSignOut(this, R.string.lock_tampered)
            is LockState.Enabled -> {
                val biometricUsable = AppLockGate.biometricUsable(this, state)
                methods = unlockMethodsFor(state.mode, biometricUsable)
                if (state.mode.usesBiometric && !biometricUsable) showMessage(R.string.lock_biometric_unavailable)
                else showDefaultMessage()
                applyMethods()
                updateLockout()
                if (autoBiometric && methods.biometric) startBiometric()
            }
        }
    }

    /** المفاتيح تُخفى بـ INVISIBLE لا GONE حتى تبقى أماكنها في الشبكة ثابتة. */
    private fun applyMethods() {
        digitKeys.forEach { it.isInvisible = !methods.pin }
        binding.keypad.findViewById<View>(R.id.keyBackspace).isInvisible = !methods.pin
        binding.pinDots.isInvisible = !methods.pin
        fingerprintKey.isInvisible = !methods.biometric
        binding.btnForgotPin.isVisible = methods.pin
    }

    private fun showDefaultMessage() {
        showMessage(
            when {
                methods.pin && methods.biometric -> R.string.lock_message_both
                methods.biometric -> R.string.lock_message_biometric
                else -> R.string.lock_message_pin
            }
        )
    }

    private fun showMessage(res: Int, vararg args: Any) {
        binding.tvLockMessage.text = getString(res, *args)
    }

    // ---------------- الرمز ----------------

    private fun onDigit(digit: Char) {
        if (verifying || countdown?.isActive == true || entered.length >= PinRules.LENGTH) return
        entered.append(digit)
        updateDots()
        if (entered.length == PinRules.LENGTH) verifyPin()
    }

    private fun onBackspace() {
        if (verifying || entered.isEmpty()) return
        entered.deleteCharAt(entered.length - 1)
        updateDots()
    }

    private fun verifyPin() {
        verifying = true
        val pin = entered.toString()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) { AppLockGate.lock.verifyPin(pin) }
            entered.setLength(0)
            verifying = false
            updateDots()
            when (result) {
                PinResult.Success -> unlock()
                is PinResult.Wrong ->
                    if (result.lockoutMs > 0) updateLockout()
                    else binding.tvLockMessage.text = resources.getQuantityString(
                        R.plurals.lock_wrong_pin, result.attemptsBeforeLockout, result.attemptsBeforeLockout
                    )
                is PinResult.LockedOut -> updateLockout()
                PinResult.TooManyAttempts -> AppLockGate.resetAndSignOut(this@LockActivity, R.string.lock_too_many_attempts)
                PinResult.Tampered -> AppLockGate.resetAndSignOut(this@LockActivity, R.string.lock_tampered)
            }
        }
    }

    /** عدّ تنازلي أثناء الانتظار، ولوحة الأرقام معطّلة خلاله. */
    private fun updateLockout() {
        countdown?.cancel()
        if (AppLockGate.lock.remainingLockoutMs() <= 0) {
            setDigitsEnabled(true)
            return
        }
        setDigitsEnabled(false)
        countdown = lifecycleScope.launch {
            while (isActive) {
                val remaining = AppLockGate.lock.remainingLockoutMs()
                if (remaining <= 0) break
                showMessage(R.string.lock_wait, formatDuration(remaining))
                delay(1_000)
            }
            setDigitsEnabled(true)
            showDefaultMessage()
        }
    }

    private fun setDigitsEnabled(enabled: Boolean) {
        digitKeys.forEach { it.isEnabled = enabled }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms + 999) / 1000
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }

    // ---------------- البصمة ----------------

    private fun startBiometric() {
        BiometricUnlock.prompt(
            this,
            getString(R.string.lock_biometric_title),
            getString(if (methods.pin) R.string.lock_use_pin else R.string.cancel)
        ) { outcome ->
            when (outcome) {
                BiometricUnlock.Outcome.Success -> {
                    AppLockGate.lock.onBiometricSuccess()
                    unlock()
                }
                BiometricUnlock.Outcome.Cancelled -> Unit
                // البصمة متوقفة أو غير صالحة: الرمز بديل دائمًا
                BiometricUnlock.Outcome.LockedOut -> fallBackToPin(R.string.lock_biometric_locked_out)
                BiometricUnlock.Outcome.Invalidated -> {
                    AppLockGate.lock.setBiometricEnrolled(false)
                    fallBackToPin(R.string.lock_biometric_changed)
                }
                BiometricUnlock.Outcome.Unavailable -> fallBackToPin(R.string.lock_biometric_unavailable)
            }
        }
    }

    private fun fallBackToPin(message: Int) {
        methods = UnlockMethods(biometric = false, pin = true)
        applyMethods()
        updateLockout()
        if (countdown?.isActive != true) showMessage(message)
    }

    // ---------------- الخروج من القفل ----------------

    private fun unlock() {
        AppLockGate.markUnlocked()
        finish()
    }

    /** نسيان الرمز: تسجيل الدخول للحساب من جديد يثبت الهوية، ثم يُفعَّل القفل مجددًا من الإعدادات. */
    private fun confirmForgotPin() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.lock_forgot_pin)
            .setMessage(R.string.lock_forgot_pin_message)
            .setPositiveButton(R.string.sign_out) { _, _ ->
                AppLockGate.resetAndSignOut(this, R.string.lock_reset_signed_out)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- بناء الواجهة ----------------

    private fun buildDots() {
        val size = resources.getDimensionPixelSize(R.dimen.pin_dot_size)
        val margin = size / 2
        repeat(PinRules.LENGTH) {
            binding.pinDots.addView(View(this), LinearLayout.LayoutParams(size, size).apply {
                marginStart = margin
                marginEnd = margin
            })
        }
        updateDots()
    }

    private fun updateDots() {
        val color = MaterialColors.getColor(binding.pinDots, androidx.appcompat.R.attr.colorPrimary)
        val stroke = resources.getDimensionPixelSize(R.dimen.pin_dot_stroke)
        for (i in 0 until binding.pinDots.childCount) {
            binding.pinDots.getChildAt(i).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(stroke, color)
                setColor(if (i < entered.length) color else android.graphics.Color.TRANSPARENT)
            }
        }
    }

    private fun buildKeypad() {
        val keys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9')
        keys.forEach { digit -> digitKeys += addKey(digit.toString()) { onDigit(digit) } }
        fingerprintKey = addKey(null, R.drawable.ic_fingerprint, getString(R.string.lock_biometric_title)) { startBiometric() }
        digitKeys += addKey("0") { onDigit('0') }
        addKey(null, R.drawable.ic_backspace, getString(R.string.lock_delete_digit)) { onBackspace() }.id = R.id.keyBackspace
    }

    private fun addKey(text: String?, icon: Int? = null, description: String? = null, onClick: () -> Unit): MaterialButton {
        val size = resources.getDimensionPixelSize(R.dimen.pin_key_size)
        val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            textSize = 24f
            insetTop = 0
            insetBottom = 0
            cornerRadius = size / 2
            minWidth = size
            minimumWidth = size
            setPadding(0, 0, 0, 0)
            if (icon != null) {
                setIconResource(icon)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = 0
                iconSize = size / 3
                strokeWidth = 0
            }
            contentDescription = description ?: text
            setOnClickListener { onClick() }
        }
        binding.keypad.addView(button, GridLayout.LayoutParams().apply {
            width = size
            height = size
        })
        return button
    }
}
