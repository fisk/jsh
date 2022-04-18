package com.oracle;

import java.io.*;
import java.util.*;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

public class Executor {
    private List<String> args;

    public Executor(List<String> args) {
        this.args = args;
    }

    private static int execute(List<String> args) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            Process process = processBuilder.inheritIO().command(args).start();
            int exitCode = process.waitFor();
            return exitCode;
        } catch (Exception e) {
            return -1;
        }
    }

    private static class ASTListener extends ShellBaseListener {
        @Override public void enterCommand(ShellParser.CommandContext ctx) {
            String command = ctx.program.getText();
            ArrayList<String> args = new ArrayList<>();
            args.add(command);
            for (int i = 1; i < ctx.IDENTIFIER().size(); ++i) {
                var arg = ctx.IDENTIFIER(i).getText();
                args.add(arg);
            }

            execute(args);
        }

        @Override public void exitCommand(ShellParser.CommandContext ctx) {
        }
    }

    public void execute() throws Exception {
        ShellLexer l = new ShellLexer(CharStreams.fromString(String.join(" ", args)));
        ShellParser p = new ShellParser(new CommonTokenStream(l));
        p.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                    throw new IllegalStateException("Syntax error at line " + line + " due to " + msg, e);
                }
            });
        ParseTree tree = p.command();
        ParseTreeWalker walker = new ParseTreeWalker();
        var astBuilder = new ASTListener();
        walker.walk(astBuilder, tree);
    }
}
