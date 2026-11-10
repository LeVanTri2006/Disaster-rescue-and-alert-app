# PHASE 5 – P5-E07-USB: Production Profile, Settings & Role Selection UI

Ngày thực thi 2026-11-08, Asia/Bangkok. **PASS — P5-E07-USB UI SCOPE**. Full application/cloud E2E NOT_RUN.

## 1. Executive Summary

Actual production **RoleSelectionScreen, ProfileScreen, SettingsScreen** đã chạy trên OPPO USB qua actual MainActivity/AppNavGraph, không dùng unavailable screen làm bằng chứng cho ba UI này. HomeScreen source được audit là local/static; actual USER Home UI được smoke-test. Cloud destinations khác giữ unavailable bindings và NOT_RUN.

Đã tái hiện lỗi Role Selection mất lựa chọn USER khi Activity recreate: **before1test/1failure**, sau sửa cùng testcasePASS. UI giữ lựa chọn bằng rememberSaveable và không đổi lựa chọn khi đang Loading. Một lỗi thứ hai được phát hiện trong complete intermediate run: Android JSON null bị parse thành chuỗi "null". Cùng physical Profile-null testcase chuyểnFAIL->PASS sau optional-string null guard. Auth epochs, token/error parsing, authorization và main cloud configuration giữ nguyên.

Final physical **129 unique cases = old99 + new30**, failures/errors/skipped0, all instrumentation/driver exit0. Routing fresh462/13suites; full JVM545/32suites; Auth/navigation42/8suites, tất cả0failures/errors/skipped/exit0. APK buildPASS.

```text
MAIN_ACTIVITY_ISOLATED = PASS
PRODUCTION_APP_NAV_GRAPH = PASS (tested Auth/account UI scope)
PRODUCTION_ROLE_SELECTION_UI = PASS
PRODUCTION_PROFILE_UI = PASS (implemented name/role/navigation contract)
PRODUCTION_SETTINGS_UI = PASS (existing local UI/navigation/logout contract)
PRODUCTION_HOME_UI = PASS (USER branch smoke; static demo data)
MAIN_GRAPH_ISOLATION = CONDITIONAL (remaining cloud destinations)
MAPS_UI = NOT_RUN
SOS_UI = NOT_RUN
RESCUE_UI = NOT_RUN
ROUTING_UI = NOT_IMPLEMENTED
FULL_APPLICATION_E2E = NOT_RUN
REAL_FIREBASE_SDK_CLEANUP = NOT_RUN
STRICT_ROUTING_DISABLED = true
F06 = OPEN
FINAL_ACCEPTANCE = PASS — P5-E07-USB UI SCOPE
```

[Actual final JUnit129](../tools/ui-harness/evidence/p5e07-usb/physical-final/TEST-production-ui.xml), [per-case commands/events](../tools/ui-harness/evidence/p5e07-usb/physical-final/results.json), [coverage groups](../tools/ui-harness/evidence/p5e07-usb/coverage-groups.json). Historical baseline99/540 không được dùng thay kết quả mới.

## 2. Device and Disk Inventory

Trước sửa/build: root8,331,784,192bytes free (~7.8G), /home10,834,829,312bytes (~11G), phone/data108G total/100G used/8.3Gavailable. Root>=2GB gatePASS, runner/JVM script recheck trước major builds. Không xóa Docker/containerd/Snap/Gradle/SDK/data. [Before](../tools/ui-harness/evidence/p5e07-usb/environment-before.json), [after](../tools/ui-harness/evidence/p5e07-usb/disk-after.txt).

Inventory thực tế đúng một USB authorized **OPPO CPH2159 / Android13 / API33**, device state device, boot1, qemu not1. Serial chọn từ inventory, target mọi device command và lưu REDACTED. Không emulator, không bypass USB authorization. Phone vẫn connected/authorized sau cleanup. [Device/cleanup](../tools/ui-harness/evidence/p5e07-usb/device-and-cleanup.json).

Java/javac25.0.4.1, Gradle9.7.1; AGP9.4.0/Kotlin Compose plugin2.2.10 giữ nguyên. Gradle embedded Kotlin2.4.0 không phải plugin upgrade. Chạy offline/no-daemon, không download dependency/SDK. [Versions](../tools/ui-harness/evidence/p5e07-usb/tool-versions.txt). API37-only permission cases không áp cho API33 và không được cộng vào physical total.

## 3. Repository Baseline

Đọc đầy đủ baseline reports E06/E05/E04/E03 và actual sources trong task. Android `/home/vantri/AndroidStudioProjects/bai1`; Backend `/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app` READ ONLY. pwd/status/diff-check trước sửa; no applicable AGENTS.md trong ancestors/repo. Dirty/untracked từ tasks trước giữ nguyên.

