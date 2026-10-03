package com.cycling.rssradar.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cycling.rssradar.core.domain.filter.KeywordMatcher
import com.cycling.rssradar.core.ui.R as UiR
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.components.OptionRow
import com.cycling.rssradar.core.ui.components.SectionHeader
import com.cycling.rssradar.core.ui.components.SettingSwitchRow
import com.cycling.rssradar.core.ui.components.SettingsSubPage

/** 勿扰时段与关键词通知。判定链在 core/domain 的 NotifyDecision，这里只管采集。 */
@Composable
fun NotificationSettingsScreen(
    viewModel: NotificationViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val prefs by viewModel.state.collectAsState()
    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }
    var includeDraft by remember(prefs.includeKeywords) {
        mutableStateOf(prefs.includeKeywords.joinToString(KEYWORD_SEPARATOR))
    }
    var excludeDraft by remember(prefs.excludeKeywords) {
        mutableStateOf(prefs.excludeKeywords.joinToString(KEYWORD_SEPARATOR))
    }

    SettingsSubPage(title = stringResource(R.string.settings_notification), onBack = onBack) {
        SectionHeader(
            stringResource(R.string.notify_dnd),
            stringResource(R.string.notify_dnd_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                SettingSwitchRow(
                    label = stringResource(R.string.notify_dnd_enabled),
                    checked = prefs.dndEnabled,
                    onChange = { enabled ->
                        if (enabled) {
                            viewModel.setDnd(DEFAULT_DND_START, DEFAULT_DND_END)
                        } else {
                            viewModel.setDnd(null, null)
                        }
                    },
                )
                if (prefs.dndEnabled) {
                    OptionRow(
                        label = stringResource(R.string.notify_dnd_start),
                        value = formatMinuteOfDay(prefs.dndStartMinute),
                        onClick = { editingStart = true },
                    )
                    OptionRow(
                        label = stringResource(R.string.notify_dnd_end),
                        value = formatMinuteOfDay(prefs.dndEndMinute),
                        onClick = { editingEnd = true },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        SectionHeader(
            stringResource(R.string.notify_keyword),
            stringResource(R.string.notify_keyword_desc),
        )
        Surface(shape = RoundedCornerShape(14.dp), color = radarColors().surface1) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                OutlinedTextField(
                    value = includeDraft,
                    onValueChange = { includeDraft = it },
                    label = { Text(stringResource(R.string.notify_keyword_include)) },
                    supportingText = { Text(stringResource(R.string.notify_keyword_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { viewModel.setIncludeKeywords(KeywordMatcher.parseKeywords(includeDraft)) }) {
                    Text(stringResource(R.string.action_save))
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = excludeDraft,
                    onValueChange = { excludeDraft = it },
                    label = { Text(stringResource(R.string.notify_keyword_exclude)) },
                    supportingText = { Text(stringResource(R.string.notify_keyword_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { viewModel.setExcludeKeywords(KeywordMatcher.parseKeywords(excludeDraft)) }) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.notify_keyword_note),
            color = radarColors().textTertiary,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }

    if (editingStart) {
        MinutePickerDialog(
            initial = prefs.dndStartMinute ?: DEFAULT_DND_START,
            title = stringResource(R.string.notify_dnd_start),
            onConfirm = { minute ->
                viewModel.setDnd(minute, prefs.dndEndMinute ?: DEFAULT_DND_END)
                editingStart = false
            },
            onDismiss = { editingStart = false },
        )
    }
    if (editingEnd) {
        MinutePickerDialog(
            initial = prefs.dndEndMinute ?: DEFAULT_DND_END,
            title = stringResource(R.string.notify_dnd_end),
            onConfirm = { minute ->
                viewModel.setDnd(prefs.dndStartMinute ?: DEFAULT_DND_START, minute)
                editingEnd = false
            },
            onDismiss = { editingEnd = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinutePickerDialog(
    initial: Int,
    title: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial / 60,
        initialMinute = initial % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) {
                Text(stringResource(UiR.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val DEFAULT_DND_START = 22 * 60
private const val DEFAULT_DND_END = 7 * 60
private const val KEYWORD_SEPARATOR = "，"

private fun formatMinuteOfDay(minute: Int?): String {
    val value = minute ?: return "--:--"
    val hour = (value / 60).coerceIn(0, 23).toString().padStart(2, '0')
    val minutePart = (value % 60).coerceIn(0, 59).toString().padStart(2, '0')
    return "$hour:$minutePart"
}
