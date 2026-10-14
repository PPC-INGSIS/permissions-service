package snippetsearcher.permissions

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PermissionsApplication

// Una sola copia de los argumentos al arrancar: no hay costo real que evitar
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<PermissionsApplication>(*args)
}
