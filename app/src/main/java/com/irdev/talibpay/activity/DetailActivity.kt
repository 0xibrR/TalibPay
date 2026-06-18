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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.adapter.PaymentAdapter
import com.irdev.talibpay.R
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import java.io.File
import java.io.FileOutputStream

@Suppress("DEPRECATION")
class DetailActivity : AppCompatActivity() {

    private val paymentList = ArrayList<Payment>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var customAdapter: PaymentAdapter

    @SuppressLint("DefaultLocale", "SetTextI18n", "InflateParams", "UseKtx", "MissingInflatedId")
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

        // استقبال البيانات من الـ Intent
        val id = intent.getStringExtra("id") ?: ""
        val name = intent.getStringExtra("name") ?: ""
        val birthYear = intent.getStringExtra("birthYear") ?: ""
        val paidCash = intent.getDoubleExtra("paidCash", 0.0)
        val paidBank = intent.getDoubleExtra("paidBank", 0.0)
        val paid = intent.getDoubleExtra("paid", 0.0)
        val total = intent.getDoubleExtra("total", 0.0)
        val remaining = intent.getDoubleExtra("remaining", 0.0)
        val paymentMethod = intent.getStringExtra("paymentMethod") ?: ""
        val paymentDate = intent.getStringExtra("paymentDate") ?: ""
        val phoneNumber = intent.getStringExtra("phoneNumber") ?: ""
        val notes = intent.getStringExtra("notes") ?: ""
        val lastModified = intent.getStringExtra("lastModified") ?: ""
        val addedIn = intent.getStringExtra("addedIn") ?: ""

        // ربط عناصر الواجهة
        val tvStudentName = findViewById<TextView>(R.id.tvName)
        val rowBirthYear = findViewById<View>(R.id.rowBirthYear)
        val rowPhone = findViewById<View>(R.id.rowPhone)
        val rowPaymentDate = findViewById<View>(R.id.rowPaymentDate)
        val rowPaidCash = findViewById<View>(R.id.rowPaidCash)
        val rowPaidBank = findViewById<View>(R.id.rowPaidBank)
        val rowPaid = findViewById<View>(R.id.rowPaid)
        val rowTotalAmount = findViewById<View>(R.id.rowTotalAmount)
        val rowRemaining = findViewById<View>(R.id.rowRemaining)
        val rowPaymentMethod = findViewById<View>(R.id.rowPaymentMethod)
        val rowAdded = findViewById<View>(R.id.rowAdded)
        val rowLastModified = findViewById<View>(R.id.rowLastModified)
        val tvNotes = findViewById<TextView>(R.id.tvNotes)
        val fabAddPayment = findViewById<FloatingActionButton>(R.id.fabAddPayment)
        recyclerView = findViewById(R.id.recyclerViewPayment)

        // عرض البيانات
        tvStudentName.text = name
        rowBirthYear.findViewById<TextView>(R.id.tvValue).text =
            getString(R.string.year_of_birth) + ": " + birthYear
        rowPhone.findViewById<TextView>(R.id.tvValue).text =
            getString(R.string.phone_number) + ": " + phoneNumber
        rowPaymentDate.findViewById<TextView>(R.id.tvValue).text =
            getString(R.string.payment_date) + ": " + paymentDate
        rowPaidCash.findViewById<TextView>(R.id.tvValue).text =
            getString(R.string.paid_cash_label) + " " + String.format("%.2f", paidCash)
        rowPaidBank.findViewById<TextView>(R.id.tvValue).text =
            getString(R.string.paid_bank_label) + " " + String.format("%.2f", paidBank)
        rowPaid.findViewById<TextView>(R.id.tvValue).text =
            getString(R.string.paid_total) + " " + String.format("%.2f", paid)
        rowTotalAmount.findViewById<TextView>(R.id.tvValue).text =
            getString(R.string.total_amount_label) + " " + String.format("%.2f", total)
        rowRemaining.findViewById<TextView>(R.id.tvValue).text =
            getString(R.string.remaining_total) + " " + String.format("%.2f", remaining)
        rowPaymentMethod.findViewById<TextView>(R.id.tvValue).text =
            getString(R.string.payment_method) + ": " + paymentMethod
        rowAdded.findViewById<TextView>(R.id.tvValue).text =
            getString(R.string.added) + ": " + addedIn
        rowLastModified.findViewById<TextView>(R.id.tvValue).text =
            getString(R.string.last_modified) + ": " + lastModified
        tvNotes.text = getString(R.string.notes) + ": " + notes

