package com.topware.timetable.ui.main

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.topware.timetable.R
import com.topware.timetable.data.model.Course
import com.topware.timetable.data.parser.TopsoftHtmlParser
import com.topware.timetable.data.repository.ScheduleRepository
import com.topware.timetable.databinding.ActivityMainBinding
import com.topware.timetable.ui.floating.FloatingScheduleActivity
import com.topware.timetable.ui.webview.WebScheduleActivity
import com.topware.timetable.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ScheduleRepository
    private var actualCurrentWeek: Int = 1
    private var selectedWeek: Int = 1

    private val pickHtmlLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importHtmlFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        repository = ScheduleRepository.getInstance(this)

        initViews()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun initViews() {
        val config = repository.getSemesterConfig()
        actualCurrentWeek = TimeUtils.getCurrentWeek(config)
        selectedWeek = actualCurrentWeek

        updateWeekTitle()

        binding.btnMainPrevWeek.setOnClickListener {
            if (selectedWeek > 1) {
                selectedWeek--
                updateWeekTitle()
                updateWeekView()
            }
        }

        binding.btnMainNextWeek.setOnClickListener {
            val total = repository.getSemesterConfig().totalWeeks
            if (selectedWeek < total) {
                selectedWeek++
                updateWeekTitle()
                updateWeekView()
            }
        }

        binding.tvMainWeekTitle.setOnClickListener {
            showSelectWeekDialog()
        }

        binding.btnOpenFloating.setOnClickListener {
            startActivity(Intent(this, FloatingScheduleActivity::class.java))
        }

        binding.fabSync.setOnClickListener {
            startActivity(Intent(this, WebScheduleActivity::class.java))
        }

        binding.timetableView.setOnCourseClickListener { course ->
            showCourseDetail(course)
        }
    }

    private fun updateWeekTitle() {
        val weekLabel = if (selectedWeek == actualCurrentWeek) {
            "第 $selectedWeek 周 (本周) ▼"
        } else {
            "第 $selectedWeek 周 ▼"
        }
        binding.tvMainWeekTitle.text = weekLabel
    }

    private fun showSelectWeekDialog() {
        val total = repository.getSemesterConfig().totalWeeks
        val items = Array(total) { i ->
            val w = i + 1
            if (w == actualCurrentWeek) "第 $w 周 (本周)" else "第 $w 周"
        }
        AlertDialog.Builder(this)
            .setTitle("跳转周次")
            .setSingleChoiceItems(items, selectedWeek - 1) { dialog, which ->
                selectedWeek = which + 1
                updateWeekTitle()
                updateWeekView()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadData() {
        val config = repository.getSemesterConfig()
        actualCurrentWeek = TimeUtils.getCurrentWeek(config)
        updateWeekTitle()
        updateWeekView()
    }

    private fun updateWeekView() {
        val courses = repository.getCoursesForWeek(selectedWeek)
        binding.timetableView.setCourses(courses, selectedWeek)
    }

    private fun showCourseDetail(course: Course) {
        val dialog = Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_course_detail, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.dialogCourseName).text = course.name
        view.findViewById<TextView>(R.id.dialogTime).text = "${course.dayName} ${course.getFormattedPeriodRange()} (${course.getFormattedTimeRange()})"
        view.findViewById<TextView>(R.id.dialogLocation).text = course.location.ifBlank { "待定" }

        val curTeacher = course.getTeacherForWeek(selectedWeek)
        view.findViewById<TextView>(R.id.dialogTeacher).text = "本周教师：$curTeacher"

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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "导入本地 HTML 课表文件")
        menu?.add(0, 2, 0, "设置默认教务网址")
        menu?.add(0, 3, 0, "设置开学日期")
        menu?.add(0, 4, 0, "恢复默认示例课表")
        menu?.add(0, 5, 0, "使用说明")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> pickHtmlLauncher.launch("text/html")
            2 -> showSetJwUrlDialog()
            3 -> showSemesterSettingDialog()
            4 -> {
                repository.resetToPresets()
                loadData()
                Toast.makeText(this, "已重置为通用示例课表", Toast.LENGTH_SHORT).show()
            }
            5 -> showHelpDialog()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun importHtmlFromUri(uri: Uri) {
        Toast.makeText(this, "正在读取并解析文件...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            var content = ""
            try {
                content = contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                } ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
            }

            var courses = TopsoftHtmlParser.parse(content)
            if (courses.isEmpty()) {
                try {
                    content = contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream, java.nio.charset.Charset.forName("GBK"))).readText()
                    } ?: ""
                    courses = TopsoftHtmlParser.parse(content)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                if (courses.isNotEmpty()) {
                    repository.saveCourses(courses)
                    loadData()
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("导入成功")
                        .setMessage("已成功解析并导入 ${courses.size} 门次课程。")
                        .setPositiveButton("确定", null)
                        .show()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("解析失败")
                        .setMessage("未能提取到课表数据，请确保选中的是教务系统课表导出的完整 HTML 文件。")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }

    private fun showSetJwUrlDialog() {
        val input = EditText(this)
        val saved = repository.getSavedJwUrl()
        input.setText(saved)
        input.hint = "https://..."
        AlertDialog.Builder(this)
            .setTitle("设置默认教务系统网址")
            .setView(input)
            .setPositiveButton("保存并前往") { _, _ ->
                val url = input.text.toString().trim()
                repository.saveJwUrl(url)
                startActivity(Intent(this, WebScheduleActivity::class.java))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSemesterSettingDialog() {
        val config = repository.getSemesterConfig()
        val cal = Calendar.getInstance()
        cal.timeInMillis = config.startDateMillis

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance()
                newCal.set(year, month, dayOfMonth, 0, 0, 0)
                val newConfig = config.copy(startDateMillis = newCal.timeInMillis)
                repository.saveSemesterConfig(newConfig)
                initViews()
                loadData()
                Toast.makeText(this, "开学日期已更新", Toast.LENGTH_SHORT).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("使用说明")
            .setMessage("""
                1. 本地导入：
                   右上角菜单 ->「导入本地 HTML 课表文件」，直接选取保存的课表网页。

                2. 网页抓取：
                   右下角「网页同步」，在内置浏览器中登录教务系统，到达课表页面后点击底部「抓取课表」。

                3. Panels / 快捷方式：
                   支持在 Panels 或桌面添加「悬浮课表」快捷方式，随时呼出完整周课表，点击空白处自动关闭。
            """.trimIndent())
            .setPositiveButton("确定", null)
            .show()
    }
}
