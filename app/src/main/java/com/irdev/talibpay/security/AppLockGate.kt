package com.irdev.talibpay.security

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.firebase.auth.FirebaseAuth
import com.irdev.talibpay.activity.ForgotPasswordActivity
import com.irdev.talibpay.activity.LockActivity
import com.irdev.talibpay.activity.LoginActivity
import com.irdev.talibpay.activity.RegisterActivity
import com.irdev.talibpay.activity.SplashActivity
import com.irdev.talibpay.data.AuthRepository
import com.irdev.talibpay.data.clearGoogleCredentialState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * يعرض شاشة القفل عند فتح التطبيق، وعند العودة إليه بعد أكثر من [RELOCK_AFTER_MS] في الخلفية.
 * العودة السريعة (من واتساب أو المشاركة مثلًا) لا تطلب الفتح من جديد.
 * حالة الفتح في الذاكرة فقط: إعادة تشغيل التطبيق تعني قفلًا من جديد.
 */
object AppLockGate : Application.ActivityLifecycleCallbacks {

    const val RELOCK_AFTER_MS = 60_000L

    /** شاشات قبل تسجيل الدخول وشاشة القفل نفسها لا تُقفل. */
    private val exempt: Set<Class<out Activity>> = setOf(
        LockActivity::class.java, SplashActivity::class.java, LoginActivity::class.java,
        RegisterActivity::class.java, ForgotPasswordActivity::class.java
    )

    private lateinit var app: Application
    val lock: AppLock by lazy { AppLock(PrefsLockStore(app), KeystoreMac(), SystemLockClock(app)) }

    @Volatile
    private var unlocked = false
    private var startedActivities = 0
    private var backgroundSince: Long? = null

    fun install(application: Application) {
        app = application
        application.registerActivityLifecycleCallbacks(this)
    }

    fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    fun state(): LockState = lock.state(currentUid())

    /** بعد فتح القفل أو تفعيله من الإعدادات. */
    fun markUnlocked() {
        unlocked = true
    }

    /** البصمة صالحة للفتح الآن: مفعّلة في القفل، ومفتاحها موجود، والجهاز يتعرف على بصمة. */
    fun biometricUsable(context: Context, state: LockState.Enabled): Boolean =
        state.mode.usesBiometric && state.biometricEnrolled && BiometricKey.exists() && BiometricKey.isAvailable(context)

    /**
     * إلغاء القفل وتسجيل الخروج: عند التلاعب، أو تجاوز المحاولات، أو "نسيت الرمز".
     * تسجيل الدخول للحساب من جديد هو ما يثبت الهوية، ثم يمكن تفعيل القفل مجددًا.
     */
    fun resetAndSignOut(activity: Activity, @StringRes message: Int) {
        lock.reset()
        BiometricKey.delete()
        unlocked = false
        AuthRepository().signOut()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            clearGoogleCredentialState(activity.applicationContext)
        }
        Toast.makeText(activity.applicationContext, message, Toast.LENGTH_LONG).show()
        activity.startActivity(
            Intent(activity, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        activity.finish()
    }

    // ---------------- دورة حياة الشاشات ----------------

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // عند تفعيل القفل: لا تظهر محتويات التطبيق في قائمة التطبيقات الأخيرة
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && state() !is LockState.NotEnabled) {
            activity.setRecentsScreenshotEnabled(false)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        val since = backgroundSince
        if (startedActivities == 1 && since != null) {
            if (SystemClock.elapsedRealtime() - since > RELOCK_AFTER_MS) unlocked = false
            backgroundSince = null
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (unlocked || activity.javaClass in exempt) return
        if (state() is LockState.NotEnabled) return
        activity.startActivity(
            Intent(activity, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        // تدوير الشاشة يوقف الشاشة ويعيد إنشاءها: ليس خروجًا من التطبيق
        if (startedActivities == 0 && !activity.isChangingConfigurations) {
            backgroundSince = SystemClock.elapsedRealtime()
        }
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
