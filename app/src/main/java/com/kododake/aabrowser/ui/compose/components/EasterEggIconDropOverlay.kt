/*
 * Copyright (C) 2025 AABrowser Contributors (https://github.com/kododake/AABrowser)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.kododake.aabrowser.ui.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.kododake.aabrowser.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
//🥚
private class FallingIconBall(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    var vr: Float,
    val radius: Float,
    val iconIndex: Int,
    var wobbleAmp: Float = 0.2f,
    var wobblePhase: Float = 0f
)

fun Modifier.easterEggReleaseDetector(
    onRelease: () -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.pressed }) {
                onRelease()
                break
            }
        }
    }
}

@Composable
fun EasterEggIconDropOverlay(
    isHolding: Boolean,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val currentHolding by rememberUpdatedState(isHolding)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val iconSizePx = with(density) { 28.dp.roundToPx() }.coerceAtLeast(32)
    val radiusPx = with(density) { 21.dp.toPx() }

    val bitmaps = remember(iconSizePx) {
        listOf(
            R.drawable.ic_github,
            R.mipmap.ic_launcher,
            R.drawable.ic_aa_anything
        ).mapNotNull { resId ->
            ContextCompat.getDrawable(context, resId)?.toBitmap(iconSizePx, iconSizePx)?.asImageBitmap()
        }
    }

    val balls = remember { mutableStateListOf<FallingIconBall>() }
    val config = LocalConfiguration.current
    val fallbackWidth = with(density) { config.screenWidthDp.dp.roundToPx() }
    val fallbackHeight = with(density) { config.screenHeightDp.dp.roundToPx() }
    var areaSize by remember { mutableStateOf(IntSize(fallbackWidth, fallbackHeight)) }
    var frameTick by remember { mutableIntStateOf(0) }
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    LaunchedEffect(bitmaps) {
        if (bitmaps.isEmpty()) {
            currentOnFinished()
            return@LaunchedEffect
        }
        var lastFrameNs = 0L
        var spawnTimer = 0.14f
        while (true) {
            withFrameNanos { frameNs ->
                val dt = if (lastFrameNs == 0L) 0.016f else ((frameNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                lastFrameNs = frameNs
                val width = areaSize.width.toFloat()
                val height = areaSize.height.toFloat()
                if (width > 0f && height > 0f) {
                    if (currentHolding) {
                        spawnTimer += dt
                        if (spawnTimer >= 0.14f && balls.size < 55) {
                            spawnTimer = 0f
                            balls.add(
                                FallingIconBall(
                                    x = Random.nextFloat() * (width - radiusPx * 2f) + radiusPx,
                                    y = -radiusPx,
                                    vx = (Random.nextFloat() - 0.5f) * 260f,
                                    vy = 180f + Random.nextFloat() * 140f,
                                    rotation = (Random.nextFloat() - 0.5f) * 24f,
                                    vr = (Random.nextFloat() - 0.5f) * 140f,
                                    radius = radiusPx,
                                    iconIndex = Random.nextInt(bitmaps.size)
                                )
                            )
                        }
                    }
                    stepPhysics(balls, width, height, dt, hasFloor = currentHolding)
                    if (!currentHolding) {
                        balls.removeAll { it.y - it.radius > height + 40f }
                    }
                    frameTick++
                }
            }
            if (!currentHolding && balls.isEmpty()) {
                currentOnFinished()
                break
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { areaSize = it }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (frameTick < 0) return@Canvas
            for (ball in balls) {
                val wobble = sin(ball.wobblePhase) * ball.wobbleAmp
                val sx = (1f + wobble).coerceIn(0.75f, 1.3f)
                val sy = (1f - wobble).coerceAtLeast(0.75f).coerceAtMost(1.3f)
                val bmp: ImageBitmap = bitmaps[ball.iconIndex]
                withTransform({
                    translate(left = ball.x, top = ball.y)
                    rotate(degrees = ball.rotation, pivot = Offset.Zero)
                    scale(scaleX = sx, scaleY = sy, pivot = Offset.Zero)
                }) {
                    drawCircle(color = surfaceColor, radius = ball.radius, center = Offset.Zero)
                    drawCircle(color = outlineColor, radius = ball.radius, center = Offset.Zero, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                    val half = (ball.radius * 0.68f).roundToInt()
                    drawImage(
                        image = bmp,
                        dstOffset = IntOffset(-half, -half),
                        dstSize = IntSize(half * 2, half * 2)
                    )
                }
            }
        }
    }
}

private fun stepPhysics(
    balls: MutableList<FallingIconBall>,
    width: Float,
    height: Float,
    dt: Float,
    hasFloor: Boolean
) {
    val gravity = 2400f
    for (b in balls) {
        b.vy += gravity * dt
        b.vx *= (1f - 0.8f * dt).coerceAtLeast(0f)
        b.x += b.vx * dt
        b.y += b.vy * dt
        b.rotation += b.vr * dt
        b.vr *= (1f - 1.5f * dt).coerceAtLeast(0f)
        b.wobblePhase += 22f * dt
        b.wobbleAmp = (b.wobbleAmp - 0.65f * dt).coerceAtLeast(0f)

        if (b.x - b.radius < 0f) {
            b.x = b.radius
            b.vx = abs(b.vx) * 0.5f
            b.wobbleAmp = (b.wobbleAmp + 0.14f).coerceAtMost(0.28f)
        } else if (b.x + b.radius > width) {
            b.x = width - b.radius
            b.vx = -abs(b.vx) * 0.5f
            b.wobbleAmp = (b.wobbleAmp + 0.14f).coerceAtMost(0.28f)
        }

        if (hasFloor && b.y + b.radius > height) {
            b.y = height - b.radius
            if (b.vy > 140f) {
                b.wobbleAmp = (b.wobbleAmp + 0.22f).coerceAtMost(0.32f)
            }
            b.vy = -b.vy * 0.42f
            if (abs(b.vy) < 40f) b.vy = 0f
            b.vx *= 0.92f
        }
    }

    for (i in 0 until balls.size) {
        val a = balls[i]
        for (j in i + 1 until balls.size) {
            val b = balls[j]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val dist = hypot(dx, dy)
            val minDist = a.radius + b.radius
            if (dist > 0.001f && dist < minDist) {
                val nx = dx / dist
                val ny = dy / dist
                val overlap = (minDist - dist) * 0.5f
                a.x -= nx * overlap
                a.y -= ny * overlap
                b.x += nx * overlap
                b.y += ny * overlap
                val rvx = b.vx - a.vx
                val rvy = b.vy - a.vy
                val velAlongNormal = rvx * nx + rvy * ny
                if (velAlongNormal < 0f) {
                    val impulse = -(1f + 0.45f) * velAlongNormal * 0.5f
                    a.vx -= impulse * nx
                    a.vy -= impulse * ny
                    b.vx += impulse * nx
                    b.vy += impulse * ny
                    if (abs(velAlongNormal) > 120f) {
                        a.wobbleAmp = (a.wobbleAmp + 0.14f).coerceAtMost(0.28f)
                        b.wobbleAmp = (b.wobbleAmp + 0.14f).coerceAtMost(0.28f)
                    }
                }
            }
        }
    }
}
