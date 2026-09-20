package com.tvengineer.pro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF070A10)
val Panel = Color(0xFF121722)
val Panel2 = Color(0xFF171D29)
val Cyan = Color(0xFF04D9F5)
val Cyan2 = Color(0xFF00A8C6)
val Text = Color(0xFFF2F7FA)
val Muted = Color(0xFF9AA4B2)
val Red = Color(0xFFFF4E67)
val Amber = Color(0xFFFFA200)
val Green = Color(0xFF46D58A)

private val colors= darkColorScheme(
    primary=Cyan, secondary=Cyan2, background=Bg, surface=Panel,
    onPrimary=Color.Black,onSecondary=Color.Black,onBackground=Text,onSurface=Text,
    error=Red
)

@Composable
fun TVEngineerTheme(content:@Composable()->Unit){
    MaterialTheme(colorScheme=colors,content=content)
}
