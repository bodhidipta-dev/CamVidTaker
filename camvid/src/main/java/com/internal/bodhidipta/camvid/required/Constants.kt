@file:JvmName("Constants")
package com.internal.bodhidipta.camvid.required

import android.Manifest

@JvmField val REQUEST_CAMERA_PERMISSION = 1
@JvmField val PIC_FILE_NAME = "pic.jpg"
@JvmField val REQUEST_VIDEO_PERMISSIONS = 1
@JvmField val VIDEO_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
