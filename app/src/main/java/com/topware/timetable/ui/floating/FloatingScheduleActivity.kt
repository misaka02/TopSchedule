package com.topware.timetable.ui.floating

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.topware.timetable.R
import com.topware.timetable.data.model.Course
import com.topware.timetable.data.repository.ScheduleRepository
import com.topware.timetable.databinding.ActivityFloatingScheduleBinding
import com.topware.timetable.databinding.ItemTodayCourseBinding
import com.topware.timetable.util.CourseColorHelper
import com.topware.timetable.util.TimeUtils

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
        binding.rootContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val cardLocation = IntArray(2)
                binding.cardFloating.getLocationOnScreen(cardLocation)
                val cardX = cardLocation[0]
                val cardY = cardLocation[1]
                val cardW = binding.cardFloating.width
                val cardH = binding.cardFloating.height

                val touchX = event.rawX
                val touchY = event.rawY

                if (touchX < cardX || touchX > cardX + cardW || touchY < cardY || touchY > cardY + cardH) {
                    finishWithAnimation()
                    return@setOnTouchListener true
                }
            }
            false
        }

        binding.btnCloseFloating.setOnClickListener {
            finishWithAnimation()
        }

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
            binding.rvTodayCourses.adapter = TodayCourseAdapter(todayCourses) { course ->
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
        view.findViewById<TextView>(R.id.dialogTime).text = "⏰ 时间：${course.dayName} 第${course.jieci}节 (${course.getFormattedTime()})"
        view.findViewById<TextView>(R.id.dialogLocation).text = "📍 地点：${course.location.ifBlank { "待定" }}"
        view.findViewById<TextView>(R.id.dialogTeacher).text = "👨‍🏫 教师：${course.teacher.ifBlank { "待定" }}"
        view.findViewById<TextView>(R.id.dialogWeeks).text = "📅 周次：${course.weeksStr.ifBlank { "全周" }}"
        view.findViewById<TextView>(R.id.dialogDepartment).text = "🏛️ 院系：${course.department.ifBlank { "教务处" }}"
        view.findViewById<TextView>(R.id.dialogPhone).text = "📞 电话：${course.phone.ifBlank { "暂无" }}"

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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishWithAnimation()
    }

    inner class TodayCourseAdapter(
        private val list: List<Course>,
        private val onClick: (Course) -> Unit
    ) : RecyclerView.Adapter<TodayCourseAdapter.ViewHolder>() {

        inner class ViewHolder(val itemBinding: ItemTodayCourseBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemTodayCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            val color = CourseColorHelper.getColor(item.colorIndex)
            holder.itemBinding.cardCourseItem.setCardBackgroundColor(color.bg)
            holder.itemBinding.cardCourseItem.strokeColor = color.border

            holder.itemBinding.tvCourseName.text = item.name
            holder.itemBinding.tvCourseName.setTextColor(color.text)
            holder.itemBinding.tvJieCiTag.text = "第 ${item.jieci} 节"
            holder.itemBinding.tvLocation.text = "📍 " + item.location.ifBlank { "未指定" }
            holder.itemBinding.tvTeacher.text = "👨‍🏫 " + item.teacher.ifBlank { "教师" }
            holder.itemBinding.tvTimeRange.text = item.getFormattedTime()

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = list.size
    }
}
