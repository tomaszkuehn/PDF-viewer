package com.example.pdfviewer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

/**
 * Thin wrapper around the platform [PdfRenderer].
 *
 * Rendering works on every API level >= 21. Text search ([searchPage]) uses the
 * new Android 15 (API 35) `PdfRenderer.Page.searchText` API and returns `null`
 * on older devices where it is unavailable.
 *
 * Only a single page may be open at a time, so all page access is synchronized.
 */
class PdfRendererManager(
    private val contentResolver: ContentResolver,
    private val uri: Uri
) : AutoCloseable {

    private val fileDescriptor: ParcelFileDescriptor =
        contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Nie można otworzyć deskryptora pliku")

    private val renderer: PdfRenderer = PdfRenderer(fileDescriptor)

    val pageCount: Int
        get() = renderer.pageCount

    @Synchronized
    fun renderPage(index: Int, targetWidthPx: Int): RenderedPage {
        val page = renderer.openPage(index)
        val pageWidthPoints = page.width
        val pageHeightPoints = page.height
        val scale = targetWidthPx.toFloat() / pageWidthPoints
        val w = targetWidthPx
        val h = (pageHeightPoints * scale).roundToInt()
        val bitmap = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return RenderedPage(bitmap, pageWidthPoints, pageHeightPoints)
    }

    /**
     * Searches a single page. Returns a flattened list of match rectangles
     * (one [RectF] per text line) expressed in page coordinates (points, 1/72").
     * Returns `null` when the search API is not available on the current device,
     * or an empty list when there are no matches.
     */
    @Synchronized
    fun searchPage(index: Int, query: String): List<RectF>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        return try {
            val page = renderer.openPage(index)
            val matches = page.searchText(query)
            page.close()
            matches.flatMap { it.bounds }
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        try {
            renderer.close()
        } catch (_: Exception) {
        }
        try {
            fileDescriptor.close()
        } catch (_: Exception) {
        }
    }
}

data class RenderedPage(
    val bitmap: Bitmap,
    val pageWidthPoints: Int,
    val pageHeightPoints: Int
)
