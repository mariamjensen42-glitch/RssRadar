package com.cycling.rssradar.ui.addsubscription

import com.cycling.rssradar.data.DiscoveredFeed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.data.rsshub.CatalogSource
import com.cycling.rssradar.data.rsshub.RouteCategory
import com.cycling.rssradar.data.rsshub.RouteExample
import com.cycling.rssradar.data.rsshub.RouteParam
import com.cycling.rssradar.data.rsshub.RssHubRoute
import com.cycling.rssradar.ui.components.AppSnackbarHost
import com.cycling.rssradar.ui.components.FeedIcon
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.Divider
import com.cycling.rssradar.ui.theme.Link
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Success
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.Surface2
import com.cycling.rssradar.ui.theme.Surface3
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleCheckBig
import com.composables.icons.lucide.Link2
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Rss
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap

/** RSSHub 品牌橙，只用在「这是 RSSHub 能力」的标识上，与紫色主色区分开。 */
private val RssHubOrange = Color(0xFFFF6B00)

/**
 * 添加订阅的入口是一个底部抽屉，而不是整页。
 *
 * 依据：日常动作是读信息流，「加源」是低频动作，不该占掉主屏。
 * 抽屉内部两阶段：Catalog（搜索 / 分类 / 路由列表）→ Params（填参数 → 预览 → 订阅）。
 * 两阶段共用**同一个** ModalBottomSheet（[AddSheetShell] 只创建一次），步骤切换由
 * ViewModel 状态驱动：selectedRoute == null 显示目录，非空显示填参页。
 * 不再拆成两个 nav 目的地——那样两层 sheet 一关一开、中间闪过全屏背景，跳转极其割裂。
 * 手填普通 RSS 链接在 Catalog 顶部，与路由构建共用同一条校验 / 订阅链路。
 */

/** 共享外壳：ModalBottomSheet + Snackbar 消费。两步目的地共用，保证观感与消息行为一致。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheetShell(
    viewModel: AddSubscriptionViewModel,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val message = viewModel.uiMessage

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onIntent(AddSubscriptionIntent.ConsumeMessage)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface1,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Surface3) },
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        Box(modifier = Modifier.fillMaxHeight(0.92f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                content()
            }
            AppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        }
    }
}

/** 加订阅抽屉：一个 ModalBottomSheet 承载两步内容，步骤切换由 VM 状态驱动。 */
@Composable
fun AddSubscriptionSheet(
    viewModel: AddSubscriptionViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    // 填参步骤按系统返回键 = 返回目录，而不是直接关掉抽屉（与原导航 popBackStack 行为一致）
    BackHandler(enabled = state.selectedRoute != null) {
        viewModel.onIntent(AddSubscriptionIntent.BackToCatalog)
    }

    AddSheetShell(viewModel = viewModel, onDismiss = onDismiss) {
        val route = state.selectedRoute
        if (route == null) {
            SheetHeader(
                title = "添加订阅",
                subtitle = "粘贴链接，或从 RSSHub 路由构建",
                onClose = onDismiss,
            )
            CatalogContent(state = state, viewModel = viewModel)
        } else {
            ParamsHeader(route = route, onBack = {
                viewModel.onIntent(AddSubscriptionIntent.BackToCatalog)
            })
            ParamsContent(
                state = state,
                route = route,
                viewModel = viewModel,
            )
        }
    }
}

/* ------------------------------- 头部 ------------------------------- */

@Composable
private fun SheetHeader(title: String, subtitle: String?, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(Lucide.X, contentDescription = "关闭", tint = TextSecondary)
        }
    }
}

