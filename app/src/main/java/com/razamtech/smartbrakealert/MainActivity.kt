package com.razamtech.smartbrakealert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.razamtech.smartbrakealert.camera.CameraAnalyzer
import com.razamtech.smartbrakealert.camera.DistanceEstimator
import com.razamtech.smartbrakealert.databinding.ActivityMainBinding
import com.razamtech.smartbrakealert.logic.CollisionPredictor
import com.razamtech.smartbrakealert.logic.WarningController
import com.razamtech.smartbrakealert.sensors.KalmanFilter
import com.razamtech.smartbrakealert.sensors.SpeedSensor
import com.razamtech.smartbrakealert.ui.SoundAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val distanceEstimator = DistanceEstimator()
    private val collisionPredictor = CollisionPredictor()
    private lateinit var speedSensor: SpeedSensor
    private lateinit var warningController: WarningController
    private lateinit var soundAlert: SoundAlert

    private var cameraExecutor: ExecutorService? = null
    private var analyzer: CameraAnalyzer? = null
    private var analyzerScope: CoroutineScope? = null

    private var currentSpeedKmh: Double = 0.0

    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.all { it.value }) {
                onPermissionsGranted()
            } else {
                showPermissionRationale()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.speedText.text = getString(R.string.speed_placeholder)
        binding.ttcText.text = getString(R.string.ttc_placeholder)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        soundAlert = SoundAlert(this)
        warningController = WarningController(binding.overlayView, soundAlert)
        speedSensor = SpeedSensor(this, KalmanFilter())

        lifecycleScope.launch {
            speedSensor.speedFlow.collectLatest { speed ->
                currentSpeedKmh = speed
                val formattedSpeed = getString(R.string.speed_format, speed)
                binding.speedText.text = formattedSpeed
            }
        }

        analyzerScope = CoroutineScope(Dispatchers.Main + Job())

        checkPermissionsAndStart()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (hasAllPermissions()) {
            speedSensor.start()
        }
    }

    override fun onPause() {
        super.onPause()
        speedSensor.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
        analyzer?.close()
        analyzerScope?.cancel()
        warningController.release()
        soundAlert.release()
    }

    private fun checkPermissionsAndStart() {
        if (hasAllPermissions()) {
            onPermissionsGranted()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun hasAllPermissions(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun onPermissionsGranted() {
        lifecycleScope.launch {
            delay(300)
            setupCamera()
            speedSensor.start()
        }
    }

    private fun showPermissionRationale() {
        if (permissions.any { shouldShowRequestPermissionRationale(it) }) {
            AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.permission_rationale)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    permissionLauncher.launch(permissions)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.permission_settings)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                    startActivity(intent)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

        val analyzerExecutor = cameraExecutor ?: Executors.newSingleThreadExecutor().also {
            cameraExecutor = it
        }

        analyzer?.close()
        val newAnalyzer = CameraAnalyzer(
            context = this,
            distanceEstimator = distanceEstimator
        ) { result ->
            analyzerScope?.launch {
                if (result == null) {
                    warningController.onNoDetection()
                    binding.ttcText.text = getString(R.string.ttc_placeholder)
                } else {
                    val ttc = collisionPredictor.calculateTtc(result.distanceMeters, currentSpeedKmh)
                    val ttcText = if (ttc != null) {
                        getString(R.string.ttc_format, ttc)
                    } else {
                        getString(R.string.ttc_placeholder)
                    }
                    binding.ttcText.text = ttcText
                    warningController.onDetection(result, ttc)
                }
            }
        }
        analyzer = newAnalyzer

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(binding.previewView.display?.rotation ?: Surface.ROTATION_0)
            .build()
            .apply {
                setAnalyzer(analyzerExecutor, newAnalyzer)
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Toast.makeText(this, exc.localizedMessage ?: "Camera error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }
}
