package com.custom.radialpad

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class RadialMenuView(
    context: Context,
    private val onKeySelected: (String) -> Unit,
    private val onMoveOverlay: (Int, Int) -> Unit,
    private val onExpandChanged: (Boolean) -> Unit
) : View(context) {

    var keys: List<String> = listOf("1", "2", "3", "4", "5", "6")
    var buttonSizePx: Float = 180f
    var menuRadiusPx: Float = 420f
    var alphaRatio: Float = 0.85f
    var mainColor: Int = Color.parseColor("#222222")
    var accentColor: Int = Color.parseColor("#FF5722")

    private var isExpanded = false
    private var isMoveMode = false
    private var selectedIndex = -1
    private var rawStartX = 0f
    private var rawStartY = 0f
    private var lastDownTime = 0L

    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

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
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = buttonSizePx / 2f

        if (!isExpanded) {
            paintFill.color = if (isMoveMode) accentColor else mainColor
            paintFill.alpha = (alphaRatio * 255).toInt()
            canvas.drawCircle(cx, cy, baseRadius, paintFill)
            paintStroke.alpha = (alphaRatio * 255).toInt()
            canvas.drawCircle(cx, cy, baseRadius - 2f, paintStroke)
            paintText.textSize = baseRadius * 0.6f
            val yPos = cy - (paintText.descent() + paintText.ascent()) / 2
            canvas.drawText(if (isMoveMode) "✥" else "❖", cx, yPos, paintText)
        } else {
            val count = keys.size.coerceAtLeast(1)
            val sweepAngle = 360f / count
            val rectOuter = RectF(cx - menuRadiusPx, cy - menuRadiusPx, cx + menuRadiusPx, cy + menuRadiusPx)

            for (i in 0 until count) {
                val startAngle = i * sweepAngle - 90f - (sweepAngle / 2f)
                paintFill.color = if (i == selectedIndex) accentColor else mainColor
                paintFill.alpha = (alphaRatio * 255).toInt()

                canvas.drawArc(rectOuter, startAngle, sweepAngle, true, paintFill)
                canvas.drawArc(rectOuter, startAngle, sweepAngle, true, paintStroke)

                val angleRad = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
                val textDist = (baseRadius + menuRadiusPx) / 2f
                val tx = cx + (textDist * cos(angleRad)).toFloat()
                val ty = cy + (textDist * sin(angleRad)).toFloat() - (paintText.descent() + paintText.ascent()) / 2f

                paintText.textSize = (menuRadiusPx - baseRadius) * 0.28f
                canvas.drawText(keys[i], tx, ty, paintText)
            }

            // Bouton central exactement au même endroit et à la même taille
            paintFill.color = mainColor
            paintFill.alpha = (alphaRatio * 255).toInt()
            canvas.drawCircle(cx, cy, baseRadius, paintFill)
            canvas.drawCircle(cx, cy, baseRadius - 2f, paintStroke)
            paintText.textSize = baseRadius * 0.6f
            val yPos = cy - (paintText.descent() + paintText.ascent()) / 2
            canvas.drawText("❖", cx, yPos, paintText)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val now = System.currentTimeMillis()
                rawStartX = event.rawX
                rawStartY = event.rawY
                selectedIndex = -1

                // Double-tap (< 320ms) = Mode déplacement
                if (now - lastDownTime < 320L) {
                    isMoveMode = true
                    isExpanded = false
                    onExpandChanged(false)
                    vibrate(45)
                } else {
                    isMoveMode = false
                    isExpanded = true
                    onExpandChanged(true)
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
                    // Calcul basé uniquement sur la distance réelle glissée par le doigt à l'écran
                    val dx = event.rawX - rawStartX
                    val dy = event.rawY - rawStartY
                    val slideDist = hypot(dx, dy)
                    val deadZone = (buttonSizePx * 0.55f).coerceAtLeast(35f)

                    if (slideDist > deadZone) {
                        val count = keys.size.coerceAtLeast(1)
                        val sliceAngle = 360f / count
                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        angle = (angle + 90f + (sliceAngle / 2f) + 360f) % 360f
                        val newIndex = (angle / sliceAngle).toInt() % count
                        if (newIndex != selectedIndex) {
                            selectedIndex = newIndex
                            vibrate(10)
                            invalidate()
                        }
                    } else if (selectedIndex != -1) {
                        selectedIndex = -1
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isMoveMode) {
                    isMoveMode = false
                    vibrate(20)
                } else if (isExpanded) {
                    if (selectedIndex in keys.indices) {
                        vibrate(25)
                        onKeySelected(keys[selectedIndex])
                    }
                    isExpanded = false
                    selectedIndex = -1
                    onExpandChanged(false)
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
