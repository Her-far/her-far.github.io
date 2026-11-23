package io.github.herfar.barodial.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.herfar.barodial.R
import io.github.herfar.barodial.data.recordings.CallRecording
import io.github.herfar.barodial.ui.icons.AppIcons
import kotlinx.coroutines.delay

object RecordingDisplay {
    /** "오후 3:12 · 2분 13초" */
    fun subtitle(r: CallRecording): String =
        "${TimeDisplay.dateTime(r.startMillis)} · ${TimeDisplay.duration(r.durationMillis / 1000)}"

    fun clock(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

/** 녹음 목록 한 줄: ▶ 버튼 + 제목 + 시각·길이 */
@Composable
fun RecordingRow(
    title: String,
    recording: CallRecording,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = RecordingDisplay.subtitle(recording),
    trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    ListRow(
        title = title.asAnnotated(),
        subtitle = subtitle.asAnnotated(),
        onClick = onPlay,
        leading = {
            CircleIcon(
                AppIcons.PlayArrow,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                background = MaterialTheme.colorScheme.tertiaryContainer,
            )
        },
        trailing = trailing,
        modifier = modifier.testTag("recording_${recording.id}"),
    )
}

/**
 * 통화 녹음 재생 시트. 열리면 바로 재생하고, 앱이 화면에서 사라지면 일시정지한다.
 * 재생할 수 없는 형식이면 다른 앱으로 열 수 있게 안내한다.
 */
@Composable
fun RecordingPlayerSheet(
    recording: CallRecording,
    title: String,
    onDismiss: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val player = remember(recording.uri) { MediaPlayer() }
    var prepared by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(recording.durationMillis) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            player.setOnPreparedListener {
                prepared = true
                if (it.duration > 0) duration = it.duration.toLong()
                it.start()
                playing = true
            }
            player.setOnCompletionListener {
                playing = false
                position = duration
            }
            player.setOnErrorListener { _, _, _ ->
                failed = true
                playing = false
                true
            }
            player.setDataSource(context, recording.uri.toUri())
            player.prepareAsync()
        } catch (e: Exception) {
            failed = true
        }
        onDispose { player.release() }
    }

    LaunchedEffect(playing) {
        while (playing) {
            if (!dragging) position = runCatching { player.currentPosition.toLong() }.getOrDefault(position)
            delay(200)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (playing) {
            runCatching { player.pause() }
            playing = false
        }
    }

    fun seekTo(ms: Long) {
        if (!prepared) return
        val target = ms.coerceIn(0, duration)
        runCatching { player.seekTo(target.toInt()) }
        position = target
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = Modifier.testTag("recordingPlayer")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                RecordingDisplay.subtitle(recording),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (failed) {
                Text(
                    stringResource(R.string.recording_play_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
            }
            Slider(
                value = if (dragging) dragValue else if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    seekTo((dragValue * duration).toLong())
                    dragging = false
                },
                enabled = prepared,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(RecordingDisplay.clock(if (dragging) (dragValue * duration).toLong() else position), style = MaterialTheme.typography.labelMedium)
                Text(RecordingDisplay.clock(duration), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                IconButton(onClick = { seekTo(position - 10_000) }, enabled = prepared) {
                    Icon(AppIcons.FastRewind, contentDescription = stringResource(R.string.recording_back10))
                }
                FilledIconButton(
                    onClick = {
                        if (!prepared) return@FilledIconButton
                        if (playing) {
                            runCatching { player.pause() }
                            playing = false
                        } else {
                            if (position >= duration) seekTo(0)
                            runCatching { player.start() }
                            playing = true
                        }
                    },
                    enabled = prepared,
                    modifier = Modifier.size(64.dp).testTag("recordingPlayPause"),
                ) {
                    Icon(
                        if (playing) AppIcons.Pause else AppIcons.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.recording_pause else R.string.recording_play),
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = { seekTo(position + 10_000) }, enabled = prepared) {
                    Icon(AppIcons.FastForward, contentDescription = stringResource(R.string.recording_forward10))
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenExternally, modifier = Modifier.fillMaxWidth()) {
                Icon(AppIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.recording_open_external))
            }
            Text(
                stringResource(R.string.recording_file_name, recording.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
