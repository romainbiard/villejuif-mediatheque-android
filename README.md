<div align="center">

# Médiathèque pour Android

**Les médiathèques de Villejuif dans la poche.**<br>
Vos prêts, vos réservations, l'agenda, le catalogue et la médiathèque numérique, dans une app Android native.

![Android 10+](https://img.shields.io/badge/Android-10%2B-34A853?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)
![Licence MIT](https://img.shields.io/badge/licence-MIT-5714A4)
![Projet non officiel](https://img.shields.io/badge/projet-non%20officiel-E0005A)

</div>

> [!IMPORTANT]
> **Projet personnel et non officiel.** Cette app n'est ni éditée, ni validée, ni soutenue par la Ville de Villejuif ou son réseau de médiathèques. Elle s'appuie sur le portail public [mediatheque.villejuif.fr](https://mediatheque.villejuif.fr/). Le nom, le blason et la charte graphique de Villejuif appartiennent à la Ville.

> [!NOTE]
> **En cours de développement.** C'est le portage de l'app iOS, lot par lot : voir le [plan de portage](docs/PLAN-PORTAGE.md). Les lots 0 à 3 sont en place : fondations, couche portail, données, et la coquille de l'app (thème, navigation, mode démo).

## Démarrer

**Prérequis** : Android Studio (version récente, avec le SDK Android 37).

1. Ouvrez le dossier du projet dans Android Studio (*File › Open*) et laissez Gradle se synchroniser.
2. Choisissez la configuration **Médiathèque** et un appareil virtuel ou un téléphone, puis ▶︎.

L'app existe en deux variantes (*Build › Select Build Variant*) :

| Variante | Données | Usage |
|---|---|---|
| `prodDebug` (par défaut) | le portail des médiathèques | l'app réelle, avec votre carte |
| `demoDebug` | l'abonnée fictive Camille MARTIN, en mémoire | essayer l'app sans carte, captures d'écran, tests d'interface |

La démo s'installe à côté de l'app réelle (« Médiathèque démo ») et n'est jamais publiée : seule `prodRelease` part sur Google Play. Sa version release ne sert qu'au profil de référence et aux vérifications R8.

En ligne de commande :

```bash
./gradlew installProdDebug
```

```bash
./gradlew installDemoDebug
```

L'identifiant de l'app versionné est neutre (`com.example.mediatheque`). Pour publier sous votre propre identifiant, ajoutez `mediatheque.applicationId=…` dans `local.properties` (ignoré par git) ou définissez la variable `MEDIATHEQUE_APPLICATION_ID`.

## Vérifications

```bash
./gradlew spotlessCheck lint testDebugUnitTest
```

- **Format** : ktlint via Spotless (`./gradlew spotlessApply` corrige).
- **Lint** Android strict, avec les règles Compose.
- **Captures d'écran** (Roborazzi sur Robolectric) : chaque écran en clair, sombre, texte à 200 % et tablette, comparé aux images de référence de `feature/*/src/test/screenshots/`. Après un changement voulu : `./gradlew recordRoborazziDebug`, puis relire les images avant de les commiter. La CI lance `./gradlew verifyRoborazziDebug`.
- **Parcours instrumentés** en démo (`app/src/androidTestDemo/`), avec les contrôles d'accessibilité (libellés, cibles tactiles, contrastes) à chaque écran : `./gradlew connectedDemoDebugAndroidTest` sur un émulateur, ou sur l'appareil géré par Gradle de la CI : `./gradlew pixel6Api35DemoDebugAndroidTest`.
- **Profil de référence** (démarrage et parcours principaux, enregistrés sur la démo) : `./gradlew :app:generateDemoReleaseBaselineProfile` avec un émulateur lancé ; le profil est commité dans `app/src/demoRelease/generated/baselineProfiles/` et livré avec toutes les variantes.
- **Build release** (R8 complet, ressources réduites, journaux supprimés) : `./gradlew :app:assembleProdRelease`.
- **Avant chaque commit** (hook dans `.githooks/`, activé par `git config core.hooksPath .githooks`) : identité git locale obligatoire, recherche de secrets ([gitleaks](https://github.com/gitleaks/gitleaks)) et de données personnelles (`scripts/check-personal-data.sh`). La CI GitHub Actions refait les mêmes contrôles.

## Architecture

Jetpack Compose et Material 3, ViewModel et `StateFlow`, Hilt, modules par couche (comme le package Swift de l'app iOS) :

```
app/                 # activité unique, navigation, écran de lancement
core/bokeh/          # transport et formats du portail (Kotlin pur)
core/model/          # modèle du domaine (Kotlin pur)
core/domain/         # règles métier (Kotlin pur)
core/data/           # dépôts, session, cache, synchronisation
core/datastore/      # réglages et coffre des identifiants
core/notifications/  # canaux et rappels
core/designsystem/   # charte de Villejuif sur Material 3
core/ui/             # composants construits depuis le domaine
core/testing/        # fakes et fixtures partagés par les tests
core/screenshottesting/ # thèmes, tailles de texte et fenêtres des captures d'écran
feature/…            # un module par destination : compte, recherche, agenda, numérique, infos
widget/              # widget « Prochains retours »
baselineprofile/     # générateur du profil de référence
build-logic/         # plugins Gradle de convention
```

## Confidentialité

- **Aucun serveur intermédiaire, aucun traceur, aucune statistique.** Les données vont seulement de votre téléphone au portail des médiathèques.
- Les identifiants restent sur l'appareil, chiffrés avec une clé de l'Android Keystore, et ne partent jamais dans les sauvegardes.

## Publication

La préparation de la fiche Google Play (textes, déclarations, captures, signature, étapes restantes) est dans
[docs/play](docs/play/CHECKLIST.md), et la politique de confidentialité dans [docs/confidentialite.md](docs/confidentialite.md).
Rien n'est publié depuis ce dépôt.

## Licence

Code sous licence [MIT](LICENSE). Icônes : [Material Symbols](https://github.com/google/material-design-icons) de Google, sous licence Apache 2.0 (ajoutées par `scripts/material_symbol.py`). Le nom, le blason et l'identité visuelle de la Ville de Villejuif, ainsi que les marques des services numériques cités, appartiennent à leurs propriétaires respectifs.
