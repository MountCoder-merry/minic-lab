// 无输入；输出 9、5、false、true、14。read 和除零都不会执行。
int main() {
    int x = 5;
    {
        int x = 9;
        write(x);
    }
    write(x);
    write(false && read() > 0);
    write(true || 1 / 0 > 0);
    write(2 + 3 * 4);
    return 0;
}
