package com.internal.bodhidipta.camvid.required

import com.internal.bodhidipta.camvid.view.AutoFitTextureView
import com.internal.bodhidipta.camvid.view.CameraSourcePreview
import com.internal.bodhidipta.camvid.view.GraphicOverlay

sealed class CameraOperationMode
data class DetectorOperationMode(
    val cameraSourcePreview: CameraSourcePreview,
    val graphicOverlay: GraphicOverlay,
    val shouldDrawFace: Boolean = false
) : CameraOperationMode()

data class CaptureOperationMode(
    val cameraSourcePreview: AutoFitTextureView
) : CameraOperationMode()