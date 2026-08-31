package com.topware.timetable.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.topware.timetable.data.model.Course
import com.topware.timetable.data.model.SemesterConfig
import com.topware.timetable.data.preset.PresetCourses

class ScheduleRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREF_NAME = "top_schedule_prefs"
        private const val KEY_COURSES = "key_courses_json"
        private const val KEY_SEMESTER_CONFIG = "key_semester_config"
        private const val KEY_SAVED_JW_URL = "key_saved_jw_url"
        private const val KEY_USE_DESKTOP_UA = "key_use_desktop_ua"
        private const val KEY_SAVED_USERNAME = "key_saved_username"
        private const val KEY_SAVED_PASSWORD = "key_saved_password"
        private const val KEY_REMEMBER_CREDENTIALS = "key_remember_credentials"
        private const val KEY_FLOATING_DEFAULT_TAB = "key_floating_default_tab" // 0: Today, 1: Week

        @Volatile
        private var instance: ScheduleRepository? = null

        fun getInstance(context: Context): ScheduleRepository {
            return instance ?: synchronized(this) {
                instance ?: ScheduleRepository(context).also { instance = it }
            }
        }
    }

    /**
     * 获取课表数据（具备向下兼容反序列化保护，绝对不会丢弃用户导入的数据）
     */
    fun getCourses(): List<Course> {
        val json = prefs.getString(KEY_COURSES, null)
        if (json.isNullOrBlank()) {
            val presets = PresetCourses.SAMPLE_COURSES
            saveCourses(presets)
            return presets
        }

        return try {
            val type = object : TypeToken<List<Course>>() {}.type
            val list: List<Course>? = gson.fromJson(json, type)
            if (!list.isNullOrEmpty()) {
                list
            } else {
                PresetCourses.SAMPLE_COURSES
            }
        } catch (e: Exception) {
            // 容错解析：防止因字段变更导致用户已有课表丢失
            try {
                val jsonArray = JsonParser.parseString(json).asJsonArray
                val fallbackList = mutableListOf<Course>()
                for (elem in jsonArray) {
                    val obj = elem.asJsonObject
                    val name = obj.get("name")?.asString ?: "课程"
                    val dayOfWeek = obj.get("dayOfWeek")?.asInt ?: 1
                    val dayName = obj.get("dayName")?.asString ?: ""
                    val jieci = obj.get("jieci")?.asString ?: ""
                    val startPeriod = obj.get("startPeriod")?.asInt ?: 1
                    val endPeriod = obj.get("endPeriod")?.asInt ?: startPeriod
                    val location = obj.get("location")?.asString ?: ""
                    val teacher = obj.get("teacher")?.asString ?: ""
                    val weeksStr = obj.get("weeksStr")?.asString ?: ""
                    val department = obj.get("department")?.asString ?: ""
                    val phone = obj.get("phone")?.asString ?: ""
                    val colorIndex = obj.get("colorIndex")?.asInt ?: Math.abs(name.hashCode())

                    val weeks = mutableListOf<Int>()
                    obj.get("weeks")?.asJsonArray?.forEach {
                        weeks.add(it.asInt)
                    }

                    fallbackList.add(
                        Course(
                            name = name,
                            teacher = teacher,
                            dayOfWeek = dayOfWeek,
                            dayName = dayName,
                            jieci = jieci,
                            startPeriod = startPeriod,
                            endPeriod = endPeriod,
                            periodCount = endPeriod - startPeriod + 1,
                            weeksStr = weeksStr,
                            weeks = weeks,
                            location = location,
                            department = department,
                            phone = phone,
                            colorIndex = colorIndex
                        )
                    )
                }
                if (fallbackList.isNotEmpty()) {
                    saveCourses(fallbackList)
                    fallbackList
                } else {
                    PresetCourses.SAMPLE_COURSES
                }
            } catch (e2: Exception) {
                PresetCourses.SAMPLE_COURSES
            }
        }
    }

    fun saveCourses(courses: List<Course>) {
        val json = gson.toJson(courses)
        prefs.edit().putString(KEY_COURSES, json).apply()
    }

    fun getSemesterConfig(): SemesterConfig {
        val json = prefs.getString(KEY_SEMESTER_CONFIG, null)
        if (json.isNullOrBlank()) {
            val default = SemesterConfig()
            saveSemesterConfig(default)
            return default
        }
        return try {
            gson.fromJson(json, SemesterConfig::class.java) ?: SemesterConfig()
        } catch (e: Exception) {
            SemesterConfig()
        }
    }

    fun saveSemesterConfig(config: SemesterConfig) {
        val json = gson.toJson(config)
        prefs.edit().putString(KEY_SEMESTER_CONFIG, json).apply()
    }

    fun getSavedJwUrl(): String {
        return prefs.getString(KEY_SAVED_JW_URL, "") ?: ""
    }

    fun saveJwUrl(url: String) {
        prefs.edit().putString(KEY_SAVED_JW_URL, url.trim()).apply()
    }

    fun isDesktopUaEnabled(): Boolean {
        return prefs.getBoolean(KEY_USE_DESKTOP_UA, true)
    }

    fun setDesktopUaEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_DESKTOP_UA, enabled).apply()
    }

    fun isRememberCredentials(): Boolean {
        return prefs.getBoolean(KEY_REMEMBER_CREDENTIALS, false)
    }

    fun getSavedUsername(): String {
        return if (isRememberCredentials()) prefs.getString(KEY_SAVED_USERNAME, "") ?: "" else ""
    }

    fun getSavedPassword(): String {
        return if (isRememberCredentials()) prefs.getString(KEY_SAVED_PASSWORD, "") ?: "" else ""
    }

    fun saveCredentials(username: String, password: String, remember: Boolean) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_REMEMBER_CREDENTIALS, remember)
        if (remember) {
            editor.putString(KEY_SAVED_USERNAME, username.trim())
            editor.putString(KEY_SAVED_PASSWORD, password)
        } else {
            editor.remove(KEY_SAVED_USERNAME)
            editor.remove(KEY_SAVED_PASSWORD)
        }
        editor.apply()
    }

    /**
     * 悬浮窗默认打开的视图：0 为当日课表，1 为周课表（默认直接打开周课表总览）
     */
    fun getFloatingDefaultTab(): Int {
        return prefs.getInt(KEY_FLOATING_DEFAULT_TAB, 0) // 默认直接打开 0 (当日课表聚焦)
    }

    fun setFloatingDefaultTab(tabIndex: Int) {
        prefs.edit().putInt(KEY_FLOATING_DEFAULT_TAB, tabIndex).apply()
    }

    fun getCoursesForWeek(week: Int): List<Course> {
        return getCourses().filter { it.isHappeningInWeek(week) }
    }

    fun getCoursesForDay(week: Int, dayOfWeek: Int): List<Course> {
        return getCourses()
            .filter { it.dayOfWeek == dayOfWeek && it.isHappeningInWeek(week) }
            .sortedBy { it.startPeriod }
    }

    fun resetToPresets() {
        saveCourses(PresetCourses.SAMPLE_COURSES)
    }
}
