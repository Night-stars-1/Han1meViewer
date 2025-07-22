package com.yenaly.han1meviewer.util

import android.content.res.Resources

fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()
fun Float.dpToPx(): Float = (this * Resources.getSystem().displayMetrics.density)
