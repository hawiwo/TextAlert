package com.example.textalert

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.textalert.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition
import java.util.concurrent.Executors
import android.graphics.RectF


class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val executor = Executors.newSingleThreadExecutor()
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private lateinit var keywordStore: KeywordStore
    private lateinit var matcher: TextMatcher
    private lateinit var alerter: AlertManager
    private var lastAlertAt = 0L

    private val reqPerms = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        keywordStore = KeywordStore(this)
        matcher = TextMatcher()
        alerter = AlertManager(this)
        binding.btnAdd.setOnClickListener {
            val t = binding.inputKeyword.text.toString().trim()
            if (t.isNotEmpty()) {
                keywordStore.add(t)
                binding.inputKeyword.text?.clear()
            }
        }
        binding.btnClear.setOnClickListener {
            keywordStore.clear()
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            reqPerms.launch(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { img -> analyze(img) }
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, selector, preview, analysis)
            binding.overlay.setTransform(binding.preview.outputTransform?.matrix)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null) {
            image.close()
            return
        }
        val rotation = image.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(media, rotation)
        recognizer.process(input)
            .addOnSuccessListener { result -> handleResult(result) }
            .addOnCompleteListener { image.close() }
    }
    private fun handleResult(text: Text) {
        val now = System.currentTimeMillis()
        val full = text.text ?: ""
        val targets = keywordStore.getAll()
        val hit = matcher.match(full, targets)
        if (hit != null && now - lastAlertAt > 1500) {
            lastAlertAt = now
            alerter.notifyHit(hit)
        }
        val hitBoxes = mutableListOf<RectF>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val s = line.text ?: ""
                if (matcher.match(s, targets) != null) {
                    val b = line.boundingBox
                    if (b != null) hitBoxes.add(RectF(b))
                }
            }
        }
        binding.overlay.show(hitBoxes)
    }

}

