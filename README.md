# Permisos

Servicio de **Snippet Searcher** encargado de los accesos. Responde una sola pregunta: **¿qué puede hacer este usuario sobre este snippet?**

Auth0 sabe *quién es* el usuario, pero no sabe de quién es cada snippet ni con quién se compartió. Eso es dominio, y vive acá. Snippets consulta a este servicio antes de dejar ver, editar o compartir.

No conoce a los snippets más allá de su id: el nombre, el lenguaje y el contenido son de Snippets y del asset service.

## Qué guarda

Una sola tabla, `snippet_permission`, con la relación usuario ↔ snippet:

| Columna | Qué es |
|---|---|
| `snippet_id` | El snippet |
| `user_id` | El `sub` del token de Auth0. Un string opaco: acá no se interpreta, se compara |
| `role` | `OWNER` (creó el snippet) o `READ` (se lo compartieron) |

La clave primaria es **`(snippet_id, user_id)`**: un usuario tiene a lo sumo un rol por snippet, y eso lo sostiene la base, no una validación que alguien se pueda olvidar.

Es una **ACL** (lista de accesos por recurso), que es lo que recomienda la presentación de Auth0 para el caso "el dueño edita y borra; con quien se compartió, lee".

## Stack

| | |
|---|---|
| Lenguaje | Kotlin 2.3 sobre Java 21 |
| Framework | Spring Boot 4.1 (Web MVC, Data JPA, Security, Actuator) |
| Base de datos | PostgreSQL 18 |
| Migraciones | Flyway |
| Build | Gradle (Kotlin DSL) |
| Calidad | ktlint, detekt, JaCoCo |
| Tests | JUnit 5 y Testcontainers |
| Observabilidad | Agente de Java de New Relic |

## Requisitos

- Java 21
- Docker (para la base y para los tests)

## Correrlo localmente

```bash
# 1. Levantar Postgres
docker compose up -d
```

```bash
# 2. Levantar el servicio
./gradlew bootRun
```

El servicio queda escuchando en `http://localhost:8080`. Se corta con `Ctrl+C`.

Para verificar que está funcionando y que llega a la base:

```bash
curl localhost:8080/actuator/health
# {"status":"UP"}  → todo bien
# {"status":"DOWN"} → el servicio corre, pero la base no responde
```

Para apagar la base: `docker compose down`. Los datos se conservan en el volumen `postgres-data`; para borrarlos también, `docker compose down -v`.

## Configuración

La base funciona sin configurar nada. **Las variables de Auth0 no tienen default**: si faltan, el servicio no arranca.

```bash
cp .env.example .env
```

| Variable | Default | Qué es |
|---|---|---|
| `AUTH0_DOMAIN` | — | Dominio del tenant. Sin esto no arranca |
| `AUTH0_AUDIENCE` | — | Identifier de la API en Auth0. Sin esto no arranca |
| `DB_PORT` | `5434` | Puerto de la base en tu máquina |
| `POSTGRES_DB` | `permissions` | Nombre de la base |
| `POSTGRES_USER` | `permissions` | Usuario de la base |
| `POSTGRES_PASSWORD` | `permissions` | Contraseña de la base |
| `LOG_FORMAT` | `ecs` | Formato de los logs. Vacío los deja en texto plano |

Los valores de Auth0 son los mismos que los del `.env` de snippet-service.

El mismo `.env` lo leen **Docker Compose** (para crear la base) y **Spring** (para conectarse), así que los dos quedan siempre sincronizados.

- `.env` está en el `.gitignore`: es de cada máquina y **nunca** se sube.
- `.env.example` sí se sube: documenta qué variables existen.

La base solo acepta conexiones desde la propia máquina (`127.0.0.1`), no desde la red. Por eso las credenciales por defecto sirven únicamente para desarrollo local. En otros entornos se pasan por variables de entorno o secretos, nunca por el repo.

## Endpoints

### `POST /permissions`

Registra un acceso. Lo llama Snippets al crear un snippet, con el dueño.

```json
{ "snippetId": "...", "userId": "auth0|...", "role": "OWNER" }
```

