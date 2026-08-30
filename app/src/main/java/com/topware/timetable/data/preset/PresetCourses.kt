package com.topware.timetable.data.preset

import com.topware.timetable.data.model.Course

object PresetCourses {

    val SAMPLE_COURSES = listOf(
        Course(
            name = "高等应用数学",
            teacher = "数学教学团队",
            dayOfWeek = 1,
            dayName = "周一",
            jieci = "1,2",
            startPeriod = 1,
            endPeriod = 2,
            periodCount = 2,
            weeksStr = "1-16周",
            weeks = (1..16).toList(),
            location = "教学楼 101",
            department = "理学院",
            phone = "010-88888888",
            colorIndex = 0
        ),
        Course(
            name = "学术论文写作与规范",
            teacher = "研究生导师组",
            dayOfWeek = 2,
            dayName = "周二",
            jieci = "1,2",
            startPeriod = 1,
            endPeriod = 2,
            periodCount = 2,
            weeksStr = "1-8周",
            weeks = (1..8).toList(),
            location = "综合楼 201",
            department = "研究生院",
            phone = "010-88888888",
            colorIndex = 1
        ),
        Course(
            name = "学术英语与学术交流",
            teacher = "外语教学部",
            dayOfWeek = 4,
            dayName = "周四",
            jieci = "1,2",
            startPeriod = 1,
            endPeriod = 2,
            periodCount = 2,
            weeksStr = "1-16周",
            weeks = (1..16).toList(),
            location = "教学楼 305",
            department = "外国语学院",
            phone = "010-88888888",
            colorIndex = 2
        ),
        Course(
            name = "高级人工智能与机器学习",
            teacher = "计算机系教师组",
            dayOfWeek = 1,
            dayName = "周一",
            jieci = "3,4",
            startPeriod = 3,
            endPeriod = 4,
            periodCount = 2,
            weeksStr = "1-12周",
            weeks = (1..12).toList(),
            location = "计算机楼 402",
            department = "计算机学院",
            phone = "010-88888888",
            colorIndex = 3
        ),
        Course(
            name = "新时代中国特色社会主义理论与实践",
            teacher = "思政教师组",
            dayOfWeek = 3,
            dayName = "周三",
            jieci = "3,4,5",
            startPeriod = 3,
            endPeriod = 5,
            periodCount = 3,
            weeksStr = "1-8周",
            weeks = (1..8).toList(),
            location = "大礼堂 101",
            department = "马克思主义学院",
            phone = "010-88888888",
            colorIndex = 4
        ),
        Course(
            name = "数值分析与科学计算",
            teacher = "计算中心教师组",
            dayOfWeek = 4,
            dayName = "周四",
            jieci = "6,7",
            startPeriod = 6,
            endPeriod = 7,
            periodCount = 2,
            weeksStr = "1-16周",
            weeks = (1..16).toList(),
            location = "实验楼 508",
            department = "智能学院",
            phone = "010-88888888",
            colorIndex = 5
        )
    )
}
