# 阅读书虫 BookReader — 安卓电子书阅读器

一个功能完整的 Android 原生电子书阅读器（Kotlin + Jetpack Compose）。

## 功能总览

| 功能 | 说明 |
|---|---|
| 多格式打开 | TXT / EPUB / MOBI / PDF（AZW3、KFX 带 DRM 的不支持） |
| 编码自动检测 | UTF-8 / GBK / Big5 等，中文 TXT 不乱码 |
| 自动排版 | 按屏幕尺寸、字号、行高动态分页；章节自动切分（"第x章"等规则） |
| 字号调节 | A- / A+ 实时重排 |
| 划线 | 4 种颜色高亮划线，持久化到 Room 数据库 |
| 笔记 | 划线后添加笔记，与划线关联保存 |
| 书签 | 一键添加当前页书签，列表跳转、删除 |
| 翻译 | ML Kit 翻译，选中即译，中英互译自动检测，50+ 语言可扩展 |
| 朗读 | 系统 TTS 逐段朗读，语速音调可调 |
| PDF OCR | ML Kit 中文 OCR 识别 PDF 页面 → 转成文本 → 参与统一排版，之后划线/翻译/朗读全部可用 |

## 目录结构

```
app/src/main/java/com/workbuddy/bookreader/
├── MainActivity.kt          # 入口 + 导航
├── data/Database.kt         # Room：书籍 / 划线笔记 / 书签
├── parser/
│   ├── BookParser.kt        # TXT/EPUB 解析、编码检测、章节切分
│   └── MobiTextExtractor.kt # MOBI (PalmDOC) 无 DRM 文本提取
├── reader/
│   ├── Paginator.kt         # 自动排版分页引擎（所有格式统一）
│   ├── PdfProcessor.kt      # PDF 渲染 + ML Kit OCR
│   ├── ReaderTTS.kt         # TTS 朗读引擎（跟读回调）
│   └── Translator.kt        # ML Kit 翻译
└── ui/
    ├── LibraryScreen.kt     # 书架（导入书籍）
    ├── ReaderScreen.kt      # 阅读器主界面
    ├── ReaderPageView.kt    # 页面渲染、划线高亮、选区
    └── SelectionActionBar.kt# 划线/翻译/朗读/笔记 操作栏
```

## 构建步骤

1. 安装 Android Studio（Ladybug 或更新）。
2. File → Open 打开本项目根目录。
3. 等待 Gradle Sync 完成（会自动下载依赖）。
4. 连接手机开启 USB 调试，点击 Run ▶ 即可安装。
5. 打 APK：Build → Build Bundle(s)/APK(s) → Build APK(s)，
   产物在 `app/build/outputs/apk/debug/app-debug.apk`。

## 使用说明

- **导入**：书架右下角选择 TXT / EPUB / MOBI / PDF，从文件管理器选书。
- **划线**：长按文字开始选择，拖动扩展选区 → 点击颜色条划线。
- **笔记**：选区 → 「笔记」→ 输入内容保存。
- **翻译**：选区 → 「翻译」，结果出现在底部面板。
- **朗读**：底部「▶ 朗读」从当前页读起；选区朗读只读选中部分。
- **书签**：顶栏 🔖 添加，「书签」查看跳转。
- **PDF**：首次打开会执行 OCR（按页进度提示），完成后与其他格式体验一致。

## 后续可扩展

- 翻页动画（仿真/平移/覆盖）
- 深色模式与背景色切换（羊皮纸/护眼绿）
- 划线笔记导出（Markdown/文本）
- EPUB 内嵌图片与 CSS 排版
- HUFF/CDIC 压缩的 MOBI 支持
- 云同步（划线、进度）