Pre-edit snapshot SHA-256 + mtime_ns: **1,453Android files,677Backend files**, gồm untracked source/docs/evidence. Exclude `.git/.gradle/build/dist/node_modules/.idea/.kotlin`; không claim IDE/generated caches immutable. Final Android **1,445baseline files unchanged,8edited,0missing**;6new implementation/test files plus report/evidence. Backend677/677unchanged và0new/removed. Không reset/clean/restore/commit/push/deploy. [Android baseline/preservation](../tools/ui-harness/evidence/p5e07-usb/android-preservation.json), [Backend baseline/preservation](../tools/ui-harness/evidence/p5e07-usb/backend-preservation.json).

## 4. Production Screen Dependency Audit

[Dependency table recorded before edits](../tools/ui-harness/evidence/p5e07-usb/dependency-audit-before-edits.md). Source audit không suy từ screen name:

| Screen/component | Params, VM/repository | SDK/network/real token/mutation | Isolation/default/source decision |
|---|---|---|---|
| Role Selection | onContinue, default destination-scoped RoleSelectionViewModel(Auth DI); Idle/Loading/Success/Error; selectedRole initialRESCUER | Actual VM calls AuthRepository.updateRole with stored bearer -> PATCH/users/me/role; no direct SDK; production server mutation possible | Core requires injected actual VM; production-only overload keeps original Auth DI factory/owner. Standalone actual repository and USER-only local PATCH fixture |
| Profile | four callbacks; no own VM; directly collected AuthAppDependencies state and DisasterRepository role | No own HTTP/SDK, but default Auth DI could expose real saved account; local avatar/stats/certificate UI | Core requires AuthState/UserRole; original production overload collects same original state. Actual synthetic user UI, no model replacement or profile write |
| Settings | four callbacks; no VM; DisasterRepository role;3remember Boolean switches | No SDK/network/token/system setting/subscription; most arrow actions no-op; logout delegated | Exact original source copied. Test real local switches/About/back/logout; no OS permission granted |
| Home |5callbacks; no VM; DisasterRepository role/static profile/warnings, local drawables/cards | No Firebase/Maps/SOS/API init, no token read or network; navigation delegated | Exact original source/dependencies; USER smoke only. Static demo data not live hazard/current account |
| MainActivity/AppNavGraph | E06 MainAppHost + AppNavScreens;27 actual routes/callbacks | Main production defaults still Auth/Routing/cloud-capable screens/providers | Source unchanged byte/hash/mtime. Account fixture factory overrides only4screen constructors, no alternate NavHost |
| Auth DI/repository/VM | Saved tokens, FirebaseAuthSession, HTTP client; VM restore | Production Firebase/backend still unsafe if wired into standalone | Default classes excluded, actual repository/VM injected with isolated storage/no-cloud adapter, E05 lifecycle source unchanged |
| SOS/Rescue/Routing DI/SDKs | Real API/token/realtime/location constructors; production screen defaults | Cloud/SOS/realtime possible in production | Production bindings/providers remain main-only, excluded standalone. Routing injected download-only repository remains strictfalse |
| DisasterRepository | MutableStateFlow role and static sample datasets | No IO/network/SDK; role/display sample data only | Unchanged source; standalone sets own in-memory USER role and graph role callback syncs synthetic server result |
| CredentialManager/Google/Firebase/Maps | Production bindings/merged providers + login click | Real credentials/SDK lifecycle possible | All SDK/class/default-wiring paths excluded from isolated target/test DEX; no real SDK runtime claim |

Audited actual main/debug/release manifests, DI/source calls and manual constructors; removing a provider alone would be insufficient. No actual cloud destination launched during audit.

## 5. Profile UI Isolation

ProfileScreen core now takes required **AuthState + UserRole**, keeping UI body/name fallback/role styling/static content/navigation unchanged. ProductionProfileScreen overload preserves original four-callback call sites and collects original AuthAppDependencies.repository.state + DisasterRepository role. Standalone calls the exact core with actual fixture repository state.

Implemented contract displays synthetic `Fixture Person` and roleUSER, mock avatar/statistics/skills/area/certificate text. Null fullName renders existing “Người dùng” fallback. Android JSON-null parsing was corrected: missing/null optional user fields remain null rather than literal text. Two new JVM contract cases cover missing/explicit-null fields, nested verification status and preservation of non-null/literal "null" text. Before-fix these2cases already PASS with cached JVM org.json; the actual Android run exposed the divergence. [Physical/JVM before-after proof](../tools/ui-harness/evidence/p5e07-usb/profile-null-before-after.json). No library upgrade or credential/schema change.

