package com.irdev.talibpay.util

import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.irdev.talibpay.domain.PaymentMethod

/** قائمة طرق الدفع المنسدلة (Exposed Dropdown) بنصوص مترجمة. */
fun MaterialAutoCompleteTextView.setupPaymentMethods() {
    setSimpleItems(PaymentMethod.entries.map { context.getString(it.label) }.toTypedArray())
}

fun MaterialAutoCompleteTextView.selectPaymentMethod(method: PaymentMethod?) {
    setText(method?.let { context.getString(it.label) }.orEmpty(), false)
}

/** الطريقة المختارة تُستنتج من النص، فتبقى صحيحة بعد تدوير الشاشة. */
fun MaterialAutoCompleteTextView.selectedPaymentMethod(): PaymentMethod? {
    val current = text?.toString().orEmpty()
    return PaymentMethod.entries.firstOrNull { context.getString(it.label) == current }
}
