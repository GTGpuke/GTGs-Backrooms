package com.gtgpuke.gtgsbackrooms.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalRenderState;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalStencilRenderer;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderPackPortalRenderer;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ViewRenderStart;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorld;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.StraddlingEntityRenderer;

/**
 * Points d'accroche du rendu monde pour les portails : dessin des portails avant la couche translucide et fin de l'image
 * sous une vue qui couvre l'ecran, vues dessinees apres l'image sous un pack de shaders (effacement de l'ecran epargne
 * au rendu complet d'une vue), ciel d'une vue rapporte a sa camera, particules des mondes distants confiees a leur
 * propre contenu, entities a cheval sur un portail laissees a leur dessin en deux moities, et reconstructions de
 * sections repercutees sur la vue secondaire de leur monde. Rien dans {@code setupTerrain} ni {@code renderLayer}, que
 * les mods de rendu reecrivent : une injection dans une methode reecrite par un mixin de priorite superieure ou egale
 * est refusee et le jeu ne demarre pas. La priorite 1001 passe apres ces mixins (1000 par defaut) : l'injection dans
 * {@code scheduleChunkRender}, qu'ils reecrivent aussi, s'applique a leur version de la methode.
 */
@Mixin(value = WorldRenderer.class, priority = 1001)
public abstract class MixinWorldRenderer {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private @Nullable ClientWorld world;

    /**
     * Les portails se dessinent apres les blocs opaques, les entities et les block entities, et avant tout ce qui est
     * translucide : l'eau, le verre teinte, les parties translucides des entities, les particules,
     * les nuages et la pluie du monde du joueur se fondent ensuite par-dessus la vue, au lieu d'en cacher le cadre. Les
     * parametres du rendu du joueur sont gardes le temps des portails pour le debut de rendu a blanc des renderers de
     * vue ({@link ViewRenderStart}). Pendant le rendu complet d'une vue sous un pack de shaders, seulement ses moities
     * d'entities a cheval sur un portail.
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/TexturedRenderLayers;getEntityTranslucentCull()Lnet/minecraft/client/render/RenderLayer;"))
    private void backrooms$renderPortals(
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightmapTextureManager lightmapTextureManager,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            CallbackInfo ci) {
        if (PortalRenderState.isRendering()) {
            ShaderPackPortalRenderer.renderViewParts(tickCounter.getTickDelta(false));
            return;
        }
        PortalRenderState.setBlockOutlineAllowed(renderBlockOutline);
        // Les renderers de vue repassent par la tete de cette methode avant de dessiner, avec ces parametres-ci.
        ViewRenderStart.beginFrame(tickCounter, camera, gameRenderer, lightmapTextureManager, positionMatrix, projectionMatrix);
        try {
            PortalStencilRenderer.renderAllVisiblePortals(camera, positionMatrix, projectionMatrix, lightmapTextureManager,
                    tickCounter.getTickDelta(false));
        } finally {
            ViewRenderStart.endFrame();
        }
    }

    /**
     * Fin du rendu du monde : le stencil qui refusait la suite, sous une vue couvrant l'ecran, est rendu ; et sous un pack
     * de shaders, les vues se dessinent ici, apres l'image du monde que le mod de shaders vient de composer (son
     * injection en fin de methode, appliquee avant celle-ci, a deja tourne). Rien a la fin du rendu complet d'une vue.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void backrooms$endPortals(
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightmapTextureManager lightmapTextureManager,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            CallbackInfo ci) {
        if (PortalRenderState.isRendering()) {
            return;
        }
        PortalStencilRenderer.endWorldRender();
        PortalStencilRenderer.renderAfterWorld(tickCounter, camera, lightmapTextureManager, positionMatrix, projectionMatrix);
    }

    /**
     * Sous un pack de shaders, une vue limitee a un cadre est un rendu complet de son renderer, apres l'image du monde du
     * joueur deja a l'ecran : l'effacement de l'ecran en tete de ce rendu l'emporterait. La zone du cadre est deja au
     * plan lointain.
     */
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void backrooms$keepWorldImageUnderShaderView(int mask, boolean getError, Operation<Void> original) {
        if (!ShaderPackPortalRenderer.skipsWorldClear()) {
            original.call(mask, getError);
        }
    }

