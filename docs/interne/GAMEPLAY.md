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
chat, réglages de qualité) **n'existent que dans les Backrooms**. Même règle que le périmètre des deux moteurs
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

---

## 6. Les niveaux

**Niveau 0**

- **Seul niveau sans entités.**
- C'est le **tutoriel** : il introduit d'abord la **santé mentale** et les **hallucinations**.
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
  (`MOTEUR_AUDIO.md` §1.1, point 6), ou des sons venus d'un autre niveau à travers un portail.
- Les tests et bancs qui demandent de vrais monstres (cris, poursuites) se font dans un **niveau habité** ou dans un
  **niveau de test réservé au développement**, jamais en ajoutant des entités au niveau 0.

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
- **Sans Simple Voice Chat** : la détection du micro ne fonctionne **qu'en solo**.

**Notes**

- **Avec le chat vocal** : le serveur reçoit déjà le flux vocal ; on en tire un **niveau sonore** (pas le contenu),
  converti en **bruit** à la position du joueur, entendu par les monstres avec l'acoustique réelle (§7, notes).
  L'identifiant technique du mod de chat vocal est toléré dans le code de compatibilité, sans aucun commentaire autour
  (`PROMPT_AUDIT.md`, passe 1).
- **En solo, sans chat vocal** : capture du micro **par le client**, réduite à un **niveau sonore** calculé
  localement ; **rien n'est enregistré, stocké ni envoyé**.
- **Consentement** : le micro n'est jamais ouvert sans **accord explicite** du joueur, via une option dédiée,
  **désactivée par défaut**, avec un **indicateur visible** quand il écoute. C'est une exigence de confiance, et souvent
  une exigence légale.
- **Écho** : la voix reçoit l'acoustique du lieu (réverb du moteur audio). Avec le chat vocal, cela demande d'agir sur
  la lecture de ses voix côté client : plus complexe, à prévoir après l'écho du solo.
- **Équité** : un joueur sans micro, ou qui l'a coupé, ne doit pas être avantagé ; à trancher (§14).
- **Périmètre** : le micro n'est écouté **que dans les Backrooms** (§1.1).

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

**Notes**

- Recettes **propres aux Backrooms**, invisibles du livre de recettes vanilla (§3.1).
- Le verrouillage par outil crée une **progression naturelle** : trouver un outil ouvre des recettes, sans arbre de
  compétences.
- La **réparation** suppose une **usure** des objets (lampe, outils, piles) : à définir objet par objet.
- Fabriquer peut **faire du bruit** (§15.3), ce qui donne un vrai choix : fabriquer maintenant ou à l'abri.

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

---

## 10. Mort et respawn

**Vision**

- Un **autre système de mort**, avec deux choix :
  - **réapparaître dans son lit, dans la dimension** ;
  - ou **réapparaître près du lieu de sa mort**, avec le risque de **recroiser la menace**.
- **Par défaut, impossible de réapparaître dans l'Overworld** à la mort.

**Notes**

- Comportement sans lit dans la dimension à définir.
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

**Notes**

- Tips : déclenchement contextuel, une seule fois par joueur, désactivables.
- Journal refait maison : aucune question de licence, et une esthétique entièrement maîtrisée.
- PC : contenu (fichiers, mails, accès) **en données**, état **côté serveur** pour qu'un PC soit le même pour tous.

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
| Mort                   | Lit dans la dimension ou près de la mort, pas de respawn dans l'Overworld                       |
| Connaissance           | Tips, journal refait (bestiaire, archives), PC avec OS                                         |
| Smilers | Haine de la lumière pointée, attaque dans le noir, orbite autour d'une lumière indirecte, rage par répétition, brisent les lumières, corps invisible sauf le sourire |
| Micro                  | Bruit du micro entendu par les monstres, écho, compatible chat vocal, solo sinon               |
| Fabrication            | Inventaire (objets simples, réparation), établis rares (outils), recettes verrouillées par outil |
| Blocs et machines      | Blocs surtout incassables, matériaux plutôt qu'objets ; machines à café et snacks (vitre brisable ou pièces) |
| Items physiques        | Items 3D au sol, clic droit pour ramasser, physique, réactions                                  |
| Blessures et traces    | Saignement grave, traces suivies par les monstres, bandages ; même système pour les traces de monstres et les liquides visqueux |

---

## 14. Points à trancher

1. **Comment sort-on des Backrooms en survie ?** Avec le cadre de la prison, plusieurs réponses sont possibles et
   configurables : libération par un modérateur, durée de peine, évasion en trouvant une sortie, ou une combinaison.
   La réponse structure la boucle de jeu, la mort et le rôle de la perception.
2. **Chat** : les joueurs des Backrooms voient-ils le chat de l'Overworld, et inversement ?
3. **Mort sans lit** dans la dimension : où réapparaît-on ?
4. **Contrepartie de la perception** : vision brouillée, monstres qui sentent, ou les deux selon le niveau ?
5. **Multijoueur** : les joueurs des Backrooms se retrouvent-ils dans le même niveau, ou chacun dans son instance ?
6. **Équité du micro** : un joueur sans micro, ou micro coupé, est-il avantagé ? Options : bruit de respiration
   simulé pour tous, avantage accepté, ou option imposée par le serveur.
7. **Nom des Smilers** : **tranché**, le nom « Smiler » est conservé (vocabulaire des Backrooms, §7.1 et §15.9).
8. **Pièces** : où les trouve-t-on, et servent-elles à autre chose que les machines ?
9. **Référence de la « vidéo de doctorat »** sur les traces d'encre : laquelle, et quelle mécanique retenir ?
10. **Usure des objets** : quels objets s'usent et se réparent ?

---

## 15. Pour aller plus loin (propositions à discuter)

### 15.1 La prison comme vrai système

- **Dossier de détention** : le modérateur indique un motif et, s'il le veut, une durée. Le joueur le retrouve sur
  le PC, comme un document officiel inquiétant.
- **Évasion possible** : trouver une sortie rapporte un succès (« Évadé ») et le colis « =) » qui prend tout son
  sens. *(Une annonce dans l'Overworld était proposée ici : elle est **retirée** car elle contredit la règle absolue
  du §1.1. Elle ne reviendrait que par décision explicite, comme exception désactivable.)*
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
  à réserver à une phase ultérieure.

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
