package com.gtgpuke.gtgsbackrooms.core.portal.client.world;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.particle.EmitterParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleGroup;

import com.gtgpuke.gtgsbackrooms.mixin.client.ParticleManagerAccessor;

/**
 * Particules d'un monde distant.
 *
 * <p>Le gestionnaire de particules vanilla est unique (atlas, fabriques, feuilles de textures) ; seul son contenu
 * change de monde. Chaque monde distant garde ici son monde et ses files, echanges avec ceux du gestionnaire le temps
 * d'y ajouter, d'y faire vivre ou d'y dessiner ses particules : tout le code vanilla (fabriques, limites par groupe,
 * rendu par feuille) sert tel quel, et les particules d'un monde ne se melangent jamais a celles d'un autre. A la
 * traversee, le contenu du monde d'arrivee devient celui du gestionnaire et celui de l'ancien monde suit ce dernier,
 * sans rien effacer. Rien n'est alloue apres la creation.
 */
public final class RemoteParticles {

    /** Contenu echange avec le gestionnaire, ou {@code null} : aucun monde distant n'y est installe. */
    private static @Nullable RemoteParticles active;

    private ClientWorld world;
    private Map<ParticleTextureSheet, Queue<Particle>> particles = new IdentityHashMap<>();
    private Queue<EmitterParticle> emitters = new ArrayDeque<>();
    private Queue<Particle> fresh = new ArrayDeque<>();
    private Object2IntOpenHashMap<ParticleGroup> groups = new Object2IntOpenHashMap<>();

    RemoteParticles(ClientWorld world) {
        this.world = world;
    }

    /**
     * Installe ce contenu dans le gestionnaire ; s'il y est deja, rien ne change.
     *
     * @return le contenu installe avant, a rendre a {@link #exit}
     */
    @Nullable RemoteParticles enter(ParticleManager manager) {
        RemoteParticles previous = active;
        if (previous != this) {
            if (previous != null) {
                previous.exchange(manager);
            }
            exchange(manager);
            active = this;
        }
        return previous;
    }

    /** Rend au gestionnaire le contenu qu'il avait avant {@link #enter}. */
    void exit(ParticleManager manager, @Nullable RemoteParticles previous) {
        if (previous == this) {
            return;
        }
        exchange(manager);
        active = null;
        if (previous != null) {
            previous.exchange(manager);
            active = previous;
        }
    }

    /**
     * Echange monde et files avec le gestionnaire. A la traversee, un seul echange fait du monde d'arrivee celui du
     * gestionnaire ; ce contenu devient alors celui de l'ancien monde, que l'appelant confie a sa vue distante.
     */
    void exchange(ParticleManager manager) {
        ParticleManagerAccessor accessor = (ParticleManagerAccessor) manager;
        ClientWorld managerWorld = accessor.backrooms$getWorld();
        Map<ParticleTextureSheet, Queue<Particle>> managerParticles = accessor.backrooms$getParticles();
        Queue<EmitterParticle> managerEmitters = accessor.backrooms$getNewEmitterParticles();
        Queue<Particle> managerFresh = accessor.backrooms$getNewParticles();
        Object2IntOpenHashMap<ParticleGroup> managerGroups = accessor.backrooms$getGroupCounts();
        accessor.backrooms$setWorld(this.world);
        accessor.backrooms$setParticles(this.particles);
        accessor.backrooms$setNewEmitterParticles(this.emitters);
        accessor.backrooms$setNewParticles(this.fresh);
        accessor.backrooms$setGroupCounts(this.groups);
        this.world = managerWorld;
        this.particles = managerParticles;
        this.emitters = managerEmitters;
        this.fresh = managerFresh;
        this.groups = managerGroups;
    }

    /** Oublie toutes les particules ; sans effet sur un contenu installe dans le gestionnaire. */
    void clear() {
        if (active == this) {
            return;
        }
        this.particles.clear();
        this.emitters.clear();
        this.fresh.clear();
        this.groups.clear();
    }

    /** Particules de ce contenu, vivantes et en attente, qu'il soit installe dans le gestionnaire ou non (diagnostic). */
    int count(ParticleManager manager) {
        ParticleManagerAccessor accessor = (ParticleManagerAccessor) manager;
        Map<ParticleTextureSheet, Queue<Particle>> living = active == this ? accessor.backrooms$getParticles() : this.particles;
        int count = active == this ? accessor.backrooms$getNewParticles().size() : this.fresh.size();
        for (Queue<Particle> sheet : living.values()) {
            count += sheet.size();
        }
        return count;
    }
}