Existing Profile does **not** render email/phone fields, explicit loading/error panels, profile edit or a logout button; these are NOT_IMPLEMENTED/NOT_APPLICABLE, not invented PASS. AuthUser email is synthetic; phone null or synthetic from role response; no personal account is read. Restoring with error/revocation leaves Profile for Login through actual graph, rather than falsely claiming a Profile error widget.

Profile->Settings/back/Home and recreation/process restore/expiry tested. Logout is exercised through actual Settings UI, not a nonexistent Profile button. Static visual verification/readiness content is existing presentation and grants no rescue authority.

## 6. Settings UI Isolation

SettingsScreen source unchanged, byte-equal in standalone. Three switches use local `remember` values, all initiallytrue; location label does not call OS settings or permission API, notification does not subscribe, readiness does not mutate rescue state. Actual native checkable state toggled and own location permission remainsDENIED; no grant/revoke/system setting action.

Existing arrow actions for account/contact/language/theme/units/area are no-op; they are not claimed implemented features. About/back/logout callbacks are real graph callbacks and tested. Existing ephemeral switches reset on Activity recreation, as original remember contract; test asserts resettrue rather than changing product behavior. No cloud side-effect adapter is necessary for these inert controls. Future implementation of subscriptions/account deletion/location/SOS requires a new audit; not covered here.

## 7. Role Selection UI Isolation

Core RoleSelectionScreen still runs actual RoleSelectionViewModel and AuthRepository; default DI overload moved to production-only ProductionRoleSelectionScreen. No fake state machine. Fixture PATCH uses same production AuthApiClient method/path, validates synthetic bearer/body limit and accepts **USER only**; any other role, includingRESCUER/ADMIN, is rejected. USER success updates actual repository AuthState and actual graph callbacks; restricted RESCUER UI choice returns403/error and never grants elevation. ADMIN is absent from production UserRole enum/UI; no ADMIN UI flow was manufactured.

Before-fix physical **accountRoleRecreateRetainsUserSelection:1test/1failure**, errors/skips0, driverexit1. Role core used remember, so USER selection reset to defaultRESCUER on recreation and USER-only fixture refused it. Same test after fixPASS in final129. Fix: rememberSaveable selection; ignore role changes whileLoading, retaining original default/visuals/labels and server-authoritative operation. Source hash and actual before/final XML: [before-after](../tools/ui-harness/evidence/p5e07-usb/role-before-after.json), [before XML](../tools/ui-harness/evidence/p5e07-usb/before-fix/accountRoleRecreateRetainsUserSelection/TEST-ui.xml).

Loading/recreation while response barrier pending, errors401/403/500/timeout/offline, actual callback/history, stale response after expiry, process restart covered. Existing role-update401 behavior is UI Error while existing AuthRepository session semantics remain unchanged; explicit session expiry/refresh rejection is separately tested through restore/logout. No auth-policy refactor or permission grant.

Synthetic RESCUER display is not APPROVED personnel/vehicle qualification; syntheticADMIN is not production authorization. Neither fixture login nor role UI can open strict routing.

## 8. Home UI Assessment

HomeScreen/DisasterRepository/EmergencyCards/TaskCard/AppBottomNavigation are local UI/static data only; no VM, SDK client, listener or location constructor. Actual USER Home branch runs in new account fixture without modifying Home source. Tests navigate Home/Profile and assert no unavailable screen was used on this round trip. All outbound events remain allowlisted local auth/role/routing fixture.

Home greeting and warnings come from existing static sample repository, not the authenticated profile/live hazard. RESCUER Home branch and its sample readiness UI not runtime-certified because fixture never approves that role. Map/SOS/Rescue destination bindings remain unavailable; no integration/routing UI added. **PRODUCTION_HOME_UI=PASS(USER smoke only)**.

## 9. Exact Source Changes

Exactly8baseline files edited and6implementation/test files added:

