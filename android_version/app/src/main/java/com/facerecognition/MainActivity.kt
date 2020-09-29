package com.facerecognition

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.media.Image
import android.media.VolumeShaper
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.Surface.ROTATION_0
import android.view.View
import android.view.WindowManager
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

// Initialize a CameraSelector
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

    override fun updateBBoxes(faces: List<FirebaseVisionFace>?, frm_width: Int, frm_height: Int) {
        val rect = Rect()
        window.decorView.getWindowVisibleDisplayFrame(rect)
        val win_width = rect.right - rect.left
        val win_height = rect.bottom - rect.top
        val scaled_win_height: Int
        val scaled_win_width: Int
        val offset_x: Int
        val offset_y: Int

        // Set resolution
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            scaled_win_height = frm_height
            scaled_win_width = win_width * scaled_win_height / win_height
            offset_x = (frm_width - scaled_win_width) / 2
            offset_y = 0
            Log.d("JOPA", "Portrait: window($win_width:$win_height), " +
                    "frm($frm_width:$frm_height), " +
                    "scaled_window($scaled_win_width:$scaled_win_height), " +
                    "offset=($offset_x, $offset_y)")
        }
        else {
            scaled_win_width = frm_width
            scaled_win_height = win_height * scaled_win_width / win_width
            offset_y = (frm_height - scaled_win_height) / 2
            offset_x = 0

            Log.d("JOPA", "Landscape: window($win_width:$win_height), " +
                    "frm($frm_width:$frm_height), " +
                    "scaled_window($scaled_win_width:$scaled_win_height), " +
                    "offset=($offset_x, $offset_y)")
        }

        // Create a bitmap
        val bm = Bitmap.createBitmap(scaled_win_width, scaled_win_height, Bitmap.Config.ARGB_8888)

        // Bounding boxes color
        val bboxColor = (255 shl 24) or (255 shl 8)

//        // Make it completely white
//        for (x in 0 until scaled_win_width)
//            for (y in 0 until scaled_win_height)
//                bm.setPixel(x, y, 0.inv())

        // If there are faces, iterate through them and draw bboxes
        if (faces?.isNotEmpty()!!) {
            for (face in faces) {
                // Draw a bbox around the face
                var left = if (frontCam) frm_width - face.boundingBox.right
                            else face.boundingBox.left
                var right = if (frontCam) frm_width - face.boundingBox.left
                            else face.boundingBox.right
                var bottom = face.boundingBox.bottom
                var top = face.boundingBox.top

                left -= offset_x
                right -= offset_x
                top -= offset_y
                bottom -= offset_y

                for (x in left..right) {
                    if (x !in 0 until scaled_win_width) continue
                    if (top in 0 until scaled_win_height) bm.setPixel(x, top, bboxColor)
                    if (bottom in 0 until scaled_win_height) bm.setPixel(x, bottom, bboxColor)
                }
                for (y in top..bottom) {
                    if (y !in 0 until scaled_win_height) continue
                    if (left in 0 until scaled_win_width) bm.setPixel(left, y, bboxColor)
                    if (right in 0 until scaled_win_width) bm.setPixel(right, y, bboxColor)
                }
            }
        }

        // Display bounding boxes
        bboxesView.setImageBitmap(bm)
    }
}

interface BBoxUpdater {
    fun updateBBoxes(faces: List<FirebaseVisionFace>?, frm_width: Int, frm_height: Int)
}