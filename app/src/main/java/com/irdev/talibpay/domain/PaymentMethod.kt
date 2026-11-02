package com.irdev.talibpay.domain

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.irdev.talibpay.R

/**
 * طريقة الدفع تُخزَّن في Firebase كرمز ثابت ([code]) بدل النص المترجم،
 * حتى لا تتأثر البيانات بتغيير لغة الجهاز.
 * الترتيب هنا هو نفس ترتيب العناصر في القائمة المنسدلة (بعد "اختر طريقة الدفع").
 */
enum class PaymentMethod(
    val code: String,
    @param:StringRes val label: Int,
    @param:ColorRes val color: Int
) {
    CASH("CASH", R.string.method_cash, R.color.method_cash),
    MIXED("MIXED", R.string.method_mixed, R.color.method_mixed),
    BANK("BANK", R.string.method_bank, R.color.method_bank),
    UNPAID("UNPAID", R.string.method_unpaid, R.color.method_unpaid);

    companion object {
        // نصوص النسخ القديمة من التطبيق (عربي وإنجليزي) قبل اعتماد الرموز
        private val legacyLabels = mapOf(
            "نقدي" to CASH, "Cash" to CASH,
            "بنكي" to BANK, "Bank" to BANK,
            "جزء نقدي وجزء بنكي" to MIXED,
            "Part cash and part bank" to MIXED,
            "Half Cash Half Bank" to MIXED,
            "غير مدفوع" to UNPAID, "Not Paid" to UNPAID
        )

        fun fromStored(value: String?): PaymentMethod? {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return entries.firstOrNull { it.code == trimmed } ?: legacyLabels[trimmed]
        }

        /** موضع القائمة المنسدلة: 0 = "اختر طريقة الدفع". */
        fun fromSpinnerPosition(position: Int): PaymentMethod? = entries.getOrNull(position - 1)

        fun spinnerPositionOf(method: PaymentMethod?): Int =
            if (method == null) 0 else method.ordinal + 1
    }
}
