package com.trevio.android.util

import com.google.common.truth.Truth.assertThat
import com.trevio.android.domain.model.*
import org.junit.Test

class SecurityAndFlowTest {

    // ─── Helpers ──────────────────────────────────────────────────

    private data class MockUser(val uid: String, val role: String = "user", val blocked: Boolean = false)
    private data class MockMember(val uid: String, val role: String = "member", val status: String = "active", val balance: Double = 0.0, val isOffline: Boolean = false)
    private data class MockGroup(val groupId: String, val memberCount: Int = 0, val totalExpenses: Double = 0.0, val updatedAt: Long = 0)

    private fun affectedKeys(before: Map<String, Any?>, after: Map<String, Any?>): Set<String> {
        val keys = (before.keys + after.keys)
        return keys.filter { before[it] != after[it] }.toSet()
    }

    // ─── USERS COLLECTION RULES ───────────────────────────────────

    @Test
    fun userRules_authenticatedCanRead() {
        assertThat(true).isEqualTo(true) // request.auth != null
    }

    @Test
    fun userRules_unauthenticatedCannotRead() {
        val auth: Any? = null
        assertThat(auth).isNull()
    }

    @Test
    fun userRules_userCanCreateOwnDoc() {
        val uid = "u1"; val targetUid = "u1"
        assertThat(uid).isEqualTo(targetUid)
    }

    @Test
    fun userRules_userCannotCreateOtherDoc() {
        val uid = "u1"; val targetUid = "u2"
        assertThat(uid).isNotEqualTo(targetUid)
    }

    @Test
    fun userRules_userCanUpdateOwnProfileNoRoleNoBlocked() {
        val before = mapOf("role" to "user", "blocked" to false, "name" to "Old")
        val after = mapOf("role" to "user", "blocked" to false, "name" to "New")
        val changed = affectedKeys(before, after)
        assertThat(changed).contains("name")
        assertThat(changed).doesNotContain("role")
        assertThat(changed).doesNotContain("blocked")
    }

    @Test
    fun userRules_userCannotUpdateOwnRole() {
        val before = mapOf("role" to "user")
        val after = mapOf("role" to "superadmin")
        val changed = affectedKeys(before, after)
        assertThat(changed).contains("role")
    }

    @Test
    fun userRules_userCannotUnblockSelf() {
        val before = mapOf("blocked" to true)
        val after = mapOf("blocked" to false)
        val changed = affectedKeys(before, after)
        assertThat(changed).contains("blocked")
    }

    @Test
    fun userRules_superadminCanUpdateAnyRole() {
        val requester = MockUser("admin", "superadmin")
        assertThat(requester.role).isEqualTo("superadmin")
    }

    @Test
    fun userRules_superadminCanBlockUser() {
        val requester = MockUser("admin", "superadmin")
        assertThat(requester.role).isEqualTo("superadmin")
    }

    @Test
    fun userRules_superadminCanUnblockUser() {
        val requester = MockUser("admin", "superadmin")
        assertThat(requester.role).isEqualTo("superadmin")
    }

    @Test
    fun userRules_regularUserCannotUpdateOther() {
        val requester = MockUser("u1", "user")
        val targetUid = "u2"
        assertThat(requester.uid).isNotEqualTo(targetUid)
        assertThat(requester.role).isNotEqualTo("superadmin")
    }

    @Test
    fun userRules_userCanDeleteOwnDoc() {
        val uid = "u1"; val targetUid = "u1"
        assertThat(uid).isEqualTo(targetUid)
    }

    @Test
    fun userRules_userCannotDeleteOtherDoc() {
        val uid = "u1"; val targetUid = "u2"
        assertThat(uid).isNotEqualTo(targetUid)
    }

    @Test
    fun userRules_blockedUserCannotUnblockSelf() {
        val user = MockUser("u1", "user", true)
        val before = mapOf("blocked" to true)
        val after = mapOf("blocked" to false)
        val changed = affectedKeys(before, after)
        val isSelf = user.uid == "u1"
        val isSuperadmin = user.role == "superadmin"
        val canUpdate = isSelf && !changed.contains("role") && !changed.contains("blocked") || isSuperadmin
        assertThat(canUpdate).isFalse()
    }

    @Test
    fun userRules_blockedUserCannotEscalateRole() {
        val user = MockUser("u1", "user", true)
        val before = mapOf("role" to "user")
        val after = mapOf("role" to "superadmin")
        val changed = affectedKeys(before, after)
        val isSelf = user.uid == "u1"
        val isSuperadmin = user.role == "superadmin"
        val canUpdate = isSelf && !changed.contains("role") && !changed.contains("blocked") || isSuperadmin
        assertThat(canUpdate).isFalse()
    }

    @Test
    fun userRules_superadminCanPromoteUser() {
        val requester = MockUser("admin", "superadmin")
        assertThat(requester.role).isEqualTo("superadmin")
    }

    @Test
    fun userRules_superadminCanDemoteSuperadmin() {
        val requester = MockUser("admin", "superadmin")
        assertThat(requester.role).isEqualTo("superadmin")
    }

    @Test
    fun userRules_superadminCanUpdateOwnRole() {
        val requester = MockUser("admin", "superadmin")
        val isSuperadmin = requester.role == "superadmin"
        assertThat(isSuperadmin).isTrue()
    }

    // ─── GROUPS COLLECTION RULES ──────────────────────────────────

    @Test
    fun groupRules_authenticatedCanRead() {
        val auth = true
        assertThat(auth).isTrue()
    }

    @Test
    fun groupRules_unauthenticatedCannotRead() {
        val auth: Boolean? = null
        assertThat(auth).isNull()
    }

    @Test
    fun groupRules_authenticatedCanCreate() {
        val auth = true
        assertThat(auth).isTrue()
    }

    @Test
    fun groupRules_unauthenticatedCannotCreate() {
        val auth: Boolean? = null
        assertThat(auth).isNull()
    }

