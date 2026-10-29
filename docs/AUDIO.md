# Audio des Backrooms (moteur audio maison)

> Spécification de référence : `docs/backrooms/MOTEUR_AUDIO.md` (feuille de route et hypothèses). Ce document décrit
> ce qui est **livré**, phase par phase, et ce qui reste ouvert. Les écarts à la spécification sont consignés dans
> `docs/GUIDE.md`, section 2.4 (27 octobre 2026).

## En jeu

Chaque dimension qui a un **profil audio** (`assets/gtgs_backrooms/audio_profiles/<dimension>.json`) a son acoustique :
une réverbération de base (durée, densité, diffusion, gain, clarté), une occlusion par les blocs entre un son et
l'auditeur (part retirée par un bloc plein, par un bloc partiel, plafond), une absorption des aigus par la distance, et
une ambiance : le bourdonnement de ses néons. Aujourd'hui le seul profil est celui du Level 0 (`level_0.json`), et le
bourdonnement est un **son par défaut** (le son d'ambiance du phare vanilla, déclaré sous `gtgs_backrooms:block.neon.hum`
dans `sounds.json`, sous-titré « Bourdonnement électrique ») en attendant le vrai, décision de l'auteur du 27 octobre
2026 : l'acoustique compte, les fichiers son viendront après.

Règle unique de la spécification : **le traitement d'un son dépend de la dimension où il est émis, pas de celle où il
est écouté.**

- Dans le Level 0, chaque son (blocs, entités, paquets du serveur, les pas du joueur, le bourdonnement) reçoit la
  réverb du Level 0, et les blocs entre lui et la caméra l'étouffent (graves gardés, aigus coupés d'abord).
- Le profil du Level 0 tire son acoustique **de sa génération** (`"acoustics": "generated"`) : à la connexion, le
  serveur donne au client la graine du layout de chaque Level 0 (le client n'a pas la graine du monde), et la forme de
  la salle autour d'une source se lit dans le layout, sans un rayon (aire et murs des colonnes ouvertes à seize blocs
  au plus : libre parcours moyen d'un son entre deux parois, quatre fois le volume sur la surface, et ouverture, part du
  pourtour qui ne se ferme pas). Là où la copie du monde contredit la génération (un bloc posé dans un passage, un mur
  cassé, autour de la source), et dans toute dimension `"acoustics": "raycast"`, l'acoustique se calcule **par rayons** :
  des rayons lancés autour de chaque source sur une copie du monde, hors du thread de rendu, donnent la taille de
  l'espace (libre parcours moyen avant un mur) et son ouverture (part des rayons sortis sans mur). L'une ou l'autre
  choisit l'un des huit réglages de réverb de la dimension (quatre tailles, fermé ou ouvert, tirés de la réverb de
  base : un placard sonne court, une grande salle long, un espace ouvert peu) ; un rayon qui passe le cadre d'un
  portail ouvert continue de l'autre côté. L'occlusion
  cherche aussi, en qualité complète, un chemin qui contourne par les côtés de la source (une porte, un trou). Un son
  démarre avec la réverb déjà connue de sa cellule de quatre blocs (ou celle de base) et un rayon droit d'occlusion,
  sans attendre ; le calcul lui arrive au tick suivant, puis toutes les dix ticks tant qu'il joue.
- Un son du Level 0 entendu depuis l'Overworld à travers un portail (rejoué au cadre par `FrameSoundPlayer`) garde
  la réverb du Level 0 ; son occlusion se mesure en deux segments, de sa vraie source au cadre dans le Level 0, puis du
  cadre à la caméra dans l'Overworld ; et si l'auditeur est lui-même dans une autre dimension profilée (un portail vers
  un autre level), la réverb de sa salle s'ajoute par le second envoi auxiliaire, le cadre étant une seconde source dans
  cette salle. Un cadre qui ramène dans la même dimension (deux salles du Level 0) n'ajoute pas de seconde réverb : ce
  serait le même profil, compté deux fois (`EmissionDimension.room`). Un son de l'Overworld reste strictement vanilla, même entendu depuis le
  Level 0.
- Un son peut passer **deux cadres** : quand aucun portail du monde de l'auditeur ne mène au monde du son, ou qu'un
  portail vu à travers un autre le rapproche davantage, il ressort au premier cadre après le trajet des deux segments
  (de la source au second cadre, puis de ce cadre au premier), le monde intermédiaire étant celui que le client tient
  pour la vue. Son acoustique reste celle de sa dimension d'émission.
- Les sons sans place dans un monde (interface, musique) ne sont jamais touchés ; en cas de doute sur la dimension
  d'émission, le son reste vanilla.
- Trois modes, une ligne dans le journal à chaque bascule : **EFX** (réverb, filtre passe-bas, deux envois auxiliaires
  demandés à la création du contexte OpenAL) ; **volume seul**, sans l'extension EFX (pilote ou périphérique qui ne la
  fournit pas, ou option `gtgsbackrooms.audio.efx` coupée) : l'occlusion et le milieu se rendent par le volume de la
  source, sans réverb ; **coupé** pour la session après une erreur OpenAL, ou si un autre moteur acoustique travaille
  dans le même contexte (reconnu à un filtre, un effet ou un slot EFX qui n'est pas des nôtres, vanilla n'en créant
  aucun) : les sons restent vanilla et l'autre moteur garde la main. La liaison son-image ne dépend d'aucun des trois.
