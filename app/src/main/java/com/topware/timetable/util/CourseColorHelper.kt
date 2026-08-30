package com.topware.timetable.util

import android.graphics.Color

object CourseColorHelper {

    data class CourseColor(
        val bg: Int,
        val text: Int,
        val border: Int
    )

    private val PALETTE = listOf(
        CourseColor(Color.parseColor("#E3F2FD"), Color.parseColor("#1565C0"), Color.parseColor("#90CAF9")), // Blue
        CourseColor(Color.parseColor("#F3E5F5"), Color.parseColor("#6A1B9A"), Color.parseColor("#CE93D8")), // Purple
        CourseColor(Color.parseColor("#E8F5E9"), Color.parseColor("#2E7D32"), Color.parseColor("#A5D6A7")), // Green
        CourseColor(Color.parseColor("#FFF3E0"), Color.parseColor("#E65100"), Color.parseColor("#FFCC80")), // Orange
        CourseColor(Color.parseColor("#FFEBEE"), Color.parseColor("#C62828"), Color.parseColor("#EF9A9A")), // Red
        CourseColor(Color.parseColor("#E0F2F1"), Color.parseColor("#00695C"), Color.parseColor("#80CBC4")), // Teal
        CourseColor(Color.parseColor("#E8EAF6"), Color.parseColor("#283593"), Color.parseColor("#9FA8DA")), // Indigo
        CourseColor(Color.parseColor("#FFFDE7"), Color.parseColor("#F57F17"), Color.parseColor("#FFF59D")), // Amber
        CourseColor(Color.parseColor("#FCE4EC"), Color.parseColor("#AD1457"), Color.parseColor("#F48FB1"))  // Pink
    )

    fun getColor(index: Int): CourseColor {
        val i = Math.abs(index) % PALETTE.size
        return PALETTE[i]
    }
}
