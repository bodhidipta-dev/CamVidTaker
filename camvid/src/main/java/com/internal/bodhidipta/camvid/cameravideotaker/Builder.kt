package com.internal.bodhidipta.camvid.cameravideotaker

import android.app.Activity
import android.graphics.RectF
import android.hardware.SensorEventListener
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.vision.face.Face
import com.internal.bodhidipta.camvid.required.CameraOperationMode
import com.internal.bodhidipta.camvid.required.CaptureOperationMode
import com.internal.bodhidipta.camvid.required.CommonClass
import com.internal.bodhidipta.camvid.required.DetectorOperationMode
import com.internal.bodhidipta.camvid.view.AutoFitTextureView
import com.internal.bodhidipta.camvid.view.CameraSourcePreview
import com.internal.bodhidipta.camvid.view.GraphicOverlay

class CamVidBuilder {
    private var imagePath: String? = null
    private var videoPath: String? = null
    private var shouldCompress: Boolean = false
    private var faceDetect: Boolean = false
    private var drawFace: Boolean = false
    private var passedTextureView: AutoFitTextureView? = null
    private var detectionCameraSource: CameraSourcePreview? = null
    private var detectionGraphicOverLay: GraphicOverlay? = null
    private var t_accelerometer: SensorEventListener? = null
    private var ratio: CommonClass.ImageAspectRatio = CommonClass.ImageAspectRatio.FULL
    private var captureCompleteCallback: (path: String) -> Unit = {}
    private var detectCallback: FaceDetectionCallback = object : FaceDetectionCallback {
        override fun onUpdateFaceCount(totalFace: List<Int>) {
            //To change body of created functions use File | Settings | File Templates.
        }

        override fun onFaceUpdate(face: Face?) {
            //To change body of created functions use File | Settings | File Templates.
        }

        override fun onDrawRectangle(area: Float, rect: RectF) {
            //To change body of created functions use File | Settings | File Templates.
        }


    }

    fun setRatio(aspectRation: CommonClass.ImageAspectRatio): CamVidBuilder {
        ratio = aspectRation
        return this
    }

    fun setCameraView(mode: CameraOperationMode): CamVidBuilder {
        faceDetect = when (mode) {
            is DetectorOperationMode -> {
                detectionCameraSource = mode.cameraSourcePreview
                detectionGraphicOverLay = mode.graphicOverlay
                drawFace = mode.shouldDrawFace
                true
            }
            is CaptureOperationMode -> {
                passedTextureView = mode.cameraSourcePreview
                false
            }
            else -> false
        }

        return this
    }

    fun setCameraImagePath(absolutePath: String): CamVidBuilder {
        imagePath = absolutePath
        return this
    }

    fun shouldCompress(compress: Boolean): CamVidBuilder {
        shouldCompress = compress
        return this
    }

    fun accelerometerSensor(accelerometer: SensorEventListener): CamVidBuilder {
        t_accelerometer = accelerometer
        return this
    }

    fun setVideoImagePath(absolutePath: String): CamVidBuilder {
        videoPath = absolutePath
        return this
    }

    fun setCaptureCallback(callback: (path: String) -> Unit): CamVidBuilder {
        captureCompleteCallback = callback
        return this
    }

    fun setDetectionCallback(callback: FaceDetectionCallback): CamVidBuilder {
        detectCallback = callback
        return this
    }

    fun getInitialise(context: Activity):
            CameraViewListener = CameraVideoTaker(
        context,
        ratio,
        imagePath,
        videoPath,
        textureView = passedTextureView,
        cameraSourcePreview = detectionCameraSource,
        graphicOverlay = detectionGraphicOverLay,
        shouldCompress = shouldCompress,
        measureSensor = t_accelerometer,
        shouldDetectFace = faceDetect,
        deatectDrawFace = drawFace,
        captureCompleteCallback = captureCompleteCallback,
        detectCallback = detectCallback
    )

    fun getInitialise(context: FragmentActivity):
            CameraViewListener = CameraVideoTaker(
        context,
        ratio,
        imagePath,
        videoPath,
        textureView = passedTextureView,
        cameraSourcePreview = detectionCameraSource,
        graphicOverlay = detectionGraphicOverLay,
        shouldCompress = shouldCompress,
        measureSensor = t_accelerometer,
        shouldDetectFace = faceDetect,
        deatectDrawFace = drawFace,
        captureCompleteCallback = captureCompleteCallback,
        detectCallback = detectCallback
    )
}