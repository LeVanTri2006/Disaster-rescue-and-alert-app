from pathlib import Path
import os,json,hashlib,re,shutil,subprocess,zipfile,xml.etree.ElementTree as E
r=Path(__file__).resolve().parents[5];e=r/'tools/ui-harness/evidence/p5e08-usb';report=r/'docs/PHASE5_P5E08_USB_READONLY_WARNING_NOTIFICATION_REPORT.md'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def dump(n,v):(e/n).write_text(json.dumps(v,indent=2,ensure_ascii=False))
def snap(root):
 out={}
 for b,ds,fs in os.walk(root):
  ds[:]=[d for d in ds if d not in {'.git','.gradle','build','dist','node_modules','.idea','.kotlin','__pycache__'}]
  for n in fs:
   p=Path(b)/n
   if p.is_file():out[str(p.relative_to(root))]={'sha256':sha(p),'mtime_ns':str(p.stat().st_mtime_ns)}
 return out
p=json.loads((e/'physical-final/results.json').read_text());j=json.loads((e/'jvm-build/results.json').read_text());drivers=json.loads((e/'physical-driver-exits.json').read_text())
assert p['device_assertions']=='PASS' and len(p['cases'])==162 and p['owned_reverse_removed'] and not p['owned_packages_remaining'] and not p['cleanup_errors']
assert all(x['exit_code']==0 for x in drivers.values()) and all(x['exit_code']==0 for x in j.values())
assert all(c['adb_exit_code']==0 and all(c[k]==0 for k in ['failures','errors','skipped']) for c in p['cases'])
hist=r/'tools/ui-harness/evidence/p5e07-usb/physical-final/TEST-production-ui.xml';old={(x.get('classname'),x.get('name')) for x in E.parse(hist).getroot().findall('testcase')};assert len(old)==129
actual=E.parse(e/'physical-final/TEST-production-ui.xml').getroot();pairs={(x.get('classname'),x.get('name')) for x in actual.findall('testcase')};assert len(pairs)==162 and old<=pairs;new=pairs-old;assert len(new)==33
assert all(c=='com.example.bai1.fixture.ProductionWarningUiTest' for c,n in new)
groups={}
for label,wanted in [('old129',old),('new-warning-notification33',new)]:
 cases=[x for x in actual.findall('testcase') if (x.get('classname'),x.get('name')) in wanted];t=E.Element('testsuite',name='P5E08_'+label,tests=str(len(cases)),failures='0',errors='0',skipped='0')
 for x in cases:assert x.find('failure') is None and x.find('error') is None and x.find('skipped') is None;t.append(x)
 E.ElementTree(t).write(e/('TEST-'+label+'.xml'),encoding='utf-8',xml_declaration=True);groups[label]=len(cases)
dump('historical-old129-junit-names.json',sorted(old))
restarts=[]
for i,c in enumerate(p['cases']):
 cmd=c['command']
 if cmd[cmd.index('preserveAuthSession')+1]=='true':
  before=p['cases'][i-1];assert before['pid'] and c['pid'] and before['pid']!=c['pid'];restarts.append({'prepare':before['name'],'restart':c['name'],'distinct_pids':True,'restart_events':len(c['fixture_events'])})
assert len(restarts)==10
nc=[c for c in p['cases'] if (c['test_class'],c['name']) in new];paths=sorted({v['path'] for c in nc for v in c['fixture_events']});assert set(paths)<={'/auth/login','/auth/me','/auth/refresh','/auth/firebase-token','/routing/current'}
dump('coverage-groups.json',{'groups':groups,'unique_final':162,'historical_xml':str(hist.relative_to(r)),'historical_xml_sha256':sha(hist),'old129_set_equality':True,'repetitions_excluded':True,'class_counts':{c:sum(x.get('classname')==c for x in actual.findall('testcase')) for c,n in pairs},'restart_pairs':restarts,'new_scope_http_paths':paths,'warning_notification_http_endpoints':[]})
# Actual isolated manifests, plus summaries of production configuration without metadata values.
a='{http://schemas.android.com/apk/res/android}';summaries={}
for label,path in [('isolated-target-manifest.xml',r/'tools/ui-harness/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml'),('isolated-test-manifest.xml',r/'tools/ui-harness/build/intermediates/packaged_manifests/debugAndroidTest/processDebugAndroidTestManifest/AndroidManifest.xml')]:
 x=E.parse(path).getroot();assert x.get(a+'sharedUserId') is None;shutil.copy2(path,e/label)
