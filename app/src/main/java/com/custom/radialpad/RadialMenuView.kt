package com.custom.radialpad

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class WheelRenderOverlayView(context: Context) : View(context) {

    var activeButton: RadialMenuView? = null
    var centerScreenX: Float = 0f
    var centerScreenY: Float = 0f

    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val btn = activeButton ?: return
        if (!btn.isExpanded) return

        val myLoc = IntArray(2)
        getLocationOnScreen(myLoc)

        val cx = centerScreenX - myLoc[0]
        val cy = centerScreenY - myLoc[1]
        val baseRadius = btn.buttonSizePx / 2f

        if (btn.config.enableOuterRing && btn.outerSlices.isNotEmpty()) {
            drawRing(canvas, btn, cx, cy, btn.innerRadiusPx, btn.outerRadiusPx, btn.outerSlices, ringId = 2)
        }

        if (btn.innerSlices.isNotEmpty()) {
            drawRing(canvas, btn, cx, cy, baseRadius, btn.innerRadiusPx, btn.innerSlices, ringId = 1)
        }

        paintFill.color = btn.mainColor
        paintFill.alpha = (btn.alphaRatio * 255).toInt()
        canvas.drawCircle(cx, cy, baseRadius, paintFill)
        paintStroke.alpha = (btn.alphaRatio * 255).toInt()
        canvas.drawCircle(cx, cy, baseRadius - 2f, paintStroke)
        paintText.textSize = baseRadius * 0.52f
        val yPos = cy - (paintText.descent() + paintText.ascent()) / 2
        val centerDisplay = if (btn.config.centerLabel.isNotBlank()) btn.config.centerLabel else "❖"
        canvas.drawText(centerDisplay, cx, yPos, paintText)
    }

    private fun drawRing(
        canvas: Canvas,
        btn: RadialMenuView,
        cx: Float,
        cy: Float,
        rIn: Float,
        rOut: Float,
        slices: List<KeySlice>,
        ringId: Int
    ) {
        val count = slices.size.coerceAtLeast(1)
        val sweepAngle = 360f / count
        val rectOuter = RectF(cx - rOut, cy - rOut, cx + rOut, cy + rOut)

        for (i in 0 until count) {
            val startAngle = i * sweepAngle - 90f - (sweepAngle / 2f)
            val isSel = (btn.selectedRing == ringId && btn.selectedIndex == i)
            paintFill.color = if (isSel) btn.accentColor else btn.mainColor
            paintFill.alpha = (btn.alphaRatio * 255).toInt()
            paintStroke.alpha = (btn.alphaRatio * 255).toInt()

            canvas.drawArc(rectOuter, startAngle, sweepAngle, true, paintFill)
            canvas.drawArc(rectOuter, startAngle, sweepAngle, true, paintStroke)

            val angleRad = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
            val textDist = (rIn + rOut) / 2f
            val tx = cx + (textDist * cos(angleRad)).toFloat()
            val ty = cy + (textDist * sin(angleRad)).toFloat() - (paintText.descent() + paintText.ascent()) / 2f

            paintText.textSize = ((rOut - rIn) * 0.34f).coerceIn(22f, 48f)
            canvas.drawText(slices[i].label, tx, ty, paintText)
        }
    }
}

