package com.topware.timetable.data.parser

import com.topware.timetable.data.model.Course
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

            var table: Element? = null
            for (t in doc.select("table")) {
                if (t.select("th[name=Monday], td[field=Monday]").isNotEmpty()) {
                    table = t
                    break
                }
            }

            if (table == null) {
                for (t in doc.select("table")) {
                    val text = t.text()
                    if (text.contains("星期一") && (text.contains("1,2") || text.contains("节次"))) {
                        table = t
                        break
                    }
                }
            }

            if (table == null) {
                return emptyList()
            }

            for (tr in table.select("tr")) {
                val jieciTd = tr.selectFirst("td[field=JieCi]")
                val rowJieci = jieciTd?.text()?.trim() ?: ""

                for ((fieldName, dayOfWeek, dayName) in DAY_FIELDS) {
                    val td = tr.selectFirst("td[field=$fieldName]") ?: continue

                    val cards = td.select("div[class*=course_card]")
                    if (cards.isEmpty()) {
                        val text = td.text().trim()
                        if (text.isNotBlank() && (text.contains("节") || text.contains("周"))) {
                            parseCourseFromText(text, dayOfWeek, dayName, rowJieci)?.let {
                                courses.add(it)
                            }
                        }
                        continue
                    }

                    for (card in cards) {
                        val name = card.selectFirst("span.course_name")?.text()?.trim()
                            ?: card.selectFirst("span.course_title")?.text()?.trim()
                            ?: ""
                        if (name.isBlank()) continue

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

                        val weeks = parseWeeks(weeksStr)
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
                            location = location,
                            department = department,
                            phone = phone,
                            colorIndex = Math.abs(name.hashCode())
                        )
                        courses.add(course)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return courses
    }

    private fun parseWeeks(weeksStr: String): List<Int> {
        val result = mutableSetOf<Int>()
        val cleaned = weeksStr.replace(Regex("""\([^)]*\)"""), "")
        for (part in cleaned.split(";", "，", "、")) {
            for (sub in part.split(",")) {
                val item = sub.trim()
                if (item.isEmpty()) continue
                if (item.contains("-")) {
                    val m = Regex("""(\d+)-(\d+)""").find(item)
                    if (m != null) {
                        val start = m.groupValues[1].toInt()
                        val end = m.groupValues[2].toInt()
                        for (w in start..end) {
                            result.add(w)
                        }
                    }
                } else {
                    val num = item.toIntOrNull()
                    if (num != null) {
                        result.add(num)
                    }
                }
            }
        }
        return result.sorted()
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
            if (line.contains("号楼") || line.contains("#") || line.contains("馆") || line.contains("楼")) {
                location = line
            }
        }
        val weeks = parseWeeks(weeksStr)
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
            location = location,
            colorIndex = Math.abs(name.hashCode())
        )
    }
}
