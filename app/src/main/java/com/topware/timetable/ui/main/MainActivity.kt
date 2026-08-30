package com.topware.timetable.ui.main

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
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

    // 系统文件选择器：支持直接选择手机中保存的 .html / .htm 课表网页文件
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
                    text = "第 $w 周(本周)"
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
        binding.tvCurrentWeekInfo.text = "当前查看：第 $selectedWeek 周 · 共 ${repository.getCoursesForWeek(selectedWeek).size} 节课"
        binding.timetableView.setCourses(repository.getCoursesForWeek(selectedWeek))
    }

    private fun showCourseDetail(course: Course) {
        AlertDialog.Builder(this)
            .setTitle(course.name)
            .setMessage("""
                ⏰ 时间：${course.dayName} 第${course.jieci}节 (${course.getFormattedTime()})
                📍 地点：${course.location.ifBlank { "待定" }}
                👨‍🏫 教师：${course.teacher.ifBlank { "待定" }}
                📅 周次：${course.weeksStr.ifBlank { "全周" }}
                🏛️ 院系：${course.department.ifBlank { "教务处" }}
                📞 电话：${course.phone.ifBlank { "暂无" }}
            """.trimIndent())
            .setPositiveButton("知道了", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "📂 导入本地 HTML 课表文件")
        menu?.add(0, 2, 0, "🌐 设置默认教务网址")
        menu?.add(0, 3, 0, "📅 设置开学时间")
        menu?.add(0, 4, 0, "🔄 恢复默认示例课表")
        menu?.add(0, 5, 0, "💡 使用说明与Panels配置")
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
                // 1. 读取文件流（优先 UTF-8）
                content = contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                } ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
            }

            var courses = TopsoftHtmlParser.parse(content)

            // 如果 UTF-8 未解析出结果，尝试 GBK
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
                        .setTitle("🎉 文件导入成功")
                        .setMessage("已成功从本地 HTML 文件中解析并导入 ${courses.size} 门次课程！\n主课表与悬浮窗已全部同步更新。")
                        .setPositiveButton("太棒了", null)
                        .show()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("⚠️ 解析失败")
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
                Toast.makeText(this, "开学时间已更新", Toast.LENGTH_SHORT).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("💡 悬浮课表与使用指引")
            .setMessage("""
                1. 【本地 HTML 文件直接导入】：
                   点击右上角菜单 ->「📂 导入本地 HTML 课表文件」，直接在手机文件管理器中选择保存好的课表网页文件即可极速导入！

                2. 【网页一键同步】：
                   点击右下角「进入网页更新」，输入教务登录网址（支持点击星星设为默认），登录进入课表页后点击底部一键抓取即可！

                3. 【Panels / 边缘手势调用】：
                   在 Panels 侧边栏或其他手势应用中，添加快捷方式 (Shortcut) 并选择「悬浮课表」，即可随时随地在任何界面上方悬浮唤起课表，点击外部空白处瞬间关闭！
            """.trimIndent())
            .setPositiveButton("学会了", null)
            .show()
    }
}
