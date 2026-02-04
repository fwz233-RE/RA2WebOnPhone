package com.ra2.webonphone

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.ra2.webonphone.data.SettingsRepository
import com.ra2.webonphone.ui.ControlMode
import com.ra2.webonphone.ui.GamepadMouseController
import com.ra2.webonphone.ui.SidebarView
import com.ra2.webonphone.ui.SystemStatsView
import com.ra2.webonphone.util.LocaleHelper
import kotlin.math.abs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ra2.webonphone.ui.theme.RA2WebOnPhoneTheme
import com.ra2.webonphone.data.ControlMethod
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class WebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        private const val BACK_PRESS_INTERVAL = 2000L
        private const val WHITE_SCREEN_CHECK_DELAY = 15000L
        private const val TAG = "WebViewActivity"
    }

    override fun attachBaseContext(newBase: Context) {
        val settingsRepo = SettingsRepository(newBase)
        val language = settingsRepo.getAppLanguage()
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    /**
     * 模拟 Alt + F 组合键
     * 用于触发网页中的特定快捷键功能
     */
    private fun simulateAltF() {
        val webView = this.webView ?: return
        val time = System.currentTimeMillis()
        
        // 按下 Alt
        webView.dispatchKeyEvent(
            KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT, 0, KeyEvent.META_ALT_ON)
        )
        
        // 按下 F (带 Alt 修饰符)
        webView.dispatchKeyEvent(
            KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F, 0, KeyEvent.META_ALT_ON)
        )
        
        // 释放 F
        webView.dispatchKeyEvent(
            KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F, 0, KeyEvent.META_ALT_ON)
        )

        // 释放 Alt
        webView.dispatchKeyEvent(
            KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ALT_LEFT, 0, 0)
        )
    }

    // 处理器类型枚举
    private enum class ProcessorType {
        QUALCOMM,   // 高通骁龙
        MEDIATEK,   // 联发科MTK
        EXYNOS,     // 三星猎户座
        KIRIN,      // 华为麒麟
        UNKNOWN     // 未知
    }

    private var webView: WebView? = null
    private lateinit var container: FrameLayout
    private lateinit var gamepadMouseController: GamepadMouseController
    private lateinit var sidebarView: SidebarView
    private lateinit var systemStatsView: SystemStatsView
    private lateinit var settingsRepository: SettingsRepository
    private var backPressedTime: Long = 0
    private var isGamepadFocusEnabled = true
    private var currentUrl: String = ""
    private var pageLoadSuccess = false
    private val handler = Handler(Looper.getMainLooper())


    // 文件选择器相关
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>

    // 全屏相关
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalSystemUiVisibility: Int = 0

    // 缓存检测到的处理器类型
    private val processorType: ProcessorType by lazy { detectProcessor() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化设置仓库
        settingsRepository = SettingsRepository(this)

        // MTK优化：禁用Window级别硬件加速以减少GPUAUX错误
        // WebView内部仍会使用GPU进行WebGL渲染
        if (isMtkProcessor()) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            Log.d(TAG, "MTK设备: 优化Window渲染以减少GPUAUX错误")
        }

        // 初始化文件选择器
        setupFileChooser()

        // 创建容器
        container = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        setContentView(container)

        // 获取URL
        currentUrl = intent.getStringExtra(EXTRA_URL) ?: "https://ra2web.github.io/"

        // 创建并配置WebView
        createAndSetupWebView()

        // 初始化手柄控制器
        gamepadMouseController = GamepadMouseController(container, webView!!)
        gamepadMouseController.inputMethod = settingsRepository.getControlMethod()
        gamepadMouseController.start()

        // 设置触摸目标提供器，确保全屏时触摸事件发送到正确的视图
        gamepadMouseController.touchTargetProvider = {
            // 如果有全屏视图（customView），使用它；否则使用 webView
            customView ?: webView!!
        }

        // 初始化侧边栏
        setupSidebar()

        // 初始化系统状态监控视图
        setupSystemStatsView()

        // 设置系统UI变化监听器，确保游戏模式下状态栏不会意外显示
        setupSystemUiListener()

        // 检查控制方式设置（首次启动需选择）
        checkForControlMethodSetup()

        // 加载URL
        loadUrl(currentUrl)
    }

    private fun checkForControlMethodSetup() {
        if (settingsRepository.getControlMethod() == com.ra2.webonphone.data.ControlMethod.UNKNOWN) {
            showControlMethodSelectionDialog(false)
        }
    }

    private fun showControlMethodSelectionDialog(cancelable: Boolean = true) {
        val composeView = ComposeView(this).apply {
            // Fix: Set the necessary ViewTree owners for Compose to work inside a Dialog
            setViewTreeLifecycleOwner(this@WebViewActivity)
            setViewTreeViewModelStoreOwner(this@WebViewActivity)
            setViewTreeSavedStateRegistryOwner(this@WebViewActivity)
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(composeView)
            .setCancelable(cancelable)
            .create()

        // Fix: Set the necessary ViewTree owners on the dialog window's decor view
        dialog.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }
        
        // Transparent background so we can use Material3 Surface shapes
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        composeView.setContent {
            RA2WebOnPhoneTheme {
                // Collect current setting
                val currentMethod by settingsRepository.controlMethod.collectAsState()
                // Local state for selection
                var selectedMethod by remember(currentMethod) { 
                    mutableStateOf(if (currentMethod == ControlMethod.UNKNOWN) ControlMethod.JOYSTICK else currentMethod) 
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp), // Reduced padding
                        verticalArrangement = Arrangement.spacedBy(12.dp) // Reduced spacing
                    ) {
                        // Title (Fixed)
                        Text(
                            text = stringResource(R.string.select_control_method),
                            style = MaterialTheme.typography.titleLarge, // Slightly smaller title
                            fontWeight = FontWeight.Bold
                        )

                        // Scrollable Content Content (Options + Info)
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false) // Allow shrinking, but take available space
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Options
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Joystick Option
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = selectedMethod == ControlMethod.JOYSTICK,
                                            onClick = { selectedMethod = ControlMethod.JOYSTICK }
                                        )
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedMethod == ControlMethod.JOYSTICK,
                                        onClick = { selectedMethod = ControlMethod.JOYSTICK }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.control_mode_joystick),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                
                                // Keyboard Option
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = selectedMethod == ControlMethod.KEYBOARD,
                                            onClick = { selectedMethod = ControlMethod.KEYBOARD }
                                        )
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedMethod == ControlMethod.KEYBOARD,
                                        onClick = { selectedMethod = ControlMethod.KEYBOARD }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.control_mode_keyboard),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }

                            // Info Text (Card)
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (selectedMethod == ControlMethod.KEYBOARD) 
                                           stringResource(R.string.mapping_info_keyboard) 
                                           else stringResource(R.string.mapping_info_joystick),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Buttons (Fixed Footer)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (cancelable) {
                                TextButton(onClick = { dialog.dismiss() }) {
                                    Text(stringResource(R.string.cancel))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            TextButton(onClick = {
                                settingsRepository.setControlMethod(selectedMethod)
                                gamepadMouseController.inputMethod = selectedMethod
                                Toast.makeText(this@WebViewActivity, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }) {
                                Text(stringResource(R.string.confirm))
                            }
                        }
                    }
                }
            }
        }
        
        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun setupSystemUiListener() {
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            // 如果系统栏变为可见，且处于游戏模式，则重新隐藏
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                if (::gamepadMouseController.isInitialized && gamepadMouseController.isGameMode()) {
                    // 延迟一下再重新应用，避免与系统动画冲突
                    handler.postDelayed({
                        if (gamepadMouseController.isGameMode()) {
                            reapplyImmersiveMode()
                        }
                    }, 100)
                }
            }
        }
    }

    private fun createAndSetupWebView() {
        // 销毁旧的WebView
        webView?.let {
            container.removeView(it)
            it.destroy()
        }

        // 使用Activity context创建WebView（关键修复！）
        webView = object : WebView(this) {
            // 在输入法之前拦截按键（最早的拦截点）
            override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK &&
                    ::gamepadMouseController.isInitialized &&
                    gamepadMouseController.isGameMode()) {
                    return true  // 完全拦截返回键
                }
                return super.onKeyPreIme(keyCode, event)
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                // 游戏模式下完全拦截返回键，防止触发网页全屏退出
                if (event.keyCode == KeyEvent.KEYCODE_BACK &&
                    ::gamepadMouseController.isInitialized &&
                    gamepadMouseController.isGameMode()) {
                    return true
                }
                return super.dispatchKeyEvent(event)
            }

            override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK &&
                    ::gamepadMouseController.isInitialized &&
                    gamepadMouseController.isGameMode()) {
                    return true
                }
                return super.onKeyDown(keyCode, event)
            }

            override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK &&
                    ::gamepadMouseController.isInitialized &&
                    gamepadMouseController.isGameMode()) {
                    return true
                }
                return super.onKeyUp(keyCode, event)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            // 额外的按键监听器作为保险
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK &&
                    ::gamepadMouseController.isInitialized &&
                    gamepadMouseController.isGameMode()) {
                    true  // 消费返回键
                } else {
                    false
                }
            }
        }
        container.addView(webView, 0)

        setupWebViewSettings()
        setupWebViewClient()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        webView?.settings?.apply {
            // JavaScript
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false

            // 缓存策略：优先使用缓存，加速大文件加载
            cacheMode = WebSettings.LOAD_DEFAULT

            // 存储 - 启用所有存储选项以支持大文件缓存
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true

            // 渲染
            @Suppress("DEPRECATION")
            setRenderPriority(WebSettings.RenderPriority.HIGH)

            // 图片 - 延迟加载图片以优先加载主要内容
            loadsImagesAutomatically = true
            @Suppress("DEPRECATION")
            blockNetworkImage = false
            blockNetworkLoads = false

            // 视口
            useWideViewPort = true
            loadWithOverviewMode = true

            // 缩放
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)

            // 文件访问
            allowFileAccess = true
            allowContentAccess = true

            // 混合内容
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // 媒体
            mediaPlaybackRequiresUserGesture = false

            // 其他
            setSupportMultipleWindows(false)
            defaultTextEncodingName = "UTF-8"
            safeBrowsingEnabled = false

            // 用户代理 - 使用桌面版UA可能获得更好的下载体验
            userAgentString = userAgentString.replace("Mobile", "Desktop")
        }

        // 根据处理器类型设置渲染模式
        setupLayerTypeForProcessor()
    }

    /**
     * 根据处理器类型设置最优的渲染模式
     * - 高通/三星/麒麟: 使用硬件加速层获得最佳性能
     * - MTK: 特殊处理以减少 GPUAUX Null anb 错误
     */
    private fun setupLayerTypeForProcessor() {
        when (processorType) {
            ProcessorType.QUALCOMM, ProcessorType.EXYNOS, ProcessorType.KIRIN -> {
                Log.d(TAG, "使用硬件加速渲染 (处理器: $processorType)")
                webView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            ProcessorType.MEDIATEK -> {
                // MTK特殊处理：
                // 1. 禁用过度滚动效果减少GPU调用
                // 2. 使用NONE模式但延迟初始化
                Log.d(TAG, "MTK处理器: 应用特殊渲染优化")
                setupMtkOptimizations()
            }
            ProcessorType.UNKNOWN -> {
                Log.d(TAG, "未知处理器类型，默认使用硬件加速")
                webView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
        }
    }

    /**
     * MTK 处理器专用优化
     * 减少 GPUAUX Null anb 错误
     */
    private fun setupMtkOptimizations() {
        webView?.apply {
            // 禁用过度滚动效果，减少不必要的GPU渲染调用
            overScrollMode = View.OVER_SCROLL_NEVER

            // 禁用滚动条，减少渲染
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            // 初始使用NONE模式
            setLayerType(View.LAYER_TYPE_NONE, null)

            // 禁用硬件加速层缓存（减少ANB分配）
            isDrawingCacheEnabled = false
        }
    }

    /**
     * MTK: 页面加载完成后的优化处理
     */
    private fun onMtkPageLoadComplete() {
        if (processorType != ProcessorType.MEDIATEK) return

        // 页面加载完成后，延迟稳定渲染状态
        handler.postDelayed({
            webView?.apply {
                // 强制重绘一次，稳定渲染管线
                invalidate()
            }
        }, 500)
    }

    /**
     * 注入 JavaScript 来保护全屏状态
     * 游戏模式下阻止 Escape 键触发退出全屏
     */
    private fun injectFullscreenProtectionScript() {
        val script = """
            (function() {
                // 防止重复注入
                if (window.__fullscreenProtectionInjected) return;
                window.__fullscreenProtectionInjected = true;

                // 游戏模式标志（由 Android 更新）
                window.__gameMode = false;

                // 拦截 keydown 事件，阻止 Escape 键退出全屏
                document.addEventListener('keydown', function(e) {
                    if (window.__gameMode && (e.key === 'Escape' || e.keyCode === 27)) {
                        e.preventDefault();
                        e.stopPropagation();
                        e.stopImmediatePropagation();
                        return false;
                    }
                }, true);

                // 拦截 keyup 事件
                document.addEventListener('keyup', function(e) {
                    if (window.__gameMode && (e.key === 'Escape' || e.keyCode === 27)) {
                        e.preventDefault();
                        e.stopPropagation();
                        e.stopImmediatePropagation();
                        return false;
                    }
                }, true);

                // 备份原始的 exitFullscreen 方法
                var originalExitFullscreen = document.exitFullscreen;
                var originalWebkitExitFullscreen = document.webkitExitFullscreen;

                // 重写 exitFullscreen，游戏模式下阻止退出
                document.exitFullscreen = function() {
                    if (window.__gameMode) {
                        console.log('Game mode: exitFullscreen blocked');
                        return Promise.resolve();
                    }
                    return originalExitFullscreen ? originalExitFullscreen.call(document) : Promise.resolve();
                };

                if (document.webkitExitFullscreen) {
                    document.webkitExitFullscreen = function() {
                        if (window.__gameMode) {
                            console.log('Game mode: webkitExitFullscreen blocked');
                            return;
                        }
                        if (originalWebkitExitFullscreen) originalWebkitExitFullscreen.call(document);
                    };
                }

                console.log('Fullscreen protection script injected');
            })();
        """.trimIndent()

        webView?.evaluateJavascript(script, null)
    }

    /**
     * 更新网页中的游戏模式状态
     */
    private fun updateWebGameModeState(isGameMode: Boolean) {
        val script = "window.__gameMode = $isGameMode;"
        webView?.evaluateJavascript(script, null)
    }

    /**
     * 触发摇杆 HTML 中的按钮事件
     * @param buttonName 按钮名称：攻、家、署、同、强、取
     * 
     * SVG结构: <path>按钮区域</path><text>按钮文字</text>
     * 需要将触摸事件发送到 path 元素（text 的前一个兄弟节点）
     */
    private fun triggerJoystickButton(buttonName: String) {
        val script = """
            (function() {
                // 查找包含指定文本的 text 元素
                var texts = document.querySelectorAll('text');
                for (var i = 0; i < texts.length; i++) {
                    if (texts[i].textContent.trim() === '$buttonName') {
                        var textElement = texts[i];
                        
                        // 获取前一个兄弟节点（path元素，即实际的按钮区域）
                        var pathElement = textElement.previousElementSibling;
                        if (!pathElement || pathElement.tagName.toLowerCase() !== 'path') {
                            pathElement = textElement;  // 降级使用text元素
                        }
                        
                        // 获取文本元素的位置
                        var rect = textElement.getBoundingClientRect();
                        var x = rect.left + rect.width / 2;
                        var y = rect.top + rect.height / 2;

                        // 模拟触摸事件（目标使用path元素）
                        var touch = new Touch({
                            identifier: Date.now(),
                            target: pathElement,
                            clientX: x,
                            clientY: y,
                            pageX: x,
                            pageY: y
                        });

                        var touchStart = new TouchEvent('touchstart', {
                            touches: [touch],
                            targetTouches: [touch],
                            changedTouches: [touch],
                            bubbles: true,
                            cancelable: true
                        });

                        var touchEnd = new TouchEvent('touchend', {
                            touches: [],
                            targetTouches: [],
                            changedTouches: [touch],
                            bubbles: true,
                            cancelable: true
                        });

                        pathElement.dispatchEvent(touchStart);
                        setTimeout(function() {
                            pathElement.dispatchEvent(touchEnd);
                        }, 50);

                        console.log('Triggered button: $buttonName -> path');
                        return true;
                    }
                }
                console.log('Button not found: $buttonName');
                return false;
            })();
        """.trimIndent()
        webView?.evaluateJavascript(script, null)
    }

    /**
     * 触发摇杆 HTML 中的按钮事件（支持按下/释放/按住重复）
     * @param buttonName 按钮名称：攻、家、署、同、强、取
     * @param eventType 事件类型："down" 或 "up"
     * @param isRepeat 是否为按住重复触发
     * 
     * 使用与 sendJoystickEvent 相同的方式：
     * 1. 通过 document.elementFromPoint 获取目标元素
     * 2. Touch 构造包含 radiusX, radiusY, force 参数
     */
    private fun triggerJoystickButtonEvent(buttonName: String, eventType: String, isRepeat: Boolean) {
        val script = """
            (function() {
                // 初始化按钮状态存储
                if (!window.__buttonTouchState) {
                    window.__buttonTouchState = {};
                }
                
                // 查找包含指定文本的 text 元素以获取坐标
                var texts = document.querySelectorAll('text');
                var textElement = null;
                for (var i = 0; i < texts.length; i++) {
                    if (texts[i].textContent.trim() === '$buttonName') {
                        textElement = texts[i];
                        break;
                    }
                }
                
                if (!textElement) {
                    console.log('Button text not found: $buttonName');
                    return false;
                }
                
                // 获取触摸坐标（使用文本元素的中心）
                var rect = textElement.getBoundingClientRect();
                var touchX = rect.left + rect.width / 2;
                var touchY = rect.top + rect.height / 2;
                
                // 使用 document.elementFromPoint 获取实际目标元素（与摇杆代码一致）
                var targetElement = document.elementFromPoint(touchX, touchY) || textElement;
                
                var eventType = '$eventType';
                var isRepeat = $isRepeat;
                
                if (eventType === 'down') {
                    if (isRepeat) {
                        // 按住重复触发：不做任何事，触摸已经在首次按下时保持
                        // 这样按住按钮就是保持触摸状态，而不是快速点击
                        console.log('Button HOLD: $buttonName (touch maintained)');
                    } else {
                        // 首次按下：保存状态，发送 touchstart
                        var touchId = Date.now();
                        window.__buttonTouchState['$buttonName'] = {
                            id: touchId,
                            target: targetElement,
                            x: touchX,
                            y: touchY
                        };
                        
                        var touch = new Touch({
                            identifier: touchId,
                            target: targetElement,
                            clientX: touchX,
                            clientY: touchY,
                            pageX: touchX + window.scrollX,
                            pageY: touchY + window.scrollY,
                            radiusX: 10,
                            radiusY: 10,
                            force: 1
                        });
                        
                        var touchStart = new TouchEvent('touchstart', {
                            touches: [touch],
                            targetTouches: [touch],
                            changedTouches: [touch],
                            bubbles: true,
                            cancelable: true
                        });
                        
                        targetElement.dispatchEvent(touchStart);
                        console.log('Button DOWN: $buttonName at (' + touchX.toFixed(0) + ',' + touchY.toFixed(0) + ')');
                    }
                    
                } else if (eventType === 'up') {
                    // 释放事件
                    var state = window.__buttonTouchState['$buttonName'];
                    if (state) {
                        var touch = new Touch({
                            identifier: state.id,
                            target: state.target,
                            clientX: state.x,
                            clientY: state.y,
                            pageX: state.x + window.scrollX,
                            pageY: state.y + window.scrollY,
                            radiusX: 10,
                            radiusY: 10,
                            force: 0
                        });
                        
                        var touchEnd = new TouchEvent('touchend', {
                            touches: [],
                            targetTouches: [],
                            changedTouches: [touch],
                            bubbles: true,
                            cancelable: true
                        });
                        
                        state.target.dispatchEvent(touchEnd);
                        window.__buttonTouchState['$buttonName'] = null;
                        console.log('Button UP: $buttonName');
                    }
                }
                
                return true;
            })();
        """.trimIndent()
        webView?.evaluateJavascript(script, null)
    }

    // 摇杆touch点状态枚举
    private enum class JoystickTouchState {
        INACTIVE,           // 无touch点
        ACTIVE,             // touch点活跃中
        PENDING_CLEAR       // 等待延迟清除
    }

    // 摇杆触摸状态管理
    private var joystickTouchState = JoystickTouchState.INACTIVE
    private var clearTouchTask: Runnable? = null
    private val CLEAR_TOUCH_DELAY = 350L  // 延迟清除时间（毫秒）

    /**
     * 控制虚拟摇杆（带延迟清除机制）
     * 
     * 状态机设计：
     * 1. INACTIVE + 有输入 → ACTIVE（创建touch从中心开始）
     * 2. ACTIVE + 有输入 → ACTIVE（移动touch）
     * 3. ACTIVE + 回中 → PENDING_CLEAR（调度延迟清除）
     * 4. PENDING_CLEAR + 有输入 → ACTIVE（取消清除，继续移动）
     * 5. PENDING_CLEAR + 超时 → INACTIVE（清除touch）
     * 6. 任何状态 + !isActive → INACTIVE（映射禁用，立即清除）
     * 
     * @param x 摇杆X轴值 (-1 到 1)
     * @param y 摇杆Y轴值 (-1 到 1)
     * @param isActive 映射是否启用
     */
    private fun controlJoystick(x: Float, y: Float, isActive: Boolean) {
        // 判断摇杆是否在移动（容差0.01避免浮点误差）
        val isMoving = abs(x) > 0.01f || abs(y) > 0.01f
        
        when {
            // 映射被禁用，立即清除touch点
            !isActive -> {
                cancelClearTouchTask()
                if (joystickTouchState != JoystickTouchState.INACTIVE) {
                    sendJoystickEvent("end", 0f, 0f)
                    joystickTouchState = JoystickTouchState.INACTIVE
                }
            }
            
            // 映射启用 + 摇杆在移动
            isMoving -> {
                // 取消任何待执行的清除任务
                cancelClearTouchTask()
                
                when (joystickTouchState) {
                    JoystickTouchState.INACTIVE -> {
                        // 从无到有：创建touch点（从中心开始）
                        sendJoystickEvent("start", 0f, 0f)
                        // 立即移动到目标位置
                        if (x != 0f || y != 0f) {
                            sendJoystickEvent("move", x, y)
                        }
                        joystickTouchState = JoystickTouchState.ACTIVE
                    }
                    JoystickTouchState.PENDING_CLEAR -> {
                        // 延迟期间又有输入：取消清除，继续移动
                        sendJoystickEvent("move", x, y)
                        joystickTouchState = JoystickTouchState.ACTIVE
                    }
                    JoystickTouchState.ACTIVE -> {
                        // 正常移动
                        sendJoystickEvent("move", x, y)
                    }
                }
            }
            
            // 映射启用 + 摇杆回中
            else -> {
                when (joystickTouchState) {
                    JoystickTouchState.ACTIVE -> {
                        // 先移回中心
                        sendJoystickEvent("move", 0f, 0f)
                        // 调度延迟清除任务
                        scheduleClearTouch()
                        joystickTouchState = JoystickTouchState.PENDING_CLEAR
                    }
                    JoystickTouchState.PENDING_CLEAR -> {
                        // 已经在等待清除，保持状态
                    }
                    JoystickTouchState.INACTIVE -> {
                        // 已经清除，无需操作
                    }
                }
            }
        }
    }

    /**
     * 调度延迟清除touch点的任务
     */
    private fun scheduleClearTouch() {
        // 先取消之前的任务（如果有）
        cancelClearTouchTask()
        
        // 创建新的延迟任务
        clearTouchTask = Runnable {
            if (joystickTouchState == JoystickTouchState.PENDING_CLEAR) {
                sendJoystickEvent("end", 0f, 0f)
                joystickTouchState = JoystickTouchState.INACTIVE
                clearTouchTask = null
            }
        }
        
        // 延迟执行
        handler.postDelayed(clearTouchTask!!, CLEAR_TOUCH_DELAY)
    }

    /**
     * 取消待执行的清除任务
     */
    private fun cancelClearTouchTask() {
        clearTouchTask?.let {
            handler.removeCallbacks(it)
            clearTouchTask = null
        }
    }

    /**
     * 发送摇杆事件到网页
     */
    private fun sendJoystickEvent(action: String, x: Float, y: Float) {
        val script = """
            (function() {
                // 查找摇杆 SVG
                if (!window.__joystickSvg) {
                    var svgs = document.querySelectorAll('svg');
                    for (var i = 0; i < svgs.length; i++) {
                        var vb = svgs[i].getAttribute('viewBox');
                        if (vb && vb.indexOf('305.6') !== -1) {
                            window.__joystickSvg = svgs[i];
                            break;
                        }
                    }
                    if (!window.__joystickSvg) {
                        // 通过文本查找
                        for (var i = 0; i < svgs.length; i++) {
                            var texts = svgs[i].querySelectorAll('text');
                            for (var j = 0; j < texts.length; j++) {
                                if (texts[j].textContent.trim() === '攻') {
                                    window.__joystickSvg = svgs[i];
                                    break;
                                }
                            }
                            if (window.__joystickSvg) break;
                        }
                    }
                }

                var svg = window.__joystickSvg;
                if (!svg) {
                    console.log('Joystick SVG not found');
                    return;
                }

                // 获取 SVG 实际渲染的位置和尺寸
                var rect = svg.getBoundingClientRect();

                // 计算中心点（基于实际渲染尺寸）
                var centerX = rect.left + rect.width / 2;
                var centerY = rect.top + rect.height / 2;
                
                // 摇杆区域大小为宽度的1/3
                var radius = rect.width / 3;

                // 计算触摸位置
                var touchX = centerX + ($x * radius);
                var touchY = centerY + ($y * radius);

                // 获取触摸点的元素
                var targetElement = document.elementFromPoint(touchX, touchY) || svg;

                var action = '$action';
                var touchId = window.__joystickTouchId || Date.now();

                if (action === 'start') {
                    window.__joystickTouchId = touchId;
                    window.__joystickTarget = targetElement;

                    // 创建并分发 touchstart
                    var touch = new Touch({
                        identifier: touchId,
                        target: targetElement,
                        clientX: touchX,
                        clientY: touchY,
                        pageX: touchX + window.scrollX,
                        pageY: touchY + window.scrollY,
                        radiusX: 10,
                        radiusY: 10,
                        force: 1
                    });
                    var event = new TouchEvent('touchstart', {
                        touches: [touch],
                        targetTouches: [touch],
                        changedTouches: [touch],
                        bubbles: true,
                        cancelable: true
                    });
                    targetElement.dispatchEvent(event);
                    console.log('Joystick START at', touchX.toFixed(0), touchY.toFixed(0));

                } else if (action === 'move') {
                    targetElement = window.__joystickTarget || targetElement;
                    touchId = window.__joystickTouchId || touchId;

                    var touch = new Touch({
                        identifier: touchId,
                        target: targetElement,
                        clientX: touchX,
                        clientY: touchY,
                        pageX: touchX + window.scrollX,
                        pageY: touchY + window.scrollY,
                        radiusX: 10,
                        radiusY: 10,
                        force: 1
                    });
                    var event = new TouchEvent('touchmove', {
                        touches: [touch],
                        targetTouches: [touch],
                        changedTouches: [touch],
                        bubbles: true,
                        cancelable: true
                    });
                    targetElement.dispatchEvent(event);

                } else if (action === 'end') {
                    targetElement = window.__joystickTarget || targetElement;
                    touchId = window.__joystickTouchId || touchId;

                    // 结束时回到中心
                    var touch = new Touch({
                        identifier: touchId,
                        target: targetElement,
                        clientX: centerX,
                        clientY: centerY,
                        pageX: centerX + window.scrollX,
                        pageY: centerY + window.scrollY,
                        radiusX: 10,
                        radiusY: 10,
                        force: 0
                    });
                    var event = new TouchEvent('touchend', {
                        touches: [],
                        targetTouches: [],
                        changedTouches: [touch],
                        bubbles: true,
                        cancelable: true
                    });
                    targetElement.dispatchEvent(event);

                    window.__joystickTarget = null;
                    window.__joystickTouchId = null;
                    console.log('Joystick END');
                }
            })();
        """.trimIndent()

        webView?.evaluateJavascript(script, null)
    }

    /**
     * 重置摇杆状态
     */
    private fun resetJoystickPosition() {
        // 取消待执行的清除任务
        cancelClearTouchTask()
        // 如果有活跃的touch点，立即清除
        if (joystickTouchState != JoystickTouchState.INACTIVE) {
            sendJoystickEvent("end", 0f, 0f)
            joystickTouchState = JoystickTouchState.INACTIVE
        }
        // 清除 JavaScript 端的缓存
        webView?.evaluateJavascript("window.__joystickSvg = null;", null)
    }

    /**
     * 快速检测是否为MTK处理器（用于onCreate早期）
     */
    private fun isMtkProcessor(): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        return hardware.contains("mt") || hardware.contains("mediatek") ||
               board.contains("mt") || board.contains("mediatek")
    }

    /**
     * 检测处理器类型
     */
    private fun detectProcessor(): ProcessorType {
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val platform = getSystemProperty("ro.board.platform").lowercase()
        val chipname = getSystemProperty("ro.chipname").lowercase()

        Log.d(TAG, "设备信息 - Hardware: $hardware, Board: $board, Platform: $platform, Chipname: $chipname")

        return when {
            // 高通骁龙检测
            hardware.contains("qcom") || hardware.contains("qualcomm") ||
            board.contains("msm") || board.contains("sdm") || board.contains("sm") ||
            platform.contains("msm") || platform.contains("sdm") || platform.contains("sm") ||
            platform.contains("qcom") -> ProcessorType.QUALCOMM

            // 联发科MTK检测
            hardware.contains("mt") || hardware.contains("mediatek") ||
            board.contains("mt") || board.contains("mediatek") ||
            platform.contains("mt") || platform.contains("mediatek") ||
            chipname.contains("mt") -> ProcessorType.MEDIATEK

            // 三星猎户座检测
            hardware.contains("exynos") || hardware.contains("samsung") ||
            board.contains("exynos") || board.contains("universal") ||
            platform.contains("exynos") -> ProcessorType.EXYNOS

            // 华为麒麟检测
            hardware.contains("kirin") || hardware.contains("hi") ||
            board.contains("kirin") || board.contains("hi36") || board.contains("hi38") ||
            platform.contains("kirin") -> ProcessorType.KIRIN

            else -> ProcessorType.UNKNOWN
        }
    }

    /**
     * 获取系统属性
     */
    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun setupWebViewClient() {
        webView?.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                pageLoadSuccess = false
                // 更新侧边栏URL
                if (::sidebarView.isInitialized) {
                    sidebarView.updateUrl(url ?: "")
                    sidebarView.updateProgress(0)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageLoadSuccess = true
                retryCount = 0  // 重置重试计数

                // 页面加载成功后，允许使用缓存加速后续加载
                view?.settings?.cacheMode = WebSettings.LOAD_DEFAULT

                webView?.requestFocus()

                // 更新侧边栏URL
                if (::sidebarView.isInitialized) {
                    sidebarView.updateUrl(url ?: "")
                }

                // 注入 JavaScript 来拦截 Escape 键，防止退出全屏
                injectFullscreenProtectionScript()

                // MTK优化：页面加载完成后的处理
                onMtkPageLoadComplete()

                // 延迟检查白屏（延长到10秒，大文件需要更多时间渲染）
                scheduleWhiteScreenCheck()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    pageLoadSuccess = false
                    // 主框架加载失败，延迟重试
                    handler.postDelayed({
                        retryLoadUrl()
                    }, 1000)
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }
        }

        webView?.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                // 更新侧边栏进度
                if (::sidebarView.isInitialized) {
                    sidebarView.updateProgress(newProgress)
                }
            }

            // 网页请求全屏时调用
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }

                customView = view
                customViewCallback = callback

                // 保存原始系统UI状态
                @Suppress("DEPRECATION")
                originalSystemUiVisibility = window.decorView.systemUiVisibility

                // 隐藏WebView，显示全屏视图
                webView?.visibility = View.GONE
                // 添加到索引1（WebView之后，侧边栏之前），确保侧边栏仍可显示
                container.addView(customView, 1, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))

                // 进入沉浸式全屏模式
                enterFullscreenMode()
            }

            // 网页退出全屏时调用
            override fun onHideCustomView() {
                if (customView == null) return

                // 退出全屏模式
                exitFullscreenMode()

                // 移除全屏视图，显示WebView
                container.removeView(customView)
                customView = null
                webView?.visibility = View.VISIBLE

                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // 取消之前的回调
                this@WebViewActivity.filePathCallback?.onReceiveValue(null)
                this@WebViewActivity.filePathCallback = filePathCallback

                try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }

                    // 支持多文件选择
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

                    fileChooserLauncher.launch(Intent.createChooser(intent, "选择文件"))
                    return true
                } catch (e: Exception) {
                    this@WebViewActivity.filePathCallback?.onReceiveValue(null)
                    this@WebViewActivity.filePathCallback = null
                    Toast.makeText(this@WebViewActivity, getString(R.string.cannot_open_file_chooser), Toast.LENGTH_SHORT).show()
                    return false
                }
            }
        }

        webView?.isFocusable = true
        webView?.isFocusableInTouchMode = true
    }

    private fun setupFileChooser() {
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data = result.data
            val results: Array<Uri>? = when {
                result.resultCode != RESULT_OK -> null
                data?.clipData != null -> {
                    // 多文件选择
                    val count = data.clipData!!.itemCount
                    Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                }
                data?.data != null -> {
                    // 单文件选择
                    arrayOf(data.data!!)
                }
                else -> null
            }
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }
    }

    private fun setupSidebar() {
        sidebarView = SidebarView(this).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 绑定WebView
        sidebarView.attachWebView(webView!!)

        // 设置回调
        sidebarView.onForceRefreshClick = {
            webView?.let { wv ->
                wv.clearCache(true)
                wv.clearHistory()
                wv.settings?.cacheMode = WebSettings.LOAD_NO_CACHE
                wv.reload()
            }
        }

        sidebarView.onClearCacheClick = {
            webView?.let { wv ->
                wv.clearCache(true)
                wv.clearHistory()
                Toast.makeText(this, getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
            }
        }

        // 游戏模式切换
        sidebarView.onGameModeToggle = {
            // 模拟 Alt+F 快捷键触发网页对应功能
            simulateAltF()
            
            val newMode = gamepadMouseController.toggleMode()
            val isGameMode = newMode == ControlMode.GAME_MODE
            sidebarView.updateGameMode(isGameMode)
            val message = if (isGameMode) getString(R.string.switched_to_game_mode) else getString(R.string.switched_to_web_mode)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

            // 更新网页中的游戏模式状态（用于 JavaScript 全屏保护）
            updateWebGameModeState(isGameMode)

            // 游戏模式下进入沉浸式全屏，隐藏状态栏
            if (isGameMode) {
                enterFullscreenMode()
            } else {
                // 网页模式下，如果没有customView全屏，则退出沉浸式
                if (!isInFullscreen()) {
                    exitFullscreenMode()
                }
                // 网页模式下自动关闭映射
                if (gamepadMouseController.isMappingEnabled()) {
                    gamepadMouseController.setMappingEnabled(false)
                    sidebarView.updateMappingEnabled(false)
                }
            }
        }

        // 映射切换
        sidebarView.onMappingToggle = {
            val enabled = !gamepadMouseController.isMappingEnabled()
            gamepadMouseController.setMappingEnabled(enabled)
            sidebarView.updateMappingEnabled(enabled)
            val statusText = if (enabled) getString(R.string.mapping_enabled) else getString(R.string.mapping_disabled)
            Toast.makeText(this, statusText, Toast.LENGTH_SHORT).show()

            // 开启映射时重置摇杆位置
            if (enabled) {
                resetJoystickPosition()
            }
        }

        // 操控方式设置
        sidebarView.onControlMethodSettingsClick = {
            showControlMethodSelectionDialog(true)
        }

        // 设置映射事件回调（支持按下、按住、释放）
        gamepadMouseController.onMappingEvent = { buttonName, eventType, isRepeat ->
            triggerJoystickButtonEvent(buttonName, eventType, isRepeat)
        }

        // 设置左摇杆移动回调（控制虚拟摇杆）
        gamepadMouseController.onJoystickMove = { x, y, isActive ->
            controlJoystick(x, y, isActive)
        }
        
        // 设置摇杆锁定时的Toast消息回调
        // 当摇杆活跃时按下其他按键，会显示提示（只显示前3次，避免打扰用户）
        gamepadMouseController.onJoystickLockMessage = {
            Toast.makeText(this, getString(R.string.joystick_in_use), Toast.LENGTH_SHORT).show()
        }
        
        // 设置肩键立即执行回调
        // 当摇杆处于PENDING_CLEAR状态（等待延迟清除）时，按下肩键会：
        // 1. 取消等待中的清除任务
        // 2. 立即清除touch点
        // 返回true表示触发了清除操作，肩键功能需要延迟执行
        // 确保没有其他挂起事件后，肩键的功能才会被执行
        gamepadMouseController.onShoulderKeyImmediate = {
            // 如果处于等待清除状态，立即清除touch点，并返回true表示需要延迟
            if (joystickTouchState == JoystickTouchState.PENDING_CLEAR) {
                cancelClearTouchTask()
                sendJoystickEvent("end", 0f, 0f)
                joystickTouchState = JoystickTouchState.INACTIVE
                true  // 触发了清除操作，需要延迟执行肩键功能
            } else {
                false  // 不需要延迟
            }
        }

        // 添加到容器（在最上层）
        container.addView(sidebarView)

        // 更新当前URL
        sidebarView.updateUrl(currentUrl)
    }

    private fun setupSystemStatsView() {
        systemStatsView = SystemStatsView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.START or android.view.Gravity.TOP
                // 留一点边距避免被刘海遮挡
                topMargin = 4
                leftMargin = 4
            }
        }

        // 设置监控目标为WebView
        systemStatsView.setTargetView(webView!!)

        // 添加到容器（在侧边栏之前，确保不遮挡侧边栏）
        container.addView(systemStatsView)

        // 根据设置控制显示
        updateSystemStatsVisibility()
    }

    private fun updateSystemStatsVisibility() {
        val showStats = settingsRepository.getShowSystemStats()
        if (showStats) {
            systemStatsView.visibility = View.VISIBLE
            systemStatsView.start()
        } else {
            systemStatsView.visibility = View.GONE
            systemStatsView.stop()
        }
    }

    private fun loadUrl(url: String) {
        currentUrl = url
        pageLoadSuccess = false

        // 使用默认缓存策略，让大文件能够被缓存
        webView?.settings?.cacheMode = WebSettings.LOAD_DEFAULT

        webView?.loadUrl(url)

        // 设置超时检测（延长到30秒，因为大文件下载需要时间）
        handler.postDelayed({
            checkLoadingStatus()
        }, 30000)
    }

    private var retryCount = 0
    private val MAX_RETRY = 3

    private fun retryLoadUrl() {
        webView?.let { wv ->
            retryCount++

            if (retryCount >= MAX_RETRY) {
                // 多次重试失败后才清除缓存
                wv.clearCache(true)
                wv.clearHistory()
                wv.settings?.cacheMode = WebSettings.LOAD_NO_CACHE
                retryCount = 0
                Toast.makeText(this, getString(R.string.clearing_cache_and_reload), Toast.LENGTH_SHORT).show()
            } else {
                // 普通重试，保留缓存
                wv.settings?.cacheMode = WebSettings.LOAD_DEFAULT
            }

            wv.loadUrl(currentUrl)
        }
    }

    private fun checkLoadingStatus() {
        webView?.let { wv ->
            // 只有在进度完全没动且未成功时才考虑重建
            if (wv.progress < 5 && !pageLoadSuccess) {
                // 加载超时，重建WebView
                recreateWebView()
            }
        }
    }

    private fun scheduleWhiteScreenCheck() {
        handler.postDelayed({
            checkForWhiteScreen()
        }, WHITE_SCREEN_CHECK_DELAY)
    }

    private fun checkForWhiteScreen() {
        webView?.let { wv ->
            // 检查页面是否有实际内容（检查body高度和是否有子元素）
            wv.evaluateJavascript(
                """
                (function() {
                    if (!document.body) return 'empty';
                    if (document.body.scrollHeight <= 0) return 'empty';
                    if (document.body.children.length === 0) return 'empty';
                    // 检查是否有可见内容
                    var hasContent = document.body.innerText.trim().length > 0 ||
                                     document.body.querySelectorAll('canvas, video, img').length > 0;
                    return hasContent ? 'ok' : 'loading';
                })()
                """.trimIndent()
            ) { result ->
                when (result) {
                    "\"empty\"", "null" -> {
                        // 确实是白屏，重新加载
                        retryLoadUrl()
                    }
                    "\"loading\"" -> {
                        // 页面正在加载内容（可能是JS下载大文件），等待更长时间
                        handler.postDelayed({
                            checkForWhiteScreen()
                        }, 10000)
                    }
                    // "ok" - 页面正常，不做任何事
                }
            }
        }
    }

    private fun recreateWebView() {
        // 保存手柄控制器
        gamepadMouseController.stop()

        // 重建WebView
        createAndSetupWebView()

        // 重新绑定手柄控制器
        gamepadMouseController = GamepadMouseController(container, webView!!)
        gamepadMouseController.start()

        // 重新设置触摸目标提供器
        gamepadMouseController.touchTargetProvider = {
            customView ?: webView!!
        }

        // 重新绑定侧边栏到新的WebView
        if (::sidebarView.isInitialized) {
            sidebarView.attachWebView(webView!!)
        }

        // 重新绑定系统状态监控到新的WebView
        if (::systemStatsView.isInitialized) {
            systemStatsView.setTargetView(webView!!)
        }

        // 重新加载
        loadUrl(currentUrl)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun enterFullscreenMode() {
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        // 处理刘海屏
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    private fun exitFullscreenMode() {
        // 清除全屏标志
        @Suppress("DEPRECATION")
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 恢复刘海屏设置
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }

        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.show(WindowInsetsCompat.Type.systemBars())
        }

        // 恢复原始系统UI状态
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = originalSystemUiVisibility
    }

    // 检查是否处于全屏状态
    private fun isInFullscreen(): Boolean = customView != null

    /**
     * 重新应用沉浸式全屏模式
     * 用于游戏模式下保持状态栏隐藏
     */
    private fun reapplyImmersiveMode() {
        // 添加全屏标志
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 处理刘海屏
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        isGamepadFocusEnabled = true
        gamepadMouseController.start()
        if (::systemStatsView.isInitialized) {
            updateSystemStatsVisibility()
        }
        webView?.requestFocus()

        // 游戏模式下恢复沉浸式全屏
        if (::gamepadMouseController.isInitialized && gamepadMouseController.isGameMode()) {
            reapplyImmersiveMode()
        }

        // 检查是否需要重新加载
        if (!pageLoadSuccess && currentUrl.isNotEmpty()) {
            handler.postDelayed({
                if (!pageLoadSuccess) {
                    retryLoadUrl()
                }
            }, 1000)
        }
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
        isGamepadFocusEnabled = false
        gamepadMouseController.stop()
        if (::systemStatsView.isInitialized) {
            systemStatsView.stop()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        gamepadMouseController.destroy()

        // 清理系统状态监控
        if (::systemStatsView.isInitialized) {
            systemStatsView.stop()
        }

        // 清理文件选择器回调
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null

        // 清理全屏资源
        customView?.let { container.removeView(it) }
        customView = null
        customViewCallback = null

        webView?.let { wv ->
            wv.stopLoading()
            wv.clearHistory()
            wv.webViewClient = WebViewClient()
            wv.webChromeClient = null
            container.removeView(wv)
            wv.destroy()
        }
        webView = null

        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (isGamepadFocusEnabled) {
            if (gamepadMouseController.handleMotionEvent(event)) {
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 游戏模式下，触摸事件用于控制鼠标位移
        if (isGamepadFocusEnabled && gamepadMouseController.isGameMode()) {
            // 检查是否点击了侧边栏区域
            if (sidebarView.isVisible()) {
                // 侧边栏可见时，让侧边栏处理触摸
                return super.dispatchTouchEvent(event)
            }
            if (gamepadMouseController.handleTouchEvent(event)) {
                return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 游戏模式下，完全拦截返回键，防止传递到WebView导致网页全屏退出
        if (event.keyCode == KeyEvent.KEYCODE_BACK && gamepadMouseController.isGameMode()) {
            if (event.action == KeyEvent.ACTION_UP) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < BACK_PRESS_INTERVAL) {
                    finish()
                } else {
                    backPressedTime = currentTime
                    Toast.makeText(this, getString(R.string.press_back_again_game_mode), Toast.LENGTH_SHORT).show()
                    // 确保保持沉浸式全屏
                    reapplyImmersiveMode()
                }
            }
            return true  // 完全消费返回键事件，不传递给WebView
        }

        // 处理 Start 按钮切换侧边栏（悬浮窗）
        // 兼容全键盘模式下的 Q 键
        val isStart = event.keyCode == KeyEvent.KEYCODE_BUTTON_START || 
                      (::gamepadMouseController.isInitialized && 
                       gamepadMouseController.inputMethod == com.ra2.webonphone.data.ControlMethod.KEYBOARD && 
                       event.keyCode == KeyEvent.KEYCODE_Q)

        if (isStart) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (sidebarView.isVisible()) {
                    sidebarView.hide()
                } else {
                    sidebarView.show()
                }
            }
            return true
        }

        if (isGamepadFocusEnabled) {
            if (event.keyCode != KeyEvent.KEYCODE_BACK) {
                if (gamepadMouseController.handleKeyEvent(event)) {
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 游戏模式下，返回键不退出全屏，只提示双击退出
        if (gamepadMouseController.isGameMode()) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressedTime < BACK_PRESS_INTERVAL) {
                @Suppress("DEPRECATION")
                super.onBackPressed()
                finish()
            } else {
                backPressedTime = currentTime
                Toast.makeText(this, getString(R.string.press_back_again_game_mode), Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 网页模式：如果处于全屏状态，先退出全屏
        if (isInFullscreen()) {
            customViewCallback?.onCustomViewHidden()
            return
        }

        val currentTime = System.currentTimeMillis()

        if (webView?.canGoBack() == true) {
            webView?.goBack()
            return
        }

        if (currentTime - backPressedTime < BACK_PRESS_INTERVAL) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
            finish()
        } else {
            backPressedTime = currentTime
            Toast.makeText(this, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 游戏模式下，返回键不退出全屏，只提示双击退出
            if (gamepadMouseController.isGameMode()) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < BACK_PRESS_INTERVAL) {
                    finish()
                    return true
                } else {
                    backPressedTime = currentTime
                    Toast.makeText(this, getString(R.string.press_back_again_game_mode), Toast.LENGTH_SHORT).show()
                    return true
                }
            }

            // 网页模式：如果处于全屏状态，先退出全屏
            if (isInFullscreen()) {
                customViewCallback?.onCustomViewHidden()
                return true
            }

            val currentTime = System.currentTimeMillis()

            if (webView?.canGoBack() == true) {
                webView?.goBack()
                return true
            }

            if (currentTime - backPressedTime < BACK_PRESS_INTERVAL) {
                finish()
                return true
            } else {
                backPressedTime = currentTime
                Toast.makeText(this, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
