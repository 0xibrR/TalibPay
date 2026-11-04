package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.irdev.talibpay.R
import com.irdev.talibpay.adapter.StudentAdapter
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.databinding.ActivityMainBinding
import com.irdev.talibpay.databinding.AlertDialogDeleteAllBinding
import com.irdev.talibpay.util.CurrencyPrefs
import com.irdev.talibpay.util.ExcelExport
import com.irdev.talibpay.util.Extras
import com.irdev.talibpay.util.ListPrefs
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.SwipeAction
import com.irdev.talibpay.util.ToastKind
import com.irdev.talibpay.util.attachSwipeActions
import com.irdev.talibpay.util.cardDialog
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.money
import com.irdev.talibpay.util.paidPercent
import com.irdev.talibpay.util.reminderMessage
import com.irdev.talibpay.util.sendReminder
import com.irdev.talibpay.util.setupToolbar
import com.irdev.talibpay.util.toast
import com.irdev.talibpay.viewmodel.MainUiState
import com.irdev.talibpay.viewmodel.MainViewModel
import com.irdev.talibpay.viewmodel.OverdueStudent
import com.irdev.talibpay.viewmodel.StudentFilter
import com.irdev.talibpay.viewmodel.StudentSort
import com.irdev.talibpay.viewmodel.UiEvent
import com.ncorti.slidetoact.SlideToActView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val adapter = StudentAdapter { openDetail(it) }

    // الطلاب الذين أُرسل لهم تذكير في هذه الجلسة (لتمييزهم في القائمة)
    private val remindedIds = mutableSetOf<String>()

    // الـ filter والـ sort المعروضان حاليًا، لمعرفة متى يتغيّران
    private var shownListOptions: Pair<StudentFilter, StudentSort>? = null

    // الـ chip الخاص بكل filter
    private val filterChips = mapOf(
        StudentFilter.ALL to R.id.chipAll,
        StudentFilter.OVERDUE to R.id.chipOverdue,
        StudentFilter.PARTIAL to R.id.chipPartial,
        StudentFilter.PAID to R.id.chipPaid
    )

    // العملة التي رُسمت بها الشاشة؛ إن تغيّرت من الإعدادات نعيد الرسم عند العودة
    private lateinit var shownCurrency: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shownCurrency = CurrencyPrefs.current(this).symbol
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main, applyTop = false)
        setupToolbar(binding.toolbar, showUp = false)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        // Swipe يمين = تعديل، يسار = أرشفة (الحذف من ملف الطالب فقط حتى لا يحدث بسحب عابر)
        binding.recyclerView.attachSwipeActions(
            right = SwipeAction(R.drawable.ic_edit, R.color.swipe_edit) { position -> openEdit(adapter.currentList[position]) },
            left = SwipeAction(R.drawable.ic_archive, R.color.swipe_archive) { position ->
                viewModel.archive(adapter.currentList[position])
            }
        )
        // تصغير الـ FAB أثناء التمرير للأسفل حتى لا يغطي الـ cards
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.newStudentFAB.shrink() else if (dy < 0) binding.newStudentFAB.extend()
            }
        })

        binding.newStudentFAB.setOnClickListener {
            startActivity(Intent(this, AddActivity::class.java))
        }
        binding.summary.btnSummaryInfo.setOnClickListener { showSummaryExplanation() }

        viewModel.setSort(StudentSort.fromName(ListPrefs.sort(this)))
        binding.btnSort.setOnClickListener { showSortDialog() }
        binding.filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = filterChips.entries.firstOrNull { it.value == checkedIds.firstOrNull() }?.key
            if (filter != null) viewModel.setFilter(filter)
        }

        collectWhileStarted(viewModel.uiState, ::render)
        collectWhileStarted(viewModel.events, ::onEvent)
    }

    private fun render(state: MainUiState) {
        binding.progressBarNotes.isVisible = state.loading
        binding.linearLayoutEmpty.isVisible = !state.loading && !state.hasAnyStudent
        binding.recyclerView.isVisible = !state.loading && state.hasAnyStudent
        binding.tvNoMatches.isVisible = !state.loading && state.hasAnyStudent && state.students.isEmpty()
        binding.filterScroll.isVisible = state.hasAnyStudent
        binding.btnSort.isVisible = state.hasAnyStudent
        for ((filter, chipId) in filterChips) {
            val chip = binding.filterChips.findViewById<Chip>(chipId)
            chip.text = getString(R.string.filter_with_count, getString(filter.labelRes), state.filterCounts[filter] ?: 0)
        }
        // مزامنة الـ chip المختار مع الحالة (مثلًا بعد الـ rotation)
        val checkedId = filterChips.getValue(state.filter)
        if (binding.filterChips.checkedChipId != checkedId) binding.filterChips.check(checkedId)
        binding.tvStudentsCount.text = resources.getQuantityString(
            R.plurals.students_count, state.studentCount, state.studentCount
        )

        with(binding.summary) {
            txtPaidValue.text = money(state.summary.collected)
            txtCashValue.text = money(state.summary.cash)
            txtBankValue.text = money(state.summary.bank)
            txtRemainingValue.text = money(state.summary.remaining)
            val percent = paidPercent(state.summary.paid, state.summary.total)
            collectionProgress.setProgressCompat(percent, true)
            tvCollectionRate.text = getString(R.string.collection_rate, percent)
        }

        // عند تغيير الـ filter أو الـ sort نعود لأول القائمة بعد تطبيق الترتيب الجديد (لا قبله)
        val listChanged = shownListOptions != null && shownListOptions != state.filter to state.sort
        shownListOptions = state.filter to state.sort
        adapter.submitList(state.students) {
            if (listChanged) binding.recyclerView.scrollToPosition(0)
        }
    }

    private fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.StudentArchived -> showArchivedSnackbar(event.student)
            is UiEvent.Message -> toast(event)

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

    private fun showArchivedSnackbar(item: StudentWithPayments) {
        Snackbar.make(
            binding.root,
            getString(R.string.student_archived_with_name, item.student.name),
            Snackbar.LENGTH_LONG
        )
            .setAction(R.string.undo) { viewModel.undoArchive(item) }
            .setAnchorView(binding.newStudentFAB)
            .show()
    }

    /** Excel export لكل الطلاب ومنهم المؤرشفون؛ الكتابة خارج الـ main thread. */
    private fun exportExcel() {
        val students = viewModel.uiState.value.everyone
        if (students.isEmpty()) {
            toast(R.string.excel_nothing)
            return
        }
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { ExcelExport.write(this@MainActivity, students) }
                ExcelExport.share(this@MainActivity, file)
            } catch (e: IOException) {
                Log.w("MainActivity", "Excel export failed", e)
                toast(R.string.error_generic, ToastKind.ERROR)
            }
        }
    }

    private fun showSummaryExplanation() {
        val refunded = viewModel.uiState.value.summary.refunded
        val message = (listOf(
            R.string.payment_summary_popup_title2,
            R.string.payment_summary_cash,
            R.string.payment_summary_bank,
            R.string.payment_summary_total,
            R.string.payment_summary_remaining
        ).map { getString(it) } + listOfNotNull(
            if (refunded > 0) getString(R.string.payment_summary_refunds, money(refunded)) else null
        )).joinToString("\n\n")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.payment_summary_explained_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (CurrencyPrefs.current(this).symbol != shownCurrency) recreate()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // عدد المؤرشفين بجانب "الأرشيف"
        val count = viewModel.uiState.value.archivedCount
        menu.findItem(R.id.archive_menu_item)?.title =
            if (count > 0) getString(R.string.filter_with_count, getString(R.string.archive_title_screen), count)
            else getString(R.string.archive_title_screen)
        return super.onPrepareOptionsMenu(menu)
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

            R.id.report_menu_item -> {
                startActivity(Intent(this, ReportActivity::class.java))
                return true
            }

            R.id.excel_menu_item -> {
                exportExcel()
                return true
            }

            R.id.archive_menu_item -> {
                startActivity(Intent(this, ArchiveActivity::class.java))
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

    /** اختيار الـ sort؛ يُحفظ ويبقى بعد إغلاق التطبيق. */
    private fun showSortDialog() {
        val sorts = StudentSort.entries
        val current = sorts.indexOf(viewModel.uiState.value.sort)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_title)
            .setSingleChoiceItems(sorts.map { getString(it.labelRes) }.toTypedArray(), current) { dialog, which ->
                val sort = sorts[which]
                viewModel.setSort(sort)
                ListPrefs.setSort(this, sort.name)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * قائمة المتأخرين: الضغط على طالب يفتح WhatsApp برسالة جاهزة له،
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
