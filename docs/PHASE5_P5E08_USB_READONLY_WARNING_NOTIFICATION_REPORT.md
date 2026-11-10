# PHASE 5 – P5-E08-USB: Read-only Warning & Notification UI Isolation

Thực thi 2026-11-08–09, Asia/Bangkok. Evidence nghiệm thu mới lưu ngày2026-11-09.

## 1. Executive Summary

**CONDITIONAL — LOCAL_STATIC_UI_SCOPE.** Actual production WarningListScreen, StormDetailScreen, FloodDetailScreen và NotificationScreen chạy qua actual MainActivity/AppNavGraph trên OPPO USB. Các screen hiện chỉ dùng demo tĩnh; chưa có authoritative/live warning API.

Final physical **162 unique tests = old129 + new33**,0failures/errors/skipped; driver và mọi instrumentation exit0. Fresh routing **462**, Auth/navigation **42**, full JVM **545**,0fail/errors/skips; APK buildPASS. Không cộng focused repetitions. [physical-final/TEST-production-ui.xml](../tools/ui-harness/evidence/p5e08-usb/physical-final/TEST-production-ui.xml), [coverage-groups.json](../tools/ui-harness/evidence/p5e08-usb/coverage-groups.json), [jvm-build/results.json](../tools/ui-harness/evidence/p5e08-usb/jvm-build/results.json).

Phiên trước bị gián đoạn và evidence trong /tmp mất; những lượt đó KHÔNG tính nghiệm thu vì không còn XML. Báo cáo chỉ dùng bằng chứng mới được lưu trực tiếp trong repo, ngày2026-11-09.

## 2. Laptop/Phone Disk Gate

Root và /home vượt2GB trước resume/build; runner và từng JVM phase kiểm ngưỡng trước ghi lớn. Phone còn khoảng8.2GB. Không dọn Docker/Snap/Gradle/SDK/emulator/file cá nhân. [disk-before.txt](../tools/ui-harness/evidence/p5e08-usb/disk-before.txt), [disk-after.txt](../tools/ui-harness/evidence/p5e08-usb/disk-after.txt), [resume-state.json](../tools/ui-harness/evidence/p5e08-usb/resume-state.json), [environment-before.json](../tools/ui-harness/evidence/p5e08-usb/environment-before.json).

## 3. USB Device Inventory

OPPO CPH2159 / Android13 / API33 / physicalUSB, authorizeddevice, boot1, qemu khác1. Serial chọn động từ inventory, redacted; không emulator/hardcode serial. API33 không chứng minh permissionAPI37. [environment-before.json](../tools/ui-harness/evidence/p5e08-usb/environment-before.json), [device-and-cleanup.json](../tools/ui-harness/evidence/p5e08-usb/device-and-cleanup.json).

## 4. Repository Baseline

Android /home/vantri/AndroidStudioProjects/bai1; Backend /home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app READ ONLY. Đã đọc E07/E06/E05/E04/E03/NET/CACHE/E01R; không AGENTS.md áp dụng. Hai repo có inheriteddirty, không reset/clean/commit/push/deploy.

Snapshot trước sửa ban đầu đã mất cùng/tmp. Baseline được **tái dựng từ E07 final persisted inventory**, gồm after SHA-256/mtime_ns của8edited+1022new và1453baseline;2recursive inventory entries dùng files E07 hiện có. Kết quả2475Android files, chỉ4existingfiles khác;677Backend files khớp toàn bộ. Không giả mạo snapshot mới thành snapshot trước sửa. [resume-state.json](../tools/ui-harness/evidence/p5e08-usb/resume-state.json), [android-baseline.json](../tools/ui-harness/evidence/p5e08-usb/android-baseline.json), [backend-baseline.json](../tools/ui-harness/evidence/p5e08-usb/backend-baseline.json), [changed-files.json](../tools/ui-harness/evidence/p5e08-usb/changed-files.json).

Loại khỏi snapshot .git/.gradle/build/dist/node_modules/.idea/.kotlin/__pycache__; source/config/tests/docs và untracked ngoài exclusions được kiểm.

## 5. Existing Warning/Notification Feature Inventory

