# Plan de portage Android : Médiathèque

Porter l'app iOS `villejuif-mediatheque-apple` (Swift 6.2 / SwiftUI, ~17 000 lignes, 160+ tests) en app Android native, **à parité fonctionnelle**, en suivant à la lettre les recommandations Google (architecture, Material 3, qualité des apps, règles Play) plutôt qu'en calquant l'iOS.

Principe directeur : **même produit, idiomes Android**. On garde les mêmes fonctionnalités, le même modèle de domaine et les mêmes règles métier. L'interface, la navigation et les intégrations système sont celles qu'un utilisateur Android attend (barre de navigation Material, retour prédictif, canaux de notification, widget Glance, raccourcis d'icône, Gestionnaire de mots de passe Google…).

---

## 1. Inventaire de l'app iOS (périmètre à porter)

| Domaine | Fonctionnalités | Source portail |
|---|---|---|
| **Compte** | Connexion (n° de carte + mot de passe, scan de la carte), mot de passe oublié, carte d'abonné Code 39 en plein écran (luminosité max, demi-tour), prêts groupés par échéance + prolongation unitaire/globale, réservations (rang dans la file, lieu de retrait, annulation), sélections (+ « Mes préférés / Déjà lu / À lire » depuis le cœur), avis, suggestions d'achat (scan ISBN), informations personnelles, mot de passe, médiathèques favorites, newsletter, réglages | `/api/user/*` (JSON, cookie `PHPSESSID`), pages et formulaires `/abonne/*` (HTML) |
| **Sécurité** | Identifiants en trousseau (cet appareil seulement), verrou Face ID au lancement et après 2 min en arrière-plan, reconnexion automatique quand la session expire (une seule à la fois), oubli du mot de passe seulement sur refus explicite du portail | — |
| **Rappels** | Retour à J-3 (réglable), réservation disponible, agenda du lundi 9 h (désactivé par défaut), centre de notifications interne (« boîte »), message de la médiathèque, écran d'explication avant la demande d'autorisation | tâche de fond toutes les ~6 h |
| **Recherche** | Catalogue JSON paginé, facettes type/médiathèque, disponibilité temps réel par exemplaire, fiche détaillée, réservation avec choix du lieu, tuiles Explorer, recherches récentes | `/recherche/simple/…/format/json`, `/noticeajax/*`, popups AJAX |
| **Agenda** | Animations jour par jour, mini-calendrier (iPad), filtres lieu/type/public, détail iCal, ajout au calendrier, partage, carte | page HTML agenda + iCal par événement |
| **Numérique** | Arte VOD, Cafeyn, Mozaik, Louise/Numilog, Izneo, Eurêka, musicMe ; « Ouvrir ou installer » selon l'app installée ; SSO par le portail dans une web view avec les cookies de session, bascule vers l'app partenaire ; tutoriels PDF | `/modules/{slug}` |
| **Infos** | 3 médiathèques sur une carte, horaires, « Ouvert / Fermé · ouvre mardi à 13:00 » calculé en direct, itinéraire, appel, e-mail | pages HTML des médiathèques |
| **Système** | Widget « Prochains retours », Siri/Raccourcis (prochains retours, ma carte, recherche, agenda), iPad (barre latérale, ⌘F, ⌘1…5), haptique, cache hors ligne, accessibilité complète, mode démo `-uiTesting` | — |
| **Anti-bot** | Défi BunkerWeb (`/challenge`) résolu dans une web view hors écran, cookies réinjectés, résolu une seule fois pour les requêtes concurrentes | — |

Tout ce qui précède est le **critère de parité** : chaque ligne devient un ticket de la phase correspondante (§ 9).

---

## 2. Décisions à valider avant de démarrer

| # | Question | Recommandation | Pourquoi |
|---|---|---|---|
| D1 | **minSdk** | **29** (Android 10) | ~95 % du parc actif, thème sombre système, navigation gestuelle, stockage cloisonné ; évite le désucrage `java.time`. 26 si on veut toucher des téléphones très anciens (public de bibliothèque), au prix de quelques contournements. |
| D2 | **Carte des médiathèques** | **MapLibre Compose + tuiles OpenFreeMap** (sans clé), itinéraire délégué à l'app de cartes (`geo:` / Google Maps) | Le README promet « aucun traceur » ; le SDK Google Maps demande une clé API, transmet des données à Google et doit être déclaré dans la fiche « Sécurité des données ». Alternative 100 % Google : Maps Compose avec clé restreinte (paquet + SHA-1) hors dépôt. |
| D3 | **Couleurs** | Thème **aux couleurs de Villejuif**, schéma M3 complet généré depuis le violet de marque, *sans* couleurs dynamiques Material You | L'identité visuelle est le cœur de l'app iOS ; M3 l'autorise explicitement pour les marques. On garde les 3 niveaux de contraste système (standard / moyen / élevé, API 34+) qui remplacent le « contraste renforcé » iOS. |
| D4 | **Place de la recherche** | Destination à part entière dans la barre de navigation + `SearchBar` M3 en haut de l'écran | Android n'a pas d'onglet « rôle recherche » ; Material limite la barre à 5 destinations, on y est pile. Ordre proposé : Compte · Recherche · Agenda · Numérique · Infos. |
| D5 | **`applicationId`** | Comme sur iOS : valeur neutre `com.example.mediatheque` versionnée, préfixe réel dans `local.properties` / variable de CI | Même logique que `Config/Local.xcconfig`, rien d'identifiant dans le dépôt. Attention : un `applicationId` publié sur Play est **définitif**. |
| D6 | **Compte Play** | Compte développeur personnel existant ? | S'il a été créé après nov. 2023 : **test fermé obligatoire de 12 testeurs pendant 14 jours** avant la production. À planifier tôt. |
| D7 | **Nom et blason** | Obtenir un accord écrit de la Ville **avant** la publication Play, ou prévoir un nom/une icône neutres | Règles Play « Usurpation d'identité » et « Applications gouvernementales » : une app non officielle qui reprend le nom et le blason d'une collectivité est un motif de rejet fréquent. Sans incidence pour un APK de test. |
| D8 | **Identité git** | Fixer `user.email` **localement** dans le nouveau dépôt (adresse perso) | L'historique iOS mélange deux adresses, dont une professionnelle. |

---

## 3. Pile technique et équivalences iOS → Android

| iOS | Android (recommandation Google) | Remarques |
|---|---|---|
| Swift 6.2, concurrence stricte | **Kotlin 2.x (K2)**, coroutines + `Flow`, JDK 21 | Versions figées dans `gradle/libs.versions.toml`, Renovate pour les montées. |
| SwiftUI | **Jetpack Compose** (BOM) + **Material 3** | Composants M3 Expressive seulement quand l'API est stable. |
| `@Observable` ViewModel | `androidx.lifecycle.ViewModel` + `StateFlow<UiState>` (flux unidirectionnel) | `SavedStateHandle` pour survivre à la mort du processus (requête, filtres). `collectAsStateWithLifecycle`. |
| swift-dependencies (`liveValue`/`previewValue`/`testValue`) | **Hilt** ; interfaces de dépôts + implémentations `Fake…` dans `:core:testing` ; `@TestInstallIn` | Le mode démo (`-uiTesting`) devient un module Hilt de données fictives, uniquement en `debug`. |
| `URLSession` éphémère | **OkHttp** + `CookieJar` en mémoire | Pas de Retrofit : les routes Bokeh sont des segments (`/cle/valeur`) construits par `HttpUrl.Builder.addPathSegment`, reproduire **exactement** l'encodage (`/`, `*`, espaces) et porter `EndpointTests`. |
| `Codable` + `LenientDecoding` | **kotlinx.serialization** (`ignoreUnknownKeys`, `coerceInputValues`, `explicitNulls = false`) + sérialiseurs tolérants | Le dernier commit iOS tolère les `null` du compte : mêmes cas couverts par les mêmes fixtures. |
| SwiftSoup | **Jsoup** | SwiftSoup est un portage de Jsoup : les sélecteurs se transposent quasi tels quels. |
| `ICalParser` maison | portage Kotlin du parseur (132 lignes) | Pas d'ical4j, trop lourd pour ce besoin. |
| `WKWebView` hors écran (BunkerWeb) | `WebView` hors écran dans un **profil WebView isolé** (`androidx.webkit` `ProfileStore`) | Voir § 5.1. |
| Trousseau `…ThisDeviceOnly` | **DataStore + Tink AEAD**, clé dans **Android Keystore**, exclu des sauvegardes | `EncryptedSharedPreferences` est déprécié. |
| Autoremplissage trousseau iCloud | **Credential Manager** (mots de passe Google) + `ContentType.Username/Password` sur les champs Compose | Proposer d'enregistrer après une connexion réussie. |
| Face ID (`LocalAuthentication`) | **`androidx.biometric` `BiometricPrompt`**, `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` | Équivalent de `.deviceOwnerAuthentication`. Verrou après 2 min via `ProcessLifecycleOwner`. |
| `UNUserNotificationCenter` | `NotificationManagerCompat` + **canaux** + permission `POST_NOTIFICATIONS` | Voir § 5.4. |
| `BGAppRefreshTask` | **WorkManager** (`PeriodicWorkRequest` 6 h, réseau requis) | Survit au redémarrage sans permission supplémentaire. |
| WidgetKit + App Group | **Jetpack Glance** (`GlanceAppWidget`), même processus : plus besoin d'App Group | Aperçus générés (API 35+), tailles adaptatives. |
| App Intents / Siri | **Raccourcis d'app** (statiques : « Ma carte », « Rechercher », « Agenda » ; dynamiques), **vignette Réglages rapides** « Ma carte » (optionnelle) | « Prochains retours » à la voix : à étudier avec **AppFunctions** (Gemini) une fois l'API stable ; hors périmètre v1. |
| VisionKit `DataScanner` | **Google Code Scanner** (`play-services-code-scanner`) | **Aucune permission caméra** ; Code 39 et EAN-13. Bouton masqué si les services Play sont absents. |
| MapKit | voir D2 | |
| `EventKit` (ajout au calendrier) | `Intent.ACTION_INSERT` sur `CalendarContract.Events` | Aucune permission. |
| `ShareLink` | Sharesheet (`Intent.ACTION_SEND` + `createChooser`) | |
| `canOpenURL` + `LSApplicationQueriesSchemes` | `PackageManager` + balises **`<queries><package>`** du manifeste | Jamais `QUERY_ALL_PACKAGES` (refusé par Play pour ce cas). |
| Fiche App Store intégrée | Fiche Play Store (`market://details?id=…`, repli `https://play.google.com/…`) | |
| Liens universels des partenaires | Intent avec **`FLAG_ACTIVITY_REQUIRE_NON_BROWSER`** (API 30+) | Ouvre l'app partenaire si elle gère le lien vérifié, sinon on reste dans la web view. |
| `URLCache` 300 Mo | **Coil 3** (cache disque dédié) | |
| `Library/Caches` (`DataCache`) | `context.cacheDir/portail` (JSON) | Même logique fraîcheur/repli, même `maxAge` (recherche 5 min non persistée, dispo 2 min, agenda 30 min, horaires 12 h). |
| `Calendar.paris` | `java.time` + `ZoneId.of("Europe/Paris")` | Tous les calculs d'échéance et d'horaires dans ce fuseau, comme sur iOS. |
| String Catalogs (fr source, en) | `values/strings.xml` **en français** (source), `values-en/`, `<plurals>`, `localeConfig` généré | Langue par app (Android 13+) automatique. Typographie française : espaces fines insécables (U+202F) avant `: ; ! ?`, guillemets « ». |
| SwiftFormat / SwiftLint / Periphery | **ktlint (via Spotless)**, **detekt**, **Android Lint**, règles `compose-lints` | Lint en erreur sur la CI. |
| XCUITest | **Compose UI tests** + Gradle Managed Devices | |
| Swift Testing | JUnit 4 + `kotlinx-coroutines-test` + Turbine + Robolectric + **MockWebServer** | `PortalStub` devient un `Dispatcher` MockWebServer qui sert les mêmes fixtures. |
| Xcode Cloud + TestFlight | **GitHub Actions** + **Play Console, piste de test interne** | |

---

## 4. Architecture et modules

Architecture recommandée par Google (couche UI → couche données, couche domaine seulement là où elle simplifie), modularisée façon *Now in Android*, avec des plugins de convention Gradle. La correspondance avec le package Swift est directe.

```
villejuif-mediatheque-android/
├── app/                       # Activity unique, NavigationSuiteScaffold, splash, Hilt, raccourcis, vignette
├── build-logic/convention/    # plugins Gradle partagés (android-library, compose, hilt, feature, jvm-library)
├── core/
│   ├── bokeh/                 # ≈ BokehAPI : Endpoint, PortalHttpClient (OkHttp), BunkerWeb, DTO, Jsoup, iCal  [module JVM pur sauf BunkerWeb]
│   ├── model/                 # Patron, Loan, Hold, Library, OpeningHours, Event, Notice…  [JVM pur]
│   ├── domain/                # Code39, ReminderPlanner, InboxPlanner, ReturnsSummary, SchoolHolidays  [JVM pur]
│   ├── data/                  # dépôts, SessionManager, AccountSync, DataCache, mappers
│   ├── datastore/             # réglages (Preferences DataStore), coffre d'identifiants (Tink + Keystore), instantané, boîte
│   ├── notifications/         # canaux, planification, contenus
│   ├── designsystem/          # thème Villejuif, typo, composants, icônes, blason (ne dépend de rien)
│   ├── ui/                    # ≈ CoreUI : écrans d'erreur depuis LoadError, composants liés au domaine
│   └── testing/               # Fakes, règles de test, fixtures partagées, MockWebServer
├── feature/
│   ├── account/               # connexion, carte, prêts, réservations, profil, réglages, onboarding
│   ├── search/                # recherche, fiche, disponibilité, réservation
│   ├── agenda/
│   ├── digital/
│   └── info/
├── widget/                    # Glance « Prochains retours »
├── baselineprofile/           # profil de référence + macrobenchmarks
├── gradle/libs.versions.toml
└── docs/
```

Règles :
- Les modules `feature` ne dépendent jamais entre eux (iOS : `AccountFeature` dépend de `SearchFeature` pour la fiche) ; la fiche document partagée remonte dans `core:ui`, ou la navigation passe par une route commune.
- `core:bokeh`, `core:model` et `core:domain` sont des **modules Kotlin/JVM purs** : tests rapides sans Android, et ils restent réutilisables si l'on passe un jour à Kotlin Multiplatform.
- Les vues n'accèdent jamais aux dépôts (même règle qu'iOS) ; les ViewModels exposent un `UiState` immuable et des fonctions d'intention.
- Navigation : **Navigation 3** (`androidx.navigation3`) avec routes typées et sérialisables ; `ListDetailPaneScaffold` pour Recherche → fiche et Compte → section sur grand écran.

