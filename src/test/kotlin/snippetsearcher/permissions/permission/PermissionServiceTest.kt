package snippetsearcher.permissions.permission

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import snippetsearcher.permissions.TestcontainersConfiguration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PermissionServiceTest
    @Autowired
    constructor(
        private val service: PermissionService,
        private val repository: SnippetPermissionRepository,
    ) {
        private val someUser = "auth0|65f3a1b2c3d4e5f6a7b8c9d0"

        @Test
        fun `registrar un acceso nuevo lo deja guardado`() {
            val snippetId = UUID.randomUUID()

            val result = service.register(snippetId, someUser, "OWNER")

            assertIs<RegisterAccessResult.Registered>(result)
            assertEquals(Role.OWNER, result.permission.role)
            assertEquals(Role.OWNER, repository.findById(SnippetPermissionId(snippetId, someUser)).get().role)
        }

        @Test
        fun `registrar el mismo acceso dos veces no lo duplica`() {
            val snippetId = UUID.randomUUID()
            service.register(snippetId, someUser, "OWNER")

            val result = service.register(snippetId, someUser, "READ")

            assertIs<RegisterAccessResult.AlreadyRegistered>(result)
            // El rol original no se pisa
            assertEquals(Role.OWNER, repository.findById(SnippetPermissionId(snippetId, someUser)).get().role)
        }

        @Test
        fun `un rol que no existe no se guarda`() {
            val snippetId = UUID.randomUUID()

            val result = service.register(snippetId, someUser, "ADMIN")

            assertIs<RegisterAccessResult.InvalidRole>(result)
            assertEquals("ADMIN", result.role)
            assertEquals(false, repository.existsById(SnippetPermissionId(snippetId, someUser)))
        }

        // La clave es (snippet, usuario): el mismo usuario en otro snippet es otro acceso
        @Test
        fun `el mismo usuario puede tener acceso a varios snippets`() {
            val primero = UUID.randomUUID()
            val segundo = UUID.randomUUID()

            service.register(primero, someUser, "OWNER")
            val result = service.register(segundo, someUser, "READ")

            assertIs<RegisterAccessResult.Registered>(result)
            assertEquals(Role.OWNER, repository.findById(SnippetPermissionId(primero, someUser)).get().role)
            assertEquals(Role.READ, repository.findById(SnippetPermissionId(segundo, someUser)).get().role)
        }

        @Test
        fun `un snippet puede estar compartido con varios usuarios`() {
            val snippetId = UUID.randomUUID()
            val otroUser = "auth0|00000000000000000000000a"

            service.register(snippetId, someUser, "OWNER")
            val result = service.register(snippetId, otroUser, "READ")

            assertIs<RegisterAccessResult.Registered>(result)
            assertEquals(Role.READ, repository.findById(SnippetPermissionId(snippetId, otroUser)).get().role)
        }

        @Test
        fun `el rol se compara por nombre exacto`() {
            assertEquals(Role.OWNER, Role.of("OWNER"))
            assertEquals(Role.READ, Role.of("READ"))
            assertNull(Role.of("owner"))
            assertNull(Role.of(""))
        }
    }
