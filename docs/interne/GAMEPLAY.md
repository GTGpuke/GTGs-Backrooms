# Vision gameplay — GTG's Backrooms

> **Document de conception interne** : il nomme des projets de référence. À garder hors du dépôt public, et sans
> aucune reprise de code ni mention de ces projets dans le mod (`QUALITE_ET_PRESETS.md` §3.0).
>
> Condensé de la vision de jeu de Pierre, rangé par catégorie. Les parties **« Vision »** reprennent ses idées sans
> rien retirer ; les parties **« Notes »** ajoutent des précisions techniques ; le §15 rassemble des **propositions**
> pour aller plus loin, à discuter, qui ne font pas partie de la vision tant qu'elles ne sont pas validées.
> Documents compagnons : `MOTEUR_SHADERS.md`, `MOTEUR_AUDIO.md`, `QUALITE_ET_PRESETS.md` (paliers de qualité, presets
> visuels et sonores, menu de réglages).

---

## 1. Vision générale

**Vision**

- Par défaut, le mod est une **prison** : il n'est pas facile d'aller dans les Backrooms par ses propres moyens. Les
  modérateurs peuvent s'en servir comme **lieu de punition pour les joueurs pénibles**.
- **Tout est configurable** sur ce point : le mod peut aussi servir autrement.
- Tout ça **ne doit pas ressembler à Minecraft**, doit être **bien fait**, tout en gardant une **esthétique qui
  rappelle le jeu**.

### 1.1 Règle absolue : le jeu de base reste parfaitement normal

**Hors des Backrooms, l'expérience doit paraître et être parfaitement vanilla**, pour tous les joueurs, y compris
ceux qui n'y sont jamais allés. Toutes les mécaniques de ce document (perception, santé mentale, hallucinations,
monstres, faune, nouvel inventaire, journal, PC, tips, mort, règles, restrictions de F3 et de commandes, effets du
chat, réglages de qualité, sac à dos, endurance, faim et sommeil, musique et sons des niveaux) **n'existent que dans
les Backrooms**. Même règle que le périmètre des deux moteurs
(`MOTEUR_SHADERS.md` et `MOTEUR_AUDIO.md`, §1.1) : l'Overworld reste vanilla à l'image, au son et au gameplay.

Concrètement, dans l'Overworld et les autres dimensions vanilla :

- inventaire, F3, coordonnées, commandes, mort et respawn, chat, menus : **strictement vanilla** ;
- aucun tip, aucune hallucination, aucun effet de santé mentale, aucun élément d'interface du mod ;
- aucune créature des Backrooms ne s'y promène d'elle-même ;
- un joueur qui n'est jamais entré dans les Backrooms ne doit rien remarquer.

**Seules exceptions, voulues et visibles uniquement par les personnes concernées :**

| Exception                                   | Pourquoi                                              |
| ------------------------------------------- | ----------------------------------------------------- |
| Le **colis « =) »** au retour d'un joueur   | Rendre l'équipement ; c'est un objet du monde, rien de plus |
| La **commande admin** et son portail        | Accès des admins et des modérateurs                   |
| Les **portails** eux-mêmes, et ce qu'on voit et entend à travers | Le passage entre les deux mondes (périmètre des moteurs) |
| Le **chat** : les messages envoyés depuis les Backrooms n'apparaissent pas dans l'Overworld | Règle du §3 ; le chat de l'Overworld, lui, reste vanilla |
| Le **générique de sortie**, vu seulement par le joueur qu'un admin fait sortir (§2.1) | Fin du séjour dans les Backrooms ; ajouté par décision explicite |

Le **menu créatif** et les commandes vanilla restent eux aussi strictement vanilla : aucun item des Backrooms n'y
apparaît (§3.1).

Toute nouvelle idée qui ferait sortir quelque chose des Backrooms (annonce, effet, créature, interface) doit soit
être refusée, soit être ajoutée à ce tableau **par décision explicite**, et désactivable dans la config.

**Notes**

- La ligne à tenir : le **rendu** reste « Minecraft, mais étrange » (les deux moteurs), alors que le **gameplay et
  l'interface** assument d'être nouveaux, avec une esthétique pixel, la palette et la police du jeu.

---

## 2. Entrer, sortir, et ce qu'on emporte

**Vision**

- **Aucun équipement dans les Backrooms, peu importe comment.**
- Seule exception : une **commande admin** qui fait apparaître un **portail d'entrée et de sortie**.
- **Au retour dans l'Overworld**, l'équipement du joueur l'attend dans un **colis** :
  - devant son **lit**,
  - ou devant sa **porte**,
  - ou au pied d'un **sapin de Noël**, peu importe la saison,
  - avec la signature **« =) »**.

**Notes**

- Les portails font déjà passer objets jetés, mobs et interactions à travers le cadre : il faut bloquer **toutes**
  les voies d'entrée d'un objet (objet lancé, mob qui porte un objet, bloc posé à travers le cadre, coffre de l'Ender,
  boîte de Shulker).
- La mise de côté de l'inventaire doit **résister aux crashs** : sauvegardée avant la téléportation, de façon atomique.
- Cas du colis à prévoir : pas de lit, lit obstrué, lit d'un autre joueur, chunk jamais chargé, plusieurs retours
  successifs.

### 2.1 Sortir des Backrooms

**Vision**

- **Pas de sortie prévue** pour l'instant, sauf par la **config**. Il existera un moyen de sortir, dont l'origine
  n'est pas encore définie.
- **À terme**, quand le reste sera développé : **plusieurs niveaux avec une sortie possible**, comme dans le lore des
  Backrooms. Pas envisagé pour l'instant.
- **Sortie par un admin** : une commande force le joueur à **noclip hors des Backrooms**, puis un **générique** (un
  peu comme la fin de Minecraft, avec tout ce qui a été fait et les remerciements), puis **retour au point de spawn du
  joueur**.

**Notes**

- Le noclip de sortie peut réutiliser l'effet de passage entre niveaux (§4) ; le générique est un écran du mod, vu
  **uniquement par le joueur qui sort** (exception ajoutée au tableau du §1.1).
- Après le générique, tout redevient vanilla ; l'équipement revient par le colis « =) » (§2).
- La commande de sortie fait partie de la **porte de secours** du §3 (console ou admin resté dehors).

---

## 3. Règles dans les Backrooms

**Vision**

- **Aucune commande** pour faire quoi que ce soit dans les Backrooms.
- **Pas de coordonnées, pas de F3.**
- **Le chat est autorisé**, mais **visible uniquement par ceux qui sont dans les Backrooms**, avec un **effet
  particulier**.
- Les **réglages de qualité** des shaders et du son ne sont **configurables et visibles que dans les Backrooms**.

**Admins**

- Un admin accède aux Backrooms par une **commande qui fait apparaître un portail**.
- Les admins **peuvent rester en créatif**.
- **Une fois en survie, ils sont coincés aussi.**

**Configuration**

- Un fichier de config **très configurable**, à la fois sur **la façon d'entrer** dans les Backrooms et sur **les
  règles qui s'y appliquent**.

**Notes**

- Coordonnées : Minecraft sait masquer les infos de débogage **par joueur** (mode « infos de débogage réduites »,
  activable par le serveur pour un joueur précis) ; masquer totalement F3 demande un mixin client.
- Chat : filtrer les **destinataires** par dimension, sans **modifier le texte** côté serveur (signature du chat
  sécurisé de 1.21) ; l'effet visuel se fait côté client.
- Config **côté serveur**, synchronisée aux clients et validée au chargement.
- **Porte de secours obligatoire** : la console du serveur, ou un admin resté dehors, doit toujours pouvoir débloquer
  quelqu'un, y compris un admin passé en survie. Commandes de dev réservées à l'environnement de développement.

### 3.1 Les items des Backrooms n'existent pas, vus de l'extérieur

**Vision**

