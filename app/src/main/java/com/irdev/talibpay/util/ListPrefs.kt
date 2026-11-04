package com.irdev.talibpay.util

import android.content.Context
import androidx.core.content.edit

/** الـ sort المختار لقائمة الطلاب؛ يبقى بعد إغلاق التطبيق (الـ filter لا يُحفظ ويعود "الكل"). */
object ListPrefs {
    private const val PREFS = "appSettings"
    private const val KEY_SORT = "StudentSort"

    fun sort(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SORT, null)

    fun setSort(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_SORT, name) }
    }
}
