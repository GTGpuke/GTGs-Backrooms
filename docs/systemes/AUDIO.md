# Audio

Le moteur audio du mod donne à chaque dimension son acoustique : réverbération, occlusion par les blocs et
bourdonnement des néons. Il ne remplace aucun son. Il branche ceux que joue le moteur vanilla sur la réverb et le filtre
de leur dimension, par l'extension EFX d'OpenAL. Le code est dans `core/audio/`, la forme générée du Level 0 dans
`backrooms/level0/`.

## En jeu

Une dimension qui a un profil audio a sa propre acoustique. Aujourd'hui, seul le Level 0 a un profil. Le traitement d'un
son dépend de la dimension où il est émis, pas de celle où il est écouté.

- Dans le Level 0, chaque son reçoit la réverb du Level 0 : blocs, entités, pas du joueur, sons venus du serveur. La
  réverb suit l'espace autour de la source. Un placard sonne court, une grande salle long, un espace ouvert peu.
- Les blocs entre un son et la caméra l'étouffent. Les graves passent, les aigus sont coupés d'abord.
- Les quatre tubes de néon les plus proches, à moins de douze blocs, bourdonnent. Un tube qui s'éteint ou se rallume
  claque.
- Un son du Level 0 entendu depuis l'Overworld à travers un portail garde l'acoustique du Level 0. Un son de l'Overworld
  reste strictement vanilla, même entendu depuis le Level 0.
- Sous l'eau, le son s'assourdit davantage, comme derrière un mur de plus.
- Un cri lié à l'image fait vaciller les néons proches, pulser la vignette et baisser la lumière de sa dimension.
- Les sons sans place dans le monde, comme l'interface et la musique, ne sont jamais touchés.

Les fichiers son définitifs ne sont pas encore là. Le bourdonnement `gtgs_backrooms:block.neon.hum` reprend l'ambiance
du phare vanilla, et les claquements le levier. Le cri `gtgs_backrooms:entity.howler.scream` reprend celui de
l'enderman et se joue par `/playsound`. Pour qu'il passe les portails, il faut un son du monde, par exemple un bloc
musical sous une tête qui porte ce son.

Les profils se rechargent avec les ressources (F3+T). Le rechargement relance aussi le moteur de son vanilla. Tous les
sons s'arrêtent, et les tubes reprennent leur bourdonnement à la revue suivante, dix ticks au plus.

## Fonctionnement

### Profil audio

Un profil est un fichier `assets/gtgs_backrooms/audio_profiles/<nom>.json`, lié au profil de look par la dimension. Il
se lit en `AudioProfile`, une simple donnée validée au chargement, rangée par dimension dans `AudioProfiles`.

| Champ | Rôle | Plage |
|-------|------|-------|
| `dimension` | Dimension du profil, obligatoire | Identifiant |
| `acoustics` | Calcul de l'acoustique : `none`, `raycast` ou `generated` | `none` par défaut |
| `reverb_base` | `decay`, `density`, `diffusion`, `gain`, `brightness` | Durée de 0,1 à 20 s, le reste de 0 à 1 |
| `occlusion` | Part retirée par un bloc plein (`wall`) ou partiel (`partial_block`), plafond (`max`) | 0 à 1, plafond sous 1 |
| `air_absorption` | Absorption des aigus par la distance | 0 à 1 |
| `underwater` | Part du gain (`gain`) et des aigus (`gain_hf`) gardée sous l'eau | 0 à 1 |
| `perceived_direction` | Part dont un son bouché se déplace vers son détour | 0 à 1 |
| `ambience` | `hum`, `hum_volume`, `click_off`, `click_on` | Volume de 0 à 1 |

Le mode `none` garde la réverb de base partout et un seul rayon d'occlusion. Le mode `raycast` calcule l'espace par
rayons, hors du thread de rendu. Le mode `generated`, celui du Level 0, lit la forme des salles dans la génération et
garde les rayons là où le monde s'en écarte.

### Dimension d'émission

`EmissionDimension.resolve` donne la dimension où un son est émis. Un son rejoué au cadre d'un portail garde celle de sa
vraie source, que porte son ancre (`SoundAnchors`). Un autre son prend la dimension du monde du joueur au moment où il
part. Un son sans place dans un monde, ou de dimension inconnue, reste vanilla.

### Occlusion

L'occlusion se mesure par un rayon droit à travers les blocs, un pas par face franchie, 64 pas au plus
(`OcclusionTrace`). Le bloc de la source et celui de l'auditeur ne comptent pas. Un bloc est plein s'il est un cube
opaque, partiel s'il a une forme de collision, et de l'air sinon. L'occlusion règle le gain et les aigus d'un filtre
passe-bas, sans dépasser le plafond du profil.

