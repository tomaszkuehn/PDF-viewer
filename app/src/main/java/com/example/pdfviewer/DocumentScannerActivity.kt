package com.example.pdfviewer

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Uruchamia systemowy skaner dokumentów ML Kit.
 *
 * ML Kit Document Scanner (play-services-mlkit-document-scanner) to gotowy,
 * douczony model wykrywania brzegów dokumentu od Google. Zamiast implementować
 * własny pipeline (Canny + contours + warpPerspective), delegujemy detekcję
 * do GmsDocumentScanning — działa w Play Services, bez OpenCV i bez dodatkowych
 * wag w APK.
 *
 * ScannerMode.FULL: detekcja brzegów + korekcja perspektywy + automatyczne
 * uwydatnianie obrazu (czyszczenie tła, kontrast). GalleryImportAllowed=false
 * wymusza pozyskiwanie stron wyłącznie przez aparat.
 *
 * Wynikiem skanowania są obrazy stron (PNG/JPEG) udostępnione przez content URI.
 * Aktywność zwraca listę URI przez setResult(Intent.EXTRA_STREAM) — nadawca
 * może ją wykorzystać do wygenerowania PDF-a lub dalszej obróbki.
 */
class DocumentScannerActivity : AppCompatActivity() {

    private val scanner: GmsDocumentScanner by lazy {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(false)
            .setPageLimiter(10)
            .build()
        GmsDocumentScanning.getClient(options)
    }

    private val startScanner =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                finishCancelled()
                return@registerForActivityResult
            }
            val resultData = result.data
            if (resultData == null) {
                finishCancelled()
                return@registerForActivityResult
            }
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(resultData)
            if (scanningResult == null) {
                finishCancelled()
                return@registerForActivityResult
            }
            handleResult(scanningResult)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startScan()
    }

    private fun startScan() {
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender: IntentSender ->
                val request = IntentSenderRequest.Builder(intentSender).build()
                startScanner.launch(request)
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.scan_unavailable, Toast.LENGTH_LONG).show()
                finishCancelled()
            }
    }

    private fun handleResult(result: GmsDocumentScanningResult) {
        val pages = result.pages ?: emptyList()
        if (pages.isEmpty()) {
            finishCancelled()
            return
        }
        val uris = pages.mapNotNull { it.imageUri }
        if (uris.isEmpty()) {
            finishCancelled()
            return
        }

        val resultIntent = Intent().apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun finishCancelled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}