- **Menu créatif** : **aucun item lié aux Backrooms**.
- **Pas de commande `/give`** ni d'autre moyen d'obtenir ces items.
- **Comme s'ils n'existaient pas.**
- **Exception : le mode développement**, où le menu créatif, `/give`, et les modes **créatif** et **spectateur**
  sont disponibles aussi.

**Notes**

- Ce qui existe déjà dans le mod est à **passer en mode développement uniquement** : l'onglet créatif
  *GTG's Backrooms - Portails* et le `/give` du portail documentés dans `PORTALS.md`. Hors développement, le seul
  accès reste la **commande admin** qui fait apparaître un portail (§2).
- Les items existent forcément dans les registres du jeu (sinon les Backrooms ne pourraient pas s'en servir). Pour
  qu'ils restent invisibles, il faut couvrir **toutes les portes d'entrée** :
  - onglets et recherche du menu créatif ;
  - suggestions et exécution de `/give`, `/item`, `/loot`, `/clear`, et pour les blocs `/setblock`, `/fill`,
    `/summon` des entités du mod ;
  - **clic molette** en créatif sur un bloc du mod vu à travers un portail ;
  - livre de recettes, recettes, butins (loot tables) et progrès qui feraient apparaître un item ;
  - mods d'affichage d'items (JEI, REI, EMI) : les items du mod y sont masqués.
- Un item des Backrooms qui se retrouverait hors des Backrooms malgré tout (bug, ancienne sauvegarde) est **retiré**
  à la détection, pour garder la règle du §2 et du §1.1.
- Le mode développement se reconnaît comme aujourd'hui (`isDevelopmentEnvironment()`), éventuellement doublé d'une
  option de config explicite pour les serveurs de test, désactivée par défaut.

---

## 4. La perception (touche V)

**Vision**

- En maintenant **V**, le personnage **se concentre sur une énergie invisible**. Ce n'est **ni un wallhack ni un
  x-ray** : ça rend visible ce qui est **normalement invisible**.
- On peut :
  - **voir les zones de noclip**, au sol ou dans les murs ;
  - **voir les traces** de certains monstres ;
  - **sentir la présence** de certains monstres ;
  - **sentir certains items** ;
  - utiliser une forme de **pathfinding** vers certains éléments (intéressant pour les **level keys**, à voir).
- Une fois une zone de noclip approchée, il faut **appuyer sur un bouton pour passer à travers** : **aucune chute
  accidentelle** dans un autre niveau.
- **Contrepartie** retenue : la **vision se brouille**, et/ou **des monstres sentent** quand on l'utilise. L'avantage
  devient une prise de risque.

**Notes**

- Toutes les données de perception sont **envoyées par le serveur, et seulement dans le rayon de perception** :
  un client modifié ne doit pas pouvoir tout voir.
- Le passage d'un niveau à l'autre peut réutiliser la **traversée sans coupure** des portails.
- Rendu : un look de plus pour le moteur de shaders.

---

## 5. Santé mentale et hallucinations

**Vision**

- Système de **santé mentale** et d'**hallucinations propres à chaque joueur**, avec **énormément d'effets**.
- **Les hallucinations ne tuent jamais**, mais elles peuvent **vraiment faire du mal** et **donnent des malus**.
- Dans les niveaux autres que le 0, elles peuvent **attirer certains monstres**.
- Leur intensité est **très forte dans le niveau 0**, et **variable** dans les autres niveaux, selon le lore et les
  besoins du gameplay.

**Notes**

- Propres à chaque joueur : elles se jouent **côté client**, l'autre joueur ne les voit pas (§15.2).
- Intensité réglable et soumise au **plafond de clignotement** du moteur audio (accessibilité).

### 5.1 Ce qui fait baisser et remonter la santé mentale

**Vision**

- Les hallucinations sont une mécanique de **tous les niveaux** ; le niveau 0 en a **plus**, et **plus souvent**, à
  cause de son effet sur la santé mentale. **Pas d'ordre scénarisé.**
- Selon le niveau, la santé mentale **baisse plus ou moins vite naturellement**, et ne **remonte que rarement**
  d'elle-même ; certains **endroits précis** peuvent la faire remonter (par exemple la **Manila Room**, §6.1).
- Elle baisse plus ou moins selon : l'**obscurité**, les **monstres**, les **sons**, certains **items**, les **actions
  des joueurs**, le fait de **se faire attaquer**, et le **manque de sommeil**.
- Elle remonte avec : certains **items** (l'Almond Water, par exemple, §9.8), le **sommeil dans un endroit sûr**, et
  d'autres évènements à définir (pistes au §15.11).
- Elle n'est visible **que subtilement** (§9.8) : aucune jauge.

**Notes**

- Un **taux de base par niveau** (donnée du niveau), plus des **modificateurs** additionnés à chaque instant
  (obscurité autour du joueur, monstre perçu, son marquant, item, attaque, temps sans dormir).
- Calculée **côté serveur** (elle influe sur les monstres et les malus), puis transmise au seul joueur concerné pour
  les effets ; les hallucinations restent jouées **côté client** (§5, notes).

---

## 6. Les niveaux

**Niveau 0**

- **Seul niveau sans entités.**
- C'est le **tutoriel** : il introduit d'abord la **santé mentale** et les **hallucinations**. *(Précisé au §6.1 :
  sans ordre scénarisé.)*
- Des **bruits communs à tous les joueurs**.
- Des **créatures dans les ventilations murales**, ou qui **déplacent des plaques du plafond** : un indice que **les
  niveaux sont reliés** et que des créatures passent d'un endroit à l'autre.
- **Seul danger : les hallucinations.**

**Autres niveaux**

- Chacun a sa **propre biologie** (§8) et ses **gros monstres** (§7).
- Intensité des hallucinations propre à chaque niveau.
- Certains niveaux sont accessibles **depuis le PC** (§11) ; des **level keys** existent (§4).

**Notes**

- Les créatures du niveau 0 ne doivent pas être des entités au sens du jeu (puisqu'il n'y en a pas) : ce sont des
  **effets** (sons, plaques qui bougent, ombres), ce qui reste cohérent et léger.
- Les **cris** entendus dans le niveau 0 sont des **sons d'hallucination**, propres au joueur qui hallucine
  (`MOTEUR_AUDIO.md` §1.1, point 6), ou des sons venus d'un autre niveau à travers un portail. S'y ajoutent les **cris
  d'ambiance**, sans source (§6.3).
- Les tests et bancs qui demandent de vrais monstres (cris, poursuites) se font dans un **niveau habité** ou dans un
  **niveau de test réservé au développement**, jamais en ajoutant des entités au niveau 0.

### 6.1 Le niveau 0 : se perdre, puis trouver la Manila Room

**Vision**

- **Pas de tutoriel scène par scène, pas d'ordre** : les hallucinations y sont plus fortes et plus fréquentes, c'est
  tout (§5.1).
- **Le but du niveau 0 est de perdre les joueurs**, et de leur donner l'incompréhension de se croire perdus, jusqu'à
  trouver la **Manila Room**, au bout de **plusieurs centaines, voire milliers de blocs** de marche.
- La Manila Room est **générée en 0, 0** dans le monde.
- Un **portail apparaît de temps en temps** pour les joueurs, pour leur permettre de trouver la pièce.
- Ce système laisse les joueurs se perdre **autant qu'on veut**, sans les frustrer ni contraindre l'architecture du
  niveau 0 : on peut **régler** l'expérience de recherche sans abîmer le ressenti des Backrooms.

**Notes**

- Pas de coordonnées ni de F3 (§3) : la position 0, 0 reste invisible pour le joueur.
- Le portail d'apparition réutilise les **portails sans coupure** du mod ; sa fréquence, sa distance d'apparition et
  sa durée sont des **réglages de config** (temps passé dans le niveau, distance parcourue, santé mentale).
- La Manila Room est aussi un **lieu où la santé mentale remonte** (§5.1).

### 6.2 Pièces uniques du niveau 0

**Vision**

- Des **pièces uniques**, sur **plusieurs étages** du niveau 0, pour **casser l'homogénéité** et placer des
  **références à des espaces liminaux connus**.
- Ce seront sans doute des **pièces construites à la main** par Pierre, ou avec l'agent de développement.

**Notes**

- Stockées comme des **structures** posées par le générateur à des emplacements déterministes, pour rester
  cohérentes avec l'acoustique et l'éclairage cuits par cellule (`MOTEUR_AUDIO.md` §6.4, `QUALITE_ET_PRESETS.md` §5.5).
- Les assets suivent la bible de style (`ASSETS_ET_PRODUCTION_IA.md` §4) ; les références restent des **ambiances**,
  sans reprise d'œuvre protégée.

### 6.3 Son et musique, selon les niveaux

**Vision**

- Une **musique d'ambiance liminale**, et d'autres, qui passe **de temps en temps**, **très faible**.
- Des **haut-parleurs** en haut des murs ou sur des meubles, qui diffusent **une autre musique**, **spatialisée**.
- Des **effets sonores propres aux Backrooms et au niveau**, comme des bruits de grotte : **cris**, **échos**,
  **meubles qui tombent**, **bruits de pas dans les conduits**, **voix**. Ils n'ont **pas forcément de lien avec la
  santé mentale** (ou peut-être en partie, à voir).
- **Atténuation et écho directionnel**, à la façon de *The Last of Us*, si c'est possible.
- À voir : comme pour la physique des items, un **moteur physique du son** écrit dans un autre langage, comme le
  fait *Create Aeronautics*.

**Notes**

- **Musique d'ambiance** : non spatialisée, très faible, à intervalles irréguliers ; réglage de volume dédié
  (`QUALITE_ET_PRESETS.md` §8).
- **Haut-parleurs** : de vraies sources dans le monde, avec l'acoustique du lieu (réverb, occlusion) du moteur audio ;
  la musique qui en sort est un son du niveau comme un autre, entendu aussi à travers un portail avec son acoustique
  d'origine (`MOTEUR_AUDIO.md` §7).
- **Effets d'ambiance** : sons **sans entité**, communs à tous les joueurs (comme les « bruits communs » du niveau 0).
  Dans le niveau 0, un cri d'ambiance reste un **son d'ambiance** : il n'utilise pas le `Cue` `scream`, réservé aux
  hallucinations dans ce niveau (`MOTEUR_AUDIO.md` §6.1), ni le tremblement de caméra. Le lien éventuel avec la santé
  mentale passe par sa fréquence ou son intensité, pas par une source.
- **Atténuation et écho directionnel** : déjà en grande partie prévus par le moteur audio (occlusion multi-chemins,
  réflexions, direction perçue en Ultra : `QUALITE_ET_PRESETS.md` §8). À étendre en priorité dans ce moteur.
- **Moteur dans un autre langage** : possible (bibliothèque native appelée depuis Java), mais avec un coût réel : une
  version compilée par système, macOS compris, des plantages natifs plus durs à diagnostiquer, et des échanges de
  données à chaque image. À n'envisager **qu'après mesure**, si le Java optimisé ne suffit pas.

---

## 7. Les gros monstres

**Vision**

- Les monstres **entendent, voient et sentent**.
- Comportements : **lurk, chasse, course, fouille**, **se cacher pour tendre des embuscades**, et d'autres selon le
  type de monstre.
- **Pas tuables**, mais **chacun a un moyen de le faire fuir ou de le faire disparaître**.
- Le **cri** de certains monstres **fait trembler l'écran** à proximité et a des **échos réalistes**.

**Notes**

- IA : une **machine à états pilotée par les sens**, avec un profil de sens et de comportements par type.
- L'**ouïe** côté serveur peut réutiliser l'acoustique déterministe du moteur audio : un monstre entend moins bien à
  travers les murs, avec la même logique que ce que le joueur entend. L'**odorat** : une piste d'odeur qui s'estompe.
- Tremblement et écho : déjà prévus par le moteur audio (`camera_shake`, réverb), avec plafond et option de réduction
  des mouvements ; jamais de tremblement dans l'Overworld, même pour un cri entendu à travers un portail.
  Le tremblement est **actif par défaut** (conformément à cette vision), très faible et plafonné, et coupé par le
  preset **Confort** (`QUALITE_ET_PRESETS.md` §7, `MOTEUR_AUDIO.md` §11).
- Les monstres doivent **connaître les cachettes** (§9) pour pouvoir les fouiller.

### 7.1 Les « Smilers » : la lumière comme arme et comme provocation

**Vision**

- Les Smilers **détestent la lumière**. Une lampe torche ou toute autre **source de lumière directement pointée** sur
  eux (pas une torche, qui éclaire une zone autour, mais une source **pointée**) les fait **entrer dans une rage folle**
  ou **essayer de fuir la source**.
- Selon la lumière :
  - **joueur dans le noir** : les Smilers **l'attaquent** ;
  - **torche ou source de lumière indirecte** : ils **hésitent**, mais **attaquent** ou **orbitent autour, comme des
    loups** ;
  - **lumière pointée directement** : soit **fuite**, soit, **à force de répétition**, **rage folle** et **poursuite
    malgré la lumière pointée** ;
  - si le joueur **rejoint une zone éclairée**, le Smiler essaie quand même de passer **en brisant les sources
    lumineuses** ; ça **prend du temps**, et il peut **perdre le joueur** à ce moment-là.
- Apparence : un Smiler est **grand**, au **corps reptilien**. Seul son **sourire lumineux** est visible, avec
  **plusieurs types de sourires**. Son corps est **invisible**, sauf dans de **très rares conditions**, et il est
  **très légèrement visible à la lumière en utilisant la vision « V »**.

**Notes**

- **Nom** : « Smiler » est un nom du vocabulaire communautaire des Backrooms. Le mod parle des Backrooms : le nom est
  **conservé** (décision, §14 point 7). Ce qu'on ne reprend pas, ce sont les **textes** et descriptions du lore
  communautaire (§15.9). Le comportement décrit ici est le nôtre.
- **Lumière pointée** : la lampe torche est une **lumière de type spot** du moteur (`QUALITE_ET_PRESETS.md` §5.1),
  portée par le joueur et synchronisée par les données d'entité. Le serveur calcule si le cône de la lampe touche le
  Smiler (direction, angle, distance, obstacles) : c'est **lui** qui décide de la réaction, pas le client.
- **Rage par répétition** : un compteur d'agacement par Smiler, qui monte à chaque exposition et redescend avec le
  temps. Sous un seuil, il fuit ; au-delà, il charge malgré la lumière.
- **Briser les lumières** : les néons et lampes ont un état « cassé » côté serveur ; un néon cassé s'éteint pour tous
  (lumière du moteur, bourdonnement coupé, `MOTEUR_AUDIO.md` §6.8). Le temps de casse est la fenêtre de fuite du joueur.
- **Rendu** : seul le **sourire émissif** est dessiné en temps normal ; le corps a un rendu séparé, activé seulement
  dans les « très rares conditions » et, très faiblement, en perception sous la lumière. Comme toute donnée de
  perception, l'information n'est envoyée qu'au joueur concerné (§4, notes).
- **Orbite comme des loups** : un comportement de groupe (plusieurs Smilers qui tournent à distance de la lumière et
  attendent une ouverture) : à concevoir avec l'IA « machine à états pilotée par les sens » du §7.

### 7.2 Le micro : les monstres entendent le joueur

**Vision**

- **Détection du bruit du micro**, **compatible avec Simple Voice Chat**.
- Elle sert à **l'ouïe des monstres** et à un **effet d'écho** sur la voix.
- **Sans Simple Voice Chat** : la détection du micro ne fonctionne **qu'en solo**. *(Remplacé par la décision
  ci-dessous : le micro fonctionne aussi en multijoueur sans chat vocal.)*
- **Décision** : le micro est **activé**, et **tout est traité en local** ; rien du son ne passe au serveur. Il sert
  au joueur pour l'**écho** (la seule façon dont il s'en rend compte). La **seule** chose envoyée au serveur est un
  **vrai ou faux**, pour que les monstres sachent. La **sensibilité** du micro se règle dans le menu du mod.
