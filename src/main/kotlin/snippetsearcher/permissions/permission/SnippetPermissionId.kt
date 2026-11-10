package snippetsearcher.permissions.permission

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

/**
 * La clave de un acceso: a qué snippet y de qué usuario.
 *
 * Es data class porque JPA compara claves por valor: necesita equals y hashCode.
 */
@Embeddable
data class SnippetPermissionId(
    @Column(name = "snippet_id", nullable = false)
    val snippetId: UUID,
    // El "sub" del token de Auth0. Un String opaco: acá no se interpreta, se compara.
    @Column(name = "user_id", nullable = false)
    val userId: String,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
