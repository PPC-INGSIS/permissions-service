package snippetsearcher.permissions.permission

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PermissionService(
    private val repository: SnippetPermissionRepository,
) {
    // Se loguean los cambios de estado (quién ganó o perdió acceso), no las consultas: esas son
    // lecturas de cada pedido y llenarían los logs sin decir nada. Sale con el trace id del agente.
    private val log = LoggerFactory.getLogger(javaClass)

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

        // El userId es el sub de Auth0: un identificador opaco, no el mail ni el nombre
        log.info("Acceso {} registrado: snippet {}, usuario {}", parsedRole, snippetId, userId)
        return RegisterAccessResult.Registered(SnippetPermission(id, parsedRole))
    }

    /**
     * Borra todos los accesos de un snippet, el del dueño y los compartidos.
     *
     * Devuelve cuantos borro, y 0 no es un error: si el pedido se repite (Snippets reintenta
     * cuando no llega la respuesta), la segunda vez ya no hay nada que borrar y el resultado
     * final es el mismo que si se hubiera hecho una sola vez.
     */
    @Transactional
    fun deleteAccessesOf(snippetId: UUID): Int {
        val deleted = repository.deleteAllBySnippetId(snippetId)
        log.info("Borrados {} accesos del snippet {}", deleted, snippetId)
        return deleted
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

    /**
     * Los accesos de un usuario, filtrados por rol si se pide uno.
     *
     * Devuelve lista y no null cuando no hay nada: "no accede a ningun snippet" es una
     * respuesta valida, no la ausencia de una respuesta.
     */
    @Transactional(readOnly = true)
    fun accessesOf(
        userId: String,
        role: Role?,
    ): List<SnippetPermission> =
        if (role == null) {
            repository.findByIdUserId(userId)
        } else {
            repository.findByIdUserIdAndRole(userId, role)
        }

    /** Por qué no sirve este userId, o null si sirve. Las reglas del userId, juntas. */
    private fun rejectionFor(userId: String): RegisterAccessResult? =
        when {
            userId.isBlank() -> RegisterAccessResult.BlankUserId
            userId.length > SnippetPermissionId.MAX_USER_ID_LENGTH -> RegisterAccessResult.UserIdTooLong
            else -> null
        }
}