Quand la caméra ou la source est sous l'eau, le milieu du profil s'ajoute à l'occlusion. En qualité complète, le calcul
cherche aussi quatre détours, à deux blocs de la source sur ses côtés horizontaux. Si le chemin droit est bouché et qu'un
détour passe mieux, la direction perçue déplace la source vers le point de passage, à la même distance de l'auditeur. Le
son semble alors venir de la porte, pas du mur. Le profil du Level 0 laisse cette part à zéro.

### Acoustique d'un espace

Chaque dimension a huit réglages de réverb, tirés de la réverb de base (`ReverbPresets`). Ils combinent quatre tailles
de salle et deux ouvertures. La taille vient du libre parcours moyen d'un son avant un mur, avec des seuils à 4, 8 et
16 blocs. L'espace est ouvert quand au moins 35 % des rayons sortent sans mur. Le réglage de base est une petite salle
fermée, avec la réverb du profil telle quelle.

Par rayons, `AcousticEvaluator` lance des rayons autour de la source sur une copie du monde, selon une sphère de
Fibonacci. Chaque rayon rebondit par réflexion sur la face qu'il franchit et s'arrête à 48 blocs. Un rayon qui passe le
cadre d'un portail ouvert continue de l'autre côté. La qualité légère lance seize rayons à deux rebonds, la complète
trente-deux à quatre rebonds.

Le Level 0 tire son acoustique de sa génération. À la connexion, le serveur envoie au client la graine du layout de chaque
Level 0 (`LevelZeroLayoutSync`), car le client n'a pas la graine du monde. `LevelZeroRoomShape` lit alors la salle autour
d'une source, faite des colonnes ouvertes atteignables à seize blocs au plus. Le libre parcours moyen vaut quatre fois
le volume sur la surface, et l'ouverture la part du pourtour qui ne se ferme pas. Si la copie du monde contredit la
génération à moins de quatre blocs de la source, les rayons prennent le relais.

Le résultat se garde par cellule de quatre blocs, cent ticks avant d'être recalculé (`AcousticCache`). Un son démarre
sans attendre, avec la réverb déjà connue de sa cellule, ou celle de base, et un rayon droit d'occlusion. Le calcul lui
arrive au tick suivant, puis toutes les dix ticks tant qu'il joue.

### Copie du monde et threads

La copie du monde se fait sur le thread de rendu, seul endroit où le monde client se lit (`SolidityIndex`). Elle avance
de deux sections par tick, autour de la caméra puis des arrivées des portails visibles. Chaque état de bloc distinct
n'est classé qu'une fois. Un bloc changé marque sa section à recopier (`MixinClientWorld`), et les colonnes déchargées
comme les sections lointaines sont oubliées.

Une section copiée se pose entière et ne change plus. Le calcul tourne sur son propre thread, `GTG's Backrooms acoustics`,
et lit une fenêtre de sept sections de côté sans verrou. Ses files sont bornées et ses objets recyclés. Une demande est
abandonnée si la file est pleine, et la revue suivante la refait. Les appels OpenAL par source passent tous par le
thread du son.

### EFX et modes

Le contexte OpenAL est créé avec deux envois auxiliaires par source (`MixinSoundEngine`). L'envoi 0 mène au réglage de
réverb de la dimension d'émission. L'envoi 1 mène à la réverb de la salle de l'auditeur, pour un son entré par un cadre.
Chaque source reçoit aussi un filtre passe-bas, tiré d'une réserve fixe de 48 filtres, et l'absorption de l'air.

Pour chaque dimension, un slot et un effet par réglage se créent à la première source qui en a besoin. Après chaque
rechargement, ils sont aussi créés d'avance sur le thread du son, et le journal en donne le nombre. Le premier son près
d'un cadre ne paie donc pas leur création.

Le moteur a trois modes, et chaque bascule écrit une ligne dans le journal.

- EFX, le mode normal.
- Volume seul, quand le pilote n'a pas EFX ou que l'option le coupe. L'occlusion et le milieu passent par le volume de
  la source (`AdjustedSource`), sans réverb.
- Coupé pour la session, après une erreur OpenAL ou si un autre moteur acoustique travaille dans le contexte. Les sons
  restent alors vanilla.

Vanilla ne crée aucun objet EFX. Un filtre, un effet ou un slot qui n'est pas au mod trahit donc un autre moteur, et le
mod lui cède la main. L'option de confort qui réduit les graves remplace le passe-bas par un passe-bande, qui retire
environ 6 dB de graves aux sons des dimensions profilées.

