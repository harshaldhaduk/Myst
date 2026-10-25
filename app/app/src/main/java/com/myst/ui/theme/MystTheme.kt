package com.myst.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * White ground, plum-grey ink, and three pastels that each mean something: blush for family,
 * periwinkle for medicine reminders, lilac for Myst itself. Text on a pastel is always [Ink].
 */
object Myst {
  val White = Color(0xFFFFFFFF)
  val Ink = Color(0xFF3B3552)
  val InkSoft = Color(0xFF746D8C)
  val Line = Color(0xFFE9E4F3)
  val Mist = Color(0xFFF8F6FC)

  val Periwinkle = Color(0xFFB9C8F8)
  val PeriwinkleWash = Color(0xFFEFF3FE)
  val Lilac = Color(0xFFD2BEF4)
  val LilacStrong = Color(0xFFB79CEB)
  val LilacWash = Color(0xFFF5F0FD)
  val Blush = Color(0xFFF7C3DA)
  val BlushWash = Color(0xFFFDF0F5)

  val Rose = Color(0xFFB4456A)
  val Leaf = Color(0xFF6BBF9A)
}

private val Display = FontFamily.Serif
private val Body = FontFamily.SansSerif

private val MystTypography =
    Typography(
        displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 40.sp),
        headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = Body, fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
        labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    )

private val MystColors =
    lightColorScheme(
        primary = Myst.LilacStrong,
        onPrimary = Myst.Ink,
        primaryContainer = Myst.Lilac,
        onPrimaryContainer = Myst.Ink,
        secondary = Myst.Periwinkle,
        onSecondary = Myst.Ink,
        secondaryContainer = Myst.PeriwinkleWash,
        onSecondaryContainer = Myst.Ink,
        tertiary = Myst.Blush,
        onTertiary = Myst.Ink,
        tertiaryContainer = Myst.BlushWash,
        onTertiaryContainer = Myst.Ink,
        background = Myst.White,
        onBackground = Myst.Ink,
        surface = Myst.White,
        onSurface = Myst.Ink,
        surfaceVariant = Myst.Mist,
        onSurfaceVariant = Myst.InkSoft,
        surfaceContainerLowest = Myst.White,
        surfaceContainerLow = Myst.Mist,
        surfaceContainer = Myst.Mist,
        surfaceContainerHigh = Myst.LilacWash,
        surfaceContainerHighest = Myst.LilacWash,
        outline = Color(0xFFD6CFE6),
        outlineVariant = Myst.Line,
        error = Myst.Rose,
        onError = Myst.White,
        errorContainer = Myst.BlushWash,
        onErrorContainer = Myst.Rose,
    )

private val MystShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(26.dp),
        extraLarge = RoundedCornerShape(34.dp),
    )

@Composable
fun MystTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = MystColors, typography = MystTypography, shapes = MystShapes, content = content)
}
