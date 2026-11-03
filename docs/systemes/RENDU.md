# Rendu

Le moteur de shaders du mod donne à chaque dimension son propre rendu, appelé look. Il patche les shaders vanilla au
chargement et ajoute un post-traitement masqué par le stencil des portails. Le code est dans `core/render/`, le module
de compatibilité avec Iris dans `compat/iris/`.

## En jeu

Un profil de look donne à une dimension sa palette (teinte, désaturation et étalonnage, tenus dans une LUT), un grain
et, au besoin, un vacillement de la lumière. Il lui donne aussi sa courbe de lumière, son brouillard, des néons qui
éclairent et un post-traitement. Aujourd'hui, seul le Level 0 a un profil.

Dans le Level 0, le terrain n'a plus l'éclairage plat de vanilla. Chaque dalle de néon éclaire vers le bas, par sa seule face
du bas : le sol sous elle, le bas et le milieu des murs, et le plafond ne reçoit que ce qui rebondit. Tous les néons éclairent :
le noir n'existe que là où la lumière n'arrive pas. Les ombres, leurs pénombres et le noir des coins se dessinent au pixel de
texture, et le relief des textures accroche la lumière. La lumière prend la couleur des murs en rebondissant. Les dalles ont un
halo. Les textures sont calmées, leur contraste ramené vers la couleur de leur bloc. En High et en Ultra, chaque bloc a sa matière, écrite dans ses textures : la moquette est mate, le fond du papier peint mat
et rugueux, et seul le sombre de son motif renvoie un peu les dalles. Les entités et la main reçoivent la même lumière que le décor, murs compris. En High et
en Ultra, le joueur et les créatures portent une ombre sur le terrain. En Ultra, la lumière se trace par rayons et l'image change
de nature : presque toute la lumière est indirecte, très douce, sans grille de pixels, comme une photo d'un Minecraft aux textures
calmes. Le Level 0 n'a pas de brouillard : on voit net jusqu'au fond des
couloirs.

Le rendu d'un pixel dépend de la dimension qu'il montre, pas de celle où se trouve le joueur.

- Dans le Level 0, tout l'écran porte le look du Level 0, main comprise. L'interface reste vanilla.
- Depuis l'Overworld, un portail vers le Level 0 montre le look dans son cadre seulement. Le reste de l'écran est
  strictement vanilla.
- Depuis le Level 0, un portail vers l'Overworld montre l'Overworld vanilla, même dans une vue imbriquée.
- Une dimension sans profil n'a aucun look : l'Overworld, le Nether, l'End et les dimensions des autres mods.

La traversée d'un portail n'a pas de transition de look. Comme le look suit le pixel, il n'y a aucune couture à masquer.

Sous un pack de shaders, le rendu du mod remplace le pack tant que le joueur est dans le Level 0. Le pack reprend en
sortant, sans recompilation. Les profils et les patchs se rechargent avec les ressources (F3+T), sans relancer le client.

## Fonctionnement

### Deux étages

Le moteur tient en deux étages, dans le stencil existant des portails, sans G-buffer ni rendu différé.

1. L'injection. Les shaders vanilla du monde sont patchés au chargement. La couleur finale de chaque fragment passe
   par `gtgs_applyLook` (`shaders/include/look.glsl`), qui applique la lumière cuite ou celle des néons, le brouillard,
   le tassement des hautes lumières, l'étalonnage du look, le grain et le vacillement. Quand le look vaut 0, la couleur
   ressort inchangée.
2. Le post masqué. En fin de rendu du monde, `LookPost` dessine un triangle plein écran par look visible. Le test de
   stencil limite chaque passe aux pixels de son look.

Le moteur reprend la lightmap par monde vu (`ViewLightmaps`), le brouillard par vue et le stencil des portails.
L'injection passe par le même point que la découpe des vues au plan du portail (`MixinShaderStage`).

### Profil de look

Un profil est un fichier `assets/gtgs_backrooms/look_profiles/<nom>.json`. Il se lit en `LookProfile`, une simple
donnée, rangée par dimension dans `LookProfiles`. Un fichier invalide est refusé et journalisé. Si deux profils visent
la même dimension ou le même identifiant, le second est ignoré.

| Champ | Rôle | Plage |
|-------|------|-------|
| `dimension` | Dimension du profil, obligatoire | Identifiant |
| `id` | Look porté par le stencil et l'uniforme `gtgs_LookId`, obligatoire | 1 à 7 |
| `tint` | Teinte et sa force, `[r, g, b, force]` | Couleur de 0 à 4, force de 0 à 1 |
| `desaturation`, `grain`, `flicker` | Désaturation, grain, vacillement de la lumière de l'écran | 0 à 1 |
| `grade` | Étalonnage : `slope`, `offset`, `power` (`[r, g, b]` chacun), `saturation` | Pente de 0 à 4, décalage de -1 à 1, puissance de 0,1 à 4, saturation de 0 à 2 |
| `baked_light` | Lumière cuite : `emission`, `ambient`, `reach`, `size`, `angle`, `shadow_size`, `occlusion`, `relief`, `haze`, `sheen`, `shoulder`, `toe`, `pixel_light`, `dark_pixels`, `detail`, `exposure`, `direct`, `bounce`, `bounce_color`, `light_limit`, et `traced` pour la [lumière tracée](#lumière-tracée) ; demande `neons` | Émission de 0,1 à 64, fond de 0 à 1, portée de 1 à 15, taille de 0,5 à 8, angle de 10 à 90 degrés, occlusion de 0 à 1, relief de 0 à 4, halo de 0 à 1, reflet de 0 à 2, épaule de 0,1 à 1, couleur des rebonds de 0 à 2, limite de 0 à 4 |
| `lightmap` | `floor`, `warmth`, `max_brightness` ; un monde à courbe n'a pas le vacillement de torche que vanilla tire à chaque tick pour la lumière de bloc (`DimensionLightmap.blockFlicker`), qui ferait trembler, à chaque tick, ce que la lightmap éclaire faiblement au loin | 0 à 1, plancher sous le plafond |
| `fog` | `color`, `start`, `end` | `end` au-delà de `start` |
| `post` | `vignette`, `chromatic`, `bloom` (booléen), `bloom_threshold`, `bloom_strength` | 0 à 1, force du bloom de 0 à 2 |
| `neons` | `block`, `radius`, `color`, `strength`, `flickering`, `dead`, `blackout` | Portée de 0,5 à 64, force de 0 à 2, parts de somme au plus 1 ; `blackout` : part des secteurs en panne, de 0 à 1 |

Une section absente garde le comportement vanilla. Le profil du Level 0 met à zéro le vacillement de l'écran et les
parts de tubes qui vacillent ou meurent. La mécanique est complète, et relever ces valeurs suffit.

Les options de confort retirent une partie du profil au chargement (`LookProfile.comfort`). Les effets de dimension
viennent du contenu. `LevelZeroEffects` déclare ceux du Level 0 : ni ciel, ni nuages, et le brouillard du profil.

### Patch des shaders

Le patch enveloppe la `main` de chaque shader ciblé. La `main` vanilla devient `gtgs_vanilla_main`, et une nouvelle
`main` l'appelle entre le code des injections. Le corps vanilla n'est jamais réécrit.

Un patch est un fichier `assets/gtgs_backrooms/shader_patches/<nom>.json`, lu en `ShaderPatch`. Son champ `stage` vaut
`vertex` ou `fragment`. Les champs `targets` et `excludes` désignent les shaders par des motifs à `*`, et une exclusion
l'emporte. Le champ `imports` liste les bibliothèques insérées en tête, et `inject` les injections. Un seul patch
s'applique par étage : le premier qui vise un shader l'enveloppe, et un patch suivant qui vise le même étage est ignoré,
journalisé une fois.

| Point d'injection | Effet |
|-------------------|-------|
| `head` | Code inséré après la `#version` active |
| `main_before` | Code joué avant la `main` vanilla |
| `main_after` | Code joué après la `main` vanilla |
| `output` | Fragment seulement : la sortie passe par la fonction nommée dans `function` |

Le point `terrain_after` joue comme `main_after`, dans les seuls shaders du terrain (ceux qui déclarent `ChunkOffset`), et
`lit_after` dans ceux qui ont la sortie `lightMapColor` (les entités).

Le mod livre deux patchs, qui visent `rendertype_*` et `particle` sauf le texte, l'interface, le contour des entités et
les nuages. `world_fragment.json` insère `neon_schedule.glsl`, `look_grade.glsl` et `look.glsl`, puis fait passer la
sortie par `gtgs_applyLook`. `world_vertex.json` ajoute sept sorties aux vertex shaders.

- `gtgs_ViewPos` : la position en espace caméra.
- `gtgs_WorldRel` : la position relative à la caméra, dans les axes du monde.
- `gtgs_Uv` et `gtgs_Normal` : les coordonnées de texture et la normale.
- `gtgs_Terrain` : le drapeau du terrain, en `flat`.
- `gtgs_Lightmap` : la couleur de la lightmap du sommet, pour le terrain.
- `gtgs_Lit` : 1 pour une entité sous la lumière cuite, en `flat`.

Le code d'une injection peut contenir des jetons, remplacés selon ce que le shader déclare. `${position}` vaut
`Position + ChunkOffset` dans un shader du terrain, `Position` ailleurs. `${uv}` vaut `UV0`, ou `vec2(-1.0)` sans lui.
`${normal}` vaut `Normal`, ou `vec3(0.0)`. `${terrain}` vaut `1.0` dans un shader du terrain, `0.0` ailleurs.

Un fragment ne reçoit son patch que si le vertex du même programme a reçu le sien (`PatchedVertexStages`). Sinon, il
lirait des entrées que rien n'écrit, et le programme ne se lierait plus. Il reste alors vanilla.

### Étalonnage

La palette d'une dimension tient dans une LUT (`LookLut`) : pour chaque couleur d'une grille de 32 pas par composante,
la couleur qu'en fait le profil. Le calcul désature d'abord vers la luminance (poids 0,299, 0,587 et 0,114), puis
applique la teinte, puis l'étalonnage de la section `grade` : `pow(max(c × slope + offset, 0), power)` par composante,
puis la saturation autour de la luminance. Sans section `grade`, l'étalonnage est neutre, et la LUT ne fait que la
désaturation et la teinte.

`LookLuts` range toutes les LUT dans une seule texture 3D de 32 × 32 × 256, en demi-flottants (`RGB16F`), car une
teinte peut dépasser 1. Elle a un étage de 32 par identifiant de look, l'identité pour vanilla et pour les identifiants
sans profil. Elle se refait à chaque rechargement des profils. Elle se relie à l'unité de texture 7 au début de chaque
rendu du monde, et les shaders patchés comme le post la lisent par l'échantillonneur `gtgs_LookLut`. La fonction
`gtgs_grade` (`shaders/include/look_grade.glsl`) lit l'étage du look entre les centres de ses texels extrêmes, si bien
que l'interpolation ne déborde jamais sur l'étage voisin. Une opération linéaire (désaturation, teinte, pente, décalage)
est exacte entre les pas, et une puissance y est approchée.

L'étalonnage s'applique en fin de calcul du fragment, après le brouillard et la lumière des néons, avant le grain et le
vacillement. La couleur y entre bornée à 1. Le shader du post en garde une copie à l'identique, pour le cas où l'étage 1
passe par lui (pack de shaders, mod de terrain). La même LUT vaut à toutes les qualités.

Le Level 0 n'a pas de filtre sur son image : sa teinte vient du chemin de la lumière. Sur l'image de référence de l'auteur, elle suit la
lumière, de l'ambre profond des ombres (teinte 43) au jaune pâle sous les dalles (teinte 57). Deux réglages de `baked_light` la donnent, aux
quatre paliers. `bounce_color` (`[1.03, 1.0, 0.72]`) s'ajoute à la couleur des blocs dans ce que les surfaces se renvoient, à chaque
rebond : la lumière qui a tourné dans une pièce devient ambrée, et colore ce qu'elle touche, si bien que le plafond, presque blanc dans sa
texture, est ambre. `light_limit` (0,96) tasse la lumière d'une surface vers sa limite, composante par composante, à partir des trois quarts de
celle-ci : un sol, qui reçoit bien plus de lumière qu'un mur, ne blanchit donc pas : sous une dalle, toute lumière finit blanche et la surface a la couleur de sa texture, alors que ce qui n'a que des rebonds garde
leur couleur. La lumière des dalles reste blanche. L'étalonnage se réduit à une réponse de film, une puissance par composante (`power`,
`[0.95, 1.0, 1.08]`), qui réchauffe les sombres sans toucher au noir ni au blanc. Toute autre couleur garde la sienne : la peau, un objet,
une tache de la moquette. L'exposition de l'Ultra (`traced.exposure`, 0,6) et sa lumière directe (`traced.direct`, 1,3) éclairent les
murs jusqu'à leur pied, et la limite tasse ce qui reçoit plusieurs dalles : un mur bien éclairé tend vers la couleur crème de sa texture,
sans que deux dalles proches ne saturent l'image. La limite vaut pour l'image : une exposition
plus basse seule assombrit ce qui est loin des dalles sans éteindre ce qui est dessous, et monte le contraste ; pour baisser toute l'image
au même contraste, les deux baissent ensemble.

