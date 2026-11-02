package com.irdev.talibpay.adapter

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
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.PaymentMethod

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
            paidCashTextView.text = Money.format(student.paidCash)
            paidBankTextView.text = Money.format(student.paidBank)
            totalAmountTextView.text = Money.format(student.total)
            remainingTextView.text = Money.format(student.remaining)
            initialTextView.text = student.name.firstOrNull()?.toString() ?: "?"

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
            // mutate() حتى لا يتشارك كل الصفوف نفس الخلفية فيتغير لونها معًا
            (initialTextView.background?.mutate() as? GradientDrawable)?.setColor(color)

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
