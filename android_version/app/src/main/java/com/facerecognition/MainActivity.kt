package com.facerecognition

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val permissions = arrayOf(android.Manifest.permission.CAMERA,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
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

        if (permissionsDenied())
            requestPermissions()
        else
            runCamera()
    }

    override fun onRequestPermissionsResult
                (requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            if (permissionsDenied()) {
                Toast.makeText(this,
                    "The app needs access to the camera to work properly :(",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
            else
                runCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun permissionsDenied(): Boolean {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_DENIED)
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
    }

    // Initialize a CameraSelector and select the camera
    var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

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
                            .also {
                                it.setAnalyzer(cameraExecutor, myAnalyzer)
                            }

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

    private fun switchCam(view: View) {
        if (camSwitcher.text == getString(R.string.cam_switcher_text1)) {
            camSwitcher.text = getString(R.string.cam_switcher_text2)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
        else {
            camSwitcher.text = getString(R.string.cam_switcher_text1)
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }

        if (permissionsDenied())
            requestPermissions()
        else
            runCamera()
    }
}