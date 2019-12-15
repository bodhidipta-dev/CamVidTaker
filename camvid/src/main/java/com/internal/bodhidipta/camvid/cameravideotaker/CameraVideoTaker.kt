package com.internal.bodhidipta.camvid.cameravideotaker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.AsyncTask
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.MultiProcessor
import com.google.android.gms.vision.face.FaceDetector
import com.internal.bodhidipta.camvid.compress.CompressAsynchronous
import com.internal.bodhidipta.camvid.required.*
import com.internal.bodhidipta.camvid.view.AutoFitTextureView
import com.internal.bodhidipta.camvid.view.CameraSourcePreview
import com.internal.bodhidipta.camvid.view.GraphicOverlay
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

internal class CameraVideoTaker constructor(
    private val context: Activity,
    private val aspectRatio: ImageAspectRatio,
    private val predefinedImagePath: String?,
    private val predefinedVideoPath: String?,
    private val textureView: AutoFitTextureView?,
    private val cameraSourcePreview: CameraSourcePreview?,
    private val graphicOverlay: GraphicOverlay?,
    private val shouldCompress: Boolean = false,
    private val measureSensor: SensorEventListener? = null,
    private val shouldDetectFace: Boolean = false,
    private val deatectDrawFace: Boolean = false,
    private val captureCompleteCallback: (path: String) -> Unit = {},
    private val detectCallback: FaceDetectionCallback
) : CommonClass(),
    CameraViewListener {
    private lateinit var mSurface: Surface
    private lateinit var sensormanager: SensorManager
    private lateinit var mAccelerometer: Sensor

    init {
        measureSensor?.let {
            sensormanager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            mAccelerometer = sensormanager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            sensormanager.registerListener(it, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }

        file = if (predefinedImagePath == null)
            File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "CamVidTaker")
        else
            File(predefinedImagePath)

        orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                currentSensorOrientation = when {
                    orientation <= 80 -> 0
                    orientation in 80..169 -> 90
                    orientation in 170..260 -> 180
                    orientation in 260..350 -> 270
                    else -> 0
                }
            }
        }
        orientationEventListener.enable()

        if (shouldDetectFace)
            createDetectionCamera()
    }


    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(
            texture: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            /*
            * When the texture available open the camera
            * */
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(
            texture: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            /*
            * Surface texture change and resize
            * */
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    }
    private var mCameraSource: CameraSource? = null

    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int) {
        if (context.isFinishing) return

        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(
                context,
                "Camera permission not granted. Please make sure Camera permission is taken.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val storagepermission =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        if (storagepermission != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(
                context,
                "Storage permission not granted. Please make sure Storage permission is taken.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }


        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)

            if (cameraDirection != null &&
                cameraDirection == cameraPrefernce
            ) {

                val map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                ) ?: continue


                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                val displayRotation = context.windowManager.defaultDisplay.rotation

                sensorOrientation =
                    characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                        ?: continue
                val swappedDimensions = areDimensionsSwapped(displayRotation)
                val displaySize = Point()
                context.windowManager.defaultDisplay.getSize(displaySize)

                val rotatedPreviewWidth = if (swappedDimensions) height else width
                val rotatedPreviewHeight = if (swappedDimensions) width else height

                var maxPreviewWidth = if (swappedDimensions) displaySize.y else displaySize.x
                var maxPreviewHeight = if (swappedDimensions) displaySize.x else displaySize.y

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.

                largest = Collections.max(
                    listOf(*map.getOutputSizes(ImageFormat.JPEG)),
                    CompareSizesByArea()
                )

                previewSize = chooseOptimalSize(
                    map.getOutputSizes(SurfaceTexture::class.java),
                    rotatedPreviewWidth,
                    rotatedPreviewHeight,
                    maxPreviewWidth,
                    maxPreviewHeight,
                    largest
                )

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView?.setAspectRatio(previewSize.width, previewSize.height)
                } else {
                    textureView?.setAspectRatio(previewSize.height, previewSize.width)
                }

                // Check if the flash is supported.
                flashSupported =
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

                this.cameraId = cameraId
            }
        }

        configureTransform(width, height)

        mediaRecorder = MediaRecorder()

        try {
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!cameraOpenCloseLock.tryAcquire(5000, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)


        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
            Toast.makeText(context, "Can not open more than one camera.", Toast.LENGTH_SHORT)
                .show()
        } catch (e: InterruptedException) {
            e.printStackTrace()
            Toast.makeText(context, "Camera could not be accessed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createDetectionCamera() {
        val detector: FaceDetector = FaceDetector.Builder(context)
            .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
            .build()

        detector.setProcessor(
            MultiProcessor.Builder(
                GraphicFaceTrackerFactory(
                    graphicOverlay!!,
                    deatectDrawFace,
                    detectCallback
                )
            )
                .build()
        )

        if (!detector.isOperational) { // Note: The first time that an app using face API is installed on a device, GMS will
// download a native library to the device in order to do detection.  Usually this
// completes before the app is run for the first time.  But if that download has not yet
// completed, then the above call will not detect any faces.
//
// isOperational() can be used to check if the required native library is currently
// available.  The detector will automatically become operational once the library
// download completes on device.

        }

        mCameraSource = CameraSource.Builder(context, detector)
            .setRequestedPreviewSize(640, 480)
            .setFacing(CameraSource.CAMERA_FACING_FRONT)
            .setRequestedFps(10.0f)
            .build()
    }

    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@CameraVideoTaker.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@CameraVideoTaker.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            this@CameraVideoTaker.context.finish()
        }

    }

    private fun createCameraPreviewSession() {
        try {
            cameraDevice?.let {
                if (textureView?.isAvailable == false) return

                /* Close any previous session*/
                captureSession?.close()
                captureSession = null

                val texture = textureView?.surfaceTexture

                // We configure the size of default buffer to be the size of camera preview we want.
                texture?.setDefaultBufferSize(previewSize.width, previewSize.height)

                // This is the output Surface we need to start preview.
                mSurface = Surface(texture)
                previewRequestBuilder = it.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                // We set up a CaptureRequest.Builder with the output Surface.
                previewRequestBuilder.addTarget(mSurface)

                /*
                * *****************************************************
                * This session will be created when the user swipe to optional mode
                * we will try to start a session for the video
                * */
                try {
                    cameraOpenCloseLock.acquire()
                    context.let {

                        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

                        val map = characteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                        )

                        /* Choose optimal viceo size */
                        videoSize = map?.getOutputSizes(MediaRecorder::class.java)?.let {
                            chooseVideoSize(it)
                        } ?: Size(640, 480)

                        cameraDevice?.createCaptureSession(
                            listOf(mSurface),
                            object : CameraCaptureSession.StateCallback() {

                                override fun onConfigured(session: CameraCaptureSession) {
                                    // The camera is already closed
                                    if (cameraDevice == null) return

                                    captureSession = session

                                    // Auto focus should be continuous for camera preview.
                                    previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                    )
                                    previewRequestBuilder.set(
                                        CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                        CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL
                                    )

                                    // Finally, we start displaying the camera preview.
                                    captureSession?.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        null, backgroundHandler
                                    )
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    showToast("Camera configuration has been failed.")
                                }
                            }, backgroundHandler
                        )

                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    cameraOpenCloseLock.release()
                }
            }

        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun lockFocus() {
        cameraDevice?.let { cameraDevice ->

            if (imageCaptureOnProgress) return

            try {
                imageCaptureOnProgress = true
                captureSession?.close()
                imageReader?.close()

                previewRequestBuilder = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW
                )

                // We set up a CaptureRequest.Builder with the output Surface.
                previewRequestBuilder.addTarget(mSurface)

                orientationEventListener.disable()

                imageReader = ImageReader.newInstance(
                    previewSize.width, previewSize.height,
                    ImageFormat.JPEG, /*maxImages*/ 2
                ).apply {
                    setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
                }


                cameraDevice.createCaptureSession(
                    listOf(
                        mSurface,
                        imageReader?.surface
                    ),
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            // This is how to tell the camera to lock focus.
                            previewRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_TRIGGER,
                                CameraMetadata.CONTROL_AF_TRIGGER_START
                            )

                            // Tell #captureCallback to wait for the lock.
                            state = STATE_WAITING_LOCK
                            session.capture(
                                previewRequestBuilder.build(), captureCallback,
                                backgroundHandler
                            )
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            showToast("Failed to configure camera.")
                        }
                    }, backgroundHandler
                )


            } catch (e: CameraAccessException) {
                Log.e(TAG, e.toString())
            }


        }

    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        private fun process(result: CaptureResult) {
            when (cameraOperationMode) {
                OperationalMode.PICTURE -> {
                    when (state) {
                        STATE_PREVIEW -> Unit // Do nothing when the camera preview is working normally.
                        STATE_WAITING_LOCK -> {
                            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                            if (afState == null) {
                                state = STATE_PICTURE_TAKEN
                                captureStillPicture()
                            } else {
                                when (afState) {
                                    CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                                    CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> {
                                        state = STATE_PICTURE_TAKEN
                                        captureStillPicture()
                                    }
                                    CaptureResult.CONTROL_AF_STATE_INACTIVE,
                                    CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> {
                                        state = STATE_PICTURE_TAKEN
                                        captureStillPicture()
                                    }
                                }
                            }
                        }
                        STATE_WAITING_PRECAPTURE -> {
                            // CONTROL_AE_STATE can be null on some devices
                            val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                            if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_INACTIVE ||
                                aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                                aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED
                            ) {
                                state = STATE_WAITING_NON_PRECAPTURE
                                showToast("Capturing low light picture. You can turn on the flash.")
                                state = STATE_PICTURE_TAKEN
                                captureStillPicture()
                            }
                        }
                        STATE_WAITING_NON_PRECAPTURE -> {
                            // CONTROL_AE_STATE can be null on some devices
                            val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                            if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                                state = STATE_PICTURE_TAKEN
                                captureStillPicture()
                            }
                        }

                        STATE_PICTURE_TAKEN -> {
                            showToast("Saved: $file")
                            previewRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_TRIGGER,
                                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
                            )
                            setFlashOff(previewRequestBuilder)

                            captureSession?.capture(
                                previewRequestBuilder.build(), null,
                                backgroundHandler
                            )
                            // After this, the camera will go back to the normal state of preview.
                            state = STATE_PREVIEW
                            captureSession?.setRepeatingRequest(
                                previewRequestBuilder.build(), null,
                                backgroundHandler
                            )

                            imageCaptureOnProgress = false
                        }
                    }
                }
                else -> {
                }
            }
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            process(partialResult)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            process(result)
        }

    }

    private fun captureStillPicture() {
        if (context.isFinishing || context.isDestroyed) {
            return
        }

        if (cameraDevice == null) return

        try {
            // This is the CaptureRequest.Builder that we use to take a picture.
            val captureBuilder = cameraDevice?.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE
            )?.apply {
                imageReader?.surface?.let {
                    addTarget(it)
                }
                // Use the same AE and AF modes as the preview.
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
            }?.also { setAutoFlash(it) }
            captureSession?.apply {
                stopRepeating()
                abortCaptures()
                captureBuilder?.build()?.let {
                    capture(it, captureCallback, backgroundHandler)
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    private fun runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            // Tell #captureCallback to wait for the precapture sequence to be set.
            state = STATE_WAITING_PRECAPTURE
            captureSession?.capture(
                previewRequestBuilder.build(), captureCallback,
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener {

        file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "${System.currentTimeMillis()}-$PIC_FILE_NAME"
        )

        ImageSaverWorker(
            file,
            it.acquireNextImage(),
            cameraPrefernce == CameraCharacteristics.LENS_FACING_FRONT,
            currentSensorOrientation,
            aspectRatio,
            captureCompleteCallback
        ).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        orientationEventListener.enable()
    }

    private fun stopRecordingVideo(
        onSuccess: (destinationPath: String?) -> Unit,
        onError: () -> Unit
    ) {
        isRecordingVideo = false
        setFlashOff(previewRequestBuilder)
        mediaRecorder?.apply {
            stop()
            reset()
        }
        showToast("Video saved: $nextVideoAbsolutePath")
        try {
            if (shouldCompress)
                CompressAsynchronous(onSuccess, onError)
                    .executeOnExecutor(
                        AsyncTask.THREAD_POOL_EXECUTOR,
                        nextVideoAbsolutePath,
                        getVideoFilePath()
                    )
            else onSuccess(nextVideoAbsolutePath)
        } catch (t: Throwable) {/* smth wrong :( */
            onError()
            t.printStackTrace()
        }
        nextVideoAbsolutePath = null
        createCameraPreviewSession()
    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder) {
        when (useFlashMode) {
            FlashMode.FORCE_ON ->
                requestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
            else ->
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }
    }

    private fun setFlashOff(requestBuilder: CaptureRequest.Builder) {
        requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
    }

    private fun hasPermissionsGranted(permissions: Array<String>) =
        permissions.none {
            ContextCompat.checkSelfPermission(
                (context),
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground")
            backgroundThread?.start()
            backgroundHandler = Handler(backgroundThread?.looper)
        }

    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun getVideoFilePath(): String {

        return if (predefinedVideoPath == null) File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "${System.currentTimeMillis()}.mp4"
        ).absolutePath else File(
            predefinedVideoPath,
            "${System.currentTimeMillis()}.mp4"
        ).absolutePath
    }

    private fun startRecordingVideo() {
        if (cameraDevice == null || textureView?.isAvailable == false) return

        try {
            captureSession?.close()
            captureSession = null

            mediaRecorder?.reset()

            val map = cameraManager.getCameraCharacteristics(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )

            /* Choose optimal viceo size */
            videoSize = map?.getOutputSizes(MediaRecorder::class.java)?.let {
                chooseVideoSize(it)
            } ?: Size(640, 480)
            if (nextVideoAbsolutePath.isNullOrEmpty()) {
                nextVideoAbsolutePath = getVideoFilePath()
            }

            val rotation = context.windowManager.defaultDisplay.rotation
            when (sensorOrientation) {
                SENSOR_ORIENTATION_DEFAULT_DEGREES ->
                    mediaRecorder?.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
                SENSOR_ORIENTATION_INVERSE_DEGREES ->
                    mediaRecorder?.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(nextVideoAbsolutePath)
                setVideoEncodingBitRate(10000000)
                setVideoFrameRate(30)
                setVideoSize(videoSize.width, videoSize.height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                prepare()
            }

            // Set up Surface for camera preview and MediaRecorder
            val surfaces = ArrayList<Surface>()
            val recorderSurface = mediaRecorder?.surface?.also {
                surfaces.add(it)
                surfaces.add(mSurface)
            }
            cameraDevice?.let {
                previewRequestBuilder =
                    it.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        recorderSurface?.let {
                            addTarget(it)
                        }
                        addTarget(mSurface)
                    }
            }
            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            cameraDevice?.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        captureSession = cameraCaptureSession

                        try {
                            setAutoFlash(previewRequestBuilder)
                            previewRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )

                            HandlerThread("CameraPreview").start()
                            captureSession?.setRepeatingRequest(
                                previewRequestBuilder.build(),
                                null, backgroundHandler
                            )
                            isRecordingVideo = true
                            mediaRecorder?.start()
                            showToast("Recording Video")
                        } catch (e: java.lang.Exception) {
                            Log.e(TAG, e.toString())
                        }
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        showToast("Recording video Failed !!!!")
                    }
                }, backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
        }

    }

    private fun areDimensionsSwapped(displayRotation: Int): Boolean {
        var swappedDimensions = false
        Log.e(TAG, "Display rotation : $displayRotation")
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                Log.e(
                    TAG,
                    "Display rotation : ${Surface.ROTATION_0} or ${Surface.ROTATION_180}"
                )
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true
                }
            }
            else -> {
                Log.e(TAG, "Display rotation is invalid: $displayRotation")
            }
        }
        return swappedDimensions
    }

    fun configureTransform(viewWidth: Int, viewHeight: Int) {

        val rotation = context.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect =
            RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale =
                (viewHeight.toFloat() / previewSize.height).coerceAtLeast(viewWidth.toFloat() / previewSize.width)
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView?.setTransform(matrix)
    }

    private fun showToast(stringMsg: String) {
//        Toast.makeText(context, stringMsg, Toast.LENGTH_SHORT).show()
    }

    override fun onOptionChange(operationMode: OperationalMode) {
        when (operationMode) {
            OperationalMode.VIDEO -> {
                cameraOperationMode = OperationalMode.VIDEO
                imageReader?.close()
            }
            OperationalMode.PICTURE -> {
                cameraOperationMode = OperationalMode.PICTURE
                isRecordingVideo = false
                mediaRecorder?.reset()
            }
        }
    }

    override fun clickCameraCapture() {
        if (shouldDetectFace) {
            mCameraSource?.takePicture({

            }, { img ->
                img?.let {
                    file = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        "${System.currentTimeMillis()}-$PIC_FILE_NAME"
                    )

                    ImageSaverWorkerDetection(
                        file,
                        it,
                        cameraPrefernce == CameraCharacteristics.LENS_FACING_FRONT,
                        currentSensorOrientation,
                        aspectRatio,
                        captureCompleteCallback
                    ).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                }
            })
        } else {
            cameraOperationMode = OperationalMode.PICTURE
            lockFocus()
        }
    }

    override fun clickCameraViewToggle() {
        cameraPrefernce = when (cameraPrefernce) {
            CameraCharacteristics.LENS_FACING_BACK -> {
                CameraCharacteristics.LENS_FACING_FRONT
            }
            else -> {
                CameraCharacteristics.LENS_FACING_BACK
            }
        }

        cameraOperationMode = OperationalMode.PICTURE

        cameraOpenCloseLock.acquire()
        cameraDevice?.close()
        captureSession?.close()
        imageReader?.close()
        mediaRecorder?.release()
        cameraOpenCloseLock.release()

        Log.i(TAG, "After closing all trying to re-open camera with preferred lance facing ")
        textureView?.let {
            openCamera(it.width, it.height)
        }
    }

    override fun startVideoCapture(): Boolean {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            Toast.makeText(
                context,
                "Audio permission not granted. Please make sure audio permission is taken.",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        shouldRecord = true
        recordingOngoing = true
        cameraOperationMode = OperationalMode.VIDEO
        startRecordingVideo()
        return shouldRecord
    }

    override fun stopVideoCapture(
        onComplete: (destinationPath: String?) -> Unit,
        onError: () -> Unit
    ) {
        if (shouldRecord && recordingOngoing) {
            shouldRecord = false
            recordingOngoing = false
            stopRecordingVideo(onComplete, onError)
        }
    }

    override fun clickVideoViewtoggle() {
        cameraDevice?.close()
        captureSession?.close()

        cameraPrefernce = when (cameraPrefernce) {
            CameraCharacteristics.LENS_FACING_BACK -> {
                CameraCharacteristics.LENS_FACING_FRONT
            }
            else -> {
                CameraCharacteristics.LENS_FACING_BACK
            }
        }

        cameraOperationMode = OperationalMode.VIDEO

        cameraOpenCloseLock.acquire()
        cameraDevice?.close()
        captureSession?.close()
        imageReader?.close()
        mediaRecorder?.release()
        cameraOpenCloseLock.release()
        Log.i(TAG, "After closing all trying to re-open camera with preferred lance facing ")
        textureView?.let {
            openCamera(it.width, it.height)
        }
    }

    override fun clickCameraFlashToggle(): Boolean {
        return when (useFlashMode) {
            FlashMode.FORCE_ON -> {
                useFlashMode = FlashMode.OFF
                false
            }
            else -> {
                useFlashMode = FlashMode.FORCE_ON
                true
            }
        }
    }


    //==============================================================================================
// Camera Source Preview
//==============================================================================================
    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private fun startCameraSource() { // check that the device has play services available.

        if (mCameraSource != null) {
            try {
                cameraSourcePreview?.start(mCameraSource, graphicOverlay)
            } catch (e: IOException) {
                mCameraSource!!.release()
                mCameraSource = null
            }
        }

    }

    override fun onResumeView() {
        startBackgroundThread()
        if (shouldDetectFace) {
            startCameraSource()
        } else {
            if (textureView?.isAvailable == true) {
                openCamera(textureView.width, textureView.height)
            } else {
                textureView?.surfaceTextureListener = surfaceTextureListener
            }

            measureSensor?.let {
                sensormanager.registerListener(
                    it,
                    mAccelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        }
    }

    override fun onPauseView() {
        try {
            measureSensor?.let {
                sensormanager.unregisterListener(it)
            }
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.let {
                it.close()
                imageReader = null
            }
            mediaRecorder?.let {
                if (isRecordingVideo) {
                    isRecordingVideo = false
                    it.apply {
                        stop()
                        release()
                    }
                }
            }

        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    override fun onStopView() {
        stopBackgroundThread()
        cameraSourcePreview?.stop()
    }

    override fun onDestroy() {
        if (mCameraSource != null) {
            mCameraSource!!.release()
        }
    }
}