| File | Before SHA-256 | After SHA-256 |
|---|---|---|
| `app/src/main/java/com/example/bai1/auth/network/AuthApiClient.kt` | `5cd17a05ce42009985e826f9efc8e9292666f4888a92b7ba202628ae75e17b20` | `5d6205b7047ac4939ef697c6867d3b45798a25330a5aae65e9d2d33b7ba803ec` |
| `app/src/main/java/com/example/bai1/presentation/auth/RoleSelectionScreen.kt` | `0f396cd6b58c5009b011af3c42b01d0b8b85c64f4530c77c39ea6461a9dd1d42` | `59fcd066539992e357098945a1b1b18b51264e7a501fc0c3120eb59857aed1dd` |
| `app/src/main/java/com/example/bai1/presentation/profile/ProfileScreen.kt` | `a6992d3d71e1df8fb90b21685823d232f6b550254f9d21b24028b1c505c1740a` | `b4277d8d58c3463d754f0c5860901a0a904a038b2d5e6d68bb0c06939e0c70ec` |
| `tools/ui-harness/README.md` | `600c127d15c04d7152f062af28703d506286fa09c86237e90e9e1fca8200ee14` | `10b5ca23d27315d890e415294255ac75d50157c25d7a70a5fe7b143a3ad64b83` |
| `tools/ui-harness/build.gradle.kts` | `f19f21919d89e56617b8e5882907f3264be90e0cceca582982438c8e81d834a6` | `bd3f56cf694998f1683d5a78acd718b4d489362c7e0637ed86496993359b079f` |
| `tools/ui-harness/host_fixture.py` | `e18f7f04321775363931fcd07c7da02946d8e9f0c243dab51a83bbcada1d4761` | `9458652f44a1ff16af66c2c113912f10af74b6882b40476b3f950e718d586786` |
| `tools/ui-harness/run_ui_tests.py` | `afedc2b0945b34d0fb49dca2b59e12159edacae5355dd902d2482d1edb15eccf` | `a36c570af5ddd561362db0e964e7a2aaf992827e4319e4b900fd454e23ac3081` |
| `tools/ui-harness/src/main/java/com/example/bai1/uiisolated/GraphFixtureApplication.kt` | `f586011e3be3e7c145b1d19e3f1d5e0ddb059dc7188ae779a5e10bd1a70b0287` | `aacc0dcb6ff5f33d170fb0322446f37339c9c3eea194112e8443c9c7cef81928` |
| `app/src/main/java/com/example/bai1/presentation/auth/ProductionRoleSelectionScreen.kt` | NEW | `eac73ec16cafb4bfd105d67da3c95186b6a3a173dc2284465b7f6ed18ffd3901` |
| `app/src/main/java/com/example/bai1/presentation/profile/ProductionProfileScreen.kt` | NEW | `1611f71043c46f0cd1726dac589dee223e86022f3872d5878b59376b1bab28a7` |
| `app/src/test/java/com/example/bai1/auth/RoleSelectionViewModelTest.kt` | NEW | `7f3c678849458652c8282c3f394079585487637de979d82ce5faa8b400d75177` |
| `app/src/test/java/com/example/bai1/auth/network/AuthUserNullableFieldsTest.kt` | NEW | `b98169ae45153bd7e91ec75960278b9101b146a9f69cf05bd69a29e962da7f6d` |
| `tools/ui-harness/src/main/java/com/example/bai1/uiisolated/ActualAccountFixtureScreens.kt` | NEW | `1c5c6842a28af4d71e01a299e31825900902cd580872a4ea27d7389e779d8416` |
| `tools/ui-harness/src/androidTest/java/com/example/bai1/fixture/ProductionAccountUiTest.kt` | NEW | `72951bd33ad8f710cdf9fc89c580d37538c4dc524985ed1277319b1bc7a89171` |


[Exact new/changed file manifest](../tools/ui-harness/evidence/p5e07-usb/changed-files.json) includes this report/evidence; SHA-256/mtime_ns before/after for all edited baseline files in preservation JSON. MainActivity/MainAppHost/DisasterApplication/AppNavGraph/AppNavScreens/ProductionAppNavScreens/ProductionAppNavGraph/Settings/Home/RoleSelectionViewModel/AuthRepository/AuthViewModel/lifecycle interfaces/routing/SOS sources remain baseline hash+mtime. Historical regression test source files unchanged.

## 10. Production AppNavGraph Integration

Actual MainActivity->manifest-selected GraphFixtureApplication->actual AppNavGraph chain retained. Standalone-only ActualAccountFixtureScreens delegates other methods to E06 factory and overrides Role/Profile/Settings/Home with exact production UI. Required actual VM/state supplied, route registration/navigation callbacks stay in production graph. No replacement NavHost or `isTest=true` auth bypass.

Old99 preserve their E06 factory and exact assertions. New30 select account-screen binding through owned test Intent only; that class/Intent configuration is absent from production APK, never changes Auth/routing policy. Actual graph unchanged27route declarations/order/arguments/callbacks; [route preservation](../tools/ui-harness/evidence/p5e07-usb/audit/route-preservation.json).

Production main calls still resolve the original overload signatures/defaults. Only Role UI saved selection/Loading selection behavior intentionally remediated; Profile state wiring separated; AuthApiClient optional user fields now guard explicit JSON null. Token parsing/headers/authorization/error mappings unchanged.

## 11. Manifest/DEX Security Proof

Before every install: distinct applicationId `com.example.bai1.p5e02.uiisolated`, test`.test`, both absent; no sharedUserId, own UID/data/storage, no install-r. Target Application GraphFixtureApplication, two non-exported Activities/no launcher, backupfalse, no provider/service/receiver/SDK metadata. Main packagecom.example.bai1 never installed/launched/cleared. Task-installed target/test UID and data-directory metadata are independently verified; distinct non-system UIDs and package-owned data paths, no private files/main package queried. [UID/data receipt](../tools/ui-harness/evidence/p5e07-usb/owned-package-proof.json).

