package com.yenaly.han1meviewer.ui.view.video

import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import cn.jzvd.JZMediaInterface
import cn.jzvd.JZMediaSystem
import cn.jzvd.Jzvd
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.util.AnimeShaders
import `is`.xyz.mpv.MPVLib
import kotlin.math.absoluteValue


/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2024/04/21 021 16:57
 */
sealed interface HMediaKernel {
    enum class Type(val clazz: Class<out JZMediaInterface>) {
        MediaPlayer(SystemMediaKernel::class.java),
        ExoPlayer(ExoMediaKernel::class.java),
        MpvPlayer(MpvMediaKernel::class.java);

        companion object {
            fun fromString(name: String): Type {
                return when (name) {
                    MediaPlayer.name -> MediaPlayer
                    ExoPlayer.name -> ExoPlayer
                    MpvPlayer.name -> MpvPlayer
                    else -> ExoPlayer
                }
            }
        }
    }
}

class ExoMediaKernel(jzvd: Jzvd) : JZMediaInterface(jzvd), Player.Listener, HMediaKernel, IMedia {
    companion object {
        const val TAG = "ExoMediaKernel"
    }

    private var _exoPlayer: ExoPlayer? = null

    /**
     * 尽量少用，用了之后容易出bug
     */
    private val exoPlayer get() = _exoPlayer!!

    private var prevSeek = 0L

    @OptIn(UnstableApi::class)
    override fun prepare() {
        Log.e(TAG, "prepare")
        val context = jzvd.context

        release()
//        mMediaHandlerThread = HandlerThread("JZVD")
//        mMediaHandlerThread.start()
//        mMediaHandler = Handler(mMediaHandlerThread.looper)
//        handler = Handler(Looper.getMainLooper())

        val videoTrackSelectionFactory = AdaptiveTrackSelection.Factory()
        val trackSelector = DefaultTrackSelector(context, videoTrackSelectionFactory)

        val loadControl: LoadControl = DefaultLoadControl.Builder()
            // .setBufferDurationsMs(360000, 600000, 1000, 5000)
            // .setPrioritizeTimeOverSizeThresholds(false)
            // .setTargetBufferBytes(C.LENGTH_UNSET)
            .build()


        val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        // 2. Create the player
        val renderersFactory = DefaultRenderersFactory(context)
        _exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .build()
        // Produces DataSource instances through which media data is loaded.
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(jzvd.jzDataSource.headerMap)
        )

