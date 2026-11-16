// 输入 100，输出 5050。单步可以观察 i、total 和跳转指令。
int main() {
    int n = read();
    int i = 1;
    int total = 0;
    while (i <= n) {
        total = total + i;
        i = i + 1;
    }
    write(total);
    return 0;
}
