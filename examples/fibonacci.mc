// 输入 10，输出 55。输入建议 0..20，较大值可能触发指令上限。
int fib(int n) {
    if (n <= 1) {
        return n;
    }
    return fib(n - 1) + fib(n - 2);
}

int main() {
    int n = read();
    write(fib(n));
    return 0;
}