- **Sous l'eau** : la caméra ou la source plongée dans l'eau, le milieu du profil (`"underwater"` : gain, gain des aigus)
  s'ajoute à l'occlusion, comme un mur de plus.
- **Direction perçue** (`"perceived_direction"`, de 0 à 1, optionnelle selon la spécification après validation à
  l'oreille) : quand le chemin droit est bouché et qu'un détour passe mieux, la source se déplace, à la même distance
  de l'auditeur, vers le point de passage du détour (le son semble venir de la porte, pas du mur). Le profil du Level 0
  la laisse à zéro tant que l'auteur ne l'a pas écoutée.
- Les tubes qui bourdonnent sont les quatre plus proches de la caméra, à moins de douze blocs, dans le monde du
  joueur, et, derrière chaque portail visible vers une dimension à ambiance, jusqu'à quatre tubes à moins de douze
  blocs de son arrivée, entendus au cadre comme tout son de l'autre côté (volume après le trajet jusqu'au cadre,
  acoustique de leur dimension, réverb de la salle de l'auditeur en plus). Chaque bourdonnement **suit l'état de son
  tube** (`NeonSchedule`, le même calendrier que son image, sur l'horloge du monde) : le volume suit l'intensité, et un
  tube qui s'éteint ou se rallume claque (`click_off`, `click_on` du profil, sons par défaut : un levier vanilla). Le
  profil de look du Level 0 garde ses parts de tubes qui vacillent et morts à zéro (décision de l'auteur du 27 octobre
  2026) : rien ne vacille tant qu'il ne les relève pas. À la traversée, chaque bourdonnement est replacé avec les autres
  sons (`SoundAnchors`) : un tube entendu au cadre se retrouve à sa place, sans relance.
- **Liaison son-image** : un son déclaré dans un lien (`assets/gtgs_backrooms/sound_cues/*.json`), émis dans une
  dimension profilée, fait réagir l'image de **sa** dimension : les néons à portée vacillent au rythme du son
  (`neon_disturbance`), la vignette du post pulse (`screen_pulse`, sur les seuls pixels de cette dimension), la lumière
  baisse (`lightmap_flicker`). Le rythme vient de l'**enveloppe** du son, précalculée au chargement des ressources
  (énergie par fenêtre de 20 ms), lue à la position de lecture réelle de la source OpenAL. Aujourd'hui un lien : le cri
  (`gtgs_backrooms:scream`, son par défaut : un cri vanilla, à jouer par `/playsound gtgs_backrooms:entity.howler.scream`).
  Une réaction suit l'enveloppe à travers une courbe (`"easing"` : linéaire, quadratique ou cubique en entrée ou en
  sortie, ou en douceur) ; le cri fait aussi trembler un peu la caméra (`camera_shake`, deux degrés au plus), si
  l'option `gtgsbackrooms.audio.cameraShake` le permet (coupée par défaut), seulement pour un joueur dans la dimension du
  cri, jamais dans une dimension vanilla ni avec l'option de confort, à l'échelle des effets d'écran du jeu. Les
  enveloppes se calculent en arrière-plan et se gardent sur disque sous l'empreinte du fichier son
  (`.cache/gtgs_backrooms/envelopes` du dossier du jeu) : un rechargement ne refait que les sons changés. Un son lié émis
  dans l'Overworld (une créature passée de l'autre côté) est un son ordinaire. Le **plafond de clignotement**
  (`FlashLimiter`) borne l'effet final de toutes les réactions ensemble : jamais plus de trois variations fortes par
  seconde, au-delà des baisses douces, sur la charge de l'écran entier (chaque vue de portail pèse la part de l'écran
  qu'elle couvre, le monde du joueur le reste) ; l'option `gtgsbackrooms.audio.reduceFlashes` adoucit tout et coupe la
  pulsation de l'écran, sans toucher au son.

Les profils se rechargent avec les ressources (F3+T) : on règle une réverb à l'oreille sans relancer. Le rechargement
relance aussi le moteur de son vanilla (comme un changement de périphérique) : tous les sons s'arrêtent, les objets EFX
disparaissent avec le contexte, les tubes proches reprennent leur bourdonnement à la revue suivante (au plus dix
ticks). Les réverbs sont **pré-chauffées** : une fois le rechargement fini (écran titre compris), le slot et l'effet de
chaque réglage de chaque dimension profilée sont créés d'avance sur le thread du son (« [Audio] Reverbs
pre-chauffees : 8 reglage(s) cree(s) d'avance »), plutôt qu'au premier son qui en a besoin, souvent à l'approche d'un
cadre (recommandation du plan, section 10.1).

Diagnostic : la commande du client `/backrooms_state` écrit dans le journal l'état de l'image (monde du joueur et son
look, looks vus et néons de la dernière image, pack de shaders, portails du monde et palier de leur vue, mondes
distants, mode du moteur audio et chaque son traité avec sa dimension, son réglage, son occlusion, sa salle, son
filtre et son décalage, perturbations en cours et plafond de clignotement) ; le joueur reçoit une ligne traduite.

