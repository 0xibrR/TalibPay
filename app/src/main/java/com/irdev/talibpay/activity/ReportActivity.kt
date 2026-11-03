package com.irdev.talibpay.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.irdev.talibpay.R
import com.irdev.talibpay.databinding.ActivityReportBinding
import com.irdev.talibpay.databinding.ItemReportRowBinding
import com.irdev.talibpay.domain.MonthlyReport
import com.irdev.talibpay.domain.todaySimpleDate
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.dueTitle
import com.irdev.talibpay.util.money
import com.irdev.talibpay.util.monthName
import com.irdev.talibpay.util.paidPercent
import com.irdev.talibpay.util.setupToolbar
import com.irdev.talibpay.viewmodel.ReportViewModel
import com.irdev.talibpay.viewmodel.UiEvent
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

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

    /**
     * نسخة من التقرير بالوضع الفاتح خارج الشاشة، بعرض ثابت،
     * حتى تكون الصورة وملف PDF مقروءة ومناسبة للطباعة مهما كان مظهر التطبيق.
     */
    private fun renderForExport(report: MonthlyReport): View {
        val config = Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
        val context = ContextThemeWrapper(createConfigurationContext(config), R.style.Theme_TalibPay)
        val export = ActivityReportBinding.inflate(LayoutInflater.from(context))
        bindReport(export, report, context)
        // شريط التقدم لا يُرسم خارج الشاشة، فنخفيه بدل ترك فراغ
        export.duesProgress.visibility = View.GONE
        val content = export.reportContent
        (content.parent as ViewGroup).removeView(content)
        // عرض بلا أب لا يرث اتجاه اللغة، فنحدده صراحة (يمين لليسار للعربية)
        content.layoutDirection = resources.configuration.layoutDirection
        content.measure(
            View.MeasureSpec.makeMeasureSpec(EXPORT_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        return content
    }

    private fun shareImage(report: MonthlyReport) {
        val content = renderForExport(report)
        val bitmap = createBitmap(content.width, content.height)
        content.draw(Canvas(bitmap))
        val file = File(cacheDir, fileName(report, "png"))
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shareFile(file, "image/png")
    }

    /** صفحات A4؛ التقرير الطويل يُقسَّم على عدة صفحات. */
    private fun sharePdf(report: MonthlyReport) {
        val content = renderForExport(report)
        val scale = A4_WIDTH_PT.toFloat() / content.width
        val pageHeightPx = A4_HEIGHT_PT / scale
        val pages = ceil(content.height / pageHeightPx).toInt().coerceAtLeast(1)

        val pdf = PdfDocument()
        try {
            for (index in 0 until pages) {
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, index + 1).create())
                page.canvas.scale(scale, scale)
                page.canvas.translate(0f, -index * pageHeightPx)
                content.draw(page.canvas)
                pdf.finishPage(page)
            }
            val file = File(cacheDir, fileName(report, "pdf"))
            FileOutputStream(file).use { pdf.writeTo(it) }
            shareFile(file, "application/pdf")
        } finally {
            pdf.close()
        }
    }

    private fun fileName(report: MonthlyReport, extension: String) = "TalibPay-${report.month.key}.$extension"

    private fun shareFile(file: File, mimeType: String) {
        // الملف في cache الخاص بالتطبيق، ويُشارك عبر FileProvider بصلاحية قراءة مؤقتة
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)))
    }

    private companion object {
        const val EXPORT_WIDTH_PX = 1080
        const val A4_WIDTH_PT = 595
        const val A4_HEIGHT_PT = 842
    }
}
