package com.internal.bodhidipta.camvid.cameravideotaker

import android.graphics.RectF
import com.google.android.gms.vision.face.Face

/**
 * Created for com.internal.bodhidipta.camvid.cameravideotaker on 14-12-2019 by Bodhidipta
 * Project CamVidTaker
 */
interface FaceDetectionCallback {
    fun onUpdateFaceCount(totalFace: List<Int>)
    fun onFaceUpdate(face: Face?)
    fun onDrawRectangle(area: Float, rect: RectF)
}