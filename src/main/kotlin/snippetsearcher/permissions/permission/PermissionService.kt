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
        callerId: String,
        snippetId: UUID,
        userId: String,
        role: String,
    ): RegisterAccessResult {
        val parsedRole = Role.of(role) ?: return RegisterAccessResult.InvalidRole(role)
        rejectionFor(callerId, snippetId, userId, parsedRole)?.let { return it }

        val id = SnippetPermissionId(snippetId, userId)

        if (repository.insertIfAbsent(snippetId, userId, parsedRole.name) == 0) {
            // Choco: o ese usuario ya tenia el acceso (el reintento de Snippets, que tiene que seguir
            // saliendo bien), o se registro otro dueño a la vez y gano el. No alcanza con saber que
            // choco: hay que mirar con quien
            return if (repository.existsById(id)) {
                RegisterAccessResult.AlreadyRegistered
            } else {
                RegisterAccessResult.NotOwner
            }
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
    fun deleteAccessesOf(
        callerId: String,
        snippetId: UUID,
    ): DeleteAccessesResult {
        // Solo se rechaza si el snippet tiene OTRO dueño. Sin dueño no hay nada que borrar, y eso no
        // es una falla de permisos: es el reintento de Snippets llegando después de un borrado que
        // sí se hizo, y tiene que seguir siendo idempotente como era antes
        val dueno = ownerOf(snippetId)
        if (dueno != null && dueno != callerId) return DeleteAccessesResult.NotOwner

        val deleted = repository.deleteAllBySnippetId(snippetId)
        log.info("Borrados {} accesos del snippet {}", deleted, snippetId)
        return DeleteAccessesResult.Deleted(deleted)
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

    /**
     * Quién puede registrar qué acceso, decidido con esta misma tabla y nada más.
     *
     * - **OWNER**: solo para uno mismo (`userId == callerId`) y solo si el snippet no tiene otro
     *   dueño. Sin la segunda condición, cualquiera podía registrarse como dueño de un snippet
     *   ajeno —la clave es (snippet, usuario), así que no choca con el dueño real— y quedarse con
     *   todo lo que puede hacer un dueño.
     * - **READ** (compartir): solo el dueño del snippet puede dar acceso de lectura a otro.
     *
     * Que el dueño siga siendo dueño vale como permitido: Snippets reintenta registrar al dueño
     * cuando no le llega la respuesta, y ese reintento tiene que seguir funcionando.
     *
     * No hace falta saber nada de snippets para esto: el dueño está en esta tabla, en la fila con
     * rol OWNER. Es lo que hace que la regla viva acá y no duplicada en Snippets.
     */
    private fun puedeRegistrar(
        callerId: String,
        snippetId: UUID,
        userId: String,
        role: Role,
    ): Boolean {
        val dueno = ownerOf(snippetId)
        return when (role) {
            Role.OWNER -> userId == callerId && (dueno == null || dueno == callerId)
            Role.READ -> dueno == callerId
        }
    }

    private fun ownerOf(snippetId: UUID): String? =
        repository.findByIdSnippetIdAndRole(snippetId, Role.OWNER)?.id?.userId

    /** Por qué no se puede registrar este acceso, o null si se puede. Todas las reglas, juntas. */
    private fun rejectionFor(
        callerId: String,
        snippetId: UUID,
        userId: String,
        role: Role,
    ): RegisterAccessResult? =
        when {
            userId.isBlank() -> RegisterAccessResult.BlankUserId
            userId.length > SnippetPermissionId.MAX_USER_ID_LENGTH -> RegisterAccessResult.UserIdTooLong
            !puedeRegistrar(callerId, snippetId, userId, role) -> RegisterAccessResult.NotOwner
            else -> null
        }
}