| Propriété | Défaut | Effet |
|-----------|--------|-------|
| `gtgsbackrooms.audio.enabled` (`-PaudioEnabled`) | `true` | Moteur audio. Coupé : aucun profil chargé, aucun son traité, aucun bourdonnement. |
| `gtgsbackrooms.audio.reduceFlashes` (`-PreduceFlashes`) | `false` | Confort : les réactions visuelles aux sons ne varient que par baisses douces, la pulsation de l'écran est coupée ; le son ne change pas. |
| `gtgsbackrooms.audio.quality` (`-PaudioQuality`) | `auto` | Calcul par rayons : `light` (seize rayons, deux rebonds, le chemin direct), `full` (trente-deux, quatre, et quatre détours), `auto` (celle du moteur de shaders, complète sauf sur une carte intégrée). |
| `gtgsbackrooms.audio.efx` (`-PaudioEfx`) | `true` | Coupée : mode volume seul (banc du repli sans EFX). |
| `gtgsbackrooms.audio.reduceBass` (`-PreduceBass`) | `false` | Confort : les sons des dimensions à profil audio perdent une part de leurs graves (environ -6 dB), le filtre de chaque source passant de passe-bas à passe-bande ; avec EFX seulement, les sons vanilla ne changent pas. |
| `gtgsbackrooms.audio.cameraShake` (`-PcameraShake`) | `false` | Le cri fait trembler un peu la caméra du joueur qui est dans sa dimension. |
| `gtgsbackrooms.audio.debugRays` (`-PaudioDebugRays`) | `false` | Débogage : les rayons des derniers calculs se dessinent dans le monde (jaune : rebond, vert : sorti, magenta : par un cadre, gris : perdu hors de la copie ; chemins d'occlusion du vert au rouge). |
| `gtgsbackrooms.audio.compareGenerated` (`-PaudioCompareGenerated`) | `false` | Développement : là où la génération suffit, les rayons sont lancés aussi et l'accord des deux est journalisé (hypothèse H9). |

## Architecture

Le moteur n'ajoute aucun son au jeu de base et ne remplace rien : il **branche** les sons que le moteur de son vanilla
joue déjà sur la réverb et le filtre de leur dimension, par l'extension EFX d'OpenAL, et fait jouer le bourdonnement
par le moteur vanilla comme n'importe quel son positionné.

| Paquet | Classes | Rôle |
|--------|---------|------|
| `core/audio/profile/` | `AudioProfile`, `AudioProfiles` | Le profil d'une dimension (donnée, `record`, validé au chargement) et leur registre par dimension. |
| `core/audio/acoustic/` | `EmissionDimension`, `OcclusionTrace`, `ReverbParameters`, `SolidityGrid`, `SolidityWindow`, `AcousticEvaluator`, `AcousticResult`, `AcousticCache`, `ReverbPresets`, `PortalPlanes`, `AudioQuality`, `GeneratedAcoustics`, `PerceivedDirection`, `RayTrace` | La forme des salles qu'un contenu tire de sa génération, enregistrée par dimension, et l'accord de la copie avec elle ; le déplacement d'une source bouchée vers son détour ; les segments des rayons d'un calcul, pour le débogage. La copie du monde (solidité de chaque bloc par section, posée entière et jamais modifiée, sous verrou court) et la fenêtre de sept sections de côté qu'un calcul lit sans verrou ; l'évaluateur par rayons (sphère de Fibonacci, rebonds par réflexion sur la face franchie, sortie du monde copié comptée comme perdue ; occlusion par le direct et quatre détours), son résultat (taille, ouverture, part perdue, occlusion), le cache par cellule de quatre blocs, les huit réglages de réverb d'une dimension, les cadres de portail que les rayons traversent (rectangle, transposition sans allocation, copie du monde d'arrivée), la qualité (légère ou complète, alignée sur le rendu). La dimension d'émission d'un son (ancre d'un son rejoué au cadre, sinon monde du joueur à la demande, sinon vanilla) et la salle dont la réverb s'ajoute à un son entré par un cadre ; l'occlusion par un rayon droit à travers les blocs (parcours de voxels, un pas par face, le bloc de la source et celui de l'auditeur exclus, plafond du profil, 64 pas au plus ; deux segments combinés pour un son entré par un cadre) et les gains du filtre passe-bas qui en découlent ; les réglages de l'effet de réverb EFX tirés du profil, dans les plages de l'effet. Logique pure, couverte à 100 %. |
| `core/audio/cue/` | `CueClock`, `Cue`, `CueRegistry`, `Envelope`, `Disturbances`, `FlashLimiter`, `Easing`, `CameraShake` | Les courbes d'une réaction ; le tremblement de caméra (deux oscillations sur l'horloge commune). L'horloge commune (temps du monde et fraction de tick, période de 2^20 ticks) ; un lien son-image (donnée validée : sons, enveloppe, réactions d'une liste fermée) et leur registre par son ; l'enveloppe d'un son (énergie par fenêtre de 20 ms, normalisée, un octet par fenêtre, lue interpolée) ; les perturbations en cours (quatre au plus, par dimension : ce qu'il reste à un néon, la pulsation à portée de l'auditeur, la baisse de lumière) ; le plafond de clignotement. Logique pure, couverte à 100 %. |
| `core/audio/client/` | `AudioResources`, `EfxBackend`, `AcousticRouter`, `NeonHum`, `BlockSolidity`, `SolidityIndex`, `AcousticWorker`, `CueResources`, `CueTracker`, `AdjustedSource`, `AcousticDebugRenderer` | Colle client (dont le pré-chauffage des réverbs sur la file du thread du son, le filtre passe-bande de l'option qui réduit les graves, et la description des sons traités et des perturbations pour `/backrooms_state`) (exemptée de couverture, nommée dans `docs/TESTING.md`) : chargement des profils ; EFX (détection au démarrage du moteur de son, envois auxiliaires relus, par dimension un slot et un effet par réglage de réverb créés à la première source qui en a besoin, réserve fixe de 48 filtres passe-bas, coupe-circuit à la première erreur OpenAL, objets oubliés à la fermeture du contexte) ; routage de chaque son joué (dimension d'émission, rayon droit d'occlusion sur le monde vivant au départ, réverb connue de la cellule, appels OpenAL confiés au thread du son par la source du son, demandes au thread de calcul et résultats appliqués, revue toutes les dix ticks tant que le son joue, filtres rendus à la fin du son) ; ce qu'un bloc fait au son ; la copie du monde (deux sections par tick autour de la caméra et des arrivées des portails visibles, sections sales recopiées, colonnes déchargées et sections lointaines oubliées) et le cache des cellules par monde ; le thread de calcul (files bornées d'objets recyclés, fenêtres de calcul, cadres de portail copiés dans la demande) ; les liens son-image chargés après les sons vanilla et les enveloppes décodées (décodeur Ogg de vanilla, chaque variante de chaque jeu de sons, un renvoi vers un autre évènement énuméré par un tirage fixe, une valeur par unité de poids) ; les sons liés en cours (position de lecture lue sur le thread du son chaque tick, amplitude par image, plafond de clignotement, ce que le rendu lit) ; bourdonnement des néons proches et, au cadre de chaque portail visible, de ceux de l'autre côté (index des néons du moteur de shaders, un par monde client, revu toutes les dix ticks ; géométrie du cadre et volume après le trajet de `PortalSound`, ancre de `SoundAnchors`) ; la part de volume et le décalage de position d'une source (mode volume seul, direction perçue) ; le dessin des rayons de débogage. |
| `backrooms/level0/` | `LevelZeroRoomShape`, `LevelZeroLayoutPayload`, `LevelZeroLayoutSync`, `client/LevelZeroAcoustics` | Le contenu : la forme d'une salle du Level 0 lue dans son layout (logique pure, couverte) ; la graine du layout envoyée à la connexion par le serveur ; côté client, la forme enregistrée pour la dimension à sa réception (colle). |

Mixins : `MixinSoundEngine` (création et fermeture du contexte OpenAL, deux envois auxiliaires demandés dans ses
attributs), `MixinSoundSystem` (chaque son qui part au moteur, après son ancre), `SourceAccessor` (identifiant OpenAL
d'une source), `MixinSource` (part de volume et décalage de position d'une source), `MixinCamera` (tremblement du cri,
après la pose de la caméra du joueur), `SoundSystemAccessor` et
`SoundManagerAccessor` (déjà là pour les portails), `MixinClientWorld` (chaque bloc changé d'un monde client marque sa
section à recopier, le même point que l'index des néons).

Threads : la copie du monde se fait sur le thread de rendu (le monde client ne se lit que là), le calcul sur son
thread (« GTG's Backrooms acoustics », démon, lancé au premier calcul), les appels OpenAL par source sur le thread du
son. Rien de bloquant : files bornées, demande abandonnée quand la file est pleine (la revue suivante la refait).

### Un son, de bout en bout

```
SoundSystem.play(son)                       vanilla : source OpenAL creee et retenue (sources.put), volume, position
  SoundAnchors.onPlay                       portails : vraie source du son (monde, position, volume)
  AcousticRouter.onPlay                     MixinSoundSystem, TAIL
    CueTracker.onPlay                       son lie (CueRegistry) : perturbation dans sa dimension, a sa vraie source,
                                            quel que soit le mode du moteur
    EfxBackend.mode                         coupe : rien de plus, le son reste vanilla
    EmissionDimension.resolve(ancre, monde du joueur, relatif)   -> dimension d'emission, ou vanilla
    AudioProfiles.of(dimension)             sans profil : rien, le son reste vanilla
    EmissionDimension.room(ancre, monde du joueur)               -> salle de l'auditeur, pour un son entre par un cadre
    OcclusionTrace.occlusion(son -> camera) rayon droit sur le monde vivant, thread de rendu
      + occlusion(source -> entree du cadre) dans le monde de la source, combinee (son entre par un cadre)
    AcousticCache (cellule de la source)    reverb deja connue de la cellule, sinon le reglage de base
    milieu                                  camera ou source dans l'eau : ce que l'eau retire, avec l'occlusion
    EfxBackend.takeFilter                   un filtre de la reserve (mode EFX)
    manager.run(source -> EfxBackend.apply) thread du son : envoi 0 vers le slot du reglage de la dimension, envoi 1 vers
                                            celui de la salle de l'auditeur, absorption de l'air, filtre passe-bas attache ;
                                            mode volume seul : part de volume de la source (AdjustedSource)
    AcousticWorker.request                  dimension calculee : demande au thread de calcul (source, cadres a portee,
                                            point joue, auditeur, point d'entree, forme generee de la dimension)
image
  LookFrame.beginWorld                      horloge commune (CueClock : temps du monde), amplitude de chaque perturbation
                                            (enveloppe a la position de lecture), plafond de clignotement
  NeonLights / lightmap / LookPost          etat des neons x ce qu'il en reste, baisse de lumiere, pulsation de la vignette
thread de calcul
  GeneratedAcoustics                        forme generee, si la copie du monde s'accorde avec la generation autour de la
                                            source ; sinon :
  SolidityGrid.window -> AcousticEvaluator  rayons autour de la source (cadres traverses), cache de la cellule s'il est
                                            frais ; occlusion du point joue a l'auditeur, et de la source au cadre ;
                                            meilleur detour (direction percue)
tick
  AcousticRouter.onClientTick               resultats recus : reglage de reverb repose s'il change (resultat fiable), filtre
                                            repose si l'occlusion a change, source deplacee vers son detour (direction
                                            percue) ; toutes les dix ticks : sons finis, filtre rendu ; sons joues,
                                            nouvelle demande (ou rayon droit hors calcul), un filtre etranger cherche
                                            (cession) ; joueur passe dans un autre monde (WORLD_SWITCHED des portails) :
                                            revue au tick suivant, envoi 1 repose
  SolidityIndex.onClientTick                copie du monde : deux sections par tick, autour de la camera puis des arrivees
  CueTracker.onClientTick                   sons lies finis liberes, position de lecture relue (thread du son), source suivie
  NeonHum.follow                            chaque bourdonnement suit l'etat de son tube (volume, claquements)
  NeonHum.onClientTick                      tubes les plus proches de la camera, puis de l'arrivee de chaque portail
                                            visible : un son en boucle chacun (au cadre pour l'autre cote), lance ou arrete
```

Un son rejoué au cadre d'un portail est, pour vanilla, un son du monde du joueur à la position du cadre : son ancre
(`SoundAnchors`) dit le monde de sa vraie source, et c'est **ce** profil qui s'applique. L'ancre porte aussi le point
par lequel le son entre dans le cadre, vu de son monde : l'occlusion se mesure de la vraie source à ce point dans le
monde de la source (le monde distant que le client tient pour la vue), puis du cadre à la caméra dans le monde du
joueur, les deux combinés ; le volume, lui, est déjà celui du trajet jusqu'au cadre (`PortalSound`).

## Hypothèses et compatibilité (état au 28 octobre 2026)

Hypothèses de la spécification (section 16) :

| # | État | Preuve ou manque |
|---|------|------------------|
| H1 | Tenue | Un son lié se reconnaît à son identifiant au départ (`CueRegistry`), avec sa vraie source par son ancre, relayé compris : test décisif (cri du Level 0 passé par deux cadres, réaction dans la vue). |
| H2 | Tenue sous Windows | Deux envois demandés à la création du contexte et obtenus (OpenAL Soft) ; Linux et macOS non essayés. |
| H3 | Tenue | Tous les réglages par source passent par le thread du son ; aucune erreur OpenAL sur les bancs. |
| H4 | Tenue | Copie incrémentale : 0,3 à 0,7 ms par section, deux par tick (2,4 ms au pire pendant le remplissage), puis 0,05 ms. |
| H5 | Ouverte | Pas de saut audible : écoute de l'auteur. |
| H6 | En partie | Position de lecture lue chaque tick ; la réaction part sur l'image du départ du cri ; décalage son-image non mesuré par capture audio-vidéo (aucun outil de capture du son au banc). |
| H7 | Tenue | Jumeau GLSL identique sur 573 vecteurs, sur le processeur graphique du PC de référence. |
| H8 | Ouverte | Coûts unitaires mesurés (5 µs par son routé, 70 à 90 µs par calcul, 47 µs par revue) ; le banc d'ensemble (30 néons, trois cris, un portail) n'est pas fait. |
| H9 | En partie | Génération et rayons : même réglage pour 70 % de 40 sons, même ouverture pour tous ; l'oreille tranchera. |

Matrice de la spécification (section 9.3), telle qu'elle est :

| Configuration | Acoustique | Liaison, côté son | Réactions visuelles | Portails (son) |
|---------------|------------|-------------------|---------------------|----------------|
| Vanilla | Oui | Oui | Oui | Oui |
| Sans EFX (ou option coupée) | Volume seul | Oui | Oui | Oui, sans réverb |
| Mod de terrain seul | Oui | Oui | Oui, sauf la face des tubes (shaders du mod de terrain) | Oui |
| Mod de shaders, pack actif | Oui | Oui | Pulsation et look de la vue sur l'image du pack ; ni lumière ni face des tubes | Oui |
| Autre moteur acoustique | Cède (objets EFX reconnus) | Oui | Oui | Celui de l'autre moteur |
| Mods qui ajoutent des sons | Oui | Oui | Oui | Oui |

## Limites connues

- **Acoustique dérivée de la génération** (spécification, section 6.4) : le client n'a pas la graine du monde (vanilla
  n'envoie qu'une graine hachée) ; le serveur lui envoie la seule graine du layout de chaque Level 0, à la connexion.
  Un serveur sans le mod, ou un Level 0 dont le générateur a changé, n'envoie rien : la dimension reste calculée par
  rayons. La forme ne connaît que le layout (colonnes ouvertes ou murs, plafond à hauteur fixe) : pas les blocs posés
  ou cassés (les rayons reprennent la main dès que la copie du monde contredit la génération autour de la source), ni
  le mobilier ou les détails de mur que le layout ne porte pas. Hypothèse H9 (la forme générée suffit-elle ?) : au banc
  du 28 octobre 2026, sur 40 sons du Level 0, la génération et les rayons choisissent le même réglage pour 70 % d'entre
  eux et la même ouverture pour tous ; les écarts sont d'une taille voisine (un couloir que les rayons voient plus long
  ou plus court). L'écoute dira si cela suffit.
- **Rayons** : la copie du monde avance de deux sections par tick (une section coûte ses 4096 blocs, chaque état de
  bloc distinct classé une fois) ; les premières secondes d'une partie, ou loin de ce qui a déjà été copié, un
  résultat avec trop de rayons perdus hors de la copie (plus d'un quart) ne compte pas : le son garde la réverb de base
  ou la dernière connue de sa cellule, le calcul reprend à la revue suivante. Un rayon s'arrête à 48 blocs ; une
  fenêtre de calcul fait sept sections de côté autour de la source (ou de l'auditeur pour l'occlusion) : un son à plus
  de 48 blocs de l'auditeur a une occlusion sur ce que la fenêtre voit. Un rayon ne traverse qu'un cadre (un niveau de
  portail), et seulement si l'autre côté est copié (le monde distant d'une vue, ou ce monde-ci) ; derrière un cadre
  dont l'autre côté n'est pas copié, le rayon rencontre ce qu'il y a derrière le plan de ce côté-ci. La réverb d'une
  cellule reste bonne cent ticks avant d'être recalculée (un bloc posé ou cassé compte alors). Les chemins de
  contournement (qualité complète) passent à deux blocs de la source sur les quatre côtés horizontaux, jamais au-dessus.
- **Son rejoué au cadre** : deux cadres au plus (un portail vu à travers un autre), et seulement par un monde
  intermédiaire que le client tient (le sien, ou le monde distant d'une vue, troisième monde compris : le serveur
  synchronise aussi l'arrivée d'un portail vu à travers un autre). L'occlusion d'un son à deux cadres compte le segment de la source au second cadre et celui du
  premier cadre à la caméra, pas le segment entre les deux cadres, dans le monde intermédiaire. Le
  trajet d'un son qui change de cadre après la traversée du joueur est remis à jour avec sa
  position (`SoundAnchors.refresh`), pas quand l'auditeur se déplace seulement. Le second envoi n'existe que si le
  contexte OpenAL en donne deux (OpenAL Soft : deux par défaut) ; sinon, la réverb de la source seule.
- **Bourdonnement au cadre** : un tube de l'autre côté n'est proposé que si l'auditeur est devant le cadre et à
  portée du son ; une fois lancé, son bourdonnement suit le cadre comme tout son rejoué là (il ne se tait pas si
  l'auditeur passe derrière le cadre, il s'éloigne seulement) ; revu toutes les dix ticks.
- **Liaison son-image** : l'état de chaque néon a deux implémentations jumelles, `NeonSchedule` (processeur, lue par
  le son) et `neon_schedule.glsl` (shaders, lue par l'image : la face du tube s'éteint dans le shader), sur un hachage
  entier de 32 bits ; un fichier de vecteurs communs (`src/test/resources/neon_schedule_vectors.json`, écrit par un
  programme indépendant) les tient d'accord, en test pour la première, au premier rechargement en développement pour
  la seconde (option `gtgsbackrooms.look.verifyNeonShader`, qui coupe le jumeau s'il s'écarte). Un son diffusé en flux
  ou de plus de trente secondes n'a pas d'enveloppe (amplitude constante). La position de lecture se lit sur le thread
  du son une fois par tick et se prolonge entre deux lectures par l'horloge de la machine : décalage son-image non
  mesuré par capture audio-vidéo (spécification, section 10.3). Sous un pack de shaders, une vue compte pour l'écran
  entier dans le plafond de clignotement (sa surface n'est pas mesurée sur ce chemin) : le plafond y est plus prudent,
  jamais plus permissif. Le tremblement de caméra suit la caméra du joueur seulement, pas celle d'une vue. Le volume d'un
  bourdonnement qui suit son tube part de son volume de départ : après une traversée, il peut différer un peu de ce
  que la remise en place a posé, jusqu'à la revue qui le remplace.
- **Occlusion des blocs partiels** : un bloc est plein s'il est un cube opaque, partiel s'il a une forme de collision,
  air sinon : une vitre compte comme partielle, l'eau comme de l'air (le milieu sous l'eau ne compte que pour la caméra
  ou la source plongée, pas pour l'eau entre elles).
- **Mode volume seul** : sans EFX, ni réverb ni filtrage des aigus ; l'occlusion baisse tout le son. La cession à un
  autre moteur se reconnaît à ses objets EFX (identifiants 1 à 128 relus toutes les dix ticks, ceux d'OpenAL Soft
  partant de 1) : le filtre direct d'une source ne se relit pas sous OpenAL Soft, un moteur qui ne crée ses objets
  qu'au-delà de ces identifiants passerait inaperçu jusqu'à son premier conflit (erreur OpenAL : coupé quand même).
- Toutes les dix ticks, la revue des sons traités parcourt la table des sons du moteur (un itérateur alloué par
  revue, comme la remise en place des sons après une traversée) ; rien par image. La table des sons traités a des clés
  fortes : c'est la revue qui retire un son que le moteur n'a plus et rend son filtre (à clés faibles, un son repris par
  le ramasse-miettes avant la revue gardait son filtre pour toujours).
- **Bourdonnement et volume** : volume général ou d'ambiance à zéro, aucun bourdonnement n'est demandé au moteur ; un
  tube dont le son a été arrêté par le moteur (relance, arrêt par vanilla) repart à la revue suivante.

## Tests

- Unitaires : `AudioProfileTest` (profil du Level 0, sons de bourdonnement et de claquement enregistrés, mode
  d'acoustique, défauts, refus), `AudioProfilesTest` (registre, doublons), `ReverbParametersTest` (réglages dans les
  plages de l'effet, valeurs ramenées dedans), `OcclusionTraceTest` (axe, diagonale, haut et bas, plafond atteint tôt,
  trajet trop long, deux segments, filtre), `EmissionDimensionTest` (ancre, monde du joueur, relatif, inconnu, salle de
  l'auditeur), `SolidityWindowTest`, `SolidityGridTest`, `AcousticEvaluatorTest`, `AcousticCacheTest`, `ReverbPresetsTest`,
  `PortalPlanesTest`, `AudioQualityTest` (acoustique par rayons, voir `docs/TESTING.md`), `CueClockTest` (horloge du
  monde, période), `EnvelopeTest` (silence, impulsion, rampe interpolée, voies entrelacées, sons minuscules), `CueTest`
  (lien du cri livré, défauts, refus : identifiant, sons, réactions, type, plages), `CueRegistryTest` (son réclamé par
  deux liens), `DisturbancesTest` (distance, amplitude, dimension, déplacement, quatre au plus, réactions absentes),
  `FlashLimiterTest` (trois variations fortes par seconde, adoucissement, option de confort, remise à zéro, charge de
  l'écran pondérée par les vues), `EasingTest`, `CameraShakeTest`, `NeonScheduleTest` (vecteurs communs),
  `GeneratedAcousticsTest`, `PerceivedDirectionTest`, `RayTraceTest`, `LevelZeroRoomShapeTest` (salle, mur, accord avec la
  copie, cache), `LevelZeroLayoutPayloadTest`, `PortalSoundTest` (un et deux cadres),
  `ModRegistriesTest` (chaque son déclaré avec son sous-titre), `MixinTargetsTest` (les mixins du moteur s'appliquent).
- En jeu, banc de production du 27 octobre 2026 au soir (PC de référence, intérieur du Level 0) : le journal dit
  « [Audio] EFX disponible, 2 envoi(s) auxiliaire(s) par source », « [Audio] 1 profil(s) audio » puis, au premier son
  du Level 0 (un bourdonnement, sur le thread du son), « [Audio] Reverb de gtgs_backrooms:backrooms_level_0 :
  decroissance 1.1 s, gain 0.32 » ; aucune erreur OpenAL ; avec le mod de terrain et le mod de shaders installés, même
  démarrage. Moniteur de performance : `client.audio.route` 1,4 ms et 32 Kio pour le tout premier son (chargement des
  classes), puis **5 µs** par son traité, et quelques centaines d'octets que la mesure arrondit à zéro (l'état du son,
  son entrée dans la table, la tâche confiée à sa source : un évènement, comme l'instance que vanilla crée pour chaque
  son, relevé à la revue du 29 octobre 2026) ; `client.audio.tick` 47 µs et 0,1 Kio toutes les dix ticks (le parcours
  de la table des sons). L'écoute reste manuelle (spécification, section 13) : pas encore faite par l'auteur.
- Le bourdonnement s'appuie sur l'index des néons du moteur de shaders, qui lit le bloc de néon dans le **profil de
  look** : moteur de shaders coupé (`gtgsbackrooms.look.enabled=false`), aucun tube ne bourdonne.
- Portails, banc du 28 octobre 2026 (portail de l'Overworld vers le Level 0, joueur dans l'Overworld) : dès le
  chargement, quatre sons routés d'un coup et la réverb du Level 0 créée, le joueur n'y étant pas : les quatre
  bourdonnements des néons de l'autre côté, entendus au cadre, avec l'acoustique du Level 0.
- Rayons, banc du 28 octobre 2026 (intérieur du Level 0, puis portail depuis l'Overworld) : « Calcul acoustique par
  rayons, qualité FULL (32 rayons, 4 rebonds, 5 chemins) », réglages 1 puis 0 créés (couloir, puis placard pour un tube
  dans un renfoncement) ; copie du monde 0,3 à 0,7 ms par section (`client.audio.snapshot` 2,4 ms au pire par tick
  pendant le remplissage, 0,05 ms ensuite), évaluation 70 à 90 µs sur le thread de calcul (`client.audio.eval`, 3 ms
  pour la toute première, chargement des classes), sur ce thread un résultat créé par évaluation (correction ci-dessous) ; depuis l'Overworld, la copie du Level
  0 se fait autour de l'arrivée du portail et les bourdonnements au cadre y sont évalués. Correction du 29 octobre 2026 :
  « zéro allocation » était faux. Chaque évaluation crée son résultat (quatre flottants), et la forme générée du Level 0
  emballait la clé de chaque colonne lue (environ 4 000 par estimation) : clés primitives depuis. Un résultat calculé avec trop
  de rayons hors de la copie (réglage « ouvert » vu au premier essai) n'est plus appliqué.
- Liaison son-image, banc du 28 octobre 2026 (intérieur du Level 0, `/playsound gtgs_backrooms:entity.howler.scream`
  sous le tube regardé, captures au départ du cri puis 0,3 et 1,5 s après) : la capture prise au départ du cri est à
  55,5 de luminosité moyenne contre 85,9 juste avant (zone du tube 59 contre 144, bas de l'image 54 contre 76), et
  l'image suivante revient à 86 (le cri vanilla dure moins d'une seconde) : néons perturbés, lumière baissée, vignette
  pulsée, puis plus rien quand le son finit. Le journal du même banc confirme les enveloppes : « 4 enveloppe(s)
  precalculee(s) », une par cri vanilla (42 à 49 fenêtres, 0,84 à 0,98 s), le renvoi `"type": "event"` du son du mod
  énuméré variante par variante. Les captures du banc sont à une seconde d'écart, plus que le cri : la forme de
  l'enveloppe (amplitude qui suit l'énergie du son) n'est pas visible capture par capture, seule la réaction au départ
  du cri l'est (58 contre 86). L'écoute (claquements, volume des bourdonnements qui suit un tube) reste manuelle, et le
  décalage son-image n'est pas mesuré par capture audio-vidéo.
- En jeu, test `LevelZeroGameTests.serverGivesTheLayoutSeedOfItsLevelZeroOnly` : un serveur sans Level 0 n'envoie aucune
  graine ; le générateur du Level 0 envoie la sienne, celle que le client refait sans la graine du monde, et un autre
  générateur rien.
- Pré-chauffage et diagnostic, banc du 28 octobre 2026 au soir : « Reverbs pre-chauffees : 8 reglage(s) cree(s)
  d'avance » sur le thread du son dès la fin du chargement ; `/backrooms_state` tapé dans le chat écrit l'état
  (« Sons traites : 0, mode EFX », portails, mondes distants ; un son dont les réglages sont restés sur une source
  qu'il ne joue plus y est marqué « source perimee ») et répond « Frame state written to the log. ». L'option
  qui réduit les graves n'est pas écoutée (aucune erreur OpenAL au banc ; l'effet reste à juger à l'oreille).
- Robustesse, banc du 28 octobre 2026 (intérieur du Level 0, cri lié) : « 2 envoi(s) auxiliaire(s) par source
  (demande : 2) » ; option EFX coupée, « mode volume seul » ; avec un mod de banc qui crée un filtre EFX avant le
  premier son, « autre moteur acoustique reconnu (filtre, identifiant 1) : le moteur cède » ; dans les trois modes, le
  cri fait réagir l'image (tube regardé de 85 à 60).
- Liaison son-image, même jour : jumeau GLSL vérifié sur le processeur graphique du PC de référence (« 573 vecteur(s)
  identiques », 72 étages patchés), la face du tube regardé s'éteint au cri ; tremblement de caméra mesuré de 1 à 2
  pixels de décalage entre deux images (corrélation) ; enveloppes : quatre calculées au premier lancement, quatre lues du
  cache au suivant.
- Acoustique générée, même jour : rayons de débogage dessinés (rebonds, sorties, chemins d'occlusion) ; comparaison
  H9 sur 40 sons (voir plus haut).
- **Test décisif** (spécification des shaders, section 12.1), banc du 28 octobre 2026 : joueur dans l'Overworld devant
  un cadre vers le Level 0 ; dans le Level 0, huit blocs derrière l'arrivée, un second cadre vers un autre point du
  Level 0 ; un cri lié (bloc musical sous une tête qui porte le son) quatre blocs derrière l'arrivée du second. Le
  journal dit le cri « rejoue au cadre ... trajet 12.0 blocs » (quatre jusqu'au second cadre, huit jusqu'au premier),
  avec la réverb du Level 0 et l'auditeur dans l'Overworld ; l'image montre la vue imbriquée, et le tube du plafond de
  la vue baisse pendant le cri (169 puis 132 de luminosité, 170 après). La vue imbriquée est trop petite à 854 x 480
  pour y mesurer la réaction.
