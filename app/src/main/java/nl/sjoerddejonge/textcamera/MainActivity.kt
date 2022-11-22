package nl.sjoerddejonge.textcamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.util.TypedValue.COMPLEX_UNIT_PX
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.TextViewCompat.getMaxLines
import nl.sjoerddejonge.textcamera.databinding.ActivityMainBinding
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


private const val TAG = "TextCamera"

/**
 * A full screen 'camera app', that converts camera images to text.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var textImagePreview: TextView
    // TODO
    // Maybe set aspect ratio instead of target resolution?
    private var targetResolutionPortrait = Size(480, 640)// 540 1200

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)

        /**
         * Setting up fullscreen
         */
        // Display edge to edge content:
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Set the layout as the content view:
        window.setContentView(viewBinding.root)
        // Hide the status bar:
        hideStatusBar()
        // Hide the app title bar:
        supportActionBar?.hide()

        /**
         * Setting up camera
         */
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        textImagePreview = viewBinding.textImagePreview

        //viewBinding.fullscreenContent.setScrollCon
        //textImagePreview.setHorizontallyScrolling(true)

        /**
         * Setting up the text size
         */
        val maxCharsInOneLine = 60
        //measureLines(250)
        //setTextSize(maxCharsInOneLine)
        //measureFont()
        // 10.5dp or 30px for 60 chars per line on my OnePlus
        // -1.5dp line spacing for 80 lines

        viewBinding.fullscreenContent.setOnLongClickListener(){


            // Capture image:
            val capture = getBitmapFromView(textImagePreview)
            // Save image:
            saveBitmapAsPNG(capture)
            return@setOnLongClickListener true
        }
    }

    private fun hideStatusBar() {
        val windowInsetsController =
            ViewCompat.getWindowInsetsController(window.decorView) ?: return
        // Configure the behavior of the hidden status bars
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Hide both the status bar and the navigation bar
        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
    }

    /**
     * Convert the textview into a bitmap.
     * Taken from https://stackoverflow.com/questions/5536066/convert-view-to-bitmap-on-android
     */
    private fun getBitmapFromView(view: View): Bitmap{
        // Define a bitmap with the same size as the view
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        // Bind a canvas to it
        val canvas = Canvas(returnedBitmap)
        // Get the view's background
        val bgDrawable = view.background
        if (bgDrawable != null) { // has background drawable, then draw it on the canvas
            bgDrawable.draw(canvas)
        } else {   // does not have background drawable, then draw white background on the canvas
            canvas.drawColor(Color.WHITE)
        }
        // Draw the view on the canvas
        view.draw(canvas)
        // Return the bitmap
        return returnedBitmap
    }

    // https://www.simplifiedcoding.net/android-save-bitmap-to-gallery/
    // and https://developer.android.com/codelabs/camerax-getting-started#4
    private fun saveBitmapAsPNG(bmp: Bitmap){
        // Generate a filename:
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.ENGLISH)
            .format(System.currentTimeMillis())

        // Output stream
        var out: OutputStream? = null


        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Text Camera")
            }
        }

        //Inserting the contentValues to contentResolver and getting the Uri
        val imageUri: Uri? =
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        //Opening an output stream with the Uri that we got
        out = imageUri?.let { contentResolver.openOutputStream(it) }

        out?.use {
            //Finally writing the bitmap to the output stream that we opened
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            val msg = "Photo capture succeeded: $imageUri"
            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setTextSize(maxCharsInOneLine: Int){
        /**
        val text = "@"
        val textPaint = textImagePreview.paint
        val textBounds = Rect()
        textPaint.getTextBounds(text,0,text.length,textBounds)
        val currentTextSize = textImagePreview.textSize
        val textWidth = kotlin.math.abs(textBounds.width()) //textWidth: 18 (px?)
        */
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics);
        val displayWidth = displayMetrics.widthPixels
        val textSize: Float = (displayWidth.toFloat()/maxCharsInOneLine.toFloat()) * (5.0f/3.0f)
        // Measured width of 18 px corresponds to 30 px set character width. Therefore a ratio of
        // 5/3 is multiplied.

        textImagePreview.setTextSize(COMPLEX_UNIT_PX, textSize)
    }

    private fun measureFont(){
        textImagePreview.setTextSize(COMPLEX_UNIT_PX, textImagePreview.textSize)
        textImagePreview.text = "#"
        val maxlines = textImagePreview.maxLines
        val height2 = textImagePreview.paint.fontMetrics.bottom -
               textImagePreview.paint.fontMetrics.top
        Log.v(TAG, "Yeah")
    }

    private fun measureLines(lineMax: Int){
        Log.v(TAG, "Text size = ${textImagePreview.textSize}")
        var lineString = "1 \n"
        for (i in 2..lineMax){
            val sb = StringBuilder()
            sb.append(lineString).append("$i \n")
            lineString = sb.toString()
        }
        textImagePreview.text = lineString
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            /**
            // Preview
            val preview = Preview.Builder()
            .build()
            .also {
            it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }
             */

            // Calculate the image resolution to request. For a portrait resolution of 480x640, the
            // requested resolution will be 640x640, so with cropping it can be both portrait and
            // landscape resolutions.
            val maxVal = if (targetResolutionPortrait.width > targetResolutionPortrait.height){
                targetResolutionPortrait.width
            } else{
                targetResolutionPortrait.height
            }
            val requestedResolution = Size(maxVal, maxVal)

            // Implement one of two below:
            // Builder().setTargetResolution(Size resolution) >> use similar aspect ratio as screen
            // Builder().setTargetAspectRatio(int aspectRatio) >> use similar aspect ratio as screen
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(requestedResolution)
                //.setTargetRotation(ROTATION_90)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor,ImageToText(targetResolutionPortrait,
                        textImagePreview,this))
                    //it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                    //    Log.d(TAG, "Average luminosity: $luma")
                    //})
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                //Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
        /**
         * Whether or not the system UI should be auto-hidden after
         * [AUTO_HIDE_DELAY_MILLIS] milliseconds.
         */
        private const val AUTO_HIDE = true

        /**
         * If [AUTO_HIDE] is set, the number of milliseconds to wait after
         * user interaction before hiding the system UI.
         */
        private const val AUTO_HIDE_DELAY_MILLIS = 3000

        /**
         * Some older devices needs a small delay between UI widget updates
         * and a change of the status and navigation bar.
         */
        private const val UI_ANIMATION_DELAY = 300
    }
}


