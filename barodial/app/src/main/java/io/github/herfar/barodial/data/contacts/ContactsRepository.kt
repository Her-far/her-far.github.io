package io.github.herfar.barodial.data.contacts

import io.github.herfar.barodial.core.text.KoreanSort
import io.github.herfar.barodial.data.search.ContactSearchEngine
import io.github.herfar.barodial.data.search.SearchIndex
import kotlinx.coroutines.flow.StateFlow

/** 가나다 색인 구간 하나 (빠른 스크롤용). */
data class ContactSection(val label: String, val contacts: List<Contact>)

sealed interface ContactsState {
    /** 처음 불러오는 중 */
    data object Loading : ContactsState

    /** READ_CONTACTS 권한 없음. 키패드·번호 검색은 계속 쓸 수 있다. */
    data object PermissionRequired : ContactsState

    data class Error(val message: String) : ContactsState

    class Ready(
        /** 전화번호가 있는 연락처, 가나다순 */
        val contacts: List<Contact>,
        val index: SearchIndex,
        val sections: List<ContactSection>,
        private val byLookup: Map<String, Contact>,
        private val byId: Map<Long, Contact>,
        private val byNumber: Map<String, Contact>,
    ) : ContactsState {

        /** lookupKey 가 바뀌었을 수 있으므로 id 로도 찾는다. */
        fun find(lookupKey: String?, contactId: Long?): Contact? =
            lookupKey?.let { byLookup[it] } ?: contactId?.let { byId[it] }

        fun findByNumber(nationalDigits: String): Contact? = byNumber[nationalDigits]

        companion object {
            fun build(sortedContacts: List<Contact>, engine: ContactSearchEngine): Ready {
                val sections = sortedContacts
                    .groupBy { KoreanSort.indexLabel(it.displayName) }
                    .map { (label, list) -> ContactSection(label, list) }
                    .sortedBy { KoreanSort.labelOrder(it.label) }
                val byNumber = HashMap<String, Contact>()
                for (c in sortedContacts) {
                    for (p in c.phones) if (p.nationalDigits.isNotEmpty()) byNumber.putIfAbsent(p.nationalDigits, c)
                }
                return Ready(
                    contacts = sortedContacts,
                    index = engine.buildIndex(sortedContacts),
                    sections = sections,
                    byLookup = sortedContacts.associateBy { it.lookupKey },
                    byId = sortedContacts.associateBy { it.id },
                    byNumber = byNumber,
                )
            }

            fun sortContacts(contacts: List<Contact>): List<Contact> {
                val cmp = KoreanSort.nameComparator()
                return contacts.sortedWith { a, b -> cmp.compare(a.displayName, b.displayName) }
            }
        }
    }
}

/**
 * 연락처 데이터 공급원. 연락처는 읽기 전용으로만 다루며,
 * 수정·추가는 시스템 연락처 앱에 위임한다 (WRITE_CONTACTS 불필요).
 */
interface ContactsRepository {
    val state: StateFlow<ContactsState>

    /** 권한 상태가 바뀌었거나 아직 불러오지 않았을 때만 다시 읽는다 (화면 복귀 시 호출). */
    fun refreshIfNeeded()

    /** 무조건 다시 읽는다. */
    fun reload()
}
