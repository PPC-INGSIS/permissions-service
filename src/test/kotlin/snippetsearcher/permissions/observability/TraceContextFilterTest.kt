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

class TraceContextFilterTest {
    private val filter = TraceContextFilter()

    private fun pasarPorElFiltro(cadena: FilterChain) =
        filter.doFilter(MockHttpServletRequest("GET", "/permissions"), MockHttpServletResponse(), cadena)

    @Test
    fun `deja pasar el pedido`() {
        var llamada = false

        pasarPorElFiltro { _, _ -> llamada = true }

        assertTrue(llamada)
    }

    /**
     * Lo que no puede pasar: que el contexto quede pegado al thread.
     *
     * Tomcat reutiliza los threads entre pedidos, así que un trace id que no se limpia
     * se le cuelga al siguiente pedido, que loguearía con el id de otro.
     */
    @Test
    fun `limpia el MDC cuando termina`() {
        pasarPorElFiltro { _, _ -> }

        assertNull(MDC.get(TraceContextFilter.TRACE_ID))
        assertNull(MDC.get(TraceContextFilter.SPAN_ID))
    }

    @Test
    fun `limpia el MDC aunque la cadena falle`() {
        assertFailsWith<IllegalStateException> {
            pasarPorElFiltro { _, _ -> error("algo exploto adentro") }
        }

        assertNull(MDC.get(TraceContextFilter.TRACE_ID))
        assertNull(MDC.get(TraceContextFilter.SPAN_ID))
    }

    // Sin el agente, la API de New Relic devuelve vacío: no se agrega nada y la app
    // funciona igual. Por eso los tests y el desarrollo local no necesitan el agente.
    @Test
    fun `sin agente no ensucia el MDC`() {
        var durante: String? = "sin tocar"

        pasarPorElFiltro { _, _ -> durante = MDC.get(TraceContextFilter.TRACE_ID) }

        assertEquals(null, durante)
    }
}
