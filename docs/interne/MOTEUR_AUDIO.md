# Moteur audio et liaison son-image — GTG's Backrooms

> **Document de conception interne** : il nomme des projets de référence. À garder hors du dépôt public, et sans
> aucune reprise de code ni mention de ces projets dans le mod (`QUALITE_ET_PRESETS.md` §3.0).
>
> Document de conception : un moteur d'acoustique maison inspiré de Sound Physics Remastered, et le lien entre ce
> que le joueur entend et ce qu'il voit (néons qui grésillent et clignotent ensemble, cris de monstres qui font
> vaciller la lumière…). Cible : Fabric, Minecraft 1.21.1, Java 21, mappings Yarn. Document compagnon de
> `MOTEUR_SHADERS.md`, dont il reprend la démarche et sur lequel il s'appuie pour toute la partie visuelle.
>
> **Statut : feuille de route de conception, pas encore spécification définitive.** Les hypothèses les plus risquées
> (OpenAL, threads, synchronisation) sont listées au §16 et doivent être validées sur le code réel (phase 0 du §14)
> avant de figer l'architecture.
>
> **Prochaine étape : le code, pas le document.** Comme pour le moteur de shaders, le document ne sera enrichi que de
> ce que les prototypes confirment ou infirment.

---

## 1. Objectif

Donner aux Backrooms une **acoustique propre** et **lier le son à l'image**, sans Sound Physics Remastered ni autre
mod audio, de sorte que :

1. l'acoustique des Backrooms (réverbération des salles, sons étouffés par les murs, bourdonnement des néons) soit
   **le comportement par défaut** de la dimension, sans réglage pour le joueur ;
2. **les sons de l'Overworld restent vanilla**, comme son image ;
3. le son **suive les portails** : un son du Level 0 entendu depuis l'Overworld à travers un cadre garde
   l'acoustique du Level 0, et inversement ;
4. son et image partagent **une seule source de vérité** : un néon qui s'éteint coupe son bourdonnement *et* sa
   lumière à la même image ; un cri fait vaciller les néons proches au rythme du cri ;
5. tous les joueurs voient et entendent **la même chose au même moment**, à quelques ticks près, sans trafic réseau
   supplémentaire pour les effets d'ambiance ;
6. rien ne trahit le mod : pas d'écho exagéré ni d'effet « démo technique » ;
7. ça tourne sur les petites configs : calcul acoustique **hors du thread principal**, coût par son borné ;
8. les effets lumineux respectent un **plafond de clignotement** pour les joueurs photosensibles (§11).

### 1.1 Périmètre : les Backrooms seulement

**Règle unique : le traitement d'un son dépend de la dimension où il est émis, pas de celle où il est écouté.**

| Situation                                                                 | Traitement                                                                                   |
| ------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Son émis dans une dimension vanilla (Overworld, Nether, End…)             | **Strictement vanilla** : aucune acoustique, aucun `Cue`, aucune réaction visuelle            |
| Son émis dans une dimension Backrooms, joueur dans les Backrooms          | Acoustique de la dimension, `Cue` et réactions visuelles                                     |
| Son émis dans une dimension Backrooms, **entendu depuis l'Overworld à travers un portail** | **Le cri et les sons passent** par le cadre, avec l'acoustique de leur dimension d'origine ; réactions visuelles limitées à la vue du portail (point 3 ci-dessous) |
| Sons de l'Overworld autour du joueur, pendant qu'il écoute un portail     | Strictement vanilla                                                                          |
| Monstre des Backrooms passé dans l'Overworld, qui y crie                  | Son émis dans l'Overworld : **traité comme un son vanilla** (le fichier du cri, sans acoustique ni réaction visuelle, comme un mob vanilla) |

Conséquences, à respecter dans chaque composant :

1. **Acoustique** (§6.1 à §6.4) : appliquée seulement aux sons émis dans une dimension qui a un `AudioProfile`.
2. **Liaison son-image** (§6.5 à §6.10) : un `Cue` ne déclenche ses réactions que si **la source du son est dans
   une dimension profilée**. Ailleurs, c'est un son ordinaire.

**Déterminer la dimension d'émission réelle (risque de régression n° 1).** Le moteur intervient sur un système de son
global : une erreur ici modifierait des sons vanilla. La dimension d'émission est **celle du monde où le son a été
créé**, jamais déduite de la dimension du joueur ni de la position où le son est rejoué :

- son joué par le monde du joueur (paquet vanilla, bloc, entité) : dimension du monde du joueur **au moment de la
  demande** ;
- son d'un monde distant (relayé par `portal_remote_entity`, rejoué par `FrameSoundPlayer`) : dimension portée par
  le relais (index de dimension du payload), **pas** celle du cadre où il est rejoué ;
- son demandé pendant une traversée prédite non confirmée : dimension du monde qui l'a émis, résolue au moment de la
  demande et gardée avec le son ;
- dimension **inconnue ou ambiguë** : le son est traité comme **vanilla**. En cas de doute, on ne touche à rien.

Pour un son vanilla, le traitement est **absent**, pas neutralisé : aucune source n'est envoyée vers un slot de
réverb, aucun filtre n'est attaché, aucun `Cue` n'est publié. Vérifié par l'hypothèse H1 et le prototype B (§14.1).
3. **Réactions visuelles d'un son entendu à travers un portail** : elles restent **dans la vue du portail**. Les
   néons qui vacillent sont ceux de la vue ; la pulsation de vignette (`screen_pulse`) passe par le post masqué du
   moteur de shaders, qui ne touche que les pixels Backrooms du cadre. Le tremblement de caméra (`camera_shake`),
   qui toucherait tout l'écran, est **interdit quand le joueur est dans une dimension vanilla**.
4. **Mises en place globales neutres** : certaines choses se font pour tout le jeu, faute de pouvoir les faire par
   dimension (envois auxiliaires négociés à la création du contexte OpenAL, objets EFX créés au démarrage,
   enveloppes calculées au chargement des ressources). Elles doivent rester **sans effet audible** hors des
   Backrooms : aucun son d'une dimension vanilla n'est routé vers un slot de réverb ni filtré.
5. **Conséquence sur le jeu de base** : bloquer Sound Physics Remastered (§9.1) le retire aussi de l'Overworld pour
   le joueur qui l'utilisait. C'est voulu : le jeu de base reste vanilla, sans acoustique ajoutée.
6. **Sons d'hallucination** (`GAMEPLAY.md` §5) : ce sont des sons **générés côté client pour un seul joueur**, sans
   source dans le monde du serveur. Règles :
   - leur « dimension d'émission » est **celle du joueur qui hallucine**, et ils n'existent **que** quand ce joueur
     est dans une dimension Backrooms : jamais dans une dimension vanilla ;
   - ils reçoivent l'acoustique de cette dimension, autour de la position fictive où ils sont joués ;
   - ils ne sont **jamais entendus par un autre joueur** ni relayés à travers un portail ;
   - ils peuvent porter un `Cue` (par exemple un faux cri dans le Level 0) dont les réactions visuelles ne concernent
     **que ce joueur** et restent soumises au plafond de clignotement (§11) ;
   - **les monstres ne les entendent pas** : ce qu'ils entendent, c'est la réaction du joueur (`GAMEPLAY.md` §15.2).

---

## 2. Pourquoi ne pas utiliser ni forker Sound Physics Remastered

### 2.1 Raisons techniques

| Sound Physics Remastered suppose                         | GTG's Backrooms fait                                                                 |
| -------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Un monde : celui du joueur                               | Plusieurs mondes vus et entendus à la fois (mondes distants des portails)            |
| La source est là où vanilla joue le son                  | `FrameSoundPlayer` rejoue un son distant **au cadre** du portail ; la vraie source est de l'autre côté |
| Les rayons s'arrêtent sur les blocs du monde courant     | Un rayon qui traverse un cadre doit continuer dans l'autre monde, transposé          |
| Acoustique s'appliquant partout, réglée globalement      | Acoustique **par dimension**, l'Overworld restant vanilla                             |
| Le son est une fin en soi                                | Le son doit **piloter l'image** (et l'image piloter le son)                          |

Installé à côté du mod, Sound Physics Remastered calculerait l'acoustique d'un son rejoué au cadre comme si sa source
était dans le monde du joueur : réverbération et occlusion fausses. Le forker reviendrait, comme pour Veil, à
débrancher ses hypothèses avant d'ajouter quoi que ce soit, avec la même contrainte de tests et de couverture.

### 2.2 Raison juridique (je ne suis pas juriste, à faire valider si besoin)

- Sound Physics Remastered est sous **GPL-3.0** (fork de Sound Physics Fabric, lui-même fork du Sound Physics
  original). GTG's Backrooms est sous licence **propriétaire**.
