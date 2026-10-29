# Moteur de shaders maison — GTG's Backrooms

> **Document de conception interne** : il nomme des projets de référence. À garder hors du dépôt public, et sans
> aucune reprise de code ni mention de ces projets dans le mod (`QUALITE_ET_PRESETS.md` §3.0).
>
> Document de conception : pourquoi un moteur maison plutôt que Veil, quelle architecture, comment l'intégrer aux
> portails, et tout ce à quoi il faut faire attention. Cible : Fabric, Minecraft 1.21.1, Java 21, mappings Yarn.
>
> **Statut : feuille de route de conception, pas encore spécification définitive.** Les hypothèses OpenGL les plus
> risquées sont listées au §15 : elles doivent être validées sur le code réel du mod (phase 0 du §12) avant de figer
> l'architecture.
>
> **Prochaine étape : le code, pas le document.** Le document est assez détaillé pour orienter le travail ; le
> prochain gain viendra des prototypes (inventaire des écritures de stencil, chemin de rendu réel, compilation des
> shaders patchés, look correct dans les deux sens des portails). Il ne sera enrichi que de ce que les prototypes
> confirment ou infirment.

---

## 1. Objectif

Donner au Level 0 (et aux futurs levels) un **rendu propre**, sans OptiFine ni Iris, qui :

1. est **le rendu par défaut** des Backrooms, sans installation ni réglage pour le joueur ;
2. **ne touche jamais l'Overworld** ni les autres dimensions vanilla ;
3. suit **les portails** : la vue d'un portail vers le Level 0 est rendue avec le look Backrooms, la vue d'un portail
   vers l'Overworld reste vanilla, y compris pour les vues imbriquées et la troisième personne ;
4. **ne trahit pas le mod** : le joueur doit penser « c'est Minecraft, mais cet endroit est bizarre », jamais
   « il y a un shaderpack » ;
5. **tourne sur les petites configs** (iGPU Intel/AMD, macOS en OpenGL 3.2 core) ;
6. ne crée **aucune coupure** à la traversée d'un portail (pas de recompilation, pas de rechargement).

**Portée de ces objectifs selon les paliers de qualité.** Les objectifs 4 et 5, ainsi que les choix « pas de rendu
différé » (§4) et « pas de compute » (§6.7), décrivent les paliers **Low** et **Medium** (`minimum` et `léger`), ceux
de la cible PC de bureau (§16). Les paliers **High** et **Ultra (expérimental)** les dépassent **volontairement** pour
atteindre le visuel de référence (rendu différé en High, compute et OpenGL 4.3 en Ultra, hors macOS) : voir
`QUALITE_ET_PRESETS.md` §2.3 et §4. Les objectifs 1, 2, 3 et 6 et le périmètre du §1.1 valent pour **tous** les paliers.

### 1.1 Périmètre : les Backrooms seulement

**Règle unique : le rendu d'un pixel dépend de la dimension qu'il montre, pas de celle où se trouve le joueur.**

| Situation                                                                  | Rendu                                                                              |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Pixel qui montre une dimension vanilla (Overworld, Nether, End…)           | **Strictement vanilla** : aucun look, aucun post, aucune réaction visuelle         |
| Pixel qui montre une dimension Backrooms, joueur dans les Backrooms        | Look et post de la dimension                                                       |
| Vue d'un portail vers les Backrooms, **vue depuis l'Overworld**            | Look et post **dans le cadre seulement** ; le reste de l'écran reste vanilla        |
| Vue d'un portail vers l'Overworld, vue depuis les Backrooms (même imbriquée) | Strictement vanilla (exigence absolue du §7)                                     |
| Interface, HUD, menus, F3                                                  | Toujours vanilla : jamais touchés par le moteur de shaders (l'interface propre au mod dans les Backrooms — inventaire, menu de qualité — relève de `GAMEPLAY.md` et `QUALITE_ET_PRESETS.md`, et n'existe pas hors des Backrooms) |

Conséquences, à respecter dans chaque composant :

1. **Mises en place globales neutres** : les shaders vanilla patchés (§6.1) sont chargés pour tout le jeu, faute de
   pouvoir les patcher par dimension. Avec `gtgs_LookId = 0`, la fonction de look rend la couleur **inchangée** : le
   rendu de l'Overworld doit rester identique à celui d'un client vanilla. Même chose pour les framebuffers du post,
   créés mais jamais appliqués à un pixel vanilla grâce au masque du stencil (§6.5).
2. **Réactions visuelles venues du son** (`MOTEUR_AUDIO.md`, §1.1) : un son émis dans les Backrooms et entendu à
   travers un portail ne fait réagir que les pixels de la vue du portail.
3. **Conséquence sur le jeu de base** : bloquer Iris (§8.2, §14.5) le retire aussi de l'Overworld pour le joueur qui
   l'utilisait. C'est voulu : le jeu de base reste vanilla, sans shaderpack.

**« Strictement vanilla » est une exigence à démontrer, pas à déduire.** Même quand la fonction de look rend sa
couleur inchangée avec `gtgs_LookId = 0`, le shader compilé n'est plus le shader d'origine (enveloppe de `main()`,
uniformes ajoutés, plan de découpe), et le pilote peut le compiler autrement. Prouver que la fonction ne change pas la
couleur ne suffit donc pas : il faut comparer le **rendu réel**. Définition retenue :

- **Méthode** : captures d'un client avec le mod et d'un client vanilla, dans les **mêmes conditions** (même graine,
  même position et orientation de caméra, même heure du monde figée, météo figée, mêmes réglages graphiques, même
  pilote, même résolution, animations de textures et particules aléatoires figées ou exclues de la comparaison).
- **Critère** : **identité au pixel près** visée. Toute différence est analysée ; une tolérance (par exemple un écart
  de 1/255 par canal sur une petite fraction de pixels, dû à une réorganisation des calculs par le pilote) n'est
  acceptée **que justifiée et documentée**, jamais par défaut.
- **Cas obligatoires** : terrain opaque, cutout et translucide (eau, verre), entités et leurs parties translucides,
  particules, ciel et nuages, main et objets tenus, chacun des trois modes graphiques, et l'Overworld vu à travers
  un portail depuis les Backrooms (exigence absolue du §7).

---

## 2. Pourquoi ne pas utiliser ni forker Veil

### 2.1 Raisons techniques

Veil part d'hypothèses contraires à celles du mod :

| Veil suppose                                    | GTG's Backrooms fait                                                         |
| ----------------------------------------------- | ---------------------------------------------------------------------------- |
| Une caméra, un `WorldRenderer`                  | Plusieurs `WorldRenderer` par image (vues distantes, vues secondaires)       |
| Post-processing appliqué une fois, tout l'écran | Chaque zone de l'écran appartient à une dimension différente                 |
| Pipeline différé optionnel sur le monde courant | Vues sous ciseau, stencil par niveau, plan de découpe `gl_ClipDistance`      |
| Ses propres mixins dans le rendu du monde       | `MixinWorldRenderer`, `MixinShaderStage`, `MixinShaderProgram` déjà présents |

Forker Veil, c'est passer plus de temps à **retirer** (Necromancer, rendu différé, thèmes, couleurs) et à
**débrancher** ses hypothèses qu'à ajouter des fonctionnalités. Le résultat devrait aussi passer Checkstyle, les
tests et la couverture à 100 % du projet : en pratique, une réécriture.

### 2.2 Raison juridique (je ne suis pas juriste, à faire valider si besoin)

- Veil est sous **LGPL-3.0**. GTG's Backrooms est sous licence **propriétaire**.
- Un code **dérivé** de Veil reste une œuvre dérivée sous LGPL, **même très modifié**. « Changer le code au point que
  ce ne soit plus le même projet » ne fait pas tomber la licence.
- La LGPL **n'interdit pas** de combiner une bibliothèque LGPL avec un projet propriétaire : c'est même sa raison
  d'être. Utiliser Veil comme **dépendance séparée et non modifiée** est le cas simple.
- En revanche, intégrer au JAR une **copie modifiée** impose sur cette partie les obligations LGPL : fournir son code
  source modifié sous LGPL, et permettre à l'utilisateur de la remplacer par sa propre version. Dans un JAR de mod
  unique, c'est contraignant à respecter et cela s'accorde mal avec une licence « tous droits réservés ». Ce n'est
  pas impossible, mais c'est un risque et une charge inutiles.
- Les obligations exactes dépendent de la manière dont le code est utilisé, modifié et distribué. **Si le mod doit
  être commercialisé, faire vérifier ce point par un juriste.**
- **Les idées, les architectures et les techniques ne sont pas protégées** (seule l'expression du code l'est). Une
  réimplémentation indépendante est la stratégie la plus sûre.

### 2.3 Conclusion

**Veil sert de référence de lecture, pas de base de code.** On lit, on comprend les concepts, on ferme, on écrit
notre version à partir de nos besoins et de notre infrastructure existante.

---

## 3. Utiliser Veil comme référence : règles

1. **Lire pour comprendre, pas pour recopier.** Prendre des notes en mots (« les points d'injection sont nommés et
   résolus au chargement »), pas en code.
