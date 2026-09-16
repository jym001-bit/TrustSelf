package com.paicli.cli;

import org.jline.terminal.Size;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SlashMenuLineReaderTest {
    @Test
    void footerIsClippedAndOwnedByTheLineReaderDisplay() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var terminal = TerminalBuilder.builder().system(false).type("xterm")
                .streams(new ByteArrayInputStream(new byte[0]), output)
                .encoding(StandardCharsets.UTF_8).build()) {
            terminal.setSize(new Size(12, 20));
            var reader = new SlashMenuLineReader(terminal);
            reader.setFooterLines(() -> List.of(new AttributedString("footer status")));
            reader.runMacro("\r");
            assertEquals("", reader.readLine("> "));
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("footer stat"));

            AttributedString footer = reader.renderFooter(List.of(
                    new AttributedString("first status line"),
                    new AttributedString("second status line")));

            assertEquals("first statu\nsecond stat", footer.toString());
        }
    }

    @Test
    void filtersCommandsAndIgnoresPathsAndArguments() {
        assertTrue(SlashMenuLineReader.matchingCommands("/").size() > 10);
        assertEquals(List.of("/model"), SlashMenuLineReader.matchingCommands("/mod").stream()
                .map(Main.SlashCommandHint::insertText).toList());
        for (String input : List.of("hello/", "src/main", "/model ", "/not-a-command"))
            assertTrue(SlashMenuLineReader.matchingCommands(input).isEmpty());
    }

    @Test
    void typingFiltersAndEnterInsertsBeforeSubmitting() throws Exception {
        assertEquals("/model", read("/mod\r\r"));
        assertEquals("/model deepseek", read("/mod\t deepseek\r"));
        assertEquals("hello/world", read("hello/world\r"));
    }

    @Test
    void arrowsSelectAndEscapeKeepsInput() throws Exception {
        var choices = SlashMenuLineReader.matchingCommands("/");
        assertEquals(choices.get(1).insertText().stripTrailing(), read("/\033[B\r\r"));
        assertEquals("/mod", read("/mod", true));
    }

    private String read(String keys) throws Exception {
        return read(keys, false);
    }

    private String read(String keys, boolean escapeDirectly) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var terminal = TerminalBuilder.builder().system(false).type("xterm")
                .streams(new ByteArrayInputStream(new byte[0]), output)
                .encoding(StandardCharsets.UTF_8).build()) {
            terminal.setSize(new Size(100, 30));
            var reader = new SlashMenuLineReader(terminal);
            Main.configureSlashCommandHint(reader);
            Main.configureJLineInteractiveWidgets(reader);
            Main.bindEscToClearInput(reader);
            reader.installSlashMenuBindings();
            if (escapeDirectly) {
                reader.getBuffer().write(keys);
                var escape = (org.jline.reader.Widget) reader.getKeyMaps()
                        .get(org.jline.reader.LineReader.MAIN).getBound("\033");
                escape.apply();
                assertEquals(keys, reader.getBuffer().toString());
                return keys;
            }
            reader.runMacro(keys);
            return reader.readLine("> ");
        }
    }
}
