package edu.minic.core;

import java.util.*;
import edu.minic.core.Source.*;
import edu.minic.core.Syntax.*;

public final class Ir {
    public record Quad(String op, String arg1, String arg2, String result, Span span) {
        @Override public String toString() { return "(%-6s %-14s %-8s %-10s) @%s".formatted(op, arg1, arg2, result, span); }
    }
    public record FunctionCode(String name, List<Quad> code) {}
    public record Range(String function, int first, int last) {}
    public record Program(List<FunctionCode> functions, Map<Node, Range> ranges) {}
    private final Semantic.Result semantic;
    private final Map<Node, Range> ranges = new IdentityHashMap<>();
    private List<Quad> code;
    private String function;
    private int temp, label;
    private final Deque<String> breaks = new ArrayDeque<>(), continues = new ArrayDeque<>();
    public Ir(Semantic.Result semantic) { this.semantic = semantic; }
    public Program generate() {
        List<FunctionCode> functions = new ArrayList<>();
        for (var f : semantic.functions().values()) {
            function = f.syntax().name(); code = new ArrayList<>(); temp = 0; label = 0;
            statement(f.syntax().body());
            if (f.syntax().type() == Type.VOID) emit("ret", "", "", "", f.syntax().span());
            functions.add(new FunctionCode(function, List.copyOf(code)));
        }
        return new Program(List.copyOf(functions), Collections.unmodifiableMap(ranges));
    }
    private String temp() { return "t" + temp++; }
    private String label() { return "L" + label++; }
    private void emit(String op, String a, String b, String r, Span span) { code.add(new Quad(op, a, b, r, span)); }
    private void mark(Node n, int first) { ranges.put(n, new Range(function, first, code.size() - 1)); }
    public static String literal(Object value) { return value instanceof Boolean ? "#b:" + value : "#i:" + value; }
    public static Object constant(String operand) {
        if (operand.startsWith("#i:")) return Integer.parseInt(operand.substring(3));
        if (operand.startsWith("#b:")) return Boolean.parseBoolean(operand.substring(3));
        return null;
    }
    private String symbol(Node n) { return semantic.attributes().get(n).binding().operand(); }
    private void statement(Stmt s) {
        int first = code.size();
        switch (s) {
            case Block b -> b.statements().forEach(this::statement);
            case VarDecl d -> { if (d.init() != null) emit("mov", expr(d.init()), "", symbol(d), d.span()); }
            case Assign a -> emit("mov", expr(a.value()), "", symbol(a), a.span());
            case If i -> {
                String no = label(), end = label();
                emit("jz", expr(i.condition()), "", no, i.condition().span()); statement(i.yes());
                emit("jmp", "", "", end, i.span()); emit("label", "", "", no, i.span());
                if (i.no() != null) statement(i.no()); emit("label", "", "", end, i.span());
            }
            case While w -> {
                String start = label(), end = label();
                emit("label", "", "", start, w.span()); emit("jz", expr(w.condition()), "", end, w.condition().span());
                breaks.push(end); continues.push(start); statement(w.body()); breaks.pop(); continues.pop();
                emit("jmp", "", "", start, w.span()); emit("label", "", "", end, w.span());
            }
            case Break b -> emit("jmp", "", "", breaks.peek(), b.span());
            case Continue c -> emit("jmp", "", "", continues.peek(), c.span());
            case Return r -> emit("ret", r.value() == null ? "" : expr(r.value()), "", "", r.span());
            case ExprStmt e -> expr(e.expr());
            default -> throw new IllegalStateException("未知语句");
        }
        mark(s, first);
    }
    private String expr(Expr e) {
        int first = code.size(); String result;
        switch (e) {
            case IntLit i -> result = literal((int)i.value());
            case BoolLit b -> result = literal(b.value());
            case Name n -> result = symbol(n);
            case Unary u -> {
                if (u.op().equals("-") && u.expr() instanceof IntLit i && i.value() == 2147483648L) result = literal(Integer.MIN_VALUE);
                else { String value = expr(u.expr()); result = temp(); emit(u.op().equals("!") ? "not" : u.op().equals("-") ? "neg" : "mov", value, "", result, u.span()); }
            }
            case Binary b -> {
                String left = expr(b.left()); result = temp();
                if (b.op().equals("&&") || b.op().equals("||")) {
                    boolean and = b.op().equals("&&"); String end = label();
                    emit("mov", literal(!and), "", result, b.span());
                    if (and) emit("jz", left, "", end, b.span());
                    else { String rhs = label(); emit("jz", left, "", rhs, b.span()); emit("jmp", "", "", end, b.span()); emit("label", "", "", rhs, b.span()); }
                    emit("mov", expr(b.right()), "", result, b.span()); emit("label", "", "", end, b.span());
                } else emit(b.op(), left, expr(b.right()), result, b.span());
            }
            case Call c -> {
                // 先求全部实参，再发 param，避免嵌套调用交错污染参数栈。
                List<String> args = c.args().stream().map(this::expr).toList();
                if (c.name().equals("read")) { result = temp(); emit("read", "", "", result, c.span()); }
                else if (c.name().equals("write")) { emit("write", args.getFirst(), "", "", c.span()); result = ""; }
                else {
                    args.forEach(a -> emit("param", a, "", "", c.span()));
                    result = semantic.attributes().get(c).type() == Type.VOID ? "" : temp();
                    emit("call", c.name(), String.valueOf(args.size()), result, c.span());
                }
            }
            default -> throw new IllegalStateException("未知表达式");
        }
        mark(e, first); return result;
    }
    public static Program optimize(Program original) {
        List<FunctionCode> functions = new ArrayList<>();
        for (FunctionCode f : original.functions()) {
            Map<String, String> copies = new HashMap<>(); List<Quad> out = new ArrayList<>();
            for (Quad q : f.code()) {
                if (q.op().equals("label")) copies.clear();
                String a = substitute(q.arg1(), copies), b = substitute(q.arg2(), copies);
                Quad rewritten = new Quad(q.op(), a, b, q.result(), q.span());
                Object av = constant(a), bv = constant(b), folded = null;
                try {
                    if (av != null && bv != null && !Set.of("call", "param").contains(q.op())) folded = Semantic.calculate(q.op(), av, bv);
                    else if (av != null && q.op().equals("neg")) folded = -(Integer)av;
                    else if (av != null && q.op().equals("not")) folded = !(Boolean)av;
                } catch (ArithmeticException e) { /* 除零/余零必须保留到 VM，保留源码位置与发生顺序。 */ }
                if (folded != null) rewritten = new Quad("mov", literal(folded), "", q.result(), q.span());
                out.add(rewritten);
                if (!q.result().isEmpty()) {
                    String destination = q.result(); copies.remove(destination);
                    copies.entrySet().removeIf(e -> e.getValue().equals(destination));
                    // 只传播临时量；用户变量可能在后续赋值中变化。
                    if (destination.startsWith("t") && rewritten.op().equals("mov") && !rewritten.arg1().startsWith("v")) copies.put(destination, rewritten.arg1());
                }
                if (Set.of("jmp", "jz", "ret", "call", "read", "write").contains(q.op())) copies.clear();
            }
            functions.add(new FunctionCode(f.name(), List.copyOf(out)));
        }
        return new Program(List.copyOf(functions), original.ranges());
    }
    private static String substitute(String value, Map<String, String> copies) {
        Set<String> seen = new HashSet<>();
        while (copies.containsKey(value) && seen.add(value)) value = copies.get(value);
        return value;
    }
    public static String format(Program p) {
        StringBuilder b = new StringBuilder();
        for (var f : p.functions()) { b.append("\n函数 ").append(f.name()).append('\n'); for (int i = 0; i < f.code().size(); i++) b.append("%4d  %s%n".formatted(i, f.code().get(i))); }
        return b.toString();
    }
}
