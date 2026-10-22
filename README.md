# Compteur Vitesse – Widget Android

Widget de vitesse GPS redimensionnable, avec 3 présentations qui changent automatiquement quand on étire le widget :

| Taille | Affichage |
|--------|-----------|
| Petit (1x1, 2x1, 3x1… : un côté sous 110 dp) | Texte de la vitesse + « km/h » |
| Moyen (2x2, 3x3… : forme carrée) | Votre image `widget_medium.png` + chiffre de vitesse, graduations allumées, aiguille |
| Grand (2x3, 2x4… : nettement plus haut que large) | Votre image `widget_grand.png` + chiffre de vitesse, graduations allumées, arc et aiguille |

## Ouvrir et lancer

1. Android Studio (Koala ou plus récent) → **Open** → dossier `SpeedWidget`.
2. Laisser Gradle synchroniser, puis **Run** sur un téléphone (Android 8.0 / API 26 minimum).
3. Dans l'app : **Démarrer le suivi** (accepter la localisation) puis **Ajouter le widget**.
4. Sur l'écran d'accueil : appui sur le widget = démarrer / arrêter le suivi. Appui long puis poignées = redimensionner.

## Personnaliser

- Couleurs et graduations : `GaugeRenderer.kt` (vitesse max de la jauge : paramètre `maxKmh`, 240 par défaut).
- Règle de choix de la taille : `pickKind` dans `SpeedWidgetProvider.kt` (110 dp et rapport hauteur/largeur 1,35).
- Textes : `res/values/strings.xml`.
- Images d'aperçu dans le sélecteur de widgets : `res/drawable/preview_large.png` et `preview_medium.png`. Images des cadrans : `res/drawable-nodpi/face_medium.png` et `face_large.png` (si vous les changez, ajustez les coordonnées dans `GaugeRenderer.kt`, objets `MEDIUM` et `LARGE`).

## Obtenir l'APK

- **Android Studio** : menu *Build → Build Bundle(s) / APK(s) → Build APK(s)*. Le fichier se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.
- **Sans Android Studio** : poussez ce dossier sur un dépôt GitHub. Le workflow `.github/workflows/build-apk.yml` compile l'APK automatiquement (onglet *Actions* → dernier run → artefact `SpeedWidget-apk`).
- Pour l'installer, autorisez « Installer des applications inconnues » sur le téléphone.
