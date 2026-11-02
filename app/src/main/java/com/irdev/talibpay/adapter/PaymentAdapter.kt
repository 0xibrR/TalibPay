package com.irdev.talibpay.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ItemPaymentBinding
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.PaymentMethod
import com.irdev.talibpay.model.Payment

class PaymentAdapter : ListAdapter<Payment, PaymentAdapter.PaymentViewHolder>(Diff) {

    class PaymentViewHolder(val binding: ItemPaymentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder =
        PaymentViewHolder(
            ItemPaymentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        val payment = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            tvPaymentDate.text = payment.paymentDate
            tvPaymentMethod.text = PaymentMethod.fromStored(payment.paymentMethod)
                ?.let { context.getString(it.label) }
                ?: payment.paymentMethod
            tvCash.text = context.getString(R.string.cash_amount, Money.format(payment.cash))
            tvBank.text = context.getString(R.string.bank_amount, Money.format(payment.bank))
            tvPaidTotal.text = Money.format(payment.paid)
            tvTotalAmount.text = Money.format(payment.total)
            tvRemainingTotal.text = Money.format(payment.remaining)
            tvNote.text = payment.note
        }
    }

    private object Diff : DiffUtil.ItemCallback<Payment>() {
        override fun areItemsTheSame(old: Payment, new: Payment) = old.id == new.id
        override fun areContentsTheSame(old: Payment, new: Payment) = old == new
    }
}
