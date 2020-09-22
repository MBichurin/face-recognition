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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.another_window)

        // Extract the passed data from the Intent that started this activity
        val name_of_user = intent.getStringExtra(EXTRA_KEY)
        // Show the greeting message
        greetingText.apply {
            text = "Wassup " + name_of_user
        }

        if (hasNoPermissions()) {
            requestPermissions()
        }
        else {
            runCamera()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
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
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, MyAnalyzer { value ->
                            Log.d("CameraX", "Returned value = $value")
                        })
                    }

                // Initialize a CameraSelector and select the camera
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    // Unbind everything from cameraProvider
                    cameraProvider.unbindAll()
                    // Bind camera selector and preview to cameraProvider
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview)
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

    private class MyAnalyzer(private val listener: AnalyzeListener): ImageAnalysis.Analyzer {
        private fun ByteBuffer.toByteArray(): ByteArray {
            // Rewind the buffer to 0
            rewind()
            val data = ByteArray(remaining())
            // Copy the buffer into a byte array
            get(data)
            // Return the byte array
            return data
        }

        override fun analyze(image: ImageProxy) {
            listener(322)
            image.close()
        }
    }
}
