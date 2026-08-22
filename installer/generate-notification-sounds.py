"""Generate the small, original fallback cue pack used by ts3j-client.

These are deliberately non-verbal UI cues. The application tries Windows SAPI
first for a spoken announcement; the cues make the feature audible even when
SAPI is unavailable. No TeamSpeak sound-pack asset is read or copied.
"""

from pathlib import Path
import math
import struct
import wave


SAMPLE_RATE = 44_100
OUTPUT = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "com" / "github" / "manevolent" / "ts3j" / "client" / "sounds"


PATTERNS = {
    "connected.wav": [(660, 0.12), (880, 0.16)],
    "disconnected.wav": [(660, 0.12), (440, 0.16)],
    "channel_switched.wav": [(560, 0.09), (820, 0.16)],
    "user_joined.wav": [(720, 0.09), (980, 0.16)],
    "user_left.wav": [(720, 0.09), (480, 0.16)],
    "mic_muted.wav": [(520, 0.10), (360, 0.16)],
    "mic_activated.wav": [(360, 0.10), (520, 0.16)],
    "sound_muted.wav": [(480, 0.10), (300, 0.16)],
    "sound_resumed.wav": [(300, 0.10), (480, 0.16)],
    "away_activated.wav": [(400, 0.08), (400, 0.18)],
    "away_deactivated.wav": [(600, 0.08), (760, 0.18)],
    "chat_message_inbound.wav": [(880, 0.09), (1100, 0.12)],
    "chat_message_outbound.wav": [(660, 0.09), (880, 0.12)],
    "you_were_poked.wav": [(900, 0.07), (700, 0.07), (900, 0.11)],
}


def render(pattern):
    frames = []
    gap = 0.025
    for frequency, duration in pattern:
        total = int(SAMPLE_RATE * duration)
        fade = max(1, int(SAMPLE_RATE * 0.012))
        for index in range(total):
            envelope = min(1.0, index / fade, (total - index) / fade)
            sample = math.sin(2.0 * math.pi * frequency * index / SAMPLE_RATE)
            frames.append(int(0.27 * 32767 * envelope * sample))
        frames.extend([0] * int(SAMPLE_RATE * gap))
    return b"".join(struct.pack("<h", value) for value in frames)


def write_wav(path, data):
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(data)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, pattern in PATTERNS.items():
        write_wav(OUTPUT / name, render(pattern))


if __name__ == "__main__":
    main()
