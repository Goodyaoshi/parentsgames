# 爸妈游戏厅

**专为七八十岁爸妈打造的中文休闲游戏合集** —— 大字体、大按钮、玩法简单、随玩随停。

本项目基于开源项目 [caioross/FatimaGames](https://github.com/caioross/FatimaGames)（Kotlin + Jetpack Compose）改造：全面简体中文化、按老年人习惯重设计界面与字号、新增中国象棋 / 五子棋 / 华容道 / 数独 / 翻牌配对等游戏、调整多款游戏玩法以降低操作门槛。感谢原作者的开源贡献。

## 游戏列表（10 款）

| 游戏 | 说明 |
| --- | --- |
| 中国象棋 | 人机对战（必不至于一面倒）+ 双人同屏，特大号汉字棋子 |
| 五子棋 | 人机对战 + 双人同屏，支持提示 / 悔棋 |
| 麻将连连看 | 默认 72 张小盘，可切 152 张大盘 |
| 纸牌 | 构造式必赢牌局，跟着亮光点即可，支持看电脑演示 |
| 扫雷 | 小盘大格（6×6 / 8×8 / 10×12），带提示与演示 |
| 数独 | 点格填数，错误标红，带提示与演示 |
| 华容道 | 多关卡，带悔棋 |
| 宝石消除 | 6×6 大格棋盘 |
| 翻牌配对 | 固定 4 列大图标 |
| 俄罗斯方块 / 青蛙过马路 / 颜色分类 / 拼图 | 场地与元素均按老年人大小优化 |

通用能力：首次进入与「玩法」按钮共用同一教程弹窗、进度自动保存、战绩统计、字体特大号、支持浅色 / 深色主题。

## 环境要求

- JDK 21
- Android SDK（compileSdk 35）
- Gradle 8.11.1（wrapper 已配置腾讯云镜像，依赖走阿里云 Maven 镜像）

## 构建

```bash
# 调试包（输出 app/build/outputs/apk/debug/app-debug.apk）
./gradlew assembleDebug

# 正式包（输出 app/build/outputs/apk/release/app-release.apk）
./gradlew assembleRelease
```

安装到设备：

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.goodyaoshi.parentsgames/.MainActivity
```

## 签名

正式包签名通过根目录 `keystore.properties`（不入库）注入：

```properties
storeFile=parentsgames.jks
storePassword=你的密码
keyAlias=parentsgames
keyPassword=你的密码
```

没有该文件时，release 自动回退 debug 签名（便于 CI / 试用）。可参考 `keystore.properties.example`。

## 版本

- **v0.0.1**（2026-10）：首个中文定制版。10 款游戏全量汉化 + 老年人适配 + 纸牌构造式必赢牌局 + 演示模式（纸牌 / 扫雷 / 数独）。
