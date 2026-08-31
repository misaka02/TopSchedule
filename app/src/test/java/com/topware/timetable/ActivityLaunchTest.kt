package com.topware.timetable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.topware.timetable.data.model.Course
import com.topware.timetable.ui.floating.FloatingScheduleActivity
import com.topware.timetable.ui.main.MainActivity
import com.topware.timetable.ui.view.TimetableView
import com.topware.timetable.ui.webview.WebScheduleActivity
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ActivityLaunchTest {

    @Test
    fun testMainActivityLifecycleAndViews() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        assertNotNull(activity)

        val timetableView = activity.findViewById<TimetableView>(R.id.timetableView)
        assertNotNull(timetableView)

        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        timetableView.draw(canvas)

        controller.pause().stop().destroy()
    }

    @Test
    fun testFloatingScheduleActivityLifecycleAndViews() {
        val controller = Robolectric.buildActivity(FloatingScheduleActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        assertNotNull(activity)

        val viewPager = activity.findViewById<ViewPager2>(R.id.viewPagerFloating)
        assertNotNull(viewPager)

        // 切换到周课表页并验证
        viewPager.setCurrentItem(1, false)

        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val timetableView = activity.findViewById<TimetableView>(R.id.floatingTimetableView)
        timetableView?.draw(canvas)

        controller.pause().stop().destroy()
    }

    @Test
    fun testWebScheduleActivityLifecycle() {
        val controller = Robolectric.buildActivity(WebScheduleActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }

    @Test
    fun testTimetableViewDrawWithExtremeHashCodes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = TimetableView(context)

        val extremeCourses = listOf(
            Course(name = "Extreme 1", colorIndex = Int.MIN_VALUE, startPeriod = 1, endPeriod = 2, dayOfWeek = 1, weeks = listOf(1)),
            Course(name = "Extreme 2", colorIndex = -999999, startPeriod = 3, endPeriod = 4, dayOfWeek = 2, weeks = listOf(1)),
            Course(name = "Extreme 3", colorIndex = Int.MAX_VALUE, startPeriod = 5, endPeriod = 6, dayOfWeek = 3, weeks = listOf(1)),
            Course(name = "Extreme 4", colorIndex = 0, startPeriod = 7, endPeriod = 8, dayOfWeek = 4, weeks = listOf(1))
        )

        view.setCourses(extremeCourses, 1)
        view.measure(1080, 2000)
        view.layout(0, 0, 1080, 2000)

        val bitmap = Bitmap.createBitmap(1080, 2000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
    }
}
