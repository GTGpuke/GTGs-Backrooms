package com.gtgpuke.gtgsbackrooms.core.render.light;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Budget de tuiles par image")
class TileBudgetTest {

    @Test
    @DisplayName("Sans mesure, le depart prudent ; une passe vide ne compte pas")
    void startsCautious() {
        TileBudget budget = new TileBudget(1.5f);
        assertEquals(TileBudget.START, budget.tiles(), "Depart.");
        budget.measured(0, 3.0f);
        assertEquals(TileBudget.START, budget.tiles(), "Rien de calcule : rien d'appris.");
        assertEquals(0.0f, budget.perTile(), 1.0e-9f, "Aucun cout connu.");
    }

    @Test
    @DisplayName("Autant de tuiles que le temps permis en laisse, d'apres le cout mesure")
    void followsTheCost() {
        TileBudget budget = new TileBudget(1.5f);
        budget.measured(100, 1.0f);
        assertEquals(0.01f, budget.perTile(), 1.0e-6f, "Le premier cout tel quel.");
        assertEquals(150, budget.tiles(), "1,5 ms a 0,01 ms la tuile.");
        budget.measured(100, 3.0f);
        assertEquals(0.015f, budget.perTile(), 1.0e-6f, "Un cout plus eleve tire la moyenne vers lui.");
        assertEquals(100, budget.tiles(), "Moins de tuiles.");
    }

    @Test
    @DisplayName("Une carte tres rapide reste bornee, une tres lente avance quand meme")
    void staysBounded() {
        TileBudget fast = new TileBudget(1.5f);
        fast.measured(1000, 0.001f);
        assertEquals(TileBudget.MAX, fast.tiles(), "Plafond.");
        TileBudget slow = new TileBudget(1.5f);
        slow.measured(10, 100.0f);
        assertEquals(TileBudget.MIN, slow.tiles(), "Plancher.");
    }
}
