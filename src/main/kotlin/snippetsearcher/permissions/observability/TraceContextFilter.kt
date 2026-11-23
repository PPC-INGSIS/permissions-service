package snippetsearcher.permissions.observability

import com.newrelic.api.agent.NewRelic
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Pone el trace id del pedido en el MDC, para que lo lleve **toda** línea de log.
 *
 * El MDC es el contexto aparte del mensaje: se carga una vez cuando entra el request y
 * el formato estructurado lo agrega a cada línea sin que nadie lo escriba. Así el
 * mensaje cuenta solo lo que pasó y el "de quién" y "de qué pedido" van al lado.
 *
 * El trace id lo genera el agente de New Relic y lo propaga entre servicios, así que es
 * el mismo número que usa Snippets para el pedido que nos llamó. Sin el agente, la API
 * devuelve vacío y no se agrega nada: la app corre igual en local y en los tests.
 */
@Component
class TraceContextFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val trace = NewRelic.getAgent().traceMetadata

        try {
            trace.traceId.putIfNotBlank(TRACE_ID)
            trace.spanId.putIfNotBlank(SPAN_ID)
            filterChain.doFilter(request, response)
        } finally {
            // Sin esto el contexto queda pegado al thread, y el thread se reutiliza: el
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