### Sons à travers le cadre

Un son rejoué au cadre d'un portail par `FrameSoundPlayer` est, pour vanilla, un son du monde du joueur. Son ancre dit
le monde de sa vraie source, et c'est ce profil qui s'applique. Le volume est déjà celui du trajet jusqu'au cadre
(`PortalSound`), décrit dans [PORTAILS.md](PORTAILS.md).

L'occlusion se mesure en deux segments. Le premier va de la vraie source au point d'entrée du cadre, dans le monde
distant que le client tient pour la vue. Le second va du cadre à la caméra, dans le monde du joueur.

Si l'auditeur est dans une autre dimension profilée, la réverb de sa salle s'ajoute par l'envoi 1, le cadre servant de
seconde source. Un cadre qui mène dans la même dimension n'ajoute pas de seconde réverb (`EmissionDimension.room`).

Un son peut passer deux cadres. C'est le cas quand aucun portail du monde de l'auditeur ne mène au monde du son, ou
qu'un portail vu à travers un autre le rapproche. Il ressort au premier cadre, et son acoustique reste celle de sa
dimension d'émission.

### Bourdonnement des néons

`NeonHum` lit l'index des néons du moteur de rendu, revu toutes les dix ticks (voir [RENDU.md](RENDU.md#néons)). Il fait
bourdonner les quatre tubes les plus proches de la caméra, à moins de douze blocs. Derrière chaque portail visible vers
une dimension à ambiance, il ajoute jusqu'à quatre tubes à moins de douze blocs de l'arrivée. Ceux-là s'entendent au
cadre, comme tout son de l'autre côté.

Chaque bourdonnement suit l'état de son tube, donné par le même calendrier que son image (`NeonSchedule`). Le volume
suit l'intensité, et un tube qui s'éteint ou se rallume joue `click_off` ou `click_on`. À la traversée, chaque
bourdonnement est replacé avec les autres sons (`SoundAnchors`), sans relance.

### Liaison son-image

Un lien est un fichier `assets/gtgs_backrooms/sound_cues/<nom>.json`, lu en `Cue`. Son champ `sounds` nomme les sons
qui le déclenchent, et `reactions` les réactions visuelles, au moins un de chaque. Avec `"envelope": true`, les réactions
suivent l'enveloppe du son. Un son réclamé par deux liens reste au premier.

| Réaction | Effet |
|----------|-------|
| `neon_disturbance` | Les néons dans le rayon vacillent au rythme du son |
| `screen_pulse` | La vignette du post pulse, sur les seuls pixels de la dimension du son |
| `lightmap_flicker` | Toute la lumière de la dimension baisse un instant |
| `camera_shake` | La caméra du joueur tremble, s'il est dans la dimension du son |

Chaque réaction a une force `strength`, de 0 à 1. Selon son type, elle a aussi une portée `radius` ou une distance
maximale `max_distance`, jusqu'à 64 blocs. `follow_envelope`, vrai par défaut, fait suivre l'enveloppe par une courbe
`easing` : `linear`, `in_quad`, `out_quad`, `in_out_quad`, `in_cubic`, `out_cubic` ou `in_out_sine`. Le seul lien livré
est le cri, `scream.json`.

Un son lié crée une perturbation dans sa dimension d'émission, à sa vraie source (`CueTracker`), quel que soit le mode
EFX. Quatre perturbations au plus coexistent, et chaque vue ne lit que celles de sa dimension. Seule une dimension à
look y réagit, si bien qu'un son lié émis dans l'Overworld reste un son ordinaire.

L'enveloppe d'un son est son énergie par fenêtre de 20 ms, normalisée, sur un octet par fenêtre. Elle se calcule en
arrière-plan après le chargement des sons, pour chaque variante de chaque jeu de sons. Elle se garde sur disque sous
l'empreinte du fichier, dans `.cache/gtgs_backrooms/envelopes` du dossier du jeu. Elle se lit à la position de lecture
réelle de la source OpenAL, relue sur le thread du son à chaque tick.

L'horloge commune (`CueClock`) donne au son et à l'image le même temps, celui du monde avec la fraction de tick. Le
tremblement de caméra s'y cale : deux oscillations, un degré et demi au plus en lacet, à l'échelle des effets d'écran du
jeu. Il est coupé par défaut et jamais joué dans une dimension vanilla.

