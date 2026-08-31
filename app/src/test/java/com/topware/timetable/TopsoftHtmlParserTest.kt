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
            <table>
                <colgroup class="WcolZgroupS">
                    <col name="JieCi" width="40"/>
                    <col name="Time" width="120"/>
                    <col name="Monday" width="120"/>
                    <col name="Tuesday" width="120"/>
                </colgroup>
                <thead class="WtheZadS">
                    <tr>
                        <th name="JieCi">节次</th>
                        <th name="Time">上课时间</th>
                        <th name="Monday">星期一</th>
                        <th name="Tuesday">星期二</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td field="JieCi">1,2</td>
                        <td field="Time">08:00-09:35</td>
                        <td field="Monday">
                            <div class="course_card course_card_blue hint--medium hint--right" data-hint="结构轻量化设计基础&#10;1班(一组教师)&#10;节次:1,2节&#10;周次:1-1(周志);2-5(温水);7-7(温水);8-9(温)&#10;地点:11#5010&#10;开课院系:航天学院&#10;电话:82589241">
                                <span class="course_title"><span class="course_name">结构轻量化设计基础</span></span>
                                <span class="course_tea_name">1班(一组教师)</span>
                                <span class="course_dtl">节次:1,2节</span>
                                <span class="course_dtl">周次:1-1(周志);2-5(温水);7-7(温水);8-9(温)</span>
                                <span class="course_dtl">地点:11#5010</span>
                                <span class="course_dtl">开课院系:航天学院</span>
                                <span class="course_dtl">电话:82589241</span>
                            </div>
                        </td>
                        <td field="Tuesday">
                            <div class="course_card course_card_green" data-hint="张量分析&#10;齐辉 1班&#10;节次:1,2节&#10;周次:1-5,7-9&#10;地点:11#0142&#10;开课院系:航天学院&#10;电话:82589241">
                                <span class="course_title"><span class="course_name">张量分析</span></span>
                                <span class="course_tea_name">齐辉 1班</span>
                                <span class="course_dtl">节次:1,2节</span>
                                <span class="course_dtl">周次:1-5,7-9</span>
                                <span class="course_dtl">地点:11#0142</span>
                                <span class="course_dtl">开课院系:航天学院</span>
                                <span class="course_dtl">电话:82589241</span>
                            </div>
                        </td>
                    </tr>
                </tbody>
            </table>
            </body></html>
        """.trimIndent()

        val courses = TopsoftHtmlParser.parse(sampleHtml)
        assertEquals(2, courses.size)

        val c1 = courses[0]
        assertEquals("结构轻量化设计基础", c1.name)
        assertEquals(1, c1.dayOfWeek)
        assertEquals(1, c1.startPeriod)
        assertEquals(2, c1.endPeriod)
        assertEquals("11#5010", c1.location)
        assertTrue(c1.weeks.contains(1))
        assertTrue(c1.weeks.contains(5))
        assertTrue(c1.weeks.contains(8))

        // 验证各周次分段教师提取
        assertEquals("周志", c1.getTeacherForWeek(1))
        assertEquals("温水", c1.getTeacherForWeek(2))
        assertEquals("温水", c1.getTeacherForWeek(5))
        assertEquals("温水", c1.getTeacherForWeek(7))
        assertEquals("温", c1.getTeacherForWeek(8))
        assertEquals("温", c1.getTeacherForWeek(9))

        val c2 = courses[1]
        assertEquals("张量分析", c2.name)
        assertEquals(2, c2.dayOfWeek)
        assertEquals("11#0142", c2.location)
        assertTrue(c2.weeks.contains(3))
        assertTrue(c2.weeks.contains(9))
        assertEquals("齐辉 1班", c2.getTeacherForWeek(3))
    }
}
