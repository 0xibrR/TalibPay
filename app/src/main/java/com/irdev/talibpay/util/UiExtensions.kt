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
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputLayout
import com.irdev.talibpay.R
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.YearMonth
import com.irdev.talibpay.model.Charge
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * يضيف padding لأشرطة النظام (system bars insets) على الـ root، مع enableEdgeToEdge.
 * في الشاشات التي فيها AppBarLayout (fitsSystemWindows) يتولى الـ AppBar الهامش العلوي،
 * فنمرر applyTop = false ونترك الـ insets تصل إليه.
 */
fun applySystemBarInsets(root: View, applyTop: Boolean = true) {
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        v.setPadding(bars.left, if (applyTop) bars.top else 0, bars.right, bars.bottom)
        insets
    }
}

/** يربط MaterialToolbar كـ action bar، مع زر رجوع اختياري. */
fun AppCompatActivity.setupToolbar(toolbar: MaterialToolbar, showUp: Boolean = true) {
    setSupportActionBar(toolbar)
    if (showUp) {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }
}

/** المبلغ مع رمز العملة المختارة، بتنسيق لغة الجهاز. */
fun Context.money(value: Double): String {
    CurrencyPrefs.current(this) // يحمّل العملة إن لم تكن محمّلة
    return formatAmount(value)
}

/** يعرض رمز العملة داخل حقل المبلغ (suffix). */
fun TextInputLayout.showCurrency() {
    suffixText = CurrencyPrefs.current(context).symbol
}

/** اسم الـ charge، أو "الرسوم" للـ legacy charge الذي ليس له اسم. */
fun Context.dueTitle(charge: Charge): String {
    if (charge.title.isNotBlank()) return charge.title
    // Monthly charge تلقائي: اسمه من شهره بلغة التطبيق الحالية
    val month = YearMonth.parse(charge.period)
        ?: return getString(R.string.due_legacy_title)
    return getString(R.string.due_title_month, monthName(month))
}

/** اسم الشهر والسنة بلغة التطبيق (مثل "تشرين الثاني 2026" أو "November 2026"). */
fun Context.monthName(month: YearMonth): String {
    val locale = ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
    val calendar = Calendar.getInstance().apply {
        clear()
        set(month.year, month.month - 1, 1)
    }
    return SimpleDateFormat("MMMM yyyy", locale).format(calendar.time)
}

/** نسبة المدفوع من الرسوم (0..100). */
fun paidPercent(paid: Double, total: Double): Int =
    if (total <= 0) 0 else ((paid / total) * 100).toInt().coerceIn(0, 100)

/** يجمع الـ Flow فقط أثناء ظهور الشاشة (repeatOnLifecycle STARTED). */
fun <T> LifecycleOwner.collectWhileStarted(flow: Flow<T>, action: suspend (T) -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}

/** يعيد النص بعد trim، أو null مع إظهار خطأ إذا كان فارغًا. */
fun EditText.requireText(): String? {
    val value = text?.toString()?.trim().orEmpty()
    if (value.isEmpty()) {
        error = context.getString(R.string.enter_value)
        return null
    }
    return value
}

/** مبلغ اختياري: الحقل الفارغ = 0، والقيمة غير الصالحة تُظهر خطأ وتعيد null. */
fun EditText.optionalAmount(): Double? {
    val raw = text?.toString()?.trim().orEmpty()
    if (raw.isEmpty()) return 0.0
    val value = Money.parse(raw)
    if (value == null) error = context.getString(R.string.invalid_amount)
    return value
}

/** مثل [requireText] لكن بدون trim (لكلمات المرور). */
fun EditText.requireRawText(): String? {
    val value = text?.toString().orEmpty()
    if (value.isEmpty()) {
        error = context.getString(R.string.enter_value)
        return null
    }
    return value
}

/**
 * Dialog لتصميم مخصص (card بحواف دائرية): window شفافة بعرض الشاشة
 * حتى لا تظهر خلفية مربعة خلف الـ card.
 */
fun Context.cardDialog(content: View, cancelable: Boolean = true): Dialog =
    Dialog(this).apply {
        setContentView(content)
        setCancelable(cancelable)
        setCanceledOnTouchOutside(cancelable)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

/** Loading dialog غير قابل للإلغاء. */
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

/** تاريخ اليوم بصيغة dd/MM/yyyy (نفس صيغة الـ date picker). */
fun todayDate(): String = String.format(
    Locale.US, "%1\$td/%1\$tm/%1\$tY", Calendar.getInstance()
)

/** يعرض الـ date picker ويعيد التاريخ بصيغة dd/MM/yyyy. */
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

/** يعرض الـ timestamp بصيغة الجهاز، أو النص الـ legacy إن لم يوجد timestamp. */
fun Context.formatTimestamp(millis: Long, legacy: String = ""): String {
    if (millis <= 0) return legacy
    val date = Date(millis)
    return DateFormat.getMediumDateFormat(this).format(date) + " " +
            DateFormat.getTimeFormat(this).format(date)
}

/** Swipe action في اتجاه واحد: الـ icon ولون الخلفية وما يحدث عند إتمام السحب. */
data class SwipeAction(
    @param:DrawableRes val icon: Int,
    @param:ColorRes val color: Int,
    val onSwiped: (position: Int) -> Unit
)

/** Swipe العناصر يمينًا أو يسارًا (ItemTouchHelper)؛ الاتجاه بلا action (null) معطّل. */
fun RecyclerView.attachSwipeActions(right: SwipeAction?, left: SwipeAction?) {
    val directions = (if (right != null) ItemTouchHelper.RIGHT else 0) or (if (left != null) ItemTouchHelper.LEFT else 0)

    ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, directions) {

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ) = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            // نعيد الـ row لمكانه دائمًا؛ التغيير الفعلي يحدّث القائمة من البيانات
            adapter?.notifyItemChanged(position)
            (if (direction == ItemTouchHelper.RIGHT) right else left)?.onSwiped?.invoke(position)
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
            val action = if (dX > 0) right else left
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && action != null) {
                RecyclerViewSwipeDecorator.Builder(
                    c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive
                )
                    .addBackgroundColor(ContextCompat.getColor(context, action.color))
                    .addActionIcon(action.icon)
                    .setActionIconTint(Color.WHITE)
                    .create()
                    .decorate()
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }).attachToRecyclerView(this)
}
