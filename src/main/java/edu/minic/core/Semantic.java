package edu.minic.core;

import java.util.*;
import edu.minic.core.Source.*;
import edu.minic.core.Syntax.*;

/** 独立的静态语义遍历，继承环境向下传递，type/constant/binding 综合向上传递。 */
public final class Semantic {
    public record Symbol(int id, String name, String kind, Type type, int scope, int slot, Span span, List<Type> parameters) {
        public String operand() { return "v" + id; }
        @Override public String toString() { return "%s %-12s %-8s %-5s scope=%d slot=%d @%s%s".formatted(operand(), name, kind, type, scope, slot, span, parameters.isEmpty() ? "" : " " + parameters); }
    }
    public record Attr(Type type, Object constant, Symbol binding, int scope) {}
    public record FunctionInfo(Function syntax, Symbol symbol, List<Symbol> parameters, int slots) {}
    public record Result(List<Symbol> symbols, Map<String, FunctionInfo> functions, Map<Node, Attr> attributes) {}
    private static final class Scope {
        final int id; final Scope parent; final Map<String, Symbol> symbols = new LinkedHashMap<>();
        Scope(int id, Scope parent) { this.id = id; this.parent = parent; }
        Symbol find(String name) { Symbol s = symbols.get(name); return s != null ? s : parent == null ? null : parent.find(name); }
    }
    private record Flow(boolean reachable, Set<Integer> assigned) {}
    private final List<Diagnostic> diagnostics;
    private final List<Symbol> symbols = new ArrayList<>();
    private final Map<Node, Attr> attrs = new IdentityHashMap<>();
    private final Map<String, FunctionInfo> functions = new LinkedHashMap<>();
    private final Map<Function, Symbol> signatures = new IdentityHashMap<>();
    private final Scope global = new Scope(0, null);
    private Scope scope = global;
    private int nextId, nextScope = 1, slot, loops;
    private Type returnType;
    public Semantic(List<Diagnostic> diagnostics) { this.diagnostics = diagnostics; }
    private void error(Node n, String message) { if (diagnostics.size() < 100) diagnostics.add(new Diagnostic("语义", n.span(), message)); }
    private Symbol define(String name, String kind, Type type, int index, Span span, List<Type> params) {
        Symbol symbol = new Symbol(nextId++, name, kind, type, scope.id, index, span, List.copyOf(params));
        symbols.add(symbol); scope.symbols.putIfAbsent(name, symbol); return symbol;
    }
    private void attr(Node n, Type type, Object constant, Symbol binding) { attrs.put(n, new Attr(type, constant, binding, scope.id)); }
    public Result analyze(Program program) {
        define("read", "builtin", Type.INT, -1, program.span(), List.of());
        define("write", "builtin", Type.VOID, -1, program.span(), List.of(Type.ERROR));
        for (Function f : program.functions()) {
            if (global.symbols.containsKey(f.name())) error(f, "函数重复定义或占用内置名：" + f.name());
            Symbol s = define(f.name(), "function", f.type(), -1, f.span(), f.params().stream().map(Param::type).toList());
            signatures.put(f, s); attr(f, f.type(), null, s);
        }
        Symbol main = global.find("main");
        if (main == null || main.type() != Type.INT || !main.parameters().isEmpty()) error(program, "必须定义入口 int main()");
        for (Function f : program.functions()) {
            scope = new Scope(nextScope++, global); slot = 0; loops = 0; returnType = f.type();
            Set<Integer> assigned = new HashSet<>(); List<Symbol> params = new ArrayList<>();
            for (Param p : f.params()) {
                if (p.type() == Type.VOID) error(p, "参数不能是 void");
                if (scope.symbols.containsKey(p.name())) error(p, "参数重复声明：" + p.name());
                Symbol s = define(p.name(), "parameter", p.type(), slot++, p.span(), List.of());
                params.add(s); assigned.add(s.id()); attr(p, p.type(), null, s);
            }
            Flow end = block(f.body(), new Flow(true, assigned), false);
            if (f.type() != Type.VOID && end.reachable()) error(f, "非 void 函数存在未返回值的路径：" + f.name());
            functions.putIfAbsent(f.name(), new FunctionInfo(f, signatures.get(f), List.copyOf(params), slot));
        }
        scope = global;
        return new Result(List.copyOf(symbols), Collections.unmodifiableMap(new LinkedHashMap<>(functions)), Collections.unmodifiableMap(attrs));
    }
    private Flow block(Block block, Flow flow, boolean nested) {
        Scope outer = scope;
        if (nested) scope = new Scope(nextScope++, scope);
        attr(block, Type.VOID, null, null);
        Flow current = copy(flow);
        for (Stmt s : block.statements()) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            Flow next = statement(s, current);
            // 仍检查不可达代码，但不可达赋值不能证明后续路径已赋值。
            if (current.reachable()) current = next;
        }
        if (nested) {
            Set<Integer> assigned = new HashSet<>(current.assigned());
            scope.symbols.values().forEach(s -> assigned.remove(s.id()));
            current = new Flow(current.reachable(), assigned);
        }
        scope = outer; return current;
    }
    private static Flow copy(Flow f) { return new Flow(f.reachable(), new HashSet<>(f.assigned())); }
    private Flow statement(Stmt stmt, Flow flow) {
        attr(stmt, Type.VOID, null, null);
        Set<Integer> assigned = new HashSet<>(flow.assigned());
        switch (stmt) {
            case Block b -> { return block(b, flow, true); }
            case VarDecl d -> {
                if (d.type() == Type.VOID) error(d, "变量不能是 void");
                if (scope.symbols.containsKey(d.name())) error(d, "当前作用域重复声明：" + d.name());
                Symbol s = define(d.name(), "variable", d.type(), slot++, d.span(), List.of());
                attr(d, d.type(), null, s);
                if (d.init() != null) { require(d.init(), expr(d.init(), assigned), d.type(), "初始化"); assigned.add(s.id()); }
            }
            case Assign a -> {
                Symbol s = variable(a.name(), a); Type t = expr(a.value(), assigned);
                if (s != null) { require(a, t, s.type(), "赋值"); assigned.add(s.id()); attr(a, s.type(), null, s); }
            }
            case If i -> {
                require(i.condition(), expr(i.condition(), assigned), Type.BOOL, "if 条件");
                Flow yes = block(i.yes(), copy(flow), true);
                Flow no = i.no() == null ? copy(flow) : block(i.no(), copy(flow), true);
                if (!yes.reachable()) return no;
                if (!no.reachable()) return yes;
                Set<Integer> both = new HashSet<>(yes.assigned()); both.retainAll(no.assigned()); return new Flow(true, both);
            }
            case While w -> {
                require(w.condition(), expr(w.condition(), assigned), Type.BOOL, "while 条件");
                loops++; block(w.body(), copy(flow), true); loops--;
            }
            case Break b -> { if (loops == 0) error(b, "break 必须位于循环内部"); return new Flow(false, assigned); }
            case Continue c -> { if (loops == 0) error(c, "continue 必须位于循环内部"); return new Flow(false, assigned); }
            case Return r -> {
                Type value = r.value() == null ? Type.VOID : expr(r.value(), assigned);
                require(r, value, returnType, "返回值"); return new Flow(false, assigned);
            }
            case ExprStmt e -> expr(e.expr(), assigned);
            default -> throw new IllegalStateException("未处理语句 " + stmt);
        }
        return new Flow(flow.reachable(), assigned);
    }
    private Symbol variable(String name, Node node) {
        Symbol s = scope.find(name);
        if (s == null) { error(node, "标识符未声明：" + name); return null; }
        if (!s.kind().equals("variable") && !s.kind().equals("parameter")) { error(node, "此处需要变量：" + name); return null; }
        return s;
    }
    private void require(Node n, Type actual, Type expected, String what) {
        if (actual != Type.ERROR && expected != Type.ERROR && actual != expected) error(n, what + "类型不匹配：需要 " + expected + "，得到 " + actual);
    }
    private Type expr(Expr e, Set<Integer> assigned) {
        Type type; Object constant = null; Symbol binding = null;
        switch (e) {
            case IntLit i -> {
                type = Type.INT;
                if (i.value() > Integer.MAX_VALUE) error(i, "正整数超出 int 范围；2147483648 仅可用于 -2147483648");
                else constant = (int)i.value();
            }
            case BoolLit b -> { type = Type.BOOL; constant = b.value(); }
            case Name n -> {
                binding = variable(n.name(), n); type = binding == null ? Type.ERROR : binding.type();
                if (binding != null && !assigned.contains(binding.id())) error(n, "变量使用前未确定赋值：" + n.name());
            }
            case Unary u -> {
                if (u.op().equals("-") && u.expr() instanceof IntLit i && i.value() == 2147483648L) {
                    attr(i, Type.INT, null, null); type = Type.INT; constant = Integer.MIN_VALUE;
                } else {
                    Type child = expr(u.expr(), assigned); type = u.op().equals("!") ? Type.BOOL : Type.INT;
                    require(u, child, type, "一元运算"); Object v = attrs.get(u.expr()).constant();
                    if (v != null && child == type) constant = u.op().equals("!") ? !(Boolean)v : u.op().equals("-") ? -(Integer)v : v;
                }
            }
            case Binary b -> {
                Type left = expr(b.left(), assigned), right = expr(b.right(), assigned);
                boolean logic = Set.of("&&", "||").contains(b.op());
                boolean equality = Set.of("==", "!=").contains(b.op());
                boolean compare = Set.of("<", "<=", ">", ">=").contains(b.op());
                if (equality) {
                    if (left == Type.VOID || right == Type.VOID) error(b, "void 不能参与比较");
                    require(b, right, left, "相等比较");
                } else { require(b.left(), left, logic ? Type.BOOL : Type.INT, "运算"); require(b.right(), right, logic ? Type.BOOL : Type.INT, "运算"); }
                type = logic || equality || compare ? Type.BOOL : Type.INT;
                Object l = attrs.get(b.left()).constant(), r = attrs.get(b.right()).constant();
                if (l != null && r != null && left == right && left != Type.VOID) {
                    try { constant = calculate(b.op(), l, r); } catch (ArithmeticException | ClassCastException ignored) { /* 保留会在运行时触发的运算，不把它折叠掉。 */ }
                }
            }
            case Call c -> {
                binding = scope.find(c.name());
                List<Type> actual = c.args().stream().map(a -> expr(a, assigned)).toList();
                if (binding == null || !(binding.kind().equals("function") || binding.kind().equals("builtin"))) {
                    error(c, "未定义或被局部变量遮蔽的函数：" + c.name()); type = Type.ERROR;
                } else {
                    type = binding.type();
                    if (actual.size() != binding.parameters().size()) error(c, "函数 " + c.name() + " 参数数量不匹配：需要 " + binding.parameters().size());
                    else for (int i = 0; i < actual.size(); i++) {
                        if (c.name().equals("write")) {
                            if (actual.get(i) == Type.VOID) error(c.args().get(i), "write 只接受 int 或 bool");
                        } else require(c.args().get(i), actual.get(i), binding.parameters().get(i), "第 " + (i+1) + " 个参数");
                    }
                }
            }
            default -> throw new IllegalStateException("未处理表达式 " + e);
        }
        attr(e, type, constant, binding); return type;
    }
    public static Object calculate(String op, Object a, Object b) {
        if (op.equals("==")) return a.equals(b);
        if (op.equals("!=")) return !a.equals(b);
        if (op.equals("&&")) return (Boolean)a && (Boolean)b;
        if (op.equals("||")) return (Boolean)a || (Boolean)b;
        int x = (Integer)a, y = (Integer)b;
        return switch (op) {
            case "+" -> x + y; case "-" -> x - y; case "*" -> x * y; case "/" -> x / y; case "%" -> x % y;
            case "<" -> x < y; case "<=" -> x <= y; case ">" -> x > y; case ">=" -> x >= y;
            default -> throw new IllegalArgumentException("未知运算 " + op);
        };
    }
}
