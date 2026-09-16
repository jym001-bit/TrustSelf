package com.paicli.cli;

import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.Reference;
import org.jline.reader.Widget;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** A transient slash menu rendered by JLine alongside its input buffer. */
public final class SlashMenuLineReader extends LineReaderImpl {
    private Supplier<String> details = () -> "";
    private Supplier<List<AttributedString>> footerLines = List::of;
    private int detailOffset;

    public void setDetails(Supplier<String> details) {
        this.details = Objects.requireNonNullElse(details, () -> "");
        detailOffset = 0;
    }

    /**
     * Sets status lines rendered inside the same JLine display as the input.
     * This is the safe fallback for IDE terminals whose scroll regions are unreliable.
     */
    public void setFooterLines(Supplier<List<AttributedString>> footerLines) {
        this.footerLines = Objects.requireNonNullElse(footerLines, List::of);
    }
    private String previousInput = "";
    private String dismissedInput;
    private int selected;

    SlashMenuLineReader(Terminal terminal) throws java.io.IOException {
        super(terminal);
    }

    void installSlashMenuBindings() {
        Widget previousInit = getWidgets().get(CALLBACK_INIT);
        getWidgets().put(CALLBACK_INIT, () -> {
            dismissedInput = null;
            previousInput = "";
            selected = 0;
            return previousInit == null || previousInit.apply();
        });
        for (KeyMap<Binding> map : List.of(MAIN, EMACS, VIINS).stream()
                .map(getKeyMaps()::get).filter(java.util.Objects::nonNull).distinct().toList()) {
            bindMenuKey(map, "\033[A", -1);
            bindMenuKey(map, "\033OA", -1);
            bindMenuKey(map, "\033[B", 1);
            bindMenuKey(map, "\033OB", 1);
            bindMenuKey(map, "\t", 0);
            bindMenuKey(map, "\r", 0);
            bindMenuKey(map, "\n", 0);
            bindMenuKey(map, "\033", 2);
            bindDetailScroll(map, "\033[5~", -1);
            bindDetailScroll(map, "\033[6~", 1);
        }
    }

    private void bindDetailScroll(KeyMap<Binding> map, String key, int direction) {
        Binding fallback = map.getBound(key);
        map.bind((Widget) () -> {
            if (!details.get().isEmpty()) {
                detailOffset = Math.max(0, detailOffset + direction * Math.max(1, terminal.getHeight() / 2));
                return true;
            }
            if (fallback instanceof Reference ref) callWidget(ref.name());
            else if (fallback instanceof Widget widget) return widget.apply();
            return true;
        }, key);
    }

    private void bindMenuKey(KeyMap<Binding> map, String key, int action) {
        Binding fallback = map.getBound(key);
        map.bind((Widget) () -> {
            List<Main.SlashCommandHint> choices = menuChoices();
            if (!choices.isEmpty()) {
                if (action == 2) {
                    dismissedInput = getBuffer().toString();
                } else if (action == 0) {
                    String command = choices.get(selected).insertText().stripTrailing();
                    getBuffer().clear();
                    getBuffer().write(command);
                    dismissedInput = command;
                } else {
                    selected = Math.floorMod(selected + action, choices.size());
                }
                return true;
            }
            if (fallback instanceof Reference reference) callWidget(reference.name());
            else if (fallback instanceof Widget widget) return widget.apply();
            return true;
        }, key);
    }

    static List<Main.SlashCommandHint> matchingCommands(String input) {
        if (!input.startsWith("/") || input.chars().anyMatch(Character::isWhitespace)) return List.of();
        LinkedHashMap<String, Main.SlashCommandHint> commands = new LinkedHashMap<>();
        for (Main.SlashCommandHint hint : Main.slashCommandHints()) {
            String command = hint.insertText().stripTrailing();
            if (!command.contains(" ") && command.startsWith(input)) commands.putIfAbsent(command, hint);
        }
        return List.copyOf(commands.values());
    }