- La GPL est **plus stricte que la LGPL** de Veil : un code dérivé de Sound Physics Remastered intégré au mod
  imposerait la GPL-3.0 **à l'ensemble du mod distribué**, pas seulement à la partie reprise. C'est incompatible avec
  « tous droits réservés ».
- S'en servir comme dépendance qui appelle son code ou l'étend pose aussi question avec la GPL (la frontière entre
  « programme séparé » et « œuvre combinée » est débattue pour les mods chargés dans le même processus). À éviter.
- **Les idées, les techniques et les paramètres physiques ne sont pas protégés** : lancer des rayons pour estimer
  une réverbération, tester l'occlusion sur plusieurs chemins, filtrer les aigus avec la distance sont des
  techniques générales de l'audio de jeu. Les réimplémenter soi-même est libre.
- Si le mod doit être commercialisé, faire vérifier ce point par un juriste.

### 2.3 Conclusion

**Sound Physics Remastered sert de référence de lecture, pas de base de code ni de dépendance.** Encore plus
strictement que pour Veil, vu la GPL.

---

## 3. Utiliser Sound Physics Remastered comme référence : règles

Mêmes règles que pour Veil (`MOTEUR_SHADERS.md`, §3), renforcées :

1. **Lire pour comprendre, pas pour recopier.** Notes en mots, jamais en code.
2. **Ne jamais copier-coller**, ni code, ni tables de valeurs propres au projet, ni structures de classes.
3. **Écrire notre code sans le leur ouvert à côté.** Revenir à la référence pour une question précise, puis refermer.
4. **Partir de la littérature générale de l'audio de jeu** (réverbération par lancer de rayons, occlusion,
   absorption de l'air, effets EFX d'OpenAL) et de la **documentation d'OpenAL Soft** autant que de leur code : nos
   valeurs se règlent à l'oreille sur nos salles, pas en reprenant les leurs.
