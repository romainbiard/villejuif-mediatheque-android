# Déclarations Play Console (brouillon)

Réponses préparées à partir de ce que fait réellement le code (`prodRelease`). À relire avant de les saisir : c'est
vous qui les certifiez auprès de Google.

## Ce que l'app envoie, et à qui

| Donnée | Vers | Quand |
|---|---|---|
| Numéro de carte et mot de passe | portail des médiathèques (`mediatheque.villejuif.fr`), HTTPS | connexion, reconnexion automatique |
| Recherches, réservations, prolongations, sélections, avis, suggestions d'achat | portail des médiathèques | à chaque action de l'abonné |
| Coordonnées (pseudo, e-mail, téléphone, canal d'envoi) | portail des médiathèques | quand l'abonné les modifie |
| Session du portail (cookies) | services numériques partenaires (WebView isolée) | quand l'abonné ouvre un service |
| Adresse IP (inévitable) | portail, OpenFreeMap (fonds de carte), hébergeurs des couvertures | à l'affichage |

Rien d'autre : pas de SDK publicitaire, d'analytics, de crash reporting, ni de serveur du développeur. Le scanner de
code-barres (Google Code Scanner) traite l'image **sur l'appareil**, sans permission caméra pour l'app.

Sur l'appareil : identifiants et profil chiffrés (Tink, clé dans le Keystore Android), instantané du compte et centre
de notifications. Tout est effacé à la déconnexion. La sauvegarde Android ne contient que les réglages de rappels.

## Sécurité des données (Data safety)

- **L'app collecte-t-elle ou partage-t-elle des données ?** Oui (collecte). Google appelle « collecte » toute donnée
  qui quitte l'appareil, même vers un service tiers que l'utilisateur choisit d'utiliser.
- **Chiffrées en transit ?** Oui (HTTPS uniquement, `cleartextTrafficPermitted=false`).
- **Moyen de demander la suppression ?** L'app ne crée pas de compte : le compte d'abonné appartient aux médiathèques.
  Répondre que les données locales sont supprimées à la déconnexion, et renvoyer vers les médiathèques pour le compte.
  À valider : Play n'exige le lien de suppression de compte que pour les apps qui permettent d'en créer un.

| Catégorie Play | Type | Collecté | Partagé | Obligatoire | Finalités |
|---|---|---|---|---|---|
| Informations personnelles | Nom | Oui | Non | Oui (compte) | Fonctionnement de l'app, gestion du compte |
| Informations personnelles | Adresse e-mail | Oui | Non | Non (facultatif) | Gestion du compte |
| Informations personnelles | Numéro de téléphone | Oui | Non | Non (facultatif) | Gestion du compte |
| Informations personnelles | ID utilisateur (numéro de carte) | Oui | Non | Oui (compte) | Fonctionnement, gestion du compte |
| Activité dans l'app | Historique de recherche dans l'app | Oui | Non | Oui | Fonctionnement de l'app |
| Activité dans l'app | Autres contenus générés (avis, suggestions) | Oui | Non | Non | Fonctionnement de l'app |

« Partagé : non » parce que l'envoi au portail des médiathèques est une action que l'abonné déclenche lui-même, vers le
service qu'il utilise (exception prévue par Play). Les données ne sont pas traitées de façon éphémère : le portail les
conserve. Pas de localisation, contacts, photos, fichiers, santé, finances, identifiants d'appareil ni diagnostics.

## Classification du contenu (questionnaire IARC)

- Catégorie : **Référence, actualités ou éducation**.
- Violence, sexualité, langage grossier, substances, jeux d'argent : **non** (l'app n'affiche que des notices et des
  couvertures du catalogue public).
- Les utilisateurs peuvent-ils interagir ou échanger du contenu ? **Oui** : les avis sont publiés sur le portail après
  modération par les médiathèques. Pas de messagerie entre utilisateurs.
- L'app partage-t-elle la position de l'utilisateur ? **Non**.
- Achats numériques : **non**.
- Navigation web non restreinte : **non** (WebView limitée au portail et aux services partenaires).

## Public cible et contenu

- **Tranches d'âge** : 13-15, 16-17 et 18 ans et plus. Ne pas cocher les moins de 13 ans : l'app n'est pas conçue pour
  les enfants (le compte est géré par l'abonné adulte) et cela imposerait le programme Familles.
- **Attrait pour les enfants** : non (pas de personnages, ni de jeux).
- **Annonces** : l'app ne contient pas d'annonces.
- **App gouvernementale** : non. L'app est indépendante de la Ville ; la description le dit en deuxième phrase.
- **App d'actualités** : non.
- **Accès à l'app (pour l'examen Google)** : une carte d'abonné est nécessaire pour la partie Compte. TODO : demander aux
  médiathèques une carte de test, ou décrire que le catalogue, l'agenda, le numérique et les infos fonctionnent sans
  carte. Ne jamais donner votre propre carte.

## Permissions déclarées

| Permission | Pourquoi |
|---|---|
| `INTERNET` | parler au portail |
| `POST_NOTIFICATIONS` | rappels de retour, réservations prêtes, agenda du lundi ; demandée après un écran d'explication, refusable |
| `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE` (via WorkManager) | relancer les rappels programmés |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` (WorkManager, MapLibre) | attendre le réseau, charger la carte |
| `USE_BIOMETRIC`, `USE_FINGERPRINT` (androidx.biometric) | verrouillage facultatif du compte |

MapLibre déclare aussi la position (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) pour un calque que l'app
n'utilise pas : le manifeste de l'app les retire (`tools:node="remove"`). Vérifier après chaque mise à jour de
dépendance avec `app/build/outputs/logs/manifest-merger-prod-release-report.txt`.

Aucune permission sensible (position, caméra, contacts, stockage, alarmes exactes, accès à toutes les applis) : pas de
formulaire de déclaration spéciale à remplir.

## Autorisation de la Ville (D7)

Avant tout envoi en production, obtenir par écrit l'accord de la Ville de Villejuif pour le nom, la mention des
médiathèques et le blason. Sans accord : titre neutre, pas de blason dans l'icône ni les visuels, et la mention « non
officielle » partout. Google peut retirer une app signalée pour usurpation (règlement « Impersonation »).