### Lumière cuite

Un profil avec une section `baked_light` remplace, sur le terrain, la lightmap et l'ombrage vanilla par face
(`ClientWorld.getBrightness` rend 1 dans ce monde) par une lumière calculée sur les blocs du monde. Tout est en forward,
sans G-buffer : le fragment lit une valeur dans une texture 3D, ce qui ne coûte qu'un échantillon.

La cuisson tourne sur un fil à part (`LightWorker`), sur une copie compacte du monde. `LightCopy` copie les sections autour
de la caméra, ou autour de l'arrivée du premier portail visible vers un tel monde depuis un monde qui n'en a pas : huit
par tick au plus, chaque état de bloc classé une fois (albédo moyen de sa texture, émission si c'est le bloc de néon du
profil). `LightZone` cuit une fenêtre de sept sections de côté et deux de haut, la plus proche d'abord, en trois passes.

1. La lumière directe (`SectionBaker`). Seules les faces d'une cellule d'air posées contre un bloc opaque portent une
   valeur. Une dalle est un rectangle orienté de `size` blocs de côté, qui n'éclaire que par la face du bas de son bloc, si
   elle est libre : rien au-dessus de son plan, rien par ses côtés. Chaque dalle à portée compte pour ce qu'elle donne
   depuis le point de son rectangle le plus proche de la surface (`AreaLight`) : le cosinus côté surface, celui côté dalle
   ramené à `angle` (l'angle où elle cesse d'éclairer, depuis sa normale), et une chute douce sur `reach`, sans carré de la
   distance qui brûlerait ce qu'elle frôle. Le tout est multiplié par la part de la dalle que la surface voit (quatre rayons
   sur la grille des blocs, `BlockRay`) : la lumière s'arrête aux murs. Les néons morts du calendrier ne cuisent pas.
2. Un premier rebond. Vingt-quatre rayons répartis sur la demi-sphère de chaque surface vont lire l'éclairement des
   surfaces qu'ils touchent, teinté par leur albédo.
3. Un second rebond, qui lit le premier chez les sections voisines. Ses rayons notent aussi les blocs à moins de 1,5 bloc :
   `occlusion` retire ce qu'ils cachent, d'où les coins sombres.

Une section ne cuit que lorsque tout ce dont elle dépend est copié, deux sections autour d'elle. Un bloc changé remet à
cuire sa section et ses voisines, plus le dernier rebond des voisines de celles-ci. Une colonne absente du client compte
comme opaque et ne fait rien attendre (`LightBlocks.UNLOADED`).

`LightVolume` tient le résultat dans une texture `RGB10_A2` de 128 × 384 × 128 : 32 blocs de haut par face de cellule pour la
lumière complète, puis autant sans la lumière directe. X et Z sont en répétition. Une section garde sa place tant que la
zone avance, et seules celles qui entrent s'envoient, quatre par image. Au shader, `gtgs_zoneLight` lit la cellule d'air devant la surface, sur la tranche de sa face, au centre de son
texel. La couleur et la validité vont ensemble : divisée par la validité, l'interpolation ne compte que les surfaces
voisines, sans fuite par un bloc plein. Une surface hors de la grille ou pas encore cuite prend le loin.

Le loin cuit les sections à moins de huit sections de l'ancrage (`LightZone.FAR_RADIUS`), soit 128 blocs, comme la zone : lumière
directe, premier rebond, puis le second avec l'estimation des suivants et les coins assombris, par les mêmes calculs
(`SectionBaker`), avec la vraie géométrie et la couleur des blocs. Il ne garde de chaque section qu'une cellule pour deux blocs à
l'horizontale (`LightZone.reduce`, moyenne des surfaces de chaque face) ; pour le rebond d'une section, ses voisines du loin sont
relues dans ces cellules (`LightZone.expand`). Une section avance d'un état quand ses voisines en sont au moins là, la plus proche de
l'ancrage d'abord, et seulement la fenêtre au repos. Une section de la fenêtre donne son loin à sa publication, son propre éclairement
réduit, et le garde quand la fenêtre s'en va. Un bloc changé fait reculer le loin d'un état de moins par section d'écart (trois
sections). `FarVolume` tient le résultat comme la texture de zone : l'éclairement complet, puis sa part indirecte, dans une texture
`RGB10_A2` de 144 × 384 × 144, en répétition tous les 288 blocs, à l'unité 17 ; le shader les lit avec la même formule que la zone.
Mesuré dans une copie d'un monde de l'auteur : 480 sections en trois états, 7,6 s sur le fil de cuisson (5 ms par état), contre 17 ms
pour une section de la zone ; le loin se remplit en une dizaine de secondes après une arrivée. La copie du monde va jusqu'à neuf
sections, huit par tick ; une section qui sort du loin est effacée de la texture puis oubliée (`LightZone.forgetFar`). Seule la zone
prévient le cache de surfaces et la grille des blocs.

