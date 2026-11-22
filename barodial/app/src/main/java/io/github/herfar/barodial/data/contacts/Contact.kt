package io.github.herfar.barodial.data.contacts

/**
 * 연락처에 저장된 전화번호 하나.
 *
 * @param type ContactsContract.CommonDataKinds.Phone.TYPE_* 값
 * @param isDefault 연락처 앱에서 "기본 번호"로 지정됨 (IS_SUPER_PRIMARY)
 */
data class PhoneNumber(
    val number: String,
    val nationalDigits: String,
    val type: Int,
    val label: String?,
    val isDefault: Boolean,
) {
    companion object {
        const val TYPE_CUSTOM = 0
        const val TYPE_HOME = 1
        const val TYPE_MOBILE = 2
        const val TYPE_WORK = 3
        const val TYPE_OTHER = 7
        const val TYPE_MAIN = 12
        const val TYPE_WORK_MOBILE = 17
    }
}

/** Android Contacts Provider 의 집계 연락처(Contacts) 하나. */
data class Contact(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val photoThumbnailUri: String?,
    val photoUri: String?,
    val starred: Boolean,
    val phones: List<PhoneNumber>,
    val organization: String? = null,
    val jobTitle: String? = null,
    val nicknames: List<String> = emptyList(),
) {
    /** 목록에 표시하고 바로 걸 대표 번호: 기본 번호 → 휴대전화 → 첫 번호 */
    val primaryPhone: PhoneNumber?
        get() = phones.firstOrNull { it.isDefault }
            ?: phones.firstOrNull { it.type == PhoneNumber.TYPE_MOBILE }
            ?: phones.firstOrNull()

    fun phoneByDigits(nationalDigits: String): PhoneNumber? =
        phones.firstOrNull { it.nationalDigits == nationalDigits }
}
