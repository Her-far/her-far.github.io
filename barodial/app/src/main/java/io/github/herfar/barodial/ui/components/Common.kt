package io.github.herfar.barodial.ui.components

import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.core.phone.KoreanPhoneNumbers
import io.github.herfar.barodial.data.contacts.PhoneNumber
import io.github.herfar.barodial.ui.icons.AppIcons
import io.github.herfar.barodial.ui.theme.LocalCallColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ------------------------------------------------------------------ 번호 표시

object PhoneDisplay {
    /** 화면 표시용 번호. +82 로 저장된 번호는 국내 형식(010-...)으로 보여 준다. */
    fun format(phone: PhoneNumber): String = format(phone.number, phone.nationalDigits)

    fun format(number: String, nationalDigits: String = KoreanPhoneNumbers.toNationalDigits(number)): String {
        val trimmed = number.trim()
        return if ((trimmed.startsWith("+82") || trimmed.startsWith("0082")) && nationalDigits.isNotEmpty()) {
            KoreanPhoneNumbers.format(nationalDigits)
        } else {
            KoreanPhoneNumbers.format(trimmed)
        }
    }

    /** 국내 숫자 구간을 형식화된 문자열 구간으로 바꿔 강조한다. */
    fun highlighted(formatted: String, digitRange: IntRange?, style: SpanStyle): AnnotatedString {
        val range = digitRange?.let { KoreanPhoneNumbers.mapDigitRange(formatted, it.first, it.last + 1) }
        return highlight(formatted, range, style)
    }
}

@Composable
fun phoneTypeLabel(phone: PhoneNumber): String {
    val resources = LocalResources.current
    return ContactsContract.CommonDataKinds.Phone.getTypeLabel(resources, phone.type, phone.label).toString()
}

fun highlight(text: String, range: IntRange?, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    if (range != null && range.first >= 0 && range.last < text.length && range.first <= range.last) {
        addStyle(style, range.first, range.last + 1)
    }
}

@Composable
fun highlightStyle(): SpanStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

// ------------------------------------------------------------------ 시간 표시

private val TIME_FORMAT = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
private val MONTH_DAY_FORMAT = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
private val FULL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy. M. d.", Locale.KOREAN)
private val DAY_HEADER_FORMAT = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

object TimeDisplay {
    fun relative(millis: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val dt = Instant.ofEpochMilli(millis).atZone(zone)
        val date = dt.toLocalDate()
        return when {
            date == today -> TIME_FORMAT.format(dt)
            date == today.minusDays(1) -> "어제"
            date.year == today.year -> MONTH_DAY_FORMAT.format(dt)
            else -> FULL_DATE_FORMAT.format(dt)
        }
    }

    fun time(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        TIME_FORMAT.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** 오늘이면 "오후 3:12", 아니면 "어제 오후 3:12" / "11월 20일 오후 3:12" */
    fun dateTime(millis: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return if (date == today) time(millis, zone) else relative(millis, today, zone) + " " + time(millis, zone)
    }

    fun dayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> if (date.year == today.year) DAY_HEADER_FORMAT.format(date) else FULL_DATE_FORMAT.format(date)
    }

    fun duration(seconds: Long): String = when {
        seconds <= 0 -> "0초"
        seconds < 60 -> "${seconds}초"
        seconds < 3600 -> if (seconds % 60 == 0L) "${seconds / 60}분" else "${seconds / 60}분 ${seconds % 60}초"
        else -> "${seconds / 3600}시간 ${(seconds % 3600) / 60}분"
    }
}

// ------------------------------------------------------------------ 공통 UI 조각

/** 화면 상단 제목줄. 상태바 영역만큼 자동으로 띄운다. */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: ImageVector? = null,
    navigationDescription: String? = null,
    onNavigate: () -> Unit = {},
    applyStatusBarInsets: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (applyStatusBarInsets) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
            .heightIn(min = 64.dp)
            .padding(start = if (navigationIcon != null) 4.dp else 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIcon != null) {
            IconButton(onClick = onNavigate, modifier = Modifier.testTag("topBarNavigation")) {
                Icon(navigationIcon, contentDescription = navigationDescription)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f).semantics { heading() }) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/** 연락처/번호 한 줄 */
@Composable
fun ListRow(
    title: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: AnnotatedString? = null,
    onLongClick: (() -> Unit)? = null,
    leading: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 68.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null && subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

fun String.asAnnotated(): AnnotatedString = AnnotatedString(this)

/** 원형 아이콘 (번호·기록 항목의 선행 아이콘) */
@Composable
fun CircleIcon(icon: ImageVector, size: Dp = 44.dp, tint: Color = MaterialTheme.colorScheme.onSecondaryContainer, background: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** 초록 통화 버튼 (48dp 터치 영역) */
@Composable
fun CallIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalCallColors.current
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = colors.call.copy(alpha = 0.14f),
            contentColor = colors.call,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
        ),
    ) {
        Icon(AppIcons.Call, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 안내 카드 (권한 요청, 기능 제약 설명 등) */
@Composable
fun InfoCard(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}

@Composable
fun ContactsPermissionCard(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    InfoCard(
        title = stringResource(R.string.permission_contacts_title),
        body = stringResource(R.string.permission_contacts_body),
        icon = AppIcons.Lock,
        modifier = modifier.testTag("contactsPermissionCard"),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            androidx.compose.material3.Button(onClick = onAllow, modifier = Modifier.testTag("allowContactsButton")) {
                Text(stringResource(R.string.permission_contacts_allow))
            }
        }
    }
}

@Composable
fun EmptyMessage(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(PaddingValues(horizontal = 32.dp, vertical = 32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