5. **Aucune mention d'autre projet** dans le code, les commentaires, le JAR, l'interface, les crédits ni la
   documentation publiée du mod (le dépôt GitHub est public). Les noms de référence n'apparaissent que dans ces
   documents de conception internes, gardés hors du dépôt public. *(Cette règle remplace l'ancienne consigne de
   « tracer l'inspiration » dans `docs/AUDIO.md`.)*
6. **Le mod est entièrement propriétaire** : chaque partie est notre propre version, écrite pour fonctionner
   parfaitement avec le reste. Règle complète : `QUALITE_ET_PRESETS.md` §3.0.

Ce qui vaut la peine d'être compris (leurs options de configuration publiques décrivent bien la mécanique) :

| Sujet                         | Ce qu'on en retient                                                                                     |
| ----------------------------- | ------------------------------------------------------------------------------------------------------- |
| Réverbération par rayons      | Rayons lancés depuis la source, avec quelques rebonds, pour estimer la taille et la nature de l'espace ; plus de rayons = plus stable mais plus cher |
| Occlusion                     | Plusieurs chemins source → auditeur, blocs traversés comptés, facteur par type de bloc, cas des blocs non pleins |
| Direction perçue              | Estimer d'où le son *semble* venir grâce aux réflexions, sauf si la source est directement visible        |
| Absorption                    | Perte des aigus avec la distance (air), filtre sous l'eau                                               |
| Accès au monde hors thread    | Copie d'une zone du monde autour du joueur, gardée quelques ticks, pour lancer les rayons hors du thread principal |
| Sons longs                    | Réévaluation périodique des sons longs ou mobiles                                                       |
| Débogage                      | Affichage en jeu des trajets des rayons et de l'occlusion ; journaux de temps de calcul                  |

---

## 4. Capacités visées

### 4.1 Équivalences Sound Physics Remastered

| Capacité                         | Équivalent GTG                                                     | Priorité |
| -------------------------------- | ------------------------------------------------------------------ | -------- |
| Réverbération par rayons         | `AcousticEvaluator` (rayons, rebonds) + `EfxBackend` (réverb EFX)  | P1       |
| Occlusion à travers les blocs    | `AcousticEvaluator` (chemins d'occlusion) + filtre passe-bas       | P1       |
| Absorption de l'air, sous l'eau  | Filtre passe-bas fonction de la distance et du milieu              | P2       |
| Direction perçue                 | Optionnel, après validation à l'oreille                            | P3       |
| Accès au monde hors thread       | `WorldSnapshot` (copie bornée) **ou** acoustique dérivée de la génération (§6.4) | P1 |
| Débogage visuel                  | Rendu des rayons et de l'occlusion, réutilisant le moniteur de perf | P1      |
| Intégration Simple Voice Chat    | Hors périmètre initial                                             | —        |

### 4.2 Capacités en plus, propres au mod

| Capacité                                          | Priorité |
| ------------------------------------------------- | -------- |
| **Acoustique par dimension** (`AudioProfile`), Overworld vanilla | **P0** |
| **Liaison son-image** : signaux partagés (`Cue`), enveloppes, fonctions déterministes | **P0** |
| **Acoustique à travers les portails**             | P1       |
| **Acoustique dérivée de la génération** du Level 0 | P1       |
| Plafond de clignotement et options de confort     | **P0**   |

---

## 5. Architecture

### 5.1 Vue d'ensemble

```
client/audio/
|-- profile/    AudioProfile (données), AudioProfileLoader, AudioProfiles (registre par dimension)
|-- efx/        EfxBackend (contexte, slots auxiliaires, filtres), EfxCapabilities (détection, repli)
|-- acoustic/   AcousticEvaluator (rayons, rebonds, occlusion), AcousticResult, AcousticCache,
|               WorldSnapshot (copie bornée hors thread), GeneratedAcoustics (Level 0, d'après la génération)
|-- portal/     PortalAcoustics (rayons et sources à travers les cadres, avec FrameSoundPlayer)
|-- cue/        Cue, CueRegistry (sound_cues/*.json), CueClock (horloge commune), CueBus (abonnés son et image)
|-- envelope/   EnvelopeBaker (enveloppes précalculées), Envelope, PlaybackTracker (position de lecture réelle)
|-- shared/     NeonSchedule, Disturbances (fonctions déterministes partagées avec le GLSL)
|-- comfort/    FlashLimiter, AudioComfortOptions
`-- compat/     AudioCompat (Sound Physics Remastered, autres mods audio)
```

Côté image, rien de nouveau n'est créé : les réactions passent par le moteur de shaders (`LookUniforms`, éclairage
des néons, post) décrit dans `MOTEUR_SHADERS.md`.

### 5.2 Principe central : une seule source de vérité

Le piège classique est de faire « le son déclenche l'image » **ou** « l'image déclenche le son » de façon ad hoc :
les deux dérivent l'un de l'autre, se décalent, et divergent d'un joueur à l'autre. Ici, **ni le son ni l'image ne
commandent** : tous deux lisent le même état.

```
                 horloge commune (CueClock)
          temps du monde + fraction de tick, synchronisé
                            |
       +--------------------+---------------------+
       |                                          |
  signaux déterministes                    signaux d'événement
  (NeonSchedule, ambiance)                 (cri d'un monstre, porte…)
  f(position, temps, graine)               son joué → Cue → enveloppe
       |                                          |
       +--------------------+---------------------+
                            |
                         CueBus
                 /                        \
          côté son                     côté image
   volume, coupure, clic          uniformes du moteur de shaders
   du bourdonnement               (lightmap, néons, post)
```

Deux familles de signaux :

- **Déterministes** : calculables par n'importe quel client à partir de la position, du temps du monde et d'une
  graine. Exemple : le motif de clignotement de chaque néon. **Aucun paquet réseau**, tout le monde est d'accord.
- **D'événement** : déclenchés par quelque chose qui arrive (un monstre crie). Le serveur joue le son comme
  vanilla ; à la réception, le client reconnaît le son comme un `Cue` et en déduit la réaction visuelle à partir de
  son **enveloppe précalculée** et de sa **position de lecture réelle**.

### 5.3 Un son, de bout en bout

```
son demandé (vanilla, serveur ou client)
  |
  CueRegistry : ce son est-il un Cue ?  -> oui : CueBus.publish(Cue, position, début)
  |
  AudioProfiles : la dimension de la SOURCE a-t-elle un profil ?
     non (Overworld)  -> son vanilla, aucun traitement (exigence absolue §7)
     oui (Level 0)    -> AcousticEvaluator, hors thread principal :
                          acoustique de la génération (§6.4) ou rayons sur un WorldSnapshot
                          + passage par les portails si la source est de l'autre côté (§7)
  |
  thread du son : EfxBackend applique réverb (slot auxiliaire) et occlusion (filtre) à la source OpenAL
  |
  lecture ; PlaybackTracker suit la position réelle de lecture pour les Cue
```

### 5.4 Une image, de bout en bout

```
début d'image
  CueClock.update(temps du monde, fraction de tick)
  NeonSchedule : état des néons proches de chaque caméra de vue (même fonction qu'en GLSL)
  Disturbances : perturbations actives (cris), avec leur enveloppe à la position de lecture courante
  FlashLimiter : plafonne la fréquence et l'amplitude des variations
  |
  LookUniforms (moteur de shaders) : gtgs_CueTime, gtgs_Disturbances, intensités des néons
  |
  rendu du monde et des vues de portail : chaque vue lit les signaux de SA dimension
```

---

## 6. Composants

### 6.1 `AudioProfile` (données, P0)

Le pendant sonore du `LookProfile`, un par dimension (`assets/gtgs_backrooms/audio_profiles/level_0.json`) :

```json
{
  "dimension": "gtgs_backrooms:backrooms_level_0",
  "acoustics": "generated",
  "reverb_base": { "decay": 1.1, "density": 0.8, "diffusion": 0.9, "gain": 0.32, "brightness": 0.7 },
  "occlusion": { "wall": 0.6, "partial_block": 0.25, "max": 0.85 },
  "air_absorption": 0.6,
  "ambience": { "hum": "gtgs_backrooms:ambient.neon_hum", "hum_follows_neons": true },
  "cues": ["gtgs_backrooms:neon", "gtgs_backrooms:scream"]
}
```

- Une dimension **sans profil** n'a aucun traitement : sons strictement vanilla.
- Le Level 0 n'a pas d'entités (`GAMEPLAY.md` §6) : dans son profil, le `Cue` `scream` ne sert qu'aux **cris
  d'hallucination** (§1.1, point 6). Les cris de vrais monstres viennent des niveaux habités, avec leur propre profil.
- `acoustics` : `generated` (§6.4), `raycast` (§6.3) ou `none`.
- Rechargé avec les ressources (F3+T) pour régler à l'oreille sans relancer.
- Le profil audio et le profil visuel d'une même dimension sont deux fichiers distincts, liés par la dimension : on
  règle l'un sans casser l'autre.

### 6.2 `EfxBackend` (P1)

Minecraft joue ses sons par OpenAL (OpenAL Soft fourni par LWJGL). L'extension **EFX** fournit réverbération
(slots auxiliaires) et filtres (passe-bas).

- **Détection** : extension `ALC_EXT_EFX` au démarrage du moteur de son. Absente → repli : seuls volume et filtre
  de distance simulés par le gain, une ligne de log.
- **Envois auxiliaires** : le nombre d'envois par source se négocie **à la création du contexte** OpenAL
  (`ALC_MAX_AUXILIARY_SENDS`). Il faut donc intervenir là où Minecraft crée son contexte. Valeur visée : 2 envois
  (une réverb « proche », une réverb « de la salle »), à confirmer (H2).
- **Slots** : un petit nombre fixe de slots de réverb préréglés, partagés par toutes les sources (pas un slot par
  son). Chaque source est envoyée vers le slot le plus proche de son acoustique, avec un gain d'envoi.
- **Filtres** : un filtre passe-bas par source pour l'occlusion et l'absorption.
- **Thread** : **tous les appels OpenAL se font sur le thread du son** de Minecraft (son exécuteur dédié), jamais
  sur le thread de rendu ni sur un thread de calcul. Le calcul acoustique produit des valeurs ; le thread du son les
  applique.
- **Durée de vie** : objets EFX créés une fois, libérés à la fermeture ou au rechargement du moteur de son (changement
  de périphérique audio).
- **Robustesse du contexte (risque de régression n° 2)** : la négociation des envois peut échouer, le contexte peut
  être recréé (changement de périphérique, rechargement du moteur, autre mod), un autre mod peut toucher aux mêmes
  sources. Règles :
  - après chaque création ou recréation du contexte, **relire** le nombre d'envois réellement obtenu
    (`ALC_MAX_AUXILIARY_SENDS` du contexte courant) et s'y adapter, jusqu'au repli sans EFX ;
  - ne toucher **qu'aux sources que le moteur a lui-même marquées** comme traitées (son émis dans les Backrooms),
    jamais aux autres ;
  - à chaque recréation, reconstruire slots et filtres et **oublier** les anciens identifiants OpenAL, jamais les
    réutiliser ;
  - en cas d'erreur OpenAL sur une opération EFX : repli propre (coupe-circuit du §12), le son continue vanilla.

### 6.3 `AcousticEvaluator` et `WorldSnapshot` (P1)

Réverbération et occlusion par rayons, pour les dimensions en `raycast` et pour les blocs modifiés par les joueurs.

- **Rayons de réverb** : depuis la source, N rayons répartis uniformément, quelques rebonds ; on en tire une
  estimation de la taille de l'espace et de sa réflectivité, convertie en paramètres de réverb. Valeurs de départ :
  16 rayons et 2 rebonds en qualité légère, 32 et 4 en complète, à régler à l'oreille et au chronomètre.
- **Occlusion** : chemins source → auditeur (1 en léger, plusieurs en complet), blocs traversés pondérés par leur
  type ; converti en gain et coupure des aigus du filtre.
- **Hors du thread principal** : le monde client n'est **pas** sûr à lire depuis un autre thread. Le calcul lit un
  `WorldSnapshot` : copie des sections de chunks autour de l'auditeur, faite sur le thread principal, renouvelée
  quand le joueur s'éloigne ou après quelques ticks. Coût de la copie à mesurer (H4).
- **Démarrage du son** : un son ne doit pas attendre le calcul. Il démarre avec la **dernière acoustique connue** de
  la zone (`AcousticCache`), puis reçoit le résultat frais dès qu'il arrive. Le saut éventuel doit être inaudible
  (H5).
- **Budget** : nombre maximal d'évaluations par tick ; au-delà, les sons partagent le résultat de la zone.

### 6.4 Acoustique dérivée de la génération (P1)

Le Level 0 est **généré de façon déterministe** (labyrinthes, salles, cellules : `LevelZeroChunkGenerator`, cache LRU
des secteurs). Sa forme est donc connue sans lancer un seul rayon :

- taille de la salle ou du couloir d'une cellule, hauteur de plafond, matériaux (moquette absorbante, papier peint),
  ouvertures vers les cellules voisines ;
- précalculées **par cellule** à partir de l'évaluation des cellules déjà faite par la génération, puis mises en
  cache comme les secteurs ;
- converties directement en paramètres de réverb et en occlusion entre cellules.

C'est **beaucoup moins cher** que les rayons, parfaitement stable (aucun bruit d'une évaluation à l'autre) et
identique pour tous les joueurs. Limite : un joueur qui casse ou pose des blocs change la forme réelle. Règle :
les cellules où un bloc a changé basculent sur les rayons (§6.3) ; les autres restent sur la génération.

### 6.5 `CueRegistry` : déclarer les liens son-image (P0)

Fichiers `assets/gtgs_backrooms/sound_cues/*.json` :

```json
{
  "id": "gtgs_backrooms:scream",
  "sounds": ["gtgs_backrooms:entity.howler.scream"],
  "envelope": true,
  "reactions": [
    { "type": "neon_disturbance", "radius": 12.0, "strength": 0.8, "follow_envelope": true },
    { "type": "screen_pulse", "strength": 0.15, "max_distance": 24.0 }
  ]
}
```

```json
{
  "id": "gtgs_backrooms:neon",
  "deterministic": "neon_schedule",
  "sounds": {
    "hum_loop": "gtgs_backrooms:block.neon.hum",
    "off_click": "gtgs_backrooms:block.neon.click_off",
    "on_click": "gtgs_backrooms:block.neon.click_on"
  },
  "reactions": [ { "type": "neon_light" } ]
}
```

- Un son qui n'est déclaré dans aucun `Cue` est un son ordinaire : aucune réaction visuelle.
- Un son déclaré dans un `Cue` mais **émis dans une dimension vanilla** (monstre passé dans l'Overworld) est lui
  aussi un son ordinaire : le `Cue` est ignoré (§1.1).
- Les types de réaction sont une liste fermée, implémentée côté image (§6.10) ; un type inconnu est refusé au
  chargement, journalisé.

### 6.6 `EnvelopeBaker` : enveloppes précalculées (P0 pour les cris)

Pour qu'un cri fasse vaciller la lumière **au rythme du cri**, il faut son enveloppe d'amplitude. Analyser l'audio
en direct est exclu : OpenAL ne rend pas facilement le signal mixé, et ce serait coûteux.

- Au chargement des ressources, pour **les seuls sons déclarés avec `"envelope": true`**, décoder l'OGG (décodeur
  Vorbis fourni par LWJGL) et calculer l'énergie RMS par fenêtre de 20 ms, normalisée entre 0 et 1.
- Stockage compact (un octet par fenêtre : environ 50 octets par seconde de son), en mémoire.
- Calcul en arrière-plan pendant le chargement, mis en cache sur disque avec l'empreinte du fichier son.
- Une enveloppe se lit à la **position de lecture réelle** du son (§6.7), pas au temps écoulé depuis la demande.

### 6.7 `CueClock` et `PlaybackTracker` : synchronisation (P0)

- **Horloge commune** : temps du monde (synchronisé par le serveur) plus fraction de tick, une valeur par image,
  identique côté son et côté image. C'est l'entrée de toutes les fonctions déterministes.
- **Position de lecture réelle** : un son demandé ne démarre pas à la même image (file du moteur de son, tampons
  OpenAL). Pour les `Cue` à enveloppe, la position se lit sur la source OpenAL (`AL_SEC_OFFSET`), sur le thread du
  son, et se transmet au rendu. Pour un son **en flux** (sons longs), cette position est relative aux tampons en
  file : à traiter à part ou à éviter pour les `Cue` (H6).
- **Tolérance visée** : décalage son-image **inférieur à une image à 60 FPS pour les cris** (environ 17 ms), et
  inférieur à un tick (50 ms) pour les clignotements d'ambiance. Objectifs à mesurer, pas garanties.
  **Ces chiffres ne sont pas promis** tant que les captures audio-vidéo (§10.3) ne les ont pas confirmés.
- **Ordre de mise en œuvre** : commencer par les **sons courts, non diffusés en flux**, mesurer le décalage réel,
  et seulement ensuite étendre aux autres types de sons. Une enveloppe lue au mauvais moment fait vaciller le néon
  avant le cri, après lui, ou à contretemps : mieux vaut une réaction moins précise mais à l'heure (repli de H6)
  qu'une réaction rythmée mais décalée.

### 6.8 Fonctions déterministes partagées Java / GLSL (P0)

Le cœur de « néon qui clignote avec le son » :

- `NeonSchedule.state(blocPos, temps, graine)` dit si un néon est allumé, éteint ou en train de vaciller, et à
  quelle intensité. **La même fonction existe en Java et en GLSL.**
- Java s'en sert pour le **son** : couper ou baisser le bourdonnement d'un néon éteint, jouer le clic à chaque
  transition, et pour l'éclairage des néons (§6.8 de `MOTEUR_SHADERS.md`).
- Le GLSL s'en sert pour **l'image** : luminosité de la face du néon, par fragment, sans aucune donnée envoyée par
  néon.
- **Arithmétique entière** pour le hachage (`uint`, opérations bit à bit, disponibles en GLSL 1.50) : Java et GLSL
  doivent donner **exactement** le même résultat. Aucun flottant dans la décision allumé/éteint ; les flottants ne
  servent qu'à l'intensité continue, tolérante aux petits écarts.
- Le temps passé au shader est le **même** que celui de `CueClock`, transmis en uniforme (`gtgs_CueTime`, en ticks
  entiers + fraction séparée pour éviter la perte de précision des grands flottants).
- **Identifier la face d'un néon en GLSL** : par le rectangle de sa texture dans l'atlas (UV), passé en uniforme
  au chargement de l'atlas, et la position monde du fragment (varying déjà prévu par `MOTEUR_SHADERS.md`, §6.8).
- **Vecteurs de test communs** : un fichier versionné (`src/test/resources/neon_schedule_vectors.json`) liste des
  entrées et le résultat attendu : position du bloc, graine, tick entier, fraction de tick, état
  (allumé / éteint / vacillant) et intensité attendue avec sa tolérance. **Les deux implémentations sont validées
  contre ce même fichier**, avant de brancher le bourdonnement aux néons. Il fixe aussi ce qui doit correspondre
  exactement : largeur et signe des entiers (`uint` 32 bits côté GLSL, `int` masqué à 32 bits non signé côté Java),
  opérations de hachage (décalages, multiplications modulo 2³², ou exclusif), et représentation du temps (tick entier
  et fraction transmis séparément). Les cas limites y figurent : coordonnées négatives, grands ticks, passage d'un
  tick au suivant.

### 6.9 `Disturbances` : événements dans l'image (P1)

Un cri doit perturber le motif déterministe des néons proches :

- Une **liste courte** de perturbations actives (position, rayon, force, temps de début, identifiant d'enveloppe),
  4 au plus, envoyée en uniforme (même uniform block que les néons).
- La fonction GLSL des néons et la fonction Java appliquent la même règle : dans le rayon, l'intensité suit
  l'enveloppe du cri ; hors du rayon, le motif normal.
- L'enveloppe est passée **par valeur** à chaque image (valeur courante, calculée côté Java à la position de lecture
  réelle), pas sous forme de tableau dans le shader.
- Perturbation terminée à la fin de son enveloppe ; la plus ancienne cède sa place si la liste est pleine.

### 6.10 Réactions visuelles disponibles

| Réaction             | Effet                                                        | Mécanisme (moteur de shaders)                          | Coût        |
| -------------------- | ------------------------------------------------------------ | ------------------------------------------------------ | ----------- |
| `lightmap_flicker`   | Toute la lumière de la vue baisse un instant                 | Courbe de lightmap par vue (`MOTEUR_SHADERS.md` §6.4)   | Nul         |
| `neon_face`          | La face du néon s'assombrit / vacille                        | Étage 1, fonction partagée (§6.8)                      | Quasi nul   |
| `neon_light`         | La lumière projetée par le néon suit son état                | Néons en uniform block (`MOTEUR_SHADERS.md` §6.8)       | Faible      |
| `neon_disturbance`   | Les néons proches d'un cri vacillent au rythme du cri        | `Disturbances` (§6.9) + `neon_face` / `neon_light`      | Faible      |
| `screen_pulse`       | Légère pulsation de vignette au pic du cri                   | Post masqué (`MOTEUR_SHADERS.md` §6.5)                  | Post        |
| `camera_shake`       | Micro-tremblement de caméra (optionnel) ; **jamais quand le joueur est dans une dimension vanilla** (§1.1) ; actif par défaut pour les cris proches, conformément à la vision (`GAMEPLAY.md` §7), plafonné et coupé par le preset Confort (§11) | Hors shader : décalage de caméra, plafonné | Nul |

**Pas de vraie modification de la lumière des blocs** (moteur de lumière de Minecraft) : trop lent, réseau, et
calculé par tick. Tout vacillement est **visuel uniquement**, ce qui garde la lumière de jeu (apparition des mobs)
stable.

---

## 7. Intégration aux portails

> **Exigence absolue, à vérifier à chaque phase** : un son dont la source est dans l'Overworld est **strictement
> vanilla**, même entendu depuis le Level 0 à travers un portail, et un son du Level 0 garde l'acoustique du Level 0
> même entendu depuis l'Overworld. Symétriquement, un néon du Level 0 vu à travers un portail clignote **en même
> temps** que son bourdonnement entendu à travers ce même portail. Règle de périmètre complète au §1.1.

1. **Acoustique de la source, pas du lieu de rejeu** : `FrameSoundPlayer` rejoue un son distant au cadre. Le profil
   et la réverb sont ceux de la **dimension de la source**, évalués **autour de la source**, de l'autre côté.
2. **Trajet par le cadre** : `PortalSound` atténue déjà le volume selon le trajet jusqu'au cadre. L'occlusion
   s'ajoute en deux segments : source → cadre (dans le monde de la source), cadre → auditeur (dans le monde du
   joueur), transposés par la géométrie existante (`PortalFacing`).
   - **Jamais d'acoustique calculée autour du cadre comme s'il était la source** : le cadre n'est que le point de
     rejeu. Les deux trajets (source → cadre, cadre → auditeur) restent distincts dans tout le code, et c'est le
     point qui demande le plus de tests avec les portails imbriqués (§13).
3. **Rayons qui traversent un cadre** (`PortalAcoustics`) : un rayon de réverb ou d'occlusion qui touche l'ouverture
   d'un portail ouvert continue dans l'autre monde, transposé, sur le `WorldSnapshot` de ce monde. Un niveau de
   traversée au plus (comme le rendu), au-delà le cadre compte comme ouvert sur le vide.
4. **Deux acoustiques à la fois** : un son du Level 0 entendu depuis l'Overworld porte la réverb du Level 0 ; un
   son de l'Overworld près du joueur reste sec. Les deux envois auxiliaires (§6.2) servent à ne pas mélanger.
5. **Signaux par vue** : chaque vue de portail reçoit les signaux (`CueClock`, néons, perturbations) de **sa**
   dimension, poussés et retirés comme le `LookState`. Une perturbation du Level 0 ne fait jamais vaciller
   l'Overworld.
6. **Bourdonnement à travers le cadre** : le bourdonnement des néons du Level 0 passe par le portail avec la même
   règle que les autres sons, et suit le même `NeonSchedule` que leur image dans la vue.

---

## 8. Intégration au moteur de shaders

Le moteur audio **n'ajoute aucun pass** : il fournit des valeurs au moteur de shaders.

| Ajout dans `MOTEUR_SHADERS.md`              | Contenu                                                              |
| ------------------------------------------- | -------------------------------------------------------------------- |
| `LookUniforms`                              | `gtgs_CueTime` (ticks entiers + fraction), graine de dimension       |
| Uniform block des néons (§6.8 de ce doc-là) | Intensité par néon issue de `NeonSchedule`, perturbations actives    |
| `look.glsl`                                 | `gtgs_neonState(...)` : fonction partagée (§6.8), reconnaissance de la face du néon |
| Post masqué                                 | `screen_pulse` : force de vignette pilotée par l'enveloppe            |
| Courbe de lightmap par vue                  | `lightmap_flicker`                                                   |

Conséquences sur l'ordre des travaux :

- `neon_face` et `lightmap_flicker` ne dépendent que des phases 1 à 3 du moteur de shaders.
- `neon_light` et `neon_disturbance` dépendent des néons en uniform block (phase 7, optionnelle, du moteur de
  shaders) : pour la liaison son-image, cette phase **devient nécessaire** et doit être avancée.
- `screen_pulse` dépend du post (phase 5 du moteur de shaders).
- Sous Sodium (voir la matrice de `MOTEUR_SHADERS.md`, §14.6), `neon_face` n'atteint pas le terrain : la réaction
  se replie sur `lightmap_flicker`.
- **Avancer la brique, sans bloquer l'audio dessus** : les néons en uniform block sont à avancer dans le planning du
  moteur de shaders, mais les premiers prototypes audio n'en dépendent pas. Le son se valide seul (prototypes A et
  B, phase 1), la liaison son-image se branche ensuite, une fois le chemin des uniformes par vue validé.

---

## 9. Compatibilité

### 9.1 Sound Physics Remastered et mods d'acoustique

Deux moteurs qui posent chacun réverb et filtres sur les mêmes sources OpenAL se marchent dessus (double réverb,
filtres écrasés, slots épuisés). Et Sound Physics Remastered ne connaît pas les portails (§2.1).

| Option                                                  | Résultat                                                                 |
| ------------------------------------------------------- | ------------------------------------------------------------------------ |
| **Bloquer** (`breaks` dans `fabric.mod.json`)           | Recommandé pour le modpack du serveur, que l'on contrôle ; retire aussi son acoustique de l'Overworld, ce qui est voulu (§1.1, point 5) |
| **Céder la place** : notre acoustique désactivée s'il est présent | Ses effets s'appliquent, faux à travers les portails ; la liaison son-image (Cue, néons) continue, elle ne dépend pas d'EFX |
| Cohabiter                                               | Exclu                                                                    |

### 9.2 Autres mods audio

- **Mods qui ajoutent des sons** (pas d'acoustique) : compatibles. Leurs sons passent par notre acoustique dans les
  dimensions profilées.
- **Mods qui modifient le moteur de son** (périphérique, contexte OpenAL, mixage) : à évaluer au cas par cas, en
  particulier ceux qui recréent le contexte (perte des envois auxiliaires négociés, §6.2).
- **Simple Voice Chat** : sa voix ne passe pas par notre acoustique au départ. Intégration éventuelle plus tard.

### 9.3 Matrice de compatibilité

| Configuration                          | Acoustique (réverb, occlusion) | Liaison son-image côté son | Réactions visuelles                        | Portails (son)   | Statut visé              |
| -------------------------------------- | ------------------------------ | -------------------------- | ------------------------------------------ | ---------------- | ------------------------ |
| Vanilla                                | Oui                            | Oui                        | Oui (selon les phases du moteur de shaders) | Oui              | **Cible principale**     |
| Sans EFX (pilote ou OpenAL limité)     | Repli : gain seul              | Oui                        | Oui                                        | Oui, sans réverb | Dégradé propre           |
| Sodium seul                            | Oui                            | Oui                        | `lightmap_flicker` seulement sur le terrain, à vérifier pour le reste | Oui (le son ne dépend pas du rendu) | Toléré |
| Iris, shaderpack actif                 | Oui                            | Oui                        | Non                                        | Oui              | Bloqué (comme pour le rendu) |
| Sound Physics Remastered présent       | Non (cède la place) ou bloqué  | Oui                        | Oui                                        | Faux             | **Bloqué**               |
| Mods qui ajoutent des sons             | Oui                            | Oui                        | Oui                                        | Oui              | Compatible               |

Remarque : le **son** à travers les portails ne dépend pas des vues de portail rendues. Il marche même quand le rendu
des vues est coupé (Sodium, Iris), car il repose sur la synchronisation serveur et `FrameSoundPlayer`.

---

## 10. Performance

### 10.1 Budgets (objectifs à valider sur une petite configuration)

Ce sont des **objectifs, pas des garanties** ; chaque ligne se mesure séparément (H8).

| Élément                                             | Budget visé                                  |
| --------------------------------------------------- | -------------------------------------------- |
| Thread principal, par tick (copie du snapshot comprise) | < 0,5 ms en moyenne                       |
| Évaluation par rayons d'un son (thread de calcul)   | < 1 ms en qualité légère                      |
| Acoustique dérivée de la génération (cellule en cache) | < 0,01 ms                                  |
| Thread du son, application EFX par son              | Négligeable, aucun appel bloquant            |
| Côté image (fonctions partagées, uniformes)         | Inclus dans les budgets de `MOTEUR_SHADERS.md` |
| Enveloppes en mémoire                               | Quelques Kio par son déclaré                 |

### 10.2 Règles

- **Rien de bloquant sur le thread principal ni sur le thread du son** : calcul sur un thread dédié, à file bornée.
- **Partage** : sons proches dans la même zone → même résultat acoustique (`AcousticCache` par cellule ou par
  bloc de 4×4×4).
- **Pas de réévaluation par image** : à l'apparition du son, puis périodiquement pour les sons longs ou mobiles
  seulement.
- **Aucune allocation par son** dans les chemins chauds : tableaux de rayons préalloués, résultats recyclés.
- **Nombre de sources EFX fixe** : slots partagés, filtres recyclés.
- **Qualité** : `désactivé` / `léger` (défaut, désormais choisi par le bouton **Auto** : `QUALITE_ET_PRESETS.md`
  §9.3) / `complet`, alignée sur celle du moteur de shaders. Un palier
  **`minimum`** s'ajoute sous `léger`, aligné sur `MOTEUR_SHADERS.md` §16.2 (§17 ici).

### 10.3 Mesure

- Sections du moniteur existant : `client.audio.snapshot`, `client.audio.eval`, `client.audio.efx`,
  `client.audio.cue`.
- Mesure du **décalage son-image** : enregistrement audio et vidéo simultané (capture d'écran avec le son du jeu,
  par exemple l'outil de stream habituel) d'un néon qui s'éteint et d'un cri, décalage mesuré image par image.
- Banc : salle du Level 0 avec 30 néons visibles, trois monstres qui crient, un portail ouvert vers l'Overworld.
  *Le Level 0 n'ayant pas d'entités (`GAMEPLAY.md` §6), ce banc se joue dans un **niveau de test réservé au
  développement** (même génération et mêmes néons que le Level 0, avec trois entités qui crient), ou dans un niveau
  habité. Les mesures « néons seuls » peuvent rester dans le Level 0.*
- **Mesurer brique par brique, puis l'ensemble** : d'abord chaque brique isolée (snapshot, évaluation, EFX, `Cue`),
  puis l'ensemble dans une scène avec plusieurs portails **et** le moteur de shaders actif (`MOTEUR_SHADERS.md`,
  §9.3). La première version ne vise pas tous les objectifs de qualité : la qualité « léger » tenue d'abord.

---

## 11. Accessibilité et confort

Des néons qui clignotent sont un **risque pour les joueurs photosensibles**. C'est aussi un point d'attention pour
le stream.

- **Plafond de clignotement** (`FlashLimiter`) : jamais plus de **3 variations fortes de luminosité par seconde**
  sur une grande partie de l'écran (repère issu des recommandations d'accessibilité sur les flashs) ; au-delà, les
  variations sont lissées. Appliqué **côté Java avant l'envoi des uniformes**, donc valable pour toutes les réactions.
- **Le plafond porte sur l'effet visuel final, pas sur chaque effet séparément.** Trois monstres qui crient en même
  temps et quatre néons qui clignotent indépendamment peuvent chacun respecter leur limite tout en produisant un
  résultat global bien trop agressif. Le limiteur travaille donc en trois niveaux :
  1. **par construction** : `NeonSchedule` borne le nombre de transitions par néon et par seconde, et décorrèle les
     néons voisins pour qu'ils ne basculent pas tous ensemble ;
  2. **en cumul, par vue** : la somme des perturbations actives, du `lightmap_flicker` et du `screen_pulse` est
     ramenée à un **budget de variation unique par vue**, calculé à chaque image ; au-delà, toutes les contributions
     sont réduites ensemble ;
  3. **sur tout l'écran** : le monde du joueur et les vues de portail (y compris une vue vers les Backrooms vue
     depuis l'Overworld) partagent le même plafond, pondéré par la surface d'écran de chaque vue.
  Le limiteur ne touche **que l'image** : le son n'est jamais modifié pour respecter le plafond.
- **Option « Réduire les flashs »** : vacillements remplacés par des baisses douces, `screen_pulse` et
  `camera_shake` coupés. Le son reste identique.
- **Sous-titres** : les sons du mod ont des sous-titres vanilla (option d'accessibilité de Minecraft), formulés de
  façon neutre pour ne pas casser l'immersion (« Bourdonnement électrique », « Cri lointain »).
- **Camera shake** désactivé par défaut, ou très faible, et coupé par l'option vanilla de réduction des mouvements de
  caméra si elle s'applique. **Précision** : la vision de jeu veut que les cris fassent trembler l'écran
  (`GAMEPLAY.md` §7). Le choix retenu est donc **actif par défaut, mais très faible et plafonné**, coupé par le preset
  **Confort** (`QUALITE_ET_PRESETS.md` §7) et par l'option vanilla de réduction des mouvements.

---

## 12. Robustesse

- **Coupe-circuit** comme pour le rendu : une exception dans le calcul acoustique ou une erreur OpenAL → acoustique
  coupée pour la session, sons joués vanilla, liaison son-image conservée.
- **Changement de périphérique audio** (casque branché, moteur de son rechargé) : objets EFX recréés, envois
  renégociés, rien ne fuit.
- **Thread du son** : jamais d'appel OpenAL ailleurs ; vérification en dev (assertion sur le thread courant).
- **Snapshot périmé** : un résultat calculé sur une copie trop ancienne (joueur téléporté, portail traversé) est
  jeté, pas appliqué.
- **Fonctions partagées** : en cas d'écart détecté en dev entre Java et GLSL (§13), le GLSL suit la valeur passée
  par uniforme (repli plus cher mais juste).
- **Ressources** : un `Cue` ou un profil invalide est refusé au chargement, journalisé, et le reste se charge.

---

## 13. Tests

| Niveau        | Quoi                                                                                                       |
| ------------- | ---------------------------------------------------------------------------------------------------------- |
| Unitaires     | `NeonScheduleTest` : déterminisme (même entrée = même sortie), distribution des pannes, fréquence plafonnée |
| Unitaires     | **Équivalence Java / GLSL** de `NeonSchedule` : la version GLSL compilée en CI (`glslangValidator`) et exécutée sur un jeu de positions et de temps, comparée bit à bit à Java pour la décision allumé/éteint (exécution GLSL par un shader de test dans le client de dev si aucun exécuteur hors GPU n'est retenu, H7) ; Java et GLSL validés contre les **mêmes vecteurs de test** (§6.8) |
| Intégration   | **Premier jalon commun** et **test décisif** de `MOTEUR_SHADERS.md`, §12.1 : néon du Level 0 vu et entendu par un portail depuis l'Overworld, synchrone, reste de l'Overworld vanilla ; cri du Level 0 entendu à travers un portail lui-même vu à travers un autre (son, réverb, néons perturbés et post dans la bonne dimension) ; le cri vient d'un niveau habité ou du niveau de test de développement, le Level 0 n'ayant pas d'entités (`MOTEUR_SHADERS.md` §12.1) |
| Manuel        | **Sons d'hallucination** (§1.1, point 6) : entendus par le seul joueur qui hallucine, jamais par un autre joueur, jamais à travers un portail, jamais dans une dimension vanilla ; réactions visuelles limitées à ce joueur ; aucun monstre ne réagit au son lui-même |
| Unitaires     | `EnvelopeBakerTest` : enveloppe d'un son de test connu (silence, impulsion, rampe), fenêtres, normalisation |
| Unitaires     | `CueRegistryTest`, `AudioProfileLoaderTest` : formats, refus des types inconnus, dimension sans profil      |
| Unitaires     | `GeneratedAcousticsTest` : cellules connues du Level 0 → paramètres attendus ; golden comme `LevelZeroGoldenTest` |
| Unitaires     | `AcousticEvaluatorTest` : salle fermée, couloir, mur entre source et auditeur, sur des snapshots construits à la main |
| Unitaires     | `PortalAcousticsTest` : rayon transposé à travers un cadre, deux segments d'occlusion, un seul niveau de traversée |
| Unitaires     | `FlashLimiterTest` : aucune séquence de sortie ne dépasse le plafond, quelle que soit l'entrée              |
| Unitaires     | `FlashLimiterTest` en **cumul** : trois cris simultanés, néons voisins, lightmap et vignette ensemble, plusieurs vues dont une vue vers les Backrooms depuis l'Overworld ; le plafond tient sur le résultat final, le son n'est pas modifié |
| Unitaires     | Dimension d'émission (§1.1) : chaque chemin d'un son (monde du joueur, relais d'un monde distant, traversée prédite, dimension inconnue) donne la bonne dimension ; dimension inconnue = vanilla |
| En jeu        | `runGameTest` : sons d'un `Cue` relayés à travers un portail (extension de `PortalPropagationGameTests`)    |
| Manuel        | **Exigence absolue du §7** : sons de l'Overworld vanilla vus d'un côté comme de l'autre ; néon du Level 0 vu et entendu à travers un portail, synchrones |
| Manuel        | Décalage son-image mesuré par capture (§10.3), cris et néons                                               |
| Manuel        | **Périmètre (§1.1)** : chaque ligne du tableau vérifiée, dont le monstre passé dans l'Overworld qui crie (son vanilla, aucune réaction) et le cri entendu depuis l'Overworld par un portail (acoustique du Level 0, réaction limitée à la vue, pas de tremblement) |
| Manuel        | Overworld avec le mod installé, loin de tout portail : comparaison à l'oreille et par enregistrement avec un client vanilla |
| Manuel        | Changement de périphérique audio pendant la partie, F3+T, option « Réduire les flashs »                    |
| Manuel        | Cycle de vie EFX : démarrage, changement de périphérique, rechargement du moteur de son, absence d'EFX, envois obtenus inférieurs à ceux demandés ; aucune fuite, aucun son vanilla touché, repli propre |
| Manuel        | Portails **imbriqués** : son du Level 0 entendu à travers un portail vu à travers un autre ; acoustique de la source, deux trajets distincts, réaction limitée à la bonne vue |
| Manuel        | Plafond de clignotement mesuré sur capture : luminance moyenne de l'écran image par image, comptage des variations fortes par seconde, scène de cumul (§11) |
| Manuel        | Matrice de compatibilité (§9.3) : chaque configuration lancée                                               |

La logique (fonctions déterministes, enveloppes, acoustique, limiteur) est **séparée d'OpenAL et du GL** pour rester
couverte.

---

## 14. Plan par phases

Même principe que pour le moteur de shaders : **une seule couche nouvelle à la fois**, chaque phase prouvée avant la
suivante. On commence par la liaison son-image la plus simple, qui donne le plus d'effet, avant l'acoustique.

| Phase | Contenu                                                                                     | Livrable vérifiable                                            |
| ----- | ------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| 0     | **Reconnaissance** : chemin d'un son dans Minecraft 1.21.1 (demande, file, thread du son, source OpenAL), création du contexte et extensions disponibles, lecture de `AL_SEC_OFFSET`, trajet des sons distants (`FrameSoundPlayer`, `PortalSound`), sons du Level 0 existants ; **prototypes A et B** (§14.1) | Hypothèses H1 à H9 (§16) statuées ou planifiées ; EFX minimal et périmètre vanilla prouvés              |
| 1     | **Néons déterministes, côté son** : `CueClock`, `NeonSchedule` en Java, bourdonnement coupé et clics aux transitions, `FlashLimiter` | Néons qui grésillent et claquent de façon identique pour deux clients |
| 2     | **Néons, côté image** : `NeonSchedule` en GLSL, `neon_face` et `lightmap_flicker` (dépend des phases 1 à 3 du moteur de shaders) | Face du néon et son bourdonnement synchrones ; équivalence Java / GLSL testée |
| 3     | **Cris** : `CueRegistry`, `EnvelopeBaker`, `PlaybackTracker`, `Disturbances`, `neon_disturbance` | Néons proches vacillant au rythme du cri, décalage mesuré      |
| 4     | **Portails, signaux** : signaux par vue, bourdonnement à travers le cadre                    | Exigence absolue du §7 tenue pour les signaux                  |
| 5     | **Acoustique de base** : `AudioProfile`, `EfxBackend`, acoustique dérivée de la génération (§6.4) | Réverb du Level 0 stable ; Overworld vanilla ; repli sans EFX  |
| 6     | **Acoustique par rayons** : `WorldSnapshot`, `AcousticEvaluator`, cellules modifiées, débogage visuel des rayons | Budgets §10.1 tenus ; démarrage des sons sans attente          |
| 7     | **Acoustique à travers les portails** : `PortalAcoustics`, deux envois                       | Exigence absolue du §7 tenue pour l'acoustique                 |
| 8     | Qualité, `AudioCompat`, coupe-circuit, options de confort complètes ; optionnel : `screen_pulse`, `camera_shake`, absorption de l'air, direction perçue (`camera_shake` n'est optionnel que dans son calendrier : il est voulu par la vision, §11) | Matrice §9.3 sans cellule « à vérifier » |

Les phases 1 à 4 donnent la **liaison son-image** sans aucune acoustique ; les phases 5 à 7 donnent l'**acoustique**.
Les deux blocs sont indépendants : on peut livrer le premier seul.

### 14.1 Prototypes indépendants, avant et pendant les phases

Le moteur audio **n'est pas un bloc à livrer d'un coup**. La chaîne sonore, la chaîne visuelle et leur
synchronisation se prouvent séparément, par des prototypes courts et jetables, avant d'être réunies. Ils servent à
statuer les hypothèses (§16), pas à livrer des fonctionnalités.

| Prototype | Contenu                                                                                                  | Hypothèses | Avant la phase |
| --------- | -------------------------------------------------------------------------------------------------------- | ---------- | -------------- |
| **A — EFX minimal** | Une source OpenAL reçoit une réverb et un filtre ; contexte et envois réellement obtenus relus ; repli sans EFX ; changement de périphérique | H2, H3 | 5 (fait en phase 0) |
| **B — Périmètre vanilla** | Mêmes sons enregistrés en Backrooms et dans l'Overworld ; sons vanilla inchangés, dont un mob des Backrooms passé dans l'Overworld | H1 | 1 (fait en phase 0) |
| **C — Un néon synchronisé** | Un seul néon : calendrier déterministe partagé par le son et l'image, décalage mesuré ; puis un cri court et son enveloppe | H6, H7 | 2 et 3 |
| **D — Portail simple** | Un son du Level 0 entendu depuis l'Overworld par un portail, puis l'inverse ; trajet, acoustique de la source et réaction visuelle validés **séparément** | H1, H6 | 4 et 7 |
| **E — Charge et compatibilité** | Plusieurs sources, sons longs, chunks modifiés, changement de périphérique, sans EFX, mods qui modifient le moteur audio | H4, H5, H8 | 6 et 8 |

Ordre recommandé : **A et B d'abord**, puis C. Ni l'acoustique complète ni les portails avant que l'EFX minimal et
la synchronisation d'un seul néon soient prouvés.

### 14.2 Ordre commun avec le moteur de shaders, et premier jalon

Les deux moteurs ne se terminent pas séparément avant d'être réunis : chacun valide ses fondations, puis un petit
test d'intégration se fait **tôt**. L'ordre commun en cinq étapes, le **premier jalon commun** (un néon du Level 0
vu à travers un portail, clignotement et bourdonnement synchrones, reste de l'Overworld strictement vanilla) et le
**test décisif** (un cri à travers un portail vu à travers un autre) sont définis une seule fois, dans
`MOTEUR_SHADERS.md`, §12.1, pour que les deux documents ne divergent pas. Côté audio, ces étapes correspondent :

| Étape commune | Ici                                                            |
| ------------- | -------------------------------------------------------------- |
| 1             | Phase 0, prototypes A et B                                     |
| 2             | Une réverb simple dans les Backrooms (prototype A étendu), Overworld prouvé inchangé (prototype B) |
| 3             | Prototype D (portail simple), avant toute perturbation complexe |
| 4             | Phases 1 à 3 : un néon déterministe, puis un cri à enveloppe   |
| 5             | Prototype E, phase 8                                           |

---

## 15. Checklist des points d'attention

- [ ] Aucun code de Sound Physics Remastered copié (GPL-3.0) ; aucune mention d'autre projet dans le code, le JAR
  ni la documentation publiée (§3, règles 5 et 6)
- [ ] Ni dépendance ni appel à son code
- [ ] **Exigence absolue** : sons de l'Overworld vanilla, y compris entendus à travers un portail (§7)
- [ ] Néon du Level 0 vu et entendu à travers un portail : synchrones (§7)
- [ ] **Périmètre** : traitement selon la dimension d'émission ; `Cue` ignoré pour un son émis dans une dimension
  vanilla ; réactions d'un son entendu par un portail limitées à la vue ; pas de `camera_shake` dans une dimension
  vanilla ; mises en place globales sans effet audible hors des Backrooms (§1.1)
- [ ] Une seule source de vérité : ni le son ni l'image ne commandent l'autre (§5.2)
- [ ] `NeonSchedule` identique en Java et en GLSL, décision allumé/éteint en entiers, testée bit à bit
- [ ] Temps transmis au shader sans perte de précision (ticks entiers + fraction)
- [ ] Enveloppes lues à la position de lecture réelle, pas au temps de la demande
- [ ] Tous les appels OpenAL sur le thread du son
- [ ] Aucun calcul acoustique bloquant sur le thread principal ni sur le thread du son
- [ ] Monde lu hors thread seulement via `WorldSnapshot`, résultat périmé jeté
- [ ] Un son démarre sans attendre son évaluation
- [ ] Envois auxiliaires négociés à la création du contexte, repli sans EFX
- [ ] Objets EFX recréés au changement de périphérique audio, sans fuite
- [ ] Vacillements visuels seulement : lumière de jeu des blocs inchangée
- [ ] **Plafond de clignotement** appliqué à toutes les réactions ; option « Réduire les flashs »
- [ ] Plafond appliqué à l'**effet visuel final** (par construction, en cumul par vue, sur tout l'écran), sans
  modifier le son (§11)
- [ ] Dimension d'émission résolue au moment de la demande ; dimension inconnue = son vanilla (§1.1)
- [ ] Envois réellement obtenus relus à chaque création de contexte ; seules les sources marquées sont touchées (§6.2)
- [ ] Décalage son-image non promis avant mesure ; sons courts non diffusés en flux d'abord (§6.7)
- [ ] Prototypes A à E menés séparément avant de réunir les chaînes (§14.1)
- [ ] Java et GLSL de `NeonSchedule` validés contre les mêmes vecteurs de test versionnés (§6.8)
- [ ] Premier jalon commun atteint, test décisif à deux portails tenu (`MOTEUR_SHADERS.md`, §12.1 ; ici §14.2)
- [ ] Budgets mesurés **par rapport au vanilla sur la machine de référence** (PC de bureau, GPU intégré) (§17)
- [ ] Priorisation des sons : évaluation fraîche pour les sons proches et importants, cache de zone pour les autres
  (§17.2)
- [ ] Palier `minimum` : ambiance sonore et liaison son-image des néons conservées (§17.1)
- [ ] Sous-titres neutres pour tous les sons du mod
- [ ] Sons d'hallucination : un seul joueur, Backrooms seulement, ni relayés ni entendus par les monstres (§1.1, point 6)
- [ ] `camera_shake` actif par défaut mais plafonné, coupé par Confort et la réduction des mouvements (§11)
- [ ] Bancs et tests avec des cris joués dans un niveau habité ou le niveau de test, pas dans le Level 0 (§10.3)
- [ ] Décision prise sur Sound Physics Remastered, déclarée dans `fabric.mod.json`
- [ ] Budgets mesurés sur petite configuration, décalage son-image mesuré par capture
- [ ] Logique séparée d'OpenAL et du GL, couverte par les tests
- [ ] Hypothèses du §16 toutes statuées avant de considérer le document comme spécification

---

## 16. Hypothèses à valider avant de figer l'architecture

| #  | Hypothèse                                                                                     | Validation                                                       | Phase | Si elle tombe                                                                 |
| -- | --------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- | ----- | ----------------------------------------------------------------------------- |
| H1 | On peut identifier de façon fiable, côté client, qu'un son joué est un `Cue`, avec sa position et sa dimension, y compris pour un son relayé par un portail | Lecture du chemin d'un son (demande vanilla, relais `portal_remote_entity`, `FrameSoundPlayer`) | 0 | Paquet dédié du serveur pour les `Cue` d'événement |
| H2 | `ALC_EXT_EFX` est disponible sur les plateformes visées, et l'on peut demander des envois auxiliaires à la création du contexte de Minecraft | Prototype sur Windows, Linux, macOS ; lecture des attributs du contexte créé | 0 | Un seul envoi (une réverb), ou acoustique par gain et filtre seuls |
| H3 | Les paramètres EFX peuvent être appliqués aux sources de Minecraft depuis son thread du son sans conflit avec sa gestion des sources | Prototype : une réverb fixe sur tous les sons du Level 0 | 0, 5 | Appliquer à la création de la source seulement, sans mise à jour en cours de lecture |
| H4 | La copie d'un `WorldSnapshot` tient dans le budget du thread principal                          | Mesure de la copie à plusieurs rayons de chunks                   | 6     | Rayons limités aux cellules modifiées, acoustique générée partout ailleurs     |
| H5 | Un son qui démarre avec l'acoustique en cache puis reçoit le résultat frais ne fait pas de saut audible | Écoute à l'aveugle, sons courts et longs                         | 6     | Lissage des paramètres EFX sur quelques centaines de ms                       |
| H6 | `AL_SEC_OFFSET` donne une position de lecture assez précise et assez fraîche pour synchroniser l'image, au moins pour les sons non diffusés en flux | Prototype : un clic périodique et un flash, capture audio-vidéo   | 0, 3  | Synchroniser sur le temps de démarrage estimé plus une latence mesurée ; `Cue` à enveloppe réservés aux sons courts |
| H7 | `NeonSchedule` peut être exactement identique en Java et en GLSL 1.50 (hachage entier), et l'équivalence peut être testée | Implémentation des deux, comparaison sur un grand jeu d'entrées   | 2     | Le GLSL lit l'état des néons proches passé par uniforme (plus cher, borné en nombre) |
| H8 | Les budgets du §10.1 tiennent avec 30 néons, trois cris et un portail                           | Banc du §10.3 sur petite configuration                           | 1 à 8 | Qualité « léger » : moins de rayons, acoustique générée seule, moins de perturbations |
| H9 | La forme du Level 0 connue par la génération suffit à une réverb crédible (§6.4)                | Comparaison à l'oreille avec les rayons dans les mêmes salles    | 5     | Rayons partout, avec cache par cellule                                        |
| H10 | Le palier `minimum` et la priorisation des sons tiennent les budgets relatifs du §17 sur la machine de référence, sans perte audible de l'ambiance | Banc du §17.3, en vanilla puis avec le mod, sur la machine de référence | 0, puis 5 à 8 | Acoustique générée seule, moins de sons évalués, réverb partagée par zone |

Tant qu'une hypothèse n'est pas statuée, la partie du document qui en dépend reste une **proposition**.

---

## 17. Cible matérielle, paliers et LOD audio

La cible est la même que celle du moteur de shaders : **PC de bureau à GPU intégré**, machine de référence et
principe des **budgets relatifs au vanilla** définis une seule fois dans `MOTEUR_SHADERS.md`, §16.1. Sur ces
machines, le goulot audio est le **processeur** : peu de cœurs, partagés avec la construction des chunks de
Minecraft. Calculer hors du thread principal n'est pas gratuit.

### 17.1 Paliers

Ce qu'un palier coupe n'est jamais l'identité sonore des Backrooms : le bourdonnement des néons, la liaison
son-image déterministe (`NeonSchedule`, coût quasi nul) et l'acoustique dérivée de la génération (une lecture de
cache) **restent actifs partout**.

| Palier       | Acoustique                                                                          | Liaison son-image                                                  |
| ------------ | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `désactivé`  | Aucune (sons vanilla, même dans les Backrooms)                                     | Néons déterministes seulement                                      |
| `minimum`    | Acoustique générée seule ; **pas de rayons**, cellules modifiées en acoustique de zone voisine ; occlusion en un seul chemin ; un seul envoi auxiliaire | Néons déterministes ; cris à enveloppe limités à 2 perturbations ; pas de `screen_pulse` ni `camera_shake` |
| `léger`      | + rayons pour les cellules modifiées (16 rayons, 2 rebonds), deux envois            | + jusqu'à 4 perturbations, `screen_pulse` selon le palier visuel   |
| `complet`    | + rayons 32 / 4 rebonds, occlusion multi-chemins, absorption de l'air               | Toutes les réactions                                               |

Dans le menu du mod, ces paliers suivent les noms **Low**, **Medium**, **High** et **Ultra** ; les **presets
sonores** (casque HRTF, haut-parleurs, immersif, streamer, nuit, accessibilité) sont décrits dans
`QUALITE_ET_PRESETS.md`, §8.

### 17.2 LOD audio : priorisation des sons

Équivalent sonore du LOD des vues (`MOTEUR_SHADERS.md`, §17) :

- **Priorité** d'un son = importance déclarée (un cri passe avant un pas) × proximité × audibilité (volume après
  atténuation et occlusion connue).
- Les sons **prioritaires** reçoivent une évaluation acoustique fraîche ; les autres prennent **l'acoustique en
  cache de leur zone** (cellule du Level 0 ou bloc de 4×4×4), sans calcul.
- **Budget d'évaluations par tick** fixé par palier ; au-delà, les sons les moins prioritaires attendent ou
  partagent un résultat. Jamais d'attente au démarrage d'un son (§6.3).
- Les sons **lointains et nombreux** (ambiance) n'ont jamais d'évaluation propre.
- Un son entendu à travers un portail dont la vue est en cadre sombre (LOD 4) garde son acoustique : le LOD visuel
  ne dégrade jamais le son.

### 17.3 Budgets relatifs et banc

| Situation, palier `minimum`, machine de référence        | Objectif (à valider)                                                    |
| -------------------------------------------------------- | ----------------------------------------------------------------------- |
| Overworld, aucun son des Backrooms                       | Surcoût **non mesurable** par rapport au vanilla (§1.1)                  |
| Level 0, ambiance (néons, pas de monstres)               | Thread principal : au plus **0,2 ms** par tick au-dessus du vanilla     |
| Pire cas raisonnable (30 néons, trois cris, un portail)  | Thread principal au plus **0,5 ms** par tick (§10.1) ; aucun à-coup de construction de chunks attribuable au calcul acoustique |
| Décalage son-image                                        | Mêmes objectifs que le §6.7, mesurés sur la machine de référence        |

Banc : celui du §10.3, joué **en vanilla puis avec le mod**, sur la machine de référence, dans le modpack réel.

---

## Références

- Sound Physics Remastered (référence de lecture, GPL-3.0) : https://github.com/henkelmax/sound-physics-remastered
- Moteur de shaders du mod : `MOTEUR_SHADERS.md`
- Documentation des portails du mod : `docs/PORTALS.md`

---

## Historique des révisions

- **v1** : conception initiale, construite avec la démarche de `MOTEUR_SHADERS.md` v4 (statut de feuille de route,
  invariants explicites, exigence absolue, budgets présentés comme objectifs, matrice de compatibilité effet par
  effet, hypothèses avec plan B, phases d'une seule couche à la fois).
- **v2** : périmètre explicite « Backrooms seulement » (§1.1) : traitement selon la dimension d'émission, sons
  entendus à travers un portail, monstre passé dans l'Overworld, réactions limitées à la vue, mises en place globales
  neutres, conséquence du blocage de Sound Physics Remastered ; reporté au §6.5, §6.10, §7, §9.1, §13 et §15.
- **v3** : intégration d'une revue externe : détermination de la dimension d'émission réelle, dimension inconnue
  traitée comme vanilla (§1.1) ; robustesse du contexte EFX et sources marquées (§6.2) ; décalage son-image non promis
  avant mesure, sons courts d'abord (§6.7) ; deux trajets distincts au cadre (§7) ; brique des néons à avancer sans
  bloquer l'audio (§8) ; plafond de clignotement sur l'effet visuel final, en cumul (§11) ; prototypes A à E (§14.1) ;
  tests et checklist complétés (§13, §15).
- **v4** : vecteurs de test communs Java / GLSL pour `NeonSchedule` (§6.8), mesure brique par brique puis d'ensemble
  (§10.3), ordre commun et premier jalon partagés avec `MOTEUR_SHADERS.md` §12.1 (§14.2) ; tests et checklist
  complétés (§13, §15).
- **v5** : cible matérielle et budgets relatifs partagés avec `MOTEUR_SHADERS.md` §16, paliers audio dont `minimum`
  (§10.2, §17.1), LOD audio par priorisation des sons (§17.2), budgets relatifs (§17.3), hypothèse H10 (§16),
  checklist complétée (§15).
- **v6** : renvoi vers `QUALITE_ET_PRESETS.md` pour les presets sonores et les noms de paliers (§17.1).
- **v7** : §3 : aucune mention d'autre projet dans le code, le JAR ni la documentation publiée (remplace « tracer
  l'inspiration ») ; mod entièrement propriétaire ; checklist alignée.
- **v8** : passe de cohérence entre les quatre documents : sons d'hallucination (§1.1, point 6) ; `Cue` `scream` du
  Level 0 réservé aux hallucinations (§6.1) ; `camera_shake` actif par défaut, plafonné, coupé par Confort (§6.10,
  §11, §14) ; banc et test d'intégration joués dans un niveau habité ou le niveau de test (§10.3, §13) ; palier par
  défaut choisi par Auto (§10.2) ; tests et checklist complétés ; mention « document interne ».
