# Evidence tools (test tools only)

Nothing here is part of any Maven module, jar or production build. Everything is run by hand on a test machine.

| File | Purpose |
|---|---|
| `DesktopLivenessHarness.java` | Feeds a local MJPEG stream (webcam through ffmpeg, or a recorded file) into the existing `SbiStreamFrameSource` + `DesktopLivenessService` and prints the final state, the challenge types shown and timings. It changes no existing class. |
| `make_mjpeg.py` | Makes a multipart MJPEG test file from one JPEG, to test the harness's parser before any face is involved. Never commit its output. |

**Every result from the harness is "Desktop engine via local webcam harness, not inside the running client".** It is not the
real client, it has no SBI device, and it does not exercise the gates, the popup, the audit table or the global parameter.

## What it prints (and does not)

Printed: `final_state`, `attempts`, `attempt_failures`, `challenges_shown`, `time_to_decision_ms`, `frames_read_from_source`
and `engine_timing` (frames, mean ms, worst-window p95 ms; a true p95 cannot be rebuilt from the engine's per-window
records). Not printed or stored: scores, thresholds, model names, frames, crops. The temporary data folder holds only the
verified model files and the config cache. Frames are read as JPEG with `mirrored=false`, the same as the Desktop client's SBI path.

By default a failed attempt is retried automatically (like pressing "Try again") until `SUCCESS` or `MAX_RETRIES`, so one
run is one registration attempt as the protocol defines it. `--no-retry` stops at the first attempt failure.

## Build and run

Needs JDK 11 and the built `registration-services` module (`mvn -o install -DskipTests` in `registration/`).

```sh
cd registration
mvn -B -o -q -pl registration-services dependency:build-classpath -Dmdep.outputFile=/tmp/services-cp.txt
CP="registration-services/target/classes:$(cat /tmp/services-cp.txt)"
mkdir -p /tmp/harness-classes
javac -cp "$CP" -d /tmp/harness-classes ../tools/evidence/DesktopLivenessHarness.java

# 1. Parser/pipeline check with a recorded file (no face needed; use any test JPEG)
python3 ../tools/evidence/make_mjpeg.py some-test.jpg /tmp/test.mjpeg 90
java -cp "/tmp/harness-classes:$CP" DesktopLivenessHarness --file /tmp/test.mjpeg --fps 15 --loop

# 2. Live webcam through ffmpeg (see below), harness reads the URL
java -cp "/tmp/harness-classes:$CP" DesktopLivenessHarness --url http://127.0.0.1:8081/ --timeout-seconds 120
```

Options: `--workflow RESIDENT|OPERATOR|SUPERVISOR`, `--config signed.json` (a signed config such as
`docs/liveness/demo-global-param.json`; without it the bundled default is used), `--no-retry`, `--timeout-seconds N`.
On Windows replace `:` by `;` in the classpath and use `\` paths.

## MockMDS (`--mds-port`)

`--mds-port 4501` talks to an SBI device service the way the Desktop client does: `MOSIPDINFO` on `/info` to find the Face
device, then the `STREAM` request (request body and method copied from `MosipDeviceSpecification_095_ProviderImpl.stream`
for spec 0.9.x and `MosipDeviceSpecification_SBI_1_0_ProviderImpl.stream` for spec 1.x; same Apache `RequestBuilder` and
entity). It reads the multipart JPEG stream that comes back.

MockMDS from `mosip/mosip-mock-services` tag `v1.2.0.2` (what we did, from its README and `application.properties`):
* `mvn clean install -Dmaven.test.skip=true -Dgpg.skip=true` in `MockMDS/` with JDK 11. With Maven 3.10 the tag's `pom.xml`
  fails with "dependencies.dependency ... must be unique: com.squareup.okhttp:okhttp:jar" because `okhttp 2.7.5` is
  declared twice; we deleted the second declaration in a scratch copy (nothing in this repository changed).
* Run from `target/`: `java -cp "mock-mds-1.2.0.2.jar:lib/*" io.mosip.mock.sbi.test.TestMockSBI "mosip.mock.sbi.device.purpose=Registration" "mosip.mock.sbi.biometric.type=Biometric Device"` (the arguments of its `run.sh`).
  It listens on port 4501, its own keystores are bundled, and no partner registration was needed for `MOSIPDINFO` or `STREAM`.
* It answers with spec version `0.9.5` and three devices: Finger (`deviceId 2`), Face (`3`), Iris (`1`). The Face stream is one fixed JPEG
  (`Biometric Devices/Face/Stream Image/0.jpeg`) sent as multipart (`--BoundaryString`, `Content-Length`) at about 29 frames per second.

## ffmpeg commands for the webcam: UNTESTED until we run them

ffmpeg is a tool on the test PC, not a dependency of this repository. The two commands below have **not** been run with
a webcam yet. Start ffmpeg first, then the harness.

macOS (list cameras, then stream camera 0):
```sh
ffmpeg -f avfoundation -list_devices true -i ""
ffmpeg -f avfoundation -framerate 15 -video_size 640x480 -i "0" -c:v mjpeg -q:v 5 -f mpjpeg -listen 1 http://127.0.0.1:8081/
```

Windows (list cameras, then stream the named camera):
```bat
ffmpeg -list_devices true -f dshow -i dummy
ffmpeg -f dshow -framerate 15 -video_size 640x480 -i video="<camera name from the list>" -c:v mjpeg -q:v 5 -f mpjpeg -listen 1 http://127.0.0.1:8081/
```

What *was* checked (macOS, no webcam, no face): the harness's parser reads ffmpeg's own `-f mpjpeg` output from a file made
with `-f lavfi -i testsrc` (60 of 60 frames read) and reads the file written by `make_mjpeg.py`.

## Tests it can and cannot stand in for

* Can: engine on live frames (passive PAD, challenges, retries), time to decision, per-frame engine timing, attacks
  shown to a webcam.
* Cannot: SBI device discovery and streaming, the JavaFX popup, the three gates, `AuditManagerService` rows,
  global-parameter sync, offline restart of the client.
* MockMDS (`mosip-mock-services` tag `v1.2.0.2`) serves fixed images from `files/MockMDS/` and has no camera code, so it can
  exercise the SBI stream path and error states (device stopped), not attacks.
* The real Desktop client needs a MOSIP environment and a Windows 10 machine with TPM 2.0
  ([installation guide](https://docs.mosip.io/1.2.0/id-lifecycle-management/identity-issuance/registration-client/deploy/registration-client-installation-guide)).
  We have neither unless the outreach to MOSIP succeeds.