Dans le noir, chaque pixel de texture tire un petit écart de lumière, fixe (`dark_pixels`) : c'est l'effet sombre pixelisé. Il
s'efface en pleine lumière et entre 8 et 20 blocs, où il ne ferait que du bruit. La face d'un néon brille d'elle-même, et les
cris retirent à la surface ce qu'ils retirent aux tubes proches.

La lumière d'une dalle s'éteint sans cassure à sa portée (`AreaLight.falloff`, le même calcul dans le shader du cache) : une
coupure nette dessinerait un arc sur le sol. `bounce` règle la part de lumière que les surfaces se renvoient : plus basse, ce
que les néons n'atteignent pas reste sombre.

Sous la lumière cuite, le vertex du terrain ne porte ni lightmap ni brouillard (point `terrain_after`) : le fragment pose
sa lumière, puis le brouillard de vanilla, ses distances et sa couleur lues dans `gtgs_FogRange` et `gtgs_FogTint`. Le
brouillard ne s'assombrit donc plus avec la surface. `gtgs_tone` applique ensuite la courbe de l'image : son pied (`toe`)
enfonce ce qui est presque noir vers le vrai noir sans toucher au reste, et `shoulder` tasse les hautes lumières vers 1 au lieu
de les saturer d'un coup. Le grain s'efface avec la lumière : il ne remonte pas le noir.

#### Cache de surfaces

La zone ne connaît la lumière qu'au bloc près. En qualité complète, le cache de surfaces (`LightSurfaces`) la complète au
pixel de texture : c'est une lightmap, une tuile de 32 × 32 par face de bloc, calculée par le processeur graphique dans une
texture de 4096 × 8192 (`SurfaceAtlas` : 32 768 tuiles, 128 Mio, rendues quand une section sort).

La section cuite donne ses surfaces et ses neons, et ses tuiles se calculent à 96 par image, la plus proche d'abord, avec le
shader `shaders/bake/surface.fsh`. Chaque tuile reçoit ses propres néons, les 24 plus proches parmi ceux de sa section
(`LightSurfaces.tileLights`). Pour chaque pixel de texture d'une face, le shader écrit quatre valeurs séparées :

| Canal | Valeur | Lecture par le terrain |
|-------|--------|------------------------|
| `r` | Racine de la lumière qui arrive, sans obstacle | Lisse, interpolée entre les pixels de la tuile ; `pixel_light` en garde une part sur la grille |
| `g` | Part de cette lumière que le pixel voit : ses ombres portées | Par pixel de texture |
| `b` | Part du ciel de la surface que rien ne cache | Par pixel de texture |
| `a` | Ce que le relief de la texture ajoute ou retire, autour de 1 (divisé par 2) | Par pixel de texture |

Les corps s'ajoutent à la lecture, à chaque image (voir [Ombres des corps](#ombres-des-corps)).

C'est ce qui donne des ombres portées pixelisées sur une lumière lisse : le dégradé ne suit pas la grille, le bord des ombres
oui, en marches de la taille d'un pixel de texture. Les rayons vont aux mêmes points de la dalle, dans un disque de
`shadow_size` blocs de côté (toute la dalle en Ultra) : rien n'est tiré au hasard, le bord d'une ombre passe d'une tuile à la suivante sans couture, et
recalculer une tuile sans que rien ait changé donne les mêmes valeurs. Une dalle très faible n'a droit qu'à deux rayons.
Plusieurs dalles se partagent une surface : le terrain met la part vue au carré, sans quoi l'ombre d'une seule serait à peine
lisible. Sans pixels d'ombre, tout se lit lissé. Le coût ne se paie qu'une fois par tuile : ensuite, le terrain ne fait que
quelques lectures.

Une texture d'indirection (`R16UI`, de la forme de la zone) donne la tuile de chaque surface. Un fragment ne lit jamais la tuile
d'un autre : une surface nouvelle n'y entre qu'une fois calculée, et sans tuile, le terrain garde la lumière complète de la
zone.

Une surface garde sa tuile tant qu'elle existe. Une section recuite ne recalcule que ce qui a changé (`SurfaceAtlas.accept`),
et une tuile à recalculer montre son ancienne valeur jusqu'à la nouvelle, écrite d'un seul dessin : rien ne disparaît, rien ne
clignote. Un bloc changé arrive à la lumière par `LightCopy.onBlockChanged`, que la prédiction du joueur et les paquets du
serveur appellent tous deux. Dès l'image suivante, il est posé sur la grille, ses faces et celles de ses voisins apparaissent
ou s'en vont sans attendre la cuisson, et seules les surfaces qu'il peut assombrir repartent (`SurfaceAtlas.blockChanged`) :
celles à moins de trois blocs, et celles dont un rayon vers un néon passe par lui. Un bloc que la lumière traverse avant comme
après (une torche) ne coûte rien. Une section cuite avant le dernier bloc de sa section (`Baked.stamp`) ne touche plus aux
surfaces. Plus de 32 blocs changés en une image repartent par sections entières.

Une tuile ne se calcule que si la grille des blocs est complète autour de l'ancrage : ses ombres ne s'écrivent jamais d'après
d'autres blocs. Seules les sections à moins de deux sections de l'ancrage ont des tuiles, et seulement à sept blocs au plus
au-dessus ou au-dessous de la caméra : un layer et ce qui le borde. La bande suit la caméra dès qu'elle monte ou descend de plus
de deux blocs, sans rien recalculer de ce qui y reste, et la fenêtre de la zone ne se déplace que si la caméra approche à moins
de quatre blocs de ses bords (`LightZone.baseFor`). Une autre fenêtre vide la texture de zone d'un seul envoi : ce qu'elle tenait se
relirait à la mauvaise hauteur.

Une grande salle ouverte demande dix-huit mille tuiles sur ce rayon. S'il en manquait, les surfaces en trop garderaient la lumière de
la zone, et leur section se verrait en carré de près. Une section sortie du rayon rend donc ses tuiles avant que les autres n'en
prennent, les sections se servent de la plus proche de l'ancrage à la plus loin, une section en manque reprend les tuiles des plus loin
et une section servie à moitié est reprise dès que des tuiles se libèrent : seul ce qui est assez loin pour se fondre dans la zone
attend.

La lumière d'une surface vient de quatre niveaux, qui se fondent l'un dans l'autre selon la distance à la caméra : les tuiles jusqu'à
24 blocs, puis la texture de zone de 32 à 40, puis le loin de 48 à 96, puis une pièce moyenne au-delà de 128. Les quatre doivent
donner la même lumière. La lumière directe de la zone a la même part que
celle des tuiles, et la zone compte, après ses deux rebonds, les suivants comme les rebonds tracés (`SectionBaker.FOLLOWING`, deux fois
le second) : sans eux la zone était 10 % plus sombre, si bien qu'une surface s'éclaircissait en approchant, entre 32 et 24 blocs. Pour
son rebond, une section relit ses voisines à l'étape d'avant, même si elles sont allées plus loin (`LightZone.at`) : une voisine déjà
finie donnait ses rebonds en trop, et une bande claire suivait les frontières de sections au plafond des paliers sans lumière tracée.

Aucune valeur propre à une dimension n'est écrite dans le shader : tout ce que le loin et la pièce moyenne montrent vient de la cuisson
de la dimension où l'on est. Contre la zone, en Low, sur le même pixel, aux deux endroits mesurés : le loin vaut 0,97 à 1,00 en
médiane, et la moitié des surfaces est à 1 % près sur les murs et au plafond (0,99 à 1,01), à 3 % au sol. Avec des rebonds fixes,
mesurés une fois dans le Level 0, l'écart allait de 0,86 à 1,26. Sur des vues entières où tout le terrain prend le loin, l'écart moyen
à la zone est de 2,3 niveaux par pixel (7,6 avec les rebonds fixes). La lightmap d'avant le loin valait 0,66 à 0,86 sur les murs,
dans un brun que la zone n'a pas.

Au-delà de 128 blocs, la pièce moyenne (`LightZone.room`, uniforme `gtgs_FarRoom`) se mesure sur tout ce qui est publié : par genre
de surface (murs, sols, plafonds), la moyenne des surfaces éclairées de chaque section, puis celle du quart le plus clair des
sections récentes, une pièce bien éclairée ; elle s'éteint avec la lightmap là où celle-ci tombe sous ce qu'elle vaut dans une pièce
éclairée. Les sections les plus sombres sont des couloirs sans dalle, que la lightmap assombrit déjà : la moyenne de toutes valait
0,55 à 0,76 de la zone dans deux salles éclairées, le quart le plus clair 0,69 à 1,17. Le passage du loin à la pièce moyenne se fond
de 96 à 128 blocs. Avant la première section cuite de la dimension (une arrivée, une vue de portail tout juste ouverte, le temps de la
copie et de la cuisson), la lightmap prend sa place, ombrée selon l'orientation de la surface, comme avant le loin : sans cela, tout
ce qui n'était pas encore cuit restait noir quelques secondes.

