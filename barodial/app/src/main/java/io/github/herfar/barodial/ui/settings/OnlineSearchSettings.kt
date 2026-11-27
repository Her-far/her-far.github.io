package io.github.herfar.barodial.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.data.places.PlaceApiConfig
import io.github.herfar.barodial.data.places.PlaceApiConfigs
import io.github.herfar.barodial.data.settings.AppSettings
import io.github.herfar.barodial.data.settings.PlaceApiMode
import io.github.herfar.barodial.ui.ConnectionTestState
import io.github.herfar.barodial.ui.components.AppTopBar
import io.github.herfar.barodial.ui.components.SectionHeader
import io.github.herfar.barodial.ui.search.placeApiConfig
import io.github.herfar.barodial.ui.search.placeErrorText
import java.net.URI

/** 설정 목록에 보여 줄 연결 상태 (키는 끝 4자리만) */
@Composable
fun onlineConnectionStatus(settings: AppSettings): String = when (val c = settings.placeApiConfig()) {
    PlaceApiConfig.None -> stringResource(R.string.settings_online_status_none)
    is PlaceApiConfig.Direct -> stringResource(R.string.settings_online_status_direct, c.restApiKey.takeLast(4))
    is PlaceApiConfig.Proxy -> stringResource(R.string.settings_online_status_proxy, runCatching { URI(c.baseUrl).host }.getOrNull() ?: c.baseUrl)
}

class OnlineSetupActions(
    val back: () -> Unit,
    val setEnabled: (Boolean) -> Unit,
    val test: (PlaceApiConfig) -> Unit,
    val save: (mode: PlaceApiMode, key: String, proxyUrl: String, proxyToken: String) -> Unit,
)

/**
 * 온라인 상호·기관 검색 설정 화면: 사용 여부, 연결 방식(내 REST API 키 / 프록시), [연결 확인], 설정 방법.
 * 접힌 화면에서는 전체 화면, 펼친 화면에서는 오른쪽 창에 열린다.
 */
@Composable
fun OnlineSearchSetupScreen(
    settings: AppSettings,
    testState: ConnectionTestState,
    navigationIcon: ImageVector,
    actions: OnlineSetupActions,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(settings.placeApiMode) }
    // 키·토큰은 저장된 인스턴스 상태(Bundle)에 남지 않도록 remember 만 쓴다.
    var key by remember { mutableStateOf(settings.kakaoRestApiKey) }
    var url by rememberSaveable { mutableStateOf(settings.proxyUrl) }
    var token by remember { mutableStateOf(settings.proxyToken) }
    var showSecret by rememberSaveable { mutableStateOf(false) }
    val secretTransform = if (showSecret) VisualTransformation.None else PasswordVisualTransformation()
    fun current(): PlaceApiConfig = when (mode) {
        PlaceApiMode.DIRECT -> PlaceApiConfigs.direct(key)
        PlaceApiMode.PROXY -> PlaceApiConfigs.proxy(url, token)
    }
    val fieldWidth = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp)

    Surface(modifier.fillMaxSize().testTag("onlineSetup"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(R.string.settings_section_online),
                navigationIcon = navigationIcon,
                navigationDescription = stringResource(R.string.action_back),
                onNavigate = actions.back,
            )
            Column(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).testTag("onlineSetupList"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .clickable(role = Role.Switch) { actions.setEnabled(!settings.onlineSearchEnabled) }
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag("onlineSetupSwitch"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_online_enable), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_online_enable_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = settings.onlineSearchEnabled, onCheckedChange = null)
                }

                SectionHeader(stringResource(R.string.settings_online_connection), modifier = Modifier.widthIn(max = 640.dp))
                SingleChoiceSegmentedButtonRow(fieldWidth) {
                    SegmentedButton(
                        selected = mode == PlaceApiMode.DIRECT,
                        onClick = { mode = PlaceApiMode.DIRECT },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("onlineModeDirect"),
                    ) { Text(stringResource(R.string.online_mode_direct), maxLines = 1) }
                    SegmentedButton(
                        selected = mode == PlaceApiMode.PROXY,
                        onClick = { mode = PlaceApiMode.PROXY },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("onlineModeProxy"),
                    ) { Text(stringResource(R.string.online_mode_proxy), maxLines = 1) }
                }
                if (mode == PlaceApiMode.DIRECT) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it.trim() },
                        label = { Text(stringResource(R.string.online_key_label)) },
                        singleLine = true,
                        visualTransformation = secretTransform,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = fieldWidth.testTag("onlineKeyField"),
                    )
                    Text(stringResource(R.string.online_key_help), style = MaterialTheme.typography.bodySmall, modifier = fieldWidth)
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it.trim() },
                        label = { Text(stringResource(R.string.online_proxy_url_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = fieldWidth.testTag("onlineProxyUrlField"),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it.trim() },
                        label = { Text(stringResource(R.string.online_proxy_token_label)) },
                        singleLine = true,
                        visualTransformation = secretTransform,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = fieldWidth.testTag("onlineProxyTokenField"),
                    )
                    Text(stringResource(R.string.online_proxy_help), style = MaterialTheme.typography.bodySmall, modifier = fieldWidth)
                }
                Row(fieldWidth, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showSecret = !showSecret }) { Text(stringResource(R.string.online_show_key)) }
                    TextButton(
                        onClick = { actions.test(current()) },
                        enabled = testState != ConnectionTestState.Running,
                        modifier = Modifier.testTag("onlineTestButton"),
                    ) { Text(stringResource(R.string.online_test)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { actions.save(mode, key, url, token) }, modifier = Modifier.testTag("onlineSaveButton")) {
                        Text(stringResource(R.string.action_save))
                    }
                }
                val result = when (testState) {
                    ConnectionTestState.Idle -> null
                    ConnectionTestState.Running -> stringResource(R.string.online_test_running)
                    ConnectionTestState.Invalid -> stringResource(R.string.online_test_invalid)
                    is ConnectionTestState.Success -> stringResource(R.string.online_test_ok, testState.totalCount)
                    is ConnectionTestState.Failed -> placeErrorText(testState.error)
                }
                if (result != null) {
                    Text(
                        result,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (testState is ConnectionTestState.Success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = fieldWidth.testTag("onlineTestResult"),
                    )
                }
                Text(
                    stringResource(R.string.settings_online_status_label, onlineConnectionStatus(settings)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = fieldWidth.testTag("onlineSavedStatus"),
                )

                SectionHeader(stringResource(R.string.online_guide_title), modifier = Modifier.widthIn(max = 640.dp))
                Text(
                    stringResource(R.string.online_guide_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = fieldWidth.testTag("onlineGuide"),
                )
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
