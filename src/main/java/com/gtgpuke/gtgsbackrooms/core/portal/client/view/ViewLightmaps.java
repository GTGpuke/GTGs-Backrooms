package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.mixin.client.LightmapTextureManagerAccessor;

/**
 * Lightmap de chaque monde vu par un portail : une texture par monde, calculee par le gestionnaire vanilla (meme
 * algorithme, meme rendu) une fois par tick client, et donnee au gestionnaire le temps de dessiner la vue.
 *
 * <p>Vanilla ne recalcule la lightmap du joueur qu'une fois par tick. Recalculer celle de la vue, puis de nouveau celle
 * du joueur, a chaque image et pour chaque vue, coutait deux calculs de 256 points par vue et par image, chacun avec un
 * millier d'objets temporaires, et deux envois de texture au pilote qui attendaient la fin des dessins en cours. Ici le
 * gestionnaire porte, le temps de la vue, la texture du monde vu : chaque couche de rendu qui active la lightmap
 * ({@code RenderPhase.Lightmap} appelle {@code enable()}) lie donc celle-ci, et la calcule d'abord si elle ne l'a pas
 * encore ete a ce tick. Une vue du monde du joueur (vue secondaire) garde la lightmap du joueur telle quelle.
 */
public final class ViewLightmaps {

    private static final int SIZE = 16;
    private static final String PERF_LIGHTMAP = "client.portal.lightmap";
    /** Lightmaps par monde ; quelques mondes au plus, jamais parcourue par image. */
    private static final Map<ClientWorld, Lightmap> LIGHTMAPS = new IdentityHashMap<>();
    /** Tick client courant : une lightmap calculee a ce tick ne l'est pas de nouveau. */
    private static int tick;
    /** Texture, image et identifiant du joueur, retires du gestionnaire le temps d'une vue ; nuls hors d'une vue. */
    private static @Nullable NativeImageBackedTexture mainTexture;
    private static @Nullable NativeImage mainImage;
    private static @Nullable Identifier mainId;

    /** Texture d'un monde et tick de son dernier calcul. */
    private static final class Lightmap {
        final NativeImageBackedTexture texture;
        final Identifier id;
        int computedTick = Integer.MIN_VALUE;

        Lightmap(NativeImageBackedTexture texture, Identifier id) {
            this.texture = texture;
            this.id = id;
        }
    }

    private ViewLightmaps() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Tick client : les lightmaps des vues se recalculeront a leur prochain dessin, comme vanilla la sienne. */
    public static void onClientTick() {
        tick++;
    }

    /**
     * Donne au gestionnaire la lightmap du monde de la vue, calculee d'abord si elle ne l'a pas encore ete a ce tick, et
     * la lie aux shaders. Le monde de {@code MinecraftClient} est deja celui de la vue : le gestionnaire vanilla y lit la
     * luminosite du ciel, la foudre et les effets de dimension, et dans le joueur ses effets (vision nocturne,
     * obscurite). A rendre par {@link #unbind} dans un {@code finally}.
     *
     * @param client client
     * @param manager gestionnaire de lightmap vanilla
     * @param world monde de la vue
     * @param tickDelta fraction de tick de l'image
     */
    @SuppressWarnings("try")
    public static void bind(MinecraftClient client, LightmapTextureManager manager, ClientWorld world, float tickDelta) {
        Lightmap lightmap = LIGHTMAPS.get(world);
        if (lightmap == null) {
            lightmap = create(client);
            LIGHTMAPS.put(world, lightmap);
        }
        LightmapTextureManagerAccessor accessor = (LightmapTextureManagerAccessor) manager;
        mainTexture = accessor.backrooms$getTexture();
        mainImage = accessor.backrooms$getImage();
        mainId = accessor.backrooms$getTextureIdentifier();
        accessor.backrooms$setTexture(lightmap.texture);
        accessor.backrooms$setImage(lightmap.texture.getImage());
        accessor.backrooms$setTextureIdentifier(lightmap.id);
        if (lightmap.computedTick != tick) {
            lightmap.computedTick = tick;
            try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_LIGHTMAP)) {
                // La lightmap du joueur, deja a jour pour ce tick, n'a rien a recalculer : le drapeau ne vaut que pour celle-ci.
                accessor.backrooms$setDirty(true);
                manager.update(tickDelta);
            }
        }
        manager.enable();
    }

    /** Rend au gestionnaire la lightmap du joueur et la lie aux shaders. */
    public static void unbind(LightmapTextureManager manager) {
        if (mainId == null) {
            return;
        }
        LightmapTextureManagerAccessor accessor = (LightmapTextureManagerAccessor) manager;
        accessor.backrooms$setTexture(mainTexture);
        accessor.backrooms$setImage(mainImage);
        accessor.backrooms$setTextureIdentifier(mainId);
        mainTexture = null;
        mainImage = null;
        mainId = null;
        manager.enable();
    }

    /**
     * Monde distant libere par le serveur : sa texture est detruite, et le monde n'est plus retenu par la table (sinon ses
     * chunks et ses entites resteraient en memoire).
     */
    public static void forget(ClientWorld world) {
        Lightmap lightmap = LIGHTMAPS.remove(world);
        if (lightmap != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(lightmap.id);
        }
    }

    /** Fin de session ou changement de monde vanilla : les textures des vues sont detruites. */
    public static void clear() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Lightmap lightmap : LIGHTMAPS.values()) {
            client.getTextureManager().destroyTexture(lightmap.id);
        }
        LIGHTMAPS.clear();
    }

    private static Lightmap create(MinecraftClient client) {
        NativeImageBackedTexture texture = new NativeImageBackedTexture(SIZE, SIZE, false);
        Identifier id = client.getTextureManager().registerDynamicTexture("gtgs_backrooms_lightmap", texture);
        return new Lightmap(texture, id);
    }
}
