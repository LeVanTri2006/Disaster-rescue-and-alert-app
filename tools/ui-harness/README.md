# Isolated production UI/Auth harness — P5-E02 through P5-E04

This standalone, offline project compiles **unchanged production source** for RegisterScreen,
OnboardingScreen, AboutAppScreen, LoadingState/EmptyState/ErrorState, shared buttons/top bar/theme/models,
the Auth HTTP client and routing consumers. P5-E04 also adds the cloud-free LoginScreen, production
AuthRepository/AuthViewModel/storage and the Firebase session interface. Source-copy equality is checked before installation.
It does not compile MainActivity, AppNavGraph, app DI, the production Google Login overload,
AndroidFirebaseAuthSession, Firebase/GMS/CredentialManager, Maps or SOS business clients. P5-E04
production seams and register validation changes are documented separately below.

**FULL_APP_ISOLATION=BLOCKED; PRODUCTION_UI_E2E=NOT_RUN.** The actual screen subset runs in
IsolatedUiActivity with a clearly labeled test-only NavHost/adapter. Register uses the real Auth HTTP
client against an owned synthetic backend. The original adapter session buttons are separate from the
new explicit Auth scope, which runs actual repository/session logic. Real cloud account behavior and SOS
UI remain unverified by these fixtures.
ROUTING_UI=NOT_IMPLEMENTED; no route UI is added, and strict routing remains disabled.

Run with installed Java25, Gradle wrapper, SDK37/emulator-5554 and cached libraries only:

```bash
cd /home/vantri/AndroidStudioProjects/bai1
python3 tools/ui-harness/run_ui_tests.py --evidence-dir /tmp/p5e02-ui
```

The driver refuses either existing namespace, builds offline, audits merged components/permissions,
actual APK classes and exact source copies, then installs only `com.example.bai1.p5e02.uiisolated` and
its `.test` package. No production app is installed, launched, granted permission or cleared. This
variant removes AndroidX automatic startup/profile receiver; no Firebase SDK/provider is packaged.

Every testcase runs in a fresh instrumentation process. Before each run the driver stops only its newly
installed owned package. Emulator API37 mode additionally revokes ACCESS_LOCAL_NETWORK and clears
its user-set/user-fixed flags so the real OS prompt can be exercised; personal phone mode never does this. The explicit process-restart pair preserves the permission
granted through the OS dialog, force-stops the process, then verifies a new PID retains that permission
without restoring any fixture session. The revocation scenario first grants then revokes that
same package. No existing app, account, private cache or emulator data is touched. The Activity uses
ActivityResultContracts.RequestPermission; grant/deny tests click actual PermissionController buttons.
Native UI Automation accesses only this package or Android/Google PermissionController windows.

The existing cached Espresso3.5.1 cannot initialize its InputManager reflection on API37. The retained
scenarios therefore use AndroidJUnitRunner + native accessibility actions on real Compose UI rather
than Espresso; no SDK/dependency download or production workaround is used. The original tooling
failure is documented in the report. JUnit listener exports actual execution XML; adb exit0 alone is
not PASS. Logcat is restricted to each owned PID and test start time.

HTTP fixture binds host127.0.0.1 ephemeral port only. The adapter fixes all HTTP requests to
10.0.2.2 at that owned port, with origin/path validation both before transport and at the network
interceptor, no redirects/proxy/automatic retry. No cloud, external endpoints, production JWT/database,
R2, email/SMS/push, location uploads or real SOS mutation exists in this graph. Fixture errors include
401/403/500, offline and actual socket timeout; valid synthetic GZIP is unverified reference data.
Only sanitized request metadata is collected; credentials/bodies are never logged.

The driver finally uninstalls only its own packages, even after assertion failures. Screenshots are
actual device captures pulled only from the newly installed package before cleanup. The production
About screen is shown inside the fixture shell, never described as full application E2E.

See `../../docs/PHASE5_P5E02_ISOLATED_MAIN_APP_RUNTIME_REPORT.md` for final counts and limitations.

## P5-E03-USB: personal physical phone

```bash
python3 tools/ui-harness/run_ui_tests.py --physical-usb --evidence-dir /tmp/p5e03-usb
```

Check `df -h / /home` first. The driver also refuses to build below 2 GB free on `/`.
Physical mode requires exactly one authorized USB device, a completed boot and API30–36.
It refuses API37+ until a user-led permission workflow is implemented; it never clicks a
personal phone's OS permission dialog. API37 grant/deny/revoke assertions are excluded,
not counted as passing lower-API tests. No physical screenshots are taken by default.

Both test package IDs must be absent. The same pre-install manifest/DEX/source-copy gates
apply. Only those newly installed packages are stopped/uninstalled. No production package,
existing data or global settings are changed. Serial values in results are redacted.

