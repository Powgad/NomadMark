#!/bin/bash
# 自动保存功能测试脚本

echo "================================"
echo "自动保存功能测试"
echo "================================"
echo ""

DEVICE_ID="SN078C10000377"
PACKAGE="com.editor.nomadmark"

# 检查设备连接
echo "1. 检查设备连接..."
adb devices | grep -q $DEVICE_ID
if [ $? -ne 0 ]; then
    echo "❌ 设备未连接"
    exit 1
fi
echo "✓ 设备已连接: $DEVICE_ID"
echo ""

# 清除应用数据
echo "2. 清除应用数据..."
adb shell am force-stop $PACKAGE
adb shell pm clear $PACKAGE >/dev/null 2>&1
echo "✓ 应用数据已清除"
echo ""

# 启动应用
echo "3. 启动应用..."
adb shell am start -n $PACKAGE/.MainActivity
sleep 3
echo "✓ 应用已启动"
echo ""

# 等待一段时间让用户输入内容
echo "4. 请在应用中输入测试内容..."
echo "   输入内容后等待 5 秒（自动保存延迟）"
read -p "按 Enter 继续..."
echo ""

# 强制停止应用（模拟崩溃）
echo "5. 模拟应用崩溃..."
adb shell am force-stop $PACKAGE
sleep 1
echo "✓ 应用已强制停止"
echo ""

# 重新启动应用
echo "6. 重新启动应用（应该显示恢复对话框）..."
adb shell am start -n $PACKAGE/.MainActivity
sleep 2
echo ""

# 检查日志
echo "7. 检查日志..."
adb logcat -d -s AutoSaveManager:* MarkdownEditorActivity:* | tail -20
echo ""

echo "================================"
echo "测试完成"
echo "================================"
echo ""
echo "预期结果："
echo "- 如果显示恢复对话框，说明自动保存功能正常"
echo "- 对话框应显示：发现未保存的内容"
echo ""
