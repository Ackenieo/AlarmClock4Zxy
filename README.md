# 开合跳闹钟 (AlarmClock4jACW)

一个「不做完运动就关不掉」的 Android 闹钟。到点后全屏响铃，必须站在前置摄像头前完成指定数量的开合跳，  
由 ML Kit Pose Detection 实时识别骨骼、统计次数，达标后才停止响铃。

> 初代版本只支持开合跳；深蹲、俯卧撑在界面上预留了位置（标注"敬请期待"）。

| 闹钟列表 | 编辑 | 锁屏响铃 | 运动挑战 | 骨架识别 |
| ---- | -- | ---- | ---- | ---- |
|      |    |      |      |      |

---

## 1. 功能

| 能力    | 说明                                                                    |
| ----- | --------------------------------------------------------------------- |
| 界面语言  | 右上角地球按钮打开语言选择器：简体中文 / English / 日本語 / Français / 跟随系统；立即生效并记住选择              |
| 成功记录  | 每完成一次任务累计次数 +1；连续天数达到 1 / 3 / 6 / 10 / 15 / 20 天（之后每 5 天）弹窗庆祝；30 分钟超时或隔天中断后从 1 重新计数 |
| 删除闹钟  | 左滑先露出红色圆形垃圾桶；继续左滑，红色按钮随距离伸长，超过约 58% 松手即删除；删除区使用与闹钟行相同的玻璃底色，也可以点垃圾桶删除 |
| 闹钟管理  | 添加 / 编辑 / 删除 / 启停；精确到分钟；支持按周重复（任选星期几）                                 |
| 任务类型  | 开合跳 / 背单词；编辑页横向滑动卡片，点击选中（深蹲、俯卧撑敬请期待）                          |
| 目标数量  | 开合跳 5~60 个（默认 20）；背单词 5~60 个（默认 10）                                   |
| 背单词任务 | 同一张「背单词」卡片，编辑页的「背单词模式」行切换：**选择**（看中文选英文，4 选 1，错项变红且必须选对）与**拼写**（3 次错给首字母、5 次错给字母数、10 次错显示答案并跳词） |
| 词库      | 内置四级（3846 词）/ 六级（5406 词）完整词表，全局选择，按固定顺序推进并记录进度；答错的词下次优先复习     |
| 单词发音  | 系统 TTS 美式英语、语速 0.9x；先合成到缓存再由本应用播放。拼写模式不自动播，点喇叭手动听；答对后 625ms 切页，尾音不截断；复习页支持点读 |
| 精确响铃  | `AlarmManager.setAlarmClock()`，不受 Doze 影响，状态栏显示闹钟图标                   |
| 锁屏全屏  | 前台服务 + 高优先级通知 + `fullScreenIntent`，息屏/锁屏时直接点亮屏幕跳到响铃页                  |
| 声音与振动 | 自带的循环铃声（`res/raw/alarm_beep.wav`，44.1kHz 生成）+ 波形振动，走 `USAGE_ALARM` 通道；音量锁定最大 |
| 免打扰兜底  | 检测到免打扰 / 静音，立刻切到更长更重的振动节拍；闹钟铃声本身在这两种状态下都不被系统抑制    |
| 运动验证  | CameraX 前置预览 + ML Kit 骨骼识别，实时画骨架、计数、进度环                               |
| 达标关闭  | 完成目标次数 → 播放完成音 → 停止服务、清除通知、回到桌面                                       |
| 开机恢复  | 监听 `BOOT_COMPLETED` / `TIME_SET` / `TIMEZONE_CHANGED` 重新登记闹钟          |
| 防逃逸   | 响铃期间没有任何关闭入口：音量键和返回键被吃掉，界面被划掉后由服务看门狗拉回，进程被杀会自动恢复（详见 3.4） |
| 硬超时兜底 | 连续响满 30 分钟自动停止——识别失败、相机被拒、半夜没人时唯一的最后一道保险（界面上不暴露）     |

## 2. 运行

```bash
./gradlew :app:assembleDebug          # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # 识别算法单元测试
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- `minSdk 26 / targetSdk 37 / Java 11`
- 如果 Android Studio 报 `SDK location not found`，在项目根目录创建 `local.properties` 指向本机 Android SDK（该文件已被 Git 忽略），或通过 `Settings → Languages & Frameworks → Android SDK` 配置后重新 Sync
- 首次启动会申请通知权限；Android 14+ 还会引导开启「全屏通知」（见下）
- 添加闹钟后长按首页标题可打开调试工具：**10 秒后测试响铃**、**10 秒后背单词测试响铃**、**用测试视频验证识别**

## 3. 关键实现

### 3.1 闹钟链路

```
AlarmManager.setAlarmClock(精确时刻)
      └─> AlarmReceiver (BroadcastReceiver)
            ├─ 重复闹钟：重新排下一次；一次性闹钟：置为关闭
            └─> AlarmService (前台服务, type=mediaPlayback)
                  ├─ MediaPlayer 循环播放 + Vibrator 波形振动
                  ├─ 高优先级通知 + fullScreenIntent
                  ├─ 每秒巡检：音量拉满 / 免打扰复核 / 界面看门狗 / 30 分钟硬超时
                  └─> RingingActivity（锁屏可见、点亮屏幕、全屏）
                        └─> ExerciseActivity（相机 + 识别）
                              └─ 达标 → RESULT_OK → AlarmService.stop()
