package edu.minic;

import edu.minic.ui.MainFrame;
import edu.minic.lab.Lr;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** 显式执行的真实桌面检查，不在普通无头 Maven 测试中弹出窗口。 */
public final class GuiSmoke {
    private static MainFrame frame;private static Robot robot;private static Path evidence;
    private static <T>T edt(Callable<T> task)throws Exception{FutureTask<T> future=new FutureTask<>(task);SwingUtilities.invokeAndWait(future);return future.get();}
    private static Component find(Component parent,String name){if(name.equals(parent.getName()))return parent;if(parent instanceof Container c)for(Component child:c.getComponents()){Component found=find(child,name);if(found!=null)return found;}return null;}
    private static <T>T named(String name,Class<T> type)throws Exception{return edt(()->type.cast(find(frame,name)));}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void waitFor(Callable<Boolean> predicate,String message)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<end){if(edt(predicate))return;Thread.sleep(80);}throw new AssertionError("等待超时："+message);}
    private static void click(String name)throws Exception{JButton c=named(name,JButton.class);require(c!=null,"组件不存在 "+name);edt(()->{require(c.isShowing()&&c.isEnabled(),"按钮未就绪 "+name);c.doClick();return null;});robot.waitForIdle();}
    private static String status(String name)throws Exception{return edt(()->((JLabel)find(frame,name)).getText());}
    private static void shot(String file)throws Exception{robot.waitForIdle();BufferedImage image=edt(()->{BufferedImage b=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=b.createGraphics();frame.printAll(g);g.dispose();return b;});ImageIO.write(image,"png",evidence.resolve(file).toFile());}
    private static JFileChooser chooser(){for(Window w:Window.getWindows())if(w.isShowing()){JFileChooser c=chooserIn(w);if(c!=null)return c;}return null;}
    private static JFileChooser chooserIn(Component c){if(c instanceof JFileChooser f)return f;if(c instanceof Container p)for(Component ch:p.getComponents()){JFileChooser f=chooserIn(ch);if(f!=null)return f;}return null;}
    private static void fileDialog(String button,Path path)throws Exception{
        SwingUtilities.invokeLater(()->((JButton)find(frame,button)).doClick());waitFor(()->chooser()!=null,"文件选择器");edt(()->{JFileChooser c=chooser();c.setSelectedFile(path.toFile());c.approveSelection();return null;});waitFor(()->chooser()==null,"文件选择器关闭");
    }
    public static void main(String[]args)throws Exception{
        if(GraphicsEnvironment.isHeadless())throw new IllegalStateException("需要可用桌面");
        evidence=Path.of(args.length==0?"target/gui-evidence":args[0]);Files.createDirectories(evidence);robot=new Robot();robot.setAutoDelay(80);
        try{
            frame=edt(()->{var f=new MainFrame();Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();f.setBounds(30,30,Math.min(1440,screen.width-60),Math.min(940,screen.height-80));f.setVisible(true);f.toFront();System.out.println("DISPLAY: "+screen+" / "+f.getGraphicsConfiguration().getDefaultTransform());return f;});
            shot("00-start.png");click("compile");waitFor(()->((JLabel)find(frame,"compilerStatus")).getText().startsWith("编译成功"),"默认编译");
            for(int i=0;i<6;i++)click("step");
            edt(()->{((JTabbedPane)find(frame,"compilationResults")).setSelectedIndex(7);return null;});shot("01-compiler-step.png");
            click("run");waitFor(()->((JTextArea)find(frame,"output")).getText().equals("120\n"),"阶乘输出");require(status("compilerStatus").contains("HALTED"),"未正常结束");
            shot("01b-compiler-output.png");String factorial=edt(()->((JTextArea)find(frame,"source")).getText());
            edt(()->{((JTextArea)find(frame,"source")).setText("int main(){while(true){}return 0;}");((JSpinner)find(frame,"maxInstructions")).setValue(100_000_000);return null;});click("compile");waitFor(()->((JLabel)find(frame,"compilerStatus")).getText().startsWith("编译成功"),"循环编译");click("run");Thread.sleep(60);click("pause");waitFor(()->((JLabel)find(frame,"compilerStatus")).getText().contains("PAUSED"),"暂停");click("run");Thread.sleep(40);click("stop");waitFor(()->((JLabel)find(frame,"compilerStatus")).getText().contains("STOPPED"),"停止");
            edt(()->{((JTextArea)find(frame,"source")).setText(factorial);((JSpinner)find(frame,"maxInstructions")).setValue(1_000_000);return null;});click("compile");waitFor(()->((JLabel)find(frame,"compilerStatus")).getText().startsWith("编译成功"),"恢复默认编译");
            Path roundtrip=Files.createTempDirectory(evidence,"roundtrip-");Path saved=roundtrip.resolve("中文源码.mc");String original=edt(()->((JTextArea)find(frame,"source")).getText());fileDialog("save",saved);require(Files.readString(saved).equals(original),"保存内容不一致");
            edt(()->{((JTextArea)find(frame,"source")).append("\n");return null;});require(!edt(()->((JButton)find(frame,"run")).isEnabled()),"修改后仍可运行旧结果");
            edt(()->{((JTextArea)find(frame,"source")).setText(original);return null;});
            Path other=roundtrip.resolve("打开测试.mc");String text="int main(){write(7);return 0;}";Files.writeString(other,text);fileDialog("open",other);require(edt(()->((JTextArea)find(frame,"source")).getText()).equals(text),"打开文件内容不一致");
            click("compile");waitFor(()->((JButton)find(frame,"export")).isEnabled(),"重新编译");Path export=roundtrip.resolve("编译结果.txt");fileDialog("export",export);require(Files.readString(export).contains("MiniC Stack Bytecode"),"导出缺少字节码");
            edt(()->{((JTextArea)find(frame,"source")).setText("int main(){\nwrite(missing);\nreturn 0;}");return null;});click("compile");waitFor(()->((JLabel)find(frame,"compilerStatus")).getText().startsWith("编译失败"),"错误诊断");
            edt(()->{((JList<?>)find(frame,"diagnostics")).setSelectedIndex(0);return null;});require(edt(()->((JTextArea)find(frame,"source")).getSelectedText()).contains("missing"),"诊断未定位源码");shot("02-diagnostics.png");
            JTabbedPane main=named("mainTabs",JTabbedPane.class);edt(()->{main.setSelectedIndex(1);return null;});click("buildAutomata");waitFor(()->((JLabel)find(frame,"automataStatus")).getText().contains("最小 DFA 4"),"自动机");shot("03-automata.png");
            edt(()->{main.setSelectedIndex(2);return null;});click("buildLl");waitFor(()->((JLabel)find(frame,"llStatus")).getText().contains("输入串被接受"),"LL(1)");shot("04-ll.png");
            edt(()->{Component ll=main.getComponentAt(2);((JButton)find(ll,"traceNext")).doClick();((JButton)find(ll,"traceReset")).doClick();return null;});
            edt(()->{main.setSelectedIndex(3);return null;});click("buildLr");waitFor(()->((JLabel)find(frame,"lrStatus")).getText().contains("12 个状态"),"SLR(1)");shot("05-lr.png");
            edt(()->{((JComboBox<?>)find(frame,"lrMode")).setSelectedItem(Lr.Mode.LR0);return null;});click("buildLr");waitFor(()->((JLabel)find(frame,"lrStatus")).getText().contains("处冲突"),"LR(0) 冲突");
            edt(()->{((JComboBox<?>)find(frame,"lrMode")).setSelectedItem(Lr.Mode.LR1);return null;});click("buildLr");waitFor(()->((JLabel)find(frame,"lrStatus")).getText().contains("22 个状态"),"LR(1)");
            edt(()->{main.setSelectedIndex(0);frame.setSize(1024,768);return null;});shot("06-compact.png");
            for(String name:new String[]{"compile","run","runtimeInput"}){Component c=named(name,Component.class);require(edt(c::isShowing),"小窗口控件不可见 "+name);}
            System.out.println("GUI_SMOKE_PASS: compile, step, run, pause, stop, UTF-8 save/open/export, stale result, diagnostics, automata, LL, LR, replay, compact layout");
        }catch(Throwable e){if(frame!=null){System.err.println("GUI status: "+status("compilerStatus"));shot("failure.png");}throw e;}
        finally{if(frame!=null)edt(()->{frame.compiler().close();frame.dispose();return null;});}
    }
}
