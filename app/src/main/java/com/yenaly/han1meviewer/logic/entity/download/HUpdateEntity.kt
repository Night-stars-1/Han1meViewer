package com.yenaly.han1meviewer.logic.entity.download

import androidx.annotation.IntRange
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.yenaly.han1meviewer.logic.state.DownloadState

@Entity
@TypeConverters(HUpdateEntity.StateTypeConverter::class)
data class HUpdateEntity(
    @PrimaryKey()
    val id: Int = 1,

    val name: String,
    val url: String,
    val nodeId: String,

    /**
     * 安装包长度
     */
    val length: Long,
    /**
     * 安装包已下载长度
     */
    val downloadedLength: Long,

    /**
     * 当前状态
     */
    val state: DownloadState = DownloadState.Unknown,
) {
    /**
     * 下载进度
     */
    @get:IntRange(from = 0, to = 100)
    val progress get() = (downloadedLength * 100 / length).toInt()
    /**
     * 是否已下载完成
     */
    val isDownloaded get() = state == DownloadState.Finished

    val isDownloading get() = state == DownloadState.Downloading

    class StateTypeConverter {
        @TypeConverter
        fun from(state: DownloadState): Int = state.mask

        @TypeConverter
        fun to(state: Int): DownloadState = DownloadState.from(state)
    }
}