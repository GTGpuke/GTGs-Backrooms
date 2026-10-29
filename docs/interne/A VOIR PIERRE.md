# BACKROOMS: INTO THE VOID — Game Design Document
## Mod Minecraft Forge 1.26.1 — Survival Horror

---

# 1. VISION GLOBALE

Le joueur "no-clip" accidentellement hors de la réalité et atterrit dans le Level 0. Son objectif : survivre, collecter des ressources, comprendre où il est, et descendre (ou remonter) à travers 14 niveaux (0-13) pour trouver une sortie — ou sombrer dans la folie. Chaque niveau est une dimension Forge custom avec sa propre génération procédurale, ses entités, et ses mécaniques survival uniques.

**Pilliers de design :**
- **Isolement** — Le joueur est seul. Pas de villageois amicaux, pas de lit pour skip la nuit.
- **Ressources rares** — Tout est limité. L'eau d'Almond est la seule nourriture fiable.
- **Tension constante** — Même les niveaux "safe" ont une ambiance oppressante.
- **Progression non-linéaire** — Le HUB et les Level Keys permettent des chemins variés.
- **Punition du triche** — L'entrée dans les Backrooms EST le châtiment pour les tricheurs.

**Accès aux Backrooms :**
Le joueur n'entre JAMAIS dans les Backrooms volontairement via un portail classique. Il y a deux voies d'entrée :

1. **Anti-Cheat Easter Egg (principal)** — Si un joueur tente d'utiliser des commandes interdites sans permission OP (`/gamemode creative`, `/give`, `/tp` vers coords protégées, `/enchant` abusif, etc.), au lieu du message "You don't have permission", l'écran glitch, le sol disparaît sous ses pieds et il tombe dans le Level 0. Son inventaire entier est sauvegardé côté serveur (NBT data liée à l'UUID du joueur) et remplacé par du vide. Il arrive dans les Backrooms les mains vides, sans armure, sans rien. S'il parvient à atteindre une sortie (True Ending), il est renvoyé dans l'Overworld à ses coordonnées d'origine et retrouve TOUT son inventaire intact. Exemples de triggers :
   - `/gamemode creative` ou `/gamemode spectator` sans OP
   - `/give` sans permission
   - Tentative de `/tp` hors limites
   - Utilisation de clients modifiés détectés (optionnel, configurable)
   - Commande custom `/backrooms enter <player>` (admin seulement) pour forcer l'entrée

