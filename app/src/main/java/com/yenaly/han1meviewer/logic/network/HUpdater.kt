package com.yenaly.han1meviewer.logic.network

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.FirebaseConstants
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.han1meviewer.logic.entity.download.HUpdateEntity
import com.yenaly.han1meviewer.logic.model.github.CommitComparison
import com.yenaly.han1meviewer.logic.model.github.Latest
import com.yenaly.han1meviewer.logic.state.DownloadState
import com.yenaly.han1meviewer.util.UPDATE_ZIP_PATH
import com.yenaly.han1meviewer.util.checkNeedUpdate
import com.yenaly.han1meviewer.util.copyTo
import com.yenaly.han1meviewer.util.runSuspendCatching
import com.yenaly.yenaly_libs.utils.applicationContext
import okio.use
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2024/03/21 021 08:28
 */
object HUpdater {

    const val TAG = "HUpdater"

    const val DEFAULT_BRANCH = "master"

    /**
     * Regex to match multiple line feeds to a single line feed
     */
    private val linefeedRegex = Regex("\\n{2,}")

    private val hUpdateDao = DownloadDatabase.instance.hUpdateDao

    /**
     * Check for update
     *
     * @param forceCheck force check
     */
    suspend fun checkForUpdate(forceCheck: Boolean = false): Latest? {
        // 如果未设置HA1_GITHUB_TOKEN，则不检测版本更新
        if (BuildConfig.HA1_GITHUB_TOKEN.isEmpty()) return null
        // debug模式不检测更新
//        if (BuildConfig.DEBUG) return null
        if (forceCheck || Preferences.isUpdateDialogVisible) {
            if (Preferences.useCIUpdateChannel && Firebase.remoteConfig.getBoolean(FirebaseConstants.ENABLE_CI_UPDATE)) {
                val curSha = BuildConfig.COMMIT_SHA
                // 特殊情况下才用注释部分，一般情况下 branch 都是固定的，要不然多一次
                // request 会对我的 API Token 造成负担。
                // val apiReq = request(HA1_GITHUB_API_URL)
                // val branch = apiReq.body?.string()?.let(::JSONObject)?.getString("default_branch")
                //     ?: return null
                val workflowRun = HanimeNetwork.githubService.getWorkflowRuns()
                    .workflowRuns.firstOrNull() ?: return null
                val shortSha = workflowRun.headSha.take(7)
                if (shortSha != curSha) {
                    val artifacts =
                        HanimeNetwork.githubService.getArtifacts(workflowRun.artifactsUrl)
                    val archiveUrl = artifacts.downloadLink
                    val nodeId = artifacts.nodeId
                    val changelog = runSuspendCatching {
                        HanimeNetwork.githubService.getCommitComparison(
                            curSha = curSha,
                            latestSha = shortSha
                        ).commits.toChangelogPrettyString()
                    }.getOrNull() ?: workflowRun.title
                    return Latest("$shortSha (CI)", changelog, archiveUrl, nodeId)
                }
            } else {
                val ver = HanimeNetwork.githubService.getLatestVersion()
                val isNeeded = checkNeedUpdate(ver.name)
                if (isNeeded) {
                    return Latest(
                        ver.tagName, ver.body,
                        ver.asset.browserDownloadURL,
                        ver.asset.nodeID
                    )
                }
            }
        }
        return null
    }

    suspend fun updateHUpdateLength(entity: HUpdateEntity, length: Long): HUpdateEntity {
        val newEntity = entity.copy(
            length = length
        )
        hUpdateDao.update(newEntity)
        return newEntity
    }
    suspend fun updateHUpdateDownloadedLength(entity: HUpdateEntity, downloadedLength: Long) {
        hUpdateDao.update(entity.copy(
            downloadedLength = downloadedLength,
            state = DownloadState.Downloading
        ))
    }

    /**
     * Inject update to file
     *
     * @param url update url
     */
    suspend fun File.injectUpdate(url: String, startOffset: Long = 0L, progress: (suspend (Int) -> Unit)? = null) {
        var entity = hUpdateDao.get()
        val res = HanimeNetwork.githubService.request(url, if (startOffset > 0) "bytes=$startOffset-" else null)
        if (url.endsWith("zip")) {
            Log.d(TAG, "Injecting update from zip ($url)")
            FileOutputStream(UPDATE_ZIP_PATH, true).use {
                res.body()?.use { body ->
                    val len = body.contentLength() + startOffset
                    entity?.let { it -> entity = updateHUpdateLength(it, len) }
                    body.byteStream().copyTo(
                        it,
                        len,
                        startOffset,
                        progress = progress,
                        downloadLength = { length ->
                            entity?.let { it1 ->
                                updateHUpdateDownloadedLength(it1, length)
                            }
                        }
                    )
                }
            }
            ZipFile(UPDATE_ZIP_PATH).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory) {
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(this, true).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } else {
            Log.d(TAG, "Injecting update from release ($url)")
            FileOutputStream(this, true).use {
                if (startOffset > 0) {
                    it.channel.position(startOffset)
                }
                res.body()?.use { body ->
                    val len = body.contentLength() + startOffset
                    entity?.let { it -> entity = updateHUpdateLength(it, len) }
                    body.byteStream().copyTo(
                        it,
                        len,
                        startOffset,
                        progress = progress,
                        downloadLength = { length ->
                            entity?.let { it1 ->
                                updateHUpdateDownloadedLength(it1, length)
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * This function is used to filter out commits that are not authored by the user.
     */
    private val CommitComparison.Commit.CommitDetail.CommitAuthor.isAuthorShouldIgnore: Boolean
        get() = name.contains("dependabot")

    private fun List<CommitComparison.Commit>.toChangelogPrettyString(): String {
        return filterNot { commit ->
            commit.commit.author.isAuthorShouldIgnore
        }.distinct().reversed().joinToString("\n\n") { commit ->
            val message = commit.commit.message.replace(linefeedRegex, "\n")
            "↓ (@${commit.commit.author.name})\n$message"
        }
    }
}