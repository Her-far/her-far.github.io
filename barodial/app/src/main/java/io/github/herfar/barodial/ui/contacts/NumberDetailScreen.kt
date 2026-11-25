package io.github.herfar.barodial.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.data.calllog.CallLogEntry
import io.github.herfar.barodial.data.calllog.CallLogGrouping
import io.github.herfar.barodial.data.recordings.CallRecording
import io.github.herfar.barodial.ui.components.AppTopBar
import io.github.herfar.barodial.ui.components.CircleIcon
import io.github.herfar.barodial.ui.components.EmptyMessage
import io.github.herfar.barodial.ui.components.PhoneDisplay
import io.github.herfar.barodial.ui.components.RecordingRow
import io.github.herfar.barodial.ui.components.SectionHeader
import io.github.herfar.barodial.ui.components.TimeDisplay
import io.github.herfar.barodial.ui.icons.AppIcons
import io.github.herfar.barodial.ui.theme.LocalCallColors

class NumberDetailActions(
    val navigate: () -> Unit,
    val call: (String) -> Unit,
    val sms: (String) -> Unit,
    val addContact: (String) -> Unit,
    val playRecording: (CallRecording, String) -> Unit,
)

/**
 * 연락처에 없는 번호의 상세: 번호, 전화·문자·연락처 추가, 통화내역(녹음 ▶), 그 밖의 녹음.
 * '연락처에 추가'는 새 연락처 / 기존 연락처를 고르는 하단 시트를 연다 (MainViewModel.addNumberToContacts).
 */
@Composable
fun NumberDetailScreen(
    number: String,
    calls: List<CallLogEntry>?,
    recordings: List<CallRecording>,
    recordingByCallId: Map<Long, CallRecording>,
    navigationIcon: ImageVector,
    actions: NumberDetailActions,
    modifier: Modifier = Modifier,
) {
    val formatted = PhoneDisplay.format(number)
    val shownCalls = calls?.take(50)
    val attachedIds = shownCalls?.mapNotNullTo(HashSet()) { recordingByCallId[it.id]?.id }.orEmpty()
    val otherRecordings = recordings.filter { it.id !in attachedIds }
    Surface(modifier = modifier.fillMaxSize().testTag("numberDetail"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = "",
                navigationIcon = navigationIcon,
                navigationDescription = stringResource(R.string.action_back),
                onNavigate = actions.navigate,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item("header") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircleIcon(AppIcons.Person, size = 104.dp)
                        Spacer(Modifier.height(14.dp))
                        Text(formatted, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.testTag("numberDetailTitle"))
                        Text(
                            stringResource(R.string.number_detail_not_in_contacts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item("actions") {
                    val callColors = LocalCallColors.current
                    Row(
                        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickAction(
                            icon = AppIcons.Call,
                            label = stringResource(R.string.action_call),
                            onClick = { actions.call(number) },
                            containerColor = callColors.call,
                            contentColor = callColors.onCall,
                            modifier = Modifier.weight(1f).testTag("numberDetailCall"),
                        )
                        QuickAction(
                            icon = AppIcons.Message,
                            label = stringResource(R.string.action_sms),
                            onClick = { actions.sms(number) },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        QuickAction(
                            icon = AppIcons.PersonAdd,
                            label = stringResource(R.string.action_add_contact),
                            onClick = { actions.addContact(number) },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f).testTag("numberDetailAddContact"),
                        )
                    }
                }
                if (shownCalls != null) {
                    item("callsHeader") {
                        val stats = CallLogGrouping.stats(shownCalls)
                        SectionHeader(
                            stringResource(R.string.detail_call_history_title, stats.totalCalls, TimeDisplay.duration(stats.totalDurationSeconds)),
                            modifier = Modifier.widthIn(max = 640.dp),
                        )
                    }
                    if (shownCalls.isEmpty()) item("callsEmpty") { EmptyMessage(stringResource(R.string.detail_call_history_empty)) }
                    items(shownCalls, key = { "call_${it.id}" }) { call ->
                        CallEntryRow(call, recordingByCallId[call.id], onPlay = { actions.playRecording(it, formatted) })
                    }
                }
                if (otherRecordings.isNotEmpty()) {
                    item("recordingsHeader") {
                        SectionHeader(
                            stringResource(
                                if (calls == null) R.string.detail_recordings_title else R.string.detail_other_recordings_title,
                                otherRecordings.size,
                            ),
                            modifier = Modifier.widthIn(max = 640.dp),
                        )
                    }
                    items(otherRecordings.take(50), key = { "rec_${it.id}" }) { rec ->
                        RecordingRow(
                            title = TimeDisplay.dateTime(rec.startMillis),
                            subtitle = stringResource(R.string.recording_length, TimeDisplay.duration(rec.durationMillis / 1000)),
                            recording = rec,
                            onPlay = { actions.playRecording(rec, formatted) },
                            modifier = Modifier.widthIn(max = 640.dp),
                        )
                    }
                }
                item("bottomInset") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
            }
        }
    }
}
