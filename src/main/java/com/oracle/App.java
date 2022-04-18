package com.oracle;

import java.io.*;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jline.builtins.*;
import org.jline.builtins.Completers.OptionCompleter;
import org.jline.console.impl.*;
import org.jline.console.CommandInput;
import org.jline.console.CommandMethods;
import org.jline.console.CommandRegistry;
import org.jline.console.ConsoleEngine;
import org.jline.console.Printer;
import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.reader.LineReader.Option;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.reader.impl.DefaultParser.Bracket;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.script.GroovyCommand;
import org.jline.script.GroovyEngine;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.Terminal.Signal;
import org.jline.utils.InfoCmp;
import org.jline.utils.InfoCmp.Capability;
import org.jline.utils.OSUtils;
import org.jline.widget.TailTipWidgets;
import org.jline.widget.TailTipWidgets.TipType;
import org.jline.widget.Widgets;

public class App {
    public static String POWERLINE_RIGHT_BLOCK_ARROW = "\uE0B0";
    public static String POWERLINE_RIGHT_THIN_ARROW = "\uE0B1";
    public static String ESCAPE = "\u001B";

    protected static class MyCommands extends JlineCommandRegistry implements CommandRegistry {
        private LineReader reader;
        private final Supplier<Path> workDir;
        private final Map<String, CommandMethods> commandExecute = new HashMap<>();

        public MyCommands(Supplier<Path> workDir) {
            super();
            this.workDir = workDir;
            commandExecute.put("clear", new CommandMethods(this::clear, this::defaultCompleter));
            commandExecute.put("exit", new CommandMethods(this::exit, this::defaultCompleter));
            commandExecute.put("!", new CommandMethods(this::shell, this::defaultCompleter));
            registerCommands(commandExecute);
        }

        public void setLineReader(LineReader reader) {
            this.reader = reader;
        }

        public boolean isCommand(String command) {
            return commandExecute.get(command) != null;
        }

        private Terminal terminal() {
            return reader.getTerminal();
        }

