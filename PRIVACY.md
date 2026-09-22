# Confidentialité — Keyra

[← Retour au README](./README.md)

## Principe

Un clavier logiciel peut traiter des informations très sensibles. Keyra est conçu avec l'objectif de réduire au maximum la collecte, la conservation et la transmission de ces données.

## Objectifs de confidentialité

Keyra s'efforce de :

- traiter la saisie localement sur l'appareil ;
- limiter les données conservées à ce qui est utile au fonctionnement du clavier ;
- garder le vocabulaire appris localement ;
- ne pas utiliser le contenu saisi pour de la publicité ciblée ;
- ne pas vendre le contenu saisi ni le vocabulaire de l'utilisateur ;
- ne pas imposer de compte utilisateur pour utiliser le clavier ;
- ne pas nécessiter une connexion réseau pour la frappe normale ;
- conserver les thèmes, réglages et images de fond personnalisées sur l'appareil.

## Suggestions et vocabulaire

La correction, les suggestions et l'apprentissage de vocabulaire sont conçus pour fonctionner localement afin d'éviter qu'un texte tapé doive être envoyé à un serveur pour être analysé.

Le vocabulaire personnel peut contenir des mots propres à l'utilisateur. Il doit donc être considéré comme une donnée privée.

## Images de fond personnalisées

Une image sélectionnée comme fond de clavier est destinée à rester utilisée localement par Keyra. Elle ne doit pas être envoyée vers un service distant simplement pour appliquer un flou, une couleur ou un effet d'affichage.

## Réseau

Le fonctionnement de base du clavier ne devrait pas dépendre d'un accès réseau.

Si une future fonctionnalité en ligne est ajoutée, elle devrait être :

- clairement documentée ;
- désactivable lorsqu'elle n'est pas indispensable ;
- limitée aux données strictement nécessaires ;
- séparée du texte tapé lorsque cela est techniquement possible.

## Données sensibles

Comme tout clavier Android, Keyra est un composant de saisie et doit être traité comme une application hautement sensible. N'installez que des builds provenant d'une source de confiance et vérifiez les changements du code source avant d'utiliser un build non officiel.

Évitez d'accorder à un clavier des permissions qui ne sont pas nécessaires à ses fonctionnalités.

## Transparence

Cette page décrit les objectifs de confidentialité du projet. Lorsqu'une fonctionnalité change la manière dont des données sont traitées, cette documentation devrait être mise à jour dans la même version.

Pour signaler un problème de confidentialité ou de sécurité, ouvrez une issue dans le dépôt GitHub en évitant d'y publier des données personnelles ou des exemples de texte sensible.
