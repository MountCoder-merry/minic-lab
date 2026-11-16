package edu.minic.lab;

import java.util.*;

public final class Lr {
    public enum Mode { LR0, SLR1, LR1;
        @Override public String toString(){return switch(this){case LR0->"LR(0)";case SLR1->"SLR(1)";case LR1->"LR(1)";};}
    }
    public record Item(int production,int dot,String lookahead) {}
    public record Key(int state,String symbol) {}
    public record Action(String kind,int value) {
        @Override public String toString(){return kind.equals("acc")?"acc":kind+value;}
    }
    public record Result(Mode mode,Grammar grammar,List<Grammar.Production> productions,List<Set<Item>> states,
                         Map<Key,Integer> transitions,Map<Key,List<Action>> action,Map<Key,Integer> goTo,List<String> conflicts) {}
    private final Grammar grammar;
    private final Mode mode;
    private final Grammar.Analysis analysis;
    private final List<Grammar.Production> productions=new ArrayList<>();
    private final Map<String,List<Grammar.Production>> byLeft=new HashMap<>();
    private Lr(Grammar grammar,Mode mode){
        this.grammar=grammar;this.mode=mode;analysis=grammar.analyze();String augmented=grammar.start()+"'";
        while(grammar.nonterminals().contains(augmented)||grammar.terminals().contains(augmented))augmented+="'";
        productions.add(new Grammar.Production(0,augmented,List.of(grammar.start())));productions.addAll(grammar.productions());
        productions.forEach(p->byLeft.computeIfAbsent(p.left(),k->new ArrayList<>()).add(p));
    }
    private Set<Item> closure(Collection<Item> kernel){
        Set<Item> out=new LinkedHashSet<>(kernel);Deque<Item> queue=new ArrayDeque<>(kernel);
        while(!queue.isEmpty()){
            Automata.checkCancelled();Item item=queue.removeFirst();var p=productions.get(item.production);
            if(item.dot>=p.right().size())continue;String next=p.right().get(item.dot);
            if(!grammar.nonterminals().contains(next))continue;
            Set<String> lookaheads=Set.of("");
            if(mode==Mode.LR1){List<String> suffix=new ArrayList<>(p.right().subList(item.dot+1,p.right().size()));suffix.add(item.lookahead);lookaheads=grammar.firstOf(suffix,analysis.first());}
            for(var target:byLeft.get(next))for(String look:lookaheads){
                Item candidate=new Item(target.id(),0,look);
                if(out.add(candidate)){queue.add(candidate);if(out.size()>10000)throw new IllegalArgumentException("单个 LR 项目集超过 10000 项目限制");}
            }
        }
        return Collections.unmodifiableSet(out);
    }
    public static Result build(Grammar grammar,Mode mode){return new Lr(grammar,mode).build();}
    private Result build(){
        List<Set<Item>> states=new ArrayList<>();Map<Set<Item>,Integer> ids=new HashMap<>();Map<Key,Integer> transitions=new LinkedHashMap<>();
        Set<Item> start=closure(List.of(new Item(0,0,mode==Mode.LR1?Grammar.END:"")));states.add(start);ids.put(start,0);
        List<String> symbols=new ArrayList<>(grammar.terminals());symbols.addAll(grammar.nonterminals());
        for(int index=0;index<states.size();index++){
            Automata.checkCancelled();
            for(String symbol:symbols){
                List<Item> kernel=new ArrayList<>();
                for(Item item:states.get(index)) {var p=productions.get(item.production);if(item.dot<p.right().size()&&p.right().get(item.dot).equals(symbol))kernel.add(new Item(item.production,item.dot+1,item.lookahead));}
                if(kernel.isEmpty())continue;Set<Item> target=closure(kernel);Integer next=ids.get(target);
                if(next==null){if(states.size()>=1000)throw new IllegalArgumentException("LR 自动机超过 1000 状态限制");next=states.size();states.add(target);ids.put(target,next);}
                transitions.put(new Key(index,symbol),next);
            }
        }
        Map<Key,List<Action>> actions=new LinkedHashMap<>();Map<Key,Integer> goTo=new LinkedHashMap<>();
        transitions.forEach((k,v)->{if(grammar.terminals().contains(k.symbol))add(actions,k,new Action("s",v));else goTo.put(k,v);});
        Set<String> allTerminals=new LinkedHashSet<>(grammar.terminals());allTerminals.add(Grammar.END);
        for(int index=0;index<states.size();index++)for(Item item:states.get(index)){
            var p=productions.get(item.production);if(item.dot!=p.right().size())continue;
            if(item.production==0){add(actions,new Key(index,Grammar.END),new Action("acc",0));continue;}
            Collection<String> reduction=mode==Mode.LR0?allTerminals:mode==Mode.SLR1?analysis.follow().get(p.left()):List.of(item.lookahead);
            for(String look:reduction)add(actions,new Key(index,look),new Action("r",p.id()));
        }
        List<String> conflicts=new ArrayList<>();Map<Key,List<Action>> frozen=new LinkedHashMap<>();
        actions.forEach((k,v)->{
            frozen.put(k,List.copyOf(v));
            if(v.size()>1){boolean shift=v.stream().anyMatch(a->a.kind.equals("s"));conflicts.add("ACTION["+k.state+", "+k.symbol+"] "+(shift?"移进/规约":"规约/规约")+"冲突："+v);}
        });
        return new Result(mode,grammar,List.copyOf(productions),List.copyOf(states),Collections.unmodifiableMap(transitions),Collections.unmodifiableMap(frozen),Collections.unmodifiableMap(goTo),List.copyOf(conflicts));
    }
    private static void add(Map<Key,List<Action>> actions,Key key,Action action){List<Action> list=actions.computeIfAbsent(key,k->new ArrayList<>());if(!list.contains(action))list.add(action);}
    public static Grammar.ParseResult parse(Result result,String text){
        List<String> input=Grammar.input(text);List<Integer> states=new ArrayList<>(List.of(0));List<String> symbols=new ArrayList<>(List.of(Grammar.END));List<Grammar.Step> steps=new ArrayList<>();int cursor=0;
        if(!result.conflicts.isEmpty())return new Grammar.ParseResult(false,List.of(new Grammar.Step("",text,"文法存在 "+result.mode+" 冲突，不能唯一分析")));
        for(int count=0;count<10000;count++){
            Automata.checkCancelled();List<Action> candidates=result.action.getOrDefault(new Key(states.getLast(),input.get(cursor)),List.of());
            String stack=states+" "+String.join(" ",symbols),remaining=String.join(" ",input.subList(cursor,input.size()));
            if(candidates.isEmpty()){steps.add(new Grammar.Step(stack,remaining,"拒绝：ACTION 无条目"));return new Grammar.ParseResult(false,List.copyOf(steps));}
            Action a=candidates.getFirst();String action=a.toString();if(a.kind.equals("r"))action+="  "+result.productions.get(a.value);
            steps.add(new Grammar.Step(stack,remaining,action));
            if(a.kind.equals("acc"))return new Grammar.ParseResult(true,List.copyOf(steps));
            if(a.kind.equals("s")){symbols.add(input.get(cursor++));states.add(a.value);}
            else {
                var p=result.productions.get(a.value);for(int i=0;i<p.right().size();i++){symbols.removeLast();states.removeLast();}
                Integer next=result.goTo.get(new Key(states.getLast(),p.left()));if(next==null)throw new IllegalStateException("规约后缺少 GOTO");symbols.add(p.left());states.add(next);
            }
        }
        steps.add(new Grammar.Step("","","分析超过 10000 步，已终止"));return new Grammar.ParseResult(false,List.copyOf(steps));
    }
    public static String itemText(Result r,Item item){var p=r.productions.get(item.production);List<String> right=new ArrayList<>(p.right());right.add(item.dot,"·");return p.left()+" → "+String.join(" ",right)+(r.mode==Mode.LR1?", "+item.lookahead:"");}
    public static String format(Result r){
        StringBuilder b=new StringBuilder(r.mode+"  状态数="+r.states.size()+"\n\n");
        for(int i=0;i<r.states.size();i++){b.append("I").append(i).append(":\n");for(Item item:r.states.get(i))b.append("  ").append(itemText(r,item)).append('\n');}
        b.append("\nACTION\n");r.action.forEach((k,v)->b.append(k.state).append(" / ").append(k.symbol).append(" = ").append(v).append('\n'));
        b.append("\nGOTO\n");r.goTo.forEach((k,v)->b.append(k.state).append(" / ").append(k.symbol).append(" = ").append(v).append('\n'));
        b.append(r.conflicts.isEmpty()?"\n无冲突\n":"\n冲突：\n"+String.join("\n",r.conflicts));return b.toString();
    }
}
