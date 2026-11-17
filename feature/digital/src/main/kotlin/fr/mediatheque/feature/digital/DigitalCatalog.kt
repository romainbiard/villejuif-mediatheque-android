// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.digital

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import fr.mediatheque.core.data.DigitalService
import fr.mediatheque.core.designsystem.R as DesignR

/** A digital service, described in the app (from the website's tutorials). */
data class DigitalOffer(
    val id: String,
    @StringRes val category: Int,
    @StringRes val provider: Int,
    @StringRes val summary: Int,
    @StringRes val quota: Int,
    /** The portal module that opens the session with the provider. */
    val service: DigitalService,
    val app: PartnerApp?,
    /** First-use steps, three at most. */
    val firstUseSteps: List<Int>,
    val tutorialUrl: String,
    /** Something to copy in one tap (the LCP passphrase for BiblioAccess). */
    val copyableHint: CopyableHint? = null,
    @DrawableRes val icon: Int,
    val colorSeed: Int,
    /** The button's verb when the service opens on the web ("Borrow" for books). */
    @StringRes val webActionLabel: Int = R.string.digital_open
)

data class CopyableHint(@StringRes val label: Int, val value: String)

/** A provider's Android app. Its package is declared in the manifest's `<queries>`, so the app can tell whether it is installed. */
data class PartnerApp(
    /** The app's name on the Play Store, a brand name. */
    val name: String,
    val publisher: String,
    val packageName: String,
    val role: Role,
    /** Three selling points at most, for the install sheet. */
    val highlights: List<Highlight> = emptyList()
) {
    enum class Role {
        /** The service is used in the app: offer to install it before opening. */
        REQUIRED,

        /** The service opens on the web; the app is a companion (music, comics). */
        COMPANION,

        /** The reader for files borrowed on the web (Louise Reader for BiblioAccess). */
        READER
    }

    data class Highlight(@DrawableRes val icon: Int, @StringRes val text: Int)
}

/**
 * The library's digital offers. The partner apps' package names were checked on the Play Store (each one's
 * play.google.com/store/apps/details?id=… page): the iOS URL schemes mean nothing on Android.
 */
object DigitalCatalog {
    private fun tutorial(name: String) =
        "https://mediatheque.villejuif.fr/userfiles/file/mediatheque-numerique_tutoriels/mediatheque-numerique_tuto-$name.pdf"

