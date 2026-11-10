package snippetsearcher.permissions.permission

/**
 * Lo que puede pasar al registrar un acceso.
 *
 * Son resultados y no excepciones: el controller los mapea a un status con un `when`
 * exhaustivo, así agregar un caso nuevo no compila hasta que alguien decida qué
 * responder.
 */
sealed interface RegisterAccessResult {
    data class Registered(
        val permission: SnippetPermission,
    ) : RegisterAccessResult

    /** Ese usuario ya tenía un rol sobre ese snippet. */
    data object AlreadyRegistered : RegisterAccessResult

    /** El rol que vino en el pedido no existe. */
    data class InvalidRole(
        val role: String,
    ) : RegisterAccessResult
}