for variant in ['debug','release']:
 path=r/f'app/build/intermediates/merged_manifests/{variant}/process{variant.title()}Manifest/AndroidManifest.xml'
 if not path.exists():path=r/f'app/build/intermediates/merged_manifest/{variant}/process{variant.title()}MainManifest/AndroidManifest.xml'
 x=E.parse(path).getroot();app=x.find('application');assert app.get(a+'name')=='com.example.bai1.DisasterApplication';assert any(v.get(a+'name')=='com.google.firebase.provider.FirebaseInitProvider' for v in app.findall('provider'))
 summaries[variant]={'sha256':sha(path),'application':app.get(a+'name'),'components':[{'tag':v.tag,'name':v.get(a+'name'),'exported':v.get(a+'exported')} for v in app if v.tag in ['provider','service','receiver']],'metadata_names':[v.get(a+'name') for v in app.findall('meta-data')],'permissions':[v.get(a+'name') for v in x.findall('uses-permission')]}
dump('production-manifest-audit.json',summaries)
apk=r/'app/build/outputs/apk/debug/app-debug.apk'
with zipfile.ZipFile(apk) as z:dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
required=['Lcom/example/bai1/DisasterApplication;','Lcom/example/bai1/MainActivity;','Lcom/example/bai1/presentation/navigation/AppNavGraphKt;','Lcom/example/bai1/presentation/navigation/ProductionAppNavScreens;','Lcom/google/firebase/provider/FirebaseInitProvider;']
absent=['Lcom/example/bai1/uiisolated/ActualWarningFixtureScreens;','Lcom/example/bai1/uiisolated/ActualAccountFixtureScreens;','Lcom/example/bai1/uiisolated/GraphFixtureApplication;']
assert all(v.encode() in dex for v in required) and all(v.encode() not in dex for v in absent)
dump('main-apk-boundary.json',{'status':'PASS','sha256':sha(apk),'bytes':apk.stat().st_size,'required_production_classes':required,'isolated_classes_absent':absent,'production_apk_installed':False})
# Log privacy/crash review: operational evidence, excluding source-support scripts.
secrets=['fixture-only'+'-password','p5e02-'+'access-synthetic-only','p5e02-'+'refresh-synthetic-only','p5e04-'+'custom-synthetic-only','p5e04-'+'expired-synthetic'];fatal=[];sdk=[];exceptions=[]
for file in e.rglob('*'):
 if not file.is_file() or file.suffix in ['.py','.pyc']:continue
 text=file.read_text(errors='replace');assert not any(s in text for s in secrets),str(file)
 if file.name=='owned-process-logcat.txt':
  for line in text.splitlines():
   if re.search('FATAL EXCEPTION|ANR in |Application Not Responding',line,re.I):fatal.append(str(file.relative_to(e)))
   if re.search('FirebaseApp.*initializ|FirebaseInitProvider|Firebase sign-in started|Credential request started',line,re.I):sdk.append(str(file.relative_to(e)))
   if re.search('IllegalStateException|NullPointerException|CoroutineException',line):exceptions.append({'file':str(file.relative_to(e)),'line':line})
