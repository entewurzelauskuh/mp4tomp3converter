#!/usr/bin/env bash
#
# Generates the tiny committed test-media fixtures used by the engine instrumented tests
# (spec §9.2). Requires ffmpeg (`brew install ffmpeg` on the dev mac).
#
# ffmpeg is a DEV TOOL ONLY — it is never shipped in the app. The generated .mp4s are small
# (< 200 KB total) and committed under app/src/androidTest/assets/ so tests run without ffmpeg.
#
# Fixtures:
#   sine_stereo_aac_3s.mp4   44.1 kHz stereo AAC, 3.0 s tone         -> success (stereo)
#   sine_mono_aac_3s.mp4     44.1 kHz mono   AAC, 3.0 s tone         -> success (mono)
#   video_only_no_audio.mp4  tiny video track, no audio             -> FailureReason.NoAudioTrack
#   sine_6ch_aac_3s.mp4      5.1 (6ch) AAC, 3.0 s tone              -> UnsupportedChannelLayout
set -euo pipefail

OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/app/src/androidTest/assets"
mkdir -p "$OUT_DIR"

command -v ffmpeg >/dev/null 2>&1 || { echo "ffmpeg not found (brew install ffmpeg)"; exit 1; }

# Stereo AAC (audio-only mp4).
ffmpeg -y -f lavfi -i "sine=frequency=440:duration=3:sample_rate=44100" \
  -ac 2 -c:a aac -b:a 64k "$OUT_DIR/sine_stereo_aac_3s.mp4"

# Mono AAC.
ffmpeg -y -f lavfi -i "sine=frequency=440:duration=3:sample_rate=44100" \
  -ac 1 -c:a aac -b:a 48k "$OUT_DIR/sine_mono_aac_3s.mp4"

# Video-only, no audio track.
ffmpeg -y -f lavfi -i "testsrc=duration=1:size=128x96:rate=10" \
  -an -c:v mpeg4 -q:v 10 "$OUT_DIR/video_only_no_audio.mp4"

# 5.1 (6-channel) AAC to drive UnsupportedChannelLayout.
ffmpeg -y -f lavfi -i "sine=frequency=440:duration=3:sample_rate=44100" \
  -af "channelmap=0|0|0|0|0|0:5.1" -ac 6 -c:a aac -b:a 96k "$OUT_DIR/sine_6ch_aac_3s.mp4"

echo "Wrote fixtures to $OUT_DIR:"
ls -la "$OUT_DIR"
echo "Total size:"; du -ch "$OUT_DIR"/*.mp4 | tail -1
