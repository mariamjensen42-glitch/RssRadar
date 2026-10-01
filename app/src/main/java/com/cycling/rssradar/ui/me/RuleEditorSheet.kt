package com.cycling.rssradar.ui.me

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.db.FeedEntity
import com.cycling.rssradar.core.domain.filter.FilterRule
import com.cycling.rssradar.core.domain.filter.RuleAction
import com.cycling.rssradar.core.domain.filter.RuleField
import com.cycling.rssradar.core.domain.filter.RuleMatchType
import com.cycling.rssradar.core.domain.filter.RuleScopeType
import com.cycling.rssradar.core.ui.components.ConfirmDialog
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.i18n.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleEditorSheet(
    rule: FilterRule,
    feeds: List<FeedEntity>,
    groups: List<String>,
    previewCount: Int?,
    onPreview: (FilterRule) -> Unit,
    onSave: (FilterRule) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(rule) { mutableStateOf(rule) }
    var confirmHide by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                text = stringResource(
                    if (rule.id == 0L) R.string.rule_new else R.string.rule_edit,
                ),
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text(stringResource(R.string.rule_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.rule_match_type), color = radarColors().textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RuleMatchType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = draft.matchType == type,
                        onClick = { draft = draft.copy(matchType = type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = RuleMatchType.entries.size),
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.rule_fields), color = radarColors().textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuleField.entries.forEach { field ->
                    FilterChip(
                        selected = field in draft.fields,
                        onClick = {
                            draft = draft.copy(
                                fields = if (field in draft.fields) draft.fields - field else draft.fields + field,
                            )
                        },
                        label = { Text(stringResource(field.labelRes())) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = draft.pattern,
                onValueChange = { draft = draft.copy(pattern = it) },
                label = { Text(stringResource(R.string.rule_pattern)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (draft.matchType == RuleMatchType.REGEX) R.string.rule_pattern_regex_hint else R.string.rule_pattern_hint,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            SettingSwitchRow(
                label = stringResource(R.string.rule_case_sensitive),
                checked = draft.caseSensitive,
                onChange = { draft = draft.copy(caseSensitive = it) },
            )
            SettingSwitchRow(
                label = stringResource(R.string.rule_whole_word),
                checked = draft.wholeWord,
                onChange = { draft = draft.copy(wholeWord = it) },
                subtitle = stringResource(R.string.rule_whole_word_hint),
            )
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.rule_scope), color = radarColors().textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RuleScopeType.entries.forEachIndexed { index, scope ->
                    SegmentedButton(
                        selected = draft.scopeType == scope,
                        onClick = { draft = draft.copy(scopeType = scope, scopeId = null) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = RuleScopeType.entries.size),
                        label = { Text(stringResource(scope.labelRes())) },
                    )
                }
            }
            if (draft.scopeType == RuleScopeType.FEED) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    feeds.forEach { feed ->
                        FilterChip(
                            selected = draft.scopeId == feed.id.toString(),
                            onClick = { draft = draft.copy(scopeId = feed.id.toString()) },
                            label = { Text(feed.title) },
                        )
                    }
                }
            }
            if (draft.scopeType == RuleScopeType.GROUP) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { group ->
                        FilterChip(
                            selected = draft.scopeId == group,
                            onClick = { draft = draft.copy(scopeId = group) },
                            label = { Text(group) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.rule_action), color = radarColors().textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RuleAction.entries.forEach { action ->
                    FilterChip(
                        selected = draft.action == action,
                        onClick = { draft = draft.copy(action = action) },
                        label = { Text(stringResource(action.labelRes())) },
                    )
                }
            }
            if (draft.action == RuleAction.HIDE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.rule_hide_warning),
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(onClick = { onPreview(draft) }) {
                    Text(stringResource(R.string.rule_preview))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        previewCount != null -> stringResource(R.string.rule_preview_hits, previewCount)
                        draft.matchType == RuleMatchType.REGEX -> stringResource(R.string.rule_preview_unknown)
                        else -> stringResource(R.string.rule_preview_none)
                    },
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { if (draft.action == RuleAction.HIDE) confirmHide = true else onSave(draft) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
            if (rule.id != 0L) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { onDelete(rule.id) }) {
                    Text(stringResource(R.string.action_delete), color = com.cycling.rssradar.core.ui.theme.Danger)
                }
            }
        }
    }

    if (confirmHide) {
        ConfirmDialog(
            title = stringResource(R.string.rule_action_hide),
            text = stringResource(R.string.rule_hide_confirm),
            confirmText = stringResource(R.string.action_confirm),
            onConfirm = {
                confirmHide = false
                onSave(draft)
            },
            onDismiss = { confirmHide = false },
            destructive = true,
        )
    }
}