| Component | Actual source/data |
|---|---|
| WarningListScreen | Production Composable;6static WarningItems; category filtering/clickID |
| StormDetailScreen | Fixed emergencyStormAlert/Yagi, không selectedID argument |
| FloodDetailScreen | Fixed flood_quangnam record; first lookup assumes it exists |
| NotificationScreen |7inline mock rows; tabs đổi selection style, không filtering; rows không detailclick |
| DisasterRepository | Pure in-memory lists/StateFlowrole; notificationList model data không được actual screen dùng |
| Home USER | Static cards/category/notification callbacks |
| Warning/Notification VM/API/FCM receiver/listener | Không có implementation trong audited source |

Không tạo screen thay thế hoặc Backend contract mới.

## 6. Production Screen Dependency Audit

| Component | Source | Data | Network | Permission | Realtime | Auth | Safe isolated |
|---|---|---|---|---|---|---|---|
| Warning list | Production Composable | Local/injected List | None | None | No | Actual graph protected | Yes |
| Storm detail | Production Composable | Static Yagi | None | None | No | Protected | Yes with demo banner |
| Flood detail | Production Composable | Static flood | None | None | No | Protected | Yes; Map not opened |
| Notification | Production Composable | Inline static7 | None | None | No | Protected | Yes with banner |
| Home USER | Production Composable | Static repo | None | None | No | Protected | Tested callbacks |
| DisasterRepository | Production singleton | In-memory | None | None | No | Via graph | Yes |

Share no-op; Follow is localBoolean only, không subscription. Map callback keeps unavailable binding. Audit đã thực hiện trước edit ở phiên đầu; bảng này được tái lập từ source còn nguyên sau resume, không có artifact audit-before-edit còn lại.

## 7. Static vs Fixture vs Live Data Classification

LOCAL_STATIC_UI_SCOPE. Exact production static lists/images; test-only owned List injection cung cấp empty/default/long/duplicate/rawtime samples. Auth/routing dùng owned HTTP fixture; warning/notification không có HTTP interface. Live/authoritative warning NOT_IMPLEMENTED; pushNOT_RUN.

Warning mode luôn hiển thị global banner **SYNTHETIC_TEST_DATA — DEMO**, “Không phải cảnh báo thực tế; dữ liệu chưa xác minh. Không có dữ liệu không đồng nghĩa không có nguy cơ.” Bao cả Home/detail/video/notifications/restart. Existing “TRỰC TIẾP/Trực tiếp”, relative times và mockSOS chỉ là demo copy. Production mock-live labels vẫn cần task riêng trước live integration.

## 8. Warning Data Contract

WarningItem: id/title/subtitle/currentLocation non-nullString; categorySTORM/FLOOD/LANDSLIDE/RESCUE; severityCRITICAL/WARNING/ALERT/INFO. updateTime là displayString, list takeLast(5), không parse thời gian/expiry. Optional movementDirection/intensity/waterLevel/currentStatus defaultemptyString, affectedAreas defaultemptyList, isEmergencyAlert defaultfalse.

Không source/verified/expiresAt/currentauthority fields, uniqueID enforcement hoặc freshness validation. NotificationItem model có displaytimestamp/category/severity/isUnread nhưng actual UI bỏ qua model. Nullable JSON/malformed response N/A với typedList seam, không tự phát minh Backend support.

## 9. Minimal UI Isolation Design

WarningListScreen thêm optional warnings:List<WarningItem> với defaultDisasterRepository.warningList; remember filter theo cả filter và list; reuse EmptyState với copy empty!=safe. Default records/callbacks giữ nguyên.

Standalone ActualWarningFixtureScreens delegates ActualAccountFixtureScreens, overrides4actualComposables. GraphFixtureApplication warningScreens flag bật actual factories, owned observableList và banner quanh actual AppNavGraph. Old modes không bật flag. Không thay AuthRepository/ViewModel, policy, graph/routes, SOS/Rescue/routing/Backend.

## 10. Exact Files Changed

- [WarningListScreen.kt](../app/src/main/java/com/example/bai1/presentation/warning/WarningListScreen.kt)
- [build.gradle.kts](../tools/ui-harness/build.gradle.kts)
- [run_ui_tests.py](../tools/ui-harness/run_ui_tests.py)
- [GraphFixtureApplication.kt](../tools/ui-harness/src/main/java/com/example/bai1/uiisolated/GraphFixtureApplication.kt)

