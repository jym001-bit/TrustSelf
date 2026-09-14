import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.spi.TerminalProvider;
import org.jline.terminal.spi.SystemStream;

/** Run with: java -cp target/paicli-1.0-SNAPSHOT.jar tools/TerminalProbe.java */
class TerminalProbe {
    public static void main(String[] args) throws Exception {
        System.out.println("Java: " + System.getProperty("java.version"));
        System.out.println("TERM: " + System.getenv("TERM"));
        for (String name : new String[]{"jni"}) {
            try {
                var provider = TerminalProvider.load(name);
                System.out.println(name + " stdin=" + provider.isSystemStream(SystemStream.Input)
                        + " stdout=" + provider.isSystemStream(SystemStream.Output));
            } catch (Throwable error) {
                error.printStackTrace(System.out);
            }
        }
        try (var terminal = TerminalBuilder.builder().system(true).dumb(true).build()) {
            System.out.println("Terminal: " + terminal.getClass().getName());
            System.out.println("Type: " + terminal.getType() + " size=" + terminal.getSize());
            if (args.length > 0 && args[0].equals("--menu")) {
                var type = Class.forName("com.paicli.cli.SlashMenuLineReader");
                var constructor = type.getDeclaredConstructor(org.jline.terminal.Terminal.class);
                constructor.setAccessible(true);
                var reader = (org.jline.reader.LineReader) constructor.newInstance(terminal);
                var install = type.getDeclaredMethod("installSlashMenuBindings");
                install.setAccessible(true);
                install.invoke(reader);
                System.out.println("Type / to test the menu. Submitted text is only echoed, never executed.");
                System.out.println("Submitted: " + reader.readLine("> "));
            }
        }
    }
}
