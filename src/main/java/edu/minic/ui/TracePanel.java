package edu.minic.ui;

import javax.swing.*;
import java.awt.*;
import java.util.List;

final class TracePanel extends JPanel {
    private final JList<String> list=new JList<>();private final JTextArea detail=Ui.text(false);private final JLabel progress=new JLabel("尚无步骤");
    private final JButton previous,next,reset;
    TracePanel(){
        super(new BorderLayout(0,8));setBackground(Ui.BG);list.setFont(Ui.CODE);list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setFixedCellHeight(30);
        previous=Ui.button("上一步","tracePrevious",()->select(list.getSelectedIndex()-1));next=Ui.button("下一步","traceNext",()->select(list.getSelectedIndex()+1));reset=Ui.button("重置","traceReset",()->select(0));
        add(Ui.flow(reset,previous,next,progress),BorderLayout.NORTH);add(Ui.split(JSplitPane.VERTICAL_SPLIT,Ui.scroll(list),Ui.scroll(detail),0.75));
        list.addListSelectionListener(e->{if(!e.getValueIsAdjusting()){int i=list.getSelectedIndex();detail.setText(i<0?"":list.getModel().getElementAt(i));detail.setCaretPosition(0);update();}});detail.setLineWrap(true);detail.setWrapStyleWord(true);update();
    }
    void setSteps(List<String> steps){list.setListData(steps.toArray(String[]::new));select(0);update();}
    private void select(int i){if(i>=0&&i<list.getModel().getSize()){list.setSelectedIndex(i);list.ensureIndexIsVisible(i);}}
    private void update(){int i=list.getSelectedIndex(),n=list.getModel().getSize();progress.setText(n==0?"尚无步骤":"步骤 "+(i+1)+" / "+n);previous.setEnabled(i>0);next.setEnabled(i>=0&&i<n-1);reset.setEnabled(n>0);}
}
