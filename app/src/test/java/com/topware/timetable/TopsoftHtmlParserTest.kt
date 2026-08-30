package com.topware.timetable

import com.topware.timetable.data.parser.TopsoftHtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopsoftHtmlParserTest {

    @Test
    fun testParseSampleTopsoftHtml() {
        val sampleHtml = """
            <html><body>
            <table class="grid">
                <tr>
                    <th name="JieCi">节次</th>
                    <th name="Time">时间</th>
                    <th name="Monday">星期一</th>
                    <th name="Tuesday">星期二</th>
                </tr>
                <tr>
                    <td field="JieCi">1,2</td>
                    <td field="Time">8:00-9:35</td>
                    <td field="Monday">
                        <div class="course_card course_card_blue">
                            <span class="course_title"><span class="course_name">高等应用数学</span></span>
                            <span class="course_tea_name">数学教学组</span>
                            <span class="course_dtl">节次:1,2节</span>
                            <span class="course_dtl">周次:1-1(教师A);2-5(教师B);7-7(教师B);8-9(教师C)</span>
                            <span class="course_dtl">地点:教学楼 101</span>
                            <span class="course_dtl">开课院系:理学院</span>
                            <span class="course_dtl">电话:010-12345678</span>
                        </div>
                    </td>
                    <td field="Tuesday">
                        <div class="course_card course_card_green">
                            <span class="course_title"><span class="course_name">学术论文写作</span></span>
                            <span class="course_tea_name">导师组 1班</span>
                            <span class="course_dtl">节次:1,2节</span>
                            <span class="course_dtl">周次:1-5,7-9</span>
                            <span class="course_dtl">地点:综合楼 201</span>
                            <span class="course_dtl">开课院系:研究生院</span>
                        </div>
                    </td>
                </tr>
            </table>
            </body></html>
        """.trimIndent()

        val courses = TopsoftHtmlParser.parse(sampleHtml)
        assertEquals(2, courses.size)

        val c1 = courses[0]
        assertEquals("高等应用数学", c1.name)
        assertEquals(1, c1.dayOfWeek)
        assertEquals(1, c1.startPeriod)
        assertEquals(2, c1.endPeriod)
        assertEquals("教学楼 101", c1.location)
        assertTrue(c1.weeks.contains(1))
        assertTrue(c1.weeks.contains(5))
        assertTrue(c1.weeks.contains(8))

        val c2 = courses[1]
        assertEquals("学术论文写作", c2.name)
        assertEquals(2, c2.dayOfWeek)
        assertEquals("综合楼 201", c2.location)
        assertTrue(c2.weeks.contains(3))
        assertTrue(c2.weeks.contains(9))
    }
}
