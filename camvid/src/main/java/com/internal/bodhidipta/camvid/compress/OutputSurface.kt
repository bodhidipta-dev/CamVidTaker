package com.internal.bodhidipta.camvid.compress

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.util.Log
import android.view.Surface
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import javax.microedition.khronos.egl.*
import kotlin.concurrent.withLock

internal class OutputSurface : SurfaceTexture.OnFrameAvailableListener {
    private var mEGL: EGL10? = null
    private var mEGLDisplay: EGLDisplay? = null
    private var mEGLContext: EGLContext? = null
    private var mEGLSurface: EGLSurface? = null
    private var mSurfaceTexture: SurfaceTexture? = null
    var surface: Surface? = null
        private set
    private var mFrameAvailable: Boolean = false
    private var mTextureRender: TextureRender? = null
    private val mFrameSyncObject = ReentrantLock() // guards mFrameAvailable
    private val condition: Condition = mFrameSyncObject.newCondition()

    constructor(width: Int, height: Int) {
        require(!(width <= 0 || height <= 0))
        eglSetup(width, height)
        makeCurrent()
        setup()
    }

    constructor() {
        setup()
    }

    private fun setup() {
        mTextureRender = TextureRender()
        mTextureRender?.surfaceCreated()
        // Even if we don't access the SurfaceTexture after the constructor returns, we
        // still need to keep a reference to it.  The Surface doesn't retain a reference
        // at the Java level, so if we don't either then the object can get GCed, which
        // causes the native finalizer to run.
        if (VERBOSE) Log.d(TAG, "textureID=" + mTextureRender?.textureId)
        mSurfaceTexture = SurfaceTexture(mTextureRender?.textureId ?: 0)
        // This doesn't work if OutputSurface is created on the thread that CTS started for
        // these test cases.
        //
        // The CTS-created thread has a Looper, and the SurfaceTexture constructor will
        // create a Handler that uses it.  The "frame available" message is delivered
        // there, but since we're not a Looper-based thread we'll never see it.  For
        // this to do anything useful, OutputSurface must be created on a thread without
        // a Looper, so that SurfaceTexture uses the main application Looper instead.
        //
        // Java language note: passing "this" out of a constructor is generally unwise,
        // but we should be able to get away with it here.
        mSurfaceTexture?.setOnFrameAvailableListener(this)
        surface = Surface(mSurfaceTexture)
    }

    private fun eglSetup(width: Int, height: Int) {
        mEGL = EGLContext.getEGL() as EGL10
        mEGLDisplay = mEGL?.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
        if (mEGL?.eglInitialize(mEGLDisplay, null) == false) {
            throw RuntimeException("unable to initialize EGL10")
        }
        // Configure EGL for pbuffer and OpenGL ES 2.0.  We want enough RGB bits
        // to be able to tell if the frame is reasonable.
        val attribList = intArrayOf(
            EGL10.EGL_RED_SIZE,
            8,
            EGL10.EGL_GREEN_SIZE,
            8,
            EGL10.EGL_BLUE_SIZE,
            8,
            EGL10.EGL_SURFACE_TYPE,
            EGL10.EGL_PBUFFER_BIT,
            EGL10.EGL_RENDERABLE_TYPE,
            EGL_OPENGL_ES2_BIT,
            EGL10.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (mEGL?.eglChooseConfig(mEGLDisplay, attribList, configs, 1, numConfigs) == false) {
            throw RuntimeException("unable to find RGB888+pbuffer EGL config")
        }
        // Configure context for OpenGL ES 2.0.
        val attrib_list = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE)
        mEGLContext = mEGL?.eglCreateContext(
            mEGLDisplay, configs[0], EGL10.EGL_NO_CONTEXT,
            attrib_list
        )
        checkEglError("eglCreateContext")
        if (mEGLContext == null) {
            throw RuntimeException("null context")
        }
        // Create a pbuffer surface.  By using this for output, we can use glReadPixels
        // to test values in the output.
        val surfaceAttribs =
            intArrayOf(EGL10.EGL_WIDTH, width, EGL10.EGL_HEIGHT, height, EGL10.EGL_NONE)
        mEGLSurface = mEGL?.eglCreatePbufferSurface(mEGLDisplay, configs[0], surfaceAttribs)
        checkEglError("eglCreatePbufferSurface")
        if (mEGLSurface == null) {
            throw RuntimeException("surface was null")
        }
    }

    fun release() {
        if (mEGL != null) {
            if (mEGL?.eglGetCurrentContext() == mEGLContext) {
                // Clear the current context and surface to ensure they are discarded immediately.
                mEGL?.eglMakeCurrent(
                    mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                    EGL10.EGL_NO_CONTEXT
                )
            }
            mEGL?.eglDestroySurface(mEGLDisplay, mEGLSurface)
            mEGL?.eglDestroyContext(mEGLDisplay, mEGLContext)
            //mEGL.eglTerminate(mEGLDisplay);
        }
        surface?.release()
        // this causes a bunch of warnings that appear harmless but might confuse someone:
        //  W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!
        //mSurfaceTexture.release();
        // null everything out so future attempts to use this object will cause an NPE
        mEGLDisplay = null
        mEGLContext = null
        mEGLSurface = null
        mEGL = null
        mTextureRender = null
        surface = null
        mSurfaceTexture = null
    }

    private fun makeCurrent() {
        if (mEGL == null) {
            throw RuntimeException("not configured for makeCurrent")
        }
        checkEglError("before makeCurrent")
        if (!mEGL!!.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun changeFragmentShader(fragmentShader: String) {
        mTextureRender!!.changeFragmentShader(fragmentShader)
    }

    fun awaitNewImage() {
        val TIMEOUT_MS = 3000
        mFrameSyncObject.withLock {
            while (!mFrameAvailable) {
                try {
                    // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    condition.await(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                    if (!mFrameAvailable) {
                        // TODO: if "spurious wakeup", continue while loop
                        throw RuntimeException("Surface frame wait timed out")
                    }
                } catch (ie: InterruptedException) {
                    // shouldn't happen
                    throw RuntimeException(ie)
                }

            }
            mFrameAvailable = false
        }
        // Latch the data.
        mTextureRender?.checkGlError("before updateTexImage")
        mSurfaceTexture?.updateTexImage()
    }

    fun drawImage() {
        mSurfaceTexture?.let {
            mTextureRender?.drawFrame(it)
        }
    }

    override fun onFrameAvailable(st: SurfaceTexture) {
        if (VERBOSE) Log.d(TAG, "new frame available")
        mFrameSyncObject.withLock {
            if (mFrameAvailable) {
                throw RuntimeException("mFrameAvailable already set, frame could be dropped")
            }
            mFrameAvailable = true
            condition.signalAll()
        }
    }

    private fun checkEglError(msg: String) {
        var failed = false
        val error: Int = mEGL?.eglGetError() ?: 0
        while (error != EGL10.EGL_SUCCESS) {
            Log.e(TAG, msg + ": EGL error: 0x" + Integer.toHexString(error))
            failed = true
        }
        if (failed) {
            throw RuntimeException("EGL error encountered (see log)")
        }
    }

    companion object {
        private val TAG = "OutputSurface"
        private val VERBOSE = false
        private val EGL_OPENGL_ES2_BIT = 4
    }
}