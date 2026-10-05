package com.cycling.rssradar.ui.addsubscription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.core.ui.text.resolve
import com.cycling.rssradar.core.ui.theme.radarColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** RSSHub 品牌橙，只用在「这是 RSSHub 能力」的标识上，与紫色主色区分开。 */
internal val RssHubOrange = Color(0xFFFF6B00)

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
internal fun AddSheetShell(
    viewModel: AddSubscriptionViewModel,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val message = viewModel.uiState.collectAsState().value.uiMessage

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.resolve(context))
            viewModel.onIntent(AddSubscriptionIntent.ConsumeMessage)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = radarColors().surface1,
        dragHandle = { BottomSheetDefaults.DragHandle(color = radarColors().surface3) },
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
    val state by viewModel.uiState.collectAsState()

    // 每次打开都确认一次实例可达：默认实例在部分网络不通，早发现早换，别等预览失败才知道
    LaunchedEffect(Unit) { viewModel.onShown() }

    // 填参步骤按系统返回键 = 返回目录，而不是直接关掉抽屉（与原导航 popBackStack 行为一致）
    BackHandler(enabled = state.selectedRoute != null) {
        viewModel.onIntent(AddSubscriptionIntent.BackToCatalog)
    }

    AddSheetShell(viewModel = viewModel, onDismiss = onDismiss) {
        val route = state.selectedRoute
        if (route == null) {
            SheetHeader(
                title = stringResource(R.string.add_title),
                subtitle = stringResource(R.string.add_subtitle),
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
            containerColor = radarColors().accent,
            contentColor = radarColors().onAccent,
            disabledContainerColor = radarColors().accent.copy(alpha = 0.4f),
            disabledContentColor = radarColors().onAccent,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = radarColors().onAccent,
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
