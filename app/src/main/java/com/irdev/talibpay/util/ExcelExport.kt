package com.irdev.talibpay.util

import android.app.Activity
import android.content.Context
import android.view.View
import com.irdev.talibpay.R
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.domain.DueStatus
import com.irdev.talibpay.domain.Receipts
import com.irdev.talibpay.domain.attendanceDaySet
import com.irdev.talibpay.domain.discount
import com.irdev.talibpay.domain.todaySimpleDate
import com.irdev.talibpay.util.XlsxWriter.Cell
import java.io.File
import java.io.FileOutputStream

/**
 * تصدير كل البيانات إلى Excel: ورقة للطلاب، وورقة للمستحقات، وورقة للدفعات.
 * يشمل المؤرشفين (مع عمود الحالة) لأن الملف للمحاسبة وكنسخة احتياطية.
 */
object ExcelExport {

    /** يكتب الملف في cache ويعيده؛ يُستدعى خارج الخيط الرئيسي. */
    fun write(context: Context, students: List<StudentWithPayments>): File {
        val file = File(context.cacheDir, "TalibPay-${todaySimpleDate().sortKey()}.xlsx")
        val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        FileOutputStream(file).use { XlsxWriter.write(it, sheets(context, students), rtl) }
        return file
    }

    fun share(activity: Activity, file: File) =
        DocumentExport.share(activity, file, XlsxWriter.MIME_TYPE, activity.getString(R.string.excel_caption))

    internal fun sheets(context: Context, students: List<StudentWithPayments>): List<XlsxWriter.Sheet> {
        val sorted = students.sortedBy { it.student.name.lowercase() }
        return listOf(studentsSheet(context, sorted), duesSheet(context, sorted), paymentsSheet(context, sorted))
    }

    private fun studentsSheet(c: Context, students: List<StudentWithPayments>) = XlsxWriter.Sheet(
        c.getString(R.string.excel_sheet_students),
        listOf(
            R.string.excel_col_name, R.string.phone_number, R.string.registration_date, R.string.attendance_days,
            R.string.monthly_fee, R.string.excel_col_discount, R.string.label_fees, R.string.label_cash,
            R.string.label_bank, R.string.excel_col_paid, R.string.label_remaining, R.string.wallet_balance,
            R.string.excel_col_status, R.string.excel_col_notes
        ).map(c::getString),
        students.map { item ->
            val s = item.student
            listOf(
                text(s.name),
                text(c.displayPhone(s)),
                text(s.registrationDate),
                text(if (s.attendanceDaySet.isEmpty()) "" else c.attendanceDaysText(s.attendanceDaySet)),
                number(s.monthlyFee),
                text(if (s.discount.isActive) c.discountLabel(s.discount) else ""),
                number(s.total),
                number(s.paidCash),
                number(s.paidBank),
                number(s.paid),
                number(s.remaining),
                number(item.ledger.walletBalance),
                text(c.getString(if (s.archived) R.string.excel_status_archived else R.string.excel_status_active)),
                text(s.notes)
            )
        }
    )

    private fun duesSheet(c: Context, students: List<StudentWithPayments>) = XlsxWriter.Sheet(
        c.getString(R.string.excel_sheet_dues),
        listOf(
            R.string.excel_col_student, R.string.excel_col_due, R.string.excel_col_amount, R.string.excel_col_discount,
            R.string.excel_col_due_date, R.string.excel_col_paid, R.string.excel_col_from_wallet,
            R.string.label_remaining, R.string.excel_col_status
        ).map(c::getString),
        students.flatMap { item ->
            item.dues.map { due ->
                listOf(
                    text(item.student.name),
                    text(c.dueTitle(due.charge)),
                    number(due.charge.amount),
                    number(due.charge.discount),
                    text(due.charge.dueDate),
                    number(due.paid),
                    number(due.walletUsed),
                    number(due.remaining),
                    text(
                        c.getString(
                            when (due.status) {
                                DueStatus.PAID -> R.string.due_status_paid
                                DueStatus.PARTIAL -> R.string.due_status_partial
                                DueStatus.UNPAID -> R.string.due_status_unpaid
                            }
                        )
                    )
                )
            }
        }
    )

    private fun paymentsSheet(c: Context, students: List<StudentWithPayments>) = XlsxWriter.Sheet(
        c.getString(R.string.excel_sheet_payments),
        listOf(
            R.string.excel_col_student, R.string.excel_col_due, R.string.excel_col_date, R.string.label_cash,
            R.string.label_bank, R.string.excel_col_total, R.string.receipt_to_wallet, R.string.excel_col_receipt,
            R.string.excel_col_notes
        ).map(c::getString),
        students.flatMap { item ->
            item.dues.flatMap { due ->
                due.payments.map { payment ->
                    listOf(
                        text(item.student.name),
                        text(c.dueTitle(due.charge)),
                        text(payment.paymentDate),
                        number(payment.cash),
                        number(payment.bank),
                        number(payment.paid),
                        number(payment.credited),
                        text(Receipts.number(payment.id)),
                        text(payment.note)
                    )
                }
            }
        }
    )

    private fun text(value: String) = Cell.Text(value)
    private fun number(value: Double) = Cell.Number(value)
}
