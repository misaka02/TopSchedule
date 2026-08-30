# TopSchedule (浮窗课表)

一款轻量、现代、高效的 Android 悬浮课表应用，专为高校教务系统（拓扑教务/标准教务系统）打造。

## ✨ 核心特性

1. **自定义教务网址 & 一键提取**
   - 支持手动输入任意高校教务系统登录网址，一键收藏/设为默认地址，下次启动自动直达。
   - 内置安全 Web 容器，在课表页面点击「一键智能抓取」即可自动解析多教师、单双周分段课表。
   - 支持粘贴离线 HTML 源码导入。

2. **即开即隐的悬浮课表面板**
   - **完全适配 Panels、边缘手势与快捷方式**：以半透明顶层浮窗形式呼出，不打扰当前前台应用。
   - **点击卡片外任意空白区域瞬间自动退隐**，即看即走。
   - 提供**下拉控制中心磁贴 (Quick Settings Tile)** 与 **桌面微件 (Widget)**。

3. **极致轻量 & 纯净安全**
   - 经过 R8/Proguard 深度代码压缩与无用资源剔除，体积精简，极速秒开。
   - 零多余权限，不收集任何用户隐私数据。

## 🛠️ 构建指南

```bash
# 克隆仓库
git clone https://github.com/misaka02/TopSchedule.git
cd TopSchedule

# 使用 Gradle 编译 Release APK
./gradlew assembleRelease
```

## 📄 开源许可

本项目遵循 [Apache-2.0 License](LICENSE)。
