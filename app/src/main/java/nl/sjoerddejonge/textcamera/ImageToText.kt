package nl.sjoerddejonge.textcamera

import android.util.Size
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.view.ViewCompat.getRotation
import java.nio.ByteBuffer
import java.nio.file.Files.size

class ImageToText(
    private var targetResolutionPortrait: Size,
    private val fullscreenContent: TextView,
    private val owner: AppCompatActivity
) : ImageAnalysis.Analyzer {

    private val charSets = charArrayOf('@','#','%','B','0','P','2', 'L', '7','?','/','!',';',':','-',',','.',' ')
    // '@','#','%','B','0','P','2','7','/','?','!',';',':','o','*','•','-',',','.','`',' '
    private val portraitTargetResolution = Size(480,640)
    override fun analyze(imageProxy: ImageProxy) {
        // The ImageProxy image is in YUV format.

        // Grab the ByteBuffer for the pixels' Y-values of the ImageProxy
        val yBuffer = imageProxy.planes[0].buffer

        // Convert the ByteBuffer to an IntArray
        val gsData = yBuffer.toIntArray(imageProxy.width, imageProxy.height,
            imageProxy.planes[0].rowStride)

        // Create a GrayscaleImage based on the IntArray with Y values
        val gsImage = GrayscaleImage(imageProxy.width, imageProxy.height, gsData)

        // Get current screen orientation:
        val rotation: Int = owner.windowManager.defaultDisplay.rotation
        val isPortrait = (rotation==0)

        // If the image needs rotation, rotate it
        when (imageProxy.imageInfo.rotationDegrees) {
            90 -> gsImage.rotateImageClockwise(1)
            180 -> gsImage.rotateImageClockwise(2)
            270 -> gsImage.rotateImageClockwise(3)
        }

        // The amount (number) of pixel tiles, depending on the screen size
        val tileAmountWidth: Int // 60 // was 120 was 48
        val tileAmountHeight: Int // 120 // was 60 was 64
        val targetSize: Size
        if (isPortrait){
            tileAmountWidth = 96 // 60 // was 120 was 48
            tileAmountHeight = 128 // 120 // was 60 was 64
            targetSize = portraitTargetResolution
        } else {
            tileAmountWidth = 128
            tileAmountHeight = 96
            // Rotate the
            targetSize = Size(portraitTargetResolution.height, portraitTargetResolution.width)
        }


        // Crop the image if the actual resolution does not match the targetResolution:
        if ((targetSize.width != gsImage.getWidth())
            || (targetSize.height != gsImage.getHeight()) ) {
            // Crop!
            gsImage.centerCrop(targetSize)
        }

        // Store the width and height in separate values (creates shorter code lines)
        val width = gsImage.getWidth()
        val height = gsImage.getHeight()

        // The width and height of each pixel tile in pixels
        val tileWidth = width/tileAmountWidth
        val tileHeight = height/tileAmountHeight

        var count = 0;
        // Count 0 values:
        for (i in gsImage.getPixelValues().indices){
            if (gsImage.getPixelValues()[i] == 0) count++
        }
        var percentage: Double = count.toDouble()/gsImage.getPixelValues().size.toDouble()
        var average: Double = gsImage.getPixelValues().average()


        // Divide the chars in charSets among the range of grayscale values in the image
        val valueDistribution = DoubleArray(charSets.size)
        val maxVal = gsImage.getPixelValues().maxOrNull()
        val totalRange = maxVal?.minus(gsImage.getPixelValues().minOrNull()!!)
        val binSize: Double? = totalRange?.div(charSets.size.toDouble())
        for (i in charSets.indices){
            if (binSize != null) {
                valueDistribution[i] = binSize*(i+1)
            }
        }

        /*
        // Create a binary mask of the image using a threshold from Otsu's method
        val mask: IntArray = otsu(gsData)

        // Multiply the mask with the grayscale image so everything outside the mask becomes 0
        for (i in gsData.indices){
            gsData[i] = gsData[i] * mask[i]
        }

        // Invert the mask: each 0 becomes 255 (white) and each 1 becomes 0 (black):
        for (i in mask.indices) {
            if (mask[i] == 0) {
                if (maxVal != null) mask[i] = maxVal
                else mask[i] = 255
            } else {
                mask[i] = 0
            }
        }
        // Add the inverted mask to the grayscale image:
        for (i in gsData.indices){
            gsData[i] = gsData[i] + mask[i]
        }

        */

        // Create the (still empty) CharArray that will get sent to screen
        val imageText = CharArray(tileAmountHeight*tileAmountWidth)

        // Loop through image blocks to calculate average grayscale level of each block
        for (h in 0 until tileAmountHeight){
            for (w in 0 until tileAmountWidth){
                var averageGrayscale = 0
                // Inside each block, loop through each pixel to add their values
                for (y in h*tileHeight until h*tileHeight+tileHeight){
                    for (x in w*tileWidth until w*tileWidth+tileWidth){
                        //Log.v(TAG, "h = $h, w = $w, y = $y and x = $x and width = $width")
                        averageGrayscale += gsImage.getPixelValues()[y*width + x]
                    }
                }
                // Divide by number of pixels per block to calculate the average
                averageGrayscale /= (tileHeight*tileWidth)
                // Determine what character corresponds to this average grayscale value
                // Set the imageText character for this block to that
                var i = 0
                do{
                    imageText[h*tileAmountWidth + w] = charSets[i]
                    i++
                    if (i >= valueDistribution.size) break
                } while (averageGrayscale > valueDistribution[i])
                // TODO: Maybe use a different type of loop here. For example:
                //  for (i in charSets.indices){
                //      if (averageGrayscale < valueDistribution[i]){
                //          imageText[h*blockAmountWidth + w] = charSets[i]
                //          break
                //      }
                //  }
            }
        }

        owner.runOnUiThread(Runnable {
            setScreenText(imageText, tileAmountWidth) // Stuff that updates the UI
        })

        // Close the ImageProxy
        imageProxy.close()
    }

    /**
     * Translate ByteBuffer to IntArray.
     *
     * Meant for images that have padding at the end of each pixel row.
     * See https://www.collabora.com/assets/images/blog/layout-1024.png (stride == rowStride).
     */
    private fun ByteBuffer.toIntArray(width: Int, height: Int, rowStride: Int): IntArray {
        rewind()    // Rewind the buffer to zero
        //Log.v(TAG, "yBuffer size: ${limit()}")
        val data = IntArray(width*height)

        for (y in 0 until height){
            for (x in 0 until width){
                //data[(y*x)] = getInt((y*rowStride) + x)
                data[(y*width+x)] = (get((y*rowStride) + x)).toInt() and 0xFF
                // (y*rowStride)+x allows for skipping the padding bytes at the end of the pixel row
            }
        }
        return data
    }

    private fun ByteBuffer.toByteArray(): ByteArray{
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }

    private fun ByteArray.removePadding(width: Int, height: Int, pixelStride: Int, rowStride: Int): ByteArray{
        val newArray = ByteArray(width * height)
        for (y in 0 until height){
            for (x in 0 until  width){
                newArray[(y*x)] = get((y*rowStride) + (x*pixelStride))
            }
        }
        return newArray
    }

    private fun otsu(inputImage: IntArray): IntArray {
        // Make a histogram of the image:
        val histogram = makeHistogram(inputImage)
        val totalPixels = inputImage.size

        var sum = 0
        for (i in 0 until 256) sum += i * histogram[i]

        var sumB: Double = 0.0
        var wB: Int = 0
        var wF: Int

        var varMax: Double = 0.0
        var threshold: Int = 0

        // Otsu's method:
        for (t in histogram.indices) {
            wB += histogram[t]
            if (wB == 0) continue
            wF = totalPixels - wB

            if (wF == 0) break

            sumB += (t * histogram[t])
            val mB = sumB / wB
            val mF = (sum - sumB) / wF

            val varBetween = wB * wF * (mB - mF) * (mB - mF)

            if (varBetween > varMax) {
                varMax = varBetween
                threshold = t
            }
        }
        // Create and return a mask using the threshold from Otsu's method
        return imageThresholding(threshold, inputImage)
    }

    private fun makeHistogram(grayscaleImage: IntArray): IntArray {
        val histogram = IntArray(256)

        for (i in grayscaleImage.indices){
            // Increase the histogram index corresponding to the grayscale value with 1
            histogram[grayscaleImage[i]]++
        }
        return histogram
    }

    private fun imageThresholding(threshold: Int, grayscaleImage: IntArray): IntArray {
        val mask: IntArray = grayscaleImage.copyOf()
        for (i in mask.indices) {
            if (mask[i] <= threshold) {
                mask[i] = 1
            } else {
                mask[i] = 0
            }
        }

        return mask
    }

    private fun setScreenText(screenTextArray: CharArray, maxCharsInOneLine: Int){
        val str = String(screenTextArray)
        val newStr = fullscreenContent.toNonBreakingString(str, maxCharsInOneLine)
        fullscreenContent.text = newStr;
    }

    // https://stackoverflow.com/questions/67919361/how-to-disable-automatic-line-breaking-in-textview
    private fun TextView.toNonBreakingString(text: String?, maxCharsInOneLine: Int): String {
        if (text == null) return ""
        val container = parent as? ViewGroup ?: return text

        val lineWidth = (container.width - container.paddingStart - container.paddingEnd).toFloat()
        //val maxCharsInOneLine = paint.breakText(text, 0, text.length, true, lineWidth, null)
        if (maxCharsInOneLine == 0) return text

        val sb = StringBuilder()
        var currentLine = 1
        var end = 0
        for (i in 0..text.count() step maxCharsInOneLine) {
            end = currentLine * maxCharsInOneLine
            if (end > text.length) end = text.length
            sb.append(text.subSequence(i, end))
            sb.append("\n")
            currentLine = currentLine.inc()
        }

        if (end < text.length) {
            val remainingChars = text.length - end
            sb.append(text.takeLast(remainingChars))
        }

        return sb.toString()
    }
}