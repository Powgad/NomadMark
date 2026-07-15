# libs 目录

此目录用于存放 Android AAR (Android Archive) 库文件。

## Supernote AAR 库

如果需要使用 Supernote 设备的原生功能（如手势识别、文件选择器等），请将以下 AAR 文件放入此目录：

- `SupernoteUiLib.aar` - Supernote UI 组件库
- `dialoglib.aar` - 对话框库
- `nativeEventlib.aar` - 原生事件库

## 使用方法

1. 将 AAR 文件复制到此目录
2. 在 `app/build.gradle` 中取消注释相应依赖：

```gradle
api(name: 'SupernoteUiLib', ext: 'aar') { transitive true }
api(name: 'dialoglib', ext: 'aar') { transitive true }
api(name: 'nativeEventlib', ext: 'aar') { transitive true }
```

3. 在 `proguard-rules.pro` 中取消注释相应的 Keep 规则

## 注意事项

- 确保 `build.gradle` 中已配置 `flatDir` repository
- AAR 文件不会被版本控制跟踪，请手动添加
