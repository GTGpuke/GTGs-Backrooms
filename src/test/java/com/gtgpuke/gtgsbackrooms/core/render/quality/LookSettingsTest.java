package com.gtgpuke.gtgsbackrooms.core.render.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

@DisplayName("Reglages du menu des Backrooms")
class LookSettingsTest {

    private static final float EPSILON = 1.0e-6f;
    private static final LookProfile LOOK = LookProfile.fromJson(JsonParser.parseString("{ \"dimension\": \"m:d\", \"id\": 1, \"tint\": [1, 1, 0.8, 0.3],"
            + " \"desaturation\": 0.1, \"grain\": 0.1, \"post\": { \"vignette\": 0.3, \"chromatic\": 0.2, \"bloom\": true, \"bloom_strength\": 1 },"
            + " \"neons\": { \"block\": \"m:tube\", \"flickering\": 0.2 },"
            + " \"baked_light\": { \"emission\": 3, \"relief\": 1, \"haze\": 0.1, \"sheen\": 0.5 } }").getAsJsonObject());

    private static LookSettings settings() {
        return LookSettings.defaults();
    }

    @Test
    @DisplayName("Seul un palier qui trace prend ce que le profil demande a la lumiere tracee, et un look vanilla n'en a pas")
    void tracedTier() {
        LookProfile traced = LookProfile.fromJson(JsonParser.parseString("{ \"dimension\": \"m:d\", \"id\": 1, \"neons\": { \"block\": \"m:tube\" },"
                + " \"baked_light\": { \"shadow_size\": 0.5, \"sheen\": 0.5, \"traced\": { \"shadow_size\": 3, \"sheen\": 0.25 } } }").getAsJsonObject());
        assertEquals(3.0f, settings().effective(traced, LookQuality.ULTRA).bakedLight().shadowSize(), "L'Ultra etale ses ombres.");
        assertEquals(0.25f, settings().effective(traced, LookQuality.ULTRA).bakedLight().sheen(), "Et prend son reflet.");
        assertEquals(0.5f, settings().effective(traced, LookQuality.HIGH).bakedLight().shadowSize(), "Le High garde la lumiere cuite.");
        assertEquals(0.0f, settings().with(LookOption.SHEEN, false).effective(traced, LookQuality.ULTRA).bakedLight().sheen(), "Une option coupe encore le reflet.");
        assertSame(LookProfile.VANILLA, settings().effective(LookProfile.VANILLA, LookQuality.ULTRA), "Vanilla reste vanilla.");
    }

    @Test
    @DisplayName("Les reflets demandent le cache de surfaces : ils suivent la matiere des blocs, que seuls ses paliers lisent")
    void sheenNeedsSurfaces() {
        assertEquals(0.0f, settings().effective(LOOK, LookQuality.MEDIUM).bakedLight().sheen(), EPSILON, "Pas de reflet en Medium.");
        assertTrue(settings().effective(LOOK, LookQuality.HIGH).bakedLight().sheen() > 0.0f, "Des reflets en High.");
        assertEquals(0.0f, settings().with(LookOption.SURFACE_CACHE, false).effective(LOOK, LookQuality.HIGH).bakedLight().sheen(), EPSILON, "Pas sans le cache.");
    }

    @Test
    @DisplayName("Par defaut, tous les effets sont actifs, sans option de confort sauf les secousses, coupees comme avant")
    void defaults() {
        LookSettings settings = settings();
        assertEquals(LookQuality.AUTO, settings.quality(), "Auto.");
        assertSame(VisualPreset.FAITHFUL, settings.preset(), "Fidele.");
        assertEquals(32, settings.pixelShadows(), "Pixels de 32.");
        for (LookOption option : new LookOption[] {LookOption.SURFACE_CACHE, LookOption.BLOOM, LookOption.HALOS, LookOption.SHEEN, LookOption.RELIEF, LookOption.VIGNETTE}) {
            assertTrue(settings.has(option), option.key() + " actif.");
        }
        assertFalse(settings.flashesReduced(), "Flashs permis.");
        assertFalse(settings.noiseReduced(), "Grain permis.");
        assertTrue(settings.motionReduced(), "Pas de secousse par defaut.");
    }

