package edu.minic.ui;

import edu.minic.Examples;
import edu.minic.core.*;
import edu.minic.core.Source.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;

public final class CompilerPanel extends JPanel {
    private final JTextArea editor=Ui.text(true),output=Ui.text(false),frames=Ui.text(false),attributes=Ui.text(false),rawIr=Ui.text(false),optimizedIr=Ui.text(false),parseTrace=Ui.text(false);
    private final JTable tokens=new JTable(),symbols=new JTable(),instructions=new JTable();
    private final JTree ast=new JTree(new DefaultMutableTreeNode("编译后查看语法树"));
    private final JTabbedPane results=new JTabbedPane(),console=new JTabbedPane();
    private final DefaultListModel<Diagnostic> errors=new DefaultListModel<>();private final JList<Diagnostic> diagnostics=new JList<>(errors);
    private final JTextField input=new JTextField("5",20);private final JCheckBox optimize=new JCheckBox("启用基本块优化",true);
    private final JLabel status=new JLabel("加载示例后，点击编译开始"),fileLabel=new JLabel("源代码"),pipeline=new JLabel();
    private final JSpinner maxInstructions=new JSpinner(new SpinnerNumberModel(1_000_000,1,100_000_000,10000)),maxDepth=new JSpinner(new SpinnerNumberModel(1024,1,4096,16));
    private final JButton compile,run,step,pause,stop,export;
    private CompilerService.CompilationResult result;private VirtualMachine vm;
    private SwingWorker<CompilerService.CompilationResult,Void> compileWorker;private SwingWorker<VirtualMachine.Snapshot,Void> runWorker;
    private long revision,compiledRevision=-1;private Path currentFile;private String savedSource="";private boolean loading;
    private record TreeEntry(Syntax.Node node,String text){@Override public String toString(){return text;}}
    private record InstructionRow(String function,int pc,Bytecode.Instruction instruction){}
    private List<InstructionRow> instructionRows=List.of();
    public CompilerPanel(){
        super(new BorderLayout(9,9));setBackground(Ui.BG);setBorder(new EmptyBorder(8,14,12,14));
        editor.setName("source");output.setName("output");status.setName("compilerStatus");input.setName("runtimeInput");results.setName("compilationResults");diagnostics.setName("diagnostics");maxInstructions.setName("maxInstructions");maxDepth.setName("maxDepth");
        compile=Ui.button("编译  F5","compile",this::compile);run=Ui.button("运行","run",()->execute(false));step=Ui.button("单步","step",()->execute(true));
        pause=Ui.button("暂停","pause",()->{if(vm!=null)vm.pause();});stop=Ui.button("停止","stop",()->{if(vm!=null){vm.stop();showSnapshot(vm.snapshot());}});export=Ui.button("导出结果","export",this::export);
        JComboBox<Examples.Example> examples=new JComboBox<>(Examples.ALL.toArray(Examples.Example[]::new));examples.setName("examples");
        JButton load=Ui.button("加载示例","loadExample",()->{if(confirmDiscard()){var e=(Examples.Example)examples.getSelectedItem();if(e!=null){setSource(e.source(),null);input.setText(e.input());}}});
        JPanel top=Ui.panel(new BorderLayout());top.add(Ui.flow(Ui.button("打开","open",this::open),Ui.button("保存","save",this::save),examples,load,compile,optimize,export));
        pipeline.setForeground(Ui.BLUE);pipeline.setBorder(new EmptyBorder(8,8,8,0));pipeline.setText("01 词法  →  02 语法  →  03 语义  →  04 四元式  →  05 优化  →  06 字节码");top.add(pipeline,BorderLayout.SOUTH);add(top,BorderLayout.NORTH);
        JScrollPane sourceScroll=Ui.scroll(editor);JTextArea lineNumbers=new JTextArea("1");lineNumbers.setEditable(false);lineNumbers.setFont(Ui.CODE);lineNumbers.setBackground(Ui.BG);lineNumbers.setForeground(new Color(0x8897AD));lineNumbers.setMargin(new Insets(12,8,12,8));sourceScroll.setRowHeaderView(lineNumbers);
        results.addTab("Token",Ui.scroll(tokens));results.addTab("AST",Ui.scroll(ast));results.addTab("符号表",Ui.scroll(symbols));results.addTab("属性与对应",Ui.scroll(attributes));
        results.addTab("四元式",Ui.split(JSplitPane.HORIZONTAL_SPLIT,Ui.titled("优化前",Ui.scroll(rawIr)),Ui.titled("优化后",Ui.scroll(optimizedIr)),.5));
        results.addTab("目标指令",Ui.scroll(instructions));results.addTab("语法轨迹",Ui.scroll(parseTrace));results.addTab("运行现场",Ui.scroll(frames));
        JPanel sourcePane=Ui.panel(new BorderLayout(0,6));fileLabel.setBorder(new EmptyBorder(6,5,3,0));fileLabel.setForeground(Ui.INK);sourcePane.add(fileLabel,BorderLayout.NORTH);sourcePane.add(sourceScroll);
        JSplitPane work=Ui.split(JSplitPane.HORIZONTAL_SPLIT,sourcePane,results,.43);work.setDividerLocation(500);
        console.addTab("程序输出",Ui.scroll(output));console.addTab("诊断（点击跳转源码）",Ui.scroll(diagnostics));
        JPanel controls=Ui.panel(new GridLayout(2,1));controls.add(Ui.flow(run,step,pause,stop,new JLabel("输入整数"),input));controls.add(Ui.flow(new JLabel("累计指令上限"),maxInstructions,new JLabel("调用深度上限"),maxDepth));
        JPanel bottom=Ui.panel(new BorderLayout(0,6));bottom.add(controls,BorderLayout.NORTH);bottom.add(console);bottom.setPreferredSize(new Dimension(900,245));
        work.setMinimumSize(new Dimension(200,180));bottom.setMinimumSize(new Dimension(200,220));
        JSplitPane vertical=Ui.split(JSplitPane.VERTICAL_SPLIT,work,bottom,.60);vertical.setDividerLocation(350);SwingUtilities.invokeLater(()->vertical.setDividerLocation(.60));add(vertical);status.setBorder(new EmptyBorder(4,4,0,0));add(status,BorderLayout.SOUTH);
        Ui.watch(editor.getDocument(),()->{sourceChanged();StringBuilder b=new StringBuilder();for(int i=1;i<=editor.getLineCount();i++)b.append(i).append('\n');lineNumbers.setText(b.toString());});
        optimize.addActionListener(e->sourceChanged());Ui.watch(input.getDocument(),this::executionInputChanged);maxInstructions.addChangeListener(e->executionInputChanged());maxDepth.addChangeListener(e->executionInputChanged());
        tokens.getSelectionModel().addListSelectionListener(e->{int row=tokens.getSelectedRow();if(valid()&&row>=0&&row<result.tokens().size())highlight(result.tokens().get(row).span());});
        instructions.getSelectionModel().addListSelectionListener(e->{int row=instructions.getSelectedRow();if(valid()&&row>=0&&row<instructionRows.size())highlight(instructionRows.get(row).instruction.span());});
        ast.addTreeSelectionListener(e->{Object selected=ast.getLastSelectedPathComponent();if(valid()&&selected instanceof DefaultMutableTreeNode n&&n.getUserObject() instanceof TreeEntry t)highlight(t.node.span());});
        diagnostics.addListSelectionListener(e->{if(valid()&&diagnostics.getSelectedValue()!=null)highlight(diagnostics.getSelectedValue().span());});
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_F5,0),"compile");getActionMap().put("compile",new AbstractAction(){public void actionPerformed(ActionEvent e){compile();}});
        setSource(Examples.ALL.getFirst().source(),null);refreshButtons();
    }
    private void sourceChanged(){
        revision++;compiledRevision=-1;if(vm!=null)vm.stop();vm=null;if(!loading){status.setText("源码或编译选项已修改，请重新编译");status.setForeground(Ui.INK);}fileLabel.setText((currentFile==null?"未命名.mc":currentFile.toString())+(isModified()?" *":""));refreshButtons();
    }
    private void executionInputChanged(){if(vm!=null)vm.stop();vm=null;refreshButtons();}
    private boolean valid(){return result!=null&&compiledRevision==revision;}
    private boolean busy(){return runWorker!=null&&!runWorker.isDone();}
    private void refreshButtons(){
        boolean compiled=valid()&&result.success();boolean running=busy();boolean compiling=compileWorker!=null&&!compileWorker.isDone();
        compile.setEnabled(!compiling&&!running);run.setEnabled(compiled&&!running);step.setEnabled(compiled&&!running);pause.setEnabled(running);stop.setEnabled(running||vm!=null&&(vm.state()==VirtualMachine.State.PAUSED||vm.state()==VirtualMachine.State.READY));export.setEnabled(valid());
    }
    public void compile(){
        if(compileWorker!=null&&!compileWorker.isDone()||busy())return;
        if(vm!=null)vm.stop();vm=null;long submitted=revision;String source=editor.getText();boolean opt=optimize.isSelected();status.setText("正在编译…");status.setForeground(Ui.INK);
        compileWorker=new SwingWorker<>(){
            @Override protected CompilerService.CompilationResult doInBackground(){return new CompilerService().compile(source,new CompilerService.Options(opt));}
            @Override protected void done(){
                try{var compiled=get();if(submitted!=revision){status.setText("编译期间源码已变化，请重新编译");return;}result=compiled;compiledRevision=submitted;showCompilation();}
                catch(CancellationException e){status.setText("编译已取消");}
                catch(InterruptedException e){Thread.currentThread().interrupt();status.setText("编译被中断");}
                catch(ExecutionException e){status.setForeground(Ui.RED);status.setText(Ui.exceptionMessage(e));}
                finally{refreshButtons();}
            }
        };compileWorker.execute();refreshButtons();
    }
    private void showCompilation(){
        List<Object[]> tokenRows=new ArrayList<>();for(int i=0;i<result.tokens().size();i++){Token t=result.tokens().get(i);tokenRows.add(new Object[]{i,t.kind(),t.text(),t.span()});}Ui.table(tokens,new String[]{"序号","类别","词素","行:列"},tokenRows);
        if(result.ast()!=null){ast.setModel(new DefaultTreeModel(tree(result.ast())));for(int i=0;i<Math.min(8,ast.getRowCount());i++)ast.expandRow(i);}else ast.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("词法未通过，未执行语法分析")));
        List<Object[]> symbolRows=new ArrayList<>();StringBuilder attr=new StringBuilder();
        if(result.semantic()!=null){for(var s:result.semantic().symbols())symbolRows.add(new Object[]{s.operand(),s.name(),s.kind(),s.type(),s.scope(),s.slot(),s.span()});formatAttributes(result.ast(),attr,0);}
        Ui.table(symbols,new String[]{"编号","名字","类别","类型","作用域","局部槽","声明位置"},symbolRows);attributes.setText(attr.toString());attributes.setCaretPosition(0);
        rawIr.setText(result.ir()==null?"语义检查通过后生成四元式":Ir.format(result.ir()));optimizedIr.setText(result.optimizedIr()==null?"":Ir.format(result.optimizedIr()));rawIr.setCaretPosition(0);optimizedIr.setCaretPosition(0);
        List<InstructionRow> byteRows=new ArrayList<>();List<Object[]> tableRows=new ArrayList<>();
        if(result.bytecode()!=null)for(var f:result.bytecode().functions().values())for(int i=0;i<f.code().size();i++){var ins=f.code().get(i);byteRows.add(new InstructionRow(f.name(),i,ins));tableRows.add(new Object[]{f.name(),i,ins.op(),ins.argument(),ins.span()});}
        instructionRows=List.copyOf(byteRows);Ui.table(instructions,new String[]{"函数","PC","指令","参数","源码位置"},tableRows);parseTrace.setText(String.join("\n",result.parseTrace()));parseTrace.setCaretPosition(0);
        errors.clear();result.diagnostics().forEach(errors::addElement);output.setText("");frames.setText("点击单步或运行，查看调用帧、局部变量和操作数栈。");
        status.setForeground(result.success()?Ui.TEAL:Ui.RED);status.setText(result.success()?"编译成功 · "+result.tokens().size()+" 个 Token · "+instructionRows.size()+" 条目标指令 · 可运行或单步":"编译失败 · "+errors.size()+" 条诊断 · 已保留前序阶段结果");
        pipeline.setText("词法 "+(!result.tokens().isEmpty()?"已扫描":"—")+"  →  语法 "+(result.ast()!=null?"已分析":"—")+"  →  语义 "+(result.semantic()!=null?"已分析":"—")+"  →  四元式 "+(result.ir()!=null?"完成":"—")+"  →  优化 "+(result.optimizedIr()!=null?"完成":"—")+"  →  字节码 "+(result.bytecode()!=null?"完成":"—"));
        console.setSelectedIndex(result.success()?0:1);
    }
    private DefaultMutableTreeNode tree(Syntax.Node n){
        String label=Syntax.label(n)+" @"+n.span();if(result.semantic()!=null){var a=result.semantic().attributes().get(n);if(a!=null)label+=" : "+a.type();}
        DefaultMutableTreeNode t=new DefaultMutableTreeNode(new TreeEntry(n,label));Syntax.children(n).forEach(child->t.add(tree(child)));return t;
    }
    private void formatAttributes(Syntax.Node n,StringBuilder b,int depth){
        var a=result.semantic().attributes().get(n);b.append("  ".repeat(Math.min(depth,20))).append(Syntax.label(n)).append(" @").append(n.span());
        if(a!=null){b.append("  type=").append(a.type()).append(" env(scope)=").append(a.scope());if(a.constant()!=null)b.append(" const=").append(a.constant());if(a.binding()!=null)b.append(" binding=").append(a.binding().operand());}
        if(result.ir()!=null){var r=result.ir().ranges().get(n);if(r!=null)b.append(" IR=").append(r.function()).append(':').append(r.first()).append("..").append(r.last()).append(r.last()<r.first()?"（无独立指令）":"");}
        b.append('\n');Syntax.children(n).forEach(child->formatAttributes(child,b,depth+1));
    }
    private void execute(boolean single){
        if(!valid()||!result.success()||busy())return;
        try{
            if(vm==null||Set.of(VirtualMachine.State.HALTED,VirtualMachine.State.ERROR,VirtualMachine.State.STOPPED).contains(vm.state())){
                List<Integer> values=new ArrayList<>();if(!input.getText().isBlank())for(String s:input.getText().strip().split("[\\s,]+"))values.add(Integer.parseInt(s));
                vm=new VirtualMachine(result.bytecode(),values,new VirtualMachine.Limits(((Number)maxInstructions.getValue()).longValue(),((Number)maxDepth.getValue()).intValue()));
            }
            if(single){showSnapshot(vm.step());refreshButtons();return;}
            VirtualMachine target=vm;status.setText("目标程序运行中…");console.setSelectedIndex(0);
            runWorker=new SwingWorker<>(){
                @Override protected VirtualMachine.Snapshot doInBackground(){return target.run();}
                @Override protected void done(){try{var s=get();if(vm==target)showSnapshot(s);}catch(CancellationException e){status.setText("运行已取消");}catch(InterruptedException e){Thread.currentThread().interrupt();status.setText("运行被中断");}catch(ExecutionException e){status.setForeground(Ui.RED);status.setText(Ui.exceptionMessage(e));}finally{refreshButtons();}}
            };runWorker.execute();refreshButtons();
        }catch(NumberFormatException e){Ui.failure(this,new IllegalArgumentException("输入区仅接受 32 位整数，以空格或逗号分隔"));}
    }
    private void showSnapshot(VirtualMachine.Snapshot s){
        output.setText(s.output());StringBuilder b=new StringBuilder("状态="+s.state()+"  累计指令="+s.steps()+"  退出值="+s.exitValue()+"\n\n");
        for(var f:s.frames())b.append("函数 ").append(f.function()).append("  下一 PC=").append(f.pc()).append("\n操作数栈 ").append(f.stack()).append("\n局部变量 ").append(f.locals()).append("\n\n");frames.setText(b.toString());frames.setCaretPosition(0);
        if(s.error()!=null){errors.addElement(s.error());console.setSelectedIndex(1);status.setForeground(Ui.RED);status.setText(s.error().toString());}
        else{status.setForeground(Ui.TEAL);status.setText("运行状态 "+s.state()+" · 累计 "+s.steps()+" 条指令"+(s.state()==VirtualMachine.State.HALTED?" · main 返回 "+s.exitValue():""));}
        if(s.position()!=null)highlight(s.position());
        if(!s.frames().isEmpty()){var f=s.frames().getFirst();for(int i=0;i<instructionRows.size();i++)if(instructionRows.get(i).function.equals(f.function())&&instructionRows.get(i).pc==f.pc()){instructions.getSelectionModel().setSelectionInterval(i,i);instructions.scrollRectToVisible(instructions.getCellRect(i,0,true));break;}}
    }
    private void highlight(Span span){int start=Math.min(editor.getDocument().getLength(),span.start()),end=Math.min(editor.getDocument().getLength(),Math.max(start,span.end()));editor.select(start,end);try{Rectangle r=editor.modelToView2D(start).getBounds();editor.scrollRectToVisible(r);}catch(javax.swing.text.BadLocationException e){throw new IllegalStateException("源码位置失效",e);}}
    public void setSource(String text,Path file){loading=true;currentFile=file;savedSource=text;editor.setText(text);editor.setCaretPosition(0);loading=false;fileLabel.setText(file==null?"未命名.mc":file.toString());status.setText("已加载源码，点击编译开始");}
    public void openPath(Path path){try{if(Files.size(path)>4_000_000)throw new IllegalArgumentException("源文件超过 4 MB 限制");setSource(Files.readString(path,StandardCharsets.UTF_8),path);}catch(Exception e){Ui.failure(this,e);}}
    public boolean isModified(){return !editor.getText().equals(savedSource);}
    public boolean confirmDiscard(){if(!isModified())return true;int choice=JOptionPane.showConfirmDialog(this,"源码尚未保存，是否先保存？","保存源码",JOptionPane.YES_NO_CANCEL_OPTION);if(choice==JOptionPane.CANCEL_OPTION||choice==JOptionPane.CLOSED_OPTION)return false;return choice==JOptionPane.NO_OPTION||save();}
    private void open(){if(!confirmDiscard())return;JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("打开 MiniC 源文件");if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)openPath(chooser.getSelectedFile().toPath());}
    private boolean save(){
        Path path=currentFile;if(path==null){JFileChooser c=new JFileChooser();c.setSelectedFile(new java.io.File("program.mc"));if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return false;path=c.getSelectedFile().toPath();if(!overwrite(path))return false;}
        try{Files.writeString(path,editor.getText(),StandardCharsets.UTF_8);currentFile=path;savedSource=editor.getText();fileLabel.setText(path.toString());status.setText("已保存 "+path);return true;}catch(Exception e){Ui.failure(this,e);return false;}
    }
    private boolean overwrite(Path p){return !Files.exists(p)||JOptionPane.showConfirmDialog(this,"文件已存在，是否覆盖？\n"+p,"覆盖文件",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
    private void export(){
        if(!valid())return;JFileChooser c=new JFileChooser();c.setSelectedFile(new java.io.File("compilation.txt"));if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;Path p=c.getSelectedFile().toPath();if(!overwrite(p))return;
        String text="MiniC Lab 编译结果\n\n源代码\n"+result.source()+"\n\nToken\n"+String.join("\n",result.tokens().stream().map(Object::toString).toList())+"\n\n属性与 AST 对应\n"+attributes.getText()+"\n四元式（优化前）\n"+rawIr.getText()+"\n四元式（优化后）\n"+optimizedIr.getText()+"\n目标指令\n"+(result.bytecode()==null?"":Bytecode.format(result.bytecode()))+"\n诊断\n"+String.join("\n",result.diagnostics().stream().map(Object::toString).toList());
        try{Files.writeString(p,text,StandardCharsets.UTF_8);status.setText("已导出 "+p);}catch(Exception e){Ui.failure(this,e);}
    }
    public void close(){if(vm!=null)vm.stop();if(compileWorker!=null)compileWorker.cancel(true);if(runWorker!=null)runWorker.cancel(true);}
}
