package com.gtgpuke.gtgsbackrooms.core.audio.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.sound.sampled.AudioFormat;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundContainer;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.RandomSplitter;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.Cue;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.CueRegistry;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.Envelope;
import com.gtgpuke.gtgsbackrooms.mixin.client.WeightedSoundSetAccessor;

/**
 * Les liens son-image ({@code sound_cues/*.json}) et les enveloppes de leurs sons, charges apres les sons vanilla a
 * chaque rechargement des ressources. Les fichiers de chaque variante de chaque son a enveloppe sont releves sur le
 * thread de rendu ; leurs enveloppes ({@link Envelope}) se lisent ensuite en arriere-plan dans le cache sur disque, sous
 * l'empreinte du fichier ({@code .cache/gtgs_backrooms/envelopes} du dossier du jeu), ou se calculent par le decodeur
 * Ogg de vanilla et s'y gardent. Jusqu'a la fin du calcul, un son lie suit une amplitude constante. Un son diffuse en
 * flux n'a pas d'enveloppe (sa position de lecture ne se lit pas), un son trop long non plus.
 */
public final class CueResources {

    /** Au-dela, un son n'est pas decode : une enveloppe sert aux cris, pas aux musiques. */
    static final int MAX_SECONDS = 30;
    private static final String CUES = "sound_cues";
    private static final String JSON = ".json";
    private static final int CHUNK = 1 << 16;
    private static final Identifier LISTENER_ID = Identifier.of(ModConstants.MOD_ID, "sound_cues");
    /** Les fichiers releves pour le calcul en cours, dans l'ordre ; thread de rendu seulement. */
    private static final Map<Identifier, Resource> PENDING = new LinkedHashMap<>();
    /** Les fichiers qui n'ont pas donne d'enveloppe, pour ne pas les redecoder (ni le redire) a chaque lien qui les cite. */
    private static final Set<Identifier> FAILED = new HashSet<>();
    private static final Pick PICK = new Pick();
    /** Les enveloppes pretes, publiees d'un coup par le calcul en arriere-plan, lues par le rendu. */
    private static volatile Map<Identifier, Envelope> envelopes = Map.of();
    /** Chaque rechargement a son numero : un calcul d'un rechargement depasse ne publie rien. */
    private static volatile int generation;

