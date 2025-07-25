package com.yenaly.han1meviewer.ui.fragment.settings

import android.os.Bundle
import android.util.Log
import androidx.preference.SeekBarPreference
import com.yenaly.han1meviewer.HFileManager
import com.yenaly.han1meviewer.HFileManager.appDownloadFolder
import com.yenaly.han1meviewer.HFileManager.appExternalDownloadFolder
import com.yenaly.han1meviewer.HFileManager.getDownloadFolder
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.network.interceptor.SpeedLimitInterceptor
import com.yenaly.han1meviewer.ui.activity.SettingsActivity
import com.yenaly.han1meviewer.ui.fragment.IToolbarFragment
import com.yenaly.han1meviewer.ui.fragment.settings.NetworkSettingsFragment.Companion.USE_BUILT_IN_HOSTS
import com.yenaly.han1meviewer.util.setSummaryConverter
import com.yenaly.han1meviewer.util.showAlertDialog
import com.yenaly.han1meviewer.worker.HanimeDownloadManagerV2
import com.yenaly.yenaly_libs.base.preference.LongClickablePreference
import com.yenaly.yenaly_libs.base.preference.MaterialSwitchPreference
import com.yenaly.yenaly_libs.base.settings.YenalySettingsFragment
import com.yenaly.yenaly_libs.utils.copyToClipboard
import com.yenaly.yenaly_libs.utils.formatBytesPerSecond
import com.yenaly.yenaly_libs.utils.mapToArray
import com.yenaly.yenaly_libs.utils.showShortToast

class DownloadSettingsFragment : YenalySettingsFragment(R.xml.settings_download),
    IToolbarFragment<SettingsActivity> {

    companion object {
        const val DOWNLOAD_PATH = "download_path"
        const val DOWNLOAD_COUNT_LIMIT = "download_count_limit"
        const val DOWNLOAD_SPEED_LIMIT = "download_speed_limit"
        const val USE_PRIVATE_DIRECTORY = "use_private_directory"
    }

    private val downloadPath
            by safePreference<LongClickablePreference>(DOWNLOAD_PATH)
    private val downloadCountLimit
            by safePreference<SeekBarPreference>(DOWNLOAD_COUNT_LIMIT)
    private val downloadSpeedLimit
            by safePreference<SeekBarPreference>(DOWNLOAD_SPEED_LIMIT)

    private val usePrivateDirectory
            by safePreference<MaterialSwitchPreference>(USE_PRIVATE_DIRECTORY)

    override fun onStart() {
        super.onStart()
        (activity as SettingsActivity).setupToolbar()
    }

    override fun onPreferencesCreated(savedInstanceState: Bundle?) {
        downloadPath.apply {
            val path = getDownloadFolder().path
            summary = path
            setOnPreferenceClickListener {
                requireContext().showAlertDialog {
                    setTitle(R.string.not_allow_to_change)
                    setMessage(
                        getString(R.string.detailed_path_s, path) + "\n"
                                + getString(R.string.long_press_pref_to_copy)
                    )
                    setPositiveButton(R.string.ok, null)
                }

                return@setOnPreferenceClickListener true
            }
            setOnPreferenceLongClickListener {
                path.copyToClipboard()
                showShortToast(R.string.copy_to_clipboard)
                return@setOnPreferenceLongClickListener true
            }
        }
        downloadCountLimit.apply {
            setSummaryConverter(
                defValue = HanimeDownloadManagerV2.MAX_CONCURRENT_DOWNLOAD_DEF,
                converter = ::toDownloadCountLimitPrettyString
            ) {
                // HanimeDownloadManager.maxConcurrentDownloadCount = it
                HanimeDownloadManagerV2.maxConcurrentDownloadCount = it
            }
        }
        downloadSpeedLimit.apply {
            min = 0
            max = SpeedLimitInterceptor.SPEED_BYTES.lastIndex
            setSummaryConverter(defValue = SpeedLimitInterceptor.NO_LIMIT_INDEX, converter = { i ->
                SpeedLimitInterceptor.SPEED_BYTES[i].toDownloadSpeedPrettyString()
            })
        }
        usePrivateDirectory.setOnPreferenceChangeListener { _, newValue ->
            val usePrivate = newValue as Boolean
            downloadPath.summary = if (usePrivate) appDownloadFolder.path else appExternalDownloadFolder.path
            return@setOnPreferenceChangeListener true
        }
    }

    private fun Long.toDownloadSpeedPrettyString(): String {
        return if (this == 0L) {
            getString(R.string.no_limit)
        } else {
            this.formatBytesPerSecond()
        }
    }

    private fun toDownloadCountLimitPrettyString(value: Int): String {
        return if (value == 0) {
            getString(R.string.no_limit)
        } else {
            value.toString()
        }
    }

    override fun SettingsActivity.setupToolbar() {
        supportActionBar!!.setTitle(R.string.download_settings)
    }
}