package com.topware.timetable.ui.main

import android.app.AlertDialog
import android.app.DatePickerDialog
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
import com.google.android.material.chip.Chip
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
    private var currentWeek: Int = 1
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
        currentWeek = TimeUtils.getCurrentWeek(config)
        selectedWeek = currentWeek

        binding.chipGroupWeeks.removeAllViews()
        for (w in 1..config.totalWeeks) {
            val chip = Chip(this).apply {
                text = "第 $w 周"
                isCheckable = true
                id = w
                if (w == currentWeek) {
                    text = "第 $w 周 (本周)"
                }
                setOnClickListener {
                    selectedWeek = w
                    updateWeekView()
                }
            }
            binding.chipGroupWeeks.addView(chip)
        }
        binding.chipGroupWeeks.check(selectedWeek)

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

    private fun loadData() {
        val config = repository.getSemesterConfig()
        currentWeek = TimeUtils.getCurrentWeek(config)
        updateWeekView()
    }

    private fun updateWeekView() {
        val count = repository.getCoursesForWeek(selectedWeek).size
        binding.tvCurrentWeekInfo.text = "当前：第 $selectedWeek 周 · 共 $count 节课程"
        binding.timetableView.setCourses(repository.getCoursesForWeek(selectedWeek), selectedWeek)
    }

    private fun showCourseDetail(course: Course) {
        val dialog = android.app.Dialog(this)
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
        menu?.add(0, 5, 0, "使用指引与快捷方式")
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
                        .setTitle("文件导入成功")
                        .setMessage("已成功从本地 HTML 文件中解析并导入 ${courses.size} 门次课程。\n主课表与悬浮窗已全部同步更新。")
                        .setPositiveButton("完成", null)
                        .show()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("解析未成功")
                        .setMessage("未能从所选 HTML 文件中提取到有效课表数据。请确保选中的文件是教务系统【学生课表】导出的完整网页。")
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
        input.hint = "https://your-school-jw-system-url..."
        AlertDialog.Builder(this)
            .setTitle("设置默认教务系统登录网址")
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
            .setTitle("使用指引")
            .setMessage("""
                1. 本地 HTML 文件导入：
                   点击右上角菜单 ->「导入本地 HTML 课表文件」，在手机文件管理器中选取保存好的课表网页文件即可极速导入。

                2. 网页一键抓取：
                   点击右下角「进入网页更新」，输入教务系统网址并登录。进入课表页面后点击底部「一键智能抓取」即可同步。

                3. Panels / 快捷方式调用：
                   在 Panels 侧边栏或其他手势应用中，添加快捷方式 (Shortcut) 并选择「悬浮课表」，即可随时随地在任何界面上方悬浮唤起课表，点击外部空白处或按返回键瞬间关闭。
            """.trimIndent())
            .setPositiveButton("确定", null)
            .show()
    }
}
