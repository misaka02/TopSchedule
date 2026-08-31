package com.topware.timetable.data.parser

import com.topware.timetable.data.model.Course
import com.topware.timetable.data.model.TeacherAssignment
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object TopsoftHtmlParser {

    private val DAY_FIELDS = listOf(
        Triple("Monday", 1, "周一"),
        Triple("Tuesday", 2, "周二"),
        Triple("Wednesday", 3, "周三"),
        Triple("Thursday", 4, "周四"),
        Triple("Friday", 5, "周五"),
        Triple("Saturday", 6, "周六"),
        Triple("Sunday", 7, "周日")
    )

    fun parse(html: String): List<Course> {
        val courses = mutableListOf<Course>()
        if (html.isBlank()) return courses

        try {
            val doc: Document = Jsoup.parse(html)

            // 1. 寻找所有可能的课表表格
            val tables = doc.select("table")
            for (table in tables) {
                val parsed = parseTable(table)
                if (parsed.isNotEmpty()) {
                    courses.addAll(parsed)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 去重合并
        return courses.distinctBy { "${it.name}_${it.dayOfWeek}_${it.startPeriod}_${it.weeksStr}_${it.location}" }
    }

    private fun parseTable(table: Element): List<Course> {
        val list = mutableListOf<Course>()

        // 策略 A: 基于 Topsoft 经典 field 属性解析
        if (table.select("th[name=Monday], td[field=Monday]").isNotEmpty() || table.select("td[field=JieCi]").isNotEmpty()) {
            for (tr in table.select("tr")) {
                val jieciTd = tr.selectFirst("td[field=JieCi]")
                val rowJieci = jieciTd?.text()?.trim() ?: ""

                for ((fieldName, dayOfWeek, dayName) in DAY_FIELDS) {
                    val td = tr.selectFirst("td[field=$fieldName]") ?: continue
                    parseTdCell(td, dayOfWeek, dayName, rowJieci, list)
                }
            }
            if (list.isNotEmpty()) return list
        }

        // 策略 B: 通用星期表头列索引映射解析
        val rows = table.select("tr")
        if (rows.isEmpty()) return emptyList()

        var headerRow: Element? = null
        val dayColMap = mutableMapOf<Int, Pair<Int, String>>() // colIndex -> (dayOfWeek, dayName)

        for (tr in rows) {
            val cells = tr.select("th, td")
            var matchedDays = 0
            for ((idx, cell) in cells.withIndex()) {
                val t = cell.text().trim()
                val dayInfo = matchDayName(t)
                if (dayInfo != null) {
                    dayColMap[idx] = dayInfo
                    matchedDays++
                }
            }
            if (matchedDays >= 3) {
                headerRow = tr
                break
            }
        }

        if (headerRow != null && dayColMap.isNotEmpty()) {
            for (tr in rows) {
                if (tr == headerRow) continue
                val cells = tr.select("th, td")
                if (cells.isEmpty()) continue

                // 提取首列节次
                val firstText = cells.first()?.text()?.trim() ?: ""
                val rowJieci = extractJieciFromText(firstText)

                for ((colIdx, dayInfo) in dayColMap) {
                    if (colIdx < cells.size) {
                        val td = cells[colIdx]
                        parseTdCell(td, dayInfo.first, dayInfo.second, rowJieci, list)
                    }
                }
            }
        }

        return list
    }

    private fun matchDayName(text: String): Pair<Int, String>? {
        return when {
            text.contains("一") -> Pair(1, "周一")
            text.contains("二") -> Pair(2, "周二")
            text.contains("三") -> Pair(3, "周三")
            text.contains("四") -> Pair(4, "周四")
            text.contains("五") -> Pair(5, "周五")
            text.contains("六") -> Pair(6, "周六")
            text.contains("日") || text.contains("天") || text.contains("七") -> Pair(7, "周日")
            else -> null
        }
    }

    private fun extractJieciFromText(text: String): String {
        val matches = Regex("""\d+""").findAll(text).map { it.value }.toList()
        return if (matches.isNotEmpty()) matches.joinToString(",") else "1,2"
    }

    private fun parseTdCell(
        td: Element,
        dayOfWeek: Int,
        dayName: String,
        rowJieci: String,
        outList: MutableList<Course>
    ) {
        val cards = td.select("div[class*=course_card], div[class*=card], div[class*=kb_item]")
        if (cards.isEmpty()) {
            val text = td.text().trim()
            if (text.isNotBlank() && (text.contains("周") || text.contains("节") || text.length >= 4)) {
                parseCourseFromText(text, dayOfWeek, dayName, rowJieci)?.let {
                    outList.add(it)
                }
            }
            return
        }

        for (card in cards) {
            val name = card.selectFirst("span.course_name")?.text()?.trim()
                ?: card.selectFirst("span.course_title")?.text()?.trim()
                ?: card.selectFirst(".title")?.text()?.trim()
                ?: ""
            if (name.isBlank()) continue

            val teacher = card.selectFirst("span.course_tea_name")?.text()?.trim()
                ?: card.selectFirst(".teacher")?.text()?.trim()
                ?: ""

            val dtls = card.select("span.course_dtl, .dtl, div, p").map { it.text().trim() }

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
                    d.contains("周") && weeksStr.isEmpty() -> weeksStr = d.replace("周次:", "").trim()
                    (d.contains("楼") || d.contains("#") || d.contains("馆") || d.contains("室")) && location.isEmpty() -> location = d.replace("地点:", "").trim()
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
            outList.add(course)
        }
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
