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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val executor = Executors.newSingleThreadExecutor()
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private lateinit var keywordStore: KeywordStore
    private lateinit var matcher: TextMatcher
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    private lateinit var imageCapture: ImageCapture
    private var lastAlertAt = 0L
    private var alertIntervalMs = 1200L
    private var beepEnabled = true
    private var photoOnMatch = false
    private var pauseOnLock = true

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
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        keywordStore = KeywordStore(this)
        matcher = TextMatcher()

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnAdd.setOnClickListener {
            val t = binding.inputKeyword.text.toString().trim()
            if (t.isEmpty()) return@setOnClickListener
            val added = keywordStore.add(t)
            if (added) {
                android.widget.Toast.makeText(this, "Hinzugefügt: $t", android.widget.Toast.LENGTH_SHORT).show()
                binding.inputKeyword.text?.clear()
            } else {
                android.widget.Toast.makeText(this, "Schon vorhanden: $t", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

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
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        beepEnabled = prefs.getBoolean("beep_enabled", true)
        alertIntervalMs = prefs.getString("alert_interval_ms", "1200")!!.toLong()
        pauseOnLock = prefs.getBoolean("pause_on_lock", true)
        photoOnMatch = prefs.getBoolean("photo_on_match", false)
        if (this::imageCapture.isInitialized) {
            imageCapture.targetRotation = binding.preview.display.rotation
        }
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
                .setTargetRotation(binding.preview.display.rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
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
        recognizer.process(input)
            .addOnSuccessListener { result -> handleResult(result) }
            .addOnCompleteListener { image.close() }
    }

    private fun handleResult(text: Text) {
        val now = System.currentTimeMillis()
        val full = text.text ?: ""
        binding.debugText.text = full.take(200).replace("\n", " ")
        val targets = keywordStore.getAll()
        val hit = matcher.match(full, targets)
        if (hit != null && now - lastAlertAt > alertIntervalMs) {
            lastAlertAt = now
            if (beepEnabled) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            if (photoOnMatch) takePhoto()
        }
    }

    private fun takePhoto() {
        if (!this::imageCapture.isInitialized) return
        val fileName = "TextAlert_${System.currentTimeMillis()}.jpg"
        val output = if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TextAlert")
            }
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ).build()
        } else {
            val base = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val dir = File(base, "TextAlert")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            ImageCapture.OutputFileOptions.Builder(file).build()
        }
        imageCapture.takePicture(
            output,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@MainActivity, "Foto gespeichert", Toast.LENGTH_SHORT).show()
                }
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Foto fehlgeschlagen: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}