@Composable
private fun ParamsHeader(route: RssHubRoute, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Lucide.ArrowLeft, contentDescription = "返回目录", tint = TextPrimary)
        }
        FeedIcon(title = route.sourceName, size = 32.dp, cornerRadius = 9.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.name,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = route.sourceName,
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* --------------------------- 阶段一：路由目录 --------------------------- */

@Composable
private fun ColumnScope.CatalogContent(
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
                FieldLabel("订阅源链接")
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
                            color = Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "正在探测订阅源…",
                            color = TextTertiary,
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
                    GroupChips(
                        options = viewModel.groupOptions,
                        selected = state.selectedGroup,
                        onSelect = { viewModel.onIntent(AddSubscriptionIntent.GroupSelected(it)) },
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "添加订阅",
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
                    text = "或从 RSSHub 路由构建",
                    color = TextSecondary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                HorizontalDivider(color = Divider, modifier = Modifier.weight(1f))
            }
        }

        item {
            SearchField(
                value = state.query,
                onChange = { viewModel.onIntent(AddSubscriptionIntent.QueryChange(it)) },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
            )
        }

        item {
            CategoryChips(
                categories = RouteCategory.ORDER,
                selected = state.category,
                onSelect = { viewModel.onIntent(AddSubscriptionIntent.CategoryChange(it)) },
            )
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
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "正在装载路由目录…",
                            color = TextTertiary,
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
                        text = "没有匹配的路由",
                        color = TextTertiary,
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

/** 目录状态：条数 + 数据时间 + 更新入口。让用户知道目录是活的可更新，而不是死的 14 条。 */
@Composable
private fun CatalogStatusBar(
    routeCount: Int,
    generatedAtMillis: Long?,
    source: CatalogSource,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = catalogStatusText(routeCount, generatedAtMillis, source),
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = Surface2,
            modifier = Modifier.clickable(enabled = !refreshing, onClick = onRefresh),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                } else {
                    Icon(
                        imageVector = Lucide.RefreshCw,
                        contentDescription = "更新路由目录",
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (refreshing) "更新中" else "更新目录",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun catalogStatusText(routeCount: Int, generatedAtMillis: Long?, source: CatalogSource): String {
    if (routeCount == 0) return ""
    val count = "$routeCount 条路由"
    val origin = if (source == CatalogSource.UPDATED) "已更新" else "内置"
    val date = generatedAtMillis?.let { formatCatalogDate(it) }
    return listOfNotNull(count, origin, date).joinToString(" · ")
}

/** 目录时间只到日期：路由表不需要精确到时分，短一点更省地方。 */
private fun formatCatalogDate(millis: Long): String {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(millis))
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
                    color = TextPrimary,
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
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Lucide.ChevronRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HotTag() {
    Box(
        modifier = Modifier
            .background(RssHubOrange.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = "热门",
            color = RssHubOrange,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CategoryChips(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it }) { key ->
            FilterChipLight(
                label = RouteCategory.label(key),
                selected = key == selected,
                onClick = { onSelect(key) },
            )
        }
    }
}

/* --------------------------- 阶段二：填参数 --------------------------- */

@Composable
private fun ColumnScope.ParamsContent(
    state: AddSubscriptionUiState,
    route: RssHubRoute,
    viewModel: AddSubscriptionViewModel,
) {
    val listState = rememberLazyListState()
    // 生成地址后把结果区滚进视野：参数一多，结果就在屏幕外，用户会以为点了没反应。
    // 等一帧再滚——重组刚把结果 item 加进列表，这一帧的 layoutInfo 还没它。
    LaunchedEffect(state.url) {
        if (state.url.isNotBlank()) {
            withFrameMillis { }
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                CodeBlock(text = route.path)
                if (route.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = route.description,
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (route.params.isEmpty()) {
            item {
                Text(
                    text = "此路由无需参数，直接生成即可。",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            items(route.params, key = { it.key }) { param ->
                ParamField(
                    param = param,
                    value = state.paramValues[param.key].orEmpty(),
                    onChange = { viewModel.onIntent(AddSubscriptionIntent.ParamChange(param.key, it)) },
                )
            }
        }

        if (route.examples.isNotEmpty()) {
            item {
                ExamplePicker(
                    examples = route.examples,
                    onSelect = { viewModel.onIntent(AddSubscriptionIntent.ExampleSelected(it)) },
                )
            }
        }

        item {
            Column {
                // 结果由哪个实例解析，写在按钮上方：实例不可达时这是最先要核对的信息
                Text(
                    text = "由 ${state.host} 解析",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    // 缺参数时按钮照样可点——点了就在下面说缺哪个。置灰不吭声 = 用户只会以为坏了。
                    onClick = { viewModel.onIntent(AddSubscriptionIntent.PreviewRoute) },
                    enabled = !state.isValidating && !state.isAdding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Surface2,
                        contentColor = TextPrimary,
                        disabledContainerColor = Surface2.copy(alpha = 0.5f),
                        disabledContentColor = TextTertiary,
                    ),
                ) {
                    Icon(
                        imageVector = Lucide.Zap,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "生成并预览",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (state.missingParams.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "还需填写：" + state.missingParams.joinToString("、") {
                            it.label.ifBlank { it.key }
                        },
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // 结果区：校验中 / 成功 / 失败都在这里出，且 loading 与结果不互斥——
        // 之前两者写成 if/else，生成地址后校验的那十几秒里界面上什么都没有。
        if (state.isUrlFromRoute || state.isValidating || state.validation !is ValidationInfo.Idle) {
            item {
                PreviewResult(state = state, viewModel = viewModel)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PreviewResult(
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
                    color = Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "正在校验…",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        ValidationBanner(info = state.validation)
        if (state.validation is ValidationInfo.Valid) {
            Spacer(Modifier.height(6.dp))
            GroupChips(
                options = viewModel.groupOptions,
                selected = state.selectedGroup,
                onSelect = { viewModel.onIntent(AddSubscriptionIntent.GroupSelected(it)) },
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = "订阅",
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
private fun ExamplePicker(
    examples: List<RouteExample>,
    onSelect: (RouteExample) -> Unit,
) {
    Column {
        Text(
            text = "示例",
            color = TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(examples.size, key = { examples[it].path }) { index ->
                val example = examples[index]
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Surface2,
                    modifier = Modifier.clickable { onSelect(example) },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = example.title.ifBlank { example.path.substringAfterLast('/') },
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (example.title.isNotBlank()) {
                            Text(
                                text = example.path,
                                color = TextTertiary,
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
private fun ParamField(
    param: RouteParam,
    value: String,
    onChange: (String) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${param.label} · :${param.key}",
                color = TextSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (param.optional) {
                Spacer(Modifier.width(6.dp))
                OptionalTag()
            }
        }

        // 有枚举值就用 chips 选：比让用户照着说明手打可靠
        if (param.options.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(param.options, key = { it.value }) { option ->
                    FilterChipLight(
                        label = option.label,
                        selected = value == option.value,
                        onClick = { onChange(option.value) },
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = param.fallback ?: "必填",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Accent,
            ),
        )
        // 说明与标签重复时不再啰嗦一遍
        if (param.description.isNotBlank() && param.description != param.label) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = param.description,
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OptionalTag() {
    Box(
        modifier = Modifier
            .background(Surface3, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = "可选",
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CodeBlock(text: String, accent: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (accent) Surface2 else Color(0xFF111114),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            color = if (accent) Link else TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/* ------------------------------ 通用件 ------------------------------ */

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun UrlField(
    value: String,
    onChange: (String) -> Unit,
    isLoading: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "https://rsshub.app/zhihu/daily",
                color = TextTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        singleLine = true,
        leadingIcon = {
            Icon(Lucide.Link2, contentDescription = null, tint = TextTertiary)
        },
        trailingIcon = {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Surface2,
            unfocusedContainerColor = Surface2,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Accent,
        ),
    )
}

@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text("搜索 3800 条路由，如 b站 / github / 日报", color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
        },
        singleLine = true,
        leadingIcon = {
            Icon(Lucide.Search, contentDescription = null, tint = TextTertiary)
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Surface2,
            unfocusedContainerColor = Surface2,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Accent,
        ),
    )
}

@Composable
private fun FilterChipLight(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) Accent else Surface2,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (selected) OnAccent else TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun GroupChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { name ->
            FilterChipLight(label = name, selected = name == selected, onClick = { onSelect(name) })
        }
    }
}

@Composable
private fun ValidationBanner(info: ValidationInfo) {
    if (info is ValidationInfo.Idle) return
    val color = when (info) {
        is ValidationInfo.Valid -> Success
        // 发现到候选不是错误，是进展：用强调色而非报错红
        is ValidationInfo.Discovered -> Accent
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
            text = info.message,
            color = color,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** 自动发现（#5）的一条候选：标题 + 地址 + 真实文章数，点一下即采用。 */
@Composable
private fun DiscoveredFeedRow(
    feed: DiscoveredFeed,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Surface1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.Rss,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feed.title.ifBlank { feed.url },
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = feed.url,
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${feed.articleCount} 篇",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = OnAccent,
            disabledContainerColor = Accent.copy(alpha = 0.4f),
            disabledContentColor = OnAccent,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = OnAccent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
