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
import com.topware.timetable.data.repository.ScheduleRepository
import com.topware.timetable.databinding.ActivityFloatingScheduleBinding
import com.topware.timetable.databinding.ItemPageTodayBinding
import com.topware.timetable.databinding.ItemPageWeekBinding
import com.topware.timetable.databinding.ItemTodayCourseBinding
import com.topware.timetable.ui.view.TimetableView
import com.topware.timetable.util.TimeUtils
import java.util.Calendar

class FloatingScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFloatingScheduleBinding
    private lateinit var repository: ScheduleRepository
    private var currentWeek: Int = 1
    private var currentDay: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFloatingScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ScheduleRepository.getInstance(this)
        val config = repository.getSemesterConfig()
        currentWeek = TimeUtils.getCurrentWeek(config)
        currentDay = TimeUtils.getCurrentDayOfWeek()

        initViews()
    }

    override fun onResume() {
        super.onResume()
        // 每次前台唤起时重新刷新数据与周次计算，确保展示最新课表
        val config = repository.getSemesterConfig()
        currentWeek = TimeUtils.getCurrentWeek(config)
        currentDay = TimeUtils.getCurrentDayOfWeek()
        binding.tvFloatingSubtitle.text = "第 $currentWeek 周 · ${TimeUtils.getDayName(currentDay)}"
        binding.viewPagerFloating.adapter?.notifyDataSetChanged()
    }

    private fun initViews() {
        // 点击空白处平滑退隐
        binding.rootContainer.setOnClickListener {
            finishWithAnimation()
        }

        binding.cardFloating.setOnClickListener {
            // 消费点击事件
        }

        binding.btnCloseFloating.setOnClickListener {
            finishWithAnimation()
        }

        // 返回键/侧滑手势退隐
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithAnimation()
            }
        })

        binding.tvFloatingSubtitle.text = "第 $currentWeek 周 · ${TimeUtils.getDayName(currentDay)}"

        // 设置 ViewPager2 左右滑动手势
        val pagerAdapter = FloatingPagerAdapter()
        binding.viewPagerFloating.adapter = pagerAdapter

        binding.toggleGroupMode.check(R.id.btnTabToday)
        binding.toggleGroupMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val targetPage = if (checkedId == R.id.btnTabToday) 0 else 1
                if (binding.viewPagerFloating.currentItem != targetPage) {
                    binding.viewPagerFloating.setCurrentItem(targetPage, true)
                }
            }
        }

        binding.viewPagerFloating.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 0) {
                    binding.toggleGroupMode.check(R.id.btnTabToday)
                } else {
                    binding.toggleGroupMode.check(R.id.btnTabWeek)
                }
            }
        })
    }

    private fun showCourseDetail(course: Course) {
        val dialog = Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_course_detail, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.dialogCourseName).text = course.name
        view.findViewById<TextView>(R.id.dialogTime).text = "${course.dayName} ${course.getFormattedPeriodRange()} (${course.getFormattedTimeRange()})"
        view.findViewById<TextView>(R.id.dialogLocation).text = course.location.ifBlank { "待定" }
        
        val curTeacher = course.getTeacherForWeek(currentWeek)
        view.findViewById<TextView>(R.id.dialogTeacher).text = "本周教师：$curTeacher"

        // 展示各周次全周期授课安排
        val allTeachersTv = view.findViewById<TextView>(R.id.dialogAllTeachers)
        if (course.teacherAssignments.isNotEmpty()) {
            val sb = StringBuilder()
            for (item in course.teacherAssignments) {
                val weekRange = if (item.startWeek == item.endWeek) "第 ${item.startWeek} 周" else "第 ${item.startWeek}-${item.endWeek} 周"
                sb.append("$weekRange: ${item.teacherName}\n")
            }
            allTeachersTv.text = sb.toString().trimEnd()
        } else {
            allTeachersTv.text = "全周: ${course.teacher.ifBlank { "待定" }}"
        }

        view.findViewById<TextView>(R.id.dialogWeeks).text = course.weeksStr.ifBlank { "全周" }
        view.findViewById<TextView>(R.id.dialogDepartment).text = course.department.ifBlank { "教务处" }
        view.findViewById<TextView>(R.id.dialogPhone).text = course.phone.ifBlank { "暂无" }

        view.findViewById<MaterialButton>(R.id.dialogBtnClose).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun finishWithAnimation() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.floating_enter, R.anim.floating_exit)
    }

    inner class FloatingPagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                val b = ItemPageTodayBinding.inflate(inflater, parent, false)
                TodayPageViewHolder(b)
            } else {
                val b = ItemPageWeekBinding.inflate(inflater, parent, false)
                WeekPageViewHolder(b)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is TodayPageViewHolder) {
                holder.bind()
            } else if (holder is WeekPageViewHolder) {
                holder.bind()
            }
        }

        override fun getItemCount(): Int = 2

        inner class TodayPageViewHolder(private val b: ItemPageTodayBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind() {
                val todayCourses = repository.getCoursesForDay(currentWeek, currentDay)
                if (todayCourses.isEmpty()) {
                    b.rvTodayCourses.visibility = View.GONE
                    b.tvEmptyToday.visibility = View.VISIBLE
                } else {
                    b.rvTodayCourses.visibility = View.VISIBLE
                    b.tvEmptyToday.visibility = View.GONE

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

                    b.rvTodayCourses.layoutManager = LinearLayoutManager(itemView.context)
                    b.rvTodayCourses.adapter = TodayCourseAdapter(courseWithStatus, nowCal) {
                        showCourseDetail(it)
                    }
                }
            }
        }

        inner class WeekPageViewHolder(private val b: ItemPageWeekBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind() {
                val weekCourses = repository.getCoursesForWeek(currentWeek)
                b.floatingTimetableView.setCourses(weekCourses, currentWeek)
                b.floatingTimetableView.setOnCourseClickListener {
                    showCourseDetail(it)
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
            
            // 展示当前周次对应的老师
            holder.itemBinding.tvTeacher.text = item.getTeacherForWeek(currentWeek)
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
