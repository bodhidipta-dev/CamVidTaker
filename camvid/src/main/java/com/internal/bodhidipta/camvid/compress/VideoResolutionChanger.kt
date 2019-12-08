package com.internal.bodhidipta.camvid.compress

import android.media.*
import android.view.Surface
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

internal class VideoResolutionChanger {

    private val mWidth = 480
    private val mHeight = 640
    private var mOutputFile: String? = null
    private var mInputFile: String? = null

    @Throws(Throwable::class)
    fun changeResolution(f: File, destinationpath: String): String {
        mInputFile = f.absolutePath

        val filePath = mInputFile?.substring(0, mInputFile!!.lastIndexOf(File.separator))
        val splitByDot =
            mInputFile?.split("\\.".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
        val ext = splitByDot?.let {
            if (it.size > 1)
                splitByDot[splitByDot.size - 1] else ""
        } ?: ""

        val lastindex = mInputFile?.lastIndexOf(
            File.separator,
            0,
            false
        )
        val fileName = mInputFile?.substring(
            lastindex?.plus(1) ?: 0,
            mInputFile?.length ?: 1
        )
        if (ext.isNotEmpty())
            fileName?.replace(".$ext", "_out.mp4")
        else
            fileName + "_out.mp4"

        val outFile = File(destinationpath)
        if (!outFile.exists())
            outFile.createNewFile()

        mOutputFile = outFile.absolutePath

        ChangerWrapper.changeResolutionInSeparatedThread(this)

        return mOutputFile ?: ""
    }

    private class ChangerWrapper private constructor(private val mChanger: VideoResolutionChanger) :
        Runnable {

        private var mThrowable: Throwable? = null

        override fun run() {
            try {
                mChanger.prepareAndChangeResolution()
            } catch (th: Throwable) {
                mThrowable = th
            }
        }

        companion object {

            @Throws(Throwable::class)
            fun changeResolutionInSeparatedThread(changer: VideoResolutionChanger) {
                val wrapper = ChangerWrapper(changer)
                val th = Thread(wrapper, ChangerWrapper::class.java.simpleName)
                th.start()
                th.join()
                if (wrapper.mThrowable != null)
                    throw wrapper.mThrowable
                        ?: NullPointerException("Could not find ChangeWrapper Exception")
            }
        }
    }

    @Throws(Exception::class)
    private fun prepareAndChangeResolution() {
        var exception: Exception? = null

        val videoCodecInfo = selectCodec(OUTPUT_VIDEO_MIME_TYPE) ?: return

        var videoExtractor: MediaExtractor? = null
        var outputSurface: OutputSurface? = null
        var videoDecoder: MediaCodec? = null
        var videoEncoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: InputSurface? = null
        try {
            videoExtractor = createExtractor()
            val videoInputTrack = getAndSelectVideoTrackIndex(videoExtractor)
            val inputFormat = videoExtractor.getTrackFormat(videoInputTrack)

            val m = MediaMetadataRetriever()
            m.setDataSource(mInputFile)
            //            int inputWidth, inputHeight;
            //            try {
            //                inputWidth = Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            //                inputHeight = Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            //            } catch (Exception e) {
            //                Bitmap thumbnail = m.getFrameAtTime();
            //                inputWidth = thumbnail.getWidth();
            //                inputHeight = thumbnail.getHeight();
            //                thumbnail.recycle();
            //            }
            //
            //            if (inputWidth > inputHeight) {
            //                if (mWidth < mHeight) {
            //                    int w = mWidth;
            //                    mWidth = mHeight;
            //                    mHeight = w;
            //                }
            //            } else {
            //                if (mWidth > mHeight) {
            //                    int w = mWidth;
            //                    mWidth = mHeight;
            //                    mHeight = w;
            //                }
            //            }

            val outputVideoFormat =
                MediaFormat.createVideoFormat(OUTPUT_VIDEO_MIME_TYPE, mWidth, mHeight)
            outputVideoFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT, OUTPUT_VIDEO_COLOR_FORMAT
            )
            outputVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_VIDEO_BIT_RATE)
            outputVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, OUTPUT_VIDEO_FRAME_RATE)
            outputVideoFormat.setInteger(
                MediaFormat.KEY_I_FRAME_INTERVAL, OUTPUT_VIDEO_IFRAME_INTERVAL
            )

