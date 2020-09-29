package com.facerecognition

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Initialize a CameraSelector and select the camera
var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
var frontCam = true

class MainActivity : AppCompatActivity(), BBoxUpdater {
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
        // Set the main activity as a listener for our analyzer
        myAnalyzer.setBBoxUpdaterListener(this)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (permissionsDenied()) {
            requestPermissions()
        }
        else {
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
                                this, cameraSelector, imageAnalyser, preview)
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

    override fun updateBBoxes(bm: Bitmap, faces: List<FirebaseVisionFace>?) {
        val width = bm.width
        val height = bm.height

        // Make a mutable copy of the bitmap and flip it
        val matrix = Matrix().apply { postScale(if (frontCam) -1f else 1f, 1f, width * 0.5f, height * 0.5f) }
        val new_bm = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true)

        // Bounding boxes color
        val bboxColor = (255 shl 24) or (255 shl 8)

//        // Make it completely white
//        for (x in 0 until width)
//            for (y in 0 until height)
//                new_bm.setPixel(x, y, 0.inv())

        // If there are faces, iterate through them and draw bboxes
        if (faces?.isNotEmpty()!!) {
            for (face in faces) {
                // Draw a bbox around the face
                var left = if (frontCam) width - face.boundingBox.right
                            else face.boundingBox.left
                val right = if (frontCam) width - face.boundingBox.left
                            else face.boundingBox.right
                val bottom = face.boundingBox.bottom
                val top = face.boundingBox.top

                for (x in left..right) {
                    if (x !in 0 until width) continue
                    if (top in 0 until height) new_bm.setPixel(x, top, bboxColor)
                    if (bottom in 0 until height) new_bm.setPixel(x, bottom, bboxColor)
                }
                for (y in top..bottom) {
                    if (y !in 0 until height) continue
                    if (left in 0 until width) new_bm.setPixel(left, y, bboxColor)
                    if (right in 0 until width) new_bm.setPixel(right, y, bboxColor)
                }
            }
        }

        // Display bounding boxes
        bboxesView.setImageBitmap(new_bm)
    }
}

interface BBoxUpdater {
    fun updateBBoxes(bm: Bitmap, faces: List<FirebaseVisionFace>?)
}