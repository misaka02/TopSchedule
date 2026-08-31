package com.topware.timetable.data.parser

import com.topware.timetable.data.model.Course
import com.topware.timetable.data.model.TeacherAssignment
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object TopsoftHtmlParser {

    private val DAY_FIELDS = mapOf(
        "Monday" to Pair(1, "周一"),
        "Tuesday" to Pair(2, "周二"),
        "Wednesday" to Pair(3, "周三"),
        "Thursday" to Pair(4, "周四"),
        "Friday" to Pair(5, "周五"),
        "Saturday" to Pair(6, "周六"),
        "Sunday" to Pair(7, "周日")
    )

    private val BLACKLIST_NAMES = setOf(
        "考生来源", "考试方式", "最后学历学习形式", "校外导师", "学号", "姓名", "性别",
        "身份证号", "专业", "班级", "学院", "培养类别", "入学时间", "学籍状态", "导师",
        "出生日期", "民族", "政治面貌", "毕业学校", "籍贯", "个人信息", "基本信息"
    )

    fun parse(html: String): List<Course> {
        val courses = mutableListOf<Course>()
        if (html.isBlank()) return courses

        try {
            val doc: Document = Jsoup.parse(html)

            // 优先策略 1：直接定位所有 div.course_card 课程卡片（拓扑教务精准提取）
            val cards = doc.select("div[class*=course_card]")
            if (cards.isNotEmpty()) {
                for (card in cards) {
                    val name = card.selectFirst("span.course_name")?.text()?.trim()
                        ?: card.selectFirst("span.course_title")?.text()?.trim()
                        ?: ""
                    if (name.isBlank() || BLACKLIST_NAMES.contains(name) || name.length <= 1) continue

                    // 确定星期几
                    val td = card.parents().firstOrNull { it.tagName() == "td" }
                    val field = td?.attr("field") ?: ""
                    val (dayOfWeek, dayName) = DAY_FIELDS[field] ?: Pair(1, "周一")

                    // 确定默认节次
                    val tr = card.parents().firstOrNull { it.tagName() == "tr" }
                    val jieciTd = tr?.selectFirst("td[field=JieCi]")
                    val rowJieci = jieciTd?.text()?.trim() ?: "1,2"

                    val teacher = card.selectFirst("span.course_tea_name")?.text()?.trim() ?: ""
                    val dtls = card.select("span.course_dtl").map { it.text().trim() }

                    var jieciStr = rowJieci
                    var weeksStr = ""
                    var location = ""
                    var department = ""
                    var phone = ""

                    for (d in dtls) {
                        when {
                            d.startsWith("节次:") -> jieciStr = d.removePrefix("节次:").replace("节", "").trim()
                            d.startsWith("周次:") -> weeksStr = d.removePrefix("周次:").trim()
                            d.startsWith("地点:") -> location = d.removePrefix("地点:").trim()
                            d.startsWith("开课院系:") -> department = d.removePrefix("开课院系:").trim()
                            d.startsWith("电话:") -> phone = d.removePrefix("电话:").trim()
                        }
                    }

                    val (weeks, assignments) = parseWeeksAndTeacherAssignments(weeksStr, teacher)
                    val periods = Regex("""\d+""").findAll(jieciStr).map { it.value.toInt() }.toList()
                    val startPeriod = periods.minOrNull() ?: 1
                    val endPeriod = periods.maxOrNull() ?: startPeriod

                    val course = Course(
                        name = name,
                        teacher = teacher,
                        dayOfWeek = dayOfWeek,
                        dayName = dayName,
                        jieci = jieciStr,
                        startPeriod = startPeriod,
                        endPeriod = endPeriod,
                        periodCount = endPeriod - startPeriod + 1,
                        weeksStr = weeksStr,
                        weeks = weeks,
                        teacherAssignments = assignments,
                        location = location,
                        department = department,
                        phone = phone,
                        colorIndex = Math.abs(name.hashCode())
                    )
                    courses.add(course)
                }

                if (courses.isNotEmpty()) {
                    return courses.distinctBy { "${it.name}_${it.dayOfWeek}_${it.startPeriod}_${it.weeksStr}_${it.location}" }
                }
            }

            // 备用策略 2：通过星期表头匹配课表（排除个人信息等无关表格）
            val tables = doc.select("table")
            for (table in tables) {
                val headerText = table.text()
                var matchedDayCount = 0
                for (day in listOf("星期一", "星期二", "星期三", "星期四", "星期五")) {
                    if (headerText.contains(day)) matchedDayCount++
                }
                if (matchedDayCount < 3) continue

                val rows = table.select("tr")
                val dayColMap = mutableMapOf<Int, Pair<Int, String>>()
                var headerRow: Element? = null

                for (tr in rows) {
                    val cells = tr.select("th, td")
                    var count = 0
                    for ((idx, cell) in cells.withIndex()) {
                        val t = cell.text().trim()
                        val dayInfo = matchDayName(t)
                        if (dayInfo != null) {
                            dayColMap[idx] = dayInfo
                            count++
                        }
                    }
                    if (count >= 3) {
                        headerRow = tr
                        break
                    }
                }

                if (headerRow != null && dayColMap.isNotEmpty()) {
                    for (tr in rows) {
                        if (tr == headerRow) continue
                        val cells = tr.select("th, td")
                        if (cells.isEmpty()) continue

                        val firstText = cells.first()?.text()?.trim() ?: ""
                        val rowJieci = extractJieciFromText(firstText)

                        for ((colIdx, dayInfo) in dayColMap) {
                            if (colIdx < cells.size) {
                                val td = cells[colIdx]
                                val text = td.text().trim()
                                if (text.isNotBlank() && (text.contains("周") || text.contains("节"))) {
                                    parseCourseFromText(text, dayInfo.first, dayInfo.second, rowJieci)?.let {
                                        if (!BLACKLIST_NAMES.contains(it.name)) {
                                            courses.add(it)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return courses.distinctBy { "${it.name}_${it.dayOfWeek}_${it.startPeriod}_${it.weeksStr}_${it.location}" }
    }

    private fun matchDayName(text: String): Pair<Int, String>? {
        return when {
            text.contains("星期一") || text == "周一" || text == "一" -> Pair(1, "周一")
            text.contains("星期二") || text == "周二" || text == "二" -> Pair(2, "周二")
            text.contains("星期三") || text == "周三" || text == "三" -> Pair(3, "周三")
            text.contains("星期四") || text == "周四" || text == "四" -> Pair(4, "周四")
            text.contains("星期五") || text == "周五" || text == "五" -> Pair(5, "周五")
            text.contains("星期六") || text == "周六" || text == "六" -> Pair(6, "周六")
            text.contains("星期日") || text.contains("星期天") || text == "周日" || text == "日" -> Pair(7, "周日")
            else -> null
        }
    }

    private fun extractJieciFromText(text: String): String {
        val matches = Regex("""\d+""").findAll(text).map { it.value }.toList()
        return if (matches.isNotEmpty()) matches.joinToString(",") else "1,2"
    }

    fun parseWeeksAndTeacherAssignments(
        weeksStr: String,
        defaultTeacher: String
    ): Pair<List<Int>, List<TeacherAssignment>> {
        val totalWeeks = mutableSetOf<Int>()
        val assignments = mutableListOf<TeacherAssignment>()

        if (weeksStr.isBlank()) {
            return Pair((1..16).toList(), emptyList())
        }

        val parts = weeksStr.split(";", "，", "、", "；")
        for (rawPart in parts) {
            val part = rawPart.trim()
            if (part.isEmpty()) continue

            val teacherMatch = Regex("""\(([^)]+)\)""").find(part)
            val stageTeacher = if (teacherMatch != null) {
                teacherMatch.groupValues[1].trim()
            } else {
                defaultTeacher
            }

            val weekPartOnly = part.replace(Regex("""\([^)]*\)"""), "").replace("周", "").trim()
            val stageWeeks = mutableSetOf<Int>()

            for (sub in weekPartOnly.split(",")) {
                val item = sub.trim()
                if (item.isEmpty()) continue
                if (item.contains("-")) {
                    val m = Regex("""(\d+)-(\d+)""").find(item)
                    if (m != null) {
                        val s = m.groupValues[1].toInt()
                        val e = m.groupValues[2].toInt()
                        for (w in s..e) {
                            stageWeeks.add(w)
                            totalWeeks.add(w)
                        }
                    }
                } else {
                    val num = item.toIntOrNull()
                    if (num != null) {
                        stageWeeks.add(num)
                        totalWeeks.add(num)
                    }
                }
            }

            if (stageWeeks.isNotEmpty()) {
                val sortedStageWeeks = stageWeeks.sorted()
                assignments.add(
                    TeacherAssignment(
                        startWeek = sortedStageWeeks.first(),
                        endWeek = sortedStageWeeks.last(),
                        weeks = sortedStageWeeks,
                        teacherName = stageTeacher,
                        rawWeeksStr = part
                    )
                )
            }
        }

        val finalWeeks = if (totalWeeks.isNotEmpty()) totalWeeks.sorted() else (1..16).toList()
        return Pair(finalWeeks, assignments)
    }

    private fun parseCourseFromText(text: String, dayOfWeek: Int, dayName: String, rowJieci: String): Course? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val name = lines[0]
        if (BLACKLIST_NAMES.contains(name) || name.length <= 1) return null

        val teacher = if (lines.size > 1) lines[1] else ""
        var location = ""
        var weeksStr = ""
        for (line in lines) {
            if (line.contains("周")) weeksStr = line
            if (line.contains("号楼") || line.contains("#") || line.contains("馆") || line.contains("楼") || line.contains("室")) {
                location = line
            }
        }
        val (weeks, assignments) = parseWeeksAndTeacherAssignments(weeksStr, teacher)
        val periods = Regex("""\d+""").findAll(rowJieci).map { it.value.toInt() }.toList()
        val startPeriod = periods.minOrNull() ?: 1
        val endPeriod = periods.maxOrNull() ?: startPeriod

        return Course(
            name = name,
            teacher = teacher,
            dayOfWeek = dayOfWeek,
            dayName = dayName,
            jieci = rowJieci,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            periodCount = endPeriod - startPeriod + 1,
            weeksStr = weeksStr,
            weeks = weeks,
            teacherAssignments = assignments,
            location = location,
            colorIndex = Math.abs(name.hashCode())
        )
    }
}
