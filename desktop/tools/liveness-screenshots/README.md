# Liveness screen screenshots

Renders the **real** Desktop face-liveness screen (`FaceLiveness.fxml`, `liveness.css`, the `labels_*` bundles and
`FaceLivenessController`) for every state and saves PNGs. No SBI device, engine, database or MOSIP environment is
involved: the service is a stub and the preview is a synthetic silhouette (never a real person's image).

```sh
# 1. Build the client (JDK 11) so registration-client/target/classes exists
cd registration && mvn -B -pl registration-client -am install -DskipTests

# 2. Runtime classpath
mvn -B -q -pl registration-client dependency:build-classpath -Dmdep.outputFile=/tmp/client-cp.txt

# 3. Compile the tool and run it for one language (en, fr or ar)
CLS=registration-client/target/classes
javac -cp "$CLS:$(cat /tmp/client-cp.txt)" -d /tmp/tool-classes \
  ../tools/liveness-screenshots/io/mosip/registration/controller/liveness/LivenessScreenshots.java
java -cp "/tmp/tool-classes:$CLS:$(cat /tmp/client-cp.txt)" \
  io.mosip.registration.controller.liveness.LivenessScreenshots out en
```

Notes

* The scene is rendered off-screen (`Scene.snapshot`). JavaFX 11.0.2 crashes in its macOS window code on current
  macOS versions, and snapshots do not need a visible window.
* JavaFX 11.0.2 ships macOS natives for Intel only. On Apple silicon run step 3 with an x64 JDK 11 under Rosetta:
  `arch -x86_64 /path/to/x64/jdk-11/bin/java ...`.
* The same old JavaFX cannot map the macOS system UI font and prints garbage. For screenshots only, pass
  `-Dliveness.font=Arial`. The product stylesheet is not changed.
* `-Dliveness.reduceMotion=true` turns off the ring animations.