        val currUrl = jzvd.jzDataSource.currentUrl.toString()
        val videoSource = if (currUrl.contains(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(currUrl))
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(currUrl))
        }

        Log.e(TAG, "URL Link = $currUrl")

        exoPlayer.addListener(this)

        val isLoop = jzvd.jzDataSource.looping
        if (isLoop) {
            exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
        } else {
            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
        }
        exoPlayer.setMediaSource(videoSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        val surfaceTexture = jzvd.textureView?.surfaceTexture
        surfaceTexture?.let { exoPlayer.setVideoSurface(Surface(it)) }

    }

    override fun start() {
        _exoPlayer?.playWhenReady = true
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        val realWidth = videoSize.width * videoSize.pixelWidthHeightRatio
        val realHeight = videoSize.height
        jzvd.onVideoSizeChanged(realWidth.toInt(), realHeight)
//        val ratio = realWidth / realHeight // > 1 橫屏， < 1 竖屏
//        if (ratio > 1) {
//            Jzvd.FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
//        } else {
//            Jzvd.FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
//        }
    }

    override fun onRenderedFirstFrame() {
        Log.e(TAG, "onRenderedFirstFrame")
    }

    override fun pause() {
        _exoPlayer?.playWhenReady = false
    }

    override fun isPlaying(): Boolean {
        return _exoPlayer?.playWhenReady ?: false
    }

    override fun seekTo(time: Long) {
        if (time != prevSeek) {
            _exoPlayer?.let { exoPlayer ->
                if (time >= exoPlayer.bufferedPosition) {
                    jzvd.onStatePreparingPlaying()
                }
                exoPlayer.seekTo(time)
                prevSeek = time
                jzvd.seekToInAdvance = time
            }
        }
    }

    override fun release() {
        if (_exoPlayer != null) { //不知道有没有妖孽
//            val tmpHandlerThread = mMediaHandlerThread
            val tmpMediaPlayer = exoPlayer
            SAVED_SURFACE = null
            tmpMediaPlayer.release() //release就不能放到主线程里，界面会卡顿
//            tmpHandlerThread.quit()
            _exoPlayer = null
        }
    }

    override fun getCurrentPosition(): Long {
        return _exoPlayer?.currentPosition ?: 0L
    }

    override fun getDuration(): Long {
        return _exoPlayer?.duration ?: 0L
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        _exoPlayer?.volume = (leftVolume + rightVolume) / 2
    }

    override fun setSpeed(speed: Float) {
        val playbackParams = PlaybackParameters(speed, 1.0F)
        _exoPlayer?.playbackParameters = playbackParams
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        Log.e(TAG, "onTimelineChanged")
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        Log.e(TAG, "onIsLoadingChanged")
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady && _exoPlayer?.playbackState == Player.STATE_READY) {
            jzvd.onStatePlaying()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                jzvd.onStatePreparingPlaying()
                onBufferingUpdate()
            }

            Player.STATE_READY -> {
                jzvd.onStatePlaying()
            }

            Player.STATE_ENDED -> {
                jzvd.onCompletion()
            }

            else -> {
                Log.e(TAG, "onPlaybackStateChanged: $playbackState")
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "onPlayerError: $error")
        mMediaHandler?.post { jzvd.onError(1000, 1000) }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            jzvd.onSeekComplete()
        }
    }

    override fun setSurface(surface: Surface?) {
        Log.e(TAG, "setSurface: ", )
        _exoPlayer?.setVideoSurface(surface)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceTextureAvailable: $SAVED_SURFACE $width $height")
        if (SAVED_SURFACE == null) {
            SAVED_SURFACE = surface
            prepare()
        } else {
            jzvd.textureView.setSurfaceTexture(SAVED_SURFACE)
        }
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit


    private fun onBufferingUpdate() {
        _exoPlayer?.bufferedPercentage?.let { per ->
            jzvd.setBufferProgress(per)
//                if (per < 100) {
//                    mMediaHandler.postDelayed(this, 300)
//                } else {
//                    mMediaHandler.removeCallbacks(this)
//                }
        }
//            mMediaHandler.removeCallbacks(this)
    }

    override val width: Int get() = _exoPlayer?.videoSize?.width ?: 0
    override val height: Int get() = _exoPlayer?.videoSize?.height ?: 0
}

