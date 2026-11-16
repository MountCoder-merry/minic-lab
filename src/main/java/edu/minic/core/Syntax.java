package edu.minic.core;

import java.util.List;
import edu.minic.core.Source.*;

public final class Syntax {
    private Syntax() {}
    public interface Node { Span span(); }
    public interface Stmt extends Node {}
    public interface Expr extends Node {}
    public record Program(List<Function> functions, Span span) implements Node {}
    public record Function(Type type, String name, List<Param> params, Block body, Span span) implements Node {}
    public record Param(Type type, String name, Span span) implements Node {}
    public record Block(List<Stmt> statements, Span span) implements Stmt {}
    public record VarDecl(Type type, String name, Expr init, Span span) implements Stmt {}
    public record Assign(String name, Expr value, Span span) implements Stmt {}
    public record If(Expr condition, Block yes, Block no, Span span) implements Stmt {}
    public record While(Expr condition, Block body, Span span) implements Stmt {}
    public record Break(Span span) implements Stmt {}
    public record Continue(Span span) implements Stmt {}
    public record Return(Expr value, Span span) implements Stmt {}
    public record ExprStmt(Expr expr, Span span) implements Stmt {}
    public record IntLit(long value, Span span) implements Expr {}
    public record BoolLit(boolean value, Span span) implements Expr {}
    public record Name(String name, Span span) implements Expr {}
    public record Unary(String op, Expr expr, Span span) implements Expr {}
    public record Binary(String op, Expr left, Expr right, Span span) implements Expr {}
    public record Call(String name, List<Expr> args, Span span) implements Expr {}

    public static List<Node> children(Node n) {
        var result = new java.util.ArrayList<Node>();
        switch (n) {
            case Program x -> result.addAll(x.functions);
            case Function x -> { result.addAll(x.params); result.add(x.body); }
            case Block x -> result.addAll(x.statements);
            case VarDecl x -> { if (x.init != null) result.add(x.init); }
            case Assign x -> result.add(x.value);
            case If x -> { result.add(x.condition); result.add(x.yes); if (x.no != null) result.add(x.no); }
            case While x -> { result.add(x.condition); result.add(x.body); }
            case Return x -> { if (x.value != null) result.add(x.value); }
            case ExprStmt x -> result.add(x.expr);
            case Unary x -> result.add(x.expr);
            case Binary x -> { result.add(x.left); result.add(x.right); }
            case Call x -> result.addAll(x.args);
            default -> { }
        }
        return List.copyOf(result);
    }
    public static String label(Node n) {
        return switch (n) {
            case Function x -> "函数 " + x.type + " " + x.name;
            case Param x -> "参数 " + x.type + " " + x.name;
            case VarDecl x -> "声明 " + x.type + " " + x.name;
            case Assign x -> "赋值 " + x.name;
            case Name x -> "标识符 " + x.name;
            case IntLit x -> "整数 " + x.value;
            case BoolLit x -> "布尔 " + x.value;
            case Unary x -> "一元 " + x.op;
            case Binary x -> "二元 " + x.op;
            case Call x -> "调用 " + x.name;
            default -> n.getClass().getSimpleName();
        };
    }
}
