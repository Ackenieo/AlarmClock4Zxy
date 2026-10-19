"""
离线验证开合跳计数算法。
MediaPipe 的 BlazePose 与 ML Kit Pose Detection 出自同一模型家族，
都是 33 个关键点、同样的索引，所以可以先用它把阈值调准，再落到 Android 端。
"""
import json
import math
import os
import sys

import cv2
import mediapipe as mp
from mediapipe.tasks.python import vision
from mediapipe.tasks.python.core.base_options import BaseOptions

MODEL = ".tmp_check/pose_landmarker_full.task"

VIDEO = "855ddb11a3c0e62621f356e425a08f35.mp4"
OUT_DIR = ".tmp_check"
SAMPLE_STEP = 2  # 30fps -> 15fps

L_SHOULDER, R_SHOULDER = 11, 12
L_WRIST, R_WRIST = 15, 16
L_HIP, R_HIP = 23, 24
L_ANKLE, R_ANKLE = 27, 28

VIS_MIN = 0.35


class Counter:
    """与 JumpingJackCounter.java 完全一致的状态机"""

    def __init__(self, arm_open=0.55, leg_open=0.90, arm_closed=0.05, leg_closed=0.60,
                 hold_ms=100, min_open_ms=180, min_gap_ms=320, lost_reset_ms=800, smooth=False):
        self.arm_open = arm_open
        self.leg_open = leg_open
        self.arm_closed = arm_closed
        self.leg_closed = leg_closed
        self.hold_ms = hold_ms
        self.min_open_ms = min_open_ms
        self.min_gap_ms = min_gap_ms
        self.lost_reset_ms = lost_reset_ms

        self.count = 0
        self.smooth = smooth
        self.phase = "UNKNOWN"
        self.pending = "UNKNOWN"
        self.pending_since = 0
        self.open_since = 0
        self.last_rep = -1e9
        self.last_valid = 0
        self.arm_ema = None
        self.leg_ema = None
        self.events = []

    def update(self, lms, now):
        """lms: dict idx -> (x, y, vis)"""
        def ok(i):
            return i in lms and lms[i][2] >= VIS_MIN

        if not (ok(L_SHOULDER) and ok(R_SHOULDER) and ok(L_HIP) and ok(R_HIP)):
            return self._invalid(now, "stand")

        shoulder_y = (lms[L_SHOULDER][1] + lms[R_SHOULDER][1]) / 2
        hip_y = (lms[L_HIP][1] + lms[R_HIP][1]) / 2
        torso = hip_y - shoulder_y
        if torso < 0.05:
            return self._invalid(now, "stand")

        shoulder_w = abs(lms[L_SHOULDER][0] - lms[R_SHOULDER][0])
        ref = max(shoulder_w, 0.55 * torso)

        if ok(L_WRIST) and ok(R_WRIST):
            wrist_y = (lms[L_WRIST][1] + lms[R_WRIST][1]) / 2
        elif ok(L_WRIST):
            wrist_y = lms[L_WRIST][1]
        elif ok(R_WRIST):
            wrist_y = lms[R_WRIST][1]
        else:
            return self._invalid(now, "arms")

        if not (ok(L_ANKLE) and ok(R_ANKLE)):
            return self._invalid(now, "feet")

        arm = (shoulder_y - wrist_y) / torso
        leg = abs(lms[L_ANKLE][0] - lms[R_ANKLE][0]) / ref

        self.arm_ema = arm if (self.arm_ema is None or not self.smooth) else 0.6 * self.arm_ema + 0.4 * arm
        self.leg_ema = leg if (self.leg_ema is None or not self.smooth) else 0.6 * self.leg_ema + 0.4 * leg
        self.last_valid = now

        if self.arm_ema >= self.arm_open and self.leg_ema >= self.leg_open:
            cand = "OPEN"
        elif self.arm_ema <= self.arm_closed and self.leg_ema <= self.leg_closed:
            cand = "CLOSED"
        else:
            cand = "UNKNOWN"

        counted = False
        if cand == "UNKNOWN" or cand == self.phase:
            self.pending = "UNKNOWN"
        elif cand != self.pending:
            self.pending = cand
            self.pending_since = now
        elif now - self.pending_since >= self.hold_ms:
            prev = self.phase
            self.phase = cand
            self.pending = "UNKNOWN"
            if cand == "OPEN":
                self.open_since = now
            elif prev == "OPEN":
                if now - self.open_since >= self.min_open_ms and now - self.last_rep >= self.min_gap_ms:
                    self.count += 1
                    self.last_rep = now
                    counted = True
                    self.events.append((now, self.count))

        return dict(valid=True, hint="", phase=self.phase, count=self.count,
                    counted=counted, arm=self.arm_ema, leg=self.leg_ema)

    def _invalid(self, now, hint):
        self.pending = "UNKNOWN"
        if now - self.last_valid > self.lost_reset_ms:
            self.phase = "UNKNOWN"
            self.arm_ema = None
            self.leg_ema = None
        return dict(valid=False, hint=hint, phase=self.phase, count=self.count,
                    counted=False, arm=self.arm_ema or 0, leg=self.leg_ema or 0)


def extract(limit=None):
    cap = cv2.VideoCapture(VIDEO)
    fps = cap.get(cv2.CAP_PROP_FPS) or 30
    options = vision.PoseLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=MODEL),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
        output_segmentation_masks=False,
    )
    landmarker = vision.PoseLandmarker.create_from_options(options)
    frames = []
    idx = 0
    while True:
        ok, frame = cap.read()
        if not ok:
            break
        if idx % SAMPLE_STEP == 0:
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_img = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            ts = int(idx * 1000 / fps)
            res = landmarker.detect_for_video(mp_img, ts)
            lms = {}
            if res.pose_landmarks:
                for i, lm in enumerate(res.pose_landmarks[0]):
                    lms[i] = (lm.x, lm.y, lm.visibility if lm.visibility is not None else 1.0)
            frames.append(dict(t=ts, lms=lms))
        idx += 1
        if limit and len(frames) >= limit:
            break
    cap.release()
    landmarker.close()
    return frames, fps


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    cache = os.path.join(OUT_DIR, "landmarks.json")
    if os.path.exists(cache):
        data = json.load(open(cache))
        frames = [dict(t=d["t"], lms={int(k): tuple(v) for k, v in d["lms"].items()}) for d in data]
        print("loaded cache", len(frames))
    else:
        frames, _ = extract()
        json.dump([dict(t=f["t"], lms={str(k): v for k, v in f["lms"].items()}) for f in frames],
                  open(cache, "w"))
        print("extracted", len(frames), "frames")

    for params in [
        dict(),
        dict(arm_open=0.80, leg_open=1.20),
        dict(arm_open=1.00, leg_open=1.35),
        dict(arm_open=0.90, leg_open=1.20, arm_closed=0.60, leg_closed=1.05),
    ]:
        c = Counter(**params)
        for f in frames:
            c.update(f["lms"], f["t"])
        print(f"{params} -> count={c.count}")

    c = Counter()
    for f in frames:
        c.update(f["lms"], f["t"])
    print("events:", [(round(t / 1000, 2), n) for t, n in c.events])


if __name__ == "__main__":
    main()
