package com.irdev.talibpay.util

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ViewToastBinding
import com.irdev.talibpay.viewmodel.UiEvent

/** نوع الرسالة: يحدد الـ icon ولونه في الـ toast. */
enum class ToastKind(val icon: Int, val color: Int) {
    SUCCESS(R.drawable.ic_toast_success, R.color.toast_success),
    ERROR(R.drawable.ic_toast_error, R.color.toast_error),
    INFO(R.drawable.ic_toast_info, R.color.toast_info)
}

/**
 * Custom toast بتصميم التطبيق (view_toast.xml) بدل الشكل الافتراضي.
 * Toast وليس Snackbar لأنه يبقى ظاهرًا بعد finish() للشاشة وفوق الـ dialogs.
 * ملاحظة: الـ custom view في Toast صار deprecated، ومن Android 11 لا يظهر والتطبيق في الـ background؛
 * كل رسائلنا تظهر والتطبيق في الـ foreground، فلا مشكلة.
 */
@Suppress("DEPRECATION")
fun Context.showToast(text: CharSequence, kind: ToastKind = ToastKind.INFO, long: Boolean = false) {
    // Inflate بـ this (وليس applicationContext) حتى يتبع الـ dark mode المختار داخل التطبيق
    val binding = ViewToastBinding.inflate(LayoutInflater.from(this))
    binding.toastText.text = text
    binding.toastIcon.setImageResource(kind.icon)
    binding.toastIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, kind.color))
    Toast(applicationContext).apply {
        view = binding.root
        duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, resources.getDimensionPixelOffset(R.dimen.toast_bottom_offset))
    }.show()
}

fun Context.toast(@StringRes message: Int, kind: ToastKind = ToastKind.INFO, long: Boolean = false) =
    showToast(getString(message), kind, long)

/** رسالة من الـ ViewModel مع الـ arg إن وُجد (مثل أقصى مبلغ مسموح). */
fun Context.toast(message: UiEvent.Message) {
    val text = if (message.arg != null) getString(message.res, message.arg) else getString(message.res)
    showToast(text, message.kind, long = true)
}
