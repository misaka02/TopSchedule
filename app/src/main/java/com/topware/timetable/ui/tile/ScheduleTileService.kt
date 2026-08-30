package com.topware.timetable.ui.tile

import android.content.Intent
import android.service.quicksettings.TileService
import com.topware.timetable.ui.floating.FloatingScheduleActivity

class ScheduleTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, FloatingScheduleActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        unlockAndRun {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
