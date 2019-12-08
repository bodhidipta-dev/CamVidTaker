package com.internal.bodhidipta.camvid.required

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.Image
import android.os.AsyncTask
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ImageSaverWorker(
    private val file: File,
    val image: Image,
    private val isfront: Boolean,
    private val orientation: Int,
    private val aspectratio: CommonClass.ImageAspectRatio
) : AsyncTask<Void, Void, Void?>() {
    private val TAG = "ImageSaver"

    override fun doInBackground(vararg params: Void?): Void? {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val sourceBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val matrix = Matrix()
        if (!isfront)
            matrix.postRotate(90f)
        else matrix.postRotate(270f)

        if (orientation != 0) {
            if (!isfront)
                matrix.postRotate(orientation * 1f)
            else
                matrix.postRotate(((orientation + 270f) % 360) - 90)
        }

        val roatedSource = Bitmap.createBitmap(
            sourceBitmap,
            0,
            0,
            sourceBitmap.width,
            sourceBitmap.height,
            matrix,
            true
        )
        var targetBitmap = roatedSource

        if (aspectratio == CommonClass.ImageAspectRatio.ONE_ONE) {
            var chosen_one =
                if (roatedSource.width < roatedSource.height) roatedSource.width else roatedSource.height

            targetBitmap = Bitmap.createBitmap(roatedSource, 0, 0, chosen_one, chosen_one)
        }

        var output: FileOutputStream? = null
        try {
            output = FileOutputStream(file)
            output.use { out ->
                val rotatedImg =
                    Bitmap.createBitmap(targetBitmap, 0, 0, targetBitmap.width, targetBitmap.height)

                rotatedImg.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    out
                ) // bmp is your Bitmap instance
                // PNG is a lossless format, the compression factor (100) is ignored
            }


        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            image.close()
            sourceBitmap.recycle()
            roatedSource.recycle()
            targetBitmap.recycle()

            output?.let {
                try {
                    it.close()
                } catch (e: IOException) {
                    Log.e(TAG, e.toString())
                }
            }
        }

        return null
    }
}