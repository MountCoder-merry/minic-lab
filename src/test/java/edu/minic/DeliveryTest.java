package edu.minic;

import edu.minic.core.*;
import edu.minic.lab.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryTest {
    @ParameterizedTest
    @CsvSource({"factorial.mc,5,120","fibonacci.mc,10,55","gcd.mc,48 18,6","sum.mc,100,5050","nested-loops.mc,'',6","mutual-recursion.mc,'',true/false","scope-short-circuit.mc,'',9/5/false/true/14"})
    void bundledPrograms(String file,String input,String expected)throws Exception{
        String disk=Files.readString(Path.of("examples",file));assertEquals(disk,Examples.resource("/examples/"+file));
        List<Integer> values=input.isBlank()?List.of():Arrays.stream(input.split(" ")).map(Integer::parseInt).toList();
        for(boolean optimize:List.of(false,true)){
            var r=new CompilerService().compile(disk,new CompilerService.Options(optimize));assertTrue(r.success(),r.diagnostics().toString());
            var s=new VirtualMachine(r.bytecode(),values,VirtualMachine.Limits.defaults()).run();assertEquals(VirtualMachine.State.HALTED,s.state());assertEquals(expected.replace('/', '\n')+"\n",s.output());assertEquals(0,s.exitValue());
        }
    }
    @Test void invalidExampleAndHelpResources(){
        var r=new CompilerService().compile(Examples.resource("/examples/errors.mc"),CompilerService.Options.defaults());assertFalse(r.success());assertEquals(6,r.diagnostics().size());
        for(String file:List.of("SPEC.md","使用手册.md","考点对照表.md","答辩与源码导读.md","课程设计报告.md"))assertFalse(Examples.resource("/docs/"+file).isBlank());
    }
    @Test void optimizerPreservesControlAndEffects(){
        String src="int f(int x){write(x);return x+1;}int main(){int i=0;while(i<5){write(2+3+i);i=f(i);}bool a=false;write(a || f(8)>0);write(a && f(9)>0);return 0;}";
        String expected="5\n0\n6\n1\n7\n2\n8\n3\n9\n4\n8\ntrue\nfalse\n";
        for(boolean optimize:List.of(false,true)){var r=new CompilerService().compile(src,new CompilerService.Options(optimize));assertTrue(r.success(),r.diagnostics().toString());assertEquals(expected,new VirtualMachine(r.bytecode(),List.of(),VirtualMachine.Limits.defaults()).run().output());}
    }
    @Test void conservativeFlowAndNameChecks(){
        for(String body:List.of("int x;while(false){x=1;}write(x);return 0;","int x=x;return 0;","return true;","continue;return 0;","int write=1;write(3);return 0;")){
            var r=new CompilerService().compile("int main(){"+body+"}",CompilerService.Options.defaults());assertFalse(r.success(),body);
        }
        String valid="int f(bool p){int v;if(p){return 1;}else{v=2;}return v;}int main(){write(f(false));return 0;}";
        var r=new CompilerService().compile(valid,CompilerService.Options.defaults());assertTrue(r.success(),r.diagnostics().toString());assertEquals("2\n",new VirtualMachine(r.bytecode(),List.of(),VirtualMachine.Limits.defaults()).run().output());
    }
    @Test void depthAndConstructionLimitsAreDiagnostics(){
        var r=new CompilerService().compile("int main(){write("+"1+".repeat(400)+"1);return 0;}",CompilerService.Options.defaults());assertFalse(r.success());assertTrue(r.diagnostics().getFirst().message().contains("200 层"));
        assertThrows(IllegalArgumentException.class,()->Automata.build("a".repeat(1001)));
        assertThrows(IllegalArgumentException.class,()->Grammar.parse("S -> "+"a ".repeat(101)));
        assertThrows(IllegalArgumentException.class,()->Grammar.parse("S -> #"));
    }
    @Test void cancelIsCooperative(){
        Thread.currentThread().interrupt();try{assertThrows(CancellationException.class,()->Automata.build("ab"));assertThrows(CancellationException.class,()->Lr.build(Grammar.parse("S -> a"),Lr.Mode.LR1));}finally{Thread.interrupted();}
    }
    @Test void pauseResumeAndStopAcrossThreads()throws Exception{
        var r=new CompilerService().compile("int main(){while(true){}return 0;}",CompilerService.Options.defaults());assertTrue(r.success());
        var vm=new VirtualMachine(r.bytecode(),List.of(),new VirtualMachine.Limits(100_000_000,1024));
        try(var pool=Executors.newSingleThreadExecutor()){
            Future<VirtualMachine.Snapshot> first=pool.submit(vm::run);long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(vm.state()!=VirtualMachine.State.RUNNING&&System.nanoTime()<until)Thread.onSpinWait();vm.pause();
            var paused=first.get(3,TimeUnit.SECONDS);assertEquals(VirtualMachine.State.PAUSED,paused.state());long n=paused.steps();assertEquals(n+1,vm.step().steps());
            Future<VirtualMachine.Snapshot> resumed=pool.submit(vm::run);until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(vm.state()!=VirtualMachine.State.RUNNING&&System.nanoTime()<until)Thread.onSpinWait();vm.stop();assertEquals(VirtualMachine.State.STOPPED,resumed.get(3,TimeUnit.SECONDS).state());
        }
    }
}
