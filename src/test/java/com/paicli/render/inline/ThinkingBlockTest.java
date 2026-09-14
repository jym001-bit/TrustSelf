package com.paicli.render.inline;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ThinkingBlockTest {
    @Test
    void reasoningStaysHiddenUntilExpandedAndCanCollapseAgain() {
        var bytes = new ByteArrayOutputStream();
        try (var renderer = new InlineRenderer(null, new PrintStream(bytes, true, StandardCharsets.UTF_8))) {
            renderer.beginTurn();
            renderer.beginThinking("Thinking");
            renderer.appendThinking("private reasoning detail");
            assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("private reasoning detail"));
            renderer.endThinking();
            renderer.appendThinkingBlock("private reasoning detail");
            renderer.appendThinkingBlock("second iteration detail");
            assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("ctrl+t to expand"));
            assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("private reasoning detail"));
            bytes.reset();
            assertTrue(renderer.toggleThinkingBlocks());
            assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("private reasoning detail"));
            assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("second iteration detail"));
            bytes.reset();
            renderer.toggleThinkingBlocks();
            assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("private reasoning detail"));
            renderer.beginTurn();
            assertFalse(renderer.toggleThinkingBlocks());
        }
    }
}