New implementation/test: ActualWarningFixtureScreens.kt và ProductionWarningUiTest.kt. Báo cáo và toàn bộ evidence/support scripts là files mới. [changed-files.json](../tools/ui-harness/evidence/p5e08-usb/changed-files.json) chứa everypath/before-afterSHA/mtime; [evidence-index.json](../tools/ui-harness/evidence/p5e08-usb/evidence-index.json) chứa evidence hashes/sizes. Old129 test source/assertions giữ nguyên exact SHA+mtime.

## 11. MainActivity/AppNavGraph Integration

Exact MainActivity→MainAppHost→actualAppNavGraph;27routes/guards/callbacks giữ nguyên SHA+mtime. No replacementNavHost.

Home STORM/LANDSLIDE→list, FLOOD→fixed flood. List idcontainsflood→fixed flood, mọiIDkhác→fixedYagi. initialCategory chưa được graph truyền. **Generic selected-item detail NOT_IMPLEMENTED**; test syntheticnonfloodID xác nhận limitation, không gọi đó là correctselectedrecordPASS. [route-preservation.json](../tools/ui-harness/evidence/p5e08-usb/route-preservation.json).

## 12. Manifest/DEX Security Proof

Pre-install gatePASS: owned applicationId absent trước install; no sharedUserId; distinct non-systemUIDs. Target2activities nonexported, nointentfilter/provider/service/receiver/SDKmetadata. TestAPK chỉ3AndroidX bootstrapactivities; noSDKstartup. No location/camera/SMS/microphone/notificationpermission.

BothDEX cloud/business exclusions, exact4productionwarningclasses required, **39byte-equal production sourcecopies** +exact sharedlifecycle test. Startup code/origin/path allowlist, productionconfiguration values0matches. Main debug/release defaults remain DisasterApplication/FirebaseInitProvider; mainAPK excludes isolatedfactories, never installed. [physical-final/startup-origin-audit.json](../tools/ui-harness/evidence/p5e08-usb/physical-final/startup-origin-audit.json), [physical-final/source-copy-hashes.json](../tools/ui-harness/evidence/p5e08-usb/physical-final/source-copy-hashes.json), [physical-final/owned-package-uid-proof.json](../tools/ui-harness/evidence/p5e08-usb/physical-final/owned-package-uid-proof.json), [isolated-target-manifest.xml](../tools/ui-harness/evidence/p5e08-usb/isolated-target-manifest.xml), [isolated-test-manifest.xml](../tools/ui-harness/evidence/p5e08-usb/isolated-test-manifest.xml), [production-manifest-audit.json](../tools/ui-harness/evidence/p5e08-usb/production-manifest-audit.json), [main-apk-boundary.json](../tools/ui-harness/evidence/p5e08-usb/main-apk-boundary.json).

## 13. USB Fixture and ADB Reverse

Owned loopback127.0.0.1:OS-assigned-port; dynamic adb-s reverse exactownedtcp, no remove-all. Exacthost/port/path allowlist, Proxy.NO_PROXY, redirects/retriesfalse, boundedrequests/timeouts, no forwarding/DB/email/SMS/push/SOS.

No warning/notificationHTTPendpoint fabricated. Newcases events only authlogin/me/refresh/firebase-token and routing/current; no rolePATCH or warningmutation. AuthPOST is fixture dependency, not warningwrite. [coverage-groups.json](../tools/ui-harness/evidence/p5e08-usb/coverage-groups.json), [physical-final/results.json](../tools/ui-harness/evidence/p5e08-usb/physical-final/results.json).

## 14. Actual Warning UI Tests

| Actual JUnit method | Result |
|---|---|
| warningHomeCategoryUsesActualGraphAndList | PASS |
| warningStormFilterUsesExistingCategory | PASS |
| warningFloodFilterUsesExistingCategory | PASS |
| warningAllFilterRestoresList | PASS |
| warningDefaultStormDetailAndBackUseActualCallbacks | PASS |
| warningFloodSelectedTemplateAndBackUseActualCallbacks | PASS |
| warningNonFloodIdStillUsesFixedStormTemplateLimitation | PASS |
| warningEmptyStateNeverImpliesNoRisk | PASS |
| warningFilterWithNoMatchingRecordsHasNeutralEmptyState | PASS |
| warningInjectedListRecomposesWithoutRememberedStaleRecords | PASS |
| warningOptionalDefaultsRenderWithoutInventedAuthority | PASS |
| warningInvalidDisplayTimeIsUnparsedAndNotFreshnessProof | PASS |
| warningOldDisplayTimeDoesNotGrantCurrentAuthority | PASS |
| warningDuplicateIdsDisplayDistinctRecordsInInputOrder | PASS |
| warningLongTextIsBoundedAndBackRemainsUsable | PASS |
| warningLocalListRemainsDemoWhileTransportOffline | PASS |
| warningDetailVideoPlaceholderRetainsSyntheticBanner | PASS |
| warningStormFollowIsLocalToggleOnly | PASS |
| warningListActivityRecreationPreservesProtectedRoute | PASS |
| warningDetailActivityRecreationPreservesTemplateRoute | PASS |
| warningLogoutRemovesProtectedHistory | PASS |
| warningSessionExpiryRemovesProtectedHistory | PASS |
| warningPrepareSessionForProcessRestart | PASS |
| warningAfterProcessRestartUsesExistingHomeRestore | PASS |

