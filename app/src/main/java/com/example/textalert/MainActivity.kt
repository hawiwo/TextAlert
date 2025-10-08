package com.example.textalert

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.example.textalert.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.Executors
import androidx.camera.core.AspectRatio
import android.graphics.RectF

class MainActivity : AppCompatActivity() {
    private lateinit var yuv: YuvToRgbConverter
    private var annotatePhoto = true

    // Settings-Flags (werden in onCreate und onResume aus Prefs geladen)
    private var extractValuesEnabled = false
    private val extractor = ValueExtractor()
    private var fuzzyStrength = 60
    private lateinit var matcher: TextMatcher
    private var regexCaseInsensitive = true
    private var fuzzyEnabled = true
    private var beepEnabled = true
    private var photoOnMatch = false
    private var pauseOnLock = true
    private var alertIntervalMs = 1200L

    private lateinit var binding: ActivityMainBinding
    private val executor = Executors.newSingleThreadExecutor()
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private lateinit var keywordStore: KeywordStore
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    private lateinit var imageCapture: ImageCapture
    private var lastAlertAt = 0L

    private var cameraProvider: ProcessCameraProvider? = null
    private var isCameraRunning = false
    private var analysis: ImageAnalysis? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!pauseOnLock) return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> stopCamera()
                Intent.ACTION_USER_PRESENT -> startCameraIfAllowed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        yuv = YuvToRgbConverter(this)
        extractValuesEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("extract_values_enabled", false)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Toast.makeText(
            this,
            "SDK=${android.os.Build.VERSION.SDK_INT}, Android=${android.os.Build.VERSION.RELEASE}",
            Toast.LENGTH_LONG
        ).show()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        regexCaseInsensitive = prefs.getBoolean("regex_case_insensitive", true)
        fuzzyEnabled = prefs.getBoolean("fuzzy_enabled", true)
        fuzzyStrength = prefs.getInt("fuzzy_strength", 60)
        matcher = TextMatcher({ regexCaseInsensitive }, { fuzzyEnabled }, { fuzzyStrength })


        // Prefs laden (inkl. Fuzzy & Case-Insensitive)
        //val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        regexCaseInsensitive = prefs.getBoolean("regex_case_insensitive", true)
        fuzzyEnabled = prefs.getBoolean("fuzzy_enabled", true)
        beepEnabled = prefs.getBoolean("beep_enabled", true)
        photoOnMatch = prefs.getBoolean("photo_on_match", false)
        pauseOnLock = prefs.getBoolean("pause_on_lock", true)
        alertIntervalMs = prefs.getString("alert_interval_ms", "1200")!!.toLong()
        annotatePhoto = prefs.getBoolean("annotate_photo", true)
        keywordStore = KeywordStore(this)

        // Matcher EINMAL bauen – Lambdas lesen immer die aktuellen Variablen
        matcher = TextMatcher({ regexCaseInsensitive }, { fuzzyEnabled })

        // UI-Handler
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnAdd.setOnClickListener {
            val t = binding.inputKeyword.text.toString().trim()
            if (t.isEmpty()) return@setOnClickListener
            val added = keywordStore.add(t)
            if (added) {
                Toast.makeText(this, "Hinzugefügt: $t", Toast.LENGTH_SHORT).show()
                binding.inputKeyword.text?.clear()
            } else {
                Toast.makeText(this, "Schon vorhanden: $t", Toast.LENGTH_SHORT).show()
            }
        }

        // Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 0)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(screenReceiver)
    }

    override fun onResume() {
        extractValuesEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("extract_values_enabled", false)
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        regexCaseInsensitive = prefs.getBoolean("regex_case_insensitive", true)
        fuzzyEnabled = prefs.getBoolean("fuzzy_enabled", true)
        fuzzyStrength = prefs.getInt("fuzzy_strength", 60)
        beepEnabled = prefs.getBoolean("beep_enabled", true)
        photoOnMatch = prefs.getBoolean("photo_on_match", false)
        pauseOnLock = prefs.getBoolean("pause_on_lock", true)
        alertIntervalMs = prefs.getString("alert_interval_ms", "1200")!!.toLong()
        annotatePhoto = prefs.getBoolean("annotate_photo", true)
        if (this::imageCapture.isInitialized) imageCapture.targetRotation = binding.preview.display.rotation
    }
    private fun startCameraIfAllowed() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && !isCameraRunning) {
            startCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.preview.surfaceProvider)
            }
            analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { a -> a.setAnalyzer(executor) { img -> analyze(img) } }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(binding.preview.display.rotation)
                .build()
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(this, selector, preview, analysis, imageCapture)
            isCameraRunning = true
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        isCameraRunning = false
    }

    private fun analyze(image: ImageProxy) {
        val media = image.image ?: run { image.close(); return }
        val rotation = image.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(media, rotation)
        val ocrW = if (rotation % 180 == 0) image.width else image.height
        val ocrH = if (rotation % 180 == 0) image.height else image.width
        recognizer.process(input)
            .addOnSuccessListener { result ->
                handleResult(result)
            }
            .addOnCompleteListener { image.close() }
    }
    private fun handleResult(text: Text) {
        val now = System.currentTimeMillis()
        val full = text.text ?: ""
        binding.debugText.text = full.take(200).replace("\n", " ")
        val targets = keywordStore.getAll()
        val hit = matcher.match(full, targets)
        if (hit != null) {
            val boxes = mutableListOf<RectF>()
            for (b in text.textBlocks) {
                for (ln in b.lines) {
                    val s = ln.text ?: ""
                    if (matcher.match(s, listOf(hit)) != null) {
                        ln.boundingBox?.let { boxes.add(RectF(it)) }
                    }
                }
            }
        }
        if (hit != null && now - lastAlertAt > alertIntervalMs) {
            lastAlertAt = now
            if (beepEnabled) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            if (photoOnMatch) takePhoto()
        }
    }

    private fun takePhoto() {
        if (!this::imageCapture.isInitialized) return

        // Display-Rotation aktualisieren (wichtig!)
        imageCapture.targetRotation = binding.preview.display.rotation

        val baseName = "TextAlert_${System.currentTimeMillis()}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TextAlert")
        }

        // ➜ CameraX legt den MediaStore-Eintrag SELBST an
        val output = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()

        imageCapture.takePicture(
            output,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                    val uri = res.savedUri
                    if (uri == null) {
                        Toast.makeText(this@MainActivity, "Foto gespeichert, aber keine URI", Toast.LENGTH_LONG).show()
                        return
                    }
                    Toast.makeText(this@MainActivity, "Foto gespeichert", Toast.LENGTH_SHORT).show()
                    if (annotatePhoto) annotateSavedPhoto(uri, baseName)
                }
                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Foto fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
    private fun annotateSavedPhoto(srcUri: android.net.Uri, baseName: String) {
        try {
            val exif = contentResolver.openInputStream(srcUri)?.use {
                androidx.exifinterface.media.ExifInterface(it)
            } ?: run {
                Toast.makeText(this, "EXIF nicht lesbar", Toast.LENGTH_SHORT).show()
                return
            }

            val bmp = decodeBitmapScaled(srcUri, maxDim = 1920) ?: run {
                Toast.makeText(this, "Bild nicht decodierbar", Toast.LENGTH_LONG).show()
                return
            }
            val oriented = applyExif(
                bmp,
                exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                )
            )

            val input = com.google.mlkit.vision.common.InputImage.fromBitmap(oriented, 0)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(input)
                .addOnSuccessListener { ocr ->
                    val marked = oriented.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                    val canvas = android.graphics.Canvas(marked)

                    val baseStroke = (kotlin.math.min(marked.width, marked.height) * 0.006f).coerceIn(3f, 12f)
                    val paintOuter = android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = baseStroke * 1.8f
                        color = android.graphics.Color.BLACK
                        alpha = 120
                        isAntiAlias = true
                    }
                    val paintInner = android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = baseStroke
                        color = android.graphics.Color.RED
                        isAntiAlias = true
                    }

                    fun expandRect(src: android.graphics.Rect, padX: Float, padY: Float): android.graphics.RectF {
                        val l = (src.left - padX).coerceAtLeast(0f)
                        val t = (src.top - padY).coerceAtLeast(0f)
                        val r = (src.right + padX).coerceAtMost(marked.width - 1f)
                        val b = (src.bottom + padY).coerceAtMost(marked.height - 1f)
                        return android.graphics.RectF(l, t, r, b)
                    }

                    val targets = keywordStore.getAll()
                    var drawn = false
                    for (b in ocr.textBlocks) {
                        for (ln in b.lines) {
                            val s = ln.text ?: continue
                            if (matcher.match(s, targets) != null) {
                                val bb = ln.boundingBox ?: continue
                                val padX = (bb.width() * 0.08f).coerceAtLeast(6f)
                                val padY = (bb.height() * 0.25f).coerceAtLeast(6f)
                                val er = expandRect(bb, padX, padY)
                                val rxy = (kotlin.math.min(er.width(), er.height()) * 0.08f).coerceIn(8f, 28f)
                                canvas.drawRoundRect(er, rxy, rxy, paintOuter)
                                canvas.drawRoundRect(er, rxy, rxy, paintInner)
                                drawn = true
                            }
                        }
                    }

                    if (!drawn) {
                        Toast.makeText(this, "Keine Boxen zum Markieren", Toast.LENGTH_SHORT).show()
                    } else {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "${baseName}_marked.jpg")
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TextAlert")
                        }
                        val dest = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (dest != null) {
                            contentResolver.openOutputStream(dest)?.use {
                                marked.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it)
                            }
                            Toast.makeText(this, "Markierte Kopie gespeichert", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Zieldatei nicht anlegbar", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "OCR-Annotation fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Annotieren fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /*
    private fun annotateSavedPhoto(srcUri: android.net.Uri, baseName: String) {
        try {
            // EXIF lesen
            val exif = contentResolver.openInputStream(srcUri)?.use {
                androidx.exifinterface.media.ExifInterface(it)
            } ?: run {
                Toast.makeText(this, "EXIF nicht lesbar", Toast.LENGTH_SHORT).show()
                return
            }

            // Bitmap speicherschonend decodieren (maxDim ~ 1920, passt zu 1080p-Erkennung)
            val bmp = decodeBitmapScaled(srcUri, maxDim = 1920) ?: run {
                Toast.makeText(this, "Bild nicht decodierbar", Toast.LENGTH_LONG).show()
                return
            }
            val oriented = applyExif(bmp, exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            ))

            // OCR auf GENAU diesem Bitmap
            val input = com.google.mlkit.vision.common.InputImage.fromBitmap(oriented, 0)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(input)
                .addOnSuccessListener { ocr ->
                    val marked = oriented.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                    val canvas = android.graphics.Canvas(marked)
                    val p = android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 6f
                        color = android.graphics.Color.RED
                        isAntiAlias = true
                    }

                    val targets = keywordStore.getAll()
                    var drawn = false
                    for (b in ocr.textBlocks) {
                        for (ln in b.lines) {
                            val s = ln.text ?: ""
                            if (matcher.match(s, targets) != null) {
                                ln.boundingBox?.let {
                                    canvas.drawRect(it, p)  // 1:1 Koordinaten zum Bitmap
                                    drawn = true
                                }
                            }
                        }
                    }

                    if (!drawn) {
                        Toast.makeText(this, "Keine Boxen zum Markieren", Toast.LENGTH_SHORT).show()
                    } else {
                        // markierte Kopie speichern
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "${baseName}_marked.jpg")
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            if (Build.VERSION.SDK_INT >= 29)
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TextAlert")
                        }
                        val dest = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (dest != null) {
                            contentResolver.openOutputStream(dest)?.use {
                                marked.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it)
                            }
                            Toast.makeText(this, "Markierte Kopie gespeichert", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Zieldatei nicht anlegbar", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "OCR-Annotation fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Annotieren fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    */
    private fun decodeBitmapScaled(uri: android.net.Uri, maxDim: Int): android.graphics.Bitmap? {
        // Größe ermitteln
        val opts1 = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts1) }
        val (w, h) = opts1.outWidth to opts1.outHeight
        if (w <= 0 || h <= 0) return null

        // inSampleSize bestimmen (Potenz von 2)
        var sample = 1
        val maxWH = maxOf(w, h)
        while (maxWH / sample > maxDim) sample *= 2

        val opts2 = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        contentResolver.openInputStream(uri)?.use { return android.graphics.BitmapFactory.decodeStream(it, null, opts2) }
        return null
    }
    private fun applyExif(src: android.graphics.Bitmap, orientation: Int): android.graphics.Bitmap {
        val m = android.graphics.Matrix()
        when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        return android.graphics.Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
    private fun saveBitmapToGallery(name: String, bmp: android.graphics.Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TextAlert")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        contentResolver.openOutputStream(uri)?.use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
        }
    }

}
