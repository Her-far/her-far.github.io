package io.github.herfar.barodial.testing

import io.github.herfar.barodial.core.phone.KoreanPhoneNumbers
import io.github.herfar.barodial.data.contacts.Contact
import io.github.herfar.barodial.data.contacts.PhoneNumber

/**
 * 테스트 전용 연락처 데이터. 앱 본체에는 포함되지 않으며,
 * 앱은 실제 기기의 Contacts Provider 만 읽는다.
 */
object TestContacts {

    fun phone(number: String, type: Int = PhoneNumber.TYPE_MOBILE, isDefault: Boolean = false, label: String? = null) =
        PhoneNumber(number, KoreanPhoneNumbers.toNationalDigits(number), type, label, isDefault)

    fun contact(
        id: Long,
        name: String,
        vararg phones: PhoneNumber,
        organization: String? = null,
        nickname: String? = null,
        starred: Boolean = false,
    ) = Contact(
        id = id,
        lookupKey = "lookup-$id",
        displayName = name,
        photoThumbnailUri = null,
        photoUri = null,
        starred = starred,
        phones = phones.toList(),
        organization = organization,
        nicknames = listOfNotNull(nickname),
    )

    val KIM_CHEOLSU = contact(1, "김철수", phone("010-1234-5678"), organization = "바로상사")
    val KIM_CHEOLMIN = contact(2, "김철민", phone("010-9876-5432"))
    val LEE_CHEOLSU = contact(3, "이철수", phone("02-555-1234", PhoneNumber.TYPE_HOME))
    val KEPCO = contact(4, "고객센터", phone("123", PhoneNumber.TYPE_WORK), organization = "한국전력공사")
    val PARK_YOUNGHEE = contact(
        5,
        "박영희",
        phone("+82 10-2222-3333"),
        phone("031-777-1111", PhoneNumber.TYPE_WORK),
        nickname = "영희쌤",
        starred = true,
    )
    val ALICE = contact(6, "Alice Kim", phone("010-4444-5555"))
    val CHEORI = contact(7, "처리반장", phone("031-777-8888", PhoneNumber.TYPE_WORK))
    val DAKGALBI = contact(8, "닭갈비집", phone("033-111-2222", PhoneNumber.TYPE_WORK))
    val WANG = contact(9, "왕서방", phone("010-5555-6666"))
    val MULTI = contact(
        10,
        "홍길동",
        phone("010-7777-8888"),
        phone("02-333-4444", PhoneNumber.TYPE_WORK),
    )

    val ALL = listOf(KIM_CHEOLSU, KIM_CHEOLMIN, LEE_CHEOLSU, KEPCO, PARK_YOUNGHEE, ALICE, CHEORI, DAKGALBI, WANG, MULTI)
}
