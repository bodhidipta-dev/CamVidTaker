package com.internal.bodhidipta.camvid.required

import com.google.android.gms.vision.Detector.Detections
import com.google.android.gms.vision.Tracker
import com.google.android.gms.vision.face.Face
import com.internal.bodhidipta.camvid.cameravideotaker.FaceDetectionCallback
import com.internal.bodhidipta.camvid.view.FaceGraphic
import com.internal.bodhidipta.camvid.view.GraphicOverlay

internal class GraphicFaceTracker(
    private val mOverlay: GraphicOverlay,
    private val drawface: Boolean?,
    private val detectCallback: FaceDetectionCallback
) : Tracker<Face?>() {
    private val mFaceGraphic: FaceGraphic = FaceGraphic(mOverlay)
    /**
     * Start tracking the detected face instance within the face overlay.
     */
    private val mutableList: MutableList<Int> = mutableListOf()

    override fun onNewItem(
        faceId: Int,
        item: Face?
    ) {
        mutableList.add(faceId)
        mFaceGraphic.setId(faceId)
        detectCallback.onUpdateFaceCount(mutableList)
    }

    /**
     * Update the position/characteristics of the face within the overlay.
     */
    override fun onUpdate(
        detectionResults: Detections<Face?>,
        face: Face?
    ) {
        mOverlay.add(mFaceGraphic)
        mFaceGraphic.updateFace(face)
        detectCallback.onFaceUpdate(face)
    }

    /**
     * Hide the graphic when the corresponding face was not detected.  This can happen for
     * intermediate frames temporarily (e.g., if the face was momentarily blocked from
     * view).
     */
    override fun onMissing(detectionResults: Detections<Face?>) {
        mOverlay.remove(mFaceGraphic)
    }

    /**
     * Called when the face is assumed to be gone for good. Remove the graphic annotation from
     * the overlay.
     */
    override fun onDone() {
        mOverlay.remove(mFaceGraphic)
    }

    init {
        mFaceGraphic.drawFace = drawface!!
    }
}