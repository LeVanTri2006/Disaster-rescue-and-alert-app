from pathlib import Path
import os,json,hashlib,re,subprocess,xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parents[2];D=ROOT/'docs/demo';EV=D/'evidence';BACK=Path('/home/vantri/AndroidStudioProjects/canh-bao-thien-tai-backend-app')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def dump(n,v):(EV/n).write_text(json.dumps(v,indent=2,ensure_ascii=False))
def snap(root):
 out={}
 for b,dirs,fs in os.walk(root):
  dirs[:]=[d for d in dirs if d not in {'.git','.gradle','build','dist','node_modules','.idea','.kotlin','__pycache__'}]
  for n in fs:
   p=Path(b)/n
   if p.is_file():out[str(p.relative_to(root))]={'sha256':sha(p),'mtime_ns':str(p.stat().st_mtime_ns)}
 return out
p=json.loads((EV/'physical-final/results.json').read_text());j=json.loads((EV/'jvm-build/results.json').read_text())
assert p['status']=='PASS' and len(p['cases'])==16 and not p['cleanup_errors'] and not p['remaining_owned_packages'] and p['owned_reverse_removed']
x=E.parse(EV/'physical-final/TEST-demo.xml').getroot();assert x.get('tests')=='16' and all(x.get(k)=='0' for k in ['failures','errors','skipped']);assert len({c.get('name') for c in x.findall('testcase')})==16
assert all(v['exit_code']==0 and (v.get('failures',0)+v.get('errors',0)+v.get('skipped',0))==0 for v in j.values())
outputs=[json.loads(o) for c in p['cases'] for o in c.get('actual_outputs',[])];assert len(outputs)==1;output=outputs[0];assert output['status']=='STRICT_ROUTING_DISABLED' and output['path']==[];dump('actual-on-phone-output.json',output)
# Actual kernel test XML provides computational evidence, distinct from the gated phone entry.
t=E.parse(EV/'jvm-build/routing-xml/TEST-com.example.bai1.rescue.routing.UniformCostSearchRouterTest.xml').getroot();names={c.get('name') for c in t.findall('testcase')};expected={'testDirectClearRoute','testMultipleRoutesLowestCostSelection','testNoPathAvailable','testBlockedRoadSkipped','testOneWayRoadCannotBeTraversedInReverse','testInvalidEdgeDataSkipped'};assert expected<=names
for c in t.findall('testcase'):
 if c.get('name') in expected:assert c.find('failure') is None and c.find('error') is None
dump('algorithm-test-examples.json',{'source':'app/src/test/java/com/example/bai1/rescue/routing/UniformCostSearchRouterTest.kt','helper':'app/src/test/java/com/example/bai1/rescue/routing/P5A01RComputationalInspection.kt','scope':'Existing private-kernel inspection in JVM tests ONLY; same public request is denied first; no such helper packaged in demo','direct':{'test':'testDirectClearRoute','path':['A','B'],'expected_cost':150.0,'expected_distance_meters':150.0},'choice':{'test':'testMultipleRoutesLowestCostSelection','alternatives':[{'path':['A','B','D'],'cost':25.0},{'path':['A','C','D'],'cost':35.0},{'path':['A','E','D'],'cost':22.0}],'asserted_selected_path':['A','E','D'],'asserted_cost':22.0},'other_passed_methods':sorted(expected)})
base=json.loads((EV/'android-baseline.json').read_text());now=snap(ROOT);assert set(base)<=set(now);changed={k:{'before':v,'after':now[k]} for k,v in base.items() if now[k]!=v};assert not changed,changed
bb=json.loads((EV/'backend-baseline.json').read_text());assert snap(BACK)==bb;graphs=[k for k in bb if 'tools/osm-importer/output' in k and k.endswith(('.json','.gz'))];assert len(graphs)==20
dump('preservation.json',{'android_baseline_files':len(base),'android_existing_changed':changed,'android_removed':[],'backend_baseline_files':len(bb),'backend_hash_and_mtime_preserved':len(bb),'backend_new_removed':[],'graph_artifacts_preserved':graphs,'production_algorithm_authority_release_config_unchanged':True})
for name,r in [('android',ROOT),('backend',BACK)]:
 q=subprocess.run(['git','diff','--check'],cwd=r,capture_output=True,text=True);assert q.returncode==0
 (EV/(name+'-status-after.txt')).write_text(subprocess.check_output(['git','status','--short'],cwd=r,text=True))