    @Test
    @DisplayName("Les options de lancement donnent le confort de depart")
    void launchOptions() {
        LookSettings comfort = LookSettings.defaults(true, false, true);
        assertTrue(comfort.flashesReduced(), "Flashs reduits.");
        assertTrue(comfort.motionReduced(), "Secousses coupees.");
        assertTrue(comfort.noiseReduced(), "Grain coupe.");
        LookSettings free = LookSettings.defaults(false, true, false);
        assertFalse(free.flashesReduced(), "Flashs permis.");
        assertFalse(free.motionReduced(), "Secousses permises.");
        assertFalse(free.noiseReduced(), "Grain permis.");
    }

    @Test
    @DisplayName("Chaque changement rend une copie, et les reglages en vigueur se relisent")
    void copies() {
        LookSettings before = settings();
        LookSettings off = before.with(LookOption.BLOOM, false);
        assertFalse(off.has(LookOption.BLOOM), "Coupe.");
        assertTrue(before.has(LookOption.BLOOM), "L'original n'a pas change.");
        assertTrue(off.with(LookOption.BLOOM, true).has(LookOption.BLOOM), "Remis.");
        assertEquals("ultra", before.withQuality("ultra").quality(), "Palier.");
        assertEquals(16, before.withPixelShadows(16).pixelShadows(), "Pixels.");
        assertEquals(32, before.withPixelShadows(7).pixelShadows(), "Une valeur inconnue vaut 32.");
        LookSettings previous = LookSettings.current();
        LookSettings.set(off);
        assertSame(off, LookSettings.current(), "Reglages en vigueur.");
        LookSettings.set(previous);
    }

    @Test
    @DisplayName("Choisir un preset pose ses pixels d'ombre, et le preset Confort vaut les trois options")
    void presets() {
        assertEquals(16, settings().withPreset(VisualPreset.PIXEL).pixelShadows(), "Pixel : gros pixels.");
        assertEquals(0, settings().withPreset(VisualPreset.REALISTIC).pixelShadows(), "Realiste : lumiere lisse.");
        LookSettings comfort = settings().with(LookOption.REDUCE_MOTION, false).withPreset(VisualPreset.COMFORT);
        assertTrue(comfort.flashesReduced(), "Aucun flash.");
        assertTrue(comfort.noiseReduced(), "Aucun grain.");
        assertTrue(comfort.motionReduced(), "Aucune secousse.");
        assertFalse(settings().with(LookOption.REDUCE_MOTION, false).motionReduced(), "Sans l'option ni le preset, la camera peut trembler.");
        assertTrue(settings().with(LookOption.REDUCE_FLASHES, true).flashesReduced(), "L'option seule.");
        assertTrue(settings().with(LookOption.REDUCE_NOISE, true).noiseReduced(), "L'option seule.");
    }

    @Test
    @DisplayName("Le fichier se relit a l'identique ; ce qui manque ou qu'on ne connait pas garde les valeurs de depart")
    void file() {
        LookSettings saved = settings().withQuality("high").withPreset(VisualPreset.CINEMATIC).with(LookOption.BLOOM, false);
        assertEquals(saved, LookSettings.fromJson(saved.toJson(), settings()), "Aller-retour.");
        assertEquals(settings(), LookSettings.fromJson(new JsonObject(), settings()), "Fichier vide.");
        JsonObject odd = JsonParser.parseString("{ \"preset\": \"inconnu\", \"pixel_shadows\": 9, \"options\": [\"bloom\", \"inconnue\"] }").getAsJsonObject();
        LookSettings read = LookSettings.fromJson(odd, settings());
        assertSame(VisualPreset.FAITHFUL, read.preset(), "Preset inconnu.");
        assertEquals(32, read.pixelShadows(), "Pixels inconnus.");
        assertTrue(read.has(LookOption.BLOOM), "Option connue.");
        assertFalse(read.has(LookOption.HALOS), "Le fichier donne toutes les options actives.");
        assertSame(LookOption.BLOOM, LookOption.byKey("bloom"), "Par son nom.");
        assertNull(LookOption.byKey("inconnue"), "Nom inconnu.");
    }

