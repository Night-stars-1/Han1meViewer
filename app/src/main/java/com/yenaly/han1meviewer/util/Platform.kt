package com.yenaly.han1meviewer.util

import android.os.Build

object Platform {
    val isHuaweiDevice = Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true)
}