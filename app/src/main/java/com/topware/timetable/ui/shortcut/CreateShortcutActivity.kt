package com.topware.timetable.ui.shortcut

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.topware.timetable.R
import com.topware.timetable.ui.floating.FloatingScheduleActivity

class CreateShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shortcutIntent = Intent(this, FloatingScheduleActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val shortcutInfo = ShortcutInfoCompat.Builder(this, "floating_schedule_shortcut")
            .setShortLabel("悬浮课表")
            .setLongLabel("呼出今日与周课表")
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(shortcutIntent)
            .build()

        val resultIntent = ShortcutManagerCompat.createShortcutResultIntent(this, shortcutInfo)
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
