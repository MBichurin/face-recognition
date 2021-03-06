package com.facerecognition

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

// Initialize a CameraSelector
var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
// To check whether it's front or rear cam turned on
var frontCam = AtomicBoolean(true)
// Application mode (Recognition or Face adding)
var AddFaceMode = AtomicBoolean(false)
// Skip the next frame
var skipNextFrame = AtomicBoolean(false)

class MainActivity : AppCompatActivity(), BBoxUpdater {
    // App permissions
    private val permissions = arrayOf(android.Manifest.permission.CAMERA,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    companion object {
        private const val REQUEST_CODE = 228
        // Maximum number of shots
        private const val MAX_N_SHOTS = 5
    }
    // Threadpool for cam
    private lateinit var cameraExecutor: ExecutorService
    // Analyzer instance
    private lateinit var myAnalyzer: MyAnalyzer
    // To store Preview's shape
    private var preview_width = -1
    private var preview_height = -1
    // Map of saved identities and name of file it's stored in
    private var SavedFaces = mutableMapOf<String, FloatArray>()
    private val IdMap_file = "/saved_faces.ser"
    // Name and embedding of the new face and the last detected embedding
    private var nameToAdd = "???"
    private lateinit var embeddingToAdd: FloatArray
    private var lastEmbedding = FloatArray(0)
    // Number of made shots
    private var n_shots = 0
    // To store the displayed names
    private val DisplayedNames: Queue<TextView> = LinkedList<TextView>()
    // The shape of bboxView is already known
    private var bboxView_shape_is_known = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Update camera switcher
        if (frontCam.get()) {
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
        // Initialize interpreter for recognition
        myAnalyzer.initModel(assets)
        // Initialize camera threadpool
        cameraExecutor = Executors.newSingleThreadExecutor()
        // Read saved identities from file
        readSavedFaces()

        // For switcher sliding
        camSwitcher.setOnCheckedChangeListener { view, _ ->
            switchCam(view)
        }

        if (permissionsDenied()) requestPermissions()
        else runCamera()
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
        // Update the stored dictionary of IDs
        writeSavedFaces()
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

    private fun readSavedFaces() {
        Log.d("JOPA", "SavedFaces were read")
        lateinit var file: File
        try {
            file = File(externalMediaDirs.firstOrNull()!!, IdMap_file)
        }
        catch(exc: Exception) {
            writeSavedFaces()
            return
        }

        if (file.exists()) {
            val fileInStream = FileInputStream(file)
            val objInStream = ObjectInputStream(fileInStream)
            SavedFaces = objInStream.readObject() as MutableMap<String, FloatArray>

            objInStream.close()
        }
    }

    private fun writeSavedFaces() {
        Log.d("JOPA", "SavedFaces were written")
        val file = File(externalMediaDirs.firstOrNull()!!, IdMap_file)

        val fileOutStream = FileOutputStream(file)
        val objOutStream = ObjectOutputStream(fileOutStream)
        objOutStream.writeObject(SavedFaces)

        objOutStream.close()
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

                    val preview_size = preview.attachedSurfaceResolution ?: Size(0, 0)
                    preview_width = preview_size.width
                    preview_height = preview_size.height
                }
                catch(exc: Exception) {
                    Log.e("CameraX", "Use case binding failed", exc)
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    fun switchCam(view: View) {
        if (frontCam.get()) {
            frontCam.set(false)
            camSwitcher.text = getString(R.string.cam_switcher_text2)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
        else {
            frontCam.set(true)
            camSwitcher.text = getString(R.string.cam_switcher_text1)
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }

        // If bitmap isn't empty
        if (bboxView_shape_is_known.get()) {
            // Clear bounding boxes
            val bitmap_drawable = bboxesView.drawable as BitmapDrawable
            var bitmap = bitmap_drawable.bitmap

            bitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
            bboxesView.setImageBitmap(bitmap)
        }
        else {
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bboxesView.setImageBitmap(bitmap)
        }
        // Clear displayed names
        eraseDisplayedNames()

        // Skip the visualisation for the current frame
        skipNextFrame.set(true)

        if (permissionsDenied()) requestPermissions()
        else runCamera()
    }

    fun changeMode(view: View) {
        if (AddFaceMode.get()) {
            // Set the button's image to a '+' sign
            addFaceButton.setImageResource(android.R.drawable.ic_input_add)
            // Set Prediction mode
            AddFaceMode.set(false)
            // Hide 'Insert name' screen
            insertNameTextbox.visibility = View.INVISIBLE
            insertNameButton.visibility = View.INVISIBLE
            plainWhiteView.visibility = View.INVISIBLE
            // Hide the 'Make a shot' button
            shotButton.visibility = View.INVISIBLE
            // Show switcher
            camSwitcher.visibility = View.VISIBLE
            // Forget the entered name
            nameToAdd = "???"
        }
        else {
            // Set the button's image to an 'x' sign
            addFaceButton.setImageResource(android.R.drawable.ic_delete)
            // Set Face adding mode
            AddFaceMode.set(true)
            // Set the number of made shots and embeddingToAdd to 0s
            n_shots = 0
            embeddingToAdd = FloatArray(128)
            // Show 'Insert name' screen
            insertNameTextbox.visibility = View.VISIBLE
            insertNameButton.visibility = View.VISIBLE
            plainWhiteView.visibility = View.VISIBLE
            // Hide switcher and displayed names
            camSwitcher.visibility = View.INVISIBLE
            eraseDisplayedNames()
        }
    }

    fun rememberTheName(view: View) {
        if (insertNameTextbox.text.toString() == "") {
            // Show a message
            Toast.makeText(
                this, "Names couldn't be empty",
                Toast.LENGTH_LONG).show()
        }
        else {
            // Save the input and clear it
            nameToAdd = insertNameTextbox.text.toString()
            insertNameTextbox.text.clear()
            // Hide the keyboard
            try {
                val imm: InputMethodManager =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
            } catch (e: Exception) {
            }
            // Show a message
            Toast.makeText(
                this, "Take $MAX_N_SHOTS shots of a person you'd like to add",
                Toast.LENGTH_LONG).show()
            // Show the 'Make a shot' button
            shotButton.visibility = View.VISIBLE
            // Hide 'Insert name' screen
            insertNameTextbox.visibility = View.INVISIBLE
            insertNameButton.visibility = View.INVISIBLE
            plainWhiteView.visibility = View.INVISIBLE
            // Show switcher
            camSwitcher.visibility = View.VISIBLE
        }
    }

    fun makeShot(view: View) {
        if (lastEmbedding.isEmpty()) {
            // Show a message
            Toast.makeText(this, "No faces found. Try again",
                Toast.LENGTH_LONG).show()
        }
        else {
            n_shots++
            // Show a message
            Toast.makeText(
                this, "$n_shots",
                Toast.LENGTH_SHORT
            ).show()

            embeddingToAdd = embeddingToAdd.zip(lastEmbedding) {a, b -> a + b}.toFloatArray()

            if (n_shots == MAX_N_SHOTS) {
                for (i in embeddingToAdd.indices) {
                    embeddingToAdd[i] = embeddingToAdd[i] / 5
                }

                // Add new embedding
                SavedFaces[nameToAdd] = embeddingToAdd
                // Save the map
                writeSavedFaces()
                // Show a message
                Toast.makeText(
                    this, "New identity's saved",
                    Toast.LENGTH_SHORT
                ).show()
                // Change mode to Recognition
                changeMode(shotButton)
            }
        }
    }

    fun L2_sq(A: FloatArray, B: FloatArray): Float {
        return A.zip(B) {a, b -> (a - b).pow(2)}.sum()
    }

    fun recognize(new_vec: FloatArray): String {
        var min_dist = -1.0f
        var closest_name = "???"
//        val threshold = 1.242f
        val threshold = 100

        for ((saved_name, saved_vec) in SavedFaces) {
            val loc_dist = L2_sq(new_vec, saved_vec)
            Log.d("JOPA", "$saved_name $loc_dist")
            if (((min_dist == -1.0f) || (min_dist > loc_dist)) && loc_dist <= threshold) {
                min_dist = loc_dist
                closest_name = saved_name
            }
        }

        return closest_name
    }

    private fun eraseDisplayedNames() {
        while (DisplayedNames.isNotEmpty())
            myConstraintLayout.removeView(DisplayedNames.remove())
    }

    private fun displayText(name: String, x: Int, y: Int, bg_clr: String) {
        // Set text, background color and coordinates
        val textView = TextView(this)
        textView.text = name
        textView.setBackgroundColor(Color.parseColor(bg_clr))
        textView.x = x.toFloat()
        textView.y = y.toFloat()

        // Remember the TextView
        DisplayedNames.add(textView)

        val params = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT)

        myConstraintLayout.addView(textView, params)
    }

    override fun updateBBoxes(faces: List<FirebaseVisionFace>?,
                              analyze_width: Int, analyze_height: Int,
                              Descriptors: Array<FloatArray>) {
        runOnUiThread{
            if (skipNextFrame.get()) {
                skipNextFrame.set(false)
                return@runOnUiThread
            }

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
                if (preview_width > preview_height) {
                    // Swap
                    preview_height += preview_width
                    preview_width = preview_height - preview_width
                    preview_height -= preview_width
                }

                scaled_win_height = preview_height
                scaled_win_width = win_width * scaled_win_height / win_height
                offset_x = (preview_width - scaled_win_width) / 2
                offset_y = 0
            } else {
                if (preview_width < preview_height) {
                    // Swap
                    preview_height += preview_width
                    preview_width = preview_height - preview_width
                    preview_height -= preview_width
                }

                scaled_win_width = preview_width
                scaled_win_height = win_height * scaled_win_width / win_width
                offset_y = (preview_height - scaled_win_height) / 2
                offset_x = 0
            }

            // Create a bitmap
            val bm =
                Bitmap.createBitmap(scaled_win_width, scaled_win_height, Bitmap.Config.ARGB_8888)

            // Erase the names that were displayed before
            eraseDisplayedNames()

            // Face adding mode
            if (AddFaceMode.get()) {
                // Bounding box and text's background colors
                val bbox_clr = (255 shl 24) or 255
                val text_bg_clr = "#8c0000ff"

                // If there are faces, iterate through them and draw bboxes
                if (faces?.isNotEmpty()!! and Descriptors.isNotEmpty()) {
                    val face = faces[0]
                    // Remember the last embedding
                    lastEmbedding = Descriptors[0]

                    // Get coordinates relative to analyzer frame
                    var left = if (frontCam.get()) analyze_width - face.boundingBox.right
                    else face.boundingBox.left
                    var right = if (frontCam.get()) analyze_width - face.boundingBox.left
                    else face.boundingBox.right
                    var bottom = face.boundingBox.bottom
                    var top = face.boundingBox.top

                    // Cast to coordinates relative to preview frame
                    left = (left * preview_width) / analyze_width as Int
                    right = (right * preview_width) / analyze_width as Int
                    top = (top * preview_height) / analyze_height as Int
                    bottom = (bottom * preview_height) / analyze_height as Int

                    // Cast to coordinates relative to scaled window
                    left -= offset_x
                    right -= offset_x
                    top -= offset_y
                    bottom -= offset_y

                    // Draw a bbox around the face
                    for (x in left..right) {
                        if (x !in 0 until scaled_win_width) continue
                        if (top in 0 until scaled_win_height) bm.setPixel(x, top, bbox_clr)
                        if (bottom in 0 until scaled_win_height) bm.setPixel(x, bottom, bbox_clr)
                    }
                    for (y in top..bottom) {
                        if (y !in 0 until scaled_win_height) continue
                        if (left in 0 until scaled_win_width) bm.setPixel(left, y, bbox_clr)
                        if (right in 0 until scaled_win_width) bm.setPixel(right, y, bbox_clr)
                    }

                    // Cast to coordinates relative to original window
                    left = left * win_width / scaled_win_width
                    top = top * win_height / scaled_win_height

                    // Display the name if it's known
                    if (nameToAdd != "???")
                        displayText("$nameToAdd?", left, top, text_bg_clr)
                } else {
                    // There's no faces on the frame
                    lastEmbedding = FloatArray(0)
                }
            }
            // Recognition mode
            else {
                // Colors for recognized and unrecognized faces
                val green_u8 = (255 shl 24) or (255 shl 8)
                val green_hex = "#8c00ff00"
                val red_u8 = (255 shl 24) or (255 shl 16)
                val red_hex = "#8cff0000"

                // If there are faces, iterate through them and draw bboxes
                if (faces?.isNotEmpty()!!) {
                    for ((face, embedding) in faces zip Descriptors) {
                        // Recognize the face
                        val face_name = recognize(embedding)
                        Log.d("JOPA", "It's $face_name")

                        // Set colors depending on if the face was recognized
                        val bbox_clr = if (face_name == "???") red_u8 else green_u8
                        val text_bg_clr = if (face_name == "???") red_hex else green_hex

                        // Get coordinates relative to analyzer frame
                        var left = if (frontCam.get()) analyze_width - face.boundingBox.right
                        else face.boundingBox.left
                        var right = if (frontCam.get()) analyze_width - face.boundingBox.left
                        else face.boundingBox.right
                        var bottom = face.boundingBox.bottom
                        var top = face.boundingBox.top

                        // Cast to coordinates relative to preview frame
                        left = (left * preview_width) / analyze_width as Int
                        right = (right * preview_width) / analyze_width as Int
                        top = (top * preview_height) / analyze_height as Int
                        bottom = (bottom * preview_height) / analyze_height as Int

                        // Cast to coordinates relative to scaled window
                        left -= offset_x
                        right -= offset_x
                        top -= offset_y
                        bottom -= offset_y

                        // Draw a bbox around the face
                        for (x in left..right) {
                            if (x !in 0 until scaled_win_width) continue
                            if (top in 0 until scaled_win_height) bm.setPixel(x, top, bbox_clr)
                            if (bottom in 0 until scaled_win_height) bm.setPixel(x, bottom, bbox_clr)
                        }
                        for (y in top..bottom) {
                            if (y !in 0 until scaled_win_height) continue
                            if (left in 0 until scaled_win_width) bm.setPixel(left, y, bbox_clr)
                            if (right in 0 until scaled_win_width) bm.setPixel(right, y, bbox_clr)
                        }

                        // Cast to coordinates relative to original window
                        left = left * win_width / scaled_win_width
                        top = top * win_height / scaled_win_height

                        // Display the name
                        displayText(face_name, left, top, text_bg_clr)
                    }
                }
            }

            // Display bounding boxes
            bboxesView.setImageBitmap(bm)
            bboxView_shape_is_known.set(true)
        }
    }
}

interface BBoxUpdater {
    fun updateBBoxes(faces: List<FirebaseVisionFace>?, analyze_width: Int, analyze_height: Int,
                     Descriptors: Array<FloatArray>)
}