package com.irdev.talibpay.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * لغة التطبيق عبر Per-app language API في AppCompat:
 * - تتغير فورًا مع recreate للشاشات المفتوحة.
 * - الاختيار يُحفظ تلقائيًا ويبقى بعد إغلاق التطبيق (Android 13+ من النظام،
 *   والأقدم عبر autoStoreLocales في الـ Manifest).
 */
object LanguagePrefs {
    const val SYSTEM = ""
    const val ARABIC = "ar"
    const val ENGLISH = "en"

    /** اللغة المختارة حاليًا، أو [SYSTEM] إن كان التطبيق يتبع لغة الجهاز. */
    fun get(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return SYSTEM
        return when (locales[0]?.language) {
            ARABIC -> ARABIC
            ENGLISH -> ENGLISH
            else -> SYSTEM
        }
    }

    fun set(language: String) {
        AppCompatDelegate.setApplicationLocales(
            if (language == SYSTEM) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(language)
        )
    }
}
