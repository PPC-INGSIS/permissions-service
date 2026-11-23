package snippetsearcher.permissions.observability

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import snippetsearcher.permissions.TestcontainersConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Que los logs salgan en JSON no es algo que se vea mirando el código: depende de una
 * property. Estos tests leen la salida real y la parsean.
 *
 * Va con @SpringBootTest porque el formato lo configura Boot al levantar el contexto:
 * sin contexto, logback usa su default y la salida no es JSON.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
class JsonLoggingTest {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun ultimaLineaCon(
        output: CapturedOutput,
        texto: String,
    ) = output.all
        .lineSequence()
        .last { texto in it && it.trimStart().startsWith("{") }

    // ECS anida: el trace.id del MDC sale como {"trace":{"id":"..."}}, no como una clave
    // con punto. Por eso el path navega con puntos.
    private fun campo(
        linea: String,
        nombre: String,
    ): String = JsonPath.read(linea, "$.$nombre")

    @Test
    fun `cada linea de log es un JSON con el servicio y el mensaje`(output: CapturedOutput) {
        log.info("probando el formato de los logs")

        val linea = ultimaLineaCon(output, "probando el formato de los logs")

        assertEquals("probando el formato de los logs", campo(linea, "message"))
        assertEquals("permissions", campo(linea, "service.name"))
    }

    // Lo que hace que un log se pueda cruzar con la traza de Snippets
    @Test
    fun `lo que esta en el MDC sale en la linea`(output: CapturedOutput) {
        MDC.put(TraceContextFilter.TRACE_ID, "abc123")
        try {
            log.info("con trace id")
        } finally {
            MDC.remove(TraceContextFilter.TRACE_ID)
        }

        val linea = ultimaLineaCon(output, "con trace id")

        assertEquals("abc123", campo(linea, TraceContextFilter.TRACE_ID))
    }

    @Test
    fun `el nivel y el logger viajan en el JSON`(output: CapturedOutput) {
        log.warn("una advertencia")

        val linea = ultimaLineaCon(output, "una advertencia")

        assertEquals("WARN", campo(linea, "log.level"))
        assertTrue(campo(linea, "log.logger").isNotBlank())
    }
}