La zone suit le palier du joueur : changer de palier en jeu la recuit (`LightCopy.refreshSettings`). Une section qui prend ses tuiles, ou une pièce qui entre dans la zone, ne se voit donc pas : c'est
ce qui faisait « s'allumer » les néons au loin. Une section qui quitte la fenêtre est oubliée par la zone (`LightZone.forget`), et
repart quand la fenêtre revient sur elle.

Trois effets dépendent de la vue et se calculent au fragment, pour les dix néons les plus proches, chacun selon sa part
(`NeonSelection.nearShare`) : elle tombe à zéro avant qu'il ne quitte la liste, et un halo ne saute jamais.

- Le halo (`gtgs_airGlow`) : la lumière que l'air renvoie vers la caméra, l'intégrale exacte d'une source ponctuelle sur le
  rayon, sans marche, pesée par `haze`. Il ne vaut que pour les rayons qui passent à moins de trois blocs d'une dalle : un
  voile sur toute l'image ferait un brouillard.
- Le reflet (`gtgs_gloss`), en High et en Ultra, sur toute face dont la [matière](#matière-des-blocs) le demande, pesé par `sheen`.
- La lumière des entités et de la main (`gtgs_entityLight`). Sous la lumière cuite, leur vertex ne porte plus la lightmap
  (point `lit_after`) : elles reçoivent le fond du profil, la direction réelle des néons proches, et ce que la pièce leur
  renvoie : un rayon par fragment, selon sa normale, lit la surface qu'il touche, dans la couleur de son bloc (`gtgs_roomLight`). Un corps
  près d'un mur prend son jaune, et dans le noir il ne reçoit rien. Sa lumière passe par la même limite et la même exposition que le décor.
  Ce sont les shaders à `lightMapColor` : blocs tenus et objets au sol gardent la lightmap.

Le bloom ajoute deux tours de flou à pas larges : une dalle rayonne dans la pièce, pas seulement autour d'elle.

La texture de zone se répète : au-delà de 48 blocs de la caméra, elle montrerait autre chose. Entre 40 et 48 blocs, le terrain
passe en fondu au loin, et entre 96 et 128 blocs, du loin à la pièce moyenne.

Une vue lit la zone si sa dimension est celle de la zone et que sa caméra en est à moins de 48 blocs, dans sa hauteur
(`LightVolume.covers`). Sinon, elle garde la lightmap et la lumière des néons : il n'y a qu'une zone à la fois.

### Matière des blocs

Ce qui brille et ce qui accroche la lumière s'écrit dans les textures du bloc, au pixel, jamais dans le code. À côté de `x.png` :

| Fichier | Rouge | Vert | Bleu |
|---------|-------|------|------|
| `x_s.png` | Lissage : 0 mat, 255 miroir | Rien | Rien |
| `x_n.png` | Pente vers la droite de la texture, 128 pour plat | Pente vers le bas de la texture, 128 pour plat | Reste de la normale |

L'alpha de `x_n.png` porte la hauteur du pixel : 255 en haut, et une texture toute à 255 n'a pas d'épaisseur. Les deux fichiers font la
taille de la texture, se retouchent comme elle, et F3+T les recharge et recalcule les tuiles. Un bloc sans fichier reste mat, et son
relief se devine d'après la luminance de sa texture. Seuls les blocs du mod en ont, en 32 pixels pour une épaisseur.

| Texture | Matière |
|---------|---------|
| `level_zero_damp_carpet` et ses variantes `_1` à `_3`, `level_zero_damp_carpet_aged` | Moquette : lissage nul partout, et une hauteur qui est le bruit de sa texture : un pixel clair est une pointe, un pixel sombre un creux |
| `level_zero_wallpaper`, `level_zero_wallpaper_aged` | Le sombre du motif : un peu lisse (115 sur 255), légèrement en relief. Le papier dessous : lissage nul, grain fin |
| `level_zero_ceiling_tile` | Le rail, un pixel sur chaque bord : métal mat, plus clair que la dalle, à peine plus haut qu'elle (255 contre 204, moins de deux centimètres pour un bloc d'un mètre), avec une arête d'un pixel. La dalle : plate, et une ombre de contact d'un pixel dans sa couleur, qui dit le relief en Low et en Medium. Sans `x_s.png` : mat |

`BlockMaterials` recopie ces fichiers dans une table de la taille de l'atlas des blocs, chaque pixel à la place du sien dans l'atlas :
un shader la lit aux mêmes coordonnées que sa texture (`gtgs_BlockMaterial`), sans savoir quel bloc il dessine. Elle n'existe qu'à
partir de High, comme le veut `QUALITE_ET_PRESETS` §5.6.

- Le relief du cache de surfaces lit la pente de la table, multipliée par `relief`.
- L'épaisseur (`gtgs_enterPile`) : sur une matière qui a une hauteur, le regard entre par le haut, dix pas sur 0,08 bloc, et s'arrête au
  premier pixel qui dépasse. Le fragment prend la couleur et la lumière de ce pixel : les brins se cachent les uns les autres et bougent
  avec la caméra. Les creux perdent un peu de lumière et les pointes en gagnent, plus en Ultra, où la texture calmée ne porte plus ce
  contraste ; un pixel plat n'est ni l'un ni l'autre. Elle s'efface de 10 à 16 blocs, et l'option de relief la coupe.
- Le calme des textures (`gtgs_calmed`, à tous les paliers) laisse leur couleur aux pixels tout en haut d'une matière presque plate :
  le rail d'une dalle est une pièce à part, pas du grain, comme le sont les brins d'une moquette. Sans cela, un rail mat disparaît en Ultra.
- Le reflet (`gtgs_gloss`) lit le lissage, et penche sa normale selon la pente du pixel. Sur un pixel mat, il s'arrête là : ni boucle
  ni rayon. Sinon, chaque dalle proche se voit au point de son rectangle le plus proche du miroir, avec le lobe de ce lissage, plus
  fort en rasant. Un rayon dans la grille vérifie qu'elle est visible, quatre sur sa surface en Ultra : un mur ou un corps coupe le
  reflet. Il s'éteint de 16 à 32 blocs de la caméra.

### Lightmap et brouillard

La courbe du profil s'applique à chaque point de la lightmap, une fois par tick et par monde (`DimensionLightmap`). Le
plancher relève le noir, le plafond borne la lumière, et la chaleur monte le rouge en baissant le bleu. Chaque vue
reçoit la lightmap du monde qu'elle montre.

Le brouillard du profil remplace celui de vanilla sur le terrain (`DimensionFog`). Vanilla garde la main quand la caméra
est dans un fluide ou la neige poudreuse, sous cécité ou obscurité, ou sous un brouillard épais imposé.

Vanilla tire la couleur du ciel et celle du brouillard des biomes autour de la caméra, en 216 points pondérés
(`CubicSampler.sampleColor`), et crée trois à quatre vecteurs par point : plus de huit cents par appel, à chaque image et
à chaque vue de portail, soit les deux tiers de ce que le fil de rendu alloue. Le mod fait le même calcul sans eux
(`BiomeColorSampling`, branché par `BiomeColors` dans `BackgroundRenderer.render` et `ClientWorld.getSkyColor`) : mêmes
opérations dans le même ordre, donc le même résultat au bit près. Les effets de dimension de vanilla (Overworld, Nether,
End) et ceux d'un monde à look sont reproduits ; d'autres, ceux d'un autre mod, laissent vanilla calculer.

### Néons

Chaque monde client tient l'index de ses néons (`NeonIndex`, `NeonMap`). Il se remplit des chunks reçus, en ne
parcourant que les sections dont la palette contient le bloc de néon. Le relevé se fait en fin de tick, dans l'ordre
d'arrivée, 1,5 ms par tick au plus : une fenêtre de portail reçue d'un coup ne coûte pas une image longue (9 ms pour
153 chunks du Level 0 en un seul relevé). Il suit ensuite chaque `setBlockState` du monde client et oublie les colonnes
déchargées.

Pour chaque vue, `NeonLights` retient les néons les plus proches de sa caméra, huit ou trente-deux selon la qualité. Il
les envoie dans un bloc d'uniformes `std140`, `gtgs_NeonBlock`, avec leur position, leur portée, leur couleur et leur
état du moment. Il y a un tampon par niveau de vue, et rien n'est alloué par image.

Chaque fragment ajoute la lumière des néons retenus, qui décroît avec le carré de la distance rapportée à la portée.
Cette lumière est multiplicative. Elle relève la couleur déjà éclairée par la lightmap, sans éclairer le noir complet.
La somme est plafonnée à +80 %, pour qu'un couloir dense ne vire pas au blanc.

