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
import java.util.concurrent.atomic.AtomicBoolean

class MyAnalyzer: ImageAnalysis.Analyzer {
    private lateinit var listener: BBoxUpdater

    // Configure and build a detector
    private val detectorOptions = FirebaseVisionFaceDetectorOptions.Builder()
        .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
        .build()
    private val detector = FirebaseVision.getInstance().getVisionFaceDetector(detectorOptions)

    // To not detect faces on every frame, just one at once
    private var detectorIsBusy = AtomicBoolean(false)

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
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

        // If detector is busy, skip the frame
        if (detectorIsBusy.get())
            return
        // Detector is busy now
        detectorIsBusy.set(true)

        // Pass the image to the detector
        detector.detectInImage(image)
            .addOnCompleteListener { faces ->
                // Detector is free now
                detectorIsBusy.set(false)
                // Pass data to the listener
                successfulDetection(faces.result, image.bitmap.width, image.bitmap.height)
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetector", e.message!!)
            }
    }

    fun setBBoxUpdaterListener(_listener: BBoxUpdater) {
        listener = _listener
    }

    private fun successfulDetection(faces: List<FirebaseVisionFace>?, width: Int, height: Int) {
        //! TODO: recognition
        listener.updateBBoxes(faces, width, height)
    }
}