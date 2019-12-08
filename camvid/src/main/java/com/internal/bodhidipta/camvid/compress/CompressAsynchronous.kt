package com.internal.bodhidipta.camvid.compress

import android.os.AsyncTask
import android.text.TextUtils
import java.io.File

class CompressAsynchronous(
    private val onSuccess: (destinationPath: String?) -> Unit,
    private val onError: () -> Unit
) :
    AsyncTask<String, Void, String>() {

    private var pathToReEncodedFile = ""

    override fun doInBackground(vararg strings: String): String {
        pathToReEncodedFile = try {
            VideoResolutionChanger().changeResolution(File(strings[0]), strings[1])
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            "" }
        return pathToReEncodedFile
    }

    override fun onPostExecute(s: String) {
        super.onPostExecute(s)
        if (!TextUtils.isEmpty(s))
            onSuccess(s)
        else
            onError()
    }
}