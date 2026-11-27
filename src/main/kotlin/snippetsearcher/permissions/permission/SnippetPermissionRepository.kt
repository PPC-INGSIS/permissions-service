package snippetsearcher.permissions.permission

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SnippetPermissionRepository : JpaRepository<SnippetPermission, SnippetPermissionId> {
    /**
     * Inserta el acceso si no choca con otro. Devuelve 1 si lo inserto, 0 si choco.
     *
     * Choca si ese usuario ya tiene un rol sobre ese snippet (la clave primaria) o si es un OWNER y
     * el snippet ya tiene otro (el indice de V2). El ON CONFLICT va sin columnas para cubrir los dos:
     * con una sola, el choque con la otra sale como excepcion. Si el que gano todavia no commiteo,
     * Postgres espera a que termine antes de decidir, asi que despues se puede preguntar cual fue.
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
            ON CONFLICT DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("snippetId") snippetId: UUID,
        @Param("userId") userId: String,
        @Param("role") role: String,
    ): Int

    /**
     * Borra todos los accesos de un snippet y devuelve cuantos borro.
     *
     * Un solo DELETE y no deleteBy...: el derivado carga cada fila y las borra de a una.
     * Usa la clave primaria, que empieza por snippet_id.
     */
    @Modifying
    @Query("DELETE FROM SnippetPermission p WHERE p.id.snippetId = :snippetId")
    fun deleteAllBySnippetId(
        @Param("snippetId") snippetId: UUID,
    ): Int

    /**
     * El acceso de rol OWNER de ese snippet, o null si todavia no tiene dueno.
     *
     * Pega sobre el prefijo de la clave primaria (snippet_id), asi que usa el indice de la PK.
     * Es lo que permite autorizar sin saber nada de snippets: el dueno esta en esta tabla.
     */
    fun findByIdSnippetIdAndRole(
        snippetId: UUID,
        role: Role,
    ): SnippetPermission?

    /** Todos los accesos del usuario. Usa el indice snippet_permission_user. */
    fun findByIdUserId(userId: String): List<SnippetPermission>

    /** Los accesos del usuario con ese rol. */
    fun findByIdUserIdAndRole(
        userId: String,
        role: Role,
    ): List<SnippetPermission>
}
