package snippetsearcher.permissions.observability

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Un trace id a pedido, para poder probar qué pasa cuando sí hay uno. */
private class FakeTraceContext(
    private val trace: String?,
    private val span: String? = null,
) : TraceContext {
    override fun traceId() = trace

    override fun spanId() = span
}

class TraceContextFilterTest {
    private fun pasarPorElFiltro(
        trace: String?,
        span: String? = null,
        cadena: FilterChain,
    ) = TraceContextFilter(FakeTraceContext(trace, span))
        .doFilter(MockHttpServletRequest("GET", "/permissions"), MockHttpServletResponse(), cadena)

    @Test
    fun `el trace id esta en el MDC mientras corre el pedido`() {
        var duranteTrace: String? = null
        var duranteSpan: String? = null

        pasarPorElFiltro("abc123", "span-9") { _, _ ->
            duranteTrace = MDC.get(TraceContextFilter.TRACE_ID)
            duranteSpan = MDC.get(TraceContextFilter.SPAN_ID)
        }

        assertEquals("abc123", duranteTrace)
        assertEquals("span-9", duranteSpan)
    }

    /**
     * Lo que no puede pasar: que el contexto quede pegado al thread.
     *
     * Tomcat reutiliza los threads entre pedidos, así que un trace id que no se limpia
     * se le cuelga al siguiente, que loguearía con el id de otro. Este test falla si se
     * borra el finally del filtro.
     */
    @Test
    fun `limpia el MDC cuando termina`() {
        pasarPorElFiltro("abc123", "span-9") { _, _ ->
            assertTrue(MDC.get(TraceContextFilter.TRACE_ID) != null, "deberia estar puesto acá")
        }

        assertNull(MDC.get(TraceContextFilter.TRACE_ID))
        assertNull(MDC.get(TraceContextFilter.SPAN_ID))
    }

    @Test
    fun `limpia el MDC aunque la cadena falle`() {
        assertFailsWith<IllegalStateException> {
            pasarPorElFiltro("abc123", "span-9") { _, _ -> error("algo exploto adentro") }
        }

        assertNull(MDC.get(TraceContextFilter.TRACE_ID))
        assertNull(MDC.get(TraceContextFilter.SPAN_ID))
    }

    // Sin el agente, la API de New Relic devuelve vacío. No se agrega nada y la app
    // funciona igual: por eso los tests y el desarrollo local no lo necesitan.
    @Test
    fun `sin trace id no ensucia el MDC`() {
        var durante: String? = "sin tocar"

        pasarPorElFiltro(trace = null) { _, _ -> durante = MDC.get(TraceContextFilter.TRACE_ID) }

        assertNull(durante)
    }

    @Test
    fun `un trace id vacio tampoco se pone`() {
        var durante: String? = "sin tocar"

        pasarPorElFiltro(trace = "   ") { _, _ -> durante = MDC.get(TraceContextFilter.TRACE_ID) }

        assertNull(durante)
    }

    @Test
    fun `deja pasar el pedido`() {
        var llamada = false

        pasarPorElFiltro("abc123") { _, _ -> llamada = true }

        assertTrue(llamada)
    }
}
