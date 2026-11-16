package edu.minic.core;

import java.util.*;
import edu.minic.core.Source.*;

/** 只执行目标指令，不直接求值 AST。每个调用帧持有独立局部槽与操作数栈。 */
public final class VirtualMachine {
    public enum State { READY, RUNNING, PAUSED, HALTED, STOPPED, ERROR }
    public record Limits(long instructions, int callDepth) {
        public Limits { if (instructions < 1 || callDepth < 1) throw new IllegalArgumentException("执行上限必须为正数"); }
        public static Limits defaults() { return new Limits(1_000_000, 1024); }
    }
    public record FrameView(String function, int pc, List<Object> stack, Map<String, Object> locals) {}
    public record Snapshot(State state, long steps, List<FrameView> frames, String output, Object exitValue, Diagnostic error, Span position) {}
    private static final class Frame {
        final Bytecode.Function function; final Object[] locals; final List<Object> stack = new ArrayList<>(); int pc;
        Frame(Bytecode.Function function, List<Object> args) { this.function = function; locals = new Object[function.slots()]; for (int i = 0; i < args.size(); i++) locals[i] = args.get(i); }
        Object pop() { if (stack.isEmpty()) throw new IllegalStateException("操作数栈为空"); return stack.removeLast(); }
    }
    private final Bytecode.Program program;
    private final Limits limits;
    private final Deque<Frame> frames = new ArrayDeque<>();
    private final Deque<Integer> input;
    private final StringBuilder output = new StringBuilder();
    private volatile State state = State.READY;
    private volatile boolean stopRequested, pauseRequested;
    private long steps; private Object exitValue; private Diagnostic error; private Span position;
    public VirtualMachine(Bytecode.Program program, List<Integer> input, Limits limits) {
        this.program = program; this.input = new ArrayDeque<>(input); this.limits = limits;
        Bytecode.Function main = program.functions().get("main");
        if (main == null) throw new IllegalArgumentException("目标代码缺少 main");
        frames.push(new Frame(main, List.of())); position = main.code().isEmpty() ? new Span(0, 0, 1, 1) : main.code().getFirst().span();
    }
    public void pause() { pauseRequested = true; }
    public void stop() { stopRequested = true; if (state == State.READY || state == State.PAUSED) state = State.STOPPED; }
    public State state() { return state; }
    public Snapshot run() {
        synchronized (this) {
            if (terminal()) return snapshot();
            pauseRequested = false; state = State.RUNNING;
        }
        while (state == State.RUNNING) {
            synchronized (this) {
                if (stopRequested || Thread.currentThread().isInterrupted()) { state = State.STOPPED; break; }
                if (pauseRequested) { state = State.PAUSED; break; }
                executeOne();
            }
        }
        return snapshot();
    }
    public synchronized Snapshot step() {
        if (state == State.RUNNING || terminal()) return snapshot();
        state = State.PAUSED; executeOne(); return snapshot();
    }
    private boolean terminal() { return state == State.HALTED || state == State.ERROR || state == State.STOPPED; }
    private void executeOne() {
        if (stopRequested) { state = State.STOPPED; return; }
        Frame f = frames.peek();
        if (f == null) { state = State.HALTED; return; }
        if (f.pc >= f.function.code().size()) { fault("目标函数执行越界（缺少返回指令）"); return; }
        Bytecode.Instruction ins = f.function.code().get(f.pc); position = ins.span();
        if (steps >= limits.instructions()) { fault("累计执行指令超过上限 " + limits.instructions()); return; }
        f.pc++; steps++;
        try {
            switch (ins.op()) {
                case "PUSH" -> f.stack.add(ins.argument());
                case "LOAD" -> { Object v = f.locals[(Integer)ins.argument()]; if (v == null) throw new IllegalStateException("读取未初始化槽"); f.stack.add(v); }
                case "STORE" -> f.locals[(Integer)ins.argument()] = f.pop();
                case "POP" -> f.pop();
                case "NEG" -> f.stack.add(-(Integer)f.pop());
                case "NOT" -> f.stack.add(!(Boolean)f.pop());
                case "JMP" -> f.pc = (Integer)ins.argument();
                case "JZ" -> { if (!(Boolean)f.pop()) f.pc = (Integer)ins.argument(); }
                case "READ" -> { if (input.isEmpty()) throw new IllegalStateException("输入不足，请补充输入后重新运行"); f.stack.add(input.removeFirst()); }
                case "WRITE" -> output.append(f.pop()).append('\n');
                case "CALL" -> {
                    Bytecode.Function callee = program.functions().get((String)ins.argument());
                    if (callee == null) throw new IllegalStateException("调用目标不存在");
                    if (frames.size() >= limits.callDepth()) throw new IllegalStateException("调用深度超过上限 " + limits.callDepth());
                    List<Object> args = new ArrayList<>(); for (int i = 0; i < callee.parameters(); i++) args.add(f.pop()); Collections.reverse(args);
                    frames.push(new Frame(callee, args));
                }
                case "RET" -> {
                    Object value = (Boolean)ins.argument() ? f.pop() : null; frames.pop();
                    if (frames.isEmpty()) { exitValue = value; state = State.HALTED; }
                    else if (value != null) frames.peek().stack.add(value);
                }
                default -> { Object right = f.pop(), left = f.pop(); f.stack.add(Semantic.calculate(ins.op(), left, right)); }
            }
        } catch (ArithmeticException e) { fault("整数除零或余零"); }
        catch (IllegalStateException | IllegalArgumentException | ClassCastException | IndexOutOfBoundsException e) { fault(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
    }
    private void fault(String message) { error = new Diagnostic("运行", position, message); state = State.ERROR; }
    public synchronized Snapshot snapshot() {
        List<FrameView> views = new ArrayList<>();
        for (Frame frame : frames) {
            Map<String, Object> locals = new LinkedHashMap<>();
            frame.function.slotNames().forEach((i, name) -> locals.put(name, frame.locals[i] == null ? "〈未赋值〉" : frame.locals[i]));
            views.add(new FrameView(frame.function.name(), frame.pc, List.copyOf(frame.stack), Collections.unmodifiableMap(locals)));
        }
        Span next = position;
        if (!terminal() && !frames.isEmpty()) { Frame f = frames.peek(); if (f.pc < f.function.code().size()) next = f.function.code().get(f.pc).span(); }
        return new Snapshot(state, steps, List.copyOf(views), output.toString(), exitValue, error, next);
    }
}
