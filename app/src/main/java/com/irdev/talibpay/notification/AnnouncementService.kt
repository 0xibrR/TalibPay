package com.irdev.talibpay.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.irdev.talibpay.R
import com.irdev.talibpay.activity.SplashActivity

/**
 * يستقبل رسائل FCM.
 * - التطبيق في الـ background: النظام يعرض الإشعار بنفسه (icon/channel من meta-data في الـ Manifest)،
 *   والضغط يفتح SplashActivity مع الـ data كـ extras.
 * - التطبيق في الـ foreground: FCM لا يعرض شيئًا، فنعرضه هنا بنفس الشكل.
 */
class AnnouncementService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        show(title, body, Announcements.safeUrl(message.data[Announcements.KEY_URL]))
    }

    // نستخدم topics فقط، فلا نحتاج إرسال الـ token لأي مكان
    override fun onNewToken(token: String) = Unit

    private fun show(title: String?, body: String?, url: String?) {
        // Android 13+: بدون إذن POST_NOTIFICATIONS لا يُعرض شيء
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val target = if (url != null) Intent(Intent.ACTION_VIEW, url.toUri())
        else Intent(this, SplashActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, getString(R.string.announcements_channel_id))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.md_primary))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        // id حسب الوقت حتى لا يستبدل إعلانٌ جديد إعلانًا سابقًا لم يُقرأ
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }
}
