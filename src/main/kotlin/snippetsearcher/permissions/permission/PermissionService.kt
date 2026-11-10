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

    /**
     * El rol que tiene ese usuario sobre ese snippet, o null si no tiene ninguno.
     *
     * Null y no un sealed interface: los unicos casos son "tiene un rol" y "no tiene",
     * y el segundo no lleva informacion. Role? ya dice exactamente eso.
     */
    @Transactional(readOnly = true)
    fun findRole(
        snippetId: UUID,
        userId: String,
    ): Role? = repository.findById(SnippetPermissionId(snippetId, userId)).orElse(null)?.role
}
