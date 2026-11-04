package com.irdev.talibpay.application

import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import com.irdev.talibpay.data.MonthlyDuesWorker
import com.irdev.talibpay.notification.Announcements
import com.irdev.talibpay.security.AppLockGate
import com.irdev.talibpay.util.CurrencyPrefs
import com.irdev.talibpay.util.ThemePrefs

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        ThemePrefs.apply(ThemePrefs.get(this))
        CurrencyPrefs.current(this)
        // App lock اختياري (fingerprint/PIN) عند الفتح وعند العودة من الـ background
        AppLockGate.install(this)
        // إنشاء الـ monthly charges يوميًا في الـ background (WorkManager)
        MonthlyDuesWorker.schedule(this)
        // Channel الإعلانات قبل وصول أي إشعار FCM
        Announcements.createChannel(this)
    }

}
