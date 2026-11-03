package com.irdev.talibpay.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ActivityReportBinding
import com.irdev.talibpay.databinding.ItemReportRowBinding
import com.irdev.talibpay.domain.MonthlyReport
import com.irdev.talibpay.domain.todaySimpleDate
import com.irdev.talibpay.util.DocumentExport
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.dueTitle
import com.irdev.talibpay.util.money
import com.irdev.talibpay.util.monthName
import com.irdev.talibpay.util.paidPercent
import com.irdev.talibpay.util.setupToolbar
import com.irdev.talibpay.viewmodel.ReportViewModel
import com.irdev.talibpay.viewmodel.UiEvent

/** التقرير الشهري: المحصّل، ومستحقات الشهر، ومن لم يسدّد؛ مع مشاركته كصورة أو PDF. */
class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private val viewModel: ReportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main, applyTop = false)
        setupToolbar(binding.toolbar)

        binding.btnPrevMonth.setOnClickListener { viewModel.previousMonth() }
        binding.btnNextMonth.setOnClickListener { viewModel.nextMonth() }

        collectWhileStarted(viewModel.report) { report ->
            if (report == null) return@collectWhileStarted
            binding.tvMonth.text = monthName(report.month)
            binding.btnNextMonth.isEnabled = viewModel.canGoNext(report.month)
            bindReport(binding, report, this)
            invalidateOptionsMenu()
        }
        collectWhileStarted(viewModel.events) { event ->
            if (event == UiEvent.NotSignedIn) {
                startActivity(Intent(this, LoginActivity::class.java))
                finishAffinity()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.report_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val ready = viewModel.report.value != null
        menu.findItem(R.id.menu_share_image)?.isEnabled = ready
        menu.findItem(R.id.menu_share_pdf)?.isEnabled = ready
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val report = viewModel.report.value ?: return super.onOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_share_image -> {
                shareImage(report)
                return true
            }

            R.id.menu_share_pdf -> {
                sharePdf(report)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /** يملأ محتوى التقرير؛ [context] هو سياق العرض (فاتح دائمًا عند التصدير). */
    private fun bindReport(b: ActivityReportBinding, report: MonthlyReport, context: Context) {
        val inflater = LayoutInflater.from(context)
        fun ViewGroup.addRow(label: String, value: String, valueColor: Int? = null) {
            val row = ItemReportRowBinding.inflate(inflater, this, true)
            row.tvRowLabel.text = label
            row.tvRowValue.text = value
            if (valueColor != null) row.tvRowValue.setTextColor(ContextCompat.getColor(context, valueColor))
        }

        b.tvReportHeading.text = getString(R.string.report_heading, monthName(report.month))

        // المحصّل
        b.tvCollected.text = money(report.collected)
        b.collectedRows.removeAllViews()
        b.collectedRows.addRow(getString(R.string.label_cash), money(report.collectedCash))
        b.collectedRows.addRow(getString(R.string.label_bank), money(report.collectedBank))
        if (report.refunded > 0) {
            b.collectedRows.addRow(getString(R.string.report_refunded), money(report.refunded))
        }
        b.collectedRows.addRow(
            getString(R.string.report_payment_count),
            resources.getQuantityString(R.plurals.payments_count, report.paymentCount, report.paymentCount)
        )

        // مستحقات الشهر
        b.duesRows.removeAllViews()
        b.duesProgress.setProgressCompat(paidPercent(report.billedPaid, report.billed), false)
        b.duesProgress.visibility = if (report.billed > 0) View.VISIBLE else View.GONE
        if (report.billed > 0) {
            b.duesRows.addRow(getString(R.string.report_billed), money(report.billed))
            if (report.discounts > 0) b.duesRows.addRow(getString(R.string.report_discounts), money(report.discounts))
            b.duesRows.addRow(getString(R.string.report_billed_paid), money(report.billedPaid), R.color.status_paid)
            b.duesRows.addRow(
                getString(R.string.report_billed_remaining),
                money(report.billedRemaining),
                if (report.billedRemaining > 0) R.color.status_due else null
            )
        } else {
            b.duesRows.addRow(getString(R.string.report_no_dues), "")
        }

        // غير المسدّدين
        b.tvUnpaidTitle.text = getString(R.string.report_unpaid_title, report.unpaid.size)
        b.unpaidRows.removeAllViews()
        if (report.unpaid.isEmpty()) {
            b.unpaidRows.addRow(
                getString(if (report.billed > 0) R.string.report_all_paid else R.string.report_no_dues),
                ""
            )
        }
        for (item in report.unpaid) {
            val name = if (item.student.archived) getString(R.string.report_archived_name, item.student.name)
            else item.student.name
            val dues = item.dues.joinToString("، ") { dueTitle(it.charge) }
            b.unpaidRows.addRow("$name\n$dues", money(item.remaining), R.color.status_due)
        }

        b.tvGeneratedAt.text = getString(R.string.report_generated, todaySimpleDate().format())
    }

    /** نسخة من التقرير بالوضع الفاتح خارج الشاشة للتصدير (انظر DocumentExport). */
    private fun renderForExport(report: MonthlyReport): View {
        val inflater = DocumentExport.inflater(this)
        val export = ActivityReportBinding.inflate(inflater)
        bindReport(export, report, inflater.context)
        // شريط التقدم لا يُرسم خارج الشاشة، فنخفيه بدل ترك فراغ
        export.duesProgress.visibility = View.GONE
        return DocumentExport.layout(this, export.reportContent)
    }

    private fun shareImage(report: MonthlyReport) =
        DocumentExport.shareImage(this, renderForExport(report), fileName(report))

    private fun sharePdf(report: MonthlyReport) =
        DocumentExport.sharePdf(this, renderForExport(report), fileName(report))

    private fun fileName(report: MonthlyReport) = "TalibPay-${report.month.key}"
}
