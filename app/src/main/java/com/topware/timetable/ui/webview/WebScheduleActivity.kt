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
            binding.webView.reload()
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

    private fun injectDesktopViewport() {
        if (!isDesktopUa) return
        val js = """
            (function() {
                var metas = document.getElementsByTagName('meta');
                var found = false;
                for (var i = 0; i < metas.length; i++) {
                    if (metas[i].name === 'viewport') {
                        metas[i].setAttribute('content', 'width=1280, initial-scale=0.25, maximum-scale=3.0, user-scalable=yes');
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    var meta = document.createElement('meta');
                    meta.name = 'viewport';
                    meta.content = 'width=1280, initial-scale=0.25, maximum-scale=3.0, user-scalable=yes';
                    document.getElementsByTagName('head')[0].appendChild(meta);
                }
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(js, null)
    }

    private fun loadUrl(rawUrl: String) {
        var formatted = rawUrl.trim()
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = "https://$formatted"
        }
        binding.webView.loadUrl(formatted)
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
                if (newProgress == 100) {
                    binding.progressBar.visibility = android.view.View.GONE
                    injectDesktopViewport()
                } else {
                    binding.progressBar.visibility = android.view.View.VISIBLE
                }
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectDesktopViewport()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectDesktopViewport()
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
        Toast.makeText(this, "正在抓取课表内容...", Toast.LENGTH_SHORT).show()
        val js = """
            (function() {
                var fullHtml = document.documentElement.outerHTML;
                var iframes = document.getElementsByTagName('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    try {
                        var idoc = iframes[i].contentDocument || iframes[i].contentWindow.document;
                        if (idoc) {
                            fullHtml += '\n<!-- IFRAME ' + i + ' -->\n' + idoc.documentElement.outerHTML;
                        }
                    } catch(e) {}
                }
                var frames = document.getElementsByTagName('frame');
                for (var j = 0; j < frames.length; j++) {
                    try {
                        var fdoc = frames[j].contentDocument || frames[j].contentWindow.document;
                        if (fdoc) {
                            fullHtml += '\n<!-- FRAME ' + j + ' -->\n' + fdoc.documentElement.outerHTML;
                        }
                    } catch(e) {}
                }
                ScheduleBridge.processHtml(fullHtml);
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
                Toast.makeText(this, "未能检测到课表表格，请进入【学生课表】或【我的课表】页面后再抓取", Toast.LENGTH_LONG).show()
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
