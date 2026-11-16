// 无输入，输出 true、false。函数声明顺序不影响调用。
bool even(int n) {
    if (n == 0) {
        return true;
    }
    return odd(n - 1);
}

bool odd(int n) {
    if (n == 0) {
        return false;
    }
    return even(n - 1);
}

int main() {
    write(even(10));
    write(odd(10));
    return 0;
}
