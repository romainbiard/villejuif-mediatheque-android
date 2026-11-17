# Politique de confidentialité de l'app Médiathèque (Android)

*Dernière mise à jour : TODO (date de publication)*

Médiathèque est une application **non officielle**, indépendante de la Ville de Villejuif et de son réseau de
médiathèques. Elle permet aux abonnés d'utiliser le portail public des médiathèques
([mediatheque.villejuif.fr](https://mediatheque.villejuif.fr/)) depuis un téléphone ou une tablette Android.

Contact : TODO (adresse e-mail dédiée au projet)

## En bref

- Aucun serveur intermédiaire : l'app parle directement au portail des médiathèques.
- Aucune publicité, aucun traceur, aucune statistique d'utilisation, aucun rapport de plantage envoyé.
- Le développeur de l'app ne reçoit **aucune** de vos données.

## Les données que l'app utilise

**Votre numéro de carte et votre mot de passe.** Ils servent à vous connecter au portail. Ils sont conservés sur votre
appareil, chiffrés avec une clé protégée par le Keystore Android, pour que l'app puisse vous reconnecter
automatiquement. Ils ne sont envoyés qu'au portail des médiathèques, en HTTPS. Ils ne font pas partie des sauvegardes
Android.

**Les données de votre compte** (nom, carte, prêts, réservations, sélections, avis, suggestions, coordonnées). Elles
viennent du portail et y retournent quand vous agissez (prolonger, réserver, publier un avis, modifier vos
coordonnées). L'app en garde une copie sur l'appareil pour s'afficher hors connexion, alimenter le widget et programmer
vos rappels. Tout est effacé quand vous vous déconnectez.

**Vos recherches.** Elles sont envoyées au portail pour interroger le catalogue. Les dernières recherches restent sur
l'appareil pour vous les proposer ; vous pouvez les effacer dans l'onglet Recherche.

**Les services numériques.** Quand vous ouvrez un service partenaire (films, presse, autoformation, bandes dessinées,
musique), l'app transmet votre session du portail à ce service, dans un navigateur intégré isolé du reste de l'app, ou
ouvre l'application du partenaire. Ce partenaire applique alors sa propre politique de confidentialité.

**Le scanner de carte et d'ISBN.** Il utilise le scanner de code-barres des services Google Play : l'image est analysée
sur l'appareil, l'app n'a pas accès à la caméra et ne conserve aucune image.

**Les notifications.** Rappels de retour, réservations disponibles et agenda du lundi sont calculés et programmés sur
l'appareil. Vous pouvez les refuser ou les désactiver à tout moment dans Mes réglages ou dans les paramètres Android.

**Le verrouillage biométrique.** Facultatif. La vérification est faite par Android ; l'app ne reçoit jamais votre
empreinte ou votre visage.

## Les services tiers contactés

| Service | Pourquoi | Ce qu'il reçoit |
|---|---|---|
| Portail des médiathèques de Villejuif | votre compte, le catalogue, l'agenda | ce que vous faites dans l'app, votre adresse IP |
| OpenFreeMap | le fond de la carte des médiathèques | votre adresse IP, la zone affichée |
| Hébergeurs des couvertures | les images des documents | votre adresse IP |
| Services numériques partenaires | quand vous les ouvrez | votre session du portail, votre adresse IP |
| Google Play (services, Play Store) | scanner, ouverture des fiches d'applis | selon les règles de Google |

Les médiathèques restent responsables des données de votre compte d'abonné : pour y accéder, les corriger ou les
supprimer, adressez-vous à elles.

## Vos choix

- Se déconnecter (Mon compte) efface de l'appareil vos identifiants, la copie de votre compte, vos rappels et le centre
  de notifications.
- Désinstaller l'app efface tout ce qu'elle a enregistré.

## Modifications

Cette politique peut évoluer avec l'app. La date en haut de la page indique la dernière version.

---

# Médiathèque app privacy policy (Android)

*Last updated: TODO (publication date)*

Médiathèque is an **unofficial** app, independent from the City of Villejuif and its library network. It lets library
members use the libraries' public portal ([mediatheque.villejuif.fr](https://mediatheque.villejuif.fr/)) from an
Android phone or tablet.

Contact: TODO (email address dedicated to the project)

## In short

- No intermediary server: the app talks directly to the libraries' portal.
- No ads, no trackers, no usage analytics, no crash reports sent.
- The app's developer receives **none** of your data.

## Data the app uses

**Your card number and password** sign you in to the portal. They are kept on your device, encrypted with a key
protected by the Android Keystore, so the app can sign you back in. They are only sent to the libraries' portal, over
HTTPS, and are excluded from Android backups.

**Your account data** (name, card, loans, holds, lists, reviews, suggestions, contact details) comes from the portal
and goes back to it when you act. The app keeps a copy on the device to work offline, feed the widget and schedule
reminders. Everything is erased when you sign out.

**Your searches** are sent to the portal to query the catalog. Recent searches stay on the device; you can clear them
in the Search tab.

**Digital services**: when you open a partner service, the app passes your portal session to it in an in-app browser
isolated from the rest of the app, or opens the partner's app. That partner's own privacy policy then applies.

**Card and ISBN scanner**: Google Play services' barcode scanner analyzes the image on the device; the app has no
camera access and keeps no image.

**Notifications** are computed and scheduled on the device. You can refuse or turn them off at any time.

**Biometric lock** is optional and handled by Android; the app never receives your fingerprint or face data.

## Third-party services contacted

The libraries' portal (your account, catalog, events), OpenFreeMap (map tiles), cover image hosts, the digital
partners you open, and Google Play services. Each receives at least your IP address. The libraries remain responsible
for your member account: contact them to access, correct or delete it.

## Your choices

Signing out erases your credentials, account copy, reminders and notification history from the device. Uninstalling
the app erases everything it stored.
