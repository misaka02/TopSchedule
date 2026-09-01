package com.topware.timetable.ui.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.topware.timetable.data.model.Course
import com.topware.timetable.data.model.SemesterConfig
import com.topware.timetable.data.model.TimeSlot
import com.topware.timetable.util.CourseColorHelper
import com.topware.timetable.util.TimeUtils

class TimetableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var courses: List<Course> = emptyList()
    private var displayedWeek: Int = 1
    private var semesterConfig: SemesterConfig = SemesterConfig()
    private var onCourseClickListener: ((Course) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val timeColWidth = 46 * density
    private val headerHeight = 44 * density
    private val rowHeight = 66 * density
    private val totalPeriods = 13
    private val totalDays = 7

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1 * density
    }

    private val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val todayHeaderBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val monthTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12 * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val dayNameTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12 * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val dateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10 * density
        textAlign = Paint.Align.CENTER
    }

    private val periodTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12 * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val timeStartTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9.5f * density
        textAlign = Paint.Align.CENTER
    }

    private val timeEndTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f * density
        textAlign = Paint.Align.CENTER
    }

    private val courseCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val courseCardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }

    private val courseTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11.5f * density
        isFakeBoldText = true
        textAlign = Paint.Align.LEFT
    }

    private val courseLocPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * density
        isFakeBoldText = true
        textAlign = Paint.Align.LEFT
    }

    private val courseTeacherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9.5f * density
        textAlign = Paint.Align.LEFT
    }

    private val courseTimeSpanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f * density
        textAlign = Paint.Align.LEFT
    }

    private val courseRects = mutableListOf<Pair<RectF, Course>>()

    fun setCourses(newCourses: List<Course>, currentWeek: Int = 1, config: SemesterConfig = SemesterConfig()) {
        this.courses = newCourses
        this.displayedWeek = currentWeek
        this.semesterConfig = config
        invalidate()
    }

    fun setOnCourseClickListener(listener: (Course) -> Unit) {
        this.onCourseClickListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val totalHeight = (headerHeight + totalPeriods * rowHeight).toInt()
        setMeasuredDimension(width, totalHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        courseRects.clear()

        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        linePaint.color = if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#EEF0F2")
        headerBgPaint.color = if (isDark) Color.parseColor("#1E1E1E") else Color.parseColor("#F8F9FA")
        todayHeaderBgPaint.color = if (isDark) Color.parseColor("#1E293B") else Color.parseColor("#E0E7FF")

        monthTextPaint.color = if (isDark) Color.parseColor("#93C5FD") else Color.parseColor("#2563EB")
        dayNameTextPaint.color = if (isDark) Color.parseColor("#E0E0E0") else Color.parseColor("#374151")
        dateTextPaint.color = if (isDark) Color.parseColor("#9CA3AF") else Color.parseColor("#6B7280")

        periodTextPaint.color = if (isDark) Color.parseColor("#E5E7EB") else Color.parseColor("#1F2937")
        timeStartTextPaint.color = if (isDark) Color.parseColor("#D1D5DB") else Color.parseColor("#4B5563")
        timeEndTextPaint.color = if (isDark) Color.parseColor("#9CA3AF") else Color.parseColor("#6B7280")

        val w = width.toFloat()
        val dayColWidth = (w - timeColWidth) / totalDays

        val currentActualWeek = TimeUtils.getCurrentWeek(semesterConfig)
        val currentActualDay = TimeUtils.getCurrentDayOfWeek()
        val isCurrentWeek = (displayedWeek == currentActualWeek)

        val weekDates = TimeUtils.getWeekDates(semesterConfig, displayedWeek)
        val monthStr = TimeUtils.getMonthNameForWeek(semesterConfig, displayedWeek)

        // 1. 顶部表头背景与月份
        canvas.drawRect(0f, 0f, w, headerHeight, headerBgPaint)
        canvas.drawText(monthStr, timeColWidth / 2f, headerHeight / 2f + 4 * density, monthTextPaint)

        val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        for (i in 0 until totalDays) {
            val left = timeColWidth + i * dayColWidth
            val centerX = left + dayColWidth / 2f
            val isToday = isCurrentWeek && (i + 1 == currentActualDay)

            // 如果是今天，绘制高亮背景胶囊
            if (isToday) {
                val pad = 3 * density
                val todayRect = RectF(left + pad, 3 * density, left + dayColWidth - pad, headerHeight - 3 * density)
                canvas.drawRoundRect(todayRect, 8 * density, 8 * density, todayHeaderBgPaint)
            }

            val dayTitle = dayNames[i]
            val dateStr = if (i < weekDates.size) {
                val (m, d) = weekDates[i]
                "${m}/${String.format("%02d", d)}"
            } else ""

            if (isToday) {
                dayNameTextPaint.color = if (isDark) Color.parseColor("#60A5FA") else Color.parseColor("#1D4ED8")
                dateTextPaint.color = if (isDark) Color.parseColor("#93C5FD") else Color.parseColor("#2563EB")
            } else {
                dayNameTextPaint.color = if (isDark) Color.parseColor("#E0E0E0") else Color.parseColor("#374151")
                dateTextPaint.color = if (isDark) Color.parseColor("#9CA3AF") else Color.parseColor("#6B7280")
            }

            // 绘制星期与公历具体日期 (如 周一 / 8/31)
            canvas.drawText(dayTitle, centerX, 17 * density, dayNameTextPaint)
            canvas.drawText(dateStr, centerX, 33 * density, dateTextPaint)

            canvas.drawLine(left, 0f, left, height.toFloat(), linePaint)
        }
        canvas.drawLine(0f, headerHeight, w, headerHeight, linePaint)
        canvas.drawLine(timeColWidth, 0f, timeColWidth, height.toFloat(), linePaint)

        // 2. 节次与完整起止时间
        for (p in 1..totalPeriods) {
            val top = headerHeight + (p - 1) * rowHeight
            val bottom = top + rowHeight
            val centerX = timeColWidth / 2f

            canvas.drawText("$p", centerX, top + 18 * density, periodTextPaint)
            val startTime = TimeSlot.getStartTime(p)
            canvas.drawText(startTime, centerX, top + 34 * density, timeStartTextPaint)
            val endTime = TimeSlot.getEndTime(p)
            canvas.drawText(endTime, centerX, top + 48 * density, timeEndTextPaint)

            canvas.drawLine(0f, bottom, w, bottom, linePaint)
        }

        // 3. 课程卡片渲染
        for (c in courses) {
            val dayIndex = c.dayOfWeek - 1
            if (dayIndex !in 0 until totalDays) continue

            val colLeft = timeColWidth + dayIndex * dayColWidth
            val top = headerHeight + (c.startPeriod - 1) * rowHeight
            val count = (c.endPeriod - c.startPeriod + 1).coerceAtLeast(1)
            val bottom = top + count * rowHeight

            val rect = RectF(
                colLeft + 2.5f * density,
                top + 2.5f * density,
                colLeft + dayColWidth - 2.5f * density,
                bottom - 2.5f * density
            )
            courseRects.add(Pair(rect, c))

            val color = CourseColorHelper.getColor(context, c.colorIndex)
            courseCardPaint.color = color.bg
            courseCardBorderPaint.color = color.border
            courseTitlePaint.color = color.text
            courseLocPaint.color = color.text
            courseTeacherPaint.color = color.text
            courseTimeSpanPaint.color = color.text
            courseTimeSpanPaint.alpha = 210

            val radius = 10 * density
            canvas.drawRoundRect(rect, radius, radius, courseCardPaint)
            canvas.drawRoundRect(rect, radius, radius, courseCardBorderPaint)

            canvas.save()
            canvas.clipRect(rect)

            var textY = rect.top + 13 * density
            val paddingX = rect.left + 5 * density
            val maxTextW = rect.width() - 8 * density

            // 课程名
            val nameWords = c.name
            var curLine = ""
            var linesRendered = 0
            for (ch in nameWords) {
                if (courseTitlePaint.measureText(curLine + ch) > maxTextW) {
                    canvas.drawText(curLine, paddingX, textY, courseTitlePaint)
                    textY += 13 * density
                    linesRendered++
                    curLine = "$ch"
                    if (linesRendered >= 2) break
                } else {
                    curLine += ch
                }
            }
            if (curLine.isNotEmpty() && linesRendered < 2 && textY <= rect.bottom - 16 * density) {
                canvas.drawText(curLine, paddingX, textY, courseTitlePaint)
                textY += 13 * density
            }

            // 教室地点
            if (c.location.isNotEmpty() && textY <= rect.bottom - 12 * density) {
                val locText = "@ " + c.location
                canvas.drawText(locText, paddingX, textY, courseLocPaint)
                textY += 12 * density
            }

            // 当前周次授课教师
            val curTeacher = c.getTeacherForWeek(displayedWeek)
            if (curTeacher.isNotEmpty() && textY <= rect.bottom - 12 * density) {
                canvas.drawText(curTeacher, paddingX, textY, courseTeacherPaint)
                textY += 12 * density
            }

            // 起止时间跨度
            if (count >= 2 && textY <= rect.bottom - 4 * density) {
                val timeSpan = "${TimeSlot.getStartTime(c.startPeriod)}-${TimeSlot.getEndTime(c.endPeriod)}"
                canvas.drawText(timeSpan, paddingX, textY, courseTimeSpanPaint)
            }

            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            for ((rect, course) in courseRects) {
                if (rect.contains(x, y)) {
                    onCourseClickListener?.invoke(course)
                    return true
                }
            }
        }
        return true
    }
}
