package io.github.herfar.barodial.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.data.calllog.CallHistoryAvailability
import io.github.herfar.barodial.data.calllog.CallLogEntry
import io.github.herfar.barodial.data.calllog.CallType
import io.github.herfar.barodial.data.recordings.CallRecording
import io.github.herfar.barodial.data.recordings.RecordingsAvailability
import io.github.herfar.barodial.ui.components.AppTopBar
import io.github.herfar.barodial.ui.components.PhoneDisplay
import io.github.herfar.barodial.ui.components.SectionHeader
import io.github.herfar.barodial.ui.components.TimeDisplay

class DiagnosticsActions(
    val back: () -> Unit,
    val rescan: () -> Unit,
    val requestPermission: () -> Unit,
    val play: (CallRecording, String) -> Unit,
)

private const val SHOWN = 15

/**
 * 통화 녹음 연결 진단.
 * 녹음 파일이 몇 개 보이는지, 파일 이름에서 시각을 읽었는지, 어느 통화에 연결되었는지를 그대로 보여 준다.
 * 화면 내용은 기기 안에서만 표시된다.
 */
@Composable
fun RecordingDiagnosticsScreen(
    recordingsAvailability: RecordingsAvailability,
    callAvailability: CallHistoryAvailability,
    recordings: List<CallRecording>,
    calls: List<CallLogEntry>,
    recordingByCallId: Map<Long, CallRecording>,
    nameFor: (CallLogEntry) -> String?,
    navigationIcon: ImageVector,
    actions: DiagnosticsActions,
) {
    val callByRecordingId = remember(recordingByCallId, calls) {
        val byId = calls.associateBy { it.id }
        recordingByCallId.entries.mapNotNull { (callId, rec) -> byId[callId]?.let { rec.id to it } }.toMap()
    }
    Surface(Modifier.fillMaxSize().testTag("recordingDiagnostics"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(R.string.diag_title),
                navigationIcon = navigationIcon,
                navigationDescription = stringResource(R.string.action_back),
                onNavigate = actions.back,
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item("summary") {
                    Card(
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val granted = recordingsAvailability == RecordingsAvailability.Available
                            Line(stringResource(R.string.diag_audio_permission, stringResource(if (granted) R.string.settings_perm_granted else R.string.diag_not_granted)))
                            val newestRec = recordings.maxByOrNull { it.startMillis }
                            Line(
                                stringResource(
                                    R.string.diag_recordings_count,
                                    recordings.size,
                                    newestRec?.let { TimeDisplay.dateTime(it.startMillis) } ?: "-",
                                ),
                            )
                            val callLogText = if (callAvailability == CallHistoryAvailability.Available) {
                                stringResource(R.string.diag_calls_count, calls.size, calls.firstOrNull()?.let { TimeDisplay.dateTime(it.timestampMillis) } ?: "-")
                            } else {
                                stringResource(R.string.diag_calls_unavailable)
                            }
                            Line(callLogText)
                            Line(stringResource(R.string.diag_matched, recordingByCallId.size, recordings.size - callByRecordingId.size))
                            Spacer(Modifier.height(4.dp))
                            if (granted) {
                                Button(onClick = actions.rescan, modifier = Modifier.fillMaxWidth().testTag("diagRescan")) {
                                    Text(stringResource(R.string.diag_rescan))
                                }
                            } else {
                                Button(onClick = actions.requestPermission, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.recordings_permission_body))
                                }
                            }
                            Text(stringResource(R.string.diag_help), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item("recHeader") { SectionHeader(stringResource(R.string.diag_recent_recordings, SHOWN), Modifier.widthIn(max = 640.dp)) }
                items(recordings.sortedByDescending { it.startMillis }.take(SHOWN), key = { "r${it.id}" }) { rec ->
                    val call = callByRecordingId[rec.id]
                    val title = call?.let { nameFor(it) ?: PhoneDisplay.format(it.number, it.nationalDigits) } ?: (rec.label ?: rec.displayName)
                    Entry(
                        title = rec.displayName,
                        lines = listOf(
                            stringResource(R.string.diag_rec_folder, rec.relativePath ?: "-"),
                            stringResource(
                                if (rec.timeFromFileName) R.string.diag_rec_time_name else R.string.diag_rec_time_modified,
                                TimeDisplay.dateTime(rec.startMillis),
                                TimeDisplay.duration(rec.durationMillis / 1000),
                            ),
                            stringResource(R.string.diag_rec_label, rec.label ?: "-", rec.labelDigits?.let { PhoneDisplay.format(it) } ?: "-"),
                            if (call != null) {
                                stringResource(R.string.diag_rec_linked, TimeDisplay.dateTime(call.timestampMillis), typeText(call.type), nameFor(call) ?: PhoneDisplay.format(call.number, call.nationalDigits))
                            } else {
                                stringResource(R.string.diag_rec_not_linked)
                            },
                        ),
                        warn = call == null && callAvailability == CallHistoryAvailability.Available,
                        onClick = { actions.play(rec, title) },
                    )
                }
                if (callAvailability == CallHistoryAvailability.Available) {
                    item("callHeader") { SectionHeader(stringResource(R.string.diag_recent_calls, SHOWN), Modifier.widthIn(max = 640.dp)) }
                    items(calls.take(SHOWN), key = { "c${it.id}" }) { call ->
                        val rec = recordingByCallId[call.id]
                        Entry(
                            title = nameFor(call) ?: PhoneDisplay.format(call.number, call.nationalDigits),
                            lines = listOf(
                                "${TimeDisplay.dateTime(call.timestampMillis)} · ${typeText(call.type)} · ${TimeDisplay.duration(call.durationSeconds)}",
                                if (rec != null) stringResource(R.string.diag_call_has_rec, rec.displayName) else stringResource(R.string.diag_call_no_rec),
                            ),
                            warn = false,
                            onClick = null,
                        )
                    }
                }
                item("inset") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
            }
        }
    }
}

@Composable
private fun typeText(type: CallType): String = stringResource(
    when (type) {
        CallType.OUTGOING -> R.string.call_type_outgoing
        CallType.MISSED -> R.string.call_type_missed
        CallType.REJECTED -> R.string.call_type_rejected
        CallType.BLOCKED -> R.string.call_type_blocked
        else -> R.string.call_type_incoming
    },
)

@Composable
private fun Line(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Entry(title: String, lines: List<String>, warn: Boolean, onClick: (() -> Unit)?) {
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        lines.forEachIndexed { i, line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = if (warn && i == lines.lastIndex) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(Modifier.widthIn(max = 640.dp).padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
