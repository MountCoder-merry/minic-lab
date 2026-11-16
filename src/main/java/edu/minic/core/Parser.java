package edu.minic.core;

import java.util.*;
import edu.minic.core.Source.*;
import edu.minic.core.Syntax.*;

public final class Parser {
    private final List<Token> tokens;
    private final List<Diagnostic> diagnostics;
    private final List<String> trace = new ArrayList<>();
    private int pos, depth;
    private static final class ParseError extends RuntimeException {}
    public Parser(List<Token> tokens, List<Diagnostic> diagnostics) { this.tokens = tokens; this.diagnostics = diagnostics; }
    public List<String> trace() { return List.copyOf(trace); }
    private Token peek() { return tokens.get(pos); }
    private Token previous() { return tokens.get(Math.max(0, pos - 1)); }
    private boolean at(String kind) { return peek().kind().equals(kind); }
    private Token take() { Token t = peek(); if (!at("EOF")) pos++; return t; }
    private boolean match(String kind) { if (at(kind)) { take(); return true; } return false; }
    private Token expect(String kind) {
        if (at(kind)) return take();
        fail("需要 '" + kind + "'，实际为 '" + (at("EOF") ? "文件结束" : peek().text()) + "'"); return peek();
    }
    private void fail(String message) {
        if (diagnostics.size() < 100) diagnostics.add(new Diagnostic("语法", peek().span(), message));
        throw new ParseError();
    }
    private void enter(String rule) {
        if (++depth > 200) fail("语法嵌套超过 200 层，请简化程序");
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        trace.add(peek().span() + "  " + "  ".repeat(Math.min(depth, 20)) + rule);
    }
    public Program parse() {
        List<Function> functions = new ArrayList<>();
        Span start = peek().span();
        while (!at("EOF")) {
            int before = pos;
            try { functions.add(function()); }
            catch (ParseError e) {
                depth = 0;
                if (pos == before) take();
                while (!at("EOF") && !(isType(peek()) && pos + 2 < tokens.size() && tokens.get(pos + 1).kind().equals("IDENT") && tokens.get(pos + 2).kind().equals("("))) take();
            }
        }
        if (functions.isEmpty() && diagnostics.isEmpty()) diagnostics.add(new Diagnostic("语法", start, "程序至少需要一个函数"));
        return new Program(List.copyOf(functions), Span.join(start, peek().span()));
    }
    private static boolean isType(Token t) { return Set.of("int", "bool", "void").contains(t.kind()); }
    private Type type() {
        return switch (take().kind()) {
            case "int" -> Type.INT; case "bool" -> Type.BOOL; case "void" -> Type.VOID;
            default -> { fail("需要类型 int、bool 或 void"); yield Type.ERROR; }
        };
    }
    private Function function() {
        enter("function → type id ( parameters ) block");
        Span start = peek().span();
        if (!isType(peek())) fail("顶层仅允许函数定义，以 int、bool 或 void 开头");
        Type type = type(); String name = expect("IDENT").text(); expect("(");
        List<Param> params = new ArrayList<>();
        if (!at(")")) do {
            Span p = peek().span();
            if (!isType(peek())) fail("参数需要类型");
            Type pt = type(); Token id = expect("IDENT"); params.add(new Param(pt, id.text(), Span.join(p, id.span())));
        } while (match(","));
        expect(")"); Block body = block(); depth--;
        return new Function(type, name, List.copyOf(params), body, Span.join(start, body.span()));
    }
    private Block block() {
        enter("block → { statement* }"); Span start = expect("{").span();
        List<Stmt> statements = new ArrayList<>();
        while (!at("}") && !at("EOF")) {
            int before = pos, savedDepth = depth;
            try { statements.add(statement()); }
            catch (ParseError e) {
                depth = savedDepth;
                while (!at("EOF") && !at("}") && !at(";")) take();
                match(";");
                if (pos == before && !at("}") && !at("EOF")) take();
            }
        }
        Span end = expect("}").span(); depth--;
        return new Block(List.copyOf(statements), Span.join(start, end));
    }
    private Stmt statement() {
        enter("statement (" + peek().kind() + ")");
        Span start = peek().span(); Stmt result;
        if (at("{")) result = block();
        else if (isType(peek())) {
            Type t = type(); String name = expect("IDENT").text(); Expr init = match("=") ? expression() : null;
            result = new VarDecl(t, name, init, Span.join(start, expect(";").span()));
        } else if (match("if")) {
            expect("("); Expr c = expression(); expect(")"); Block yes = block(); Block no = match("else") ? block() : null;
            result = new If(c, yes, no, Span.join(start, no == null ? yes.span() : no.span()));
        } else if (match("while")) {
            expect("("); Expr c = expression(); expect(")"); Block b = block(); result = new While(c, b, Span.join(start, b.span()));
        } else if (match("break")) result = new Break(Span.join(start, expect(";").span()));
        else if (match("continue")) result = new Continue(Span.join(start, expect(";").span()));
        else if (match("return")) {
            Expr value = at(";") ? null : expression(); result = new Return(value, Span.join(start, expect(";").span()));
        } else if (at("IDENT") && tokens.get(pos + 1).kind().equals("=")) {
            String name = take().text(); take(); Expr value = expression(); result = new Assign(name, value, Span.join(start, expect(";").span()));
        } else {
            Expr e = expression();
            if (!(e instanceof Call)) fail("表达式语句仅允许函数调用；赋值请使用 id = 表达式;");
            result = new ExprStmt(e, Span.join(start, expect(";").span()));
        }
        depth--; return result;
    }
    private Expr expression() { return binary(0); }
    private static final List<Set<String>> OPS = List.of(Set.of("||"), Set.of("&&"), Set.of("==", "!="), Set.of("<", "<=", ">", ">="), Set.of("+", "-"), Set.of("*", "/", "%"));
    private Expr binary(int level) {
        if (level == OPS.size()) return unary();
        Expr left = binary(level + 1);
        while (OPS.get(level).contains(peek().kind())) {
            String op = take().kind(); Expr right = binary(level + 1);
            left = new Binary(op, left, right, Span.join(left.span(), right.span()));
        }
        return left;
    }
    private Expr unary() {
        enter("expression / unary"); Expr result;
        if (Set.of("!", "-", "+").contains(peek().kind())) {
            Token op = take(); Expr e = unary(); result = new Unary(op.kind(), e, Span.join(op.span(), e.span()));
        } else if (match("(")) { result = expression(); expect(")"); }
        else if (at("NUMBER")) { Token t = take(); result = new IntLit(Long.parseLong(t.text()), t.span()); }
        else if (at("true") || at("false")) { Token t = take(); result = new BoolLit(t.kind().equals("true"), t.span()); }
        else if (at("IDENT")) {
            Token id = take();
            if (match("(")) {
                List<Expr> args = new ArrayList<>();
                if (!at(")")) do { args.add(expression()); } while (match(","));
                result = new Call(id.text(), List.copyOf(args), Span.join(id.span(), expect(")").span()));
            } else result = new Name(id.text(), id.span());
        } else { fail("需要表达式，实际为 '" + peek().text() + "'"); return null; }
        depth--; return result;
    }
}