Actual nativeUI/ActivityScenario checks, eachXML/log under [physical-final/results.json](../tools/ui-harness/evidence/p5e08-usb/physical-final/results.json). Fixedtemplate limitations explicitly tested; no generic detail correctness claim.

## 15. Actual Notification UI Tests

| Actual JUnit method | Result |
|---|---|
| notificationActualHomeCallbackRendersStaticRows | PASS |
| notificationTabsOnlyChangeSelectionKnownLimitation | PASS |
| notificationScrollsToExistingLastStaticRecord | PASS |
| notificationBackReturnsHomeAndBottomProfileUsesActualGraph | PASS |
| notificationActivityRecreationStaysInActualGraph | PASS |
| notificationLocalRowsRemainDemoWhileOffline | PASS |
| notificationSessionExpiryCannotRestoreProtectedRoute | PASS |
| notificationPrepareExpiredSessionForProcessRestart | PASS |
| notificationAfterExpiredProcessRestartCannotRestoreRows | PASS |

Static rows/navigation/recreation/offline/expiry chạy thật. Selection-only tab test confirms missingfilterfeature. Notificationdetails/history/readstate/backendempty/error NOT_IMPLEMENTED; OS pushNOT_RUN, no notificationpermission/subscription.

## 16. Data Empty/Error/Stale/Unknown Safety

| State | Actual result |
|---|---|
| List/filter/order | PASS typedLocal data, input order; no timesort contract |
| Empty/filterempty/recompose | PASS neutral empty!=safe |
| Missing optional fields | PASS defaultemptystrings/lists; JSONnull N/A nonnullablemodel |
| DuplicateIDs | PASS2distincttitles sameID, verticalinputorder; no dedup contract |
| Longtext | PASS boundedellipsis/backusable |
| Invalid/old time | PASS rawdisplay/demo banner; validation/freshness/expiry NOT_IMPLEMENTED |
| Offline afterlogin | PASS localdemo continues; no warningrequest |
| Warning HTTP200/401/403/500/timeout/missingreverse/malformedJSON/cancel/retry | N/A no warningAPI; existing auth/network tests run separately |
| Warning loading/error/retry UI | NOT_IMPLEMENTED |
| Notification empty/malformed/duplicate/freshness | N/A fixedinline rows |
| Missing/generic selected detail | NOT_IMPLEMENTED fixedtemplates |

Unknown/stale/synthetic never grants verified/current/area-safe or strict routing.

## 17. Home-to-Warning Navigation

Actual graph callbacks: Homecategory→list→originalYagi detail→backlist→Home; filteredoriginalflood→FloodDetail→backlist; Home→notification→backHome/Profile. Logout/expiry removes protected history while on warning/detail/notification. Maps/SOS/Rescue productionUI staysNOT_RUN; unavailablebinding not evidence for thoseUI.

## 18. Activity/Process Restart

Actual list/detail/notification recreation tests;10processrestartpairs final (8old+2new), distinctPIDs, predecessorPASS, ownstoragepreserved only. New warningdetailprepare restoresexistingHome semantics afterprocessrestart, not selecteddetail. Expirednotification prepare cannot restoreprotectedrows. [coverage-groups.json](../tools/ui-harness/evidence/p5e08-usb/coverage-groups.json).

Runner now waits ownPIDgone afterforce-stop plus750ms teardown; no OSsettings/permissions altered. Newtest waits NavHost/filter/accessibility updates beforeclick. APIclearCache was checked locally: introducedAPI34, so not used onAPI33.

