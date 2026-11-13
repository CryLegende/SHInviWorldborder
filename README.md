<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHInviWorldborder

*Bordures invisibles des mondes et des îles, signalées par un mur de particules.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Le mur bleu du worldborder vanilla est trop voyant, une bordure sans aucun repère ne l'est pas assez.
SHInviWorldborder garde la bordure mais la rend invisible : un nuage de particules rouges apparaît sur
le mur quand un joueur s'en approche, et s'épaissit à mesure qu'il s'en rapproche.

**Bordures de monde.** Le worldborder natif est masqué ; la vraie bordure est gardée dans `borderdata.yml`.
`/worldborder set` et `/worldborder center` restent utilisables : leur valeur devient la nouvelle bordure invisible.

**Bordures d'îles** (SuperiorSkyblock2). Le mur suit la zone protégée de l'île, là où ses membres
peuvent construire. En vol (ou en élytres), le joueur peut la dépasser d'un bloc (`islands.margin`) pour
poser les blocs du bord. À pied, il s'arrête au bord : il ne peut pas marcher sur un bloc posé hors de l'île,
que la protection retire aussitôt.
`stop-leaving` doit être à `false` dans la configuration de SuperiorSkyblock2, sinon il arrête le joueur
au bord exact, avant cette marge.

Dans les deux cas :
- le joueur glisse le long du mur au lieu de s'arrêter net ;
- une perle ou un fruit de chorus qui mènerait au-delà est annulé ;
- un joueur en monture ou en bateau en descend contre le mur ;
- un joueur qui se retrouve déjà dehors est renvoyé au spawn du monde, ou sur l'île.

Côté réseau, chaque mur visible coûte un seul paquet par affichage : le client répartit lui-même les
particules autour du point visé.

Utilisé sur : **Opale**.

## Commandes et permissions

| Commande | Permission | Effet |
| --- | --- | --- |
| `/shinviworldborder reload` | `shinviworldborder.admin.reload` (op) | Recharge `config.yml`, `messages.yml` et `borderdata.yml`. Un fichier invalide annule le rechargement et garde l'ancienne configuration. |
| `/shinviworldborder info [monde]` | `shinviworldborder.admin.info` (op) | Bordure réelle de chaque monde (le worldborder natif est masqué à la taille maximale) et réglages actifs. |
| | `shinviworldborder.admin` (op) | Parent des deux permissions précédentes. |
| | `shinviworldborder.bypass` | Traverser les bordures. Le mode bypass de SuperiorSkyblock2 ouvre aussi les bordures d'îles. |
| | `shinviworldborder.margin.<nom>` | Marge d'île élargie, définie dans `islands.margins` (SkyHand : `vip` 2 blocs, `vipplus` 5, `vipelite` 10). |

## Configuration

Fichiers par défaut, avec des valeurs d'exemple. Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHInviWorldborder/
├── <a href="src/main/resources/config.yml">config.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
