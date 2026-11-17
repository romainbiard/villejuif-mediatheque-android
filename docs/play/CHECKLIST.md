# Publication sur Google Play : ce qu'il reste à faire à la main

Tout ce qui pouvait être préparé sans votre compte l'est dans ce dossier. Ces étapes vous reviennent, dans cet ordre.

## Avant tout

1. **Autorisation de la Ville (D7).** Écrire à la Ville de Villejuif (service communication) pour l'usage du nom
   « Médiathèque », de la mention des médiathèques de Villejuif et du blason. Garder la réponse écrite.
   Sans accord : changer le titre, refaire l'icône et la bannière sans le blason.
2. **Adresse de contact.** Créer une adresse e-mail dédiée au projet, puis remplacer les `TODO` de
   [fiche.md](fiche.md) et de [confidentialite.md](../confidentialite.md).
3. **Héberger la politique de confidentialité** à une URL publique et stable (GitHub Pages du dépôt, par exemple),
   en remplaçant la date `TODO`.

## Compte et identité

4. **Compte développeur Google Play** (25 $ une fois), au nom d'une personne : vérification d'identité et d'un
   téléphone. Un compte personnel récent doit passer par le test fermé (étape 12).
5. **Choisir le vrai applicationId** (par exemple `fr.<votre-domaine>.mediatheque`) et le mettre dans
   `local.properties` (`mediatheque.applicationId=…`). **Il ne pourra plus changer.**

## Signature

6. **Créer la clé d'importation** et la déclarer dans `~/.gradle/gradle.properties` : voir [SIGNATURE.md](SIGNATURE.md).
   La sauvegarder hors de ce Mac.
7. **Construire le bundle** : `./gradlew :app:bundleProdRelease -Pmediatheque.versionCode=1`, puis vérifier la
   signature (`jarsigner -verify`).
8. **Essai final** sur un vrai téléphone avec votre carte : connexion, prolongation, réservation, ouverture d'un service
   numérique, notification, widget. (La variante prod n'a été testée sur émulateur qu'avec les parties publiques.)

## Play Console

9. **Créer l'app** : nom, langue par défaut français, application gratuite, « Laisser Google gérer la clé de
   signature ».
10. **Remplir les déclarations** avec [declarations.md](declarations.md) : politique de confidentialité, accès à l'app
    (carte de test à demander aux médiathèques), annonces (aucune), classification, public cible, sécurité des données,
    app gouvernementale (non).
11. **Fiche du Store** avec [fiche.md](fiche.md) : textes FR et EN, icône 512 px (exportée d'Android Studio),
    bannière, captures de [screenshots/](screenshots/).
12. **Test interne**, puis **test fermé** : au moins 12 testeurs inscrits pendant 14 jours d'affilée (obligatoire pour
    les comptes personnels récents). Recruter les testeurs parmi des abonnés volontaires, avec leur propre carte.
13. **Demander l'accès à la production** dans la Play Console à la fin du test fermé (questionnaire sur le test).
14. **Production en déploiement progressif** (par exemple 20 %, puis 50 %, puis 100 %), en surveillant Android Vitals.

## À chaque nouvelle version

- Augmenter `mediatheque.versionName` (`gradle.properties`) et le `versionCode`.
- Vérifier les permissions du manifeste fusionné (aucune position, voir [declarations.md](declarations.md)).
- Refaire les captures si l'interface a changé (variante démo, mode démo d'Android, voir [fiche.md](fiche.md)).
- Mettre à jour les notes de version et, si les données changent, la sécurité des données et la politique.
