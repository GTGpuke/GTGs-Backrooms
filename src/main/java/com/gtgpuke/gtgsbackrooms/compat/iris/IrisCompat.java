package com.gtgpuke.gtgsbackrooms.compat.iris;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import com.gtgpuke.gtgsbackrooms.compat.iris.mixin.PipelineManagerAccessor;
import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalRenderState;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;

/**
 * Compatibilite avec le mod de shaders Iris : dans une dimension a look (les Backrooms), le rendu du mod remplace le pack
 * de shaders actif (decision de l'auteur). Tant que le joueur est dans une telle dimension, le gestionnaire de pipelines
 * d'Iris rend son pipeline vanilla pour tout ce qui se dessine, monde du joueur et vues de portail : les shaders vanilla,
 * patches par le mod, dessinent tout (look, neons, post), et aucune image ne melange le format de maillage du terrain du
 * pack et celui de vanilla (melanges, ils dessinaient le terrain en eclats, essai du 27 octobre 2026). En sortant, le
 * pipeline du pack, garde compile dans le cache d'Iris, reprend sans recompilation.
 *
 * <p>Chaque pipeline d'Iris pose a sa creation des reglages globaux (format des maillages, ombrage, identifiants pour le
 * pack) qu'un pipeline repris du cache ne repose pas : ils sont gardes et reposes a chaque bascule
 * ({@link PipelineSettings}), le mod de terrain recharge ses shaders de sections, et le terrain est reconstruit dans le
 * format du pipeline courant (le renderer du joueur ; ceux des vues suivent son rechargement). La bascule ne se fait
 * qu'au rendu du monde du joueur, jamais au milieu d'une vue. Depuis l'Overworld, une vue du Level 0 reste rendue par le
 * pack, le look du mod pose dessus.
 *
 * <p>Option {@code gtgsbackrooms.look.replaceShaderPack} (vrai par defaut) ; coupee, ou Iris hors de la plage verifiee
 * ({@link IrisVersion}), le pack rend aussi les Backrooms et le look se pose sur son image. Chargee seulement quand
 * Iris l'est (les mixins qui l'appellent ne s'appliquent pas sans lui).
 */
public final class IrisCompat {

    private static @Nullable WorldRenderingPipeline vanilla;
    private static @Nullable PipelineSettings packSettings;
    private static @Nullable PipelineSettings vanillaSettings;
    private static boolean replacing;
    private static boolean logged;

    private IrisCompat() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Au debut de chaque rendu du monde, avant qu'Iris ne prepare le sien : le pipeline a utiliser a la place, ou
     * {@code null} pour celui d'Iris.
     *
     * @param manager gestionnaire de pipelines d'Iris
     * @return le pipeline vanilla du mod dans une dimension a look, sinon {@code null}
     */
    public static @Nullable WorldRenderingPipeline override(PipelineManager manager) {
        if (PortalRenderState.isRendering()) {
            // Au milieu d'une vue : le choix de l'image tient.
            return replacing ? vanilla : null;
        }
        if (wantsVanilla()) {
            if (!replacing) {
                packSettings = PipelineSettings.capture();
                if (vanilla == null) {
                    // Sa construction pose ses reglages : les garder pour les bascules suivantes.
                    vanilla = new VanillaRenderingPipeline();
                    vanillaSettings = PipelineSettings.capture();
                } else {
                    vanillaSettings.apply();
                }
                replacing = true;
                switched(manager);
            }
            ((PipelineManagerAccessor) manager).backrooms$setPipeline(vanilla);
            return vanilla;
        }
        if (replacing) {
            replacing = false;
            packSettings.apply();
            switched(manager);
        }
        return null;
    }

    /** Iris detruit ses pipelines (pack change, recharge, shaders coupes) : le pipeline vanilla du mod part avec eux. */
    public static void onPipelinesDestroyed() {
        if (vanilla != null) {
            vanilla.destroy();
            vanilla = null;
        }
        replacing = false;
        packSettings = null;
        vanillaSettings = null;
    }

    /** Un pack est charge, l'option le permet, et le joueur est dans une dimension a look. */
    private static boolean wantsVanilla() {
        if (!ModConfig.LOOK_REPLACE_SHADER_PACK || !ModConfig.ENABLE_LOOK || Iris.getCurrentPack().isEmpty()) {
            return false;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null && !LookProfiles.of(player.getWorld().getRegistryKey()).vanilla();
    }

    /** Apres une bascule : shaders de sections recharges, terrain reconstruit dans le nouveau format. */
    private static void switched(PipelineManager manager) {
        PipelineManagerAccessor accessor = (PipelineManagerAccessor) manager;
        accessor.backrooms$setShaderReloadCounter(accessor.backrooms$getShaderReloadCounter() + 1);
        WorldRenderingSettings.INSTANCE.clearReloadRequired();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer != null) {
            client.worldRenderer.reload();
        }
        if (!logged) {
            logged = true;
            ModConstants.LOGGER.info("[Look] Pack de shaders actif : dans les dimensions a look, le rendu du mod le remplace (pipeline vanilla d'Iris).");
        }
    }
}
