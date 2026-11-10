package snippetsearcher.permissions.permission

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/permissions")
class PermissionController(
    private val service: PermissionService,
) {
    @PostMapping
    fun register(
        @RequestBody request: RegisterAccessRequest,
    ): ResponseEntity<out Any> =
        when (val result = service.register(request.snippetId, request.userId, request.role)) {
            is RegisterAccessResult.Registered ->
                ResponseEntity.status(HttpStatus.CREATED).body(result.permission.toResponse())

            is RegisterAccessResult.AlreadyRegistered ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ErrorResponse("El usuario ya tiene un rol sobre ese snippet"),
                )

            is RegisterAccessResult.InvalidRole ->
                ResponseEntity.badRequest().body(
                    // Los validos salen del enum: agregar un rol no deja este mensaje viejo
                    ErrorResponse("El rol '${result.role}' no existe. Los validos son: ${Role.entries.joinToString()}"),
                )

            is RegisterAccessResult.BlankUserId ->
                ResponseEntity.badRequest().body(ErrorResponse("Falta el userId"))

            is RegisterAccessResult.UserIdTooLong ->
                ResponseEntity.badRequest().body(
                    ErrorResponse(
                        "El userId no puede superar los " +
                            "${SnippetPermissionId.MAX_USER_ID_LENGTH} caracteres",
                    ),
                )
        }

    // El userId es el "sub" de Auth0 y trae caracteres como "|": el cliente lo manda
    // percent-encoded y Spring lo decodifica antes de que llegue acá.
    @GetMapping("/{snippetId}/users/{userId}")
    fun role(
        @PathVariable snippetId: UUID,
        @PathVariable userId: String,
    ): ResponseEntity<out Any> =
        service.findRole(snippetId, userId)?.let {
            ResponseEntity.ok(PermissionResponse(snippetId, userId, it))
        } ?: ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse("El usuario no tiene acceso a ese snippet"),
        )
}

// El rol viaja como texto y no como Role: si fuera el enum, un valor invalido lo
// rechazaria Jackson con una excepcion y el caso dejaria de ser un resultado.
data class RegisterAccessRequest(
    val snippetId: UUID,
    val userId: String,
    val role: String,
)

data class PermissionResponse(
    val snippetId: UUID,
    val userId: String,
    val role: Role,
)

data class ErrorResponse(
    val message: String,
)

private fun SnippetPermission.toResponse() = PermissionResponse(id.snippetId, id.userId, role)
