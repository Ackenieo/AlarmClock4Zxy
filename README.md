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
| 闹钟管理  | 添加 / 编辑 / 删除 / 启停；精确到分钟；支持按周重复（任选星期几）                                 |
| 目标次数  | 5~60 个，滑块调节，默认 20                                                     |
| 精确响铃  | `AlarmManager.setAlarmClock()`，不受 Doze 影响，状态栏显示闹钟图标                   |
| 锁屏全屏  | 前台服务 + 高优先级通知 + `fullScreenIntent`，息屏/锁屏时直接点亮屏幕跳到响铃页                  |
| 声音与振动 | 自带的循环铃声（`res/raw/alarm_beep.wav`，44.1kHz 生成）+ 波形振动，走 `USAGE_ALARM` 通道 |
| 运动验证  | CameraX 前置预览 + ML Kit 骨骼识别，实时画骨架、计数、进度环                               |
| 达标关闭  | 完成目标次数 → 播放完成音 → 停止服务、清除通知、回到桌面                                       |
| 开机恢复  | 监听 `BOOT_COMPLETED` / `TIME_SET` / `TIMEZONE_CHANGED` 重新登记闹钟          |
| 安全兜底  | 响铃页长按 5 秒可紧急关闭；相机权限被拒时长按 10 秒可强制关闭（避免手机被锁死）                           |

## 2. 运行

```bash
./gradlew :app:assembleDebug          # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # 识别算法单元测试
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- `minSdk 26 / targetSdk 37 / Java 11`
- 首次启动会申请通知权限；Android 14+ 还会引导开启「全屏通知」（见下）
- 添加闹钟后长按首页标题可打开调试工具：**10 秒后测试响铃**、**用测试视频验证识别**

## 3. 关键实现

### 3.1 闹钟链路

```
AlarmManager.setAlarmClock(精确时刻)
      └─> AlarmReceiver (BroadcastReceiver)
            ├─ 重复闹钟：重新排下一次；一次性闹钟：置为关闭
            └─> AlarmService (前台服务, type=mediaPlayback)
                  ├─ MediaPlayer 循环播放 + Vibrator 波形振动
                  ├─ 高优先级通知 + fullScreenIntent
                  └─> RingingActivity（锁屏可见、点亮屏幕、全屏）
                        └─> ExerciseActivity（相机 + 识别）
                              └─ 达标 → RESULT_OK → AlarmService.stop()
```

声音只由 `AlarmService` 控制，界面层无法单独关闭——这是"必须运动"的前提。

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
├── AlarmEditActivity.java     时间、重复、次数编辑
├── RingingActivity.java       锁屏响铃页
├── ExerciseActivity.java      CameraX + ML Kit 实时识别
├── Alarm.java                 闹钟模型（含下次触发时间计算）
├── AlarmStore.java            SharedPreferences + JSON 持久化
├── AlarmScheduler.java        AlarmManager 精确调度
├── AlarmReceiver.java         到点广播
├── AlarmService.java          响铃前台服务（声音/振动/全屏通知）
├── BootReceiver.java          开机重排
├── JumpingJackCounter.java    开合跳状态机（纯逻辑，可单测）
├── Landmarks.java             33 点归一化封装
├── PoseOverlayView.java       骨架绘制
├── RingProgressView.java      进度环
└── VideoTestActivity.java     视频回放验证（调试）
```

## 6. 已知限制 / 后续

- 只有开合跳。深蹲、俯卧撑的 UI 位置已留好，接入时只需再写一个 `Counter`（同样吃 `Landmarks`）。
- 前置摄像头需要拍到全身：建议把手机横放在床头柜或立在桌上，退后 2~3 米。太近会提示"请后退"。
- 强光/逆光下骨骼点会丢失，此时界面会提示"没看到人"，不会误计数。
- 未做"多次运动累计/多闹钟队列"等进阶玩法。
