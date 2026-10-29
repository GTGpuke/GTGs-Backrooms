package com.gtgpuke.gtgsbackrooms.core.audio.client;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.system.MemoryStack;

import net.minecraft.client.sound.Source;
import net.minecraft.util.Identifier;

import net.fabricmc.loader.api.FabricLoader;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.ReverbParameters;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.ReverbPresets;
import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfile;
import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfiles;

/**
 * Colle OpenAL de l'acoustique : l'extension EFX, detectee au demarrage du moteur de son, fournit une reverberation
 * par slot auxiliaire et un filtre passe-bas par source. Deux envois auxiliaires par source sont demandes a la creation
 * du contexte (la reverb du son, celle de la salle de l'auditeur) et le nombre obtenu est relu. Par dimension profilee,
 * un slot et son effet par reglage de reverb ({@link ReverbPresets}), chacun cree a la premiere source qui en a besoin
 * et partage par toutes ; une reserve fixe de filtres, prise et rendue par source traitee. Tous les objets sont oublies
 * quand le moteur de son se ferme (changement de peripherique, rechargement) : le contexte les emporte, et leurs
 * identifiants ne sont jamais reutilises.
 *
 * <p>Trois modes ({@link Mode}) : EFX ; sans l'extension (pilote, ou option {@code gtgsbackrooms.audio.efx} coupee),
 * l'occlusion se rend par le volume seul ; apres une erreur OpenAL, ou si un autre moteur acoustique travaille dans le
 * meme contexte (il est reconnu a un filtre, un effet ou un slot EFX qui n'est pas des notres : vanilla n'en cree
 * aucun), plus rien pour la session : les sons continuent vanilla et l'autre moteur garde la main. Les appels par source se font sur le thread du son, ou le moteur
 * de son les demande ; en developpement, un appel ailleurs est journalise.
 */
public final class EfxBackend {

    /** Comment l'acoustique s'applique aux sources. */
    public enum Mode {

        /** Reverb par slot auxiliaire, filtre passe-bas, absorption de l'air. */
        EFX,
        /** Sans EFX : l'occlusion et le milieu rendus par le seul volume du son. */
        GAIN,
        /** Rien : erreur OpenAL, ou un autre moteur acoustique s'en charge. */
        OFF
    }

    /** Filtres en reserve : au-dela, un son traite recoit la reverb sans occlusion. */
    static final int FILTER_POOL = 48;
    /** Envois auxiliaires demandes a la creation du contexte : la reverb du son, celle de la salle de l'auditeur. */
    private static final int REQUESTED_SENDS = 2;
    private static final int AIR_ABSORPTION_SCALE = 10;
    private static final String SOUND_THREAD = "Sound engine";
    /** Identifiants EFX passes en revue pour reconnaitre un autre moteur : OpenAL Soft les donne en commencant par 1. */
    private static final int SCAN_LIMIT = 128;
    /** Part des graves gardee sous l'option de confort qui les reduit (environ -6 dB). */
    private static final float REDUCED_BASS_GAIN = 0.5f;
    /** Slots (et leurs effets) que le mod peut creer : huit reglages pour huit dimensions profilees. */
    private static final int SLOT_CAPACITY = 64;
    private static final boolean DEVELOPMENT = FabricLoader.getInstance().isDevelopmentEnvironment();
    /** Par dimension, le slot de chaque reglage de reverb ; 0 tant qu'il n'est pas cree. */
    private static final Map<Identifier, int[]> SLOTS = new HashMap<>();
    private static final int[] FILTERS = new int[FILTER_POOL];
    private static final boolean[] FILTER_TAKEN = new boolean[FILTER_POOL];
    private static final int[] OUR_SLOTS = new int[SLOT_CAPACITY];
    private static final int[] OUR_EFFECTS = new int[SLOT_CAPACITY];
    /**
     * Thread du son : un autre moteur a-t-il cree ses objets EFX ? Sans etat ni capture : une seule instance, confiee a
     * une source a chaque revue.
     */
    public static final Consumer<Source> VERIFIER = source -> verify();
    private static boolean available;
    /** Une erreur OpenAL a coupe l'acoustique : ecrit sur le thread du son, lu sur celui du rendu. */
    private static volatile boolean failed;
    /** Un autre moteur acoustique travaille dans le contexte : ecrit sur le thread du son, lu sur celui du rendu. */
    private static volatile boolean ceded;
    private static int slotsCreated;
    /** Envois auxiliaires par source, relus a chaque contexte : un pour la reverb du son, un second pour la salle de l'auditeur. */
    private static int sends;
    private static int filtersGenerated;
    private static boolean warnedThread;
    /** Moteur de son demarre ou profils recharges depuis le dernier pre-chauffage des reverbs (thread de rendu). */
    private static boolean prewarmDue;