#### Lumière au texel

Sur un bloc du terrain, la distance aux néons se mesure depuis le centre du texel du fragment, pas depuis sa position
exacte (`gtgs_lightPosition`). Sur les deux axes de sa face, la position est ramenée à `(floor(p × N) + 0,5) / N`, et
l'axe de la normale la garde (à égalité, x, puis y, puis z). N vaut 32, le pas des textures du mod, 16, ou 0 pour une
lumière lisse (option `pixelShadows`). La position est reprise depuis le bloc entier de la caméra (`gtgs_CameraFract`
plus `gtgs_WorldRel`), si bien que la grille reste alignée sur les blocs loin de l'origine. Elle repasse ensuite en
espace caméra par `ModelViewMat`. Les entités, les particules et le reste gardent la position exacte. `TexelGrid` est le
jumeau Java de ce calcul.

#### Ombres des corps

En High et en Ultra, le joueur et les créatures portent une ombre sur le terrain. Chaque tick, `EntityShadows` retient les huit
corps vivants les plus proches de l'ancrage de la zone ; à chaque image, leur position affichée part dans le bloc d'uniformes,
sous forme de cylindres debout (`ShadowCasters` : jambes, torse et tête pour un corps debout, un seul pour un corps bas). Le
joueur en fait partie, y compris à la première personne.

Le terrain lit l'ombre au pixel de texture (`gtgs_casterShade`) : pour chaque dalle à portée, des rayons vers toute sa surface.
L'ombre est donc nette au contact et se dilue en s'éloignant. Un rayon arrêté par un corps ne compte que si la grille des blocs
le laissait passer : un corps ne fait pas d'ombre à une dalle qu'on ne voit pas d'ici. Le retrait se fait après la courbe du cache,
en part de ce que la surface reçoit vraiment. Un préfiltre garde le coût : seuls les corps à moins de 10 blocs comptent, et seul
un rayon vers le centre de la dalle qui frôle un corps lance les autres.

Les entités et la main reçoivent le même calcul de dalle que le terrain, à sa même échelle. En High et en Ultra, un rayon par dalle
dans la grille les laisse dans l'ombre des murs : l'ombre d'un bloc les traverse, en marches sur une grille de seize par bloc.

## Lumière tracée

Le palier Ultra trace sa lumière dans la grille des blocs, sans rien accumuler à l'écran : tout ce qui se calcule en rayons se
range sur les surfaces, et ne se voit qu'une fois fini. Il vise une image éclairée comme une photo : lumière presque toute indirecte,
pas de noir tant que la lumière peut arriver, ombres très larges, sol mat. Une pièce fermée reste noire. La matière reste celle du
jeu : ses pixels restent, leur contraste baisse.

La section `traced` de `baked_light` porte ce qui change, et tout ce qu'elle ne dit pas garde la valeur de la lumière cuite.
`detail`, `exposure` et `direct` valent aussi dans `baked_light`, pour les autres paliers (1 par défaut) :

| Clé | Rôle |
|-----|------|
| `shadow_size` | Ce que visent les rayons des ombres : plus grand que la dalle, les ombres s'étalent |
| `direct` | Ce que vaut la lumière directe à l'image, de 0 à 4, les rebonds restant entiers : plus basse, la lumière s'égalise |
| `bounce` | Part de la lumière que renvoient les surfaces |
| `exposure` | Multiplie toute la lumière des surfaces : les rebonds apportent bien plus que la lumière cuite |
| `detail` | Part du contraste des textures gardée : 0 rend la couleur moyenne de chaque bloc |
| `relief`, `sheen`, `toe`, `shoulder` | Comme dans `baked_light` |

Sous cette lumière, le terrain n'a plus de grille des texels : tout se lit lissé, quelle que soit la taille des pixels d'ombre choisie,
qui ne vaut que pour les autres paliers. La part vue d'une dalle n'est plus mise au carré : les rebonds font le gros de la lumière.

- **Pénombres vraies** : 32 rayons par dalle, sur un disque de `shadow_size` blocs.
- **Rebonds** (`LightBounces`, `shaders/bake/bounce.fsh`) : une tuile de 8 pixels par surface, dans un cache de 1024 × 2048 pixels
  (16 Mio avec le premier tour). Chaque pixel lance 64 rayons sur 24 blocs, répartis selon le cosinus de sa normale : un rayon qui touche une surface y reprend
  sa lumière directe (sa tuile, ou la zone hors des tuiles), dans la couleur de son bloc. Le premier tour écrit dans une texture à
  part, le second le relit et écrit le cache que lit le terrain, en comptant les rebonds suivants par une part fixe de ce qu'il
  ajoute : un résultat qui ne dépend pas de ce qui était là avant, et aucun état intermédiaire à l'écran. Rien n'est tiré au hasard : après un bloc posé, chaque rebond est identique, au bit
  près, à un recalcul complet. Une surface neuve garde, dans l'image où elle apparaît, les rebonds que la zone lui donnait : sa tuile
  arrive sans que sa lumière change, et le balayage suivant lui donne ses rebonds tracés, à 1 % près dans une grande salle. Un gain
  fixe sur la lumière directe relue la laissait 2 à 15 % trop claire selon les réglages, puis la faisait retomber à la fin du balayage.
  Un balayage repasse ensuite
  partout une fois le cache de surfaces au repos, 96 tuiles par image. Les coins s'assombrissent d'eux-mêmes : seul le fond du profil
  garde encore l'ouverture.