Target and test DEX exclude Firebase/GMS/CredentialManager SDK and production Application/DI/default screen bindings, including new ProductionProfileScreen/ProductionRoleSelectionScreen. Require actual MainActivity/AppNavGraph/UI/Auth/storage/client/Role VM classes. **35production Kotlin copies byte-equal**, shared lifecycle test exact copy/hash, source startup/factory wiring and strict origin pin checked. google-services production configuration values checked without writing values:0matches; no production token/fallback endpoints. [Preinstall audit](../tools/ui-harness/evidence/p5e07-usb/physical-final/startup-origin-audit.json), [copy hashes](../tools/ui-harness/evidence/p5e07-usb/physical-final/source-copy-hashes.json).

Test packaged manifest separately checked: runner targets only isolated app, no customApplication/providers/services/receivers; only3standard AndroidX bootstrap/empty activities. REORDER_TASKS test infrastructure does not grant account/location access. [Target/test manifests](../tools/ui-harness/evidence/p5e07-usb/isolated-target-manifest.xml), [test manifest](../tools/ui-harness/evidence/p5e07-usb/isolated-test-manifest.xml).

Main debug/release manifests unchanged retain DisasterApplication and production providers/services/Maps metadata. Main APK requires production UI overload classes and excludes isolated factories/fixture credential literals. Main APK not installed. [Production manifest audit](../tools/ui-harness/evidence/p5e07-usb/audit/production-manifest-audit.json), [APK boundary](../tools/ui-harness/evidence/p5e07-usb/audit/main-apk-boundary.json).

## 12. USB Fixture Connectivity

Laptop loopback127.0.0.1:OS-assigned ephemeral port; phone127.0.0.1 through owned ADB reverse. No emulator10.0.2.2 on phone, hardcoded3000/LAN/production URL. Client scheme/host/port/path pin, no proxy/redirect/retry. Added only local PATCH/users/me/role plus synthetic nullable-profile shape and role fault headers; bounded request body4096, no forwarding/DB/email/SOS/push. Host event metadata never stores body/password/JWT.

Every observed request Host must equal owned loopback origin. Barrier registry reset percase; release onfinally. Actual HTTP entered/release/done observations and coroutine joins determine race order, not sleep assumptions. Existing GZIP/strictfalse download-only routing fixture unchanged.

```text
adb -s <discovered_USB_serial> reverse tcp:<OS-owned-port> tcp:<OS-owned-port>
adb -s <discovered_USB_serial> install -t <newly-owned-apk>
adb -s <discovered_USB_serial> shell am instrument -w -r -e class <actual-class>#<method> ... <isolated-test-package>/<runner>
adb -s <discovered_USB_serial> logcat --pid=<owned-pid> -v threadtime -T <test-window>
adb -s <discovered_USB_serial> reverse --remove tcp:<owned-port>
adb -s <discovered_USB_serial> uninstall <task-installed-package>
```

Actual commands/ports/exit codes in results.json; serial REDACTED. No reverse--remove-all/pmclear/install-r/global-logclear.

## 13. Physical Role Selection Tests

Actual role UI displays USER/RESCUER options and no ADMIN; USER-only acceptance and restricted-role refusal, Loading, recreation before/during submission, HTTP401/403/500/timeout/offline, stale response after expiry and process restart PASS. Callback history must never containRESCUER/ADMIN; actual repository endsUSER and routing canNavigateSafely=false. No approval inferred from UI “nhà hoạt động cứu hộ” label.

Three added JVM RoleSelectionViewModel tests exercise actual repository interaction: pending until reply, server denial not elevated state, late completion after logout cannot publish success/user. Production RoleSelectionViewModel unchanged.

## 14. Physical Profile Tests

Actual name/USER role render, null-name fallback, Settings/Home navigation, local expiry/refresh rejection/restore error, Activity recreate and process restore PASS. Required default production overload excluded; exact core consumes real repository state. Authenticated profile email synthetic; no personal phone account data. Email/phone visual fields, edit, explicit loading/error panels and Profile logout NOT_IMPLEMENTED in current source, not claimed PASS.

Profile visibility ends at actual graph authentication boundary; failed/expired restore cannot keep stale profile. Static decorative verified/stats/certification copy is not authoritative qualification and was not redesigned.

## 15. Physical Settings Tests

Actual Settings rendering/local3switches/state changes, unchanged ephemeral reset on recreation, About/back, actual logout/injected credential cleanup, expiry across recreate and isolated process restore PASS. Own fine-location permission staysDENIED during local toggle. No notifications subscription/system settings/production mutation.

