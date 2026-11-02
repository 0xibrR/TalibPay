package com.irdev.talibpay.util

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/** إعداد الوضع الليلي: يُحفظ ويُطبَّق من مكان واحد. */
object ThemePrefs {
    const val LIGHT = 1
    const val DARK = 2
    const val SYSTEM = 3

    private const val PREFS = "appSettings"
    private const val KEY = "NightMode"

    fun get(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, SYSTEM)

    fun set(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt(KEY, mode) }
        apply(mode)
    }

    fun apply(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else
                    AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
            }
        )
    }
}
