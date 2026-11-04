package com.irdev.talibpay.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.irdev.talibpay.R

/**
 * إذن POST_NOTIFICATIONS (Android 13+) يُطلب مرة واحدة بعد تسجيل الدخول، مع شرح قبل نافذة النظام،
 * لأن الطلب المفاجئ عند أول فتح غالبًا يُرفض. إن رفض المستخدم يمكنه التفعيل لاحقًا من إعدادات الجهاز.
 * يُنشأ كـ property في الـ Activity (registerForActivityResult يجب أن يُستدعى قبل onStart).
 */
class NotificationPermission(private val activity: ComponentActivity) {

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun askOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        prefs.edit { putBoolean(KEY_ASKED, true) }

        MaterialAlertDialogBuilder(activity)
            .setIcon(R.drawable.ic_notification)
            .setTitle(R.string.notifications_permission_title)
            .setMessage(R.string.notifications_permission_message)
            .setPositiveButton(R.string.notifications_permission_allow) { _, _ ->
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(R.string.notifications_permission_later, null)
            .show()
    }

    private companion object {
        const val PREFS = "announcements"
        const val KEY_ASKED = "permission_asked"
    }
}
