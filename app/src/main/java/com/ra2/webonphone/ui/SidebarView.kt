package com.ra2.webonphone.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.ra2.webonphone.R

/**
 * WebView控制侧边栏
 * 根据屏幕高度自动计算，按钮占满整屏
 */
class SidebarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var webView: WebView? = null
    private var isShowing = false
    private var sidebarWidth = 320
    private var animationDuration = 200L

    // UI组件
    private lateinit var sidebarPanel: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var urlText: TextView

    // 回调
    var onForceRefreshClick: (() -> Unit)? = null
    var onClearCacheClick: (() -> Unit)? = null
    var onCloseClick: (() -> Unit)? = null
    var onGameModeToggle: (() -> Unit)? = null
    var onMappingToggle: (() -> Unit)? = null

    // 游戏模式按钮引用
    private lateinit var gameModeButton: TextView
    private lateinit var mappingButton: TextView
    private var isGameMode = false
    private var isMappingEnabled = false

    init {
        setupView()
    }

    private fun setupView() {
        // 半透明背景遮罩，点击关闭
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { hide() }

        // 侧边栏面板 - 使用 weight 让内容占满
        sidebarPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F0222222"))
            elevation = 16f
            setPadding(16, 16, 16, 16)
            isClickable = true
        }

        val sidebarParams = LayoutParams(sidebarWidth, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.END
        }
        addView(sidebarPanel, sidebarParams)

        // 初始隐藏在右侧外面
        sidebarPanel.translationX = sidebarWidth.toFloat()

        buildSidebarContent()
    }

    private fun buildSidebarContent() {
        // 顶部信息区 (固定高度)
        val infoContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 标题
        val titleText = TextView(context).apply {
            text = context.getString(R.string.control_panel)
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 16)
        }
        infoContainer.addView(titleText)

        // 进度
        progressText = TextView(context).apply {
            text = context.getString(R.string.progress_format, 0)
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
        }
        infoContainer.addView(progressText)

        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 6
            ).apply { topMargin = 6; bottomMargin = 8 }
        }
        infoContainer.addView(progressBar)

        // URL
        urlText = TextView(context).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            maxLines = 1
        }
        infoContainer.addView(urlText)

        sidebarPanel.addView(infoContainer)

        // 分隔线
        addDivider(8)

        // 按钮区域 - 使用 weight 占满剩余空间
        val buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f // weight = 1, 占满剩余空间
            )
        }

        // 添加按钮，每个按钮使用相同的 weight
        // 游戏模式切换按钮
        gameModeButton = createWeightedButton(buttonContainer, getGameModeButtonText()) {
            onGameModeToggle?.invoke()
            hide()
        }
        buttonContainer.addView(gameModeButton)

        // 映射按钮（游戏模式下才能点击）
        mappingButton = createWeightedButton(buttonContainer, getMappingButtonText()) {
            if (isGameMode) {
                onMappingToggle?.invoke()
                hide()
            }
        }
        updateMappingButtonState()
        buttonContainer.addView(mappingButton)

        addWeightedButton(buttonContainer, "⟳  ${context.getString(R.string.force_refresh)}") {
            onForceRefreshClick?.invoke()
            hide()
        }

        addWeightedButton(buttonContainer, "🗑  ${context.getString(R.string.clear_cache)}") {
            onClearCacheClick?.invoke()
            hide()
        }

        addWeightedButton(buttonContainer, "✕  ${context.getString(R.string.close)}") {
            hide()
        }

        sidebarPanel.addView(buttonContainer)
    }

    private fun getGameModeButtonText(): String {
        return if (isGameMode) "🌐  ${context.getString(R.string.web_mode)}" else "🎮  ${context.getString(R.string.game_mode)}"
    }

    private fun getMappingButtonText(): String {
        return if (isMappingEnabled) "🎮  ${context.getString(R.string.disable_mapping)}" else "🎮  ${context.getString(R.string.mapping)}"
    }

    /**
     * 更新映射按钮状态（根据游戏模式显示可用/禁用状态）
     */
    private fun updateMappingButtonState() {
        if (::mappingButton.isInitialized) {
            mappingButton.text = getMappingButtonText()
            if (isGameMode) {
                mappingButton.setTextColor(Color.WHITE)
                mappingButton.alpha = 1.0f
            } else {
                mappingButton.setTextColor(Color.parseColor("#666666"))
                mappingButton.alpha = 0.5f
            }
        }
    }

    /**
     * 更新游戏模式状态
     */
    fun updateGameMode(gameMode: Boolean) {
        isGameMode = gameMode
        if (::gameModeButton.isInitialized) {
            gameModeButton.text = getGameModeButtonText()
        }
        // 切换到网页模式时，自动关闭映射
        if (!gameMode && isMappingEnabled) {
            isMappingEnabled = false
        }
        updateMappingButtonState()
    }

    /**
     * 更新映射状态
     */
    fun updateMappingEnabled(enabled: Boolean) {
        isMappingEnabled = enabled
        updateMappingButtonState()
    }

    /**
     * 创建带权重的按钮并返回
     */
    private fun createWeightedButton(container: LinearLayout, text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 0, 24, 0)

            // 使用 weight 平均分配高度
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f // 每个按钮 weight = 1
            )

            // 圆角背景
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 8f
            }

            // 点击监听
            setOnClickListener { onClick() }

            // 触摸反馈
            setOnTouchListener { v, event ->
                val bg = v.background as? GradientDrawable
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        bg?.setColor(Color.parseColor("#33FFFFFF"))
                        v.invalidate()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        bg?.setColor(Color.TRANSPARENT)
                        v.invalidate()
                    }
                }
                false
            }
        }
    }

    private fun addWeightedButton(container: LinearLayout, text: String, onClick: () -> Unit) {
        val button = TextView(context).apply {
            this.text = text
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 0, 24, 0)

            // 使用 weight 平均分配高度
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f // 每个按钮 weight = 1
            )

            // 圆角背景
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 8f
            }

            // 点击监听
            setOnClickListener { onClick() }

            // 触摸反馈
            setOnTouchListener { v, event ->
                val bg = v.background as? GradientDrawable
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        bg?.setColor(Color.parseColor("#33FFFFFF"))
                        v.invalidate()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        bg?.setColor(Color.TRANSPARENT)
                        v.invalidate()
                    }
                }
                false
            }
        }
        container.addView(button)
    }

    private fun addDivider(verticalMargin: Int) {
        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply {
                topMargin = verticalMargin
                bottomMargin = verticalMargin
            }
        }
        sidebarPanel.addView(divider)
    }

    fun attachWebView(webView: WebView) {
        this.webView = webView
    }

    fun updateProgress(progress: Int) {
        progressBar.progress = progress
        progressText.text = context.getString(R.string.progress_format, progress)
    }

    fun updateUrl(url: String) {
        urlText.text = url
    }

    fun show() {
        if (isShowing) return
        isShowing = true

        bringToFront()
        visibility = View.VISIBLE

        ValueAnimator.ofInt(0, 100).apply {
            duration = animationDuration
            addUpdateListener {
                setBackgroundColor(Color.argb(it.animatedValue as Int, 0, 0, 0))
            }
            start()
        }

        ValueAnimator.ofFloat(sidebarWidth.toFloat(), 0f).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                sidebarPanel.translationX = it.animatedValue as Float
            }
            start()
        }
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false

        ValueAnimator.ofInt(100, 0).apply {
            duration = animationDuration
            addUpdateListener {
                setBackgroundColor(Color.argb(it.animatedValue as Int, 0, 0, 0))
            }
            start()
        }

        ValueAnimator.ofFloat(0f, sidebarWidth.toFloat()).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                sidebarPanel.translationX = it.animatedValue as Float
                if (it.animatedValue as Float >= sidebarWidth.toFloat()) {
                    visibility = View.GONE
                }
            }
            start()
        }

        onCloseClick?.invoke()
    }

    fun toggle() {
        if (isShowing) hide() else show()
    }

    fun isVisible(): Boolean = isShowing
}
