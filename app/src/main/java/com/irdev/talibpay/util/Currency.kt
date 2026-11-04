package com.irdev.talibpay.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.irdev.talibpay.R
import com.irdev.talibpay.domain.Money

/** العملات المتاحة؛ المبالغ تُحفظ أرقامًا فقط، والعملة رمز يُعرض بجانبها. */
enum class Currency(val symbol: String, @param:StringRes val nameRes: Int) {
    ILS("₪", R.string.currency_ils),
    USD("$", R.string.currency_usd),
    EUR("€", R.string.currency_eur),
    JOD("د.أ", R.string.currency_jod),
    EGP("ج.م", R.string.currency_egp),
    SAR("ر.س", R.string.currency_sar),
    AED("د.إ", R.string.currency_aed),
    QAR("ر.ق", R.string.currency_qar),
    KWD("د.ك", R.string.currency_kwd),
    BHD("د.ب", R.string.currency_bhd),
    OMR("ر.ع", R.string.currency_omr),
    IQD("د.ع", R.string.currency_iqd),
    LBP("ل.ل", R.string.currency_lbp),
    SYP("ل.س", R.string.currency_syp),
    TRY("₺", R.string.currency_try),
    GBP("£", R.string.currency_gbp);

    companion object {
        val DEFAULT = ILS
        fun fromCode(code: String?): Currency = entries.firstOrNull { it.name == code } ?: DEFAULT
    }
}

/**
 * عملة التطبيق: تُحفظ في SharedPreferences وتُحمَّل مرة عند البدء،
 * فيمكن تنسيق المبالغ حتى بدون Context (مثل داخل الـ ViewModel).
 */
object CurrencyPrefs {
    private const val PREFS = "appSettings"
    private const val KEY = "Currency"

    @Volatile
    private var cached: Currency? = null

    fun current(context: Context): Currency =
        cached ?: Currency.fromCode(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        ).also { cached = it }

    fun set(context: Context, currency: Currency) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, currency.name) }
        cached = currency
    }

    /** رمز العملة الحالية (الافتراضية إن لم تُحمَّل بعد). */
    val symbol: String get() = (cached ?: Currency.DEFAULT).symbol
}

/** المبلغ مع رمز العملة، مثل "150 ₪"؛ non-breaking space حتى لا ينفصل الرمز عن الرقم. */
fun formatAmount(value: Double): String = Money.format(value) + " " + CurrencyPrefs.symbol
