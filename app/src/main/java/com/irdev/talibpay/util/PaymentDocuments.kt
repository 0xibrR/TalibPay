package com.irdev.talibpay.util

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.irdev.talibpay.R
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.databinding.DocPaymentDocumentBinding
import com.irdev.talibpay.databinding.ItemReportRowBinding
import com.irdev.talibpay.domain.DueStatus
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.Receipts
import com.irdev.talibpay.domain.methodFor
import com.irdev.talibpay.domain.todaySimpleDate
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.model.Student

// ---------------- إيصال الدفع ----------------

/** إيصال دفعة واحدة: صورة أو PDF، يُرسل لولي الأمر عبر واتساب أو أي تطبيق. */
fun Activity.shareReceipt(student: Student, due: DueSummary, payment: Payment) {
    val fileName = "Receipt-${Receipts.number(payment.id)}"
    val caption = getString(R.string.receipt_caption, student.name, money(payment.paid))
    DocumentExport.chooseFormat(
        this, R.string.receipt_send,
        onImage = { DocumentExport.shareImage(this, renderReceipt(student, due, payment), fileName, caption) },
        onPdf = { DocumentExport.sharePdf(this, renderReceipt(student, due, payment), fileName, caption) }
    )
}

private fun Activity.renderReceipt(student: Student, due: DueSummary, payment: Payment): View {
    val b = DocPaymentDocumentBinding.inflate(DocumentExport.inflater(this))
    val context = b.root.context
    b.tvDocType.setText(R.string.receipt_title)
    b.tvDocMeta.text = getString(R.string.receipt_meta, Receipts.number(payment.id), payment.paymentDate)
    b.tvStudentName.text = student.name
    b.tvSubtitle.text = getString(R.string.receipt_for_due, dueTitle(due.charge))

    b.tvHighlightLabel.setText(R.string.receipt_amount_paid)
    b.tvHighlightValue.text = money(payment.paid)
    b.highlightRows.removeAllViews()
    if (payment.cash > 0) b.highlightRows.addDocRow(context, getString(R.string.label_cash), money(payment.cash))
    if (payment.bank > 0) b.highlightRows.addDocRow(context, getString(R.string.label_bank), money(payment.bank))

    b.tvSectionTitle.setText(R.string.receipt_details)
    with(b.sectionRows) {
        removeAllViews()
        addDocRow(context, getString(R.string.receipt_due_amount), money(due.charge.amount))
        addDocRow(
            context, getString(R.string.receipt_remaining_after), money(payment.remaining),
            if (payment.remaining > 0) R.color.status_due else R.color.status_paid
        )
        if (payment.credited > 0) {
            addDocRow(context, getString(R.string.receipt_to_wallet), money(payment.credited), R.color.status_paid)
        }
        addDocRow(context, getString(R.string.receipt_method), getString(methodFor(payment.cash, payment.bank).label))
    }
    b.tvNote.isVisible = payment.note.isNotBlank()
    b.tvNote.text = payment.note
    b.tvFooter.text = getString(R.string.document_footer, todaySimpleDate().format())
    return DocumentExport.layout(this, b.root)
}

// ---------------- كشف الحساب ----------------

/** كشف حساب الطالب: كل المستحقات وحالتها، والمجاميع، ورصيد المحفظة. */
fun Activity.shareStatement(data: StudentWithPayments) {
    val fileName = "Statement-${data.student.name.filter { it.isLetterOrDigit() }.ifEmpty { "student" }}"
    val caption = getString(R.string.statement_caption, data.student.name)
    DocumentExport.chooseFormat(
        this, R.string.statement_title,
        onImage = { DocumentExport.shareImage(this, renderStatement(data), fileName, caption) },
        onPdf = { DocumentExport.sharePdf(this, renderStatement(data), fileName, caption) }
    )
}

private fun Activity.renderStatement(data: StudentWithPayments): View {
    val student = data.student
    val b = DocPaymentDocumentBinding.inflate(DocumentExport.inflater(this))
    val context = b.root.context
    b.tvDocType.setText(R.string.statement_title)
    b.tvDocMeta.text = getString(R.string.statement_meta, todaySimpleDate().format())
    b.tvStudentName.text = student.name
    b.tvSubtitle.isVisible = false

    b.tvHighlightLabel.setText(R.string.statement_remaining)
    b.tvHighlightValue.text = money(student.remaining)
    with(b.highlightRows) {
        removeAllViews()
        addDocRow(context, getString(R.string.statement_total_fees), money(student.total))
        addDocRow(context, getString(R.string.statement_paid), money(student.paid))
        if (student.paidCash > 0 && student.paidBank > 0) {
            addDocRow(context, getString(R.string.label_cash), money(student.paidCash))
            addDocRow(context, getString(R.string.label_bank), money(student.paidBank))
        }
        if (data.ledger.walletBalance > 0) {
            addDocRow(context, getString(R.string.wallet_balance), money(data.ledger.walletBalance))
        }
    }

    b.tvSectionTitle.text = resources.getQuantityString(R.plurals.dues_count, data.dues.size, data.dues.size)
    with(b.sectionRows) {
        removeAllViews()
        if (data.dues.isEmpty()) addDocRow(context, getString(R.string.statement_no_dues), "")
        data.dues.forEach { due ->
            val (statusRes, color) = when (due.status) {
                DueStatus.PAID -> R.string.due_status_paid to R.color.status_paid
                DueStatus.PARTIAL -> R.string.due_status_partial to R.color.status_due
                DueStatus.UNPAID -> R.string.due_status_unpaid to R.color.status_due
            }
            val label = dueTitle(due.charge) + "\n" + getString(
                R.string.statement_due_line, money(due.charge.amount), money(due.paid), getString(statusRes)
            )
            addDocRow(context, label, money(due.remaining), if (due.remaining > 0) color else R.color.status_paid)
        }
    }
    b.tvFooter.text = getString(R.string.document_footer, todaySimpleDate().format())
    return DocumentExport.layout(this, b.root)
}

// ---------------- مشترك ----------------

/** صف عنوان/قيمة في مستند؛ [context] سياق التصدير (فاتح دائمًا). */
private fun ViewGroup.addDocRow(context: Context, label: String, value: String, @ColorRes valueColor: Int? = null) {
    val row = ItemReportRowBinding.inflate(android.view.LayoutInflater.from(context), this, true)
    row.tvRowLabel.text = label
    row.tvRowValue.text = value
    if (valueColor != null) row.tvRowValue.setTextColor(ContextCompat.getColor(context, valueColor))
}
