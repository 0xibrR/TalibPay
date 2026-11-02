package com.irdev.talibpay.activity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.adapter.PaymentAdapter
import com.irdev.talibpay.R
import com.irdev.talibpay.data.recalculateStudentTotals
import com.irdev.talibpay.data.studentRef
import com.irdev.talibpay.model.Student
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
class DetailActivity : AppCompatActivity() {

    private val paymentList = ArrayList<Payment>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var customAdapter: PaymentAdapter

    private lateinit var studentId: String
    private var student = Student()
    private var lastPayment: Payment? = null

    private var dbRef: DatabaseReference? = null
    private var studentListener: ValueEventListener? = null

    @SuppressLint("UseKtx", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detail)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        title = getString(R.string.details_student)

        studentId = intent.getStringExtra("id").orEmpty()
        if (studentId.isEmpty()) {
            finish()
            return
        }

        val fabAddPayment = findViewById<FloatingActionButton>(R.id.fabAddPayment)
        recyclerView = findViewById(R.id.recyclerViewPayment)

        fabAddPayment.setOnClickListener {
            val intent = Intent(this, AddPayment::class.java)
            intent.putExtra("id", studentId)
            startActivity(intent)
            finish()
        }

        // إعداد RecyclerView
        customAdapter = PaymentAdapter(paymentList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = customAdapter

        val itemTouchHelper = ItemTouchHelper(object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val payment = paymentList.getOrNull(position) ?: return

                when (direction) {
                    ItemTouchHelper.LEFT -> confirmDeletePayment(payment, position)

                    ItemTouchHelper.RIGHT -> {
                        // إعادة الصف لمكانه، ثم فتح شاشة التعديل
                        customAdapter.notifyItemChanged(position)
                        val intent =
                            Intent(this@DetailActivity, EditPaymentActivity::class.java).apply {
                                putExtra("studentId", studentId)
                                putExtra("id", payment.id)
                                putExtra("cash", payment.cash)
                                putExtra("bank", payment.bank)
                                putExtra("total", payment.total)
                                putExtra("paymentMethod", payment.paymentMethod)
                                putExtra("paymentDate", payment.paymentDate)
                                putExtra("note", payment.note)
                            }
                        startActivity(intent)
                    }
                }
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
                    if (dX > 0) {
                        RecyclerViewSwipeDecorator.Builder(
                            c,
                            recyclerView,
                            viewHolder,
                            dX,
                            dY,
                            actionState,
                            isCurrentlyActive
                        )
                            .addBackgroundColor("#2196F3".toColorInt())
                            .addActionIcon(R.drawable.ic_edit)
                            .create()
                            .decorate()
                    } else {
                        RecyclerViewSwipeDecorator.Builder(
                            c,
                            recyclerView,
                            viewHolder,
                            dX,
                            dY,
                            actionState,
                            isCurrentlyActive
                        )
                            .addBackgroundColor("#F44336".toColorInt())
                            .addActionIcon(R.drawable.ic_delete)
                            .create()
                            .decorate()
                    }
                }
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        })

        itemTouchHelper.attachToRecyclerView(recyclerView)

    }

    private fun confirmDeletePayment(payment: Payment, position: Int) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_title_payment, ""))
            .setMessage(getString(R.string.delete_message_payment, ""))
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                val ref = dbRef
                if (ref == null || payment.id.isEmpty()) {
                    customAdapter.notifyItemChanged(position)
                } else {
                    ref.child("payments").child(payment.id).removeValue()
                        .addOnSuccessListener {
                            // تحديث مجاميع الطالب بعد حذف الدفعة
                            recalculateStudentTotals(ref, getCurrentTime()) { ok ->
                                if (!ok) showSnackbar(getString(R.string.failed_to_update_student))
                            }
                        }
                        .addOnFailureListener {
                            showSnackbar(getString(R.string.deleted_failed))
                            customAdapter.notifyItemChanged(position)
                        }
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                customAdapter.notifyItemChanged(position)
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    @SuppressLint("DefaultLocale", "SetTextI18n")
    private fun bindStudent() {
        val paymentMethod = lastPayment?.paymentMethod.orEmpty()
        val paymentDate = lastPayment?.paymentDate.orEmpty()

        fun row(id: Int) = findViewById<View>(id).findViewById<TextView>(R.id.tvValue)

        findViewById<TextView>(R.id.tvName).text = student.name
        row(R.id.rowBirthYear).text =
            getString(R.string.year_of_birth) + ": " + student.birthYear
        row(R.id.rowPhone).text =
            getString(R.string.phone_number) + ": " + student.phoneNumber
        row(R.id.rowPaymentDate).text =
            getString(R.string.payment_date) + ": " + paymentDate
        row(R.id.rowPaidCash).text =
            getString(R.string.paid_cash_label) + " " + String.format("%.2f", student.paidCash)
        row(R.id.rowPaidBank).text =
            getString(R.string.paid_bank_label) + " " + String.format("%.2f", student.paidBank)
        row(R.id.rowPaid).text =
            getString(R.string.paid_total) + " " + String.format("%.2f", student.paid)
        row(R.id.rowTotalAmount).text =
            getString(R.string.total_amount_label) + " " + String.format("%.2f", student.total)
        row(R.id.rowRemaining).text =
            getString(R.string.remaining_total) + " " + String.format("%.2f", student.remaining)
        row(R.id.rowPaymentMethod).text =
            getString(R.string.payment_method) + ": " + paymentMethod
        row(R.id.rowAdded).text =
            getString(R.string.added) + ": " + student.addedIn
        row(R.id.rowLastModified).text =
            getString(R.string.last_modified) + ": " + student.lastModified
        findViewById<TextView>(R.id.tvNotes).text =
            getString(R.string.notes) + ": " + student.notes
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.detail_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.menu_edit -> {
                val intent = Intent(this, EditActivity::class.java).apply {
                    putExtra("id", studentId)
                    putExtra("name", student.name)
                    putExtra("birthYear", student.birthYear)
                    putExtra("phoneNumber", student.phoneNumber)
                    putExtra("notes", student.notes)
                }
                startActivity(intent)
                return true
            }

            R.id.menu_delete -> {

                val builderAlertDialog = AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_title_with_name, student.name))
                    .setMessage(getString(R.string.delete_message_with_name, student.name))

                builderAlertDialog.setPositiveButton(getString(R.string.ok)) { dialog: DialogInterface, _: Int ->

                    // حذف الطالب من Firebase
                    dbRef?.removeValue()
                        ?.addOnSuccessListener {
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            )
                            finish()
                        }
                        ?.addOnFailureListener {
                            showSnackbar(getString(R.string.deleted_failed))
                        }

                    dialog.dismiss()
                }

                builderAlertDialog.setNegativeButton(getString(R.string.cancel)) { dialog: DialogInterface, _: Int ->
                    dialog.dismiss()
                }

                val dialog = builderAlertDialog.create()
                dialog.setCanceledOnTouchOutside(false)
                dialog.setCancelable(false)
                dialog.show()
                return true
            }

            R.id.menu_share -> {
                val shareText = """
    ${getString(R.string.share_name, student.name)}
    ${getString(R.string.share_birth_year, student.birthYear)}
    ${getString(R.string.share_phone, student.phoneNumber)}
    ${getString(R.string.share_payment_date, lastPayment?.paymentDate.orEmpty())}

    ${getString(R.string.share_paid_cash, student.paidCash)}
    ${getString(R.string.share_paid_bank, student.paidBank)}
    ${getString(R.string.share_paid_total, student.paid)}
    ${getString(R.string.share_total_amount, student.total)}
    ${getString(R.string.share_remaining, student.remaining)}
    ${getString(R.string.share_payment_method, lastPayment?.paymentMethod.orEmpty())}

    ${getString(R.string.share_notes, student.notes)}
    ${getString(R.string.share_added_at, student.addedIn)}
    ${getString(R.string.share_last_modified, student.lastModified)}
""".trimIndent()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, student.name))
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }

                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        getString(R.string.share_chooser_title)
                    )
                )

                return true
            }

            R.id.menu_downloadNotification -> {
                // الحصول على الـ View المطلوب تحويله لصورة
                val viewToCapture = findViewById<View>(R.id.student_card_layout)

                // إنشاء صورة Bitmap من هذا الجزء
                val bitmap = createBitmap(viewToCapture.width, viewToCapture.height)
                val canvas = Canvas(bitmap)
                viewToCapture.draw(canvas)

                // حفظ الصورة مؤقتًا في cache
                val file = File(cacheDir, "student_card.png")
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                // مشاركة الصورة
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        getString(R.string.share_chooser_title)
                    )
                )
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // نستمع لعقدة الطالب كاملة: بياناته ودفعاته معًا، فيبقى العرض محدَّثًا بعد أي تعديل
        val ref = studentRef(currentUser.uid, studentId)
        val listener = object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    // الطالب حُذف (من هذا الجهاز أو غيره)
                    finish()
                    return
                }
                student = snapshot.getValue(Student::class.java) ?: Student()

                paymentList.clear()
                for (paymentSnap in snapshot.child("payments").children) {
                    paymentSnap.getValue(Payment::class.java)?.let { paymentList.add(it) }
                }
                // المفاتيح مرتبة زمنيًا؛ آخر دفعة هي الأحدث
                lastPayment = paymentList.lastOrNull()
                paymentList.reverse()

                bindStudent()
                customAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@DetailActivity,
                    getString(R.string.failed_to_load_payments),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        ref.addValueEventListener(listener)
        dbRef = ref
        studentListener = listener
    }

    override fun onStop() {
        super.onStop()
        studentListener?.let { dbRef?.removeEventListener(it) }
        studentListener = null
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("E, MMM dd, yyyy hh:mm:ss a", Locale.getDefault())
        return sdf.format(Date())
    }

}
