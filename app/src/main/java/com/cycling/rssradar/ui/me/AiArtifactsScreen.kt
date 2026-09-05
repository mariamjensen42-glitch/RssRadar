package com.cycling.rssradar.ui.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Trash2
// AiArtifactDetail 定义在同包的 AiArtifactsViewModel 里，同包无需 import。
import com.cycling.rssradar.core.data.ai.AiArtifactGroup
import com.cycling.rssradar.core.data.ai.AiArtifactItem
import com.cycling.rssradar.core.data.ai.AiPayloadLine
import com.cycling.rssradar.core.data.ai.AiPayloadText
import com.cycling.rssradar.core.data.ai.AiScope
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.core.ui.components.EmptyState
import com.cycling.rssradar.core.ui.theme.radarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * AI 产物中心：把 `ai_artifacts` 里的全部产物按功能摊开，随手可查。
 *
 * 为什么要有这一页：35 项 AI 功能里只有一部分有专属展示位，其余的执行器照常跑、
 * 产物照常落库，但 App 里没有任何地方能看到它们，用户只觉得"跑成功了，结果呢？"。
 * 这一页不认识任何 payload 的具体类型（渲染交给 [AiPayloadText]），
 * 因此新增功能**零成本**自动纳入，不需要为每项功能再写一个页面。
 *
 * 三条刻意的呈现决定：
 * 1. **先按功能分组、再按时间倒序**——用户来这里是带着"XX 功能到底出结果没有"
 *    这个问题来的，按功能聚合能一眼扫到；同一功能内部按时间排，最新的在最前。
 * 2. **每条都显示主体标题**（文章标题 / 订阅源名），不是一串 subjectId。
 *    数字 id 对人没有意义，看不出这条结果挂在哪篇文章上。
 * 3. **原文可展开**。模型输出是唯一的原始证据，"AI 说它做了什么"和"模型实际说了什么"
 *    必须都能看到，否则排查时只能靠猜。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiArtifactsScreen(
    viewModel: AiArtifactsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    /** 文章级产物跳详情；不传则该按钮不出现（不让用户点一个没有落点的按钮）。 */
    onOpenArticle: (Long) -> Unit = {},
    /** 订阅源级产物跳该源文章列表。 */
    onOpenFeed: (Long) -> Unit = {},
    /**
     * 从总览页「查看结果」进来时预选的功能（dbValue）。
     * 用 LaunchedEffect 应用而不是在 VM 构造时读路由参数：VM 是 hiltViewModel
     * 默认创建的，不知道路由；而筛选一次即可，不该把路由耦合进 VM 生命周期。
     */
    initialFeatureDbValue: Int? = null,
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(initialFeatureDbValue) {
        if (initialFeatureDbValue != null) {
            viewModel.onIntent(AiArtifactsIntent.SelectKind(initialFeatureDbValue))
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.onIntent(AiArtifactsIntent.ConsumeMessage)
    }

    Scaffold(
        containerColor = radarColors().bgRoot,
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Lucide.ArrowLeft,
                        contentDescription = "返回",
                        tint = radarColors().textPrimary,
                    )
                }
                Text(
                    text = "AI 结果",
                    style = MaterialTheme.typography.titleMedium,
                    color = radarColors().textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.onIntent(AiArtifactsIntent.Refresh) }) {
                    Icon(
                        imageVector = Lucide.RotateCw,
                        contentDescription = "刷新",
                        tint = radarColors().textSecondary,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            OverviewRow(
                total = state.items.size,
                featureCount = state.groups.size,
                outputChars = state.groups.sumOf { it.outputChars },
            )
            Spacer(Modifier.height(12.dp))

            if (state.groups.isNotEmpty()) {
                FeatureFilterRow(
                    groups = state.groups,
                    selected = state.selectedKind,
                    onSelect = { viewModel.onIntent(AiArtifactsIntent.SelectKind(it)) },
                )
                Spacer(Modifier.height(12.dp))
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = radarColors().accent)
                }

                state.items.isEmpty() -> EmptyState(
                    icon = Lucide.Sparkles,
                    message = if (state.groups.isEmpty()) "还没有任何 AI 产物" else "这项功能还没有产物",
                    hint = if (state.groups.isEmpty()) {
                        "开启几项 AI 功能后，跑出来的结果会集中显示在这里"
                    } else {
                        "功能已开启但还没跑出结果？去「AI 智能功能」页的任务队列看看"
                    },
                    // 同理必须给权重：EmptyState 内部是 fillMaxSize，不给权重会顶掉剩余空间。
                    modifier = Modifier.weight(1f),
                )

                else -> ArtifactList(
                    items = state.items,
                    timeFormat = timeFormat,
                    onOpen = { viewModel.onIntent(AiArtifactsIntent.OpenDetail(it)) },
                    // weight 只能在这一层给：ArtifactList 是独立的 @Composable，
                    // 它函数体内拿不到 ColumnScope，在里面写 Modifier.weight 编译不过。
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    state.detail?.let { detail ->
        AiArtifactDetailSheet(
            detail = detail,
            timeFormat = timeFormat,
            onOpenArticle = onOpenArticle,
            onOpenFeed = onOpenFeed,
            onDelete = { viewModel.onIntent(AiArtifactsIntent.Delete(detail.item)) },
            onDismiss = { viewModel.onIntent(AiArtifactsIntent.DismissDetail) },
        )
    }
}

// ── 总览与筛选 ──────────────────────────────────────────────────────────────

@Composable
private fun OverviewRow(total: Int, featureCount: Int, outputChars: Long) {
    val colors = radarColors()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OverviewCell(label = "产物", value = total.toString(), modifier = Modifier.weight(1f))
        OverviewCell(label = "功能", value = featureCount.toString(), modifier = Modifier.weight(1f))
        OverviewCell(label = "模型输出", value = "${formatCount(outputChars)} 字", modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = "全部 AI 功能的生成结果都在这里，按功能筛选后查看。数字来自本地产物表。",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textTertiary,
    )
}

@Composable
private fun OverviewCell(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = radarColors()
    Surface(shape = RoundedCornerShape(12.dp), color = colors.surface1, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FeatureFilterRow(
    groups: List<AiArtifactGroup>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    val colors = radarColors()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            text = "全部",
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        groups.forEach { group ->
            FilterChip(
                text = "${group.feature.label} ${group.total}",
                selected = selected == group.feature.dbValue,
                onClick = { onSelect(group.feature.dbValue) },
            )
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = radarColors()
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) colors.accent else colors.surface2,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) colors.onAccent else colors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── 列表 ────────────────────────────────────────────────────────────────────

@Composable
private fun ArtifactList(
    items: List<AiArtifactItem>,
    timeFormat: SimpleDateFormat,
    onOpen: (AiArtifactItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = radarColors()
    // 按功能分组，组内按时间倒序（数据库已按时间倒序返回，分组不破坏该顺序）。
    val groups = remember(items) {
        items.groupBy { it.feature }.toList().sortedByDescending { (_, list) -> list.first().createdAt }
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        groups.forEach { (feature, list) ->
            stickyHeader(key = "h-${feature.dbValue}") {
                Surface(color = colors.bgRoot) {
                    Text(
                        text = "${feature.label} · ${list.size} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    )
                }
            }
            items(list, key = { "${it.feature.dbValue}:${it.subjectId}" }) { item ->
                ArtifactRow(item = item, timeFormat = timeFormat, onOpen = { onOpen(item) })
            }
        }
    }
}

@Composable
private fun ArtifactRow(
    item: AiArtifactItem,
    timeFormat: SimpleDateFormat,
    onOpen: () -> Unit,
) {
    val colors = radarColors()
    val preview = remember(item.payload) { previewOf(item) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.feature.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = timeFormat.format(Date(item.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = subjectLabel(item),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 条目上的主体名。查不到标题时不留空——给「文章 #id」比一片空白好定位。 */
private fun subjectLabel(item: AiArtifactItem): String = when (item.scope) {
    AiScope.ARTICLE -> item.subjectTitle ?: "文章 #${item.subjectId}"
    AiScope.FEED -> item.subjectTitle ?: "订阅源 #${item.subjectId}"
    AiScope.GLOBAL -> "全局 · ${item.subjectId}"
}

/** 列表预览：取渲染结果的第一行有意义文本，省得用户为了看一句结论点开每一条。 */
private fun previewOf(item: AiArtifactItem): String {
    val lines = AiPayloadText.lines(item.payload)
    val first = lines.firstOrNull { it.value.length >= 2 } ?: return ""
    return if (first.label != null) "${first.label}：${first.value}" else first.value
}

// ── 详情面板 ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiArtifactDetailSheet(
    detail: AiArtifactDetail,
    timeFormat: SimpleDateFormat,
    onOpenArticle: (Long) -> Unit,
    onOpenFeed: (Long) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = radarColors()
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    var showRaw by remember(detail) { mutableStateOf(false) }
    val item = detail.item

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.bgRoot,
    ) {
        Column(
            // 用确定高度（屏高 92%）而不是 wrap + max：面板高度不定时，
            // 下面内容区的 weight(1f) 拿不到确定约束，滚动区会被压成 0。
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = item.feature.label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subjectLabel(item),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = listOf(
                    item.model,
                    timeFormat.format(Date(item.createdAt)),
                    "输入 ${formatCount(item.inputChars.toLong())} 字",
                    "输出 ${formatCount(item.outputChars.toLong())} 字",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(12.dp))

            // 结构化内容：可能很长，必须可滚动，否则会被父容器截断。
            // weight(1f) 而非 fill=false——fill=false 时高度由内容决定，
            // 内容多高就要多高，verticalScroll 反而失去意义。
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (detail.lines.isEmpty()) {
                    Text(
                        text = "这条产物没有可解析的内容",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                } else {
                    detail.lines.forEach { line ->
                        PayloadLineRow(line = line)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = { showRaw = !showRaw }) {
                Text(
                    text = if (showRaw) "收起模型原文" else "查看模型原文",
                    color = colors.accent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (showRaw) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surface2,
                    modifier = Modifier.heightIn(max = 220.dp),
                ) {
                    Box(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Text(
                            text = detail.raw,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
                TextButton(onClick = { clipboardScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("artifact", detail.raw))) } }) {
                    Icon(
                        imageVector = Lucide.Copy,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("复制原文", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 跳转按钮只在有落点时出现：点一个没接线的按钮，用户只会以为又坏了。
                if (item.scope == AiScope.ARTICLE) {
                    TextButton(onClick = { onOpenArticle(item.subjectId) }) {
                        Text("打开文章", color = colors.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (item.scope == AiScope.FEED) {
                    TextButton(onClick = { onOpenFeed(item.subjectId) }) {
                        Text("打开订阅源", color = colors.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = Lucide.Trash2,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("删除", color = colors.textTertiary)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PayloadLineRow(line: AiPayloadLine) {
    val colors = radarColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (line.depth * 12).dp, top = 3.dp, bottom = 3.dp),
    ) {
        // label 来自另一模块的 data class，smart cast 不可用，必须先落到本地变量
        val label = line.label
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.width(84.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = line.value,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatCount(value: Long): String = when {
    value >= 100_000_000 -> String.format("%.1f亿", value / 100_000_000.0)
    value >= 10_000 -> String.format("%.1f万", value / 10_000.0)
    else -> value.toString()
}
