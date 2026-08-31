package com.topware.timetable.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

object CourseColorHelper {

    data class CourseColor(
        val bg: Int,
        val text: Int,
        val border: Int
    )

    private val LIGHT_PALETTE = listOf(
        CourseColor(Color.parseColor("#E3F2FD"), Color.parseColor("#1565C0"), Color.parseColor("#90CAF9")),
        CourseColor(Color.parseColor("#F3E5F5"), Color.parseColor("#6A1B9A"), Color.parseColor("#CE93D8")),
        CourseColor(Color.parseColor("#E8F5E9"), Color.parseColor("#2E7D32"), Color.parseColor("#A5D6A7")),
        CourseColor(Color.parseColor("#FFF3E0"), Color.parseColor("#E65100"), Color.parseColor("#FFCC80")),
        CourseColor(Color.parseColor("#FFEBEE"), Color.parseColor("#C62828"), Color.parseColor("#EF9A9A")),
        CourseColor(Color.parseColor("#E0F2F1"), Color.parseColor("#00695C"), Color.parseColor("#80CBC4")),
        CourseColor(Color.parseColor("#E8EAF6"), Color.parseColor("#283593"), Color.parseColor("#9FA8DA")),
        CourseColor(Color.parseColor("#FFFDE7"), Color.parseColor("#B45309"), Color.parseColor("#FDE68A"))
    )

    private val DARK_PALETTE = listOf(
        CourseColor(Color.parseColor("#1E293B"), Color.parseColor("#93C5FD"), Color.parseColor("#334155")),
        CourseColor(Color.parseColor("#2E1065"), Color.parseColor("#D8B4FE"), Color.parseColor("#4C1D95")),
        CourseColor(Color.parseColor("#064E3B"), Color.parseColor("#6EE7B7"), Color.parseColor("#047857")),
        CourseColor(Color.parseColor("#451A03"), Color.parseColor("#FDBA74"), Color.parseColor("#7C2D12")),
        CourseColor(Color.parseColor("#4C0519"), Color.parseColor("#FDA4AF"), Color.parseColor("#881337")),
        CourseColor(Color.parseColor("#134E4A"), Color.parseColor("#5EEAD4"), Color.parseColor("#115E59")),
        CourseColor(Color.parseColor("#1E1B4B"), Color.parseColor("#A5B4FC"), Color.parseColor("#312E81")),
        CourseColor(Color.parseColor("#3B2F04"), Color.parseColor("#FDE047"), Color.parseColor("#713F12"))
    )

    fun getColor(context: Context, index: Int): CourseColor {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val palette = if (isDark) DARK_PALETTE else LIGHT_PALETTE
        val safeIndex = ((index % palette.size) + palette.size) % palette.size
        return palette[safeIndex]
    }
}