    private EfxBackend() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Attributs du contexte OpenAL que le moteur de son va creer : ceux de vanilla, plus la demande de deux envois
     * auxiliaires par source (sans elle, le nombre est celui du pilote). Alloues sur la pile de memoire native ouverte par
     * vanilla autour de la creation ; rien de change si l'acoustique ou EFX est coupe, ou si vanilla demande deja des envois.
     *
     * @param vanilla attributs de vanilla, termines par 0
     * @return les attributs a donner a la creation du contexte
     */
    public static @Nullable IntBuffer contextAttributes(@Nullable IntBuffer vanilla) {
        if (!ModConfig.ENABLE_AUDIO || !ModConfig.AUDIO_EFX) {
            return vanilla;
        }
        int pairs = 0;
        if (vanilla != null) {
            while (2 * pairs + 1 < vanilla.limit() && vanilla.get(2 * pairs) != 0) {
                if (vanilla.get(2 * pairs) == EXTEfx.ALC_MAX_AUXILIARY_SENDS) {
                    return vanilla;
                }
                pairs++;
            }
        }
        IntBuffer attributes = MemoryStack.stackGet().mallocInt(2 * pairs + 3);
        for (int i = 0; i < 2 * pairs; i++) {
            attributes.put(vanilla.get(i));
        }
        attributes.put(EXTEfx.ALC_MAX_AUXILIARY_SENDS).put(REQUESTED_SENDS).put(0).flip();
        return attributes;
    }

    /**
     * Le moteur de son vient de creer son contexte : l'extension et le nombre d'envois auxiliaires obtenus sont relus,
     * les anciens objets oublies.
     *
     * @param device peripherique OpenAL ouvert par le moteur
     */
    public static void onEngineStarted(long device) {
        forgetAll();
        failed = false;
        if (!ModConfig.AUDIO_EFX) {
            available = false;
            sends = 0;
            ModConstants.LOGGER.info("[Audio] EFX coupe par l'option : occlusion rendue par le volume seul, sans reverb.");
            return;
        }
        boolean efx = AL.getCapabilities().ALC_EXT_EFX;
        sends = efx ? ALC10.alcGetInteger(device, EXTEfx.ALC_MAX_AUXILIARY_SENDS) : 0;
        available = efx && sends >= 1;
        if (available) {
            ModConstants.LOGGER.info("[Audio] EFX disponible, {} envoi(s) auxiliaire(s) par source (demande : {}).", sends, REQUESTED_SENDS);
            // Un autre moteur qui cree ses objets a la creation du contexte se reconnait des maintenant.
            scanForeign();
            prewarmDue = true;
        } else {
            ModConstants.LOGGER.info("[Audio] EFX indisponible (extension {}, envois {}) : occlusion rendue par le volume seul, sans reverb.", efx, sends);
        }
    }

    /** Les profils viennent d'etre recharges : les reverbs seront pre-chauffees de nouveau, sur leurs reglages. */
    public static void requestPrewarm() {
        prewarmDue = true;
    }

    /**
     * Si le moteur de son ou les profils ont change depuis, et que plus aucun rechargement n'est en cours : le slot et
     * l'effet de chaque reglage de reverb de chaque dimension profilee sont crees d'avance, sur le thread du son (ou
     * les envois les lisent), plutot qu'au premier son qui en a besoin, souvent au moment ou un cadre s'approche.
     *
     * @param soundThread file du thread du son
     */
    public static void prewarmIfDue(Executor soundThread) {
        if (!prewarmDue) {
            return;
        }
        prewarmDue = false;
        soundThread.execute(EfxBackend::prewarm);
    }

