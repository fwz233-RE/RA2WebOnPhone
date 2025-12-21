package com.ra2.webonphone.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * 控制模式枚举
 */
enum class ControlMode {
    WEB_MODE,   // 网页模式：双摇杆控制，A/B键点击
    GAME_MODE   // 游戏模式：触屏滑动控制，R1/R2鼠标键
}

/**
 * 手柄鼠标控制器
 *
 * 网页模式（默认）：
 * - 左摇杆：快速移动鼠标
 * - 右摇杆：慢速精细移动鼠标
 * - A键：点击
 * - B键：长按拖拽
 *
 * 游戏模式：
 * - 手指滑动屏幕：鼠标同步位移（摇杆无效）
 * - R1/R2/L2：鼠标左键（按住拖拽，支持选择框）
 * - A/B键：无功能（与网页模式区分）
 * - 移动光标时自动发送鼠标移动事件（支持拾取效果，如建筑跟随鼠标）
 */
class GamepadMouseController(
    private val container: ViewGroup,
    private val webView: WebView
) {
    private val cursor: VirtualCursor = VirtualCursor(container.context)
    private val handler = Handler(Looper.getMainLooper())

    // 当前控制模式
    var controlMode: ControlMode = ControlMode.WEB_MODE
        private set

    // 触摸目标视图获取器（用于支持全屏时发送事件到正确的视图）
    var touchTargetProvider: (() -> View)? = null

    // 获取触摸目标视图（优先使用 provider，否则使用 webView）
    private val touchTarget: View
        get() = touchTargetProvider?.invoke() ?: webView

    // 模式切换回调
    var onModeChanged: ((ControlMode) -> Unit)? = null

    // 映射功能
    private var mappingEnabled = false
    var onMappingEvent: ((buttonName: String, eventType: String, isRepeat: Boolean) -> Unit)? = null  // 按钮映射事件回调 (按钮名称, 事件类型 "down"/"up", 是否重复触发)
    var onJoystickMove: ((Float, Float, Boolean) -> Unit)? = null  // 左摇杆移动回调 (x, y, isActive)

    // 左摇杆拖动状态
    private var isJoystickDragging = false

    // 左摇杆状态（快速移动）
    private var leftAxisX: Float = 0f
    private var leftAxisY: Float = 0f

    // 右摇杆状态（慢速精细移动）
    private var rightAxisX: Float = 0f
    private var rightAxisY: Float = 0f

    // 鼠标移动参数
    private var cursorSpeed = 15f
    private val slowCursorSpeed = 4f  // 右摇杆慢速
    private val deadZone = 0.15f
    private val acceleration = 1.5f

    // 长按状态
    private var isLongPressing = false
    private var longPressStartTime: Long = 0

    // 游戏模式：触摸状态
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var isTouchTracking = false

    // 游戏模式：R1按键状态（R2/L2触发器共享此状态）
    private var isR1Pressed = false
    private var r1DownTime: Long = 0

    // 更新循环
    private var isRunning = false
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                updateCursorPosition()
                handler.postDelayed(this, 16) // ~60fps
            }
        }
    }

    init {
        // 添加光标到容器
        cursor.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        cursor.elevation = 100f // 确保在最上层
        container.addView(cursor)

        // 初始位置在屏幕中央
        cursor.post {
            val centerX = container.width / 2f
            val centerY = container.height / 2f
            cursor.setPosition(centerX, centerY)
        }
    }

    fun start() {
        isRunning = true
        cursor.visibility = View.VISIBLE
        handler.post(updateRunnable)
    }

    fun stop() {
        isRunning = false
        cursor.visibility = View.GONE
        handler.removeCallbacks(updateRunnable)
    }

    fun destroy() {
        stop()
        container.removeView(cursor)
    }

    /**
     * 切换控制模式
     */
    fun toggleMode(): ControlMode {
        controlMode = when (controlMode) {
            ControlMode.WEB_MODE -> ControlMode.GAME_MODE
            ControlMode.GAME_MODE -> ControlMode.WEB_MODE
        }
        // 切换模式时重置状态
        resetState()
        onModeChanged?.invoke(controlMode)
        return controlMode
    }

    /**
     * 设置控制模式
     */
    fun setMode(mode: ControlMode) {
        if (controlMode != mode) {
            controlMode = mode
            resetState()
            onModeChanged?.invoke(controlMode)
        }
    }

    /**
     * 重置状态
     */
    private fun resetState() {
        // 结束任何正在进行的长按
        if (isLongPressing) {
            endLongPress()
        }
        // 结束R1按键（R2/L2触发器共享此状态）
        if (isR1Pressed) {
            endR1Press()
        }
        isR2TriggerHeld = false
        // 重置摇杆状态
        leftAxisX = 0f
        leftAxisY = 0f
        rightAxisX = 0f
        rightAxisY = 0f
        // 重置触摸跟踪
        isTouchTracking = false
    }

    /**
     * 是否处于游戏模式
     */
    fun isGameMode(): Boolean = controlMode == ControlMode.GAME_MODE

    /**
     * 是否启用映射
     */
    fun isMappingEnabled(): Boolean = mappingEnabled

    /**
     * 设置映射启用状态
     */
    fun setMappingEnabled(enabled: Boolean) {
        val wasEnabled = mappingEnabled
        mappingEnabled = enabled
        
        if (enabled && !wasEnabled) {
            // 启用映射时，立即在中心创建touch点（开始持续触摸）
            isJoystickDragging = true
            onJoystickMove?.invoke(0f, 0f, true)
        } else if (!enabled && wasEnabled) {
            // 禁用映射时，结束touch点
            isJoystickDragging = false
            onJoystickMove?.invoke(0f, 0f, false)
        }
    }

    // 触发器状态（用于轴模式，R2/L2触发器共享R1状态）
    private val triggerThreshold = 0.15f  // 大幅降低阈值，提高兼容性
    private var isR2TriggerHeld = false  // 用于跟踪触发器是否被按下

    // 鼠标悬浮状态（用于拾取效果，如建筑跟随鼠标）
    private var lastHoverTime: Long = 0
    private val hoverEventInterval = 16L  // 约60fps，避免事件过于频繁

    /**
     * 处理手柄摇杆/触发器事件
     */
    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) {

            // 游戏模式下处理触发器轴（R2和L2都检测，某些手柄可能混淆）
            if (controlMode == ControlMode.GAME_MODE) {
                handleTriggerAxes(event)
                // 注意：不要直接return，继续更新摇杆值以支持拖拽选择框
            }

            // 获取左摇杆的值（快速移动）
            leftAxisX = event.getAxisValue(MotionEvent.AXIS_X)
            leftAxisY = event.getAxisValue(MotionEvent.AXIS_Y)

            // 获取右摇杆的值（慢速精细移动）
            rightAxisX = event.getAxisValue(MotionEvent.AXIS_Z)
            rightAxisY = event.getAxisValue(MotionEvent.AXIS_RZ)

            // 应用死区 - 左摇杆
            if (abs(leftAxisX) < deadZone) leftAxisX = 0f
            if (abs(leftAxisY) < deadZone) leftAxisY = 0f

            // 应用死区 - 右摇杆
            if (abs(rightAxisX) < deadZone) rightAxisX = 0f
            if (abs(rightAxisY) < deadZone) rightAxisY = 0f

            // 映射模式下处理摇杆控制
            if (mappingEnabled && controlMode == ControlMode.GAME_MODE) {
                handleJoystickMapping()
            }

            return true
        }
        return false
    }

    /**
     * 处理摇杆映射（只移动已存在的touch点，不创建或销毁touch）
     * touch点的创建和销毁由setMappingEnabled控制
     */
    private fun handleJoystickMapping() {
        // 直接发送当前摇杆位置，让touch点跟随移动
        // 即使摇杆在死区内（0,0），也发送移动事件让touch回到中心
        onJoystickMove?.invoke(leftAxisX, leftAxisY, true)
    }

    /**
     * 处理触发器轴（R2/L2触发器都调用R1的逻辑）
     *
     * 全面检测所有可能的触发器轴，提高手柄兼容性：
     * - AXIS_RTRIGGER (23): 标准右触发器（Xbox/通用）
     * - AXIS_LTRIGGER (17): 标准左触发器
     * - AXIS_GAS (22): 某些手柄的油门轴
     * - AXIS_BRAKE (23): 某些手柄的刹车轴
     * - AXIS_THROTTLE (19): 某些手柄的节流阀轴
     * - AXIS_RUDDER (20): 某些手柄的舵轴
     * - AXIS_WHEEL (21): 某些手柄的轮轴
     * - AXIS_GENERIC_1-16: 通用轴（某些手柄可能使用）
     */
    private fun handleTriggerAxes(event: MotionEvent) {
        // 检测所有可能的触发器轴，取最大值
        var maxTriggerValue = 0f

        // 标准触发器轴
        maxTriggerValue = maxOf(maxTriggerValue, event.getAxisValue(MotionEvent.AXIS_RTRIGGER))
        maxTriggerValue = maxOf(maxTriggerValue, event.getAxisValue(MotionEvent.AXIS_LTRIGGER))
        maxTriggerValue = maxOf(maxTriggerValue, event.getAxisValue(MotionEvent.AXIS_GAS))
        maxTriggerValue = maxOf(maxTriggerValue, event.getAxisValue(MotionEvent.AXIS_BRAKE))
        maxTriggerValue = maxOf(maxTriggerValue, event.getAxisValue(MotionEvent.AXIS_THROTTLE))

        // 其他可能用于触发器的轴
        maxTriggerValue = maxOf(maxTriggerValue, event.getAxisValue(MotionEvent.AXIS_RUDDER))
        maxTriggerValue = maxOf(maxTriggerValue, event.getAxisValue(MotionEvent.AXIS_WHEEL))

        // 通用轴（某些手柄可能使用这些）
        maxTriggerValue = maxOf(maxTriggerValue, event.getAxisValue(MotionEvent.AXIS_GENERIC_1))
        maxTriggerValue = maxOf(maxTriggerValue, event.getAxisValue(MotionEvent.AXIS_GENERIC_2))
        maxTriggerValue = maxOf(maxTriggerValue, event.getAxisValue(MotionEvent.AXIS_GENERIC_3))
        maxTriggerValue = maxOf(maxTriggerValue, event.getAxisValue(MotionEvent.AXIS_GENERIC_4))

        val isTriggerPressed = maxTriggerValue > triggerThreshold

        if (isTriggerPressed && !isR2TriggerHeld) {
            // 触发器刚被按下 - 直接调用R1的逻辑
            isR2TriggerHeld = true
            if (!isR1Pressed) {
                startR1Press()
            }
        } else if (!isTriggerPressed && isR2TriggerHeld) {
            // 触发器刚被释放 - 直接调用R1的逻辑
            isR2TriggerHeld = false
            if (isR1Pressed) {
                endR1Press()
            }
        }
    }

    /**
     * 处理手柄按键事件
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        when (controlMode) {
            ControlMode.WEB_MODE -> return handleWebModeKeyEvent(event)
            ControlMode.GAME_MODE -> return handleGameModeKeyEvent(event)
        }
    }

    /**
     * 网页模式按键处理
     */
    private fun handleWebModeKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    simulateClick()
                    return true
                }
            }
            KeyEvent.KEYCODE_BUTTON_B -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (!isLongPressing) {
                            startLongPress()
                        }
                        return true
                    }
                    KeyEvent.ACTION_UP -> {
                        if (isLongPressing) {
                            endLongPress()
                        }
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * 游戏模式按键处理
     *
     * R1 - 鼠标左键（按住拖拽，释放完成）- 主要操作键
     * R2 - 直接调用R1的逻辑（模拟长按R1效果）
     * L2 - 同R2，作为备选
     * A/B键 - 游戏模式下无效（与网页模式区分），但映射模式下有映射功能
     * 摇杆 - 游戏模式下无效（只能通过触摸屏移动光标），但映射模式下用于控制摇杆HTML
     *
     * 映射模式按键对应：
     * - A键 -> 攻 按钮
     * - B键 -> 家 按钮
     * - X键 -> 署 按钮
     * - Y键 -> 同 按钮
     * - Start键 -> 强 按钮
     * - Select键 -> 取 按钮
     *
     * 注意：不同手柄可能使用不同的键码，这里全面覆盖
     */
    private fun handleGameModeKeyEvent(event: KeyEvent): Boolean {
        // 映射模式下的按键处理 - 支持按下、按住（重复）和释放
        if (mappingEnabled) {
            val buttonName = when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> "攻"
                KeyEvent.KEYCODE_BUTTON_B -> "家"
                KeyEvent.KEYCODE_BUTTON_X -> "署"
                KeyEvent.KEYCODE_BUTTON_Y -> "同"
                KeyEvent.KEYCODE_BUTTON_START -> "强"
                KeyEvent.KEYCODE_BUTTON_SELECT -> "取"
                else -> null
            }
            
            if (buttonName != null) {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        // 按下事件：首次按下或按住重复触发
                        val isRepeat = event.repeatCount > 0
                        onMappingEvent?.invoke(buttonName, "down", isRepeat)
                        return true
                    }
                    KeyEvent.ACTION_UP -> {
                        // 释放事件
                        onMappingEvent?.invoke(buttonName, "up", false)
                        return true
                    }
                }
            }
        }

        when (event.keyCode) {
            // R1 - 鼠标左键（主要操作键）
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (!isR1Pressed) {
                            startR1Press()
                        }
                        return true
                    }
                    KeyEvent.ACTION_UP -> {
                        if (isR1Pressed) {
                            endR1Press()
                        }
                        return true
                    }
                }
            }
            // R2 - 直接调用R1的逻辑（模拟长按R1效果）
            // 包含多种可能的R2键码，提高手柄兼容性
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_Z,      // 某些手柄的Z键可能是R2
            KeyEvent.KEYCODE_BUTTON_C,      // 某些手柄的C键
            KeyEvent.KEYCODE_BUTTON_MODE -> {  // 某些手柄的Mode键
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        // 直接调用R1的按下逻辑
                        if (!isR1Pressed) {
                            startR1Press()
                        }
                        return true
                    }
                    KeyEvent.ACTION_UP -> {
                        // 直接调用R1的释放逻辑
                        if (isR1Pressed) {
                            endR1Press()
                        }
                        return true
                    }
                }
            }
            // L1 - 模拟长按650ms
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    simulateLongPress(650)
                }
                return true
            }
            // L2 - 同R2，作为备选（某些手柄R2可能映射到L2）
            KeyEvent.KEYCODE_BUTTON_L2 -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (!isR1Pressed) {
                            startR1Press()
                        }
                        return true
                    }
                    KeyEvent.ACTION_UP -> {
                        if (isR1Pressed) {
                            endR1Press()
                        }
                        return true
                    }
                }
            }
            // A/B/X/Y键 - 映射模式下已处理，非映射模式下不处理
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                return mappingEnabled  // 映射模式下消费事件，否则不处理
            }
            // Start/Select键 - 映射模式下已处理
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT -> {
                return mappingEnabled
            }
        }
        return false
    }

    /**
     * 游戏模式：处理触摸事件（手指滑动控制鼠标位移）
     *
     * 拾取效果实现原理：
     * - 当用户点击网页上的某个控件（如建筑按钮）后，游戏可能进入"拾取模式"
     * - 在拾取模式下，被拾取的物体会跟随鼠标移动
     * - 为了实现这个效果，我们需要在移动光标时始终发送鼠标移动事件
     * - 如果R1按着，发送ACTION_MOVE（拖拽模式）
     * - 如果R1没按着，发送鼠标悬浮移动事件（拾取模式/普通移动）
     */
    @SuppressLint("ClickableViewAccessibility")
    fun handleTouchEvent(event: MotionEvent): Boolean {
        if (controlMode != ControlMode.GAME_MODE) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isTouchTracking = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTouchTracking) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y
                    // 移动光标
                    cursor.moveBy(dx, dy, container.width.toFloat(), container.height.toFloat())

                    // 如果R1按着（R2/L2触发器也共享R1状态），发送ACTION_MOVE事件以支持拖拽选择框
                    if (isR1Pressed) {
                        updateR1DragPosition()
                    } else {
                        // 如果没有按着任何键，发送鼠标悬浮移动事件
                        // 这支持：1. 拾取效果（建筑跟随鼠标）2. 普通的鼠标移动
                        sendMouseHoverMove()
                    }

                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouchTracking = false
                return true
            }
        }
        return false
    }

    /**
     * 开始R1按压（鼠标左键按下）
     * 注意：R2/L2触发器现在也直接调用此函数
     */
    private fun startR1Press() {
        isR1Pressed = true
        r1DownTime = SystemClock.uptimeMillis()
        val x = cursor.cursorX
        val y = cursor.cursorY
        // 右键通过长按模拟
        val downEvent = MotionEvent.obtain(
            r1DownTime, r1DownTime,
            MotionEvent.ACTION_DOWN,
            x, y, 0
        )
        downEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        touchTarget.dispatchTouchEvent(downEvent)
        downEvent.recycle()
    }

    /**
     * 结束R1按压（鼠标右键释放）
     */
    private fun endR1Press() {
        if (!isR1Pressed) return
        val x = cursor.cursorX
        val y = cursor.cursorY
        val eventTime = SystemClock.uptimeMillis()
        val upEvent = MotionEvent.obtain(
            r1DownTime, eventTime,
            MotionEvent.ACTION_UP,
            x, y, 0
        )
        upEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        touchTarget.dispatchTouchEvent(upEvent)
        upEvent.recycle()
        isR1Pressed = false
    }

    /**
     * 更新R1拖拽位置（发送ACTION_MOVE事件，用于拖拽选择框）
     */
    private fun updateR1DragPosition() {
        val x = cursor.cursorX
        val y = cursor.cursorY
        val eventTime = SystemClock.uptimeMillis()

        val moveEvent = MotionEvent.obtain(
            r1DownTime, eventTime,
            MotionEvent.ACTION_MOVE,
            x, y, 0
        )
        moveEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        touchTarget.dispatchTouchEvent(moveEvent)
        moveEvent.recycle()
    }

    private fun updateCursorPosition() {
        // 如果两个摇杆都没有输入，跳过
        if (leftAxisX == 0f && leftAxisY == 0f && rightAxisX == 0f && rightAxisY == 0f) return

        // 游戏模式下摇杆不移动光标（只能通过触摸屏移动）
        if (controlMode == ControlMode.GAME_MODE) {
            return
        }

        // 网页模式：计算左摇杆移动（快速）
        val leftSpeedMultiplier = cursorSpeed * acceleration
        val leftDx = leftAxisX * leftSpeedMultiplier
        val leftDy = leftAxisY * leftSpeedMultiplier

        // 计算右摇杆移动（慢速精细）
        val rightDx = rightAxisX * slowCursorSpeed
        val rightDy = rightAxisY * slowCursorSpeed

        // 合并两个摇杆的移动
        val dx = leftDx + rightDx
        val dy = leftDy + rightDy

        // 移动光标
        cursor.moveBy(dx, dy, container.width.toFloat(), container.height.toFloat())

        // 如果正在长按，更新触摸位置（网页模式下B键拖拽）
        if (isLongPressing) {
            updateLongPressPosition()
        }
    }

    /**
     * 模拟点击
     */
    private fun simulateClick() {
        val x = cursor.cursorX
        val y = cursor.cursorY
        val downTime = SystemClock.uptimeMillis()

        // 创建按下事件
        val downEvent = MotionEvent.obtain(
            downTime, downTime,
            MotionEvent.ACTION_DOWN,
            x, y, 0
        )
        downEvent.source = InputDevice.SOURCE_TOUCHSCREEN

        // 创建抬起事件
        val upEvent = MotionEvent.obtain(
            downTime, downTime + 50,
            MotionEvent.ACTION_UP,
            x, y, 0
        )
        upEvent.source = InputDevice.SOURCE_TOUCHSCREEN

        // 发送事件到目标视图（webView 或 customView）
        touchTarget.dispatchTouchEvent(downEvent)
        handler.postDelayed({
            touchTarget.dispatchTouchEvent(upEvent)
            downEvent.recycle()
            upEvent.recycle()
        }, 50)
    }

    /**
     * 模拟长按（指定时间）
     */
    private fun simulateLongPress(duration: Long) {
        val x = cursor.cursorX
        val y = cursor.cursorY
        val downTime = SystemClock.uptimeMillis()

        // 创建按下事件
        val downEvent = MotionEvent.obtain(
            downTime, downTime,
            MotionEvent.ACTION_DOWN,
            x, y, 0
        )
        downEvent.source = InputDevice.SOURCE_TOUCHSCREEN

        // 发送按下事件
        touchTarget.dispatchTouchEvent(downEvent)
        
        // 延迟发送抬起事件
        handler.postDelayed({
            val upTime = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(
                downTime, upTime,
                MotionEvent.ACTION_UP,
                x, y, 0
            )
            upEvent.source = InputDevice.SOURCE_TOUCHSCREEN
            touchTarget.dispatchTouchEvent(upEvent)
            
            // 回收事件
            downEvent.recycle()
            upEvent.recycle()
        }, duration)
    }

    /**
     * 开始长按
     */
    private fun startLongPress() {
        isLongPressing = true
        longPressStartTime = SystemClock.uptimeMillis()

        val x = cursor.cursorX
        val y = cursor.cursorY

        // 创建按下事件
        val downEvent = MotionEvent.obtain(
            longPressStartTime, longPressStartTime,
            MotionEvent.ACTION_DOWN,
            x, y, 0
        )
        downEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        touchTarget.dispatchTouchEvent(downEvent)
        downEvent.recycle()
    }

    /**
     * 更新长按位置（用于拖拽）
     */
    private fun updateLongPressPosition() {
        val x = cursor.cursorX
        val y = cursor.cursorY
        val eventTime = SystemClock.uptimeMillis()

        val moveEvent = MotionEvent.obtain(
            longPressStartTime, eventTime,
            MotionEvent.ACTION_MOVE,
            x, y, 0
        )
        moveEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        touchTarget.dispatchTouchEvent(moveEvent)
        moveEvent.recycle()
    }

    /**
     * 结束长按
     */
    private fun endLongPress() {
        if (!isLongPressing) return

        val x = cursor.cursorX
        val y = cursor.cursorY
        val eventTime = SystemClock.uptimeMillis()

        // 创建抬起事件
        val upEvent = MotionEvent.obtain(
            longPressStartTime, eventTime,
            MotionEvent.ACTION_UP,
            x, y, 0
        )
        upEvent.source = InputDevice.SOURCE_TOUCHSCREEN
        touchTarget.dispatchTouchEvent(upEvent)
        upEvent.recycle()

        isLongPressing = false
    }

    /**
     * 发送鼠标悬浮移动事件（模拟PC上的mousemove，用于拾取效果如建筑跟随鼠标）
     *
     * 在PC上，移动鼠标会持续触发mousemove事件，即使没有按下任何鼠标键。
     * 这对于RTS游戏的建筑放置、单位预览等功能至关重要。
     *
     * Android的触摸事件需要DOWN->MOVE->UP序列，无法模拟这种"无按键移动"。
     * 但我们可以使用ACTION_HOVER_MOVE事件来模拟鼠标悬浮移动。
     */
    private fun sendMouseHoverMove() {
        val currentTime = SystemClock.uptimeMillis()
        // 限制发送频率，避免事件过于频繁
        if (currentTime - lastHoverTime < hoverEventInterval) return
        lastHoverTime = currentTime

        val x = cursor.cursorX
        val y = cursor.cursorY

        // 方案1：使用HOVER_MOVE事件（模拟鼠标悬浮）
        val hoverEvent = MotionEvent.obtain(
            currentTime, currentTime,
            MotionEvent.ACTION_HOVER_MOVE,
            x, y, 0
        )
        hoverEvent.source = InputDevice.SOURCE_MOUSE
        touchTarget.dispatchGenericMotionEvent(hoverEvent)
        hoverEvent.recycle()
    }

    /**
     * 设置光标移动速度
     */
    fun setCursorSpeed(speed: Float) {
        cursorSpeed = speed.coerceIn(5f, 30f)
    }

    /**
     * 获取光标是否可见
     */
    fun isVisible(): Boolean = cursor.visibility == View.VISIBLE
}
