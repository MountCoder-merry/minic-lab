package edu.minic.lab;

import java.util.*;

public final class Grammar {
    public static final String EPS = "ε", END = "#";
    public record Production(int id, String left, List<String> right) {
        @Override public String toString() { return id + ": " + left + " → " + (right.isEmpty() ? EPS : String.join(" ",right)); }
    }
    public record Cell(String row,String column) {}
    public record Analysis(Set<String> nullable, Map<String,Set<String>> first, Map<String,Set<String>> follow,
                           Map<Integer,Set<String>> select, Map<Cell,List<Production>> table, List<String> conflicts) {}
    public record Step(String stack,String remaining,String action) {
        @Override public String toString() { return "%-38s │ %-30s │ %s".formatted(stack,remaining,action); }
    }
    public record ParseResult(boolean accepted,List<Step> steps) {}
    private final List<Production> productions;
    private final Set<String> nonterminals, terminals;
    private final String start;
    private Grammar(List<Production> productions) {
        this.productions=List.copyOf(productions); start=productions.getFirst().left;
        Set<String> vn = new LinkedHashSet<>(), vt=new LinkedHashSet<>(); productions.forEach(p -> vn.add(p.left));
        productions.forEach(p -> vt.addAll(p.right)); vt.removeAll(vn);
        nonterminals=Collections.unmodifiableSet(vn); terminals=Collections.unmodifiableSet(vt);
    }
    public List<Production> productions() { return productions; }
    public Set<String> nonterminals() { return nonterminals; }
    public Set<String> terminals() { return terminals; }
    public String start() { return start; }
    public static Grammar parse(String text) {
        if (text.length()>50000) throw new IllegalArgumentException("文法超过 50000 字符限制");
        List<Production> ps = new ArrayList<>(); int line=0;
        for (String raw:text.split("\\R")) {
            Automata.checkCancelled(); line++; String s=raw.strip(); if (s.isEmpty()) continue;
            String[] lr=s.split("->|→",-1);
            if (lr.length!=2 || lr[0].strip().isEmpty() || lr[0].strip().split("\\s+").length!=1) throw new IllegalArgumentException("第 "+line+" 行应为 A -> 符号 符号 | ε");
            String left=lr[0].strip();
            if (Set.of(EPS,END,"|").contains(left)) throw new IllegalArgumentException("左部不能使用保留符号 "+left);
            for (String alternative:lr[1].split("\\|",-1)) {
                String rhs=alternative.strip(); if (rhs.isEmpty()) throw new IllegalArgumentException("第 "+line+" 行空产生式请明确写 ε");
                List<String> symbols=List.of(rhs.split("\\s+"));
                if (symbols.contains(END) || symbols.contains(EPS) && symbols.size()!=1) throw new IllegalArgumentException("# 保留为结束符；ε 必须单独出现");
                if (symbols.equals(List.of(EPS))) symbols=List.of();
                if (symbols.size()>100) throw new IllegalArgumentException("单条产生式右部超过 100 符号");
                Production p=new Production(ps.size()+1,left,symbols);
                if (ps.stream().anyMatch(old -> old.left.equals(p.left) && old.right.equals(p.right))) throw new IllegalArgumentException("重复产生式："+p);
                ps.add(p); if (ps.size()>200) throw new IllegalArgumentException("文法超过 200 产生式限制");
            }
        }
        if (ps.isEmpty()) throw new IllegalArgumentException("请至少输入一条产生式"); return new Grammar(ps);
    }
    private Map<String,Set<String>> emptySets() { Map<String,Set<String>> result=new LinkedHashMap<>(); nonterminals.forEach(n -> result.put(n,new LinkedHashSet<>())); return result; }
    public Set<String> firstOf(List<String> sequence, Map<String,Set<String>> first) {
        Set<String> result=new LinkedHashSet<>(); boolean nullable=true;
        for (String symbol:sequence) {
            Set<String> f=nonterminals.contains(symbol) ? first.get(symbol) : Set.of(symbol);
            for (String c:f) if (!c.equals(EPS)) result.add(c);
            if (!f.contains(EPS)) { nullable=false; break; }
        }
        if (nullable) result.add(EPS); return result;
    }
    public Analysis analyze() {
        Map<String,Set<String>> first=emptySets(), follow=emptySets(); boolean changed;
        do {
            Automata.checkCancelled(); changed=false;
            for (Production p:productions) changed |= first.get(p.left).addAll(firstOf(p.right,first));
        } while(changed);
        Set<String> nullable=new LinkedHashSet<>(); first.forEach((n,f) -> { if(f.contains(EPS)) nullable.add(n); });
        follow.get(start).add(END);
        do {
            Automata.checkCancelled(); changed=false;
            for (Production p:productions) for(int i=0;i<p.right.size();i++) {
                String symbol=p.right.get(i); if (!nonterminals.contains(symbol)) continue;
                Set<String> suffix=firstOf(p.right.subList(i+1,p.right.size()),first);
                for(String c:suffix) if(!c.equals(EPS)) changed |= follow.get(symbol).add(c);
                if(suffix.contains(EPS)) changed |= follow.get(symbol).addAll(follow.get(p.left));
            }
        } while(changed);
        Map<Integer,Set<String>> select=new LinkedHashMap<>(); Map<Cell,List<Production>> table=new LinkedHashMap<>();
        for (Production p:productions) {
            Set<String> set=new LinkedHashSet<>(firstOf(p.right,first)); if(set.remove(EPS)) set.addAll(follow.get(p.left));
            select.put(p.id,Set.copyOf(set));
            for(String terminal:set) table.computeIfAbsent(new Cell(p.left,terminal),k->new ArrayList<>()).add(p);
        }
        List<String> conflicts=new ArrayList<>(); table.forEach((cell,ps)->{ if(ps.size()>1) conflicts.add("M["+cell.row+", "+cell.column+"] 冲突："+ps); });
        Map<Cell,List<Production>> frozen=new LinkedHashMap<>(); table.forEach((k,v)->frozen.put(k,List.copyOf(v)));
        return new Analysis(Set.copyOf(nullable),freeze(first),freeze(follow),Collections.unmodifiableMap(select),Collections.unmodifiableMap(frozen),List.copyOf(conflicts));
    }
    private static Map<String,Set<String>> freeze(Map<String,Set<String>> m) {
        Map<String,Set<String>> out=new LinkedHashMap<>(); m.forEach((k,v)->out.put(k,Collections.unmodifiableSet(new LinkedHashSet<>(v)))); return Collections.unmodifiableMap(out);
    }
    public static List<String> input(String text) {
        List<String> symbols=text.isBlank()?new ArrayList<>():new ArrayList<>(List.of(text.strip().split("\\s+")));
        if(symbols.size()>2000) throw new IllegalArgumentException("输入串超过 2000 符号");
        if(symbols.contains(END)||symbols.contains(EPS)) throw new IllegalArgumentException("输入串不要填写 # 或 ε，空串请留空");
        symbols.add(END);return symbols;
    }
    public ParseResult predict(Analysis a,String text) {
        List<Step> steps=new ArrayList<>(); List<String> input=input(text); List<String> stack=new ArrayList<>(List.of(END,start)); int cursor=0;
        if(!a.conflicts.isEmpty()) return new ParseResult(false,List.of(new Step("",text,"文法存在 LL(1) 冲突，不能唯一分析")));
        for(int count=0;count<10000;count++) {
            Automata.checkCancelled(); String top=stack.getLast(), look=input.get(cursor), action;
            if(top.equals(END)&&look.equals(END)) { steps.add(new Step(String.join(" ",stack),END,"接受"));return new ParseResult(true,List.copyOf(steps)); }
            if(!nonterminals.contains(top)) {
                if(!top.equals(look)) { steps.add(new Step(String.join(" ",stack),String.join(" ",input.subList(cursor,input.size())),"拒绝：期望 "+top));return new ParseResult(false,List.copyOf(steps)); }
                action="匹配 "+look;
            } else {
                List<Production> candidates=a.table.getOrDefault(new Cell(top,look),List.of());
                if(candidates.isEmpty()) { steps.add(new Step(String.join(" ",stack),String.join(" ",input.subList(cursor,input.size())),"拒绝：预测表无条目"));return new ParseResult(false,List.copyOf(steps)); }
                action=candidates.getFirst().toString();
            }
            steps.add(new Step(String.join(" ",stack),String.join(" ",input.subList(cursor,input.size())),action));stack.removeLast();
            if(nonterminals.contains(top)) { List<String> rhs=a.table.get(new Cell(top,look)).getFirst().right;for(int i=rhs.size()-1;i>=0;i--)stack.add(rhs.get(i)); }
            else cursor++;
        }
        steps.add(new Step("","","分析超过 10000 步，已终止"));return new ParseResult(false,List.copyOf(steps));
    }
    /** 左角图含 Nullable 前缀，以区分直接与间接左递归。 */
    public List<String> leftRecursion() {
        Analysis a=analyze();Map<String,Set<String>> graph=emptySets();
        for(Production p:productions) for(String s:p.right) {
            if(!nonterminals.contains(s))break;graph.get(p.left).add(s);if(!a.nullable.contains(s))break;
        }
        List<String> result=new ArrayList<>();
        for(String n:nonterminals) {
            if(graph.get(n).contains(n))result.add(n+" 存在直接/可空前缀左递归");
            Deque<String> pending=new ArrayDeque<>(graph.get(n));Set<String> seen=new HashSet<>();
            while(!pending.isEmpty()) { Automata.checkCancelled();String x=pending.removeFirst();if(x.equals(n)||!seen.add(x))continue;
                if(graph.get(x).contains(n)){result.add(n+" 存在间接左递归（经 "+x+"）");break;}pending.addAll(graph.get(x)); }
        }
        return List.copyOf(result);
    }
    public String eliminateDirectLeftRecursion() {
        Map<String,List<List<String>>> rules=rules(); Set<String> used=new HashSet<>();used.addAll(nonterminals);used.addAll(terminals);
        Map<String,List<List<String>>> out=new LinkedHashMap<>();
        for(var entry:rules.entrySet()) {
            Automata.checkCancelled();String a=entry.getKey();List<List<String>> alpha=new ArrayList<>(),beta=new ArrayList<>();
            for(List<String> rhs:entry.getValue()) { if(!rhs.isEmpty()&&rhs.getFirst().equals(a)) { if(rhs.size()>1)alpha.add(rhs.subList(1,rhs.size())); }else beta.add(rhs); }
            boolean hadSelf=entry.getValue().stream().anyMatch(r -> !r.isEmpty()&&r.getFirst().equals(a));
            if(hadSelf&&beta.isEmpty())throw new IllegalArgumentException(a+" 没有非递归候选式，不能用此公式消除；请先检查文法是否能生成终结串");
            if(alpha.isEmpty()){out.put(a,beta);continue;}
            String helper=fresh(a,used);List<List<String>> left=new ArrayList<>(),right=new ArrayList<>();
            for(List<String> b:beta)left.add(append(b,helper));for(List<String> r:alpha)right.add(append(r,helper));right.add(List.of());out.put(a,left);out.put(helper,right);
        }
        return renderRules(out);
    }
    public String factor() {
        Map<String,List<List<String>>> rules=rules();Set<String> used=new HashSet<>();used.addAll(nonterminals);used.addAll(terminals);
        boolean changed;
        do {
            Automata.checkCancelled();changed=false;
            for(String left:new ArrayList<>(rules.keySet())) {
                List<List<String>> alternatives=rules.get(left);List<String> prefix=List.of();
                for(int i=0;i<alternatives.size();i++)for(int j=i+1;j<alternatives.size();j++) {
                    var x=alternatives.get(i);var y=alternatives.get(j);int size=0;while(size<Math.min(x.size(),y.size())&&x.get(size).equals(y.get(size)))size++;
                    if(size>prefix.size())prefix=List.copyOf(x.subList(0,size));
                }
                if(prefix.isEmpty())continue;
                String helper=fresh(left,used);List<List<String>> remain=new ArrayList<>(),suffix=new ArrayList<>();
                for(List<String> rhs:alternatives) { if(rhs.size()>=prefix.size()&&rhs.subList(0,prefix.size()).equals(prefix))suffix.add(List.copyOf(rhs.subList(prefix.size(),rhs.size())));else remain.add(rhs); }
                remain.add(append(prefix,helper));rules.put(left,remain);rules.put(helper,suffix);changed=true;
                if(rules.size()>400)throw new IllegalArgumentException("提取公因子结果过大");break;
            }
        }while(changed);
        return renderRules(rules);
    }
    private Map<String,List<List<String>>> rules(){Map<String,List<List<String>>> rules=new LinkedHashMap<>();productions.forEach(p->rules.computeIfAbsent(p.left,k->new ArrayList<>()).add(p.right));return rules;}
    private static String fresh(String base,Set<String> used){String name=base+"'";while(!used.add(name))name+="'";return name;}
    private static List<String> append(List<String> list,String last){List<String> out=new ArrayList<>(list);out.add(last);return List.copyOf(out);}
    private static String renderRules(Map<String,List<List<String>>> rules){StringBuilder b=new StringBuilder();rules.forEach((left,alts)->b.append(left).append(" -> ").append(String.join(" | ",alts.stream().map(rhs->rhs.isEmpty()?EPS:String.join(" ",rhs)).toList())).append('\n'));return b.toString();}
    public String format(Analysis a) {
        StringBuilder b=new StringBuilder("开始符="+start+"\nVN="+nonterminals+"\nVT="+terminals+"\nNullable="+new TreeSet<>(a.nullable)+"\n\n");
        nonterminals.forEach(n->b.append(n).append(" FIRST=").append(new TreeSet<>(a.first.get(n))).append(" FOLLOW=").append(new TreeSet<>(a.follow.get(n))).append('\n'));
        productions.forEach(p->b.append(p).append(" SELECT=").append(new TreeSet<>(a.select.get(p.id))).append('\n'));
        b.append("\n预测分析表\n");a.table.forEach((k,v)->b.append("M[").append(k.row).append(", ").append(k.column).append("] = ").append(v).append('\n'));
        b.append(a.conflicts.isEmpty()?"\n是 LL(1) 文法\n":"\n冲突：\n"+String.join("\n",a.conflicts));return b.toString();
    }
}
