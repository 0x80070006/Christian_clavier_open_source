# Keyra — Patch notes v3.0

**Version :** v3.0  
**Plateforme :** Android  
**Release :** https://github.com/0x80070006/Christian_clavier_open_source/releases/tag/v3.0

[⬇️ Télécharger directement l'APK](https://github.com/0x80070006/Christian_clavier_open_source/releases/download/v3.0/Keyra-Pixel-9a.apk) · [← Retour au README](./README.md)

---

## Saisie et robustesse

- Renforcement de la gestion de la frappe très rapide.
- Gestion indépendante des contacts multi-touch afin de mieux traiter deux touches pressées à quelques millisecondes d'intervalle.
- Prise en charge robuste de `ACTION_POINTER_DOWN` et `ACTION_POINTER_UP`.
- Meilleur traitement des transitions autour de la barre d'espace pour réduire les lettres perdues.
- Nettoyage systématique de l'état visuel des touches après relâchement, annulation ou interruption d'un geste.
- Réduction des cas où une touche peut rester visuellement highlightée alors qu'aucun doigt n'est posé dessus.
- Chemin de saisie allégé afin de limiter les traitements bloquants pendant un appui.

## Suppression

- Répétition de Suppr accélérée lors d'un maintien prolongé.
- Démarrage de la répétition après environ **230 ms** de maintien.
- Répétition rapprochée, jusqu'à environ **30 ms** entre suppressions dans le comportement prévu pour cette version.

## Majuscules

- Double appui rapide sur **Maj** pour activer le verrouillage des majuscules.
- Les accents et variantes suivent l'état de Maj/verrouillage.

## Appui long, accents et variantes

- Ajout d'un mini panneau contextuel lors du maintien d'une lettre compatible.
- Affichage des variantes accentuées et autres variantes disponibles.
- Panneau compact positionné près de la touche d'origine.
- Prise en charge des chiffres/symboles associés lorsque la disposition le prévoit.

## Suggestions et correction

- Suggestions plus persistantes pendant une frappe rapide.
- Amélioration de la correction des mots contenant une apostrophe.
- Normalisation des apostrophes `'` et `’` pendant l'analyse.
- Meilleure tolérance aux lettres oubliées, avec des cas ciblés comme :

```text
vnir   → venir
maintn → maintenant
```

- Sélection d'une suggestion : insertion du mot suivie automatiquement d'une espace.
- Apprentissage local du vocabulaire de l'utilisateur selon la configuration active.
- Réduction du travail de suggestion sur le chemin critique de la saisie pour préserver la fluidité.

## Retour haptique

- Ajout d'un retour haptique configurable.
- Option pour le désactiver dans les paramètres du clavier.

## Thèmes

- Ajout de thèmes supplémentaires.
- Couleurs personnalisées.
- Image de fond personnalisée.
- Flou gaussien du fond.
- Niveau de flou indépendant pour la zone des touches.
- Possibilité de garder les touches plus floues que le fond pour améliorer leur lisibilité sur une image.

## Confidentialité

- Ajout d'une page de confidentialité.
- Mise en avant du traitement local et de la minimisation des données comme objectifs du projet.
- Documentation séparée : [PRIVACY.md](./PRIVACY.md).

## Identité visuelle

- Nouveau logo Keyra intégré à la documentation du projet.

## Notes de performance

Keyra vise une latence de saisie aussi faible et stable que possible. Une latence de l'ordre de la nanoseconde n'est pas réaliste pour une chaîne complète écran tactile → Android → IME → application : la latence finale dépend du matériel, du système, de la fréquence d'échantillonnage tactile, du rafraîchissement de l'écran et de l'application cible.

L'objectif de v3.0 est donc la **stabilité**, la **précision** et l'absence de lettres perdues avant la recherche d'un chiffre de latence artificiellement bas.
