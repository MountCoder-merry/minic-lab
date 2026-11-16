// 无输入，输出 6。break/continue 总是作用于最近一层循环。
int main() {
    int i = 0;
    int count = 0;
    while (i < 3) {
        i = i + 1;
        int j = 0;
        while (j < 5) {
            j = j + 1;
            if (j == 2) {
                continue;
            }
            if (j == 4) {
                break;
            }
            count = count + 1;
        }
    }
    write(count);
    return 0;
}
