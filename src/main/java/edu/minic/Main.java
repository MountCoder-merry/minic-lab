package edu.minic;

import edu.minic.core.*;
import edu.minic.ui.MainFrame;
import javax.swing.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Main {
    public static void main(String[] args){
        if(args.length==0||args[0].equals("--open")){
            SwingUtilities.invokeLater(()->{try{MainFrame frame=new MainFrame();if(args.length>1)frame.compiler().openPath(Path.of(args[1]));frame.setVisible(true);}catch(Exception e){e.printStackTrace(System.err);JOptionPane.showMessageDialog(null,e.toString(),"MiniC Lab 启动失败",JOptionPane.ERROR_MESSAGE);}});return;
        }
        try{System.exit(cli(args));}catch(Exception e){System.err.println("操作失败："+e.getMessage());System.exit(1);}
    }
    private static int cli(String[] args)throws Exception{
        if(args[0].equals("--help")){System.out.println("MiniC Lab / Java 21\n无参数：桌面工作台\n--open 文件.mc：桌面打开源码\n--compile 文件.mc [--no-opt]：输出字节码\n--run 文件.mc [--input \"5 10\"] [--no-opt]：编译并执行");return 0;}
        if(args.length<2||!Set.of("--compile","--run").contains(args[0]))throw new IllegalArgumentException("使用 --help 查看用法");
        boolean optimize=true;List<Integer> input=new ArrayList<>();
        for(int i=2;i<args.length;i++){if(args[i].equals("--no-opt"))optimize=false;else if(args[i].equals("--input")&&i+1<args.length){String text=args[++i];if(!text.isBlank())for(String s:text.strip().split("[\\s,]+"))input.add(Integer.parseInt(s));}else throw new IllegalArgumentException("未知或不完整参数："+args[i]);}
        Path path=Path.of(args[1]);if(Files.size(path)>4_000_000)throw new IllegalArgumentException("源文件超过 4 MB");
        var result=new CompilerService().compile(Files.readString(path,StandardCharsets.UTF_8),new CompilerService.Options(optimize));
        if(!result.success()){result.diagnostics().forEach(System.err::println);return 2;}
        if(args[0].equals("--compile")){System.out.print(Bytecode.format(result.bytecode()));return 0;}
        var s=new VirtualMachine(result.bytecode(),input,VirtualMachine.Limits.defaults()).run();System.out.print(s.output());
        if(s.error()!=null){System.err.println(s.error());return 3;}
        System.err.println("[完成] main="+s.exitValue()+"，指令="+s.steps());return 0;
    }
    private Main(){}
}
