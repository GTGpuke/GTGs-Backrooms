# Prompt d'audit — GTG's Backrooms

> **Document interne** : ce prompt nomme des projets tiers et l'IA. Il ne doit **jamais** être versionné dans le
> dépôt public. À coller tel quel dans une session de l'agent de développement, sur le dépôt de développement privé.

---

```text
Tu fais l'audit complet du mod GTG's Backrooms (Fabric, Minecraft 1.21.1, Java 21) avant publication.

OBJECTIF
1. Aucune mention d'un autre projet, ni de l'IA, nulle part dans ce qui est livré ou public.
2. Un code et des assets uniques, sans reprise, qui se lisent comme l'œuvre cohérente de l'auteur du projet.
3. Des passes de qualité et d'optimisation (performance, robustesse, sécurité, cohérence).

RÈGLES DE TRAVAIL
- Phase 1 en LECTURE SEULE : tu ne modifies rien. Tu produis un rapport.
- Phase 2 seulement après mon accord explicite, constat par constat.
- Jamais de réécriture de l'historique Git, jamais de push, jamais de suppression de fichier sans mon accord.
- N'ouvre et ne télécharge jamais le code des projets cités ci-dessous : on vérifie l'absence de leurs traces,
  on ne va pas les lire.
- N'ajoute aucune dépendance. Préfère les outils déjà présents (Gradle, Checkstyle, tests, git, un petit script
  Python sans dépendance si nécessaire). Pas d'usine à gaz : un seul script d'audit au maximum.
- Respecte les conventions du dépôt (Checkstyle, nommage, langue et ton des commentaires déjà en place) : la
  référence de style, c'est le code existant écrit par l'auteur, pas un idéal générique.
- Chaque constat donne : gravité (BLOQUANT / IMPORTANT / MINEUR), fichier et ligne, preuve (extrait court),
  correction proposée. Pas de constat sans preuve.

PÉRIMÈTRE
- Tout le dépôt : src/main, src/test, src/gametest, resources (assets, data), docs, README, LICENSE, build.gradle,
  gradle.properties, settings.gradle, fabric.mod.json, mixins JSON, .github (workflows, modèles d'issues),
  .gitignore, fichiers cachés à la racine.
- Le JAR produit par ./gradlew build (ce qui est réellement livré).
- L'historique Git et les métadonnées publiques : messages de commit, auteurs, branches, tags, descriptions de
  releases et de pull requests (via git log et gh si disponible). Lecture seule.

────────────────────────────────────────────────────────────────────────
PASSE 1 — AUCUNE MENTION D'UN AUTRE PROJET
────────────────────────────────────────────────────────────────────────
Recherche insensible à la casse, sur le dépôt, le JAR décompressé et l'historique Git :
- Rendu : Veil, FoundryMC, foundry.veil, Iris, OptiFine, Sodium, Indium, Lumina, Lumin, RTX Remix, NVIDIA,
  Complementary, Rethinking Voxels, SEUS, Photon, BSL, Vibrant Visuals, Distant Horizons, Immersive Portals,
  LabPBR, shaderLABS.
- Son : Sound Physics, SoundPhysics, sonicether, henkelmax, Simple Voice Chat.
- Assets et outils : Blockbench, GeckoLib, Brikk, lootvote, PixelGraph, Patchouli.
- Concurrents et lore : Project NoClip, SpacePotato, Found Footage (comme nom de projet), Liminality, LudoCrypt,
  Escape the Backrooms, Kane Pixels, Kane Parsons, Async, Backrooms Wiki, wikidot, Fandom.
- Code tiers : SPDX, « Copyright », « License », GPL, LGPL, MIT, Apache, Creative Commons, CC BY, Stack Overflow,
  shadertoy, github.com/, gist., et toute URL dans un commentaire.
Points d'attention :
- Les exports de modèles peuvent contenir un champ "credit" (par exemple « Made with ... ») : il doit disparaître.
- Les identifiants techniques STRICTEMENT nécessaires sont tolérés, sans commentaire ni explication autour :
  dépendances exigées par le chargeur (Minecraft, Fabric Loader, Fabric API, Java) et identifiants de mods
  utilisés pour la compatibilité (déclarations "breaks"/"conflicts" de fabric.mod.json, détection de mods dans
  les classes de compatibilité). Liste chaque occurrence tolérée à part, pour que je décide.
- Le mot « Backrooms » et les noms propres au vocabulaire des Backrooms sont le thème du mod : autorisés, ne les
  signale pas et ne les retire pas (entités comme Smiler, Bacteria, Hound, Skin Stealer, Skinwalker, Partygoers,
  Howler ; niveaux et leur numérotation ; objets comme Almond Water ; groupes de l'univers comme M.E.G.). Ce qui est
  interdit, ce sont les textes repris : signale toute description, note, entrée de journal ou phrase de lore qui
  semble copiée d'un wiki ou d'une œuvre communautaire plutôt qu'écrite par nous. Les noms de projets, d'auteurs et de sites listés ci-dessus
  restent interdits.

────────────────────────────────────────────────────────────────────────
PASSE 2 — AUCUNE MENTION NI TRACE DE L'IA
────────────────────────────────────────────────────────────────────────
Recherche insensible à la casse : Claude, Anthropic, GPT, ChatGPT, OpenAI, Copilot, Gemini, LLM, « AI », « IA »
(en mot entier), assistant, prompt, « generated », « généré automatiquement », « Generated with », Co-Authored-By,
noreply@anthropic.com, 🤖, ainsi que les noms d'outils de génération d'images, de sons et de voix.
Vérifie aussi :
- Fichiers et dossiers d'outillage présents dans ce qui est public : CLAUDE.md, .claude/, .skills/, .mcp.json,
  .cursor*, AGENTS.md, fichiers de prompts, documents de conception internes (MOTEUR_*.md, QUALITE_*.md,
  GAMEPLAY.md, CONCURRENTS.md, ASSETS_*.md, PROMPT_*.md). Ils ne doivent exister que dans le dépôt privé.
- Historique Git : messages de commit, lignes de co-auteur, descriptions de PR et de releases. Rapporte, ne
  réécris rien : je déciderai (dépôt privé de développement, nouveau dépôt public propre).
- Réglage d'attribution de l'outil de développement : confirme qu'il est désactivé pour les prochains commits.

────────────────────────────────────────────────────────────────────────
PASSE 3 — MÉTADONNÉES DES ASSETS
────────────────────────────────────────────────────────────────────────
Pour chaque fichier livré :
- PNG : signale tout bloc tEXt, iTXt, zTXt, eXIf, et les manifestes de provenance (blocs C2PA / caBX). Clés à
  surveiller : parameters, prompt, workflow, Software, Comment, Author, Description, Source. Les PNG livrés ne
  gardent que les blocs nécessaires à l'image.
- OGG : commentaires Vorbis (ENCODER, COMMENT, TITLE, ARTIST, et tout champ libre).
- JSON de modèles, d'animations, de lang, pack.mcmeta : champs de crédit, de logiciel, de format d'outil.
- Fichiers sources d'outils de modélisation (.bbmodel et équivalents) : absents du dépôt public et du JAR.
Propose un nettoyage automatique (étape de build ou du validateur d'assets), pas un nettoyage à la main.

────────────────────────────────────────────────────────────────────────
PASSE 4 — UNICITÉ DU CODE (AUCUNE REPRISE)
────────────────────────────────────────────────────────────────────────
- En-têtes de licence, notices de copyright, crédits ou URL dans le code : chacun est un indice de reprise.
- Extraits de shaders célèbres reconnaissables à leurs constantes, à réécrire par notre propre implémentation :
  hachage « fract(sin(dot(…, vec2(12.9898, 78.233))) * 43758.5453) », hachages à constantes .1031 / .1030 /
  .0973, courbe de tone mapping ACES « (x*(2.51x+0.03))/(x*(2.43x+0.59)+0.14) », matrices ACES ajustées, tables
  numériques d'éclairage copiées (les tables LTC doivent sortir de notre propre outil d'ajustement).
- Blocs de code dont le style tranche avec le reste (autres conventions de nommage, autre langue de commentaires,
  autre indentation, idiomes d'une autre base de code) : signale-les comme reprises possibles.
- Doublons internes : code copié-collé d'une classe à l'autre, à factoriser.
- Noms de paquets, de classes ou de fichiers qui reprennent ceux d'un autre projet.

────────────────────────────────────────────────────────────────────────
PASSE 5 — UN CODE QUI SE LIT COMME CELUI DE L'AUTEUR
────────────────────────────────────────────────────────────────────────
But : un code intentionnel et homogène, aligné sur les conventions existantes du dépôt. Signale :
- Commentaires qui paraphrasent le code (« incrémente le compteur »), qui s'adressent au lecteur (« Here we… »,
  « Note: », « This method… »), qui vendent (« robust », « seamless », « comprehensive », « elegant »), ou dans
  une langue différente du reste du dépôt.
- Javadoc mécanique sur des méthodes triviales ; ou à l'inverse, absence de commentaire là où une décision non
  évidente le mérite (renvoie vers le pourquoi, comme les docs du dépôt).
- Noms génériques : data, result, temp, value, info, item2, handleX, processData, Helper, Utils, Manager,
  Service, Impl sans raison ; noms différents pour un même concept selon les fichiers.
- Sur-ingénierie : interface à une seule implémentation sans besoin de test, fabrique pour un seul produit,
  couches d'abstraction vides, options de configuration jamais lues, paramètres jamais utilisés.
- Défense excessive : vérifications null impossibles, try/catch qui avalent tout, catch (Exception) sans raison,
  journaux redondants, valeurs de repli qui masquent des erreurs.
- Code mort, code commenté, TODO génériques, exemples d'utilisation laissés dans le code, textes de remplissage.
- Uniformité suspecte : même gabarit répété partout là où le dépôt varie naturellement selon le besoin.
- Emojis, tirets cadratins ou mise en forme Markdown dans les commentaires et messages de log.
- Messages de commit génériques ou d'un style différent du reste de l'historique (rapport seulement).
Pour chaque cas, propose la version simplifiée, dans le style du dépôt. Ne change pas le comportement.

────────────────────────────────────────────────────────────────────────
PASSE 6 — ASSETS UNIQUES ET COHÉRENTS
────────────────────────────────────────────────────────────────────────
Selon la bible de style et la bibliothèque de matériaux du projet (si présentes dans le dépôt privé) :
- Textures en 32×32 par face (atlas d'entités en multiples exacts), aucun pixel hors palette du niveau, pas
  d'anticrénelage ni de dégradé lisse, pas de transparence partielle hors matériaux prévus.
- Cartes _n et _s présentes et dans les plages du format retenu ; valeurs conformes au matériau déclaré.
- Indices typiques d'images générées : pixels hors grille, bruit sans motif, couleurs en trop (compte les
  couleurs uniques), éclairage peint dans l'albédo avec des directions incohérentes, texte illisible ou absurde,
  symétries cassées sans raison.
- Doublons exacts (même empreinte) et quasi-doublons (empreinte perceptuelle proche) entre assets.
- Aucune copie de fichiers du jeu de base : compare les empreintes avec les assets du JAR de Minecraft 1.21.1 ;
  aucun asset vanilla redistribué.
- Modèles : coordonnées dans les bornes, grille 1/32, rotations autorisées, budget d'éléments, états de blocs
  complets, traductions présentes, aucune référence à une texture absente.
- Sons : niveaux cohérents entre eux, pas de saturation, pas de silence parasite en début ou fin, boucles propres.
- Ressources orphelines (jamais référencées) et références cassées.

────────────────────────────────────────────────────────────────────────
PASSE 7 — PÉRIMÈTRE « JEU DE BASE INTACT »
────────────────────────────────────────────────────────────────────────
- Chaque mixin et chaque gestionnaire d'évènement qui touche au rendu, au son, à l'interface, aux commandes, au
  chat, à l'inventaire, à la mort : il ne doit agir que dans une dimension Backrooms (ou sur ce qu'un portail en
  montre). Liste ceux qui ne sont pas gardés par cette condition.
- Contenu du mod invisible hors des Backrooms hors mode développement : onglets créatifs, recherche, suggestions
  de commandes, recettes, butins, progrès, affichage dans les mods d'items.
- Outils et commandes de développement absents du build de release, ou gardés par l'environnement de développement.

────────────────────────────────────────────────────────────────────────
PASSE 8 — QUALITÉ
────────────────────────────────────────────────────────────────────────
- ./gradlew build complet : Checkstyle, tests, tests en jeu, couverture exigée, contrôle du JAR. Tout doit passer.
- Avertissements du compilateur, dépréciations, types bruts, conversions implicites dangereuses.
- Gestion d'erreurs : chaque exception est traitée, journalisée une fois, ou propagée ; pas d'échec silencieux.
- Concurrence : appels graphiques sur le thread de rendu, appels audio sur le thread du son, monde lu hors thread
  seulement par copie ; structures partagées protégées ou immuables.
- Ressources natives : chaque objet graphique ou audio créé est libéré ; chaque tampon natif alloué est rendu.
- Réseau et sécurité (le mod sert de prison, il doit résister à un client modifié) : chaque paquet reçu par le
  serveur est validé (bornes, distances, dimensions, droits, fréquence) ; aucune décision de gameplay n'est
  laissée au client ; aucune donnée sensible envoyée au-delà du rayon prévu.
- Configuration : valeurs validées au chargement, bornes et valeurs par défaut documentées, erreurs explicites.
- Journaux : pas de spam par tick ou par image en production ; niveaux de log cohérents.
- Traductions : clés manquantes ou inutilisées, textes en dur dans l'interface.
- Documentation publique (README, docs/) cohérente avec le code réel.

────────────────────────────────────────────────────────────────────────
PASSE 9 — OPTIMISATION
────────────────────────────────────────────────────────────────────────
Mesure avant de corriger ; propose seulement ce qui a un effet mesurable.
- Chemins chauds (par image, par tick, par son, par paquet) : allocations (new, lambdas capturantes, boxing,
  itérateurs, String.format, concaténations), recherches répétées, calculs qui pourraient être faits une fois.
- Rendu : changements d'état graphique inutiles, uniformes renvoyées sans changement, synchronisations CPU-GPU
  (lectures de pixels, glFinish, glGetError en boucle hors développement), objets graphiques recréés par image.
- Serveur : coût par tick des systèmes du mod, parcours d'entités, chargements de chunks déclenchés inutilement.
- Réseau : taille et fréquence des paquets du mod, envois redondants.
- Mémoire : caches sans borne, références qui retiennent des mondes ou des chunks déchargés.
- Assets : taille du JAR, textures plus grandes que nécessaire, sons trop longs ou trop lourds, modèles au-delà
  du budget.
- Utilise le moniteur de performance du projet et les bancs existants ; rapporte avant / après sur la machine de
  référence quand c'est possible.

────────────────────────────────────────────────────────────────────────
RAPPORT
────────────────────────────────────────────────────────────────────────
Écris le rapport dans AUDIT_RAPPORT.md, à la racine du dépôt privé, ignoré par Git. Structure :
1. Résumé : nombre de constats par passe et par gravité ; ce qui bloque une publication.
2. Les constats, passe par passe, au format : gravité, fichier:ligne, preuve, correction proposée.
3. Les occurrences tolérées (identifiants techniques nécessaires), à part, pour décision.
4. Les traces dans l'historique Git et sur la plateforme, avec les options possibles, sans rien exécuter.
5. Ce que tu n'as pas pu vérifier, et pourquoi.
6. Les vérifications à automatiser pour la suite : au plus trois, les plus utiles (par exemple : liste de termes
   interdits en CI, nettoyage des métadonnées au build, contrôle des champs de crédit des modèles), intégrées aux
   tâches Gradle existantes.

Puis arrête-toi et attends mon accord avant toute correction.

PHASE 2 (après accord)
- Corrige par petits lots cohérents, un lot par commit, avec un message dans le style de l'historique du dépôt, sans
  aucune mention d'outil ni de co-auteur.
- Après chaque lot : ./gradlew build complet ; rien ne régresse (tests, couverture, Checkstyle, contrôle du JAR).
- Relance les recherches des passes 1 à 3 sur le JAR final : zéro occurrence hors liste tolérée.
- Termine par un résumé de ce qui a changé et de ce qui reste ouvert.
```

---

## Notes d'utilisation

- **Où le lancer** : dans le dépôt de développement **privé**, avec la session de l'agent en mode plan ou en
  lecture seule pour la phase 1.
- **Avant de le lancer** : désactiver l'attribution automatique des commits dans les réglages de l'outil, sinon la
  phase 2 recréerait les traces qu'elle doit supprimer.
- **Fréquence** : une fois avant chaque publication ; les trois vérifications automatisées (rapport, point 6) tournent
  ensuite à chaque build.
- **Ce que l'audit ne remplace pas** : ta relecture en jeu, et la vérification des règles d'une plateforme de
  publication qui exigerait de déclarer l'usage de l'IA (`ASSETS_ET_PRODUCTION_IA.md` §12.4).
- **Liste de termes** : à compléter quand un nouveau projet de référence ou un nouvel outil entre dans le travail.