Initial focused test failed because it required native classandroid.widget.Switch. Own-window diagnostic showed **three checkable checked android.view.View nodes with “Đang bật” state**. Fixed only native element discovery to checkable semantics;3switch count, initialtrue, click->false, recreate->true and permissionDENIED assertions retained. Settings production source unchanged. [Diagnostic](../tools/ui-harness/evidence/p5e07-usb/switch-diagnostic/), [corrected focused3](../tools/ui-harness/evidence/p5e07-usb/focused-corrected/TEST-production-ui.xml). This was test wiring, not a second product defect.

## 16. Navigation/Back Stack Tests

Old actual Splash/Onboarding/Login/Register/About flows re-executed. New authenticated Home->Profile->Settings->About/back uses actual screen callbacks/graph. Profile back/Home bottom navigation returns Home. USER role success removes RoleSelection entry. Settings logout clears protected history and invokes injected cleaner; back cannot expose protected screens. Revoked/expired routes return Login, stale role/network results cannot resurrect authenticated state.

Maps/SOS/Rescue routing placeholders in old99 remain only graph coverage, not UI integration. Core graph permission checks unchanged and never infer routing authority from local role.

## 17. Activity/Process Restart Tests

Actual role selection survives recreation; while pending HTTP it retains submittedUSER and actual destination-scoped Role VM. Authenticated Profile recreates with same actual Activity Auth VM and no extra storage write. New helper waits a **different NavController composition** before asserting recreated route/UI. Settings route persists while its existing local remember values resettrue; unauthenticated Login staysafterexpiry/recreate.

Eight ordered prepare/restart pairs (old4 + newProfile/Settings/Role/expired4) assert distinct owned PIDs; actual SharedPreferences synthetic sessions restored by actual AuthViewModel. Profile/Settings reached through Home after process restore. Existing contract does not persist isNewUser/unfinished role selection; restart restoresUSER session toHome, explicitly tested rather than claiming pending role-step persistence. Expired restart emits no auth HTTP and cannot expose Profile/Settings. [Restart receipts](../tools/ui-harness/evidence/p5e07-usb/coverage-groups.json).

## 18. Old99 Physical Regression

**99/99PASS**,0failures/errors/skipped, everyADB exit0. Historical testcase names compared directly with **E06 actual JUnit XML** (and historical results), all retained:76E05 +23E06graph. Neither source tests nor old assertions edited. [Old99 execution XML](../tools/ui-harness/evidence/p5e07-usb/TEST-old99.xml). No historical result/emulator/JVM substituted for current phone executions.

## 19. New Physical UI Tests

**30/30 unique new methods PASS** in final129; failure/error/skipped0, everyADB exit0. Initial before-fix1fail, initial focused6with5pass/1test-wiringfailure, switch diagnostic1fail corrected-focused3PASS, first complete129(128PASS/1Profile-nullFAIL), and Profile-null-fixed focused1PASS are separate repetitions and not added to129. [New30 XML](../tools/ui-harness/evidence/p5e07-usb/TEST-new-account30.xml), [all results](../tools/ui-harness/evidence/p5e07-usb/physical-final/results.json), [exit codes](../tools/ui-harness/evidence/p5e07-usb/exit-codes.json).

| Actual JUnit method | Result | ADB exit |
|---|---|---:|
| accountRoleUiDisplaysExistingChoicesAndRejectsElevatedRole | PASS | 0 |
| accountRoleUserSuccessClearsRoleHistory | PASS | 0 |
| accountRoleRecreateRetainsUserSelection | PASS | 0 |
| accountRoleLoadingAndRecreateDuringHttpRetainSubmittedRole | PASS | 0 |
| accountRoleHttp401ShowsProductionError | PASS | 0 |
| accountRoleHttp403ShowsProductionError | PASS | 0 |
| accountRoleHttp500ShowsProductionError | PASS | 0 |
| accountRoleTimeoutShowsProductionError | PASS | 0 |
| accountRoleOfflineShowsProductionError | PASS | 0 |
| accountRoleLateResponseAfterExpiryCannotRestoreProtectedUi | PASS | 0 |
| accountProfileRendersSyntheticUserAndUserRole | PASS | 0 |
| accountProfileEmptyNameUsesProductionFallback | PASS | 0 |
| accountProfileActualSettingsAndHomeNavigation | PASS | 0 |
| accountProfileSessionExpiryClearsProtectedHistory | PASS | 0 |
| accountProfileRefreshRejectedReturnsLogin | PASS | 0 |
| accountProfileRestoreErrorReturnsLoginWithoutStaleUser | PASS | 0 |
| accountProfileRecreateRetainsActualUiAndViewModel | PASS | 0 |
| accountPrepareProfileSessionForProcessRestart | PASS | 0 |
| accountProfileAfterProcessRestartUsesIsolatedRestore | PASS | 0 |
| accountSettingsRendersLocalStateAndSafeToggles | PASS | 0 |
| accountSettingsAboutBackAndActualLogout | PASS | 0 |
| accountSettingsExpiryLeavesLoginAcrossRecreate | PASS | 0 |
| accountSettingsRecreateKeepsRouteAndExistingEphemeralDefaults | PASS | 0 |
| accountPrepareSettingsSessionForProcessRestart | PASS | 0 |
| accountSettingsAfterProcessRestartUsesIsolatedRestore | PASS | 0 |
| accountPrepareRoleSessionForProcessRestart | PASS | 0 |
| accountRoleAfterProcessRestartUsesExistingHomeRestoreSemantics | PASS | 0 |
| accountPrepareExpiredSessionForProcessRestart | PASS | 0 |
| accountRestartAfterExpiryCannotShowProfileOrSettings | PASS | 0 |
| accountHomeActualUiHasNoCloudOnProfileRoundTrip | PASS | 0 |


