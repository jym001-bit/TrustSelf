package com.paicli.render.inline;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ThinkingBlockTest {
    @Test
    void splitUtf8CharactersSurviveFlushBetweenBytes() {
        var bytes = new ByteArrayOutputStream();
        try (var renderer = new InlineRenderer(null, new PrintStream(bytes, true, StandardCharsets.UTF_8))) {
            String text = "中文思考内容\n";
            for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
                renderer.stream().write(b & 0xff);
                renderer.stream().flush();
            }
            assertEquals(text, bytes.toString(StandardCharsets.UTF_8));
        }
    }
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
            assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("Ctrl+T: recent thinking"));
            assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("private reasoning detail"));
            String originalTranscript = bytes.toString(StandardCharsets.UTF_8);
            assertTrue(renderer.toggleThinkingBlocks());
            assertTrue(renderer.thinkingDetails().contains("private reasoning detail"));
            assertTrue(renderer.thinkingDetails().contains("second iteration detail"));
            assertEquals(originalTranscript, bytes.toString(StandardCharsets.UTF_8));
            renderer.toggleThinkingBlocks();
            assertEquals("", renderer.thinkingDetails());
            assertEquals(originalTranscript, bytes.toString(StandardCharsets.UTF_8));
            renderer.beginTurn();
            assertTrue(renderer.toggleThinkingBlocks());
            assertTrue(renderer.thinkingDetails().contains("private reasoning detail"));
        }
    }

    @Test
    void plainParagraphFlushesBeforeNewline() {
        var bytes = new ByteArrayOutputStream();
        try (var renderer = new InlineRenderer(null, new PrintStream(bytes, true, StandardCharsets.UTF_8))) {
            var markdown = new com.paicli.util.TerminalMarkdownRenderer(renderer.stream());
            String text = "This is a long paragraph without a newline";
            markdown.append(text);
            assertEquals(text, bytes.toString(StandardCharsets.UTF_8));
            markdown.append(" and its continuation");
            markdown.finish();
            assertEquals(text + " and its continuation" + System.lineSeparator(), bytes.toString(StandardCharsets.UTF_8));
        }
    }
}
