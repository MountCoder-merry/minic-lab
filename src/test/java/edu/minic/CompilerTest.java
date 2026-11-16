package edu.minic;

import edu.minic.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {
    private CompilerService.CompilationResult compile(String source, boolean optimize) {
        var r = new CompilerService().compile(source, new CompilerService.Options(optimize));
        assertTrue(r.success(), () -> r.diagnostics().toString()); return r;
    }
    private VirtualMachine.Snapshot run(String source, boolean optimize, Integer... input) {
        return new VirtualMachine(compile(source, optimize).bytecode(), List.of(input), VirtualMachine.Limits.defaults()).run();
    }
    private void output(String source, String expected, Integer... input) {
        for (boolean opt : List.of(false,true)) {
            var s = run(source,opt,input); assertEquals(VirtualMachine.State.HALTED,s.state(), () -> String.valueOf(s.error())); assertEquals(expected,s.output());
        }
    }
    @Test void arithmeticAndLimits() {
        output("int main(){write(2+3*4);write((2+3)*4);write(-2147483648);write(2147483647+1);write(-7/3);return 0;}", "14\n20\n-2147483648\n-2147483648\n-2\n");
    }
    @Test void recursionAndArgumentOrder() {
        output("int fact(int n){if(n<=1){return 1;}return n*fact(n-1);} int sub(int a,int b){return a-b;} int main(){write(fact(5));write(sub(read(),sub(read(),read())));return 0;}","120\n9\n",10,3,2);
    }
    @Test void mutualRecursion() {
        output("bool even(int n){if(n==0){return true;}return odd(n-1);} bool odd(int n){if(n==0){return false;}return even(n-1);}int main(){write(even(10));write(odd(10));return 0;}","true\nfalse\n");
    }
    @Test void loopsScopesAndFlow() {
        output("int main(){int x=5;{int x=9;write(x);}write(x);int n=0;int s=0;while(n<10){n=n+1;if(n==3){continue;}if(n==7){break;}s=s+n;}write(s);int v;if(x>0){v=1;}else{return 0;}write(v);return 0;}","9\n5\n18\n1\n");
    }
    @Test void shortCircuitAndUnusedReturn() {
        output("int f(){return 5;}void w(){write(true);}int main(){f();w();write(false && read()>0);write(true || 1/0>0);return 0;}","true\nfalse\ntrue\n");
    }
    @Test void diagnostics() {
        Map<String,String> cases = new LinkedHashMap<>();
        cases.put("int main(){write(x);return 0;}","未声明");
        cases.put("int main(){int x;write(x);return 0;}","未确定赋值");
        cases.put("int main(){int x=1;int x=2;return 0;}","重复声明");
        cases.put("int main(){int x=true;return 0;}","类型不匹配");
        cases.put("int main(){if(1){return 0;}return 1;}","类型不匹配");
        cases.put("int f(int x){return x;}int main(){return f();}","参数数量");
        cases.put("int f(){int x=1;}int main(){return 0;}","未返回值");
        cases.put("int main(){break;return 0;}","循环内部");
        cases.put("int main(){int x;if(true){x=1;}write(x);return 0;}","未确定赋值");
        cases.put("int main(){write(2147483648);return 0;}","超出 int");
        cases.put("int main(){int x=12abc;return 0;}","非法数字");
        cases.put("int main(){return 0;} /*", "未闭合");
        cases.forEach((src, expected) -> { var r = new CompilerService().compile(src,CompilerService.Options.defaults()); assertFalse(r.success(),src); assertNull(r.bytecode()); assertTrue(r.diagnostics().stream().anyMatch(d -> d.message().contains(expected)),r.diagnostics().toString()); });
    }
    @Test void syntaxRecoveryAndLocations() {
        var r = new CompilerService().compile("int main(){\nint a = ;\nint b = ;\nreturn 0;}",CompilerService.Options.defaults());
        assertEquals(2,r.diagnostics().size()); assertEquals(2,r.diagnostics().getFirst().span().line()); assertNotNull(r.ast());
        for (String newline : List.of("\n", "\r\n", "\r")) {
            var locations = new CompilerService().compile("// 注释" + newline + "int main(){" + newline + "write(missing);return 0;}", CompilerService.Options.defaults());
            assertEquals(3, locations.diagnostics().getFirst().span().line());
            assertEquals(7, locations.diagnostics().getFirst().span().column());
        }
    }
    @Test void runtimeErrorsKeepOrderAndPosition() {
        for (boolean opt : List.of(false,true)) {
            var s = run("int main(){write(7);\nint x=1/0;write(x);return 0;}",opt);
            assertEquals(VirtualMachine.State.ERROR,s.state()); assertEquals("7\n",s.output()); assertEquals(2,s.error().span().line());
            assertTrue(run("int main(){write(read());return 0;}",opt).error().message().contains("输入不足"));
        }
    }
    @Test void budgetsStepAndStop() {
        var r = compile("int main(){while(true){}return 0;}",true);
        var vm = new VirtualMachine(r.bytecode(),List.of(),new VirtualMachine.Limits(30,10));
        assertEquals(1,vm.step().steps()); assertEquals(VirtualMachine.State.PAUSED,vm.state());
        assertEquals(VirtualMachine.State.ERROR,vm.run().state()); assertEquals(30,vm.snapshot().steps());
        var stopped = new VirtualMachine(r.bytecode(),List.of(),VirtualMachine.Limits.defaults()); stopped.stop(); assertEquals(VirtualMachine.State.STOPPED,stopped.step().state());
        var rec = compile("int f(){return f();}int main(){return f();}",true);
        var s = new VirtualMachine(rec.bytecode(),List.of(),new VirtualMachine.Limits(1000,8)).run(); assertTrue(s.error().message().contains("调用深度"));
    }
}
