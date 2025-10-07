package com.example.textalert

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
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
import com.example.textalert.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import androidx.preference.PreferenceManager

class MainActivity : AppCompatActivity() {
    private var beepEnabled = true
    private lateinit var binding: ActivityMainBinding
    private val executor = Executors.newSingleThreadExecutor()
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private lateinit var keywordStore: KeywordStore
    private lateinit var matcher: TextMatcher
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    private lateinit var imageCapture: ImageCapture
    private var lastAlertAt = 0L
    private var alertIntervalMs = 1200L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        keywordStore = KeywordStore(this)
        matcher = TextMatcher()

        binding.btnAdd.setOnClickListener {
            val t = binding.inputKeyword.text.toString().trim()
            if (t.isNotEmpty()) {
                keywordStore.add(t)
                binding.inputKeyword.text?.clear()
            }
        }
        binding.btnClear.setOnClickListener { keywordStore.clear() }
        binding.btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 0)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        beepEnabled = prefs.getBoolean("beep_enabled", true)
        alertIntervalMs = prefs.getString("alert_interval_ms", "1200")!!.toLong()
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.preview.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { img -> analyze(img) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, selector, preview, analysis, imageCapture)
        }, ContextCompat.getMainExecutor(this))
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
            takePhoto()
        }
    }

    private fun takePhoto() {
        val name = "TextAlert_${System.currentTimeMillis()}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TextAlert")
        }
        val output = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()
        imageCapture.takePicture(
            output,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {}
                override fun onError(exception: ImageCaptureException) {}
            }
        )
    }
}