fatal=[];sdk=[];leaks=[]
secrets=['fixture-only'+'-password','e09-synthetic-'+'access-not-a-jwt','e09-synthetic-'+'refresh-not-a-jwt']
for file in EV.rglob('*'):
 if not file.is_file() or file.suffix in ['.py','.pyc','.apk']:continue
 text=file.read_text(errors='replace')
 if any(v in text for v in secrets):leaks.append(str(file.relative_to(EV)))
 if file.name=='own-pid-logcat.txt':
  if re.search(r'FATAL EXCEPTION|ANR in |Application Not Responding',text,re.I):fatal.append(str(file.relative_to(EV)))
  if re.search(r'FirebaseApp.*initializ|FirebaseInitProvider|FusedLocationProvider.*connect',text,re.I):sdk.append(str(file.relative_to(EV)))
assert not fatal and not sdk and not leaks,(fatal,sdk,leaks);dump('log-review.json',{'own_pid_only':True,'fatal_anr':fatal,'unexpected_sdk_gps_init':sdk,'credential_literal_leaks':leaks,'screenshots':'NOT_RUN'})
adb='/home/vantri/Android/Sdk/platform-tools/adb';lines=subprocess.check_output([adb,'devices','-l'],text=True).splitlines()[1:];phones=[l.split()[0] for l in lines if 'usb:' in l and not l.startswith('emulator')];assert len(phones)==1;serial=phones[0]
def run(*a):
 q=subprocess.run([adb,'-s',serial,*a],capture_output=True,text=True);return {'exit_code':q.returncode,'stdout':q.stdout.strip().replace(serial,'REDACTED'),'stderr':q.stderr.strip().replace(serial,'REDACTED')}
