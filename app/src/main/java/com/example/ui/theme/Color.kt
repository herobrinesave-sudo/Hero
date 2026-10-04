package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Futuristic Cyber Dark Theme Palette
val SpaceBlack = Color(0xFF090D16)
val DeepDark = Color(0xFF0D121F)
val SurfaceDark = Color(0xFF13192B)
val CardBackground = Color(0xFF172036)
val CardBorder = Color(0xFF263352)

// Neon & Futuristic Accents
val CyberCyan = Color(0xFF00F0FF)
val ElectricBlue = Color(0xFF3B82F6)
val NeonPurple = Color(0xFF8B5CF6)
val VibrantViolet = Color(0xFFA855F7)
val AccentPink = Color(0xFFEC4899)
val NeonGreen = Color(0xFF10B981)

// Text Colors
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextTertiary = Color(0xFF64748B)

// Message Bubbles
val UserBubbleGradientStart = Color(0xFF2563EB)
val UserBubbleGradientEnd = Color(0xFF7C3AED)
val AiBubbleBackground = Color(0xFF161F33)
val AiBubbleBorder = Color(0xFF2B3A5C)

// Gradient Brushes
val CyberGradient = Brush.horizontalGradient(
    colors = listOf(CyberCyan, NeonPurple)
)

val HeroGlowGradient = Brush.radialGradient(
    colors = listOf(CyberCyan.copy(alpha = 0.25f), Color.Transparent)
)

val UserBubbleBrush = Brush.linearGradient(
    colors = listOf(UserBubbleGradientStart, UserBubbleGradientEnd)
)

val CardBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF151D30), Color(0xFF1A243D))
)