            val inputSurfaceReference = AtomicReference<Surface>()
            videoEncoder = createVideoEncoder(
                videoCodecInfo, outputVideoFormat, inputSurfaceReference
            )
            inputSurface = InputSurface(inputSurfaceReference.get())
            inputSurface.makeCurrent()

            outputSurface = OutputSurface()
            outputSurface.surface?.let {
                videoDecoder = createVideoDecoder(inputFormat, it)
            }
            muxer = MediaMuxer(mOutputFile!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            videoDecoder?.let {
                changeResolution(
                    videoExtractor,
                    it, videoEncoder,
                    muxer, inputSurface, outputSurface
                )
            }
        } finally {
            try {
                videoExtractor?.release()
            } catch (e: Exception) {
                if (exception == null)
                    exception = e
            }

            try {
                if (videoDecoder != null) {
                    videoDecoder?.stop()
                    videoDecoder?.release()
                }
            } catch (e: Exception) {
                if (exception == null)
                    exception = e
            }

            try {
                outputSurface?.release()
            } catch (e: Exception) {
                if (exception == null)
                    exception = e
            }

            try {
                if (videoEncoder != null) {
                    videoEncoder.stop()
                    videoEncoder.release()
                }
            } catch (e: Exception) {
                if (exception == null)
                    exception = e
            }

            try {
                if (muxer != null) {
                    muxer.stop()
                    muxer.release()
                }
            } catch (e: Exception) {
                if (exception == null)
                    exception = e
            }

            try {
                inputSurface?.release()
            } catch (e: Exception) {
                if (exception == null)
                    exception = e
            }

        }
        if (exception != null)
            throw exception
    }

    @Throws(IOException::class)
    private fun createExtractor(): MediaExtractor {
        val extractor: MediaExtractor = MediaExtractor()
        mInputFile?.let {
            extractor.setDataSource(it)
        }
        return extractor
    }

    @Throws(IOException::class)
    private fun createVideoDecoder(inputFormat: MediaFormat, surface: Surface): MediaCodec {
        val decoder = MediaCodec.createDecoderByType(getMimeTypeFor(inputFormat)!!)
        decoder.configure(inputFormat, surface, null, 0)
        decoder.start()
        return decoder
    }

    @Throws(IOException::class)
    private fun createVideoEncoder(
        codecInfo: MediaCodecInfo, format: MediaFormat,
        surfaceReference: AtomicReference<Surface>
    ): MediaCodec {
        val encoder = MediaCodec.createByCodecName(codecInfo.name)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surfaceReference.set(encoder.createInputSurface())
        encoder.start()
        return encoder
    }


    private fun getAndSelectVideoTrackIndex(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            if (isVideoFormat(extractor.getTrackFormat(index))) {
                extractor.selectTrack(index)
                return index
            }
        }
        return -1
    }

    private fun changeResolution(
        videoExtractor: MediaExtractor,
        videoDecoder: MediaCodec, videoEncoder: MediaCodec,
        muxer: MediaMuxer,
        inputSurface: InputSurface, outputSurface: OutputSurface
    ) {
        val videoDecoderInputBuffers: Array<ByteBuffer> = videoDecoder.inputBuffers
        var videoDecoderOutputBuffers: Array<ByteBuffer>? = videoDecoder.outputBuffers
        var videoEncoderOutputBuffers: Array<ByteBuffer>? = videoEncoder.outputBuffers
        val videoDecoderOutputBufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
        val videoEncoderOutputBufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()

        var decoderOutputVideoFormat: MediaFormat? = null
        var encoderOutputVideoFormat: MediaFormat? = null
        var outputVideoTrack = -1

        var videoExtractorDone = false
        var videoDecoderDone = false
        var videoEncoderDone = false


        var muxing = false
        while (!videoEncoderDone) {
            while (!videoExtractorDone && (encoderOutputVideoFormat == null || muxing)) {
                val decoderInputBufferIndex = videoDecoder.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break

                val decoderInputBuffer = videoDecoderInputBuffers[decoderInputBufferIndex]
                val size = videoExtractor.readSampleData(decoderInputBuffer, 0)
                val presentationTime = videoExtractor.sampleTime

                if (size >= 0) {
                    videoDecoder.queueInputBuffer(
                        decoderInputBufferIndex,
                        0,
                        size,
                        presentationTime,
                        videoExtractor.sampleFlags
                    )
                }
                videoExtractorDone = !videoExtractor.advance()
                if (videoExtractorDone)
                    videoDecoder.queueInputBuffer(
                        decoderInputBufferIndex,
                        0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                break
            }

            while (!videoDecoderDone && (encoderOutputVideoFormat == null || muxing)) {
                val decoderOutputBufferIndex = videoDecoder.dequeueOutputBuffer(
                    videoDecoderOutputBufferInfo, TIMEOUT_USEC.toLong()
                )
                if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break

                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    videoDecoderOutputBuffers = videoDecoder.outputBuffers
                    break
                }
                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    decoderOutputVideoFormat = videoDecoder.outputFormat
                    break
                }

                val decoderOutputBuffer = videoDecoderOutputBuffers?.get(decoderOutputBufferIndex)
                if (videoDecoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    videoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false)
                    break
                }

                val render = videoDecoderOutputBufferInfo.size != 0
                videoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, render)
                if (render) {
                    outputSurface.awaitNewImage()
                    outputSurface.drawImage()
                    inputSurface.setPresentationTime(
                        videoDecoderOutputBufferInfo.presentationTimeUs * 1000
                    )
                    inputSurface.swapBuffers()
                }
                if (videoDecoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    videoDecoderDone = true
                    videoEncoder.signalEndOfInputStream()
                }
                break
            }

            while (!videoEncoderDone && (encoderOutputVideoFormat == null || muxing)) {
                val encoderOutputBufferIndex = videoEncoder.dequeueOutputBuffer(
                    videoEncoderOutputBufferInfo, TIMEOUT_USEC.toLong()
                )
                if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    videoEncoderOutputBuffers = videoEncoder.outputBuffers
                    break
                }
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    encoderOutputVideoFormat = videoEncoder.outputFormat
                    break
                }

                val encoderOutputBuffer = videoEncoderOutputBuffers!![encoderOutputBufferIndex]
                if (videoEncoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    videoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
                    break
                }
                if (videoEncoderOutputBufferInfo.size != 0) {
                    muxer.writeSampleData(
                        outputVideoTrack, encoderOutputBuffer, videoEncoderOutputBufferInfo
                    )
                }
                if (videoEncoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    videoEncoderDone = true
                }
                videoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
                break
            }


            if (!muxing && encoderOutputVideoFormat != null) {
                outputVideoTrack = muxer.addTrack(encoderOutputVideoFormat)
                muxer.start()
                muxing = true
            }
        }
    }

    companion object {

        private const val TIMEOUT_USEC = 10000

        private const val OUTPUT_VIDEO_MIME_TYPE = "video/avc"
        private const val OUTPUT_VIDEO_BIT_RATE = 1024 * 1024 // actually it will be 2048 x 1024
        private const val OUTPUT_VIDEO_FRAME_RATE = 30
        private const val OUTPUT_VIDEO_IFRAME_INTERVAL = 10
        private const val OUTPUT_VIDEO_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface

        private fun isVideoFormat(format: MediaFormat): Boolean {
            return getMimeTypeFor(format)!!.startsWith("video/")
        }

        private fun getMimeTypeFor(format: MediaFormat): String? {
            return format.getString(MediaFormat.KEY_MIME)
        }

        private fun selectCodec(mimeType: String): MediaCodecInfo? {
            val numCodecs = MediaCodecList.getCodecCount()
            for (i in 0 until numCodecs) {
                val codecInfo = MediaCodecList.getCodecInfoAt(i)
                if (!codecInfo.isEncoder) {
                    continue
                }
                val types = codecInfo.supportedTypes
                for (j in types.indices) {
                    if (types[j].equals(mimeType, ignoreCase = true)) {
                        return codecInfo
                    }
                }
            }
            return null
        }
    }
}