    private static void prewarm() {
        checkThread();
        if (!ready()) {
            return;
        }
        int created = slotsCreated;
        for (AudioProfile profile : AudioProfiles.all()) {
            // Sans calcul, une dimension n'utilise que son reglage de base.
            if (profile.acoustics().computed()) {
                for (int preset = 0; preset < ReverbPresets.COUNT; preset++) {
                    slotFor(profile, preset);
                }
            } else {
                slotFor(profile, ReverbPresets.BASE);
            }
        }
        ModConstants.LOGGER.info("[Audio] Reverbs pre-chauffees : {} reglage(s) cree(s) d'avance.", slotsCreated - created);
    }

    /** Le moteur de son se ferme : ses objets disparaissent avec lui. */
    public static void onEngineClosed() {
        forgetAll();
        available = false;
    }

    /** Le mode de l'acoustique pour les sons qui partent. */
    public static Mode mode() {
        if (ceded || failed) {
            return Mode.OFF;
        }
        return available ? Mode.EFX : Mode.GAIN;
    }

    /** {@code true} si l'acoustique passe par EFX. */
    public static boolean ready() {
        return mode() == Mode.EFX;
    }

    /**
     * Prend un filtre de la reserve pour une source traitee.
     *
     * @return l'index du filtre, ou -1 si la reserve est vide
     */
    public static int takeFilter() {
        for (int i = 0; i < FILTER_POOL; i++) {
            if (!FILTER_TAKEN[i]) {
                FILTER_TAKEN[i] = true;
                return i;
            }
        }
        return -1;
    }

    /** Rend un filtre a la reserve, la source qui l'utilisait etant finie. */
    public static void releaseFilter(int index) {
        if (index >= 0) {
            FILTER_TAKEN[index] = false;
        }
    }

    /** Toutes les sources traitees sont finies : la reserve entiere est rendue. */
    public static void releaseAllFilters() {
        for (int i = 0; i < FILTER_POOL; i++) {
            FILTER_TAKEN[i] = false;
        }
    }

    /**
     * Thread du son : branche une source sur la reverb de sa dimension et, s'il entre par un cadre, sur celle de la
     * salle de l'auditeur ({@link #sends}), pose son filtre d'occlusion et l'absorption de l'air de sa dimension.
     *
     * @param source identifiant OpenAL de la source
     * @param profile profil de la dimension d'emission
     * @param preset reglage de reverb de l'espace autour de la source ({@link ReverbPresets})
     * @param room profil de la dimension de l'auditeur quand le son y entre par un cadre, vanilla sinon
     * @param filter index du filtre pris pour cette source, ou -1 sans occlusion
     * @param lowPassGain gain direct du filtre
     * @param lowPassGainHf gain des aigus du filtre
     */
    public static void apply(int source, AudioProfile profile, int preset, AudioProfile room, int filter, float lowPassGain, float lowPassGainHf) {
        checkThread();
        if (!ready()) {
            return;
        }
        sends(source, profile, preset, room);
        AL10.alSourcef(source, EXTEfx.AL_AIR_ABSORPTION_FACTOR, profile.airAbsorption() * AIR_ABSORPTION_SCALE);
        filter(source, filter, lowPassGain, lowPassGainHf);
        checkError("application a une source");
    }

    /**
     * Thread du son, sans EFX : l'occlusion et le milieu d'un son rendus par son volume, que vanilla multiplie ensuite a
     * chaque fois qu'il le repose (sons qui bougent, boucles).
     *
     * @param source source du moteur de son
     * @param scale part du volume gardee
     */
    public static void applyGain(Source source, float scale) {
        checkThread();
        if (mode() == Mode.GAIN) {
            ((AdjustedSource) source).backrooms$setVolumeScale(scale);
        }
    }

