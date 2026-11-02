package com.irdev.talibpay.adapter

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.irdev.talibpay.R
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.databinding.ItemStudentBinding
import com.irdev.talibpay.domain.PaymentMethod
import com.irdev.talibpay.util.money
import com.irdev.talibpay.util.paidPercent

class StudentAdapter(
    private val onClick: (StudentWithPayments) -> Unit
) : ListAdapter<StudentWithPayments, StudentAdapter.StudentViewHolder>(Diff) {

    class StudentViewHolder(val binding: ItemStudentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder =
        StudentViewHolder(
            ItemStudentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val item = getItem(position)
        val student = item.student
        val context = holder.itemView.context

        with(holder.binding) {
            nameTextView.text = student.name
            initialTextView.text = student.name.firstOrNull()?.toString() ?: "?"

            paidCashTextView.text =
                context.getString(R.string.labeled_amount, context.getString(R.string.label_cash), context.money(student.paidCash))
            paidBankTextView.text =
                context.getString(R.string.labeled_amount, context.getString(R.string.label_bank), context.money(student.paidBank))
            totalAmountTextView.text =
                context.getString(R.string.labeled_amount, context.getString(R.string.label_fees), context.money(student.total))

            // المتبقي: أحمر إن وُجد، أخضر عند السداد الكامل، ومحايد إن لم تُسجَّل رسوم
            remainingTextView.text = context.money(student.remaining)
            remainingTextView.setTextColor(
                ContextCompat.getColor(
                    context,
                    when {
                        student.total <= 0 -> R.color.method_none
                        student.remaining > 0 -> R.color.status_due
                        else -> R.color.status_paid
                    }
                )
            )
            paidProgress.setProgressCompat(paidPercent(student.paid, student.total), false)

            // اللون والنص حسب طريقة الدفع في آخر دفعة
            val lastPayment = item.lastPayment
            val method = PaymentMethod.fromStored(lastPayment?.paymentMethod)
            paymentMethodTextView.text = when {
                lastPayment == null -> context.getString(R.string.no_payments)
                method != null -> context.getString(method.label)
                else -> lastPayment.paymentMethod
            }
            paymentDateTextView.text = lastPayment?.paymentDate.orEmpty()

            val color = ContextCompat.getColor(context, method?.color ?: R.color.method_none)
            paymentMethodTextView.setTextColor(color)
            paidProgress.setIndicatorColor(color)
            // mutate() حتى لا يتشارك كل الصفوف نفس الخلفية فيتغير لونها معًا
            (initialTextView.background?.mutate() as? GradientDrawable)?.color =
                ColorStateList.valueOf(color)

            root.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<StudentWithPayments>() {
        override fun areItemsTheSame(old: StudentWithPayments, new: StudentWithPayments) =
            old.student.id == new.student.id

        override fun areContentsTheSame(old: StudentWithPayments, new: StudentWithPayments) =
            old == new
    }
}
