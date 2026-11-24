package snippetsearcher.permissions.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Pone el trace id del pedido en el MDC, para que lo lleve **toda** línea de log.
 *
 * El MDC es el contexto aparte del mensaje: se carga una vez cuando entra el request y
 * el formato estructurado lo agrega a cada línea sin que nadie lo escriba. Así el
 * mensaje cuenta solo lo que pasó y el "de qué pedido" va al lado.
 *
 * El trace id lo genera el agente de New Relic y lo propaga entre servicios, así que es
 * el mismo número que usa quien nos llamó. Sin el agente no hay id y no se agrega nada:
 * la app corre igual en local y en los tests.
 *
 * Va primero de todos los filtros a propósito: lo que loguee un filtro que corra antes
 * saldría sin trace id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceContextFilter(
    private val traceContext: TraceContext,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            traceContext.traceId().putIfNotBlank(TRACE_ID)
            traceContext.spanId().putIfNotBlank(SPAN_ID)
            filterChain.doFilter(request, response)
        } finally {
            // Sin esto el contexto queda pegado al thread, y Tomcat los reutiliza: el
            // próximo pedido loguearía con el trace id del anterior.
            MDC.remove(TRACE_ID)
            MDC.remove(SPAN_ID)
        }
    }

    private fun String?.putIfNotBlank(key: String) {
        if (!this.isNullOrBlank()) MDC.put(key, this)
    }

    companion object {
        // Los nombres que usa New Relic para enlazar un log con su traza
        const val TRACE_ID = "trace.id"
        const val SPAN_ID = "span.id"
    }
}