cleanup={'serial':'REDACTED','state':run('get-state'),'model':run('shell','getprop','ro.product.model'),'api':run('shell','getprop','ro.build.version.sdk'),'reverse':run('reverse','--list'),'packages':{n:run('shell','pm','path',n) for n in ['com.example.bai1.p5e09.demo','com.example.bai1.p5e09.demo.test']},'disk':run('shell','df','-h','/data')};assert cleanup['state']['stdout']=='device' and cleanup['reverse']['stdout']=='' and all(not v['stdout'] for v in cleanup['packages'].values());dump('device-and-cleanup.json',cleanup)
(EV/'disk-after.txt').write_text(subprocess.check_output(['df','-h','/','/home'],text=True))
# Report starts with the five presentation questions requested by the user.
def L(path,label=None):return '['+(label or path)+']('+path+')'
rows='\n'.join('| '+v['name']+' | PASS |' for v in p['cases'])
edges='\n'.join('| '+v['id']+' | '+f"{v['lengthMeters']:.3f}"+' | '+v['condition']+' / '+v['compatibility']+' |' for v in output['edges'])
source=ROOT/'app/src/main/java/com/example/bai1/rescue/routing/algorithm/UniformCostSearchRouter.kt'
report=f'''# P5-E09-DEMO — Map, UCS và cứu hộ mô phỏng trên OPPO

Ngày thực thi: 09/11/2026, Asia/Bangkok. Buổi trình bày: **10/11/2026**. **CONDITIONAL — diagnostic/fixture demo; chưa có computed route trên điện thoại.**

1. **Ngày mai có thể demo gì?** APK riêng, graph địa lý offline 8 nút/8 cạnh, chọn A/B, parser và luật rẽ thật, gọi routing public để quan sát từ chối, gửi yêu cầu SOS synthetic tới fixture và hiển thị marker phản hồi. Có kịch bản3–5phút và phương án graph offline.
2. **Thuật toán thật nào đang chạy?** Source có **Uniform Cost Search (UCS)** trên edge-based state, priority queue. Kernel được thực thi trong JVM tests có sẵn. **Trên điện thoại public findRoute chỉ chạy validation/safety gates, chưa vào vòng lặp UCS** vì authority còn thiếu.
3. **Bản đồ đã hiển thị kết quả thuật toán chưa?** Diagnostic hiển thị graph input và trạng thái trả về của router thật; **chưa hiển thị polyline từ một path tính được**. Các đoạn xám/đỏ là cạnh đầu vào, không phải route output. Cost N/A; không diễn giải raw default0 thành đường tối ưu0m.
4. **SOS/Rescue mô phỏng đến đâu?** Form test-only gọi exact production ApiSosTransport→OkHttpSosApiClient→owned HTTP fixture, nhận ACTIVE synthetic và marker. UI SOS/Rescue production, mission accept/complete, Firebase/realtime/dispatch chưa nghiệm thu.
5. **Chưa được tuyên bố gì hoàn thiện?** Không Google Maps production, không tính tuyến tối ưu end-to-end trên điện thoại, không dẫn đường cứu hộ an toàn, GPS/cloud/push/dispatch, qualification authority/F06 hoặc Full Application E2E.

## A. Progress summary

```text
ALGORITHM_IMPLEMENTATION = PRESENT (UCS private computational kernel)
ALGORITHM_EXECUTION = PASS (existing JVM computational tests); PHONE SEARCH KERNEL = BLOCKED
GRAPH_INPUT_VALIDATION = PASS (actual parser, valid/invalid input)
MAP_PRODUCTION_UI = NOT_RUN
MAP_DIAGNOSTIC_UI = PASS (offline graph on physical phone)
ALGORITHM_TO_MAP_INTEGRATION = CONDITIONAL (public gate result; no computed path)
RESCUE_UI = TEST_ONLY_FORM_PASS; PRODUCTION_UI_NOT_RUN
SOS_FIXTURE = PASS (actual API client + transport, synthetic local server)
PHYSICAL_USB_TEST = PASS (16 unique cases)
DEMO_READY = CONDITIONAL (diagnostic/fixture rehearsal; shortest-path phone demo blocked)
DEMO_CORE = BLOCKED for computed-route demonstration until an approved path is available
F06 = OPEN
STRICT_ROUTING_DISABLED = true
FULL_APPLICATION_E2E = NOT_RUN
```

Đã ưu tiên P0/P1, không mở rộng Auth/Profile/Settings/warning/notification. Không chạy lại162physical unrelated: **NOT_RUN** trong E09 để tập trung demo; không dùng162E08 làm bằng chứng Map/SOS. Security regression mới: SOS26/7suites, routing462/13suites, fullJVM545/32suites,0fail/errors/skips, tất cảexit0; mainAPK và demoAPK buildPASS.

## B. Thuật toán thực tế và phạm vi tính toán

Source: `app/src/main/java/com/example/bai1/rescue/routing/algorithm/UniformCostSearchRouter.kt` (SHA256 `{sha(source)}`). `findRoute` có overload RoadGraph và RescueRoutingContext; đều fail-closed trước `private executeSearch`. Không thêm API chẩn đoán mở khóa, không reflection gọi kernel trong APK. New adapter không gán VERIFIED_COMPATIBLE/CLEAR/canNavigateSafely=true.

| Thành phần | Thực sự làm gì |
|---|---|
| TypeScript OSM importer | Cắt way thành directed edges tại giao lộ, policy một chiều, geometry xuôi/ngược; Haversine polyline length; metadata/profile restrictions; turn restrictions |
| vehicle-profile/edge-qualifier/qualification | Đánh giá highway/access/physical/legal dimensions; thiếu evidence vẫn UNVERIFIED; không phải shortest-path search |
| Backend RoutingService | Validate manifest, signed download URL, download-only readinessfalse; không tính đường |
| Android parser/download/cache/loader | Validate contract2.1, IDs/references/coordinates/geometry/length, manifest/profile/version, rawGZIP digest/limits, atomiccache, restriction contexts; không cấp qualification |
| UCS kernel | PriorityQueue, bestCost và parent; trạng thái nodeId+incomingEdgeId+restrictionStateId; stale-queue checks; tích hợp via-node index và via-way DFA; dựng path nodes/edges/geometry |
| Public Android router | Validate input rồi từ chối khi chưa đủ authority. Không trả candidate cho demo hiện tại |
| Diagnostic UI | Vẽ graph thực từ model/parser, hiển thị input/result gates và fixture marker; không production Routing UI |

UCS chọn trạng thái có costG nhỏ nhất; tie-break insertion order. Cost cạnh = `lengthMeters × riskMultiplier`; CLEAR1, FLOODED50 chỉ sau trusted/fresh flood validation. Tổng cost là **weighted meters**, không phải ETA. Bỏ invalid/nonfinite/negative edges; UNKNOWN/BLOCKED và non-VERIFIED không được đi; turn restrictions và geometry guard là điều kiện bổ sung. Kernel trả NO_PATH nếu queue cạn, INVALID_INPUT/GRAPH_UNAVAILABLE trướcsearch; publicAPI có thể trả STRICT_ROUTING_DISABLED trước khi xét connectivity.

Vehicle: importer có profileRESCUE_TRUCK_4X4 (height3m,width2.2m,actualWeight5t,rating5.5t,axle3t và margins). Runtime router không tự đo kích thước xe; sử dụng compatibility metadata. Demo chỉ khai báo tênprofile và UNVERIFIED, **không chứng minh xe đã đạt width/height/load constraints**. Fresh routing tests giữ các guards cũ; không chạy pipeline approval production.

### Sơ đồ đúng với entry đang chạy

```mermaid
flowchart TD
 I[OSM importer / base graph contract] --> P[Android parser / integrity / cache]
 P --> G[RoadGraph + restrictions + A/B]
 G --> F[Public findRoute validation / safety gates]
 F --> D[STRICT_ROUTING_DISABLED]
 D --> U[Diagnostic graph + actual denial; path empty; cost N/A]
 K[Private UCS kernel] --> T[Existing JVM-only computational tests]
 T --> O[Assertions path / cost / NO_PATH]
```

**Không có mũi tên từ public gate bị từ chối sang private kernel.** Graph demo không dùng production graph artifact; địa lý và request đều SYNTHETIC_TEST_DATA.

### Ví dụ số từ actual kernel tests

Existing `testMultipleRoutesLowestCostSelection`: A–B–D=10+15=25; A–C–D=5+30=35; A–E–D=12+10=22. Assertions chọn `[A,E,D]`, cost22. Điều này phân biệt UCS tối ưu tổng chi phí với chọn tham lam cạnh đầu5. `testDirectClearRoute` asserts `[A,B]`, cost150/distance150. Đây là synthetic unit fixtures được helper `P5A01RComputationalInspection.kt` inspect private kernel **chỉ trong src/test**, sau khi assert public API từ chối cùng request. Helper và test qualifications không đưa vào demo APK.

Bằng chứng mới: {L('evidence/jvm-build/routing-xml/TEST-com.example.bai1.rescue.routing.UniformCostSearchRouterTest.xml','Actual UCS JUnit XML')}, {L('evidence/algorithm-test-examples.json')}. Source tests không sửa.

### Dataset trên điện thoại

| Thuộc tính | Giá trị |
|---|---|
| INPUT GRAPH | SYNTHETIC_TEST_DATA_E09_normal,8nodes/8directededges |
| START / DESTINATION | A→B; UI đổi C/H, H không có cạnh nối |
| VEHICLE PROFILE | RESCUE_TRUCK_4X4, chỉ tênprofile, chưa qualified |
| ALGORITHM / FUNCTION EXECUTED | UniformCostSearchRouter.findRoute public; TurnRestrictionIndex.checkTransition |
| TRAVERSAL CONSTRAINTS | AllUNVERIFIED; unknownroad; synthetic blockedAC/cấmAC→CB |
| COMPUTED RESULT | STRICT_ROUTING_DISABLED; path[]; visited0 |
| COST VALUE / UNIT | N/A cho route bị từ chối; inputedge lengths bằngm |
| QUALIFICATION STATUS | Unverified; no production authority |
| NAVIGATION ALLOWED | FALSE |

Input edge lengths bên dưới được lấy từ output instrumentation thật; adapter dùng hàm Haversine hiện có `distanceMeters` để tạo input. Không gọi các con số này là tổng route cost.

| Edge | Input length(m) | Condition / Compatibility |
|---|---|---|
{edges}

{L('evidence/actual-on-phone-output.json')} chứa output thật; rawDefaultCost0 chỉ là default data-class khi denied, không phải kết quả tối ưu.

### Hazard contract finding

`StreamingRoadGraphParser.parseEdge` **không đọc JSONcondition và khởi tạo CLEAR** cho basegraph. Điều này thuộc baseline/F06, không sửa trong E09. Initial demo fixture đã phát hiện mismatch qua test BLOCKED thất bại. Adapter được sửa: parse cấu trúc thật rồi **test-only conservative overlay** copy mọi cạnh thànhUNKNOWN, hoặcAC→BLOCKED theo scenario. UI ghi Overlay TEST-ONLY. Không có livehazard/freshness pipeline production; không tuyên bố đây là remediation F06.

## C. Map và SOS dependency audit

Production `DisasterMapScreen` tạo AndroidLocationProvider/GoogleMap, permission launcher, production SOS/rescue defaults; `DisasterMapViewModel` observeSOS và RESTrefresh mỗi4s. Không mang SDK/key/provider đó vào harness. Canvas offline dùng RoadGraph/RoadNode/RoadEdge đã parse, chọnA/B thật, edgeblockedđỏ, marker từ SOSresponse. Không maptiles hoặc GPS.

Production `SOSScreen` và `SosRescueRequestScreen` tự xin permission; ViewModel defaultfactory khởi tạo productionDI/location/photo dependencies. Production SOS UI **NOT_RUN**; không gọi đó là UI đã cô lập. Demoform riêng dùng exact production domainmodel, ApiSosTransport, OkHttpSosApiClient, không repositoryFirebase. RescueMissionAPI có nearby/list/create/respond/status workflows, nhưng không được demo approval/accept/complete hay dispatch. Chưa có source nối SOS location vào production route search; ở demo chỉ dùng nodeđích tọa độTEST cho SOSrequest và phản hồi marker.

Transport fixture chỉ nhận syntheticcondition, coords trong vùngtest, adults1..10 và faketoken không phảiJWT. Success201 trả ACTIVE synthetic, noDBwrites/forwarding/push/email/SMS. Fault403, timeout và offline được test. Epoch test-only chặn late response sau logout; authtoken chỉ memory, restart yêu cầu đăng nhập lại, riêngcổnglocal được lưu vào prefs task.

## D. Actual physical evidence và regression

Device mới xác minh: {p['model']}, API33, physicalUSB/authorized, dynamicserialREDACTED. {L('evidence/environment-before.json')}. **16 unique runtime cases**,0fail/errors/skips, mọiADBinstrumentationexit0. Focused/development runs không cộng vào16.

| Actual JUnit method | Result |
|---|---|
{rows}

{L('evidence/physical-final/TEST-demo.xml')}, {L('evidence/physical-final/results.json')}, {L('evidence/physical-final-driver.log')}; mỗi case có ownPIDlogcat và command. Actual publicrouter output nằm trong instrumentation/status và tagP5E09_UCS.

| JVM/build mới | Actual counts | Exit |
|---|---|---|
| SOS targeted |26/7suites,0fail/error/skip|0|
| Routing fresh --rerun-tasks |462/13suites,0fail/error/skip|0|
| Full JVM |545/32suites,0fail/error/skip|0|
| Main debugAPK + release manifest |BUILD SUCCESSFUL|0|
| Isolated demo + test APK |BUILD SUCCESSFUL|0|

{L('evidence/jvm-build/results.json')}; exactcommands nằm trong logs/JSON. MainAPK SHA `{j['apk']['sha256']}`, không cài phone. DemoAPK SHA `{p['apk_sha256']}`; APK đã được giữ tại {L('artifacts/p5e09-demo.apk')}, {L('artifacts/SHA256SUMS')}.

Initial build thiếu standaloneBuildConfig đã bị chặn trước install; chỉ bật generation trong module mới. FocusedUI có lỗi cuộn, đã dùng touchscroll trong ownview và layoutscenario2rows; không đổi assertions. Một reverseport attempt thất bại, khônginstall, cleanupownedserver; runner hiện ghi bounded3freshport attempts, không xóa mapping người dùng. Hazardoverlay mismatch đã ghi ở trên. Các before/focusedXML/log còn nguyên dưới evidence, không được gọi là finalPASS.

## E. Security / phone / source preservation

- New package `com.example.bai1.p5e09.demo`, testpackage riêng; absent trước install, install-t không-r; distinct non-systemUIDs. Launcher exported có chủ đích để mởdemo, không dùng componentproduction.
- BothDEX không Firebase/GMS/Maps/credentialSDK/productionDI/locationprovider. No GPS/camera/mic/SMS/notificationpermission/provider/receiver/service metadata startup. 39exactoriginalfiles; {L('evidence/physical-final/gate.json')}, {L('evidence/physical-final/source-hashes.json')}, target/test manifests và compiledpolicy.
- Client exact127.0.0.1:ownedport/pathallowlist, noProxy/redirect/retry, boundedbody4096/response65536/timeouts; fixture handshake xác nhận marker trướclogin. No productionURL/key/credentials fallback; scan productionconfig0matches.
- OwnPID/windowlogs only, no FATAL/ANR/unexpectedSDKinit/credentialleak; {L('evidence/log-review.json')}. No screenshots/privatephone data collection.
- Sau instrumentation: gỡ đúng2newpackages, xóa đúngownedreverse, shutdownfixture, phonevẫnauthorized; {L('evidence/device-and-cleanup.json')}. **Không giữ app trên phone tự động**. APK/testAPK/runbook giữ trênlaptop. Người dùng có thể tựchọn `demo_session.py --install --keep-demo` để giữ app trước giờ trình bày; không tựgỡ/càiđè package cósẵn.
- ExistingAndroidsource/config/tests/docs giữ nguyên SHA+mtime; toàn bộ thay đổi E09 là filesmới trong tools/demo-harness và docs/demo. Backend{len(bb)}files,20graphartifacts giữ nguyên SHA+mtime,0new/removed; {L('evidence/preservation.json')}. gitdiffcheck0bothrepos, khôngcommit/push/deploy. {L('evidence/changed-files.json')}, {L('evidence/evidence-index.json')}.

## F. Trình bày, runbook và blockers

- {L('DEMO_TEACHER_SCRIPT_VI.md','Kịch bản tiếng Việt 3–5 phút')}.
- {L('DEMO_RUNBOOK_USB.md','Runbook: USB, fixture, reinstall, rehearsal, recovery')}.
- Phiên cài/mở/dừng theo runbook đã được smoke-test riêng: {L("evidence/presentation-session-smoke.json")}, exit0; không cộng vào16JUnitcases.
- Một lệnh sau khi kiểm SHA/USB: `python3 tools/demo-harness/demo_session.py --install --keep-demo`. Đây là lựa chọn rõ của người chạy để giữ bản demo; assistant chỉ nghiệm thu và cleanup.
- Fallbackkhôngnetwork: **Chẩn đoán offline**; khônglogin/token/GPS, SOSdisable; graph/publicgate vẫn chạy. FallbackUIkhôngkhảdụng: XMLJVM, actual-on-phone-output, logtag và reporttables; không giảvờcomputedroute đãxuất hiện.

**Blockers còn lại:** productionqualificationauthority/F06; trustedhazard/freshness; public search entry hiện không gọi kernel; chưa có authorizedcandidatepath để vẽ; GoogleMap/Location isolation; productionSOS/rescueUI; missionworkflow/dispatch/realtime; sản phẩm chưaFullE2E. Không mở safety gate, không giả qualification/hazardCLEAR và không chuyển diagnostic thành navigation instruction. Không thực hiện taskmới ngoài P5-E09-DEMO.
'''
for old,new in [('kịch bản3–5phút','kịch bản 3–5 phút'),('16 unique runtime cases','16 unique runtime cases'),('Không có tuyến đã tính','Không có tuyến đã tính')]:report=report.replace(old,new)
(D/'DEMO_MAP_ALGORITHM_RESCUE_REPORT.md').write_text(report)
# Final inventory after documentation is complete; recursive inventories omit self hash.
now=snap(ROOT);new=sorted(set(now)-set(base));special={'docs/demo/evidence/changed-files.json','docs/demo/evidence/evidence-index.json'}
dump('changed-files.json',{'baseline_files':len(base),'existing_changed':{},'new_files':[{'path':k,'sha256':now[k]['sha256'],'mtime_ns':now[k]['mtime_ns']} for k in new if k not in special],'removed':[],'recursive_inventory_hashes_omitted':sorted(special)})
for file in [D/'DEMO_MAP_ALGORITHM_RESCUE_REPORT.md',D/'DEMO_TEACHER_SCRIPT_VI.md']:
 for link in re.findall(r'\]\(([^)]+)\)',file.read_text()):
  if not link.startswith(('http:','https:','#')):assert (file.parent/link).exists() or (file.parent/link).resolve()==EV/'evidence-index.json',link
for file in [*EV.rglob('*'),D/'DEMO_MAP_ALGORITHM_RESCUE_REPORT.md',D/'DEMO_RUNBOOK_USB.md',D/'DEMO_TEACHER_SCRIPT_VI.md']:
 if file.is_file():assert serial not in file.read_text(errors='replace'),str(file)
dump('evidence-index.json',{str(f.relative_to(EV)):{'sha256':sha(f),'bytes':f.stat().st_size} for f in EV.rglob('*') if f.is_file() and f.name!='evidence-index.json'})
print(json.dumps({'status':'CONDITIONAL','phone_tests':16,'routing_jvm':462,'sos_jvm':26,'full_jvm':545,'kernel_in_phone':'BLOCKED; no bypass','demo_apk':str(D/'artifacts/p5e09-demo.apk'),'backend_preserved':len(bb),'graph_artifacts_preserved':20,'report':str(D/'DEMO_MAP_ALGORITHM_RESCUE_REPORT.md')},indent=2))
