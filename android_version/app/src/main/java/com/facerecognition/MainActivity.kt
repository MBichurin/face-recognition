package com.facerecognition

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val permissions = arrayOf(android.Manifest.permission.CAMERA)
    companion object {
        private const val REQUEST_CODE = 228
    }
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var myAnalyzer: MyAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        myAnalyzer = MyAnalyzer()
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (hasNoPermissions()) {
            requestPermissions()
        }
        else {
            runCamera()
        }
    }

    private fun hasNoPermissions(): Boolean {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED)
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
    }

    private fun runCamera() {
        // Used to bind the camera lifecycle to the lifecycle owner
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        // Add a listener
        cameraProviderFuture.addListener(
                Runnable {
                    // Used to bind the camera lifecycle to the lifecycle owner
                    // within the apps process
                    val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                    // Initialize, build a preview and set the viewFinder's surface on it
                    val preview = Preview.Builder()
                            .build()
                            .also {
                                it.setSurfaceProvider(viewFinder.surfaceProvider)
                            }

                    // Initialize, build an analyzer and set the viewFinder's surface on it
                    val imageAnalyser = ImageAnalysis.Builder()
                            // To get current frame by skipping previous ones if needed
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                    imageAnalyser.setAnalyzer(cameraExecutor, myAnalyzer)

                    // Initialize a CameraSelector and select the camera
                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    try {
                        // Unbind everything from cameraProvider
                        cameraProvider.unbindAll()
                        // Bind camera selector and use-cases to cameraProvider
                        cameraProvider.bindToLifecycle(
                                this, cameraSelector, preview, imageAnalyser)
                    }
                    catch(exc: Exception) {
                        Log.e("CameraX", "Use case binding failed", exc)
                    }
                },
                ContextCompat.getMainExecutor(this)
        )
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            if (hasNoPermissions()) {
                Toast.makeText(this,
                        "The app needs access to the camera to work properly :(",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
            else runCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}