package com.example

import android.Manifest
import android.app.AppComponentFactory
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.impl.ImageOutputConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.another_window.*
import java.net.ConnectException
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GreetingScreen: AppCompatActivity() {
    private val permissions = arrayOf(android.Manifest.permission.CAMERA)
    companion object {
        private const val REQUEST_CODE = 228
    }
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var myAnalyzer: MyAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.another_window)

        // Extract the passed data from the Intent that started this activity
        val name_of_user = intent.getStringExtra(EXTRA_KEY)
        // Show the greeting message
        greetingText.apply {
            text = "Wassup " + name_of_user
        }

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
            else {
                runCamera()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private class MyAnalyzer: ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {

            // Image must be closed, use a copy of it for analysis
            image.close()
        }
    }
}
