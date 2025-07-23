package com.yenaly.han1meviewer.worker

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.UPDATE_NOTIFICATION_CHANNEL
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.han1meviewer.logic.entity.download.HUpdateEntity
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.logic.network.HUpdater
import com.yenaly.han1meviewer.logic.state.DownloadState
import com.yenaly.han1meviewer.util.UPDATE_ZIP_PATH
import com.yenaly.han1meviewer.util.installApkPackage
import com.yenaly.han1meviewer.util.runSuspendCatching
import com.yenaly.han1meviewer.util.updateFile
import com.yenaly.yenaly_libs.utils.applicationContext
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.internal.closeQuietly
import java.util.concurrent.CancellationException
import kotlin.random.Random

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2024/03/22 022 21:27
 */
class HUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams), WorkerMixin {
    companion object {
        const val TAG = "HUpdateWorker"
        private val workManager = WorkManager.getInstance(applicationContext)
        private val hUpdateDao = DownloadDatabase.instance.hUpdateDao

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mainScope = CoroutineScope(Dispatchers.Main.immediate)

        const val DOWNLOAD_LINK = "download_link"
        const val START_OFFSET = "start_offset"
        const val NODE_ID = "node_id"
        const val UPDATE_APK = "update_apk"

        fun deleteUpdate(context: Context) {
            deleteUpdateFile(context)
            // 删除数据库中的更新记录
            scope.launch {
                hUpdateDao.delete()
            }
        }

        fun deleteUpdateFile(context: Context) {
            context.updateFile.delete()
            UPDATE_ZIP_PATH.delete()
        }
        /**
         * This function is used to enqueue a download task
         */
        suspend fun enqueue(context: Context, latest: Latest) {
            deleteUpdateFile(context)

            val entity = HUpdateEntity(
                name = latest.version,
                url = latest.downloadLink,
                nodeId = latest.nodeId,
                length = 1,
                downloadedLength = 0,
                state = DownloadState.Queued,
            )
            hUpdateDao.insert(entity)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val data = workDataOf(
                DOWNLOAD_LINK to latest.downloadLink,
                NODE_ID to latest.nodeId,
                START_OFFSET to entity.downloadedLength
            )
            val req = OneTimeWorkRequestBuilder<HUpdateWorker>()
                .addTag(TAG)
                .setConstraints(constraints)
                .setInputData(data)
                .build()
            workManager
                .beginUniqueWork(TAG, ExistingWorkPolicy.REPLACE, req)
                .enqueue()
        }

        fun resume() {
            scope.launch {
                val entity = hUpdateDao.get() as HUpdateEntity
                val data = workDataOf(
                    DOWNLOAD_LINK to entity.url,
                    NODE_ID to entity.nodeId,
                    START_OFFSET to entity.downloadedLength
                )
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val req = OneTimeWorkRequestBuilder<HUpdateWorker>()
                    .addTag(TAG)
                    .setConstraints(constraints)
                    .setInputData(data)
                    .build()
                workManager
                    .beginUniqueWork(TAG, ExistingWorkPolicy.REPLACE, req)
                    .enqueue().await()
            }
        }

        fun stop() {
            scope.launch {
                runSuspendCatching {
                    workManager.cancelUniqueWork(TAG).await()
                }.onFailure { t -> // 上述方法可能无法取消任务
                    t.printStackTrace()
                    // 通过替换任务实现任务删除（文件不删除），确保 WorkManager 真正取消
                    val deleteRequest =
                        OneTimeWorkRequestBuilder<HanimeDownloadWorker>()
                            .addTag(TAG)
                            .setInputData(workDataOf(HanimeDownloadWorker.FAST_PATH_CANCEL to true))
                            .build()
                    workManager.beginUniqueWork(
                        TAG, ExistingWorkPolicy.REPLACE, deleteRequest
                    ).enqueue().await()
                }
            }
        }

        /**
         * This function is used to collect the output of the download task
         */
        suspend fun collectOutput(context: Context) = WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(TAG)
            .collect { workInfos ->
                // 只有一個！
                val workInfo = workInfos.firstOrNull()
                workInfo?.let {
                    when (it.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            val apkPath = it.outputData.getString(UPDATE_APK)
                            val file = apkPath?.toUri()?.toFile()
                            file?.let { context.installApkPackage(file) }
                        }

                        WorkInfo.State.FAILED -> {
                            showShortToast(R.string.update_failed)
                        }

                        else -> Unit
                    }
                }
            }
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    private val downloadLink by inputData(DOWNLOAD_LINK, EMPTY_STRING)
    private val startOffset by inputData(START_OFFSET, 0L)
    private val nodeId by inputData(NODE_ID, EMPTY_STRING)
    private val downloadId = Random.nextInt()

    override suspend fun doWork(): Result {
        with(HUpdater) {
            var result: Result = Result.failure()
            val file = context.updateFile
            try {
                setForeground(createForegroundInfo())
                Log.i(TAG, "doWork: $startOffset")
                file.injectUpdate(downloadLink, startOffset) { progress ->
                    updateNotification(progress)
                }
                val outputData = workDataOf(
                    UPDATE_APK to file.toUri().toString(),
                    DownloadState.STATE to DownloadState.Finished.mask
                )
                Preferences.updateNodeId = nodeId
                result =  Result.success(outputData)
            } catch (e: Exception) {
                result = if (e is CancellationException) {
                    // cancellation exception block 是代表用户暂停
//                    cancelDownloadNotification()
                    Result.success(
                        workDataOf(DownloadState.STATE to DownloadState.Paused.mask)
                    )
                } else {
//                    showFailureNotification(e.localizedMessage)
                    e.printStackTrace()
                    mainScope.launch {
                        showShortToast(e.localizedMessage)
                    }
                    file.delete()
                    Result.failure(
                        workDataOf(DownloadState.STATE to DownloadState.Failed.mask)
                    )
                }
            } finally {
                val state = DownloadState.from(
                    result.outputData.getInt(DownloadState.STATE, DownloadState.Unknown.mask)
                )
                // 为什么要用 dbScope 包住？
                // 使用 dbScope 是为了确保即使当前协程因任务取消而失效，
                // “update”挂起函数仍然能够找到有效的协程作用域来更新数据库。
                // 这也是一个历史遗留问题。
                scope.launch {
                    hUpdateDao.updateState(state)
                }
            }
            return result
        }
    }

    private fun createNotification(progress: Int = 0): Notification {
        return NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentTitle(context.getString(R.string.downloading_update_percent, progress))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(progress: Int) {
        notificationManager.notify(downloadId, createNotification(progress))
    }

    private fun createForegroundInfo(progress: Int = 0): ForegroundInfo {
        return ForegroundInfo(
            downloadId,
            createNotification(progress),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
        )
    }
}