package com.internal.bodhidipta.camvid.required

import com.google.android.gms.vision.MultiProcessor
import com.google.android.gms.vision.Tracker
import com.google.android.gms.vision.face.Face
import com.internal.bodhidipta.camvid.cameravideotaker.FaceDetectionCallback
import com.internal.bodhidipta.camvid.view.GraphicOverlay

internal class GraphicFaceTrackerFactory(
    private val mGraphicOverlay: GraphicOverlay,
    private val drawFace: Boolean,
    private val detectCallback: FaceDetectionCallback
) : MultiProcessor.Factory<Face> {
    override fun create(face: Face): Tracker<Face> {
        return GraphicFaceTracker(
            mOverlay = mGraphicOverlay,
            drawface = drawFace,
            detectCallback = detectCallback
        ) as Tracker<Face>
    }
}