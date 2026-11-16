package com.superdash.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.superdash.R

private const val FALLBACK_ICON = "${MDI_PREFIX}help-circle-outline"

private val mdiFontFamily = FontFamily(Font(R.font.materialdesignicons))

@Composable
fun rememberMdiCodepoints(): MdiCodepoints {
    val assets = LocalContext.current.applicationContext.assets
    return remember(assets) { MdiCodepoints.shared(assets) }
}

@Composable
fun MdiIcon(
    name: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    val codepoints = rememberMdiCodepoints()
    val codepoint = codepoints.codepoint(name) ?: checkNotNull(codepoints.codepoint(FALLBACK_ICON))
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Text(
        text = String(Character.toChars(codepoint)),
        modifier = modifier.size(size).clearAndSetSemantics {},
        color = tint,
        style =
            TextStyle(
                fontFamily = mdiFontFamily,
                fontSize = fontSize,
                lineHeight = fontSize,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle =
                    LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
            ),
    )
}
