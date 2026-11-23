# Permisos

## Observabilidad

El servicio manda trazas, metricas y logs a New Relic con el **agente de Java**, segun lo
decidido en [infraestructura#46](https://github.com/PPC-INGSIS/infraestructura/issues/46).

- El jar del agente **no es una dependencia de la app**: se baja en la configuracion
  `newrelicAgent` de Gradle y la tarea `copyNewRelicAgent` lo deja en `build/newrelic/`.
  El Dockerfile lo copia a la imagen y arranca con `-javaagent:/app/newrelic.jar`.
- `newrelic/newrelic.yml` lleva **valores literales**: el agente no entiende `${VAR}` en
  ese archivo. Lo que cambia por entorno lo pisan las variables `NEW_RELIC_*`, que el
  agente lee solo. Por eso `app_name` dice `permissions-local` y en las VMs lo reemplaza
  `NEW_RELIC_APP_NAME`.
- En local queda apagado: el compose manda `NEW_RELIC_AGENT_ENABLED=false`.

Los logs salen en **JSON (formato ECS)** a la salida estandar. `TraceContextFilter` pone el
`trace.id` del agente en el MDC al entrar cada pedido, y el formato lo agrega a cada linea
sin que nadie lo escriba. Es el mismo trace id que propaga el agente entre servicios, asi
que un log de Permisos se puede cruzar con la traza del pedido que lo origino.

Sin el agente, la API de New Relic devuelve vacio y no se agrega nada: la app corre igual
en local y en los tests.
