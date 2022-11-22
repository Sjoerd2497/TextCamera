package nl.sjoerddejonge.textcamera

import android.util.Log
import android.util.Size

private const val TAG = "Image.kt"

/**
 * Image class.
 * Origin (0,0) of the image is in the top-left. Pixels are stored in a 1D array (pixelValues).
 * Example:
 * 5 2 2 3
 * 5 3 3 2
 * 6 9 2 8
 * Above 'image' is stored as:
 * 5 2 2 3 5 3 3 2 6 9 2 8
 */
abstract class Image(private var width: Int,
                     private var height: Int,
                     private var pixelValues: IntArray) {
    abstract val isGrayscale: Boolean

    fun getWidth(): Int{
        return width
    }
    fun getHeight(): Int{
        return height
    }
    fun getPixelValues(): IntArray{
        return pixelValues
    }
    fun setWidth(newWidth: Int){
        width = newWidth
    }
    fun setHeight(newHeight: Int){
        height = newHeight
    }
    fun setPixelValues(newArray: IntArray, suppresWarning: Boolean = false){
        if (pixelValues.size != newArray.size && !suppresWarning){
            Log.w(TAG, "New pixel array size is different from previous array size!")
        }
        pixelValues = newArray.copyOf()
    }

}

/**
 * Grayscale Image class based on the Image class.
 */
class GrayscaleImage(width: Int,
                     height: Int,
                     pixelValues: IntArray) : Image(width, height, pixelValues) {
    override val isGrayscale: Boolean = true

    /**
     * Get the pixel grayscale value at the (x,y) coordinates.
     */
    fun getPixelValue(x: Int, y: Int): Int{
        return getPixelValues()[y*getWidth() + x]
    }

    /**
     * Rotate the image (a multiple of) 90 degrees clockwise.
     */
    fun rotateImageClockwise(rotations: Int = 1) {
        val trueRotations: Int
        if (rotations % 4 == 0){
            return                                                  // 4 rotations == 0 rotations
        }else{
            trueRotations = rotations % 4
        }

        when (trueRotations) {
            1 -> {
                // Rotate image 90 degrees:
                val newPixelValues = IntArray(getPixelValues().size)
                var i = 0
                for (x in 0 until getWidth()){
                    for (y in (getHeight()-1) downTo 0){
                        newPixelValues[i] = getPixelValue(x,y)
                        i++
                    }
                }
                setPixelValues(newPixelValues)
                val newWidth = getHeight()      // Width becomes height and vice-versa
                val newHeight = getWidth()
                setWidth(newWidth)
                setHeight(newHeight)
            }
            2 -> {
                // Rotate image 180 degrees:
                setPixelValues(getPixelValues().reversedArray())
            }
            else -> { // 3 ->
                // Rotate image 270 degrees:
                val newPixelValues = IntArray(getPixelValues().size)
                var i = 0
                for (x in (getWidth()-1) downTo 0){
                    for (y in 0 until getHeight()){
                        newPixelValues[i] = getPixelValue(x,y)
                        i++
                    }
                }
                setPixelValues(newPixelValues)
                val newWidth = getHeight()      // Width becomes height and vice-versa
                val newHeight = getWidth()
                setWidth(newWidth)
                setHeight(newHeight)
            }
        }
    }

    /**
     * Crop the image towards the center.
     */
    fun centerCrop(targetSize: Size){
        // Verify that the target resolution is smaller than the current size
        if ((getWidth() < targetSize.width) || (getHeight()) < targetSize.height ){
            Log.e(TAG, "Cannot crop image, target size is larger than image.")
            return
        }
        val newPixelValues = IntArray(targetSize.width*targetSize.height)

        val xOffset = getWidth()-targetSize.width
        val yOffset = getHeight()-targetSize.height
        // Loop the newPixelValues
        for (x in 0 until targetSize.width){
            for (y in 0 until targetSize.height) {
                // Write the image pixels that fall within the cropped range to newPixelValues:
                newPixelValues[y*targetSize.width + x] = getPixelValue(x+(xOffset/2), y+(yOffset/2))
            }
        }
        setPixelValues(newPixelValues, true)
        setWidth(targetSize.width)
        setHeight(targetSize.height)
    }
}

/*
===================================================
90 degree rotation:
===================================================
input image:
01 02 03 04     w: 4
05 06 07 08     h: 3
09 10 11 12

pixel values: [01 02 03 04 05 06 07 08 09 10 11 12]

after rotation:
09 05 01        w: 3
10 06 02        h: 4
11 07 03
12 08 04

pixel values: [09 05 01 10 06 02 11 07 03 12 08 04]

===================================================
180 degree rotation:
===================================================
input image:
01 02 03 04     w: 4
05 06 07 08     h: 3
09 10 11 12

pixel values: [01 02 03 04 05 06 07 08 09 10 11 12]

after rotation:
12 11 10 09     w: 4
08 07 06 05     h: 3
04 03 02 01

pixel values: [12 11 10 09 08 07 06 05 04 03 02 01]

===================================================
270 degree rotation:
===================================================
input image:
01 02 03 04     w: 4
05 06 07 08     h: 3
09 10 11 12

pixel values: [01 02 03 04 05 06 07 08 09 10 11 12]

after rotation:
04 08 12        w: 3
03 07 11        h: 4
02 06 10
01 05 09

pixel values: [04 08 12 03 07 11 02 06 10 01 05 09]
 */