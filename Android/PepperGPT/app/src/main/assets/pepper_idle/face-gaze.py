import os
import select
import signal
import sys
import time
import uuid

import cv2
import numpy as np
import qi

def stop(signum, frame):
    raise SystemExit()

for sig in (signal.SIGHUP, signal.SIGTERM):
    signal.signal(sig, stop)

detector = cv2.CascadeClassifier("/usr/share/opencv4/haarcascades/haarcascade_frontalface_default.xml")
if detector.empty():
    sys.exit(1)
cv2.setNumThreads(1)
session = qi.Session()
session.connect("tcp://127.0.0.1:9559")
video = session.service("ALVideoDevice")
subscriber = video.subscribeCamera("PG_face_" + uuid.uuid4().hex[:10], 0, 1, 11, 5)
deadline = time.time() + 1.1
previous = None
try:
    while time.time() < deadline:
        if select.select([sys.stdin], [], [], 0.15)[0]:
            heartbeat = os.read(sys.stdin.fileno(), 64)
            if not heartbeat:
                break
            deadline = time.time() + 1.1
        frame = video.getImageRemote(subscriber)
        if not frame or time.time() >= deadline:
            continue
        width, height = frame[:2]
        pixels = np.frombuffer(frame[6], dtype=np.uint8)
        if pixels.size != width * height * 3:
            continue
        gray = cv2.cvtColor(pixels.reshape(height, width, 3), cv2.COLOR_RGB2GRAY)
        small = cv2.resize(gray, (160, 120))
        faces = detector.detectMultiScale(cv2.equalizeHist(small), scaleFactor=1.14, minNeighbors=5, minSize=(18, 18))
        if len(faces) != 1:
            previous = None
            continue
        x, y, w, h = [float(v) for v in faces[0]]
        center = ((x + w / 2) / 160, (y + h / 2) / 120)
        if previous is None or max(abs(center[i] - previous[i]) for i in (0, 1)) > 0.18:
            previous = center
            continue
        previous = center
        dx, dy = center[0] - 0.5, center[1] - 0.46
        print("%.3f %.3f" % (dx, dy))
finally:
    video.unsubscribe(subscriber)
