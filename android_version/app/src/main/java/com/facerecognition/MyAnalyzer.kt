package com.facerecognition

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.view.Surface.*
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.MappedByteBuffer

// To store embeddings
private var Desctiptors = Array(0) { FloatArray(128) }

// To not detect faces on every frame, just one at once
private var detect_describe_isBusy = AtomicBoolean(false)

class MyAnalyzer: ImageAnalysis.Analyzer {
    private lateinit var listener: BBoxUpdater

    // Model file
    private lateinit var modelFile: MappedByteBuffer

    // Configure and build a detector
    private val detectorOptions = FirebaseVisionFaceDetectorOptions.Builder()
        .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
        .build()
    private val detector = FirebaseVision.getInstance().getVisionFaceDetector(detectorOptions)


    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val t1 = System.currentTimeMillis()

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
        if (detect_describe_isBusy.get())
            return
        // Detector or Descriptor is busy now
        detect_describe_isBusy.set(true)

        // Pass the image to the detector
        detector.detectInImage(image)
            .addOnCompleteListener { faces ->
                Log.d("JOPA", "t{Detection of ${faces.result?.size} face(s)} = ${System.currentTimeMillis() - t1}")
                // Pass data to the listener
                successfulDetection(faces.result, image.bitmap)
            }
            .addOnFailureListener { e ->
                // Detector is free now
                detect_describe_isBusy.set(false)
                Log.e("FaceDetector", e.message!!)
            }
    }

    fun setBBoxUpdaterListener(_listener: BBoxUpdater) {
        listener = _listener
    }

    private fun successfulDetection(faces: List<FirebaseVisionFace>?, bitmap: Bitmap) {
        if (faces?.isNotEmpty()!!) {
            val recognition = Recognition(faces, bitmap, modelFile)
            Thread(recognition).start()
        }
        else {
            // Descriptor is free now
            detect_describe_isBusy.set(false)
        }

        listener.updateBBoxes(faces, bitmap.width, bitmap.height, Desctiptors)
    }

    fun initModel(assetManager: AssetManager) {
        val fileDescriptor = assetManager.openFd("facenet.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLen = fileDescriptor.declaredLength

        modelFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLen)
    }

    class Recognition constructor(private val faces: List<FirebaseVisionFace>,
                                  private val bitmap: Bitmap,
                                  private val modelFile: MappedByteBuffer): Runnable {
        override fun run() {
            var t1 = System.currentTimeMillis()

            // Interpreter of facenet model
            val facenet = Interpreter(modelFile)

            var t2 = System.currentTimeMillis()
            Log.d("JOPA", "t{new Interpreter()} = ${t2 - t1}")
            t1 = t2

//            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            // Initialize Descriptors
            Desctiptors = Array(faces.size) { FloatArray(128) }
            // Image size
            val img_size = 160
            // Iterate through faces
            for ((i, face) in faces.withIndex()) {
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

                Desctiptors[i] = output[0]
            }

            // Descriptor is free now
            detect_describe_isBusy.set(false)

            t2 = System.currentTimeMillis()
            Log.d("JOPA", "t{Description of ${faces.size} face(s)} = ${t2 - t1}")
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
    }
}