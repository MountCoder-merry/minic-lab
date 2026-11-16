package edu.minic.ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;

final class Ui {
    static final Color BG=new Color(0xF4F7FB),INK=new Color(0x17324D),BLUE=new Color(0x2864DC),TEAL=new Color(0x078477),RED=new Color(0xBE3455),LINE=new Color(0xDCE4EF);
    // 逻辑等宽字体保留 CJK 回退，物理 Consolas 会把中文注释显示成方块。
    static final Font BODY=new Font(Font.DIALOG,Font.PLAIN,14),CODE=new Font(Font.MONOSPACED,Font.PLAIN,15);
    static void theme(){
        UIManager.put("defaultFont",BODY);
        for(String key:List.of("Button.font","Label.font","TabbedPane.font","TextField.font","ComboBox.font","Table.font","TableHeader.font","Tree.font","OptionPane.messageFont","OptionPane.buttonFont"))UIManager.put(key,BODY);
        UIManager.put("Panel.background",BG);UIManager.put("TabbedPane.background",BG);UIManager.put("Table.rowHeight",28);UIManager.put("Tree.rowHeight",28);
    }
    static JPanel panel(LayoutManager layout){JPanel p=new JPanel(layout);p.setBackground(BG);return p;}
    static JPanel flow(Component...components){JPanel p=panel(new FlowLayout(FlowLayout.LEFT,8,6));for(var c:components)p.add(c);return p;}
    static JButton button(String text,String name,Runnable action){JButton b=new JButton(text);b.setName(name);b.setFocusPainted(true);b.setMargin(new Insets(7,12,7,12));b.addActionListener(e->action.run());return b;}
    static JTextArea text(boolean editable){JTextArea t=new JTextArea();t.setFont(CODE);t.setEditable(editable);t.setTabSize(4);t.setMargin(new Insets(12,12,12,12));t.setForeground(INK);t.setBackground(Color.WHITE);return t;}
    static JScrollPane scroll(Component c){JScrollPane s=new JScrollPane(c);s.setBorder(new LineBorder(LINE));return s;}
    static JPanel titled(String title,Component c){JPanel p=panel(new BorderLayout(0,7));JLabel l=new JLabel(title);l.setForeground(INK);l.setBorder(new EmptyBorder(5,3,0,0));p.add(l,BorderLayout.NORTH);p.add(c);return p;}
    static JSplitPane split(int direction,Component left,Component right,double weight){JSplitPane s=new JSplitPane(direction,left,right);s.setResizeWeight(weight);s.setDividerSize(7);s.setBorder(null);s.setContinuousLayout(true);return s;}
    static void table(JTable t,String[] headers,List<Object[]> rows){t.setModel(new DefaultTableModel(rows.toArray(Object[][]::new),headers){@Override public boolean isCellEditable(int r,int c){return false;}});t.setRowHeight(28);t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);for(int c=0;c<headers.length;c++)t.getColumnModel().getColumn(c).setPreferredWidth(c==0?70:150);t.getTableHeader().setReorderingAllowed(false);}
    static void watch(javax.swing.text.Document d,Runnable action){d.addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){action.run();}public void removeUpdate(javax.swing.event.DocumentEvent e){action.run();}public void changedUpdate(javax.swing.event.DocumentEvent e){}});}
    static void failure(Component owner,Throwable error){JOptionPane.showMessageDialog(owner,error.getMessage()==null?error.toString():error.getMessage(),"操作未完成",JOptionPane.ERROR_MESSAGE);}
    static String exceptionMessage(Throwable error){Throwable e=error instanceof ExecutionException&&error.getCause()!=null?error.getCause():error;return e.getMessage()==null?e.toString():e.getMessage();}
    static abstract class AsyncPanel extends JPanel {
        private SwingWorker<?,?> worker;private long revision;
        final JLabel status=new JLabel("准备就绪");final JButton cancel=button("取消","cancel",()->{if(worker!=null)worker.cancel(true);});
        AsyncPanel(){super(new BorderLayout(10,10));setBorder(new EmptyBorder(12,14,14,14));setBackground(BG);cancel.setEnabled(false);}
        void changed(){revision++;status.setText("输入已修改，请重新计算");status.setForeground(INK);}
        <T> void compute(Supplier<T> work,Consumer<T> accept,JButton...buttons){
            if(worker!=null&&!worker.isDone())return;long submitted=revision;for(JButton b:buttons)b.setEnabled(false);cancel.setEnabled(true);status.setText("计算中…");status.setForeground(INK);
            worker=new SwingWorker<T,Void>(){
                @Override protected T doInBackground(){return work.get();}
                @Override protected void done(){
                    for(JButton b:buttons)b.setEnabled(true);cancel.setEnabled(false);
                    try{T result=get();if(submitted==revision)accept.accept(result);else status.setText("输入已变化，本次结果已丢弃，请重新计算");}
                    catch(CancellationException e){status.setText("已取消");}
                    catch(InterruptedException e){Thread.currentThread().interrupt();status.setText("操作被中断");}
                    catch(ExecutionException e){status.setForeground(RED);status.setText(exceptionMessage(e));}
                }
            };worker.execute();
        }
        void close(){if(worker!=null)worker.cancel(true);}
    }
}
