// 输入 48 18，输出 6。展示循环与余数运算。
int gcd(int a, int b) {
    while (b != 0) {
        int remainder = a % b;
        a = b;
        b = remainder;
    }
    return a;
}

int main() {
    int a = read();
    int b = read();
    write(gcd(a, b));
    return 0;
}