| Respuesta | Cuándo |
|---|---|
| `201` | Se registró |
| `409` | Ese usuario ya tenía un rol sobre ese snippet |
| `400` | El rol no es `OWNER` ni `READ`, o falta el `userId` |
| `403` | El que pide no puede registrar ese acceso (ver [Autorización](#autorización)) |

El `role` viaja como texto y no como enum a propósito: si fuera el enum, un valor inválido lo rechazaría Jackson con una excepción y el caso dejaría de ser un resultado del dominio.

### `GET /permissions/{snippetId}?userId=...`

El rol de ese usuario sobre ese snippet.

| Respuesta | Cuándo |
|---|---|
| `200` | `{ "snippetId": "...", "userId": "...", "role": "OWNER" }` |
| `404` | Ese usuario no tiene acceso |
| `403` | El `userId` no es el del token: solo se puede preguntar por uno mismo |

El `userId` va como **query param y no en el path**: es el `sub` de Auth0 y según el proveedor puede traer `/`. Medido contra un servidor real, en el path un `%2F` devuelve **400** antes de llegar al controller, y un `/` sin codificar cae en **otra ruta** sin avisar.

### `DELETE /permissions/{snippetId}`

Borra **todos** los accesos de un snippet, el del dueño y los compartidos. Lo llama Snippets al borrar un snippet.

| Respuesta | Cuándo |
|---|---|
| `204` | Se borraron, **y también si no había ninguno** |
| `403` | El snippet tiene otro dueño |

Responde `204` aunque no hubiera accesos porque el pedido es idempotente: repetirlo —porque la respuesta se perdió en la red o porque quien llama lo vuelve a mandar— tiene que dar el mismo resultado, y la segunda vez ya no hay nada que borrar. Snippets hoy no reintenta solo, pero la regla no depende de eso. Por eso un snippet **sin dueño** tampoco se rechaza: "no hay nada que borrar" no es una falla de permisos.

### `GET /permissions?userId=...&role=...`

Los snippets a los que accede un usuario, con su rol. El `role` filtra y es opcional.

Sin accesos devuelve `200 []`, no `404`: el usuario existe y la pregunta tiene respuesta, que es "ninguno".

| Respuesta | Cuándo |
|---|---|
| `200` | La lista, vacía o no |
| `403` | El `userId` no es el del token: solo se pueden listar los propios |

## Autenticación

Todos los endpoints, salvo `/actuator/health`, exigen un token de Auth0 (presentación de Auth0, diap. 48: "validar el token en cada servicio" y "chequear el scope"). La regla sale del método HTTP, igual que en Snippets:

| Pedido | Scope que exige |
|---|---|
| `GET` | `read:snippets` |
| `POST`, `PUT`, `DELETE` | `write:snippets` |

Sin token responde `401`; con un token sin el scope, `403`. Snippets reenvía el token del usuario en cada llamada.

El token dice **quién llama**. Lo que ese usuario puede hacer se decide aparte, con la tabla: ver [Autorización](#autorización).

## Autorización

Validar el token no alcanza: dice que quien llama es quien dice ser, no que pueda hacer lo que pide. Hasta #28, el usuario sobre el que se actuaba venía en el cuerpo o en el query, así que con un token válido cualquiera —y todo usuario logueado tiene uno— se podía registrar un acceso sobre un snippet ajeno, borrar los accesos de cualquiera o enumerar los snippets de otro.

Ahora cada operación se decide con el `sub` del token y **con esta misma tabla**: el dueño de un snippet es su fila con rol `OWNER`.

| Operación | Quién puede |
|---|---|
| `POST` con rol `OWNER` | Solo uno mismo (`userId` == `sub`), y solo si el snippet no tiene **otro** dueño |
| `POST` con rol `READ` | Solo el dueño del snippet |
| `DELETE /{snippetId}` | Solo el dueño. Un snippet sin dueño no se rechaza: no hay nada que borrar |
| `GET /{snippetId}?userId=` | Solo sobre uno mismo |
| `GET ?userId=` | Solo los propios |

Decisiones y por qué:

- **La regla vive acá y no en Snippets.** El dueño ya está en esta tabla, así que no hace falta saber qué es un snippet ni duplicar en dos repos una regla que después se desincroniza.
- **Que el dueño siga siendo dueño vale como permitido.** Si el mismo registro de dueño llega dos veces —un pedido repetido por la red o por quien llama—, el segundo tiene que dar `409` (ya lo tenía) y no `403`: no cambió nada en los permisos. Snippets hoy no reintenta solo; la regla es para que repetir un pedido nunca convierta un éxito en un rechazo.
- **Un solo `OWNER` por snippet lo sostiene la base**, con un índice único parcial (`V2`). El chequeo y el `INSERT` son dos statements, así que dos dueños a la vez pasarían los dos: la clave es `(snippet, usuario)` y no chocan entre sí.
- **El `403` dice siempre lo mismo.** Explicar por qué no se puede —"ese snippet ya tiene dueño", "no sos el dueño"— le contaría a cualquiera si un snippet existe y de quién es.

### Por qué no hay una identidad de servicio

Permisos solo reconoce usuarios: no tiene forma de que Snippets diga "soy yo, actuando por mi cuenta". **Hoy no hace falta**, y conviene saber por qué, porque el día que haga falta no es obvio.

Todo lo que llama a Permisos nace de un pedido de un usuario, y Snippets le reenvía ese token: crear un snippet (registra al dueño), compartirlo, borrarlo, y las dos consultas de acceso. En los cinco casos el `sub` es el del usuario que corresponde.

Los trabajos en segundo plano **no llaman a Permisos**: el linteo y el formateo automáticos (snippet-service#36 y #37) y la corrida de tests (#38) trabajan sobre los snippets **propios** del usuario, que Snippets encuentra con su propia columna `owner_id`, y no cambian ningún acceso. Lo que sí necesitan es un token para PrintScript, que es lo que da snippet-service#50 — y PrintScript solo pide `read:snippets`.

**Cuándo deja de alcanzar:** si alguna vez un trabajo sin usuario tiene que registrar o borrar accesos, su token M2M tiene `sub = <client-id>@clients`, que nunca va a ser dueño de nada, y va a recibir `403`. Ahí hay que agregarle un scope propio a la app M2M (por ejemplo `admin:permissions`), habilitado solo para ella y no para la SPA, y aceptarlo acá como "puede operar sobre cualquier snippet". No antes: un scope que nadie usa es una puerta abierta sin nadie que la cruce.

## Observabilidad

El servicio manda trazas, métricas y logs a New Relic con el **agente de Java**, según lo decidido en [infraestructura#46](https://github.com/PPC-INGSIS/infraestructura/issues/46).

- El jar del agente **no es una dependencia de la app**: se baja en la configuración `newRelicAgent` de Gradle y la tarea `copyNewRelicAgent` lo deja en `build/newrelic/`. El Dockerfile lo copia a la imagen y arranca con `-javaagent:/app/newrelic.jar`.
- `newrelic/newrelic.yml` lleva **valores literales**: el agente **no entiende `${VAR}` en ese archivo**, lo deja como texto. Lo que cambia por entorno lo pisan las variables `NEW_RELIC_*`, que el agente lee solo. Por eso `app_name` dice `permissions-local` y en las VMs lo reemplaza `NEW_RELIC_APP_NAME`.
- En local queda apagado: el compose de `infraestructura` manda `NEW_RELIC_AGENT_ENABLED=false`.

Los logs salen en **JSON (formato ECS)** a la salida estándar. `TraceContextFilter` pone el `trace.id` del agente en el MDC al entrar cada pedido, y el formato lo agrega a cada línea sin que nadie lo escriba. Es el mismo trace id que propaga el agente entre servicios, así que un log de Permisos se puede cruzar con la traza del pedido que lo originó.

Sin el agente, la API de New Relic devuelve vacío y no se agrega nada: la app corre igual en local y en los tests.

## Verificar

```bash
./gradlew check
```

Corre, en orden:

- **ktlint**: formato del código (espacios, saltos de línea, imports). Reglas en `.editorconfig`.
- **detekt**: estructura del código (complejidad, cantidad de returns, nombres). Reglas en `config/detekt/detekt.yml`.
- **Tests**, con un Postgres descartable levantado por Testcontainers (necesita Docker abierto).
- **Cobertura**: falla si queda por debajo del 80%. El reporte queda en `build/reports/jacoco/test/html/index.html`.

Si ktlint falla, la mayoría de los problemas se corrigen solos:

```bash
./gradlew ktlintFormat
```

## Git hooks

Se instalan solos la primera vez que corrés `./gradlew check`. Viven en `.githooks/` y se copian a `.git/hooks/`.

| Hook | Cuándo | Qué corre |
|---|---|---|
| `pre-commit` | Antes de cada commit | ktlint y detekt |
| `pre-push` | Antes de cada push | `./gradlew check` completo (necesita Docker abierto) |

## CI

Cada pull request a `dev` o `main` corre `./gradlew check` en GitHub Actions, con el workflow reusable compartido por todos los servicios del proyecto.

Al mergear a `dev` se construye la imagen, se publica en `ghcr.io` con el hash del commit como tag, y se actualiza `PERMISSIONS_TAG` en `versiones.env` de `infraestructura`, que dispara el deploy.

## Si desarrollás en Windows

`gradlew` tiene que quedar con LF: lo fuerza el `.gitattributes`. Con CRLF el shebang queda `#!/bin/sh\r`, que adentro de un contenedor Linux no existe, y `docker build` falla con `./gradlew: not found`.

Los tests necesitan la zona horaria canónica, porque Java en Windows resuelve el alias viejo `America/Buenos_Aires` y Postgres 18 no lo acepta. Hasta que salga la versión de `gradle-conventions` con el arreglo:

```bash
./gradlew --stop && export JAVA_TOOL_OPTIONS="-Duser.timezone=America/Argentina/Buenos_Aires"
```
