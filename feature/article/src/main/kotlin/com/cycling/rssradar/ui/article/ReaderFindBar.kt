package com.cycling.rssradar.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.ui.theme.radarColors
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X

/**
 * 查找在当前正文里的**能力边界**：做不到的事要说出来，而不是让按钮看起来能用（UI 铁律）。
 *
 * [NO_AUTO_SCROLL]：整页模式的 WebView 正文由外层 Compose 滚动，WebView 自己滚不动，
 * 命中会被平台高亮，但「上一处/下一处」带不过去视口。
 * [UNSUPPORTED]：译文分段渲染没有接查找（按段落重排后的文本与原文对不上）。
 */
internal enum class FindLimit { NONE, NO_AUTO_SCROLL, UNSUPPORTED }

/**
 * 阅读页的页内查找栏：输入 → 命中计数 → 上一处/下一处 → 关闭。
 *
 * 放在正文区顶部（顶部工具栏之下、正文之上），不随正文滚动跑掉——
 * 它是**阅读时的常驻工具**，滚没了就等于每找一次都要重新打开。
 */
@Composable
internal fun ReaderFindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    count: Int,
    cursor: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    limit: FindLimit = FindLimit.NONE,
) {
    val colors = radarColors()
    val focusRequester = remember { FocusRequester() }
    // 打开就聚焦：查找栏的唯一用途就是输入，还要用户再点一下输入框是多余的
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface1)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Lucide.Search,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.find_hint),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            Text(
                text = "$cursor/$count",
                color = if (count == 0) colors.textSecondary else colors.textPrimary,
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(onClick = onPrevious, enabled = count > 0) {
                Icon(
                    Lucide.ChevronUp,
                    contentDescription = stringResource(R.string.find_previous),
                    tint = if (count > 0) colors.textPrimary else colors.textSecondary,
                )
            }
            IconButton(onClick = onNext, enabled = count > 0) {
                Icon(
                    Lucide.ChevronDown,
                    contentDescription = stringResource(R.string.find_next),
                    tint = if (count > 0) colors.textPrimary else colors.textSecondary,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Lucide.X, contentDescription = stringResource(R.string.find_close), tint = colors.textPrimary)
            }
        }
        // 无命中：说清是"没找到"而不是"还在找"；能力受限：说清为什么
        val note = when {
            limit == FindLimit.UNSUPPORTED -> stringResource(R.string.find_unsupported)
            query.isNotBlank() && count == 0 -> stringResource(R.string.find_no_result)
            query.isNotBlank() && limit == FindLimit.NO_AUTO_SCROLL ->
                stringResource(R.string.find_no_auto_scroll)
            else -> null
        }
        if (note != null) {
            Text(
                text = note,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface1)
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}
