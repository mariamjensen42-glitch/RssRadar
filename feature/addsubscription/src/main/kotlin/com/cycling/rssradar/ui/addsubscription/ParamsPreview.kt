package com.cycling.rssradar.ui.addsubscription

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.CircleCheckBig
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.core.model.rsshub.RouteExample
import com.cycling.rssradar.core.model.rsshub.RssHubRoute
import com.cycling.rssradar.core.ui.components.FeedIcon
import com.cycling.rssradar.core.ui.text.resolve
import com.cycling.rssradar.core.ui.theme.Success
import com.cycling.rssradar.core.ui.theme.radarColors

@Composable

internal fun ParamsHeader(route: RssHubRoute, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.add_back_to_catalog), tint = radarColors().textPrimary)
        }
        FeedIcon(title = route.sourceName, size = 32.dp, cornerRadius = 9.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.name,
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = route.sourceName,
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* --------------------------- 阶段一：路由目录 --------------------------- */

@Composable

internal fun PreviewResult(
    state: AddSubscriptionUiState,
    viewModel: AddSubscriptionViewModel,
) {
    Column {
        if (state.url.isNotBlank()) {
            CodeBlock(text = state.url, accent = true)
            Spacer(Modifier.height(8.dp))
        }
        if (state.isValidating) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = radarColors().accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    // 路由拼出来的地址要等 RSSHub 现抓上游站点，十几秒是常态。
                    // 不说清就是干转圈，用户会以为卡死了（UI 铁律：静默 = 坏了）。
                    text = if (state.isUrlFromRoute) {
                        stringResource(R.string.add_validating_slow)
                    } else {
                        stringResource(R.string.add_validating)
                    },
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        ValidationBanner(info = state.validation)
        if (state.validation is ValidationInfo.Valid) {
            Spacer(Modifier.height(6.dp))
            FieldLabel(stringResource(R.string.add_group))
            Spacer(Modifier.height(6.dp))
            GroupChips(
                options = state.groupOptions,
                selected = state.selectedGroup,
                onSelect = { viewModel.onIntent(AddSubscriptionIntent.GroupSelected(it)) },
            )
            Spacer(Modifier.height(12.dp))
            // 与目录步同一套：选中态读 effectiveContentType，没挑过就是地址预判结果
            FieldLabel(stringResource(R.string.add_content_type))
            Spacer(Modifier.height(6.dp))
            ContentTypeChips(
                selected = state.effectiveContentType,
                onSelect = { viewModel.onIntent(AddSubscriptionIntent.ContentTypeSelected(it)) },
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.add_subscribe),
                enabled = state.canSubmit,
                loading = state.isAdding,
                onClick = { viewModel.onIntent(AddSubscriptionIntent.Submit) },
            )
        }
    }
}

/**
 * 示例选择。
 *
 * 多数路由的参数（uid / 板块 id / 分类码）用户根本背不下来，而 RSSHub 元数据里
 * 每条路由都带了跑通过的示例——点一下比手填靠谱得多。
 */
@Composable

internal fun ExamplePicker(
    examples: List<RouteExample>,
    onSelect: (RouteExample) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.add_example),
            color = radarColors().textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(examples.size, key = { examples[it].path }) { index ->
                val example = examples[index]
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = radarColors().surface2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(example) },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = example.title.ifBlank { example.path.substringAfterLast('/') },
                            color = radarColors().textPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (example.title.isNotBlank()) {
                            Text(
                                text = example.path,
                                color = radarColors().textTertiary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable

internal fun CodeBlock(text: String, accent: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (accent) radarColors().surface2 else Color(0xFF111114),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            color = if (accent) radarColors().link else radarColors().textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/* ------------------------------ 通用件 ------------------------------ */

@Composable

internal fun ValidationBanner(info: ValidationInfo) {
    if (info is ValidationInfo.Idle) return
    val color = when (info) {
        is ValidationInfo.Valid -> Success
        // 发现到候选不是错误，是进展：用强调色而非报错红
        is ValidationInfo.Discovered -> radarColors().accent
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (info is ValidationInfo.Valid) {
            Icon(
                imageVector = Lucide.CircleCheckBig,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = info.message.resolve(),
            color = color,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