    val offers: List<DigitalOffer> = listOf(
        DigitalOffer(
            id = "films",
            category = R.string.offer_films_category,
            provider = R.string.offer_films_provider,
            summary = R.string.offer_films_summary,
            quota = R.string.offer_films_quota,
            service = DigitalService.ARTE_VOD,
            app = PartnerApp(
                name = "Médiathèque Numérique",
                publisher = "ARTE Studio Lab",
                packageName = "fr.artefrance.offline_player_android.mednum",
                role = PartnerApp.Role.REQUIRED,
                highlights = listOf(
                    PartnerApp.Highlight(DesignR.drawable.ic_play_circle, R.string.offer_films_highlight_catalog),
                    PartnerApp.Highlight(DesignR.drawable.ic_confirmation_number, R.string.offer_films_highlight_credits),
                    PartnerApp.Highlight(DesignR.drawable.ic_lock, R.string.offer_highlight_card)
                )
            ),
            firstUseSteps = listOf(R.string.offer_films_step_1, R.string.offer_films_step_2, R.string.offer_films_step_3),
            tutorialUrl = tutorial("arte-vod-univers-cine"),
            icon = DesignR.drawable.ic_movie,
            colorSeed = 5
        ),
        DigitalOffer(
            id = "presse",
            category = R.string.offer_press_category,
            provider = R.string.offer_press_provider,
            summary = R.string.offer_press_summary,
            quota = R.string.offer_unlimited,
            service = DigitalService.CAFEYN,
            app = PartnerApp(
                name = "Cafeyn",
                publisher = "LEKIOSQUE FR",
                packageName = "fr.lekiosque",
                role = PartnerApp.Role.REQUIRED,
                highlights = listOf(
                    PartnerApp.Highlight(DesignR.drawable.ic_newspaper, R.string.offer_press_highlight_catalog),
                    PartnerApp.Highlight(DesignR.drawable.ic_all_inclusive, R.string.offer_press_highlight_unlimited),
                    PartnerApp.Highlight(DesignR.drawable.ic_lock, R.string.offer_highlight_card)
                )
            ),
            firstUseSteps = listOf(R.string.offer_press_step_1, R.string.offer_press_step_2),
            tutorialUrl = tutorial("cafeyn"),
            icon = DesignR.drawable.ic_newspaper,
            colorSeed = 0
        ),
        DigitalOffer(
            id = "formation",
            category = R.string.offer_learning_category,
            provider = R.string.offer_learning_provider,
            summary = R.string.offer_learning_summary,
            quota = R.string.offer_unlimited,
            service = DigitalService.TOUT_APPRENDRE,
            app = PartnerApp(
                name = "Mozaik par Toutapprendre",
                publisher = "Toutapprendre",
                packageName = "com.toutapprendre",
                role = PartnerApp.Role.REQUIRED,
                highlights = listOf(
                    PartnerApp.Highlight(DesignR.drawable.ic_school, R.string.offer_learning_highlight_catalog),
                    PartnerApp.Highlight(DesignR.drawable.ic_all_inclusive, R.string.offer_learning_highlight_unlimited),
                    PartnerApp.Highlight(DesignR.drawable.ic_lock, R.string.offer_highlight_card)
                )
            ),
            firstUseSteps = listOf(R.string.offer_learning_step_1, R.string.offer_learning_step_2, R.string.offer_learning_step_3),
            tutorialUrl = tutorial("mozaik"),
            icon = DesignR.drawable.ic_school,
            colorSeed = 2
        ),
        DigitalOffer(
            id = "ebooks",
            category = R.string.offer_ebooks_category,
            provider = R.string.offer_ebooks_provider,
            summary = R.string.offer_ebooks_summary,
            quota = R.string.offer_ebooks_quota,
            service = DigitalService.NUMILOG,
            app = PartnerApp(
                name = "Louise Reader",
                publisher = "Numilog",
                packageName = "com.numilog.louise",
                role = PartnerApp.Role.READER
            ),
            firstUseSteps = listOf(R.string.offer_ebooks_step_1, R.string.offer_ebooks_step_2, R.string.offer_ebooks_step_3),
            tutorialUrl = tutorial("biblioaccess"),
            // The library's own LCP passphrase, published in its BiblioAccess tutorial.
            copyableHint = CopyableHint(R.string.offer_ebooks_passphrase, "300005"),
            icon = DesignR.drawable.ic_menu_book,
            colorSeed = 3,
            webActionLabel = R.string.digital_borrow
        ),
        DigitalOffer(
            id = "bd",
            category = R.string.offer_comics_category,
            provider = R.string.offer_comics_provider,
            summary = R.string.offer_comics_summary,
            quota = R.string.offer_comics_quota,
            service = DigitalService.IZNEO,
            // Library access in the izneo app is not confirmed: the web stays the main entry point.
            app = PartnerApp(name = "izneo", publisher = "ONO - IZNEO", packageName = "fr.airweb.izneo", role = PartnerApp.Role.COMPANION),
            firstUseSteps = listOf(R.string.offer_comics_step_1),
            tutorialUrl = tutorial("izneo"),
            icon = DesignR.drawable.ic_grid_view,
            colorSeed = 1
        ),
        DigitalOffer(
            id = "eureka",
            category = R.string.offer_eureka_category,
            provider = R.string.offer_eureka_provider,
            summary = R.string.offer_eureka_summary,
            quota = R.string.offer_eureka_quota,
            service = DigitalService.EUREKA,
            app = PartnerApp(
                name = "musicMe pour bibliothèques",
                publisher = "Apache Network",
                packageName = "com.apachenetwork.musicme.mediatheque",
                role = PartnerApp.Role.COMPANION
            ),
            firstUseSteps = listOf(R.string.offer_eureka_step_1, R.string.offer_eureka_step_2),
            tutorialUrl = tutorial("musicme"),
            icon = DesignR.drawable.ic_music_note,
            colorSeed = 4
        )
    )

    /** The partner apps' packages: the only apps the portal WebView may hand a link to. */
    val partnerPackages: Set<String> = offers.mapNotNull { it.app?.packageName }.toSet()

    fun offer(id: String): DigitalOffer? = offers.firstOrNull { it.id == id }
}
