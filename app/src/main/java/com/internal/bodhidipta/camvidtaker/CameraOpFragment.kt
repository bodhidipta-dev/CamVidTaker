package com.internal.bodhidipta.camvidtaker

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.internal.bodhidipta.camvid.cameravideotaker.CameraVideoTaker
import com.internal.bodhidipta.camvid.cameravideotaker.CameraViewListener
import com.internal.bodhidipta.camvid.required.CommonClass
import com.internal.bodhidipta.camvid.view.AutoFitTextureView
import java.io.File


/**
 * Created on 06/12/18.
 * Project CamCordPrev
 */

class CameraOpFragment : Fragment() {
    private lateinit var cameraViewListener: CameraViewListener
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.cameraview_surface_view, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val cameraview: AutoFitTextureView = view.findViewById(R.id.cameraview)
        val imageContainer: RelativeLayout = view.findViewById(R.id.image_container)
        val flash_option: ImageView = view.findViewById(R.id.flash_option)
        val operation_mode: TabLayout = view.findViewById(R.id.operation_mode)
        activity?.let {
            cameraViewListener = CameraVideoTaker
                .setCameraView(cameraview)
                .setRatio(CommonClass.ImageAspectRatio.FULL)
                .getInitialise(it)
        }

        flash_option.setOnClickListener {
            if (cameraViewListener.clickCameraFlashToggle()) {
                it.setBackgroundResource(R.drawable.ic_flash_on)
            } else {
                it.setBackgroundResource(R.drawable.ic_flash_off)

            }
        }
        view.findViewById<ImageView>(R.id.capture_image).setOnClickListener {
            cameraViewListener.clickCameraCapture()
        }
        var record = false
        view.findViewById<ImageView>(R.id.capture_video).setOnClickListener {
            if (record) {
                record = false
                cameraViewListener.stopVideoCapture({ destinationPath ->
                    /*
                    Decide whether to proceed or not
                     * */
                    destinationPath?.let {
                        val retriever = MediaMetadataRetriever()
                        //use one of overloaded setDataSource() functions to set your data source
                        retriever.setDataSource(context, Uri.fromFile(File(it)))
                        val time =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val timeInMillisec = java.lang.Long.parseLong(time)
                        Toast.makeText(
                            activity,
                            "Time elapsed :: $timeInMillisec",
                            Toast.LENGTH_SHORT
                        ).show()
                        retriever.release()
                    }

                }, {

                })
            } else {
                record = true
                cameraViewListener.startVideoCapture()
            }
        }

        view.findViewById<ImageView>(R.id.change_camera).setOnClickListener {
            cameraViewListener.clickCameraViewToggle()
        }
        view.findViewById<ImageView>(R.id.change_video_camera).setOnClickListener {
            cameraViewListener.clickVideoViewtoggle()
        }

        operation_mode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(p0: TabLayout.Tab?) {

            }

            override fun onTabUnselected(p0: TabLayout.Tab?) {
            }

            override fun onTabSelected(p0: TabLayout.Tab?) {
                when (p0?.position) {
                    0 -> {
                        imageContainer.visibility = View.VISIBLE
                    }
                    1 -> {
                        imageContainer.visibility = View.GONE
                    }
                    else -> {
                        Toast.makeText(
                            activity,
                            "Need to implement gallery view here.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

        })


    }

    override fun onResume() {
        super.onResume()
        ::cameraViewListener.isInitialized.let {
            if (it)
                cameraViewListener.onResumeView()
        }
    }

    override fun onPause() {
        super.onPause()
        ::cameraViewListener.isInitialized.let {
            if (it)
                cameraViewListener.onPauseView()
        }
    }

    override fun onStop() {
        super.onStop()
        ::cameraViewListener.isInitialized.let {
            if (it)
                cameraViewListener.onStopView()
        }
    }
}