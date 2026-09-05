package com.cycling.rssradar.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.cycling.rssradar.core.ui.theme.radarColors

/**
 * 兴趣画像页（ADR-0013）：推荐流"为什么推这些"的答案，只读。
 *
 * 画像只由真实行为驱动（打开 / 收藏 / 稍后读），没有任何预置兴趣类别；
 * 数字一律来自真实统计，收藏了几篇、打开过几次都是库里查出来的，不做估算。
 */
@Composable
fun InterestProfileScreen(
    viewModel: InterestProfileViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(radarColors().bgRoot)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = "返回", tint = radarColors().textPrimary)
            }
            Text(
                text = "兴趣画像",
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = "推荐流按你的真实阅读行为排序：打开过的文章、收藏、稍后读都会计入画像，" +
                    "越近的行为权重越高。画像只存在本机，不上传。",
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = radarColors().accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                }
                state.isColdStart -> {
                    Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Lucide.Sparkles,
                                    contentDescription = null,
                                    tint = radarColors().accent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("还没有学到偏好", color = radarColors().textPrimary, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "多读几篇文章后这里会出现兴趣词；在此之前，推荐 tab 按订阅源轮转展示最近未读。",
                                color = radarColors().textTertiary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        text = "兴趣词 ${state.terms.size} 个",
                        color = radarColors().textSecondary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
                        // 词袋按权重降序，权重条即"这个词在你读过的东西里有多突出"
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.terms.take(30).forEach { term ->
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = term.term,
                                            color = radarColors().textPrimary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${(term.weight * 100).toInt()}",
                                            color = radarColors().textTertiary,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(radarColors().surface2),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(term.weight.toFloat().coerceIn(0.02f, 1f))
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(radarColors().accent),
                                        )
                                    }
                                }
                            }
                            if (state.terms.size > 30) {
                                Text(
                                    text = "仅展示权重最高的 30 个，完整词袋共 ${state.terms.size} 个",
                                    color = radarColors().textTertiary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    if (state.affinities.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "订阅源亲和度 ${state.affinities.size} 个",
                            color = radarColors().textSecondary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                state.affinities.take(15).forEach { row ->
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = row.title.ifBlank { "（已删除的订阅源）" },
                                                color = radarColors().textPrimary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${(row.affinity * 100).toInt()}",
                                                color = radarColors().textTertiary,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(radarColors().surface2),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(row.affinity.toFloat().coerceIn(0.02f, 1f))
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(radarColors().accent),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "亲和度 = 该源文章的历史打开率（越近的打开权重越高），按最高的那个源归一化。",
                                    color = radarColors().textTertiary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
