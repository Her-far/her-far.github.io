package io.github.herfar.barodial.ui.dialer

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
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
)

private data class Key(val digit: Char, val letters: String, val longPress: Char? = null)

private val KEYS = listOf(
    listOf(Key('1', ""), Key('2', "ABC"), Key('3', "DEF")),
    listOf(Key('4', "GHI"), Key('5', "JKL"), Key('6', "MNO")),
    listOf(Key('7', "PQRS"), Key('8', "TUV"), Key('9', "WXYZ")),
    listOf(Key('*', ""), Key('0', "+", longPress = '+'), Key('#', "")),
)

/**
 * 숫자 키패드. 큰 버튼, 넉넉한 간격, 입력 중 일치 연락처 표시.
 * 화면 높이에 맞춰 버튼 높이를 조절하고, 펼친 화면에서도 버튼이 지나치게 커지지 않도록 폭을 제한한다.
 */
@Composable
fun DialerPane(
    state: DialerUiState,
    haptics: Boolean,
    actions: DialerActions,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
) {
    val view = LocalView.current
    fun feedback() {
        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    BoxWithConstraints(modifier.fillMaxSize().testTag("dialerPane")) {
        val keyHeight = ((maxHeight * 0.46f) / 5f).coerceIn(44.dp, 72.dp)
        val gap = if (keyHeight < 56.dp) 6.dp else 10.dp
        Column(Modifier.fillMaxSize()) {
            topBar()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.digits.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dialer_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    )
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
            }
            NumberDisplay(state, actions, onFeedback = ::feedback)
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
                                height = keyHeight,
                                onPress = {
                                    feedback()
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
                ActionRow(state, keyHeight, actions, onFeedback = ::feedback)
            }
            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun NumberDisplay(state: DialerUiState, actions: DialerActions, onFeedback: () -> Unit) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pasteLabel = stringResource(R.string.dialer_paste)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .combinedClickable(
                onLongClickLabel = pasteLabel,
                onClick = {},
                onLongClick = {
                    onFeedback()
                    scope.launch {
                        val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (text != null) actions.paste(text)
                    }
                },
            )
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 24.dp, vertical = 8.dp),
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