The server binds an ephemeral laptop `127.0.0.1` port. The driver checks existing reverse
mappings, adds only `tcp:<owned-port>`, and pins the test client and download URL to phone
`127.0.0.1:<owned-port>` over USB. Emulator mode retains `10.0.2.2`. The missing-tunnel test
removes this one mapping, verifies the client fails closed, then restores it for subsequent
cases. Cleanup removes only this task's mapping; `--remove-all` is never used.

Physical coverage includes actual production onboarding/register/about components, synthetic
registration/session adapter, Auth HTTP login API (not LoginScreen), errors/loading,
loopback marker, Activity restart, socket reset, invalid graph parsing, SHA-bound download,
cache reuse/tamper detection/recovery and strict denial. Accessibility scroll actions wait
for Compose animation completion on smaller displays. Startup timing measures ActivityScenario
launch through visible shell; graph timing includes the tiny synthetic USB download and parse.
Neither is a cold-start benchmark or evidence about large production graphs.

See `../../docs/PHASE5_P5E03_USB_REAL_DEVICE_VALIDATION_REPORT.md` for actual phone execution,
initial tooling failures and remaining full-app isolation blockers. Full application E2E,
LoginScreen, AuthRepository lifecycle, Maps and SOS remain outside this isolated graph.

## P5-E04-USB: actual Login UI and injected production Auth lifecycle

Physical mode now defaults to the complete old USB suite plus new Auth cases. Counts are taken
from execution XML, with the old 16 names compared separately against the P5-E03 evidence;
repeated focused/development runs are not added as unique cases. API37 emulator mode keeps its
existing scenarios and excludes the new physical-only Auth methods. No emulator evidence is used
for P5-E04 acceptance.

The source-copy gate now covers 18 originals: it additionally compiles the cloud-free production
LoginScreen, registration validator, AuthRepository, AuthViewModel, TokenStorage (including actual
SharedPreferences implementation), and FirebaseAuthSession interface. ProductionLoginScreen and
AndroidFirebaseAuthSession implementations are deliberately excluded; the APK still must contain
no Firebase/GMS/CredentialManager SDK classes, app DI, MainActivity/AppNavGraph, Maps or SOS clients.
The main application's existing LoginScreen call resolves to the production overload; only a
Google-button click creates CredentialManager. The isolated overload requires an explicit callback.

Auth scenarios use real repository/view-model logic and SharedPreferences in the newly installed
package. A no-cloud Firebase session adapter records invocation counts; `/auth/me`, `/auth/refresh`
and `/auth/firebase-token` are synthetic responses from the pinned USB fixture, never forwarding.
An Activity-scoped test-owned ViewModelStore is explicitly cleared on destruction; recreated
Activities restore the synthetic saved session with a new production AuthViewModel. This verifies
storage/repository restoration, not the production AppNavGraph's ViewModel retention/navigation.
The ordered process-restart pair preserves only this owned storage, force-stops the owned package,
asserts a new PID, restores, then logs out. Other Auth cases reset only the owned TokenStorage.

Negative register cases exercise actual production validation and assert that the submit callback
was never invoked; host request metadata independently shows no HTTP register request. Terms text
and the pre-existing checked default are preserved; the checkbox now gates submission. Phone
validation keeps the existing DTO's string length contract rather than introducing a new numbering
policy. No fixture password/token literal is included in reports or collected log output.

MainActivity/AppNavGraph/Maps/SOS remain outside this harness. Credential account-picker/cloud
success, production server authentication, real Firebase expiry cleanup and arbitrary concurrent
login/logout races are not certified by these fixture tests.

## P5-E05-USB: auth epochs, cancellation and adapter-effect ordering

Physical mode runs all old44 cases, twelve added local-HTTP/UI lifecycle cases and the exact shared
`AuthLifecycleSafetyTest` suite copied from the JVM test tree. Copy equality/SHA is checked before
installation. Each method still emits real JUnit XML; source annotations only schedule methods.
The shared suite is injected-adapter orchestration executed on Android, while the HTTP/UI lifecycle cases
also cross the actual USB reverse tunnel. These are distinct evidence scopes.

Host barriers use explicit entered/release events per owned test ID. The client snapshots a test-only
barrier header on the selected auth endpoint; newer requests can proceed without releasing older
responses. Completion is awaited through actual coroutine jobs and storage write counters, not an
assumed delay. Server waits and polling deadlines are deadlock bounds; observed gate state establishes
ordering. Actual SharedPreferences storage is wrapped only to count save calls, never to replace it.

Production AuthRepository now assigns a generation to each session-mutating operation, checks the
originating Job before writes and rejects stale completions. Logout revokes local state/storage/routing
before suspendable cleanup. AuthViewModel tracks its own jobs so clearing an old VM cannot revoke an
operation owned by a different VM, and completed saved sessions survive ordinary recreation. SDK
adapter effects have a separate serialized lane; in-flight sign-in completion is compensated before
newer SDK effects. Cleanup exceptions leave `firebaseCleanupPending=true`; they are never called a
successful real SDK sign-out. Backend HTTP authentication remains independent of Firebase sync, as
before. NoCloud tests do not certify real Firebase SDK cleanup or callback timing.