    @Test
    @DisplayName("Fidele en High laisse le look du fichier ; chaque palier et option retire ce qu'il doit, sans toucher a la lumiere")
    void effectiveByTier() {
        LookProfile high = settings().effective(LOOK, LookQuality.HIGH);
        assertEquals(LOOK.post(), high.post(), "High : tout le post.");
        assertEquals(LOOK.bakedLight(), high.bakedLight(), "High : relief, halos, reflet.");
        LookProfile medium = settings().effective(LOOK, LookQuality.MEDIUM);
        assertEquals(0.3f, medium.post().vignette(), EPSILON, "Medium : la vignette.");
        assertFalse(medium.post().bloom(), "Medium : pas de bloom.");
        assertEquals(0.0f, medium.bakedLight().relief(), EPSILON, "Medium : pas de relief sans cache de surfaces.");
        assertEquals(0.1f, medium.bakedLight().haze(), EPSILON, "Medium : les halos.");
        LookProfile low = settings().effective(LOOK, LookQuality.LOW);
        assertTrue(low.post().none(), "Low : aucun post.");
        assertEquals(0.0f, low.bakedLight().haze() + low.bakedLight().sheen(), EPSILON, "Low : ni halos ni reflet.");
        assertTrue(low.bakedLight().any(), "Low : la lumiere cuite reste.");
        assertEquals(LOOK.bakedLight().emission(), low.bakedLight().emission(), EPSILON, "Meme lumiere a tous les paliers : aucun avantage de visibilite.");
        LookProfile off = settings().effective(LOOK, LookQuality.OFF);
        assertFalse(off.bakedLight().any(), "Desactive : pas de lumiere cuite.");
        assertFalse(off.neons().any(), "Ni neons.");
        assertEquals(0.0f, off.tintStrength() + off.grain() + off.desaturation(), EPSILON, "Ni teinte, ni grain, ni desaturation.");
        assertEquals(LOOK.id(), off.id(), "L'identifiant reste.");
        assertSame(LookProfile.VANILLA, settings().effective(LookProfile.VANILLA, LookQuality.HIGH), "Vanilla reste vanilla.");
        assertEquals(LookProfile.VANILLA, settings().effective(LookProfile.VANILLA, LookQuality.OFF), "Meme desactive.");
    }

    @Test
    @DisplayName("Chaque option du menu retire son effet, le cache de surfaces emporte le relief, et les options de confort s'appliquent")
    void effectiveByOption() {
        assertFalse(settings().with(LookOption.BLOOM, false).effective(LOOK, LookQuality.HIGH).post().bloom(), "Bloom.");
        LookProfile noBloom = LOOK.styled(LOOK.grain(), LOOK.desaturation(), new LookProfile.Post(0.3f, 0.2f, false, 0.7f, 1.0f), LOOK.grade(), LOOK.bakedLight());
        assertFalse(settings().effective(noBloom, LookQuality.ULTRA).post().bloom(), "Un look sans bloom n'en gagne pas au palier le plus haut.");
        assertEquals(0.0f, settings().with(LookOption.VIGNETTE, false).effective(LOOK, LookQuality.HIGH).post().vignette(), EPSILON, "Vignette.");
        assertEquals(0.0f, settings().with(LookOption.HALOS, false).effective(LOOK, LookQuality.HIGH).bakedLight().haze(), EPSILON, "Halos.");
        assertEquals(0.0f, settings().with(LookOption.SHEEN, false).effective(LOOK, LookQuality.HIGH).bakedLight().sheen(), EPSILON, "Reflet.");
        assertEquals(0.0f, settings().with(LookOption.RELIEF, false).effective(LOOK, LookQuality.HIGH).bakedLight().relief(), EPSILON, "Relief.");
        LookSettings noCache = settings().with(LookOption.SURFACE_CACHE, false);
        assertFalse(noCache.surfaceLight(LookQuality.HIGH), "Cache coupe.");
        assertEquals(0.0f, noCache.effective(LOOK, LookQuality.HIGH).bakedLight().relief(), EPSILON, "Le relief vit dans le cache.");
        assertTrue(settings().surfaceLight(LookQuality.ULTRA), "Actif en Ultra.");
        assertFalse(settings().surfaceLight(LookQuality.LOW), "Jamais en Low.");
        assertEquals(0.0f, settings().with(LookOption.REDUCE_NOISE, true).effective(LOOK, LookQuality.HIGH).grain(), EPSILON, "Sans grain.");
        assertEquals(0.0f, settings().with(LookOption.REDUCE_FLASHES, true).effective(LOOK, LookQuality.HIGH).neons().flickering(), EPSILON, "Aucun tube ne vacille.");
    }

