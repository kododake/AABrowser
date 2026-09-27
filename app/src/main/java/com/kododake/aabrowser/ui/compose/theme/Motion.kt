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

package com.kododake.aabrowser.ui.compose.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.unit.Dp

/**
 * Material 3 Expressive motion specifications with spring physics.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object ExpressiveMotion {

    val scheme: MotionScheme
        get() = MotionScheme.expressive()

    val BouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val DpBouncySpring = spring<Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val SpatialSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    val CornerMorphSpring = spring<Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> fastSpatial(): FiniteAnimationSpec<T> = scheme.fastSpatialSpec()
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = scheme.defaultSpatialSpec()
    fun <T> slowSpatial(): FiniteAnimationSpec<T> = scheme.slowSpatialSpec()
    fun <T> fastEffects(): FiniteAnimationSpec<T> = scheme.fastEffectsSpec()
    fun <T> defaultEffects(): FiniteAnimationSpec<T> = scheme.defaultEffectsSpec()
    fun <T> slowEffects(): FiniteAnimationSpec<T> = scheme.slowEffectsSpec()
}
