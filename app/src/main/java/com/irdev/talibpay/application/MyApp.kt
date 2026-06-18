package com.irdev.talibpay.application

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.database.FirebaseDatabase

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)


        val sharedPrefs = getSharedPreferences("appSettings", MODE_PRIVATE)
        val nightModeStatus = sharedPrefs.getInt("NightMode", 3)

        when (nightModeStatus) {
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
                }
            }
        }
    }

}