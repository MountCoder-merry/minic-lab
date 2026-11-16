package edu.minic.core;

import java.util.*;
import edu.minic.core.Source.*;

public final class Bytecode {
    public record Instruction(String op, Object argument, Span span) {
        @Override public String toString() { return "%-7s %-18s @%s".formatted(op, argument == null ? "" : argument, span); }
    }
    public record Function(String name, Type returnType, int parameters, int slots, List<Instruction> code, Map<Integer, String> slotNames) {}
    public record Program(Map<String, Function> functions) {}
    public static Program generate(Ir.Program ir, Semantic.Result semantic) {
        Map<String, Function> functions = new LinkedHashMap<>();
        for (Ir.FunctionCode f : ir.functions()) {
            var info = semantic.functions().get(f.name());
            List<Instruction> code = new ArrayList<>(); Map<String, Integer> slots = new LinkedHashMap<>(); Map<Integer, String> names = new LinkedHashMap<>();
            // 全局唯一符号号保证跨作用域名字不会共用槽。
            semantic.symbols().stream().filter(s -> s.slot() >= 0).forEach(s -> slots.put(s.operand(), s.slot()));
            final int[] next = {info.slots()};
            Map<String, Integer> labels = new HashMap<>();
            for (Ir.Quad q : f.code()) {
                java.util.function.Consumer<String> load = operand -> {
                    Object constant = Ir.constant(operand);
                    if (constant != null) code.add(new Instruction("PUSH", constant, q.span()));
                    else { int index = slots.computeIfAbsent(operand, k -> next[0]++); names.putIfAbsent(index, operand); code.add(new Instruction("LOAD", index, q.span())); }
                };
                java.util.function.Consumer<String> store = operand -> {
                    int index = slots.computeIfAbsent(operand, k -> next[0]++); names.putIfAbsent(index, operand); code.add(new Instruction("STORE", index, q.span()));
                };
                switch (q.op()) {
                    case "label" -> labels.put(q.result(), code.size());
                    case "mov" -> { load.accept(q.arg1()); store.accept(q.result()); }
                    case "jmp" -> code.add(new Instruction("JMP", q.result(), q.span()));
                    case "jz" -> { load.accept(q.arg1()); code.add(new Instruction("JZ", q.result(), q.span())); }
                    case "param" -> load.accept(q.arg1());
                    case "call" -> { code.add(new Instruction("CALL", q.arg1(), q.span())); if (!q.result().isEmpty()) store.accept(q.result()); }
                    case "read" -> { code.add(new Instruction("READ", null, q.span())); store.accept(q.result()); }
                    case "write" -> { load.accept(q.arg1()); code.add(new Instruction("WRITE", null, q.span())); }
                    case "ret" -> { if (!q.arg1().isEmpty()) load.accept(q.arg1()); code.add(new Instruction("RET", !q.arg1().isEmpty(), q.span())); }
                    case "neg", "not" -> { load.accept(q.arg1()); code.add(new Instruction(q.op().toUpperCase(Locale.ROOT), null, q.span())); store.accept(q.result()); }
                    default -> { load.accept(q.arg1()); load.accept(q.arg2()); code.add(new Instruction(q.op(), null, q.span())); store.accept(q.result()); }
                }
            }
            for (int i = 0; i < code.size(); i++) {
                Instruction ins = code.get(i);
                if (ins.op().equals("JMP") || ins.op().equals("JZ")) {
                    Integer target = labels.get((String)ins.argument());
                    if (target == null) throw new IllegalStateException("标签未定义 " + ins.argument());
                    code.set(i, new Instruction(ins.op(), target, ins.span()));
                }
            }
            for (var s : semantic.symbols()) if (s.slot() >= 0 && slots.containsKey(s.operand())) {
                // 只展示这个函数实际引用的符号或参数。
                if (names.containsKey(s.slot()) && names.get(s.slot()).equals(s.operand()) || info.parameters().contains(s)) names.put(s.slot(), s.name() + " (" + s.operand() + ")");
            }
            functions.put(f.name(), new Function(f.name(), info.syntax().type(), info.parameters().size(), next[0], List.copyOf(code), Collections.unmodifiableMap(names)));
        }
        return new Program(Collections.unmodifiableMap(functions));
    }
    public static String format(Program p) {
        StringBuilder b = new StringBuilder("MiniC Stack Bytecode v1\n");
        for (Function f : p.functions().values()) { b.append("\n.function ").append(f.name()).append(" params=").append(f.parameters()).append(" slots=").append(f.slots()).append('\n'); for (int i = 0; i < f.code().size(); i++) b.append("%4d  %s%n".formatted(i, f.code().get(i))); }
        return b.toString();
    }
}
