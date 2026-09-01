package com.topware.timetable.ui.main

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.topware.timetable.R
import com.topware.timetable.data.model.Course
import com.topware.timetable.data.model.SemesterConfig
import com.topware.timetable.data.model.TimeSlot
import com.topware.timetable.data.parser.TopsoftHtmlParser
import com.topware.timetable.data.repository.ScheduleRepository
import com.topware.timetable.databinding.ActivityMainBinding
import com.topware.timetable.ui.floating.FloatingScheduleActivity
import com.topware.timetable.ui.webview.WebScheduleActivity
import com.topware.timetable.util.TimeUtils
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ScheduleRepository

    private var selectedWeek: Int = 1
    private var currentActualWeek: Int = 1
    private lateinit var semesterConfig: SemesterConfig

    private val selectHtmlLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ScheduleRepository.getInstance(this)
        setSupportActionBar(binding.toolbar)

        initTimeData()
        setupListeners()
        loadScheduleForWeek(selectedWeek)
    }

    override fun onResume() {
        super.onResume()
        initTimeData()
        loadScheduleForWeek(selectedWeek)
    }

    private fun importFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val html = stream.bufferedReader().readText()
                val courses = TopsoftHtmlParser.parse(html)
                if (courses.isNotEmpty()) {
                    repository.saveCourses(courses)
                    loadScheduleForWeek(selectedWeek)
                    AlertDialog.Builder(this)
                        .setTitle("本地课表导入成功")
                        .setMessage("成功解析并导入 ${courses.size} 门次课程。\n主界面与悬浮窗均已更新。")
                        .setPositiveButton("确定", null)
                        .show()
                } else {
                    Toast.makeText(this, "未能从选中的文件中解析到课表数据", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "读取文件失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPinShortcut() {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val shortcutIntent = Intent(this, FloatingScheduleActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, "floating_schedule_pinned")
                .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                .setShortLabel("悬浮课表")
                .setLongLabel("呼出今日与周课表")
                .setIntent(shortcutIntent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
            Toast.makeText(this, "已发起添加快捷方式请求，请在弹窗中允许", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "当前系统不支持自动添加，请在 Panels 中选择【快捷方式】->【悬浮课表】", Toast.LENGTH_LONG).show()
        }
    }

    private fun initTimeData() {
        semesterConfig = repository.getSemesterConfig()
        currentActualWeek = TimeUtils.getCurrentWeek(semesterConfig)
        selectedWeek = currentActualWeek
        updateWeekTitle(selectedWeek)
    }

    private fun setupListeners() {
        binding.btnOpenFloating.setOnClickListener {
            val intent = Intent(this, FloatingScheduleActivity::class.java)
            startActivity(intent)
        }

        binding.btnMainPrevWeek.setOnClickListener {
            if (selectedWeek > 1) {
                selectedWeek--
                updateWeekSelection(selectedWeek)
            }
        }

        binding.btnMainNextWeek.setOnClickListener {
            if (selectedWeek < semesterConfig.totalWeeks) {
                selectedWeek++
                updateWeekSelection(selectedWeek)
            }
        }

        binding.tvMainWeekTitle.setOnClickListener {
            showWeekSelectDialog()
        }

        binding.fabSync.setOnClickListener {
            val intent = Intent(this, WebScheduleActivity::class.java)
            startActivity(intent)
        }

        binding.fabSync.setOnLongClickListener {
            selectHtmlLauncher.launch("*/*")
            true
        }

        binding.timetableView.setOnCourseClickListener { course ->
            showCourseDetailDialog(course)
        }
    }

    private fun showWeekSelectDialog() {
        val items = (1..semesterConfig.totalWeeks).map { w ->
            val range = TimeUtils.getWeekDateRange(semesterConfig, w)
            if (w == currentActualWeek) {
                "第 $w 周 (本周)  [$range]"
            } else {
                "第 $w 周  [$range]"
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择周次与日期")
            .setSingleChoiceItems(items, selectedWeek - 1) { dialog, which ->
                selectedWeek = which + 1
                updateWeekSelection(selectedWeek)
                dialog.dismiss()
            }
            .setNeutralButton("校准开学日期") { _, _ ->
                showSetStartDateDialog()
            }
            .show()
    }

    private fun showSetStartDateDialog() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = semesterConfig.startDateMillis
        }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                    // 确保对齐到该周周一
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                }
                val newConfig = semesterConfig.copy(startDateMillis = newCal.timeInMillis)
                repository.saveSemesterConfig(newConfig)
                initTimeData()
                loadScheduleForWeek(selectedWeek)
                Toast.makeText(this, "开学周一已设置为：${TimeUtils.getFormattedDate(newCal.timeInMillis)}", Toast.LENGTH_SHORT).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateWeekSelection(week: Int) {
        updateWeekTitle(week)
        loadScheduleForWeek(week)
    }

    private fun updateWeekTitle(week: Int) {
        val dateRange = TimeUtils.getWeekDateRange(semesterConfig, week)
        val weekText = if (week == currentActualWeek) {
            "第 $week 周 (本周) · $dateRange ▼"
        } else {
            "第 $week 周 · $dateRange ▼"
        }
        binding.tvMainWeekTitle.text = weekText
    }

    private fun loadScheduleForWeek(week: Int) {
        val weekCourses = repository.getCoursesForWeek(week)
        binding.timetableView.setCourses(weekCourses, week, semesterConfig)
    }

    private fun showCourseDetailDialog(course: Course) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(course.name)
        val currentWeek = selectedWeek
        val teacherForThisWeek = course.getTeacherForWeek(currentWeek)

        val specificDate = TimeUtils.getSpecificDateString(semesterConfig, currentWeek, course.dayOfWeek)
        val startTime = TimeSlot.getStartTime(course.startPeriod)
        val endTime = TimeSlot.getEndTime(course.endPeriod)

        val sb = StringBuilder()
        if (specificDate.isNotBlank()) {
            sb.append("日期：$specificDate ${course.dayName}\n")
        } else {
            sb.append("时间：${course.dayName}\n")
        }
        sb.append("节次：第 ${course.jieci} 节 ($startTime - $endTime)\n")
        sb.append("教室：${course.location.ifBlank { "未指定" }}\n")
        sb.append("教师：${teacherForThisWeek.ifBlank { course.teacher.ifBlank { "待定" } }}\n")
        sb.append("周次：${course.weeksStr.ifBlank { "${course.weeks.firstOrNull() ?: 1}-${course.weeks.lastOrNull() ?: 16}周" }}\n")
        if (course.department.isNotBlank()) sb.append("开课院系：${course.department}\n")
        if (course.phone.isNotBlank()) sb.append("联系电话：${course.phone}\n")

        builder.setMessage(sb.toString())
        builder.setPositiveButton("确定", null)
        builder.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "添加侧边栏/桌面快捷方式")
        menu?.add(0, 2, 1, "校准开学日期")
        menu?.add(0, 3, 2, "导入本地 HTML 课表")
        menu?.add(0, 4, 3, "网络同步课表")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> {
                requestPinShortcut()
                true
            }
            2 -> {
                showSetStartDateDialog()
                true
            }
            3 -> {
                selectHtmlLauncher.launch("*/*")
                true
            }
            4 -> {
                startActivity(Intent(this, WebScheduleActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
