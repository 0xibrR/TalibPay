package com.irdev.talibpay.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.ArrayAdapter
import androidx.core.content.edit
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.irdev.talibpay.R
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.PhoneNumbers
import com.irdev.talibpay.domain.overdueTotal
import com.irdev.talibpay.model.Student

/**
 * آخر رمز دولة اختاره المستخدم لطالب: يُعبّأ تلقائيًا للطالب التالي،
 * ويُستخدم للطلاب القدامى الذين ليس لهم رمز خاص.
 */
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

/** رمز دولة الطالب، أو الرمز الافتراضي من الإعدادات إن لم يُحدَّد له رمز (الطلاب القدامى). */
fun Context.countryCodeFor(student: Student): String =
    student.phoneCountryCode.ifBlank { ReminderPrefs.countryCode(this) }

/** رقم الطالب للعرض مع رمز دولته، مثل "+970 591234567". */
fun Context.displayPhone(student: Student): String =
    PhoneNumbers.display(student.phoneNumber, countryCodeFor(student))

/**
 * حقل رمز الدولة: قائمة بالدول الشائعة (الرمز · الاسم) مع إمكانية كتابة أي رمز آخر.
 * كتابة أرقام تصفّي القائمة، واختيار دولة يضع رمزها فقط في الحقل.
 */
fun MaterialAutoCompleteTextView.setupCountryCodePicker(initialCode: String) {
    val options = resources.getStringArray(R.array.country_codes).map { it.split("|", limit = 2) }
    val labels = options.map { (code, country) -> context.getString(R.string.country_code_item, code, country) }
    setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, labels))
    setOnItemClickListener { parent, _, position, _ ->
        // القائمة المصفّاة تتغير مع الكتابة، فنقرأ النص المختار ونستخرج رمزه
        val label = parent.getItemAtPosition(position) as String
        setText(label.substringBefore(" "), false)
    }
    setText(initialCode, false)
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
fun Context.sendReminder(student: Student, message: String) {
    val phone = PhoneNumbers.toInternational(student.phoneNumber, countryCodeFor(student))
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

/**
 * يقرأ رمز الدولة من الحقل ويتحقق منه؛ يعرض الخطأ ويعيد null إن لم يكن صالحًا.
 * الرمز الصالح يُحفظ ليكون الافتراضي للطالب التالي.
 */
fun MaterialAutoCompleteTextView.requireCountryCode(layout: TextInputLayout): String? {
    val code = text?.toString()?.trim().orEmpty()
    if (!PhoneNumbers.isValidCountryCode(code)) {
        layout.error = context.getString(R.string.invalid_country_code)
        requestFocus()
        return null
    }
    layout.error = null
    ReminderPrefs.setCountryCode(context, code)
    return code
}