    private List<Main.SlashCommandHint> menuChoices() {
        String input = getBuffer().toString();
        if (!input.equals(previousInput)) {
            previousInput = input;
            selected = 0;
            if (!input.equals(dismissedInput)) dismissedInput = null;
        }
        if (input.equals(dismissedInput) || getBuffer().cursor() != getBuffer().length()
                || terminal.getType().startsWith("dumb")) return List.of();
        List<Main.SlashCommandHint> choices = matchingCommands(input);
        selected = choices.isEmpty() ? 0 : Math.min(selected, choices.size() - 1);
        return choices;
    }

    @Override
    protected void redisplay(boolean flush) {
        List<Main.SlashCommandHint> choices = menuChoices();
        String detailText = details.get();
        List<AttributedString> currentFooter = safeFooterLines();
        if (choices.isEmpty() && detailText.isEmpty() && currentFooter.isEmpty()) {
            super.redisplay(flush);
            return;
        }
        // Let JLine own cursor movement and clearing; never print a second screen over it.
        var previousPost = post;
        SuggestionType previousSuggestion = getAutosuggestion();
        post = () -> !choices.isEmpty()
                ? renderMenu(choices)
                : !detailText.isEmpty() ? renderDetails(detailText) : renderFooter(currentFooter);
        setAutosuggestion(SuggestionType.NONE);
        try {
            super.redisplay(flush);
        } finally {
            post = previousPost;
            setAutosuggestion(previousSuggestion);
        }
    }

    private List<AttributedString> safeFooterLines() {
        List<AttributedString> lines = footerLines.get();
        return lines == null ? List.of() : lines.stream().filter(Objects::nonNull).toList();
    }

    AttributedString renderFooter(List<AttributedString> lines) {
        int width = Math.max(1, terminal.getWidth() - 1);
        AttributedStringBuilder result = new AttributedStringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            result.append(lines.get(i).columnSubSequence(0, width));
            if (i + 1 < lines.size()) {
                result.append('\n');
            }
        }
        return result.toAttributedString();
    }

    private AttributedString renderDetails(String text) {
        int width = Math.max(1, terminal.getWidth() - 1);
        int height = Math.max(1, terminal.getHeight() - 9);
        var lines = new AttributedString(text).columnSplitLength(width, false, true);
        detailOffset = Math.min(detailOffset, Math.max(0, lines.size() - height));
        var result = new AttributedStringBuilder();
        result.append(new AttributedString("Details | PgUp/PgDn scroll | Ctrl+T/O close")
                .columnSubSequence(0, width)).append('\n');
        for (int i = detailOffset; i < Math.min(lines.size(), detailOffset + height); i++) {
            result.append(lines.get(i)).append('\n');
        }
        return result.toAttributedString();
    }

    private AttributedString renderMenu(List<Main.SlashCommandHint> choices) {
        int width = Math.max(1, terminal.getWidth() - 1);
        int count = Math.min(8, Math.max(1, terminal.getHeight() - 8));
        int start = Math.max(0, selected - count + 1);
        AttributedStringBuilder result = new AttributedStringBuilder();
        for (int i = start; i < Math.min(choices.size(), start + count); i++) {
            Main.SlashCommandHint hint = choices.get(i);
            String command = hint.insertText().stripTrailing();
            String text = (i == selected ? "> " : "  ") + String.format("%-16s", command)
                    + "  " + hint.description();
            AttributedStyle style = i == selected ? AttributedStyle.DEFAULT.inverse() : AttributedStyle.DEFAULT;
            result.append(new AttributedString(text, style).columnSubSequence(0, width)).append('\n');
        }
        result.append(new AttributedString((selected + 1) + "/" + choices.size()
                + "  Up/Down select | Tab/Enter insert | Esc close", AttributedStyle.DEFAULT.faint())
                .columnSubSequence(0, width));
        return result.toAttributedString();
    }
}