- **Sans micro** : tant mieux pour le joueur.
- **Avec Simple Voice Chat** : il débloque le **vol de voix** par certains monstres, et le fait de **s'entendre entre
  joueurs** avec l'écho et le moteur sonore du mod.

**Notes**

- **Avec le chat vocal** : le serveur reçoit déjà le flux vocal ; on en tire un **niveau sonore** (pas le contenu),
  converti en **bruit** à la position du joueur, entendu par les monstres avec l'acoustique réelle (§7, notes).
  *(Option technique antérieure ; la décision ci-dessus s'applique : les monstres n'ont besoin que du vrai ou faux.)*
  L'identifiant technique du mod de chat vocal est toléré dans le code de compatibilité, sans aucun commentaire autour
  (`PROMPT_AUDIT.md`, passe 1).
- **En solo, sans chat vocal** : capture du micro **par le client**, réduite à un **niveau sonore** calculé
  localement ; **rien n'est enregistré, stocké ni envoyé**.
- **Consentement** : le micro n'est jamais ouvert sans **accord explicite** du joueur, via une option dédiée,
  **désactivée par défaut**, avec un **indicateur visible** quand il écoute. C'est une exigence de confiance, et souvent
  une exigence légale.
  *Avec la décision « micro activé »* : garder au minimum une **demande unique à la première entrée** dans les
  Backrooms (réponse par défaut « oui »), l'**indicateur visible** et la possibilité de le couper dans le menu.
  Traitement local et simple vrai ou faux vont dans le bon sens, mais l'ouverture du micro sans aucun avis reste à
  éviter (confiance, règles des plateformes et du système).
  **Décision** : un **petit tooltip** le signale à l'**arrivée dans le niveau 1**, là où le micro devient vraiment
  utile ; dans les **réglages du micro**, une mention claire indique que le micro ne sert qu'à une **détection
  vocale** et que **rien d'autre n'est transmis au serveur**.
