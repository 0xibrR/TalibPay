package com.irdev.talibpay

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StudentAdapter(private var studentList: ArrayList<Student>) :
    RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {

    // HashMap لتخزين آخر دفعة لكل طالب
    val lastPaymentsMap = HashMap<String, Payment>()

    class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.nameTextView)
        val paidCashText: TextView = itemView.findViewById(R.id.paidCashTextView)
        val paidBankText: TextView = itemView.findViewById(R.id.paidBankTextView)
        val paymentMethodText: TextView = itemView.findViewById(R.id.paymentMethodTextView)
        val totalAmountText: TextView = itemView.findViewById(R.id.totalAmountTextView)
        val remainingText: TextView = itemView.findViewById(R.id.remainingTextView)
        val paymentDateText: TextView = itemView.findViewById(R.id.paymentDateTextView)
        val initialTextView: TextView = itemView.findViewById(R.id.initialTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student, parent, false)
        return StudentViewHolder(view)
    }

    @SuppressLint("UseKtx")
    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = studentList[position]

        holder.nameText.text = student.name
        holder.paidCashText.text = student.paidCash.toString()
        holder.paidBankText.text = student.paidBank.toString()
        holder.totalAmountText.text = student.total.toString()
        holder.remainingText.text = student.remaining.toString()
        holder.initialTextView.text = student.name.firstOrNull()?.toString() ?: "?"

        // الحصول على آخر دفعة للطالب
        val lastPayment = lastPaymentsMap[student.id]

        // عرض وتلوين حسب طريقة الدفع من آخر دفعة
        lastPayment?.let { payment ->
            holder.paymentMethodText.text = payment.paymentMethod
            holder.paymentDateText.text = payment.paymentDate

            val color = when (payment.paymentMethod.trim()) {
                "نقدي", "Cash" -> Color.parseColor("#4CAF50")       // أخضر
                "بنكي", "Bank" -> Color.parseColor("#1976D2")      // أزرق غامق
                "جزء نقدي وجزء بنكي", "Half Cash Half Bank" -> Color.parseColor("#FF9800") // برتقالي
                "غير مدفوع", "Not Paid" -> Color.parseColor("#D32F2F") // أحمر
                else -> Color.GRAY
            }

            holder.paymentMethodText.setTextColor(color)

            // تلوين الدائرة
            val backgroundDrawable = holder.initialTextView.background
            if (backgroundDrawable is GradientDrawable) {
                backgroundDrawable.setColor(color)
            }
        } ?: run {
            holder.paymentMethodText.text = "لا يوجد دفعات"
            holder.paymentDateText.text = ""
            holder.paymentMethodText.setTextColor(Color.GRAY)
            (holder.initialTextView.background as? GradientDrawable)?.setColor(Color.GRAY)
        }

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val lastPayment = lastPaymentsMap[student.id]

            val intent = Intent(context, DetailActivity::class.java).apply {
                putExtra("id", student.id)
                putExtra("name", student.name)
                putExtra("birthYear", student.birthYear)
                putExtra("paidCash", student.paidCash)
                putExtra("paidBank", student.paidBank)
                putExtra("paid", student.paid)
                putExtra("total", student.total)
                putExtra("remaining", student.remaining)
                putExtra("phoneNumber", student.phoneNumber)
                putExtra("notes", student.notes)
                putExtra("lastModified", student.lastModified)
                putExtra("addedIn", student.addedIn)

                // ✅ إرسال طريقة الدفع وتاريخ الدفع إن وُجدت دفعة
                putExtra("paymentMethod", lastPayment?.paymentMethod ?: "لا يوجد")
                putExtra("paymentDate", lastPayment?.paymentDate ?: "")
            }

            context.startActivity(intent)
        }

    }

    override fun getItemCount(): Int = studentList.size

    @SuppressLint("NotifyDataSetChanged")
    fun getStudentAt(position: Int): Student? {
        return if (position in 0 until studentList.size) studentList[position] else null
    }

    fun removeStudent(student: Student) {
        val index = studentList.indexOf(student)
        if (index != -1) {
            studentList.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun restoreStudent(position: Int, student: Student) {
        if (position in 0..studentList.size) {
            studentList.add(position, student)
            notifyItemInserted(position)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newList: List<Student>) {
        studentList.clear()
        studentList.addAll(newList)
        notifyDataSetChanged()
    }
}