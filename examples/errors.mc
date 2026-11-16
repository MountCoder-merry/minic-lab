// 故意包含静态语义错误；编译失败后不能运行。
int main() {
    int uninitialized;
    write(uninitialized);
    bool flag = 1;
    int x = 1;
    int x = 2;
    write(unknown);
    break;
    return true;
}
