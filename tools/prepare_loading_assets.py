"""Build the six approved eyecatches. Run with uv run --with av --with pillow.

Original WebM files are retained; the client uses bundled PNG atlases and PCM audio,
so startup never requires network access, Python, or a video decoder.
"""
import array
import hashlib
import io
import json
import math
from pathlib import Path
import urllib.request
import wave

import av
from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/yachiyoc/loading"
ORIGINALS = OUT / "originals"
ATLAS_SCALE = 0.75
NAMES = [
    ("s1e02_01", "152fm5fqi20hvrt", 2, 1),
    ("s1e02_02", "fgvmiqjg099r1t9", 2, 2),
    ("s1e02_03", "cnhncvr0dyqjg4o", 2, 3),
    ("s1e02_04", "16ef2b6pmwtk7qq", 2, 4),
    ("s1e07_01", "wkg0ps444ng92ea", 7, 1),
    ("s1e07_02", "asv8q10o36ljmvy", 7, 2),
]


def read_clip(name, key, episode, clip):
    filename = f"小林さんちのメイドラゴン {episode}話　アイキャッチ{clip}.webm"
    from urllib.parse import quote
    url = f"https://dl.dropboxusercontent.com/s/{key}/{quote(filename)}"
    target = ORIGINALS / f"{name}.webm"
    if not target.exists():
        target.write_bytes(urllib.request.urlopen(url, timeout=40).read())
    raw = target.read_bytes()
    frames, times, bounding = [], [], None
    last_time = 0
    with av.open(io.BytesIO(raw)) as container:
        stream = container.streams.video[0]
        fps = float(stream.average_rate)
        for frame in container.decode(video=0):
            rgb = frame.to_image().convert("RGB")
            width, height = rgb.size
            # Only the black center symbols are animated. Keep their antialiased
            # coverage in alpha and draw the original yellow as one shared fill.
            mask = rgb.getchannel("R").point(lambda red: max(0, min(255, round((220-red)*255/220))))
            region = (width//10, height//3, width*9//10, height*2//3)
            central = Image.new("L", rgb.size)
            central.paste(mask.crop(region), region[:2])
            bbox = central.point(lambda value: 255 if value > 16 else 0).getbbox()
            if bbox:
                bounding = bbox if bounding is None else (
                    min(bounding[0], bbox[0]), min(bounding[1], bbox[1]),
                    max(bounding[2], bbox[2]), max(bounding[3], bbox[3]))
            last_time = round(float(frame.time or 0) * 1000)
            # Drop only repeated holds / tiny codec noise, retaining actual motion
            # and the source timestamps. This avoids uploading dozens of duplicates.
            if frames:
                differences = ImageChops.difference(frames[-1],central).histogram()
                if sum(differences[33:]) <= 8:
                    continue
            frames.append(central)
            times.append(last_time)
    origin = times[0]
    times = [time-origin for time in times]
    duration = last_time-origin + round(1000/fps)
    left, top, right, bottom = bounding
    bounding = (max(0,left-8), max(0,top-8), min(width,right+8), min(height,bottom+8))
    crop_w, crop_h = bounding[2]-bounding[0], bounding[3]-bounding[1]
    atlas_scale = ATLAS_SCALE
    while True:
        frame_w, frame_h = max(1,round(crop_w*atlas_scale)), max(1,round(crop_h*atlas_scale))
        cell_w, cell_h = frame_w+4, frame_h+4
        columns = min(len(frames), max(1, 2048//cell_w))
        atlas_w, atlas_h = columns*cell_w, math.ceil(len(frames)/columns)*cell_h
        if max(atlas_w,atlas_h) <= 2048: break
        atlas_scale *= 0.85
    atlas = Image.new("RGBA", (atlas_w,atlas_h))
    for index, mask in enumerate(frames):
        tile = Image.new("RGBA", (frame_w,frame_h))
        tile.putalpha(mask.crop(bounding).resize((frame_w,frame_h), Image.Resampling.LANCZOS))
        atlas.paste(tile, ((index%columns)*cell_w+2, (index//columns)*cell_h+2))
    atlas_target = OUT / f"{name}.png"
    atlas_temp = OUT / f"{name}.new.png"
    atlas.save(atlas_temp, optimize=True)
    atlas_temp.replace(atlas_target)
    pcm = bytearray()
    with av.open(io.BytesIO(raw)) as container:
        resampler = av.AudioResampler(format="s16", layout="stereo", rate=44100)
        for frame in container.decode(audio=0):
            for output in resampler.resample(frame):
                pcm.extend(bytes(output.planes[0])[:output.samples*4])
        for output in resampler.resample(None):
            pcm.extend(bytes(output.planes[0])[:output.samples*4])
    record = dict(id=name, source=url, sha256=hashlib.sha256(raw).hexdigest(),
                  audio=f"{name}.wav",
                  sourceWidth=width, sourceHeight=height, durationMs=duration,
                  x=bounding[0], y=bounding[1], width=crop_w, height=crop_h,
                  frameWidth=frame_w, frameHeight=frame_h, columns=columns,
                  atlasWidth=atlas_w, atlasHeight=atlas_h, frameTimes=times)
    print(f"{name}: {len(frames)} frames, {duration} ms, atlas {atlas_w}x{atlas_h}, {len(raw)} source bytes", flush=True)
    return record, pcm


def similarity(first, second):
    a, b = array.array("h",first), array.array("h",second)
    # Compare both recordings on the same clock; lossy encoders may differ by a few samples.
    a, b = a[::20], b[::20]
    best = 0.0
    for lag in range(-40,41):
        x, y = (a[-lag:],b) if lag < 0 else (a,b[lag:])
        pairs = list(zip(x,y))
        product = sum(u*v for u,v in pairs)
        energy = math.sqrt(sum(u*u for u,v in pairs)*sum(v*v for u,v in pairs))
        if energy: best = max(best,product/energy)
    return best


def main():
    ORIGINALS.mkdir(parents=True,exist_ok=True)
    clips, audio = [], []
    for spec in NAMES:
        record, pcm = read_clip(*spec)
        clips.append(record)
        audio.append(pcm)
        with wave.open(str(OUT / record["audio"]),"wb") as sound:
            sound.setnchannels(2)
            sound.setsampwidth(2)
            sound.setframerate(44100)
            sound.writeframes(pcm)
    matches = [round(similarity(audio[0],sample),5) for sample in audio]
    # Retain the first cue as well as every clip's own synchronized original sound.
    with wave.open(str(OUT / "cue.wav"),"wb") as sound:
        sound.setnchannels(2)
        sound.setsampwidth(2)
        sound.setframerate(44100)
        sound.writeframes(audio[0])
    manifest = dict(version=1, background="FFF9F044", audio="cue.wav", audioSimilarity=matches, clips=clips)
    (OUT / "manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print("Original-audio similarities (each clip retains its own track):", matches, flush=True)


if __name__ == "__main__":
    main()
