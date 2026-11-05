import math
import os
import select
import signal
import sys
import time
import qi

session = qi.Session()
session.connect("tcp://127.0.0.1:9559")
leds = session.service("ALLeds")
ears = "EarLeds"
rgb = ("ChestLedsRed", "ChestLedsGreen", "ChestLedsBlue")
colors = {
    "listening": (.02, .45, .06),
    "thinking": (.32, .04, .60),
    "speaking": (.01, .14, .62),
    "greeting": (.20, .13, .62),
    "enumeration": (.02, .39, .58),
    "story": (.33, .04, .55),
    "recipe": (.48, .08, .33),
    "weather": (.03, .32, .62)
}

def interrupted(signum, frame):
    raise SystemExit()

for sig in (signal.SIGHUP, signal.SIGTERM):
    signal.signal(sig, interrupted)

def save(group):
    values = leds.getIntensity(group)
    if isinstance(values, (list, tuple)):
        if all(value == values[0] for value in values):
            return [(group, values[0])]
        return zip(leds.listGroup(group), values)
    return [(group, values)]

def restore(channels):
    for channel, value in channels:
        leds.setIntensity(channel, value)

initial_ears = save(ears)
initial_rgb = sum([save(channel) for channel in rgb], [])
phase = "idle"
touched_ears = False
touched_rgb = False
next_rgb = 0
deadline = time.time() + 2
buf = ""
try:
    while True:
        requested = phase
        if select.select([sys.stdin], [], [], .12)[0]:
            data = os.read(sys.stdin.fileno(), 1024)
            if not data:
                break
            buf += data
            while "\n" in buf:
                requested, buf = buf.split("\n", 1)
                if requested in colors or requested == "idle":
                    deadline = time.time() + 2
                elif requested == "close":
                    raise SystemExit()
                else:
                    requested = phase
        if time.time() >= deadline:
            break
        if phase != requested:
            if phase == "thinking":
                restore(initial_ears)
                touched_ears = False
            phase = requested
            next_rgb = 0
            if phase not in colors and touched_rgb:
                restore(initial_rgb)
                touched_rgb = False
        # Native Qi speech can reset the chest LEDs. Reassert the same target
        # instead of adding a visible pulse to the speaking state.
        if phase in colors and time.time() >= next_rgb:
            touched_rgb = True
            for channel, value in zip(rgb, colors[phase]):
                leds.setIntensity(channel, value)
            next_rgb = time.time() + .24
        if phase == "thinking":
            touched_ears = True
            brightness = .48 + .30 * math.sin(time.time() * 4.5)
            leds.setIntensity(ears, brightness)
finally:
    if touched_ears:
        restore(initial_ears)
    if touched_rgb:
        restore(initial_rgb)