---

## 5. Points délicats (détail d'implémentation)

### 5.1 Défi anti-bot BunkerWeb
- `PortalHttpClient` détecte une réponse dont l'URL finale a pour chemin `/challenge`, résout le défi **une seule fois** pour toutes les requêtes concurrentes (`Mutex` + `Deferred` partagé + compteur de génération, exactement comme l'acteur iOS), puis rejoue la requête une fois ; second échec → `BokehError.ChallengeFailed`.
- Le solveur : `WebView` créée sur le thread principal, **non attachée** à une fenêtre, dans un profil `androidx.webkit` dédié (supprimé ensuite) ; repli si `WebViewFeature.MULTI_PROFILE` n'est pas pris en charge : `CookieManager` vidé avant et après.
- **Même User-Agent** côté OkHttp et WebView (BunkerWeb lie sa session à l'UA). Choisir un UA Android explicite (`MediathequesVillejuif-Android/x.y`).
- Injection des cookies OkHttp → `CookieManager.setCookie`, puis lecture au retour. `CookieManager.getCookie()` ne renvoie que `nom=valeur` : reconstruire des `okhttp3.Cookie` pour le domaine du portail.
- Détection de la sortie de `/challenge` dans `WebViewClient` (`onPageStarted` / `doUpdateVisitedHistory`) et arrêt immédiat ; délai maximal 30 s ; les annulations de navigation dues au formulaire du défi ne sont pas des échecs.
- Test instrumenté dédié (vrai WebView, page de défi servie par MockWebServer) : c'est le composant le plus fragile.

### 5.2 Session et reconnexion
- `SessionManager` : `perform { }` intercepte `Unauthorized`, se reconnecte une seule fois (connexion en cours partagée), rejoue ; supprime les identifiants **uniquement** sur `InvalidCredentials` (message explicite du portail), jamais sur un délai dépassé ou une 5xx. Porter `SessionManagerTests` à l'identique.
- Mot de passe oublié : respecter le `minimum_time` (attendre 2,2 s) et le pot de miel `website`.
- `SessionStore` (état `Restoring / Locked / LoggedOut / LoggedIn`) devient un singleton Hilt exposant un `StateFlow`, observé par l'`Activity` et le widget.

### 5.3 Stockage local
| Donnée | Emplacement | Sauvegarde |
|---|---|---|
| Identifiants | DataStore chiffré (Tink AEAD, clé Keystore non exportable) | **exclus** (`dataExtractionRules` cloud + transfert d'appareil) |
| Dernier compte connu, instantané prêts/réservations, boîte | `filesDir` (stockage chiffré par le système, disponible après le premier déverrouillage, comme `completeUntilFirstUserAuthentication`) | exclus |
| Réglages (rappels, biométrie, onboarding) | Preferences DataStore | inclus, sauf « biométrie activée » |
| Cache public | `cacheDir` | jamais |

La clé Keystore n'est **pas** liée à l'authentification biométrique : la synchronisation en arrière-plan doit pouvoir se reconnecter sans l'utilisateur (même compromis que l'iOS). Le verrou biométrique protège l'interface. À la déconnexion : tout effacer (identifiants, instantané, boîte, notifications programmées, travaux WorkManager, widget mis à jour).

### 5.4 Notifications et tâches de fond
- **Canaux** (réglables par l'utilisateur dans les paramètres système) : « Rappels de retour », « Réservations disponibles », « Agenda de la semaine », « Messages de la médiathèque ». Les interrupteurs de l'app et ceux du système restent cohérents (lien profond vers le canal).
- **Permission `POST_NOTIFICATIONS`** (Android 13+) demandée seulement après l'écran d'explication de l'onboarding, avec gestion du refus définitif (lien vers les paramètres).
- Notifications sur l'écran verrouillé : `VISIBILITY_PRIVATE` + `publicVersion` **sans titres de documents** (équivalent du `privacySensitive` iOS).
- Rappels J-3 : `OneTimeWorkRequest` avec délai initial, **pas d'alarme exacte** (`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` sont réservés par Play aux réveils et calendriers). Une heure approximative (vers 9 h) suffit.
- Synchronisation : `PeriodicWorkRequest` 6 h, contrainte réseau, travail unique `KEEP` ; même logique qu'`AccountSync.run` (instantané, replanification, boîte, réservations nouvellement disponibles sauf au tout premier instantané).
- Agenda du lundi : travail ponctuel replanifié à chaque exécution pour viser lundi 9 h.

### 5.5 Médiathèque numérique
- Détection des apps : liste des **noms de paquets Android** des partenaires à établir et vérifier sur le Play Store (ne pas les deviner), déclarée dans `<queries>`. Les schémas iOS (`arte-mednum`…) ne valent pas sur Android.
- L'URL SSO est **résolue au moment du tap**, jamais journalisée, jamais mise en cache ni passée dans un `Intent` loggable.
- Web view du portail : profil isolé détruit à la fermeture, `safeBrowsingEnabled`, pas de `addJavascriptInterface`, `allowFileAccess = false`, contenu mixte interdit, cookies tiers autorisés **uniquement** pour cette web view (le SSO en a besoin).
- Navigation sortante : reprendre la règle iOS `mayOpenExternally` : schémas des partenaires depuis le cadre principal seulement, `mailto:`/`tel:` uniquement après un tap, tout le reste bloqué. Les URL `intent://` sont analysées avec `Intent.parseUri` puis **assainies** (composant et sélecteur retirés, catégorie `BROWSABLE` forcée) pour éviter le détournement d'intent.
- Tutoriels PDF : **Custom Tabs**.

### 5.6 Carte d'abonné
- Encodeur Code 39 porté tel quel (pur Kotlin) et dessiné dans un `Canvas` Compose, avec zones de silence suffisantes.
- Plein écran : `WindowManager.LayoutParams.screenBrightness = BRIGHTNESS_OVERRIDE_FULL` et `FLAG_KEEP_SCREEN_ON` posés dans un `DisposableEffect`, restaurés en sortie ; bouton demi-tour.
- **Ne pas verrouiller l'orientation** : avec `targetSdk 36`, Android 16 ignore les verrouillages d'orientation sur les écrans ≥ 600 dp. La carte doit fonctionner dans toutes les orientations (code-barres pivoté en paysage).
- Haptique : `HapticFeedbackType.Confirm` / `Reject`, uniquement sur les événements qui ont un sens (comme l'iOS).

---

## 6. Liste de contrôle « standards Google »

**Plateforme**
- [ ] `compileSdk`/`targetSdk` = dernière API stable (au moins 36, exigé par Play pour toute mise à jour depuis le 31 août 2026).
- [ ] **Bord à bord** (`enableEdgeToEdge`, insets gérés partout, y compris clavier `imePadding`) ; barres système transparentes.
- [ ] **Retour prédictif** activé (`enableOnBackInvokedCallback`), `PredictiveBackHandler` là où on intercepte le retour.
- [ ] **SplashScreen API** (blason blanc sur magenta, maintenu jusqu'à la lecture du compte en cache, pas au-delà de ~1 s).
- [ ] **Icône adaptative** (premier plan + fond) **et calque monochrome** pour les icônes à thème (Android 13+).
- [ ] Activité unique, aucun Fragment ; gestion correcte des changements de configuration et de la mort du processus (tester « Ne pas conserver les activités »).
- [ ] Permissions minimales : `INTERNET`, `POST_NOTIFICATIONS`, `USE_BIOMETRIC`. Pas de caméra (Code Scanner), pas de calendrier (Intent), pas d'alarme exacte, pas de `QUERY_ALL_PACKAGES`.

**Material 3 et interface**
- [ ] `NavigationSuiteScaffold` : barre de navigation (compact), rail (moyen), tiroir permanent (étendu) selon les **classes de taille de fenêtre**.
- [ ] Composants M3 natifs : `SearchBar`, `PullToRefreshBox`, `ModalBottomSheet`, `DatePicker` pour l'agenda, `SegmentedButton`/`FilterChip` pour les filtres, `Snackbar` pour les confirmations.
- [ ] Recherche paginée avec **Paging 3**.
- [ ] Typographie M3 (police système), tailles en `sp`, aucune hauteur de texte figée.
- [ ] Mode sombre, 3 niveaux de contraste, « Supprimer les animations » respecté.

**Grands écrans** (niveau 2 des consignes de qualité grands écrans)
- [ ] Tablettes, pliables (posture table), ChromeOS, fenêtres redimensionnables et multi-fenêtre.
- [ ] Liste/détail côte à côte (Recherche, Compte, Agenda avec calendrier).
- [ ] Clavier et souris : `Ctrl+F` (recherche), `Ctrl+1…5` (destinations), survol, menus contextuels, **aide aux raccourcis clavier** (`onProvideKeyboardShortcuts`).

**Accessibilité** (équivalent RGAA / EN 301 549 de l'iOS)
- [ ] TalkBack : `heading()`, `stateDescription`, `contentDescription`, régions dynamiques pour les annonces (prolongation réussie…), ordre de parcours.
- [ ] Taille de police jusqu'à 200 % (mise à l'échelle non linéaire d'Android 14) sans troncature.
- [ ] Cibles tactiles ≥ 48 dp, contrastes AA, `accessibilityChecks` activés dans les tests Compose, passage de l'Accessibility Scanner.
- [ ] Accès complet au clavier et Switch Access.

**Performance**
- [ ] Profil de référence (Baseline Profile) + macrobenchmark de démarrage et de défilement.
- [ ] R8 en mode complet, `shrinkResources`, journaux supprimés en release.
- [ ] `StrictMode` et LeakCanary en `debug`.
- [ ] Aucun travail réseau ou disque sur le thread principal ; images aux bonnes dimensions (Coil).

**Sécurité**
- [ ] `network_security_config` : HTTPS uniquement, pas d'autorités utilisateur en release (autorisées en `debug-overrides` pour inspecter le trafic).
- [ ] Composants exportés au strict minimum (`exported` explicite).
- [ ] Aucune donnée personnelle ou URL SSO dans les journaux (règle Lint/detekt personnalisée sur `Log.*` + `-assumenosideeffects`).

**Confidentialité et Play**
- [ ] Pas de SDK d'analyse ni de rapport de plantage tiers : on s'appuie sur **Android vitals** de la Play Console.
- [ ] Fiche **« Sécurité des données »** remplie honnêtement (identifiants et données du compte transmis au portail à la demande de l'utilisateur, chiffrés en transit, non partagés avec le développeur).
- [ ] **Politique de confidentialité** publiée (GitHub Pages) et liée depuis l'app et la fiche Play.
- [ ] Classification du contenu, public cible, déclaration « non affilié à la Ville » dans la description (voir D7).
- [ ] Suppression de compte : sans objet (l'app ne crée pas de compte), à expliquer dans la Play Console.

---

## 7. Données personnelles et hygiène du dépôt

Le dépôt sera **public** comme l'iOS. Règles appliquées dès le premier commit :

1. **`.gitignore`** : `local.properties`, `keystore.properties`, `*.jks`, `*.keystore`, `*.p12`, `google-services.json`, `build/`, `.gradle/`, `.idea/` (sauf styles de code partagés), `captures/`, `*.hprof`, sorties de Roborazzi non validées.
2. **Signature** : clé d'envoi Play hors dépôt (secrets GitHub en base64), **Play App Signing** pour la clé de l'app.
3. **Fixtures** : reprendre les fixtures iOS déjà anonymisées (abonné fictif « Camille MARTIN », carte `0012345`, `exemple.fr`). Toute nouvelle capture du portail est anonymisée **avant** d'entrer dans le dépôt : noms, n° de carte, e-mail, téléphone, adresse, `PHPSESSID`, `csrftoken`, cookies BunkerWeb, jetons SSO (la fixture `sso-tout-apprendre.html` en est un exemple à vérifier ligne à ligne).
4. **Analyse automatique** avant chaque commit et sur la CI : **gitleaks** (secrets) + script `scripts/check-personal-data.sh` qui refuse les motifs sensibles (n° de carte autres que `0012345`, e-mails hors `exemple.fr`/`example.com`, numéros de téléphone français, `PHPSESSID=`, adresses postales réelles d'abonnés). Le même script tourne sur les fixtures, les captures d'écran (métadonnées EXIF) et la fiche Play.
5. **Captures d'écran** (README, Play Store) produites **uniquement en mode démo**, jamais depuis un vrai compte.
6. **Identité git** : `git config user.name/user.email` en local dans ce dépôt (D8) ; vérification de l'auteur par un hook `pre-commit`.
7. Les coordonnées des médiathèques, horaires et contenus publics du portail ne sont pas des données personnelles et peuvent rester en dur.

---

## 8. Tests et qualité

| Niveau | Outils | Contenu (porté depuis l'iOS) |
|---|---|---|
| Analyse portail | JUnit sur JVM pur | `EndpointTests`, `DecodingTests`, `ParserTests`, `PatronSpaceTests` sur les mêmes fixtures |
| Domaine | JUnit + `kotlinx-coroutines-test` | `DomainTests` (échéances, horaires, Code 39, résumé des retours, vacances scolaires), `LoadErrorTests` |
| Clients réels contre faux portail | **MockWebServer** (`PortalDispatcher` ≈ `PortalStub`) | `LiveClientTests`, `SessionManagerTests` : connexion, expiration puis reconnexion, prolongation, réservation, défi BunkerWeb simulé |
| ViewModels | Fakes + Turbine | `SearchViewModelTests`, `AccountTests`, `ProfileTests`, `NotificationsTests`, `OnboardingTests`, `RemindersAndFaceIDTests`, `DigitalTests`, `AgendaAndInfoTests` |
| Écrans | **Roborazzi** (captures sur Robolectric) | Chaque écran en clair/sombre, police 100 %/200 %, téléphone/tablette |
| Bout en bout | Compose UI tests sur **Gradle Managed Devices** (Pixel, tablette, ATD) | Parcours de `BrowsingUITests` / `AccountUITests` en mode démo : onglets, carte plein écran, prolongation, recherche puis réservation, filtres agenda, connexion, très grande police |
| WebView | test instrumenté | Défi BunkerWeb, règles de navigation sortante du SSO |
| Fumée réseau | test manuel ou nocturne, désactivé par défaut | ≈ `LiveSmokeTests` contre le vrai portail, sans compte |

Objectif de couverture : au moins le niveau iOS (74 % de la logique), mesuré par **Kover**. Tests en `fr-FR` / `Europe/Paris` forcés (règle JUnit qui fixe `Locale` et `TimeZone`).

---

## 9. Phasage

Chaque lot se termine par : build vert, lint/detekt/ktlint propres, tests du lot passants, analyse de données personnelles propre, commit.

| Lot | Contenu | Fin de lot |
|---|---|---|
| **0. Fondations** | Dépôt git, licence MIT, README FR, `.gitignore`, hooks (gitleaks + données perso + identité), wrapper Gradle, catalogue de versions, `build-logic`, squelette des modules, CI GitHub Actions (build + tests JVM + lint) | Un `assembleDebug` vide passe sur la CI |
| **1. Portail** | `core:model`, `core:bokeh` (Endpoint, client OkHttp, DTO, Jsoup, iCal, erreurs), fixtures copiées et revérifiées | Tests d'analyse portés et verts |
| **2. Données** | `core:domain`, `core:datastore` (coffre Tink/Keystore), `core:data` (SessionManager, dépôts, DataCache, AccountSync), solveur BunkerWeb | Tests clients contre MockWebServer verts |
| **3. Coquille** | `core:designsystem` (thème, couleurs, typo, composants), `core:ui`, Activity, splash, icônes, navigation adaptative, bord à bord, retour prédictif, mode démo | L'app navigue entre 5 destinations vides sur téléphone et tablette |
| **4. Public** | Infos (carte D2, horaires, statut en direct), Agenda (filtres, détail iCal, calendrier, partage), Recherche (paginée, facettes, dispo temps réel, fiche, tuiles Explorer, récentes) | Utilisable sans compte, hors ligne grâce au cache |
| **5. Compte** | Connexion (Credential Manager, scan de carte), carte Code 39, prêts/prolongation, réservations/annulation, réservation depuis la fiche, biométrie et reverrouillage, onboarding notifications | Parcours abonné complet en mode démo et sur un vrai compte |
| **6. Profil** | Sélections et cœur, avis, suggestions (scan ISBN), informations personnelles, mot de passe, médiathèques favorites, newsletter, réglages, boîte de notifications | Parité « Mon compte » |
| **7. Numérique** | Catalogue des offres, détection des apps (`<queries>`), Play Store, web view SSO sécurisée, bascule vers les apps partenaires, tutoriels | Les 7 services s'ouvrent connectés |
| **8. Système** | Canaux et rappels, WorkManager, widget Glance, raccourcis d'app (+ vignette Réglages rapides), effacement complet à la déconnexion | Rappel reçu, widget à jour après synchro en arrière-plan |
| **9. Finition** | Audit TalkBack et grands écrans, clavier, captures Roborazzi, profil de référence, R8, vérification APK (analyse des permissions et des composants exportés), revue de sécurité | Consignes de qualité Google cochées (§ 6) |
| **10. Publication** | Signature, Play App Signing, fiche Play (captures du mode démo), sécurité des données, politique de confidentialité, test interne puis **test fermé 12 × 14 jours** si D6, production progressive | App en production (sous réserve de D7) |

Les lots 4, 6 et 7 sont largement indépendants une fois le lot 3 terminé : ils peuvent avancer en parallèle.

---

## 10. Risques

| Risque | Impact | Parade |
|---|---|---|
| Rejet Play pour usurpation (nom/blason de la Ville) | Publication bloquée | D7 : accord écrit, ou nom et icône neutres ; mention « non officiel » partout |
| BunkerWeb change de défi ou bloque les WebView Android | Plus d'accès au portail | Solveur isolé et testé, UA cohérent, message d'erreur clair, surveillance par le test de fumée nocturne |
| Le portail change son HTML | Écrans vides | Analyse isolée dans `core:bokeh`, analyseurs tolérants, fixtures à jour |
| Synchronisation en arrière-plan bridée par certains constructeurs (Xiaomi, Samsung…) | Rappels en retard | Rappels planifiés localement dès la synchro au premier plan ; aide « optimisation de batterie » seulement si nécessaire, sans demander `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| Appareils sans services Google Play | Pas de scan ni de Credential Manager côté Google | Fonctionnalités masquées proprement, saisie manuelle toujours possible |
| Fuite de données personnelles dans le dépôt public | Grave et irréversible | § 7 : hooks, CI, revue des fixtures, mode démo pour toute capture |
