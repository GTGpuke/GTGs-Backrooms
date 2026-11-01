package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSchedule;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Ce que la cuisson retient d'un profil : la lumiere que renvoie une dalle (couleur du neon fois son emission), la lumiere
 * de fond, la portee d'une dalle, et les neons morts du calendrier, qui ne cuisent pas.
 *
 * @param red lumiere d'une dalle
 * @param green lumiere d'une dalle
 * @param blue lumiere d'une dalle
 * @param ambientRed lumiere de fond de toute surface
 * @param ambientGreen lumiere de fond
 * @param ambientBlue lumiere de fond
 * @param reach portee d'une dalle, en blocs
 * @param size cote du rectangle qui eclaire, en blocs
 * @param cone cosinus de l'angle ou une dalle cesse d'eclairer, compte depuis sa normale
 * @param shadowSize cote de ce que les rayons des ombres visent, en blocs : plus petit, le bord des ombres est plus net
 * @param occlusion ce qu'une surface perd dans un coin ferme, de 0 a 1
 * @param bounce part de la lumiere que renvoient les surfaces : plus basse, ce que les neons n'atteignent pas reste sombre
 * @param seed graine du calendrier des neons de la dimension
 * @param flickering part des neons qui vacillent
 * @param dead part des neons morts
 * @param blackout part des secteurs en panne
 */
public record LightBakeSettings(float red, float green, float blue, float ambientRed, float ambientGreen, float ambientBlue, float reach, float size, float cone, float shadowSize,
        float occlusion, float bounce, int seed, float flickering, float dead, float blackout) {

    /** Les reglages d'un profil a lumiere cuite. */
    public static LightBakeSettings of(LookProfile profile, Identifier dimension) {
        LookProfile.Neons neons = profile.neons();
        LookProfile.BakedLight baked = profile.bakedLight();
        return new LightBakeSettings(neons.red() * baked.emission(), neons.green() * baked.emission(), neons.blue() * baked.emission(),
                (float) baked.ambient().x, (float) baked.ambient().y, (float) baked.ambient().z, baked.reach(), baked.size(),
                (float) Math.cos(Math.toRadians(baked.angle())), baked.shadowSize(), baked.occlusion(), baked.bounce(),
                NeonSchedule.seed(dimension), neons.flickering(), neons.dead(), neons.blackout());
    }

    /** {@code true} si le neon de cette position n'est pas mort, ni son secteur en panne. */
    boolean lit(int x, int y, int z) {
        return !NeonSchedule.blackout(x, z, this.seed, this.blackout)
                && NeonSchedule.kind(NeonSchedule.character(x, y, z, this.seed), this.flickering, this.dead) != NeonSchedule.DEAD;
    }
}
