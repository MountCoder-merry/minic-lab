package edu.minic.core;

import java.util.*;
import edu.minic.core.Source.*;

public final class CompilerService {
    public record Options(boolean optimize) { public static Options defaults() { return new Options(true); } }
    public record CompilationResult(String source, List<Token> tokens, Syntax.Program ast, List<String> parseTrace,
                                    Semantic.Result semantic, Ir.Program ir, Ir.Program optimizedIr,
                                    Bytecode.Program bytecode, List<Diagnostic> diagnostics) {
        public boolean success() { return bytecode != null && diagnostics.isEmpty(); }
    }
    public CompilationResult compile(String source, Options options) {
        Objects.requireNonNull(source); Objects.requireNonNull(options);
        List<Diagnostic> errors = new ArrayList<>();
        if (source.length() > 1_000_000) return new CompilationResult(source, List.of(), null, List.of(), null, null, null, null, List.of(new Diagnostic("输入", new Span(0,0,1,1), "源码超过 100 万字符限制")));
        List<Token> tokens = new Lexer(source, errors).scan(); Syntax.Program ast = null; List<String> trace = List.of();
        Semantic.Result semantic = null; Ir.Program ir = null, optimized = null; Bytecode.Program bytecode = null;
        if (errors.isEmpty()) { Parser parser = new Parser(tokens, errors); ast = parser.parse(); trace = parser.trace(); }
        if (errors.isEmpty()) {
            record Pending(Syntax.Node node, int depth) {}
            Deque<Pending> pending = new ArrayDeque<>(); pending.push(new Pending(ast, 0));
            while (!pending.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                Pending p = pending.pop();
                if (p.depth > 200) { errors.add(new Diagnostic("语法", p.node.span(), "语法树超过 200 层，请拆分长表达式或嵌套语句")); break; }
                for (Syntax.Node child : Syntax.children(p.node)) pending.push(new Pending(child, p.depth + 1));
            }
        }
        if (errors.isEmpty()) semantic = new Semantic(errors).analyze(ast);
        if (errors.isEmpty()) { ir = new Ir(semantic).generate(); optimized = Ir.optimize(ir); bytecode = Bytecode.generate(options.optimize() ? optimized : ir, semantic); }
        return new CompilationResult(source, tokens, ast, trace, semantic, ir, optimized, bytecode, List.copyOf(errors));
    }
}