```

声音只由 `AlarmService` 控制，界面层拿不到任何停止入口——这是"必须运动"的前提。
`ACTION_STOP` 是唯一的停止指令，只有响铃页在收到 `RESULT_OK` 后才会发出一次。

### 3.2 开合跳识别

模型：`com.google.mlkit:pose-detection`（**bundled** 版，模型内置在 APK 里，不依赖 Google Play 服务、不需要联网，  
半夜断网也能用），`STREAM_MODE` 实时模式，输出 33 个关键点。

**为什么不用绝对坐标**：站着离镜头 1 米和 3 米，像素坐标差 3 倍。所以所有判定都用身体自身尺度归一化：

```
armScore = (肩线y - 双腕y) / 躯干长度        手臂下垂 ≈ -1.0，举过头顶 ≈ +0.7 ~ +1.1
legScore = |左踝x - 右踝x| / 参考宽度        并腿 ≈ 0.1~0.5，开腿 ≈ 0.9~1.3
           参考宽度 = max(肩宽, 0.55 × 躯干长)   ← 侧身时肩宽会塌缩，用躯干兜底
```

**计数**：一次开合跳 = `张开 → 并拢` 的完整状态迁移。为了避免抖动误判：

1. 进入张开：`armScore ≥ 0.55 且 legScore ≥ 0.90`；回到并拢：`armScore ≤ 0.05 且 legScore ≤ 0.60`  
   —— 两套阈值构成滞回区间，卡在中间不会来回翻转；
2. 候选状态必须连续保持 100ms 才被采纳（约 2~3 帧）；
3. 张开状态至少持续 180ms，两次计数至少间隔 320ms；
4. 丢失人体超过 800ms 后状态归零，避免"张开 → 走开 → 回来并腿"被算成一次。

**踩过的坑**：

- **ML Kit 的 `PoseLandmark.getPosition()` 返回的是图像像素坐标，不是 0~1 归一化坐标。**  
  直接拿去画骨架会全部跑到屏幕外。现在统一在 `Landmarks.fromPose()` 里按图像尺寸归一化一次，  
  并且做了坐标空间自检（任一坐标 > 1.5 即判定为像素坐标），两种约定都能正确处理。
- **不能对得分做指数平滑（EMA）。** 每次动作的峰值只维持 3~5 帧，EMA 会滞后于峰值，  
  实测把手臂得分从 0.9 削到 0.56，导致大量漏计（75 秒视频从 56 个掉到 10 个）。  
  抗抖动交给上面的时间滞回，而不是数值平滑。
- **`pending` 状态必须及时清空。** 否则"孤立的一帧张开 + 很久以后又出现一帧张开"会拼成一次完整动作。

### 3.3 阈值是怎么定下来的

`tools/verify_counter.py` 用 MediaPipe 的 BlazePose（与 ML Kit 同源、同样是 33 点）在测试视频上离线跑一遍，  
把每帧的 `armScore / legScore` 导出，再用**与 Java 完全相同的状态机**统计，做参数扫描：

| arm_open / leg_open | 75 秒视频计数 |
| ------------------- | -------- |
| 0.50 / 0.85         | 57       |
| **0.55 / 0.90（采用）** | **56**   |
| 0.60 / 0.95         | 49       |
| 0.65 / 1.05         | 26       |

0.55/0.90 落在稳定平台区：再放宽几乎不变（说明没漏），再收紧快速掉（说明开始漏）。  
同时人工抽查了未被计数的片段（第 60~66 秒是"手臂平举、慢动作示范"），确认是**正确地没算**，不是漏检。

App 内的「用测试视频验证识别」把这个视频逐帧喂给真正的 ML Kit，用同一套计数器跑，  
可用于回归验证（不依赖真人站在镜头前）。

### 3.4 防逃逸：为什么关不掉

响铃期间 `AlarmService` 每秒巡检一次（`ticker`），配合两个界面上的按键拦截，把常见的逃逸路径逐条堵死：

| 逃逸尝试              | 拦截方式                                                                                     |
| ----------------- | ---------------------------------------------------------------------------------------- |
| 按音量键调小 / 静音      | 响铃页与运动页都消费 `VOLUME_UP/DOWN/MUTE`（`VolumeKeyGuard`），只提示「音量已锁定最大」                        |
| 通知栏音量滑块 / 系统调小   | `STREAM_ALARM` 每秒重新 `setStreamVolume(max)`，最多压低 1 秒                                       |
| 开免打扰 / 拨静音物理键    | `ringerMode != NORMAL` 或 `currentInterruptionFilter != ALL` → 切强震动波形，振动用途标成 `USAGE_ALARM`。**铃声本身两种情况下都照常放**（实测 `STREAM_ALARM` 不在铃声模式抑制的流里）；振动只在静音/振动档生效，免打扰下会被系统按 app-ops 吞掉——见 6 的说明 |
| 按 Home / 切到别的应用   | 界面 `onPause` 上报，服务发现前台界面消失超过 2 秒 → 重新拉起响铃页                                          |
| 从概览把应用划掉         | `onTaskRemoved` → 800ms 后重新拉起响铃页，声音和振动全程不停（响铃页本身 `excludeFromRecents`，概览里根本没有它的卡片可划） |
| 杀进程 / 系统回收       | `START_STICKY` + 会话落库（闹钟 id、次数、标题、截止时间）→ 服务重启后恢复同一个闹钟继续响                             |
| 按返回键            | 响铃页拦截返回键，只提示「先完成运动，闹钟才会停」                                                                   |

**唯一出口**是做完目标开合跳。**最后的保险**是连续响满 30 分钟（`MAX_RING_MILLIS`）自动停止：
识别失败、相机被拒、半夜没人时不至于把手机变成一台只会响的砖头。这个兜底不在界面上暴露，也没有手动提前停的入口。

> 后台启动 Activity 在 Android 10+ 受限，服务直接 `startActivity` 可能被系统拦下；
> 拦下时退回「重发带 `fullScreenIntent` 的高优先级通知」这条系统认可的通路，息屏/锁屏下由系统代为拉起。

### 3.5 锁定任务（Kiosk）模式

想做到「连 Home 和概览都按不动」，普通安装是不够的——平台不允许第三方应用单方面锁死设备。
本项目的 `android:lockTaskMode="if_whitelisted"` + `KioskController` 是这么设计的：

- **不是设备所有者**：`startLockTask()` 抛异常，代码捕获后静默降级，只保留上面 3.4 那套按键拦截 + 看门狗。
- **是设备所有者（Device Owner）**：`setLockTaskPackages()` 把本应用加白名单，
  `setLockTaskFeatures(LOCK_TASK_FEATURE_NONE)` 关掉 Home / 概览 / 通知栏 / 锁屏，
  响铃时进入 `LOCK_TASK_MODE_LOCKED`，用户无法离开本应用，也就进不去「设置 → 应用 → 强制停止」。

设备所有者只能设在**已恢复出厂且未登录任何 Google / 厂商账号**的设备上，需要用 adb 手动授予一次：

```bash
adb shell dpm set-device-owner com.example.alarmclock4jacw/.AlarmDeviceAdminReceiver
```

授予后无需其他操作，到点响铃自动进入锁定；做完运动时 `KioskController.exitLockTask()` 会释放锁定，
否则任务无法结束。撤销：

```bash
adb shell dpm remove-active-admin com.example.alarmclock4jacw/.AlarmDeviceAdminReceiver
```

## 4. 权限

| 权限                                                         | 用途                                     |
| ---------------------------------------------------------- | -------------------------------------- |
| `CAMERA`                                                   | 骨骼识别                                   |
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM`                 | 精确闹钟                                   |
| `USE_FULL_SCREEN_INTENT`                                   | 锁屏全屏提醒（Android 14+ 需用户在设置里允许，App 内会引导） |
| `POST_NOTIFICATIONS`                                       | 响铃通知                                   |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 响铃前台服务                                 |
| `VIBRATE` / `WAKE_LOCK` / `DISABLE_KEYGUARD`               | 振动、点亮屏幕、跳过锁屏                           |
| `RECEIVE_BOOT_COMPLETED`                                   | 重启后恢复闹钟                                |