    /**
     * Le ciel d'une vue se place par rapport a sa camera, pas a celle du joueur : couleur du ciel, et surtout le plan
     * sombre que vanilla tire sous l'horizon quand le joueur est bas (il noircirait le ciel vu depuis le Level 0). La
     * valeur de vanilla est remplacee apres coup : a chaque image, rien n'est alloue (l'appel d'origine d'un
     * {@code @WrapOperation} recoit ses arguments dans un tableau).
     */
    @ModifyExpressionValue(method = "renderSky",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getCameraPosVec(F)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d backrooms$skyPlayerCameraPos(Vec3d playerCameraPos) {
        Vec3d override = PortalRenderState.viewCameraPos();
        return override != null ? override : playerCameraPos;
    }

    @ModifyExpressionValue(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;getPos()Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d backrooms$skyCameraPos(Vec3d cameraPos) {
        Vec3d override = PortalRenderState.viewCameraPos();
        return override != null ? override : cameraPos;
    }

    /**
     * Les particules d'un monde distant (entities, effets des blocs, paquets du serveur) vont dans le contenu de
     * particules de ce monde, dessine dans ses vues ; vanilla les aurait ajoutees au monde du joueur, a ses coordonnees.
     */
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;ZZDDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void backrooms$routeRemoteParticles(
            ParticleEffect parameters,
            boolean shouldAlwaysSpawn,
            boolean important,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfo ci) {
        RemoteWorld remote = RemoteWorldRegistry.ownerOf((WorldRenderer) (Object) this);
        if (remote != null) {
            ci.cancel();
            remote.spawnParticle(this.client, parameters, shouldAlwaysSpawn, important, x, y, z, velocityX, velocityY, velocityZ);
        } else if (this.world != this.client.world && !PortalRenderState.isRendering()) {
            // Renderer sans monde distant connu : ses particules n'ont nulle part ou aller.
            ci.cancel();
        }
    }

    /** Une entity a cheval sur un portail se dessine en deux moities, chacune coupee au plan : vanilla ne la dessine pas entiere. */
    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void backrooms$skipEntityDrawnInParts(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (StraddlingEntityRenderer.skipsVanillaDraw(entity, cameraX, cameraY, cameraZ, tickDelta)) {
            ci.cancel();
        }
    }

    /**
     * Une section a reconstruire l'est aussi dans la vue secondaire de son monde (portails qui y ramenent). Le monde du
     * renderer designe la vue, pas celui de {@code MinecraftClient}, substitue pendant le rendu des portails : les mises
     * a jour de lumiere faites pendant une vue sont relayees elles aussi.
     */
    /**
     * Le renderer du joueur se recharge (pack de shaders active ou coupe, mode graphique, F3+A) : ceux des vues aussi. Leurs
     * sections gardaient sinon le format de sommets et les couches d'avant jusqu'au prochain rechargement des ressources (un
     * pack coupe laissait des feuilles et du verre opaques dans les vues, puis dans le monde rejoint par un portail). Pas
     * pendant le rendu des portails, ou le renderer du client est celui d'une vue.
     */
    @Inject(method = "reload()V", at = @At("TAIL"))
    private void backrooms$reloadViews(CallbackInfo ci) {
        if (ModConfig.ENABLE_PORTAL_FEATURE && (Object) this == this.client.worldRenderer && !PortalRenderState.isRendering()) {
            RemoteWorldRegistry.reloadViewRenderers((WorldRenderer) (Object) this);
        }
    }

    @Inject(method = "scheduleChunkRender", at = @At("HEAD"))
    private void backrooms$mirrorChunkRender(int x, int y, int z, boolean important, CallbackInfo ci) {
        RemoteWorldRegistry.onChunkRenderScheduled((WorldRenderer) (Object) this, this.world, x, y, z, important);
    }

    /**
     * Vue allegee d'un portail (palier de detail) : ni pluie ni neige, sur les deux chemins de rendu (le dessin d'une vue
     * l'appelle directement, le rendu complet de son renderer sous un pack de shaders depuis {@code render}).
     */
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void backrooms$noWeatherInReducedView(
            LightmapTextureManager manager, float tickDelta, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        if (PortalRenderState.reducedView()) {
            ci.cancel();
        }
    }
}
