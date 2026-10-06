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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Rss
import com.cycling.rssradar.core.model.rsshub.RouteCategory
import com.cycling.rssradar.core.model.rsshub.RssHubRoute
import com.cycling.rssradar.core.ui.R as UiR
import com.cycling.rssradar.core.ui.components.FeedIcon
import com.cycling.rssradar.core.ui.theme.radarColors

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
                tint = radarColors().textPrimary,
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
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = radarColors().textTertiary,
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
                            color = radarColors().accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.add_discovering),
                            color = radarColors().textTertiary,
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
                    color = radarColors().textSecondary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                HorizontalDivider(color = radarColors().divider, modifier = Modifier.weight(1f))
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
            Surface(color = radarColors().bgRoot) {
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
                        CircularProgressIndicator(color = radarColors().accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.add_catalog_loading),
                            color = radarColors().textTertiary,
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
                        color = radarColors().textTertiary,
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
                    color = radarColors().textPrimary,
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
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Lucide.ChevronRight,
            contentDescription = null,
            tint = radarColors().textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}
