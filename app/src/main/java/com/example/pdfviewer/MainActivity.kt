package com.example.pdfviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.pdfviewer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { startViewer(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.openButton.setOnClickListener { openPdf() }

        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            startViewer(intent.data!!)
        }
    }

    private fun openPdf() {
        openDocument.launch(arrayOf("application/pdf"))
    }

    private fun startViewer(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        val intent = Intent(this, PdfViewerActivity::class.java).setData(uri)
        startActivity(intent)
    }
}
