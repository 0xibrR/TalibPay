package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.irdev.talibpay.R
import com.irdev.talibpay.adapter.StudentAdapter
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.databinding.ActivityMainBinding
import com.irdev.talibpay.databinding.AlertDialogDeleteAllBinding
import com.irdev.talibpay.util.Extras
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.attachSwipeActions
import com.irdev.talibpay.util.cardDialog
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.money
import com.irdev.talibpay.util.paidPercent
import com.irdev.talibpay.util.reminderMessage
import com.irdev.talibpay.util.sendReminder
import com.irdev.talibpay.util.setupToolbar
import com.irdev.talibpay.viewmodel.MainUiState
import com.irdev.talibpay.viewmodel.MainViewModel
import com.irdev.talibpay.viewmodel.OverdueStudent
import com.irdev.talibpay.viewmodel.UiEvent
import com.ncorti.slidetoact.SlideToActView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val adapter = StudentAdapter { openDetail(it) }

    // الطلاب الذين أُرسل لهم تذكير في هذه الجلسة (لتمييزهم في القائمة)
    private val remindedIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main, applyTop = false)
        setupToolbar(binding.toolbar, showUp = false)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.attachSwipeActions(
            onEdit = { position -> openEdit(adapter.currentList[position]) },
            onDelete = { position -> confirmDelete(adapter.currentList[position]) }
        )
        // تصغير زر الإضافة أثناء التمرير للأسفل حتى لا يغطي البطاقات
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.newStudentFAB.shrink() else if (dy < 0) binding.newStudentFAB.extend()
            }
        })

        binding.newStudentFAB.setOnClickListener {
            startActivity(Intent(this, AddActivity::class.java))
        }
        binding.summary.btnSummaryInfo.setOnClickListener { showSummaryExplanation() }

        collectWhileStarted(viewModel.uiState, ::render)
        collectWhileStarted(viewModel.events, ::onEvent)
    }

    private fun render(state: MainUiState) {
        binding.progressBarNotes.isVisible = state.loading
        binding.linearLayoutEmpty.isVisible = !state.loading && !state.hasAnyStudent
        binding.recyclerView.isVisible = !state.loading && state.hasAnyStudent
        binding.tvStudentsCount.text = resources.getQuantityString(
            R.plurals.students_count, state.studentCount, state.studentCount
        )

        with(binding.summary) {
            txtPaidValue.text = money(state.summary.paid)
            txtCashValue.text = money(state.summary.cash)
            txtBankValue.text = money(state.summary.bank)
            txtRemainingValue.text = money(state.summary.remaining)
            val percent = paidPercent(state.summary.paid, state.summary.total)
            collectionProgress.setProgressCompat(percent, true)
            tvCollectionRate.text = getString(R.string.collection_rate, percent)
        }

        adapter.submitList(state.students)
    }

    private fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.StudentDeleted -> showUndoSnackbar(event.student)
            is UiEvent.Message -> snackbar(
                if (event.arg != null) getString(event.res, event.arg) else getString(event.res)
            )

            UiEvent.NotSignedIn -> {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }

            else -> Unit
        }
    }

    private fun openDetail(item: StudentWithPayments) {
        startActivity(
            Intent(this, DetailActivity::class.java)
                .putExtra(Extras.STUDENT_ID, item.student.id)
        )
    }

    private fun openEdit(item: StudentWithPayments) {
        startActivity(
            Intent(this, EditActivity::class.java)
                .putExtra(Extras.STUDENT_ID, item.student.id)
        )
    }

    private fun confirmDelete(item: StudentWithPayments) {
        val name = item.student.name
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_title_with_name, name))
            .setMessage(getString(R.string.delete_message_with_name, name))
            .setPositiveButton(R.string.ok) { _, _ -> viewModel.deleteStudent(item) }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(false)
            .show()
    }

    private fun showUndoSnackbar(item: StudentWithPayments) {
        Snackbar.make(
            binding.root,
            getString(R.string.deleted_successfully_with_name, item.student.name),
            Snackbar.LENGTH_LONG
        )
            .setAction(R.string.undo) { viewModel.undoDelete(item) }
            .setAnchorView(binding.newStudentFAB)
            .show()
    }

    private fun snackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAnchorView(binding.newStudentFAB)
            .show()
    }

    private fun showSummaryExplanation() {
        val message = listOf(
            R.string.payment_summary_popup_title2,
            R.string.payment_summary_cash,
            R.string.payment_summary_bank,
            R.string.payment_summary_total,
            R.string.payment_summary_remaining
        ).joinToString("\n\n") { getString(it) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.payment_summary_explained_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        val searchItem = menu.findItem(R.id.search_menu_item)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.maxWidth = Integer.MAX_VALUE
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                viewModel.setQuery(newText)
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                viewModel.setQuery(query)
                return true
            }
        })
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.setQuery("")
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings_menu_item -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }

            R.id.remindOverdue_menu_item -> {
                showOverdueReminders()
                return true
            }

            R.id.deleteAll_menu_item -> {
                showDeleteAllDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * قائمة المتأخرين: الضغط على طالب يفتح واتساب برسالة جاهزة له،
     * وتبقى القائمة مفتوحة ليُرسل للتالي بعد العودة، مع علامة ✓ لمن أُرسل له.
     */
    private fun showOverdueReminders() {
        val overdue = viewModel.uiState.value.overdue
        if (overdue.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reminder_overdue_title)
                .setMessage(R.string.reminder_none_overdue)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        fun label(item: OverdueStudent): String = getString(
            if (item.data.student.id in remindedIds) R.string.reminder_overdue_item_sent
            else R.string.reminder_overdue_item,
            item.data.student.name,
            money(item.total)
        )

        val labels = overdue.map(::label).toMutableList()
        val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        val list = ListView(this).apply {
            adapter = listAdapter
            setOnItemClickListener { _, _, position, _ ->
                val item = overdue[position]
                sendReminder(item.data.student, reminderMessage(item.data.student, item.dues))
                remindedIds += item.data.student.id
                labels[position] = label(item)
                listAdapter.notifyDataSetChanged()
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reminder_overdue_title)
            .setMessage(R.string.reminder_overdue_hint)
            .setView(list)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showDeleteAllDialog() {
        val dialogBinding = AlertDialogDeleteAllBinding.inflate(layoutInflater)
        val dialog = cardDialog(dialogBinding.root, cancelable = false)
        dialog.show()

        dialogBinding.slideConfirm.onSlideCompleteListener =
            object : SlideToActView.OnSlideCompleteListener {
                override fun onSlideComplete(view: SlideToActView) {
                    viewModel.deleteAll()
                    dialog.dismiss()
                }
            }
        dialogBinding.imgBtnCancelDialog.setOnClickListener { dialog.dismiss() }
    }
}
