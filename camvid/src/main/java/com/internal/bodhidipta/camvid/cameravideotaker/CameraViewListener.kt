package com.internal.bodhidipta.camvid.cameravideotaker

import com.internal.bodhidipta.camvid.required.CommonClass

interface CameraViewListener {
    fun onResumeView()
    fun onPauseView()
    fun onStopView()
    fun clickCameraCapture()
    fun clickCameraViewToggle()
    fun startVideoCapture(): Boolean
    fun stopVideoCapture(onComplete: (destinationPath: String?) -> Unit, onError: () -> Unit)
    fun clickVideoViewtoggle()
    fun clickCameraFlashToggle(): Boolean
    fun onOptionChange(operationMode: CommonClass.OperationalMode)
}