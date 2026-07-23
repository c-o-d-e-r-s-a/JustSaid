package com.justsaid.app.data

import android.app.ActivityManager
import android.content.Context
import com.justsaid.app.core.DeviceRamInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Production [DeviceRamInfo] via [ActivityManager.MemoryInfo.totalMem]. */
@Singleton
class DeviceRamInfoImpl @Inject constructor(
    @ApplicationContext context: Context,
) : DeviceRamInfo {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    override fun totalRamBytes(): Long {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info.totalMem
    }
}
