package com.gtgpuke.gtgsbackrooms.compat.iris;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;

@DisplayName("Plage de versions verifiee du mod de shaders")
class IrisVersionTest {

    private static boolean inRange(String version) throws VersionParsingException {
        return IrisVersion.inRange(SemanticVersion.parse(version));
    }

    @Test
    @DisplayName("Les versions verifiees sont acceptees, metadonnees de build comprises")
    void verifiedVersionsAreAccepted() throws VersionParsingException {
        assertTrue(inRange("1.8.8+1.21.1-fabric"), "1.8.8 est la premiere version verifiee.");
        assertTrue(inRange("1.8.11+1.21.1-fabric"), "Une version entre les deux bornes est acceptee.");
        assertTrue(inRange("1.8.14-beta.1+1.21.1-fabric"), "1.8.14-beta.1 est la derniere version verifiee.");
    }

    @Test
    @DisplayName("Une version non verifiee, avant comme apres, est refusee")
    void unverifiedVersionsAreRefused() throws VersionParsingException {
        assertFalse(inRange("1.8.7+1.21.1-fabric"), "1.8.7 precede la plage.");
        assertFalse(inRange("1.8.14+1.21.1-fabric"), "1.8.14 finale n'a pas ete verifiee.");
        assertFalse(inRange("1.9.0+1.21.1-fabric"), "1.9.0 n'a pas ete verifiee.");
    }

    @Test
    @DisplayName("Sans le mod de shaders, le module ne s'applique pas")
    void absentModIsNotSupported() {
        assertFalse(IrisVersion.present(), "Le mod de shaders n'est pas charge dans les tests unitaires.");
        assertFalse(IrisVersion.supported(), "Absent, il n'est pas dans la plage.");
    }
}
