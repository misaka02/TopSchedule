package com.topware.timetable.ui.view

import android.content.Context
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
    private var onCourseClickListener: ((Course) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val timeColWidth = 42 * density
    private val headerHeight = 36 * density
    private val rowHeight = 60 * density
    private val totalPeriods = 13
    private val totalDays = 7

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1 * density
    }

    private val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F5F5")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#424242")
        textSize = 12 * density
        textAlign = Paint.Align.CENTER
    }

    private val periodTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757575")
        textSize = 11 * density
        textAlign = Paint.Align.CENTER
    }

    private val timeSubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        textSize = 9 * density
        textAlign = Paint.Align.CENTER
    }

    private val courseCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val courseCardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1 * density
    }

    private val courseTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 11.5f * density
        isFakeBoldText = true
        textAlign = Paint.Align.LEFT
    }

    private val courseDtlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        textSize = 10 * density
        textAlign = Paint.Align.LEFT
    }

    private val courseRects = mutableListOf<Pair<RectF, Course>>()

    fun setCourses(newCourses: List<Course>) {
        this.courses = newCourses
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

        val w = width.toFloat()
        val dayColWidth = (w - timeColWidth) / totalDays

        canvas.drawRect(0f, 0f, w, headerHeight, headerBgPaint)

        val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
        for (i in 0 until totalDays) {
            val left = timeColWidth + i * dayColWidth
            val centerX = left + dayColWidth / 2f
            canvas.drawText("周" + dayNames[i], centerX, headerHeight / 2f + 4 * density, textPaint)
            canvas.drawLine(left, 0f, left, height.toFloat(), linePaint)
        }
        canvas.drawLine(0f, headerHeight, w, headerHeight, linePaint)
        canvas.drawLine(timeColWidth, 0f, timeColWidth, height.toFloat(), linePaint)

        for (p in 1..totalPeriods) {
            val top = headerHeight + (p - 1) * rowHeight
            val bottom = top + rowHeight

            canvas.drawText("$p", timeColWidth / 2f, top + 18 * density, periodTextPaint)
            val startTime = TimeSlot.getStartTime(p)
            canvas.drawText(startTime, timeColWidth / 2f, top + 34 * density, timeSubTextPaint)
            canvas.drawLine(0f, bottom, w, bottom, linePaint)
        }

        for (c in courses) {
            val dayIndex = c.dayOfWeek - 1
            if (dayIndex !in 0 until totalDays) continue

            val colLeft = timeColWidth + dayIndex * dayColWidth
            val top = headerHeight + (c.startPeriod - 1) * rowHeight
            val count = (c.endPeriod - c.startPeriod + 1).coerceAtLeast(1)
            val bottom = top + count * rowHeight

            val rect = RectF(
                colLeft + 2 * density,
                top + 2 * density,
                colLeft + dayColWidth - 2 * density,
                bottom - 2 * density
            )
            courseRects.add(Pair(rect, c))

            val color = CourseColorHelper.getColor(c.colorIndex)
            courseCardPaint.color = color.bg
            courseCardBorderPaint.color = color.border
            courseTitlePaint.color = color.text

            val radius = 8 * density
            canvas.drawRoundRect(rect, radius, radius, courseCardPaint)
            canvas.drawRoundRect(rect, radius, radius, courseCardBorderPaint)

            canvas.save()
            canvas.clipRect(rect)

            var textY = rect.top + 14 * density
            val paddingX = rect.left + 5 * density
            val maxTextW = rect.width() - 8 * density

            val nameWords = c.name
            var curLine = ""
            for (ch in nameWords) {
                if (courseTitlePaint.measureText(curLine + ch) > maxTextW) {
                    canvas.drawText(curLine, paddingX, textY, courseTitlePaint)
                    textY += 13 * density
                    curLine = "$ch"
                    if (textY > rect.bottom - 20 * density) break
                } else {
                    curLine += ch
                }
            }
            if (curLine.isNotEmpty() && textY <= rect.bottom - 10 * density) {
                canvas.drawText(curLine, paddingX, textY, courseTitlePaint)
                textY += 13 * density
            }

            if (c.location.isNotEmpty() && textY <= rect.bottom - 4 * density) {
                val locText = "@" + c.location.replace("号楼", "#")
                canvas.drawText(locText, paddingX, textY, courseDtlPaint)
                textY += 11 * density
            }

            if (c.teacher.isNotEmpty() && textY <= rect.bottom - 4 * density) {
                val teaText = c.teacher.take(6)
                canvas.drawText(teaText, paddingX, textY, courseDtlPaint)
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
