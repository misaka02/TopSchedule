package com.topware.timetable.ui.floating

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.topware.timetable.R
import com.topware.timetable.data.model.Course
import com.topware.timetable.data.model.CourseStatus
import com.topware.timetable.data.model.SemesterConfig
import com.topware.timetable.data.model.TimeSlot
import com.topware.timetable.data.repository.ScheduleRepository
import com.topware.timetable.databinding.ActivityFloatingScheduleBinding
import com.topware.timetable.databinding.ItemPageTodayBinding
import com.topware.timetable.databinding.ItemPageWeekBinding
import com.topware.timetable.databinding.ItemTodayCourseBinding
import com.topware.timetable.util.TimeUtils
import java.util.Calendar

class FloatingScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFloatingScheduleBinding
    private lateinit var repository: ScheduleRepository
    private var actualCurrentWeek: Int = 1
    private var selectedWeek: Int = 1
    private var currentDay: Int = 1
    private lateinit var semesterConfig: SemesterConfig
    private lateinit var pagerAdapter: FloatingPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFloatingScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ScheduleRepository.getInstance(this)
        semesterConfig = repository.getSemesterConfig()
        actualCurrentWeek = TimeUtils.getCurrentWeek(semesterConfig)
        selectedWeek = actualCurrentWeek
        currentDay = TimeUtils.getCurrentDayOfWeek()

        initViews()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        semesterConfig = repository.getSemesterConfig()
        actualCurrentWeek = TimeUtils.getCurrentWeek(semesterConfig)
        currentDay = TimeUtils.getCurrentDayOfWeek()
        updateWeekTitle()
        loadData()
    }

    private fun initViews() {
        binding.rootContainer.setOnClickListener {
            finishWithAnimation()
        }

        binding.cardFloating.setOnClickListener {
        }

        binding.btnCloseFloating.setOnClickListener {
            finishWithAnimation()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithAnimation()
            }
        })

        binding.btnPrevWeek.setOnClickListener {
            if (selectedWeek > 1) {
                selectedWeek--
                updateWeekTitle()
                loadData()
            }
        }

        binding.btnNextWeek.setOnClickListener {
            if (selectedWeek < semesterConfig.totalWeeks) {
                selectedWeek++
                updateWeekTitle()
                loadData()
            }
        }

        updateWeekTitle()

        pagerAdapter = FloatingPagerAdapter()
        binding.viewPagerFloating.adapter = pagerAdapter

        val defaultTab = repository.getFloatingDefaultTab()
        if (defaultTab == 1) {
            binding.toggleGroupMode.check(R.id.btnTabWeek)
            binding.viewPagerFloating.setCurrentItem(1, false)
        } else {
            binding.toggleGroupMode.check(R.id.btnTabToday)
            binding.viewPagerFloating.setCurrentItem(0, false)
        }

        binding.toggleGroupMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnTabWeek) {
                    binding.viewPagerFloating.setCurrentItem(1, true)
                    repository.setFloatingDefaultTab(1)
                } else {
                    binding.viewPagerFloating.setCurrentItem(0, true)
                    repository.setFloatingDefaultTab(0)
                }
            }
        }

        binding.viewPagerFloating.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == 0) {
                    binding.toggleGroupMode.check(R.id.btnTabToday)
                    repository.setFloatingDefaultTab(0)
                } else {
                    binding.toggleGroupMode.check(R.id.btnTabWeek)
                    repository.setFloatingDefaultTab(1)
                }
            }
        })
    }

    private fun updateWeekTitle() {
        val dateRange = TimeUtils.getWeekDateRange(semesterConfig, selectedWeek)
        val weekStr = if (selectedWeek == actualCurrentWeek) {
            "第 $selectedWeek 周 (本周) · $dateRange"
        } else {
            "第 $selectedWeek 周 · $dateRange"
        }
        binding.tvCurrentWeek.text = weekStr
    }

    private fun loadData() {
        pagerAdapter.notifyDataSetChanged()
    }

    private fun finishWithAnimation() {
        finish()
    }

    private fun showCourseDetail(course: Course) {
        val dialog = Dialog(this, R.style.Theme_TopSchedule_Floating)
        dialog.setContentView(R.layout.dialog_course_detail)
        dialog.setCanceledOnTouchOutside(true)

        val tvName = dialog.findViewById<TextView>(R.id.dialogCourseName)
        val tvTime = dialog.findViewById<TextView>(R.id.dialogTime)
        val tvLoc = dialog.findViewById<TextView>(R.id.dialogLocation)
        val tvTeacher = dialog.findViewById<TextView>(R.id.dialogTeacher)
        val tvAllTeachers = dialog.findViewById<TextView>(R.id.dialogAllTeachers)
        val tvWeeks = dialog.findViewById<TextView>(R.id.dialogWeeks)
        val tvDept = dialog.findViewById<TextView>(R.id.dialogDepartment)
        val tvPhone = dialog.findViewById<TextView>(R.id.dialogPhone)
        val btnClose = dialog.findViewById<MaterialButton>(R.id.dialogBtnClose)

        tvName?.text = course.name

        val specificDate = TimeUtils.getSpecificDateString(semesterConfig, selectedWeek, course.dayOfWeek)
        val startTime = TimeSlot.getStartTime(course.startPeriod)
        val endTime = TimeSlot.getEndTime(course.endPeriod)
        val timeDisplay = if (specificDate.isNotBlank()) {
            "$specificDate ${course.dayName} 第 ${course.jieci} 节 ($startTime - $endTime)"
        } else {
            "${course.dayName} 第 ${course.jieci} 节 ($startTime - $endTime)"
        }
        tvTime?.text = timeDisplay

        tvLoc?.text = course.location.ifBlank { "未指定教室" }

        val teacherForThisWeek = course.getTeacherForWeek(selectedWeek)
        tvTeacher?.text = "本周教师：${teacherForThisWeek.ifBlank { course.teacher.ifBlank { "待定" } }}"

        if (course.teacherAssignments.isNotEmpty()) {
            val sbTeachers = StringBuilder()
            for (a in course.teacherAssignments) {
                sbTeachers.append("${a.startWeek}-${a.endWeek}周：${a.teacherName}\n")
            }
            tvAllTeachers?.text = sbTeachers.toString().trimEnd()
        } else {
            tvAllTeachers?.text = course.teacher.ifBlank { "待定" }
        }

        tvWeeks?.text = course.weeksStr.ifBlank { "${course.weeks.firstOrNull() ?: 1}-${course.weeks.lastOrNull() ?: 16}周" }

        if (course.department.isNotBlank()) {
            tvDept?.visibility = View.VISIBLE
            tvDept?.text = course.department
        } else {
            tvDept?.visibility = View.GONE
        }

        if (course.phone.isNotBlank()) {
            tvPhone?.visibility = View.VISIBLE
            tvPhone?.text = course.phone
        } else {
            tvPhone?.visibility = View.GONE
        }

        btnClose?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    inner class FloatingPagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount(): Int = 2

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val b = ItemPageTodayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                TodayViewHolder(b)
            } else {
                val b = ItemPageWeekBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                WeekViewHolder(b)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is TodayViewHolder) {
                holder.bind()
            } else if (holder is WeekViewHolder) {
                holder.bind()
            }
        }

        inner class TodayViewHolder(private val pageBinding: ItemPageTodayBinding) :
            RecyclerView.ViewHolder(pageBinding.root) {

            fun bind() {
                val todayCourses = repository.getCoursesForDay(selectedWeek, currentDay)
                if (todayCourses.isEmpty()) {
                    pageBinding.rvTodayCourses.visibility = View.GONE
                    pageBinding.tvEmptyToday.visibility = View.VISIBLE
                } else {
                    pageBinding.rvTodayCourses.visibility = View.VISIBLE
                    pageBinding.tvEmptyToday.visibility = View.GONE

                    val nowCal = Calendar.getInstance()
                    var nextFound = false
                    val sortedList = todayCourses.sortedBy { it.startPeriod }
                    val courseWithStatus = sortedList.map { course ->
                        val rawStatus = course.calculateStatus(nowCal)
                        val finalStatus = if (rawStatus == CourseStatus.FUTURE && !nextFound) {
                            nextFound = true
                            CourseStatus.NEXT_UPCOMING
                        } else {
                            rawStatus
                        }
                        Pair(course, finalStatus)
                    }

                    pageBinding.rvTodayCourses.layoutManager = LinearLayoutManager(this@FloatingScheduleActivity)
                    pageBinding.rvTodayCourses.adapter = TodayCourseAdapter(courseWithStatus, nowCal) { course ->
                        showCourseDetail(course)
                    }
                }
            }
        }

        inner class WeekViewHolder(private val pageBinding: ItemPageWeekBinding) :
            RecyclerView.ViewHolder(pageBinding.root) {

            fun bind() {
                val weekCourses = repository.getCoursesForWeek(selectedWeek)
                pageBinding.floatingTimetableView.setCourses(weekCourses, selectedWeek, semesterConfig)
                pageBinding.floatingTimetableView.setOnCourseClickListener { course ->
                    showCourseDetail(course)
                }
            }
        }
    }

    inner class TodayCourseAdapter(
        private val list: List<Pair<Course, CourseStatus>>,
        private val nowCal: Calendar,
        private val onClick: (Course) -> Unit
    ) : RecyclerView.Adapter<TodayCourseAdapter.ViewHolder>() {

        inner class ViewHolder(val itemBinding: ItemTodayCourseBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemTodayCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (item, status) = list[position]

            holder.itemBinding.tvCourseName.text = item.name
            holder.itemBinding.tvJieCiTag.text = item.getFormattedPeriodRange()
            holder.itemBinding.tvTimeRange.text = item.getFormattedTimeRange()
            holder.itemBinding.tvLocation.text = item.location.ifBlank { "未指定教室" }
            holder.itemBinding.tvTeacher.text = item.getTeacherForWeek(selectedWeek)
            holder.itemBinding.tvWeeks.text = item.weeksStr.ifBlank { "全部周次" }

            when (status) {
                CourseStatus.ONGOING -> {
                    holder.itemBinding.cardCourseItem.alpha = 1.0f
                    holder.itemBinding.cardCourseItem.strokeColor = android.graphics.Color.parseColor("#1A73E8")
                    holder.itemBinding.tvStatusBadge.visibility = View.VISIBLE
                    holder.itemBinding.tvStatusBadge.text = "正在进行"
                    holder.itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_ongoing)
                    holder.itemBinding.tvCountdown.visibility = View.GONE
                }
                CourseStatus.NEXT_UPCOMING -> {
                    holder.itemBinding.cardCourseItem.alpha = 1.0f
                    holder.itemBinding.cardCourseItem.strokeColor = android.graphics.Color.parseColor("#D97706")
                    holder.itemBinding.tvStatusBadge.visibility = View.VISIBLE
                    holder.itemBinding.tvStatusBadge.text = "下一节课"
                    holder.itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_next)

                    val minutes = item.getMinutesUntilStart(nowCal)
                    holder.itemBinding.tvCountdown.visibility = View.VISIBLE
                    holder.itemBinding.tvCountdown.text = if (minutes > 60) {
                        "还有 ${minutes / 60}小时${minutes % 60}分钟 上课"
                    } else {
                        "还有 ${minutes}分钟 上课"
                    }
                }
                CourseStatus.FUTURE -> {
                    holder.itemBinding.cardCourseItem.alpha = 1.0f
                    holder.itemBinding.cardCourseItem.strokeColor = android.graphics.Color.parseColor("#E5E7EB")
                    holder.itemBinding.tvStatusBadge.visibility = View.GONE
                    holder.itemBinding.tvCountdown.visibility = View.GONE
                }
                CourseStatus.FINISHED -> {
                    holder.itemBinding.cardCourseItem.alpha = 0.5f
                    holder.itemBinding.cardCourseItem.strokeColor = android.graphics.Color.parseColor("#E5E7EB")
                    holder.itemBinding.tvStatusBadge.visibility = View.VISIBLE
                    holder.itemBinding.tvStatusBadge.text = "已结束"
                    holder.itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_finished)
                    holder.itemBinding.tvCountdown.visibility = View.GONE
                }
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = list.size
    }
}
