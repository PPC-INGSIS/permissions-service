# Permisos

## Autenticación

Todos los endpoints, salvo `/actuator/health`, exigen un token de Auth0 (presentación de Auth0, diap. 48: "validar el token en cada servicio" y "chequear el scope"). La regla sale del método HTTP, igual que en Snippets:

| Pedido | Scope que exige |
|---|---|
| `GET` | `read:snippets` |
| `POST`, `PUT`, `DELETE` | `write:snippets` |

Sin token responde `401`; con un token sin el scope, `403`. Snippets reenvía el token del usuario en cada llamada.

Para correrlo en local hacen falta las variables de Auth0: copiá `.env.example` como `.env` y completalo con los mismos valores que el `.env` de snippet-service.