> **关于全屏通知**：Android 14 起，系统默认只给通话/闹钟类应用弹全屏提醒。  
> App 启动时会检测 `NotificationManager.canUseFullScreenIntent()`，未授权则引导用户去  
> `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` 打开。没有这个权限时，锁屏只会有通知横幅，点一下也能进响铃页。

## 5. 目录结构

```
app/src/main/java/com/example/alarmclock4jacw/
├── MainActivity.java          闹钟列表 / 权限引导 / 调试入口
├── AlarmApplication.java      进程级 Context（让服务/通知跟随界面语言）
├── AppLanguage.java           语言存档与 Context 包装
├── BaseActivity.java          所有页面统一套用界面语言
├── LanguageDialog.java        跟随系统 / 简体中文 / English 选择弹窗
├── AlarmEditActivity.java     时间、重复、任务类型与目标数量编辑
├── RingingActivity.java       锁屏响铃页（无关闭入口，拦音量键与返回键）
├── ExerciseActivity.java      CameraX + ML Kit 实时识别（无退出入口）
├── WordSpellActivity.java     背单词选择/拼写页（4 选 1 / 提示 / good! / 复习列表）
├── TtsSpeaker.java            美音 TTS：合成缓存 + 本应用播放
├── WordStore.java             词库读取、全局词库选择与顺序进度
├── Word.java                  单词模型、答案归一化、顺序抽词
├── SpellingEditText.java      禁联想 / 禁粘贴的拼写输入框
├── Alarm.java                 闹钟模型（含下次触发时间计算）
├── StreakStore.java           成功次数 / 连续天数 / 中断重置
├── StreakRules.java           庆祝节点：1、3、6、10、15、20，之后每 5 天
├── CelebrationDialog.java     连续达成庆祝弹窗
├── DeletePillView.java        左滑删除的伸缩红色药丸
├── SwipeRevealLayout.java     左滑手势、吸附与删除阈值
├── AlarmStore.java            SharedPreferences + JSON 持久化
├── AlarmScheduler.java        AlarmManager 精确调度
├── AlarmReceiver.java         到点广播
├── AlarmService.java          响铃前台服务（音量锁定 / 免打扰切震动 / 看门狗 / 30 分钟兜底）
├── KioskController.java       锁定任务进入与释放（设备所有者下才真正锁死）
├── AlarmDeviceAdminReceiver.java 设备管理员声明，用于换取 Device Owner 身份
├── VolumeKeyGuard.java        响铃期间把音量键变成废键
├── BootReceiver.java          开机重排
├── JumpingJackCounter.java    开合跳状态机（纯逻辑，可单测）
├── Landmarks.java             33 点归一化封装
├── PoseOverlayView.java       骨架绘制
├── RingProgressView.java      进度环
└── VideoTestActivity.java     视频回放验证（调试）
```

