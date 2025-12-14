# 快速测试步骤

## 立即运行测试

### 1. Gradle Sync（如果还没做）
在 Android Studio 中：
- 点击顶部的 **Sync Project with Gradle Files**（大象图标）
- 或者执行菜单：**File → Sync Project with Gradle Files**

等待同步完成（会下载 Okio 依赖）

### 2. 构建并安装
在项目根目录打开命令行（cmd），执行：
```cmd
gradlew.bat clean assembleDebug installDebug
```

或者在 Android Studio 中点击 **Run** 按钮（绿色三角形）

### 3. 测试步骤
1. 打开应用
2. 点击首页的 **"Open SVGA Test"** 按钮
3. 点击 **"Play SVGA (from assets/demo.svga)"** 按钮
4. 观察界面提示和动画播放

### 4. 查看日志
打开终端执行：
```cmd
adb logcat -s SvgaTestActivity
```

**成功的日志应该是：**
```
D/SvgaTestActivity: 文件信息: ZIP格式 (SVGA), 大小: xxxxx字节
D/SvgaTestActivity: 准备解析 SVGA 文件: xxx.svga, 大小: xxxxx bytes
D/SvgaTestActivity: SVGA 解析成功
```

**如果失败，日志会显示：**
```
D/SvgaTestActivity: 文件信息: ⚠️ JPEG图片 (非SVGA), 大小: xxxxx字节
E/SvgaTestActivity: SVGA 解析失败 - onError 回调
```

这时你就知道下载的文件格式不对。

## 预期结果

### ✅ 成功场景
- Toast 提示："下载完成: ZIP格式 (SVGA), 大小: xxxxx字节"
- Toast 提示："播放成功"
- SVGAImageView 显示动画并循环播放

### ❌ 原 URL 失败场景（已修复）
使用 `https://res.lukeelive.com/...?imageslim` 时：
- Toast 提示："下载完成: ⚠️ JPEG/PNG/WebP图片 (非SVGA), 大小: xxxxx字节"
- Toast 提示："SVGA 解析失败 - 文件格式可能不正确"
- 动画不播放

### ✅ 新 URL 成功场景（当前代码）
使用 `https://github.com/svga/SVGA-Samples/raw/master/rose.svga` 时：
- ✅ 正常下载
- ✅ 正常解析
- ✅ 正常播放

## 替换为你自己的 SVGA URL

### 步骤
1. 打开 `SvgaTestActivity.kt`
2. 找到第 30 行附近：
```kotlin
val demoUrl = "https://github.com/svga/SVGA-Samples/raw/master/rose.svga"
```
3. 替换为你的 URL：
```kotlin
val demoUrl = "https://你的域名.com/path/to/file.svga"
```
4. **重要**：确保 URL 不带任何图片处理参数：
   - ❌ `?imageslim`
   - ❌ `?x-oss-process=image`
   - ❌ `?imageView2`
   - ✅ 只要纯净的 `.svga` URL

### 验证你的 URL
在浏览器中打开你的 URL：
- ✅ 如果提示下载 `.svga` 文件 → URL 正确
- ❌ 如果直接显示图片 → URL 被转换了，需要获取原始链接

## 常见问题排查

### Q1: "Unresolved reference 'okio'" 错误
**解决**：执行 Gradle Sync
```
File → Sync Project with Gradle Files
```

### Q2: "Theme.AppCompat" 运行时错误
**解决**：已修复（themes.xml 已改为 AppCompat）

### Q3: Kotlin 版本不兼容错误
**解决**：已修复（Kotlin 版本已升级到 2.2.0）

### Q4: 下载完成但解析失败
**原因**：URL 返回的不是 SVGA 文件
**解决**：
1. 查看 Toast 提示的文件格式
2. 如果是图片格式，需要获取原始 SVGA URL
3. 参考 `SVGA_TROUBLESHOOTING.md` 获取详细说明

### Q5: 网络连接失败
**检查**：
- 设备是否联网
- URL 是否可访问
- 是否需要 VPN（GitHub 在某些网络下可能受限）

**临时方案**：使用 Assets 方式（见下文）

## 使用 Assets 方式（无需网络）

如果网络不稳定或 URL 无法访问，可以把 SVGA 文件放到 assets：

### 步骤
1. 下载 SVGA 文件到本地
2. 将文件放到 `app/src/main/assets/` 目录（创建 assets 文件夹如果不存在）
3. 修改 `SvgaTestActivity.kt`：

```kotlin
btnPlay.setOnClickListener {
    playFromAssets("demo.svga")  // 使用 assets 中的文件
}

private fun playFromAssets(fileName: String) {
    val parser = SVGAParser(this)
    parser.decodeFromAssets(
        fileName,
        object : SVGAParser.ParseCompletion {
            override fun onComplete(videoItem: SVGAVideoEntity) {
                svgaImageView?.setVideoItem(videoItem)
                svgaImageView?.startAnimation()
                Toast.makeText(this@SvgaTestActivity, "播放成功", Toast.LENGTH_SHORT).show()
            }
            
            override fun onError() {
                Toast.makeText(this@SvgaTestActivity, "解析失败", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
```

## 测试用的其他 SVGA URL

```kotlin
// 官方示例（推荐测试用）
"https://github.com/svga/SVGA-Samples/raw/master/rose.svga"
"https://github.com/svga/SVGA-Samples/raw/master/alarm.svga"
"https://github.com/svga/SVGA-Samples/raw/master/halloween.svga"

// 可以在按钮点击时切换不同 URL 测试
```

## 需要帮助？

如果测试仍然失败，请提供：
1. **Logcat 完整日志**（执行 `adb logcat -s SvgaTestActivity > log.txt`）
2. **Toast 显示的文件格式信息**
3. **你使用的 URL**（如果可以分享）
4. **错误截图**

将这些信息提供给开发者，可以更快定位问题。