## 19. Old129 Physical Regression

Names from historical **actualJUnitXML**, exactclassname+methodset129. Source/assertions unchanged; final old1290fail/errors/skips/allADB0. Contains56subsetUI +20sharedrepository lifecycle tests inAndroidprocess +23graph +30account; not all129 are UIinteractions. [historical-old129-junit-names.json](../tools/ui-harness/evidence/p5e08-usb/historical-old129-junit-names.json), [TEST-old129.xml](../tools/ui-harness/evidence/p5e08-usb/TEST-old129.xml), [coverage-groups.json](../tools/ui-harness/evidence/p5e08-usb/coverage-groups.json).

## 20. New Physical Test Results

Final33new,0fail/errors/skips; complete final162 in oneactual run, no summing focused repeats. Focused3cases anddriverexit0 are separately retained. [TEST-new-warning-notification33.xml](../tools/ui-harness/evidence/p5e08-usb/TEST-new-warning-notification33.xml), [focused/TEST-production-ui.xml](../tools/ui-harness/evidence/p5e08-usb/focused/TEST-production-ui.xml), [physical-driver-exits.json](../tools/ui-harness/evidence/p5e08-usb/physical-driver-exits.json).

Earlier session observed filter/accessibility races and intermittentoldAuth UItimeouts. Their /tmpXML was lost; these are historical observations, not auditablePASS/FAILtotals. New nativehelper waits transitions/filterexclusion/eventidle; old assertions untouched. Runner waits ownprocessteardown. Rootcause of earlier intermittentAuth timeouts not conclusively established; no productionAuth fix claimed. Only newpersisted complete162 result is acceptance.

Reproduce: `python3 tools/ui-harness/run_ui_tests.py --physical-usb --evidence-dir /tmp/p5e08-fresh-evidence` (destination mustnotexist; fordurableproof choose persistentdestination).

## 21. Auth/Navigation Regression

ActualXML 42tests/8suites,0fail/errors/skips,exit0. Command: `./gradlew --offline --no-daemon :app:testDebugUnitTest --tests 'com.example.bai1.auth.*' --tests 'com.example.bai1.presentation.navigation.*'`. [jvm-build/auth-navigation-xml](../tools/ui-harness/evidence/p5e08-usb/jvm-build/auth-navigation-xml), [jvm-build/auth-navigation.log](../tools/ui-harness/evidence/p5e08-usb/jvm-build/auth-navigation.log).

## 22. Routing JVM Regression

Fresh 462tests/13suites,0fail/errors/skips,exit0. Command: `./gradlew --offline --no-daemon :app:testDebugUnitTest --rerun-tasks --tests 'com.example.bai1.rescue.routing.*'`. [jvm-build/routing-xml](../tools/ui-harness/evidence/p5e08-usb/jvm-build/routing-xml), [jvm-build/routing.log](../tools/ui-harness/evidence/p5e08-usb/jvm-build/routing.log).

## 23. Full JVM Regression

Actual 545tests/32suites,0fail/errors/skips,exit0. `./gradlew --offline --no-daemon :app:testDebugUnitTest`. Counts from newXML, not copiedbaseline. No mirrorunit tests for Composable seam; new33physical exercise behavior. [jvm-build/full-xml](../tools/ui-harness/evidence/p5e08-usb/jvm-build/full-xml), [jvm-build/full.log](../tools/ui-harness/evidence/p5e08-usb/jvm-build/full.log).

## 24. APK Build Results

DebugAPK BUILD SUCCESSFUL exit0;95,686,058bytes;SHA256 `4981213710b1cc888bd5662e2e2c24d7e73263896a843ee5d3d23e11b7b5f0a2`. `./gradlew --offline --no-daemon :app:assembleDebug :app:processReleaseMainManifest`. [jvm-build/apk.log](../tools/ui-harness/evidence/p5e08-usb/jvm-build/apk.log). Standalonetarget/testbuild0+securitygatePASS; hashes [physical-final/results.json](../tools/ui-harness/evidence/p5e08-usb/physical-final/results.json). MainAPK neverinstalled. Java25/Gradle9.7.1/AGP9.4.0/KotlinCompose2.2.10 unchanged; no dependencyupgrade. [tool-versions.txt](../tools/ui-harness/evidence/p5e08-usb/tool-versions.txt).

## 25. Logcat/Crash/Privacy

