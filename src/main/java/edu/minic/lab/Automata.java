package edu.minic.lab;

import java.util.*;
import java.util.concurrent.CancellationException;

/** Thompson → 子集构造（含空集陷阱状态）→ 等价类分割。 */
public final class Automata {
    public record Edge(int from, int to, String label) { public String display() { return label == null ? "ε" : label; } }
    public record Machine(int count, int start, Set<Integer> finals, List<Edge> edges, Map<Integer,String> names) {
        public List<String> alphabet() { return edges.stream().map(Edge::label).filter(Objects::nonNull).distinct().sorted().toList(); }
    }
    public record Result(Machine nfa, Machine dfa, Machine minimized, List<String> construction) {}
    public record Recognition(boolean accepted, List<String> steps) {}
    private record Fragment(int start, int end) {}
    private record Lexeme(String value, boolean literal) {}
    private final List<Lexeme> input = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<String> trace = new ArrayList<>();
    private int pos, states, depth;
    private Automata(String regex) {
        if (regex.length() > 4000) throw new IllegalArgumentException("正则式超过 4000 字符限制");
        int[] cp = regex.codePoints().toArray();
        for (int i = 0; i < cp.length; i++) {
            String value = new String(Character.toChars(cp[i]));
            if (value.equals("\\")) {
                if (++i == cp.length) throw new IllegalArgumentException("正则式末尾缺少转义字符");
                input.add(new Lexeme(new String(Character.toChars(cp[i])), true));
            } else if (!Character.isWhitespace(cp[i])) input.add(new Lexeme(value, false));
        }
    }
    public static void checkCancelled() { if (Thread.currentThread().isInterrupted()) throw new CancellationException("操作已取消"); }
    private int state() { checkCancelled(); if (states >= 2000) throw new IllegalArgumentException("NFA 超过 2000 状态限制"); return states++; }
    private boolean at(String value) { return pos < input.size() && !input.get(pos).literal && input.get(pos).value.equals(value); }
    private boolean match(String v) { if (at(v)) { pos++; return true; } return false; }
    private Fragment atom() {
        checkCancelled();
        if (++depth > 150) throw new IllegalArgumentException("正则嵌套超过 150 层");
        Fragment f;
        if (match("(")) {
            f = expression(); if (!match(")")) throw new IllegalArgumentException("缺少右括号 )，符号位置 " + (pos + 1));
        } else {
            if (pos >= input.size() || at("|") || at(")") || at("*") || at("·")) throw new IllegalArgumentException("需要正则项，符号位置 " + (pos + 1));
            Lexeme t = input.get(pos++); int start = state(), end = state();
            if (t.literal || !t.value.equals("∅")) edges.add(new Edge(start,end,!t.literal && t.value.equals("ε") ? null : t.value));
            f = new Fragment(start,end); trace.add("Thompson: " + t.value + " → " + start + "…" + end);
        }
        while (match("*")) {
            int start = state(), end = state();
            edges.addAll(List.of(new Edge(start,f.start,null),new Edge(start,end,null),new Edge(f.end,f.start,null),new Edge(f.end,end,null)));
            f = new Fragment(start,end); trace.add("闭包 * → " + start + "…" + end);
        }
        depth--; return f;
    }
    private Fragment concatenation() {
        Fragment f = atom();
        while (pos < input.size() && !at("|") && !at(")")) {
            match("·"); Fragment right = atom(); edges.add(new Edge(f.end,right.start,null)); f = new Fragment(f.start,right.end);
        }
        return f;
    }
    private Fragment expression() {
        Fragment f = concatenation();
        while (match("|")) {
            Fragment right = concatenation(); int start = state(), end = state();
            edges.addAll(List.of(new Edge(start,f.start,null),new Edge(start,right.start,null),new Edge(f.end,end,null),new Edge(right.end,end,null)));
            f = new Fragment(start,end); trace.add("并 | → " + start + "…" + end);
        }
        return f;
    }
    public static Result build(String regex) {
        Automata parser = new Automata(regex); Fragment f;
        if (parser.input.isEmpty()) { int start = parser.state(), end = parser.state(); parser.edges.add(new Edge(start,end,null)); f = new Fragment(start,end); }
        else f = parser.expression();
        if (parser.pos != parser.input.size()) throw new IllegalArgumentException("正则式存在多余右括号，符号位置 " + (parser.pos + 1));
        Map<Integer,String> names = new LinkedHashMap<>(); for (int i = 0; i < parser.states; i++) names.put(i,String.valueOf(i));
        Machine nfa = new Machine(parser.states, f.start, Set.of(f.end), List.copyOf(parser.edges), Collections.unmodifiableMap(names));
        Machine dfa = determinize(nfa,parser.trace); Machine min = minimize(dfa,parser.trace);
        return new Result(nfa,dfa,min,List.copyOf(parser.trace));
    }
    public static Set<Integer> closure(Machine m, Collection<Integer> initial) {
        Set<Integer> result = new TreeSet<>(initial); Deque<Integer> pending = new ArrayDeque<>(initial);
        Map<Integer,List<Edge>> adj = adjacency(m);
        while (!pending.isEmpty()) {
            checkCancelled(); int s = pending.removeFirst();
            for (Edge e : adj.getOrDefault(s,List.of())) if (e.label == null && result.add(e.to)) pending.add(e.to);
        }
        return Collections.unmodifiableSet(result);
    }
    private static Map<Integer,List<Edge>> adjacency(Machine m) {
        Map<Integer,List<Edge>> adj = new HashMap<>(); m.edges.forEach(e -> adj.computeIfAbsent(e.from,k -> new ArrayList<>()).add(e)); return adj;
    }
    private static Set<Integer> move(Machine m, Set<Integer> states, String label) {
        Set<Integer> result = new TreeSet<>(); for (Edge e : m.edges) if (states.contains(e.from) && label.equals(e.label)) result.add(e.to); return result;
    }
    private static Machine determinize(Machine nfa, List<String> trace) {
        Map<Set<Integer>,Integer> ids = new LinkedHashMap<>(); List<Set<Integer>> sets = new ArrayList<>();
        Set<Integer> start = closure(nfa,Set.of(nfa.start)); ids.put(start,0); sets.add(start);
        List<Edge> edges = new ArrayList<>(); Set<Integer> finals = new TreeSet<>(); Map<Integer,String> names = new LinkedHashMap<>();
        List<String> alphabet = nfa.alphabet();
        for (int index = 0; index < sets.size(); index++) {
            checkCancelled(); Set<Integer> set = sets.get(index); names.put(index,set.toString());
            if (!Collections.disjoint(set,nfa.finals)) finals.add(index);
            trace.add("D" + index + " = ε-closure " + set);
            for (String c : alphabet) {
                Set<Integer> target = closure(nfa,move(nfa,set,c)); Integer next = ids.get(target);
                if (next == null) {
                    if (sets.size() >= 1000) throw new IllegalArgumentException("DFA 超过 1000 状态限制");
                    next = sets.size(); ids.put(target,next); sets.add(target);
                }
                edges.add(new Edge(index,next,c)); trace.add("  move(D" + index + ", " + c + ") → D" + next + " " + target);
            }
        }
        return new Machine(sets.size(),0,Set.copyOf(finals),List.copyOf(edges),Collections.unmodifiableMap(names));
    }
    private static Machine minimize(Machine dfa,List<String> trace) {
        List<String> alphabet = dfa.alphabet(); Map<Integer,Map<String,Integer>> delta = new HashMap<>();
        dfa.edges.forEach(e -> delta.computeIfAbsent(e.from,k -> new HashMap<>()).put(e.label,e.to));
        Set<Integer> accepting = new TreeSet<>(dfa.finals), other = new TreeSet<>();
        for (int i=0;i<dfa.count;i++) if (!accepting.contains(i)) other.add(i);
        List<Set<Integer>> groups = new ArrayList<>(); if (!accepting.isEmpty()) groups.add(accepting); if (!other.isEmpty()) groups.add(other);
        trace.add("初始分割 P0 = " + groups);
        while (true) {
            checkCancelled(); Map<Integer,Integer> owner = owners(groups); List<Set<Integer>> next = new ArrayList<>();
            for (Set<Integer> group : groups) {
                Map<List<Integer>,Set<Integer>> buckets = new LinkedHashMap<>();
                for (int s : group) {
                    List<Integer> signature = alphabet.stream().map(c -> owner.get(delta.get(s).get(c))).toList();
                    buckets.computeIfAbsent(signature,k -> new TreeSet<>()).add(s);
                }
                next.addAll(buckets.values());
            }
            if (next.equals(groups)) break;
            groups=next; trace.add("细化分割 = " + groups);
        }
        Map<Integer,Integer> owner = owners(groups); List<Edge> edges = new ArrayList<>(); Set<Integer> finals = new TreeSet<>(); Map<Integer,String> names = new LinkedHashMap<>();
        for (int i=0;i<groups.size();i++) {
            Set<Integer> g = groups.get(i); names.put(i,g.toString()); if (!Collections.disjoint(g,dfa.finals)) finals.add(i);
            int representative = g.iterator().next(); for (String c : alphabet) edges.add(new Edge(i,owner.get(delta.get(representative).get(c)),c));
        }
        return new Machine(groups.size(),owner.get(dfa.start),Set.copyOf(finals),List.copyOf(edges),Collections.unmodifiableMap(names));
    }
    private static Map<Integer,Integer> owners(List<Set<Integer>> groups) {
        Map<Integer,Integer> result = new HashMap<>(); for (int i=0;i<groups.size();i++) for (int s:groups.get(i)) result.put(s,i); return result;
    }
    public static Recognition recognize(Machine machine,String input) {
        List<String> steps = new ArrayList<>(); Set<Integer> states = closure(machine,Set.of(machine.start)); steps.add("初始 ε-closure = " + states);
        for (int cp : input.codePoints().toArray()) {
            checkCancelled(); String symbol = new String(Character.toChars(cp));
            states = closure(machine,move(machine,states,symbol)); steps.add("读取 '" + symbol + "' → " + states);
        }
        boolean accepted = !Collections.disjoint(states,machine.finals); steps.add(accepted ? "接受：包含终态" : "拒绝：不包含终态");
        return new Recognition(accepted,List.copyOf(steps));
    }
    public static String format(Machine m) {
        StringBuilder b = new StringBuilder("状态数=" + m.count + "  初态=" + m.start + "  终态=" + new TreeSet<>(m.finals) + "\n");
        m.names.forEach((id,name) -> b.append(id).append(" = ").append(name).append('\n'));
        m.edges.forEach(e -> b.append(e.from).append(" --").append(e.display()).append("--> ").append(e.to).append('\n')); return b.toString();
    }
}
