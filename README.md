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
> **En cours de développement.** C'est le portage de l'app iOS, lot par lot : voir le [plan de portage](docs/PLAN-PORTAGE.md). Le lot 0 (fondations du projet) est en place.

## Démarrer

**Prérequis** : Android Studio (version récente, avec le SDK Android 37).

1. Ouvrez le dossier du projet dans Android Studio (*File › Open*) et laissez Gradle se synchroniser.
2. Choisissez la configuration **app** et un appareil virtuel ou un téléphone, puis ▶︎.

En ligne de commande :

```bash
./gradlew assembleDebug
```

L'identifiant de l'app versionné est neutre (`com.example.mediatheque`). Pour publier sous votre propre identifiant, ajoutez `mediatheque.applicationId=…` dans `local.properties` (ignoré par git) ou définissez la variable `MEDIATHEQUE_APPLICATION_ID`.

## Vérifications

```bash
./gradlew spotlessCheck lint testDebugUnitTest
```

- **Format** : ktlint via Spotless (`./gradlew spotlessApply` corrige).
- **Lint** Android strict, avec les règles Compose.
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
feature/…            # un module par destination : compte, recherche, agenda, numérique, infos
widget/              # widget « Prochains retours »
build-logic/         # plugins Gradle de convention
```

## Confidentialité

- **Aucun serveur intermédiaire, aucun traceur, aucune statistique.** Les données vont seulement de votre téléphone au portail des médiathèques.
- Les identifiants restent sur l'appareil, chiffrés avec une clé de l'Android Keystore, et ne partent jamais dans les sauvegardes.

## Licence

Code sous licence [MIT](LICENSE). Le nom, le blason et l'identité visuelle de la Ville de Villejuif, ainsi que les marques des services numériques cités, appartiennent à leurs propriétaires respectifs.
