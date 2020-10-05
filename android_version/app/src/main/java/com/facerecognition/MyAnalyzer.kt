package com.facerecognition

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.media.Image
import android.os.Environment
import android.util.Log
import android.view.Surface
import android.view.Surface.*
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.graphics.get
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import kotlinx.android.synthetic.main.activity_main.bboxesView
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
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

    // Facenet model
    private lateinit var facenet: Interpreter

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
                successfulDetection(faces.result, image.bitmap)
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetector", e.message!!)
            }
    }

    fun setBBoxUpdaterListener(_listener: BBoxUpdater) {
        listener = _listener
    }

    private fun successfulDetection(faces: List<FirebaseVisionFace>?, bitmap: Bitmap) {
        //! TODO: recognition
        val img_size = 160

        if (faces?.isNotEmpty()!!) {
            for (face in faces) {
                // Crop
                var img_face = CropFace(bitmap, face.boundingBox)

                // Resize
                img_face = Bitmap.createScaledBitmap(img_face, img_size, img_size, false)

                // Initialize a bytebuffer
                val img_buffer = ByteBuffer.allocateDirect(img_size * img_size * 3 * 4)
                img_buffer.order(ByteOrder.nativeOrder())

                // Get all pixels
                val pixels = IntArray(img_size * img_size)
                img_face.getPixels(pixels, 0, img_size, 0, 0, img_size, img_size)

                for (y in 0 until img_size)
                    for (x in 0 until img_size) {
                        // Get the current pixel
                        val pixel = pixels[y * img_size + x]
                        // Red
                        var next = (((pixel shr 16) and 255) - 127.5) / 127.5
                        img_buffer.putFloat(next.toFloat())
                        // Green
                        next = (((pixel shr 8) and 255) - 127.5) / 127.5
                        img_buffer.putFloat(next.toFloat())
                        // Blue
                        next = ((pixel and 255) - 127.5) / 127.5
                        img_buffer.putFloat(next.toFloat())
                    }

                // Run Facenet
                val output = Array(1) {FloatArray(128)}
                facenet.run(img_buffer, output)
                Log.d("JOPA", "Facenet output: " + ArrayToString(output[0]))
            }
        }

        listener.updateBBoxes(faces, bitmap.width, bitmap.height)
    }

    private fun ArrayToString(A: FloatArray): String {
        var s = ""
        for (el in A)
            s += "$el "
        return s
    }

    private fun CropFace(frame_bm: Bitmap, face_bbox: Rect): Bitmap {
        val bbox_left = max(face_bbox.left, 0)
        val bbox_top = max(face_bbox.top, 0)
        val bbox_width = min(face_bbox.width() + face_bbox.left - bbox_left,
            frame_bm.width - bbox_left)
        val bbox_height = min(face_bbox.height() + face_bbox.top - bbox_top,
            frame_bm.height - bbox_top)
        return Bitmap.createBitmap(frame_bm, bbox_left, bbox_top, bbox_width, bbox_height)
    }

    private fun min(a: Int, b: Int): Int {
        return if (a < b) a else b
    }

    private fun max(a: Int, b: Int): Int {
        return if (a > b) a else b
    }

    fun initInterpreter(assetManager: AssetManager) {
        val fileDescriptor = assetManager.openFd("facenet.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLen = fileDescriptor.declaredLength

        val modelFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLen)

        facenet = Interpreter(modelFile)
    }
}