    /**
     * Thread du son : la direction percue d'un son, par un decalage de sa source (avec ou sans EFX).
     *
     * @param source source du moteur de son
     * @param dx decalage, en blocs
     * @param dy decalage
     * @param dz decalage
     */
    public static void perceive(Source source, double dx, double dy, double dz) {
        checkThread();
        if (mode() != Mode.OFF) {
            ((AdjustedSource) source).backrooms$setPositionOffset(dx, dy, dz);
        }
    }

    /**
     * Thread du son : les envois d'une source vers les reverbs. Le premier porte celle de sa dimension d'emission, au
     * reglage de l'espace autour de la source ; le second, quand le contexte en donne deux, celle de la salle de
     * l'auditeur (reglage de base) quand le son y entre par un cadre (le cadre est alors une seconde source dans cette
     * salle), sinon rien. Repose quand les rayons changent le reglage, et a la traversee du joueur.
     *
     * @param source identifiant OpenAL de la source
     * @param profile profil de la dimension d'emission
     * @param preset reglage de reverb de l'espace autour de la source
     * @param room profil de la dimension de l'auditeur quand le son y entre par un cadre, vanilla sinon
     */
    public static void sends(int source, AudioProfile profile, int preset, AudioProfile room) {
        checkThread();
        if (!ready()) {
            return;
        }
        int slot = slotFor(profile, preset);
        AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot > 0 ? slot : EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
        if (sends >= 2) {
            int roomSlot = slotFor(room, ReverbPresets.BASE);
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, roomSlot > 0 ? roomSlot : EXTEfx.AL_EFFECTSLOT_NULL, 1, EXTEfx.AL_FILTER_NULL);
        }
        checkError("envois d'une source");
    }

    /**
     * Thread du son : l'occlusion d'une source a change (l'auditeur a bouge), son filtre est repose.
     *
     * @param source identifiant OpenAL de la source
     * @param filter index du filtre de cette source, ou -1 sans occlusion
     * @param lowPassGain gain direct du filtre
     * @param lowPassGainHf gain des aigus du filtre
     */
    public static void filter(int source, int filter, float lowPassGain, float lowPassGainHf) {
        checkThread();
        if (!ready() || filter < 0) {
            return;
        }
        int id = filterId(filter);
        if (ModConfig.AUDIO_REDUCE_BASS) {
            // Option de confort : le meme filtre en passe-bande, qui retire aussi une part des graves.
            EXTEfx.alFilterf(id, EXTEfx.AL_BANDPASS_GAIN, lowPassGain);
            EXTEfx.alFilterf(id, EXTEfx.AL_BANDPASS_GAINHF, lowPassGainHf);
            EXTEfx.alFilterf(id, EXTEfx.AL_BANDPASS_GAINLF, REDUCED_BASS_GAIN);
        } else {
            EXTEfx.alFilterf(id, EXTEfx.AL_LOWPASS_GAIN, lowPassGain);
            EXTEfx.alFilterf(id, EXTEfx.AL_LOWPASS_GAINHF, lowPassGainHf);
        }
        // Une source copie les reglages du filtre quand on le lui attache : le reposer les lui fait relire.
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, id);
        // Lue ici, l'erreur est la notre : laissee, vanilla la lirait a son prochain appel et la journaliserait comme sienne.
        checkError("filtre d'une source");
    }

    /** Thread du son : un autre moteur a-t-il cree ses objets EFX ? Si oui, l'acoustique lui cede la place. */
    private static void verify() {
        checkThread();
        if (ready()) {
            scanForeign();
        }
    }

    /**
     * Passe en revue les premiers identifiants EFX du contexte : un filtre, un effet ou un slot qui n'est pas des notres
     * est celui d'un autre moteur acoustique (vanilla n'en cree aucun). L'acoustique lui cede alors la place pour la
     * session, une ligne dans le journal. Les questions posees ne produisent pas d'erreur OpenAL pour un nom inconnu.
     */
    private static void scanForeign() {
        for (int id = 1; id <= SCAN_LIMIT; id++) {
            boolean foreign = EXTEfx.alIsFilter(id) && !contains(FILTERS, filtersGenerated, id)
                    || EXTEfx.alIsEffect(id) && !contains(OUR_EFFECTS, slotsCreated, id)
                    || EXTEfx.alIsAuxiliaryEffectSlot(id) && !contains(OUR_SLOTS, slotsCreated, id);
            if (foreign) {
                ceded = true;
                ModConstants.LOGGER.warn("[Audio] Un autre moteur acoustique cree ses propres objets EFX (identifiant {}) : l'acoustique du mod lui "
                        + "cede la place pour la session (sons sans reverb ni occlusion du mod, liaison son-image gardee).", id);
                return;
            }
        }
    }

    private static boolean contains(int[] ids, int count, int id) {
        for (int i = 0; i < count; i++) {
            if (ids[i] == id) {
                return true;
            }
        }
        return false;
    }

    private static int filterId(int index) {
        while (filtersGenerated <= index) {
            int id = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(id, EXTEfx.AL_FILTER_TYPE, ModConfig.AUDIO_REDUCE_BASS ? EXTEfx.AL_FILTER_BANDPASS : EXTEfx.AL_FILTER_LOWPASS);
            FILTERS[filtersGenerated++] = id;
        }
        return FILTERS[index];
    }

    /** Le slot d'un reglage de reverb d'une dimension, cree a la premiere demande ; 0 sans reverb ou apres une erreur. */
    private static int slotFor(AudioProfile profile, int preset) {
        if (!profile.reverb().any()) {
            return 0;
        }
        int[] slots = SLOTS.get(profile.dimension());
        if (slots == null) {
            slots = new int[ReverbPresets.COUNT];
            SLOTS.put(profile.dimension(), slots);
        }
        if (slots[preset] == 0) {
            slots[preset] = createSlot(profile, preset);
        }
        return slots[preset];
    }

    /** Cree l'effet et le slot d'un reglage ; 0 si OpenAL refuse (circuit coupe). */
    private static int createSlot(AudioProfile profile, int preset) {
        ReverbParameters parameters = ReverbPresets.parameters(profile.reverb(), preset);
        int effect = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_TIME, parameters.decayTime());
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DENSITY, parameters.density());
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DIFFUSION, parameters.diffusion());
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAIN, parameters.gain());
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAINHF, parameters.gainHf());
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_HFRATIO, parameters.decayHfRatio());
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_LATE_REVERB_GAIN, parameters.lateGain());
        int slot = EXTEfx.alGenAuxiliaryEffectSlots();
        EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
        if (slotsCreated < SLOT_CAPACITY) {
            OUR_EFFECTS[slotsCreated] = effect;
            OUR_SLOTS[slotsCreated++] = slot;
        }
        if (!checkError("creation de la reverb")) {
            return 0;
        }
        ModConstants.LOGGER.info("[Audio] Reverb de {} (reglage {}) : decroissance {} s, gain {}.", profile.dimension(), preset, parameters.decayTime(),
                parameters.gain());
        return slot;
    }

    /** {@code true} sans erreur OpenAL ; sinon le circuit est coupe pour la session, journalise une fois. */
    private static boolean checkError(String operation) {
        int error = AL10.alGetError();
        if (error == AL10.AL_NO_ERROR) {
            return true;
        }
        failed = true;
        ModConstants.LOGGER.error("[Audio] Erreur OpenAL {} ({}) : acoustique coupee pour la session, sons vanilla.", error, operation);
        return false;
    }

    /** En developpement : un appel OpenAL par source ne se fait que sur le thread du son ; ailleurs, journalise une fois. */
    private static void checkThread() {
        if (DEVELOPMENT && !warnedThread && !SOUND_THREAD.equals(Thread.currentThread().getName())) {
            warnedThread = true;
            ModConstants.LOGGER.error("[Audio] Appel OpenAL par source hors du thread du son ({}).", Thread.currentThread().getName(),
                    new IllegalStateException("Thread du son attendu."));
        }
    }

    private static void forgetAll() {
        SLOTS.clear();
        filtersGenerated = 0;
        slotsCreated = 0;
        releaseAllFilters();
    }
}
