package com.example.pdfviewer

import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.example.pdfviewer.databinding.ActivityViewerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewerBinding
    private lateinit var manager: PdfRendererManager
    private lateinit var bookmarks: BookmarksStore

    private var docId: String = ""
    private var fileName: String = ""

    private val activeSearch = mutableMapOf<Int, List<RectF>>()
    private var currentSearchQuery: String = ""
    private val fragmentRegistry = mutableMapOf<Int, PageFragment>()

    fun registerPageFragment(page: Int, fragment: PageFragment) {
        fragmentRegistry[page] = fragment
    }

    fun unregisterPageFragment(page: Int) {
        fragmentRegistry.remove(page)
    }

    val pdfManager: PdfRendererManager
        get() = manager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val uri = intent.data
        if (uri == null) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        docId = uri.toString()
        fileName = getFileName(uri)
        title = fileName

        try {
            manager = PdfRendererManager(contentResolver, uri)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        bookmarks = BookmarksStore(this)

        val adapter = PageAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updatePageIndicator(position)
        })

        binding.prevButton.setOnClickListener { gotoPage(binding.viewPager.currentItem - 1) }
        binding.nextButton.setOnClickListener { gotoPage(binding.viewPager.currentItem + 1) }
        binding.pageIndicator.setOnClickListener { showJumpDialog() }

        updatePageIndicator(0)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.viewer_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_search -> {
                onSearch()
                true
            }
            R.id.action_bookmark -> {
                toggleBookmark()
                true
            }
            R.id.action_bookmarks -> {
                showBookmarks()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        val bookmarkItem = menu.findItem(R.id.action_bookmark)
        val isMarked = bookmarks.getPages(docId).contains(binding.viewPager.currentItem)
        bookmarkItem?.icon?.let { icon ->
            DrawableCompat.setTint(
                icon,
                if (isMarked) 0xFFEDEDED.toInt() else 0xFF9E9E9E.toInt()
            )
        }
        return super.onPrepareOptionsMenu(menu)
    }

    private fun updatePageIndicator(position: Int) {
        val marked = if (bookmarks.getPages(docId).contains(position)) " ★" else ""
        binding.pageIndicator.text =
            getString(R.string.page_format, position + 1, manager.pageCount) + marked
        invalidateOptionsMenu()
    }

    private fun gotoPage(page: Int) {
        val clamped = page.coerceIn(0, manager.pageCount - 1)
        binding.viewPager.setCurrentItem(clamped, false)
    }

    private fun toggleBookmark() {
        val page = binding.viewPager.currentItem
        val nowMarked = bookmarks.toggle(docId, page)
        Toast.makeText(
            this,
            if (nowMarked) R.string.bookmark_added else R.string.bookmark_removed,
            Toast.LENGTH_SHORT
        ).show()
        updatePageIndicator(page)
    }

    fun getSearchRects(page: Int): List<RectF>? = activeSearch[page]

    private fun onSearch() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            Toast.makeText(this, R.string.search_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_search, null)
        sheet.setContentView(view)

        val input = view.findViewById<EditText>(R.id.search_input)
        val list = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.search_results)
        list.layoutManager = LinearLayoutManager(this)

        val adapter = SearchAdapter { result ->
            activeSearch.clear()
            activeSearch[result.page] = result.rects
            fragmentRegistry.values.forEach { it.applyHighlights() }
            sheet.dismiss()
            gotoPage(result.page)
        }
        list.adapter = adapter

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString().orEmpty()
                runSearch(currentSearchQuery, adapter)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        sheet.show()
    }

    private fun runSearch(query: String, adapter: SearchAdapter) {
        if (query.isBlank()) {
            adapter.submitList(emptyList())
            return
        }
        val requested = query
        lifecycleScope.launch(Dispatchers.IO) {
            val results = mutableListOf<SearchResult>()
            for (i in 0 until manager.pageCount) {
                val rects = manager.searchPage(i, query)
                if (!rects.isNullOrEmpty()) results.add(SearchResult(i, rects))
            }
            withContext(Dispatchers.Main) {
                if (requested == currentSearchQuery) adapter.submitList(results)
            }
        }
    }

    private fun showBookmarks() {
        val pages = bookmarks.list(docId)
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_bookmarks, null)
        sheet.setContentView(view)

        val list = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.bookmarks_list)
        list.layoutManager = LinearLayoutManager(this)

        val adapter = BookmarksAdapter(
            onJump = { page ->
                sheet.dismiss()
                gotoPage(page)
            },
            onRemove = { page ->
                bookmarks.remove(docId, page)
                (list.adapter as BookmarksAdapter).submitList(bookmarks.list(docId))
                updatePageIndicator(binding.viewPager.currentItem)
            }
        )
        adapter.submitList(pages)
        list.adapter = adapter

        if (pages.isEmpty()) {
            Toast.makeText(this, R.string.no_bookmarks, Toast.LENGTH_SHORT).show()
        }
        sheet.show()
    }

    private fun showJumpDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "1 – ${manager.pageCount}"
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.jump_title)
            .setView(input)
            .setPositiveButton(R.string.jump_ok) { _, _ ->
                val value = input.text.toString().toIntOrNull()
                if (value != null) gotoPage(value - 1)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getFileName(uri: android.net.Uri): String {
        return try {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                } else null
            } ?: uri.lastPathSegment ?: "document.pdf"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "document.pdf"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::manager.isInitialized) manager.close()
    }
}

class PageAdapter(
    private val activity: androidx.fragment.app.FragmentActivity
) : androidx.viewpager2.adapter.FragmentStateAdapter(activity) {

    private val manager: PdfRendererManager
        get() = (activity as PdfViewerActivity).pdfManager

    override fun getItemCount(): Int = manager.pageCount

    override fun createFragment(position: Int): androidx.fragment.app.Fragment =
        PageFragment.newInstance(position)
}
