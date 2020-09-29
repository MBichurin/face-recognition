package com.facerecognition

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.media.Image
import android.os.Environment
import android.util.Log
import android.view.Surface
import android.view.Surface.*
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import kotlinx.android.synthetic.main.activity_main.bboxesView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MyAnalyzer: ImageAnalysis.Analyzer {
    private lateinit var listener: BBoxUpdater

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val t_start = System.currentTimeMillis()

        // Get rotation of the frame
        val rotation = when (imageProxy.imageInfo.rotationDegrees) {
            // Upside down
            in 45..134 -> ROTATION_90
            // Clockwise phone rotation
            in 135..224 -> ROTATION_180
            // Vertical view
            in 225..314 -> ROTATION_270
            // Anticlockwise phone rotation
            else -> ROTATION_0
        }

        // Convert to FirebaseVisionImage + rotate
        val image = FirebaseVisionImage.fromMediaImage(imageProxy.image!!, rotation)

        // Image must be closed, use a copy of it for analysis
        imageProxy.close()

        // Configure and build a detector
        val detectorOptions = FirebaseVisionFaceDetectorOptions.Builder()
            .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
            .build()
        val detector = FirebaseVision.getInstance().getVisionFaceDetector(detectorOptions)

        // Pass the image to the detector
        detector.detectInImage(image)
            .addOnCompleteListener { faces ->
                successfulDetection(image.bitmap, faces.result)
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetector", e.message!!)
            }

        val t_finish = System.currentTimeMillis()
        Log.d("JOPA", (t_finish - t_start).toString())
    }

    fun setBBoxUpdaterListener(_listener: BBoxUpdater) {
        listener = _listener
    }

    private fun successfulDetection(bm: Bitmap, faces: List<FirebaseVisionFace>?) {
        listener.updateBBoxes(bm, faces)
    }
}