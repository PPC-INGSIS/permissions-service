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

            val result = service.register(someUser, snippetId, someUser, "OWNER")

            assertIs<RegisterAccessResult.Registered>(result)
            assertEquals(Role.OWNER, result.permission.role)
            assertEquals(Role.OWNER, repository.findById(SnippetPermissionId(snippetId, someUser)).get().role)
        }

        @Test
        fun `registrar el mismo acceso dos veces no lo duplica`() {
            val snippetId = UUID.randomUUID()
            service.register(someUser, snippetId, someUser, "OWNER")

            val result = service.register(someUser, snippetId, someUser, "READ")

            assertIs<RegisterAccessResult.AlreadyRegistered>(result)
            // El rol original no se pisa
            assertEquals(Role.OWNER, repository.findById(SnippetPermissionId(snippetId, someUser)).get().role)
        }

        @Test
        fun `un rol que no existe no se guarda`() {
            val snippetId = UUID.randomUUID()

            val result = service.register(someUser, snippetId, someUser, "ADMIN")

            assertIs<RegisterAccessResult.InvalidRole>(result)
            assertEquals("ADMIN", result.role)
            assertEquals(false, repository.existsById(SnippetPermissionId(snippetId, someUser)))
        }

        // La clave es (snippet, usuario): el mismo usuario en otro snippet es otro acceso
        @Test
        fun `el mismo usuario puede tener acceso a varios snippets`() {
            val primero = UUID.randomUUID()
            val segundo = UUID.randomUUID()

            val otroDueno = usuarioNuevo()
            service.register(someUser, primero, someUser, "OWNER")
            // El segundo es de otro, que se lo comparte: un usuario accede a snippets propios y ajenos
            service.register(otroDueno, segundo, otroDueno, "OWNER")
            val result = service.register(otroDueno, segundo, someUser, "READ")

            assertIs<RegisterAccessResult.Registered>(result)
            assertEquals(Role.OWNER, repository.findById(SnippetPermissionId(primero, someUser)).get().role)
            assertEquals(Role.READ, repository.findById(SnippetPermissionId(segundo, someUser)).get().role)
        }

        @Test
        fun `un snippet puede estar compartido con varios usuarios`() {
            val snippetId = UUID.randomUUID()
            val otroUser = "auth0|00000000000000000000000a"

            service.register(someUser, snippetId, someUser, "OWNER")
            val result = service.register(someUser, snippetId, otroUser, "READ")

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

            assertIs<RegisterAccessResult.BlankUserId>(service.register(someUser, snippetId, "   ", "OWNER"))
            assertEquals(false, repository.existsById(SnippetPermissionId(snippetId, "   ")))
        }

        @Test
        fun `un userId mas largo que la columna no se guarda`() {
            val largo = "a".repeat(SnippetPermissionId.MAX_USER_ID_LENGTH + 1)

            val result = service.register(largo, UUID.randomUUID(), largo, "OWNER")

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
                            service.register(someUser, snippetId, someUser, "OWNER")
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
            service.register(someUser, snippetId, someUser, "OWNER")

            assertEquals(Role.OWNER, service.findRole(snippetId, someUser))
        }

        @Test
        fun `consultar el rol de alguien con quien se compartio devuelve READ`() {
            val snippetId = UUID.randomUUID()
            val otroUser = "auth0|00000000000000000000000a"
            service.register(someUser, snippetId, someUser, "OWNER")
            service.register(someUser, snippetId, otroUser, "READ")

            assertEquals(Role.READ, service.findRole(snippetId, otroUser))
        }

        @Test
        fun `consultar el rol de un usuario sin acceso devuelve null`() {
            val snippetId = UUID.randomUUID()
            service.register(someUser, snippetId, someUser, "OWNER")

            assertNull(service.findRole(snippetId, "auth0|00000000000000000000000b"))
        }

        // Tener acceso a un snippet no da acceso a otro: la consulta mira la clave entera
        @Test
        fun `el acceso a un snippet no alcanza para otro`() {
            val suyo = UUID.randomUUID()
            val ajeno = UUID.randomUUID()
            service.register(someUser, suyo, someUser, "OWNER")

            assertEquals(Role.OWNER, service.findRole(suyo, someUser))
            assertNull(service.findRole(ajeno, someUser))
        }

        // Los tests comparten la base, asi que listar "todo lo de un usuario" solo da un
        // resultado estable si el usuario es nuevo en cada test
        private fun usuarioNuevo() = "auth0|${UUID.randomUUID()}"

        @Test
        fun `listar sin rol devuelve todos los accesos del usuario`() {
            val usuario = usuarioNuevo()
            val propio = UUID.randomUUID()
            val compartido = UUID.randomUUID()
            val otroDueno = usuarioNuevo()
            service.register(usuario, propio, usuario, "OWNER")
            service.register(otroDueno, compartido, otroDueno, "OWNER")
            service.register(otroDueno, compartido, usuario, "READ")

            val accesos = service.accessesOf(usuario, null)

            assertEquals(2, accesos.size)
            assertEquals(setOf(propio, compartido), accesos.map { it.id.snippetId }.toSet())
        }

        @Test
        fun `listar con rol filtra`() {
            val usuario = usuarioNuevo()
            val propio = UUID.randomUUID()
            val compartido = UUID.randomUUID()
            val otroDueno = usuarioNuevo()
            service.register(usuario, propio, usuario, "OWNER")
            service.register(otroDueno, compartido, otroDueno, "OWNER")
            service.register(otroDueno, compartido, usuario, "READ")

            val soloPropios = service.accessesOf(usuario, Role.OWNER)

            assertEquals(1, soloPropios.size)
            assertEquals(propio, soloPropios.single().id.snippetId)
        }

        @Test
        fun `un usuario sin accesos devuelve lista vacia`() {
            assertEquals(emptyList(), service.accessesOf(usuarioNuevo(), null))
        }

        // Lo que no tiene que pasar: que el listado de uno traiga los accesos de otro
        @Test
        fun `listar no mezcla los accesos de otros usuarios`() {
            val snippetId = UUID.randomUUID()
            val duenio = usuarioNuevo()
            val invitado = usuarioNuevo()
            service.register(duenio, snippetId, duenio, "OWNER")
            service.register(duenio, snippetId, invitado, "READ")

            assertEquals(Role.OWNER, service.accessesOf(duenio, null).single().role)
            assertEquals(Role.READ, service.accessesOf(invitado, null).single().role)
        }

        @Test
        fun `borrar los accesos de un snippet saca el del duenio y los compartidos`() {
            val snippetId = UUID.randomUUID()
            val duenio = usuarioNuevo()
            val invitado = usuarioNuevo()
            service.register(duenio, snippetId, duenio, "OWNER")
            service.register(duenio, snippetId, invitado, "READ")

            val result = service.deleteAccessesOf(duenio, snippetId)

            assertEquals(2, assertIs<DeleteAccessesResult.Deleted>(result).count)
            assertNull(service.findRole(snippetId, duenio))
            assertNull(service.findRole(snippetId, invitado))
        }

        // Lo que no tiene que pasar: que borrar un snippet se lleve los accesos de otro
        @Test
        fun `borrar los accesos de un snippet no toca los de otros`() {
            val borrado = UUID.randomUUID()
            val otro = UUID.randomUUID()
            val duenio = usuarioNuevo()
            service.register(duenio, borrado, duenio, "OWNER")
            service.register(duenio, otro, duenio, "OWNER")

            service.deleteAccessesOf(duenio, borrado)

            assertEquals(Role.OWNER, service.findRole(otro, duenio))
        }

        // El reintento de Snippets: repetir el pedido no falla ni cambia el resultado
        @Test
        fun `borrar los accesos de un snippet sin accesos devuelve cero`() {
            val snippetId = UUID.randomUUID()
            val duenio = usuarioNuevo()
            service.register(duenio, snippetId, duenio, "OWNER")
            service.deleteAccessesOf(duenio, snippetId)

            // Sigue siendo idempotente: sin dueño no hay nada que borrar, y eso no es falta de permisos
            assertEquals(0, assertIs<DeleteAccessesResult.Deleted>(service.deleteAccessesOf(duenio, snippetId)).count)
        }

        private companion object {
            const val TIMEOUT_SEGUNDOS = 10L
        }
    }
