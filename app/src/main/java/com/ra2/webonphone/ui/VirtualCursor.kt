package com.ra2.webonphone.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * 虚拟鼠标光标视图
 */
class VirtualCursor(context: Context) : View(context) {

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val cursorPath = Path()
    private val cursorSize = 24f

    // 光标位置
    var cursorX: Float = 0f
        private set
    var cursorY: Float = 0f
        private set

    init {
        // 创建箭头形状的光标
        setupCursorPath()
    }

    private fun setupCursorPath() {
        cursorPath.reset()
        cursorPath.moveTo(0f, 0f)
        cursorPath.lineTo(0f, cursorSize)
        cursorPath.lineTo(cursorSize * 0.35f, cursorSize * 0.75f)
        cursorPath.lineTo(cursorSize * 0.5f, cursorSize * 1.1f)
        cursorPath.lineTo(cursorSize * 0.65f, cursorSize * 1.0f)
        cursorPath.lineTo(cursorSize * 0.5f, cursorSize * 0.65f)
        cursorPath.lineTo(cursorSize * 0.85f, cursorSize * 0.65f)
        cursorPath.close()
    }

    fun setPosition(x: Float, y: Float) {
        cursorX = x
        cursorY = y
        translationX = x
        translationY = y
    }

    fun moveBy(dx: Float, dy: Float, maxX: Float, maxY: Float) {
        cursorX = (cursorX + dx).coerceIn(0f, maxX)
        cursorY = (cursorY + dy).coerceIn(0f, maxY)
        translationX = cursorX
        translationY = cursorY
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (cursorSize * 1.5f).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 绘制光标边框
        canvas.drawPath(cursorPath, borderPaint)
        // 绘制光标填充
        canvas.drawPath(cursorPath, cursorPaint)
    }
}
