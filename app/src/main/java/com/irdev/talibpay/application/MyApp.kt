package com.irdev.talibpay.application

import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import com.irdev.talibpay.util.ThemePrefs

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        ThemePrefs.apply(ThemePrefs.get(this))
    }

}
