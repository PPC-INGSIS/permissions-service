package snippetsearcher.permissions.permission

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PermissionService(
    private val repository: SnippetPermissionRepository,
) {
    // El chequeo de existencia y el alta van en la misma transaccion.
    @Transactional
    fun register(
        snippetId: UUID,
        userId: String,
        role: String,
    ): RegisterAccessResult {
        val parsedRole = Role.of(role) ?: return RegisterAccessResult.InvalidRole(role)
        val id = SnippetPermissionId(snippetId, userId)

        if (repository.existsById(id)) return RegisterAccessResult.AlreadyRegistered

        return RegisterAccessResult.Registered(repository.save(SnippetPermission(id, parsedRole)))
    }
}
