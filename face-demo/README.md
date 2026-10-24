# Face recognition demo (laptop stand-in for the glasses pipeline)

Webcam -> local MediaPipe face detector (every frame) -> padded, small face crop ->
CompreFace `recognize` (only for a new face, ~2.5 req/s max) -> name, similarity and
round-trip latency drawn on the video. This mirrors the planned flow: glasses stream ->
Android app -> face crops -> CompreFace on the laptop -> spoken name.

Everyone enrolled must have consented. Matches are probabilistic; the demo shows
"Unknown" below the threshold instead of guessing.

## Prerequisites

- Docker Desktop running (allow it at least ~6 GB RAM; the CompreFace API container uses a 4 GB JVM heap).
- Python 3.9 to 3.12 (3.11 tested). MediaPipe wheels do not exist for newer Pythons.
- Webcam access for your terminal app (macOS: System Settings > Privacy & Security > Camera).

## Apple Silicon note

CompreFace 1.2.0 (latest stable) only publishes `linux/amd64` images, so on an M-series Mac
they run under emulation. Expect a one-time warm-up of ~30 s on the first request after
the server starts, then fast calls. `docker compose ps` may label `compreface-core` as
`unhealthy`: its healthcheck has a 1 s timeout, too tight under emulation. It still works.
Warm it up before a demo by hitting the endpoint once (see step 7 below).

## 1. Start the CompreFace server

```bash
cd face-demo/compreface
docker compose up -d      # first run pulls ~2 GB
```

Open http://localhost:8000. The compose file and `.env` there are the unmodified official
v1.2.0 release config. Stop later with `docker compose down` (data persists in a Docker volume).

## 2. Create an account and API key (manual, in the UI)

1. On http://localhost:8000 click **Sign Up**, create the owner account, log in.
2. **Create Application** (any name, e.g. `myst`).
3. Open the application, **Add Service**, choose type **Recognition**, give it a name.
4. In the service list, click the copy icon next to the service's **API key**.

## 3. Python setup and `.env`

```bash
cd face-demo
python3.11 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env      # then paste the API key into COMPREFACE_API_KEY
```

## 4. Add photos

Create `faces/<person_name>/` folders with 10 to 20 photos each. See `faces/README.md`.

## 5. Enroll

```bash
python enroll.py             # uploads every faces/<name>/ folder
python enroll.py --replace   # on re-runs: wipe each person's old faces first (avoids duplicates)
```

Prints a per-person summary and lists photos skipped for having no face.

## 6. Run the live demo

```bash
python live_demo.py              # window titled "Myst face demo"
python live_demo.py --speak      # also say each new person's name (macOS `say`, else pyttsx3)
```

Keys: `q` / Esc quit, `=` / `-` raise or lower the threshold by 0.01 live.
Other flags: `--threshold`, `--max-rps` (default 2.5), `--crop-size` (default 160 px), `--camera`.

Overlay: green box = recognized, orange = Unknown, grey = searching. Text shows
`name similarity latency_ms`. A recognized name is cached while that face stays in view; a new
face (or the same face returning after leaving) triggers a new query. Unknown results are
retried at the throttle rate in case lighting or pose improves. With several faces in
frame, only the largest is tracked.

## 7. Tuning the threshold

`SIMILARITY_THRESHOLD` in `.env` (default 0.85), or `--threshold`, or `=`/`-` while running.

1. Stand in front of the camera as an enrolled person and note the similarity shown.
2. Have a non-enrolled person (or a photo of a stranger) look at it and note their highest score.
3. Set the threshold between the two, nearer the stranger's score to avoid false names.
   Higher = fewer wrong names but more "Unknown"; lower = the opposite.

Warm-up before a demo: after `docker compose up`, run one recognition (or just start
`live_demo.py` and show a face once) so the emulated model is loaded.

## Tests

```bash
pip install -r requirements-dev.txt && python -m pytest
```

## Files

| Path | Purpose |
|------|---------|
| `compreface/` | Official CompreFace 1.2.0 `docker-compose.yml` + `.env` |
| `common.py` | `.env` config and CompreFace REST client |
| `tracking.py` | Pure state machine: when to query, caching, new-face detection |
| `enroll.py` / `live_demo.py` | The two scripts |
| `faces/` | Your photos (gitignored) |
