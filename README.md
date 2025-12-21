# RA2 Web On Phone 📱🎮

红色警戒2 手机版控制器 - 为移动设备优化的游戏控制界面

## 📖 项目简介

RA2WebOnPhone 是一个专为红色警戒2（Red Alert 2）游戏设计的Android应用，提供优化的手机触控和手柄控制体验。

## ✨ 主要功能

- 🕹️ **摇杆控制** - 自定义的虚拟摇杆界面，支持精确操作
- 🎮 **手柄支持** - 完整的游戏手柄映射和控制
- 🖱️ **鼠标映射** - 手柄摇杆到鼠标的智能映射
- ⌨️ **键盘快捷键** - 支持Alt+F等游戏快捷键模拟
- 📱 **移动优化** - 为手机屏幕优化的UI布局

## 🛠️ 技术栈

- **开发语言**: Kotlin
- **构建系统**: Gradle
- **最低Android版本**: API 21+
- **UI组件**: 自定义摇杆SVG界面

## 📂 项目结构

```
RA2WebOnPhone/
├── app/                        # 主应用模块
│   └── src/
│       └── main/
│           └── java/com/ra2/webonphone/
│               └── ui/
│                   └── GamepadMouseController.kt
├── joystick_demo.mp4           # 功能演示视频
├── build.gradle.kts            # 项目构建配置
└── README.md                   # 项目说明文档
```

## 🚀 快速开始

### 环境要求

- Android Studio Arctic Fox 或更高版本
- JDK 11 或更高版本
- Android SDK API 21+

### 构建步骤

1. 克隆项目
```bash
git clone https://github.com/fwz233-RE/RA2WebOnPhone.git
cd RA2WebOnPhone
```

2. 使用Android Studio打开项目

3. 等待Gradle同步完成

4. 连接Android设备或启动模拟器

5. 点击运行按钮或执行：
```bash
./gradlew installDebug
```

## 🎮 使用说明

### 游戏模式控制

- **L1按钮**: 长按模拟（500ms）
- **Alt+F**: 通过"游戏模式"选项触发
- **摇杆**: 支持延迟触摸清理机制

### 手柄映射

项目支持完整的手柄按钮映射，包括：
- 方向摇杆映射到鼠标移动
- 按钮映射到游戏快捷键
- R1/R2 特殊功能支持

### 🎬 功能演示视频

<div align="center">
  <video src="https://raw.githubusercontent.com/fwz233-RE/RA2WebOnPhone/main/joystick_demo.mp4" width="100%" controls></video>
</div>

> 完整的摇杆控制功能演示 - 展示游戏手柄映射、虚拟摇杆操作和游戏控制界面

## 📝 开发历史

该项目经过多次迭代改进，主要更新包括：
- 游戏模式L1点击优化
- Alt+F快捷键模拟
- 按钮映射修复
- 摇杆映射控制改进
- 侧边栏UI调整
- 权限管理跳转

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启Pull Request

## 📄 许可证

本项目采用开源许可证，详见 [LICENSE](LICENSE) 文件。

## 👨‍💻 作者

- GitHub: [@fwz233-RE](https://github.com/fwz233-RE)

## 🙏 致谢

感谢所有为这个项目做出贡献的开发者！

---

⭐ 如果这个项目对你有帮助，请给个Star！
