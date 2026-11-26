package snippetsearcher.permissions.permission

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import snippetsearcher.permissions.TestcontainersConfiguration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Los cambios de estado de Permisos quedan en el log: es lo que permite contestar después
 * "¿quién le dio acceso a este snippet?", y en New Relic sale con el trace id del pedido.
 *
 * Mismas anotaciones que PermissionServiceTest: Spring reusa ese contexto en vez de levantar otro.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PermissionServiceLogTest
    @Autowired
    constructor(
        private val service: PermissionService,
    ) {
        @Test
        fun `registrar un acceso nuevo deja un INFO con el rol, el snippet y el usuario`() {
            val snippetId = UUID.randomUUID()
            val usuario = "auth0|log-registrar"
            val dueno = "auth0|log-registrar-dueno"
            service.register(dueno, snippetId, dueno, "OWNER")

            val logs = infosDuring { service.register(dueno, snippetId, usuario, "READ") }

            assertEquals("Acceso READ registrado: snippet $snippetId, usuario $usuario", logs.single())
        }

        // Un 409 no cambia nada: no hay estado nuevo que contar
        @Test
        fun `registrar un acceso que ya existe no loguea`() {
            val snippetId = UUID.randomUUID()
            service.register("auth0|log-duplicado", snippetId, "auth0|log-duplicado", "OWNER")

            val logs = infosDuring { service.register("auth0|log-duplicado", snippetId, "auth0|log-duplicado", "READ") }

            assertTrue(logs.isEmpty())
        }

        @Test
        fun `borrar los accesos de un snippet deja un INFO con cuantos borro`() {
            val snippetId = UUID.randomUUID()
            val ana = "auth0|log-borrar-ana"
            service.register(ana, snippetId, ana, "OWNER")
            service.register(ana, snippetId, "auth0|log-borrar-beto", "READ")

            val logs = infosDuring { service.deleteAccessesOf(ana, snippetId) }

            assertEquals("Borrados 2 accesos del snippet $snippetId", logs.single())
        }

        // Los INFO de PermissionService mientras corre el bloque, ya con los argumentos reemplazados
        private fun infosDuring(block: () -> Unit): List<String> {
            val logger = LoggerFactory.getLogger(PermissionService::class.java) as Logger
            val appender = ListAppender<ILoggingEvent>().apply { start() }
            logger.addAppender(appender)
            try {
                block()
            } finally {
                logger.detachAppender(appender)
            }
            return appender.list.filter { it.level == Level.INFO }.map { it.formattedMessage }
        }
    }
