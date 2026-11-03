package com.irdev.talibpay.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.irdev.talibpay.R
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.PhoneNumbers
import com.irdev.talibpay.domain.overdueTotal
import com.irdev.talibpay.model.Student

/** رمز الدولة المستخدم لتحويل الأرقام المحلية إلى صيغة واتساب الدولية. */
object ReminderPrefs {
    private const val PREFS = "appSettings"
    private const val KEY_COUNTRY_CODE = "WhatsAppCountryCode"

    fun countryCode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COUNTRY_CODE, null) ?: PhoneNumbers.DEFAULT_COUNTRY_CODE

    fun setCountryCode(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_COUNTRY_CODE, code) }
    }
}

/** نص التذكير بلغة التطبيق: اسم الطالب، كل مستحق متأخر ومتبقيه، والمجموع. */
fun Context.reminderMessage(student: Student, overdue: List<DueSummary>): String {
    val lines = overdue.map { getString(R.string.reminder_due_line, dueTitle(it.charge), money(it.remaining)) }
    return getString(
        R.string.reminder_message,
        student.name,
        lines.joinToString("\n"),
        money(overdue.overdueTotal())
    )
}

private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

/**
 * يفتح محادثة واتساب مع الرقم والرسالة جاهزة (واتساب العادي ثم واتساب للأعمال)،
 * وإن لم يكن واتساب مثبتًا يفتح الرسائل النصية بنفس النص.
 * إن لم يكن الرقم صالحًا يعرض قائمة المشاركة ليختار المستخدم التطبيق والمستلم بنفسه.
 */
fun Context.sendReminder(phoneNumber: String, message: String) {
    val phone = PhoneNumbers.toInternational(phoneNumber, ReminderPrefs.countryCode(this))
    if (phone == null) {
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
        startActivity(Intent.createChooser(share, getString(R.string.reminder_send)))
        return
    }
    val whatsAppUri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")
    for (pkg in WHATSAPP_PACKAGES) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, whatsAppUri).setPackage(pkg))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
    try {
        startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+$phone")).putExtra("sms_body", message)
        )
    } catch (_: ActivityNotFoundException) {
        toast(R.string.reminder_no_app)
    }
}
