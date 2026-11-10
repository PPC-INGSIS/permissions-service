package snippetsearcher.permissions.permission

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SnippetPermissionRepository : JpaRepository<SnippetPermission, SnippetPermissionId> {
    /**
     * Inserta el acceso si todavia no existe. Devuelve 1 si lo inserto, 0 si ya estaba.
     *
     * Chequear con existsById y despues guardar deja una ventana entre las dos
     * consultas: dos pedidos simultaneos pasan los dos el chequeo y el segundo revienta
     * contra la clave primaria al commitear. Postgres resuelve el conflicto dentro del
     * mismo statement, asi que no hay ventana.
     *
     * Ademas evita el SELECT de save(): con @EmbeddedId y sin @Version, Hibernate no
     * sabe si la entidad es nueva, hace merge y lee antes de escribir.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO snippet_permission (snippet_id, user_id, role)
            VALUES (:snippetId, :userId, :role)
            ON CONFLICT (snippet_id, user_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("snippetId") snippetId: UUID,
        @Param("userId") userId: String,
        @Param("role") role: String,
    ): Int
}
