package com.yenaly.han1meviewer.ui.activity

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cn.jzvd.JZMediaInterface
import cn.jzvd.Jzvd
import cn.jzvd.Jzvd.goOnPlayOnPause
import coil.load
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import com.yenaly.han1meviewer.COMMENT_TYPE
import com.yenaly.han1meviewer.FROM_DOWNLOAD
import com.yenaly.han1meviewer.FirebaseConstants
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.VIDEO_CODE
import com.yenaly.han1meviewer.VIDEO_COMMENT_PREFIX
import com.yenaly.han1meviewer.databinding.ActivityVideoBinding
import com.yenaly.han1meviewer.getHanimeVideoLink
import com.yenaly.han1meviewer.logic.entity.HKeyframeEntity
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.han1meviewer.logic.exception.ParseException
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.ui.fragment.video.CommentFragment
import com.yenaly.han1meviewer.ui.fragment.video.VideoIntroductionFragment
import com.yenaly.han1meviewer.ui.view.video.HMediaKernel
import com.yenaly.han1meviewer.ui.view.video.HanimeDataSource
import com.yenaly.han1meviewer.ui.viewmodel.CommentViewModel
import com.yenaly.han1meviewer.ui.viewmodel.VideoViewModel
import com.yenaly.han1meviewer.util.getOrCreateBadgeOnTextViewAt
import com.yenaly.han1meviewer.util.logScreenViewEvent
import com.yenaly.han1meviewer.util.showAlertDialog
import com.yenaly.yenaly_libs.base.YenalyActivity
import com.yenaly.yenaly_libs.utils.OrientationManager
import com.yenaly.yenaly_libs.utils.activity
import com.yenaly.yenaly_libs.utils.browse
import com.yenaly.yenaly_libs.utils.dp
import com.yenaly.yenaly_libs.utils.intentExtra
import com.yenaly.yenaly_libs.utils.makeBundle
import com.yenaly.yenaly_libs.utils.showShortToast
import com.yenaly.yenaly_libs.utils.startActivity
import com.yenaly.yenaly_libs.utils.view.attach
import com.yenaly.yenaly_libs.utils.view.setUpFragmentStateAdapter
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class VideoActivity : YenalyActivity<ActivityVideoBinding>(),
    OrientationManager.OrientationChangeListener {
    companion object {
        private const val ACTION_PLAY = "com.yenaly.han1meviewer.ACTION_PLAY"
        private const val ACTION_PAUSE = "com.yenaly.han1meviewer.ACTION_PAUSE"
    }

    val viewModel by viewModels<VideoViewModel>()
    private val commentViewModel by viewModels<CommentViewModel>()

    private val kernel = HMediaKernel.Type.fromString(Preferences.switchPlayerKernel)

    private val fromDownload by intentExtra(FROM_DOWNLOAD, false)
    private val videoCode by intentExtra<String>(VIDEO_CODE)
    private var videoTitle: String? = null
    private var videoCodeByWebsite: String? = null

    private var builder: PictureInPictureParams.Builder? = null

    private val tabNameArray = intArrayOf(R.string.introduction, R.string.comment)

    override fun getViewBinding(layoutInflater: LayoutInflater): ActivityVideoBinding =
        ActivityVideoBinding.inflate(layoutInflater)

    override val onFragmentResumedListener: (Fragment) -> Unit = { fragment ->
        logScreenViewEvent(fragment)
    }

    override fun setUiStyle() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
    }

    override fun initData(savedInstanceState: Bundle?) {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            uri?.let {
                videoCodeByWebsite = it.getQueryParameter("v")
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (Jzvd.backPress()) {
                return@addCallback
            }
            finish()
        }

        // 必须 fromDownload 在前，videoCode 在后
        viewModel.fromDownload = fromDownload
        requireNotNull(videoCodeByWebsite ?: videoCode).let {
            viewModel.videoCode = it
            commentViewModel.code = it
            binding.videoPlayer.videoCode = it
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.videoPlayer) { v, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updateLayoutParams<MarginLayoutParams> {
                topMargin = navBar.top
            }
            WindowInsetsCompat.CONSUMED
        }

        lifecycle.addObserver(OrientationManager(this))
        initViewPager()
        initHKeyframe()
        if (Preferences.isPipAllowed) initPIP()
    }

    override fun bindDataObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.hanimeVideoStateFlow.collect { state ->
                    when (state) {
                        is VideoLoadingState.Error -> {
                            showShortToast(state.throwable.localizedMessage)
                            if (state.throwable is ParseException) {
                                browse(getHanimeVideoLink(viewModel.videoCode))
                            }
                            finish()
                        }

                        is VideoLoadingState.Loading -> {

                        }

                        is VideoLoadingState.Success -> {
                            videoTitle = state.info.title

                            if (state.info.videoUrls.isEmpty()) {
                                binding.videoPlayer.startButton.setOnClickListener {
                                    showShortToast(R.string.fail_to_get_video_link)
                                    browse(getHanimeVideoLink(viewModel.videoCode))
                                }
                            } else {
                                binding.videoPlayer.setUp(
                                    HanimeDataSource(state.info.title, state.info.videoUrls),
                                    Jzvd.SCREEN_NORMAL, kernel
                                )
                            }
                            binding.videoPlayer.posterImageView.load(state.info.coverUrl) {
                                crossfade(true)
                            }
                            // 將觀看記錄保存數據庫
                            if (!fromDownload) {
                                val entity = WatchHistoryEntity(
                                    state.info.coverUrl,
                                    state.info.title,
                                    state.info.uploadTimeMillis,
                                    Clock.System.now().toEpochMilliseconds(),
                                    viewModel.videoCode
                                )
                                viewModel.insertWatchHistoryWithCover(entity)
                            }
                        }

                        is VideoLoadingState.NoContent -> {
                            showShortToast(R.string.video_might_not_exist)
                            finish()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.observeKeyframe(viewModel.videoCode)?.flowWithLifecycle(lifecycle)?.collect {
                binding.videoPlayer.hKeyframe = it
                viewModel.hKeyframes = it
            }
        }

        lifecycleScope.launch {
            viewModel.modifyHKeyframeFlow.collect { (_, reason) ->
                showShortToast(reason)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        goOnPlayOnPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        Jzvd.releaseAllVideos()
        unregisterReceiver()
    }

    override fun onOrientationChanged(orientation: OrientationManager.ScreenOrientation) {
        if (Jzvd.CURRENT_JZVD != null
            && (binding.videoPlayer.state == Jzvd.STATE_PLAYING || binding.videoPlayer.state == Jzvd.STATE_PAUSE)
            && binding.videoPlayer.screen != Jzvd.SCREEN_TINY
            && Jzvd.FULLSCREEN_ORIENTATION != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        ) {
            if (orientation.isLandscape && binding.videoPlayer.screen == Jzvd.SCREEN_NORMAL) {
                changeScreenFullLandscape(orientation)
            } else if (orientation === OrientationManager.ScreenOrientation.PORTRAIT
                && binding.videoPlayer.screen == Jzvd.SCREEN_FULLSCREEN
            ) {
                changeScreenNormal()
            }
        }
    }

    /**
     * 竖屏并退出全屏
     */
    private fun changeScreenNormal() {
        if (binding.videoPlayer.screen == Jzvd.SCREEN_FULLSCREEN) {
            binding.videoPlayer.gotoNormalScreen()
        }
    }

    /**
     * 横屏
     */
    private fun changeScreenFullLandscape(orientation: OrientationManager.ScreenOrientation) {
        //从竖屏状态进入横屏
        if (binding.videoPlayer.screen != Jzvd.SCREEN_FULLSCREEN) {
            if (System.currentTimeMillis() - Jzvd.lastAutoFullscreenTime > 2000) {
                binding.videoPlayer.autoFullscreen(orientation)
                Jzvd.lastAutoFullscreenTime = System.currentTimeMillis()
            }
        }
    }

    private fun initViewPager() {
        binding.videoVp.offscreenPageLimit = 1

        binding.videoVp.setUpFragmentStateAdapter(this) {
            addFragment { VideoIntroductionFragment() }
            if (!fromDownload) {
                addFragment { CommentFragment().makeBundle(COMMENT_TYPE to VIDEO_COMMENT_PREFIX) }
            }
        }

        binding.videoTl.attach(binding.videoVp) { tab, position ->
            tab.setText(tabNameArray[position])
        }
    }

    private fun initHKeyframe() {
        binding.videoPlayer.onGoHomeClickListener = { _ ->
            // singleTask 直接把所有 VideoActivity 都 finish 掉
            startActivity<MainActivity>()
        }
        binding.videoPlayer.onKeyframeClickListener = { v ->
            binding.videoPlayer.clickHKeyframe(v)
        }
        binding.videoPlayer.onKeyframeLongClickListener = {
            val mi: JZMediaInterface? = binding.videoPlayer.mediaInterface
            if (mi != null && !mi.isPlaying) {
                val currentPosition = binding.videoPlayer.currentPositionWhenPlaying
                it.context.showAlertDialog {
                    setTitle(R.string.add_to_h_keyframe)
                    setMessage(buildString {
                        appendLine(getString(R.string.sure_to_add_to_h_keyframe))
                        append(getString(R.string.current_position_d_ms, currentPosition))
                    })
                    setPositiveButton(R.string.confirm) { _, _ ->
                        viewModel.appendHKeyframe(
                            viewModel.videoCode,
                            videoTitle ?: "Untitled",
                            HKeyframeEntity.Keyframe(
                                position = currentPosition,
                                prompt = null // 這裏不要給太多負擔，保存就行了沒必要寫comment
                            )
                        )
                        // 使用到这里说明用户可能是关键H帧目标用户
                        Firebase.analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT) {
                            param(
                                FirebaseAnalytics.Param.ITEM_ID,
                                FirebaseConstants.H_KEYFRAMES
                            )
                            param(
                                FirebaseAnalytics.Param.CONTENT_TYPE,
                                FirebaseConstants.H_KEYFRAMES
                            )
                        }
                    }
                    setNegativeButton(R.string.cancel, null)
                }
            } else {
                showShortToast(R.string.pause_then_long_press)
            }
        }
    }

    fun updateVideoPlayerSize(isInPictureInPictureMode: Boolean) {
        if (binding.videoPlayer.layoutParams !is LinearLayout.LayoutParams) return
        if (isInPictureInPictureMode) {
            // 进入画中画模式时，设置视频播放器的布局参数为全屏
            // 避免画中画只显示部分画面.
            val params = binding.videoPlayer.layoutParams as LinearLayout.LayoutParams
            params.width = MATCH_PARENT
            params.height = MATCH_PARENT
            binding.videoPlayer.layoutParams = params
        } else {
            // 恢复原来的布局参数
            val params = binding.videoPlayer.layoutParams as LinearLayout.LayoutParams
            params.width = MATCH_PARENT
            params.height = 250.dp
            binding.videoPlayer.layoutParams = params
        }
    }

    @Deprecated("Deprecated in android.app.Activity")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        // 当全屏状态进入画中画时 videoPlayer.layoutParams 为 FrameLayout.LayoutParams
        updateVideoPlayerSize(isInPictureInPictureMode)
    }

    private val playbackReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PLAY -> binding.videoPlayer.startButton.performClick()
                ACTION_PAUSE -> goOnPlayOnPause()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                updatePIPAction()
            }

            // 暂停时隐藏UI
            binding.videoPlayer.topContainer.isVisible = false
            binding.videoPlayer.bottomContainer.isVisible = false
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
        }
        activity?.let {
            ContextCompat.registerReceiver(
                it,
                playbackReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }
    }
    private fun unregisterReceiver() {
        try {
            unregisterReceiver(playbackReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("VideoActivity", "Receiver not registered: $e")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePIPAction() {
        val (iconRes, titleRes, actionRes) = if (binding.videoPlayer.state == Jzvd.STATE_PLAYING) {
            Triple(R.drawable.pause_circle_24px, R.string.pause, ACTION_PAUSE)
        } else {
            Triple(R.drawable.play_circle_24px, R.string.continues, ACTION_PLAY)
        }
        val pauseIntent = PendingIntent.getBroadcast(
            activity,
            0,
            Intent(actionRes).setPackage(activity?.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val action = RemoteAction(
            Icon.createWithResource(activity, iconRes),
            getString(titleRes),
            getString(titleRes),
            pauseIntent
        )
        builder?.setActions(listOf(action))
    }

    private fun initPIP() {
        registerReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            binding.videoPlayer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val sourceRectHint = Rect()
                binding.videoPlayer.getGlobalVisibleRect(sourceRectHint)
                builder = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .setSourceRectHint(sourceRectHint)
                updatePIPAction()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder?.setAutoEnterEnabled(true)
                }
                setPictureInPictureParams(builder!!.build())
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Preferences.isPipAllowed) {
            enterPictureInPictureMode()
        }
    }

    fun showRedDotCount(count: Int) {
        binding.videoTl.getOrCreateBadgeOnTextViewAt(
            tabNameArray.indexOf(R.string.comment),
            null, Gravity.START, 2.dp
        ) {
            isVisible = count > 0
            number = count
        }
    }
}