assert not fatal and not sdk and not exceptions,(fatal,sdk,exceptions)
dump('log-review.json',{'scope':'own PID/per-test time window only','fatal_anr':fatal,'unexpected_sdk_initialization':sdk,'runtime_exceptions':exceptions,'credential_literal_leaks':0,'screenshots':'NOT_RUN','global_logcat_clear':False})
adb='/home/vantri/Android/Sdk/platform-tools/adb';lines=subprocess.check_output([adb,'devices','-l'],text=True).splitlines()[1:];phones=[l.split()[0] for l in lines if 'usb:' in l and not l.startswith('emulator')];assert len(phones)==1;serial=phones[0]
def run(*args):
 x=subprocess.run([adb,'-s',serial,*args],capture_output=True,text=True);return {'exit_code':x.returncode,'stdout':x.stdout.strip().replace(serial,'REDACTED'),'stderr':x.stderr.strip().replace(serial,'REDACTED')}
cleanup={'serial':'REDACTED','state':run('get-state'),'model':run('shell','getprop','ro.product.model'),'api':run('shell','getprop','ro.build.version.sdk'),'disk':run('shell','df','-h','/data'),'reverse':run('reverse','--list'),'owned_packages':{pkg:run('shell','pm','path',pkg) for pkg in ['com.example.bai1.p5e02.uiisolated','com.example.bai1.p5e02.uiisolated.test']}}
assert cleanup['state']['stdout']=='device' and cleanup['reverse']['stdout']=='' and all(not v['stdout'] for v in cleanup['owned_packages'].values());dump('device-and-cleanup.json',cleanup)
(e/'disk-after.txt').write_text(subprocess.check_output(['df','-h','/','/home'],text=True))
base=json.loads((e/'android-baseline.json').read_text());now=snap(r);assert set(base)<=set(now);changed={k:{'before':v,'after':now[k]} for k,v in base.items() if now[k]!=v}
expected={'app/src/main/java/com/example/bai1/presentation/warning/WarningListScreen.kt','tools/ui-harness/build.gradle.kts','tools/ui-harness/run_ui_tests.py','tools/ui-harness/src/main/java/com/example/bai1/uiisolated/GraphFixtureApplication.kt'};assert set(changed)==expected,set(changed)
oldtests=['tools/ui-harness/src/androidTest/java/com/example/bai1/fixture/ProductionSubsetUiTest.kt','tools/ui-harness/src/androidTest/java/com/example/bai1/fixture/ProductionGraphUiTest.kt','tools/ui-harness/src/androidTest/java/com/example/bai1/fixture/ProductionAccountUiTest.kt','app/src/test/java/com/example/bai1/auth/AuthLifecycleSafetyTest.kt'];assert all(now[k]==base[k] for k in oldtests)
graph='app/src/main/java/com/example/bai1/presentation/navigation/AppNavGraph.kt';assert now[graph]==base[graph];dump('route-preservation.json',{'status':'PASS','before':base[graph],'after':now[graph],'routes':27,'old129_test_sources_unchanged':oldtests})
dump('android-preservation.json',{'baseline_files':len(base),'unchanged_hash_mtime':len(base)-len(changed),'changed':changed,'removed':[]})
broot=Path('/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app');bb=json.loads((e/'backend-baseline.json').read_text());assert snap(broot)==bb;graphs=[k for k in bb if 'tools/osm-importer/output' in k and k.endswith(('.json','.gz'))];assert len(graphs)==20
dump('backend-preservation.json',{'baseline_files':len(bb),'unchanged_hash_mtime':len(bb),'new_files':[],'removed':[],'graph_artifacts':graphs})
for label,root in [('android',r),('backend',broot)]:
 assert subprocess.run(['git','diff','--check'],cwd=root,capture_output=True).returncode==0
 (e/(label+'-status-after.txt')).write_text(subprocess.check_output(['git','status','--short'],cwd=root,text=True))
