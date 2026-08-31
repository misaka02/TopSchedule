package com.topware.timetable.ui.floating

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.topware.timetable.R
import com.topware.timetable.data.model.Course
import com.topware.timetable.data.model.CourseStatus
import com.topware.timetable.data.repository.ScheduleRepository
import com.topware.timetable.databinding.ActivityFloatingScheduleBinding
import com.topware.timetable.databinding.ItemTodayCourseBinding
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
        loadData()
    }

    private fun initViews() {
        // 1. 极其可靠的点击空白处退出机制：直接监听全屏背景 rootContainer 点击
        binding.rootContainer.setOnClickListener {
            finishWithAnimation()
        }

        // 拦截居中卡片内部的点击，防止点卡片内容时触发关闭
        binding.cardFloating.setOnClickListener {
            // Do nothing
        }

        binding.btnCloseFloating.setOnClickListener {
            finishWithAnimation()
        }

        // 2. 注册返回键与手势监听，按返回键/手势立刻退隐
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithAnimation()
            }
        })

        val dayName = TimeUtils.getDayName(currentDay)
        binding.tvFloatingSubtitle.text = "第 $currentWeek 周 · $dayName"

        binding.toggleGroupMode.check(R.id.btnTabToday)
        binding.toggleGroupMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnTabToday) {
                    binding.containerToday.visibility = View.VISIBLE
                    binding.containerWeek.visibility = View.GONE
                } else {
                    binding.containerToday.visibility = View.GONE
                    binding.containerWeek.visibility = View.VISIBLE
                }
            }
        }

        binding.rvTodayCourses.layoutManager = LinearLayoutManager(this)

        binding.floatingTimetableView.setOnCourseClickListener { course ->
            showCourseDetail(course)
        }
    }

    private fun loadData() {
        val todayCourses = repository.getCoursesForDay(currentWeek, currentDay)
        if (todayCourses.isEmpty()) {
            binding.rvTodayCourses.visibility = View.GONE
            binding.tvEmptyToday.visibility = View.VISIBLE
        } else {
            binding.rvTodayCourses.visibility = View.VISIBLE
            binding.tvEmptyToday.visibility = View.GONE

            // 计算每门课当前的状态（进行中、下一节、未来、已结束）并排序
            val nowCal = Calendar.getInstance()
            var nextFound = false

            // 先按节次排序
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

            binding.rvTodayCourses.adapter = TodayCourseAdapter(courseWithStatus, nowCal) { course ->
                showCourseDetail(course)
            }
        }

        val weekCourses = repository.getCoursesForWeek(currentWeek)
        binding.floatingTimetableView.setCourses(weekCourses)
    }

    private fun showCourseDetail(course: Course) {
        val dialog = Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_course_detail, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.dialogCourseName).text = course.name
        view.findViewById<TextView>(R.id.dialogTime).text = "${course.dayName} ${course.getFormattedPeriodRange()} (${course.getFormattedTimeRange()})"
        view.findViewById<TextView>(R.id.dialogLocation).text = course.location.ifBlank { "待定" }
        view.findViewById<TextView>(R.id.dialogTeacher).text = course.teacher.ifBlank { "待定" }
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
            holder.itemBinding.tvTeacher.text = item.teacher.ifBlank { "任课教师" }
            holder.itemBinding.tvWeeks.text = item.weeksStr.ifBlank { "全部周次" }

            // 根据当前状态高亮与展示标签
            when (status) {
                CourseStatus.ONGOING -> {
                    holder.itemBinding.cardCourseItem.alpha = 1.0f
                    holder.itemBinding.cardCourseItem.strokeColor = android.graphics.Color.parseColor("#1A73E8")
                    holder.itemBinding.cardCourseItem.setCardBackgroundColor(android.graphics.Color.parseColor("#F0F6FF"))
                    holder.itemBinding.tvStatusBadge.visibility = View.VISIBLE
                    holder.itemBinding.tvStatusBadge.text = "正在进行"
                    holder.itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_ongoing)
                    holder.itemBinding.tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#1A73E8"))
                    holder.itemBinding.tvCountdown.visibility = View.GONE
                }
                CourseStatus.NEXT_UPCOMING -> {
                    holder.itemBinding.cardCourseItem.alpha = 1.0f
                    holder.itemBinding.cardCourseItem.strokeColor = android.graphics.Color.parseColor("#D97706")
                    holder.itemBinding.cardCourseItem.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFBEB"))
                    holder.itemBinding.tvStatusBadge.visibility = View.VISIBLE
                    holder.itemBinding.tvStatusBadge.text = "下一节课"
                    holder.itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_next)
                    holder.itemBinding.tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#D97706"))

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
                    holder.itemBinding.cardCourseItem.strokeColor = android.graphics.Color.parseColor("#E0E0E0")
                    holder.itemBinding.cardCourseItem.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"))
                    holder.itemBinding.tvStatusBadge.visibility = View.GONE
                    holder.itemBinding.tvCountdown.visibility = View.GONE
                }
                CourseStatus.FINISHED -> {
                    holder.itemBinding.cardCourseItem.alpha = 0.55f
                    holder.itemBinding.cardCourseItem.strokeColor = android.graphics.Color.parseColor("#E5E7EB")
                    holder.itemBinding.cardCourseItem.setCardBackgroundColor(android.graphics.Color.parseColor("#F9FAFB"))
                    holder.itemBinding.tvStatusBadge.visibility = View.VISIBLE
                    holder.itemBinding.tvStatusBadge.text = "已结束"
                    holder.itemBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_finished)
                    holder.itemBinding.tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#9CA3AF"))
                    holder.itemBinding.tvCountdown.visibility = View.GONE
                }
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = list.size
    }
}