Le plafond de clignotement (`FlashLimiter`) borne l'effet de toutes les réactions ensemble. L'image ne varie jamais
fortement plus de trois fois par seconde, et ne bouge ensuite que par petits pas. Chaque vue pèse la part de l'écran
qu'elle couvre. L'option de confort qui réduit les flashs impose les petits pas et coupe la pulsation, sans toucher au
son.

### Un son de bout en bout

1. `SoundSystem.play` crée la source OpenAL. Pour un son rejoué au cadre, `SoundAnchors` retient sa vraie source.
2. `AcousticRouter.onPlay` prend la main en fin de `play` (`MixinSoundSystem`). `CueTracker` y démarre la perturbation
   d'un son lié.
3. Le routeur cherche la dimension d'émission et son profil. Sans profil, ou moteur coupé, le son reste vanilla.
4. Il mesure l'occlusion par un rayon droit sur le monde vivant, plus le segment côté source pour un son entré par un
   cadre. Il prend la réverb connue de la cellule et le milieu.
5. Sur le thread du son, la source reçoit ses deux envois, l'absorption de l'air et un filtre de la réserve. En mode
   volume seul, elle reçoit une part de volume.
6. Si l'acoustique se calcule, une demande part au thread de calcul. Il lit la forme générée ou lance ses rayons, puis
   mesure l'occlusion et le meilleur détour.
7. À chaque tick, le routeur applique les résultats reçus : réglage de réverb, filtre, position décalée.
8. Toutes les dix ticks, il rend les filtres des sons finis et redemande un calcul pour ceux qui jouent. Après une
   traversée, la revue a lieu au tick suivant et l'envoi 1 est reposé.

### Code

| Paquet | Rôle |
|--------|------|
| `core/audio/profile/` | Profils audio et leur registre |
| `core/audio/acoustic/` | Dimension d'émission, occlusion, rayons, réglages de réverb, cache, forme générée, direction perçue |
| `core/audio/cue/` | Liens son-image, enveloppes, perturbations, horloge commune, courbes, plafond de clignotement, tremblement |
| `core/audio/client/` | Colle OpenAL et client : EFX, routage, bourdonnement, copie du monde, thread de calcul, enveloppes |
| `backrooms/level0/` | Forme d'une salle du Level 0, graine du layout envoyée au client |

Tout sauf `core/audio/client/` est de la logique pure. Les mixins du moteur sont `MixinSoundEngine`,
`MixinSoundSystem`, `MixinSource`, `SourceAccessor` et `MixinCamera`, plus `MixinClientWorld`, partagé avec l'index des
néons.

## Compatibilité