2. **Commande Admin** — `/backrooms send <player>` envoie un joueur dans les Backrooms (même mécanisme de sauvegarde d'inventaire). `/backrooms retrieve <player>` le sort de force. `/backrooms inventory <player>` permet de vérifier l'inventaire sauvegardé.

**Récupération d'inventaire :**
- L'inventaire est stocké dans un fichier NBT par joueur (`backrooms/saved_inventories/<uuid>.dat`).
- En cas de crash serveur pendant une session Backrooms, l'inventaire est safe.
- Si le joueur meurt dans les Backrooms, il respawn au Level 0 (pas dans l'Overworld). Son inventaire Backrooms droppé est perdu mais l'inventaire Overworld original reste sauvegardé.
- Seul le True Ending ou la commande admin restaure l'inventaire.

---

# 2. MÉCANIQUES SURVIVAL CORE

## 2.1 Santé Mentale (Sanity)
Barre custom au-dessus de la faim. Diminue avec : l'obscurité prolongée, la proximité d'entités, le bourdonnement des néons, l'isolation. Augmente avec : l'Almond Water, les zones éclairées, les notes/journaux trouvés (lore = réconfort). En dessous de 30%, effets visuels (distorsion shader, sons parasites). En dessous de 10%, hallucinations d'entités qui n'existent pas et risque de no-clip involontaire vers un niveau aléatoire.

## 2.2 Système de Ressources
- **Almond Water** — Nourriture + santé mentale. Se trouve dans des distributeurs cassés, rarement dans des caisses.
- **Batteries** — Pour la lampe torche. Durée limitée (5 min temps réel). Ressource la plus critique.
- **Bandages / Scotch** — Soins basiques. Le scotch répare aussi certains objets.
- **Lucky O' Milk** — Rare. Restaure 100% santé mentale. Dangereux à consommer (10% chance d'empoisonnement mortel).
- **Firesalt** — Cristaux trouvés dès le Level 3. Repoussent temporairement certaines entités.
- **Lampe torche** — Item de départ. Sans batterie = inutile. L'allumer attire certaines entités.
- **Journal du Wanderer (Patchouli)** — Livre Patchouli custom. Se trouve dès le Level 0 sur un cadavre de wanderer. Catégories : "Niveaux" (descriptions + indices de transition), "Entités" (comportements + contre-mesures, débloquées au premier contact), "Objets" (crafts et usages), "Notes de terrain" (lore narratif, fragments d'histoire). Les pages se débloquent au fur et à mesure de la progression via des advancements Forge. Design visuel du livre : pages jaunies, écriture manuscrite, croquis d'entités, taches de sang/almond water. Le livre sert de tutorial, de guide et de motivation narrative.

## 2.3 Level Keys & Système de Portes
Les transitions entre niveaux fonctionnent via un système de **Level Keys** (Clés de Niveau) :
- Chaque niveau contient quelque part une **Level Key** correspondant au niveau suivant (ou à un niveau alternatif).
- Les Level Keys sont des items physiques avec un numéro de niveau gravé dessus et un glow effect de couleur unique par niveau.
- Pour passer au niveau suivant, le joueur doit trouver la Level Key ET la porte correspondante (porte avec le même glow/symbole que la clé).
- Certains niveaux ont des **Level Keys cachées** menant à des niveaux non-séquentiels (raccourcis ou niveaux secrets).
- Le HUB (voir section dédiée) contient TOUTES les portes mais nécessite les clés correspondantes.

## 2.4 Le HUB & Le Key Master

Le **HUB** est un espace intermédiaire accessible depuis n'importe quel niveau via des portes spéciales marquées d'un symbole ∞ (rares, 1-2 par niveau).

**Description :** Salle circulaire massive, sol en marbre fissuré, plafond voûté très haut. Des portes numérotées (0-13) bordent les murs en cercle, chacune avec un cadre lumineux de couleur différente. Les portes sans la clé correspondante sont verrouillées et émettent un bourdonnement sourd. Au centre : un piédestal et le **Key Master**.

**Le Key Master :**
- Entité unique, non-hostile. Humanoïde grand et mince, vêtu d'un long manteau sombre, visage partiellement masqué. Des clés pendent de sa ceinture.
- **Fonction principale :** PNJ marchand/guide. Il parle au joueur via le chat (texte stylisé, cryptique mais utile).
- **Services :**
  - **Indice de localisation de clé** — Contre de l'Almond Water, il indique dans quelle zone du niveau actuel se trouve la prochaine Level Key.
  - **Duplication de clé** — Contre une ressource rare (Firesalt x10 + Almond Water x5), il peut dupliquer une Level Key déjà possédée (utile en multijoueur).
  - **Avertissement** — Il donne un warning cryptique sur le prochain niveau ("Les yeux dans le noir ne sont pas toujours ce qu'ils semblent" pour prévenir des Smilers).
  - **Lore** — Fragments de dialogue qui révèlent l'histoire des Backrooms et pourquoi le joueur est là.
- **Ambiguïté :** Le Key Master est-il un allié ou fait-il partie du piège ? Son aide a toujours un coût, et certains de ses "indices" sont volontairement vagues. Il semble connaître le joueur mieux qu'il ne devrait.

**Règles du HUB :**
- Aucune entité hostile ne peut entrer dans le HUB (safe zone absolue).
- Le joueur peut y stocker des items dans un coffre personnel (1 coffre, pas extensible).
- Le HUB ne restaure PAS la santé mentale (les néons bourdonnent ici aussi).
- Le joueur peut retourner au dernier niveau visité via la porte par laquelle il est entré.
- Les portes déjà déverrouillées (clé utilisée) restent ouvertes en permanence (progression persistante).

## 2.5 Système de No-Clip (Transitions visuelles)
Les transitions se déclenchent quand le joueur utilise une Level Key sur la bonne porte. Visuellement : distorsion shader progressive → écran de glitch VHS → fondu vers le nouveau niveau. Si Immersive Portals est installé : portails seamless see-through à la place du fondu. Le no-clip involontaire (basse santé mentale < 10%) ignore les portes et envoie le joueur dans un niveau aléatoire SANS Level Key (il devra trouver une porte HUB pour se réorienter).

---

# 3. LES NIVEAUX

## LEVEL 0 — "The Lobby"
**Ambiance :** Couloirs jaunes infinis, moquette humide, néons bourdonnants, plafond bas. L'archétype des Backrooms.
**Génération :** Grille procédurale de salles rectangulaires connectées par des couloirs. Murs en papier peint jaune moisi, sol en moquette mouillée. Plafond à 3 blocs. Néons fluorescents partout (certains grésillent/clignotent).
**Gameplay survival :**
- Pas d'entités hostiles visibles. Mais des sons lointains (pas, murmures) maintiennent la tension.
- Almond Water rare dans des coins de pièces.
- Le joueur apprend les bases : lampe, santé mentale, journal.
- Le bourdonnement constant des néons réduit lentement la santé mentale.
**Entités :** Aucune confirmée. De rares Facelings (passifs, errent sans but — humanoïdes sans visage). Leur présence draine la santé mentale sans danger physique.
**Level Key 1 :** Trouvée sur un cadavre de wanderer dans un cul-de-sac. Glow jaune-vert.
**Porte :** Mur qui "transpire" un liquide sombre — la clé révèle la porte cachée dedans.
**Porte HUB :** Rare. Marquée ∞ dans un coin de pièce facile à manquer.

---

## LEVEL 1 — "Habitable Zone"
**Ambiance :** Entrepôt/parking industriel en béton. Plus spacieux que Level 0, mais plus sombre. Flaques d'eau au sol, éclairage intermittent.
**Génération :** Grandes salles ouvertes (plafond 6-8 blocs) reliées par des couloirs étroits. Colonnes en béton, grillages, portes métalliques (certaines verrouillées). Éclairage : néons espacés, zones de pénombre fréquentes.
**Gameplay survival :**
- Premier niveau avec entités hostiles.
- Des caisses contiennent des ressources (Almond Water, batteries, bandages).
- Certaines portes mènent à des "safe rooms" temporaires (éclairées, sans spawn d'entités pendant 2 min).
- Introduction du mécanisme de bruit : courir fait du bruit → attire les entités.
**Entités :**
- **Smilers** — Yeux et dents luminescents dans l'obscurité. Ne bougent PAS tant que le joueur les regarde. Si le joueur tourne le dos ou éteint sa lampe dans leur zone : attaque fatale. Contre-mesure : maintenir le contact visuel et reculer lentement.
- **Hounds** — Canidés déformés. Patrouillent en meute de 2-3. Détection par le son. Si le joueur reste immobile et silencieux, ils passent. Vulnérables au Firesalt.
**Level Key 2 :** Dans un coffre derrière une porte verrouillée (crochetable avec du scotch + fil de fer). Glow orange.
**Porte :** Escalier de service dans les zones les plus profondes. La clé déverrouille la grille qui bloque l'accès.
**Porte HUB :** Derrière un grillage dans une safe room.

---

## LEVEL 2 — "Pipe Dreams"
**Ambiance :** Tunnels de maintenance sombres, tuyaux partout, chaleur étouffante. Claustrophobe. L'éclairage est quasi absent — la lampe torche est obligatoire.
**Génération :** Réseau de tunnels étroits (2 blocs de large) avec des intersections en T/croix. Tuyaux au plafond et aux murs (certains émettent de la vapeur = dégâts). Petites salles de maintenance avec machinerie. Température : effet de chaleur (barre de soif accélérée).
**Gameplay survival :**
- L'obscurité quasi totale rend les batteries critiques.
- Les tuyaux de vapeur font des dégâts de brûlure si le joueur passe trop près.
- Des intersections forment un labyrinthe — le joueur peut marquer les murs avec du scotch.
- Premier niveau où le Firesalt peut être trouvé (rarement, dans des fissures murales).
**Entités :**
- **Smilers** — Plus fréquents ici, embusqués dans les intersections sombres.
- **Scratchers** — Invisibles. Se manifestent par le bruit de griffes sur les murs. Si le joueur reste immobile 5 sec quand il entend le bruit, le Scratcher passe. Sinon il attaque.
- **Dullers** — Apparence humaine grisâtre. Immobiles dans les coins. Si le joueur les touche accidentellement, drain massif de santé mentale.
**Level Key 3 :** Cachée dans une fissure murale protégée par de la vapeur (le joueur doit timer son passage). Glow rouge.
**Porte :** Porte coupe-feu au bout d'un long tunnel rectiligne. Son de machinerie qui guide.
**Porte HUB :** Dans une petite salle de maintenance, derrière des tuyaux.

---

## LEVEL 3 — "Electrical Station"
**Ambiance :** Complexe de maintenance industrielle, machines électriques partout, câbles au sol, chaleur intense (32-46°C). Bourdonnement électrique constant.
**Génération :** Couloirs larges en brique/béton avec machines (transformateurs, ventilateurs industriels, armoires électriques). Fils électriques au sol et plafond. Salles de machines spacieuses. Tuyaux contenant de l'eau bouillante. Le layout shift quand le joueur ne regarde pas (géométrie qui change — réarrangement des couloirs derrière le joueur).
**Gameplay survival :**
- La chaleur draine la faim plus vite et cause des dégâts progressifs sans Almond Water.
- Les câbles au sol peuvent électrocuter (dégâts + stun).
- Les ventilateurs industriels créent des zones de vent qui repoussent le joueur.
- Le shifting de layout signifie que rebrousser chemin ne ramène jamais au même endroit.
**Entités :**
- **Wretches** — Humains mutés par le désespoir. Rapides, erratiques. Fuient la lumière forte (lampe torche les ralentit). En groupe, deviennent plus agressifs. Unique à ce niveau.
- **Deathmoths** — Mites géantes attirées par la lumière. Crachent de l'acide. Dilemme : la lampe repousse les Wretches mais attire les Deathmoths.
- **Anethikas** — Figures humanoïdes sombres de 3 blocs de haut, tête pendante. Extrêmement rapides. Se calment si le joueur utilise un item spécial (cloche bouddhiste, rare).
**Level Key 4 :** Obtenue en résolvant le puzzle électrique (router le courant via les machines pour alimenter un coffre-fort). Glow bleu électrique.
**Porte :** Porte marquée "Office Sector", alimentée par le puzzle.
**Porte HUB :** Dissimulée derrière un faux mur dans une salle de machines (mur qui sonne creux).

---

## LEVEL 4 — "Abandoned Office"
**Ambiance :** Bureaux vides des années 90. Moquette grise, cloisons, bureaux avec PC éteints, plantes en plastique. Étrangement calme après le chaos de Level 3. Lumières fonctionnelles mais tamisées.
**Génération :** Open spaces avec cubicles, salles de réunion, couloirs avec portes numérotées. Machines à café (source rare d'Almond Water chaude = bonus santé mentale). Fenêtres donnant sur du noir absolu. Ascenseurs (la plupart ne fonctionnent pas).
**Gameplay survival :**
- Niveau de "repos relatif". Moins d'entités, plus de loot.
- Les PC fonctionnent parfois et affichent des fragments de lore (emails de la M.E.G., rapports).
- Les photocopieuses impriment parfois des pages seules — indices pour les niveaux suivants.
- PIÈGE : les salles de réunion fermées peuvent être des pièges à entités.
**Entités :**
- **Facelings** — Plus nombreux ici. Certains "travaillent" aux bureaux (comportement mimétique). Passifs SAUF si le joueur perturbe leur routine (interagir avec leur bureau, courir, lampe dans les yeux).
- **Stalkers** — Se déguisent en Facelings. Différence subtile : ils suivent le joueur du "regard" (rotation de la tête). Si détectés, ils deviennent hostiles et très rapides.
**Level Key 5 :** Dans le tiroir d'un bureau de manager (salle verrouillée, clé du bureau trouvable sur un Faceling "manager"). Glow blanc cassé.
**Porte :** Ascenseur spécifique qui fonctionne. La clé sert de badge d'accès au sous-sol.
**Porte HUB :** Dans une salle serveur, derrière les racks.
**Level Key secrète :** Level Key 7 (rare) dans un coffre-fort de la salle de réunion du dernier étage — raccourci qui skip L5 et L6.

---

## LEVEL 5 — "Terror Hotel" / "The Boiler Room"
**Ambiance :** Partie haute : hôtel délabré (couloirs de moquette rouge, portes numérotées, lustres cassés). Partie basse : salle des chaudières massive, chaude, bruyante.
**Génération :** Deux zones distinctes. Zone Hôtel : couloirs longs avec portes de chambres (certaines ouvertes → loot, certaines piégées). Réception vide. Zone Chaudière : espace industriel ouvert avec tuyaux, chaudières massives, vapeur.
**Gameplay survival :**
- Les chambres d'hôtel peuvent contenir du loot précieux OU des entités embusquées. Risk/reward.
- L'eau des robinets de l'hôtel n'est PAS de l'Almond Water (boire = dégâts).
- La Zone Chaudière est un labyrinthe vertical (échelles, passerelles, tuyaux).
- Son design encourage l'exploration méthodique chambre par chambre.
**Entités :**
- **Deathmoths** — Nichent dans les lustres de l'hôtel. Perturbés par le mouvement.
- **Growlers** — Créatures en fils de fer/pipe-cleaners. Chassent par écholocation. Faire du bruit = mort. Le joueur doit avancer en sneak dans la zone chaudière.
- **Bellhops** (custom) — Facelings en uniforme d'hôtel. Guident le joueur vers des pièges si on les suit. Les ignorer est la bonne stratégie.
**Level Key 6 :** Cachée dans la zone chaudière, sur le cadavre d'un wanderer accroché à une passerelle au-dessus d'un gouffre. Glow rouge foncé.
**Porte :** Trappe dans la chaudière la plus profonde.
**Porte HUB :** Dans une chambre d'hôtel dont le numéro est 888 (triple infini retourné).

---

## LEVEL 6 — "Lights Out"
**Ambiance :** NOIR TOTAL. Aucune source de lumière naturelle. Silence absolu. Le niveau le plus terrifiant.
**Génération :** Espace immense et ouvert (plafond très haut, 20+ blocs). Sol en béton fissuré. Des colonnes massives occasionnelles. Le joueur ne voit RIEN sans lampe, et la lampe n'éclaire que 4 blocs (au lieu de 8 normalement). Effet de brouillard noir.
**Gameplay survival :**
- Les batteries se vident 2x plus vite ici.
- La santé mentale chute rapidement dans le noir total.
- Le silence amplifie chaque son du joueur (pas, inventaire).
- Si le joueur n'a plus de batterie, il doit avancer à l'aveugle avec uniquement le son pour se guider.
- Rare : des "îlots" de lumière faible (champignons bioluminescents) servent de repères et de safe zones temporaires.
**Entités :**
- **Aucune entité physique confirmée.** C'est le piège : le danger EST le niveau lui-même.
- La privation sensorielle cause des hallucinations audio/visuelles (faux bruits de pas, fausses lueurs).
- À santé mentale très basse : le joueur "voit" des Smilers qui n'existent pas (ou peut-être que si ?).
**Level Key 7 :** Trouvée dans un des rares îlots de lumière (champignons bioluminescents), protégée par une hallucination test (le joueur doit ramasser la clé même si une "entité" semble la garder — c'est une illusion). Glow violet sombre.
**Porte :** Escalier descendant (son d'eau lointain comme guide). Trébucher dans le noir et tomber dans l'eau.
**Porte HUB :** Invisible dans le noir — le joueur doit écouter un bourdonnement spécifique (fréquence différente des néons normaux).

---

## LEVEL 7 — "Thalassophobia"
**Ambiance :** Un océan peu profond et infini sous un ciel vide et gris. L'eau monte aux genoux/taille. Une seule "île" — une petite pièce en béton émergeant de l'eau.
**Génération :** Flatworld aquatique. Eau à hauteur de 2 blocs. Sol invisible sous l'eau (béton, parfois absent → gouffres). Brouillard dense limitant la visibilité à 15 blocs. La petite pièce en béton contient du loot et un point de repos. D'autres structures submergées (voitures, meubles) parsèment le fond.
**Gameplay survival :**
- Le déplacement est ralenti par l'eau.
- La visibilité limitée crée une tension énorme dans un espace "ouvert".
- L'eau est salée — ne pas boire.
- Les gouffres dans le sol sont des pièges mortels (chute infinie → respawn au début du niveau).
- La pièce en béton est safe mais un seul exemplaire existe par chunk généré.
**Entités :**
- **Leviathans** (sous l'eau) — Ombres massives qui passent sous le joueur. Ne sont PAS hostiles tant que le joueur reste en surface. Si le joueur tombe dans un gouffre ou nage sous l'eau → attaque.
- **Sirens** (custom) — Sons mélodieux provenant d'une direction. Suivre le son = piège (gouffre). Bon réflexe = aller dans la direction OPPOSÉE.
**Level Key 8 :** Sous l'eau, dans un coffre submergé marqué par des bulles. Le joueur doit plonger brièvement sans se faire attraper par les Leviathans. Glow turquoise.
**Porte :** Gouffre spécifique (marqué par des bulles à la surface) — la clé empêche l'attaque des Leviathans pendant la descente.
**Porte HUB :** Sur la pièce en béton émergée, une petite porte rouillée presque invisible.

---

## LEVEL 8 — "Cave System"
**Ambiance :** Grottes naturelles immenses, stalactites, rivières souterraines, obscurité partielle. Contraste avec les niveaux artificiels précédents.
**Génération :** Système de grottes Minecraft-like mais amplifié. Cavernes massives (30+ blocs de haut), passages étroits, rivières souterraines, cristaux luminescents (Firesalt naturel). Champignons géants par endroits.
**Gameplay survival :**
- Le Firesalt est abondant ici — le joueur peut en stocker.
- Les rivières souterraines contiennent de l'Almond Water naturelle (rare source renouvelable).
- Les cristaux luminescents servent de repères naturels.
- Des éboulements peuvent bloquer des passages (destructibles avec le bon outil).
- Environnement vertical : beaucoup de chutes possibles.
**Entités :**
- **Cave Crawlers** — Arthropodes géants au plafond. Tombent sur le joueur qui passe en dessous. Regarder vers le haut régulièrement = survie.
- **Hounds** — Retour des meutes. Écho des grottes rend la localisation sonore trompeuse.
- **Skin-Stealers** — Humanoïdes portant la "peau" d'anciens wanderers. Appellent le joueur par son nom (affichent le pseudo du joueur en chat). Ne PAS répondre ni s'approcher.
**Level Key 9 :** Dans un nid de Cave Crawlers (le joueur doit observer le plafond et passer au bon moment). Glow vert émeraude.
**Porte :** Puits naturel remontant vers une trappe → cave de maison (transition vers les suburbs).
**Porte HUB :** Cachée derrière une cascade souterraine (le joueur doit traverser l'eau).

---

## LEVEL 9 — "Darkened Suburbs"
**Ambiance :** Banlieue américaine de nuit. Maisons identiques, rues vides, lampadaires faibles, ciel noir sans étoiles. Sensation de "presque normal" profondément dérangeante.
**Génération :** Rues en grille avec maisons procédurales (1-2 étages, jardins, garages). Lampadaires avec lumière faible. Voitures garées (vides). Intérieurs de maisons accessibles (meubles, cuisine, chambres). Pas de lune ni d'étoiles — juste du noir au-dessus.
**Gameplay survival :**
- Les maisons contiennent du loot domestique (nourriture basique, batteries, outils).
- Certaines maisons sont "habitées" par des Facelings (repas sur la table, TV allumée sur de la neige).
- Le joueur peut dormir dans un lit MAIS le réveil n'est pas garanti dans la même maison.
- Faux sentiment de sécurité — c'est un niveau très dangereux la nuit (et il fait toujours nuit).
**Entités :**
- **Neighborhood Watchers** (custom) — Silhouettes aux fenêtres des maisons. Observent. Si le joueur entre dans une maison où un Watcher était à la fenêtre, la maison est vide mais les portes se verrouillent.
- **Hounds** — Rôdent dans les rues. Plus agressifs ici (meutes de 4-5).
- **Smilers** — Dans les garages, les sous-sols, les placards.
- **The Mailman** (custom, rare) — Faceling en uniforme de facteur. Dépose une lettre dans la boîte aux lettres du joueur. La lettre contient un indice cryptique pour le prochain niveau. Totalement inoffensif, mais sa présence est terrifiante.
**Level Key 10 :** Dans la boîte aux lettres visitée par le Mailman. La lettre + la clé sont ensemble. Glow jaune doré.
**Porte :** Au bout d'une rue qui ne finit jamais (~500 blocs). Les maisons disparaissent et un champ apparaît.
**Porte HUB :** Dans le sous-sol d'une maison dont les fenêtres sont murées de l'intérieur.

---

## LEVEL 10 — "Field of Wheat"
**Ambiance :** Champ de blé infini sous un ciel crépusculaire permanent (ni jour ni nuit). Vent constant. Horizon vide. Première fois que le joueur voit un "ciel" depuis Level 0.
**Génération :** Terrain plat avec blé à hauteur d'yeux (1.5 blocs). Ciel orange/violet fixe. Vent en particules. De rares structures : granges délabrées, silos, épouvantails (certains sont des entités). Sentiers de terre battue serpentent entre les champs.
**Gameplay survival :**
- Le blé est comestible (faim faible, pas de santé mentale).
- La visibilité est réduite par le blé — le joueur ne voit pas ce qui approche.
- Les granges sont des points de loot majeurs (outils, Almond Water, lore).
- Le vent masque les sons d'approche des entités.
- Les épouvantails : certains sont des items (loot), certains sont des entités. Le joueur doit observer avant d'approcher.
**Entités :**
- **Scarecrows** (custom) — Immobiles tant qu'observés. Se déplacent quand hors du champ de vision. Si un épouvantail a changé de position, FUIR immédiatement.
- **Deathmoths** — Volent au-dessus du blé au crépuscule. Crachent de l'acide depuis les airs.
- **Harvesters** (custom) — Sons de machines agricoles au loin. S'approchent lentement. Si le joueur est dans leur chemin dans le blé → dégâts massifs. Rester sur les sentiers = safe.
**Level Key 11 :** Enterrée sous un épouvantail "normal" (pas une entité) dans une grange. Indice : c'est le seul épouvantail qui ne bouge JAMAIS. Glow orange brûlé.
**Porte :** Sentier de terre → route goudronnée → skyline de ville au loin.
**Porte HUB :** Dans un silo, au fond, une trappe au sol.

---

## LEVEL 11 — "The Endless City"
**Ambiance :** Ville infinie. Immeubles, rues, trottoirs, feux de circulation (bloqués sur rouge). Lumière de jour permanente mais soleil invisible. Pas un bruit de civilisation.
**Génération :** Génération procédurale urbaine : rues en grille, immeubles de 5-15 étages, magasins au rez-de-chaussée (fermés), parcs vides, parkings. Voitures garées partout. Les intérieurs des bâtiments sont accessibles (bureaux, appartements, magasins). Certains bâtiments ont des étages "impossibles" (escalier qui monte indéfiniment).
**Gameplay survival :**
- Niveau le plus riche en loot (magasins, appartements). Le joueur peut enfin se réapprovisionner.
- La ville a un cycle jour pseudo-normal mais le soleil n'existe pas (lumière ambiante).
- Les magasins contiennent des items uniques (sac à dos = plus d'inventaire, radio = détecte les entités proches par grésillement).
- Les bâtiments en hauteur offrent une vue d'ensemble mais attirent l'attention.
- Premier niveau avec des structures M.E.G. (graffitis, caches de ressources, panneaux d'avertissement).
**Entités :**
- **Facelings** — Nombreux. "Vivent" dans la ville. Certains marchent dans les rues, d'autres sont assis dans les cafés. Passifs tant que non provoqués. Un faux sentiment de compagnie.
- **Smilers** — Dans les ruelles sombres, les sous-sols, les parkings.
- **Windows** (custom) — Entités qui apparaissent AUX fenêtres des immeubles quand le joueur regarde vers le haut. Si le joueur entre dans le bâtiment correspondant, l'entité a disparu mais l'étage est dangereux (piège, obscurité soudaine).
- **The Concierge** (custom, unique) — NPC ambigu dans le hall d'un immeuble spécifique. Propose un "deal" : un objet clé contre toute l'Almond Water du joueur. L'objet est nécessaire pour Level 13.
**Level Key 12 :** Obtenue via le deal avec le Concierge (coûte toute l'Almond Water du joueur) OU trouvable dans un appartement du dernier étage d'un gratte-ciel (très dangereux, beaucoup de Windows entities). Glow blanc pur.
**Porte :** Immeuble dont les fenêtres émettent une lumière verte pulsante.
**Porte HUB :** Au sommet d'un building, accessible par un escalier de secours extérieur.

---

## LEVEL 12 — "The Matrix"
**Ambiance :** Espace abstrait. Murs, sol et plafond en motifs géométriques lumineux sur fond noir. Sensation de "glitch dans la réalité". Structures impossibles (escaliers d'Escher, couloirs qui se replient).
**Génération :** Environnement non-euclidien maximal. Salles cubiques flottant dans le vide, reliées par des passerelles de lumière. Les murs changent de couleur/pattern. La gravité peut être inversée dans certaines zones. Des "glitchs" visuels (blocs qui tremblent, textures corrompues intentionnellement). Si Immersive Portals est présent : boucles de couloirs visibles, salles qui contiennent elles-mêmes.
**Gameplay survival :**
- La santé mentale chute très vite (environnement incompréhensible).
- La navigation est un puzzle en soi — les repères visuels sont peu fiables.
- Des "données" flottent dans l'air (particules de texte) — les collecter restaure la santé mentale.
- Certaines passerelles sont des pièges (disparaissent après passage → chute dans le vide → respawn au début du niveau).
- Les zones de gravité inversée sont signalées par un changement de couleur du sol.
**Entités :**
- **Glitched Entities** — Versions corrompues de TOUTES les entités précédentes. Apparaissent et disparaissent en "glitchant". Imprévisibles, ne suivent plus leurs règles normales.
- **The Architect** (custom, boss optionnel) — Entité massive composée de géométrie fractale. Ne peut pas être tuée, seulement évitée. Réarrange le niveau autour du joueur.
**Level Key 13 :** Matérialisée en collectant toutes les "données" flottantes (particules de texte) dans une zone spécifique — elles fusionnent en clé. Glow noir avec contour blanc (inversé). C'est la seule Level Key qui se "craft" dans le monde.
**Porte :** Un "pixel mort" — point de noir absolu dans la lumière. La clé permet d'y entrer sans être détruit.
**Porte HUB :** Sur une passerelle flottante isolée, la porte ∞ est à l'envers (le joueur doit inverser sa gravité pour l'atteindre).

---

## LEVEL 13 — "The Infinite Apartments"
**Ambiance :** Immeuble d'appartements soviétiques/brutalistes infini. Couloirs identiques, portes numérotées (les numéros ne suivent aucune logique), lumière jaunâtre faible. Sentiment de boucle.
**Génération :** Couloirs longs avec portes d'appartements. Escaliers menant à des étages identiques. Les appartements intérieurs sont petits (salon, cuisine, chambre, salle de bain). Certains sont meublés normalement, d'autres sont déformés (meubles au plafond, murs en pente). L'architecture boucle — le joueur finit par revenir à son point de départ sans s'en rendre compte.
**Gameplay survival :**
- Le DERNIER niveau. La tension est à son maximum.
- Les ressources sont quasi absentes — le joueur doit survivre avec ce qu'il a.
- Le puzzle principal : trouver l'appartement "correct" parmi des milliers. L'indice vient de la lettre du Mailman (Level 9) et de l'objet du Concierge (Level 11).
- Chaque "mauvais" appartement a une chance de contenir une entité.
- L'appartement correct contient... une porte de sortie vers la réalité. OU une boucle vers Level 0 (selon les choix du joueur tout au long du jeu).
**Entités :**
- **All previous entities** — Tout peut apparaître ici, en versions plus agressives.
- **The Tenant** (custom, finale) — Entité assise dans un fauteuil dans certains appartements. Immobile. Si le joueur la regarde trop longtemps, elle tourne la tête. Si le joueur entre dans la pièce du Tenant, les portes se verrouillent et les lumières s'éteignent.
- **Doppelgänger** (custom) — Copie exacte du skin du joueur. Apparaît au bout des couloirs. S'approche lentement. Si le joueur le touche → game over instantané + lore ending.
**Fin du jeu :**
- **True Ending** — Trouver le bon appartement avec les indices. La porte s'ouvre sur la lumière blanche. Le joueur retourne dans l'Overworld Minecraft normal avec son inventaire Backrooms (items uniques devenus trophées).
- **Loop Ending** — Mauvais appartement ou pas d'indices. Le joueur tombe dans le noir et réapparaît au Level 0. Tout recommence mais le monde est subtilement différent (NG+).
- **Void Ending** — Toucher le Doppelgänger. Écran noir avec texte de lore. Le joueur respawn dans l'Overworld mais sa santé mentale est permanemment réduite (debuff permanent comme cicatrice).

---

# 4. SYSTÈME D'ENTITÉS — DESIGN TECHNIQUE

## 4.1 Comportements IA
Chaque entité utilise un système de **states** (état machine) :
- **Idle** — Patrouille ou immobile.
- **Alert** — Le joueur a été détecté (son, lumière, proximité). L'entité cherche.
- **Chase** — Poursuite active. Vitesse et agressivité dépendent de l'entité.
- **Attack** — En portée. Animation d'attaque + dégâts.
- **Retreat** — Certaines entités fuient (lumière, Firesalt).

## 4.2 Détection
Trois canaux de détection combinables par entité :
- **Visuelle** — Cône de vision. Bloqué par les murs/obstacles. Affecté par la lumière.
- **Sonore** — Rayon circulaire. Le bruit du joueur (marche, course, inventaire, casse de blocs) a un "volume" qui détermine le rayon de détection.
- **Proximité** — Certaines entités sentent le joueur dans un rayon fixe (Dullers, Smilers).

## 4.3 Table récapitulative des entités

| Entité | Niveaux | Détection | Contre-mesure | Danger |
|---|---|---|---|---|
| Faceling | 0,4,9,11 | Proximité | Ne pas interagir | Faible |
| Smiler | 1,2,9,11,13 | Vision (obscurité) | Contact visuel + recul | Létal |
| Hound | 1,8,9 | Son | Immobilité + silence | Élevé |
| Scratcher | 2 | Son mutuel | Immobilité 5 sec | Moyen |
| Duller | 2 | Proximité | Éviter contact physique | Drain mental |
| Wretch | 3 | Vision | Lumière forte | Élevé |
| Deathmoth | 3,5,10 | Lumière | Éteindre lampe | Moyen |
| Anethika | 3 | Vision | Cloche bouddhiste | Très élevé |
| Stalker | 4 | Vision | Détecter le mimétisme | Élevé |
| Growler | 5 | Son (écholocation) | Sneak absolu | Létal |
| Bellhop | 5 | Proximité | Ignorer | Piège |
| Scarecrow | 10 | Vision inverse | Observer constamment | Élevé |
| Harvester | 10 | Chemin fixe | Rester sur sentiers | Létal |
| Window | 11 | Regard du joueur | Ne pas entrer | Piège |
| Concierge | 11 | Script | Deal ou ignorer | Neutre |
| Mailman | 9 | Script | Lire la lettre | Neutre |
| Glitched | 12 | Aléatoire | Imprévisible | Variable |
| Architect | 12 | Omniscient | Fuite | Boss |
| Tenant | 13 | Regard du joueur | Ne pas entrer | Létal |
| Doppelgänger | 13 | Proximité | Fuir | Game over |
| Cave Crawler | 8 | Proximité (dessus) | Regarder en haut | Élevé |
| Skin-Stealer | 8 | Son (voix) | Ignorer les appels | Létal |
| Leviathan | 7 | Submersion | Rester en surface | Létal |
| Siren | 7 | Son (leurre) | Aller direction opposée | Piège |
| N. Watcher | 9 | Fenêtre | Ne pas entrer | Piège |

---

# 5. PROGRESSION & FLOW SURVIVAL

## 5.1 Courbe de difficulté
```
Tension
  ▲
  │          ╱╲
  │    ╱╲  ╱  ╲    ╱╲         ╱\
  │   ╱  ╲╱    ╲  ╱  ╲   ╱╲ ╱  ╲
  │  ╱          ╲╱    ╲ ╱  ╲    ╲
  │ ╱                  ╲       PEAK
  │╱
  └───────────────────────────────►
   0  1  2  3  4  5  6  7  8  9 10 11 12 13
      Intro  Ramp  Rest  Peak  Rest  Ramp  CLIMAX
```

- **L0-L1** : Introduction progressive. Le joueur apprend.
- **L2-L3** : Premier vrai pic de difficulté. Beaucoup meurent ici.
- **L4** : Respiration. Repos, loot, lore.
- **L5-L6** : Second pic. L6 est psychologiquement le plus dur.
- **L7-L8** : Dépaysement + challenge mécanique (eau, verticalité).
- **L9-L10** : Faux calme. L'horreur vient du "presque normal".
- **L11** : Dernier repos. Le joueur se prépare.
- **L12-L13** : Climax total. Tout converge.

## 5.2 Items clés pour la progression
- **Level 9 → Lettre du Mailman** : Contient un numéro d'appartement (indice pour L13).
- **Level 11 → Objet du Concierge** : Clé spéciale nécessaire pour ouvrir le bon appart à L13.
- **Sans ces deux items** : Le joueur obtient le Loop Ending (NG+).

---

# 6. AUDIO & AMBIANCE

## 6.1 Par niveau
| Niveau | Ambiance sonore principale | Sons d'entités |
|---|---|---|
| 0 | Bourdonnement néons, moquette humide | Pas lointains, murmures |
| 1 | Écho béton, gouttes d'eau, vent dans grillages | Grognements (Hounds), rire distant (Smilers) |
| 2 | Vapeur, métal qui grince, silence oppressant | Griffes sur murs (Scratchers) |
| 3 | Bourdonnement électrique intense, ventilateurs | Cris aigus (Wretches), battements d'ailes |
| 4 | Silence de bureau, clavier fantôme, imprimante | Pas feutrés (Stalkers) |
| 5 | Tuyaux qui claquent, eau qui bout | Ultrasons (Growlers), craquements |
| 6 | SILENCE ABSOLU | Hallucinations audio uniquement |
| 7 | Clapotis d'eau, vent marin lointain | Chant mélodieux (Sirens), grondement sous-marin |
| 8 | Écho de grottes, eau qui coule, pierres qui tombent | Cliquetis (Cave Crawlers), votre pseudo murmuré |
| 9 | Grillons, vent dans les feuilles, TV en fond | Aboiements lointains, pas sur trottoir |
| 10 | Vent dans le blé, machines agricoles au loin | Bruissement rapide, bourdonnement d'ailes |
| 11 | Vent urbain, feux de circulation qui cliquent | Pas de foule fantôme, portes qui claquent |
| 12 | Fréquences digitales, glitch audio | Sons corrompus de toutes entités précédentes |
| 13 | Bourdonnement de néon faible, TV neige | Respiration lourde, votre pseudo appelé |

---

# 7. NOTES TECHNIQUES POUR CLAUDE CODE

## 7.1 Système Anti-Cheat / Point d'entrée
```
BackroomsEntryHandler {
  // Écoute les événements de commande Forge
  @SubscribeEvent onCommandEvent(CommandEvent e) {
    if (!player.hasPermission("op") && isBlockedCommand(e.command)) {
      e.cancel()
      savePlayerInventory(player) // → backrooms/saved_inventories/<uuid>.dat
      savePlayerPosition(player)  // → coords + dimension d'origine
      clearPlayerInventory(player)
      triggerNoclipAnimation(player) // shader glitch + chute
      teleportToLevel0(player)
    }
  }

  blockedCommands: [
    "gamemode creative", "gamemode spectator",
    "give", "enchant", "tp" (conditions), "op"
  ]
  // Configurable dans backrooms-config.toml
}
```

```
BackroomsExitHandler {
  onTrueEnding(player) {
    restorePlayerInventory(player) // depuis le .dat sauvegardé
    teleportToSavedPosition(player)
    deleteSavedData(player) // cleanup
    grantAdvancement("backrooms:escaped")
  }

  onAdminRetrieve(player) {
    restorePlayerInventory(player)
    teleportToSavedPosition(player)
    deleteSavedData(player)
  }

  onDeathInBackrooms(player) {
    // L'inventaire Backrooms est droppé et perdu
    // L'inventaire Overworld sauvegardé reste INTACT
    respawnAtLevel0(player)
  }
}
```

## 7.2 Commandes Admin
```
/backrooms send <player>        — Envoie un joueur (sauvegarde inventaire)
/backrooms retrieve <player>    — Sort un joueur de force (restaure inventaire)
/backrooms inventory <player>   — Affiche l'inventaire Overworld sauvegardé
/backrooms reset <player>       — Reset la progression (Level Keys, HUB)
/backrooms tp <player> <level>  — TP vers un niveau spécifique (debug)
```

## 7.3 Patchouli — Journal du Wanderer
```
Dépendance : Patchouli (Forge)
Book ID : backrooms:wanderer_journal
Structure :
  book.json
  ├── entries/
  │   ├── levels/
  │   │   ├── level_0.json  → Débloqué par défaut
  │   │   ├── level_1.json  → Advancement "backrooms:visited_level_1"
  │   │   └── ...
  │   ├── entities/
  │   │   ├── smiler.json   → Advancement "backrooms:encountered_smiler"
  │   │   ├── hound.json    → Advancement "backrooms:encountered_hound"
  │   │   └── ...
  │   ├── items/
  │   │   ├── almond_water.json
  │   │   ├── level_key.json
  │   │   ├── firesalt.json
  │   │   └── ...
  │   ├── hub/
  │   │   ├── hub_overview.json
  │   │   ├── key_master.json
  │   │   └── ...
  │   └── lore/
  │       ├── meg_reports.json
  │       ├── wanderer_notes.json
  │       └── ...

Chaque entry utilise des advancements Forge comme conditions de visibilité.
Les pages d'entités affichent le modèle 3D de l'entité (rendu entity dans Patchouli).
Les pages de niveaux incluent une "carte" schématique (image custom).
```

## 7.4 Level Key & HUB System
```
LevelKey extends Item {
  - targetLevel: int (0-13)
  - glowColor: int (couleur hex unique par niveau)
  - hasGlint() → true (glow enchanté)
  - onUseOnDoor(player, door) → vérif level match → transition

  // NBT : stocke le niveau cible
  // Stackable : non
  // Obtenable : 1 par niveau par run (sauf duplication Key Master)
}

HubDimension extends BackroomsDimension {
  - doors: Map<int, HubDoor> // 14 portes (0-13)
  - keyMaster: KeyMasterEntity

  HubDoor {
    - targetLevel: int
    - unlocked: boolean // persisté dans world data
    - glowColor: int
    - onInteract(player) → check si player a la LevelKey correspondante
  }
}

KeyMasterEntity extends NPC {
  - trades: List<KeyMasterTrade>
  - dialogueTree: DialogueManager
  - onInteract(player) → ouvre GUI de dialogue/trade

  KeyMasterTrade {
    - HINT: 2x Almond Water → message chat avec indice
    - DUPLICATE_KEY: 10x Firesalt + 5x Almond Water → copie de LevelKey
    - WARNING: gratuit, 1 fois par niveau → avertissement cryptique
  }
}
```

## 7.5 Dépendances & Jar-in-Jar

**Principe :** Le joueur télécharge UN seul .jar (le mod Backrooms). Les dépendances légères sont embarquées dedans via Jar-in-Jar (JiJ) de Forge. Les dépendances lourdes/optionnelles restent séparées.

```gradle
// build.gradle
dependencies {
    // ═══ EMBARQUÉ (Jar-in-Jar) ═══
    // Le joueur n'a rien à installer pour ceux-ci
    
    // Patchouli — Journal du Wanderer
    jarJar(group: 'vazkii.patchouli', name: 'Patchouli', version: '[1.26,)') {
        jarJar.ranged(it, '[1.26,)')
    }
    implementation fg.deobf('vazkii.patchouli:Patchouli:1.26-XXX')
    
    // Autres libs légères si besoin (ex: config lib, shader lib)
    // jarJar(group: '...', name: '...', version: '...')
    
    // ═══ OPTIONNEL (Soft Dependency) ═══
    // Le joueur installe séparément s'il veut les features bonus
    
    // Immersive Portals — portails seamless (NON embarqué)
    compileOnly 'com.qouteall:immersive-portals:XXX' // compile only = pas dans le jar
}

// Config JiJ dans le jar final
jarJar.enable()

tasks.named('jarJar') {
    // Le jar final contient Patchouli automatiquement
}
```

```toml
# mods.toml — déclaration des dépendances
[[dependencies.backrooms]]
    modId = "patchouli"
    mandatory = true       # requis, mais embarqué donc toujours présent
    versionRange = "[1.26,)"
    ordering = "NONE"
    side = "BOTH"

[[dependencies.backrooms]]
    modId = "immersive_portals"
    mandatory = false      # optionnel
    versionRange = "[1.26,)"
    ordering = "AFTER"
    side = "CLIENT"
```

```java
// Runtime detection pour Immersive Portals
public class BackroomsCompat {
    public static final boolean HAS_IMMERSIVE_PORTALS = 
        ModList.get().isLoaded("immersive_portals");
    
    public static void handleTransition(Player player, int targetLevel) {
        if (HAS_IMMERSIVE_PORTALS) {
            IPTransitionHandler.createSeamlessPortal(player, targetLevel);
        } else {
            FallbackTransitionHandler.doVHSGlitch(player, targetLevel);
        }
    }
}
```

**Résultat pour le joueur :**
- Télécharge `backrooms-1.0.0.jar` (contient Patchouli)
- Le dépose dans `/mods/` → tout fonctionne
- S'il ajoute Immersive Portals à côté → portails seamless activés automatiquement

## 7.6 Architecture des dimensions
Chaque niveau = 1 dimension Forge custom (`DimensionType` + `ChunkGenerator` custom). Les dimensions sont chargées à la demande (lazy loading) pour ne pas surcharger la mémoire.

## 7.7 Génération procédurale — Approche générale
Chaque niveau utilise le pipeline de génération adapté à sa nature :

| Niveau | Méthode | Notes |
|--------|---------|-------|
| 0 | **Maze script → chunk map** | Voir section 7.8 détaillée |
| 1-4 | Maze script (variantes) | Même pipeline, paramètres différents |
| 5 | Hybride maze + prefabs | Zone hôtel = maze, zone chaudière = prefabs |
| 6 | Open space + colonnes | Simple, focus sur le fog shader |
| 7 | Flatworld aquatique + noise | Simplex noise pour les gouffres sous-marins |
| 8 | Perlin noise 3D | Caves vanilla amplifiées |
| 9-10 | Placement de prefabs sur grille | Maisons/champs = structures .nbt sur grille |
| 11 | City grid + building prefabs | Grille de rues + immeubles de hauteurs variées |
| 12 | Salles + portails | Espaces reliés par TP/portails seamless |
| 13 | Couloirs infinis + appartements .nbt | Répétition avec variations subtiles |

## 7.8 Génération Level 0 — Pipeline détaillé

### Concept
La génération est **infinie et à la volée**. Chaque chunk de 16×16 est calculable indépendamment, sans stocker de maze global. Un script Python sert de **référence** pour les règles visuelles du labyrinthe (proportions de couloirs, fréquence de virages, tailles de pièces, etc.). Ces règles sont traduites en un algorithme Java déterministe qui, pour n'importe quel (chunkX, chunkZ) + worldSeed, produit toujours le même résultat.

### Principe fondamental : cohérence aux bordures
Le problème central de la génération à la volée est que deux chunks voisins doivent s'accorder sur leurs ouvertures partagées SANS se consulter.

**Solution : les bords appartiennent à un hash partagé.**

```
Pour chaque BORD entre deux chunks adjacents (A et B) :

  // Le hash est SYMÉTRIQUE : A→B et B→A donnent le même résultat
  edgeSeed = hash(worldSeed, min(A, B), max(A, B))
  
  // Ce seed détermine TOUT sur ce bord :
  //   - Est-il ouvert ? (y a-t-il un passage ?)
  //   - Si oui, à quelle position ? (offset dans les 16 blocs)
  //   - Quelle largeur ? (2, 3 ou 4 blocs)
  
  EdgeData {
    isOpen:   (edgeSeed % 100) < OPEN_RATIO   // règle du script Python
    offset:   (edgeSeed >> 8) % 10 + 3        // position 3-12 dans le chunk
    width:    (edgeSeed >> 16) % 3 + 2        // largeur 2-4 blocs
  }

Quand le chunk A se génère, il calcule ses 4 bords (N, E, S, W).
Quand le chunk B (voisin est de A) se génère, il calcule aussi
son bord W → c'est LE MÊME hash → résultat identique.
```

### Pipeline de génération par chunk

```
ChunkGenerator.generateChunk(chunkX, chunkZ, worldSeed) :

  ╔══════════════════════════════════════════════════════╗
  ║  ÉTAPE 1 — Calculer les 4 bords du chunk            ║
  ╚══════════════════════════════════════════════════════╝
  
  EdgeData northEdge = computeEdge(chunkX, chunkZ, chunkX, chunkZ-1)
  EdgeData eastEdge  = computeEdge(chunkX, chunkZ, chunkX+1, chunkZ)
  EdgeData southEdge = computeEdge(chunkX, chunkZ, chunkX, chunkZ+1)
  EdgeData westEdge  = computeEdge(chunkX, chunkZ, chunkX-1, chunkZ)
  
  // On sait maintenant OÙ sont les ouvertures de ce chunk
  // → C'est le "contrat" avec les chunks voisins

  ╔══════════════════════════════════════════════════════╗
  ║  ÉTAPE 2 — Déterminer le type de cellule             ║
  ╚══════════════════════════════════════════════════════╝
  
  int openCount = count(northEdge.isOpen, eastEdge.isOpen, 
                        southEdge.isOpen, westEdge.isOpen)
  
  cellType = switch(openCount) {
    0 → SEALED        // mur plein (rare, ~5%)
    1 → DEAD_END      // cul-de-sac
    2 → CORRIDOR      // couloir droit ou virage
    3 → T_JUNCTION    // carrefour en T
    4 → CROSSROAD     // carrefour ouvert
  }

  ╔══════════════════════════════════════════════════════╗
  ║  ÉTAPE 3 — Générer le layout interne du chunk        ║
  ╚══════════════════════════════════════════════════════╝
  
  Seed local : localSeed = hash(worldSeed, chunkX, chunkZ)
  
  Le layout interne relie les ouvertures entre elles avec des couloirs.
  
  RÈGLES DU SCRIPT PYTHON À RESPECTER :
  (À compléter quand le script est analysé)
  
  - Largeur de couloir : [à définir] blocs
  - Hauteur plafond : 3-4 blocs
  - Les couloirs ne sont JAMAIS parfaitement droits sur 16 blocs
    → léger décalage (1-2 blocs) pour casser la monotonie
  - Les pièces se forment naturellement aux jonctions
    (espace ouvert quand 3+ couloirs se rejoignent)
  - Ratio murs/vide : environ [à définir]% mur, [à définir]% traversable
  - Murs d'épaisseur [à définir] blocs minimum
  
  Algorithme interne (pseudo-code) :
  
  1. Placer les "points d'ancrage" aux positions des ouvertures
     sur chaque bord (offset + width de l'EdgeData)
  2. Relier les points d'ancrage entre eux via des chemins
     → Si 2 ouvertures opposées (N↔S ou E↔W) : couloir traversant
       avec décalage aléatoire (coude léger)
     → Si ouvertures adjacentes (N+E) : virage en L
     → Si 3+ ouvertures : espace central élargi (mini-pièce)
  3. Autour des chemins : remplir de murs
  4. Les zones non-utilisées entre les chemins : 
     murs pleins OU micro-pièces fermées (seed-based)

  ╔══════════════════════════════════════════════════════╗
  ║  ÉTAPE 4 — Placement des blocs                      ║
  ╚══════════════════════════════════════════════════════╝
  
  Pour chaque colonne (x, z) dans le chunk :
  
  FLOOR 0 (principal, y=60 à y=64) :
    y=60 : bedrock (empêcher de creuser en dessous)
    y=61 : sol béton (sous la moquette)
    y=62 : moquette jaune humide (damp_carpet)
    
    y=62-64 (hauteur traversable) :
      Si layout[x][z] == MUR  → wallpaper_block (papier peint jauni)
      Si layout[x][z] == VIDE → air
    
    y=65 : plafond (ceiling_tile)
    y=66 : au-dessus du plafond (void/bedrock)
  
  FLOOR 1 (optionnel, y=67 à y=71, si cellule multi-étage) :
    Même structure mais ambiance dégradée
    Relié par un escalier dans un coin du chunk

  NÉONS :
    Placés sur le plafond (y=65, face dessous)
    Tous les 4 blocs dans les couloirs, alignés au centre
    Blockstate : normal | flickering | broken
    Distribution : 70% normal, 20% flickering, 10% broken (seed-based)

  ╔══════════════════════════════════════════════════════╗
  ║  ÉTAPE 5 — Décoration contextuelle                  ║
  ╚══════════════════════════════════════════════════════╝
  
  Le cellType + localSeed déterminent la déco :
  
  CORRIDOR :
    - Flaques d'eau au sol (10%)
    - Moquette arrachée par endroits (15%)
    - Marques sur les murs (griffures, taches) (5%)
  
  DEAD_END :
    - Plus grande chance de loot (distributeur, caisse)
    - Peut contenir un Faceling immobile
    - Spot possible pour Level Key 1
  
  T_JUNCTION / CROSSROAD :
    - Éclairage plus défaillant (plus de néons broken)
    - Spawn point d'entités potentiel
    - Parfois un panneau directionnel absurde
  
  SEALED (mur plein, pas d'ouverture) :
    - Ce chunk n'est jamais visité
    - Peut servir de "mur épais" entre deux zones

  ╔══════════════════════════════════════════════════════╗
  ║  ÉTAPE 6 — Structures prédéfinies                   ║
  ╚══════════════════════════════════════════════════════╝
  
  APRÈS le layout interne, un check de structure :
  
  structureSeed = hash(worldSeed, chunkX, chunkZ, "structures")
  
  // Les structures remplacent le contenu généré
  // mais RESPECTENT les ouvertures aux bords (contrat inviolable)
  
  if (structureSeed % 2000 == 0 && distance(spawn) > 30 chunks)
    → MANILA ROOM (pièce blanche vide, 1×1 chunk)
      Connexions : s'adapte aux ouvertures existantes du chunk
      Intérieur : remplacé par le .nbt de la Manila Room
  
  if (structureSeed % 500 == 0)
    → ALMOND WATER CACHE (distributeurs cassés)
  
  if (structureSeed % 300 == 0 && cellType == DEAD_END)
    → WANDERER CAMP (campement abandonné + lore)
  
  if (structureSeed % 5000 == 0)
    → HUB ENTRANCE (porte ∞, rare)
  
  if (isLevelKeyChunk(chunkX, chunkZ, worldSeed))
    → LEVEL KEY SHRINE (unique dans tout le niveau,
       position déterminée par le worldSeed)
  
  Chaque structure .nbt a des "connection slots" sur ses bords
  qui sont alignés aux ouvertures calculées à l'étape 1.
  Si une ouverture du maze tombe là où la structure a un mur,
  la structure est adaptée (on perce l'ouverture dans le .nbt).
```

### Escaliers & multi-étages

```
Certaines cellules sont flaggées "MULTI_FLOOR" par un check seed-based.
Fréquence : ~5% des chunks traversables.

  Floor 0 (y=60-65) : layout normal du maze
  Floor 1 (y=67-72) : layout DIFFÉRENT (second seed ou rotation du floor 0)
  Escalier           : dans un coin du chunk, relie les deux floors
                       (position déterminée par localSeed)

Le second étage a :
  - Couloirs plus étroits (largeur -1 bloc)
  - Plus de néons broken/flickering
  - Moquette plus abîmée (variante de texture)
  - Spawn d'entités plus fréquent

Le joueur entend un écho différent dans les cellules multi-étage
→ signal audio que du contenu vertical existe ici
```

### Paramètres configurables (backrooms-generation.toml)
```toml
[level0]
open_ratio = 65          # % de bords ouverts entre chunks
corridor_width_min = 2   # largeur min des couloirs (blocs)  
corridor_width_max = 4   # largeur max
ceiling_height = 3       # hauteur sous plafond
light_normal_pct = 70    # % néons normaux
light_flicker_pct = 20   # % néons qui grésillent
light_broken_pct = 10    # % néons cassés
multi_floor_pct = 5      # % chunks avec 2 étages
structure_manila_freq = 2000    # 1 chance sur N
structure_hub_freq = 5000       # 1 chance sur N
structure_cache_freq = 500      # 1 chance sur N
```

## 7.7 Système de transitions
```
TransitionManager {
  - detectTransitionZone(player, level)
  - triggerTransition(player, fromLevel, toLevel)
  - applyVisualEffect(type: GLITCH | VHS | FADE | SEAMLESS)
  - handleImmersivePortals() // si le mod est présent
  - handleFallback() // TP silencieux + effet visuel
}
```

## 7.8 Sanity System
```
SanityManager {
  - currentSanity: float (0-100)
  - drainRate: float (par tick, modifié par l'environnement)
  - modifiers: List<SanityModifier> // lumière, entités, items
  - applyShaderEffects(sanityLevel) // distorsion, hallucinations
  - triggerHallucination() // faux sons, fausses entités
  - triggerInvoluntaryNoclip() // si < 10%
}
```

## 7.9 Entity AI Framework
```
BackroomsEntity extends Monster {
  - detectionType: VISUAL | SOUND | PROXIMITY
  - currentState: IDLE | ALERT | CHASE | ATTACK | RETREAT
  - counterMeasure: CounterMeasureType
  - updateState(player)
  - checkCounterMeasure(player) → boolean
  - onPlayerDetected(player)
  - onCounterMeasureApplied(player) // ex: lumière → Wretch recule
}
```

---

# 8. IMMERSIVE PORTALS — INTÉGRATION

## 8.1 Si présent (dépendance optionnelle)
- Transitions entre niveaux via portails see-through.
- Level 3 : geometry shifting visible en temps réel.
- Level 12 : boucles de couloirs, salles récursives, escaliers d'Escher fonctionnels.
- Level 13 : le Doppelgänger est visible à travers un portail comme un miroir.

## 8.2 Si absent (fallback)
- Transitions par TP silencieux + effet shader (VHS glitch).
- Level 3 : shifting fait via remplacement de chunks quand le joueur ne regarde pas.
- Level 12 : TP invisible aux limites de chunk pour simuler les boucles.
- Level 13 : le Doppelgänger apparaît normalement (mob avec le skin du joueur).

---

# 9. ROADMAP DE DÉVELOPPEMENT SUGGÉRÉE

**Phase 1 — Core** : Dimensions 0-3, sanity system, lampe + batteries, 3 entités (Faceling, Smiler, Hound), transition basique.

**Phase 2 — Mid-game** : Levels 4-8, toutes les entités de ces niveaux, système de loot complet, sound design.

**Phase 3 — End-game** : Levels 9-13, entités finales, système d'endings, items clés (lettre, clé), boss Level 12.

**Phase 4 — Polish** : Immersive Portals intégration, shaders, NG+, optimisation, balancing.