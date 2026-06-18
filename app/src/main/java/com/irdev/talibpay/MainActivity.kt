package com.irdev.talibpay

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
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
import com.ncorti.slidetoact.SlideToActView
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private val studentList = ArrayList<Student>()
    private val allStudents = ArrayList<Student>()
    private lateinit var customAdapter: StudentAdapter
    private lateinit var recyclerView: RecyclerView


    @SuppressLint("MissingInflatedId", "UseKtx", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // ربط عناصر الواجهة
        recyclerView = findViewById(R.id.recyclerView)
        val btnAddStudent = findViewById<FloatingActionButton>(R.id.newStudentFAB)
        val paymentSummary = findViewById<LinearLayout>(R.id.payment_summary_layout)

        paymentSummary.setOnClickListener {
            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.tooltip_payment_summary, null)

            val popup = PopupWindow(
                view,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )
            popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            popup.elevation = 10f

            // عرض البوب أب تحت الأيقونة
            popup.showAsDropDown(paymentSummary, -20, 0)
        }

        // إعداد RecyclerView
        customAdapter = StudentAdapter(studentList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = customAdapter

        // زر الإضافة
        btnAddStudent.setOnClickListener {
            startActivity(Intent(this, AddActivity::class.java))
        }


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
                val student = customAdapter.getStudentAt(position) ?: return

                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        val builder = AlertDialog.Builder(this@MainActivity)
                            .setTitle(getString(R.string.delete_title_with_name, student.name))
                            .setMessage(getString(R.string.delete_message_with_name, student.name))

                        builder.setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                            val currentUser = FirebaseAuth.getInstance().currentUser
                            currentUser?.let { user ->
                                val userId = user.uid

                                // حذف من Firebase
                                FirebaseDatabase.getInstance().getReference("TalibPay")
                                    .child(userId).child("students").child(student.id).removeValue()
                                    .addOnSuccessListener {
                                        showUndoSnackbar(student, position)
                                    }
                                    .addOnFailureListener { e ->
                                        Snackbar.make(
                                            findViewById(android.R.id.content),
                                            "${getString(R.string.deleted_failed)} ${e.message}",
                                            Snackbar.LENGTH_LONG
                                        ).show()
                                    }

                                // حذف من كافة القوائم
                                customAdapter.removeStudent(student)
                                allStudents.remove(student)
                                studentList.remove(student)
                            }
                            dialog.dismiss()
                        }

                        builder.setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                            customAdapter.notifyItemChanged(position)
                            dialog.dismiss()
                        }

                        val dialog = builder.create()
                        dialog.setCanceledOnTouchOutside(false)
                        dialog.setCancelable(false)
                        dialog.show()
                    }

                    ItemTouchHelper.RIGHT -> {
                        val intent = Intent(this@MainActivity, EditActivity::class.java).apply {
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
                            putExtra("addedIn", student.addedIn)
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

        loadData()

    }

    override fun onStart() {
        super.onStart()
        loadData()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showUndoSnackbar(student: Student, removedPos: Int) {
        val snackbar = Snackbar.make(
            findViewById(android.R.id.content),
            getString(R.string.deleted_successfully_with_name, student.name),
            Snackbar.LENGTH_LONG
        )

        snackbar.setAction(getString(R.string.undo)) {
            // 1. أعد الطالب إلى القوائم
            allStudents.add(removedPos, student)
            studentList.add(removedPos, student)
            customAdapter.restoreStudent(removedPos, student)

            // 2. Firebase
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    getString(R.string.user_not_found),
                    Snackbar.LENGTH_LONG
                ).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return@setAction
            }

            val userId = currentUser.uid
            FirebaseDatabase.getInstance().getReference("TalibPay")
                .child(userId).child("students").child(student.id).setValue(student)
                .addOnSuccessListener {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        getString(R.string.restored_successfully_with_name, student.name),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                .addOnFailureListener {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        getString(R.string.restored_failed_with_name, student.name),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
        }

        snackbar.setAnchorView(R.id.newStudentFAB).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        val searchItem = menu.findItem(R.id.search_menu_item)
        val searchView = searchItem.actionView as SearchView

        // إعداد SearchView
        searchView.queryHint = getString(R.string.search_hint) // نص تلميحي
        searchView.maxWidth = Integer.MAX_VALUE // لجعل شريط البحث يأخذ أقصى عرض ممكن

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            private var searchRunnable: Runnable? = null
            private val handler = android.os.Handler(Looper.getMainLooper())

            override fun onQueryTextChange(newText: String): Boolean {
                searchRunnable?.let { handler.removeCallbacks(it) }
                searchRunnable = Runnable { filterStudents(newText) }
                handler.postDelayed(searchRunnable!!, 300) // تأخير 300 مللي ثانية
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                // معالجة عند الضغط على زر البحث في الكيبورد
                filterStudents(query)
                return true
            }
        })

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                customAdapter.updateData(ArrayList(allStudents))
                return true
            }
        })


        return true
    }

    @SuppressLint("UseKtx")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings_menu_item -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            R.id.deleteAll_menu_item -> {
                val inflater = LayoutInflater.from(this)
                val view = inflater.inflate(R.layout.alert_dialog_delete_all, null)
                val slide = view.findViewById<SlideToActView>(R.id.slideConfirm)
                val btnCancel = view.findViewById<ImageView>(R.id.img_btn_cancel_dialog)
                val dialog = AlertDialog.Builder(this)
                    .setView(view)
                    .create()

                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                dialog.setCanceledOnTouchOutside(false)
                dialog.setCancelable(false)
                dialog.show()

                slide.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
                    @SuppressLint("NotifyDataSetChanged")
                    override fun onSlideComplete(view: SlideToActView) {
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        if (currentUser != null) {
                            val userId = currentUser.uid
                            val databaseRef =
                                FirebaseDatabase.getInstance().getReference("TalibPay")
                                    .child(userId).child("students")

                            databaseRef.removeValue()
                                .addOnSuccessListener {
                                    Snackbar.make(
                                        findViewById(android.R.id.content),
                                        getString(R.string.delete_all_note),
                                        Snackbar.LENGTH_LONG
                                    ).show()
                                    studentList.clear()
                                    customAdapter.notifyDataSetChanged()
                                }
                                .addOnFailureListener { e ->
                                    Snackbar.make(
                                        findViewById(android.R.id.content),
                                        "${getString(R.string.deleted_failed)} ${e.message}",
                                        Snackbar.LENGTH_LONG
                                    ).show()
                                }
                        } else {
                            Snackbar.make(
                                findViewById(android.R.id.content),
                                getString(R.string.user_not_found),
                                Snackbar.LENGTH_LONG
                            ).show()
                        }

                        dialog.dismiss()
                    }
                }

                btnCancel.setOnClickListener {
                    dialog.dismiss()
                }
            }

        }
        return super.onOptionsItemSelected(item)
    }

    private fun filterStudents(query: String) {
        val filteredList = if (query.isBlank()) {
            ArrayList(allStudents)
        } else {
            allStudents.filter { student ->
                student.name.contains(query, ignoreCase = true) ||
                        student.phoneNumber.contains(query, ignoreCase = true) ||
                        student.paidCash.toString().contains(query) ||
                        student.paidBank.toString().contains(query) ||
                        student.remaining.toString().contains(query)
            }.toCollection(ArrayList())
        }

        customAdapter.updateData(filteredList)
    }

    private fun loadData() {
        val emptyLayout = findViewById<LinearLayout>(R.id.linearLayout_empty)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar_notes)
        val txtCashValue = findViewById<TextView>(R.id.txtCashValue)
        val txtBankValue = findViewById<TextView>(R.id.txtBankValue)
        val txtPaidValue = findViewById<TextView>(R.id.txtPaidValue)
        val txtRemainingValue = findViewById<TextView>(R.id.txtRemainingValue)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, getString(R.string.user_not_found), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val userId = currentUser.uid
        val databaseRef = FirebaseDatabase.getInstance().getReference("TalibPay")
            .child(userId).child("students")

        progressBar.isVisible = true
        emptyLayout.isVisible = false
        recyclerView.isVisible = false

        databaseRef.addValueEventListener(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged", "SetTextI18n")
            override fun onDataChange(snapshot: DataSnapshot) {
                allStudents.clear()
                studentList.clear()

                var totalCash = 0.0
                var totalBank = 0.0
                var totalPaid = 0.0
                var totalRemaining = 0.0

                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    emptyLayout.isVisible = true
                    recyclerView.isVisible = false
                } else {
                    for (stuSnap in snapshot.children) {
                        val student = stuSnap.getValue(Student::class.java)
                        student?.let {
                            allStudents.add(it)
                            studentList.add(it)

                            totalCash += it.paidCash
                            totalBank += it.paidBank
                            totalPaid += it.paid
                            totalRemaining += it.remaining
                        }
                    }

                    emptyLayout.isVisible = false
                    recyclerView.isVisible = true
                }

                txtCashValue.text = totalCash.toString()
                txtBankValue.text = totalBank.toString()
                txtPaidValue.text = totalPaid.toString()
                txtRemainingValue.text = totalRemaining.toString()

                progressBar.isVisible = false
                customAdapter.notifyDataSetChanged()

                // ✅ تحميل آخر دفعة لكل طالب
                for (student in studentList) {
                    val paymentRef = FirebaseDatabase.getInstance().getReference("TalibPay")
                        .child(userId)
                        .child("students")
                        .child(student.id)
                        .child("payments")

                    paymentRef.orderByKey().limitToLast(1)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                for (paymentSnap in snapshot.children) {
                                    val lastPayment = paymentSnap.getValue(Payment::class.java)
                                    if (lastPayment != null) {
                                        customAdapter.lastPaymentsMap[student.id] = lastPayment
                                        val index = studentList.indexOfFirst { it.id == student.id }
                                        if (index != -1) {
                                            customAdapter.notifyItemChanged(index)
                                        }
                                    }
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e("Firebase", "فشل تحميل الدفعة الأخيرة: ${error.message}")
                            }
                        })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progressBar.isVisible = false
                Toast.makeText(this@MainActivity, "Error: ${error.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        })

        databaseRef.keepSynced(true)
    }

}
