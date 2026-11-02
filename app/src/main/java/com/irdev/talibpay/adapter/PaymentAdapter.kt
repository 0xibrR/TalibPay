package com.irdev.talibpay.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ItemPaymentBinding
import com.irdev.talibpay.domain.PaymentMethod
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.util.money

class PaymentAdapter : ListAdapter<Payment, PaymentAdapter.PaymentViewHolder>(Diff) {

    class PaymentViewHolder(val binding: ItemPaymentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder =
        PaymentViewHolder(
            ItemPaymentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        val payment = getItem(position)
        val context = holder.itemView.context

        fun labeled(label: Int, value: Double) =
            context.getString(R.string.labeled_amount, context.getString(label), context.money(value))

        with(holder.binding) {
            tvPaymentDate.text = payment.paymentDate

            val method = PaymentMethod.fromStored(payment.paymentMethod)
            tvPaymentMethod.text = method?.let { context.getString(it.label) } ?: payment.paymentMethod
            tvPaymentMethod.isVisible = tvPaymentMethod.text.isNotBlank()
            val color = ContextCompat.getColor(context, method?.color ?: R.color.method_none)
            tvPaymentMethod.setTextColor(color)
            tvPaymentMethod.chipStrokeColor = ColorStateList.valueOf(color)

            tvPaidTotal.text = context.money(payment.paid)
            tvCash.text = labeled(R.string.label_cash, payment.cash)
            tvBank.text = labeled(R.string.label_bank, payment.bank)
            tvTotalAmount.text = labeled(R.string.label_fees, payment.total)
            tvRemainingTotal.text = labeled(R.string.label_remaining_after, payment.remaining)

            tvNote.text = payment.note
            tvNote.isVisible = payment.note.isNotBlank()
        }
    }

    private object Diff : DiffUtil.ItemCallback<Payment>() {
        override fun areItemsTheSame(old: Payment, new: Payment) = old.id == new.id
        override fun areContentsTheSame(old: Payment, new: Payment) = old == new
    }
}
