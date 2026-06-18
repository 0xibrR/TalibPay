package com.irdev.talibpay.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.R

class PaymentAdapter(private val paymentList: List<Payment>) :
    RecyclerView.Adapter<PaymentAdapter.PaymentViewHolder>() {

    class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPaymentDate: TextView = itemView.findViewById(R.id.tvPaymentDate)
        val tvPaymentMethod: TextView = itemView.findViewById(R.id.tvPaymentMethod)
        val tvCash: TextView = itemView.findViewById(R.id.tvCash)
        val tvBank: TextView = itemView.findViewById(R.id.tvBank)
        val tvPaidTotal: TextView = itemView.findViewById(R.id.tvPaidTotal)
        val tvTotalAmount: TextView = itemView.findViewById(R.id.tvTotalAmount)
        val tvRemainingTotal: TextView = itemView.findViewById(R.id.tvRemainingTotal)
        val tvNote: TextView = itemView.findViewById(R.id.tvNote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_payment, parent, false)
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        val payment = paymentList[position]

        holder.tvPaymentDate.text = payment.paymentDate
        holder.tvPaymentMethod.text = payment.paymentMethod
        holder.tvCash.text = "كاش ${payment.cash}"
        holder.tvBank.text = "بنكي ${payment.bank}"
        holder.tvPaidTotal.text = payment.paid.toString()
        holder.tvTotalAmount.text = payment.total.toString()
        holder.tvRemainingTotal.text = payment.remaining.toString()
        holder.tvNote.text = payment.note
    }

    override fun getItemCount(): Int = paymentList.size
}
