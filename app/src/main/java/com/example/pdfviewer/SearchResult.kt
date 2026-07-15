package com.example.pdfviewer

import android.graphics.RectF

data class SearchResult(
    val page: Int,
    val rects: List<RectF>
)