- **Écho** : la voix reçoit l'acoustique du lieu (réverb du moteur audio). Avec le chat vocal, cela demande d'agir sur
  la lecture de ses voix côté client : plus complexe, à prévoir après l'écho du solo.
- **Équité** : un joueur sans micro, ou qui l'a coupé, ne doit pas être avantagé ; à trancher (§14).
  **Tranché** : l'avantage est accepté (§14, point 6).
- **Vrai ou faux** (**confirmé** : c'est une **détection vocale**, « oui » = le joueur parle, « non » = il ne parle
  pas) : il signifie « le joueur parle en ce moment » (voix au-dessus de la sensibilité réglée), calculé
  sur le client et envoyé seulement quand il change. C'est ce qui permet à un monstre d'**entendre parler** un joueur
  caché (§9.6). Un client modifié peut le fausser : seul cet avantage est en jeu, rien d'autre ne dépend du client.
- **Vol de voix** (avec chat vocal) : la voix d'un joueur, déjà relayée par le serveur du chat vocal, peut être
  rejouée par un monstre ; à concevoir avec soin (court extrait, jamais stocké au-delà de la session).
- **Périmètre** : le micro n'est écouté **que dans les Backrooms** (§1.1).

### 7.3 Tous les monstres : rareté, IA et écosystème

**Vision**

- **Premier monstre du niveau 1** : à définir d'après le lore du niveau 1 ; les Smilers n'y sont probablement pas.
  **Chaque monstre** aura potentiellement des **mécaniques uniques**.
- Référence : le **wiki communautaire des Backrooms** (« model 3+ »). Chaque monstre a une **particularité**, mais les
  monstres sont **rarement propres à un seul niveau**.
- Dans les niveaux, les monstres sont **rares** ; ils peuvent parfois être **plusieurs**, et **très rarement en même
  temps**.
- Chaque niveau doit donner le sentiment d'une **biologie** et d'un **écosystème très singuliers** : ne pas hésiter
  à créer des **comportements organiques et immersifs**.
- De **vraies IA intelligentes**, pour **empêcher les joueurs de comprendre comment elles marchent** et garder la
  peur.

**Notes**

- Le wiki sert de **référence de concepts** (particularités, présence par niveau) ; les textes ne sont pas repris
  (§15.9).
- **Imprévisibilité** sans tricher : des sens réels (§7), une **mémoire** par monstre (dernières positions, cachettes
  fouillées, habitudes du joueur, §9.6), des **besoins** (faim, territoire, repos) qui créent des comportements hors
  chasse, et une part de hasard pondéré dans les choix. Un monstre ne connaît jamais la position du joueur sans l'avoir
  perçue.
- **Rareté** : un budget de monstres par zone et par niveau, côté serveur, pour qu'une rencontre reste un évènement.

---

## 8. Faune et écosystème

**Vision**

- En plus des « gros monstres », **toute une biologie** dans chaque niveau.
- Des **petites créatures** (rats, chiens…), **tuables**, elles.
- Un **système de boids** pour les plus petites créatures.
- Elles font partie d'une **chaîne alimentaire**.

**Notes**

- Créatures purement visuelles : boids **côté client**, sans réseau. Créatures de la chaîne alimentaire : agents
  simplifiés **côté serveur**, avec une grille spatiale.
- Loin des joueurs : **populations simulées par zone**, recréées en détail à l'approche (même logique que le LOD).

---

## 9. Survie : items, cachettes, inventaire, progression

**Vision**

- **Beaucoup d'items à trouver.**
- Des **façons de se cacher** ou de **semer ses poursuivants** :
  - **casiers** ;
  - **crevasses d'un demi-bloc dans les murs** pour s'y glisser ;
  - **sous les bureaux et les lits** ;
  - et d'autres.
- Un **nouveau système d'inventaire**, un **système d'items**, et une **progression**.

**Notes**

- L'inventaire vanilla étant mis de côté à l'entrée, l'inventaire des Backrooms peut être **une donnée à part**,
  sans toucher aux emplacements vanilla.