        private void clear(CommandInput input) {
            final String[] usage = {
                    "clear - clear terminal",
                    "Usage: clear",
                    "  -? --help                       Displays command help"
            };
            try {
                parseOptions(usage, input.args());
                terminal().puts(Capability.clear_screen);
                terminal().flush();
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void exit(CommandInput input) {
            final String[] usage = {
                "exit - exit terminal",
                "Usage: exit",
                "  -? --help                       Displays command help"
            };
            try {
                parseOptions(usage, input.args());
                System.exit(0);
            } catch (Exception e) {
                saveException(e);
            }
        }

        private void shell(CommandInput input) {
            final String[] usage = { "<command> - execute shell command"
                                   , "Usage: <command>"
                                   , "  -? --help                       Displays command help" };
            if (input.args().length == 1 && (input.args()[0].equals("-?") || input.args()[0].equals("--help"))) {
                try {
                    parseOptions(usage, input.args());
                } catch (Exception e) {
                    saveException(e);
                }
            } else {
                List<String> argv = new ArrayList<>(Arrays.asList(input.args()));
                if (!argv.isEmpty()) {
                    try {
                        var exec = new Executor(argv);
                        exec.execute();
                    } catch (Exception e) {
                        saveException(e);
                    }
                }
            }
        }

        private Set<String> capabilities() {
            return InfoCmp.getCapabilitiesByName().keySet();
        }
    }

    private static class ReplSystemRegistry extends SystemRegistryImpl {
        public ReplSystemRegistry(Parser parser, Terminal terminal, Supplier<Path> workDir, ConfigurationPath configPath) {
            super(parser, terminal, workDir, configPath);
        }

        @Override
        public boolean isCommandOrScript(String command) {
            return command.startsWith("!") || super.isCommandOrScript(command);
        }
    }

    public static Path workDir() {
        return Paths.get(System.getProperty("user.dir"));
    }

    public static String prompt() {
        try {
            var home = System.getProperty("user.home");
            var user = System.getProperty("user.name");
            var host = InetAddress.getLocalHost().getHostName();
            var cwd = workDir().toString().replace(home, "~");
            var dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
            var now = LocalDateTime.now();
            var fnow = dtf.format(now);
            var color_begin = ESCAPE + "[";
            var color_end = "m";
            var fg_blue_bg_black = color_begin + "34;40" + color_end;
            var fg_red_bg_black = color_begin + "31;40" + color_end;
            var fg_green_bg_black = color_begin + "32;40" + color_end;
            var fg_black_bg_blue = color_begin + "30;44" + color_end;
            var fg_blue_bg_default = color_begin + "34;49" + color_end;
            var fg_green_bg_default = color_begin + "32;49" + color_end;
            return fg_blue_bg_black + " " + user +
                   fg_red_bg_black + "@" +
                   fg_green_bg_black + host + " " +
                   fg_black_bg_blue + POWERLINE_RIGHT_BLOCK_ARROW + " " + cwd + " " +
                   fg_blue_bg_default + POWERLINE_RIGHT_BLOCK_ARROW + "\n" + " " +
                   fg_green_bg_default + fnow + " " +
                   fg_blue_bg_default + POWERLINE_RIGHT_THIN_ARROW + " ";
        } catch (Exception e) {
            return "jsandbox> ";
        }
    }

    public static void main(String[] args) {
        try {
            //
            // Parser & Terminal
            //
            DefaultParser parser = new DefaultParser();
            parser.setEofOnUnclosedBracket(Bracket.CURLY, Bracket.ROUND, Bracket.SQUARE);
            parser.setEofOnUnclosedQuote(true);
            parser.setEscapeChars(null);
            parser.setRegexCommand("[:]{0,1}[a-zA-Z!]{1,}\\S*");
            Terminal terminal = TerminalBuilder.builder().build();
            if (terminal.getWidth() == 0 || terminal.getHeight() == 0) {
                terminal.setSize(new Size(120, 40));
            }
            Thread executeThread = Thread.currentThread();
            terminal.handle(Signal.INT, signal -> executeThread.interrupt());
            File file = new File(System.getProperty("user.home") + "/.jsandbox");
            String root = file.getCanonicalPath().replace("classes", "").replaceAll("\\\\", "/");

            GroovyEngine scriptEngine = new GroovyEngine();
            scriptEngine.put("ROOT", root);
            ConfigurationPath configPath = new ConfigurationPath(Paths.get(root), Paths.get(root));
            Printer printer = new DefaultPrinter(scriptEngine, configPath);
            ConsoleEngineImpl consoleEngine = new ConsoleEngineImpl(scriptEngine
                                                                    , printer
                                                                    , App::workDir, configPath);
            Builtins builtins = new Builtins(App::workDir, configPath,  (String fun)-> new ConsoleEngine.WidgetCreator(consoleEngine, fun));
            MyCommands myCommands = new MyCommands(App::workDir);
            ReplSystemRegistry systemRegistry = new ReplSystemRegistry(parser, terminal, App::workDir, configPath);
            systemRegistry.register("groovy", new GroovyCommand(scriptEngine, printer));
            systemRegistry.addCompleter(scriptEngine.getScriptCompleter());
            systemRegistry.setScriptDescription(scriptEngine::scriptDescription);

            systemRegistry.setCommandRegistries(consoleEngine, builtins, myCommands);

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(systemRegistry.completer())
                    .parser(parser)
                    .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%M%P > ")
                    .variable(LineReader.INDENTATION, 2)
                    .variable(LineReader.LIST_MAX, 100)
                    .variable(LineReader.HISTORY_FILE, Paths.get(root, "history"))
                    .option(Option.INSERT_BRACKET, true)
                    .option(Option.EMPTY_WORD_OPTIONS, false)
                    .option(Option.USE_FORWARD_SLASH, true)
                    .option(Option.DISABLE_EVENT_EXPANSION, true)
                    .build();
            if (OSUtils.IS_WINDOWS) {
                reader.setVariable(LineReader.BLINK_MATCHING_PAREN, 0);
            }

            consoleEngine.setLineReader(reader);
            builtins.setLineReader(reader);
            myCommands.setLineReader(reader);

            new TailTipWidgets(reader, systemRegistry::commandDescription, 5, TipType.COMPLETER);
            KeyMap<Binding> keyMap = reader.getKeyMaps().get("main");
            keyMap.bind(new Reference(Widgets.TAILTIP_TOGGLE), KeyMap.alt("s"));

            while (true) {
                try {
                    systemRegistry.cleanUp();
                    String line = reader.readLine(prompt());
                    line = !myCommands.isCommand(parser.getCommand(line)) ? "! " + line : line;
                    Object result = systemRegistry.execute(line);
                    consoleEngine.println(result);
                }
                catch (UserInterruptException e) {
                    // Ignore
                }
                catch (EndOfFileException e) {
                    String pl = e.getPartialLine();
                    if (pl != null) {
                        try {
                            consoleEngine.println(systemRegistry.execute(pl));
                        } catch (Exception e2) {
                            systemRegistry.trace(e2);
                        }
                    }
                    break;
                }
                catch (Exception|Error e) {
                    systemRegistry.trace(e);
                }
            }
            systemRegistry.close();
        }
        catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