No failing final acceptance case. Actual method scheduling annotations are not test counts: runtime listener emits JUnit XML, runCount1/tests1 checked per invocation, aggregate129uniquecases.

## 20. Auth Lifecycle Regression

AuthRepository/AuthViewModel/FirebaseAuthSession/AndroidFirebaseAuthSession/credential interface retain E06 hash+mtime, including E05 epochs, cancellation/ownership, stale result rejection, cleanup uncertainty, routing invalidation and saved sessions. Old physical HTTP races/shared20/VM-related tests all re-executed. New role stale completion waits host barrier and actual role VM jobs after release; no stale state/token write/protected navigation.

Targeted Auth/navigation **42tests/8suites,0failures/errors/skipped,exit0**, including3new RoleSelectionViewModel/repository integrations and2nullable-user parser contract cases. Existing role API error/session semantics unchanged. No-cloud adapters certify orchestration only; real Firebase cleanup/account persistence/provider/Google credential ownership remains NOT_RUN. No cleanup uncertainty upgraded to real SDK success.

## 21. Routing JVM Regression

Fresh routing `--rerun-tasks --tests 'com.example.bai1.rescue.routing.*'`: **462tests/13suites,0failures/errors/skipped,exit0**,27Gradle tasks executed. Routing source/tests unchanged SHA+mtime. [Routing XML](../tools/ui-harness/evidence/p5e07-usb/jvm-build/routing-xml/), [routing log](../tools/ui-harness/evidence/p5e07-usb/jvm-build/routing.log).

## 22. Full JVM Regression

`:app:testDebugUnitTest`: **545tests/32suites,0failures/errors/skipped,exit0** = baseline540 +3new role-VM integration cases +2nullable-user contract cases. Counts from actual XML. Auth42/routing462 overlap full and are not summed. [Full XML](../tools/ui-harness/evidence/p5e07-usb/jvm-build/full-xml/), [JVM/build commands and exits](../tools/ui-harness/evidence/p5e07-usb/jvm-build/results.json).

