package ghart.space.pi_drive.shared.auto

/**
 * Manages the active page for [SplitPanelScreen].
 *
 * The split panel has two pages:
 * - [Page.HERO] — instant MPG (hero value) + 4 compact pills.
 * - [Page.TILES] — 6 compact metric tiles for RPM, coolant, throttle, fuel, battery, oil temp.
 *
 * Page state is in-memory only; the side panel resets to [Page.HERO] on session restart.
 * [SplitPanelScreen] calls [invalidate] after [togglePage] to re-render the template.
 */
class SplitPageManager {

    enum class Page { HERO, TILES }

    /** The page currently shown in the side panel. Defaults to [Page.HERO]. */
    var currentPage: Page = Page.HERO
        private set

    /**
     * Toggles between [Page.HERO] and [Page.TILES].
     *
     * Called from [SplitPanelScreen] ActionStrip listener. The caller is responsible
     * for calling [Screen.invalidate] after this to trigger a template re-render.
     */
    fun togglePage() {
        currentPage = if (currentPage == Page.HERO) Page.TILES else Page.HERO
    }

    /** Forces the screen to show [Page.HERO]. */
    fun showHero() { currentPage = Page.HERO }

    /** Forces the screen to show [Page.TILES]. */
    fun showTiles() { currentPage = Page.TILES }
}
