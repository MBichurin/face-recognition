package com.facerecognition

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class MyAnalyzer: ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {

        // Image must be closed, use a copy of it for analysis
        image.close()
    }
}