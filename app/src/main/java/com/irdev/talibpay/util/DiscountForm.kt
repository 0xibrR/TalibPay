package com.irdev.talibpay.util

import android.content.Context
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ViewDiscountFieldsBinding
import com.irdev.talibpay.domain.DiscountType
import com.irdev.talibpay.domain.DiscountValidation
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.StudentDiscount
import com.irdev.talibpay.domain.validateDiscount
import com.irdev.talibpay.domain.discount
import com.irdev.talibpay.model.Student
import java.util.Locale

/**
 * حقول الخصم في form الطالب: تظهر فقط عند إدخال monthly fee،
 * وتعرض الرسوم بعد الخصم أثناء الكتابة (live preview).
 */
fun ViewDiscountFieldsBinding.setup(feeField: EditText, initial: StudentDiscount) {
    val context = root.context
    discountTypeToggle.check(
        when (initial.takeIf { it.isActive }?.type) {
            DiscountType.PERCENT -> R.id.btnDiscountPercent
            DiscountType.AMOUNT -> R.id.btnDiscountAmount
            null -> R.id.btnDiscountNone
        }
    )
    if (initial.isActive) {
        edtDiscountValue.setText(Money.format(initial.value, Locale.US).replace(",", ""))
        edtDiscountReason.setText(initial.reason)
    }

    fun refresh() {
        val fee = Money.parse(feeField.text?.toString()) ?: 0.0
        root.isVisible = fee > 0
        val type = selectedType()
        discountDetails.isVisible = type != null
        discountValueLayout.suffixText =
            if (type == DiscountType.PERCENT) "%" else CurrencyPrefs.current(context).symbol
        val value = Money.parse(edtDiscountValue.text?.toString()) ?: 0.0
        val discount = StudentDiscount(type, value)
        tvDiscountPreview.text = context.getString(
            R.string.discount_preview, context.money(discount.netFee(fee)), context.money(discount.amountOff(fee))
        )
    }

    discountTypeToggle.addOnButtonCheckedListener { _, _, isChecked -> if (isChecked) refresh() }
    feeField.doAfterTextChanged { refresh() }
    edtDiscountValue.doAfterTextChanged {
        discountValueLayout.error = null
        refresh()
    }
    refresh()
}

private fun ViewDiscountFieldsBinding.selectedType(): DiscountType? = when (discountTypeToggle.checkedButtonId) {
    R.id.btnDiscountPercent -> DiscountType.PERCENT
    R.id.btnDiscountAmount -> DiscountType.AMOUNT
    else -> null
}

/** الخصم المُدخل بعد الـ validation؛ null مع عرض الخطأ إن لم يكن صالحًا. بدون monthly fee لا خصم. */
fun ViewDiscountFieldsBinding.readDiscount(monthlyFee: Double): StudentDiscount? {
    if (monthlyFee <= 0) return StudentDiscount.NONE
    val context = root.context
    val result = validateDiscount(
        selectedType(),
        edtDiscountValue.text?.toString().orEmpty(),
        edtDiscountReason.text?.toString().orEmpty(),
        monthlyFee
    )
    discountValueLayout.error = when (result) {
        is DiscountValidation.Valid -> null
        DiscountValidation.InvalidValue -> context.getString(R.string.invalid_amount)
        DiscountValidation.PercentOver100 -> context.getString(R.string.discount_percent_over_100)
        DiscountValidation.ExceedsFee -> context.getString(R.string.discount_exceeds_fee)
    }
    if (result !is DiscountValidation.Valid) {
        edtDiscountValue.requestFocus()
        return null
    }
    return result.discount
}

/** وصف الخصم، مثل "20%" أو "30 ₪"، مع السبب إن وُجد: "20% (أخ)". */
fun Context.discountLabel(discount: StudentDiscount): String {
    val value = if (discount.type == DiscountType.PERCENT) "${Money.format(discount.value)}%" else money(discount.value)
    return if (discount.reason.isBlank()) value else getString(R.string.discount_with_reason, value, discount.reason)
}

/** الـ monthly fee للعرض: "100 ₪"، أو مع الخصم "100 ₪ − 20% (أخ) = 80 ₪". */
fun Context.monthlyFeeText(student: Student): String {
    val discount = student.discount
    if (!discount.isActive) return money(student.monthlyFee)
    return getString(
        R.string.fee_with_discount,
        money(student.monthlyFee), discountLabel(discount), money(discount.netFee(student.monthlyFee))
    )
}
