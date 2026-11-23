package io.github.herfar.barodial.ui.dialer

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.textclassifier.TextClassifier
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.data.contacts.Contact
import io.github.herfar.barodial.data.contacts.PhoneNumber
import io.github.herfar.barodial.ui.DialerUiState
import io.github.herfar.barodial.ui.components.CallIconButton
import io.github.herfar.barodial.ui.components.ContactAvatar
import io.github.herfar.barodial.ui.components.ListRow
import io.github.herfar.barodial.ui.components.PhoneDisplay
import io.github.herfar.barodial.ui.components.asAnnotated
import io.github.herfar.barodial.ui.components.highlightStyle
import io.github.herfar.barodial.ui.icons.AppIcons
import io.github.herfar.barodial.ui.theme.DialDigitStyle
import io.github.herfar.barodial.ui.theme.LocalCallColors
import kotlinx.coroutines.launch

class DialerActions(
    val digit: (Char) -> Unit,
    val backspace: () -> Unit,
    val clear: () -> Unit,
    val call: () -> Unit,
    val addContact: (String) -> Unit,
    val sms: (String) -> Unit,
    val openContact: (Contact) -> Unit,
    val callContact: (Contact, PhoneNumber?) -> Unit,
    val paste: (String) -> Unit,
    val numberCopied: () -> Unit,
)

private data class Key(val digit: Char, val letters: String, val longPress: Char? = null)

private val KEYS = listOf(
    listOf(Key('1', ""), Key('2', "ABC"), Key('3', "DEF")),
    listOf(Key('4', "GHI"), Key('5', "JKL"), Key('6', "MNO")),
    listOf(Key('7', "PQRS"), Key('8', "TUV"), Key('9', "WXYZ")),
    listOf(Key('*', ""), Key('0', "+", longPress = '+'), Key('#', "")),
)

/** 화면 높이에 맞춘 키 높이 (너무 작거나 크지 않게) */
fun dialKeyHeightFor(availableHeight: Dp, reservedAbove: Dp): Dp =
    ((availableHeight - reservedAbove - 140.dp) / 5f).coerceIn(46.dp, 72.dp)

/**
 * 숫자 키패드. 큰 버튼, 넉넉한 간격, 입력 중 일치 연락처 표시.
 *
 * - 번호 옆 ⋮ 메뉴: 붙여넣기, 번호 복사, 연락처에 추가, 문자, 2초 대기(,)·대기(;) 추가
 * - 클립보드에 복사한 텍스트가 있으면 [붙여넣기] 버튼을 보여 준다 (내용은 누를 때만 읽는다)
 * - 키를 누르면 진동과, 시스템 설정을 따르는 키패드 톤
 *
 * @param keyHeight 지정하지 않으면 화면 높이에 맞춰 정한다
 * @param emptyContent 번호를 입력하기 전, 일치 연락처 자리에 보여 줄 내용 (홈: 즐겨찾기)
 */
