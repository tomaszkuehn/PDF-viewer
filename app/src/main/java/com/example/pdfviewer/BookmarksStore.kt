package com.example.pdfviewer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists per-document bookmarks (page indices) in a JSON file inside the
 * app's private storage. Uses only the built-in `org.json` API.
 */
class BookmarksStore(context: Context) {

    private val file = File(context.filesDir, "bookmarks.json")

    private fun load(): JSONObject {
        if (!file.exists()) return JSONObject()
        return try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun save(root: JSONObject) {
        try {
            file.writeText(root.toString())
        } catch (_: Exception) {
        }
    }

    fun getPages(docId: String): Set<Int> {
        val arr = load().optJSONArray(docId) ?: return emptySet()
        val set = mutableSetOf<Int>()
        for (i in 0 until arr.length()) set.add(arr.getInt(i))
        return set
    }

    fun list(docId: String): List<Int> = getPages(docId).sorted()

    /** Toggles the bookmark for [page]; returns `true` if it is now bookmarked. */
    fun toggle(docId: String, page: Int): Boolean {
        val root = load()
        val arr = root.optJSONArray(docId) ?: JSONArray()
        val pages = mutableListOf<Int>()
        for (i in 0 until arr.length()) pages.add(arr.getInt(i))
        return if (pages.contains(page)) {
            pages.remove(page)
            root.put(docId, JSONArray(pages))
            save(root)
            false
        } else {
            pages.add(page)
            root.put(docId, JSONArray(pages.sorted()))
            save(root)
            true
        }
    }

    fun remove(docId: String, page: Int) {
        val root = load()
        val arr = root.optJSONArray(docId) ?: return
        val pages = mutableListOf<Int>()
        for (i in 0 until arr.length()) if (arr.getInt(i) != page) pages.add(arr.getInt(i))
        if (pages.isEmpty()) root.remove(docId) else root.put(docId, JSONArray(pages))
        save(root)
    }
}