- Les crevasses peuvent détourner la **pose « ramper »** de Minecraft (nage à sec dans un espace d'un bloc).

### 9.1 Fabrication (crafting)

**Vision**

- **Fabrication dans l'inventaire personnel** : **objets simples** et **réparation d'items**.
- **Fabrication à l'établi** : des **établis trouvables un peu partout dans les niveaux, mais rares**, pour fabriquer
  **certains outils**.
- **Certaines fabrications sont verrouillées derrière certains outils.**
- **Pas de menu de fabrication** : à l'établi, la caméra passe en **vue de haut, directement sur la table**, et on
  fabrique en **posant et en assemblant les objets sur sa surface**, comme le font certains mods immersifs.

**Notes**

- Recettes **propres aux Backrooms**, invisibles du livre de recettes vanilla (§3.1).
- Le verrouillage par outil crée une **progression naturelle** : trouver un outil ouvre des recettes, sans arbre de
  compétences.
- La **réparation** suppose une **usure** des objets (lampe, outils, piles) : à définir objet par objet.
  **Décision** : l'usure dépend de l'**objet** et de sa **qualité** (trouvé ou fabriqué), avec un système propre au mod,
  probablement différent de la durabilité vanilla (§14, point 10).
- Fabriquer peut **faire du bruit** (§15.3), ce qui donne un vrai choix : fabriquer maintenant ou à l'abri.
- **Vue de haut sur la table** :
  - transition de caméra douce depuis la vue du joueur vers le dessus de l'établi, puis retour ; le monde reste
    rendu normalement (éclairage, néons, ombres des deux moteurs), ce n'est pas un écran à part ;
  - les objets posés sont les **items physiques en 3D** du §9.4, alignés sur la grille du mod (1/32 de bloc) ;
  - **le jeu ne se met pas en pause** : on entend ce qui approche (son et acoustique normaux), et on peut quitter la
    table à tout moment d'une touche. Fabriquer devient un moment de vulnérabilité ;
  - **côté serveur** : ce qui est posé sur la table et le résultat sont décidés par le serveur ; en multijoueur, les
    autres joueurs voient les objets sur la table ;
  - **lisibilité** : un objet survolé affiche son nom, et une recette reconnue se signale discrètement (un léger
    éclat, un son), sans liste de recettes à l'écran ;
  - la vue de haut ne montre **rien de plus** que ce que voit le joueur (règle d'équité, `QUALITE_ET_PRESETS.md` §11) ;
  - fabrication **dans l'inventaire** : même logique ou non, à trancher (§14, point 11).

### 9.2 Blocs et matériaux

**Vision**

- **La majorité des blocs ne sont pas cassables.**
- Quand un bloc est cassable, il donne **des matériaux**, **pas l'objet en lui-même**.

**Notes**

- Cela vaut aussi pour le **minage à travers un portail** (déjà possible dans le mod, `docs/PORTALS.md`) : un bloc
  incassable des Backrooms reste incassable vu de l'Overworld.
- Les matériaux obtenus alimentent la fabrication (§9.1).
- Cohérent avec l'acoustique et l'éclairage cuits par cellule : peu de blocs modifiables, donc peu de recalculs.

### 9.3 Machines à café et à snacks

**Vision**

- **Machine à café** et **machine à snacks** avec une **vitre brisable** : les **items sont prenables** dedans.
- On peut aussi **utiliser des pièces normalement**, si on en a.

**Notes**

- Un choix naturel : **payer en silence** ou **briser la vitre bruyamment** (bruit entendu par les monstres, §15.3).
- Les **pièces** deviennent une petite ressource à trouver ; leur origine est à définir (§14).
  Trois pistes, à trancher : une **monnaie**, un **matériau de fabrication** qui sert aussi de pièce, ou un **objet
  unique** (jeton, clé de service) qui ouvre la machine sans la casser (§14, point 8).
- La vitre brisée est un état de bloc côté serveur : une machine pillée l'est pour tous.

### 9.4 Items physiques au sol

**Vision**

- Les items sont **en 3D sur le sol**, **même quand ils sont jetés**.
- Il faut faire un **clic droit** sur un item pour le **récupérer**.
- Ces items ont une **physique**, et **certains peuvent réagir**.

**Notes**

- Pas de ramassage automatique vanilla **dans les Backrooms** (§1.1 : hors des Backrooms, tout reste vanilla).
- **Physique légère et maison** : chute, rebond, roulement, rotation, repos ; pas de moteur physique complet. Calcul
  côté serveur pour la position qui compte, interpolée côté client.
- **Réactions** : une bouteille qui se brise, une canette qui roule, une pile qui étincelle ; chaque choc fait un
  **bruit** que les monstres peuvent entendre. Lancer un objet devient un **appât** (§15.3).
- Budget : nombre d'items physiques actifs par zone plafonné ; un item au repos ne coûte plus rien.

### 9.5 Blessures, saignement et traces au sol

**Vision**

- Quand le personnage est **gravement blessé par un monstre**, il **saigne** et **laisse des traces sur le sol**
  (voir les **mécaniques d'encre de la vidéo de doctorat**).
- Ces traces peuvent servir aux **monstres pour suivre le joueur**.
- Des **bandages** empêchent ça.
- Ça **ne concerne pas les hallucinations**, qui ne font que des **dégâts mentaux** au joueur.
- La **même physique de particules qui tombent au sol** peut servir pour les **traces d'autres monstres**, ou pour les
  **liquides un peu visqueux** de certains conduits.

**Notes**

- **Un seul système de traces** pour tout : sang, traces de monstres, liquides de conduits. Des gouttes qui tombent,
  puis une **marque au sol** posée sur la face du bloc, **alignée sur la grille des texels** (32 par bloc) pour garder
  l'aspect blocky et la cohérence avec les ombres pixelisées (`QUALITE_ET_PRESETS.md` §5.4).
- **Côté serveur** : la liste des traces (position, type, âge) fait autorité ; les monstres la lisent pour **pister**.
  Côté client : seulement le rendu des marques.
- Les traces **s'estompent** avec le temps ; leur nombre est plafonné par zone.
- En **perception** (§4), les traces de monstres et de sang peuvent ressortir davantage.
- **Bandages** : un item fabricable (§9.1), qui arrête le saignement.
- La **référence « vidéo de doctorat »** est à préciser (quelle vidéo, quelle mécanique exacte) pour en reprendre
  l'idée et non le code.

### 9.6 Cachettes : être vu, être entendu, être pisté

**Vision**

- **Si un monstre te voit te cacher, il t'attrape.**
- **Si un monstre entend parler dans une cachette**, pareil.
- Le reste du temps, le monstre peut :
  - **pister les traces** (le sang, §9.5) ;
  - **pister l'odeur** (moins de chances, selon le monstre) ;
  - **apprendre du joueur** : un monstre qui chasse un joueur depuis longtemps peut apprendre ses **habitudes** et
    **deviner** ; propre à chaque monstre.
- **Certaines cachettes sont meilleures que d'autres** : à définir dans l'**objet cachette** lui-même, avec la
  **probabilité d'être trouvé pour chaque monstre**, et à faire entrer proprement dans l'IA du monstre.

**Notes**

- **Donnée par cachette** : une probabilité de base d'être trouvé, puis un ajustement par type de monstre (par
  exemple : un casier peu sûr face à un monstre qui flaire, une crevasse très sûre face à un grand monstre).
- **Vu en train de se cacher** : le serveur vérifie si le monstre avait le joueur en vue au moment de l'entrée ; la
  cachette est alors « grillée » pour ce monstre.
- **Entendu** : le « vrai ou faux » du micro (§7.2) et les bruits du joueur (§15.3) comptent ; retenir sa
  respiration prend tout son sens.
- **Apprentissage** : une mémoire par monstre et par joueur (cachettes utilisées, directions de fuite, fréquence),
  qui augmente les chances de fouiller le bon endroit ; elle s'efface avec le temps pour laisser une chance.

### 9.7 Le sac à dos (inventaire des Backrooms)

**Vision**

- Un **inventaire en forme de sac à dos**, dans lequel on **fouille**, avec une vue et une immersion proches de
  celles des établis et ateliers (§9.1).
- Des **améliorations de sac** ou de **meilleurs modèles de stockage**, trouvables selon ce qu'on fouille dans les
  niveaux, pour porter **plus d'items**.
- **Pas de limite de poids** : un système proche de Minecraft, mais avec des **items plus gros que d'autres**.
- Exemple : les **outils**, si le sac n'a pas d'**espace sur le côté** (rangement dédié, accès rapide).

**Notes**

- **Grille d'emplacements** à la Minecraft, où certains items occupent **plusieurs cases** ; les **poches latérales**
  sont des emplacements dédiés aux outils et à l'accès rapide. Un sac sans poche latérale oblige à ranger l'outil dans
  la grille, où il prend de la place.
- **Fouiller sans pause** : comme à l'établi, le monde continue pendant qu'on fouille son sac.
- **Côté serveur** pour le contenu (prison oblige) ; le sac est une donnée à part, sans toucher aux emplacements
  vanilla (§9, notes).

### 9.8 Le corps : vie, endurance, faim et sommeil

**Vision**

- **Pas de barre de faim** à l'écran, une **vie plus subjective**, et une **santé mentale visible seulement
  subtilement**.
- Une **endurance** (stamina), **pas limitante**, mais peut-être plus contraignante quand le personnage est
  **poursuivi** ; beaucoup d'objets peuvent aider. Ressentie par le **souffle**, le **flou** et les **battements**.
- **Pas de soif.**
- Une **faim moins restrictive** que dans Minecraft, qui donne des **bonus** ou des **malus** selon ce qu'on mange.
  Par exemple : l'**Almond Water** redonne de la **vie** et de la **santé mentale** ; la **Moth Jelly** donne un
  **boost d'endurance**.
- **Décision** : **pas de faim** non plus. La **nourriture** garde son rôle de **bonus** et aide à **se soigner**
  (l'Almond Water et la Moth Jelly gardent leurs effets ci-dessus). *(Remplace la « faim moins restrictive » : rien
  à représenter à l'écran, aucun état de faim.)*
- Le **manque de sommeil** compte (§5.1).

**Notes**

- **Interface quasi absente** : l'état se lit sur l'image et le son (couleur et netteté de l'image, souffle,
  battements, démarche), avec les moteurs ; aucune jauge permanente.
- **Endurance** : peu de contrainte en temps normal, une baisse plus rapide sous la peur ou en poursuite.
- **Faim** : un état lent, sans mort de faim brutale ; les aliments sont des **effets** (bonus et malus) plus que des
  points de faim. *(Option antérieure ; décision : pas de faim, la nourriture n'apporte que des effets et des soins.)*
- **Nourriture** : chaque aliment est un **effet** (soin, santé mentale, endurance, ou malus pour un aliment douteux),
  avec une durée ; la faim vanilla est neutralisée **dans les Backrooms seulement** (§1.1).
- **Sommeil** : dormir dans un lit ou un endroit sûr ; dormir dans un endroit dangereux expose au monstre.
- **Lisibilité et accessibilité** : la règle d'équité s'applique (`QUALITE_ET_PRESETS.md` §11) ; l'état reste
  lisible avec le preset Confort (moins de flou, d'autres signaux).

---

## 10. Mort et respawn

**Vision**

- Un **autre système de mort**, avec deux choix :
  - **réapparaître dans son lit, dans la dimension** ;
  - ou **réapparaître près du lieu de sa mort**, avec le risque de **recroiser la menace**.
- **Par défaut, impossible de réapparaître dans l'Overworld** à la mort.
- **Précision** : les deux choix à la mort sont :
  - **réapparaître dans un rayon fixe autour du point de mort** : risque de recroiser la menace qui a tué, mais
    équipement facile à récupérer ;
  - **réapparaître dans un lit ou une cachette** : en sécurité, mais sans équipement facile à retrouver.

**Notes**

- Comportement sans lit dans la dimension à définir. **Tranché** : la réapparition « sûre » se fait dans un lit
  **ou une cachette** (§14, point 3).
- Le rayon fixe est un réglage de config ; le point d'apparition est choisi hors de la vue du monstre qui a tué, mais
  dans le rayon.
- Lié à la question de la sortie (§14).

---

## 11. Connaissance : tips, journal, PC

**Tips**

- Des **astuces à certains moments du jeu**, par exemple : *« N'oubliez pas de consulter vos notes avant de passer
  dans des endroits suspects. »*

**Journal**

- Un journal **façon Patchouli, mais refait** maison.
- **Bestiaire**, et **archives** de ce qu'on sait d'un niveau et des autres mécaniques.

**PC**

- Un **PC avec un OS complet**.
- Pour **accéder à des informations**, mais aussi **à certains niveaux**.
- Interface **inspirée de Windows XP**, mais globalement **très Backrooms**.
- Un **navigateur**, l'accès à la **documentation**, des **énigmes**, des **.exe**, des **petits jeux**, et des **.exe
  qui donnent accès à des niveaux ou à des pièges** (qui invoquent des monstres horribles).
- Le **contenu peut changer selon l'emplacement du PC**, ce qui permet des **éléments rares**.
- La **documentation ne porte que sur ce qu'on a déjà vu** : ça brise le quatrième mur, mais ça garde le mystère.

**Notes**

- Tips : déclenchement contextuel, une seule fois par joueur, désactivables.
- Journal refait maison : aucune question de licence, et une esthétique entièrement maîtrisée.
- PC : contenu (fichiers, mails, accès) **en données**, état **côté serveur** pour qu'un PC soit le même pour tous.
- PC et documentation : l'**état du PC** (fichiers, .exe déjà lancés, pièges) est commun à tous ; la **documentation**
  affichée est filtrée par **ce que le joueur qui regarde a déjà vu** (bestiaire du §15.7).
- Interface « inspirée de » : style d'époque recréé par nous, sans reprendre d'éléments graphiques du système
  d'origine (logos, icônes, sons).
- Un .exe piège invoque un monstre **réel** côté serveur, soumis au budget de rareté (§7.3).

---

## 12. Esthétique et qualité

**Vision**

- **Ne pas ressembler à Minecraft**, être **bien fait**, avec une **esthétique qui rappelle le jeu**.
- Réglages de qualité des shaders et du son **visibles seulement dans les Backrooms** (§3).

**Notes**

- Cohérent avec le périmètre « Backrooms seulement » des deux moteurs : l'Overworld reste vanilla, à l'image comme
  au son.
- Paliers de qualité (Low, Medium, High, Ultra expérimental), presets et menu du menu pause : `QUALITE_ET_PRESETS.md`.
  Ils respectent la **règle d'équité** : aucun réglage ne donne d'avantage de visibilité (§11 de ce document-là).

---

## 13. Récapitulatif

| Catégorie              | Éléments                                                                                      |
| ---------------------- | --------------------------------------------------------------------------------------------- |
| Vision générale        | Prison par défaut, punition des joueurs pénibles, tout configurable, pas « Minecraft » mais esthétique qui le rappelle |
| Entrée et équipement   | Aucun équipement, portails par commande admin, colis « =) » au retour (lit, porte, sapin)      |
| Règles                 | Pas de commandes, ni coordonnées ni F3, chat réservé aux Backrooms avec effet, admins coincés en survie, config riche |
| Items invisibles       | Aucun item des Backrooms dans le menu créatif, pas de `/give` ni d'autre moyen de les obtenir ; tout disponible en mode développement (créatif et spectateur compris) |
| Perception (V)         | Zones de noclip, traces, présences, items, pathfinding (level keys), bouton pour traverser, contrepartie |
| Santé mentale          | Hallucinations par joueur, ne tuent pas, malus, attirent des monstres, intensité par niveau     |
| Niveaux                | Niveau 0 sans entités, tutoriel, ventilations et plafonds qui bougent, niveaux reliés          |
| Gros monstres          | Trois sens, lurk/chasse/course/fouille/embuscade, intuables, moyen de fuite, cris et échos     |
| Faune                  | Biologie par niveau, petites créatures tuables, boids, chaîne alimentaire                      |
| Survie                 | Items, cachettes (casier, crevasse, bureau, lit), nouvel inventaire, progression                |
| Mort                   | Lit dans la dimension ou près de la mort, pas de respawn dans l'Overworld ; rayon fixe autour de la mort (risque, équipement proche) ou lit et cachette (sûr, équipement loin) |
| Connaissance           | Tips, journal refait (bestiaire, archives), PC avec OS ; style XP très Backrooms, navigateur, énigmes, .exe, petits jeux, accès aux niveaux, pièges, contenu selon l'emplacement, documentation de ce qu'on a vu |
| Smilers | Haine de la lumière pointée, attaque dans le noir, orbite autour d'une lumière indirecte, rage par répétition, brisent les lumières, corps invisible sauf le sourire |
| Micro                  | Bruit du micro entendu par les monstres, écho, compatible chat vocal, solo sinon ; décision : activé, tout en local, seul un vrai ou faux au serveur, sensibilité réglable ; chat vocal : vol de voix, écho entre joueurs |
| Fabrication            | Inventaire (objets simples, réparation), établis rares (outils), recettes verrouillées par outil ; pas de menu : vue de haut sur la table, objets posés en 3D, sans pause |
| Blocs et machines      | Blocs surtout incassables, matériaux plutôt qu'objets ; machines à café et snacks (vitre brisable ou pièces) |
| Items physiques        | Items 3D au sol, clic droit pour ramasser, physique, réactions                                  |
| Blessures et traces    | Saignement grave, traces suivies par les monstres, bandages ; même système pour les traces de monstres et les liquides visqueux |
| Sortie                 | Pas de sortie prévue hors config ; à terme, niveaux avec sortie ; sortie par admin : noclip, générique, retour au spawn |
| Santé mentale (détail) | Baisse naturelle par niveau, rares remontées, lieux sûrs (Manila Room) ; obscurité, monstres, sons, items, actions, attaques, manque de sommeil ; items et sommeil sûr |
| Niveau 0               | Se perdre sur des centaines de blocs, Manila Room en 0, 0, portail qui apparaît de temps en temps ; pièces uniques sur plusieurs étages |
| Son et musique         | Musique liminale très faible, haut-parleurs spatialisés, effets propres au niveau, écho directionnel |
| Monstres (tous)        | Lore du niveau 1 à suivre, mécaniques uniques, rares, rarement ensemble, écosystème organique, vraies IA |
| Cachettes              | Vu ou entendu = attrapé ; pistage au sang, à l'odeur, par apprentissage ; probabilité par cachette et par monstre |
| Sac à dos              | Inventaire qu'on fouille, améliorations, pas de poids, gros items, poches latérales pour les outils |
| Corps                  | Pas de barre de faim, vie subjective, endurance non limitante, pas de soif, aliments à effets (Almond Water, Moth Jelly), sommeil ; décision : pas de faim, la nourriture donne des bonus et soigne |

---

## 14. Points à trancher

1. **Comment sort-on des Backrooms en survie ?** Avec le cadre de la prison, plusieurs réponses sont possibles et
   configurables : libération par un modérateur, durée de peine, évasion en trouvant une sortie, ou une combinaison.
   La réponse structure la boucle de jeu, la mort et le rôle de la perception.
   **Tranché** : pas de sortie prévue pour l'instant, sauf par la config ; sortie par un admin (noclip, générique,
   retour au spawn) ; niveaux avec sortie à terme (§2.1). L'origine du moyen de sortir reste à définir.
2. **Chat** : les joueurs des Backrooms voient-ils le chat de l'Overworld, et inversement ?
3. **Mort sans lit** dans la dimension : où réapparaît-on ? **Tranché** : dans un rayon fixe autour du point de
   mort, ou dans un lit ou une cachette (§10).
4. **Contrepartie de la perception** : vision brouillée, monstres qui sentent, ou les deux selon le niveau ?
5. **Multijoueur** : les joueurs des Backrooms se retrouvent-ils dans le même niveau, ou chacun dans son instance ?
6. **Équité du micro** : un joueur sans micro, ou micro coupé, est-il avantagé ? Options : bruit de respiration
   simulé pour tous, avantage accepté, ou option imposée par le serveur. **Tranché** : avantage accepté (§7.2).
7. **Nom des Smilers** : **tranché**, le nom « Smiler » est conservé (vocabulaire des Backrooms, §7.1 et §15.9).
8. **Pièces** : où les trouve-t-on, et servent-elles à autre chose que les machines ? *(Question sur les pièces de
   monnaie des machines, §9.3 : toujours ouverte. Les pièces au sens de « salles uniques » sont décidées, §6.2.)*
   Pistes de Pierre : une **monnaie**, un **item qui sert aussi à d'autres fabrications** (et qui se révèle être des
   pièces), ou un **objet unique** qui permet d'utiliser la machine sans la casser.
9. **Référence de la « vidéo de doctorat »** sur les traces d'encre : laquelle, et quelle mécanique retenir ?
10. **Usure des objets** : quels objets s'usent et se réparent ? **En partie tranché** : selon l'objet et sa qualité,
    avec un système propre (§9.1) ; la liste des objets reste à faire.
11. **Fabrication dans l'inventaire** : même principe sans menu (objets posés au sol ou dans les mains), ou un écran
    simple réservé aux petites fabrications ? *(Le sac à dos qu'on fouille, §9.7, est une piste.)*
12. **Premier monstre du niveau 1** : lequel, d'après le lore du niveau 1 (§7.3) ?
13. **Origine du moyen de sortir** des Backrooms (§2.1).
14. **Signification exacte du « vrai ou faux » du micro** : « micro activé » ou « le joueur parle en ce moment »
    (§7.2, interprétation retenue en attendant : « parle en ce moment »). **Tranché** : détection vocale, « le joueur
    parle en ce moment ».

---

## 15. Pour aller plus loin (propositions à discuter)

### 15.1 La prison comme vrai système

- **Dossier de détention** : le modérateur indique un motif et, s'il le veut, une durée. Le joueur le retrouve sur
  le PC, comme un document officiel inquiétant.
- **Évasion possible** : trouver une sortie rapporte un succès (« Évadé ») et le colis « =) » qui prend tout son
  sens. *(À terme seulement : aucune sortie n'est prévue pour l'instant, §2.1.)* *(Une annonce dans l'Overworld
  était proposée ici : elle est **retirée** car elle contredit la règle absolue du §1.1. Elle ne reviendrait que par décision explicite, comme exception désactivable.)*
- **Panneau modérateur hors des Backrooms** : liste des détenus, niveau et état mental, libération, observation en
  spectateur invisible. Puisqu'aucune commande ne marche à l'intérieur, c'est l'outil qui manque aux modérateurs.

### 15.2 Hallucinations et perception qui se répondent

- **Les hallucinations n'ont pas de trace en perception** : en maintenant V, un faux monstre ne laisse rien. C'est
  le moyen de distinguer le vrai du faux, au prix de la contrepartie de la perception.
- **La perception coûte un peu de santé mentale** : plus on s'en sert, plus on hallucine. Une boucle de tension simple.
- **Paranoïa en multijoueur** : chacun a ses hallucinations, invisibles pour les autres. « Tu l'as vu aussi ? »
  devient une vraie question.
- **Les hallucinations attirent les monstres par le joueur** : ce n'est pas l'hallucination que le monstre entend,
  c'est le joueur qui sursaute, court ou crie. Cohérent avec l'ouïe côté serveur.

### 15.3 Le bruit du joueur comme ressource

- Un **niveau de bruit** du joueur (marche, course, items manipulés, portes) que les monstres entendent avec
  l'acoustique réelle.
- Dans une cachette : **retenir sa respiration** quelques secondes, avec un battement de cœur qui monte.
- Plus tard, en option : les monstres qui **entendent le chat vocal** (Simple Voice Chat). Très fort pour l'horreur,
  à réserver à une phase ultérieure. *(Désormais dans la vision, §7.2 : micro en local, vrai ou faux au serveur ;
  vol de voix avec le chat vocal.)*

### 15.4 Chat qui fait partie du monde

- L'« effet particulier » du chat pourrait être une **dégradation avec la distance** : un message lointain arrive
  brouillé, et un item de type **radio** porte la voix plus loin, au risque d'être entendu par les monstres.

### 15.5 Écosystème utilisable

- Se servir de la chaîne alimentaire : une **carcasse** de petite créature attire un prédateur ailleurs, une
  **odeur** couvre la sienne.
- Les petites créatures qui **fuient d'un coup** annoncent un gros monstre : un indice sans interface.

### 15.6 Mort qui laisse une trace

- À la mort, les items des Backrooms restent sur place, **visibles en perception** : revenir les chercher près du
  lieu de sa mort, c'est exactement le risque de recroiser la menace.

### 15.7 Connaissance partagée et découverte

- Le **bestiaire se remplit par l'expérience** (voir, entendre, fuir un monstre), et les **moyens de faire fuir**
  ne sont révélés qu'en les découvrant ou en les lisant sur un PC.
- **Notes laissées par d'autres joueurs**, retrouvables par les suivants.

### 15.8 Mode streamer

- Un préréglage qui active le **plafond de clignotement**, coupe les flashs les plus forts et masque les infos
  personnelles à l'écran, sans enlever la peur.
- Il se compose des presets **Streamer** (son) et **Confort** (image) de `QUALITE_ET_PRESETS.md` §7 et §8 ; reste à
  ajouter le masquage des infos personnelles à l'écran.

### 15.9 Lore original

- Écrire **son propre lore**. Une partie du lore communautaire des Backrooms est publiée sous des licences qui
  imposent des conditions de réutilisation : s'en inspirer, comme pour Veil et Sound Physics, mais ne pas en reprendre
  les textes.
- **Noms communautaires autorisés** : les noms propres aux Backrooms (entités comme les Smilers, niveaux, objets comme
  l'Almond Water) font partie du vocabulaire du thème et peuvent être utilisés tels quels. Seuls les textes,
  descriptions et récits du lore communautaire ne sont pas repris : nos descriptions, notes et entrées de journal sont
  écrites par nous.

### 15.10 Pistes autour des nouvelles mécaniques

- **La lumière comme ressource tendue** : la lampe torche est la seule arme contre les Smilers, mais ses **piles
  s'usent** (§9.1, réparation et fabrication). L'éteindre pour économiser, c'est s'exposer dans le noir.
- **Les Smilers et les pannes** : quand un Smiler brise les néons, la zone devient **leur territoire**. Les pannes
  du calendrier des néons (`MOTEUR_AUDIO.md` §6.8) deviennent des moments où ils s'approchent.
- **Le sourire comme seul repère** : dans le noir total, ne voir qu'un sourire qui se rapproche, puis un deuxième.
  Les **types de sourires** peuvent annoncer le comportement (calme, agacé, enragé) à qui sait les lire (bestiaire,
  §15.7).
- **Le sang qui trahit** : un joueur blessé laisse une piste ; un coéquipier peut **la couvrir** ou **la suivre pour le
  retrouver**. Un bandage devient une décision d'équipe.
- **Les objets comme appâts** : lancer une canette loin pour attirer un monstre ; briser une vitre volontairement pour
  faire diversion (§9.3, §9.4).
- **Le silence comme compétence** : avec le micro, chuchoter devient une vraie mécanique ; retenir sa respiration dans
  une cachette (§15.3) prend tout son sens.

### 15.11 Pistes autour de la santé mentale, du corps et du son

- **Remonter la santé mentale autrement** : écouter une musique familière sur un haut-parleur (§6.3), retrouver un
  autre joueur, allumer une lumière dans le noir, lire une note rassurante, finir une énigme sur un PC.
- **Les haut-parleurs comme piège** : une musique qui s'arrête net, ou une voix qui se mêle à la musique, annonce
  quelque chose ; un haut-parleur qu'on coupe fait aussi du bruit.
- **Le sommeil risqué** : dormir remonte la santé mentale, mais un monstre peut entendre ou flairer un joueur
  endormi ; les cauchemars sont des hallucinations propres au sommeil.
- **La Manila Room comme repère** : la seule pièce calme du niveau 0, où l'on retrouve ses notes et où l'on peut
  dormir ; la quitter devient un choix.
- **Le vol de voix sans chat vocal** : un monstre qui rejoue des **sons** du joueur (pas, porte, toux) plutôt que sa
  voix, pour garder une version de la mécanique pour tous.

---

## 16. Notes techniques transversales

- **Le serveur fait autorité** sur tout ce qui compte (perception, monstres, inventaire, mort, règles) : c'est un mod
  multijoueur, et une prison doit résister aux clients modifiés.
- **Liens avec les moteurs** : perception et hallucinations passent par le moteur de shaders ; cris, échos, ouïe des
  monstres et bruits du niveau 0 par le moteur audio.
- **Périmètre** : tout ce document ne concerne que les Backrooms ; l'Overworld reste vanilla (règle absolue du §1.1).
- **À vérifier à chaque fonctionnalité** : un client et un serveur avec le mod, dans l'Overworld, se comportent
  comme vanilla. Les mixins côté client (F3, chat, inventaire) ne s'activent **que** quand le joueur est dans une
  dimension Backrooms.
- **Ordre de travail** : une tranche verticale d'abord (niveau 0 avec santé mentale, un niveau avec un gros monstre
  et un moyen de le faire fuir, un casier, la mise de côté de l'inventaire et le colis), puis le reste.

---

## Historique des révisions

- **v1** : condensé de la vision de jeu, rangé par catégorie, avec notes techniques et propositions.
- **v2** : règle absolue « le jeu de base reste parfaitement normal » (§1.1) ; annonce d'évasion dans l'Overworld
  retirée (§15.1).
- **v3** : items des Backrooms invisibles hors mode développement (§3.1).
- **v4** : passe de cohérence entre les quatre documents : `QUALITE_ET_PRESETS.md` en document compagnon ; cris du
  niveau 0 = hallucinations ou sons venus d'un portail, tests avec monstres hors du niveau 0 (§6) ; tremblement actif
  par défaut et plafonné (§7) ; renvoi aux paliers et à la règle d'équité (§12) ; mode streamer relié aux presets
  (§15.8) ; mention « document interne ».
- **v5** (construite sur la v4) : nouvelles mécaniques de la vision : Smilers et lumière (§7.1), micro et ouïe des
  monstres (§7.2), fabrication (§9.1), blocs et matériaux (§9.2), machines à café et à snacks (§9.3), items physiques
  (§9.4), blessures, saignement et traces (§9.5) ; récapitulatif (§13), points à trancher 6 à 10 (§14), pistes (§15.10).
- **v6** : décision sur les noms : les noms propres au vocabulaire des Backrooms (entités, niveaux, objets) sont
  autorisés ; « nom de travail » retiré pour les Smilers (§7.1, §13), point 7 tranché (§14), précision au §15.9. Les
  textes du lore communautaire restent non repris.
- **v6.1** : mise en forme : le contenu du §15.9 replacé sous son titre, avant le §15.10 (aucun texte modifié).
- **v7** : fabrication à l'établi sans menu, en vue de haut directement sur la table (§9.1, vision et notes) ;
  récapitulatif (§13) ; point à trancher 11 (§14).
- **v8** : intégration des précisions de Pierre : sortie des Backrooms et générique (§2.1, exception au §1.1) ;
  santé mentale en détail (§5.1) ; niveau 0 et Manila Room (§6.1), pièces uniques (§6.2), son et musique par niveau
  (§6.3) ; micro activé en local avec un vrai ou faux au serveur, vol de voix avec le chat vocal (§7.2) ; tous les
  monstres : rareté, IA, écosystème (§7.3) ; usure selon l'objet et sa qualité (§9.1) ; cachettes (§9.6), sac à dos
  (§9.7), corps : vie, endurance, faim, sommeil (§9.8) ; deux choix de réapparition (§10) ; PC détaillé (§11) ;
  récapitulatif (§13) ; points 1, 3, 6 tranchés, 10 en partie, 12 à 14 ajoutés (§14) ; pistes (§15.11). Les
  formulations remplacées par une décision sont conservées et annotées.
- **v9** : décisions de Pierre : « vrai ou faux » du micro = détection vocale, tooltip à l'arrivée dans le niveau 1
  et mention dans les réglages (§7.2, §14 point 14 tranché) ; pas de faim, la nourriture donne des bonus et soigne
  (§9.8, §13) ; trois pistes pour les pièces des machines (§9.3, §14 point 8). Formulations remplacées conservées et
  annotées.
