package com.irdev.talibpay.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.irdev.talibpay.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/**
 * تصدير المستندات المرسلة للأهالي (التقرير، الإيصال، كشف الحساب) كصورة أو PDF.
 * تُرسم off-screen دائمًا بالـ light mode وبعرض ثابت، لتكون واضحة وصالحة للطباعة مهما كان theme التطبيق.
 */
object DocumentExport {

    private const val WIDTH_PX = 1080
    private const val A4_WIDTH_PT = 595
    private const val A4_HEIGHT_PT = 842

    /** Context بالـ light mode (مع لغة التطبيق الحالية) لإنشاء نسخة التصدير. */
    fun lightContext(activity: Activity): Context {
        val config = Configuration(activity.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
        return ContextThemeWrapper(activity.createConfigurationContext(config), R.style.Theme_TalibPay)
    }

    fun inflater(activity: Activity): LayoutInflater = LayoutInflater.from(lightContext(activity))

    /** يفصل [content] عن الـ parent ويعمل measure/layout بعرض التصدير؛ View بلا parent لا يرث الـ layoutDirection فنحدده صراحة. */
    fun layout(activity: Activity, content: View): View {
        (content.parent as? ViewGroup)?.removeView(content)
        content.layoutDirection = activity.resources.configuration.layoutDirection
        content.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        return content
    }

    /** يسأل: صورة أم PDF. */
    fun chooseFormat(activity: Activity, title: Int, onImage: () -> Unit, onPdf: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setItems(arrayOf(activity.getString(R.string.share_as_image), activity.getString(R.string.share_as_pdf))) { _, which ->
                if (which == 0) onImage() else onPdf()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun shareImage(activity: Activity, content: View, fileName: String, caption: String? = null) {
        val bitmap = createBitmap(content.width, content.height)
        content.draw(Canvas(bitmap))
        val file = File(activity.cacheDir, "$fileName.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        share(activity, file, "image/png", caption)
    }

    /** صفحات A4؛ المستند الطويل يُقسَّم على عدة صفحات. */
    fun sharePdf(activity: Activity, content: View, fileName: String, caption: String? = null) {
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
            val file = File(activity.cacheDir, "$fileName.pdf")
            FileOutputStream(file).use { pdf.writeTo(it) }
            share(activity, file, "application/pdf", caption)
        } finally {
            pdf.close()
        }
    }

    /** الملف في الـ cache الخاص بالتطبيق، ويُشارك عبر FileProvider بصلاحية قراءة مؤقتة. */
    fun share(activity: Activity, file: File, mimeType: String, caption: String? = null) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            if (caption != null) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_chooser_title)))
    }
}
