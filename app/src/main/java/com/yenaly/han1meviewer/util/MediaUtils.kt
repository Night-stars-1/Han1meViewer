package com.yenaly.han1meviewer.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.yenaly.yenaly_libs.utils.showShortToast

object MediaUtils {

    fun playMedia(context: Context, uri: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val videoFile = uri.toUri().toFile()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileProvider",
                videoFile
            )
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }


        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            showShortToast("未找到可用的播放器")
        }
    }
}