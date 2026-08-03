package com.example.pdfviewer

import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pdfviewer.databinding.FragmentPageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageFragment : Fragment() {

    private var pageIndex: Int = 0
    private var _binding: FragmentPageBinding? = null
    private val binding get() = _binding!!
    private var pageScale: Float = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageIndex = arguments?.getInt(ARG_PAGE) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as PdfViewerActivity
        val imageView = binding.pageImage
        val dm = resources.displayMetrics
        // Render at physical pixel resolution (logical px × density) so text stays
        // crisp in both portrait and landscape; RENDER_QUALITY adds zoom headroom.
        val targetWidth = (dm.widthPixels * dm.density * RENDER_QUALITY)
            .toInt()
            .coerceAtMost(2600)

        (requireActivity() as PdfViewerActivity).registerPageFragment(pageIndex, this)

        lifecycleScope.launch(Dispatchers.IO) {
            val rendered = activity.pdfManager.renderPage(pageIndex, targetWidth)
            pageScale = rendered.bitmap.width.toFloat() / rendered.pageWidthPoints
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                imageView.setImageBitmap(rendered.bitmap)
                applyHighlights()
            }
        }
    }

    /** Re-applies (or clears) search highlights from the activity's active search. */
    fun applyHighlights() {
        val activity = requireActivity() as PdfViewerActivity
        val rects = activity.getSearchRects(pageIndex)
        if (rects != null) {
            binding.pageImage.setHighlights(rects.map { toBitmapRect(it, pageScale) })
        } else {
            binding.pageImage.setHighlights(emptyList())
        }
    }

    override fun onDestroyView() {
        (requireActivity() as PdfViewerActivity).unregisterPageFragment(pageIndex)
        val drawable = binding.pageImage.drawable
        (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
        binding.pageImage.setImageDrawable(null)
        super.onDestroyView()
        _binding = null
    }

    private fun toBitmapRect(r: RectF, scale: Float): RectF =
        RectF(r.left * scale, r.top * scale, r.right * scale, r.bottom * scale)

    companion object {
        private const val ARG_PAGE = "arg_page"
        const val RENDER_QUALITY = 3.0f

        fun newInstance(page: Int): PageFragment =
            PageFragment().apply {
                arguments = Bundle().apply { putInt(ARG_PAGE, page) }
            }
    }
}