Actual commands:

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
cd /home/vantri/AndroidStudioProjects/bai1
java -version
javac -version
./gradlew --offline --no-daemon --version
./gradlew --offline --no-daemon :app:testDebugUnitTest --tests 'com.example.bai1.auth.*' --tests 'com.example.bai1.presentation.navigation.*'
./gradlew --offline --no-daemon :app:testDebugUnitTest --rerun-tasks --tests 'com.example.bai1.rescue.routing.*'
./gradlew --offline --no-daemon :app:testDebugUnitTest
./gradlew --offline --no-daemon :app:assembleDebug :app:processReleaseMainManifest
./gradlew --offline --no-daemon :app:processReleaseManifest
PYTHONDONTWRITEBYTECODE=1 python3 tools/ui-harness/run_ui_tests.py --physical-usb --evidence-dir /tmp/p5e07-final-corrected
```


## 23. APK Build

Main debug APK BUILD SUCCESSFUL, exit0, **95,446,518bytes**, SHA-256 `7f1db274c7be92d93154ea353a49e26eb7dec4d513b45171309093ad65bfac87`; never installed. Standalone target/test buildsPASS before installation; targetSHA `de289363bf193efb6c8f56a96471bfc19dc23ea7d6e4e1b89e3f335553e47aa0`, testSHA `7385926071f8f2fa563044da418f1c858eedac0dc7ffbf92ad931ce6fcd63607`. Main/release manifest processing exit0, retained production dependencies/defaults; no toolchain/plugin change. [APK log](../tools/ui-harness/evidence/p5e07-usb/jvm-build/apk.log), [release manifest log](../tools/ui-harness/evidence/p5e07-usb/release-manifest.log). BuildPASS not used as UI acceptance.

## 24. Logcat/Privacy/Crash Analysis

PID-scoped per-test window only, no global clear/other-app logs/screenshots. Final logs0fatal/ANR/unexpected SDK startup markers,0fixture password/token leakage. Review checks navigation/null/IllegalState errors as well; no failing final case. OPPO caught receiver-not-registered teardown warnings are vendor own-PID messages, not app crash. [Log review](../tools/ui-harness/evidence/p5e07-usb/log-review.json).

Manifest/DEX/source wiring and allowed request origin/path together establish tested isolation; logs alone are not universal network-firewall proof. Diagnostic accessibility dump reads only owned Settings window, contains labels/checkable state and no password/token. Serial redacted from all persisted evidence/report.

## 25. Personal Device Preservation

No reset/root/flash/account login/private-data read, no real SOS/location/email/push; no camera/location/SMS/microphone permission grants or system setting changes. Existing main app never installed/overwritten/launched/cleared. Only two verified-absent task namespaces installed with -t/no-r, force-stop limited to ownedtarget, uninstalled only task-installed packages. Own reverse removed, barriers released/server stopped; final package paths absent/reverseempty/cleanup_errors empty/phoneauthorized. [Cleanup receipt](../tools/ui-harness/evidence/p5e07-usb/device-and-cleanup.json).

## 26. Backend/Graph Preservation

**677/677Backend files unchanged exact SHA-256 + mtime_ns,0new/removed**, including20JSON/GZIP graph/manifest/diagnostic artifacts across outputdirectories. No Backend edit/run/cloudrequest/deploy. Android routing/SOS/Rescue/Google-services/main-debug network policy/manifest/coregraph/historicalreports/evidence unchanged. Changed source scope precisely section9; inherited git dirty state not attributed to this task. [Backend preservation](../tools/ui-harness/evidence/p5e07-usb/backend-preservation.json), [Android preservation](../tools/ui-harness/evidence/p5e07-usb/android-preservation.json), [index](../tools/ui-harness/evidence/p5e07-usb/evidence-index.json).

## 27. Remaining Map/SOS/Rescue/UI Blockers

Maps/SOS/Rescue actual UI NOT_RUN; SDKs/manual dependencies/location/realtime/sending APIs require dedicated audited boundaries. Other cloud/factory destinations still unavailable. Role/Profile/Settings UI tested under injected state, not production-server account approval. Home PASS covers USER/static smoke only. Real Google credential picker/Firebase startup/signin/expirycleanup/process persistence NOT_RUN. Profile email/phone/edit/loading-error widgets not implemented; Settings inert entries and cosmetic toggles not backend/system settings features.

API37 physical permissions, RESCUER Home branch, broad device/network matrix and full cloud application E2E remain untested. Do not generalize this isolated APK to production package or live rescue readiness.

## 28. Remaining F06/Parity/P5-I02 Work

F06 OPEN; no qualification/UCS/hazard/parity/P5-P01/P5-I02 changes or Routing UI. **NO PRODUCTION-AUTHORITATIVE QUALIFICATION => STRICT_ROUTING_DISABLED**. READY/VERIFIED_COMPATIBLE/digest/edgecounts/authenticatedfixture/RESCUERlabel/AppNavGraphPASS/phonePASS/HazardATTACHED grant no routing/rescue authorization. Existing cache/network limitations not solved by account-screenUI PASS.

## 29. Final Acceptance

**PASS — P5-E07-USB UI SCOPE**: all three requested actual production UIs run on physicalUSB via actual MainActivity/AppNavGraph, minimal DI/default compatibility, reproducible Role lifecycle and Android nullable-user defects fixed, logout/expiry/backstack/restart and safe settings state verified; old99 +new30PASS, freshrouting462/full545/APKPASS, all isolation gates, Backend/artifact/phone preservationPASS.

MAIN_GRAPH_ISOLATION remainsCONDITIONAL because cloud destinations remain unavailable; FULL_APPLICATION_E2E and real Firebase SDK lifecycleNOT_RUN. No unqualified AppE2EPASS. Individual statuses/limits in section1 retained. Strict routing disabled. No failing final testcase, commit/push/deploy or scope continuation.

## 30. Recommended Next Task

Đề xuất task hẹp **read-only notification/warning UI isolation** hoặc hoàn thiện hợp đồng Profile/Settings còn inert sau khi xác định yêu cầu sản phẩm. Maps/SOS/Rescue cần task riêng với explicit API/realtime/location boundaries và không gửi dữ liệu thật trên điện thoại cá nhân. Real Google/Firebase lifecycle cần test plan riêng. Không tự thực hiện các task này, F06/parity/P5-I02/Routing UI hoặc mở strict routing.
