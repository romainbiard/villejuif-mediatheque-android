# Signature et versions

## Principe

Google Play signe l'app avec sa propre **clé de signature** (Play App Signing). Vous, vous signez chaque envoi avec une
**clé d'importation** (« upload key ») qui ne sert qu'à prouver à Google que l'envoi vient de vous. Si elle est perdue
ou compromise, Google peut la remplacer : la clé qui signe l'app chez les utilisateurs, elle, ne quitte jamais Google.

Aucune clé, aucun mot de passe n'est dans le dépôt. Le `.gitignore` exclut `*.jks`, `*.keystore`, `*.p12`, `*.pepk` et
`keystore.properties`, et le hook pre-commit (gitleaks) bloque les secrets.

## 1. Créer la clé d'importation (une fois)

Hors du dépôt, par exemple dans `~/.android-keys/` :

```bash
mkdir -p ~/.android-keys && chmod 700 ~/.android-keys
```

```bash
keytool -genkeypair -v -keystore ~/.android-keys/mediatheque-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` est fourni par le JDK d'Android Studio
(`/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool`). Choisissez un mot de passe fort, rangez-le
dans votre gestionnaire de mots de passe, et sauvegardez le fichier `.jks` ailleurs que sur ce Mac (coffre chiffré).

## 2. Dire à Gradle où la trouver

Dans `~/.gradle/gradle.properties` (votre dossier personnel, **pas** celui du projet) :

```properties
mediatheque.upload.storeFile=/Users/<vous>/.android-keys/mediatheque-upload.jks
mediatheque.upload.storePassword=…
mediatheque.upload.keyAlias=upload
mediatheque.upload.keyPassword=…
```

Sur la CI, les mêmes valeurs passent par des secrets : `MEDIATHEQUE_UPLOAD_STORE_FILE` (chemin d'un fichier décodé
depuis un secret base64), `MEDIATHEQUE_UPLOAD_STORE_PASSWORD`, `MEDIATHEQUE_UPLOAD_KEY_ALIAS`,
`MEDIATHEQUE_UPLOAD_KEY_PASSWORD`.

Sans ces quatre valeurs, les builds release restent **non signés** : c'est voulu, ils servent à vérifier R8 en local.

## 3. Le vrai applicationId

Il n'est pas dans le dépôt non plus : `mediatheque.applicationId=…` dans `local.properties`, ou la variable
`MEDIATHEQUE_APPLICATION_ID`. **Il ne pourra plus changer après le premier envoi sur Play.** Vérifiez-le avant.

## 4. Versions

| | Où | Règle |
|---|---|---|
| `versionName` | `mediatheque.versionName` dans `gradle.properties` (dépôt) | Version affichée sur la fiche : `1.0.0`, puis `1.0.1` pour un correctif, `1.1.0` pour des nouveautés. |
| `versionCode` | `VERSION_CODE` (CI) ou `-Pmediatheque.versionCode=…` (local) | Entier **strictement croissant** à chaque envoi, jamais réutilisé, même pour un envoi refusé. 1 par défaut. |

Sur la CI, `VERSION_CODE` vaut le numéro d'exécution GitHub (`GITHUB_RUN_NUMBER`) : il ne fait que monter. En local,
reprenez le dernier code envoyé (visible dans la Play Console) et ajoutez 1.

## 5. Construire le bundle

```bash
./gradlew :app:bundleProdRelease -Pmediatheque.versionCode=1
```

Le fichier est `app/build/outputs/bundle/prodRelease/app-prod-release.aab`. Vérifiez qu'il est signé par votre clé :

```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/prodRelease/app-prod-release.aab | tail -5
```

## 6. Play App Signing

À la création de l'app dans la Play Console, gardez le choix par défaut : **« Laisser Google gérer et protéger la clé
de signature »**. Au premier envoi, la clé d'importation est enregistrée automatiquement.
