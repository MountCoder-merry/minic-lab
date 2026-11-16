package edu.minic.core;

public final class Source {
    private Source() {}
    public record Span(int start, int end, int line, int column) {
        public static Span join(Span a, Span b) { return new Span(a.start, b.end, a.line, a.column); }
        @Override public String toString() { return line + ":" + column; }
    }
    public record Diagnostic(String phase, Span span, String message) {
        @Override public String toString() { return "[" + phase + "] " + span + "  " + message; }
    }
    public enum Type { INT, BOOL, VOID, ERROR }
    public record Token(String kind, String text, Span span) {
        @Override public String toString() { return "%4d:%-3d %-10s %s".formatted(span.line, span.column, kind, text); }
    }
}
