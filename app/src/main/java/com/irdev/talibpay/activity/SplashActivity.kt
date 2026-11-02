package com.irdev.talibpay.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * نقطة الدخول: تعرض شاشة البداية الرسمية (SplashScreen API) أثناء تحميل التطبيق فقط،
 * بدون انتظار ثابت، ثم تنتقل لشاشة الدخول التي تقرر الوجهة.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
