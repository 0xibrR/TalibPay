package com.irdev.talibpay.viewmodel

import androidx.annotation.StringRes
import com.irdev.talibpay.R
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.domain.SimpleDate
import com.irdev.talibpay.domain.overdueDues
import java.text.Collator
import java.util.Locale

/** Filters قائمة الطلاب حسب حالة الدفع. غير متنافية: الطالب قد يكون متأخرًا ومدفوعًا جزئيًا معًا. */
enum class StudentFilter(@param:StringRes val labelRes: Int) {
    ALL(R.string.filter_all),

    /** عليه charge حان موعده ولم يُسدَّد بالكامل. */
    OVERDUE(R.string.filter_overdue),

    /** دفع جزءًا وما زال عليه مبلغ. */
    PARTIAL(R.string.filter_partial),

    /** عليه رسوم وسدّدها كلها. */
    PAID(R.string.filter_paid);

    fun matches(item: StudentWithPayments, today: SimpleDate): Boolean {
        val student = item.student
        return when (this) {
            ALL -> true
            OVERDUE -> item.ledger.overdueDues(today).isNotEmpty()
            PARTIAL -> student.paid > 0 && student.remaining > 0
            PAID -> student.total > 0 && student.remaining <= 0
        }
    }
}

/** ترتيب (sort) قائمة الطلاب. الافتراضي ترتيب الإضافة. */
enum class StudentSort(@param:StringRes val labelRes: Int) {
    ADDED_OLDEST(R.string.sort_added_oldest),
    ADDED_NEWEST(R.string.sort_added_newest),
    NAME(R.string.sort_name),
    REMAINING_HIGHEST(R.string.sort_remaining),

    /** الأقدم تأخرًا أولًا، ثم من ليس عليه تأخير. */
    OVERDUE_OLDEST(R.string.sort_overdue);

    /** @param list بترتيب الإضافة (ترتيب Firebase keys). الـ sort ثابت (stable): المتساويان يبقيان بترتيب الإضافة. */
    fun apply(list: List<StudentWithPayments>, today: SimpleDate, locale: Locale): List<StudentWithPayments> =
        when (this) {
            ADDED_OLDEST -> list
            ADDED_NEWEST -> list.asReversed()
            NAME -> {
                val collator = Collator.getInstance(locale)
                list.sortedWith { a, b -> collator.compare(a.student.name.trim(), b.student.name.trim()) }
            }
            REMAINING_HIGHEST -> list.sortedByDescending { it.student.remaining }
            OVERDUE_OLDEST -> {
                val oldest = list.associateWith { oldestOverdueDate(it, today) }
                list.sortedWith(compareBy(nullsLast()) { oldest[it] })
            }
        }

    companion object {
        val DEFAULT = ADDED_OLDEST
        fun fromName(name: String?): StudentSort = entries.firstOrNull { it.name == name } ?: DEFAULT

        /** Charge متأخر بلا تاريخ (مثل بيانات legacy) يُعد أقدم من أي تاريخ. */
        private val UNDATED = SimpleDate(1900, 1, 1)

        private fun oldestOverdueDate(item: StudentWithPayments, today: SimpleDate): SimpleDate? =
            item.ledger.overdueDues(today).minOfOrNull { SimpleDate.parse(it.charge.dueDate) ?: UNDATED }
    }
}
