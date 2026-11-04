package com.irdev.talibpay.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessaging
import com.irdev.talibpay.R

/**
 * إعلانات التطبيق (أعطال، تحديثات) تُرسل من Firebase Console → Messaging إلى FCM topics:
 * - [ALL]: كل المستخدمين.
 * - [ARABIC] / [ENGLISH]: حسب لغة التطبيق، لترسل كل رسالة بلغة المستخدم.
 * لا نحتاج server ولا تخزين tokens: الـ subscription يكفي.
 */
object Announcements {

    const val ALL = "announcements"
    const val ARABIC = "announcements_ar"
    const val ENGLISH = "announcements_en"

    /** Data key اختياري في الرسالة: رابط يُفتح عند الضغط (مثل رابط التحديث). */
    const val KEY_URL = "url"

    private const val PREFS = "announcements"
    private const val KEY_TOPIC = "subscribed_topic"

    /** Topic اللغة: العربية لـ "ar"، والإنجليزية لأي لغة أخرى (الافتراضية في values/). */
    fun topicFor(language: String?): String = if (language == "ar") ARABIC else ENGLISH

    /** الرابط مقبول فقط إن كان http(s)، حتى لا يفتح الإشعار أي intent آخر. */
    fun safeUrl(url: String?): String? =
        url?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

    /**
     * يشترك في [ALL] وفي topic لغة التطبيق الحالية، ويلغي topic اللغة الأخرى.
     * يُستدعى عند فتح الشاشة الرئيسية؛ لا يكرر الطلب إن لم تتغير اللغة منذ آخر نجاح.
     */
    fun sync(context: Context, language: String?) {
        val topic = topicFor(language)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_TOPIC, null) == topic) return
        val messaging = FirebaseMessaging.getInstance()
        messaging.unsubscribeFromTopic(if (topic == ARABIC) ENGLISH else ARABIC)
        messaging.subscribeToTopic(ALL)
        messaging.subscribeToTopic(topic)
            .addOnSuccessListener { prefs.edit { putString(KEY_TOPIC, topic) } }
            // يُعاد المحاولة تلقائيًا في الفتح التالي لأن الـ topic لم يُحفظ
            .addOnFailureListener { Log.w("Announcements", "Topic subscription failed", it) }
    }

    /** Notification channel للإعلانات (Android 8+)؛ اسمه يظهر للمستخدم في إعدادات الإشعارات. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            context.getString(R.string.announcements_channel_id),
            context.getString(R.string.announcements_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.announcements_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
