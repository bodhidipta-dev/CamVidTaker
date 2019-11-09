package com.internal.bodhidipta.camvidtaker

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity


class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        setContentView(R.layout.activity_main)

        /* The main UI goes for VCameraVideoTaker along with its superclass
        * */
        savedInstanceState ?: supportFragmentManager.beginTransaction()
            .replace(R.id.fragcontainer, CameraOpFragment())
            .commit()

    }

    /*TODO -- Need to take storage permission*/
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        var valid = true
        for (grantResult in grantResults) {
            valid = valid && grantResult == PackageManager.PERMISSION_GRANTED
        }

    }

    /** Check if this device has a camera */
    private fun checkCameraHardware(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }
}
