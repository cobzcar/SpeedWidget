# Compteur Vitesse – Widget Android

Widget de vitesse GPS redimensionnable, avec 3 présentations qui changent automatiquement quand on étire le widget :

| Taille | Affichage |
|--------|-----------|
| Petit (ex. 1x1, 2x1) | Texte de la vitesse + « km/h » |
| Moyen (ex. 2x2) | Jauge néon rouge, vitesse au centre |
| Grand (ex. 2x3) | Visuel voiture en fond, jauge néon, distance du trajet et vitesse max |

## Ouvrir et lancer

1. Android Studio (Koala ou plus récent) → **Open** → dossier `SpeedWidget`.
2. Laisser Gradle synchroniser, puis **Run** sur un téléphone (Android 8.0 / API 26 minimum).
3. Dans l'app : **Démarrer le suivi** (accepter la localisation) puis **Ajouter le widget**.
4. Sur l'écran d'accueil : appui sur le widget = démarrer / arrêter le suivi. Appui long puis poignées = redimensionner.

## Personnaliser

- Couleurs et graduations : `GaugeRenderer.kt` (vitesse max de la jauge : paramètre `maxKmh`, 240 par défaut).
- Seuils de changement de taille : `SpeedWidgetProvider.kt` (valeurs en dp).
- Textes : `res/values/strings.xml`.
- Images d'aperçu dans le sélecteur de widgets : `res/drawable/preview_large.png` et `preview_medium.png`. Fond du grand widget : `res/drawable-nodpi/bg_car.png` (remplaçable ; la jauge est centrée à 31 % de la hauteur, voir `renderOnCar`).

## Obtenir l'APK

- **Android Studio** : menu *Build → Build Bundle(s) / APK(s) → Build APK(s)*. Le fichier se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.
- **Sans Android Studio** : poussez ce dossier sur un dépôt GitHub. Le workflow `.github/workflows/build-apk.yml` compile l'APK automatiquement (onglet *Actions* → dernier run → artefact `SpeedWidget-apk`).
- Pour l'installer, autorisez « Installer des applications inconnues » sur le téléphone.
