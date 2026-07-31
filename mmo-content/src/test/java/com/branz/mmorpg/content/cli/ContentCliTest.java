package com.branz.mmorpg.content.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContentCliTest {
    @Test
    void validatesManifestFixture() throws URISyntaxException {
        ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream standardError = new ByteArrayOutputStream();
        Path fixture =
                Path.of(getClass().getResource("/fixtures/content-manifest.valid.json").toURI());

        int exitCode =
                ContentCli.execute(
                        new String[] {"validate", fixture.toString()},
                        new PrintStream(standardOutput, true, StandardCharsets.UTF_8),
                        new PrintStream(standardError, true, StandardCharsets.UTF_8));

        assertEquals(0, exitCode);
        assertTrue(
                standardOutput.toString(StandardCharsets.UTF_8).contains("Valid content manifest"));
        assertEquals("", standardError.toString(StandardCharsets.UTF_8));
    }
}
