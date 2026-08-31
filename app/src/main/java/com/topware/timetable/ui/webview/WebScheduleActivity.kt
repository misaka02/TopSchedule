package com.topware.timetable.ui.webview

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.topware.timetable.R
import com.topware.timetable.data.model.Course
import com.topware.timetable.data.parser.TopsoftHtmlParser
import com.topware.timetable.data.repository.ScheduleRepository
import com.topware.timetable.databinding.ActivityWebScheduleBinding

class WebScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebScheduleBinding
    private lateinit var repository: ScheduleRepository
    private var isDesktopUa: Boolean = true
    private var defaultMobileUa: String = ""

    private val selectHtmlLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importFromUri(it) }
    }

    companion object {
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ScheduleRepository.getInstance(this)
        isDesktopUa = repository.isDesktopUaEnabled()

        setupListeners()
        setupCredentials()
        setupWebView()

        val savedUrl = repository.getSavedJwUrl()
        if (savedUrl.isNotBlank()) {
            binding.etUrlInput.setText(savedUrl)
            loadUrl(savedUrl)
            updateBookmarkIcon(true)
        } else {
            updateBookmarkIcon(false)
            binding.etUrlInput.requestFocus()
        }
    }

    private fun importFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val html = stream.bufferedReader().readText()
                val courses = TopsoftHtmlParser.parse(html)
                if (courses.isNotEmpty()) {
                    repository.saveCourses(courses)
                    AlertDialog.Builder(this)
                        .setTitle("本地课表导入成功")
                        .setMessage("成功从本地网页解析到 " + courses.size + " 门次课程。\n主课表与悬浮窗已全部同步更新。")
                        .setPositiveButton("查看") { _, _ -> finish() }
                        .show()
                } else {
                    Toast.makeText(this, "未能从选中的文件中识别到课表表格", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "读取文件失败：" + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCredentials() {
        binding.cbRememberPwd.isChecked = repository.isRememberCredentials()
        binding.etUsername.setText(repository.getSavedUsername())
        binding.etPassword.setText(repository.getSavedPassword())

        binding.btnAutoFill.setOnClickListener {
            val user = binding.etUsername.text.toString().trim()
            val pwd = binding.etPassword.text.toString()
            val remember = binding.cbRememberPwd.isChecked

            repository.saveCredentials(user, pwd, remember)

            val js = """
                (function() {
                    var userInputs = document.querySelectorAll('input[type="text"], input[name*="user"], input[name*="account"], input[name*="name"], input[id*="user"], input[id*="account"]');
                    for (var i = 0; i < userInputs.length; i++) {
                        userInputs[i].value = '$user';
                        userInputs[i].dispatchEvent(new Event('input', { bubbles: true }));
                        userInputs[i].dispatchEvent(new Event('change', { bubbles: true }));
                    }
                    var pwdInputs = document.querySelectorAll('input[type="password"], input[name*="pwd"], input[name*="pass"], input[id*="pwd"], input[id*="pass"]');
                    for (var j = 0; j < pwdInputs.length; j++) {
                        pwdInputs[j].value = '$pwd';
                        pwdInputs[j].dispatchEvent(new Event('input', { bubbles: true }));
                        pwdInputs[j].dispatchEvent(new Event('change', { bubbles: true }));
                    }
                })();
            """.trimIndent()
            binding.webView.evaluateJavascript(js, null)
            Toast.makeText(this, "已自动填入账号密码", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupListeners() {
        binding.btnWebBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                finish()
            }
        }

        binding.etUrlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                val url = binding.etUrlInput.text.toString().trim()
                if (url.isNotEmpty()) loadUrl(url)
                true
            } else {
                false
            }
        }

        binding.btnToggleUa.setOnClickListener {
            isDesktopUa = !isDesktopUa
            repository.setDesktopUaEnabled(isDesktopUa)
            applyUserAgentAndScale()
            val modeName = if (isDesktopUa) "电脑桌面版 (PC)" else "手机移动版 (Mobile)"
            Toast.makeText(this, "已切换为 $modeName", Toast.LENGTH_SHORT).show()
            val curUrl = binding.webView.url ?: binding.etUrlInput.text.toString().trim()
            if (curUrl.isNotBlank()) {
                loadUrl(curUrl)
            } else {
                binding.webView.reload()
            }
        }

        binding.btnSaveBookmark.setOnClickListener {
            val currentUrl = binding.webView.url ?: binding.etUrlInput.text.toString().trim()
            if (currentUrl.isNotBlank()) {
                val saved = repository.getSavedJwUrl()
                if (saved == currentUrl) {
                    repository.saveJwUrl("")
                    updateBookmarkIcon(false)
                    Toast.makeText(this, "已取消默认地址", Toast.LENGTH_SHORT).show()
                } else {
                    repository.saveJwUrl(currentUrl)
                    updateBookmarkIcon(true)
                    Toast.makeText(this, "已设为默认教务地址", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnExtractSchedule.setOnClickListener {
            extractScheduleHtml()
        }

        binding.btnExtractSchedule.setOnLongClickListener {
            selectHtmlLauncher.launch("*/*")
            true
        }
    }

    private fun updateBookmarkIcon(isSaved: Boolean) {
        if (isSaved) {
            binding.btnSaveBookmark.setImageResource(R.drawable.ic_star_filled)
        } else {
            binding.btnSaveBookmark.setImageResource(R.drawable.ic_star_outline)
        }
    }

    private fun updateUaButton() {
        if (isDesktopUa) {
            binding.btnToggleUa.text = "电脑版"
        } else {
            binding.btnToggleUa.text = "手机版"
        }
    }

    private fun getDesktopHeaders(): Map<String, String> {
        if (!isDesktopUa) return emptyMap()
        return mapOf(
            "User-Agent" to DESKTOP_UA,
            "Sec-CH-UA" to "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"",
            "Sec-CH-UA-Mobile" to "?0",
            "Sec-CH-UA-Platform" to "\"Windows\"",
            "Sec-CH-UA-Platform-Version" to "\"15.0.0\"",
            "Sec-CH-UA-Model" to "\"\"",
            "Upgrade-Insecure-Requests" to "1",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language" to "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7"
        )
    }

    private fun applyUserAgentAndScale() {
        val settings = binding.webView.settings
        if (isDesktopUa) {
            settings.userAgentString = DESKTOP_UA
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
        } else {
            settings.userAgentString = defaultMobileUa.ifEmpty { null }
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
        }
        updateUaButton()
    }

    private fun injectDesktopEnvironment() {
        if (!isDesktopUa) return
        val js = """
            (function() {
                try {
                    Object.defineProperty(navigator, 'userAgent', {
                        get: function() { return '$DESKTOP_UA'; },
                        configurable: true
                    });
                    Object.defineProperty(navigator, 'appVersion', {
                        get: function() { return '5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36'; },
                        configurable: true
                    });
                    Object.defineProperty(navigator, 'platform', {
                        get: function() { return 'Win32'; },
                        configurable: true
                    });
                    Object.defineProperty(navigator, 'vendor', {
                        get: function() { return 'Google Inc.'; },
                        configurable: true
                    });
                    Object.defineProperty(navigator, 'maxTouchPoints', {
                        get: function() { return 0; },
                        configurable: true
                    });
                    if (navigator.userAgentData) {
                        Object.defineProperty(navigator, 'userAgentData', {
                            get: function() {
                                return {
                                    brands: [
                                        { brand: 'Chromium', version: '128' },
                                        { brand: 'Google Chrome', version: '128' },
                                        { brand: 'Not;A=Brand', version: '24' }
                                    ],
                                    mobile: false,
                                    platform: 'Windows'
                                };
                            },
                            configurable: true
                        });
                    }

                    var metas = document.getElementsByTagName('meta');
                    for (var i = 0; i < metas.length; i++) {
                        if (metas[i].name === 'viewport') {
                            metas[i].setAttribute('content', 'width=1100, user-scalable=yes');
                            break;
                        }
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(js, null)
    }

    private fun loadUrl(rawUrl: String) {
        var formatted = rawUrl.trim()
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = "https://$formatted"
        }
        val headers = getDesktopHeaders()
        if (headers.isNotEmpty()) {
            binding.webView.loadUrl(formatted, headers)
        } else {
            binding.webView.loadUrl(formatted)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings: WebSettings = binding.webView.settings
        defaultMobileUa = settings.userAgentString

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowContentAccess = true
        settings.allowFileAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        applyUserAgentAndScale()

        binding.webView.addJavascriptInterface(ScheduleJsBridge(this), "ScheduleBridge")

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                if (newProgress >= 20) {
                    injectDesktopEnvironment()
                }
                if (newProgress == 100) {
                    binding.progressBar.visibility = android.view.View.GONE
                    injectDesktopEnvironment()
                } else {
                    binding.progressBar.visibility = android.view.View.VISIBLE
                }
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val headers = getDesktopHeaders()
                    if (headers.isNotEmpty() && request.requestHeaders["Sec-CH-UA-Mobile"] != "?0") {
                        view?.loadUrl(url, headers)
                        return true
                    }
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectDesktopEnvironment()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectDesktopEnvironment()
                url?.let {
                    if (!binding.etUrlInput.hasFocus()) {
                        binding.etUrlInput.setText(it)
                    }
                    val saved = repository.getSavedJwUrl()
                    updateBookmarkIcon(saved.isNotBlank() && (saved == it || it.startsWith(saved)))
                }
            }
        }
    }

    private fun extractScheduleHtml() {
        Toast.makeText(this, "正在深度递归抓取所有框架课表...", Toast.LENGTH_SHORT).show()
        val js = """
            (function() {
                var collectedCourses = [];
                var allHtml = '';

                function inspectWindow(win) {
                    if (!win) return;
                    try {
                        var doc = win.document;
                        if (!doc) return;
                        allHtml += doc.documentElement.outerHTML + '
';

                        // 查找包含课程文本的所有卡片元素
                        var elements = doc.querySelectorAll('div, td');
                        for (var i = 0; i < elements.length; i++) {
                            var el = elements[i];
                            var cls = el.className || '';
                            if (typeof cls !== 'string') cls = '';
                            var txt = el.innerText || '';

                            var isCard = (cls.indexOf('course_card') !== -1) || 
                                         (txt.indexOf('节次:') !== -1 && txt.indexOf('周次:') !== -1);

                            if (isCard && txt.length > 5) {
                                var td = el.tagName === 'TD' ? el : el.closest('td');
                                var dayOfWeek = 1;
                                var dayName = '周一';

                                if (td) {
                                    var field = td.getAttribute('field') || '';
                                    if (field === 'Monday') { dayOfWeek = 1; dayName = '周一'; }
                                    else if (field === 'Tuesday') { dayOfWeek = 2; dayName = '周二'; }
                                    else if (field === 'Wednesday') { dayOfWeek = 3; dayName = '周三'; }
                                    else if (field === 'Thursday') { dayOfWeek = 4; dayName = '周四'; }
                                    else if (field === 'Friday') { dayOfWeek = 5; dayName = '周五'; }
                                    else if (field === 'Saturday') { dayOfWeek = 6; dayName = '周六'; }
                                    else if (field === 'Sunday') { dayOfWeek = 7; dayName = '周日'; }
                                    else {
                                        var idx = td.cellIndex;
                                        if (idx !== undefined && idx >= 2 && idx <= 8) {
                                            dayOfWeek = idx - 1;
                                            var names = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'];
                                            dayName = names[dayOfWeek] || '周一';
                                        }
                                    }
                                }

                                var lines = txt.split('
').map(function(s) { return s.trim(); }).filter(function(s) { return s.length > 0; });
                                var name = '';
                                var teacher = '';
                                var jieci = '1,2';
                                var weeks = '';
                                var location = '';
                                var department = '';
                                var phone = '';

                                for (var l = 0; l < lines.length; l++) {
                                    var line = lines[l];
                                    if (line.indexOf('节次:') !== -1) {
                                        jieci = line.replace('节次:', '').replace('节', '').trim();
                                    } else if (line.indexOf('周次:') !== -1) {
                                        weeks = line.replace('周次:', '').trim();
                                    } else if (line.indexOf('地点:') !== -1) {
                                        location = line.replace('地点:', '').trim();
                                    } else if (line.indexOf('开课院系:') !== -1) {
                                        department = line.replace('开课院系:', '').trim();
                                    } else if (line.indexOf('电话:') !== -1) {
                                        phone = line.replace('电话:', '').trim();
                                    } else if (!name && line.indexOf(':') === -1 && line.indexOf('：') === -1) {
                                        name = line;
                                    } else if (!teacher && line.indexOf(':') === -1 && line.indexOf('：') === -1 && line !== name) {
                                        teacher = line;
                                    }
                                }

                                if (name && weeks && name !== '考生来源' && name !== '考试方式' && name !== '最后学历学习形式' && name !== '校外导师') {
                                    collectedCourses.push({
                                        name: name,
                                        teacher: teacher,
                                        dayOfWeek: dayOfWeek,
                                        dayName: dayName,
                                        jieci: jieci,
                                        weeksStr: weeks,
                                        location: location,
                                        department: department,
                                        phone: phone
                                    });
                                }
                            }
                        }
                    } catch(e) {}

                    try {
                        if (win.frames) {
                            for (var f = 0; f < win.frames.length; f++) {
                                try {
                                    inspectWindow(win.frames[f]);
                                } catch(e) {}
                            }
                        }
                    } catch(e) {}
                }

                inspectWindow(window);
                ScheduleBridge.processResult(JSON.stringify(collectedCourses), allHtml);
            })();
        """.trimIndent()

        binding.webView.evaluateJavascript(js, null)
    }

    data class RawJsCourse(
        val name: String = "",
        val teacher: String = "",
        val dayOfWeek: Int = 1,
        val dayName: String = "周一",
        val jieci: String = "1,2",
        val weeksStr: String = "",
        val location: String = "",
        val department: String = "",
        val phone: String = ""
    )

    fun onResultReceived(json: String, html: String) {
        runOnUiThread {
            var courses: List<Course> = emptyList()

            if (json.isNotBlank() && json != "[]") {
                try {
                    val gson = Gson()
                    val type = object : TypeToken<List<RawJsCourse>>() {}.type
                    val rawList: List<RawJsCourse> = gson.fromJson(json, type)
                    courses = rawList.map { raw ->
                        val (weeks, assignments) = TopsoftHtmlParser.parseWeeksAndTeacherAssignments(raw.weeksStr, raw.teacher)
                        val periods = Regex("""\d+""").findAll(raw.jieci).map { it.value.toInt() }.toList()
                        val startPeriod = periods.minOrNull() ?: 1
                        val endPeriod = periods.maxOrNull() ?: startPeriod
                        Course(
                            name = raw.name,
                            teacher = raw.teacher,
                            dayOfWeek = raw.dayOfWeek,
                            dayName = raw.dayName,
                            jieci = raw.jieci,
                            startPeriod = startPeriod,
                            endPeriod = endPeriod,
                            periodCount = endPeriod - startPeriod + 1,
                            weeksStr = raw.weeksStr,
                            weeks = weeks,
                            teacherAssignments = assignments,
                            location = raw.location,
                            department = raw.department,
                            phone = raw.phone,
                            colorIndex = Math.abs(raw.name.hashCode())
                        )
                    }.distinctBy { "${it.name}_${it.dayOfWeek}_${it.startPeriod}_${it.weeksStr}_${it.location}" }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (courses.isEmpty() && html.isNotBlank()) {
                courses = TopsoftHtmlParser.parse(html)
            }

            if (courses.isNotEmpty()) {
                repository.saveCourses(courses)
                val currentUrl = binding.webView.url
                if (repository.getSavedJwUrl().isBlank() && currentUrl != null) {
                    repository.saveJwUrl(currentUrl)
                }

                AlertDialog.Builder(this)
                    .setTitle("课表抓取成功")
                    .setMessage("成功解析到 " + courses.size + " 门次课程。\n主课表与悬浮窗已全部同步更新。")
                    .setPositiveButton("查看") { _, _ ->
                        finish()
                    }
                    .show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("未检测到课表")
                    .setMessage("已抓取页面全部框架（共 " + html.length + " 字符），但未能识别到课表表格。\n\n建议：\n1. 请在教务系统中点击左侧菜单进入【我的课表】或【学生课表】页面；\n2. 长按【抓取课表】按钮可直接导入手机本地已保存的 HTML 课表网页。")
                    .setPositiveButton("我知道了", null)
                    .setNeutralButton("导入本地网页") { _, _ ->
                        selectHtmlLauncher.launch("*/*")
                    }
                    .show()
            }
        }
    }

    class ScheduleJsBridge(private val activity: WebScheduleActivity) {
        @JavascriptInterface
        fun processResult(json: String, html: String) {
            activity.onResultReceived(json, html)
        }
    }
}