# Render the requested 31-section report using actual execution evidence.
def L(n,label=None):return '['+(label or n)+'](../tools/ui-harness/evidence/p5e08-usb/'+n+')'
def S(n):return '['+Path(n).name+'](../'+n+')'
sections=[]
def sec(title,body):sections.append('## '+str(len(sections)+1)+'. '+title+'\n\n'+body.strip()+'\n')
sec('Executive Summary',f'''**CONDITIONAL — LOCAL_STATIC_UI_SCOPE.** Actual production WarningListScreen, StormDetailScreen, FloodDetailScreen và NotificationScreen chạy qua actual MainActivity/AppNavGraph trên OPPO USB. Các screen hiện chỉ dùng demo tĩnh; chưa có authoritative/live warning API.

Final physical **162 unique tests = old129 + new33**,0failures/errors/skipped; driver và mọi instrumentation exit0. Fresh routing **{j['routing']['tests']}**, Auth/navigation **{j['auth-navigation']['tests']}**, full JVM **{j['full']['tests']}**,0fail/errors/skips; APK buildPASS. Không cộng focused repetitions. {L('physical-final/TEST-production-ui.xml')}, {L('coverage-groups.json')}, {L('jvm-build/results.json')}.

Phiên trước bị gián đoạn và evidence trong /tmp mất; những lượt đó KHÔNG tính nghiệm thu vì không còn XML. Báo cáo chỉ dùng bằng chứng mới được lưu trực tiếp trong repo, ngày2026-11-09.''')
sec('Laptop/Phone Disk Gate',f'''Root và /home vượt2GB trước resume/build; runner và từng JVM phase kiểm ngưỡng trước ghi lớn. Phone còn khoảng8.2GB. Không dọn Docker/Snap/Gradle/SDK/emulator/file cá nhân. {L('disk-before.txt')}, {L('disk-after.txt')}, {L('resume-state.json')}, {L('environment-before.json')}.''')
sec('USB Device Inventory',f'''OPPO CPH2159 / Android13 / API33 / physicalUSB, authorizeddevice, boot1, qemu khác1. Serial chọn động từ inventory, redacted; không emulator/hardcode serial. API33 không chứng minh permissionAPI37. {L('environment-before.json')}, {L('device-and-cleanup.json')}.''')
sec('Repository Baseline',f'''Android /home/vantri/AndroidStudioProjects/bai1; Backend /home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app READ ONLY. Đã đọc E07/E06/E05/E04/E03/NET/CACHE/E01R; không AGENTS.md áp dụng. Hai repo có inheriteddirty, không reset/clean/commit/push/deploy.

Snapshot trước sửa ban đầu đã mất cùng/tmp. Baseline được **tái dựng từ E07 final persisted inventory**, gồm after SHA-256/mtime_ns của8edited+1022new và1453baseline;2recursive inventory entries dùng files E07 hiện có. Kết quả2475Android files, chỉ4existingfiles khác;677Backend files khớp toàn bộ. Không giả mạo snapshot mới thành snapshot trước sửa. {L('resume-state.json')}, {L('android-baseline.json')}, {L('backend-baseline.json')}, {L('changed-files.json')}.

Loại khỏi snapshot .git/.gradle/build/dist/node_modules/.idea/.kotlin/__pycache__; source/config/tests/docs và untracked ngoài exclusions được kiểm.''')
sec('Existing Warning/Notification Feature Inventory','''| Component | Actual source/data |
|---|---|
| WarningListScreen | Production Composable;6static WarningItems; category filtering/clickID |
| StormDetailScreen | Fixed emergencyStormAlert/Yagi, không selectedID argument |
| FloodDetailScreen | Fixed flood_quangnam record; first lookup assumes it exists |
| NotificationScreen |7inline mock rows; tabs đổi selection style, không filtering; rows không detailclick |
| DisasterRepository | Pure in-memory lists/StateFlowrole; notificationList model data không được actual screen dùng |
| Home USER | Static cards/category/notification callbacks |
| Warning/Notification VM/API/FCM receiver/listener | Không có implementation trong audited source |

Không tạo screen thay thế hoặc Backend contract mới.''')
sec('Production Screen Dependency Audit','''| Component | Source | Data | Network | Permission | Realtime | Auth | Safe isolated |
|---|---|---|---|---|---|---|---|
| Warning list | Production Composable | Local/injected List | None | None | No | Actual graph protected | Yes |
| Storm detail | Production Composable | Static Yagi | None | None | No | Protected | Yes with demo banner |
| Flood detail | Production Composable | Static flood | None | None | No | Protected | Yes; Map not opened |
| Notification | Production Composable | Inline static7 | None | None | No | Protected | Yes with banner |
| Home USER | Production Composable | Static repo | None | None | No | Protected | Tested callbacks |
| DisasterRepository | Production singleton | In-memory | None | None | No | Via graph | Yes |

Share no-op; Follow is localBoolean only, không subscription. Map callback keeps unavailable binding. Audit đã thực hiện trước edit ở phiên đầu; bảng này được tái lập từ source còn nguyên sau resume, không có artifact audit-before-edit còn lại.''')
sec('Static vs Fixture vs Live Data Classification','''LOCAL_STATIC_UI_SCOPE. Exact production static lists/images; test-only owned List injection cung cấp empty/default/long/duplicate/rawtime samples. Auth/routing dùng owned HTTP fixture; warning/notification không có HTTP interface. Live/authoritative warning NOT_IMPLEMENTED; pushNOT_RUN.

Warning mode luôn hiển thị global banner **SYNTHETIC_TEST_DATA — DEMO**, “Không phải cảnh báo thực tế; dữ liệu chưa xác minh. Không có dữ liệu không đồng nghĩa không có nguy cơ.” Bao cả Home/detail/video/notifications/restart. Existing “TRỰC TIẾP/Trực tiếp”, relative times và mockSOS chỉ là demo copy. Production mock-live labels vẫn cần task riêng trước live integration.''')
sec('Warning Data Contract','''WarningItem: id/title/subtitle/currentLocation non-nullString; categorySTORM/FLOOD/LANDSLIDE/RESCUE; severityCRITICAL/WARNING/ALERT/INFO. updateTime là displayString, list takeLast(5), không parse thời gian/expiry. Optional movementDirection/intensity/waterLevel/currentStatus defaultemptyString, affectedAreas defaultemptyList, isEmergencyAlert defaultfalse.

Không source/verified/expiresAt/currentauthority fields, uniqueID enforcement hoặc freshness validation. NotificationItem model có displaytimestamp/category/severity/isUnread nhưng actual UI bỏ qua model. Nullable JSON/malformed response N/A với typedList seam, không tự phát minh Backend support.''')
sec('Minimal UI Isolation Design','''WarningListScreen thêm optional warnings:List<WarningItem> với defaultDisasterRepository.warningList; remember filter theo cả filter và list; reuse EmptyState với copy empty!=safe. Default records/callbacks giữ nguyên.

Standalone ActualWarningFixtureScreens delegates ActualAccountFixtureScreens, overrides4actualComposables. GraphFixtureApplication warningScreens flag bật actual factories, owned observableList và banner quanh actual AppNavGraph. Old modes không bật flag. Không thay AuthRepository/ViewModel, policy, graph/routes, SOS/Rescue/routing/Backend.''')
sec('Exact Files Changed','\n'.join('- '+S(k) for k in sorted(changed))+'''\n\nNew implementation/test: ActualWarningFixtureScreens.kt và ProductionWarningUiTest.kt. Báo cáo và toàn bộ evidence/support scripts là files mới. '''+L('changed-files.json')+' chứa everypath/before-afterSHA/mtime; '+L('evidence-index.json')+' chứa evidence hashes/sizes. Old129 test source/assertions giữ nguyên exact SHA+mtime.')
sec('MainActivity/AppNavGraph Integration',f'''Exact MainActivity→MainAppHost→actualAppNavGraph;27routes/guards/callbacks giữ nguyên SHA+mtime. No replacementNavHost.

Home STORM/LANDSLIDE→list, FLOOD→fixed flood. List idcontainsflood→fixed flood, mọiIDkhác→fixedYagi. initialCategory chưa được graph truyền. **Generic selected-item detail NOT_IMPLEMENTED**; test syntheticnonfloodID xác nhận limitation, không gọi đó là correctselectedrecordPASS. {L('route-preservation.json')}.''')
sec('Manifest/DEX Security Proof',f'''Pre-install gatePASS: owned applicationId absent trước install; no sharedUserId; distinct non-systemUIDs. Target2activities nonexported, nointentfilter/provider/service/receiver/SDKmetadata. TestAPK chỉ3AndroidX bootstrapactivities; noSDKstartup. No location/camera/SMS/microphone/notificationpermission.

BothDEX cloud/business exclusions, exact4productionwarningclasses required, **39byte-equal production sourcecopies** +exact sharedlifecycle test. Startup code/origin/path allowlist, productionconfiguration values0matches. Main debug/release defaults remain DisasterApplication/FirebaseInitProvider; mainAPK excludes isolatedfactories, never installed. {L('physical-final/startup-origin-audit.json')}, {L('physical-final/source-copy-hashes.json')}, {L('physical-final/owned-package-uid-proof.json')}, {L('isolated-target-manifest.xml')}, {L('isolated-test-manifest.xml')}, {L('production-manifest-audit.json')}, {L('main-apk-boundary.json')}.''')
sec('USB Fixture and ADB Reverse',f'''Owned loopback127.0.0.1:OS-assigned-port; dynamic adb-s reverse exactownedtcp, no remove-all. Exacthost/port/path allowlist, Proxy.NO_PROXY, redirects/retriesfalse, boundedrequests/timeouts, no forwarding/DB/email/SMS/push/SOS.

No warning/notificationHTTPendpoint fabricated. Newcases events only authlogin/me/refresh/firebase-token and routing/current; no rolePATCH or warningmutation. AuthPOST is fixture dependency, not warningwrite. {L('coverage-groups.json')}, {L('physical-final/results.json')}.''')
def table(cases):return '| Actual JUnit method | Result |\n|---|---|\n'+'\n'.join('| '+c['name']+' | PASS |' for c in cases)
sec('Actual Warning UI Tests',table([c for c in nc if c['name'].startswith('warning')])+f'\n\nActual nativeUI/ActivityScenario checks, eachXML/log under {L("physical-final/results.json")}. Fixedtemplate limitations explicitly tested; no generic detail correctness claim.')
sec('Actual Notification UI Tests',table([c for c in nc if c['name'].startswith('notification')])+'''\n\nStatic rows/navigation/recreation/offline/expiry chạy thật. Selection-only tab test confirms missingfilterfeature. Notificationdetails/history/readstate/backendempty/error NOT_IMPLEMENTED; OS pushNOT_RUN, no notificationpermission/subscription.''')
sec('Data Empty/Error/Stale/Unknown Safety','''| State | Actual result |
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

Unknown/stale/synthetic never grants verified/current/area-safe or strict routing.''')
sec('Home-to-Warning Navigation','''Actual graph callbacks: Homecategory→list→originalYagi detail→backlist→Home; filteredoriginalflood→FloodDetail→backlist; Home→notification→backHome/Profile. Logout/expiry removes protected history while on warning/detail/notification. Maps/SOS/Rescue productionUI staysNOT_RUN; unavailablebinding not evidence for thoseUI.''')
sec('Activity/Process Restart',f'''Actual list/detail/notification recreation tests;10processrestartpairs final (8old+2new), distinctPIDs, predecessorPASS, ownstoragepreserved only. New warningdetailprepare restoresexistingHome semantics afterprocessrestart, not selecteddetail. Expirednotification prepare cannot restoreprotectedrows. {L('coverage-groups.json')}.

Runner now waits ownPIDgone afterforce-stop plus750ms teardown; no OSsettings/permissions altered. Newtest waits NavHost/filter/accessibility updates beforeclick. APIclearCache was checked locally: introducedAPI34, so not used onAPI33.''')
sec('Old129 Physical Regression',f'''Names from historical **actualJUnitXML**, exactclassname+methodset129. Source/assertions unchanged; final old1290fail/errors/skips/allADB0. Contains56subsetUI +20sharedrepository lifecycle tests inAndroidprocess +23graph +30account; not all129 are UIinteractions. {L('historical-old129-junit-names.json')}, {L('TEST-old129.xml')}, {L('coverage-groups.json')}.''')
sec('New Physical Test Results',f'''Final33new,0fail/errors/skips; complete final162 in oneactual run, no summing focused repeats. Focused3cases anddriverexit0 are separately retained. {L('TEST-new-warning-notification33.xml')}, {L('focused/TEST-production-ui.xml')}, {L('physical-driver-exits.json')}.

Earlier session observed filter/accessibility races and intermittentoldAuth UItimeouts. Their /tmpXML was lost; these are historical observations, not auditablePASS/FAILtotals. New nativehelper waits transitions/filterexclusion/eventidle; old assertions untouched. Runner waits ownprocessteardown. Rootcause of earlier intermittentAuth timeouts not conclusively established; no productionAuth fix claimed. Only newpersisted complete162 result is acceptance.

Reproduce: `python3 tools/ui-harness/run_ui_tests.py --physical-usb --evidence-dir /tmp/p5e08-fresh-evidence` (destination mustnotexist; fordurableproof choose persistentdestination).''')
sec('Auth/Navigation Regression',f'''ActualXML {j['auth-navigation']['tests']}tests/{j['auth-navigation']['suites']}suites,0fail/errors/skips,exit0. Command: `./gradlew --offline --no-daemon :app:testDebugUnitTest --tests 'com.example.bai1.auth.*' --tests 'com.example.bai1.presentation.navigation.*'`. {L('jvm-build/auth-navigation-xml')}, {L('jvm-build/auth-navigation.log')}.''')
sec('Routing JVM Regression',f'''Fresh {j['routing']['tests']}tests/{j['routing']['suites']}suites,0fail/errors/skips,exit0. Command: `./gradlew --offline --no-daemon :app:testDebugUnitTest --rerun-tasks --tests 'com.example.bai1.rescue.routing.*'`. {L('jvm-build/routing-xml')}, {L('jvm-build/routing.log')}.''')
sec('Full JVM Regression',f'''Actual {j['full']['tests']}tests/{j['full']['suites']}suites,0fail/errors/skips,exit0. `./gradlew --offline --no-daemon :app:testDebugUnitTest`. Counts from newXML, not copiedbaseline. No mirrorunit tests for Composable seam; new33physical exercise behavior. {L('jvm-build/full-xml')}, {L('jvm-build/full.log')}.''')
sec('APK Build Results',f'''DebugAPK BUILD SUCCESSFUL exit0;{j['apk']['size']:,}bytes;SHA256 `{j['apk']['sha256']}`. `./gradlew --offline --no-daemon :app:assembleDebug :app:processReleaseMainManifest`. {L('jvm-build/apk.log')}. Standalonetarget/testbuild0+securitygatePASS; hashes {L('physical-final/results.json')}. MainAPK neverinstalled. Java25/Gradle9.7.1/AGP9.4.0/KotlinCompose2.2.10 unchanged; no dependencyupgrade. {L('tool-versions.txt')}.''')
sec('Logcat/Crash/Privacy',f'''OnlyownPID/per-testwindow; no globallogcatclear. FATAL/ANR/navigation/coroutine/runtimeexception/SDKinit/credentialliteral scans no findings in newpersistedevidence. {L('log-review.json')}. ExpectedHTTPfaults tested, fixtureevents omit bodies/tokens; no private screenshot/accountdata collection. Vendor caught Receiver-not-registered warnings are not fatal/ANR proof.''')
sec('Phone Data Preservation',f'''Resume found2leftovertaskAPKs; beforecleanup installedpublicAPK SHA256 matched exactownedbuilds, thenonlythose2uninstalled. {L('resume-owned-package-cleanup.json')}. Newrun requiresbothabsent, install-t never-r, ownUID proof. No productioninstall/launch/root/reset/permissiongrant/private-data query. Final ownpackagesabsent/reversebaselineempty/phoneauthorized. {L('device-and-cleanup.json')}. No claim of forensic hashing personalphonefiles.''')
sec('Backend/Graph Preservation',f'''Backend677/677 exactSHA+mtime,0new/removed;20graphmanifest/GZIP artifacts preserved. Androidrouting/SOS/Rescue/auth/coregraph/defaultconfig unchanged; gitdiffcheck0bothrepos. {L('backend-preservation.json')}, {L('android-preservation.json')}, {L('route-preservation.json')}.''')
sec('Remaining F06/Parity/P5-I02 Work','''F06OPEN; production-authoritativehazard/qualification verifier absent. P5-P01/parity/P5-I02 not performed. `NO PRODUCTION-AUTHORITATIVE QUALIFICATION => STRICT_ROUTING_DISABLED`. No defaultCLEAR; empty/synthetic/hash/USER/RESCUER/READY/VERIFIED_COMPATIBLE/devicePASS never authorizes saferouting.''')
sec('Remaining Maps/SOS/Rescue/Cloud Blockers','''Maps/SOS/RescueNOT_RUN; routingUI NOT_IMPLEMENTED; realFCM/pushNOT_RUN. Need approvedwarningAPI/source/time/freshness/unknown semantics, selectedIDdetail, notificationfilter/history/readstate, loading/error/offlinecontract. Productionstaticlivecopy and fixedflood.firstmissingrecord handling remain blockers. IsolatedAuthfixture doesnot certify realFirebase/credential/account lifecycle.''')
sec('Final Acceptance','''**CONDITIONAL — LOCAL_STATIC_UI_SCOPE.** Actualscreen/static behavior tested; no live/authoritative/readiness claim.

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

No realdisasterwarning/area-safe/route-safe/fullappPASS. No commit/push/deploy.''')
sec('Recommended Next Task','''Separate task: define approvedread-onlywarning/notificationcontract with source/time/freshness/unknown semantics; map selectedIDtocorrectdetail; implementnotificationcategories/history and remove mock-livecopy. Then fixture-backed loading/error/offline/stale matrix against actualcontract. Do not startF06/Maps/SOS/Rescue/push/strictactivation here.''')
assert len(sections)==31;report.write_text('# PHASE 5 – P5-E08-USB: Read-only Warning & Notification UI Isolation\n\nThực thi 2026-11-08–09, Asia/Bangkok. Evidence nghiệm thu mới lưu ngày2026-11-09.\n\n'+'\n'.join(sections))
# Exact files and final evidence inventory. Self hashes intentionally omitted.
now=snap(r);newpaths=sorted(set(now)-set(base));special={str((e/n).relative_to(r)) for n in ['changed-files.json','evidence-index.json']}
entries=[{'path':k,'before':None,'after':now[k]} for k in newpaths if k not in special]
for k in special:entries.append({'path':k,'before':None,'after':None,'note':'Recursive inventory self-hash omitted'})
dump('changed-files.json',{'baseline_provenance':'reconstructed E07 final inventory after interrupted /tmp evidence loss','baseline_files':len(base),'unchanged_baseline_files':len(base)-len(changed),'edited_baseline_files':[{'path':k,**v} for k,v in changed.items()],'new_files':entries,'removed_files':[]})
for link in re.findall(r'\]\(([^)]+)\)',report.read_text()):
 if not link.startswith(('http:','https:','#')):assert (report.parent/link).exists() or (report.parent/link).resolve()==e/'evidence-index.json',link
assert re.findall(r'^## (\d+)\. ',report.read_text(),re.M)==list(map(str,range(1,32)))
dump('evidence-index.json',{str(f.relative_to(e)):{'sha256':sha(f),'bytes':f.stat().st_size} for f in e.rglob('*') if f.is_file() and '__pycache__' not in f.parts and f.name!='evidence-index.json'})
for f in [report,*[f for f in e.rglob('*') if f.is_file()]]:assert serial not in f.read_text(errors='replace'),str(f)
print(json.dumps({'sections':31,'physical':162,'old129':129,'new33':33,'jvm':j['full']['tests'],'routing':j['routing']['tests'],'backend_preserved':677,'graphs_preserved':20,'edited_existing_files':len(changed),'report':str(report),'status':'CONDITIONAL_LOCAL_STATIC_UI_SCOPE'},indent=2))
