package com.irdev.talibpay.domain

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.irdev.talibpay.R

/**
 * طريقة الدفع تُخزَّن في Firebase كـ code ثابت ([code]) بدل النص المترجم،
 * حتى لا تتأثر البيانات بلغة الجهاز.
 * الترتيب هنا هو ترتيب العناصر في الـ dropdown.
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
        // Legacy: نصوص النسخ القديمة (عربي وإنجليزي) قبل اعتماد الـ codes
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
    }
}
