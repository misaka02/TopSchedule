package com.topware.timetable.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.topware.timetable.R
import com.topware.timetable.data.repository.ScheduleRepository
import com.topware.timetable.ui.floating.FloatingScheduleActivity
import com.topware.timetable.util.TimeUtils

class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val repository = ScheduleRepository.getInstance(context)
        val config = repository.getSemesterConfig()
        val currentWeek = TimeUtils.getCurrentWeek(config)
        val currentDay = TimeUtils.getCurrentDayOfWeek()
        val dayName = TimeUtils.getDayName(currentDay)

        val todayCourses = repository.getCoursesForDay(currentWeek, currentDay)

        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule_layout)
            views.setTextViewText(R.id.widgetDate, "第 $currentWeek 周 · $dayName")

            if (todayCourses.isEmpty()) {
                views.setTextViewText(R.id.widgetCourseContent, "今日无课程安排")
            } else {
                val sb = StringBuilder()
                for ((idx, c) in todayCourses.withIndex()) {
                    sb.append("${idx + 1}. [${c.jieci}节] ${c.name} @ ${c.location}\n")
                }
                views.setTextViewText(R.id.widgetCourseContent, sb.toString().trimEnd())
            }

            val intent = Intent(context, FloatingScheduleActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
