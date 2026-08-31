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
import com.topware.timetable.data.model.TimeSlot
import com.topware.timetable.util.CourseColorHelper

class TimetableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var courses: List<Course> = emptyList()
    private var displayedWeek: Int = 1
    private var onCourseClickListener: ((Course) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val timeColWidth = 44 * density
    private val headerHeight = 36 * density
    private val rowHeight = 64 * density
    private val totalPeriods = 13
    private val totalDays = 7

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1 * density
    }

    private val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12 * density
        textAlign = Paint.Align.CENTER
    }

    private val periodTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11.5f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val timeSubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9.5f * density
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

    private val courseRects = mutableListOf<Pair<RectF, Course>>()

    fun setCourses(newCourses: List<Course>, currentWeek: Int = 1) {
        this.courses = newCourses
        this.displayedWeek = currentWeek
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

        // 颜色动态适配深色/浅色模式
        linePaint.color = if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#EEF0F2")
        headerBgPaint.color = if (isDark) Color.parseColor("#1E1E1E") else Color.parseColor("#F8F9FA")
        textPaint.color = if (isDark) Color.parseColor("#E0E0E0") else Color.parseColor("#374151")
        periodTextPaint.color = if (isDark) Color.parseColor("#D1D5DB") else Color.parseColor("#4B5563")
        timeSubTextPaint.color = if (isDark) Color.parseColor("#9CA3AF") else Color.parseColor("#6B7280")

        val w = width.toFloat()
        val dayColWidth = (w - timeColWidth) / totalDays

        // 1. 顶部星期栏
        canvas.drawRect(0f, 0f, w, headerHeight, headerBgPaint)

        val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        for (i in 0 until totalDays) {
            val left = timeColWidth + i * dayColWidth
            val centerX = left + dayColWidth / 2f
            canvas.drawText(dayNames[i], centerX, headerHeight / 2f + 4 * density, textPaint)
            canvas.drawLine(left, 0f, left, height.toFloat(), linePaint)
        }
        canvas.drawLine(0f, headerHeight, w, headerHeight, linePaint)
        canvas.drawLine(timeColWidth, 0f, timeColWidth, height.toFloat(), linePaint)

        // 2. 节次与时间
        for (p in 1..totalPeriods) {
            val top = headerHeight + (p - 1) * rowHeight
            val bottom = top + rowHeight

            canvas.drawText("$p", timeColWidth / 2f, top + 20 * density, periodTextPaint)
            val startTime = TimeSlot.getStartTime(p)
            canvas.drawText(startTime, timeColWidth / 2f, top + 36 * density, timeSubTextPaint)
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

            // 教室
            if (c.location.isNotEmpty() && textY <= rect.bottom - 12 * density) {
                val locText = "@ " + c.location
                canvas.drawText(locText, paddingX, textY, courseLocPaint)
                textY += 12 * density
            }

            // 当前周次的授课教师
            val curTeacher = c.getTeacherForWeek(displayedWeek)
            if (curTeacher.isNotEmpty() && textY <= rect.bottom - 4 * density) {
                canvas.drawText(curTeacher, paddingX, textY, courseTeacherPaint)
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
