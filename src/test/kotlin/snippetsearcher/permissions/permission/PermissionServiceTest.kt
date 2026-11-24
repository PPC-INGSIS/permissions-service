package snippetsearcher.permissions.permission

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import snippetsearcher.permissions.TestcontainersConfiguration
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

        @Test
        fun `un userId vacio no se guarda`() {
            val snippetId = UUID.randomUUID()

            assertIs<RegisterAccessResult.BlankUserId>(service.register(snippetId, "   ", "OWNER"))
            assertEquals(false, repository.existsById(SnippetPermissionId(snippetId, "   ")))
        }

        @Test
        fun `un userId mas largo que la columna no se guarda`() {
            val largo = "a".repeat(SnippetPermissionId.MAX_USER_ID_LENGTH + 1)

            val result = service.register(UUID.randomUUID(), largo, "OWNER")

            assertIs<RegisterAccessResult.UserIdTooLong>(result)
        }

        /**
         * El caso que motivó insertIfAbsent: con existsById + save, varios hilos pasan el
         * chequeo y el segundo en commitear revienta contra la PK, lo que sale como 500.
         *
         * Es probabilístico por naturaleza: la barrera hace que larguen juntos para que la
         * ventana se abra, pero lo que prueba es que no hay ventana que abrir.
         */
        @Test
        fun `altas simultaneas del mismo acceso no fallan y gana una sola`() {
            val snippetId = UUID.randomUUID()
            val hilos = 8
            val listos = CountDownLatch(hilos)
            val largada = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(hilos)

            val pendientes =
                (1..hilos).map {
                    pool.submit(
                        Callable {
                            listos.countDown()
                            largada.await()
                            service.register(snippetId, someUser, "OWNER")
                        },
                    )
                }

            listos.await()
            largada.countDown()
            // Si alguno tiró excepción, get() la propaga y el test falla acá
            val resultados = pendientes.map { it.get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS) }
            pool.shutdown()

            assertEquals(1, resultados.count { it is RegisterAccessResult.Registered })
            assertEquals(hilos - 1, resultados.count { it is RegisterAccessResult.AlreadyRegistered })
            assertEquals(Role.OWNER, repository.findById(SnippetPermissionId(snippetId, someUser)).get().role)
        }

        @Test
        fun `consultar el rol del dueno devuelve OWNER`() {
            val snippetId = UUID.randomUUID()
            service.register(snippetId, someUser, "OWNER")

            assertEquals(Role.OWNER, service.findRole(snippetId, someUser))
        }

        @Test
        fun `consultar el rol de alguien con quien se compartio devuelve READ`() {
            val snippetId = UUID.randomUUID()
            val otroUser = "auth0|00000000000000000000000a"
            service.register(snippetId, otroUser, "READ")

            assertEquals(Role.READ, service.findRole(snippetId, otroUser))
        }

        @Test
        fun `consultar el rol de un usuario sin acceso devuelve null`() {
            val snippetId = UUID.randomUUID()
            service.register(snippetId, someUser, "OWNER")

            assertNull(service.findRole(snippetId, "auth0|00000000000000000000000b"))
        }

        // Tener acceso a un snippet no da acceso a otro: la consulta mira la clave entera
        @Test
        fun `el acceso a un snippet no alcanza para otro`() {
            val suyo = UUID.randomUUID()
            val ajeno = UUID.randomUUID()
            service.register(suyo, someUser, "OWNER")

            assertEquals(Role.OWNER, service.findRole(suyo, someUser))
            assertNull(service.findRole(ajeno, someUser))
        }

        private companion object {
            const val TIMEOUT_SEGUNDOS = 10L
        }
    }
