package com.cycling.rssradar.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.model.FeedValueMode
import com.cycling.rssradar.core.model.ListViewMode
import com.cycling.rssradar.core.model.MarkAsReadCondition
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.core.ui.components.OptionPickerSheet
import com.cycling.rssradar.core.ui.labels.labelRes
import com.cycling.rssradar.core.ui.text.resolve
import com.cycling.rssradar.core.ui.theme.LocalListDisplay

@Composable

fun FeedListDestination(
    onOpenSearch: () -> Unit = {},
    onOpenArticle: (ArticleWithFeed) -> Unit = {},
    /** 空态「添加订阅源」直达入口（新用户第一分钟不该被卡在找入口上）。 */
    onAddFeed: () -> Unit = {},
    /** 新用户空态「导入 OPML」入口：跳订阅页（SAF 入口在订阅页顶栏菜单）。 */
    onOpenSubscriptions: () -> Unit = {},
    viewModel: FeedListViewModel = hiltViewModel(),
) {
    // MVI 候选 C：单一 UiState 快照驱动渲染
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FeedListScreen(
        uiState = uiState,
        onOpenSearch = onOpenSearch,
        onOpenArticle = onOpenArticle,
        onAddFeed = onAddFeed,
        onOpenSubscriptions = onOpenSubscriptions,
        onIntent = viewModel::onIntent,
    )
}

