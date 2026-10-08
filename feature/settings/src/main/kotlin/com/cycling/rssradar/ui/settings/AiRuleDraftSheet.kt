package com.cycling.rssradar.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.cycling.rssradar.core.domain.filter.RuleField
import com.cycling.rssradar.core.ui.text.resolve
import com.cycling.rssradar.core.ui.theme.Warning

/**
 * 「用 AI 生成规则」面板：一句话描述 → 生成 → **逐条复核** → 勾选启用。
 *
 * 面板的存在理由不是"让 AI 写规则"，而是**让用户在规则生效前看见两件事**：
 * ① 这条规则本地实算会命中哪些真实标题；② 模型给的举例里有没有对不上的。
 * 后者是关键——规则一旦启用命中就被隐藏，用户此后看不到那些文章，
 * 也就无从发现自己被屏蔽了什么。这一步是这条链路唯一的安全阀，所以它必须显眼且有结论。
 *
 * 只做「全部订阅 + 隐藏」：想改作用域或动作，启用后到规则编辑器里改。
 * 把三个下拉搬进这里，会让"看一眼再确认"变成一次配置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiRuleDraftSheet(
    draft: FilterRulesViewModel.DraftUi,
    aiEnabled: Boolean,
    onDescriptionChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onToggleProposal: (Int) -> Unit,
    onApply: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val selectedCount = draft.proposals.count { it.selected }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                text = stringResource(R.string.rule_ai_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.rule_ai_desc),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))

            // 功能没开时不摆输入框：让用户敲完一句话再告诉他要先去别处开开关，
            // 是白费他一次输入。
            if (!aiEnabled) {
                AiSettingOffNotice(onOpenAiSettings = onOpenAiSettings)
                return@Column
            }

            OutlinedTextField(
                value = draft.description,
                onValueChange = onDescriptionChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.rule_ai_placeholder),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onGenerate,
                // 禁用时必须说明原因：加载中不是"不能点"，而是"已经在跑了"。
                enabled = !draft.loading && draft.description.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        when {
                            draft.loading -> R.string.rule_ai_generating
                            draft.proposals.isEmpty() -> R.string.rule_ai_generate
                            else -> R.string.rule_ai_regenerate
                        },
                    ),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.rule_ai_scope_note),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )

            draft.problem?.let { problem ->
                Spacer(Modifier.height(12.dp))
                ProblemLine(text = problem.resolve())
            }

            if (draft.proposals.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                draft.proposals.forEachIndexed { index, item ->
                    ProposalCard(
                        proposal = item,
                        selected = item.selected,
                        onToggle = { onToggleProposal(index) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onApply,
                    enabled = selectedCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.rule_ai_apply, selectedCount))
                }
            }
        }
    }
}

/** 功能未开启时的解释 + 出口。禁用态不给"为什么"就是死路，所以这里必须带一句话和一条路。 */
@Composable
private fun AiSettingOffNotice(onOpenAiSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerLowest) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.rule_ai_off),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOpenAiSettings) {
                Text(
                    text = stringResource(R.string.rule_ai_off_action),
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ProblemLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Lucide.CircleAlert,
            contentDescription = null,
            tint = Warning,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * 一条提案。
 *
 * 三行信息缺一不可：规则本身（关键词 + 作用字段）、**本地实算的命中**、
 * **模型举例里对不上的**。中间那行是复核结论，最后那行是"这条规则可能有问题"的警报——
 * 少了它，用户就只剩"相信模型"这一个选项。
 */
@Composable
private fun ProposalCard(
    proposal: FilterRulesViewModel.ProposalUi,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val item = proposal.proposal
    // 非 @Composable 的 lambda（joinToString 的 transform）里不能调 stringResource，
    // 先在组合作用域把标签取好——associateWith 是 inline，所以它能带 @Composable 调用。
    val fieldLabels = RuleField.entries.associateWith { stringResource(it.labelRes()) }
    val fieldLabel = item.fields.joinToString("、") { fieldLabels.getValue(it) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 整行可点：只让 24dp 的勾选框可点，会让这排卡片变得难对付
                .clickable(onClick = onToggle)
                .padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = selected, onCheckedChange = null)
            Spacer(Modifier.width(4.dp))
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.keyword,
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = fieldLabel,
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (item.checked) {
                    Text(
                        text = stringResource(R.string.rule_ai_verified, item.verifiedHits.size),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    item.verifiedHits.take(HIT_PREVIEW).forEach { hit ->
                        Text(
                            text = "· $hit",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.rule_ai_unchecked),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (item.fabricated.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.rule_ai_fabricated, item.fabricated.size),
                        color = Warning,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    item.fabricated.take(HIT_PREVIEW).forEach { hit ->
                        Text(
                            text = "· $hit",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.rule_ai_library, item.libraryCount),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** 每条提案最多列几个标题示例：再多就把面板撑成一堵墙，复核也就没人看了。 */
private const val HIT_PREVIEW = 3
