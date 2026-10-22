package com.cycling.rssradar.ui.addsubscription

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Rss
import com.cycling.rssradar.core.model.rsshub.RouteCategory
import com.cycling.rssradar.core.model.rsshub.RssHubRoute
import com.cycling.rssradar.core.ui.R as UiR
import com.cycling.rssradar.core.ui.components.FeedIcon

@Composable

internal fun CatalogHeader(title: String, subtitle: String?, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 页面语义用返回箭头（此前是抽屉的关闭 X）
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Lucide.ArrowLeft,
                contentDescription = stringResource(UiR.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Lucide.Rss,
                    contentDescription = null,
                    tint = RssHubOrange,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "RSSHub",
                    color = RssHubOrange,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable

internal fun ColumnScope.CatalogContent(
    state: AddSubscriptionUiState,
    viewModel: AddSubscriptionViewModel,
) {
    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                FieldLabel(stringResource(R.string.add_field_url))
                Spacer(Modifier.height(8.dp))
                UrlField(
                    value = state.url,
                    onChange = { viewModel.onIntent(AddSubscriptionIntent.UrlChange(it)) },
                    isLoading = state.isValidating,
                )
                ValidationBanner(info = state.validation)
                // 自动发现（#5）：贴的是站点首页时列出找到的订阅源，点一条即采用
                if (state.isDiscovering) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.add_discovering),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (state.discovered.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    state.discovered.forEach { feed ->
                        DiscoveredFeedRow(
                            feed = feed,
                            onClick = {
                                viewModel.onIntent(AddSubscriptionIntent.PickDiscovered(feed))
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (state.validation is ValidationInfo.Valid) {
                    Spacer(Modifier.height(10.dp))
                    FieldLabel(stringResource(R.string.add_group))
                    Spacer(Modifier.height(6.dp))
                    GroupChips(
                        options = state.groupOptions,
                        selected = state.selectedGroup,
                        onSelect = { viewModel.onIntent(AddSubscriptionIntent.GroupSelected(it)) },
                    )
                    Spacer(Modifier.height(12.dp))
                    // 内容类型：选中态用的是 effectiveContentType，没手动挑过时它就是地址预判的结果，
                    // 所以这里显示的是「这个源会被当成什么」，用户改一下即可覆盖。
                    FieldLabel(stringResource(R.string.add_content_type))
                    Spacer(Modifier.height(6.dp))
                    ContentTypeChips(
                        selected = state.effectiveContentType,
                        onSelect = { viewModel.onIntent(AddSubscriptionIntent.ContentTypeSelected(it)) },
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = stringResource(R.string.add_title),
                        enabled = state.canSubmit,
                        loading = state.isAdding,
                        onClick = { viewModel.onIntent(AddSubscriptionIntent.Submit) },
                    )
                }
            }
        }

        // 「猜你想订」（AiFeature.FEED_RECOMMEND 的展示落点）：排在路由目录**之前**，
        // 因为它要解决的正是"还没想好搜什么"——摆在目录下面，用户已经找到要订的东西了。
        // 没有建议时整块不渲染：加订阅页是"来干一件具体的事"的页面，空态提示只是挡路。
        if (state.aiSuggestions.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Lucide.Compass,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.add_suggest_title),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.add_suggest_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    state.aiSuggestions.forEach { suggestion ->
                        SuggestionRow(
                            suggestion = suggestion,
                            onClick = {
                                viewModel.onIntent(AddSubscriptionIntent.PickSuggestion(suggestion))
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.add_or_from_route),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.weight(1f))
            }
        }

        item {
            SearchField(
                value = state.query,
                onChange = { viewModel.onIntent(AddSubscriptionIntent.QueryChange(it)) },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
            )
        }

        // 分类吸顶：路由目录动辄上百条，滑到中段还能就地切分类，不必先滚回顶部。
        // 背景必须不透明，否则滚过的行会从下面透出来。
        stickyHeader(key = "category-chips") {
            Surface(color = MaterialTheme.colorScheme.surface) {
                CategoryChips(
                    categories = RouteCategory.ORDER,
                    selected = state.category,
                    onSelect = { viewModel.onIntent(AddSubscriptionIntent.CategoryChange(it)) },
                )
            }
        }

        item {
            CatalogStatusBar(
                routeCount = state.catalogRouteCount,
                generatedAtMillis = state.catalogGeneratedAt,
                source = state.catalogSource,
                refreshing = state.isCatalogRefreshing,
                onRefresh = { viewModel.onIntent(AddSubscriptionIntent.RefreshCatalog) },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
            )
        }

        if (state.isCatalogLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.add_catalog_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        } else if (state.visibleRoutes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.add_catalog_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            items(state.visibleRoutes, key = { it.id }) { route ->
                RouteRow(
                    route = route,
                    onClick = { viewModel.onIntent(AddSubscriptionIntent.RouteSelected(route)) },
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * 一条「猜你想订」。
 *
 * 行尾如实标出"点下去会发生什么"：三种落点的结果完全不同（进填参 / 填地址 / 去目录搜），
 * 一律写成「添加」就是在骗用户点——他按下之后可能只是被送进一个还得填参数的页面。
 */
@Composable
private fun SuggestionRow(suggestion: FeedSuggestionUi, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (suggestion.reason.isNotBlank()) {
                    Text(
                        text = suggestion.reason,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(suggestion.target.hintRes()),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 落点 → 行尾提示文案。三者必须区分开，否则用户不知道按下去会怎样。 */
private fun SuggestionTarget.hintRes(): Int = when (this) {
    is SuggestionTarget.Route -> R.string.add_suggest_via_route
    is SuggestionTarget.Url -> R.string.add_suggest_via_url
    SuggestionTarget.Search -> R.string.add_suggest_via_search
}

@Composable

private fun RouteRow(route: RssHubRoute, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedIcon(title = route.sourceName, size = 34.dp, cornerRadius = 9.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = route.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (route.heat >= RssHubRoute.FEATURED_HEAT) {
                    Spacer(Modifier.width(6.dp))
                    HotTag()
                }
            }
            Text(
                text = route.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Lucide.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
