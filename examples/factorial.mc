// 输入 5，输出 120。展示函数参数、递归、分支与返回值。
int factorial(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

int main() {
    int n = read();
    write(factorial(n));
    return 0;
}
