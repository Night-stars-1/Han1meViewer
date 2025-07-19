package com.yenaly.han1meviewer.ui.view.video

interface IMedia {
    val width: Int
    val height: Int

    val ratio: Float
        get() = if (height == 0) 1f else width.toFloat() / height.toFloat()
}