package com.cycling.rssradar.ui

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.data.RouteParam
import com.cycling.rssradar.data.RssHubRoute
import com.cycling.rssradar.data.RssHubRoutes
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
 * 两阶段由嵌套 nav graph 表达为两个目的地（issue #33）——
 * [AddSubscriptionCatalogScreen] 与 [AddSubscriptionParamsScreen]，ViewModel 为 navGraph
 * 作用域共享（MainActivity 装配处 hiltViewModel(parentEntry)）。
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
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        }
    }
}

/** 第一步：路由目录（搜索 / 分类 / 路由列表 + 手填链接）。选路由后跳转 Params 目的地。 */
@Composable
fun AddSubscriptionCatalogScreen(
    viewModel: AddSubscriptionViewModel,
    onDismiss: () -> Unit,
    onOpenParams: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    AddSheetShell(viewModel = viewModel, onDismiss = onDismiss) {
        SheetHeader(
            title = "添加订阅",
            subtitle = "粘贴链接，或从 RSSHub 路由构建",
            onClose = onDismiss,
        )
        CatalogContent(
            state = state,
            viewModel = viewModel,
            onOpenParams = onOpenParams,
        )
    }
}

/** 第二步：填参数 → 预览 → 订阅。返回目录由导航（popBackStack）承担。 */
@Composable
fun AddSubscriptionParamsScreen(
    viewModel: AddSubscriptionViewModel,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    AddSheetShell(viewModel = viewModel, onDismiss = onDismiss) {
        val route = state.selectedRoute
        if (route == null) {
            // 理论上不可达：进入本目的地前必先 RouteSelected；防御性回退
            SheetHeader(title = "添加订阅", subtitle = null, onClose = onDismiss)
        } else {
            ParamsHeader(route = route, onBack = onBack)
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
        FeedIcon(title = route.name, size = 32.dp, cornerRadius = 9.dp)
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
                text = route.pathTemplate,
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
    onOpenParams: () -> Unit,
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
                        categories = RssHubRoutes.categories,
                selected = state.category,
                onSelect = { viewModel.onIntent(AddSubscriptionIntent.CategoryChange(it)) },
            )
        }

        items(state.visibleRoutes, key = { it.id }) { route ->
            RouteRow(
                route = route,
                onClick = {
                    viewModel.onIntent(AddSubscriptionIntent.RouteSelected(route))
                    onOpenParams()
                },
            )
        }

        if (state.visibleRoutes.isEmpty()) {
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
        FeedIcon(title = route.name, size = 34.dp, cornerRadius = 9.dp)
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
                if (route.featured) {
                    Spacer(Modifier.width(6.dp))
                    HotTag()
                }
            }
            Text(
                text = route.pathTemplate,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { name ->
            FilterChipLight(
                label = name,
                selected = name == selected,
                onClick = { onSelect(name) },
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
    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            CodeBlock(text = route.pathTemplate)
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

        item {
            Button(
                onClick = { viewModel.onIntent(AddSubscriptionIntent.PreviewRoute) },
                enabled = state.canPreview,
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
        }

        if (state.isUrlFromRoute) {
            item {
                CodeBlock(text = state.url, accent = true)
                Spacer(Modifier.height(8.dp))
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
        } else if (state.isValidating) {
            item {
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
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ParamField(
    param: RouteParam,
    value: String,
    onChange: (String) -> Unit,
) {
    Column {
        Text(
            text = "${param.label} · :${param.key}",
            color = TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(param.placeholder, color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
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
            Text("搜索路由，如 b站 / github / 日报", color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
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