        fabAddPayment.setOnClickListener {
            val intent = Intent(this, AddPayment::class.java)
            intent.putExtra("id", id)
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

            @SuppressLint("UseKtx", "NotifyDataSetChanged")
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition

                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        val position = viewHolder.adapterPosition
                        AlertDialog.Builder(this@DetailActivity)
                            .setTitle(getString(R.string.delete_title_payment, ""))
                            .setMessage(getString(R.string.delete_message_payment, ""))
                            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                                val currentUser = FirebaseAuth.getInstance().currentUser
                                currentUser?.let { user ->
                                    val userId = user.uid
                                    val studentId = intent.getStringExtra("id").orEmpty()
                                    val paymentId = paymentList.getOrNull(position)?.id

                                    if (studentId.isNotEmpty() && !paymentId.isNullOrEmpty()) {
                                        FirebaseDatabase.getInstance().getReference("TalibPay")
                                            .child(userId).child("students").child(studentId)
                                            .child("payments").child(paymentId)
                                            .removeValue()
                                            .addOnSuccessListener {

                                            }
                                            .addOnFailureListener { e ->
                                                Snackbar.make(
                                                    findViewById(android.R.id.content),
                                                    "${getString(R.string.deleted_failed)} ${e.message}",
                                                    Snackbar.LENGTH_LONG
                                                ).show()
                                                customAdapter.notifyItemChanged(position)
                                            }
                                    } else {
                                        customAdapter.notifyItemChanged(position)
                                    }
                                } ?: run {
                                    paymentList.removeAt(position)
                                    customAdapter.notifyItemRemoved(position)
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

                    ItemTouchHelper.RIGHT -> {
                        val intent =
                            Intent(this@DetailActivity, EditPaymentActivity::class.java).apply {
                                putExtra("studentId", id)
                                putExtra("id", paymentList[position].id)
                                putExtra("cash", paymentList[position].cash)
                                putExtra("bank", paymentList[position].bank)
                                putExtra("total", paymentList[position].total)
                                putExtra("paymentMethod", paymentList[position].paymentMethod)
                                putExtra("paymentDate", paymentList[position].paymentDate)
                                putExtra("note", paymentList[position].note)
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.detail_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.menu_edit -> {
                val intent = Intent(this, EditActivity::class.java).apply {
                    putExtra("id", intent.getStringExtra("id"))
                    putExtra("name", intent.getStringExtra("name"))
                    putExtra("birthYear", intent.getStringExtra("birthYear"))
                    putExtra("paidCash", intent.getDoubleExtra("paidCash", 0.0))
                    putExtra("paidBank", intent.getDoubleExtra("paidBank", 0.0))
                    putExtra("paid", intent.getDoubleExtra("paid", 0.0))
                    putExtra("total", intent.getDoubleExtra("total", 0.0))
                    putExtra("remaining", intent.getDoubleExtra("remaining", 0.0))
                    putExtra("paymentMethod", intent.getStringExtra("paymentMethod"))
                    putExtra("paymentDate", intent.getStringExtra("paymentDate"))
                    putExtra("phoneNumber", intent.getStringExtra("phoneNumber"))
                    putExtra("notes", intent.getStringExtra("notes"))
                    putExtra("addedIn", intent.getStringExtra("addedIn"))

                }
                startActivity(intent)
                return true
            }

            R.id.menu_delete -> {

                val builderAlertDialog = AlertDialog.Builder(this)
                    .setTitle(
                        getString(
                            R.string.delete_title_with_name,
                            intent.getStringExtra("name")
                        )
                    )
                    .setMessage(
                        getString(
                            R.string.delete_message_with_name,
                            intent.getStringExtra("name")
                        )
                    )

                builderAlertDialog.setPositiveButton(getString(R.string.ok)) { dialog: DialogInterface, _: Int ->

                    val currentUser = FirebaseAuth.getInstance().currentUser
                    currentUser?.let {
                        val userId = it.uid

                        // حذف الطالب من Firebase
                        FirebaseDatabase.getInstance().getReference("TalibPay")
                            .child(userId).child("students")
                            .child(intent.getStringExtra("id").toString()).removeValue()

                            .addOnSuccessListener {
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Snackbar.make(
                                    findViewById(android.R.id.content),
                                    "${getString(R.string.deleted_failed)} ${e.message}",
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }
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
                true
            }

            R.id.menu_share -> {
                val name = intent.getStringExtra("name") ?: ""
                val birthYear = intent.getStringExtra("birthYear") ?: ""
                val paidCash = intent.getDoubleExtra("paidCash", 0.0)
                val paidBank = intent.getDoubleExtra("paidBank", 0.0)
                val paid = intent.getDoubleExtra("paid", 0.0)
                val total = intent.getDoubleExtra("total", 0.0)
                val remaining = intent.getDoubleExtra("remaining", 0.0)
                val paymentMethod = intent.getStringExtra("paymentMethod") ?: ""
                val paymentDate = intent.getStringExtra("paymentDate") ?: ""
                val phoneNumber = intent.getStringExtra("phoneNumber") ?: ""
                val notes = intent.getStringExtra("notes") ?: ""
                val lastModified = intent.getStringExtra("lastModified") ?: ""
                val addedIn = intent.getStringExtra("addedIn") ?: ""

                val shareText = """
    ${getString(R.string.share_name, name)}
    ${getString(R.string.share_birth_year, birthYear)}
    ${getString(R.string.share_phone, phoneNumber)}
    ${getString(R.string.share_payment_date, paymentDate)}

    ${getString(R.string.share_paid_cash, paidCash)}
    ${getString(R.string.share_paid_bank, paidBank)}
    ${getString(R.string.share_paid_total, paid)}
    ${getString(R.string.share_total_amount, total)}
    ${getString(R.string.share_remaining, remaining)}
    ${getString(R.string.share_payment_method, paymentMethod)}

    ${getString(R.string.share_notes, notes)}
    ${getString(R.string.share_added_at, addedIn)}
    ${getString(R.string.share_last_modified, lastModified)}
""".trimIndent()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, name))
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
                val outputStream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
                outputStream.close()

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

            }

            else -> false
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()

        val studentId = intent.getStringExtra("id") ?: return
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid

        val dbRef = FirebaseDatabase.getInstance()
            .getReference("TalibPay")
            .child(userId)
            .child("students")
            .child(studentId)
            .child("payments")

        dbRef.addValueEventListener(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(snapshot: DataSnapshot) {
                paymentList.clear()
                for (paymentSnap in snapshot.children) {
                    val payment = paymentSnap.getValue(Payment::class.java)
                    payment?.let { paymentList.add(it) }
                }

                // ترتيب الدفعات من الأحدث إلى الأقدم حسب التاريخ (اختياري)
//                paymentList.sortByDescending { it.paymentDate }
                paymentList.reverse()


                customAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@DetailActivity,
                    "حدث خطأ أثناء تحميل الدفعات",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        dbRef.keepSynced(true)

    }

}