    @Test
    @DisplayName("Chaque preset change le style sans toucher a la lumiere ni au brouillard, et les styles du post le gardent en Low")
    void presetLooks() {
        for (VisualPreset preset : VisualPreset.values()) {
            assertSame(preset, VisualPreset.byKey(preset.key()), "Par son nom : " + preset.key() + ".");
            LookProfile styled = preset.apply(LOOK);
            assertEquals(LOOK.bakedLight().emission(), styled.bakedLight().emission(), EPSILON, "Meme lumiere : " + preset.key() + ".");
            assertSame(LOOK.fog(), styled.fog(), "Meme brouillard : " + preset.key() + ".");
            assertSame(LOOK.lightmap(), styled.lightmap(), "Meme courbe : " + preset.key() + ".");
            assertSame(LookProfile.VANILLA, preset.apply(LookProfile.VANILLA), "Vanilla reste vanilla : " + preset.key() + ".");
        }
        assertSame(LOOK, VisualPreset.FAITHFUL.apply(LOOK), "Fidele : le look du fichier.");
        assertSame(LOOK, VisualPreset.COMFORT.apply(LOOK), "Confort passe par les options.");
        LookProfile pixel = VisualPreset.PIXEL.apply(LOOK);
        assertTrue(pixel.bakedLight().relief() > LOOK.bakedLight().relief(), "Pixel : relief marque.");
        assertTrue(pixel.post().bloomStrength() < LOOK.post().bloomStrength(), "Pixel : moins de rayonnement.");
        LookProfile realistic = VisualPreset.REALISTIC.apply(LOOK);
        assertTrue(realistic.bakedLight().sheen() > LOOK.bakedLight().sheen(), "Realiste : reflets pousses.");
        LookProfile tape = VisualPreset.FOUND_FOOTAGE.apply(LOOK);
        assertTrue(tape.grain() > LOOK.grain(), "Bande : plus de grain.");
        assertTrue(tape.post().chromatic() > LOOK.post().chromatic(), "Bande : couleurs qui bavent.");
        assertTrue(tape.desaturation() > LOOK.desaturation(), "Bande : balance approximative.");
        assertEquals(1.0f, VisualPreset.FOUND_FOOTAGE.tape(), EPSILON, "Effet de bande.");
        assertEquals(0.0f, VisualPreset.FAITHFUL.tape(), EPSILON, "Pas de bande.");
        LookProfile film = VisualPreset.CINEMATIC.apply(LOOK);
        assertTrue(film.post().vignette() > LOOK.post().vignette(), "Film : vignette.");
        assertTrue(film.grade().power().x > 1.0, "Film : contraste dense.");
        assertTrue(VisualPreset.CINEMATIC.bars() > 0.0f, "Film : bandes noires.");
        assertEquals(0.0f, VisualPreset.FAITHFUL.bars(), EPSILON, "Pas de bandes.");
        assertSame(VisualPreset.FAITHFUL, VisualPreset.byKey("inconnu"), "Nom inconnu.");
        assertTrue(VisualPreset.FOUND_FOOTAGE.needsPost() && VisualPreset.CINEMATIC.needsPost(), "Ces deux-la vivent dans le post.");
        assertFalse(VisualPreset.PIXEL.needsPost(), "Pas celui-ci.");
        assertFalse(settings().withPreset(VisualPreset.CINEMATIC).effective(LOOK, LookQuality.LOW).post().none(), "Le film garde son post en Low.");
    }
}
