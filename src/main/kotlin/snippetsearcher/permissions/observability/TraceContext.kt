package snippetsearcher.permissions.observability

import com.newrelic.api.agent.NewRelic
import org.springframework.stereotype.Component

/**
 * De dónde sale el trace id del pedido en curso.
 *
 * Existe para poder reemplazarlo en los tests: `NewRelic.getAgent()` es estático, y sin
 * el agente devuelve siempre vacío, así que un test contra él no podría distinguir
 * "limpia el MDC" de "nunca puso nada".
 */
interface TraceContext {
    fun traceId(): String?

    fun spanId(): String?
}

@Component
class NewRelicTraceContext : TraceContext {
    override fun traceId(): String? = NewRelic.getAgent().traceMetadata.traceId

    override fun spanId(): String? = NewRelic.getAgent().traceMetadata.spanId
}