### 5.1 词库来源与许可证

`app/src/main/assets/words_cet4.json` 与 `words_cet6.json` 由 **ECDICT** 生成：

- 项目：<https://github.com/skywind3000/ECDICT>
- 许可证：**MIT**
- 处理方式：按 `tag` 中的 `cet4` / `cet6` 筛出词条，只保留英文单词与最多 3 条常用中文释义，按词频排序后打包进 APK，离线可用。

## 6. 已知限制 / 后续

- 开合跳、背单词选择、背单词拼写可用；深蹲、俯卧撑的 UI 位置已留好，接入时只需再写一个 `Counter`（同样吃 `Landmarks`）。
- 单词发音依赖系统自带的离线英语语音包；没有语音包时会弹窗引导下载。没有把 TTS 引擎或整套音频打包进 APK——体积不可控。
- 前置摄像头需要拍到全身：建议把手机横放在床头柜或立在桌上，退后 2~3 米。太近会提示"请后退"。
- 强光/逆光下骨骼点会丢失，此时界面会提示"没看到人"，不会误计数。
- 未做"多次运动累计/多闹钟队列"等进阶玩法。
- **强停仍是普通安装的出口**：`设置 → 应用 → 强制停止` 能杀掉进程，第三方应用无权禁止；要堵住只能走 3.5 的 Device Owner。
- **免打扰下的振动会被系统吞掉**（API 37 模拟器实测：振动请求状态 `ignored_app_ops`，`FLAG_BYPASS_INTERRUPTION_POLICY` 也会被剥离成 `flags: 0`）。想让免打扰下也震，必须申请并让用户手动授予「通知策略访问」权限（`ACCESS_NOTIFICATION_POLICY`），本项目按产品决定没有申请。静音 / 振动档下振动实测正常生效（`currentState = VIBRATING`），铃声两种情况都在放。
- **屏幕固定不是设备所有者**：非 Device Owner 时 `startLockTask()` 进入的是系统「屏幕固定」，实测 Home 键、概览键、上滑回桌面、拉起设置都被挡下；但「同时长按 返回 + 概览」是系统保留的解除方式，第三方应用拦不住（解除后铃声照旧，仍必须做完运动）。
- **硬件强重启拦不住**：电源 + 音量长按 10 秒是系统级逃生口，任何应用都消费不掉（本项目也不打算对抗它）。
- **看门狗受后台启动限制**：界面消失后靠 `startActivity` 拉回，被系统拦下时退回全屏通知；Android 14+ 没授予「全屏通知」的话，息屏下可能只弹横幅。
- 30 分钟兜底自动停止后，一次性闹钟已在触发时被置为关闭，重复闹钟照常排下一次。
