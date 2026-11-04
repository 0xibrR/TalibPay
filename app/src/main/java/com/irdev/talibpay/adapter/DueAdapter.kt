package com.irdev.talibpay.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.irdev.talibpay.R
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.databinding.ItemDueBinding
import com.irdev.talibpay.databinding.ItemDuePaymentBinding
import com.irdev.talibpay.domain.DueStatus
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.PaymentMethod
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.util.dueTitle
import com.irdev.talibpay.util.money
import com.irdev.talibpay.util.paidPercent

/** Cards الـ charges، وتحت كل charge دفعاته. */
class DueAdapter(
    private val onPay: (DueSummary) -> Unit,
    private val onDueMenu: (DueSummary, View) -> Unit,
    private val onPaymentClick: (DueSummary, Payment) -> Unit
) : ListAdapter<DueSummary, DueAdapter.DueViewHolder>(Diff) {

    class DueViewHolder(val binding: ItemDueBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DueViewHolder =
        DueViewHolder(ItemDueBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: DueViewHolder, position: Int) {
        val due = getItem(position)
        val context = holder.itemView.context
        val charge = due.charge

        with(holder.binding) {
            tvDueTitle.text = context.dueTitle(charge)
            tvDueAmount.text = if (charge.dueDate.isNotBlank())
                context.getString(R.string.due_amount_with_date, context.money(charge.amount), charge.dueDate)
            else
                context.getString(R.string.due_amount_only, context.money(charge.amount))

            val (statusText, statusColor) = when (due.status) {
                DueStatus.PAID -> R.string.due_status_paid to R.color.status_paid
                DueStatus.PARTIAL -> R.string.due_status_partial to R.color.method_mixed
                DueStatus.UNPAID -> R.string.due_status_unpaid to R.color.status_due
            }
            val color = ContextCompat.getColor(context, statusColor)
            chipDueStatus.setText(statusText)
            chipDueStatus.setTextColor(color)
            chipDueStatus.chipStrokeColor = ColorStateList.valueOf(color)

            dueProgress.setProgressCompat(paidPercent(due.paid, charge.amount), false)
            dueProgress.setIndicatorColor(color)
            tvDueSummary.text = listOfNotNull(
                context.getString(R.string.due_summary, context.money(due.paid), context.money(due.remaining)),
                // المسدَّد من الـ wallet (ليس دفعة، فلا يظهر في قائمة الدفعات)
                if (due.walletUsed > 0) context.getString(R.string.due_wallet_used, context.money(due.walletUsed)) else null,
                // Monthly charge أُنشئ بخصم: نوضح قيمته قبل الخصم
                if (due.charge.discount > 0) context.getString(
                    R.string.due_discount, context.money(Money.sum(listOf(due.charge.amount, due.charge.discount))), context.money(due.charge.discount)
                ) else null
            ).joinToString("\n")

            // الدفعات: الأحدث أولًا، مع المتبقي من الـ charge بعد كل دفعة
            duePayments.removeAllViews()
            val inflater = LayoutInflater.from(context)
            due.payments.asReversed().forEach { payment ->
                val row = ItemDuePaymentBinding.inflate(inflater, duePayments, true)
                val method = PaymentMethod.fromStored(payment.paymentMethod)
                val methodLabel = method?.let { context.getString(it.label) } ?: payment.paymentMethod
                row.tvPaymentDate.text = listOf(payment.paymentDate, methodLabel)
                    .filter { it.isNotBlank() }.joinToString(" · ")
                row.tvPaymentDetails.text = listOf(
                    context.getString(R.string.remaining_after_payment, context.money(payment.remaining)),
                    if (payment.credited > 0) context.getString(R.string.payment_to_wallet, context.money(payment.credited)) else "",
                    payment.note
                ).filter { it.isNotBlank() }.joinToString(" · ")
                row.tvPaymentAmount.text = context.money(payment.paid)
                row.methodDot.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, method?.color ?: R.color.method_none)
                )
                row.root.setOnClickListener { onPaymentClick(due, payment) }
            }

            // زر الدفع يظهر فقط لـ charge غير مسدد بالكامل
            btnPayDue.isVisible = due.remaining > 0
            dueBottomSpace.isVisible = due.remaining <= 0
            btnPayDue.setOnClickListener { onPay(due) }
            btnDueMenu.setOnClickListener { onDueMenu(due, it) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DueSummary>() {
        override fun areItemsTheSame(old: DueSummary, new: DueSummary) = old.charge.id == new.charge.id
        override fun areContentsTheSame(old: DueSummary, new: DueSummary) = old == new
    }
}
