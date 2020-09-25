package com.facerecognition

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.media.Image
import android.os.Environment
import android.util.Log
import android.view.Surface.ROTATION_0
import android.view.Surface.ROTATION_90
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MyAnalyzer: ImageAnalysis.Analyzer {

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
//        // Convert YUV_420_888 to Bitmap
//        val bitmap = toBitmap(imageProxy.image!!)
//        // Convert the image to FirebaseVisionImage
//        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val image = FirebaseVisionImage.fromMediaImage(imageProxy.image!!, ROTATION_0)

        // Configure and build a detector
        val detectorOptions = FirebaseVisionFaceDetectorOptions.Builder().build()
        val detector = FirebaseVision.getInstance().getVisionFaceDetector(detectorOptions)

        // Pass the image to the detector
        detector.detectInImage(image)
            .addOnCompleteListener {faces ->
                successfulDetection(faces.result)
            }
            .addOnFailureListener {e ->
                Log.e("FaceDetector", e.message!!)
            }

        // Image must be closed, use a copy of it for analysis
        imageProxy.close()
    }

    private fun successfulDetection(faces: List<FirebaseVisionFace>?) {
        Log.d("JOPA", "successfulDetection()")
        if (faces?.isNotEmpty()!!) {
            for (face in faces) {
                Log.d("JOPA", face.boundingBox.toString())
            }
        }
        else {
            Log.d("JOPA", "no faces found(")
        }
    }

    private fun toBitmap(image : Image): Bitmap{
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val yuv = out.toByteArray()
        return BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
    }

    /*private fun BitmaptoNv21( bitmap: Bitmap ): ByteArray {
        val argb = IntArray(bitmap.width * bitmap.height )
        bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val yuv = ByteArray(bitmap.height * bitmap.width + 2 * Math.ceil(bitmap.height / 2.0).toInt()
                * Math.ceil(bitmap.width / 2.0).toInt())
        encodeYUV420SP( yuv, argb, bitmap.width, bitmap.height)
        return yuv
    }

    private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize
        var R: Int
        var G: Int
        var B: Int
        var Y: Int
        var U: Int
        var V: Int
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                R = argb[index] and 0xff0000 shr 16
                G = argb[index] and 0xff00 shr 8
                B = argb[index] and 0xff shr 0
                Y = (66 * R + 129 * G + 25 * B + 128 shr 8) + 16
                U = (-38 * R - 74 * G + 112 * B + 128 shr 8) + 128
                V = (112 * R - 94 * G - 18 * B + 128 shr 8) + 128
                yuv420sp[yIndex++] = (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
                    yuv420sp[uvIndex++] = (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
                }
                index++
            }
        }
    }*/
}