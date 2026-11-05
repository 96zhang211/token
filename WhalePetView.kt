package com.whalechan.balance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.min
import kotlin.math.sin

/**
 * 桌宠本体：用 Canvas 现画的鲸鱼娘，不占图片资源。
 * 默认会轻轻上下浮动；被戳一下会喷水、冒个气泡。
 * 后面想换成自己的立绘，把这个类的 onDraw 换成 drawBitmap 就行。
 */
class WhalePetView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        color = 0xFF1E3A8A.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() }
    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59FF8FA3 }
    private val sprayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7DD3FC.toInt() }
    private val bubbleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E3A8A.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
    }

    private var phase = 0f
    private var pokeAt = 0L
    private var bubbleText: String? = null

    private val tailPath = Path()
    private val finPath = Path()
    private val bodyRect = RectF()

    init {
        // 呼吸节拍：一直转，负责上下浮动
        postOnAnimation(object : Runnable {
            override fun run() {
                phase += 0.045f
                if (phase > 6.283f) phase -= 6.283f
                invalidate()
                postOnAnimation(this)
            }
        })
    }

    /** 被戳了 */
    fun poke() {
        pokeAt = System.currentTimeMillis()
        bubbleText = POKE_WORDS.random()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 全图按 100x100 的单位来画，自动缩放到当前大小
        val s = min(w, h) / 100f
        val bob = sin(phase) * 2.4f * s
        val now = System.currentTimeMillis()
        val pokeProgress = (now - pokeAt).toFloat() / 1000f

        canvas.save()
        canvas.translate(w / 2f, h / 2f + bob)
        drawSpray(canvas, s, pokeProgress)
        drawWhale(canvas, s, pokeProgress)
        canvas.restore()

        if (bubbleText != null && pokeProgress < 2.2f) {
            drawBubble(canvas, s, w, h, pokeProgress)
        } else if (pokeProgress >= 2.2f) {
            bubbleText = null
        }
    }

    private fun drawWhale(canvas: Canvas, s: Float, pokeProgress: Float) {
        // 被戳的时候轻轻缩一下
        val squash = if (pokeProgress < 0.14f) 1f - (0.14f - pokeProgress) * 0.9f else 1f
        val scale = 0.97f + 0.03f * squash
        canvas.save()
        canvas.scale(scale, scale)

        // 尾巴
        tailPath.reset()
        tailPath.moveTo(24f * s, 2f * s)
        tailPath.lineTo(44f * s, -16f * s)
        tailPath.lineTo(37f * s, 2f * s)
        tailPath.lineTo(44f * s, 20f * s)
        tailPath.close()
        bodyPaint.shader = null
        bodyPaint.color = 0xFF60A5FA.toInt()
        canvas.drawPath(tailPath, bodyPaint)
        canvas.drawPath(tailPath, outlinePaint)

        // 身体
        bodyRect.set(-34f * s, -24f * s, 30f * s, 24f * s)
        bodyPaint.shader = LinearGradient(
            bodyRect.left, bodyRect.top, bodyRect.right, bodyRect.bottom,
            intArrayOf(0xFF93C5FD.toInt(), 0xFF3B82F6.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(bodyRect, bodyPaint)
        canvas.drawOval(bodyRect, outlinePaint)

        // 肚子上的浅色
        bodyPaint.shader = null
        bodyPaint.color = 0x55FFFFFF
        bodyRect.set(-22f * s, 2f * s, 22f * s, 22f * s)
        canvas.drawOval(bodyRect, bodyPaint)

        // 侧鳍
        finPath.reset()
        finPath.moveTo(-10f * s, 10f * s)
        finPath.quadTo(-2f * s, 26f * s, 10f * s, 14f * s)
        finPath.quadTo(0f, 8f * s, -10f * s, 10f * s)
        finPath.close()
        bodyPaint.color = 0xFF3B82F6.toInt()
        canvas.drawPath(finPath, bodyPaint)
        canvas.drawPath(finPath, outlinePaint)

        // 鲸鳍耳（脑袋两侧的鳍）
        bodyPaint.color = 0xFF2563EB.toInt()
        bodyRect.set(-40f * s, -6f * s, -28f * s, 6f * s)
        canvas.drawOval(bodyRect, bodyPaint)
        bodyRect.set(24f * s, -6f * s, 36f * s, 6f * s)
        canvas.drawOval(bodyRect, bodyPaint)

        // 眼睛（戳一下会眯起来）
        val eyeRy = if (pokeProgress < 0.5f) 4.6f * s else 5.6f * s
        val eyes = floatArrayOf(-14f * s, 6f * s)
        for (ex in eyes) {
            bodyRect.set(ex - 6.2f * s, -13f * s - eyeRy, ex + 6.2f * s, -13f * s + eyeRy)
            canvas.drawOval(bodyRect, whitePaint)
            canvas.drawOval(bodyRect, outlinePaint)
            bodyRect.set(ex - 2.8f * s, -15f * s, ex + 2.8f * s, -10f * s)
            canvas.drawOval(bodyRect, pupilPaint)
        }

        // 腮红
        bodyRect.set(-26f * s, -6f * s, -14f * s, 0f)
        canvas.drawOval(bodyRect, blushPaint)
        bodyRect.set(14f * s, -6f * s, 26f * s, 0f)
        canvas.drawOval(bodyRect, blushPaint)

        // 小嘴
        bodyRect.set(-6f * s, -3f * s, 2f * s, 4f * s)
        canvas.drawArc(bodyRect, 20f, 140f, false, outlinePaint)

        canvas.restore()
    }

    private fun drawSpray(canvas: Canvas, s: Float, progress: Float) {
        if (progress > 1.1f) return
        val t = progress / 1.1f
        sprayPaint.alpha = ((1f - t) * 220).toInt().coerceIn(0, 255)
        for (i in 0 until 5) {
            val spread = (i - 2) * 5f
            val rise = t * (30f + i * 3f)
            val r = (2.2f - t * 1.2f).coerceAtLeast(0.6f) * s
            canvas.drawCircle(
                (-4f * s) + spread * s * 0.6f,
                (-30f * s) - rise * s,
                r,
                sprayPaint
            )
        }
    }

    private fun drawBubble(canvas: Canvas, s: Float, w: Float, h: Float, progress: Float) {
        val text = bubbleText ?: return
        val alpha = if (progress > 1.7f) ((2.2f - progress) / 0.5f * 255).toInt() else 255
        bubbleBgPaint.alpha = alpha.coerceIn(0, 255)
        bubbleTextPaint.alpha = alpha.coerceIn(0, 255)

        val padX = 7f * density
        val padY = 5f * density
        val tw = bubbleTextPaint.measureText(text)
        val th = bubbleTextPaint.textSize
        val cx = w / 2f + 6f * s
        val cy = h / 2f - 44f * s

        val rect = RectF(cx - tw / 2f - padX, cy - th - padY, cx + tw / 2f + padX, cy + padY)
        canvas.drawRoundRect(rect, 8f * density, 8f * density, bubbleBgPaint)
        // 小尾巴
        val path = Path()
        path.moveTo(cx - 3f * density, rect.bottom)
        path.lineTo(cx + 4f * density, rect.bottom)
        path.lineTo(cx - 1f * density, rect.bottom + 6f * density)
        path.close()
        canvas.drawPath(path, bubbleBgPaint)

        canvas.drawText(text, cx, rect.bottom - padY, bubbleTextPaint)
    }

    companion object {
        private val POKE_WORDS = listOf("诶！", "咕噜~", "别戳啦", "在呢在呢", "白饭呢")
    }
}
