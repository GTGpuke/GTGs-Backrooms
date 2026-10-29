package com.gtgpuke.gtgsbackrooms.compat;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.gtgpuke.gtgsbackrooms.compat.iris.IrisVersion;

/**
 * Les mixins des modules de compatibilite ne s'appliquent que si leur mod est la, dans une version dont les classes visees
 * ont ete verifiees : absent ou hors de la plage, ils ne sont pas appliques. Appele pendant le demarrage de Mixin : rien
 * ici ne charge de classe du mod tiers.
 */
public final class CompatMixinPlugin implements IMixinConfigPlugin {

    private static final String IRIS_PACKAGE = "com.gtgpuke.gtgsbackrooms.compat.iris.mixin.";

    @Override
    public void onLoad(String mixinPackage) {
        // Rien a preparer.
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !mixinClassName.startsWith(IRIS_PACKAGE) || IrisVersion.supported();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // Aucune cible a retirer.
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Rien avant l'application.
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Rien apres l'application.
    }
}
