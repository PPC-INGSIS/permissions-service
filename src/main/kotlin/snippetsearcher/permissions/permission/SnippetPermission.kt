package snippetsearcher.permissions.permission

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

/**
 * Lo que un usuario puede hacer sobre un snippet.
 *
 * Del snippet guarda solo el id: el nombre, el lenguaje y el contenido son de Snippets
 * y del asset service.
 */
@Entity
@Table(name = "snippet_permission")
class SnippetPermission(
    // La clave es (snippet_id, user_id): un usuario tiene a lo sumo un rol por snippet,
    // y eso queda sostenido por la clave primaria, no por una validación que alguien
    // se pueda olvidar.
    @EmbeddedId
    val id: SnippetPermissionId,
    // STRING y no ORDINAL: con ORDINAL, reordenar el enum cambiaría en silencio el
    // significado de lo que ya está guardado.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: Role,
)
