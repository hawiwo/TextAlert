package com.example.textalert

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
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
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val executor = Executors.newSingleThreadExecutor()
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private lateinit var keywordStore: KeywordStore
    private lateinit var matcher: TextMatcher
    private lateinit var alerter: AlertManager
    private var lastAlertAt = 0L

    private var frameW = 0
    private var frameH = 0
    private var frameRot = 0

    private val reqPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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
            binding.overlay.show(emptyList(), frameW, frameH)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            reqPerm.launch(Manifest.permission.CAMERA)
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
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { img -> analyze(img) }
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, selector, preview, analysis)

            binding.preview.post {
                binding.overlay.setTransform(binding.preview.outputTransform?.matrix)
                binding.overlay.bringToFront()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(image: ImageProxy) {
        val media = image.image ?: run { image.close(); return }
        frameW = image.width
        frameH = image.height
        frameRot = image.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(media, frameRot)
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
        if (hit != null && now - lastAlertAt > 1500) {
            lastAlertAt = now
            alerter.notifyHit(hit)
        }

        val hitBoxes = mutableListOf<RectF>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val bb = line.boundingBox ?: continue
                val s = line.text ?: ""
                if (matcher.match(s, targets) != null) {
                    hitBoxes.add(mlToBufferRect(bb, frameW, frameH, frameRot))
                }
            }
        }
        runOnUiThread { binding.overlay.show(hitBoxes, frameW, frameH) }
    }

    private fun mlToBufferRect(r: Rect, w: Int, h: Int, rot: Int): RectF {
        val x0 = r.left.toFloat()
        val y0 = r.top.toFloat()
        val x1 = r.right.toFloat()
        val y1 = r.bottom.toFloat()
        fun mapPoint(x: Float, y: Float): PointF {
            return when (((rot % 360) + 360) % 360) {
                0 -> PointF(x, y)
                90 -> PointF(y, h - x)
                180 -> PointF(w - x, h - y)
                270 -> PointF(w - y, x)
                else -> PointF(x, y)
            }
        }
        val p1 = mapPoint(x0, y0)
        val p2 = mapPoint(x1, y0)
        val p3 = mapPoint(x0, y1)
        val p4 = mapPoint(x1, y1)
        val left = minOf(p1.x, p2.x, p3.x, p4.x)
        val top = minOf(p1.y, p2.y, p3.y, p4.y)
        val right = maxOf(p1.x, p2.x, p3.x, p4.x)
        val bottom = maxOf(p1.y, p2.y, p3.y, p4.y)
        return RectF(left, top, right, bottom)
    }
}