@Composable
fun DialerPane(
    state: DialerUiState,
    haptics: Boolean,
    actions: DialerActions,
    modifier: Modifier = Modifier,
    keyHeight: Dp? = null,
    applyBottomInsets: Boolean = true,
    topBar: @Composable () -> Unit = {},
    emptyContent: @Composable BoxScope.() -> Unit = { DialerHint() },
) {
    val view = LocalView.current
    val tones = rememberDialTones()
    fun feedback(c: Char? = null) {
        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (c != null) tones.play(c)
    }
    val clipboardHasText = rememberClipboardHasText()

    BoxWithConstraints(modifier.fillMaxSize().testTag("dialerPane")) {
        val kh = keyHeight ?: dialKeyHeightFor(maxHeight, 64.dp)
        val gap = if (kh < 56.dp) 6.dp else 10.dp
        Column(Modifier.fillMaxSize()) {
            topBar()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.digits.isEmpty()) {
                    emptyContent()
                } else {
                    LazyColumn(reverseLayout = true, modifier = Modifier.fillMaxSize().testTag("dialerMatches")) {
                        items(state.matches, key = { it.contact.id }) { match ->
                            val c = match.contact
                            val phone = match.matchedPhone
                            ListRow(
                                title = c.displayName.asAnnotated(),
                                subtitle = phone?.let { PhoneDisplay.highlighted(PhoneDisplay.format(it), match.phoneDigitRange, highlightStyle()) },
                                onClick = { actions.openContact(c) },
                                leading = { ContactAvatar(c.displayName, c.photoThumbnailUri, 40.dp) },
                                trailing = {
                                    CallIconButton(
                                        onClick = { actions.callContact(c, phone) },
                                        contentDescription = stringResource(R.string.action_call_target, c.displayName),
                                    )
                                },
                            )
                        }
                    }
                }
                if (state.digits.isEmpty() && clipboardHasText) {
                    PasteChip(
                        onPaste = actions.paste,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                    )
                }
            }
            NumberDisplay(state, actions, onFeedback = { feedback() })
            Column(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (row in KEYS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (key in row) {
                            DialKey(
                                key = key,
                                height = kh,
                                onPress = {
                                    feedback(key.digit)
                                    actions.digit(key.digit)
                                },
                                onLongPress = key.longPress?.let { lp ->
                                    {
                                        feedback()
                                        actions.digit(lp)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                ActionRow(state, kh, actions, onFeedback = { feedback() })
            }
            Spacer(Modifier.height(10.dp))
            if (applyBottomInsets) Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun BoxScope.DialerHint() {
    Text(
        text = stringResource(R.string.dialer_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
    )
}

/** 클립보드에서 텍스트를 읽어 키패드에 붙여넣는다. */
@Composable
private fun rememberPaste(onPaste: (String) -> Unit): () -> Unit {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(context)?.toString()
            onPaste(text.orEmpty())
        }
    }
}

@Composable
private fun PasteChip(onPaste: (String) -> Unit, modifier: Modifier = Modifier) {
    val paste = rememberPaste(onPaste)
    AssistChip(
        onClick = paste,
        label = { Text(stringResource(R.string.dialer_paste_copied)) },
        leadingIcon = { Icon(AppIcons.ContentPaste, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        modifier = modifier.testTag("pasteChip"),
    )
}

@Composable
private fun NumberDisplay(state: DialerUiState, actions: DialerActions, onFeedback: () -> Unit) {
    val paste = rememberPaste(actions.paste)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    val pasteLabel = stringResource(R.string.dialer_paste)
    val hasDigits = state.digits.isNotEmpty()
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(48.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .combinedClickable(
                    onLongClickLabel = pasteLabel,
                    onClick = {},
                    onLongClick = {
                        onFeedback()
                        menuOpen = true
                    },
                )
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = state.formatted,
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 38.sp, stepSize = 2.sp),
                modifier = Modifier.fillMaxWidth().testTag("dialDisplay"),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("dialMenu")) {
                Icon(AppIcons.MoreVert, contentDescription = stringResource(R.string.dialer_menu))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dialer_paste)) },
                    leadingIcon = { Icon(AppIcons.ContentPaste, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        paste()
                    },
                    modifier = Modifier.testTag("dialMenuPaste"),
                )
                if (hasDigits) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_copy_number)) },
                        leadingIcon = { Icon(AppIcons.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("phone", state.digits))) }
                            actions.numberCopied()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_add_contact)) },
                        leadingIcon = { Icon(AppIcons.PersonAdd, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            actions.addContact(state.digits)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_sms)) },
                        leadingIcon = { Icon(AppIcons.Message, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            actions.sms(state.digits)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dialer_add_pause)) },
                        onClick = {
                            menuOpen = false
                            actions.digit(',')
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dialer_add_wait)) },
                        onClick = {
                            menuOpen = false
                            actions.digit(';')
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DialKey(
    key: Key,
    height: Dp,
    onPress: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onPress, onLongClick = onLongPress)
            .semantics { contentDescription = key.digit.toString() }
            .testTag("key_${key.digit}"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(key.digit.toString(), style = DialDigitStyle, color = MaterialTheme.colorScheme.onSurface)
            if (key.letters.isNotEmpty() && height >= 56.dp) {
                Text(
                    key.letters,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(state: DialerUiState, keyHeight: Dp, actions: DialerActions, onFeedback: () -> Unit) {
    val callColors = LocalCallColors.current
    val callSize = (keyHeight + 8.dp).coerceIn(56.dp, 76.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        val hasDigits = state.digits.isNotEmpty()
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (hasDigits) {
                val addLabel = stringResource(R.string.action_add_contact)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .combinedClickable(onClick = { actions.addContact(state.digits) }, onLongClick = { actions.sms(state.digits) })
                        .semantics { contentDescription = addLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val callLabel = stringResource(R.string.action_call)
        Box(
            modifier = Modifier
                .size(callSize)
                .clip(CircleShape)
                .background(callColors.call)
                .combinedClickable(onClick = {
                    onFeedback()
                    actions.call()
                })
                .semantics { contentDescription = callLabel }
                .testTag("dialCallButton"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Call, contentDescription = null, tint = callColors.onCall, modifier = Modifier.size(30.dp))
        }
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (hasDigits) {
                val backLabel = stringResource(R.string.dialer_backspace)
                val clearLabel = stringResource(R.string.dialer_clear)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onLongClickLabel = clearLabel,
                            onClick = {
                                onFeedback()
                                actions.backspace()
                            },
                            onLongClick = {
                                onFeedback()
                                actions.clear()
                            },
                        )
                        .semantics { contentDescription = backLabel }
                        .testTag("dialBackspace"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Backspace, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 클립보드 · 키패드 톤

/**
 * 클립보드에 텍스트가 있는지 (내용을 읽지 않고 설명만 확인하므로 Android 의 "클립보드 접근" 알림이 뜨지 않는다).
 * Android 12+ 에서 시스템이 분류를 마쳤다면 전화번호일 가능성이 있는 경우에만 true.
 */
@Composable
private fun rememberClipboardHasText(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val focused = LocalWindowInfo.current.isWindowFocused
    var hasText by remember { mutableStateOf(false) }
    fun check() {
        hasText = try {
            val d = manager?.primaryClipDescription
            d != null && (d.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || d.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) && looksLikePhone(d)
        } catch (e: Exception) {
            false
        }
    }
    LaunchedEffect(focused) { if (focused) check() }
    DisposableEffect(manager) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener { check() }
        manager?.addPrimaryClipChangedListener(listener)
        onDispose { manager?.removePrimaryClipChangedListener(listener) }
    }
    return hasText
}

private fun looksLikePhone(d: ClipDescription): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    if (d.classificationStatus != ClipDescription.CLASSIFICATION_COMPLETE) return true
    return d.getConfidenceScore(TextClassifier.TYPE_PHONE) > 0.2f
}

/** 키패드 톤. 시스템 설정 "다이얼 키패드 톤"이 켜져 있고 벨소리 모드일 때만 소리를 낸다. */
private class DialTones(private val context: Context) {
    private val generator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_DTMF, 70)
    } catch (e: RuntimeException) {
        null
    }

    private fun enabled(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        return Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1
    }

    fun play(c: Char) {
        val tone = when (c) {
            in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (c - '0')
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        if (enabled()) generator?.startTone(tone, 120)
    }

    fun release() = generator?.release()
}

@Composable
private fun rememberDialTones(): DialTones {
    val context = LocalContext.current
    val tones = remember(context) { DialTones(context.applicationContext) }
    DisposableEffect(tones) { onDispose { tones.release() } }
    return tones
}