class SystemMediaKernel(jzvd: Jzvd) : JZMediaSystem(jzvd), HMediaKernel, IMedia {
    // #issue-26: 有的手機長按快進會報錯，合理懷疑是不是因爲沒有加 post
    // #issue-28: 有的平板长按快进也会报错，结果是 IllegalArgumentException，很奇怪，两次 try-catch 处理试试。
    override fun setSpeed(speed: Float) {
        mMediaHandler?.post {
            try {
                val pp = mediaPlayer.playbackParams
                pp.speed = speed.absoluteValue
                mediaPlayer.playbackParams = pp
            } catch (_: IllegalArgumentException) {
                try {
                    val opp = PlaybackParams().setSpeed(speed.absoluteValue)
                    mediaPlayer.playbackParams = opp
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onVideoSizeChanged(mediaPlayer: MediaPlayer?, width: Int, height: Int) {
        super.onVideoSizeChanged(mediaPlayer, width, height)
        val ratio = width.toFloat() / height // > 1 橫屏， < 1 竖屏
        if (ratio > 1) {
            Jzvd.FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            Jzvd.FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // #issue-139: 部分机型暂停报错，没判空导致的
    override fun pause() {
        mMediaHandler?.post {
            mediaPlayer?.pause()
        }
    }

    // #issue-crashlytics-c8636c4bb0b8516675cbeb9e8776bf0b:
    // 有些机器到这里可能会报空指针异常，所以加了个判断，但是不知道为什么会报空指针异常
    override fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    override val width: Int get() = mediaPlayer?.videoWidth ?: 0
    override val height: Int get() = mediaPlayer?.videoHeight ?: 0
}

class MpvMediaKernel(jzvd: Jzvd) : JZMediaInterface(jzvd), HMediaKernel, IMedia {
    companion object {
        const val TAG = "MpvMediaKernel"
    }

    fun init() {

        MPVLib.addObserver(mpvEventObserver)

        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("hwdec", "auto")
        MPVLib.setOptionString("msg-level", "all=" + if (BuildConfig.DEBUG) "v" else "warn")

        MPVLib.observeProperty("time-pos", MPVLib.mpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.mpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("playback-active", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("video-params/w", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("video-params/h", MPVLib.mpvFormat.MPV_FORMAT_INT64)
    }

    override fun prepare() {
        init()
        handler = Handler(Looper.getMainLooper())

        val url = jzvd.jzDataSource.currentUrl.toString()
        if (url.isEmpty()) {
            Log.e(TAG, "视频链接为空")
            return
        }

        Log.e(TAG, "URL Link = $url")
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.command(arrayOf("loadfile", url))

        val surfaceTexture = jzvd.textureView?.surfaceTexture
        surfaceTexture?.let { MPVLib.attachSurface(Surface(it)) }
    }

    override fun start() {
        MPVLib.setPropertyBoolean("pause", false)
    }

    override fun pause() {
        MPVLib.setPropertyBoolean("pause", true)
    }

    override fun isPlaying(): Boolean {
        val pause = MPVLib.getPropertyBoolean("pause")
        return !pause
    }

    override fun seekTo(time: Long) {
        MPVLib.command(arrayOf("seek", (time / 1000.0).toString(), "absolute"))
    }

    override fun release() {
        Log.d(TAG, "release")
        clearSuperResolution()
        MPVLib.setPropertyBoolean("pause", true)
        MPVLib.command(arrayOf("loadfile", "", "replace"))
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setOptionString("force-window", "no")
        MPVLib.detachSurface()
        MPVLib.removeObserver(mpvEventObserver)
        SAVED_SURFACE = null
    }

    override fun getCurrentPosition(): Long {
        val timePosSeconds = MPVLib.getPropertyDouble("time-pos")
        return (timePosSeconds ?: 0.0).toLong() * 1000
    }

    override fun getDuration(): Long {
        val durationSeconds = MPVLib.getPropertyDouble("duration")
        return (durationSeconds ?: 0.0).toLong() * 1000
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        val volume = (leftVolume + rightVolume) / 2 * 100
        MPVLib.setPropertyDouble("volume", volume.toDouble())
    }

    override fun setSpeed(speed: Float) {
        MPVLib.setPropertyDouble("speed", speed.toDouble())
    }

    override fun setSurface(surface: Surface?) {
        MPVLib.attachSurface(surface)
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        if (SAVED_SURFACE == null) {
            SAVED_SURFACE = surfaceTexture
            prepare()
        } else {
            jzvd.textureView.setSurfaceTexture(SAVED_SURFACE)
        }
    }

    fun updateSurFaceSize(width: Int, height: Int) {
        Log.d(TAG, "updateSurFaceSize ${width}x${height}")
        MPVLib.setPropertyString("android-surface-size", "${width}x${height}")
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceTextureSizeChanged ${width}x${height}")
        updateSurFaceSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean = false

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}


    private fun clearSuperResolution() {
        MPVLib.command(arrayOf("change-list", "glsl-shaders", "clr", ""))
    }
    fun setSuperResolution(index: Int) {
        if (index != 0) {
            val cmd = arrayOf("change-list", "glsl-shaders", "set", AnimeShaders.getShader(jzvd.context, index))
            MPVLib.command(cmd)
        } else {
            clearSuperResolution()
        }
    }

    private val mpvEventObserver = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {
//            Log.d(TAG, "eventProperty: $property")
        }

        override fun eventProperty(property: String, value: Long) {

        }

        override fun eventProperty(property: String, value: Boolean) {
//            Log.d(TAG, "eventProperty: $property $value")
        }

        override fun eventProperty(property: String, value: String) {
//            Log.d(TAG, "eventProperty: $property $value")
        }

        override fun eventProperty(property: String, value: Double) {
//            Log.d(TAG, "eventProperty: $property $value")
        }

        override fun event(eventId: Int) {
            handler.post {
                when (eventId) {
                    MPVLib.mpvEventId.MPV_EVENT_START_FILE -> {
                        // 文件开始加载
                        jzvd.onStatePreparing()
                    }
                    MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
                        // 文件加载成功
                        jzvd.onPrepared()
                    }
                    MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> {
                        // 播放重新开始
                        jzvd.onStatePlaying()
                    }
                    MPVLib.mpvEventId.MPV_EVENT_END_FILE -> {
                        // 播放结束
                        jzvd.onCompletion()
                    }
                    MPVLib.mpvEventId.MPV_EVENT_SHUTDOWN -> {
                        Log.e(TAG, "event: $eventId")
                    }
                }
            }
        }
    }

    override val width: Int get() = MPVLib.getPropertyInt("video-params/w") ?: 0
    override val height: Int get() = MPVLib.getPropertyInt("video-params/h") ?: 0
}
