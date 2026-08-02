package io.github.javakky.phpgloss.parser;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class PhpDocTypeReferenceDetectorTest {
    private final PhpDocTypeReferenceDetector detector = new PhpDocTypeReferenceDetector();

    @Test
    public void detectsSimpleReference() {
        assertDetected("/** @param UserData $data */", "UserData");
    }

    @Test
    public void detectsGenericReference() {
        assertDetected("/** @param list<UserData> $data */", "UserData");
    }

    @Test
    public void detectsUnionReference() {
        assertDetected("/** @return UserData|null */", "UserData");
    }

    @Test
    public void detectsNestedGenericReference() {
        assertDetected("/** @phpstan-param Collection<int, UserData> $data */", "UserData");
    }

    @Test
    public void excludesVariableTagAndDescriptionWords() {
        assertEmpty("/** @param UserData $payload description */", "payload");
        assertEmpty("/** @param UserData $payload description */", "param");
        assertEmpty("/** @param UserData $payload description */", "description");
    }

    private void assertDetected(String doc, String word) {
        int offset = doc.indexOf(word);
        Optional<DetectedTypeReference> result = detector.detect(doc, 0, offset);
        assertTrue(result.isPresent());
        assertEquals(word, result.get().name());
    }

    private void assertEmpty(String doc, String word) {
        int offset = doc.indexOf(word);
        Optional<DetectedTypeReference> result = detector.detect(doc, 0, offset);
        assertTrue(result.isEmpty());
    }
}

