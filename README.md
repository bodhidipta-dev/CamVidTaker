# CamVidTaker [ ![Download](https://api.bintray.com/packages/bodhidipta-dev/CamVidTaker/com.internal.bodhidipta.camvid/images/download.svg) ](https://bintray.com/bodhidipta-dev/CamVidTaker/com.internal.bodhidipta.camvid/_latestVersion)

Wrapper for both camera and video, seamless switching between camera to video and video to camera mode without restarting camera preview. Also  added 90% compression on the video quality along with original one.
Feel free to contribute more ideas.

[![Watch the video](https://youtu.be/--2surmu1uU)](https://youtu.be/--2surmu1uU)

<b> How to Implement </b>

 This will return an instrance of the implementation class 
 
                val cameraViewListener: CameraViewListener = CameraVideoTaker
                .setCameraView(cameraview)
                .shouldCompress(true) // If not required dont need to send
                .setRatio(CommonClass.ImageAspectRatio.FULL) // For full image or ONE:ONE
                .getInitialise(it)
                
With the implementation class you can 

    cameraViewListener.clickCameraCapture()

or

     cameraViewListener.startVideoCapture()

to stop video recording
    
    cameraViewListener.stopVideoCapture({ destinationPath ->
                  // Your code goes here after success
                }, {
                // For Error fallback
                })
                
                
To Change the Camera from back to front
   
    cameraViewListener.clickCameraViewToggle()    

or

    cameraViewListener.clickVideoViewtoggle()

Place the codes for onResume,onPause and OnStop
to avoid memory leak from camera or any allocated surface object.
    
     override fun onResume() {
        super.onResume()
        cameraViewListener.onResumeView()
        }
     override fun onPause() {
        super.onPause()
        cameraViewListener.onPauseView()
        }
    }
     override fun onStop() {
        super.onStop()
        cameraViewListener.onStopView()
        }
    }    
  
  
