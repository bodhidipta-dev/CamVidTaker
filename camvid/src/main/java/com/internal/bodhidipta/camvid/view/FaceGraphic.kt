/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.internal.bodhidipta.camvid.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.vision.face.Face
import com.internal.bodhidipta.camvid.cameravideotaker.FaceDetectionCallback
import com.internal.bodhidipta.camvid.view.GraphicOverlay.Graphic

/**
 * Graphic instance for rendering face position, orientation, and landmarks within an associated
 * graphic overlay view.
 */
internal class FaceGraphic(
    overlay: GraphicOverlay,
    private val faceDetectionCallback: FaceDetectionCallback
) : Graphic(overlay) {
    private val mFacePositionPaint: Paint
    private val mIdPaint: Paint
    private val mBoxPaint: Paint
    @Volatile
    private var mFace: Face? = null
    private var mFaceId = 0
    private val mFaceHappiness = 0f
    fun setId(id: Int) {
        mFaceId = id
    }

    var drawFace = true

    /**
     * Updates the face instance from the detection of the most recent frame.  Invalidates the
     * relevant portions of the overlay to trigger a redraw.
     */
    fun updateFace(face: Face?) {
        mFace = face
        postInvalidate()
    }

    /**
     * Draws the face annotations for position on the supplied canvas.
     */

    companion object {
        private const val FACE_POSITION_RADIUS = 10.0f
        private const val ID_TEXT_SIZE = 40.0f
        private const val ID_Y_OFFSET = 50.0f
        private const val ID_X_OFFSET = -50.0f
        private const val BOX_STROKE_WIDTH = 5.0f
        private val COLOR_CHOICES = intArrayOf(
            Color.BLUE,
            Color.CYAN,
            Color.GREEN,
            Color.MAGENTA,
            Color.RED,
            Color.WHITE,
            Color.YELLOW
        )
        private var mCurrentColorIndex = 0
    }

    init {
        mCurrentColorIndex =
            (mCurrentColorIndex + 1) % COLOR_CHOICES.size
        val selectedColor =
            COLOR_CHOICES[mCurrentColorIndex]
        mFacePositionPaint = Paint()
        mFacePositionPaint.color = selectedColor
        mIdPaint = Paint()
        mIdPaint.color = selectedColor
        mIdPaint.textSize = ID_TEXT_SIZE
        mBoxPaint = Paint()
        mBoxPaint.color = selectedColor
        mBoxPaint.style = Paint.Style.STROKE
        mBoxPaint.strokeWidth = BOX_STROKE_WIDTH
    }

    override fun draw(canvas: Canvas?) {
        canvas?.let {
            val face = mFace ?: return
            val x = translateX(face.position.x + face.width / 2)
            val y = translateY(face.position.y + face.height / 2)
            // Draws a bounding box around the face.
            val xOffset = scaleX(face.width / 2.0f)
            val yOffset = scaleY(face.height / 2.0f)
            val left = x - xOffset
            val top = y - yOffset
            val right = x + xOffset
            val bottom = y + yOffset
            if (drawFace) {
                // Draws a circle at the position of the detected face, with the face's track id below.
//                it.drawCircle(x, y, FACE_POSITION_RADIUS, mFacePositionPaint)
//                it.drawText(
//                    "id: $mFaceId",
//                    x + ID_X_OFFSET,
//                    y + ID_Y_OFFSET,
//                    mIdPaint
//                )
//                it.drawText(
//                    "happiness: " + String.format(
//                        "%.2f",
//                        face.isSmilingProbability
//                    ),
//                    x - ID_X_OFFSET,
//                    y - ID_Y_OFFSET,
//                    mIdPaint
//                )
//                it.drawText(
//                    "right eye: " + String.format(
//                        "%.2f",
//                        face.isRightEyeOpenProbability
//                    ),
//                    x + ID_X_OFFSET * 2,
//                    y + ID_Y_OFFSET * 2,
//                    mIdPaint
//                )
//                it.drawText(
//                    "left eye: " + String.format(
//                        "%.2f",
//                        face.isLeftEyeOpenProbability
//                    ),
//                    x - ID_X_OFFSET * 2,
//                    y - ID_Y_OFFSET * 2,
//                    mIdPaint
//                )
//                it.drawRect(left, top, right, bottom, mBoxPaint)

            }

            faceDetectionCallback.onDrawRectangle(
                (2 * (right - left) + (bottom - top)),
                RectF(left, top, right, bottom),
                face
            )
        }
    }
}