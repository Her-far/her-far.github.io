package io.github.herfar.barodial.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.ui.icons.AppIcons

/**
 * 하단 고정 검색창.
 *
 * - 둥근 알약 모양 전체가 터치 영역이다. 어디를 눌러도 바로 입력이 시작되고 키보드가 열린다.
 * - 키보드가 열리면 상위 레이아웃이 IME 높이만큼 올려 주므로 검색창이 가려지지 않는다.
 * - 오른쪽 원형 버튼은 키패드로 바로 가는 버튼이다 (검색 중에는 숨긴다).
 */
@Composable
fun SearchDock(
    state: TextFieldState,
    active: Boolean,
    focusRequest: Int,
    showKeypadButton: Boolean,
    onFocused: () -> Unit,
    onExit: () -> Unit,
    onKeypad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(active) {
        if (!active && focused) focusManager.clearFocus()
    }

    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = {
                focusRequester.requestFocus()
                keyboard?.show()
            },
            shape = RoundedCornerShape(28.dp),
            color = if (active) colors.surfaceContainerHighest else colors.surfaceContainerHigh,
            border = if (focused) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
            shadowElevation = if (active) 0.dp else 2.dp,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 58.dp)
                .testTag("searchBar"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active) {
                    IconButton(onClick = onExit, modifier = Modifier.testTag("searchExit")) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.search_close))
                    }
                } else {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Search, contentDescription = null, tint = colors.onSurfaceVariant)
                    }
                }
                BasicTextField(
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 14.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) onFocused()
                        }
                        .testTag("searchField"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                        autoCorrectEnabled = false,
                    ),
                    onKeyboardAction = { keyboard?.hide() },
                    cursorBrush = SolidColor(colors.primary),
                    decorator = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (state.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (state.text.isNotEmpty()) {
                    IconButton(onClick = { state.clearText() }, modifier = Modifier.testTag("searchClear")) {
                        Icon(AppIcons.Cancel, contentDescription = stringResource(R.string.search_clear), tint = colors.onSurfaceVariant)
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
        if (showKeypadButton && !active) {
            Spacer(Modifier.width(10.dp))
            FilledTonalIconButton(
                onClick = onKeypad,
                modifier = Modifier.size(58.dp).testTag("keypadButton"),
            ) {
                Icon(AppIcons.Dialpad, contentDescription = stringResource(R.string.keypad), modifier = Modifier.size(26.dp))
            }
        }
    }
}