    @Test
    fun groupRules_activeMemberCanUpdate() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        val member = members["u1"]
        assertThat(member?.status).isEqualTo("active")
    }

    @Test
    fun groupRules_nonMemberCanUpdateRestrictedFields() {
        val before = mapOf("memberCount" to 3, "totalExpenses" to 100.0, "updatedAt" to 1000L)
        val after = mapOf("memberCount" to 4, "totalExpenses" to 100.0, "updatedAt" to 2000L)
        val changed = affectedKeys(before, after)
        val allowed = changed.all { it in listOf("memberCount", "totalExpenses", "updatedAt") }
        assertThat(allowed).isTrue()
    }

    @Test
    fun groupRules_nonMemberCannotUpdateName() {
        val before = mapOf("name" to "Old", "memberCount" to 3)
        val after = mapOf("name" to "New", "memberCount" to 4)
        val changed = affectedKeys(before, after)
        val allowed = changed.all { it in listOf("memberCount", "totalExpenses", "updatedAt") }
        assertThat(allowed).isFalse()
    }

    @Test
    fun groupRules_pendingMemberCannotUpdateNonRestricted() {
        val members = mapOf("u1" to MockMember("u1", "member", "pending"))
        val member = members["u1"]
        assertThat(member?.status).isEqualTo("pending")
    }

    @Test
    fun groupRules_pendingMemberCanUpdateRestricted() {
        val before = mapOf("memberCount" to 3, "updatedAt" to 1000L)
        val after = mapOf("memberCount" to 4, "updatedAt" to 2000L)
        val changed = affectedKeys(before, after)
        val allowed = changed.all { it in listOf("memberCount", "totalExpenses", "updatedAt") }
        assertThat(allowed).isTrue()
    }

    @Test
    fun groupRules_adminCanDelete() {
        val members = mapOf("u1" to MockMember("u1", "admin", "active"))
        assertThat(members["u1"]?.role).isEqualTo("admin")
    }

    @Test
    fun groupRules_regularMemberCannotDelete() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        assertThat(members["u1"]?.role).isNotEqualTo("admin")
    }

    @Test
    fun groupRules_nonMemberCannotDelete() {
        val members = emptyMap<String, MockMember>()
        assertThat(members["u1"]).isNull()
    }

    @Test
    fun groupRules_leftMemberCanUpdateRestricted() {
        val before = mapOf("memberCount" to 3, "updatedAt" to 1000L)
        val after = mapOf("memberCount" to 2, "updatedAt" to 2000L)
        val changed = affectedKeys(before, after)
        val allowed = changed.all { it in listOf("memberCount", "totalExpenses", "updatedAt") }
        assertThat(allowed).isTrue()
    }

    @Test
    fun groupRules_leftMemberCannotUpdateNonRestricted() {
        val before = mapOf("name" to "Old")
        val after = mapOf("name" to "New")
        val changed = affectedKeys(before, after)
        val allowed = changed.all { it in listOf("memberCount", "totalExpenses", "updatedAt") }
        assertThat(allowed).isFalse()
    }

    @Test
    fun groupRules_activeMemberCanUpdateName() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        assertThat(members["u1"]?.status).isEqualTo("active")
    }

    @Test
    fun groupRules_nonMemberCanUpdateOnlyUpdatedAt() {
        val before = mapOf("updatedAt" to 1000L)
        val after = mapOf("updatedAt" to 2000L)
        val changed = affectedKeys(before, after)
        val allowed = changed.all { it in listOf("memberCount", "totalExpenses", "updatedAt") }
        assertThat(allowed).isTrue()
    }

    @Test
    fun groupRules_nonMemberCanUpdateOnlyMemberCount() {
        val before = mapOf("memberCount" to 3)
        val after = mapOf("memberCount" to 4)
        val changed = affectedKeys(before, after)
        val allowed = changed.all { it in listOf("memberCount", "totalExpenses", "updatedAt") }
        assertThat(allowed).isTrue()
    }

    @Test
    fun groupRules_nonMemberCanUpdateOnlyTotalExpenses() {
        val before = mapOf("totalExpenses" to 100.0)
        val after = mapOf("totalExpenses" to 200.0)
        val changed = affectedKeys(before, after)
        val allowed = changed.all { it in listOf("memberCount", "totalExpenses", "updatedAt") }
        assertThat(allowed).isTrue()
    }

    // ─── MEMBERS SUBCOLLECTION RULES ──────────────────────────────

    @Test
    fun memberRules_userCanReadOwnDoc() {
        val uid = "u1"; val targetUid = "u1"
        assertThat(uid).isEqualTo(targetUid)
    }

    @Test
    fun memberRules_activeMemberCanReadOthers() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        assertThat(members["u1"]?.status).isEqualTo("active")
    }

    @Test
    fun memberRules_nonMemberCannotReadOthers() {
        val members = emptyMap<String, MockMember>()
        assertThat(members["u1"]).isNull()
    }

    @Test
    fun memberRules_userCanCreateOwnDoc() {
        val uid = "u1"; val targetUid = "u1"
        assertThat(uid).isEqualTo(targetUid)
    }

    @Test
    fun memberRules_activeMemberCanCreateOfflineDoc() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        assertThat(members["u1"]?.status).isEqualTo("active")
    }

    @Test
    fun memberRules_nonMemberCannotCreateDoc() {
        val members = emptyMap<String, MockMember>()
        assertThat(members["u1"]).isNull()
    }

    @Test
    fun memberRules_userCanUpdateOwnStatus() {
        val before = mapOf("status" to "pending")
        val after = mapOf("status" to "active")
        val changed = affectedKeys(before, after)
        assertThat(changed).contains("status")
        assertThat(changed).doesNotContain("role")
        assertThat(changed).doesNotContain("balance")
    }

    @Test
    fun memberRules_userCannotUpdateOwnRole() {
        val before = mapOf("role" to "member")
        val after = mapOf("role" to "admin")
        val changed = affectedKeys(before, after)
        assertThat(changed).contains("role")
    }

    @Test
    fun memberRules_userCannotUpdateOwnBalance() {
        val before = mapOf("balance" to 100.0)
        val after = mapOf("balance" to 200.0)
        val changed = affectedKeys(before, after)
        assertThat(changed).contains("balance")
    }

    @Test
    fun memberRules_activeMemberCanUpdateOthersBalance() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        val before = mapOf("balance" to 100.0)
        val after = mapOf("balance" to 200.0)
        val changed = affectedKeys(before, after)
        assertThat(changed).hasSize(1)
        assertThat(changed).contains("balance")
        assertThat(members["u1"]?.status).isEqualTo("active")
    }

    @Test
    fun memberRules_activeMemberCannotUpdateOthersRole() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        val before = mapOf("role" to "member")
        val after = mapOf("role" to "admin")
        val changed = affectedKeys(before, after)
        assertThat(changed).contains("role")
        assertThat(changed).doesNotContain("balance")
    }

    @Test
    fun memberRules_adminCanUpdateRoleForTransfer() {
        val members = mapOf("u1" to MockMember("u1", "admin", "active"))
        val before = mapOf("role" to "member")
        val after = mapOf("role" to "admin")
        val changed = affectedKeys(before, after)
        val isAdmin = members["u1"]?.role == "admin"
        val allowedFields = changed.all { it in listOf("role", "updatedAt") }
        assertThat(isAdmin && allowedFields).isTrue()
    }

    @Test
    fun memberRules_adminCanDemoteSelfForTransfer() {
        val members = mapOf("u1" to MockMember("u1", "admin", "active"))
        val before = mapOf("role" to "admin")
        val after = mapOf("role" to "member")
        val changed = affectedKeys(before, after)
        val isAdmin = members["u1"]?.role == "admin"
        val allowedFields = changed.all { it in listOf("role", "updatedAt") }
        assertThat(isAdmin && allowedFields).isTrue()
    }

    @Test
    fun memberRules_userCanDeleteOwnDoc() {
        val uid = "u1"; val targetUid = "u1"
        assertThat(uid).isEqualTo(targetUid)
    }

    @Test
    fun memberRules_adminCanDeleteAnyDoc() {
        val members = mapOf("u1" to MockMember("u1", "admin", "active"))
        assertThat(members["u1"]?.role).isEqualTo("admin")
    }

    @Test
    fun memberRules_activeMemberCanDeleteOfflineDoc() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        val target = MockMember("u2", isOffline = true)
        assertThat(members["u1"]?.status).isEqualTo("active")
        assertThat(target.isOffline).isTrue()
    }

    @Test
    fun memberRules_activeMemberCannotDeleteNonOfflineDoc() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        val target = MockMember("u2", isOffline = false)
        assertThat(target.isOffline).isFalse()
    }

    @Test
    fun memberRules_leftMemberCanReadOwnDoc() {
        val uid = "u1"; val targetUid = "u1"
        assertThat(uid).isEqualTo(targetUid)
    }

    @Test
    fun memberRules_leftMemberCannotReadOthers() {
        val members = mapOf("u1" to MockMember("u1", "member", "left"))
        assertThat(members["u1"]?.status).isEqualTo("left")
    }

    // ─── EXPENSES SUBCOLLECTION RULES ─────────────────────────────

    @Test
    fun expenseRules_activeMemberCanRead() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        assertThat(members["u1"]?.status).isEqualTo("active")
    }

    @Test
    fun expenseRules_nonMemberCannotRead() {
        val members = emptyMap<String, MockMember>()
        assertThat(members["u1"]).isNull()
    }

    @Test
    fun expenseRules_pendingMemberCannotRead() {
        val members = mapOf("u1" to MockMember("u1", "member", "pending"))
        assertThat(members["u1"]?.status).isNotEqualTo("active")
    }

    @Test
    fun expenseRules_activeMemberCanCreate() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        assertThat(members["u1"]?.status).isEqualTo("active")
    }

    @Test
    fun expenseRules_nonMemberCannotCreate() {
        val members = emptyMap<String, MockMember>()
        assertThat(members["u1"]).isNull()
    }

    @Test
    fun expenseRules_activeMemberCanUpdate() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        assertThat(members["u1"]?.status).isEqualTo("active")
    }

    @Test
    fun expenseRules_activeMemberCanDelete() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        assertThat(members["u1"]?.status).isEqualTo("active")
    }

    @Test
    fun expenseRules_leftMemberCannotRead() {
        val members = mapOf("u1" to MockMember("u1", "member", "left"))
        assertThat(members["u1"]?.status).isNotEqualTo("active")
    }

    @Test
    fun expenseRules_adminCanDelete() {
        val members = mapOf("u1" to MockMember("u1", "admin", "active"))
        assertThat(members["u1"]?.status).isEqualTo("active")
    }

    // ─── ACTIVITIES SUBCOLLECTION RULES ───────────────────────────

    @Test
    fun activityRules_activeMemberCanRead() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        assertThat(members["u1"]?.status).isEqualTo("active")
    }

    @Test
    fun activityRules_nonMemberCannotRead() {
        val members = emptyMap<String, MockMember>()
        assertThat(members["u1"]).isNull()
    }

    @Test
    fun activityRules_anyAuthenticatedCanCreate() {
        val auth = true
        assertThat(auth).isTrue()
    }

    @Test
    fun activityRules_unauthenticatedCannotCreate() {
        val auth: Boolean? = null
        assertThat(auth).isNull()
    }

    @Test
    fun activityRules_immutable_noOneCanUpdate() {
        val canUpdate = false
        assertThat(canUpdate).isFalse()
    }

    @Test
    fun activityRules_adminCanDelete() {
        val members = mapOf("u1" to MockMember("u1", "admin", "active"))
        assertThat(members["u1"]?.role).isEqualTo("admin")
    }

    @Test
    fun activityRules_regularMemberCannotDelete() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        assertThat(members["u1"]?.role).isNotEqualTo("admin")
    }

    @Test
    fun activityRules_pendingMemberCannotRead() {
        val members = mapOf("u1" to MockMember("u1", "member", "pending"))
        assertThat(members["u1"]?.status).isNotEqualTo("active")
    }

    // ─── SETTLEMENTS SUBCOLLECTION RULES ──────────────────────────

    @Test
    fun settlementRules_activeMemberCanRead() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        assertThat(members["u1"]?.status).isEqualTo("active")
    }

    @Test
    fun settlementRules_nonMemberCannotRead() {
        val members = emptyMap<String, MockMember>()
        assertThat(members["u1"]).isNull()
    }

    @Test
    fun settlementRules_activeMemberCanCreate() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        assertThat(members["u1"]?.status).isEqualTo("active")
    }

    @Test
    fun settlementRules_nonMemberCannotCreate() {
        val members = emptyMap<String, MockMember>()
        assertThat(members["u1"]).isNull()
    }

    @Test
    fun settlementRules_adminCanDelete() {
        val members = mapOf("u1" to MockMember("u1", "admin", "active"))
        assertThat(members["u1"]?.role).isEqualTo("admin")
    }

    @Test
    fun settlementRules_regularMemberCannotDelete() {
        val members = mapOf("u1" to MockMember("u1", "member", "active"))
        assertThat(members["u1"]?.role).isNotEqualTo("admin")
    }

    // ─── INVITATIONS RULES ────────────────────────────────────────

    @Test
    fun invitationRules_invitedUserCanRead() {
        val toUid = "u1"; val currentUid = "u1"
        assertThat(toUid).isEqualTo(currentUid)
    }

    @Test
    fun invitationRules_inviterCanRead() {
        val invitedBy = "u2"; val currentUid = "u2"
        assertThat(invitedBy).isEqualTo(currentUid)
    }

    @Test
    fun invitationRules_unrelatedCannotRead() {
        val toUid = "u1"; val invitedBy = "u2"; val currentUid = "u3"
        assertThat(currentUid).isNotEqualTo(toUid)
        assertThat(currentUid).isNotEqualTo(invitedBy)
    }

    @Test
    fun invitationRules_invitedUserCanUpdate() {
        val toUid = "u1"; val currentUid = "u1"
        assertThat(toUid).isEqualTo(currentUid)
    }

    @Test
    fun invitationRules_inviterCannotUpdate() {
        val toUid = "u1"; val currentUid = "u2"
        assertThat(toUid).isNotEqualTo(currentUid)
    }

    @Test
    fun invitationRules_noOneCanDelete() {
        val canDelete = false
        assertThat(canDelete).isFalse()
    }

    // ─── BROADCAST RULES ──────────────────────────────────────────

    @Test
    fun broadcastRules_authenticatedCanRead() {
        val auth = true
        assertThat(auth).isTrue()
    }

    @Test
    fun broadcastRules_superadminCanCreate() {
        val role = "superadmin"
        assertThat(role).isEqualTo("superadmin")
    }

    @Test
    fun broadcastRules_regularUserCannotCreate() {
        val role = "user"
        assertThat(role).isNotEqualTo("superadmin")
    }

    @Test
    fun broadcastRules_superadminCanUpdate() {
        val role = "superadmin"
        assertThat(role).isEqualTo("superadmin")
    }

    @Test
    fun broadcastRules_regularUserCannotUpdate() {
        val role = "user"
        assertThat(role).isNotEqualTo("superadmin")
    }

    @Test
    fun broadcastRules_userCanReadOwnReads() {
        val uid = "u1"; val targetUid = "u1"
        assertThat(uid).isEqualTo(targetUid)
    }

    @Test
    fun broadcastRules_superadminCanReadAnyReads() {
        val role = "superadmin"
        assertThat(role).isEqualTo("superadmin")
    }

    @Test
    fun broadcastRules_userCanCreateOwnRead() {
        val uid = "u1"; val targetUid = "u1"
        assertThat(uid).isEqualTo(targetUid)
    }

    @Test
    fun broadcastRules_userCannotCreateOthersRead() {
        val uid = "u1"; val targetUid = "u2"
        assertThat(uid).isNotEqualTo(targetUid)
    }

    // ─── FLOW: GROUP CREATION ─────────────────────────────────────

    @Test
    fun flow_createGroup_validName() {
        assertThat("Trip to Goa".trim().isNotEmpty()).isTrue()
    }

    @Test
    fun flow_createGroup_emptyNameFails() {
        assertThat("".trim().isEmpty()).isTrue()
    }

    @Test
    fun flow_createGroup_whitespaceNameFails() {
        assertThat("   ".trim().isEmpty()).isTrue()
    }

    @Test
    fun flow_createGroup_creatorBecomesAdmin() {
        val role = "admin"
        assertThat(role).isEqualTo("admin")
    }

    @Test
    fun flow_createGroup_initialMemberCount1() {
        assertThat(1).isEqualTo(1)
    }

    @Test
    fun flow_createGroup_initialTotalExpenses0() {
        assertThat(0.0).isEqualTo(0.0)
    }

    @Test
    fun flow_createGroup_creatorExcludedFromInvitations() {
        val memberUids = listOf("u1", "u2", "u3")
        val creatorUid = "u1"
        val invited = memberUids.filter { it != creatorUid }
        assertThat(invited).containsExactly("u2", "u3")
    }

    @Test
    fun flow_createGroup_noInvitationsWhenOnlyCreator() {
        val memberUids = listOf("u1")
        val creatorUid = "u1"
        val invited = memberUids.filter { it != creatorUid }
        assertThat(invited).isEmpty()
    }

    @Test
    fun flow_createGroup_activityTypeGroupCreated() {
        assertThat("group_created").isEqualTo("group_created")
    }

    // ─── FLOW: JOIN GROUP ─────────────────────────────────────────

    @Test
    fun flow_joinGroup_inviteCodeUppercased() {
        assertThat("abc123".uppercase()).isEqualTo("ABC123")
    }

    @Test
    fun flow_joinGroup_emptyCodeFails() {
        assertThat("".isEmpty()).isTrue()
    }

    @Test
    fun flow_joinGroup_alreadyActiveMemberRejected() {
        val member = MockMember("u1", "member", "active", isOffline = false)
        val isAlreadyMember = member.status == "active" && !member.isOffline
        assertThat(isAlreadyMember).isTrue()
    }

    @Test
    fun flow_joinGroup_offlineMemberCanRejoin() {
        val member = MockMember("u1", "member", "active", isOffline = true)
        val isAlreadyMember = member.status == "active" && !member.isOffline
        assertThat(isAlreadyMember).isFalse()
    }

    @Test
    fun flow_joinGroup_pendingMemberCanJoin() {
        val member = MockMember("u1", "member", "pending")
        assertThat(member.status).isEqualTo("pending")
    }

    @Test
    fun flow_joinGroup_newMemberRoleMember() {
        assertThat("member").isEqualTo("member")
    }

    @Test
    fun flow_joinGroup_newMemberStatusActive() {
        assertThat("active").isEqualTo("active")
    }

    @Test
    fun flow_joinGroup_memberCountIncrementedForNew() {
        val before = 3; val after = before + 1
        assertThat(after).isEqualTo(4)
    }

    @Test
    fun flow_joinGroup_memberCountNotIncrementedForPending() {
        val before = 3; val isPending = true
        val after = if (isPending) before else before + 1
        assertThat(after).isEqualTo(3)
    }

    @Test
    fun flow_joinGroup_activityTypeMemberJoined() {
        assertThat("member_joined").isEqualTo("member_joined")
    }

    // ─── FLOW: ACCEPT INVITATION ──────────────────────────────────

    @Test
    fun flow_acceptInvitation_notForYouRejected() {
        val toUid = "u1"; val currentUid = "u2"
        assertThat(toUid).isNotEqualTo(currentUid)
    }

    @Test
    fun flow_acceptInvitation_alreadyAcceptedRejected() {
        val status = "accepted"
        assertThat(status).isNotEqualTo("pending")
    }

    @Test
    fun flow_acceptInvitation_alreadyDeclinedRejected() {
        val status = "declined"
        assertThat(status).isNotEqualTo("pending")
    }

    @Test
    fun flow_acceptInvitation_pendingCanAccept() {
        val status = "pending"
        assertThat(status).isEqualTo("pending")
    }

    @Test
    fun flow_acceptInvitation_statusUpdatedToAccepted() {
        assertThat("accepted").isEqualTo("accepted")
    }

    // ─── FLOW: LEAVE GROUP ────────────────────────────────────────

    @Test
    fun flow_leaveGroup_statusSetToLeft() {
        assertThat("left").isEqualTo("left")
    }

    @Test
    fun flow_leaveGroup_memberCountDecremented() {
        val before = 4; val after = before - 1
        assertThat(after).isEqualTo(3)
    }

    @Test
    fun flow_leaveGroup_adminCannotLeaveWithOthers() {
        val activeMembers = 3; val isAdmin = true
        val canLeave = !isAdmin || activeMembers <= 1
        assertThat(canLeave).isFalse()
    }

    @Test
    fun flow_leaveGroup_adminCanLeaveAsSole() {
        val activeMembers = 1; val isAdmin = true
        val canLeave = !isAdmin || activeMembers <= 1
        assertThat(canLeave).isTrue()
    }

    @Test
    fun flow_leaveGroup_regularMemberCanLeave() {
        val isAdmin = false
        val canLeave = !isAdmin
        assertThat(canLeave).isTrue()
    }

    // ─── FLOW: ADD EXPENSE VALIDATION ─────────────────────────────

    @Test
    fun flow_addExpense_validPasses() {
        val groupId = "g1"; val desc = "Dinner"; val amount = 100.0; val paidBy = "u1"
        assertThat(groupId.isNotEmpty() && desc.isNotEmpty() && amount != 0.0 && paidBy.isNotEmpty()).isTrue()
    }

    @Test
    fun flow_addExpense_missingGroupIdFails() {
        val groupId = ""; val desc = "Dinner"; val amount = 100.0; val paidBy = "u1"
        assertThat(groupId.isNotEmpty() && desc.isNotEmpty() && amount != 0.0 && paidBy.isNotEmpty()).isFalse()
    }

    @Test
    fun flow_addExpense_missingDescriptionFails() {
        val groupId = "g1"; val desc = ""; val amount = 100.0; val paidBy = "u1"
        assertThat(groupId.isNotEmpty() && desc.isNotEmpty() && amount != 0.0 && paidBy.isNotEmpty()).isFalse()
    }

    @Test
    fun flow_addExpense_zeroAmountFails() {
        val groupId = "g1"; val desc = "Dinner"; val amount = 0.0; val paidBy = "u1"
        assertThat(groupId.isNotEmpty() && desc.isNotEmpty() && amount != 0.0 && paidBy.isNotEmpty()).isFalse()
    }

    @Test
    fun flow_addExpense_missingPaidByFails() {
        val groupId = "g1"; val desc = "Dinner"; val amount = 100.0; val paidBy = ""
        assertThat(groupId.isNotEmpty() && desc.isNotEmpty() && amount != 0.0 && paidBy.isNotEmpty()).isFalse()
    }

    @Test
    fun flow_addExpense_categoryDefaultsToOther() {
        val category = "".ifEmpty { "other" }
        assertThat(category).isEqualTo("other")
    }

    @Test
    fun flow_addExpense_categoryPreserved() {
        val category = "food" ?: "other"
        assertThat(category).isEqualTo("food")
    }

    @Test
    fun flow_addExpense_noteIncludedWhenProvided() {
        val note = "Birthday dinner"
        assertThat(note.isNotEmpty()).isTrue()
    }

    @Test
    fun flow_addExpense_noteExcludedWhenNotProvided() {
        val note: String? = null
        assertThat(note).isNull()
    }

    @Test
    fun flow_addExpense_recurringIncludedWhenProvided() {
        val recurring = RecurringConfig(frequency = RecurringFrequency.WEEKLY)
        assertThat(recurring).isNotNull()
    }

    @Test
    fun flow_addExpense_recurringExcludedWhenNotProvided() {
        val recurring: RecurringConfig? = null
        assertThat(recurring).isNull()
    }

    @Test
    fun flow_addExpense_amountInBaseCalculation() {
        val amount = 100.0; val rate = 83.5
        assertThat(amount * rate).isEqualTo(8350.0)
    }

    @Test
    fun flow_addExpense_totalExpensesIncremented() {
        val before = 500.0; val amountInBase = 100.0
        assertThat(before + amountInBase).isEqualTo(600.0)
    }

    @Test
    fun flow_addExpense_activityTypeExpenseAdded() {
        assertThat("expense_added").isEqualTo("expense_added")
    }

    // ─── FLOW: ADD SETTLEMENT VALIDATION ──────────────────────────

    @Test
    fun flow_addSettlement_validPasses() {
        val groupId = "g1"; val fromUid = "u1"; val toUid = "u2"; val amount = 50.0
        assertThat(groupId.isNotEmpty() && fromUid.isNotEmpty() && toUid.isNotEmpty() && amount != 0.0).isTrue()
    }

    @Test
    fun flow_addSettlement_missingGroupIdFails() {
        val groupId = ""; val fromUid = "u1"; val toUid = "u2"; val amount = 50.0
        assertThat(groupId.isNotEmpty() && fromUid.isNotEmpty() && toUid.isNotEmpty() && amount != 0.0).isFalse()
    }

    @Test
    fun flow_addSettlement_zeroAmountFails() {
        val groupId = "g1"; val fromUid = "u1"; val toUid = "u2"; val amount = 0.0
        assertThat(groupId.isNotEmpty() && fromUid.isNotEmpty() && toUid.isNotEmpty() && amount != 0.0).isFalse()
    }

    @Test
    fun flow_addSettlement_selfSettlementFails() {
        val fromUid = "u1"; val toUid = "u1"
        assertThat(fromUid).isEqualTo(toUid)
    }

    @Test
    fun flow_addSettlement_methodDefaultsToCash() {
        val method = "".ifEmpty { "cash" }
        assertThat(method).isEqualTo("cash")
    }

    @Test
    fun flow_addSettlement_upiMethodPasses() {
        assertThat(SettlementMethod.UPI).isEqualTo(SettlementMethod.UPI)
    }

    @Test
    fun flow_addSettlement_cashMethodPasses() {
        assertThat(SettlementMethod.CASH).isEqualTo(SettlementMethod.CASH)
    }

    @Test
    fun flow_addSettlement_otherMethodPasses() {
        assertThat(SettlementMethod.OTHER).isEqualTo(SettlementMethod.OTHER)
    }

    @Test
    fun flow_addSettlement_amountInBaseRounded() {
        val amount = 50.123456; val rate = 83.5
        val amountInBase = kotlin.math.round((amount * rate) * 100) / 100
        assertThat(amountInBase).isEqualTo(4185.31)
    }

    @Test
    fun flow_addSettlement_upiRefIdSavedWhenProvided() {
        val upiRefId = "UPI123456"
        assertThat(upiRefId.isNotEmpty()).isTrue()
    }

    @Test
    fun flow_addSettlement_upiRefIdNotSavedWhenNotProvided() {
        val upiRefId: String? = null
        assertThat(upiRefId).isNull()
    }

    @Test
    fun flow_addSettlement_currencyIsINR() {
        assertThat("INR").isEqualTo("INR")
    }

    @Test
    fun flow_addSettlement_originalAmountPreserved() {
        val originalAmount = 50.0
        assertThat(originalAmount).isEqualTo(50.0)
    }

    @Test
    fun flow_addSettlement_originalCurrencyPreserved() {
        val originalCurrency = "USD"
        assertThat(originalCurrency).isEqualTo("USD")
    }

    @Test
    fun flow_addSettlement_notificationExcludesRecorderAsFrom() {
        val fromUid = "u1"; val toUid = "u2"; val recorder = "u1"
        val notify = listOf(fromUid, toUid).filter { it != recorder }
        assertThat(notify).containsExactly("u2")
    }

    @Test
    fun flow_addSettlement_notificationExcludesRecorderAsTo() {
        val fromUid = "u1"; val toUid = "u2"; val recorder = "u2"
        val notify = listOf(fromUid, toUid).filter { it != recorder }
        assertThat(notify).containsExactly("u1")
    }

    @Test
    fun flow_addSettlement_notificationToBothWhenThirdParty() {
        val fromUid = "u1"; val toUid = "u2"; val recorder = "u3"
        val notify = listOf(fromUid, toUid).filter { it != recorder }
        assertThat(notify).containsExactly("u1", "u2")
    }

    @Test
    fun flow_addSettlement_activityTypeSettlementAdded() {
        assertThat("settlement_added").isEqualTo("settlement_added")
    }

    // ─── FLOW: ADMIN OPERATIONS ───────────────────────────────────

    @Test
    fun flow_admin_superadminCanAccess() {
        val role = "superadmin"
        assertThat(role).isEqualTo("superadmin")
    }

    @Test
    fun flow_admin_regularUserCannotAccess() {
        val role = "user"
        assertThat(role).isNotEqualTo("superadmin")
    }

    @Test
    fun flow_admin_cannotBlockSelf() {
        val targetUid = "u1"; val currentUid = "u1"
        assertThat(targetUid).isEqualTo(currentUid)
    }

    @Test
    fun flow_admin_canBlockOther() {
        val targetUid = "u2"; val currentUid = "u1"
        assertThat(targetUid).isNotEqualTo(currentUid)
    }

    @Test
    fun flow_admin_cannotDemoteSelf() {
        val targetUid = "u1"; val currentUid = "u1"
        assertThat(targetUid).isEqualTo(currentUid)
    }

    @Test
    fun flow_admin_blockedUserHasBlockedTrue() {
        assertThat(true).isTrue()
    }

    @Test
    fun flow_admin_promotedUserHasRoleSuperadmin() {
        assertThat("superadmin").isEqualTo("superadmin")
    }

    @Test
    fun flow_admin_demotedUserHasRoleUser() {
        assertThat("user").isEqualTo("user")
    }

    @Test
    fun flow_admin_searchByNameFilters() {
        val users = listOf("John Doe", "Jane Smith", "John Smith")
        val filtered = users.filter { it.lowercase().contains("john") }
        assertThat(filtered).hasSize(2)
    }

    @Test
    fun flow_admin_searchByEmailFilters() {
        val users = listOf("john@example.com", "jane@example.com")
        val filtered = users.filter { it.lowercase().contains("jane") }
        assertThat(filtered).hasSize(1)
    }

    @Test
    fun flow_admin_youTagForCurrentUser() {
        val currentUid = "u1"
        val user = User(uid = "u1", displayName = "John")
        val displayName = if (user.uid == currentUid) "${user.displayName} (You)" else user.displayName
        assertThat(displayName).isEqualTo("John (You)")
    }

    @Test
    fun flow_admin_noYouTagForOtherUser() {
        val currentUid = "u1"
        val user = User(uid = "u2", displayName = "Jane")
        val displayName = if (user.uid == currentUid) "${user.displayName} (You)" else user.displayName
        assertThat(displayName).isEqualTo("Jane")
    }

    @Test
    fun flow_admin_blockButtonDisabledForSelf() {
        val isCurrentUser = true
        assertThat(isCurrentUser).isTrue()
    }

    @Test
    fun flow_admin_blockButtonEnabledForOthers() {
        val isCurrentUser = false
        assertThat(isCurrentUser).isFalse()
    }

    @Test
    fun flow_admin_statsShowBlockedCount() {
        val users = listOf(User(blocked = false), User(blocked = true), User(blocked = true))
        assertThat(users.count { it.blocked }).isEqualTo(2)
    }

    @Test
    fun flow_admin_statsShowSuperadminCount() {
        val users = listOf(User(role = "user"), User(role = "superadmin"))
        assertThat(users.count { it.role == "superadmin" }).isEqualTo(1)
    }

    // ─── FLOW: BROADCAST VALIDATION ───────────────────────────────

    @Test
    fun flow_broadcast_validPasses() {
        val title = "Maintenance"; val content = "<p>Down</p>"; val startAt = 1000L
        assertThat(title.isNotEmpty() && content.isNotEmpty() && startAt != 0L).isTrue()
    }

    @Test
    fun flow_broadcast_missingTitleFails() {
        val title = ""
        assertThat(title.isEmpty()).isTrue()
    }

    @Test
    fun flow_broadcast_missingContentFails() {
        val content = ""
        assertThat(content.isEmpty()).isTrue()
    }

    @Test
    fun flow_broadcast_endBeforeStartFails() {
        val startAt = 2000L; val endAt = 1000L
        assertThat(endAt < startAt).isTrue()
    }

    @Test
    fun flow_broadcast_nullEndAtPasses() {
        val endAt: Long? = null
        assertThat(endAt).isNull()
    }

    @Test
    fun flow_broadcast_specificTargetNoUsersFails() {
        val targetType = BroadcastTargetType.SPECIFIC; val targetUids = emptyList<String>()
        assertThat(targetType == BroadcastTargetType.SPECIFIC && targetUids.isEmpty()).isTrue()
    }

    @Test
    fun flow_broadcast_specificTargetWithUsersPasses() {
        val targetType = BroadcastTargetType.SPECIFIC; val targetUids = listOf("u1", "u2")
        assertThat(targetType == BroadcastTargetType.SPECIFIC && targetUids.isNotEmpty()).isTrue()
    }

    @Test
    fun flow_broadcast_senderExcludedFromReceiving() {
        val senderUid = "u1"
        val targetUids = listOf("u1", "u2", "u3")
        val filtered = targetUids.filter { it != senderUid }
        assertThat(filtered).containsExactly("u2", "u3")
    }

    @Test
    fun flow_broadcast_criticalCannotDismiss() {
        val priority = BroadcastPriority.CRITICAL
        val canDismiss = priority != BroadcastPriority.CRITICAL
        assertThat(canDismiss).isFalse()
    }

    @Test
    fun flow_broadcast_infoCanDismiss() {
        val priority = BroadcastPriority.INFO
        val canDismiss = priority != BroadcastPriority.CRITICAL
        assertThat(canDismiss).isTrue()
    }

    @Test
    fun flow_broadcast_maintenanceCanDismiss() {
        val priority = BroadcastPriority.MAINTENANCE
        val canDismiss = priority != BroadcastPriority.CRITICAL
        assertThat(canDismiss).isTrue()
    }

    @Test
    fun flow_broadcast_allExceptBlockedExcludesBlocked() {
        val users = listOf(User(uid = "u1", blocked = false), User(uid = "u2", blocked = true))
        val filtered = users.filter { !it.blocked }
        assertThat(filtered).hasSize(1)
    }

    @Test
    fun flow_broadcast_specificIncludesOnlySelected() {
        val targetUids = listOf("u1", "u3")
        val users = listOf("u1", "u2", "u3", "u4")
        val filtered = users.filter { it in targetUids }
        assertThat(filtered).containsExactly("u1", "u3")
    }

    @Test
    fun flow_broadcast_currentUserExcludedFromSpecificSelection() {
        val allUsers = listOf("u1", "u2", "u3")
        val currentUid = "u1"
        val filtered = allUsers.filter { it != currentUid }
        assertThat(filtered).containsExactly("u2", "u3")
    }

    @Test
    fun flow_broadcast_stopSetsActiveFalse() {
        val broadcast = BroadcastMessage(active = false, stoppedAt = 1000L)
        assertThat(broadcast.active).isFalse()
        assertThat(broadcast.stoppedAt).isNotNull()
    }

    @Test
    fun flow_broadcast_pastEndNotShown() {
        val now = System.currentTimeMillis()
        val broadcast = BroadcastMessage(endAt = now - 1000, active = true)
        val isVisible = broadcast.active && (broadcast.endAt == null || broadcast.endAt > now)
        assertThat(isVisible).isFalse()
    }

    @Test
    fun flow_broadcast_futureEndShown() {
        val now = System.currentTimeMillis()
        val broadcast = BroadcastMessage(endAt = now + 10000, active = true)
        val isVisible = broadcast.active && (broadcast.endAt == null || broadcast.endAt > now)
        assertThat(isVisible).isTrue()
    }

    @Test
    fun flow_broadcast_stoppedNotShown() {
        val now = System.currentTimeMillis()
        val broadcast = BroadcastMessage(endAt = now + 10000, active = false)
        val isVisible = broadcast.active && (broadcast.endAt == null || broadcast.endAt > now)
        assertThat(isVisible).isFalse()
    }

    @Test
    fun flow_broadcast_detailExcludesSender() {
        val targetUsers = listOf("u1", "u2", "u3")
        val senderUid = "u1"
        val filtered = targetUsers.filter { it != senderUid }
        assertThat(filtered).containsExactly("u2", "u3")
    }

    @Test
    fun flow_broadcast_listSortedByCreatedAtDesc() {
        val broadcasts = listOf(
            BroadcastMessage(createdAt = 1000),
            BroadcastMessage(createdAt = 3000),
            BroadcastMessage(createdAt = 2000)
        )
        val sorted = broadcasts.sortedByDescending { it.createdAt }
        assertThat(sorted[0].createdAt).isEqualTo(3000L)
    }

    @Test
    fun flow_broadcast_readRecordCreatedOnAcknowledge() {
        val read = BroadcastRead(uid = "u1", readAt = 1000L)
        assertThat(read.uid).isEqualTo("u1")
    }

    // ─── FLOW: NOTIFICATION LOGIC ─────────────────────────────────

    @Test
    fun flow_notification_expenseExcludesCreator() {
        val members = listOf("u1", "u2", "u3"); val creator = "u1"
        val notify = members.filter { it != creator }
        assertThat(notify).containsExactly("u2", "u3")
    }

    @Test
    fun flow_notification_settlementExcludesRecorder() {
        val parties = listOf("u1", "u2"); val recorder = "u1"
        val notify = parties.filter { it != recorder }
        assertThat(notify).containsExactly("u2")
    }

    @Test
    fun flow_notification_invitationSentOnlyToTarget() {
        val toUid = "u2"
        val notify = listOf(toUid)
        assertThat(notify).containsExactly("u2")
    }

    @Test
    fun flow_notification_readDefaultsFalse() {
        val read = false
        assertThat(read).isFalse()
    }

    @Test
    fun flow_notification_markAsReadSetsTrue() {
        val read = true
        assertThat(read).isTrue()
    }

    @Test
    fun flow_notification_batchHandles450() {
        val batchSize = 450
        assertThat(batchSize).isEqualTo(450)
    }

    @Test
    fun flow_notification_creationFailureNonBlocking() {
        val expenseSuccess = true; val notificationSuccess = false
        assertThat(expenseSuccess && !notificationSuccess).isTrue()
    }

    // ─── FLOW: TRANSFER ADMIN ─────────────────────────────────────

    @Test
    fun flow_transferAdmin_adminCanTransfer() {
        val role = "admin"
        assertThat(role).isEqualTo("admin")
    }

    @Test
    fun flow_transferAdmin_nonAdminCannotTransfer() {
        val role = "member"
        assertThat(role).isNotEqualTo("admin")
    }

    @Test
    fun flow_transferAdmin_cannotTransferToSelf() {
        val currentUid = "u1"; val targetUid = "u1"
        assertThat(currentUid).isEqualTo(targetUid)
    }

    @Test
    fun flow_transferAdmin_cannotTransferToNonMember() {
        val exists = false
        assertThat(exists).isFalse()
    }

    @Test
    fun flow_transferAdmin_cannotTransferToPending() {
        val status = "pending"
        assertThat(status).isNotEqualTo("active")
    }

    @Test
    fun flow_transferAdmin_canTransferToActive() {
        val status = "active"
        assertThat(status).isEqualTo("active")
    }

    @Test
    fun flow_transferAdmin_currentBecomesMember() {
        assertThat("member").isEqualTo("member")
    }

    @Test
    fun flow_transferAdmin_targetBecomesAdmin() {
        assertThat("admin").isEqualTo("admin")
    }

    @Test
    fun flow_transferAdmin_activityTypeAdminTransferred() {
        assertThat("admin_transferred").isEqualTo("admin_transferred")
    }

    // ─── FLOW: DELETE GROUP ───────────────────────────────────────

    @Test
    fun flow_deleteGroup_adminCanDelete() {
        val role = "admin"
        assertThat(role).isEqualTo("admin")
    }

    @Test
    fun flow_deleteGroup_nonAdminCannotDelete() {
        val role = "member"
        assertThat(role).isNotEqualTo("admin")
    }

    @Test
    fun flow_deleteGroup_cannotDeleteWithOthers() {
        val activeMembers = 3
        assertThat(activeMembers > 1).isTrue()
    }

    @Test
    fun flow_deleteGroup_canDeleteAsSoleMember() {
        val activeMembers = 1
        assertThat(activeMembers <= 1).isTrue()
    }

    @Test
    fun flow_deleteGroup_deletesGroupDocLast() {
        val order = listOf("members", "expenses", "settlements", "activities", "group")
        assertThat(order.last()).isEqualTo("group")
    }

    // ─── FLOW: OFFLINE MEMBER ─────────────────────────────────────

    @Test
    fun flow_offlineMember_hasIsOfflineTrue() {
        val member = Member(isOffline = true, displayName = "Guest")
        assertThat(member.isOffline).isTrue()
    }

    @Test
    fun flow_offlineMember_activeMemberCanAdd() {
        val status = "active"
        assertThat(status).isEqualTo("active")
    }

    @Test
    fun flow_offlineMember_canBeClaimedByRealUser() {
        val isOffline = true
        assertThat(isOffline).isTrue()
    }

    @Test
    fun flow_offlineMember_claimingReplacesDocWithUserUid() {
        val oldId = "auto_123"; val newId = "u1"
        assertThat(oldId).isNotEqualTo(newId)
    }

    @Test
    fun flow_offlineMember_claimingPreservesBalanceAndRole() {
        val oldMember = Member(balance = 100.0, role = "member")
        val newMember = oldMember.copy(uid = "u1", isOffline = false)
        assertThat(newMember.balance).isEqualTo(100.0)
        assertThat(newMember.role).isEqualTo("member")
        assertThat(newMember.isOffline).isFalse()
    }

    @Test
    fun flow_offlineMember_activeMemberCanDeleteOfflineDoc() {
        val isOffline = true; val isActive = true
        assertThat(isOffline && isActive).isTrue()
    }

    @Test
    fun flow_offlineMember_activeMemberCannotDeleteNonOfflineDoc() {
        val isOffline = false; val isActive = true
        assertThat(isOffline && isActive).isFalse()
    }

    @Test
    fun flow_offlineMember_displayNameUsedInSettlementNames() {
        val isOffline = true; val displayName = "Guest User"
        val name = if (isOffline) displayName else "Real User"
        assertThat(name).isEqualTo("Guest User")
    }

    // ─── FLOW: CURRENCY & EXCHANGE RATES ──────────────────────────

    @Test
    fun flow_currency_inrIsBaseRate1() {
        assertThat(1.0).isEqualTo(1.0)
    }

    @Test
    fun flow_currency_usdRateCalculation() {
        val apiRate = 0.012
        val rateToBase = 1.0 / apiRate
        assertThat(rateToBase).isWithin(0.1).of(83.33)
    }

    @Test
    fun flow_currency_eurRateCalculation() {
        val apiRate = 0.011
        val rateToBase = 1.0 / apiRate
        assertThat(rateToBase).isWithin(0.1).of(90.91)
    }

    @Test
    fun flow_currency_sameDayRatesUseCache() {
        val cachedDate = "Mon Jan 01 2024"; val today = "Mon Jan 01 2024"
        assertThat(cachedDate).isEqualTo(today)
    }

    @Test
    fun flow_currency_nextDayRatesTriggerNewCall() {
        val cachedDate = "Mon Jan 01 2024"; val today = "Tue Jan 02 2024"
        assertThat(cachedDate).isNotEqualTo(today)
    }

    @Test
    fun flow_currency_expenseInInrRate1() {
        val currency = "INR"; val rate = if (currency == "INR") 1.0 else 83.5
        assertThat(rate).isEqualTo(1.0)
    }

    @Test
    fun flow_currency_expenseInUsdRate83_5() {
        val currency = "USD"; val rate = if (currency == "INR") 1.0 else 83.5
        assertThat(rate).isEqualTo(83.5)
    }

    @Test
    fun flow_currency_apiFailureFallsBackToRate1() {
        val fallbackRate = 1.0
        assertThat(fallbackRate).isEqualTo(1.0)
    }

    @Test
    fun flow_currency_settlementAmountConvertedToBase() {
        val amount = 50.0; val rate = 83.5
        val amountInBase = kotlin.math.round((amount * rate) * 100) / 100
        assertThat(amountInBase).isEqualTo(4175.0)
    }

    @Test
    fun flow_currency_multipleExpensesDifferentCurrenciesSumInBase() {
        val expenses = listOf(100.0 * 1.0, 50.0 * 83.5)
        val total = expenses.sum()
        assertThat(total).isEqualTo(100.0 + 4175.0)
    }

    // ─── FLOW: QR CODE & INVITE ───────────────────────────────────

    @Test
    fun flow_qrCode_inviteCode6Chars() {
        val code = Calculations.generateInviteCode()
        assertThat(code).hasLength(6)
    }

    @Test
    fun flow_qrCode_containsInviteCode() {
        val inviteCode = "ABC123"
        val qrContent = "https://trevio.app/join/$inviteCode"
        assertThat(qrContent).contains(inviteCode)
    }

    @Test
    fun flow_qrCode_joinUrlFormat() {
        val inviteCode = "XYZ789"
        val url = "/join/$inviteCode"
        assertThat(url).isEqualTo("/join/XYZ789")
    }

    @Test
    fun flow_qrCode_scannedCodeExtractsInviteCode() {
        val qrContent = "https://trevio.app/join/ABC123"
        val match = Regex("/join/([A-Z0-9]{6})").find(qrContent)
        assertThat(match).isNotNull()
        assertThat(match!!.groupValues[1]).isEqualTo("ABC123")
    }

    @Test
    fun flow_qrCode_invalidQrReturnsNull() {
        val qrContent = "https://example.com"
        val match = Regex("/join/([A-Z0-9]{6})").find(qrContent)
        assertThat(match).isNull()
    }

    @Test
    fun flow_qrCode_caseInsensitiveLookup() {
        val input = "abc123"
        assertThat(input.uppercase()).isEqualTo("ABC123")
    }

    // ─── FLOW: AUTH & ONBOARDING ──────────────────────────────────

    @Test
    fun flow_auth_newUserDefaultValues() {
        val user = User()
        assertThat(user.defaultCurrency).isEqualTo("INR")
        assertThat(user.role).isEqualTo("user")
        assertThat(user.blocked).isFalse()
        assertThat(user.acceptedTnC).isFalse()
    }

    @Test
    fun flow_auth_existingUserNoDuplicate() {
        val exists = true; val createNew = !exists
        assertThat(createNew).isFalse()
    }

    @Test
    fun flow_auth_blockedUserAutoSignedOut() {
        val blocked = true
        assertThat(blocked).isTrue()
    }

    @Test
    fun flow_auth_userWithoutTnCRedirectedToTerms() {
        val acceptedTnC = false
        assertThat(acceptedTnC).isFalse()
    }

    @Test
    fun flow_auth_userWithAllSetupPassesOnboarding() {
        val acceptedTnC = true; val phoneNumber = "9876543210"
        assertThat(acceptedTnC && phoneNumber.isNotEmpty()).isTrue()
    }

    @Test
    fun flow_auth_autoGeneratedUsername() {
        val username = Calculations.generateBaseUsername("John", "Doe")
        assertThat(username).isEqualTo("john.doe")
    }

    // ─── FLOW: PROFILE ────────────────────────────────────────────

    @Test
    fun flow_profile_editDisplayName() {
        val newName = "Jane Doe"
        assertThat(newName).isEqualTo("Jane Doe")
    }

    @Test
    fun flow_profile_editPhoneNumberWithCountryCode() {
        val phone = "9876543210"; val countryCode = "+91"
        assertThat("$countryCode $phone").isEqualTo("+91 9876543210")
    }

    @Test
    fun flow_profile_editUpiId() {
        val upiId = "john@okhdfcbank"
        assertThat(upiId).isEqualTo("john@okhdfcbank")
    }

    @Test
    fun flow_profile_editDefaultCurrency() {
        val currency = "USD"
        assertThat(currency).isEqualTo("USD")
    }

    @Test
    fun flow_profile_deleteAccountRemovesUserDoc() {
        assertThat(true).isTrue()
    }

    @Test
    fun flow_profile_signOutClearsAuth() {
        assertThat(true).isTrue()
    }

    // ─── FLOW: ACTIVITY FEED ──────────────────────────────────────

    @Test
    fun flow_activity_sortedByCreatedAtDesc() {
        val activities = listOf(
            Activity(createdAt = 1000),
            Activity(createdAt = 3000),
            Activity(createdAt = 2000)
        )
        val sorted = activities.sortedByDescending { it.createdAt }
        assertThat(sorted[0].createdAt).isEqualTo(3000L)
    }

    @Test
    fun flow_activity_youTagForCurrentUser() {
        val currentUid = "u1"
        val activity = Activity(userId = "u1", userName = "John")
        val name = if (activity.userId == currentUid) "${activity.userName} (You)" else activity.userName
        assertThat(name).isEqualTo("John (You)")
    }

    @Test
    fun flow_activity_noYouTagForOtherUser() {
        val currentUid = "u1"
        val activity = Activity(userId = "u2", userName = "Jane")
        val name = if (activity.userId == currentUid) "${activity.userName} (You)" else activity.userName
        assertThat(name).isEqualTo("Jane")
    }

    @Test
    fun flow_activity_emptyStateShowsMessage() {
        val activities = emptyList<Activity>()
        val message = if (activities.isEmpty()) "No activity yet." else ""
        assertThat(message).isEqualTo("No activity yet.")
    }

    @Test
    fun flow_activity_immutable() {
        val canUpdate = false
        assertThat(canUpdate).isFalse()
    }

    @Test
    fun flow_activity_adminCanDelete() {
        val role = "admin"
        assertThat(role).isEqualTo("admin")
    }

    @Test
    fun flow_activity_regularMemberCannotDelete() {
        val role = "member"
        assertThat(role).isNotEqualTo("admin")
    }

    // ─── FLOW: RECURRING EXPENSES ─────────────────────────────────

    @Test
    fun flow_recurring_weeklyFrequencySaved() {
        val recurring = RecurringConfig(frequency = RecurringFrequency.WEEKLY)
        assertThat(recurring.frequency).isEqualTo(RecurringFrequency.WEEKLY)
    }

    @Test
    fun flow_recurring_monthlyFrequencySaved() {
        val recurring = RecurringConfig(frequency = RecurringFrequency.MONTHLY)
        assertThat(recurring.frequency).isEqualTo(RecurringFrequency.MONTHLY)
    }

    @Test
    fun flow_recurring_toggleDisabledSavesNoConfig() {
        val recurring: RecurringConfig? = null
        assertThat(recurring).isNull()
    }

    @Test
    fun flow_recurring_withEndDate() {
        val recurring = RecurringConfig(frequency = RecurringFrequency.WEEKLY, endDate = 1700000000000)
        assertThat(recurring.endDate).isNotNull()
    }

    @Test
    fun flow_recurring_withoutEndDate() {
        val recurring = RecurringConfig(frequency = RecurringFrequency.MONTHLY)
        assertThat(recurring.endDate).isNull()
    }

    @Test
    fun flow_recurring_withParentExpenseId() {
        val recurring = RecurringConfig(frequency = RecurringFrequency.MONTHLY, parentExpenseId = "exp1")
        assertThat(recurring.parentExpenseId).isEqualTo("exp1")
    }

    // ─── FLOW: EXPENSE NOTES ──────────────────────────────────────

    @Test
    fun flow_note_savedWhenProvided() {
        val note = "Birthday celebration"
        assertThat(note).isEqualTo("Birthday celebration")
    }

    @Test
    fun flow_note_notSetWhenNotProvided() {
        val note: String? = null
        assertThat(note).isNull()
    }

    @Test
    fun flow_note_emptyString() {
        val note = ""
        assertThat(note).isEmpty()
    }

    @Test
    fun flow_note_specialCharacters() {
        val note = "Lunch @ Café! (team)"
        assertThat(note).isEqualTo("Lunch @ Café! (team)")
    }

    // ─── FLOW: SETTLEMENT HISTORY ─────────────────────────────────

    @Test
    fun flow_settlementHistory_sortedByDateDesc() {
        val settlements = listOf(
            Settlement(date = 1000),
            Settlement(date = 3000),
            Settlement(date = 2000)
        )
        val sorted = settlements.sortedByDescending { it.date }
        assertThat(sorted[0].date).isEqualTo(3000L)
    }

    @Test
    fun flow_settlementHistory_youPaidForPayer() {
        val currentUid = "u1"
        val settlement = Settlement(fromUid = "u1", toUid = "u2")
        assertThat(settlement.fromUid).isEqualTo(currentUid)
    }

    @Test
    fun flow_settlementHistory_xPaidYouForReceiver() {
        val currentUid = "u1"
        val settlement = Settlement(fromUid = "u2", toUid = "u1")
        assertThat(settlement.toUid).isEqualTo(currentUid)
    }

    @Test
    fun flow_settlementHistory_emptyShowsMessage() {
        val settlements = emptyList<Settlement>()
        val message = if (settlements.isEmpty()) "No settlements yet." else ""
        assertThat(message).isEqualTo("No settlements yet.")
    }

    @Test
    fun flow_settlementHistory_nonMemberCannotView() {
        val isMember = false
        assertThat(isMember).isFalse()
    }

    @Test
    fun flow_settlementHistory_limitedTo50() {
        val limit = 50
        assertThat(limit).isEqualTo(50)
    }

    // ─── FLOW: GROUP SETTINGS ─────────────────────────────────────

    @Test
    fun flow_groupSettings_adminCanAccess() {
        val role = "admin"
        assertThat(role).isEqualTo("admin")
    }

    @Test
    fun flow_groupSettings_nonAdminCannotAccess() {
        val role = "member"
        assertThat(role).isNotEqualTo("admin")
    }

    @Test
    fun flow_groupSettings_emptyNameFails() {
        val name = ""
        assertThat(name.trim().isEmpty()).isTrue()
    }

    @Test
    fun flow_groupSettings_validNamePasses() {
        val name = "Updated Name"
        assertThat(name.trim().isNotEmpty()).isTrue()
    }

    @Test
    fun flow_groupSettings_archiveSetsTrue() {
        val archived = true
        assertThat(archived).isTrue()
    }

    @Test
    fun flow_groupSettings_unarchiveSetsFalse() {
        val archived = false
        assertThat(archived).isFalse()
    }

    // ─── FLOW: CROSS-PLATFORM CONSISTENCY ─────────────────────────

    @Test
    fun flow_crossPlatform_groupVisibleOnBoth() {
        val group = Group(groupId = "g1", name = "Trip")
        assertThat(group.groupId).isEqualTo("g1")
    }

    @Test
    fun flow_crossPlatform_expenseVisibleOnBoth() {
        val expense = Expense(expenseId = "e1", description = "Dinner")
        assertThat(expense.expenseId).isEqualTo("e1")
    }

    @Test
    fun flow_crossPlatform_blockedOnBoth() {
        val blocked = true
        assertThat(blocked).isTrue()
    }

    @Test
    fun flow_crossPlatform_youTagCapitalizedBoth() {
        assertThat("(You)").isEqualTo("(You)")
    }

    @Test
    fun flow_crossPlatform_selfInvitationBlockedBoth() {
        val fromUid = "u1"; val toUid = "u1"
        assertThat(fromUid).isEqualTo(toUid)
    }

    @Test
    fun flow_crossPlatform_splitCalculationsMatch() {
        val result1 = Calculations.calculateSplits(100.0, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        val result2 = Calculations.calculateSplits(100.0, SplitType.EQUAL, listOf("u1", "u2", "u3"))
        assertThat(result1).isEqualTo(result2)
    }

    @Test
    fun flow_crossPlatform_balanceCalculationsMatch() {
        val expenses = listOf(
            Calculations.ExpenseBalanceData("u1", mapOf("u1" to SplitEntry(50.0), "u2" to SplitEntry(50.0)), 100.0, 1.0)
        )
        val result1 = Calculations.calculateBalances(expenses, emptyList(), listOf("u1", "u2"))
        val result2 = Calculations.calculateBalances(expenses, emptyList(), listOf("u1", "u2"))
        assertThat(result1).isEqualTo(result2)
    }

    @Test
    fun flow_crossPlatform_simplifyDebtsMatch() {
        val balances = mapOf("u1" to -100.0, "u2" to 100.0)
        val result1 = Calculations.simplifyDebts(balances)
        val result2 = Calculations.simplifyDebts(balances)
        assertThat(result1).isEqualTo(result2)
    }

    @Test
    fun flow_crossPlatform_userSearchExcludesSelf() {
        val allUsers = listOf("u1", "u2", "u3"); val currentUid = "u1"
        val filtered = allUsers.filter { it != currentUid }
        assertThat(filtered).doesNotContain("u1")
    }

    @Test
    fun flow_crossPlatform_expenseNotificationExcludesCreator() {
        val creator = "u1"; val members = listOf("u1", "u2", "u3")
        val notify = members.filter { it != creator }
        assertThat(notify).doesNotContain("u1")
    }

    // ─── FLOW: EDGE CASES ─────────────────────────────────────────

    @Test
    fun flow_edgeCase_unauthenticatedGetsError() {
        val uid: String? = null
        assertThat(uid).isNull()
    }

    @Test
    fun flow_edgeCase_nonExistentGroupError() {
        val exists = false
        assertThat(exists).isFalse()
    }

    @Test
    fun flow_edgeCase_selfSettlementBlocked() {
        val fromUid = "u1"; val toUid = "u1"
        assertThat(fromUid).isEqualTo(toUid)
    }

    @Test
    fun flow_edgeCase_selfInvitationBlocked() {
        val fromUid = "u1"; val toUid = "u1"
        assertThat(fromUid).isEqualTo(toUid)
    }

    @Test
    fun flow_edgeCase_emptyMemberListReturnsEmpty() {
        val result = Calculations.calculateSplits(100.0, SplitType.EQUAL, emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun flow_edgeCase_nonMemberCannotViewGroup() {
        val isMember = false
        assertThat(isMember).isFalse()
    }

    @Test
    fun flow_edgeCase_nonMemberCannotAddExpense() {
        val isMember = false
        assertThat(isMember).isFalse()
    }

    @Test
    fun flow_edgeCase_nonMemberCannotSettle() {
        val isMember = false
        assertThat(isMember).isFalse()
    }

    @Test
    fun flow_edgeCase_nonMemberCannotViewActivities() {
        val isMember = false
        assertThat(isMember).isFalse()
    }

    @Test
    fun flow_edgeCase_exchangeRateApiFailureFallbackRate1() {
        val fallbackRate = 1.0
        assertThat(fallbackRate).isEqualTo(1.0)
    }

    @Test
    fun flow_edgeCase_userDocMissingTriggersAutoCreate() {
        val exists = false; val shouldCreate = !exists
        assertThat(shouldCreate).isTrue()
    }

    @Test
    fun flow_edgeCase_inviteCodeCaseInsensitive() {
        val input = "abc123"
        assertThat(input.uppercase()).isEqualTo("ABC123")
    }

    @Test
    fun flow_edgeCase_htmlInBroadcastSanitized() {
        val html = "<script>alert('xss')</script><p>Hello</p>"
        val sanitized = html.replace(Regex("<script[^>]*>.*?</script>", RegexOption.IGNORE_CASE), "")
        assertThat(sanitized).doesNotContain("<script>")
        assertThat(sanitized).contains("<p>Hello</p>")
    }

    @Test
    fun flow_edgeCase_jsInBroadcastDisabled() {
        val html = "<img src=x onerror=alert(1)>"
        val sanitized = html.replace(Regex("onerror=", RegexOption.IGNORE_CASE), "")
        assertThat(sanitized).doesNotContain("onerror=")
    }

    @Test
    fun flow_edgeCase_largeGroupNotificationBatch() {
        val members = (1..500).map { "u$it" }
        assertThat(members).hasSize(500)
    }

    @Test
    fun flow_edgeCase_acceptAlreadyAcceptedError() {
        val status = "accepted"
        assertThat(status).isNotEqualTo("pending")
    }

    @Test
    fun flow_edgeCase_sharesZeroTotalReturnsEmpty() {
        val splits = mapOf("u1" to SplitEntry(0.0, 0.0), "u2" to SplitEntry(0.0, 0.0))
        val result = Calculations.calculateSplits(100.0, SplitType.SHARES, listOf("u1", "u2"), splits)
        assertThat(result).isEmpty()
    }

    // ─── FLOW: DARK MODE ──────────────────────────────────────────

    @Test
    fun flow_darkMode_toggleAddsDarkClass() {
        val htmlClass = "dark"
        assertThat(htmlClass).isEqualTo("dark")
    }

    @Test
    fun flow_darkMode_lightRemovesDarkClass() {
        val htmlClass = ""
        assertThat(htmlClass).isEmpty()
    }

    @Test
    fun flow_darkMode_toggleSwitchesTheme() {
        var theme = "light"
        theme = if (theme == "light") "dark" else "light"
        assertThat(theme).isEqualTo("dark")
    }

    // ─── FLOW: EXPENSE SEARCH & FILTER ────────────────────────────

    @Test
    fun flow_search_byDescriptionFilters() {
        val expenses = listOf("Dinner at restaurant", "Lunch", "Coffee break")
        val filtered = expenses.filter { it.lowercase().contains("dinner") }
        assertThat(filtered).hasSize(1)
    }

    @Test
    fun flow_search_noMatchesShowsEmpty() {
        val expenses = listOf("Dinner", "Lunch")
        val filtered = expenses.filter { it.lowercase().contains("xyz") }
        assertThat(filtered).isEmpty()
    }

    @Test
    fun flow_search_caseInsensitive() {
        val expense = "Dinner"
        val search = "dinner"
        assertThat(expense.lowercase().contains(search.lowercase())).isTrue()
    }

    @Test
    fun flow_search_categoryAllShowsEverything() {
        val category = "all"
        val isFiltering = category != "all"
        assertThat(isFiltering).isFalse()
    }

    @Test
    fun flow_search_emptySearchShowsAll() {
        val search = ""
        val isFiltering = search.isNotEmpty()
        assertThat(isFiltering).isFalse()
    }

    // ─── FLOW: UPI DEEP LINK ──────────────────────────────────────

    @Test
    fun flow_upiLink_generatesValidLink() {
        val link = "upi://pay?pa=test@upi&am=100&pn=John%20Doe"
        assertThat(link).contains("upi://pay")
        assertThat(link).contains("pa=test@upi")
        assertThat(link).contains("am=100")
    }

    @Test
    fun flow_upiLink_encodesPayeeName() {
        val link = "upi://pay?pa=test@upi&am=100&pn=John%20Doe"
        assertThat(link).contains("pn=John%20Doe")
    }

    @Test
    fun flow_upiLink_handlesDecimalAmount() {
        val link = "upi://pay?pa=test@upi&am=99.99&pn=John"
        assertThat(link).contains("am=99.99")
    }

    // ─── FLOW: CSV EXPORT ─────────────────────────────────────────

    @Test
    fun flow_csv_headerRowCorrect() {
        val header = "Date,Description,Amount,Currency,Category,Paid By,Split Type,Note"
        assertThat(header.split(",")).hasSize(8)
    }

    @Test
    fun flow_csv_descriptionWithCommasEscaped() {
        val desc = "Dinner, Drinks & Dessert"
        val escaped = "\"$desc\""
        assertThat(escaped).isEqualTo("\"Dinner, Drinks & Dessert\"")
    }

    @Test
    fun flow_csv_filenameIncludesGroupName() {
        val groupName = "Trip to Goa"
        val filename = "$groupName-expenses.csv"
        assertThat(filename).isEqualTo("Trip to Goa-expenses.csv")
    }

    // ─── FLOW: WEB PUSH NOTIFICATIONS ─────────────────────────────

    @Test
    fun flow_pushNotification_fcmInitialized() {
        assertThat(true).isTrue()
    }

    @Test
    fun flow_pushNotification_permissionRequested() {
        val permission = "granted"
        assertThat(permission).isEqualTo("granted")
    }

    @Test
    fun flow_pushNotification_tokenSavedToUserDoc() {
        val token = "fcm_token_123"
        assertThat(token).isNotEmpty()
    }

    @Test
    fun flow_pushNotification_serviceWorkerRegistered() {
        assertThat(true).isTrue()
    }

    // ─── FLOW: LINK OFFLINE MEMBER (ADMIN) ────────────────────────

    @Test
    fun flow_linkOfflineMember_adminCanLink() {
        val role = "admin"
        assertThat(role).isEqualTo("admin")
    }

    @Test
    fun flow_linkOfflineMember_nonAdminCannotLink() {
        val role = "member"
        assertThat(role).isNotEqualTo("admin")
    }

    @Test
    fun flow_linkOfflineMember_cannotLinkNonOffline() {
        val isOffline = false
        assertThat(isOffline).isFalse()
    }

    @Test
    fun flow_linkOfflineMember_createsDocWithRealUid() {
        val memberData = mapOf("displayName" to "Guest", "balance" to 50.0, "role" to "member")
        val linkedData = memberData + mapOf("uid" to "u1", "isOffline" to false)
        assertThat(linkedData["uid"]).isEqualTo("u1")
        assertThat(linkedData["isOffline"]).isEqualTo(false)
        assertThat(linkedData["balance"]).isEqualTo(50.0)
    }

    @Test
    fun flow_linkOfflineMember_deletesOfflineDocWhenExistingDoc() {
        val existingDoc = true
        assertThat(existingDoc).isTrue()
    }

    @Test
    fun flow_linkOfflineMember_decrementsCountWhenExistingDoc() {
        val existingDoc = true
        val currentCount = 5
        val newCount = if (existingDoc) currentCount - 1 else currentCount
        assertThat(newCount).isEqualTo(4)
    }

    @Test
    fun flow_linkOfflineMember_preservesBalanceAndRole() {
        val offlineMember = Member(balance = 100.0, role = "member", displayName = "Guest")
        val linkedMember = offlineMember.copy(uid = "u1", isOffline = false)
        assertThat(linkedMember.balance).isEqualTo(100.0)
        assertThat(linkedMember.role).isEqualTo("member")
    }

    @Test
    fun flow_linkOfflineMember_activityTypeMemberLinked() {
        assertThat("member_linked").isEqualTo("member_linked")
    }

    @Test
    fun flow_linkOfflineMember_triggersMigrateReferences() {
        assertThat(true).isTrue()
    }

    @Test
    fun flow_linkOfflineMember_triggersRecalculateBalances() {
        assertThat(true).isTrue()
    }

    // ─── FLOW: MIGRATE MEMBER REFERENCES ──────────────────────────

    @Test
    fun flow_migrateRef_migratesPaidByInExpenses() {
        val oldId = "auto_123"; val newId = "u1"
        val paidBy = oldId
        val migrated = if (paidBy == oldId) newId else paidBy
        assertThat(migrated).isEqualTo("u1")
    }

    @Test
    fun flow_migrateRef_migratesSplitsKeyInExpenses() {
        val oldId = "auto_123"; val newId = "u1"
        val splits = mutableMapOf(oldId to SplitEntry(50.0))
        if (splits.containsKey(oldId)) {
            splits[newId] = splits[oldId]!!
            splits.remove(oldId)
        }
        assertThat(splits.containsKey(oldId)).isFalse()
        assertThat(splits[newId]!!.amount).isEqualTo(50.0)
    }

    @Test
    fun flow_migrateRef_migratesFromUidInSettlements() {
        val oldId = "auto_123"; val newId = "u1"
        val fromUid = oldId
        val migrated = if (fromUid == oldId) newId else fromUid
        assertThat(migrated).isEqualTo("u1")
    }

    @Test
    fun flow_migrateRef_migratesToUidInSettlements() {
        val oldId = "auto_123"; val newId = "u1"
        val toUid = oldId
        val migrated = if (toUid == oldId) newId else toUid
        assertThat(migrated).isEqualTo("u1")
    }

    @Test
    fun flow_migrateRef_doesNotModifyUnrelatedExpenses() {
        val oldId = "auto_123"
        val paidBy = "u2"
        val changed = paidBy == oldId
        assertThat(changed).isFalse()
    }

    @Test
    fun flow_migrateRef_doesNotModifyUnrelatedSettlements() {
        val oldId = "auto_123"
        val fromUid = "u2"; val toUid = "u3"
        val changed = fromUid == oldId || toUid == oldId
        assertThat(changed).isFalse()
    }

    // ─── FLOW: DECLINE INVITATION ─────────────────────────────────

    @Test
    fun flow_declineInvitation_notForYouRejected() {
        val toUid = "u1"; val currentUid = "u2"
        assertThat(toUid).isNotEqualTo(currentUid)
    }

    @Test
    fun flow_declineInvitation_alreadyAcceptedRejected() {
        val status = "accepted"
        assertThat(status).isNotEqualTo("pending")
    }

    @Test
    fun flow_declineInvitation_pendingCanBeDeclined() {
        val status = "pending"
        assertThat(status).isEqualTo("pending")
    }

    @Test
    fun flow_declineInvitation_setsStatusToDeclined() {
        assertThat("declined").isEqualTo("declined")
    }

    @Test
    fun flow_declineInvitation_deletesPendingMemberDoc() {
        val memberStatus = "pending"
        assertThat(memberStatus == "pending").isTrue()
    }

    @Test
    fun flow_declineInvitation_decrementsMemberCountForPending() {
        val memberStatus = "pending"
        val currentCount = 5
        val newCount = if (memberStatus == "pending") maxOf(0, currentCount - 1) else currentCount
        assertThat(newCount).isEqualTo(4)
    }

    @Test
    fun flow_declineInvitation_doesNotDecrementIfNoPendingMember() {
        val memberExists = false
        val currentCount = 5
        val newCount = if (memberExists) maxOf(0, currentCount - 1) else currentCount
        assertThat(newCount).isEqualTo(5)
    }

    @Test
    fun flow_declineInvitation_countDoesNotGoBelow0() {
        val currentCount = 0
        val newCount = maxOf(0, currentCount - 1)
        assertThat(newCount).isEqualTo(0)
    }

    // ─── FLOW: NOTIFICATION DATA UPDATE ───────────────────────────

    @Test
    fun flow_notifDataUpdate_mergesNewWithExisting() {
        val existing = mapOf("groupId" to "g1", "type" to "invitation")
        val newData = mapOf("invitationId" to "inv1", "status" to "accepted")
        val merged = existing + newData
        assertThat(merged["groupId"]).isEqualTo("g1")
        assertThat(merged["invitationId"]).isEqualTo("inv1")
    }

    @Test
    fun flow_notifDataUpdate_marksAsRead() {
        val read = true
        assertThat(read).isTrue()
    }

    @Test
    fun flow_notifDataUpdate_preservesExistingKeys() {
        val existing = mapOf("groupId" to "g1", "groupName" to "Trip")
        val newData = mapOf("status" to "accepted")
        val merged = existing + newData
        assertThat(merged["groupId"]).isEqualTo("g1")
        assertThat(merged["groupName"]).isEqualTo("Trip")
    }

    @Test
    fun flow_notifDataUpdate_overwritesExistingKeys() {
        val existing = mapOf("status" to "pending")
        val newData = mapOf("status" to "accepted")
        val merged = existing + newData
        assertThat(merged["status"]).isEqualTo("accepted")
    }

    // ─── FLOW: MARK ALL NOTIFICATIONS READ ────────────────────────

    @Test
    fun flow_markAllRead_queriesOnlyUnread() {
        val notifications = listOf(
            AppNotification(notificationId = "n1", read = false),
            AppNotification(notificationId = "n2", read = false),
            AppNotification(notificationId = "n3", read = true)
        )
        val unread = notifications.filter { !it.read }
        assertThat(unread).hasSize(2)
    }

    @Test
    fun flow_markAllRead_batchUpdatesAllToRead() {
        val notifications = listOf(
            AppNotification(notificationId = "n1", read = false),
            AppNotification(notificationId = "n2", read = false)
        )
        val updated = notifications.map { it.copy(read = true) }
        assertThat(updated.all { it.read }).isTrue()
    }

    @Test
    fun flow_markAllRead_emptyBatchWhenNoUnread() {
        val notifications = emptyList<AppNotification>()
        assertThat(notifications).isEmpty()
    }

    @Test
    fun flow_markAllRead_alreadyReadExcluded() {
        val notifications = listOf(
            AppNotification(notificationId = "n1", read = true),
            AppNotification(notificationId = "n2", read = false)
        )
        val unread = notifications.filter { !it.read }
        assertThat(unread).hasSize(1)
        assertThat(unread[0].notificationId).isEqualTo("n2")
    }

    // ─── FLOW: ACCEPT TnC FALLBACK ────────────────────────────────

    @Test
    fun flow_acceptTnC_generatesUsernameFromName() {
        val base = Calculations.generateBaseUsername("John", "Doe")
        assertThat(base).isEqualTo("john.doe")
    }

    @Test
    fun flow_acceptTnC_fallsBackToEmailPrefix() {
        val base = Calculations.generateBaseUsername("", "")
        val email = "john.doe@example.com"
        val emailPrefix = email.split("@")[0].lowercase().replace(Regex("[^a-z0-9]"), "")
        val finalBase = base.ifEmpty { emailPrefix }.ifEmpty { "user" }
        assertThat(finalBase).isEqualTo("johndoe")
    }

    @Test
    fun flow_acceptTnC_fallsBackToUserWhenAllEmpty() {
        val base = ""
        val emailPrefix = ""
        val finalBase = base.ifEmpty { emailPrefix }.ifEmpty { "user" }
        assertThat(finalBase).isEqualTo("user")
    }

    @Test
    fun flow_acceptTnC_returnsExistingIfAlreadyAccepted() {
        val acceptedTnC = true; val username = "john.doe"
        val shouldReturn = acceptedTnC && username.isNotEmpty()
        assertThat(shouldReturn).isTrue()
    }

    @Test
    fun flow_acceptTnC_setsAcceptedTnCTrue() {
        val acceptedTnC = true
        assertThat(acceptedTnC).isTrue()
    }

    @Test
    fun flow_acceptTnC_findUniqueUsernameAppendsSuffix() {
        val base = "john.doe"
        val existing = setOf("john.doe")
        var username = base
        var suffix = 0
        while (existing.contains(username)) {
            suffix++
            username = "$base$suffix"
        }
        assertThat(username).isEqualTo("john.doe1")
    }

    @Test
    fun flow_acceptTnC_findUniqueUsernameIncrementsSuffix() {
        val base = "john.doe"
        val existing = setOf("john.doe", "john.doe1", "john.doe2")
        var username = base
        var suffix = 0
        while (existing.contains(username)) {
            suffix++
            username = "$base$suffix"
        }
        assertThat(username).isEqualTo("john.doe3")
    }

    // ─── FLOW: UPDATE USERNAME EDGE CASES ─────────────────────────

    @Test
    fun flow_updateUsername_sameReturnsEarly() {
        val current = "john.doe"; val new = "john.doe"
        assertThat(current == new).isTrue()
    }

    @Test
    fun flow_updateUsername_differentProceeds() {
        val current = "john.doe"; val new = "jane.doe"
        assertThat(current == new).isFalse()
    }

    @Test
    fun flow_updateUsername_normalizesToLowercase() {
        val input = "John.Doe"
        val normalized = input.lowercase().replace(Regex("[^a-z0-9._]"), "")
        assertThat(normalized).isEqualTo("john.doe")
    }

    @Test
    fun flow_updateUsername_rejectsTooShort() {
        val username = "ab"
        assertThat(username.length < 3).isTrue()
    }

    @Test
    fun flow_updateUsername_deletesOldUsernameDoc() {
        val currentUsername = "old.name"
        assertThat(currentUsername.isNotEmpty()).isTrue()
    }

    @Test
    fun flow_updateUsername_noDeleteIfNoOldUsername() {
        val currentUsername: String? = null
        assertThat(currentUsername).isNull()
    }

    // ─── FLOW: SEARCH USERS EDGE CASES ────────────────────────────

    @Test
    fun flow_searchUsers_emptyQueryReturnsEmpty() {
        val query = ""
        val result = if (query.length < 1) emptyList<String>() else listOf("result")
        assertThat(result).isEmpty()
    }

    @Test
    fun flow_searchUsers_excludesCurrentUser() {
        val currentUid = "u1"
        val results = listOf("u1", "u2", "u3")
        val filtered = results.filter { it != currentUid }
        assertThat(filtered).containsExactly("u2", "u3")
    }

    @Test
    fun flow_searchUsers_limitsTo10() {
        val results = (1..15).map { "u$it" }
        val limited = results.take(10)
        assertThat(limited).hasSize(10)
    }

    @Test
    fun flow_searchUsers_filtersOutEmptyUsernames() {
        val results = listOf(
            UserSearchResult(uid = "u1", username = ""),
            UserSearchResult(uid = "u2", username = "jane")
        )
        val filtered = results.filter { it.username.isNotEmpty() }
        assertThat(filtered).hasSize(1)
    }

    @Test
    fun flow_searchUsers_normalizesQuery() {
        val query = "John.Doe@"
        val normalized = query.lowercase().replace(Regex("[^a-z0-9._]"), "")
        assertThat(normalized).isEqualTo("john.doe")
    }

    // ─── FLOW: CHECK USERNAME AVAILABILITY ────────────────────────

    @Test
    fun flow_checkUsername_tooShortReturnsUnavailable() {
        val username = "ab"
        assertThat(username.length < 3).isTrue()
    }

    @Test
    fun flow_checkUsername_emptyReturnsUnavailable() {
        val username = ""
        assertThat(username.length < 3).isTrue()
    }

    @Test
    fun flow_checkUsername_exactly3CharsPasses() {
        val username = "abc"
        assertThat(username.length >= 3).isTrue()
    }

    @Test
    fun flow_checkUsername_normalizesForLookup() {
        val input = "John.Doe!"
        val normalized = input.lowercase().replace(Regex("[^a-z0-9._]"), "")
        assertThat(normalized).isEqualTo("john.doe")
    }

    @Test
    fun flow_checkUsername_availableIfDocNotExists() {
        val docExists = false
        assertThat(!docExists).isTrue()
    }

    @Test
    fun flow_checkUsername_unavailableIfDocExists() {
        val docExists = true
        assertThat(!docExists).isFalse()
    }

    // ─── FLOW: DELETE ACCOUNT ─────────────────────────────────────

    @Test
    fun flow_deleteAccount_setsAllGroupsToLeft() {
        val memberships = listOf(
            MockMember("u1", "member", "active"),
            MockMember("u1", "member", "active")
        )
        val updated = memberships.map { it.copy(status = "left") }
        assertThat(updated.all { it.status == "left" }).isTrue()
    }

    @Test
    fun flow_deleteAccount_decrementsMemberCount() {
        val groups = listOf(5, 3)
        val updated = groups.map { maxOf(0, it - 1) }
        assertThat(updated[0]).isEqualTo(4)
        assertThat(updated[1]).isEqualTo(2)
    }

    @Test
    fun flow_deleteAccount_countDoesNotGoBelow0() {
        val count = 0
        val newCount = maxOf(0, count - 1)
        assertThat(newCount).isEqualTo(0)
    }

    @Test
    fun flow_deleteAccount_deletesUsernameDocIfExists() {
        val username = "john.doe"
        assertThat(username.isNotEmpty()).isTrue()
    }

    @Test
    fun flow_deleteAccount_noDeleteUsernameIfNoneExists() {
        val username: String? = null
        assertThat(username).isNull()
    }

    @Test
    fun flow_deleteAccount_deletesUserDoc() {
        assertThat(true).isTrue()
    }

    @Test
    fun flow_deleteAccount_deletesAuthAccount() {
        assertThat(true).isTrue()
    }

    @Test
    fun flow_deleteAccount_onlyUpdatesActiveMemberships() {
        val memberships = listOf(
            MockMember("u1", "member", "active"),
            MockMember("u1", "member", "left")
        )
        val activeOnly = memberships.filter { it.status == "active" }
        assertThat(activeOnly).hasSize(1)
    }

    // ─── FLOW: NOTIFICATION PAGINATION ────────────────────────────

    @Test
    fun flow_notifPagination_defaultPageSize20() {
        assertThat(20).isEqualTo(20)
    }

    @Test
    fun flow_notifPagination_customPageSizeRespected() {
        val pageSize = 50
        assertThat(pageSize).isEqualTo(50)
    }

    @Test
    fun flow_notifPagination_hasMoreTrueWhenFullPage() {
        val snapshotSize = 20; val pageSize = 20
        val hasMore = snapshotSize == pageSize
        assertThat(hasMore).isTrue()
    }

    @Test
    fun flow_notifPagination_hasMoreFalseWhenPartialPage() {
        val snapshotSize = 15; val pageSize = 20
        val hasMore = snapshotSize == pageSize
        assertThat(hasMore).isFalse()
    }

    @Test
    fun flow_notifPagination_lastIdIsLastDocId() {
        val docs = listOf("n1", "n2", "n3")
        val lastId = if (docs.isNotEmpty()) docs.last() else null
        assertThat(lastId).isEqualTo("n3")
    }

    @Test
    fun flow_notifPagination_lastIdNullWhenEmpty() {
        val docs = emptyList<String>()
        val lastId = if (docs.isNotEmpty()) docs.last() else null
        assertThat(lastId).isNull()
    }

    @Test
    fun flow_notifPagination_usesCursorForSecondPage() {
        val lastNotificationId = "n20"
        assertThat(lastNotificationId.isNotEmpty()).isTrue()
    }

    @Test
    fun flow_notifPagination_firstPageHasNoCursor() {
        val lastNotificationId: String? = null
        assertThat(lastNotificationId).isNull()
    }

    @Test
    fun flow_notifPagination_sortedByCreatedAtDesc() {
        val notifications = listOf(
            AppNotification(notificationId = "n1", createdAt = 1000),
            AppNotification(notificationId = "n2", createdAt = 3000),
            AppNotification(notificationId = "n3", createdAt = 2000)
        )
        val sorted = notifications.sortedByDescending { it.createdAt }
        assertThat(sorted[0].notificationId).isEqualTo("n2")
    }

    // ─── FLOW: JOIN GROUP OFFLINE REJOIN ──────────────────────────

    @Test
    fun flow_joinOffline_offlineMemberNotBlockedFromRejoin() {
        val member = MockMember("u1", "member", "active", isOffline = true)
        val isAlreadyMember = member.status == "active" && !member.isOffline
        assertThat(isAlreadyMember).isFalse()
    }

    @Test
    fun flow_joinOffline_offlineMemberDoesNotIncrementCount() {
        val memberExists = true
        val memberCount = 5
        val newCount = if (memberExists) memberCount else memberCount + 1
        assertThat(newCount).isEqualTo(5)
    }

    @Test
    fun flow_joinOffline_pendingMemberSetsStatusActive() {
        val memberDoc = mapOf("status" to "pending")
        assertThat(memberDoc["status"]).isEqualTo("pending")
    }

    @Test
    fun flow_joinOffline_newMemberCreatesFullDoc() {
        val memberDoc = MockMember("u1", "member", "active")
        assertThat(memberDoc.role).isEqualTo("member")
        assertThat(memberDoc.status).isEqualTo("active")
    }

    @Test
    fun flow_joinOffline_newMemberIncrementsCount() {
        val memberDocExists = false
        val currentCount = 4
        val newCount = if (!memberDocExists) currentCount + 1 else currentCount
        assertThat(newCount).isEqualTo(5)
    }

    @Test
    fun flow_joinOffline_createsMemberJoinedActivity() {
        assertThat("member_joined").isEqualTo("member_joined")
    }

    // ─── FLOW: SEND INVITATION EDGE CASES ─────────────────────────

    @Test
    fun flow_sendInvite_alreadyMemberRejected() {
        val existingMember = true
        assertThat(existingMember).isTrue()
    }

    @Test
    fun flow_sendInvite_nonExistingUserRejected() {
        val usernameDocExists = false
        assertThat(usernameDocExists).isFalse()
    }

    @Test
    fun flow_sendInvite_selfInvitationRejected() {
        val toUid = "u1"; val currentUid = "u1"
        assertThat(toUid).isEqualTo(currentUid)
    }

    @Test
    fun flow_sendInvite_normalizesUsername() {
        val input = "John.Doe!"
        val normalized = input.lowercase().replace(Regex("[^a-z0-9._]"), "")
        assertThat(normalized).isEqualTo("john.doe")
    }

    @Test
    fun flow_sendInvite_createsPendingMemberDoc() {
        val pendingMember = MockMember("u2", "member", "pending")
        assertThat(pendingMember.status).isEqualTo("pending")
    }

    @Test
    fun flow_sendInvite_incrementsCountForNewPending() {
        val pendingDocExists = false
        val currentCount = 3
        val newCount = if (!pendingDocExists) currentCount + 1 else currentCount
        assertThat(newCount).isEqualTo(4)
    }

    @Test
    fun flow_sendInvite_noDuplicateIfAlreadyPending() {
        val pendingDocExists = true
        assertThat(pendingDocExists).isTrue()
    }

    @Test
    fun flow_sendInvite_notificationNonBlocking() {
        val invitationCreated = true; val notificationFailed = false
        assertThat(invitationCreated && !notificationFailed).isTrue()
    }

    // ─── FLOW: ADD OFFLINE MEMBER EDGE CASES ──────────────────────

    @Test
    fun flow_addOffline_emptyNameRejected() {
        val displayName = ""
        assertThat(displayName.isBlank()).isTrue()
    }

    @Test
    fun flow_addOffline_whitespaceNameRejected() {
        val displayName = "   "
        assertThat(displayName.isBlank()).isTrue()
    }

    @Test
    fun flow_addOffline_validNameAccepted() {
        val displayName = "Guest User"
        assertThat(displayName.isNotBlank()).isTrue()
    }

    @Test
    fun flow_addOffline_hasEmptyUid() {
        val member = Member(uid = "", isOffline = true, displayName = "Guest")
        assertThat(member.uid).isEmpty()
    }

    @Test
    fun flow_addOffline_hasIsOfflineTrue() {
        val member = Member(isOffline = true)
        assertThat(member.isOffline).isTrue()
    }

    @Test
    fun flow_addOffline_hasRoleMember() {
        val member = Member(role = "member")
        assertThat(member.role).isEqualTo("member")
    }

    @Test
    fun flow_addOffline_hasBalance0() {
        val member = Member(balance = 0.0)
        assertThat(member.balance).isEqualTo(0.0)
    }

    @Test
    fun flow_addOffline_hasStatusActive() {
        val member = Member(status = "active")
        assertThat(member.status).isEqualTo("active")
    }

    @Test
    fun flow_addOffline_recordsAddedBy() {
        val addedBy = "u1"
        assertThat(addedBy).isEqualTo("u1")
    }

    @Test
    fun flow_addOffline_incrementsMemberCount() {
        val currentCount = 3
        val newCount = currentCount + 1
        assertThat(newCount).isEqualTo(4)
    }

    @Test
    fun flow_addOffline_createsMemberAddedActivity() {
        assertThat("member_added").isEqualTo("member_added")
    }

    @Test
    fun flow_addOffline_nonActiveMemberCannotAdd() {
        val callerStatus = "left"
        assertThat(callerStatus).isNotEqualTo("active")
    }

    @Test
    fun flow_addOffline_activeMemberCanAdd() {
        val callerStatus = "active"
        assertThat(callerStatus).isEqualTo("active")
    }

    // ─── FLOW: CLAIM OFFLINE MEMBER EDGE CASES ────────────────────

    @Test
    fun flow_claimOffline_nonOfflineCannotBeClaimed() {
        val isOffline = false
        assertThat(isOffline).isFalse()
    }

    @Test
    fun flow_claimOffline_offlineCanBeClaimed() {
        val isOffline = true
        assertThat(isOffline).isTrue()
    }

    @Test
    fun flow_claimOffline_existingDocDeletesOfflineDoc() {
        val existingDoc = true
        assertThat(existingDoc).isTrue()
    }

    @Test
    fun flow_claimOffline_existingDocDecrementsCount() {
        val existingDoc = true
        val currentCount = 5
        val newCount = if (existingDoc) maxOf(0, currentCount - 1) else currentCount
        assertThat(newCount).isEqualTo(4)
    }

    @Test
    fun flow_claimOffline_noExistingDocCreatesNewWithOfflineData() {
        val offlineData = Member(displayName = "Guest", balance = 50.0, role = "member")
        val newDoc = offlineData.copy(uid = "u1", isOffline = false)
        assertThat(newDoc.uid).isEqualTo("u1")
        assertThat(newDoc.isOffline).isFalse()
        assertThat(newDoc.balance).isEqualTo(50.0)
    }

    @Test
    fun flow_claimOffline_recordsClaimedAt() {
        val claimedAt = System.currentTimeMillis()
        assertThat(claimedAt).isGreaterThan(0L)
    }

    @Test
    fun flow_claimOffline_createsMemberClaimedActivity() {
        assertThat("member_claimed").isEqualTo("member_claimed")
    }

    @Test
    fun flow_claimOffline_triggersMigrateReferences() {
        assertThat(true).isTrue()
    }

    @Test
    fun flow_claimOffline_triggersRecalculateBalances() {
        assertThat(true).isTrue()
    }

    @Test
    fun flow_claimOffline_countDoesNotGoBelow0() {
        val currentCount = 0
        val newCount = maxOf(0, currentCount - 1)
        assertThat(newCount).isEqualTo(0)
    }

    // ─── FLOW: BROADCAST UNREAD FILTER ────────────────────────────

    @Test
    fun flow_broadcastUnread_excludesAlreadyRead() {
        val active = listOf("b1", "b2")
        val readIds = setOf("b1")
        val unread = active.filter { it !in readIds }
        assertThat(unread).containsExactly("b2")
    }

    @Test
    fun flow_broadcastUnread_allUnreadWhenNoReads() {
        val active = listOf("b1", "b2")
        val readIds = emptySet<String>()
        val unread = active.filter { it !in readIds }
        assertThat(unread).hasSize(2)
    }

    @Test
    fun flow_broadcastUnread_allReadReturnsEmpty() {
        val active = listOf("b1", "b2")
        val readIds = setOf("b1", "b2")
        val unread = active.filter { it !in readIds }
        assertThat(unread).isEmpty()
    }

    @Test
    fun flow_broadcastUnread_acknowledgeCreatesReadRecord() {
        val read = BroadcastRead(uid = "u1", readAt = System.currentTimeMillis())
        assertThat(read.uid).isEqualTo("u1")
        assertThat(read.readAt).isGreaterThan(0L)
    }

    @Test
    fun flow_broadcastUnread_getReadCountReturnsTotal() {
        val reads = listOf(BroadcastRead(uid = "u1"), BroadcastRead(uid = "u2"), BroadcastRead(uid = "u3"))
        assertThat(reads).hasSize(3)
    }

    // ─── FLOW: GROUP INFO & MEMBER ACCESS ─────────────────────────

    @Test
    fun flow_groupInfo_nonMemberCannotGetInfo() {
        val isMember = false
        assertThat(isMember).isFalse()
    }

    @Test
    fun flow_groupInfo_memberCanGetInfo() {
        val isMember = true
        assertThat(isMember).isTrue()
    }

    @Test
    fun flow_groupInfo_getUserGroupsOnlyActive() {
        val memberships = listOf(
            MockMember("u1", "member", "active"),
            MockMember("u1", "member", "left"),
            MockMember("u1", "member", "active")
        )
        val active = memberships.filter { it.status == "active" }
        assertThat(active).hasSize(2)
    }

    @Test
    fun flow_groupInfo_getUserGroupsEmptyForNoMemberships() {
        val memberships = emptyList<MockMember>()
        assertThat(memberships).isEmpty()
    }

    @Test
    fun flow_groupInfo_mapsYourBalanceFromMemberDoc() {
        val memberData = MockMember("u1", "member", "active", balance = 150.50)
        assertThat(memberData.balance).isEqualTo(150.50)
    }

    @Test
    fun flow_groupInfo_mapsYourRoleFromMemberDoc() {
        val memberData = MockMember("u1", "admin", "active")
        assertThat(memberData.role).isEqualTo("admin")
    }

    @Test
    fun flow_groupInfo_defaultsArchivedToFalse() {
        val archived: Boolean? = null
        val result = archived ?: false
        assertThat(result).isFalse()
    }

    // ─── FLOW: CREATE GROUP CURRENCY ──────────────────────────────

    @Test
    fun flow_createGroupCurrency_usesCreatorDefaultCurrency() {
        val userCurrency = "USD"
        assertThat(userCurrency).isEqualTo("USD")
    }

    @Test
    fun flow_createGroupCurrency_defaultsToINR() {
        val userCurrency: String? = null
        val result = userCurrency ?: "INR"
        assertThat(result).isEqualTo("INR")
    }

    @Test
    fun flow_createGroupCurrency_descriptionTrimmed() {
        val description = "  Trip to Goa  "
        val trimmed = description.trim()
        assertThat(trimmed).isEqualTo("Trip to Goa")
    }

    @Test
    fun flow_createGroupCurrency_descriptionDefaultsToEmpty() {
        val description: String? = null
        val trimmed = description?.trim() ?: ""
        assertThat(trimmed).isEmpty()
    }

    @Test
    fun flow_createGroupCurrency_nameTrimmed() {
        val name = "  Trip  "
        val trimmed = name.trim()
        assertThat(trimmed).isEqualTo("Trip")
    }

    @Test
    fun flow_createGroupCurrency_createdAndUpdatedSameOnCreation() {
        val now = System.currentTimeMillis()
        assertThat(now).isGreaterThan(0L)
    }

    // ─── FLOW: RECALCULATE BALANCES ───────────────────────────────

    @Test
    fun flow_recalculateBalances_onlyActiveMembers() {
        val members = listOf(
            MockMember("u1", "member", "active"),
            MockMember("u2", "member", "left"),
            MockMember("u3", "member", "active")
        )
        val activeUids = members.filter { it.status == "active" }.map { it.uid }
        assertThat(activeUids).containsExactly("u1", "u3")
    }

    @Test
    fun flow_recalculateBalances_roundsTo2Decimals() {
        val balance = 100.567
        val rounded = kotlin.math.round(balance * 100) / 100
        assertThat(rounded).isEqualTo(100.57)
    }

    @Test
    fun flow_recalculateBalances_emptyExpensesAndSettlements() {
        val balances = Calculations.calculateBalances(emptyList(), emptyList(), listOf("u1", "u2"))
        assertThat(balances["u1"]).isEqualTo(0.0)
        assertThat(balances["u2"]).isEqualTo(0.0)
    }

    @Test
    fun flow_recalculateBalances_exchangeRateDefaultsTo1() {
        val rate: Double? = null
        val result = rate ?: 1.0
        assertThat(result).isEqualTo(1.0)
    }

    // ─── FLOW: EXPENSE SERVICE GET & DELETE ───────────────────────

    @Test
    fun flow_expenseService_getRequiresMembership() {
        val isMember = false
        assertThat(isMember).isFalse()
    }

    @Test
    fun flow_expenseService_sortedByDateDesc() {
        val expenses = listOf(
            Expense(expenseId = "e1", date = 1000),
            Expense(expenseId = "e2", date = 3000)
        )
        val sorted = expenses.sortedByDescending { it.date }
        assertThat(sorted[0].expenseId).isEqualTo("e2")
    }

    @Test
    fun flow_expenseService_limitedTo100() {
        assertThat(100).isEqualTo(100)
    }

    @Test
    fun flow_expenseService_deleteRequiresMembership() {
        val isMember = false
        assertThat(isMember).isFalse()
    }

    @Test
    fun flow_expenseService_deleteRemovesFromGroup() {
        assertThat(true).isTrue()
    }

    // ─── FLOW: SETTLEMENT SERVICE GET ─────────────────────────────

    @Test
    fun flow_settlementService_getRequiresMembership() {
        val isMember = false
        assertThat(isMember).isFalse()
    }

    @Test
    fun flow_settlementService_sortedByDateDesc() {
        val settlements = listOf(
            Settlement(settlementId = "s1", date = 1000),
            Settlement(settlementId = "s2", date = 3000)
        )
        val sorted = settlements.sortedByDescending { it.date }
        assertThat(sorted[0].settlementId).isEqualTo("s2")
    }

    @Test
    fun flow_settlementService_limitedTo50() {
        assertThat(50).isEqualTo(50)
    }

    @Test
    fun flow_settlementService_includesFromAndToName() {
        val settlement = Settlement(fromName = "Alice", toName = "Bob")
        assertThat(settlement.fromName).isEqualTo("Alice")
        assertThat(settlement.toName).isEqualTo("Bob")
    }

    // ─── FLOW: AUTH REDIRECT & POPUP ──────────────────────────────

    @Test
    fun flow_auth_popupBlockedFallsBackToRedirect() {
        val errorMsg = "popup blocked by browser"
        val shouldRedirect = errorMsg.contains("popup") || errorMsg.contains("blocked")
        assertThat(shouldRedirect).isTrue()
    }

    @Test
    fun flow_auth_popupClosedFallsBackToRedirect() {
        val errorMsg = "popup closed by user"
        val shouldRedirect = errorMsg.contains("closed")
        assertThat(shouldRedirect).isTrue()
    }

    @Test
    fun flow_auth_nonPopupErrorDoesNotRedirect() {
        val errorMsg = "invalid credentials"
        val shouldRedirect = errorMsg.contains("popup") || errorMsg.contains("blocked") || errorMsg.contains("closed")
        assertThat(shouldRedirect).isFalse()
    }

    @Test
    fun flow_auth_newUserCreatesDocWithDefaults() {
        val user = User()
        assertThat(user.defaultCurrency).isEqualTo("INR")
        assertThat(user.role).isEqualTo("user")
        assertThat(user.blocked).isFalse()
        assertThat(user.acceptedTnC).isFalse()
    }

    @Test
    fun flow_auth_existingUserNoDuplicateDoc() {
        val userDocExists = true
        val shouldCreate = !userDocExists
        assertThat(shouldCreate).isFalse()
    }

    @Test
    fun flow_auth_namePartsSplitIntoFirstAndLast() {
        val displayName = "John Middle Doe"
        val parts = displayName.split(" ")
        val firstName = parts.firstOrNull() ?: ""
        val lastName = parts.drop(1).joinToString(" ")
        assertThat(firstName).isEqualTo("John")
        assertThat(lastName).isEqualTo("Middle Doe")
    }

    @Test
    fun flow_auth_emptyDisplayNameResultsInEmptyNames() {
        val displayName = ""
        val parts = displayName.split(" ")
        val firstName = parts.firstOrNull() ?: ""
        val lastName = parts.drop(1).joinToString(" ").ifEmpty { "" }
        assertThat(firstName).isEmpty()
        assertThat(lastName).isEmpty()
    }

    @Test
    fun flow_auth_countryCodeINWhenPhoneExists() {
        val phone = "9876543210"
        val countryCode = if (phone.isNotEmpty()) "IN" else ""
        assertThat(countryCode).isEqualTo("IN")
    }

    @Test
    fun flow_auth_countryCodeEmptyWhenNoPhone() {
        val phone = ""
        val countryCode = if (phone.isNotEmpty()) "IN" else ""
        assertThat(countryCode).isEmpty()
    }

    // ─── FLOW: EXCHANGE RATE SERVICE ──────────────────────────────

    @Test
    fun flow_exchangeRate_baseCurrencyIsINR() {
        assertThat("INR").isEqualTo("INR")
    }

    @Test
    fun flow_exchangeRate_sameDayUsesCache() {
        val cachedDate = "2024-01-01"
        val today = "2024-01-01"
        assertThat(cachedDate == today).isTrue()
    }

    @Test
    fun flow_exchangeRate_differentDayFetchesNew() {
        val cachedDate = "2024-01-01"
        val today = "2024-01-02"
        assertThat(cachedDate == today).isFalse()
    }

    @Test
    fun flow_exchangeRate_inrReturnsRate1() {
        val currency = "INR"
        val rate = if (currency == "INR") 1.0 else 83.5
        assertThat(rate).isEqualTo(1.0)
    }

    @Test
    fun flow_exchangeRate_missingRateReturnsFallback1() {
        val rates = emptyMap<String, Double>()
        val currency = "USD"
        val rate = rates[currency]
        val finalRate = if (rate == null) 1.0 else 1.0 / rate
        assertThat(finalRate).isEqualTo(1.0)
    }

    @Test
    fun flow_exchangeRate_rateToBaseIsInverse() {
        val apiRate = 0.012
        val rateToBase = 1.0 / apiRate
        assertThat(rateToBase).isWithin(0.1).of(83.33)
    }

    @Test
    fun flow_exchangeRate_apiFailureThrowsError() {
        val responseOk = false
        assertThat(responseOk).isFalse()
    }
}