@Composable
fun FeedListScreen(
    uiState: FeedListUiState,
    onOpenSearch: () -> Unit = {},
    onOpenArticle: (ArticleWithFeed) -> Unit = {},
    /** 空态「添加订阅源」直达入口（新用户第一分钟不该被卡在找入口上）。 */
    onAddFeed: () -> Unit = {},
    /** 新用户空态「导入 OPML」入口：跳订阅页（SAF 入口在订阅页顶栏菜单）。 */
    onOpenSubscriptions: () -> Unit = {},
    onIntent: (FeedListIntent) -> Unit = {},
) {
    val groupOptions = uiState.groupOptions
    val unreadCount = uiState.unreadCount
    val recommendationEnabled = uiState.recommendationEnabled
    val snackbarHostState = remember { SnackbarHostState() }
    var showGroupSheet by remember { mutableStateOf(false) }
    /** 批量标记已读条件弹层（#10）。 */
    var showMarkReadSheet by remember { mutableStateOf(false) }
    /** 列表视图模式选择弹层（列表/卡片/杂志/网格）。 */
    var showViewModeSheet by remember { mutableStateOf(false) }
    /** 视图模式是全局显示偏好（ListDisplayStore → CompositionLocal），这里读，VM 写。 */
    val viewMode = LocalListDisplay.current.viewMode
    /** AI 价值档位：同样是全局显示偏好（ListDisplayStore → CompositionLocal）。 */
    val valueMode = LocalListDisplay.current.valueMode
    val context = LocalContext.current
    val message = uiState.uiMessage

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.resolve(context))
            onIntent(FeedListIntent.ConsumeMessage)
        }
    }

    // 「减少此类」撤销：Snackbar 期内可撤销，超时自动丢弃（降权保留）
    // 文案在组合作用域预取：LaunchedEffect 不是组合作用域，在里面用 context.getString
    // 会被 lint 判为 configuration-unaware（切语言/配置变化时可能拿到旧文案）。
    val undoLabel = stringResource(R.string.undo)
    val reduceSuchMessage = stringResource(R.string.feed_reduce_such)
    val pendingUndoReduce = uiState.pendingUndoReduceFeedId
    LaunchedEffect(pendingUndoReduce) {
        pendingUndoReduce?.let {
            val result = snackbarHostState.showSnackbar(
                message = reduceSuchMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> onIntent(FeedListIntent.UndoReduceSuch)
                SnackbarResult.Dismissed -> onIntent(FeedListIntent.DiscardUndoReduce)
            }
        }
    }

    // 删除撤销（issue #46）：Snackbar 期内可撤销，超时自动丢弃
    val pendingUndo = uiState.pendingUndoDelete
    val deletedMessage = stringResource(R.string.feed_deleted_article, pendingUndo?.title.orEmpty())
    LaunchedEffect(pendingUndo) {
        pendingUndo?.let {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> onIntent(FeedListIntent.UndoDeleteArticle)
                SnackbarResult.Dismissed -> onIntent(FeedListIntent.DiscardUndo)
            }
        }
    }

    // 分组筛选已下沉 DB 查询（issue #74）：ViewModel 返回的页本身就是按分组过滤后的
    // 分页结果，不再对已加载页做内存过滤（旧做法会让首屏大量留白、hasMore 语义错乱）
    val currentList = uiState.articles

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            FeedListTopBar(
                onOpenSearch = onOpenSearch,
                onOpenFilter = { showGroupSheet = true },
                onMarkAllRead = { showMarkReadSheet = true },
                onOpenViewMode = { showViewModeSheet = true },
                viewMode = viewMode,
                valueMode = valueMode,
                onSelectValueMode = { onIntent(FeedListIntent.SetValueMode(it)) },
                // 只判分组：内容类型已常驻首页一行且选中态自明，不必再借 ⋮ 菜单里的一行文案提示
                filterActive = uiState.selectedGroup != null,
                selectedTab = uiState.selectedTab,
                unreadCount = unreadCount,
                // 推荐流开关：关掉就不渲染「推荐」
                tabs = if (recommendationEnabled) FeedTab.entries else FeedTab.entries.filter { it != FeedTab.Recommended },
                onSelectTab = { onIntent(FeedListIntent.SelectTab(it)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ContentTypeFilterRow(
                selected = uiState.selectedContentType,
                onSelect = { onIntent(FeedListIntent.SelectContentType(it)) },
            )
            // 刷新进度（真机反馈缺口）：708 源全量刷新可达数十分钟，
            // 一个孤零零的转圈分不清「在跑」还是「卡死」——细进度条 + 计数，不抢一整行
            if (uiState.isRefreshing && uiState.refreshTotal > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { uiState.refreshDone.toFloat() / uiState.refreshTotal },
                        trackColor = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${uiState.refreshDone}/${uiState.refreshTotal}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { onIntent(FeedListIntent.Refresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiState.isRanking) {
                    RecommendationLoading(modifier = Modifier.fillMaxSize())
                } else if (currentList.isEmpty() && uiState.isFirstLoad) {
                    // 首屏查询在途：什么都不渲染。查询只有几十~几百 ms，spinner 刚
                    // 出现就被列表替换，闪烁比空白更难看——直接留白，内容一次到位。
                    Spacer(Modifier.fillMaxSize())
                } else if (currentList.isEmpty()) {
                    EmptyState(
                        selectedTab = uiState.selectedTab,
                        selectedContentType = uiState.selectedContentType,
                        partitionEmpty = uiState.partitionEmpty,
                        onAddFeed = onAddFeed,
                        onOpenSubscriptions = onOpenSubscriptions,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    /**
                     * 换筛选 = 整份列表换人，所以按筛选身份建 key 让 LazyColumn 整体重建，
                     * 而不是按 item key 逐项增删：后者会把被移除的卡片留在原位淡出
                     * （animateItem 的 fadeOut 本是给单篇删除用的），与瞬时上移的卡片
                     * 叠出一层重影——切 tab 时那一闪就是这么来的。重建顺带把滚动位置
                     * 归零，免得停在上一个筛选的深度上（新列表更短时会被夹到末尾，像跳了一下）。
                     */
                    key(uiState.selectedTab, uiState.selectedGroup, uiState.selectedContentType) {
                        ArticleCardList(
                            articles = currentList,
                            onArticleClick = { item ->
                                onIntent(FeedListIntent.MarkRead(item.article.id))
                                onOpenArticle(item)
                            },
                            onToggleRead = { id, read ->
                                onIntent(FeedListIntent.SetRead(id, read))
                            },
                            onToggleStarred = { id ->
                                onIntent(FeedListIntent.ToggleStarred(id))
                            },
                            onToggleBookmarked = { id ->
                                onIntent(FeedListIntent.ToggleBookmarked(id))
                            },
                            onDelete = { id ->
                                onIntent(FeedListIntent.DeleteArticle(id))
                            },
                            // 推荐 tab 才有「减少此类」：只有这里的排序由画像决定
                            onReduceSuch = if (uiState.selectedTab == FeedTab.Recommended) {
                                { id -> onIntent(FeedListIntent.ReduceSuch(id)) }
                            } else {
                                null
                            },
                            // 各 tab 均分页；滚动到底自动加载下一页
                            onScrolledToEnd = { onIntent(FeedListIntent.LoadMore) },
                            markReadPassed = { ids ->
                                onIntent(FeedListIntent.MarkReadPassed(ids))
                            },
                            totalCount = uiState.totalCount,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.isLoadingMore) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showGroupSheet) {
        GroupFilterSheet(
            groups = groupOptions,
            selected = uiState.selectedGroup,
            onSelect = { group ->
                onIntent(FeedListIntent.SelectGroup(group))
                showGroupSheet = false
            },
            onDismiss = { showGroupSheet = false },
        )
    }

    // 列表视图模式（列表/卡片/杂志/网格）：全局偏好，切换后所有文章列表即改即见
    if (showViewModeSheet) {
        val viewModeLabels = ListViewMode.entries.associateWith { stringResource(it.labelRes()) }
        val viewModeSubtitles = ListViewMode.entries.associateWith {
            when (it) {
                ListViewMode.LIST -> stringResource(R.string.vm_list_desc)
                ListViewMode.CARD -> stringResource(R.string.vm_card_desc)
                ListViewMode.MAGAZINE -> stringResource(R.string.vm_magazine_desc)
                ListViewMode.GRID -> stringResource(R.string.vm_grid_desc)
            }
        }
        OptionPickerSheet(
            title = stringResource(R.string.view_mode_title),
            options = ListViewMode.entries.toList(),
            selected = viewMode,
            label = { viewModeLabels.getValue(it) },
            subtitle = { viewModeSubtitles.getValue(it) },
            onSelect = { mode -> onIntent(FeedListIntent.SetViewMode(mode)) },
            onDismiss = { showViewModeSheet = false },
        )
    }

    // 批量标记已读（#10）：选条件后一次性写库，数字由 DAO 的真实影响行数汇报
    if (showMarkReadSheet) {
        val markReadLabels = MarkAsReadCondition.entries.associateWith { stringResource(it.labelRes()) }
        val markReadSubtitles = MarkAsReadCondition.entries.associateWith {
            if (it == MarkAsReadCondition.ALL) {
                stringResource(R.string.mark_read_all_desc)
            } else {
                stringResource(R.string.mark_read_before_desc, markReadLabels.getValue(it))
            }
        }
        OptionPickerSheet(
            title = stringResource(R.string.mark_read_title),
            options = MarkAsReadCondition.entries.toList(),
            selected = null,
            label = { markReadLabels.getValue(it) },
            subtitle = { markReadSubtitles.getValue(it) },
            onSelect = { condition -> onIntent(FeedListIntent.MarkAllRead(condition)) },
            onDismiss = { showMarkReadSheet = false },
        )
    }
}


@Preview(showBackground = true, name = "信息流 · 首屏未加载")
@Composable
private fun FeedListScreenFirstLoadPreview() {
    RssRadarTheme(darkTheme = false) {
        FeedListScreen(uiState = FeedListUiState())
    }
}

@Preview(showBackground = true, name = "信息流 · 深色空态")
@Composable
private fun FeedListScreenDarkPreview() {
    RssRadarTheme(darkTheme = true) {
        FeedListScreen(uiState = FeedListUiState(isFirstLoad = false))
    }
}