- **Reflets** : ceux de la [matière](#matière-des-blocs), qui visent toute la dalle par quatre rayons au lieu d'un.

La grille des blocs porte l'indice de la matière, et `gtgs_Materials` sa couleur moyenne et si elle émet. Cette grille ne couvre que la
zone et se répète au-delà : hors d'elle, à plus de 48 blocs ou à une autre hauteur, le contraste d'une texture se calme d'après le plus
petit niveau de la texture elle-même, sans quoi il changerait par carrés de sections. Tout tient dans des shaders en GLSL 150, sans calcul GPU ni G-buffer : l'Ultra n'est jamais grisé. Il ne s'applique
qu'à la vue du joueur : une vue de portail garde les rebonds de la zone et le reflet du palier High. Un shader refusé par le pilote
coupe les rebonds pour la session, et le terrain garde ceux de la zone.

Mesure du 1er novembre 2026, marche réelle sur 437 blocs d'une grande salle ouverte, sur une copie d'un monde de l'auteur, distance de vue 12,
1080p, RTX 5070 Ti, calcul des tuiles et des rebonds compris : 325 images par seconde en Ultra, 6 ms au 99e centile. À l'arrêt : High 0,8 ms, Ultra 1,5 ms de GPU. Le chronomètre de `GpuTimer` démarre après ce calcul : ce pic a été mesuré en le déplaçant,
sur une copie. Une seule machine : ni les paliers de la spec ni les cartes visées ne sont vérifiés.

## Calendrier des néons

L'état d'un néon dépend de sa position, de la graine de sa dimension et du tick du monde (`NeonSchedule`). La plupart
des tubes brillent sans faillir, et les parts du profil fixent ceux qui vacillent et ceux qui sont morts. Un tube qui
vacille a des coupures de quatre ticks, tirées au sort par créneau. Éteint, il garde 12 % de son intensité.

Par-dessus, `blackout` fixe la part des secteurs de 64 blocs de côté qui sont en panne : tous leurs néons sont morts, et le
cœur d'un tel secteur ne reçoit plus rien de ses voisins. L'image, la lumière cuite et le bourdonnement lisent le même calendrier.
Le Level 0 le laisse à 0 pour le moment : tous ses néons éclairent.

La même fonction existe en GLSL, dans `shaders/include/neon_schedule.glsl`. Les deux reposent sur un hachage entier de
32 bits, sans flottant dans la décision. L'image lit la version GLSL, le son la version Java.

La face de chaque tube du terrain suit son état par ce jumeau GLSL. Le shader la reconnaît au drapeau du terrain et au
rectangle de la texture du néon dans l'atlas (`gtgs_NeonFace`), recalculé par `NeonFaces` à chaque rechargement des
modèles. Il retrouve le bloc du tube depuis la caméra, donnée en bloc entier plus un reste. La précision ne se perd donc
pas loin de l'origine.

Les sons liés à l'image ajoutent jusqu'à quatre perturbations au bloc d'uniformes. Chacune retire à la face des tubes à
sa portée ce que le son leur retire, même aux tubes que la sélection n'a pas retenus (voir
[AUDIO.md](AUDIO.md#liaison-son-image)). Si le jumeau échoue à sa vérification, il est coupé pour la session. La face
d'un néon retenu suit alors l'état envoyé dans le bloc.

### Post masqué et bloom

En fin de rendu du monde, main comprise, `LookPost` copie l'image dans le framebuffer `gtgs_backrooms:screen_copy`. Il
dessine ensuite un triangle plein écran par look visible dont le profil a un post. Le stencil rejette les autres pixels
avant le fragment shader, et le programme `look_post` n'a donc ni `discard` ni `gl_FragDepth`.

Le post ajoute la vignette et l'aberration chromatique. En qualité complète, il ajoute le bloom, calculé sur la copie de
l'écran. Les pixels au-dessus du seuil sont extraits à demi-résolution, puis un flou séparable en deux passes les étale
à quart de résolution. Le post ajoute ce flou, pesé par la force du profil, sur les seuls pixels du look.

Les framebuffers se déclarent dans `assets/gtgs_backrooms/framebuffers/*.json` : nom, échelle, format `RGBA8`, profondeur
et filtrage. Ils se créent à la demande et ne se recréent qu'au redimensionnement. Le triangle plein écran est un tampon
persistant (`FullscreenTriangle`).

L'image figée d'une vue lointaine (`FrozenViews`) prend les bits de look de sa dimension. Le post la traite comme la vue
qu'elle remplace.

### Paliers de qualité

Le palier (`LookQuality`) se choisit dans le menu, ou par l'option `quality`.

| Palier | Néons par vue | Lumière | Post | Halos et reflets |
|--------|---------------|---------|------|------------------|
| Low | 8 | Lumière cuite de zone | Aucun | Non |
| Medium | 8 | Lumière cuite de zone | Vignette et aberration | Halos seuls |
| High | 32 | Cache de surfaces, relief, matières des blocs, ombres des corps | Plus le bloom | Oui |
| Ultra | 32 | High, plus la lumière tracée : pénombres, rebonds | Plus le bloom | Oui, quatre rayons par dalle |
| Désactivé | Aucun | Lightmap et ombrage vanilla | Aucun | Non |

La lumière cuite garde la même émission à tous les paliers : seul le réalisme change, jamais ce qu'on voit des dangers. Les paliers ont
la palette, les niveaux et le contraste de l'Ultra, comme le veut le document de qualité (« Low et Ultra ont la même palette ») : ils
prennent ses réglages (exposition, lumière directe, rebonds, textures calmées par leur plus petit niveau), et la zone compte, comme lui,
les rebonds qui suivent le second. Sans rebonds tracés, la lumière directe des tuiles n'a plus de courbe qui creuse les ombres, et leurs
rebonds, ceux de la zone, ne repassent pas par l'ouverture : la zone assombrit déjà ses coins. Mesure du 2 novembre 2026, grande salle
d'un monde de l'auteur : moyenne de l'image 113,4 en Ultra, 114,4 en High, 111,9 en Medium, 113,8 en Low, contraste de 2,58 à 2,70 ;
teintes des murs et du sol à un degré près. En mode
`auto`, `LookQuality` prend High, ou Low sur une carte graphique intégrée, reconnue à la chaîne `GL_RENDERER` du pilote
(`intel`, `llvmpipe`, `swiftshader`). Le choix est journalisé, et le calcul acoustique le suit par défaut. Les anciens noms
`light` et `full` valent Medium et High.

### Menu

Dans une dimension à look, le menu pause a un petit bouton carré à gauche de sa première rangée, une icône sans texte
(`PauseMenuButton`). Ailleurs, le menu pause reste vanilla. Le bouton ouvre un panneau à gauche de l'écran, sans flou ni
assombrissement : le jeu tourne derrière, et chaque choix se voit aussitôt.

- **Qualité** : Auto, Low, Medium, High, Ultra.
- **Style** : Fidèle, VHS, Cinématique (`VisualPreset`). Un style ne touche ni à la lumière ni au brouillard. Le VHS (`look_post.fsh`)
  montre ce qu'un caméscope a enregistré sur une cassette, relue au magnétoscope. Le cadre est un 4:3 droit au milieu de l'écran, entre
  deux bandes noires. L'objectif courbe les droites vers les bords de l'image, le milieu restant presque droit (termes en r² et r⁴), et
  y laisse une frange rouge et bleue ; le milieu s'agrandit d'autant, pour remplir le cadre jusqu'aux coins. Le signal a 240 lignes par
  trame, qui bougent d'un cinquième de ligne d'une image à la suivante, sans faire sautiller les bords ; la luminosité est molle puis accentuée par le magnétoscope,
  qui cerne chaque bord d'un liseré clair et sombre ; la couleur bave sur une large part de la ligne et arrive en retard, vers la droite ;
  les fortes lumières laissent une traînée à leur droite. Le temps des lignes n'est pas tout à fait tenu : chaque ligne glisse à peine,
  le haut penche un peu, le bas se déchire au changement de tête, le cadre restant droit. Le bruit court le long des lignes, discret
  dans le clair, fort et en couleur dans les sombres, avec de rares pertes blanches et, quelques secondes de temps en temps, une bande
  de suivi ; les traits blancs ne prennent qu'un tiers de la hauteur d'une ligne. Un très léger tremblé à l'épaule déplace l'image dans
  le cadre (jamais sans secousse). Ni gris ni noirs relevés : la couleur reste celle du look, un peu plus dense, et les coins
  s'assombrissent. Sans grain, le bruit de la cassette tombe à 30 %. Le cinématique pose des bandes noires, un grain de film et un
  contraste plus dense. En quittant les Backrooms (portail, commande, réapparition), le style revient à Fidèle et s'enregistre ; le
  palier et le confort restent (`LookResources.onClientTick`). Une partie rejointe hors des Backrooms n'est pas une sortie.
- **Ombres pixelisées** : oui (32 pixels par bloc, ceux des textures) ou non. Sans, la lumière s'interpole entre les pixels de la tuile.
- **Confort** : sans flash, sans secousse, sans grain.
- **Avancé** : chaque effet un par un, et le temps GPU du monde, du bloom et du post sur la dernière seconde, mesuré tant que cet
  écran est ouvert (`GpuTimer`). Le palier désactivé ne se choisit plus que par l'option de lancement `quality`.

`LookSettings` tient les réglages, enregistrés dans `config/gtgs_backrooms.json` (`LookSettingsStore`) : les options de
lancement ne donnent que les valeurs de départ. `LookResources` garde les looks tels que les fichiers les décrivent, et refait
à chaque changement le look en vigueur : options de confort, puis preset, puis ce que le palier et les options retirent
(`LookSettings.effective`). Le cache de surfaces se recalcule alors depuis ce qui est déjà cuit, et le terrain se reconstruit
seulement si l'ombrage de ses faces change (palier désactivé).

### Stencil

Le stencil des portails porte le niveau d'imbrication et le look de chaque pixel (`StencilLookMask`).

| Bits | Sens |
|------|------|
| `0x03` | Niveau d'imbrication : 0 pour le monde du joueur, 1 pour une vue, 2 pour une vue imbriquée |
| `0x1C` | Look de la dimension que montre le pixel : 0 pour vanilla, 1 à 7 sinon |
| `0x40`, `0x80` | Sous un pack de shaders seulement : main du joueur et cadre imbriqué |

Tout test de niveau masque avec `StencilLookMask.LEVEL_MASK`. Une vue n'écrit ses bits de look que sur les pixels
qu'elle prend, ceux qui passent du niveau n à n+1, et le retour de niveau les laisse. En fin d'image, les bits de look
d'un pixel valent donc le look de la vue la plus profonde qui y est visible. La main du joueur, dessinée après les vues
et par-dessus tout, écrit sur ses pixels les bits de look du monde du joueur (`HandMask`) : elle est de son monde, même
devant un cadre, et le post du look que montre le cadre ne dessine pas la silhouette du cadre sur elle. Il en va de
même pour les moitiés d'entités à cheval sur un cadre (`StraddlingEntityRenderer`). Vanilla ne les dessine pas, et
elles passent après les vues : leurs pixels prennent les bits de look du monde du joueur, au niveau du monde
(`PortalStencilRenderer.renderMainWorldParts`). Sans cela, un cochon frappé contre un cadre montrait la silhouette du
cadre sur son rouge.

L'effacement n'écrit que les bits du masque courant, d'où un masque `0xFF`. Il a lieu au début du rendu du monde, même
sans portail visible, puis au début de la passe des portails. L'option `debugStencil` affiche le stencil à la place de
l'image.

### Une image de bout en bout

1. En tête du rendu du joueur, `LookFrame` ouvre l'image. `LookState.beginWorld` pose le profil du monde du joueur, la
   caméra et l'horloge commune avec l'audio (`CueClock`). Le stencil est effacé au niveau 0 avec le look du joueur,
   et la LUT est reliée à son unité.
2. Les perturbations des sons liés sont mises à jour, et `NeonLights` remplit le bloc des néons proches du joueur.
3. La lightmap et le brouillard prennent les réglages du profil.
4. Les couches opaques et les entités se dessinent. `ShaderProgram.bind` ne renvoie le look que si sa version a changé.
5. Pour chaque portail visible, la zone du cadre passe au niveau n+1 et prend le look de la dimension vue.
   `LookState.push` et `NeonLights.push` posent le look et les néons de la vue, qui se dessine avec sa lightmap et son
   brouillard. Le niveau revient ensuite à n, et les bits de look restent.
6. Les translucides, les particules, les nuages et la météo se dessinent, puis la main, qui reprend dans le stencil le
   look du monde du joueur.
7. `LookPost` copie l'écran, calcule le bloom et dessine un triangle par look visible.
8. `LookState.endWorld` remet le look vanilla pour l'interface.

### Coupe-circuits et état GL

Chaque coupe-circuit est journalisé une fois.

- Une source patchée que le pilote refuse reste vanilla (`ShaderCompileCheck`).
- Une exception pendant le post le coupe pour la session, et l'étage 1 reste en place.
- Sans stencil, il n'y a ni post ni bits de look.
- Un framebuffer non déclaré fait sauter sa passe.

Les mesures GPU (`GpuTimer`) n'ouvrent qu'une requête à la fois, et aucune sous un pack. Chaque passe rend l'état GL
que son appelant tenait. Hors pack, c'est l'état de repos de vanilla : test de profondeur et
stencil coupés, écriture de la profondeur active. Dans une vue sous un pack, le test de stencil de la vue doit rester
posé, car la suite de la vue en dépend. L'option `checkGlState` relit l'état au pilote autour de chaque passe et
journalise chaque écart.

Le code se range en `patch/`, `profile/`, `state/`, `light/`, `quality/`, `post/`, `fbo/` et `client/`. Tout sauf
`client/`, la colle OpenGL, est de la logique pure.

## Ce que vérifie le patch des shaders

`ShaderPatcherTest` relit les 110 shaders de base de Minecraft 1.21.1 dans le jar du jeu, compilés comme le jeu les
compile. Il les compare à l'inventaire `src/test/resources/shader_inventory_1_21_1.json`, si bien qu'une mise à jour du
jeu se voit. Les 55 fragment shaders ont une seule sortie `fragColor`, une seule `main` et aucune directive
conditionnelle. Tous sont patchables, comme les 55 vertex shaders, et les 39 qui font `discard` le gardent.

Une source hors de cette forme reste vanilla, journalisée une fois. C'est le cas d'une version autre que 150, de deux
`main`, d'un fragment qui déclare une autre sortie que sa seule `out vec4` (de tout type, tableau compris), d'une
directive conditionnelle ou inconnue, ou d'une macro dans la sortie ou la signature de `main`. Il n'y a jamais de patch « au mieux », même pour un shader remplacé par un pack de ressources.

`PatchedShadersGoldenTest` passe chaque shader de base par la découpe des portails puis par les patchs. Il compare
l'empreinte des 90 sources changées à `src/test/resources/patched_shaders_1_21_1.json`. Un changement voulu se recopie
depuis la table que donne le test.

Au chargement, `ShaderCompileCheck` fait compiler chaque source patchée par le pilote avant de la donner au jeu. Une
source refusée est remplacée par la source vanilla : un look manque plutôt qu'un monde rose. Chaque rechargement
journalise `[Look] N etage(s) de shaders patche(s) compile(s) par le pilote, M refuse(s)`, et 72 étages portent le look
en 1.21.1. Cette ligne est la seule preuve que l'étage 1 est en place, car la lightmap et le post changent l'image sans
lui.

`NeonScheduleTest` vérifie `NeonSchedule` sur les 573 vecteurs de `src/test/resources/neon_schedule_vectors.json`,
écrits par un programme indépendant. Le client vérifie `neon_schedule.glsl` sur les mêmes vecteurs, sur le processeur
graphique, quand l'option `verifyNeonShader` donne le chemin du fichier.

`LookLutTest` vérifie la LUT (identité, désaturation puis teinte, étalonnage, ordre de la table) et que le shader du post
garde la même `gtgs_grade` que `look_grade.glsl`. `TexelGridTest` vérifie la grille des texels sur les six faces, sous
zéro et aux égalités de la normale. Dans un vrai client, `LookGradeClientTests` compile les fonctions des bibliothèques
telles que le jeu les charge dans un shader de test, et compare ce que rend le processeur graphique à `LookLut` et à
`TexelGrid`. Il vérifie aussi que les étages patchés compilent tous, que la LUT est liée à son unité et lue par le
terrain et le post, que le pas de la grille arrive au terrain, que le terrain lit la zone dans le Level 0, et qu'il
l'ignore dans l'Overworld, où l'ombrage vanilla reste.

`LookSettingsTest` vérifie les réglages : fichier, options, presets, et ce que chaque palier retire au look, sans jamais toucher
à la lumière. `SurfaceAtlasTest` vérifie la vie des tuiles : bande de hauteur, file, section recuite qui garde ses tuiles, faces
qui suivent un bloc, ombres touchées par un bloc et rien d'autre, cache plein, section servie à moitié puis reprise, premier néon d'une
section.

`LightBakeBasicsTest` vérifie le stockage, la copie du monde, le facteur de forme contre sa formule et les rayons sur les
six directions. `LightBakeSceneTest` cuit une pièce connue : sol sous la dalle, plafond sans lumière directe, ombre d'un mur,
néon mort, rebond teinté par un mur jaune, coins assombris, puis l'ordre de cuisson, le recuit après un bloc changé et le
déplacement de la zone ; le loin (moyenne de ses cellules, trois états après la fenêtre, recul après un bloc changé, oubli et
retour, monde haut), qui vaut la même section cuite dans la fenêtre à 5 % près en moyenne et assombrit ses coins comme elle ; la
pièce moyenne, celle du quart le plus
clair des sections ; et la part des rebonds suivants, la même que celle du shader des rebonds tracés.

## Compatibilité

### Packs de shaders

Sous un pack, les shaders du pack remplacent ceux de vanilla, et sa passe finale ignore le stencil. Le mod reconnaît un
pack sans nommer de mod (`ShaderPackState`). Juste avant la couche translucide, les programmes que vanilla donne aux
entités et aux translucides ne sont plus ceux du jeu.

Les vues de portail se dessinent alors par le pack, après son image. Leur fonctionnement et leurs limites sont décrits
dans [PORTAILS.md](PORTAILS.md). L'option `shaderCohabitation` des portails peut les couper, et les cadres restent alors
sombres.

### Module `compat/iris`

Tant que le joueur est dans une dimension à look, le gestionnaire de pipelines d'Iris rend son pipeline vanilla pour
tout ce qui se dessine, vues comprises (`MixinPipelineManager`). Le look du mod s'applique alors comme sans pack.

Une image n'utilise qu'un pipeline. Le mod de terrain qu'exige Iris n'a qu'un format de maillage à la fois, et deux
pipelines dans la même image dessinent le terrain en éclats. Dans le Level 0, l'Overworld vu par un portail est donc
vanilla.

Chaque pipeline d'Iris pose des réglages globaux à sa création, qu'un pipeline repris du cache ne repose pas. À chaque
bascule, le module garde ceux du pipeline quitté et repose ceux de l'autre (`PipelineSettings`). Le mod de terrain
recharge ses shaders de sections, et le terrain se reconstruit dans le nouveau format.

La bascule se fait au rendu du joueur, jamais au milieu d'une vue, après une traversée comme après un changement de
monde vanilla. Le pipeline du pack reste dans le cache d'Iris, sans recompilation. Sur la machine de référence, l'image
de bascule dure de 96 à 117 ms en entrant et 52 ms en sortant, contre environ 1 s pour couper puis rallumer le pack.

Depuis l'Overworld sous un pack, une vue du Level 0 reste rendue par le pack. Le post pose le look entier de sa
dimension sur les pixels du cadre (`LookPost.renderUnderShaderPackView`).

Le module ne s'applique que dans la plage d'Iris vérifiée, `>=1.8.8 <=1.8.14-beta.1` (`IrisVersion`). Hors de la plage,
un message traduit prévient le joueur à chaque connexion (`IrisVersionNotice`). Le pack rend alors aussi le Level 0, et
le post pose le look sur son image. L'option `replaceShaderPack` coupée donne le même résultat.

Les mixins du module sont déclarés dans `gtgs_backrooms.compat.mixins.json` et filtrés par `CompatMixinPlugin`. Iris et
Sodium sont compilés en `modCompileOnly`, jamais embarqués ni requis.

### Mod de terrain

Un mod qui dessine le terrain par ses propres shaders se reconnaît à sa grille vanilla de distance nulle. Les shaders
patchés n'atteignent pas ses blocs. L'étage 1 passe alors dans le post, pour le terrain comme pour les entités
(`LookState.shadersCarryLook`). La lumière des néons et la face des tubes manquent, le reste du look demeure.

Iris exige un tel mod. Dans le Level 0 sous un pack, le rendu est donc celui de ce cas.

### Résumé

| Configuration | Look | Lumière et face des néons | Vues de portail |
|---------------|------|---------------------------|-----------------|
| Vanilla, ou mods qui ne dessinent pas le terrain | Complet | Oui | Oui |
| Mod de terrain seul, ou Iris sans pack | Étage 1 par le post | Non | Oui |
| Pack actif, joueur dans le Level 0 | Celui du mod, étage 1 par le post | Non | Oui, vanilla |
| Pack actif, joueur hors des Backrooms | Posé par le post dans chaque vue d'une dimension à look | Non | Rendues par le pack |
| Pack actif, `replaceShaderPack` coupée ou Iris hors de la plage | Posé par le post sur l'image du pack | Non | Rendues par le pack |

## Réglages

Les options du moteur activent ou coupent le moteur, choisissent la qualité, règlent le confort et ouvrent les outils
de débogage. Leur liste est dans [OPTIONS.md](../reference/OPTIONS.md#rendu), et celle de la cohabitation des vues avec
un pack dans la section [Portails](../reference/OPTIONS.md#portails).

## Diagnostic

La commande du client `/backrooms_state` écrit dans le journal l'état de la dernière image et des sons traités. Sa
sortie est décrite dans [AUDIO.md](AUDIO.md#diagnostic).

## Limites

- Sous un pack, chaque bascule reconstruit le terrain, et les sections réapparaissent en quelques images.
- La lumière cuite n'atteint que le terrain des shaders patchés, et un mod de terrain garde la lightmap. En Low et en Medium, la lumière
  des entités ignore les murs : un néon derrière une cloison les éclaire. Il n'y a qu'une zone à la fois : une vue vers un autre endroit du même
  monde, loin de la caméra, garde la lightmap, et son image change à la traversée.
- Seuls les corps vivants portent une ombre (ni objets, ni véhicules, ni invisibles), huit au plus, sur le terrain seulement : un
  corps n'en porte pas sur un autre, et il n'y en a pas en Low ni en Medium, ni dans une vue de portail, plafonnée sous ce palier.
  Sa silhouette est faite de cylindres : l'ombre ne suit ni ses bras ni sa pose. Sous ses pieds, elle retire au plus 80 %.
- Le vacillement du calendrier n'entre pas dans la lumière cuite : seuls les néons morts et les secteurs en panne sont retirés, et
  les cris modulent la surface. Une ombre de bloc non opaque (dalle,
  escalier, vitre) n'existe pas : seuls les blocs pleins arrêtent la lumière. Le relief suppose l'orientation d'un cube simple : une face
  tournée par son modèle garde un relief décalé.
- La zone et les tuiles ne donnent pas tout à fait la même lumière (en Ultra, la zone vaut environ 0,9 fois les tuiles) : le passage
  de 24 à 32 blocs se fond, sans disparaître tout à fait.
- Les rebonds et les surfaces des paliers sans cache de surfaces suivent un bloc changé avec la cuisson, quelques dixièmes de
  seconde plus tard.
- Le loin est la zone en cellules de deux blocs : un détail plus fin qu'elles se fond. Au-delà de 128 blocs, la pièce moyenne ne
  connaît ni les ombres, ni les pannes, ni quel mur fait face aux dalles : une salle très éclairée y paraît plus sombre que de près
  (jusqu'à 0,69 fois dans une salle mesurée), une salle peu éclairée plus claire, le changement se fondant de 96 à 128 blocs.
- En Ultra, un rayon ne voit que la grille des blocs : un bloc non plein n'arrête pas les rebonds, les corps ne font pas d'ombre aux
  rebonds. Un reflet ne montre que les dalles : ni les murs ni les corps, qui lui coupent seulement une dalle. Un rebond suit un bloc posé une fois le cache au repos, en environ une seconde. Pas de rendu réduit
  ni de TAA : rien n'est accumulé à l'écran. Le coût vient de chaque carte : mesuré sur une seule machine (ci-dessus). Les dalles restent
  des carrés d'un bloc, celles de la génération : une dalle en longue bande demanderait de toucher au Level 0. Au-delà de la zone
  des tuiles, à 40 blocs environ, la lumière retombe sur celle de la zone, puis du loin.
- Une surface hors de la grille (plinthe rentrée dans son bloc) n'a pas de tuile et prend le loin.
- Une surface au-dessus ou au-dessous de la hauteur de la zone, vue par un puits entre deux couches, prend la pièce moyenne.
- Le cache de surfaces a une taille fixe : une salle de plus de trente-deux mille surfaces sur le rayon des tuiles laisserait ses sections
  les plus loin à la lumière de la zone. Le Level 0 en demande dix-huit mille au pire de ce qui a été mesuré.
- Le Level 0 a de vrais couloirs sans dalle : ils restent très sombres. Les éclairer demanderait de toucher à sa génération.
- Hors des Backrooms, aucun mode ne rend les vues en vanilla sous un pack, car deux pipelines dans la même image
  dessineraient le terrain en éclats.
- Sous un pack, une vue du Level 0 vue de l'Overworld n'a ni la lumière des néons ni la lightmap du profil. Son post
  alloue environ 0,3 Kio par appel, d'origine non élucidée.
- Un pack se détecte au milieu du rendu du joueur. Ce qui se décide avant lit donc l'état de l'image précédente, d'où
  une image de retard à l'activation ou à la coupure d'un pack.
- L'index des néons relève les chunks après leur arrivée, en quelques ticks pour une rafale. Un tube d'un chunk tout
  juste reçu n'éclaire qu'une fois relevé. Un profil rechargé qui change de bloc de néon ne vaut que pour les chunks
  reçus ensuite.
- Au-delà des néons retenus pour une vue, un tube brille sans éclairer autour de lui. La portée 8 et la force 0,6 du
  Level 0 sont un réglage de départ, à ajuster à l'œil.
- Le bloom n'est masqué qu'à la composition. Au bord d'un cadre, le halo d'un tube peut déborder de quelques pixels vers
  l'intérieur du cadre, jamais sur les pixels vanilla.
- L'aberration chromatique lit les pixels voisins sans vérifier leur look. Au bord d'un cadre, une frange de deux ou
  trois pixels mêle les deux côtés, ce qui est accepté pour un décalage de 3 % au plus.
- Une vitre ou de l'eau devant un cadre porte les bits de look de la vue derrière elle, et le post la traite avec la vue.
- La face d'un néon se reconnaît à sa texture. Un pack de ressources qui lui donne une texture par face sort de cette
  règle, et un bloc qui partagerait sa texture suivrait le calendrier. Un néon tenu ou porté par une entité suit l'état
  vanilla.
- Même moteurs coupés, un pixel d'une épée tenue peut différer d'un rendu sans le mod, à la frontière de deux texels.
  La cause la plus probable est la disposition de l'atlas, que les textures du mod décalent.

## Tests

La stratégie de test et la couverture sont décrites dans [TESTS.md](../dev/TESTS.md). La colle OpenGL de
`core/render/client/` y est nommée, et le reste du moteur est couvert à 100 %.

La logique pure a ses tests unitaires, de `ShaderPatcherTest` à `NeonMapTest`. `BiomeColorSamplingTest` compare la
couleur des biomes à celle de vanilla, au bit près, sur des milliers de points et pour chaque effet de dimension. `MixinTargetsTest` vérifie
que les mixins du moteur s'appliquent. Dans un vrai client, `LevelZeroClientTests` vérifie le look du Level 0, les néons
envoyés, la couleur du brouillard et le jumeau GLSL, et que la couleur des biomes du mod est celle de vanilla dans
l'Overworld comme dans le Level 0 (`biome_colors_match_vanilla`). `SettingsMenuClientTests` vérifie le menu : icône absente hors des Backrooms, à l'écran et juste à gauche de la première rangée dans
le Level 0, et chaque choix appliqué en direct ; le style VHS pose sa bande noire dans le Level 0, revient à Fidèle en sortant,
le palier restant, et ne laisse rien dans l'Overworld (`tape_only_in_backrooms`). `EngineRulesClientTests` vérifie le seuil du bloom, le stencil d'un
framebuffer recréé et le sampler du dessin plein écran. `LookGradeClientTests` compare l'étalonnage et la lumière au
texel du processeur graphique à leurs jumeaux Java. `PortalViewClientTests` vérifie le look dans un cadre et
l'absence de couture à la traversée.

Avant de mesurer une image à la main, on vérifie deux preuves : le bilan des étages patchés dans le journal, et le
look attendu sur tout l'écran en mode `debugStencil`. Hors du cadre d'un portail, un monde sans profil se rend comme sans le mod, à
2/255 près. Ces écarts touchent quelques arêtes et entités, car le pilote recompile les shaders enveloppés.

Sur la machine de référence en 1080p, le moteur entier coûte environ 0,16 ms de GPU par image, dont 0,12 ms pour
l'étage 1. Le post coûte environ 0,05 ms de processeur, sans allocation. Le coût sur une carte graphique intégrée reste
à mesurer.
