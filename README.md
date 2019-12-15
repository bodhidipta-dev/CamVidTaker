# CamVidTaker [ ![Download](https://api.bintray.com/packages/bodhidipta-dev/internal/com.internal.bodhidipta/images/download.svg) ](https://bintray.com/bodhidipta-dev/internal/com.internal.bodhidipta/_latestVersion)
Wrapper for both camera and video, seamless switching between camera to video and video to camera mode without restarting camera preview. Also  added 90% compression on the video quality along with original one.
Feel free to contribute more ideas.

[![Watch the video](https://youtu.be/--2surmu1uU)](https://youtu.be/--2surmu1uU)

<b> How to Implement </b>

 This will return an instrance of the implementation class 
 
 For Normal Camera and video operation-
 
                val cameraViewListener: CameraViewListener = CamVidBuilder()
                .setCameraView(
                    CaptureOperationMode(
                        preview
                    )
                )
                .shouldCompress(true) // If not required dont need to send
                .setRatio(CommonClass.ImageAspectRatio.FULL) // For full image or ONE:ONE
                .getInitialise(it)
                
 For Face Detection mode(Front camera default)
               
              val cameraViewListener: CameraViewListener = CamVidBuilder()
                .setCameraView(
                    DetectorOperationMode(
                        cameraSourcePreview = preview,
                        graphicOverlay = faceOverlay,
                        shouldDrawFace = true
                    )
                )
                .setCaptureCallback {

                }
                .setDetectionCallback(object : FaceDetectionCallback {
                    override fun onUpdateFaceCount(totalFace: List<Int>) {
                       //To change body of created functions use File | Settings | File Templates.
                    }

                    override fun onFaceUpdate(face: Face?) {
                         //To change body of created functions use File | Settings | File Templates.
                    }

                })
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
 
# Code source
Camera example taken from
https://github.com/googlearchive/android-Camera2Basic

Face detection code source is taken from
https://github.com/googlesamples/android-vision
 
# Dependency Added
  On your project's app build.gradle file add implementation as

            implementation 'com.internal.bodhidipta:camvid:1.0.5'

Add View on your Xml as 
 
     <com.internal.bodhidipta.camvid.view.AutoFitTextureView
    android:id="@+id/cameraview"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
