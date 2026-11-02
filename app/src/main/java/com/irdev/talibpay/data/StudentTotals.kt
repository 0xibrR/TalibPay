package com.irdev.talibpay.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

fun studentRef(uid: String, studentId: String): DatabaseReference =
    FirebaseDatabase.getInstance().getReference("TalibPay")
        .child(uid).child("students").child(studentId)

/**
 * يعيد حساب مجاميع الطالب (نقدي، بنكي، المدفوع، الإجمالي، المتبقي) من دفعاته داخل transaction،
 * فتبقى المجاميع مطابقة للدفعات بعد أي إضافة أو تعديل أو حذف، حتى مع أكثر من جهاز.
 */
fun recalculateStudentTotals(
    studentRef: DatabaseReference,
    lastModified: String,
    onComplete: (Boolean) -> Unit
) {
    studentRef.runTransaction(object : Transaction.Handler {
        override fun doTransaction(currentData: MutableData): Transaction.Result {
            // الطالب غير موجود محليًا (محذوف أو لم يُحمَّل بعد): لا نُنشئ عقدة جديدة،
            // وسيعيد Firebase تشغيل الـ transaction بالقيمة الحقيقية من الخادم إن وُجدت
            if (currentData.value == null) return Transaction.success(currentData)

            var cash = 0.0
            var bank = 0.0
            var total = 0.0
            for (payment in currentData.child("payments").children) {
                cash += payment.child("cash").getValue(Double::class.java) ?: 0.0
                bank += payment.child("bank").getValue(Double::class.java) ?: 0.0
                total = maxOf(total, payment.child("total").getValue(Double::class.java) ?: 0.0)
            }
            val paid = cash + bank

            currentData.child("paidCash").value = cash
            currentData.child("paidBank").value = bank
            currentData.child("paid").value = paid
            currentData.child("total").value = total
            currentData.child("remaining").value = (total - paid).coerceAtLeast(0.0)
            currentData.child("lastModified").value = lastModified
            return Transaction.success(currentData)
        }

        override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
            onComplete(error == null && committed)
        }
    })
}
