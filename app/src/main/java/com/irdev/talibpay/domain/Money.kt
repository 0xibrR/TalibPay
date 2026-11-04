package com.irdev.talibpay.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * المبالغ تبقى Double في Firebase للتوافق مع البيانات الحالية،
 * لكن كل جمع وطرح يتم بـ BigDecimal مع تقريب لخانتين، لتجنب أخطاء floating point مثل 0.1 + 0.2.
 */
object Money {

    fun round(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    fun sum(values: Iterable<Double>): Double =
        values.fold(BigDecimal.ZERO) { acc, v -> acc + BigDecimal.valueOf(v) }
            .setScale(2, RoundingMode.HALF_UP).toDouble()

    fun subtract(a: Double, b: Double): Double =
        (BigDecimal.valueOf(a) - BigDecimal.valueOf(b)).setScale(2, RoundingMode.HALF_UP).toDouble()

    /**
     * Parse لنص المستخدم إلى مبلغ: يقبل الأرقام العربية والفاصلة العربية أو الأوروبية.
     * يعيد null للنص غير الصالح أو السالب.
     */
    fun parse(text: String?): Double? {
        val normalized = text.orEmpty().trim()
            .map { c ->
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    '٫', ',' -> '.'
                    else -> c
                }
            }
            .joinToString("")
        if (normalized.isEmpty()) return null
        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return value.setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    fun format(value: Double, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }.format(value)
}
