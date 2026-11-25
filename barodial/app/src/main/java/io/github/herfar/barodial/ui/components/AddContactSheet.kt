package io.github.herfar.barodial.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.ui.icons.AppIcons

/**
 * '연락처에 추가' 하단 시트: 새 연락처 만들기 / 기존 연락처에 추가.
 * 실제 저장은 시스템 연락처 앱이 한다 (이 앱은 연락처 DB 에 쓰지 않는다).
 * 펼친 화면에서는 Material 3 기본값대로 가운데에 최대 640dp 너비로 뜬다.
 */
@Composable
fun AddContactSheet(
    number: String,
    onCreateNew: () -> Unit,
    onAddToExisting: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = Modifier.testTag("addContactSheet")) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.add_contact_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp),
            )
            Text(
                text = PhoneDisplay.format(number),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 8.dp).testTag("addContactNumber"),
            )
            ListRow(
                title = stringResource(R.string.add_contact_new).asAnnotated(),
                subtitle = stringResource(R.string.add_contact_new_desc).asAnnotated(),
                onClick = onCreateNew,
                leading = { CircleIcon(AppIcons.PersonAdd) },
                modifier = Modifier.testTag("addContactNew"),
            )
            ListRow(
                title = stringResource(R.string.add_contact_existing).asAnnotated(),
                subtitle = stringResource(R.string.add_contact_existing_desc).asAnnotated(),
                onClick = onAddToExisting,
                leading = { CircleIcon(AppIcons.People) },
                modifier = Modifier.testTag("addContactExisting"),
            )
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
