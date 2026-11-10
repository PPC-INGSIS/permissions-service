package snippetsearcher.permissions.permission

/**
 * Qué puede hacer un usuario sobre un snippet.
 *
 * OWNER es quien lo creó y puede todo (US 2, 4 y 8). READ es con quien se compartió:
 * lee el snippet y sus tests, nada más (US 7).
 */
enum class Role {
    OWNER,
    READ,
    ;

    companion object {
        /** Null si el texto no es un rol: el que llama decide qué hacer con eso. */
        fun of(value: String): Role? = entries.firstOrNull { it.name == value }
    }
}
