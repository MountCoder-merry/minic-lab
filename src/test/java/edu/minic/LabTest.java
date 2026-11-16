package edu.minic;

import edu.minic.lab.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LabTest {
    static final String LL="E -> T E'\nE' -> + T E' | ε\nT -> F T'\nT' -> * F T' | ε\nF -> ( E ) | id";
    static final String EXPR="E -> E + T | T\nT -> T * F | F\nF -> ( E ) | id";
    @Test void thompsonSubsetAndMinimalLanguage(){
        var r=Automata.build("(a|b)*abb");assertEquals(4,r.minimized().count());
        for(int size=0;size<=7;size++)for(int bits=0;bits<(1<<size);bits++){
            StringBuilder s=new StringBuilder();for(int i=0;i<size;i++)s.append((bits&(1<<i))==0?'a':'b');String text=s.toString();
            boolean expected=text.endsWith("abb");for(var m:List.of(r.nfa(),r.dfa(),r.minimized()))assertEquals(expected,Automata.recognize(m,text).accepted(),text);
        }
    }
    @Test void epsilonEmptyEscapeAndUnicode(){
        assertTrue(Automata.recognize(Automata.build("ε").minimized(),"").accepted());
        assertFalse(Automata.recognize(Automata.build("∅").minimized(),"").accepted());
        assertTrue(Automata.recognize(Automata.build("\\|·\\*·山").minimized(),"|*山").accepted());
        assertTrue(Automata.recognize(Automata.build("").nfa(),"").accepted());
        for(String bad:List.of("a|","(a","*a","a·","a)"))assertThrows(IllegalArgumentException.class,()->Automata.build(bad));
    }
    @Test void nullableFirstFollowSelectAndPredict(){
        var g=Grammar.parse(LL);var a=g.analyze();
        assertEquals(Set.of("E'","T'"),a.nullable());assertEquals(Set.of("(","id"),a.first().get("E"));assertEquals(Set.of(")","#"),a.follow().get("E"));assertTrue(a.conflicts().isEmpty());
        assertTrue(g.predict(a,"id + id * id").accepted());assertFalse(g.predict(a,"id + * id").accepted());
        var empty=Grammar.parse("S -> A B\nA -> ε\nB -> ε");assertTrue(empty.predict(empty.analyze(),"").accepted());assertFalse(empty.analyze().follow().get("A").contains("ε"));
    }
    @Test void conflictsAndTransformations(){
        var conflict=Grammar.parse("S -> a A | a B\nA -> c\nB -> d");assertFalse(conflict.analyze().conflicts().isEmpty());
        var factored=Grammar.parse(conflict.factor());assertTrue(factored.analyze().conflicts().isEmpty());assertTrue(factored.predict(factored.analyze(),"a c").accepted());
        var g=Grammar.parse("E -> E + T | T\nT -> id");var transformed=Grammar.parse(g.eliminateDirectLeftRecursion());assertTrue(transformed.analyze().conflicts().isEmpty());assertTrue(transformed.predict(transformed.analyze(),"id + id").accepted());
        assertTrue(Grammar.parse("A -> B a | c\nB -> A b | d").leftRecursion().stream().anyMatch(s->s.contains("间接")));
        assertThrows(IllegalArgumentException.class,()->Grammar.parse("A -> A a").eliminateDirectLeftRecursion());
    }
    @Test void lr0MatchesLectureExample(){
        var r=Lr.build(Grammar.parse("S -> a A c B e\nA -> b | A b\nB -> d"),Lr.Mode.LR0);
        assertEquals(10,r.states().size());assertTrue(r.conflicts().isEmpty());assertTrue(Lr.parse(r,"a b b c d e").accepted());assertFalse(Lr.parse(r,"a b c e").accepted());
    }
    @Test void expressionSlrAndCanonicalLr(){
        var g=Grammar.parse(EXPR);var lr0=Lr.build(g,Lr.Mode.LR0);assertFalse(lr0.conflicts().isEmpty());assertFalse(Lr.parse(lr0,"id").accepted());
        var slr=Lr.build(g,Lr.Mode.SLR1);assertEquals(12,slr.states().size());assertTrue(slr.conflicts().isEmpty());assertTrue(Lr.parse(slr,"id + id * id").accepted());
        var lr=Lr.build(g,Lr.Mode.LR1);assertEquals(22,lr.states().size());assertTrue(lr.conflicts().isEmpty());assertTrue(Lr.parse(lr,"( id + id ) * id").accepted());assertFalse(Lr.parse(lr,"id id").accepted());
    }
    @Test void canonicalDistinguishesSlrAndEpsilon(){
        var g=Grammar.parse("S -> L = R | R\nL -> * R | id\nR -> L");assertFalse(Lr.build(g,Lr.Mode.SLR1).conflicts().isEmpty());var lr=Lr.build(g,Lr.Mode.LR1);assertTrue(lr.conflicts().isEmpty());assertTrue(Lr.parse(lr,"* id = id").accepted());
        for(var mode:Lr.Mode.values()){var r=Lr.build(Grammar.parse("S -> A\nA -> ε"),mode);assertTrue(r.conflicts().isEmpty());assertTrue(Lr.parse(r,"").accepted());}
    }
}
