package com.topware.timetable.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
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

        @Volatile
        private var instance: ScheduleRepository? = null

        fun getInstance(context: Context): ScheduleRepository {
            return instance ?: synchronized(this) {
                instance ?: ScheduleRepository(context).also { instance = it }
            }
        }
    }

    fun getCourses(): List<Course> {
        val json = prefs.getString(KEY_COURSES, null)
        if (json.isNullOrBlank()) {
            val presets = PresetCourses.SAMPLE_COURSES
            saveCourses(presets)
            return presets
        }
        return try {
            val type = object : TypeToken<List<Course>>() {}.type
            gson.fromJson(json, type) ?: PresetCourses.SAMPLE_COURSES
        } catch (e: Exception) {
            PresetCourses.SAMPLE_COURSES
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

    // --- 账号密码保存与自动填充 ---
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
