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

    /** Sin userId no hay a quién darle el acceso. */
    data object BlankUserId : RegisterAccessResult

    /**
     * El que pide no es el dueno del snippet, o quiso registrarse como dueno de uno que ya tiene otro.
     *
     * Un solo caso y no dos: distinguirlos le diria a cualquiera si un snippet ya tiene dueno, o
     * sea si existe, que es justo lo que no se cuenta.
     */
    data object NotOwner :
        RegisterAccessResult,
        NotAllowed

    /** Más largo que la columna: sin esto, la base lo rechaza y sale un 500. */
    data object UserIdTooLong : RegisterAccessResult
}

sealed interface DeleteAccessesResult {
    data class Deleted(
        val count: Int,
    ) : DeleteAccessesResult

    /** El que pide no es el dueno del snippet. */
    data object NotOwner :
        DeleteAccessesResult,
        NotAllowed
}

/**
 * Quien pide no puede hacer eso.
 *
 * Es un resultado y no una excepcion por lo mismo que el resto: el pedido llego bien formado y
 * autenticado, lo que no alcanza son los permisos de quien lo manda.
 */
sealed interface NotAllowed
