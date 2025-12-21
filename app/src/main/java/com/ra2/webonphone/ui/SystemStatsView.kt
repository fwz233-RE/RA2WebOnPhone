package com.ra2.webonphone.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.RandomAccessFile

/**
 * 系统状态监控视图
 * 显示：帧率、分辨率、CPU温度、CPU使用率、电池温度
 * 位于左上角，全透明背景，超小字横排显示
 */
class SystemStatsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextView(context, attrs, defStyleAttr) {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    // 帧率计算
    private var frameCount = 0
    private var lastFpsTime = System.nanoTime()
    private var currentFps = 0

    // CPU使用率计算
    private var lastCpuTotal: Long = 0
    private var lastCpuIdle: Long = 0
    private var cpuUsage = 0

    // 电池温度
    private var batteryTemp = 0f
    private var batteryReceiver: BroadcastReceiver? = null

    // 分辨率目标视图
    private var targetView: View? = null

    // Choreographer帧回调
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            frameCount++
            val now = System.nanoTime()
            val elapsed = now - lastFpsTime

            // 每秒更新一次帧率
            if (elapsed >= 1_000_000_000L) {
                currentFps = (frameCount * 1_000_000_000L / elapsed).toInt()
                frameCount = 0
                lastFpsTime = now
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // 定时更新任务
    private val updateTask = object : Runnable {
        override fun run() {
            if (!isRunning) return
            updateStats()
            handler.postDelayed(this, 1000) // 每秒更新一次
        }
    }

    init {
        // 设置样式：超小字、透明背景、白色文字带阴影
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
        setTextColor(Color.WHITE)
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
        setBackgroundColor(Color.TRANSPARENT)
        gravity = Gravity.START or Gravity.TOP
        setPadding(8, 4, 8, 4)

        // 不拦截触摸事件
        isClickable = false
        isFocusable = false
    }

    fun setTargetView(view: View) {
        targetView = view
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        // 注册电池广播
        registerBatteryReceiver()

        // 启动帧率计算
        lastFpsTime = System.nanoTime()
        frameCount = 0
        Choreographer.getInstance().postFrameCallback(frameCallback)

        // 启动定时更新
        handler.post(updateTask)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(updateTask)

        // 注销电池广播
        unregisterBatteryReceiver()
    }

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                batteryTemp = temp / 10f
            }
        }
        context.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        batteryReceiver = null
    }

    private fun updateStats() {
        val fps = currentFps
        val resolution = getResolution()
        val cpuTemp = getCpuTemperature()
        cpuUsage = getCpuUsage()

        // 格式化显示
        val statsText = buildString {
            append("${fps}FPS")
            append(" | ")
            append(resolution)
            append(" | ")
            append("CPU:${cpuUsage}%")
            append(" | ")
            if (cpuTemp > 0) {
                append("${cpuTemp.toInt()}°C")
            } else {
                append("--°C")
            }
            append(" | ")
            append("Battery:${batteryTemp.toInt()}°C")
        }

        text = statsText
    }

    private fun getResolution(): String {
        val view = targetView ?: return "?x?"
        val width = view.width
        val height = view.height
        return if (width > 0 && height > 0) "${width}x${height}" else "?x?"
    }

    private fun getCpuTemperature(): Float {
        // 尝试多个温度文件路径
        val tempPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/hwmon/hwmon0/temp1_input",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/devices/platform/s5p-tmu/curr_temp"
        )

        for (path in tempPaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val temp = file.readText().trim().toLongOrNull() ?: continue
                    // 温度值可能是毫摄氏度，需要转换
                    return if (temp > 1000) temp / 1000f else temp.toFloat()
                }
            } catch (_: Exception) {}
        }
        return 0f
    }

    private fun getCpuUsage(): Int {
        try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()

            if (line != null && line.startsWith("cpu ")) {
                val parts = line.substring(5).trim().split("\\s+".toRegex())
                if (parts.size >= 4) {
                    val user = parts[0].toLongOrNull() ?: 0L
                    val nice = parts[1].toLongOrNull() ?: 0L
                    val system = parts[2].toLongOrNull() ?: 0L
                    val idle = parts[3].toLongOrNull() ?: 0L
                    val iowait = if (parts.size > 4) parts[4].toLongOrNull() ?: 0L else 0L
                    val irq = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L
                    val softirq = if (parts.size > 6) parts[6].toLongOrNull() ?: 0L else 0L

                    val total = user + nice + system + idle + iowait + irq + softirq
                    val idleTime = idle + iowait

                    if (lastCpuTotal > 0) {
                        val diffTotal = total - lastCpuTotal
                        val diffIdle = idleTime - lastCpuIdle

                        if (diffTotal > 0) {
                            val usage = ((diffTotal - diffIdle) * 100 / diffTotal).toInt()
                            lastCpuTotal = total
                            lastCpuIdle = idleTime
                            return usage.coerceIn(0, 100)
                        }
                    }

                    lastCpuTotal = total
                    lastCpuIdle = idleTime
                }
            }
        } catch (_: Exception) {}
        return cpuUsage
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }
}
