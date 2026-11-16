package edu.minic;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Examples {
    public record Example(String title,String file,String input){
        @Override public String toString(){return title;}
        public String source(){return resource("/examples/"+file);}
    }
    public static final List<Example> ALL=List.of(
        new Example("01 · 阶乘与递归","factorial.mc","5"),new Example("02 · 斐波那契","fibonacci.mc","10"),
        new Example("03 · 最大公约数","gcd.mc","48 18"),new Example("04 · 累加与循环","sum.mc","100"),
        new Example("05 · 作用域与短路","scope-short-circuit.mc",""),new Example("06 · 嵌套循环与跳转","nested-loops.mc",""),
        new Example("07 · 相互递归","mutual-recursion.mc",""),new Example("08 · 静态错误诊断","errors.mc",""));
    public static String resource(String path){
        try(InputStream input=Examples.class.getResourceAsStream(path)){
            if(input==null)throw new IllegalStateException("缺少内置资源："+path);return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }catch(IOException e){throw new UncheckedIOException("无法读取资源 "+path,e);}
    }
    private Examples(){}
}
