package com.irdev.talibpay.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

/**
 * Daily background worker (WorkManager) ينشئ الـ monthly charges التي حان موعدها،
 * فتظهر في موعدها حتى لو لم يُفتح التطبيق ذلك اليوم.
 * Idempotent: id ثابت لكل شهر داخل transaction (انظر StudentRepository).
 */
class MonthlyDuesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // بدون user مسجّل لا توجد بيانات؛ يعمل الـ worker في المرة التالية بعد الدخول
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()
        return try {
            StudentRepository().generateMonthlyDues()
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Monthly dues generation failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MonthlyDuesWorker"
        private const val WORK_NAME = "monthly-dues-generation"

        /** يجدول الـ worker مرة يوميًا (KEEP: لا يعيد الجدولة إذا كان مجدولًا). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonthlyDuesWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