| Configuration | Acoustique | Réactions visuelles | Sons à travers les portails |
|---------------|------------|---------------------|-----------------------------|
| Vanilla, ou mods qui ajoutent des sons | Oui | Oui | Oui |
| Sans EFX, ou option coupée | Volume seul | Oui | Oui, sans réverb |
| Mod de terrain | Oui | Sans la lumière ni la face des néons | Oui |
| Pack de shaders actif | Oui | Voir [RENDU.md](RENDU.md#compatibilité) | Oui |
| Autre moteur acoustique | Cède la main | Oui | Celles de l'autre moteur |

## Réglages

Les options du moteur l'activent ou le coupent, choisissent la qualité du calcul et règlent le confort. D'autres
coupent EFX ou dessinent les rayons de débogage. Leur liste est dans [OPTIONS.md](../reference/OPTIONS.md#audio).

## Diagnostic

La commande du client `/backrooms_state` écrit l'état de l'image dans le journal, et le joueur reçoit une ligne
traduite. On y lit le monde du joueur et son look, la qualité, les looks vus et les néons de la dernière image. Elle
donne aussi la présence d'un pack, les portails du monde avec le palier de leur vue, et les mondes distants.

Pour l'audio, elle écrit le mode du moteur puis chaque son traité. Chaque ligne donne la dimension, le réglage,
l'occlusion, la salle de l'auditeur, le filtre et le décalage. Les mentions `volume seul` et `sous l'eau` s'ajoutent au
besoin. La mention `source perimee` signale un son qui joue sur une autre source que celle qui a reçu ses réglages.
Suivent les perturbations en cours et la valeur du plafond de clignotement.

## Limites

- La forme générée exige un serveur avec le mod. Sans graine du layout, le Level 0 se calcule par rayons. La forme ne
  connaît que le layout, pas le mobilier ni les détails de mur. Sur quarante sons du Level 0, génération et rayons
  choisissent le même réglage pour 70 % d'entre eux, et la même ouverture pour tous.
- La copie du monde avance de deux sections par tick. Au début d'une partie, ou loin de ce qui est copié, un résultat
  dont plus d'un quart des rayons sortent de la copie ne compte pas. Le son garde la réverb de sa cellule ou celle de
  base.
- Un son à plus de 48 blocs de l'auditeur n'a d'occlusion que sur ce que la fenêtre de calcul voit.
- Un rayon ne traverse qu'un cadre, et seulement si l'autre côté est copié. Sinon, il rencontre ce qu'il y a derrière le
  plan de ce côté-ci.
- Les détours passent sur les quatre côtés horizontaux de la source, jamais au-dessus.
- Un son passe deux cadres au plus, par un monde intermédiaire que le client tient. Son occlusion ignore le segment
  entre les deux cadres.
- Le trajet d'un son rejoué au cadre se remet à jour quand le joueur traverse, pas quand il se déplace seulement.
- Un tube de l'autre côté n'est proposé que si l'auditeur est devant le cadre et à portée. Une fois lancé, son
  bourdonnement suit le cadre et ne se tait pas si l'auditeur passe derrière. Après une traversée, son volume peut
  différer un peu jusqu'à la revue suivante.
- Volume général ou d'ambiance à zéro, aucun bourdonnement n'est demandé. Moteur de rendu coupé, aucun tube ne
  bourdonne, car l'index des néons vient de lui.
- Une vitre compte comme un bloc partiel et l'eau comme de l'air. Le milieu ne compte que pour une caméra ou une source
  plongée.
- En mode volume seul, il n'y a ni réverb ni filtrage des aigus, et l'occlusion baisse tout le son.
- Le mod reconnaît un autre moteur à ses objets EFX d'identifiant 1 à 128, relus toutes les dix ticks. Un moteur qui
  n'en crée qu'au-delà passe inaperçu jusqu'à une erreur OpenAL, qui coupe alors le moteur du mod.
- Le second envoi n'existe que si le contexte OpenAL en donne deux, comme OpenAL Soft par défaut. Il n'est vérifié que
  sous Windows.
- Un son diffusé en flux, ou de plus de trente secondes, n'a pas d'enveloppe et garde une amplitude constante. Le
  décalage entre le son et l'image n'est pas mesuré.
- Sous un pack, une vue compte pour l'écran entier dans le plafond de clignotement, qui y est donc plus prudent. Le
  tremblement suit la caméra du joueur, jamais celle d'une vue.
- La revue toutes les dix ticks alloue un itérateur sur la table des sons du moteur. Rien n'est alloué par image.
- Les réglages de réverb, la direction perçue et la réduction des graves n'ont pas encore été validés à l'oreille. Le
  coût d'une scène chargée, avec trente néons, trois cris et un portail, reste à mesurer.

## Tests

La stratégie de test et la couverture sont décrites dans [TESTS.md](../dev/TESTS.md). La colle de `core/audio/client/` y
est nommée, et le reste du moteur est couvert à 100 %.

La logique pure a ses tests unitaires, de `AudioProfileTest` à `FlashLimiterTest`. `LevelZeroRoomShapeTest`
et `LevelZeroLayoutPayloadTest` couvrent la forme générée, et `PortalSoundTest` le trajet par un ou deux cadres.
`ModRegistriesTest` vérifie que chaque son déclaré a son sous-titre. En jeu, le test
`serverGivesTheLayoutSeedOfItsLevelZeroOnly` vérifie que seul le générateur du Level 0 envoie sa graine.

Les tests du client tournent sur le périphérique nul d'OpenAL Soft, avec EFX complet et rien d'audible.

- `LevelZeroClientTests` vérifie la graine du layout reçue, le bourdonnement routé et EFX prêt.
- `EngineRulesClientTests` lit sur la source OpenAL un volume posé sous une échelle. Il vérifie aussi qu'un son arrêté
  puis rejoué reste traité sur sa nouvelle source, sans `source perimee`.
- `PortalFeaturesClientTests` vérifie qu'un cri perturbe les néons et s'entend à travers le cadre depuis l'Overworld. Il
  vérifie aussi l'occlusion d'un son du Level 0 entendu au cadre.

Au démarrage, le journal doit dire `[Audio] EFX disponible, 2 envoi(s) auxiliaire(s) par source` et donner le nombre de
profils. L'option `compareGenerated` lance aussi les rayons là où la génération suffit, et journalise l'accord des
deux.

Sur la machine de référence, un son traité coûte environ 5 µs au départ, et la revue 47 µs toutes les dix ticks. Une
évaluation prend 70 à 90 µs sur le thread de calcul et alloue son résultat. Une section copiée coûte 0,3 à 0,7 ms, soit
2,4 ms au pire par tick pendant le remplissage, puis 0,05 ms.
