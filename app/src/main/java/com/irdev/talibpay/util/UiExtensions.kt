package com.irdev.talibpay.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.irdev.talibpay.R
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** يضيف هوامش أشرطة النظام للجذر (مع enableEdgeToEdge). */
fun applySystemBarInsets(root: View) {
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        insets
    }
}

/** يجمع الـ Flow فقط أثناء ظهور الشاشة. */
fun <T> LifecycleOwner.collectWhileStarted(flow: Flow<T>, action: suspend (T) -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}

fun Context.toast(@StringRes message: Int, long: Boolean = false) {
    Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

/** يعيد النص بعد قصّ المسافات، أو null مع إظهار خطأ إذا كان فارغًا. */
fun EditText.requireText(): String? {
    val value = text?.toString()?.trim().orEmpty()
    if (value.isEmpty()) {
        error = context.getString(R.string.enter_value)
        return null
    }
    return value
}

/** مثل [requireText] لكن دون قصّ المسافات (لكلمات المرور). */
fun EditText.requireRawText(): String? {
    val value = text?.toString().orEmpty()
    if (value.isEmpty()) {
        error = context.getString(R.string.enter_value)
        return null
    }
    return value
}

/** نافذة تحميل غير قابلة للإلغاء. */
class LoadingDialog(activity: Activity) {
    @SuppressLint("InflateParams")
    private val dialog = Dialog(activity).apply {
        setContentView(LayoutInflater.from(activity).inflate(R.layout.progress_bar_dialog, null))
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    fun setVisible(visible: Boolean) {
        if (visible && !dialog.isShowing) dialog.show()
        if (!visible && dialog.isShowing) dialog.dismiss()
    }
}

fun Context.showYearPicker(onPicked: (Int) -> Unit) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_year_picker, null)
    val numberPicker = dialogView.findViewById<NumberPicker>(R.id.yearPicker).apply {
        minValue = 1980
        maxValue = currentYear
        value = currentYear
    }
    AlertDialog.Builder(this)
        .setTitle(getString(R.string.select_year_of_birth))
        .setView(dialogView)
        .setPositiveButton(getString(R.string.ok)) { _, _ -> onPicked(numberPicker.value) }
        .setNegativeButton(getString(R.string.cancel), null)
        .show()
}

/** يعرض منتقي التاريخ ويعيد التاريخ بصيغة dd/MM/yyyy. */
fun Context.showDatePicker(onPicked: (String) -> Unit) {
    val calendar = Calendar.getInstance()
    DatePickerDialog(
        this,
        { _, year, month, day -> onPicked(String.format(Locale.US, "%02d/%02d/%04d", day, month + 1, year)) },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

/** يعرض الطابع الزمني بصيغة الجهاز، أو النص القديم إن لم يوجد طابع. */
fun Context.formatTimestamp(millis: Long, legacy: String = ""): String {
    if (millis <= 0) return legacy
    val date = Date(millis)
    return DateFormat.getMediumDateFormat(this).format(date) + " " +
            DateFormat.getTimeFormat(this).format(date)
}

/** سحب العناصر: يمين = تعديل، يسار = حذف. */
fun RecyclerView.attachSwipeActions(
    onEdit: (position: Int) -> Unit,
    onDelete: (position: Int) -> Unit
) {
    val editColor = ContextCompat.getColor(context, R.color.swipe_edit)
    val deleteColor = ContextCompat.getColor(context, R.color.swipe_delete)

    ItemTouchHelper(object :
        ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ) = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            // نعيد الصف لمكانه دائمًا؛ الحذف الفعلي يحدّث القائمة من البيانات
            adapter?.notifyItemChanged(position)
            if (direction == ItemTouchHelper.RIGHT) onEdit(position) else onDelete(position)
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                val editing = dX > 0
                RecyclerViewSwipeDecorator.Builder(
                    c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive
                )
                    .addBackgroundColor(if (editing) editColor else deleteColor)
                    .addActionIcon(if (editing) R.drawable.ic_edit else R.drawable.ic_delete)
                    .create()
                    .decorate()
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }).attachToRecyclerView(this)
}
