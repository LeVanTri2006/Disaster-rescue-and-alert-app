# P5-E01 isolated Android device harness

This is a standalone **test-only** Android project. It never installs or starts `com.example.bai1`.
The target package is `com.example.bai1.p5e01.isolated`; no credentials, Firebase, Maps, Auth repository,
SOS client, services, receivers or providers are packaged. Its only Activity is a local harness screen.
That screen's error/logout labels are UI smoke fixtures, not the application's UI or authentication flow.

The build compiles the existing production routing algorithm/model/network/download/parser/loader/repository
source directories directly, without changing or mirroring their implementation. `RoutingAppDependencies`
is excluded. The production `AccessTokenProvider` interface alone is extracted with an exact declaration
check to avoid compiling its neighboring SOS implementation. It does not supply authorization authority.
Both network security XML policies are copied from the application's actual variant sources at build time.

Use the installed Java25/SDK and the repository wrapper. All Gradle operations are offline:

```bash
cd /home/vantri/AndroidStudioProjects/bai1
python3 tools/emulator-harness/run_device_tests.py --variant debug --evidence-dir /tmp/p5e01-debug
python3 tools/emulator-harness/run_device_tests.py --variant releaseLike --evidence-dir /tmp/p5e01-release
```

Prerequisites: the existing booted `emulator-5554`, API37, cached dependencies, ADB access. The driver refuses
to overwrite either existing harness namespace, audits merged manifest and APK classes/policy **before**
installing, installs only its own APK/test APK, grants `ACCESS_LOCAL_NETWORK` only to that namespace,
then runs a test-only AndroidJUnitRunner subclass through `adb shell am instrument`. Finally it uninstalls only packages proven
absent before that invocation. No clear-data, emulator wipe, production package launch or device-wide
logcat clear/read. The API37 local-network permission is needed for the actual `10.0.2.2` host fixture;
it is separate from cleartext policy and is not added to the production app.

The host HTTP fixture binds only `127.0.0.1` on an ephemeral port and serves one static health marker.
Other fixtures listen only on device loopback. A negative nonallowlisted-host test maps DNS explicitly
to that same loopback server and verifies Android blocks HTTP before any request. It does not contact
the Internet. Target-process-only logcat is collected while instrumentation runs.

The releaseLike build is nondebuggable, uses the unchanged main/release XML and generated DEBUG=false,
and is locally test-signed. It is a policy harness, not the full production release artifact.

`DeviceXmlListener` exports real JUnit execution events through instrumentation status to
`TEST-device-runtime.xml`; the driver checks actual run count, failures/errors/skips and package cleanup.
This ADB path avoids an unavailable offline AGP UTP `gradle-work-action:32.4.0`; the attempted Gradle
`connectedDebugAndroidTest` failed on that dependency before device execution. No download was used.
Do not run `:app:connectedDebugAndroidTest` against the production application without separate isolation.

Only cached dependency alignments are used in this standalone project: Okio3.9.1, Kotlin stdlib2.2.10,
coroutines Android1.9.0, AndroidX Test runner1.5.2/ext JUnit1.1.5, annotation-experimental1.4.1,
Guava31.1-android, jsr3053.0.2 and lifecycle-common-jvm2.11.0 substitution. None changes app dependencies.

Acceptance evidence and exact limitations are in
`../../docs/PHASE5_P5E01_ANDROID_EMULATOR_TEST_REPORT.md`. Strict routing remains disabled;
F06, production UI, account login/logout, external services and full application E2E are outside this harness.

P5-E01R re-acceptance: debug 18/18 PASS; releaseLike 6/6 PASS. The original two failing assertions
remain unchanged and now pass with the production main XML's explicit localhost deny rule. The added
actual socket matrix proves rejection for IPv4, IPv6 and localhost names on reachable owned fixtures.
Historical P5-E01 failures remain archived in `evidence/release`; new evidence is in `evidence/p5e01r`.
See `../../docs/PHASE5_P5E01R_ANDROID_LOCALHOST_POLICY_REMEDIATION_REPORT.md`.

API37 permission assessment can also be reproduced using only the isolated harness namespace:

```bash
python3 tools/emulator-harness/run_device_tests.py --variant debug --permission-state denied --evidence-dir /tmp/p5e01r-denied
python3 tools/emulator-harness/run_device_tests.py --variant debug --permission-state revoked --evidence-dir /tmp/p5e01r-revoked
```

`denied` leaves the newly installed package permission ungranted; `revoked` grants and then revokes
before instrumentation starts. Both verify that cleartext fixture policy remains permitted while the
actual host bridge connection fails without local-network permission. No production package is granted
permission, installed or started. The production app's runtime permission/UI flow remains outside this
harness; adding a declaration alone would not implement that flow.

The runner waits 1.5 seconds before test execution so PID-scoped logcat can attach. The driver uses
the device start timestamp to include only this run. No production Application initialization is overridden.
