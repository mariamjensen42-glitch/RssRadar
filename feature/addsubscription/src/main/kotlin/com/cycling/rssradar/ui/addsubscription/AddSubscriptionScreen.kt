package com.cycling.rssradar.ui.addsubscription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.core.ui.text.resolve

/** RSSHub 品牌橙，只用在「这是 RSSHub 能力」的标识上，与紫色主色区分开。 */
internal val RssHubOrange = Color(0xFFFF6B00)

/**
 * 添加订阅页（Destination）：收 VM、接一次性预填，两步内容由 VM 状态驱动。
 *
 * 两步同页：Catalog（搜索 / 分类 / 路由目录，顶部兼手填普通 RSS 链接）→ Params（填参数 → 预览 → 订阅）。
 * 不拆成两个目的地——那样返回时中间会闪过一整页背景，跳转割裂。
 *
 * VM 绑定本路由的 backStackEntry：退出页面即销毁，下次进入天然是干净状态，
 * 因此不再需要原先那套「Activity 级 VM + 关闭时手动 reset」。
 *
 * @param prefillUrl 系统分享/选中文字带进来的一次性预填地址，由 Activity 侧状态传入。
 * @param onPrefillConsumed 预填已消费，调用方据此清掉待处理状态。
 */
@Composable
fun AddSubscriptionDestination(
    onDone: () -> Unit,
    prefillUrl: String? = null,
    onPrefillConsumed: () -> Unit = {},
    viewModel: AddSubscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 每次进入都确认一次实例可达：默认实例在部分网络不通，早发现早换，别等预览失败才知道
    LaunchedEffect(Unit) { viewModel.onShown() }

    // 预填只做一次：Activity 重建会让 LaunchedEffect 重跑，没有闸门就会覆盖用户已经改过的输入
    var prefillApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(prefillUrl) {
        val url = prefillUrl ?: return@LaunchedEffect
        if (prefillApplied) return@LaunchedEffect
        viewModel.onIntent(AddSubscriptionIntent.UrlChange(url))
        prefillApplied = true
        onPrefillConsumed()
    }

    // 填参步骤按系统返回键 = 回目录，而不是直接退出页面
    BackHandler(enabled = state.selectedRoute != null) {
        viewModel.onIntent(AddSubscriptionIntent.BackToCatalog)
    }

    AddSubscriptionScaffold(viewModel = viewModel) {
        val route = state.selectedRoute
        if (route == null) {
            CatalogHeader(
                title = stringResource(R.string.add_title),
                subtitle = stringResource(R.string.add_subtitle),
                onClose = onDone,
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

/**
 * 全屏外壳：两步内容共用，负责消息消费与底部 inset。
 *
 * 此前这里是最高 92% 的 ModalBottomSheet。加订阅虽低频却要填表单，
 * 抽屉被键盘顶起后路由目录只剩一条窄缝，故改回整页。
 */
@Composable
internal fun AddSubscriptionScaffold(
    viewModel: AddSubscriptionViewModel,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val message = viewModel.uiState.collectAsStateWithLifecycle().value.uiMessage

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.resolve(context))
            viewModel.onIntent(AddSubscriptionIntent.ConsumeMessage)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // 系统栏 inset 收在这里统一处理，而不是各步自己加：两步（目录 / 填参）各自的头部
        // 都要贴住状态栏，分散处理必漏一处——真机上「填参页顶到状态栏」就是这么来的。
        // 原先是 ModalBottomSheet 自带的 inset 在兜底，改成整页后这份兜底没了。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
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

/* ------------------------------- 头部 ------------------------------- */

@Composable
internal fun PrimaryButton(
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
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
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