    private CueResources() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A appeler a l'initialisation du client. */
    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return LISTENER_ID;
            }

            @Override
            public Collection<Identifier> getFabricDependencies() {
                return Set.of(ResourceReloadListenerKeys.SOUNDS);
            }

            @Override
            public void reload(ResourceManager manager) {
                envelopes = Map.of();
                FAILED.clear();
                PENDING.clear();
                int current = ++generation;
                if (ModConfig.ENABLE_AUDIO) {
                    load(manager, current);
                } else {
                    CueRegistry.clear();
                }
            }
        });
    }

    /** L'enveloppe d'un fichier son (sa place dans les ressources), ou {@code null} s'il n'en a pas (ou pas encore). */
    public static @Nullable Envelope envelope(Identifier location) {
        return envelopes.get(location);
    }

    private static void load(ResourceManager manager, int current) {
        List<Cue> cues = new ArrayList<>();
        for (Map.Entry<Identifier, Resource> entry : manager.findResources(CUES, CueResources::isModJson).entrySet()) {
            try (Reader reader = entry.getValue().getReader()) {
                cues.add(Cue.fromJson(JsonParser.parseString(IOUtils.toString(reader)).getAsJsonObject()));
            } catch (IOException | JsonParseException | IllegalStateException exception) {
                ModConstants.LOGGER.error("[Audio] Lien {} refuse : {}", entry.getKey(), exception.getMessage());
            }
        }
        CueRegistry.load(cues);
        MinecraftClient client = MinecraftClient.getInstance();
        for (Cue cue : cues) {
            if (!cue.envelope()) {
                continue;
            }
            for (Identifier soundId : cue.sounds()) {
                WeightedSoundSet set = client.getSoundManager().get(soundId);
                if (set == null) {
                    ModConstants.LOGGER.error("[Audio] Le son {} du lien {} n'existe pas : pas d'enveloppe.", soundId, cue.id());
                    continue;
                }
                bakeAll(manager, set);
            }
        }
        ModConstants.LOGGER.info("[Audio] {} lien(s) son-image, {} fichier(s) son a enveloppe.", CueRegistry.size(), PENDING.size());
        if (PENDING.isEmpty()) {
            return;
        }
        List<Map.Entry<Identifier, Resource>> files = List.copyOf(PENDING.entrySet());
        PENDING.clear();
        Path cache = FabricLoader.getInstance().getGameDir().resolve(".cache").resolve(ModConstants.MOD_ID).resolve("envelopes");
        CompletableFuture.runAsync(() -> bakeInBackground(files, cache, current), Util.getMainWorkerExecutor());
    }

    /**
     * En arriere-plan : l'enveloppe de chaque fichier, lue dans le cache sous l'empreinte de son contenu ou calculee et
     * gardee, puis toutes publiees d'un coup si aucun rechargement n'est passe entre-temps.
     */
    private static void bakeInBackground(List<Map.Entry<Identifier, Resource>> files, Path cache, int current) {
        Map<Identifier, Envelope> baked = new HashMap<>();
        int fromCache = 0;
        for (Map.Entry<Identifier, Resource> file : files) {
            Identifier location = file.getKey();
            try (InputStream input = file.getValue().getInputStream()) {
                byte[] sound = input.readAllBytes();
                Path stored = cache.resolve(Envelope.cacheName(sound));
                Envelope envelope = Files.isRegularFile(stored) ? Envelope.fromBytes(Files.readAllBytes(stored)) : null;
                if (envelope != null) {
                    fromCache++;
                } else {
                    envelope = decode(sound);
                    Files.createDirectories(cache);
                    Files.write(stored, envelope.toBytes());
                }
                baked.put(location, envelope);
            } catch (IOException | RuntimeException exception) {
                ModConstants.LOGGER.error("[Audio] Le son {} ne donne pas d'enveloppe : {}", location, exception.getMessage());
            }
        }
        if (current != generation) {
            return;
        }
        envelopes = Map.copyOf(baked);
        ModConstants.LOGGER.info("[Audio] {} enveloppe(s) prete(s) en arriere-plan : {} lue(s) du cache, {} calculee(s).", baked.size(), fromCache,
                baked.size() - fromCache);
    }

    /**
     * Les variantes d'un jeu de sons : un fichier, un jeu imbrique, ou un renvoi vers un autre evenement. Le conteneur
     * d'un renvoi ne dit pas vers quel jeu : ses variantes se tirent une par une par un tirage fixe ({@link Pick}),
     * une valeur par unite de poids, et chacune sort exactement une fois quand le jeu vise ne contient que des fichiers.
     */
    private static void bakeAll(ResourceManager manager, WeightedSoundSet set) {
        List<SoundContainer<Sound>> sounds = ((WeightedSoundSetAccessor) set).backrooms$getSounds();
        for (int i = 0; i < sounds.size(); i++) {
            SoundContainer<Sound> container = sounds.get(i);
            if (container instanceof WeightedSoundSet nested) {
                bakeAll(manager, nested);
            } else if (container instanceof Sound sound) {
                bake(manager, sound);
            } else {
                int weight = container.getWeight();
                for (int draw = 0; draw < weight; draw++) {
                    Sound sound = container.getSound(PICK.at(draw));
                    if (sound != null && sound != SoundManager.MISSING_SOUND) {
                        bake(manager, sound);
                    }
                }
            }
        }
    }

    /** Releve le fichier d'une variante (thread de rendu) ; son enveloppe se fera en arriere-plan. */
    private static void bake(ResourceManager manager, Sound sound) {
        Identifier location = sound.getLocation();
        if (PENDING.containsKey(location) || FAILED.contains(location)) {
            return;
        }
        if (sound.isStreamed()) {
            FAILED.add(location);
            ModConstants.LOGGER.warn("[Audio] Le son {} est diffuse en flux : pas d'enveloppe (sa position de lecture ne se lit pas).", location);
            return;
        }
        Resource resource = manager.getResource(location).orElse(null);
        if (resource == null) {
            FAILED.add(location);
            ModConstants.LOGGER.error("[Audio] Fichier son {} introuvable : pas d'enveloppe.", location);
            return;
        }
        PENDING.put(location, resource);
    }

    /** Decode un fichier Ogg (le decodeur de vanilla) et calcule son enveloppe, sur au plus {@value #MAX_SECONDS} secondes. */
    private static Envelope decode(byte[] sound) throws IOException {
        try (OggAudioStream stream = new OggAudioStream(new ByteArrayInputStream(sound))) {
            AudioFormat format = stream.getFormat();
            int channels = format.getChannels();
            int rate = (int) format.getSampleRate();
            int limit = MAX_SECONDS * rate * channels;
            short[] samples = new short[Math.min(limit, rate * channels)];
            int count = 0;
            while (count < limit) {
                ByteBuffer chunk = stream.read(CHUNK);
                if (chunk == null || !chunk.hasRemaining()) {
                    break;
                }
                ShortBuffer shorts = chunk.order(ByteOrder.nativeOrder()).asShortBuffer();
                int take = Math.min(shorts.remaining(), limit - count);
                if (count + take > samples.length) {
                    samples = Arrays.copyOf(samples, Math.max(samples.length * 2, count + take));
                }
                shorts.get(samples, count, take);
                count += take;
            }
            return Envelope.bake(Arrays.copyOf(samples, count), channels, rate);
        }
    }

    private static boolean isModJson(Identifier id) {
        return id.getNamespace().equals(ModConstants.MOD_ID) && id.getPath().endsWith(JSON);
    }

    /**
     * Un tirage fixe : {@code nextInt(bound)} rend toujours la meme valeur (bornee), ce qui fait choisir a un jeu de
     * sons pondere l'entree qui couvre cette unite de poids. Le choix d'une variante ne tire rien d'autre.
     */
    private static final class Pick implements Random {
        private int value;

        Pick at(int draw) {
            value = draw;
            return this;
        }

        @Override
        public int nextInt(int bound) {
            return Math.min(value, bound - 1);
        }

        @Override
        public Random split() {
            return this;
        }

        @Override
        public RandomSplitter nextSplitter() {
            throw new UnsupportedOperationException("Tirage fixe.");
        }

        @Override
        public void setSeed(long seed) {
            // Rien : le tirage est fixe.
        }

        @Override
        public int nextInt() {
            return value;
        }

        @Override
        public long nextLong() {
            return value;
        }

        @Override
        public boolean nextBoolean() {
            return false;
        }

        @Override
        public float nextFloat() {
            return 0.0f;
        }

        @Override
        public double nextDouble() {
            return 0.0;
        }

        @Override
        public double nextGaussian() {
            return 0.0;
        }
    }
}
