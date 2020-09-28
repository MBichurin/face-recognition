package com.facerecognition

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.Image
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Surface.ROTATION_0
import android.view.View
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Initialize a CameraSelector and select the camera
var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
var frontCam = true

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

        // Update camera switcher
        if (frontCam) {
            camSwitcher.text = getString(R.string.cam_switcher_text1)
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }
        else {
            camSwitcher.text = getString(R.string.cam_switcher_text2)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }

        myAnalyzer = MyAnalyzer()
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (permissionsDenied()) {
            requestPermissions()
        }
        else {
            saveImage()
            runCamera()
        }
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

    fun switchCam(view: View) {
        if (frontCam) {
            frontCam = false
            camSwitcher.text = getString(R.string.cam_switcher_text2)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
        else {
            frontCam = true
            camSwitcher.text = getString(R.string.cam_switcher_text1)
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }

        if (permissionsDenied())
            requestPermissions()
        else
            runCamera()
    }

    private fun saveImage() {
        // Get bitmap from imageView
        val bitmap_drawable = imageView.drawable as BitmapDrawable
        val bitmap =  bitmap_drawable.bitmap

        // Write the image to the external storage
        val outputDirectory = getOutputDirectory()
        val photoFile = File(outputDirectory, "willsmith.jpg")
        val outstream = FileOutputStream(photoFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outstream)
        outstream.flush()
        outstream.close()
        Log.d("JOPA", "Image's Saved $photoFile")

        // Get pixel of the bitmap
        val width = bitmap.width
        val height = bitmap.height
        var pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Change pixels and use them in a mutable copy of the bitmap
        for (i in pixels.indices)
            // [int]pixel = (Alpha   |     Red      |    Green  |  Blue)
            pixels[i] = (255 shl 24) + (255 shl 16) + (255 shl 8) + 255
        val changed_bm = bitmap.copy(bitmap.config, true)
        changed_bm.setPixels(pixels, 0, width, 0, 0, width, height)

        // Display changed bitmap
        imageView.setImageBitmap(changed_bm)
    }

    private fun getOutputDirectory(): File {
        return externalMediaDirs.firstOrNull()!!
    }
}