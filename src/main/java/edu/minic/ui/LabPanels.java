package edu.minic.ui;

import edu.minic.lab.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

final class LabPanels {
    static final String LL="E -> T E'\nE' -> + T E' | ε\nT -> F T'\nT' -> * F T' | ε\nF -> ( E ) | id";
    static final String EXPR="E -> E + T | T\nT -> T * F | F\nF -> ( E ) | id";
    static final class AutomataPanel extends Ui.AsyncPanel {
        private final JTextField regex=new JTextField("(a|b)*abb",25),word=new JTextField("aabb",18);
        private final JComboBox<String> machineChoice=new JComboBox<>(new String[]{"NFA","DFA","最小 DFA"});
        private final GraphPanel graph=new GraphPanel();private final JTextArea transitions=Ui.text(false);private final TracePanel construction=new TracePanel(),recognition=new TracePanel();
        private final JButton build;private Automata.Result result;private List<Automata.Recognition> recognitions=List.of();
        private record Built(Automata.Result result,List<Automata.Recognition> recognitions){}
        AutomataPanel(){
            regex.setName("regex");word.setName("automataInput");status.setName("automataStatus");
            build=Ui.button("构造并识别","buildAutomata",this::build);
            JPanel header=Ui.panel(new BorderLayout());header.add(Ui.flow(new JLabel("正则式"),regex,new JLabel("识别字符串"),word,build,cancel),BorderLayout.NORTH);
            JLabel hint=new JLabel("第三章 · 支持连接、|、*、括号、ε、∅ 与反斜杠转义；空输入串直接留空。");hint.setBorder(BorderFactory.createEmptyBorder(5,8,8,0));header.add(hint,BorderLayout.SOUTH);add(header,BorderLayout.NORTH);
            JTabbedPane tabs=new JTabbedPane();tabs.addTab("状态图 · 滚轮缩放 / 拖动平移",graph);tabs.addTab("转换表",Ui.scroll(transitions));tabs.addTab("构造回放",construction);tabs.addTab("识别回放",recognition);
            JPanel center=Ui.panel(new BorderLayout());center.add(Ui.flow(new JLabel("查看自动机"),machineChoice),BorderLayout.NORTH);center.add(tabs);add(center);add(status,BorderLayout.SOUTH);
            Ui.watch(regex.getDocument(),this::invalidateInput);Ui.watch(word.getDocument(),this::invalidateInput);machineChoice.addActionListener(e->display());
        }
        private void invalidateInput(){result=null;changed();}
        private void build(){String re=regex.getText(),text=word.getText();compute(()->{var r=Automata.build(re);return new Built(r,List.of(Automata.recognize(r.nfa(),text),Automata.recognize(r.dfa(),text),Automata.recognize(r.minimized(),text)));},built->{result=built.result;recognitions=built.recognitions;construction.setSteps(result.construction());display();status.setForeground(Ui.TEAL);status.setText("NFA "+result.nfa().count()+" → DFA "+result.dfa().count()+" → 最小 DFA "+result.minimized().count()+" 个状态 · "+(recognitions.getFirst().accepted()?"输入串被接受":"输入串被拒绝"));},build);}
        private void display(){if(result==null)return;int i=machineChoice.getSelectedIndex();var m=i==0?result.nfa():i==1?result.dfa():result.minimized();graph.setMachine(m);transitions.setText(Automata.format(m));transitions.setCaretPosition(0);recognition.setSteps(recognitions.get(i).steps());}
    }
    static final class LlPanel extends Ui.AsyncPanel {
        private final JTextArea grammar=Ui.text(true),summary=Ui.text(false);private final JTextField input=new JTextField("id + id * id",22);private final JTable table=new JTable();private final TracePanel trace=new TracePanel();private final JButton build,direct,factor;
        private record Built(Grammar grammar,Grammar.Analysis analysis,Grammar.ParseResult result,List<String> recursion){}
        LlPanel(){
            grammar.setText(LL);grammar.setName("llGrammar");input.setName("llInput");status.setName("llStatus");
            build=Ui.button("计算并分析","buildLl",this::build);direct=Ui.button("消除直接左递归","eliminateLeft",()->transform(true));factor=Ui.button("提取左公因子","factorGrammar",()->transform(false));
            JPanel head=Ui.panel(new BorderLayout());head.add(Ui.flow(build,direct,factor,cancel),BorderLayout.NORTH);head.add(Ui.flow(new JLabel("第四章 · 输入符号以空格分隔，空串留空："),input),BorderLayout.SOUTH);add(head,BorderLayout.NORTH);
            JTabbedPane tabs=new JTabbedPane();tabs.addTab("集合、产生式与检查",Ui.scroll(summary));tabs.addTab("预测分析表",Ui.scroll(table));tabs.addTab("分析回放",trace);
            var split=Ui.split(JSplitPane.HORIZONTAL_SPLIT,Ui.titled("文法 · A -> a B | ε",Ui.scroll(grammar)),tabs,.32);split.setDividerLocation(340);add(split);add(status,BorderLayout.SOUTH);Ui.watch(grammar.getDocument(),this::changed);Ui.watch(input.getDocument(),this::changed);
        }
        private void build(){String text=grammar.getText(),word=input.getText();compute(()->{var g=Grammar.parse(text);var a=g.analyze();return new Built(g,a,g.predict(a,word),g.leftRecursion());},r->{summary.setText(r.grammar.format(r.analysis)+"\n\n左递归检查\n"+(r.recursion.isEmpty()?"未发现左递归":String.join("\n",r.recursion)));summary.setCaretPosition(0);llTable(r.grammar,r.analysis,table);trace.setSteps(r.result.steps().stream().map(Object::toString).toList());status.setForeground(r.analysis.conflicts().isEmpty()?Ui.TEAL:Ui.RED);status.setText(r.analysis.conflicts().isEmpty()?"LL(1) 表已生成 · "+(r.result.accepted()?"输入串被接受":"输入串被拒绝"):"检测到 "+r.analysis.conflicts().size()+" 处冲突，未选择冲突候选式");},build,direct,factor);}
        private void transform(boolean eliminate){String text=grammar.getText();compute(()->{var g=Grammar.parse(text);return eliminate?g.eliminateDirectLeftRecursion():g.factor();},transformed->{grammar.setText(transformed);summary.setText("已应用文法变换。请点击“计算并分析”重新计算集合和预测表。\n\n"+transformed);status.setText("变换完成，请重新计算；间接左递归需根据检查结果手工处理");},build,direct,factor);}
        private static void llTable(Grammar g,Grammar.Analysis a,JTable table){List<String> terms=new ArrayList<>(g.terminals());terms.add(Grammar.END);List<String> headers=new ArrayList<>(List.of("非终结符"));headers.addAll(terms);List<Object[]> rows=new ArrayList<>();for(String n:g.nonterminals()){List<Object> row=new ArrayList<>(List.of(n));for(String t:terms)row.add(a.table().getOrDefault(new Grammar.Cell(n,t),List.of()).toString());rows.add(row.toArray());}Ui.table(table,headers.toArray(String[]::new),rows);}
    }
    static final class LrPanel extends Ui.AsyncPanel {
        private final JTextArea grammar=Ui.text(true),items=Ui.text(false);private final JTextField input=new JTextField("id + id * id",22);
        private final JComboBox<Lr.Mode> mode=new JComboBox<>(Lr.Mode.values());private final JTable action=new JTable(),goTo=new JTable();private final TracePanel trace=new TracePanel();private final GraphPanel graph=new GraphPanel();private final JButton build;
        private record Built(Lr.Result result,Grammar.ParseResult parse){}
        LrPanel(){
            grammar.setText(EXPR);grammar.setName("lrGrammar");input.setName("lrInput");mode.setName("lrMode");mode.setSelectedItem(Lr.Mode.SLR1);status.setName("lrStatus");build=Ui.button("构造并分析","buildLr",this::build);
            JPanel header=Ui.panel(new BorderLayout());header.add(Ui.flow(new JLabel("分析方法"),mode,build,cancel),BorderLayout.NORTH);header.add(Ui.flow(new JLabel("第五章 · 输入串（符号间留空格）："),input),BorderLayout.SOUTH);add(header,BorderLayout.NORTH);
            JTabbedPane tabs=new JTabbedPane();tabs.addTab("ACTION / GOTO",Ui.split(JSplitPane.VERTICAL_SPLIT,Ui.titled("ACTION · s 移进 / r 规约 / acc 接受",Ui.scroll(action)),Ui.titled("GOTO",Ui.scroll(goTo)),.6));tabs.addTab("项目集与冲突",Ui.scroll(items));tabs.addTab("活前缀自动机",graph);tabs.addTab("分析回放",trace);
            var split=Ui.split(JSplitPane.HORIZONTAL_SPLIT,Ui.titled("文法 · 首行左部为开始符",Ui.scroll(grammar)),tabs,.32);split.setDividerLocation(340);add(split);add(status,BorderLayout.SOUTH);Ui.watch(grammar.getDocument(),this::changed);Ui.watch(input.getDocument(),this::changed);mode.addActionListener(e->changed());
        }
        private void build(){String text=grammar.getText(),word=input.getText();Lr.Mode selected=(Lr.Mode)mode.getSelectedItem();compute(()->{var r=Lr.build(Grammar.parse(text),selected);return new Built(r,Lr.parse(r,word));},built->{var r=built.result;items.setText((r.conflicts().isEmpty()?"无冲突\n":"冲突明细\n"+String.join("\n",r.conflicts())+"\n\n")+Lr.format(r));items.setCaretPosition(0);fill(r,action,true);fill(r,goTo,false);trace.setSteps(built.parse.steps().stream().map(Object::toString).toList());graph.setMachine(machine(r));status.setForeground(r.conflicts().isEmpty()?Ui.TEAL:Ui.RED);status.setText(r.mode()+" · "+r.states().size()+" 个状态 · "+(r.conflicts().isEmpty()?(built.parse.accepted()?"输入串被接受":"输入串被拒绝"):r.conflicts().size()+" 处冲突，未执行歧义分析"));},build);}
        private static void fill(Lr.Result r,JTable table,boolean actions){List<String> columns=new ArrayList<>(actions?r.grammar().terminals():r.grammar().nonterminals());if(actions)columns.add(Grammar.END);List<String> headers=new ArrayList<>(List.of("状态"));headers.addAll(columns);List<Object[]> rows=new ArrayList<>();for(int state=0;state<r.states().size();state++){List<Object> row=new ArrayList<>(List.of(state));for(String s:columns){var key=new Lr.Key(state,s);row.add(actions?r.action().getOrDefault(key,List.of()).toString():r.goTo().getOrDefault(key,null));}rows.add(row.toArray());}Ui.table(table,headers.toArray(String[]::new),rows);}
        private static Automata.Machine machine(Lr.Result r){List<Automata.Edge> edges=new ArrayList<>();r.transitions().forEach((k,v)->edges.add(new Automata.Edge(k.state(),v,k.symbol())));Set<Integer> finals=new HashSet<>();r.action().forEach((k,as)->{if(as.stream().anyMatch(a->a.kind().equals("acc")))finals.add(k.state());});Map<Integer,String> names=new LinkedHashMap<>();for(int i=0;i<r.states().size();i++){StringBuilder b=new StringBuilder();for(var item:r.states().get(i))b.append(Lr.itemText(r,item)).append("; ");names.put(i,b.toString());}return new Automata.Machine(r.states().size(),0,Set.copyOf(finals),List.copyOf(edges),Collections.unmodifiableMap(names));}
    }
}
