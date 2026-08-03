# SoundFont 音色包目录

此目录用于存放本地 MIDI 音色包，支持离线音频播放。

## 当前状态

- 目录结构已创建，但音色包文件未包含
- 应用会自动回退到在线音色源：https://paulrosen.github.io/midi-js-soundfonts/FluidR3_GM/

## 如何添加本地音色包

1. 下载 FluidR3_GM 音色包
2. 将所有 `.js` 和 `.json` 文件放置到此目录
3. 应用将优先使用本地音色，实现完全离线播放

## 音色包来源

FluidR3_GM 是一个开源的 GM (General MIDI) 音色包，由 Paul Rosen 制作并托管。

在线音色包位置：https://paulrosen.github.io/midi-js-soundfonts/FluidR3_GM/
