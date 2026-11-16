package edu.minic.ui;

import edu.minic.Examples;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

public final class MainFrame extends JFrame {
    private final CompilerPanel compiler;
    public MainFrame(){
        super("MiniC Lab · 编译原理实验工作台");Ui.theme();setName("minicLab");setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);setMinimumSize(new Dimension(980,700));
        Rectangle usable=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();setSize(Math.max(980,Math.min(1440,usable.width-40)),Math.max(700,Math.min(940,usable.height-40)));setLocation(usable.x+(usable.width-getWidth())/2,usable.y+(usable.height-getHeight())/2);
        JPanel root=Ui.panel(new BorderLayout(0,8));JPanel header=Ui.panel(new BorderLayout());header.setBorder(new EmptyBorder(18,22,10,22));
        JLabel brand=new JLabel("MiniC Lab");brand.setFont(new Font("Segoe UI",Font.BOLD,29));brand.setForeground(Ui.INK);header.add(brand,BorderLayout.WEST);
        JLabel caption=new JLabel("编译原理  /  课程设计与复习实验台");caption.setForeground(Ui.TEAL);header.add(caption,BorderLayout.EAST);root.add(header,BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();tabs.setName("mainTabs");compiler=new CompilerPanel();var automata=new LabPanels.AutomataPanel();var ll=new LabPanels.LlPanel();var lr=new LabPanels.LrPanel();
        tabs.addTab("编译工作台",compiler);tabs.addTab("自动机实验",automata);tabs.addTab("LL(1) 与文法",ll);tabs.addTab("LR 分析",lr);tabs.addTab("复习与答辩",guide());root.add(tabs);setContentPane(root);
        addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){if(!compiler.confirmDiscard())return;compiler.close();automata.close();ll.close();lr.close();dispose();}});
    }
    public CompilerPanel compiler(){return compiler;}
    private JPanel guide(){
        JPanel p=Ui.panel(new BorderLayout(10,10));p.setBorder(new EmptyBorder(14,16,14,16));JTextArea content=Ui.text(false);content.setLineWrap(true);content.setWrapStyleWord(true);content.setFont(Ui.BODY);
        String[] files={"使用手册.md","考点对照表.md","答辩与源码导读.md","课程设计报告.md","SPEC.md"};JComboBox<String> choose=new JComboBox<>(files);Runnable load=()->{try{content.setText(Examples.resource("/docs/"+choose.getSelectedItem()));content.setCaretPosition(0);}catch(RuntimeException e){content.setText(e.getMessage());}};
        choose.addActionListener(e->load.run());p.add(Ui.flow(new JLabel("选择材料"),choose),BorderLayout.NORTH);p.add(Ui.scroll(content));load.run();return p;
    }
}
