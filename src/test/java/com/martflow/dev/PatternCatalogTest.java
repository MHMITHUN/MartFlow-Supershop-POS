package com.martflow.dev;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drift guards for the Developer Mode catalog: every class a card cites must exist in the
 * source tree, every cited test must exist, snippets must mention a real class and stay short.
 * This is the test that would have caught the old "ReceiptBuilder" ghost — a renamed class now
 * fails the build with a precise message instead of lying on a card.
 */
class PatternCatalogTest {

    private static Set<String> javaFileNames(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java"))
                    .map(p -> p.getFileName().toString().replace(".java", ""))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot walk " + root, e);
        }
    }

    @Test
    void exactlyEighteenPatternsInThreeCategories() {
        List<PatternCatalog.Pattern> all = PatternCatalog.all();
        assertEquals(18, all.size());
        assertEquals(4, all.stream().filter(p -> "Creational".equals(p.category())).count());
        assertEquals(5, all.stream().filter(p -> "Structural".equals(p.category())).count());
        assertEquals(9, all.stream().filter(p -> "Behavioral".equals(p.category())).count());
        assertEquals(18, all.stream().map(PatternCatalog.Pattern::id).distinct().count(),
                "pattern ids must be unique");
    }

    @Test
    void everyReferencedClassAndTestActuallyExists() {
        Set<String> main = javaFileNames(Path.of("src/main/java"));
        Set<String> test = javaFileNames(Path.of("src/test/java"));
        for (PatternCatalog.Pattern p : PatternCatalog.all()) {
            assertFalse(p.classes().isEmpty(), p.name() + " cites no classes");
            for (String cls : p.classes()) {
                assertTrue(main.contains(cls),
                        p.name() + " cites " + cls + " — no such class under src/main/java");
            }
            assertTrue(test.contains(p.testClass()),
                    p.name() + " cites test " + p.testClass() + " — no such class under src/test/java");
        }
    }

    @Test
    void everySnippetMentionsARealClassAndStaysShort() {
        for (PatternCatalog.Pattern p : PatternCatalog.all()) {
            String code = p.snippet().code();
            assertFalse(code == null || code.isBlank(), p.name() + " has an empty snippet");
            int lines = code.split("\n").length;
            assertTrue(lines <= 30, p.name() + " snippet is " + lines + " lines (max 30)");
            boolean mentions = p.classes().stream().anyMatch(code::contains);
            assertTrue(mentions, p.name() + " snippet never mentions any of its cited classes");
            assertFalse(p.snippet().file() == null || p.snippet().file().isBlank(),
                    p.name() + " snippet has no source file");
        }
    }

    @Test
    void liveLinksTargetInAppScreensWithBusinessRoles() {
        Set<String> roles = Set.of("CASHIER", "MANAGER", "ADMIN");
        for (PatternCatalog.Pattern p : PatternCatalog.all()) {
            assertTrue(p.live().href().startsWith("#/"),
                    p.name() + " live link must be an in-app route");
            assertTrue(roles.contains(p.live().minRole()),
                    p.name() + " live minRole must be a business role");
            assertFalse(p.live().label() == null || p.live().label().isBlank(),
                    p.name() + " live label is missing");
        }
    }

    @Test
    void everyCardTellsTheFullStory() {
        for (PatternCatalog.Pattern p : PatternCatalog.all()) {
            assertFalse(p.problem() == null || p.problem().isBlank(), p.name() + ": no problem");
            assertFalse(p.whyThisPattern() == null || p.whyThisPattern().isBlank(), p.name() + ": no why");
            assertFalse(p.alternative() == null || p.alternative().isBlank(),
                    p.name() + ": no rejected alternative — the viva answer needs it");
        }
    }
}
