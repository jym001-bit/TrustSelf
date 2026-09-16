package com.paicli.render.inline;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;

import java.util.Locale;
import java.util.Map;

/**
 * 终端能力探测：决定 inline 渲染器的各项特性是否可启用。
 *
 * <p>探测逻辑保守——能开则开，老终端 / 非 TTY 环境优雅降级。
 */
public final class TerminalCapabilities {

    private TerminalCapabilities() {
    }

    /** 终端是否能渲染 ANSI 转义序列（颜色、光标控制、inline status 等）。 */
    public static boolean supportsAnsi(Terminal terminal) {
        if (terminal == null) {
            return false;
        }
        String type = terminal.getType();
        if (type != null && type.equalsIgnoreCase("dumb")) {
            return false;
        }
        if (System.getenv("NO_COLOR") != null) {
            // NO_COLOR 只影响样式，不影响光标控制——保留 true，颜色由 AnsiStyle 自己关
            return true;
        }
        String envTerm = System.getenv("TERM");
        return envTerm == null || !envTerm.equalsIgnoreCase("dumb");
    }

    /**
     * 终端是否适合启用 inline status 状态区。
     * 同时校验终端尺寸合理（rows ≥ 5）。
     */
    public static boolean supportsScrollRegion(Terminal terminal) {
        return supportsScrollRegion(terminal, System.getenv());
    }

    static boolean supportsScrollRegion(Terminal terminal, Map<String, String> environment) {
        Map<String, String> env = environment == null ? Map.of() : environment;
        if (!supportsAnsi(terminal)) {
            return false;
        }
        if (statusBarDisabled(env)) {
            return false;
        }
        // JetBrains' JediTerm and xterm.js based IDE terminals accept DECSTBM,
        // but JLine Status updates can still scroll reserved rows into the
        // transcript when LineReader accepts or redraws a line. Their fallback
        // status is rendered through LineReader.post instead.
        if (isEmbeddedTerminal(env)) {
            return false;
        }
        Size size = safeSize(terminal);
        return size.getRows() >= 5 && size.getColumns() >= 20;
    }

    /** Whether an IDE terminal should show status through LineReader.post. */
    public static boolean supportsLineReaderFooter(Terminal terminal) {
        return supportsLineReaderFooter(terminal, System.getenv());
    }

    static boolean supportsLineReaderFooter(Terminal terminal, Map<String, String> environment) {
        Map<String, String> env = environment == null ? Map.of() : environment;
        if (!supportsAnsi(terminal) || statusBarDisabled(env) || !isEmbeddedTerminal(env)) {
            return false;
        }
        Size size = safeSize(terminal);
        return size.getRows() >= 5 && size.getColumns() >= 20;
    }

    private static boolean statusBarDisabled(Map<String, String> environment) {
        return Boolean.parseBoolean(environment.get("PAICLI_NO_STATUSBAR"))
                || Boolean.parseBoolean(System.getProperty("paicli.no.statusbar"));
    }

    static boolean isEmbeddedTerminal(Map<String, String> environment) {
        if (environment == null || environment.isEmpty()) {
            return false;
        }
        String emulator = environment.getOrDefault("TERMINAL_EMULATOR", "").toLowerCase(Locale.ROOT);
        if (emulator.contains("jetbrains") || emulator.contains("jediterm")) {
            return true;
        }
        String program = environment.getOrDefault("TERM_PROGRAM", "").toLowerCase(Locale.ROOT);
        return program.contains("vscode") || program.contains("cursor");
    }

    /** 终端是否支持 24-bit TrueColor（用于丰富的代码高亮等）。 */
    public static boolean supportsTrueColor() {
        String colorterm = System.getenv("COLORTERM");
        return "truecolor".equalsIgnoreCase(colorterm) || "24bit".equalsIgnoreCase(colorterm);
    }

    public static Size safeSize(Terminal terminal) {
        try {
            Size s = terminal.getSize();
            if (s == null || s.getRows() <= 0 || s.getColumns() <= 0) {
                return new Size(80, 24);
            }
            return s;
        } catch (Exception e) {
            return new Size(80, 24);
        }
    }
}