2. **Ne jamais copier-coller**, même une fonction utilitaire, même un fichier GLSL, même « juste pour tester ».
3. **Écrire notre code sans Veil ouvert à côté.** Revenir à la référence pour une question précise, puis refermer.
4. **Structure et nommage propres au projet** (packages, classes, conventions Checkstyle existantes) : ne pas
   reproduire l'arborescence de Veil.
5. **Aucune mention d'autre projet** dans le code, les commentaires, le JAR, l'interface, les crédits ni la
   documentation publiée du mod (le dépôt GitHub est public). Les noms de référence n'apparaissent que dans ces
   documents de conception internes, gardés hors du dépôt public. *(Cette règle remplace l'ancienne consigne de
   « tracer l'inspiration » dans `docs/RENDER.md`.)*
6. **Le mod est entièrement propriétaire** : chaque partie est notre propre version, écrite pour fonctionner
   parfaitement avec le reste (portails, stencil, moteur audio). Règle complète : `QUALITE_ET_PRESETS.md` §3.0.

Ce qui vaut la peine d'être lu dans Veil :

| Sujet                        | Ce qu'on en retient                                                                    |
| ---------------------------- | -------------------------------------------------------------------------------------- |
| Modification de shaders      | Points d'insertion nommés, patchs déclaratifs, résolution au chargement                |
| Framebuffers en données      | Déclaration JSON (taille relative à l'écran, formats), recréation au redimensionnement |
| Chaîne de post-processing    | Ordre des passes, liaison des textures d'entrée, uniformes communs                     |
| Gestion des programmes       | Rechargement à chaud avec les ressources, erreurs de compilation lisibles              |
| Easings                      | Liste des courbes classiques (triviale à réécrire)                                     |
| Workflow RenderDoc           | Lancement du client de dev sous RenderDoc pour inspecter stencil et passes             |

---

## 4. Capacités visées (équivalences Veil)

| Capacité Veil          | Équivalent GTG                                         | Priorité                    |
| ---------------------- | ------------------------------------------------------ | --------------------------- |
| Shader Modification    | `ShaderPatchRegistry` (généralise `ShaderClipInjector`) | **P0**                      |
| Shaders (programmes)   | `GtgShaderPrograms` : programmes custom vertex/fragment | P1                          |
| Framebuffer (JSON)     | `FramebufferRegistry` déclaratif                       | P1                          |
| Post Processing        | `LookPostPipeline`, **masqué par le stencil**           | P1                          |
| Lumières               | Néons « forward » en uniformes, pas de rendu différé (Low et Medium ; High passe en différé : `QUALITE_ET_PRESETS.md` §4.4) | P2                          |
| Easings                | `Easing` (enum)                                        | P2                          |
| Colors / Themes        | Couleurs dans les `LookProfile`                         | Inclus dans les profils     |
| Necromancer            | Hors périmètre (animation de modèles, pas du shader)    | —                           |

Capacité **en plus** de Veil, propre au mod : **le look par vue de portail** (`LookState`).

---

## 5. Architecture

### 5.1 Vue d'ensemble

```
client/render/look/
|-- patch/      ShaderPatchRegistry, ShaderPatch, InjectionPoint, PatchedSourceCache
|-- profile/    LookProfile (données), LookProfileLoader (ressources), LookProfiles (registre par dimension)
|-- state/      LookState (pile push/pop), LookUniforms (envoi aux programmes), LookTransition (interpolation)
|-- light/      DimensionLightmap (courbe par dimension), DimensionFog
|-- post/       LookPostPipeline, LookPostPass, StencilLookMask
|-- fbo/        FramebufferRegistry, FramebufferSpec
|-- program/    GtgShaderPrograms (programmes propres au mod)
|-- quality/    LookQuality (désactivé / léger / complet), détection matérielle
`-- compat/     RenderCompat (Sodium, Iris, autres)
```

### 5.2 Une image, de bout en bout

```
début d'image
  LookState.reset(profil de la dimension du joueur)
  stencil effacé à 0x80 si le joueur est dans une dimension Backrooms, sinon 0x00
     (masque d'écriture forcé à 0xFF le temps de l'effacement, puis restauré : §6.5)
  |
  monde du joueur : couches opaques, entités        <- shaders vanilla patchés, gtgs_LookId = profil du joueur
  |
  portails (PortalStencilRenderer), pour chaque vue :
     zone : niveau n -> n+1, bit 0x80 = "dimension vue est Backrooms"
     LookState.push(profil de la dimension vue)
     RemoteViewRenderer.render(...)                  <- mêmes shaders, autre gtgs_LookId
       portails imbriqués : idem, un niveau plus loin
     LookState.pop()
     zone : retour au niveau n (bits 0x03 seulement, le bit 0x80 reste)
  |
  monde du joueur : couches translucides, particules, nuages, météo
  |
  LookPostPipeline : copie couleur -> texture, quad plein écran
                     avec glStencilFunc(GL_EQUAL, 0x80, 0x80)
  |
  main, interface (non affectées, ou affectées selon le choix du §7, point 3)
fin d'image
```

Deux étages, et c'est tout le moteur :

- **Étage 1 — injection** : effets par fragment, sans lire les pixels voisins (étalonnage, désaturation, grain,
  vacillement des néons, brouillard). Coût quasi nul.
- **Étage 2 — post masqué** : effets qui lisent l'image (vignette, aberration légère, bloom). Un seul pass, limité
  aux pixels Backrooms par le stencil.

---

## 6. Composants

### 6.1 `ShaderPatchRegistry` (P0)

Généralisation de `ShaderClipInjector` : patcher **vertex et fragment** des programmes vanilla au chargement.

**Patch déclaratif** (ressource `assets/gtgs_backrooms/shader_patches/*.json`) :

```json
{
  "targets": ["rendertype_solid", "rendertype_cutout", "rendertype_cutout_mipped",
              "rendertype_translucent", "rendertype_entity_*", "particle"],
  "stage": "fragment",
  "imports": ["gtgs_backrooms:look.glsl"],
  "family": "world_fragment",
  "inject": [
    { "point": "output", "function": "gtgs_applyLook" }
  ]
}
```

**Technique d'injection : envelopper `main()` plutôt que chercher une ligne.** Chercher « la dernière écriture de
`fragColor` » dans le texte est fragile : certains shaders ont plusieurs chemins de sortie (`discard`, `return`
anticipé, écritures conditionnelles) ou une variable de sortie d'un autre nom. Le patcheur procède donc ainsi :

1. repérer la variable de sortie **déclarée** (`out vec4 <nom>;`) au lieu de supposer `fragColor` ;
2. renommer `void main()` en `void gtgs_vanilla_main()` ;
3. ajouter une nouvelle `main()` qui appelle la main vanilla, puis applique le patch à la variable de sortie.

```glsl
void gtgs_vanilla_main() { /* corps vanilla, inchangé */ }

void main() {
    gtgs_vanilla_main();
    fragColor = gtgs_applyLook(fragColor);   // <nom> réel de la sortie déclarée
}
```

Un `return` anticipé sort de la main vanilla et passe quand même par le look ; un `discard` garde son effet vanilla.
Le code vanilla n'est jamais modifié ligne à ligne. C'est le prolongement du mécanisme déjà éprouvé de
`ShaderClipInjector` (insertion en tête de `main`), à réutiliser autant que possible.

**Points d'injection** :

| Point         | Où                                                                              |
| ------------- | ------------------------------------------------------------------------------- |
| `head`        | Après `#version` et les `#moj_import` (uniformes, imports, varyings)            |
| `main_before` | Dans la nouvelle `main()`, avant l'appel de la main vanilla                     |
| `main_after`  | Dans la nouvelle `main()`, après l'appel de la main vanilla                     |
| `output`      | Fragment : la variable de sortie déclarée passe par la fonction donnée (dans `main_after`) |

**Familles et sources non reconnues.** Chaque shader cible appartient à une **famille déclarée** (`world_fragment`,
`world_vertex`, `entity_fragment`, `particle_fragment`…) dont la signature attendue est vérifiée avant de patcher :
`#version 150`, une seule `void main()`, et pour un fragment une seule sortie `out vec4`. **Une source qui ne
correspond pas n'est pas patchée** : elle est journalisée une fois et rendue vanilla. Jamais de patch « au mieux ».
Cela couvre aussi les shaders remplacés par un pack de ressources.

**Détection de la sortie et de la signature : pas d'expression régulière permissive.** L'enveloppe de `main()` reste
une technique **à valider sur les sources réelles** (hypothèse H2 du §15). Le patcheur doit :

- travailler sur une **suite de jetons** du source, commentaires retirés, et non sur des lignes de texte ;
- reconnaître une sortie avec ses qualificatifs éventuels (`layout(location = 0) out vec4 fragColor;`) ;
- **refuser** toute source où la déclaration de sortie, la signature de `main()` ou leur voisinage dépend de
  directives conditionnelles (`#if`, `#ifdef`) ou de macros (`#define`) qu'il ne sait pas évaluer, ainsi que toute
  source à **plusieurs sorties** ;
- appliquer ces règles après résolution des `#moj_import`, puisque c'est le source final qui est compilé ;
- traiter chaque **variante de compilation** éventuelle d'un même shader comme un source à part entière.

**Premier livrable technique de cette partie : l'inventaire des shaders de Minecraft 1.21.1.** Pour chaque shader
du jar : famille, sortie déclarée et ses qualificatifs, présence de directives conditionnelles, de macros ou de
variantes, chemins de sortie (`discard`, `return`), verdict « patchable » ou « refusé ». Cet inventaire est versionné
avec les tests et rejoué à chaque mise à jour de Minecraft.

**Bibliothèque GLSL** (`assets/gtgs_backrooms/shaders/include/look.glsl`) :

```glsl
uniform int  gtgs_LookId;       // 0 = vanilla : la fonction rend la couleur telle quelle
uniform vec4 gtgs_LookTint;     // rgb = teinte, a = force de l'étalonnage
uniform vec4 gtgs_LookParams;   // x = désaturation, y = grain, z = vacillement, w = temps

vec4 gtgs_applyLook(vec4 c) {
    if (gtgs_LookId == 0) {
        return c;
    }
    float l = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    c.rgb = mix(c.rgb, vec3(l), gtgs_LookParams.x);
    c.rgb = mix(c.rgb, c.rgb * gtgs_LookTint.rgb, gtgs_LookTint.a);
    return c;
}
```

**Points d'attention**

- **Uniformes inconnus de vanilla** : `ShaderProgram` ne connaît que les uniformes de son JSON. Reprendre la technique
  déjà utilisée pour `backrooms_ClipPlane` : location lue par `glGetUniformLocation` au lien, mise en cache, envoi
  direct. Location `-1` = programme non patché → ne rien envoyer.
- **Patcher le source, pas le binaire** : intercepter au même endroit que `MixinShaderStage`.
- **Ne pas patcher** `blit_screen`, `position` (ciel), les shaders d'interface et de texte : la GUI doit rester
  vanilla.
- **Échec de compilation = repli** : si un shader patché ne compile pas (pilote strict), recompiler **sans le patch**
  et le journaliser une fois. Un look manquant vaut mieux qu'un monde rose.
- **GLSL strict** : rester en `#version 150`, pas d'extensions, pas de `int` implicite vers `float`, pas de tableaux
  non constants. Les pilotes Intel et AMD refusent ce que NVIDIA accepte.
- **Rechargement des ressources** (F3+T) : le cache des sources patchés est invalidé avec les ressources.
- **Branche uniforme** : `gtgs_LookId` est identique pour tout un draw call, la branche ne coûte pas de divergence.

### 6.2 `LookProfile` (données)

Un profil par dimension (`assets/gtgs_backrooms/look_profiles/level_0.json`) :

```json
{
  "dimension": "gtgs_backrooms:backrooms_level_0",
  "id": 1,
  "tint": [1.06, 1.0, 0.82, 0.35],
  "desaturation": 0.15,
  "grain": 0.02,
  "flicker": 0.04,
  "lightmap": { "floor": 0.08, "warmth": 0.2, "max_brightness": 0.92 },
  "fog": { "color": [0.48, 0.44, 0.30], "start": 4.0, "end": 48.0 },
  "post": { "vignette": 0.25, "chromatic": 0.0, "bloom": "quality>=full" }
}
```

- Une dimension **sans profil** a `id = 0` : rendu strictement vanilla. L'Overworld n'a jamais de profil.
- Les profils sont rechargés avec les ressources : itération rapide sans relancer le client.

### 6.3 `LookState` et `LookUniforms`

- **Pile** : `push(profil)` en entrée de vue, `pop()` en sortie. Même discipline que `PortalClipping` (le plan de
  découpe). Garde de cohérence en fin d'image : pile vide, sinon log et remise à zéro.
- **Envoi** : à chaque activation de programme (le point où `MixinShaderProgram` envoie déjà le plan de découpe),
  envoyer les uniformes du profil au sommet de la pile. Ne renvoyer que si le programme ou le profil a changé depuis
  le dernier envoi.
- **Transition** : à la traversée, le profil du joueur change. `LookTransition` interpole les paramètres sur
  ~0,3 s (avec un `Easing`). **Aucune recompilation**, seulement des valeurs d'uniformes.
- Les paramètres animés (`w = temps`, vacillement) se calculent **une fois par image**, pas par draw call.

### 6.4 Lightmap et brouillard par dimension

Le plus gros de l'ambiance, pour un coût nul :

- **Lightmap** : `ViewLightmaps` calcule déjà une lightmap par monde vu. Appliquer la courbe du profil
  (`floor`, `warmth`, `max_brightness`) dans ce calcul, une fois par tick.
- **Brouillard** : couleur et distances du profil, appliquées là où le mod règle déjà le brouillard par vue.
- **Données de dimension** (`DimensionType`, `DimensionEffects`) : `ambient_light`, ciel absent, brouillard proche.
  À faire **en premier**, avant tout shader.

### 6.5 `LookPostPipeline` masqué par le stencil (P1)

**Disposition du stencil** (8 bits) :

| Bits   | Sens                                                         |
| ------ | ------------------------------------------------------------ |
| `0x03` | Niveau d'imbrication (existant : 0, 1, 2)                    |
| `0x80` | Le pixel montre une dimension Backrooms (à écrire)           |
| reste  | Libre, réservé                                               |

**Écriture du bit**

- Effacement en début d'image : `glClearStencil(joueurDansBackrooms ? 0x80 : 0x00)`.
  **Attention** : `glClearStencil` ne fixe que la **valeur** d'effacement. Un effacement du stencil (`glClear` avec
  `GL_STENCIL_BUFFER_BIT`) n'écrit que les bits autorisés par le **masque d'écriture courant** (`glStencilMask`) ; si
  une étape précédente l'a laissé à `0x03` ou `0x00`, le bit `0x80` de l'image précédente survit. Séquence à
  respecter :
  1. sauvegarder le masque d'écriture et la valeur d'effacement courants (`GL_STENCIL_WRITEMASK`,
     `GL_STENCIL_CLEAR_VALUE`, lus une fois et mis en cache côté Java plutôt que par `glGet` à chaque image) ;
  2. `glStencilMask(0xFF)`, puis `glClearStencil(...)`, puis l'effacement ;
  3. restaurer le masque et la valeur d'effacement d'origine.
  L'effacement se fait **au même endroit** que l'effacement de stencil existant du mod (le stencil est déjà
  « relâché en fin d'image » par `MixinWorldRenderer`), pour ne pas créer un second point d'effacement concurrent.
- Quand la zone d'une vue passe au niveau `n+1` : écrire aussi le bit selon la dimension de la vue
  (`glStencilMask(0x80)`, `GL_REPLACE`, référence `0x80` ou `0x00`), limité aux pixels du niveau `n+1`.
- Quand la zone **redescend** au niveau `n` : n'écrire que les bits de niveau (`glStencilMask(0x03)`). **Le bit
  `0x80` doit survivre**, sinon le post ne verrait plus la vue.
- **Tous les tests de niveau existants** doivent masquer avec `0x03` dans `glStencilFunc` : sinon le bit
  `0x80` fausse les comparaisons de niveau. C'est le changement le plus risqué, à faire en premier et à vérifier
  sous RenderDoc.

**Invariant à garantir (risque architectural n° 1)**

Le stencil porte **un état par pixel, pas une pile d'états par vue**. Tout repose donc sur cet invariant :

> En fin de rendu du monde, le bit `0x80` d'un pixel vaut la dimension de la vue **la plus profonde réellement
> visible** sur ce pixel.

Il tient si, et seulement si :

- une vue n'écrit le bit **que sur les pixels qu'elle prend** (ceux qui passent du niveau `n` à `n+1`) ;
- une vue imbriquée écrase le bit de **ses propres pixels seulement**, ce qui est correct puisqu'elle remplace
  visuellement la vue qui la contient ;
- **aucune autre étape** n'écrit le stencil sans respecter les masques : retour de la zone à sa vraie profondeur,
  plein écran de la troisième personne (`ThirdPersonPortal`), découpes des entités à cheval sur un portail, et toute
  écriture de stencil d'un autre mod.

Chaque écriture de stencil existante du mod est à **inventorier** et à classer (respecte les masques ou non) avant
d'écrire le bit. Puis **prototype obligatoire avant tout post** : un mode de débogage
(`-Dgtgsbackrooms.look.debugStencil=true`) qui affiche uniquement le stencil en couleurs (niveau en teinte, bit
`0x80` en hachures), validé dans RenderDoc sur : un portail dans chaque sens, un portail imbriqué, deux portails l'un
derrière l'autre, la troisième personne derrière un portail, une entité à cheval sur un cadre. Si l'invariant ne
tient pas, voir le plan B de l'hypothèse H1 (§15).

**Pass post**

1. Copier la couleur du framebuffer principal dans une texture (`glBlitFramebuffer`, pas de `glReadPixels`).
2. Lier le framebuffer principal (avec son stencil), activer le test :
   `glStencilFunc(GL_EQUAL, 0x80, 0x80)`, `glStencilMask(0x00)`, profondeur et écriture de profondeur coupées.
3. Dessiner un triangle plein écran avec le programme `look_post` (vignette, aberration, composition du bloom).

Le test de stencil rejette les fragments hors Backrooms. Sur la plupart des GPU, ce rejet a lieu **avant**
l'exécution du fragment shader (test de stencil anticipé), ce qui économise son coût. Ce n'est **pas garanti** par
la spécification OpenGL : cela dépend du matériel, du pilote et du shader. Pour en maximiser les chances, le shader
de post n'utilise **ni `discard` ni `gl_FragDepth`**. Le gain réel se mesure (§9.3, hypothèse H7 du §15).

**Points d'attention**

- **Point d'insertion à confirmer sur le code réel** : avant de figer quoi que ce soit, cartographier le chemin de
  rendu exact de 1.21.1 (`GameRenderer.render` → `renderWorld` → `WorldRenderer.render`, composition des
  translucides en « Fabuleux », `renderHand`, interface), et vérifier que le framebuffer lié au moment du post porte
  bien le stencil ajouté par `MixinFramebuffer` et un masque encore intact (hypothèses H3 et H4 du §15).
  L'architecture ne doit pas dépendre d'une supposition sur le framebuffer.

- **Lire le stencil comme texture** demande OpenGL 4.3 : interdit (macOS). On utilise le **test** de stencil, qui
  existe en 3.2.
- **Graphismes « Fabuleux »** : vanilla rend les translucides dans des framebuffers séparés et les compose par son
  propre post (`transparency`). Vérifier où le stencil vit à ce moment-là, et placer le post **après** la
  composition. Tester les trois modes graphiques (Rapide, Joli, Fabuleux).
- **Translucides du monde du joueur** : dessinés après les vues, ils héritent du bit de la vue derrière eux (une
  vitre devant un portail vers le Level 0 prend le look). Comportement accepté.
- **Troisième personne derrière un portail** (`ThirdPersonPortal`) : tout l'écran passe au niveau 1. Le bit doit
  suivre la dimension de cette vue.
- **Taille** : la texture de copie suit la taille de la fenêtre (`FramebufferRegistry`, recréée au
  redimensionnement uniquement).

### 6.6 `FramebufferRegistry` (P1)

Framebuffers déclarés en données :

```json
{ "name": "gtgs_backrooms:bloom_half", "scale": 0.5, "format": "RGBA8", "depth": false, "filter": "linear" }
```

- Créés à la demande, **recréés uniquement** au redimensionnement ou au changement de qualité.
- Jamais d'allocation par image.
- Formats compatibles partout : `RGBA8`. `RGBA16F` seulement en qualité « complet », après vérification du support.
- Libérés à la fin de session.

### 6.7 `GtgShaderPrograms` (P1)

Programmes propres au mod (post, bloom, effets spéciaux), chargés comme les shaders vanilla depuis les ressources.

- Vertex + fragment. Geometry possible en 3.2 mais à éviter (lent sur iGPU). **Pas de compute** (4.3), sauf dans le
  palier **Ultra (expérimental)**, réservé au matériel OpenGL 4.3 et grisé ailleurs (`QUALITE_ET_PRESETS.md` §4.5).
- Erreurs de compilation : log complet du pilote, nom du programme, lignes numérotées du source patché.
- Rechargement avec F3+T.

### 6.8 Néons qui éclairent (P2, optionnel)

Sans rendu différé :

- Le client garde les **N néons les plus proches** de la caméra de chaque vue (N = 8 en léger, 16 en complet).
- Envoyés dans un **uniform block** (OpenGL 3.1, donc OK), mis à jour une fois par vue.
- `gtgs_applyLook` ajoute une contribution diffuse par fragment, atténuée par la distance.
- Besoin de la **position monde** du fragment : la transmettre depuis le vertex shader patché (varying ajouté par
  l'injection `head` + `main_after`).

### 6.9 Bloom (P2, optionnel)

- Seuil sur les pixels très lumineux, en **demi-résolution**, flou séparable 2 passes en **quart de résolution**,
  composé dans le pass post final.
- Qualité « complet » uniquement par défaut.

---

## 7. Intégration aux portails : récapitulatif

> **Exigence absolue, à vérifier à chaque phase** : un portail vers l'Overworld reste **visuellement vanilla**, même
> vu depuis les Backrooms à travers un autre portail (vue imbriquée), et quelle que soit la position du joueur. Si
> cette propriété tient avec les vues imbriquées, l'une des parties les plus difficiles du projet est validée ; si
> elle tombe, aucune autre fonctionnalité du moteur n'est livrée tant qu'elle n'est pas rétablie.

1. **Look par vue** : `LookState.push/pop` dans `RemoteViewRenderer`, au même endroit que le plan de découpe.
2. **Post par zone** : bit `0x80` du stencil, écrit avec la zone de chaque vue, conservé au retour de niveau.
3. **Main et interface** : choix de design.
   - Option A : post **avant** la main → main vanilla partout (plus discret).
   - Option B : post **après** la main → la main prend le look dans les Backrooms (plus immersif).
   - Dans les deux cas, l'interface (HUD, menus, F3) reste vanilla.
4. **Entités à cheval sur un portail** (`StraddlingEntityRenderer`) : chaque moitié est dessinée dans son monde →
   pousser le profil du monde de chaque moitié.
5. **Particules des mondes distants** (`RemoteParticles`) : dessinées dans la vue, donc avec le profil de la vue.
6. **Ciel et nuages** : non patchés (le Level 0 n'en a pas ; l'Overworld vu depuis le Level 0 reste vanilla).

---

## 8. Compatibilité

### 8.1 Sodium (point critique pour les petites configs)

Sodium dessine le terrain avec **ses propres shaders**, pas `rendertype_solid`. L'étage 1 n'atteindrait pas les
blocs. Le doc des portails note déjà que les vues ne sont pas supportées avec un mod qui remplace le pipeline de
terrain. À trancher tôt :

| Option                                       | Effet                                                                |
| -------------------------------------------- | -------------------------------------------------------------------- |
| Interdire Sodium (`breaks`)                  | Simple, mais prive les petites configs de leur premier mod de perf   |
| Patcher aussi les shaders de Sodium          | Mixin sur son chargeur de shaders, API instable entre versions       |
| Look porté par lightmap + brouillard + post  | Marche avec ou sans Sodium ; l'étage 1 devient un bonus vanilla      |

Recommandation : **la troisième**, en gardant l'étage 1 pour le rendu vanilla, puis évaluer la deuxième plus tard.

### 8.2 Iris / OptiFine

- Les vues de portail ne fonctionnent pas sous Iris (il tourne sur Sodium et réécrit le pipeline).
- Basculer Iris par son API à l'entrée des Backrooms recharge tout son pipeline : gel de plusieurs centaines de ms à
  plusieurs secondes, incompatible avec la traversée sans coupure.
- Recommandation pour le modpack du serveur : `"breaks": { "iris": "*", "optifabric": "*" }` dans
  `fabric.mod.json`, ou au minimum un mode dégradé propre avec un avertissement clair au lancement (détail des
  options au §14.4). Bloquer Iris le retire aussi de l'Overworld, ce qui est voulu (§1.1, point 3).

### 8.3 Autres mods

- `RenderCompat` détecte les mods connus (`FabricLoader.isModLoaded`) et désactive proprement ce qui ne peut pas
  marcher (le post, l'étage 1) au lieu de planter.
- Garder la règle du projet : **rien d'injecté dans `setupTerrain` ni `renderLayer`**.

---

## 9. Performance

### 9.1 Budgets (objectifs à valider, à 1080p, sur un iGPU type Intel UHD 620)

Ce sont des **objectifs, pas des garanties**. Ils sont ambitieux, surtout avec plusieurs vues de portail : le coût de
l'étage 1 se multiplie par le nombre de vues (§14.1), et le post inclut la copie du framebuffer (un blit pleine
résolution par image). Chaque ligne se mesure **séparément**, sur une vraie petite configuration, avant d'être
considérée comme tenue (hypothèse H5 du §15).

| Élément                        | Budget GPU visé  |
| ------------------------------ | ---------------- |
| Étage 1 (injection)            | < 0,1 ms         |
| Pass post (léger)              | < 0,5 ms         |
| Bloom (complet)                | < 1,0 ms         |
| Total moteur, qualité légère   | < 0,7 ms         |

### 9.2 Règles

- **Un seul uber-pass** post plutôt qu'une chaîne de petites passes.
- **Basse résolution** pour tout ce qui floute.
- **Le stencil élimine** les pixels hors Backrooms.
- **Aucune allocation par image** : FBO, textures, buffers, tableaux d'uniformes préalloués.
- **Pas de synchronisation CPU-GPU** : jamais `glReadPixels`, `glFinish`, `glGetError` en boucle (seulement en dev).
- **Envoi d'uniformes minimal** : seulement si le programme ou le profil change.
- **Qualité** : `désactivé` / `léger` (défaut, désormais choisi par le bouton **Auto** selon le matériel :
  `QUALITE_ET_PRESETS.md` §9.3) / `complet`, avec détection d'un iGPU au démarrage
  (chaîne `GL_RENDERER`) pour proposer `léger`. Un palier **`minimum`** s'ajoute sous `léger`, et la détection d'un
  iGPU propose désormais `minimum` (§16.2).

### 9.3 Mesure

- Ajouter au moniteur existant les sections `client.look.inject`, `client.look.post`, `client.look.bloom`.
- **Temps GPU** (que le banc actuel n'affiche pas) : requêtes `GL_TIME_ELAPSED` autour de chaque pass, lues
  **deux images plus tard** pour ne pas bloquer.
- Banc : reprendre les scènes de `PORTALS.md` (vue stable sur le Level 0, retour sur l'Overworld avec portail
  imbriqué), à 854×480 **et** 1920×1080, sur la machine de dev **et** sur un iGPU.
- **Mesurer brique par brique, puis l'ensemble** : d'abord le coût de chaque brique isolée (injection, masque, post),
  puis celui de l'ensemble dans une scène avec plusieurs portails **et** les sources sonores du moteur audio
  (`MOTEUR_AUDIO.md`, §10.3). La première version ne vise pas tous les objectifs de qualité : la qualité « léger »
  tenue d'abord, « complet » ensuite.

---

## 10. Robustesse

- **Coupe-circuit** comme `PortalRenderState` : une exception ou une erreur GL pendant le post → post coupé pour la
  session, look par injection conservé, une ligne de log.
- **Repli de compilation** shader par shader (§6.1).
- **État GL rendu tel quel** après chaque pass : stencil, profondeur, blend, programme, textures liées, viewport.
  Vanilla et les autres mods supposent cet état.
- **Changement de dimension vanilla, déconnexion** : pile `LookState` vidée, FBO libérés.
- **Multi-pilotes** : tester NVIDIA, AMD, Intel, et macOS (Apple Silicon via la couche OpenGL).

---

## 11. Tests

| Niveau          | Quoi                                                                                                   |
| --------------- | ------------------------------------------------------------------------------------------------------ |
| Unitaires       | `ShaderPatchRegistryTest` : chaque point d'injection sur **tous** les shaders vanilla du jar (comme `ShaderClipInjectorTest`), source patché qui garde une seule `main`, idempotence (patcher deux fois = une fois), sources non reconnues refusées (signature de famille non respectée, sortie absente ou multiple) |
| Compilation     | **Chaque shader patché est compilé**, pas seulement inspecté comme du texte : validation hors GPU en CI par le compilateur de référence Khronos (`glslangValidator`, profil GLSL 150) ; puis compilation réelle au lancement du client de dev sur chaque pilote testé |
| Unitaires       | `LookProfileLoaderTest` : format, valeurs par défaut, dimension sans profil = `id 0`                    |
| Unitaires       | `LookStateTest` : push/pop, pile vide en fin d'image, transition interpolée                             |
| Unitaires       | `StencilLookMaskTest` : calcul des valeurs, masques et fonctions de stencil pour chaque niveau          |
| Golden          | Empreinte du source patché de chaque shader cible : tout changement de patch est explicite              |
| Manuel          | RenderDoc : contenu du stencil (bits `0x03` et `0x80`) avec un portail, un portail imbriqué, la 3e personne |
| Manuel          | Mode `debugStencil` (§6.5) sur les cas de l'invariant : portail dans chaque sens, imbriqué, deux portails alignés, 3e personne, entité à cheval |
| Manuel          | Matrice de compatibilité (§14.6) : chaque configuration lancée, chaque cellule « à vérifier » tranchée |
| Manuel          | **Exigence absolue du §7** : depuis le Level 0, portail vers l'Overworld vu directement puis à travers un autre portail ; rendu comparé à l'Overworld vanilla (capture côte à côte), avec et sans post |
| Manuel          | **Périmètre (§1.1)** : Overworld avec le mod installé, loin de tout portail, comparé par capture à un client vanilla (même graine, même position, même réglages) ; portail vers le Level 0 vu depuis l'Overworld : look dans le cadre seulement |
| Manuel          | **« Strictement vanilla »** selon la définition du §1.1 : comparaison au pixel près sur tous les cas obligatoires, écarts analysés et documentés |
| Intégration     | **Premier jalon commun** et **test décisif** du §12.1 (néon synchronisé vu par un portail ; cri à travers deux portails) |
| Manuel          | Effacement du stencil : image suivante sans trace du bit `0x80` de l'image précédente (RenderDoc), après une traversée dans chaque sens |
| Manuel          | Trois modes graphiques, trois qualités, F3+T, redimensionnement de fenêtre, traversée aller-retour      |

Le rendu GPU lui-même n'est pas testable en `runGameTest` : la logique (patchs, profils, masques) doit donc être
**séparée du code GL** pour rester couverte.

---

## 12. Plan par phases

Principe : **une seule couche de rendu nouvelle à la fois**, chaque phase prouvée avant la suivante. Ni bloom ni
registre de framebuffers au départ : on commence par l'ambiance de dimension et un shader minimal, puis on prouve que
chaque vue de portail affiche le bon look, avant d'ajouter le moindre post.

| Phase | Contenu                                                                                     | Livrable vérifiable                                  |
| ----- | ------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| 0     | **Reconnaissance** : cartographier le chemin de rendu réel de 1.21.1 et du mod, inventorier les écritures de stencil, vérifier le framebuffer et son stencil au point de post envisagé, lancer chaque configuration de la matrice (§14.6), **inventaire des shaders de 1.21.1** (§6.1) | Hypothèses H1 à H8 (§15) statuées ou planifiées, point d'insertion du post choisi, inventaire des shaders versionné |
| 1     | **Ambiance sans shader** : `DimensionType`, `DimensionEffects`, lumière ambiante, brouillard, courbe de lightmap du Level 0 | L'ambiance tient sans aucun shader ; Overworld inchangé, **y compris à travers les portails** |
| 2     | **Un shader patché, une seule vue** : teinte simple sur `rendertype_solid` par enveloppe de `main()`, repli vanilla | Compilation (CI et pilotes), repli, F3+T, coût mesuré |
| 3     | **Look par vue** : familles de shaders, `LookProfile`, `LookState`, envoi des uniformes par vue | Portail dans les deux sens, imbriqué, 3e personne : bon look partout ; **exigence absolue du §7 tenue**. **Pas de post tant que ce n'est pas fiable** |
| 4     | **Masque seul** : bit `0x80` du stencil (effacement avec masque d'écriture compris), masques `0x03` partout, mode `debugStencil` ; prototype minimal du plan B (H8) | Invariant du §6.5 vérifié sous RenderDoc dans tous ses cas ; exigence absolue du §7 tenue ; plan B prouvé ou écarté |
| 5     | **Post** : `FramebufferRegistry`, `LookPostPipeline` (vignette d'abord, aberration légère ensuite), trois modes graphiques | Post limité aux pixels Backrooms en Rapide, Joli et Fabuleux |
| 6     | Qualité, `RenderCompat` (Sodium, Iris), coupe-circuit                                       | Sodium toléré sans crash ; Iris bloqué ou en mode dégradé (§14.5) |
| 7     | Optionnel : néons en uniform block, bloom basse résolution. **Les néons en uniform block deviennent nécessaires à la liaison son-image** (`MOTEUR_AUDIO.md`, §8) : à avancer après la phase 3 si l'audio en a besoin, sans que les prototypes audio les attendent | Budgets §9.1 tenus sur iGPU                          |

Faire la phase 1 **avant tout shader** : elle donne une grande partie de l'ambiance et sert de référence visuelle
pour juger si chaque effet suivant vaut son coût.

### 12.1 Ordre commun avec le moteur audio, et premier jalon

Les deux moteurs ne se terminent pas séparément avant d'être réunis : leurs fondations se valident chacune de son
côté, puis un **petit test d'intégration se fait tôt**. Ordre commun (le même figure dans `MOTEUR_AUDIO.md`, §14.2) :

| Étape | Moteur de shaders                                                   | Moteur audio                                                        |
| ----- | ------------------------------------------------------------------- | ------------------------------------------------------------------- |
| 1     | Chemin de rendu réel, points d'injection, framebuffers, stencil (phase 0) | Cycle de vie OpenAL, capacités EFX, suivi de lecture (phase 0, prototypes A et B) |
| 2     | Une teinte minimale dans une seule vue ; Overworld prouvé inchangé (phases 1 et 2) | Un son avec une réverb simple dans les Backrooms ; Overworld prouvé inchangé |
| 3     | Portail dans chaque sens, puis vues imbriquées (phases 3 et 4) ; ni bloom ni effet complexe avant | Portail simple dans chaque sens (prototype D) ; aucune perturbation complexe avant |
| 4     | Réception des signaux du moteur audio (`neon_face`, `lightmap_flicker`) | Un néon déterministe, puis un cri à enveloppe ; décalage mesuré avant les sons longs |
| 5     | Charge et compatibilité : plusieurs portails, Sodium, trois modes graphiques | Charge et compatibilité : plusieurs sons, périphériques, F3+T, sans EFX |

**Premier jalon commun, modeste mais irréfutable** : un néon du Level 0 **visible à travers un portail** depuis
l'Overworld, dont le clignotement et le bourdonnement restent synchronisés, tandis que le reste de l'Overworld
demeure **strictement vanilla** à l'image comme au son. Il valide à lui seul le look par vue, le masque, la fonction
partagée Java / GLSL, le son à travers le cadre et le périmètre des deux moteurs.

**Test d'intégration décisif, à garder obligatoire** : un cri du Level 0 entendu à travers un portail, lui-même vu à
travers un autre portail. Son, réverb, néons perturbés et post doivent tous respecter la bonne dimension. Il n'est
pas développé d'un coup : il se construit au fil des étapes 3 à 5 et sert de critère de sortie.
*Le Level 0 n'a pas d'entités (`GAMEPLAY.md` §6) : le cri de ce test vient d'un **niveau habité**, ou d'un **niveau
de test réservé au développement** (même génération et mêmes néons que le Level 0, avec une entité qui crie). Le
premier jalon, lui, n'a besoin que des néons et reste dans le Level 0.*

---

## 13. Checklist des points d'attention

- [ ] Aucun code de Veil copié ; aucune mention d'autre projet dans le code, le JAR ni la documentation publiée
  (§3, règles 5 et 6)
- [ ] Overworld et dimensions sans profil : rendu **strictement** vanilla (`id 0`)
- [ ] **Périmètre** : rendu selon la dimension montrée par le pixel ; vue d'un portail vers les Backrooms lookée dans
  le cadre seulement ; shaders patchés et framebuffers sans effet visible hors des Backrooms (§1.1)
- [ ] « Strictement vanilla » démontré par captures au pixel près, pas déduit du code (§1.1)
- [ ] Premier jalon commun atteint avant d'étendre les effets ; test décisif à deux portails tenu (§12.1)
- [ ] Budgets mesurés **par rapport au vanilla sur la machine de référence** (PC de bureau, GPU intégré) (§16)
- [ ] Palier `minimum` : identité des Backrooms conservée, seuls les effets secondaires coupés (§16.2)
- [ ] LOD des vues : niveau complet près du cadre, hystérésis, aucune transition visible, mémoire bornée (§17)
- [ ] Look poussé/retiré par vue, pile vide en fin d'image
- [ ] Aucune recompilation ni rechargement à la traversée
- [ ] Tests de niveau du stencil masqués à `0x03`, bit `0x80` conservé au retour de niveau
- [ ] Interface, texte, ciel non patchés
- [ ] Repli si un shader patché ne compile pas
- [ ] GLSL `#version 150`, testé sur Intel, AMD, NVIDIA, macOS
- [ ] Graphismes Rapide, Joli et Fabuleux testés
- [ ] Décision prise sur Sodium et Iris, déclarée dans `fabric.mod.json`
- [ ] Aucune allocation ni synchronisation CPU-GPU par image
- [ ] Budgets GPU mesurés par requêtes de temps, sur iGPU
- [ ] État GL restauré après chaque pass
- [ ] Logique séparée du code GL et couverte par les tests
- [ ] Effets discrets : le joueur ne doit pas reconnaître un shaderpack (paliers Low et Medium ; High et Ultra visent
  volontairement le visuel de référence, `QUALITE_ET_PRESETS.md` §2.3)
- [ ] **Exigence absolue** : portail vers l'Overworld visuellement vanilla, y compris vu à travers un autre portail (§7)
- [ ] Effacement du stencil sous masque d'écriture `0xFF`, masque et valeur d'effacement restaurés (§6.5)
- [ ] Inventaire des shaders de 1.21.1 versionné ; sortie et signature détectées par jetons, pas par regex (§6.1)
- [ ] Plan B du stencil prototypé avant d'être compté comme repli (H8, §15)
- [ ] Mode dégradé propre si un mod de rendu incompatible est présent sans être bloqué (§14.4)
- [ ] Écritures de stencil du mod inventoriées ; invariant du bit `0x80` validé en mode `debugStencil` (§6.5)
- [ ] Patch par enveloppe de `main()`, sortie déclarée détectée, sources non reconnues refusées (§6.1)
- [ ] Shaders patchés compilés en CI et sur chaque pilote, pas seulement vérifiés comme du texte (§11)
- [ ] Point d'insertion du post choisi d'après le chemin de rendu réel, pas d'après une supposition (§6.5)
- [ ] Matrice de compatibilité à jour, sans cellule « à vérifier » au moment d'une release (§14.6)
- [ ] Hypothèses du §15 toutes statuées avant de considérer le document comme spécification

---

## 14. Ce que la base permet, et ses limites de compatibilité

Ce chapitre complète le §8 : il répond à « pourra-t-on tout faire avec cette base, et rester compatible ? ».

### 14.1 Les effets : une base extensible

L'architecture est **extensible à de nouveaux effets, sous réserve des contraintes du pipeline et du budget GPU** :

- l'**injection** (§6.1) donne la main sur chaque fragment de chaque shader vanilla patché ;
- le **post masqué** (§6.5) donne l'image entière, zone de dimension par zone de dimension ;
- les **framebuffers déclaratifs** (§6.6) et les **programmes propres** (§6.7) permettent d'enchaîner autant de
  passes que voulu.

Il devient ainsi possible, plus tard : éclairage dynamique (§6.8), distorsions, transitions entre levels, effets
propres à chaque level, voire un rendu différé partiel si un besoin réel apparaît.

Ce n'est pas une extensibilité illimitée. Les effets qui demandent **la profondeur** comme texture, **des buffers
supplémentaires** (normales, matériaux) ou **des données de plusieurs vues à la fois** (réflexions, effets qui
traversent un cadre) sortent du modèle « injection + post masqué » et peuvent demander des changements importants :
nouvelles cibles de rendu par vue, nouvel ordre de composition, nouvelles hypothèses à valider au §15.

**La limite la plus visible est le coût, et il se paie par vue.** Un effet à 2 ms coûte environ 6 ms avec deux portails
à l'écran (le monde du joueur plus deux vues). Les règles du §9 (discret, basse résolution, un seul uber-pass) ne
sont donc pas qu'une question de style : elles conditionnent la tenue des budgets dès qu'un portail est visible.

### 14.2 Mods « normaux » : compatibles

Les familles de mods qui ne touchent pas au rendu du terrain passent déjà le banc des portails sans écart
(`docs/PORTALS.md`, *Limites connues*) : lumière dynamique, minimap, enregistrement de parties, cache de chunks,
culling d'entités, optimisations serveur. Le moteur de shaders n'y change rien **tant qu'il respecte la règle du
§8.3** : rien d'injecté dans `setupTerrain` ni `renderLayer`.

### 14.3 Sodium : faisable, avec un coût de maintenance

| Partie du moteur                          | Avec Sodium                                                                      |
| ----------------------------------------- | -------------------------------------------------------------------------------- |
| Lightmap, brouillard, données de dimension | Fonctionne sans rien faire                                                       |
| Post masqué (§6.5)                         | Fonctionne, sous réserve que le stencil du framebuffer principal soit conservé   |
| Injection dans les blocs (§6.1)           | N'atteint pas le terrain : demande un second patcheur sur les shaders de Sodium |
| Vues de portail                           | Non supportées aujourd'hui (problème du système de portails, pas du moteur)      |

- Un patcheur dédié aux shaders de terrain de Sodium est possible, mais à **revérifier à chaque mise à jour de
  Sodium** (API interne, non stable).
- Le chantier principal pour Sodium est **le rendu des vues de portail**, pas le moteur de shaders.
- Cohérent avec la recommandation du §8.1 : porter le look par lightmap, brouillard et post d'abord, et n'ajouter
  le patcheur Sodium que si les joueurs à petite config le demandent.

### 14.4 Iris : une compatibilité complète n'est pas réaliste

Ce n'est pas une faiblesse de cette base : c'est la nature d'Iris.

- Iris **remplace tout le pipeline de rendu** par celui du shaderpack, et tourne sur Sodium.
- Il n'existe **aucune API** pour injecter le look du mod dans un shaderpack, ni pour appliquer un pack à une zone
  de l'écran et pas à une autre. Le « look par vue de portail » est donc impossible sous Iris.
- Les vues de portail ne fonctionnent pas sous Iris (§8.2). Les mods de portails établis ont eux-mêmes un support
  d'Iris limité.

Options possibles, de la plus à la moins cohérente avec l'objectif d'immersion :

| Option                                     | Résultat                                                                                   |
| ------------------------------------------ | ------------------------------------------------------------------------------------------ |
| **Bloquer** (`breaks` dans `fabric.mod.json`) | Expérience garantie ; recommandé pour le modpack du serveur, que l'on contrôle              |
| **Mode dégradé propre**                    | Iris détecté (`RenderCompat`) : look et post coupés, portails en cadres sombres, jeu stable ; le joueur garde son pack mais perd l'illusion |
| **Bascule par l'API d'Iris**               | Pack coupé à l'entrée des Backrooms : gel de rechargement à chaque passage, incompatible avec la traversée sans coupure (objectif 6 du §1) |

### 14.5 Décision recommandée

1. **Iris** : bloqué pour le modpack du serveur. Si le mod est un jour distribué publiquement, remplacer le blocage
   par le mode dégradé propre, accompagné d'un avertissement au lancement.
2. **Sodium** : toléré dès la phase 6 du plan (§12), avec le look porté par lightmap, brouillard et post. Patcheur
   Sodium et support des vues de portail évalués ensuite, selon la demande.
3. **Autres mods** : compatibles par défaut, `RenderCompat` désactivant proprement ce qui ne peut pas marcher au
   lieu de planter.

Ce n'est pas renoncer à la compatibilité : c'est choisir celle qui préserve l'expérience que le mod construit.

### 14.6 Matrice de compatibilité

« Compatible » ne veut rien dire globalement : chaque configuration se décrit **effet par effet**. Tolérer Sodium,
par exemple, ne signifie pas que les vues de portail fonctionnent avec lui. Les cellules « à vérifier » se
tranchent en phase 0 (§12) et sont suivies par l'hypothèse H6 (§15).

| Configuration                               | Ambiance (dimension, lightmap, brouillard) | Étage 1 sur le terrain | Étage 1 sur entités et particules | Post masqué | Vues de portail | Statut visé               |
| ------------------------------------------- | ----------------------------------------- | ---------------------- | --------------------------------- | ----------- | --------------- | ------------------------- |
| Vanilla                                     | Oui                                       | Oui                    | Oui                               | Oui         | Oui             | **Cible principale**      |
| Sodium seul                                 | Oui                                       | Non (shaders Sodium)   | À vérifier                        | À vérifier (stencil conservé ?) | Non aujourd'hui | Toléré, rendu dégradé |
| Sodium + patcheur Sodium (futur)            | Oui                                       | Oui, à maintenir       | À vérifier                        | À vérifier  | Non (chantier portails) | Évalué selon la demande |
| Iris, shaderpack actif                      | Partielle (le pack impose son rendu)      | Non                    | Non                               | Non         | Non             | **Bloqué** (§14.5)        |
| Iris installé, aucun pack actif             | Comme Sodium seul (Iris s'appuie sur Sodium) | Non                 | À vérifier                        | À vérifier  | Non             | Bloqué pour le serveur ; dégradé si un jour public |
| Mods sans rendu du terrain (§14.2)          | Oui                                       | Oui                    | Oui                               | Oui         | Oui             | Compatible                |

Toute nouvelle combinaison jugée pertinente pour le modpack (par exemple un mod de rendu lointain) s'ajoute comme
une ligne, avec les mêmes colonnes.

---

## 15. Hypothèses à valider avant de figer l'architecture

Le document est convaincant sur le papier ; ces points ne sont prouvés que par le code réel. Chaque hypothèse a une
façon de la valider, une phase, et un plan B si elle tombe.

| #  | Hypothèse                                                                                     | Validation                                                        | Phase | Si elle tombe                                                                 |
| -- | --------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- | ----- | ----------------------------------------------------------------------------- |
| H1 | L'invariant du bit `0x80` tient (vues imbriquées, 3e personne, entités à cheval, autres écritures), effacement compris (§6.5) | Inventaire des écritures de stencil, mode `debugStencil`, RenderDoc | 0, 4  | Appliquer le post **par vue**, juste après le dessin de chaque vue, sous son ciseau et son stencil de niveau, au lieu d'un post unique en fin d'image — **plan B lui-même à prototyper** (H8) |
| H2 | Les shaders vanilla ciblés suivent la signature de leur famille et se patchent par enveloppe de `main()`, qualificatifs de sortie, directives conditionnelles et variantes compris (§6.1) | **Inventaire des shaders de 1.21.1** (premier livrable), patch de tous les shaders du jar, compilation `glslangValidator` en CI | 0, 2, 3 | Patchs explicites par shader pour les exceptions, refus pour le reste (rendu vanilla) |
| H3 | Il existe un point du rendu de 1.21.1 où le post voit l'image complète du monde, translucides compris, dans les trois modes graphiques | Cartographie du chemin de rendu, RenderDoc en Rapide, Joli, Fabuleux | 0     | Point d'insertion différent par mode graphique, ou post par vue (plan B de H1) |
| H4 | Le framebuffer lié à ce point porte le stencil de `MixinFramebuffer` avec le masque intact     | RenderDoc au point d'insertion retenu                             | 0     | Copier le masque dans une cible dédiée au moment où il est encore valide     |
| H5 | Les budgets du §9.1 tiennent avec plusieurs vues                                               | Requêtes `GL_TIME_ELAPSED` par étape, sur iGPU, scènes de `PORTALS.md` | 2 à 7 | Qualité « léger » sans post, post à résolution réduite, moins d'effets      |
| H6 | Le comportement réel sous Sodium (entités, particules, stencil) est celui supposé par la matrice | Lancement de chaque configuration de la matrice (§14.6)          | 0, 6  | Sodium réduit à l'ambiance seule, ou bloqué                                  |
| H7 | Le test de stencil anticipé économise bien le coût du post hors Backrooms                       | Mesure du post avec et sans masque, sur iGPU                     | 5     | Limiter en plus la zone traitée au rectangle d'écran des vues concernées (ciseau) |

| H8 | Le plan B de H1 (post par vue) est viable                                                        | Prototype minimal : une vue, puis une vue imbriquée, en mode `debugStencil` et avec une vignette visible | 4     | Réduire le post aux effets locaux (étage 1 seul), ou repenser la composition des vues |
| H9 | Le palier « minimum » et le LOD des vues tiennent les budgets relatifs du §16 sur la machine de référence, sans transition visible entre niveaux | Banc du §16.3 sur la machine de référence, avant / après chaque niveau de LOD | 0, puis chaque phase | Abaisser les seuils de LOD, limiter à une seule vue rendue en « minimum », cadre sombre au-delà |

**Plan B de H1 : ce qu'il faut prouver avant de le considérer comme un repli garanti.** Appliquer le post vue par
vue n'est pas un simple remplacement du post global : cela change l'ordre de composition. Le prototype doit
démontrer que le post d'une vue :

- ne traite **que sa zone visible** : ciseau de la vue et stencil **égal à son niveau exact**, pour exclure les
  zones de ses portails imbriqués ;
- respecte le masque de profondeur et ne réécrit ni la profondeur ni le stencil ;
- ne modifie **aucun pixel d'une vue déjà composée** ni du monde du joueur ;
- s'exécute au bon moment de la séquence : après le contenu de la vue, et dans un ordre défini par rapport à ses
  portails imbriqués et aux translucides du monde qui la contient, l'ordre de composition devenant déterminant avec
  l'imbrication ;
- lit une image source cohérente : la copie de couleur se limite au rectangle de la vue, sinon le coût se multiplie
  par le nombre de vues (H5).

Tant qu'une hypothèse n'est pas statuée, la partie du document qui en dépend reste une **proposition**.

---

## 16. Cible matérielle et paliers de qualité

### 16.1 Cible : le plus grand nombre, donc les PC de bureau à GPU intégré

Le mod vise **le plus grand nombre de joueurs**, donc la machine la plus modeste raisonnable : un **PC de bureau ou
portable de bureautique avec GPU intégré**.

| Élément                  | Machine de référence (à confirmer en phase 0)                         |
| ------------------------ | --------------------------------------------------------------------- |
| GPU                      | Intégré, type Intel UHD 620 (ou équivalent AMD Radeon intégré ancien) |
| Processeur               | 4 cœurs d'entrée de gamme, type Core i5 de 8e génération mobile        |
| Mémoire                  | 8 Go au total, 2 à 4 Go alloués à Minecraft                           |
| Écran                    | 1920×1080 (la résolution la plus courante), testé aussi en 1366×768    |
| Réglages Minecraft       | Graphismes « Rapide », distance de rendu 6 à 8, sans Sodium            |

Sur ces machines, **le jeu vanilla lui-même peine déjà**. Promettre un nombre de FPS absolu n'aurait pas de sens.
Les budgets sont donc **relatifs au vanilla, sur la même machine et avec les mêmes réglages** : le mod ne doit pas
dégrader ce que le joueur a déjà, au-delà d'un surcoût borné et connu.

### 16.2 Paliers de qualité

Un seul moteur, plusieurs budgets. Ce que chaque palier coupe, ce n'est jamais l'identité des Backrooms : l'ambiance
de base (lightmap, brouillard, paramètres de dimension, phase 1 du §12) ne coûte rien et **reste active partout**.

| Palier       | Pour                                    | Moteur de shaders                                                                      | Vues de portail (LOD, §17)                                                  |
| ------------ | --------------------------------------- | -------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `désactivé`  | Diagnostic, ou choix du joueur          | Ambiance de base seule, aucun shader patché appliqué, aucun post                        | Comme `minimum`                                                             |
| `minimum`    | **PC de bureau à GPU intégré** (défaut proposé sur iGPU) | Ambiance de base + étage 1 (injection) ; pas de post, pas de néons en uniform block, pas de bloom | Une seule vue rendue à la fois, seuils de LOD bas, vues imbriquées coupées, mondes distants en mémoire limités |
| `léger`      | Configuration modeste à carte dédiée    | + post masqué simple (vignette), néons en uniform block (N = 8)                        | Seuils de LOD moyens, vues imbriquées autorisées                            |
| `complet`    | Bonne marge GPU et CPU                  | + bloom basse résolution, néons N = 16, effets du §6.8 et §6.9                         | Seuils de LOD hauts                                                         |

Ces paliers sont une **proposition d'architecture**, pas des performances garanties : leurs seuils se fixent sur la
machine de référence (H9).

**Paliers présentés au joueur** : dans le menu du mod, ces paliers s'affichent sous les noms **Low** (`minimum`),
**Medium** (`léger`) et **High** (`complet`), complétés par un palier **Ultra (expérimental)** à ray tracing. Leur
contenu détaillé, les presets visuels et sonores, le menu, les lumières de zone et les ombres pixelisées sont décrits
dans `QUALITE_ET_PRESETS.md`, qui prolonge ce document sans en changer les règles.

### 16.3 Budgets relatifs et banc

| Situation, palier `minimum`, machine de référence        | Objectif (à valider)                                                  |
| -------------------------------------------------------- | --------------------------------------------------------------------- |
| Aucun portail visible, Overworld                         | Surcoût **non mesurable** par rapport au vanilla (§1.1 : strictement vanilla) |
| Aucun portail visible, Level 0                           | Temps d'image au plus **10 %** au-dessus du même temps dans une salle vanilla comparable |
| Un portail visible, taille moyenne à l'écran             | Temps d'image au plus **30 %** au-dessus de la même scène sans portail |
| Plusieurs portails visibles                              | Pas de pire que « un portail » grâce au LOD : une seule vue complète, les autres en niveau léger ou en cadre sombre |
| Mémoire                                                  | Nombre de `WorldRenderer` distants plafonné (une trentaine de Mio chacun hors du tas Java, `PORTALS.md`) |
| Plancher de jouabilité                                   | Une scène que le vanilla tient au-dessus de 30 FPS ne passe pas sous 30 FPS avec le mod |

Banc : les scènes de `PORTALS.md` et du §9.3, plus une scène de pire cas raisonnable (salle du Level 0, plusieurs
portails, sons du moteur audio actifs), mesurées **en vanilla puis avec le mod**, sur la machine de référence, avec
une **marge laissée aux autres mods** du modpack. Un prototype vide ne prouve rien : la mesure se fait avec le mod
complet, dans le modpack réel.

---

## 17. LOD des vues de portail

> Le LOD est **en cours de développement dans le mod**. Cette section fixe les **exigences** qu'il doit tenir pour
> servir la cible du §16 ; les détails (noms, seuils, structures) suivent l'implémentation.

### 17.1 Principe

Une vue de portail redessine un monde entier : c'est **le premier poste de coût** sur un PC modeste, loin devant les
effets des moteurs. Le LOD réduit **ce qu'une vue dessine** selon son importance à l'écran.

- **Le coût en pixels est déjà proportionnel à la surface du cadre** : les vues se dessinent sous un ciseau et le
  stencil, dans l'image principale. Rendre une vue dans une texture en basse résolution n'est donc **pas** le bon
  levier et casserait la traversée sans coupure.
- **Ce qui ne dépend pas de la surface** est la cible du LOD : sections de terrain parcourues, appels de dessin,
  entités, particules, lightmap, vues imbriquées, effets des moteurs.

### 17.2 Critères

| Critère                        | Source                                                                     |
| ------------------------------ | -------------------------------------------------------------------------- |
| **Surface du cadre à l'écran** (critère principal) | Rectangle du ciseau déjà calculé par `PortalStencilRenderer`              |
| Distance au cadre              | Position de la caméra et du portail                                       |
| Profondeur d'imbrication       | Niveau du stencil (une vue imbriquée descend d'au moins un niveau de LOD)  |
| Palier de qualité              | §16.2                                                                      |
| Nombre de vues déjà rendues dans l'image | Budget de vues par palier (une seule vue complète en `minimum`) |

La distance seule ne suffit pas : un grand portail de 8 × 8 au loin peut occuper plus d'écran qu'un 1 × 2 proche.

### 17.3 Niveaux

| Niveau | Quand                                   | Ce que la vue dessine                                                              |
| ------ | --------------------------------------- | ---------------------------------------------------------------------------------- |
| 0      | Portail proche ou grand à l'écran       | Tout : terrain à pleine distance de vue, entités, particules, look et post         |
| 1      | Taille moyenne                          | Distance de rendu de la vue réduite, entités lointaines coupées, pas de particules, pas de post |
| 2      | Petit à l'écran                         | Terrain proche du cadre seulement, entités proches seulement, étage 1 seul          |
| 3      | Minuscule ou très lointain (optionnel)  | Imposteur : une image figée de la vue, rafraîchie rarement, dessinée dans le cadre  |
| 4      | Au-delà, ou budget de vues épuisé       | Le cadre sombre de repli qui existe déjà                                           |

### 17.4 Exigences

1. **Niveau 0 obligatoire près du cadre** : à moins de 8 blocs (la distance où l'arrivée est chargée sur toute la
   distance de vue, `PORTALS.md`), la vue est complète. Sinon la traversée sans coupure montrerait un monde
   incomplet juste avant le passage.
2. **Hystérésis** : un niveau se dégrade un peu plus loin qu'il ne se rétablit, et ne change pas plus d'une fois par
   intervalle minimal. Sans cela, un portail à la limite change de niveau à chaque pas et son terrain clignote.
3. **Aucune transition visible** : un changement de niveau ne doit pas se remarquer. À vérifier à l'œil sur la
   machine de référence et sur capture ; un changement qui se voit fait baisser les seuils, pas l'exigence.
4. **Le niveau 3 est optionnel** : une image figée n'a plus de mobs qui bougent ni de néons qui clignotent
   (`MOTEUR_AUDIO.md`). Acceptable pour un portail minuscule au bout d'un couloir, à valider à l'œil ; à couper
   s'il trahit l'illusion. Il demande aussi un rendu dans une texture, à part du chemin du stencil.
5. **Mémoire bornée** : en `minimum`, nombre de mondes distants et de vues secondaires gardés en mémoire plafonné ;
   au-delà, le monde le moins récemment vu est libéré (son retour coûte l'à-coup de première vue de `PORTALS.md`).
6. **Cohérence avec le périmètre (§1.1)** : quel que soit le niveau, une vue vers l'Overworld reste strictement
   vanilla, et le LOD ne change jamais la dimension montrée par un pixel.
7. **Cohérence avec le son** : le LOD est visuel. Un portail en cadre sombre laisse toujours passer le son
   (`MOTEUR_AUDIO.md`, §9.3) ; un néon d'une vue en niveau 3 garde son bourdonnement, et son image figée doit être
   rafraîchie assez souvent pour ne pas contredire le son de façon visible, sinon passer au niveau 2.
8. **Mesuré** : chaque niveau a son coût mesuré sur le banc du §16.3 (sections `client.portal.*` existantes, plus
   le niveau de LOD de chaque vue dans le journal de débogage des portails).

---

## Références

- Veil (référence de lecture, LGPL-3.0) : https://github.com/FoundryMC/Veil
- Documentation des portails du mod : `docs/PORTALS.md`

---

## Historique des révisions

- **v1** : conception initiale (§1 à §13).
- **v2** : ajout du §14 (possibilités de la base et limites de compatibilité).
- **v3** : intégration d'une revue externe : invariant du stencil et prototype `debugStencil` (§6.5), patch par
  enveloppe de `main()` et familles de shaders (§6.1), compilation réelle des shaders patchés (§11), point
  d'insertion du post à confirmer sur le pipeline réel (§6.5), budgets présentés comme objectifs (§9.1), nuance
  juridique sur la LGPL (§2.2), phases réordonnées avec une phase de reconnaissance (§12), matrice de compatibilité
  (§14.6), hypothèses à valider (§15).
- **v4** : seconde revue externe : effacement du stencil sous masque d'écriture et restauration de l'état (§5.2,
  §6.5), détection de la sortie par jetons et refus des directives conditionnelles, macros et sorties multiples
  (§6.1), inventaire des shaders de 1.21.1 comme premier livrable (§6.1, §12), plan B du stencil à prototyper
  (H8, §15), extensibilité formulée sous réserve des contraintes du pipeline et du budget GPU (§14.1), exigence
  absolue sur les portails vers l'Overworld (§7, §11, §12, §13), statut « prochaine étape : le code ».
- **v5** : périmètre explicite « Backrooms seulement » (§1.1) : rendu selon la dimension montrée par chaque pixel,
  mises en place globales neutres, réactions venues du son limitées à la vue du portail, conséquence du blocage
  d'Iris ; reporté au §8.2, §11 et §13. Document compagnon : `MOTEUR_AUDIO.md`.
- **v6** : phase 7 : les néons en uniform block deviennent nécessaires à la liaison son-image (`MOTEUR_AUDIO.md`, §8).
- **v7** : définition démontrable de « strictement vanilla » (§1.1), mesure brique par brique puis d'ensemble (§9.3),
  ordre commun avec le moteur audio, premier jalon commun et test décisif à deux portails (§12.1) ; tests et
  checklist complétés (§11, §13).
- **v8** : cible matérielle « PC de bureau à GPU intégré » et budgets relatifs au vanilla (§16.1, §16.3), palier
  `minimum` (§9.2, §16.2), LOD des vues de portail et ses exigences, en cohérence avec l'implémentation en cours
  (§17), hypothèse H9 (§15), checklist complétée (§13).
- **v9** : renvoi vers `QUALITE_ET_PRESETS.md` (paliers Low à Ultra, presets, menu, lumières de zone, ombres
  pixelisées) et correspondance des noms de paliers (§16.2).
- **v10** : §3 : aucune mention d'autre projet dans le code, le JAR ni la documentation publiée (remplace « tracer
  l'inspiration ») ; mod entièrement propriétaire ; checklist alignée.
- **v11** : passe de cohérence entre les quatre documents : portée des objectifs 4 et 5 et des choix « pas de rendu
  différé » et « pas de compute » limitée à Low et Medium (§1, §4, §6.7, §13) ; interface du mod hors du périmètre du
  moteur de shaders (§1.1) ; test décisif joué dans un niveau habité ou un niveau de test, le Level 0 n'ayant pas
  d'entités (§12.1) ; palier par défaut choisi par Auto (§9.2) ; mention « document interne ».
