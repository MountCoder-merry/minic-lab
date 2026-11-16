package edu.minic.core;

import java.util.*;
import edu.minic.core.Source.*;

/** 手写确定状态扫描器；Token 使用 UTF-16 偏移与一基行列。 */
public final class Lexer {
    private static final Set<String> KEYWORDS = Set.of("int", "bool", "void", "if", "else", "while", "break", "continue", "return", "true", "false");
    private static final Set<String> DOUBLE = Set.of("==", "!=", "<=", ">=", "&&", "||");
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private final List<Diagnostic> diagnostics;
    private int pos, line = 1, col = 1;
    public Lexer(String source, List<Diagnostic> diagnostics) { this.source = source; this.diagnostics = diagnostics; }
    private char peek() { return pos < source.length() ? source.charAt(pos) : '\0'; }
    private char next() {
        char c = source.charAt(pos++);
        if (c == '\r') { line++; col = 1; }
        else if (c == '\n') { if (pos < 2 || source.charAt(pos - 2) != '\r') line++; col = 1; }
        else col++;
        return c;
    }
    private Span span(int start, int ln, int column) { return new Span(start, pos, ln, column); }
    private static boolean letter(char c) { return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_'; }
    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
    private void error(Span s, String message) { if (diagnostics.size() < 100) diagnostics.add(new Diagnostic("词法", s, message)); }
    public List<Token> scan() {
        while (pos < source.length()) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            int start = pos, ln = line, column = col;
            char c = next();
            if (Character.isWhitespace(c) || c == '\uFEFF' && start == 0) continue;
            if (c == '/' && peek() == '/') { while (pos < source.length() && peek() != '\n' && peek() != '\r') next(); continue; }
            if (c == '/' && peek() == '*') {
                next(); boolean closed = false;
                while (pos < source.length()) {
                    if (next() == '*' && peek() == '/') { next(); closed = true; break; }
                }
                if (!closed) error(span(start, ln, column), "块注释未闭合，缺少 */");
                continue;
            }
            String kind;
            if (letter(c)) {
                while (letter(peek()) || digit(peek())) next();
                String text = source.substring(start, pos);
                kind = KEYWORDS.contains(text) ? text : "IDENT";
            } else if (digit(c)) {
                while (digit(peek())) next();
                if (letter(peek()) || peek() == '.') {
                    while (letter(peek()) || digit(peek()) || peek() == '.') next();
                    error(span(start, ln, column), "非法数字；仅支持十进制整数，数字后不能紧接标识符");
                }
                kind = "NUMBER";
                try {
                    long value = Long.parseLong(source.substring(start, pos));
                    if (value > 2147483648L) error(span(start, ln, column), "整数超出 32 位范围");
                } catch (NumberFormatException e) { error(span(start, ln, column), "非法整数或整数超出范围"); }
            } else {
                String two = "" + c + peek();
                if (DOUBLE.contains(two)) { next(); kind = two; }
                else if ("+-*/%<>=!(){};,".indexOf(c) >= 0) kind = String.valueOf(c);
                else { error(span(start, ln, column), "无法识别字符 '" + c + "'"); continue; }
            }
            tokens.add(new Token(kind, source.substring(start, pos), span(start, ln, column)));
        }
        tokens.add(new Token("EOF", "", new Span(pos, pos, line, col)));
        return List.copyOf(tokens);
    }
}