AppNavGraph received optional AuthViewModel and credential-cleanup injection seams, retaining its
production defaults. Its SOS/Maps/screen graph and process providers still require broader isolation,
so MAIN_GRAPH_ISOLATION=BLOCKED and PRODUCTION_APP_NAV_GRAPH=NOT_RUN. No main app install or routing
UI was added. See the P5-E05 report for acceptance by subscope and remaining limits.


## P5-E06-USB: actual MainActivity and AppNavGraph

The standalone application now implements `MainAppHost` and launches the exact production
`MainActivity` and `AppNavGraph` sources. The core graph still owns every route, argument,
navigation callback and back-stack operation. `AppNavScreens` supplies screen construction only.
Production `DisasterApplication` selects production defaults; only SOS/Rescue construction is
now deferred until a dependent destination. Production Firebase/Google providers remain in the
main APK and are never installed on the personal phone by this harness.

The standalone manifest uses `GraphFixtureApplication`, two non-exported Activities (the legacy
subset host for old76 and actual MainActivity), no providers/services/receivers/SDK metadata.
Only its own synthetic SharedPreferences session and pinned loopback client can be constructed.
All 26 production copies, shared repository tests, both DEX files, manifests, application startup
wiring and absence of production configuration values are audited before install. Every observed
request Host must equal the owned loopback address/port. Package absence, no replace install,
PID-scoped logs and owned-only cleanup remain mandatory.

`ProductionGraphUiTest` exercises actual Splash/Onboarding/Login/Register/About UI and actual
AuthRepository/AuthViewModel navigation. Home, role selection, profile/settings and every other
screen-factory destination use explicitly labelled unavailable UI with actual graph callbacks.
These cases certify graph transitions only for those factories; they do not certify the production
Maps/SOS/Rescue/Home/Profile/Settings UI, real Google account picker or Firebase SDK lifecycle.
The old76 still run unchanged in behavior; the old class-absence check now forbids production
Application/default screen bindings instead of the newly allowed core Activity/graph.

Revoked or rejected sessions leave protected routes for Login, clearing prior protected history.
Protected screen construction is suppressed while restore is unresolved or unauthenticated.
The role-selection completion removes its own entry. New navigation JVM policy checks and
physical back-stack/recreation/process-restart cases cover these changes. Full E2E remains NOT_RUN;
F06 remains OPEN, strict routing DISABLED, and no routing UI is implemented.


## P5-E07-USB: actual account screens and optional Home smoke

The new standalone-only `ActualAccountFixtureScreens` overrides the E06 factory's Role Selection,
Profile, Settings and Home methods with exact production Composables. Old99 keep their E06 screen
factory and assertions. The new binding is selected only by the owned instrumentation Intent;
it is absent from the production APK and changes no authentication or routing authorization.

Production Role Selection now requires an injected actual RoleSelectionViewModel in its core UI;
a production-only overload preserves its original default repository/owner. Profile's core takes
AuthState/UserRole, with its original production overload collecting the same repositories.
Settings and Home source remain unchanged: they use only in-memory sample data/local UI switches.
All 35 production source copies are audited byte-for-byte; main cloud bindings remain excluded.
The synthetic role PATCH fixture accepts USER only and rejects RESCUER/ADMIN. Existing visual
readiness/verification labels and Settings switches grant no rescue qualification or permission.

A physical before/after case reproduces lost USER selection on Activity recreation. The UI uses
rememberSaveable for selection and ignores option changes during Loading. New physical tests also
await real role ViewModel jobs for stale completions and verify new NavController composition on
recreation. Settings accessibility uses owned nodes' native checkable/checked semantics (this
Compose version reports android.view.View rather than android.widget.Switch); three-switch and
actual state-change assertions remain mandatory. Existing switches reset on recreation per the
unchanged remember contract; no OS permission/system setting is changed.

Profile currently displays name/role, local mock avatar/stats/certificate copy, and navigation;
email/phone, edit, explicit loading/error widgets and a Profile logout button are not implemented.
Logout is tested through actual Settings UI and actual graph callbacks. Home smoke covers USER
branch/static demo data only. Maps/SOS/Rescue remain unavailable, real Firebase cleanup NOT_RUN,
full application E2E NOT_RUN, F06 OPEN and strict routing DISABLED.


E07 also found an Android-only nullable-user parsing defect in the first complete 129-case run:
JSON null became literal text "null", so actual Profile did not receive a missing name. The same
physical case passes after checking JSONObject.isNull before optional string conversion; non-null
values, literal "null" strings, token parsing and authorization semantics are preserved. Two JVM
contract cases pass both before/after with the cached org.json implementation, so JVM PASS is not
presented as Android defect reproduction. The initial 128-pass/1-fail run and final fresh129 run
are kept separately, with all old99 rerun after remediation.
