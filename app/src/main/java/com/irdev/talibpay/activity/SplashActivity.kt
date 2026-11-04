package com.irdev.talibpay.activity

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.irdev.talibpay.notification.Announcements

/**
 * Entry point: تعرض الـ splash (SplashScreen API) أثناء تحميل التطبيق فقط،
 * بدون انتظار ثابت، ثم تنتقل لـ LoginActivity التي تقرر الوجهة.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (!openAnnouncementLink()) startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    /**
     * ضغط على إشعار FCM وصل والتطبيق في الـ background: الـ data تصل كـ extras.
     * إن كان فيه رابط (مثل رابط التحديث) نفتحه بدل التطبيق.
     */
    private fun openAnnouncementLink(): Boolean {
        val url = Announcements.safeUrl(intent.getStringExtra(Announcements.KEY_URL)) ?: return false
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
