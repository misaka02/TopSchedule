package com.topware.timetable.ui.webview

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.topware.timetable.R
import com.topware.timetable.data.parser.TopsoftHtmlParser
import com.topware.timetable.data.repository.ScheduleRepository
import com.topware.timetable.databinding.ActivityWebScheduleBinding

class WebScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebScheduleBinding
    private lateinit var repository: ScheduleRepository
    private var isDesktopUa: Boolean = true
    private var defaultMobileUa: String = ""

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

                    // 居中铺满全屏自适应宽度（不强制 0.25 缩放）
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
                function collectHtml(win) {
                    var str = '';
                    try {
                        if (win && win.document && win.document.documentElement) {
                            str += win.document.documentElement.outerHTML + '
';
                        }
                    } catch(e) {}
                    try {
                        if (win && win.frames) {
                            for (var i = 0; i < win.frames.length; i++) {
                                try {
                                    str += collectHtml(win.frames[i]);
                                } catch(e) {}
                            }
                        }
                    } catch(e) {}
                    return str;
                }
                var allHtml = collectHtml(window);
                ScheduleBridge.processHtml(allHtml);
            })();
        """.trimIndent()

        binding.webView.evaluateJavascript(js, null)
    }

    fun onHtmlReceived(html: String) {
        runOnUiThread {
            val courses = TopsoftHtmlParser.parse(html)
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
                    .setTitle("未提取到课程")
                    .setMessage("已抓取页面内容（字符数：" + html.length + "），但未能识别到课表表格。\n请确保当前处于【学生课表】或【我的课表】排课页面。")
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    class ScheduleJsBridge(private val activity: WebScheduleActivity) {
        @JavascriptInterface
        fun processHtml(html: String) {
            activity.onHtmlReceived(html)
        }
    }
}
