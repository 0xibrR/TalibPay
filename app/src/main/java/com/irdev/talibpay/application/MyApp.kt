package com.irdev.talibpay.application

import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import com.irdev.talibpay.data.MonthlyDuesWorker
import com.irdev.talibpay.security.AppLockGate
import com.irdev.talibpay.util.CurrencyPrefs
import com.irdev.talibpay.util.ThemePrefs

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        ThemePrefs.apply(ThemePrefs.get(this))
        CurrencyPrefs.current(this)
        // قفل التطبيق الاختياري (بصمة/رمز) عند الفتح وعند العودة من الخلفية
        AppLockGate.install(this)
        // إنشاء الرسوم الشهرية يوميًا في الخلفية
        MonthlyDuesWorker.schedule(this)
    }

}