OnlyownPID/per-testwindow; no globallogcatclear. FATAL/ANR/navigation/coroutine/runtimeexception/SDKinit/credentialliteral scans no findings in newpersistedevidence. [log-review.json](../tools/ui-harness/evidence/p5e08-usb/log-review.json). ExpectedHTTPfaults tested, fixtureevents omit bodies/tokens; no private screenshot/accountdata collection. Vendor caught Receiver-not-registered warnings are not fatal/ANR proof.

## 26. Phone Data Preservation

Resume found2leftovertaskAPKs; beforecleanup installedpublicAPK SHA256 matched exactownedbuilds, thenonlythose2uninstalled. [resume-owned-package-cleanup.json](../tools/ui-harness/evidence/p5e08-usb/resume-owned-package-cleanup.json). Newrun requiresbothabsent, install-t never-r, ownUID proof. No productioninstall/launch/root/reset/permissiongrant/private-data query. Final ownpackagesabsent/reversebaselineempty/phoneauthorized. [device-and-cleanup.json](../tools/ui-harness/evidence/p5e08-usb/device-and-cleanup.json). No claim of forensic hashing personalphonefiles.

## 27. Backend/Graph Preservation

Backend677/677 exactSHA+mtime,0new/removed;20graphmanifest/GZIP artifacts preserved. Androidrouting/SOS/Rescue/auth/coregraph/defaultconfig unchanged; gitdiffcheck0bothrepos. [backend-preservation.json](../tools/ui-harness/evidence/p5e08-usb/backend-preservation.json), [android-preservation.json](../tools/ui-harness/evidence/p5e08-usb/android-preservation.json), [route-preservation.json](../tools/ui-harness/evidence/p5e08-usb/route-preservation.json).

## 28. Remaining F06/Parity/P5-I02 Work

F06OPEN; production-authoritativehazard/qualification verifier absent. P5-P01/parity/P5-I02 not performed. `NO PRODUCTION-AUTHORITATIVE QUALIFICATION => STRICT_ROUTING_DISABLED`. No defaultCLEAR; empty/synthetic/hash/USER/RESCUER/READY/VERIFIED_COMPATIBLE/devicePASS never authorizes saferouting.

## 29. Remaining Maps/SOS/Rescue/Cloud Blockers

Maps/SOS/RescueNOT_RUN; routingUI NOT_IMPLEMENTED; realFCM/pushNOT_RUN. Need approvedwarningAPI/source/time/freshness/unknown semantics, selectedIDdetail, notificationfilter/history/readstate, loading/error/offlinecontract. Productionstaticlivecopy and fixedflood.firstmissingrecord handling remain blockers. IsolatedAuthfixture doesnot certify realFirebase/credential/account lifecycle.

## 30. Final Acceptance

**CONDITIONAL — LOCAL_STATIC_UI_SCOPE.** Actualscreen/static behavior tested; no live/authoritative/readiness claim.

```text
PRODUCTION_WARNING_UI = CONDITIONAL (actual list; local static/injected data)
WARNING_DETAIL_UI = CONDITIONAL (fixed templates; generic selected detail NOT_IMPLEMENTED)
IN_APP_NOTIFICATION_UI = CONDITIONAL (static rows/navigation; filters/history NOT_IMPLEMENTED)
REAL_PUSH_NOTIFICATION_E2E = NOT_RUN
MAIN_ACTIVITY_ISOLATED = PASS
PRODUCTION_APP_NAV_GRAPH = PASS (tested auth/navigation scope)
PRODUCTION_HOME_UI = PASS (USER static warning/notification callbacks)
MAPS_UI = NOT_RUN
SOS_UI = NOT_RUN
RESCUE_UI = NOT_RUN
ROUTING_UI = NOT_IMPLEMENTED
F06 = OPEN
STRICT_ROUTING_DISABLED = true
FULL_APPLICATION_E2E = NOT_RUN
```

No realdisasterwarning/area-safe/route-safe/fullappPASS. No commit/push/deploy.

## 31. Recommended Next Task

Separate task: define approvedread-onlywarning/notificationcontract with source/time/freshness/unknown semantics; map selectedIDtocorrectdetail; implementnotificationcategories/history and remove mock-livecopy. Then fixture-backed loading/error/offline/stale matrix against actualcontract. Do not startF06/Maps/SOS/Rescue/push/strictactivation here.