class RadialMenuView(
    context: Context,
    var config: RadialButtonConfig,
    private val wheelRenderer: WheelRenderOverlayView,
    private val onKeySelected: (String) -> Unit,
    private val onMoveOverlay: (Int, Int) -> Unit,
    private val onMoveFinished: () -> Unit
) : View(context) {

    var innerSlices: List<KeySlice> = emptyList()
    var outerSlices: List<KeySlice> = emptyList()
    var buttonSizePx: Float = 180f
    var innerRadiusPx: Float = 380f
    var outerRadiusPx: Float = 560f
    var alphaRatio: Float = 0.85f
    var mainColor: Int = Color.parseColor("#222222")
    var accentColor: Int = Color.parseColor("#FF5722")

    var isExpanded = false
    var isMoveMode = false
    var selectedRing = 0
    var selectedIndex = -1
    private var quickFlickTriggered = false

    private var rawStartX = 0f
    private var rawStartY = 0f
    private var maxSlideDist = 0f
    private var downTimestamp = 0L
    private var lastDownTime = 0L

    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    fun syncFromConfig(density: Float) {
        innerSlices = config.parseSlices(config.innerKeys)
        outerSlices = config.parseSlices(config.outerKeys)
        buttonSizePx = dpToEvenPx(config.buttonSizeDp, density).toFloat()
        innerRadiusPx = dpToEvenPx(config.innerRadiusDp, density).toFloat()
        outerRadiusPx = dpToEvenPx(config.outerRadiusDp, density).toFloat()
        alphaRatio = config.opacity.coerceIn(10, 100) / 100f
        try { mainColor = Color.parseColor(config.mainColor) } catch (_: Exception) {}
        try { accentColor = Color.parseColor(config.accentColor) } catch (_: Exception) {}
        invalidate()
    }

    private fun dpToEvenPx(dp: Int, density: Float): Int {
        val px = (dp * density).toInt()
        return if (px % 2 == 0) px else px + 1
    }

    private fun getMaxActiveRadiusPx(): Float {
        return if (config.enableOuterRing && outerSlices.isNotEmpty()) outerRadiusPx else innerRadiusPx
    }

    private fun vibrate(ms: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isExpanded) return

        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = buttonSizePx / 2f

        paintFill.color = if (isMoveMode) accentColor else mainColor
        paintFill.alpha = (alphaRatio * 255).toInt()
        canvas.drawCircle(cx, cy, baseRadius, paintFill)
        paintStroke.alpha = (alphaRatio * 255).toInt()
        canvas.drawCircle(cx, cy, baseRadius - 2f, paintStroke)
        paintText.textSize = baseRadius * 0.55f
        val yPos = cy - (paintText.descent() + paintText.ascent()) / 2
        val centerDisplay = if (isMoveMode) "✥" else if (config.centerLabel.isNotBlank()) config.centerLabel else "❖"
        canvas.drawText(centerDisplay, cx, yPos, paintText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val now = System.currentTimeMillis()
                val loc = IntArray(2)
                getLocationOnScreen(loc)
                rawStartX = loc[0] + width / 2f
                rawStartY = loc[1] + height / 2f

                maxSlideDist = 0f
                downTimestamp = now
                selectedRing = 0
                selectedIndex = -1
                quickFlickTriggered = false

                if (now - lastDownTime < 320L) {
                    isMoveMode = true
                    isExpanded = false
                    wheelRenderer.activeButton = null
                    wheelRenderer.visibility = View.GONE
                    rawStartX = event.rawX
                    rawStartY = event.rawY
                    vibrate(45)
                } else {
                    isMoveMode = false
                    isExpanded = true
                    wheelRenderer.centerScreenX = rawStartX
                    wheelRenderer.centerScreenY = rawStartY
                    wheelRenderer.activeButton = this
                    wheelRenderer.visibility = View.VISIBLE
                    wheelRenderer.invalidate()
                }
                lastDownTime = now
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isMoveMode) {
                    val dx = (event.rawX - rawStartX).toInt()
                    val dy = (event.rawY - rawStartY).toInt()
                    if (abs(dx) > 1 || abs(dy) > 1) {
                        rawStartX = event.rawX
                        rawStartY = event.rawY
                        onMoveOverlay(dx, dy)
                    }
                } else if (isExpanded) {
                    val dx = event.rawX - rawStartX
                    val dy = event.rawY - rawStartY
                    val slideDist = hypot(dx, dy)
                    if (slideDist > maxSlideDist) maxSlideDist = slideDist

                    val deadZone = buttonSizePx * 0.50f

                    if (slideDist <= deadZone) {
                        quickFlickTriggered = false
                        if (selectedRing != 0 || selectedIndex != -1) {
                            selectedRing = 0
                            selectedIndex = -1
                            wheelRenderer.invalidate()
                        }
                    } else if (!quickFlickTriggered) {
                        val useOuter = config.enableOuterRing && outerSlices.isNotEmpty() && slideDist > innerRadiusPx
                        val activeRing = if (useOuter) 2 else 1
                        val activeList = if (useOuter) outerSlices else innerSlices

                        if (activeList.isNotEmpty()) {
                            val count = activeList.size
                            val sliceAngle = 360f / count
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            angle = (angle + 90f + (sliceAngle / 2f) + 360f) % 360f
                            val newIndex = (angle / sliceAngle).toInt() % count

                            if (activeRing != selectedRing || newIndex != selectedIndex) {
                                selectedRing = activeRing
                                selectedIndex = newIndex
                                vibrate(10)
                                wheelRenderer.invalidate()
                            }

                            val flickThreshold = getMaxActiveRadiusPx() * 0.88f
                            if (config.quickFlick && slideDist >= flickThreshold) {
                                quickFlickTriggered = true
                                vibrate(30)
                                onKeySelected(activeList[selectedIndex].keySpec)
                            }
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isMoveMode) {
                    isMoveMode = false
                    vibrate(20)
                    onMoveFinished()
                } else if (isExpanded) {
                    val pressDuration = System.currentTimeMillis() - downTimestamp
                    val deadZone = buttonSizePx * 0.50f

                    if (!quickFlickTriggered) {
                        if (maxSlideDist < deadZone && pressDuration < 280L && config.centerTapKey.isNotBlank()) {
                            vibrate(20)
                            onKeySelected(config.centerTapKey)
                        } else if (selectedRing == 1 && selectedIndex in innerSlices.indices) {
                            vibrate(25)
                            onKeySelected(innerSlices[selectedIndex].keySpec)
                        } else if (selectedRing == 2 && selectedIndex in outerSlices.indices) {
                            vibrate(25)
                            onKeySelected(outerSlices[selectedIndex].keySpec)
                        }
                    }

                    isExpanded = false
                    selectedRing = 0
                    selectedIndex = -1
                    quickFlickTriggered = false
                    if (wheelRenderer.activeButton == this) {
                        wheelRenderer.activeButton = null
                        wheelRenderer.visibility = View.GONE
                    }
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
