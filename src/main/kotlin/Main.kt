import gui.CascadiaApplication

/**
 * Main entry point that starts the [CascadiaApplication]
 *
 * Once the application is closed, it prints a message indicating the end of the application.
 */
fun main() {
    CascadiaApplication().show()
    println("Application ended. Goodbye")
}