package com.ra2.webonphone.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ra2.webonphone.R
import com.ra2.webonphone.ui.theme.RA2WebOnPhoneTheme

/**
 * WebView控制侧边栏 (Material Design You)
 * 根据屏幕高度自动计算，按钮占满整屏
 */
class SidebarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var webView: WebView? = null
    private var isShowing = false
    private var sidebarWidthPx = 0
    private var animationDuration = 200L

    // Compose View
    private lateinit var composeView: ComposeView

    // State managed by Compose
    private var _progress by mutableStateOf(0)
    private var _url by mutableStateOf("")
    private var _isGameMode by mutableStateOf(false)
    private var _isMappingEnabled by mutableStateOf(false)

    // Callbacks
    var onForceRefreshClick: (() -> Unit)? = null
    var onClearCacheClick: (() -> Unit)? = null
    var onCloseClick: (() -> Unit)? = null
    var onGameModeToggle: (() -> Unit)? = null
    var onMappingToggle: (() -> Unit)? = null
    var onControlMethodSettingsClick: (() -> Unit)? = null

    init {
        setupView()
    }

    private fun setupView() {
        // Transparent scrim background, click to close
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { hide() }

        // Calculate dynamic width
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        // 320dp to pixels
        val preferredWidthPx = (320 * displayMetrics.density).toInt()
        // Max 50% of screen width
        sidebarWidthPx = minOf(preferredWidthPx, (screenWidth * 0.5f).toInt())

        // Create ComposeView
        composeView = ComposeView(context).apply {
            layoutParams = LayoutParams(sidebarWidthPx, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.END
            }
            // Initial Translation (Hidden)
            translationX = sidebarWidthPx.toFloat()
            
            setContent {
                RA2WebOnPhoneTheme {
                    SidebarContent(
                        progress = _progress,
                        url = _url,
                        isGameMode = _isGameMode,
                        isMappingEnabled = _isMappingEnabled,
                        onGameModeClick = { 
                            onGameModeToggle?.invoke()
                            hide()
                        },
                        onMappingClick = { 
                            if (_isGameMode) {
                                onMappingToggle?.invoke()
                                hide()
                            }
                        },
                        onControlsClick = { 
                            onControlMethodSettingsClick?.invoke()
                            hide()
                        },
                        onRefreshClick = { 
                            onForceRefreshClick?.invoke()
                            hide()
                        },
                        onClearCacheClick = { 
                            onClearCacheClick?.invoke()
                            hide()
                        },
                        onCloseClick = { hide() }
                    )
                }
            }
        }
        addView(composeView)
    }

    fun attachWebView(webView: WebView) {
        this.webView = webView
    }

    fun updateProgress(progress: Int) {
        _progress = progress
    }

    fun updateUrl(url: String) {
        _url = url
    }

    fun updateGameMode(gameMode: Boolean) {
        _isGameMode = gameMode
        // Switch to web mode -> disable mapping automatically
        if (!gameMode && _isMappingEnabled) {
            _isMappingEnabled = false
        }
    }

    fun updateMappingEnabled(enabled: Boolean) {
        _isMappingEnabled = enabled
    }

    fun show() {
        if (isShowing) return
        isShowing = true

        bringToFront()
        visibility = View.VISIBLE

        // Animate scrim
        ValueAnimator.ofInt(0, 100).apply {
            duration = animationDuration
            addUpdateListener {
                setBackgroundColor(Color.argb((it.animatedValue as Int) * 128 / 100, 0, 0, 0)) // Max alpha ~128 (50%)
            }
            start()
        }

        // Animate sidebar slide in
        ValueAnimator.ofFloat(sidebarWidthPx.toFloat(), 0f).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                composeView.translationX = it.animatedValue as Float
            }
            start()
        }
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false

        // Animate scrim fade out
        ValueAnimator.ofInt(100, 0).apply {
            duration = animationDuration
            addUpdateListener {
                setBackgroundColor(Color.argb((it.animatedValue as Int) * 128 / 100, 0, 0, 0))
            }
            start()
        }

        // Animate sidebar slide out
        ValueAnimator.ofFloat(0f, sidebarWidthPx.toFloat()).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                composeView.translationX = it.animatedValue as Float
                if (it.animatedValue as Float >= sidebarWidthPx.toFloat()) {
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

@Composable
fun SidebarContent(
    progress: Int,
    url: String,
    isGameMode: Boolean,
    isMappingEnabled: Boolean,
    onGameModeClick: () -> Unit,
    onMappingClick: () -> Unit,
    onControlsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainer, // Material You-ish background
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.control_panel),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${progress}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // URL
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Buttons - fill remaining space equally
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Take remaining space
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Game Mode
                SidebarButton(
                    text = if (isGameMode) stringResource(R.string.web_mode) else stringResource(R.string.game_mode),
                    icon = if (isGameMode) Icons.Default.Language else Icons.Default.SportsEsports,
                    modifier = Modifier.weight(1f),
                    onClick = onGameModeClick,
                    containerColor = if (isGameMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isGameMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )

                // Mapping (Enabled only in Game Mode)
                SidebarButton(
                    text = if (isMappingEnabled) stringResource(R.string.disable_mapping) else stringResource(R.string.mapping),
                    icon = Icons.Default.Gamepad,
                    modifier = Modifier.weight(1f),
                    onClick = onMappingClick,
                    enabled = isGameMode,
                    containerColor = if (isMappingEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isMappingEnabled) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
                    alpha = if (isGameMode) 1f else 0.5f
                )

                // Control Method Settings
                SidebarButton(
                    text = stringResource(R.string.control_method),
                    icon = Icons.Default.Settings,
                    modifier = Modifier.weight(1f),
                    onClick = onControlsClick
                )

                // Force Refresh
                SidebarButton(
                    text = stringResource(R.string.force_refresh),
                    icon = Icons.Default.Refresh,
                    modifier = Modifier.weight(1f),
                    onClick = onRefreshClick
                )

                // Clear Cache
                SidebarButton(
                    text = stringResource(R.string.clear_cache),
                    icon = Icons.Default.Delete,
                    modifier = Modifier.weight(1f),
                    onClick = onClearCacheClick
                )


            }
        }
    }
}

@Composable
fun SidebarButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    alpha: Float = 1f
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = containerColor.copy(alpha = alpha * if (enabled) 1f else 0.6f),
        contentColor = contentColor.copy(alpha = alpha * if (enabled) 1f else 0.6f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).height(24.dp)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
