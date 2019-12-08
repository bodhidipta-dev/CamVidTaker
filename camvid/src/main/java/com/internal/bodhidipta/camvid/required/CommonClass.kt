package com.internal.bodhidipta.camvid.required

import android.hardware.camera2.*
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.util.SparseIntArray
import android.view.OrientationEventListener
import android.view.Surface
import com.internal.bodhidipta.camvid.view.AutoFitTextureView
import java.io.File
import java.util.*
import java.util.concurrent.Semaphore

open class CommonClass {
    enum class OperationalMode {
        PICTURE,
        VIDEO
    }

    enum class FlashMode {
        FORCE_ON,
        OFF
    }

    enum class ImageAspectRatio {
        ONE_ONE,
        FULL
    }

    protected var imageCaptureOnProgress = false
    protected var shouldRecord = false
    protected var recordingOngoing = false
    protected var cameraOperationMode: OperationalMode = OperationalMode.PICTURE
    protected var useFlashMode = FlashMode.OFF
    protected var cameraPrefernce = CameraCharacteristics.LENS_FACING_BACK
    protected lateinit var cameraManager: CameraManager
    protected var mediaRecorder: MediaRecorder? = null
    protected lateinit var file: File
    protected var currentSensorOrientation = 0
    protected lateinit var orientationEventListener: OrientationEventListener
    protected var isRecordingVideo = false
    protected lateinit var videoSize: Size
    protected var imageReader: ImageReader? = null
    protected lateinit var previewRequestBuilder: CaptureRequest.Builder
    protected var captureSession: CameraCaptureSession? = null
    protected var cameraDevice: CameraDevice? = null
    protected lateinit var cameraId: String
    protected var sensorOrientation = 0
    protected lateinit var previewSize: Size
    protected lateinit var largest: Size
    protected lateinit var textureView: AutoFitTextureView
    protected var flashSupported = false
    protected var backgroundThread: HandlerThread? = null
    protected var backgroundHandler: Handler? = null
    protected val cameraOpenCloseLock = Semaphore(1)
    protected var state = STATE_PREVIEW
    protected var nextVideoAbsolutePath: String? = null

    companion object {
        const val TAG = "CameraVideoTaker"
        private val ORIENTATIONS = SparseIntArray()

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        const val STATE_PREVIEW = 0
        const val STATE_WAITING_LOCK = 1
        const val STATE_WAITING_PRECAPTURE = 2
        internal const val STATE_WAITING_NON_PRECAPTURE = 3
        internal const val STATE_PICTURE_TAKEN = 4
        internal const val MAX_PREVIEW_WIDTH = 1920
        internal const val MAX_PREVIEW_HEIGHT = 1080
        internal const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        internal const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
        internal val DEFAULT_ORIENTATIONS = SparseIntArray().apply {
            append(Surface.ROTATION_0, 90)
            append(Surface.ROTATION_90, 0)
            append(Surface.ROTATION_180, 270)
            append(Surface.ROTATION_270, 180)
        }
        internal val INVERSE_ORIENTATIONS = SparseIntArray().apply {
            append(Surface.ROTATION_0, 270)
            append(Surface.ROTATION_90, 180)
            append(Surface.ROTATION_180, 90)
            append(Surface.ROTATION_270, 0)
        }

        internal fun chooseVideoSize(choices: Array<Size>): Size {
            var size = choices.firstOrNull {
                it.width == it.height * 3 / 4 && it.width <= 1080
            }

            if (size == null || size.width < 400) {
                size = Collections.max(choices.asList(), CompareSizesByArea())
            }

            if (size?.width!! > 1080) {
                size = Size(640, 480)
            }

            return size

        }

        @JvmStatic
        internal fun chooseOptimalSize(
            choices: Array<Size>,
            textureViewWidth: Int,
            textureViewHeight: Int,
            maxWidth: Int,
            maxHeight: Int,
            optionSize: Size
        ): Size {

            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough = ArrayList<Size>()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough = ArrayList<Size>()
            val w = optionSize.width
            val h = optionSize.height

            for (option in choices) {

                if (
                    option.width <= maxWidth
                    && option.height <= maxHeight
                    && option.height == (option.width * h / w)
                ) {

                    if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            return when {
                bigEnough.size > 0 -> Collections.min(bigEnough, CompareSizesByArea())
                notBigEnough.size > 0 -> Collections.max(notBigEnough, CompareSizesByArea())
                else -> {
                    // check for 16 : 9 preview size
                    choices[0]

                }
            }
        }
    }
}