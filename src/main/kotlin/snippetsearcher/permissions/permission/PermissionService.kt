package snippetsearcher.permissions.permission

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PermissionService(
    private val repository: SnippetPermissionRepository,
) {
    // El duplicado lo decide la base en un solo statement, no un chequeo previo: ver
    // insertIfAbsent.
    @Transactional
    fun register(
        snippetId: UUID,
        userId: String,
        role: String,
    ): RegisterAccessResult {
        rejectionFor(userId)?.let { return it }

        val parsedRole = Role.of(role) ?: return RegisterAccessResult.InvalidRole(role)
        val id = SnippetPermissionId(snippetId, userId)

        if (repository.insertIfAbsent(snippetId, userId, parsedRole.name) == 0) {
            return RegisterAccessResult.AlreadyRegistered
        }

        return RegisterAccessResult.Registered(SnippetPermission(id, parsedRole))
    }

    /** Por qué no sirve este userId, o null si sirve. Las reglas del userId, juntas. */
    private fun rejectionFor(userId: String): RegisterAccessResult? =
        when {
            userId.isBlank() -> RegisterAccessResult.BlankUserId
            userId.length > SnippetPermissionId.MAX_USER_ID_LENGTH -> RegisterAccessResult.UserIdTooLong
            else -> null
        }
}
