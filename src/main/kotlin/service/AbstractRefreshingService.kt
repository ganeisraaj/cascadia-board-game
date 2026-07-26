package service

/**
 * Abstract service class that handles multiples [Refreshable]s (usually UI elements, such as
 * specialized [tools.aqua.bgw.core.BoardGameScene] classes/instances) which are notified
 * of changes to refresh via the [onAllRefreshables] method.
 */
abstract class AbstractRefreshingService {
    private val refreshables = mutableListOf<Refreshable>()

    /**
     * Adds a new [Refreshable] to the list of refreshables.
     *
     * @param newRefreshable The [Refreshable] to be added
     */
    fun addRefreshable(newRefreshable: Refreshable) {
        refreshables += newRefreshable
    }

    /**
     * Removed a [Refreshable] from the list of refreshables.
     *
     * @param refreshable The [Refreshable] to be removed
     */
    fun removeRefreshable(refreshable: Refreshable) {
        refreshables -= refreshable
    }

    /**
     * Adds each of the provided [Refreshable]s to the list of refreshables.
     *
     * @param method The [Refreshable]s to be added
     */
    fun onAllRefreshables(method: Refreshable.() -> Unit) =
        refreshables.forEach { it.method() }

}