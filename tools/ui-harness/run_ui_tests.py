#!/usr/bin/env python3
"""Build/audit first. Run actual production Composable subset in an owned package only."""
import sys
sys.dont_write_bytecode=True
import shutil,argparse,base64,hashlib,json,os,re,subprocess,threading,time,zipfile
from pathlib import Path
from http.server import ThreadingHTTPServer
import xml.etree.ElementTree as ET
from host_fixture import Fixture,GZIP

HERE=Path(__file__).resolve().parent
REPO=HERE.parents[1]
ADB='/home/vantri/Android/Sdk/platform-tools/adb'
AAPT='/home/vantri/Android/Sdk/build-tools/36.0.0/aapt2'
PKG='com.example.bai1.p5e02.uiisolated'
ANDROID='{http://schemas.android.com/apk/res/android}'

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--physical-usb',action='store_true');parser.add_argument('--evidence-dir',type=Path,required=True)
    parser.add_argument('--tests',help='Optional comma-separated method names; every selected case still emits real JUnit XML')
    args=parser.parse_args();out=args.evidence_dir.resolve();assert not out.exists(), 'Refuse existing evidence directory';out.mkdir(parents=True)
    env=dict(os.environ,JAVA_HOME='/usr/lib/jvm/java-25-openjdk-amd64',ANDROID_HOME='/home/vantri/Android/Sdk')
    env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
    assert shutil.disk_usage('/').free >= 2_000_000_000, 'DISK_SPACE_BLOCKED'
    serial='emulator-5554'
    if args.physical_usb:
        inventory=subprocess.check_output([ADB,'devices','-l'],text=True).splitlines()[1:]
        phones=[line.split()[0] for line in inventory if 'usb:' in line and not line.startswith('emulator-')]
        assert len(phones)==1,'Exactly one USB phone required; clarify device selection otherwise'
        serial=phones[0]
    def adb(*cmd):return subprocess.run([ADB,'-s',serial,*cmd],capture_output=True,text=True)
    assert adb('get-state').stdout.strip()=='device'
    api=int(adb('shell','getprop','ro.build.version.sdk').stdout.strip())
    assert (30 <= api < 37) if args.physical_usb else api==37, 'Physical API37 permission prompts require user-led confirmation'
    assert adb('shell','getprop','ro.kernel.qemu').stdout.strip() != '1' if args.physical_usb else True
    assert adb('shell','getprop','sys.boot_completed').stdout.strip()=='1'
    for pkg in [PKG,PKG+'.test']:assert 'package:' not in adb('shell','pm','path',pkg).stdout,'Refuse existing namespace'
    cmd=[str(REPO/'gradlew'),'-p',str(HERE),'--offline','--no-daemon','assembleDebug','assembleDebugAndroidTest']
    with (out/'build.log').open('w') as log:code=subprocess.run(cmd,cwd=REPO,env=env,stdout=log,stderr=subprocess.STDOUT).returncode
    assert code==0,'Build failed before install'
    apk=next((HERE/'build/outputs/apk/debug').glob('*.apk'));test_apk=next((HERE/'build/outputs/apk/androidTest/debug').glob('*.apk'))
    manifest=ET.parse(HERE/'build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml').getroot();app=manifest.find('application')
    assert manifest.get('package')==PKG and app.get(ANDROID+'name')=='com.example.bai1.uiisolated.GraphFixtureApplication'
    assert app.get(ANDROID+'debuggable')=='true' and app.get(ANDROID+'allowBackup')=='false'
    assert app.get(ANDROID+'usesCleartextTraffic')=='false' and app.get(ANDROID+'networkSecurityConfig')=='@xml/routing_network_security_config'
    for tag in ['provider','service','receiver','meta-data']:assert app.find(tag) is None,'Unexpected startup component: '+tag
    assert [a.get(ANDROID+'name') for a in app.findall('activity')]==['com.example.bai1.MainActivity','com.example.bai1.uiisolated.IsolatedUiActivity']
    assert all(a.get(ANDROID+'exported')=='false' and a.find('intent-filter') is None for a in app.findall('activity'))
    test_manifest=ET.parse(HERE/'build/intermediates/packaged_manifests/debugAndroidTest/processDebugAndroidTestManifest/AndroidManifest.xml').getroot()
    assert test_manifest.get('package')==PKG+'.test'
    assert test_manifest.find('instrumentation').get(ANDROID+'targetPackage')==PKG
    test_app=test_manifest.find('application');assert test_app.get(ANDROID+'name') is None
    assert all(test_app.find(tag) is None for tag in ['provider','service','receiver','meta-data'])
    assert {a.get(ANDROID+'name') for a in test_app.findall('activity')}=={'androidx.test.core.app.InstrumentationActivityInvoker$BootstrapActivity','androidx.test.core.app.InstrumentationActivityInvoker$EmptyActivity','androidx.test.core.app.InstrumentationActivityInvoker$EmptyFloatingActivity'}
    perms={p.get(ANDROID+'name') for p in manifest.findall('uses-permission')}
    assert perms <= {'android.permission.INTERNET','android.permission.ACCESS_LOCAL_NETWORK',PKG+'.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
    with zipfile.ZipFile(apk) as z:
        dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
        for prefix in [b'Lcom/google/firebase/',b'Lcom/google/android/gms/',b'Landroidx/credentials/',
            b'Lcom/example/bai1/presentation/profile/ProductionProfileScreenKt;',b'Lcom/example/bai1/presentation/auth/ProductionRoleSelectionScreenKt;',b'Lcom/example/bai1/DisasterApplication;',b'Lcom/example/bai1/presentation/navigation/ProductionAppNavGraphKt;',b'Lcom/example/bai1/presentation/navigation/ProductionAppNavScreens;',b'Lcom/example/bai1/sos/SosAppDependencies;',b'Lcom/example/bai1/rescue/RescueMissionAppDependencies;',b'Lcom/example/bai1/rescue/routing/RoutingAppDependencies;',b'Lcom/example/bai1/presentation/auth/ProductionLoginScreenKt;',b'Lcom/example/bai1/auth/firebase/AndroidFirebaseAuthSession;',b'Lcom/example/bai1/auth/AuthAppDependencies;',
            b'Lcom/example/bai1/sos/network/OkHttpSosApiClient;']:
            assert prefix not in dex,'Forbidden cloud/business initialization in APK'
        for prefix in [b'Lcom/example/bai1/presentation/profile/ProfileScreenKt;',b'Lcom/example/bai1/presentation/settings/SettingsScreenKt;',b'Lcom/example/bai1/presentation/auth/RoleSelectionScreenKt;',b'Lcom/example/bai1/auth/RoleSelectionViewModel;',b'Lcom/example/bai1/presentation/home/HomeScreenKt;',b'Lcom/example/bai1/MainActivity;',b'Lcom/example/bai1/MainAppHost;',b'Lcom/example/bai1/uiisolated/GraphFixtureApplication;',b'Lcom/example/bai1/presentation/navigation/AppNavGraphKt;',b'Lcom/example/bai1/presentation/auth/RegisterScreenKt;',b'Lcom/example/bai1/presentation/auth/OnboardingScreenKt;',
            b'Lcom/example/bai1/presentation/auth/LoginScreenKt;',b'Lcom/example/bai1/auth/AuthRepository;',b'Lcom/example/bai1/auth/AuthViewModel;',b'Lcom/example/bai1/auth/session/SharedPreferencesTokenStorage;',b'Lcom/example/bai1/presentation/settings/AboutAppScreenKt;',b'Lcom/example/bai1/auth/network/OkHttpAuthApiClient;']:
            assert prefix in dex,'Actual production class missing'
    with zipfile.ZipFile(test_apk) as z:
        test_dex=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
        for prefix in [b'Lcom/google/firebase/',b'Lcom/google/android/gms/',b'Landroidx/credentials/',b'Lcom/example/bai1/DisasterApplication;',b'Lcom/example/bai1/auth/AuthAppDependencies;',b'Lcom/example/bai1/sos/SosAppDependencies;']:
            assert prefix not in test_dex,'Forbidden cloud initialization in instrumentation APK'
    for screen in ['warning/WarningListScreen','warning/StormDetailScreen','warning/FloodDetailScreen','notification/NotificationScreen']:
        assert ('Lcom/example/bai1/presentation/'+screen+'Kt;').encode() in dex
    warning_factory=(HERE/'src/main/java/com/example/bai1/uiisolated/ActualWarningFixtureScreens.kt').read_text()
    for binding in ['warning.WarningListScreen(onNavigateBack = onNavigateBack, onNavigateToDetail = onNavigateToDetail, warnings = app.warningItems)', 'warning.StormDetailScreen(onNavigateBack)', 'warning.FloodDetailScreen(onNavigateBack, onViewFloodMap)', 'notification.NotificationScreen(onNavigateBack, onBottomNavNavigate)']:
        assert binding in warning_factory
    for forbidden in ['AuthAppDependencies','FirebaseAuth','CredentialManager','https://','NotificationManager','FusedLocationProvider']:
        assert forbidden not in warning_factory
    assert 'if (warningScreens) ActualWarningFixtureScreens(this)' in (HERE/'src/main/java/com/example/bai1/uiisolated/GraphFixtureApplication.kt').read_text()
    assert 'SYNTHETIC_TEST_DATA' in (HERE/'src/main/java/com/example/bai1/uiisolated/GraphFixtureApplication.kt').read_text()
    copies={}
    for source in (HERE/'build/generated/production-ui').rglob('*.kt'):
        rel=source.relative_to(HERE/'build/generated/production-ui');original=REPO/'app/src/main/java'/rel
        assert original.read_bytes()==source.read_bytes(),'Production UI copy was transformed'
        copies[str(rel)]=hashlib.sha256(source.read_bytes()).hexdigest()
    assert len(copies)==39
    # Explicit startup/source/origin audit supplements manifest, DEX and byte equality.
    activity=(REPO/'app/src/main/java/com/example/bai1/MainActivity.kt').read_text()
    assert 'val host = application as MainAppHost' in activity and 'host.initialize(this)' in activity
    assert 'host.AppGraph(navController = navController)' in activity
    for forbidden in ['AuthAppDependencies','SosAppDependencies','RescueMissionAppDependencies','RoutingAppDependencies','Firebase','CredentialManager']:
        assert forbidden not in activity
    fixture=(HERE/'src/main/java/com/example/bai1/uiisolated/GraphFixtureApplication.kt').read_text()
    assert 'base = "http://127.0.0.1:$port"' in fixture and 'require(port in 1024..65535)' in fixture
    for required in ['Proxy.NO_PROXY','followRedirects(false)','followSslRedirects(false)','retryOnConnectionFailure(false)', 'u.scheme == "http" && u.host == "127.0.0.1" && u.port == port','X-Fixture-Expire-Access','AuthRepository(api, counted, cloud, routing::invalidateSession)']:
        assert required in fixture
    for forbidden in ['10.0.2.2','BuildConfig','AuthAppDependencies','FirebaseAuth.getInstance','https://','localhost:3000']:
        assert forbidden not in fixture
    account_factory=(HERE/'src/main/java/com/example/bai1/uiisolated/ActualAccountFixtureScreens.kt').read_text()
    for required in ['com.example.bai1.presentation.auth.RoleSelectionScreen(onContinue, model)','RoleSelectionViewModel.factory(app.repository)','com.example.bai1.presentation.profile.ProfileScreen(','com.example.bai1.presentation.settings.SettingsScreen(','com.example.bai1.presentation.home.HomeScreen(']:assert required in account_factory
    for forbidden in ['AuthAppDependencies','FirebaseAuth','CredentialManager','https://']:assert forbidden not in account_factory
    defaults=['app/src/main/java/com/example/bai1/DisasterApplication.kt','app/src/main/java/com/example/bai1/presentation/navigation/ProductionAppNavGraph.kt','app/src/main/java/com/example/bai1/presentation/navigation/ProductionAppNavScreens.kt']
    assert all(not (HERE/'build/generated/production-ui'/Path(v).relative_to('app/src/main/java')).exists() for v in defaults)
    # Reject production configuration values without writing any secret value to evidence.
    production_values=[]
    google_file=REPO/'app/google-services.json'
    if google_file.exists():
        google=json.loads(google_file.read_text())
        def strings(obj):
            if isinstance(obj,dict):
                for key,value in obj.items():
                    if key in {'current_key','client_id','project_id','firebase_url','storage_bucket','mobilesdk_app_id'} and isinstance(value,str):production_values.append(value)
                    else:strings(value)
            elif isinstance(obj,list):
                for item in obj:strings(item)
        strings(google)
    assert not any(value.encode() in dex for value in production_values if len(value)>8), 'Production configuration packaged'
    assert b'firebasedatabase.app' not in dex and b'firebaseio.com' not in dex
    (out/'startup-origin-audit.json').write_text(json.dumps({'status':'PASS','actual_activity':True,'actual_graph':True,'source_copies':len(copies),'production_configuration_values_checked':len(production_values),'production_configuration_matches':0,'standalone_application':'GraphFixtureApplication','cloud_defaults_excluded':defaults,'application_source_sha256':hashlib.sha256(fixture.encode()).hexdigest()},indent=2))
    lifecycle_source=REPO/'app/src/test/java/com/example/bai1/auth/AuthLifecycleSafetyTest.kt'
    lifecycle_copy=HERE/'build/generated/lifecycle-tests/com/example/bai1/auth/AuthLifecycleSafetyTest.kt'
    assert lifecycle_source.read_bytes()==lifecycle_copy.read_bytes(), 'Lifecycle test copy transformed'
    (out/'lifecycle-test-copy-hash.json').write_text(json.dumps({'sha256':hashlib.sha256(lifecycle_copy.read_bytes()).hexdigest()}))
    policy=subprocess.check_output([AAPT,'dump','xmltree',str(apk),'--file','res/xml/routing_network_security_config.xml'],text=True)
    assert policy.count('cleartextTrafficPermitted=true')==1
    for host in ['10.0.2.2','127.0.0.1','localhost']:assert "T: '"+host+"'" in policy
    (out/'compiled-policy.txt').write_text(policy)
    (out/'source-copy-hashes.json').write_text(json.dumps(copies,indent=2))
    result={'isolation_gate':'PASS','build_command':cmd,'build_exit_code':code,'package':PKG,'preexisting_owned_packages':[],'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'test_apk_sha256':hashlib.sha256(test_apk.read_bytes()).hexdigest(),'cases':[]}
    print('PREINSTALL ISOLATION PASS: actual MainActivity/AppNavGraph, no cloud/providers',flush=True)
    names=re.findall(r'@Test fun (\w+)\(', (HERE/'src/androidTest/java/com/example/bai1/fixture/ProductionSubsetUiTest.kt').read_text())
    if args.physical_usb:
        excluded={'realPermissionGrantEnablesLocalFixtureAndSurvivesActivityRestart','grantedPermissionSurvivesProcessRestartWithoutRestoringFixtureSession','realPermissionDenialRendersProductionErrorStateWithoutCrash','absentPermissionBlocksActualFixtureAndRestartKeepsUnauthenticated','revokedPermissionBlocksConnectionAndRestartsDenied','genuineProductionAboutScreenshot'}
        names=[n for n in names if n not in excluded]
    else:
        names=[n for n in names if not n.startswith(('usb','auth','lifecycle'))]
    classes={n:'com.example.bai1.fixture.ProductionSubsetUiTest' for n in names}
    if args.physical_usb:
        for n in re.findall(r'@Test fun (\w+)\(',lifecycle_source.read_text()):
            assert n not in classes
            classes[n]='com.example.bai1.auth.AuthLifecycleSafetyTest';names.append(n)
    if args.physical_usb:
        for n in re.findall(r'@Test fun (\w+)\(', (HERE/'src/androidTest/java/com/example/bai1/fixture/ProductionGraphUiTest.kt').read_text()):
            assert n not in classes
            classes[n]='com.example.bai1.fixture.ProductionGraphUiTest';names.append(n)
    if args.physical_usb:
        for n in re.findall(r'@Test fun (\w+)\(', (HERE/'src/androidTest/java/com/example/bai1/fixture/ProductionAccountUiTest.kt').read_text()):
            assert n not in classes
            classes[n]='com.example.bai1.fixture.ProductionAccountUiTest';names.append(n)
    if args.physical_usb:
        for n in re.findall(r'@Test fun (\w+)\(', (HERE/'src/androidTest/java/com/example/bai1/fixture/ProductionWarningUiTest.kt').read_text()):
            assert n not in classes
            classes[n]='com.example.bai1.fixture.ProductionWarningUiTest';names.append(n)
    if args.tests:
        chosen=args.tests.split(',');assert set(chosen)<=set(names);names=chosen
    server=ThreadingHTTPServer(('127.0.0.1',0),Fixture);server.events=[];server.loading_gate=threading.Event();server.barriers={};server.barrier_lock=threading.Lock()
    server.fixture_host='127.0.0.1' if args.physical_usb else '10.0.2.2'
    thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start();installed=[];owned_reverse=False
    remote='tcp:'+str(server.server_port)
    result.update(device_type='PHYSICAL_USB' if args.physical_usb else 'EMULATOR',device_serial='REDACTED',android_api=api)
    suite=ET.Element('testsuite',name='P5E03PhysicalUsbProductionSubsetUi' if args.physical_usb else 'P5E02ProductionSubsetUi',tests='0',failures='0',errors='0',skipped='0')
    try:
        if args.physical_usb:
            mappings=adb('reverse','--list');assert mappings.returncode==0
            assert not any(remote in line.split()[1:2] for line in mappings.stdout.splitlines()), 'Refuse reverse collision'
            assert adb('reverse',remote,remote).returncode==0;owned_reverse=True
        for pkg,file in [(PKG,apk),(PKG+'.test',test_apk)]:
            p=adb('install','-t',str(file));assert p.returncode==0,'Owned APK install failed';installed.append(pkg)
        uid_proof={}
        for pkg in installed:
            info=adb('shell','dumpsys','package',pkg);assert info.returncode==0
            uid=int(re.search(r'userId=(\d+)',info.stdout).group(1));assert uid>=10000
            uid_proof[pkg]={'uid':uid,'preexisting':False}
        assert len({v['uid'] for v in uid_proof.values()})==2
        (out/'owned-package-uid-proof.json').write_text(json.dumps(uid_proof,indent=2))
        for name in names:
            # Only the newly installed, previously absent package. Never touch production packages/data.
            assert adb('shell','am','force-stop',PKG).returncode==0
            # Force-stop returns before the old owned process/window necessarily disappears.
            # Keep historical test bodies untouched; await only our package teardown.
            teardown_deadline=time.monotonic()+5
            while adb('shell','pidof',PKG).stdout.strip():
                assert time.monotonic()<teardown_deadline, 'Owned process did not stop'
                time.sleep(.1)
            if args.physical_usb:time.sleep(.75)
            if not args.physical_usb and name.startswith('grantedPermissionSurvivesProcessRestart'):
                assert result['cases'] and result['cases'][-1]['name']=='realPermissionGrantEnablesLocalFixtureAndSurvivesActivityRestart'
                assert result['cases'][-1]['failures']==0
                # Preserve OS permission across the actual force-stop/new-process boundary.
            elif not args.physical_usb:
                if name.startswith('revoked'):
                    assert adb('shell','pm','grant',PKG,'android.permission.ACCESS_LOCAL_NETWORK').returncode==0
                assert adb('shell','pm','revoke',PKG,'android.permission.ACCESS_LOCAL_NETWORK').returncode==0
                assert adb('shell','pm','clear-permission-flags',PKG,'android.permission.ACCESS_LOCAL_NETWORK','user-set','user-fixed').returncode==0
            if name in {'usbMissingReverseFailsClosed','authLoginMissingReverseFailsClosed','graphMissingReverseKeepsLogin'}:
                assert owned_reverse and adb('reverse','--remove',remote).returncode==0;owned_reverse=False
            elif args.physical_usb and not owned_reverse:
                assert adb('reverse',remote,remote).returncode==0;owned_reverse=True
            if name in {'authSavedSessionSurvivesProcessRestart','lifecycleRestartAfterExpiryRemainsUnauthenticated','graphSavedSessionSurvivesProcessRestartThroughActualSplashGraph','graphRestartAfterExpiryNeverRestoresProtectedRoute','accountProfileAfterProcessRestartUsesIsolatedRestore','accountSettingsAfterProcessRestartUsesIsolatedRestore','accountRoleAfterProcessRestartUsesExistingHomeRestoreSemantics','accountRestartAfterExpiryCannotShowProfileOrSettings','warningAfterProcessRestartUsesExistingHomeRestore','notificationAfterExpiredProcessRestartCannotRestoreRows'}:
                expected={'authSavedSessionSurvivesProcessRestart':'authPrepareSavedSessionForProcessRestart','lifecycleRestartAfterExpiryRemainsUnauthenticated':'lifecyclePrepareExpiredSessionForRestart','graphSavedSessionSurvivesProcessRestartThroughActualSplashGraph':'graphPrepareSavedSessionForProcessRestart','graphRestartAfterExpiryNeverRestoresProtectedRoute':'graphPrepareExpiredSessionForProcessRestart','accountProfileAfterProcessRestartUsesIsolatedRestore':'accountPrepareProfileSessionForProcessRestart','accountSettingsAfterProcessRestartUsesIsolatedRestore':'accountPrepareSettingsSessionForProcessRestart','accountRoleAfterProcessRestartUsesExistingHomeRestoreSemantics':'accountPrepareRoleSessionForProcessRestart','accountRestartAfterExpiryCannotShowProfileOrSettings':'accountPrepareExpiredSessionForProcessRestart','warningAfterProcessRestartUsesExistingHomeRestore':'warningPrepareSessionForProcessRestart','notificationAfterExpiredProcessRestartCannotRestoreRows':'notificationPrepareExpiredSessionForProcessRestart'}[name]
                assert result['cases'][-1]['name']==expected and result['cases'][-1]['failures']==0
            with server.barrier_lock:server.barriers.clear()
            server.loading_gate.clear()
            folder=out/name;folder.mkdir(exist_ok=True);since=adb('shell','date "+%m-%d %H:%M:%S.000"').stdout.strip();assert len(since)==18
            command=[ADB,'-s',serial,'shell','am','instrument','-w','-r','-e','class',classes[name]+'#'+name,
                '-e','listener','com.example.bai1.fixture.DeviceXmlListener','-e','hostFixturePort',str(server.server_port),'-e','physicalUsb',str(args.physical_usb).lower(),'-e','preserveAuthSession',str(name in {'authSavedSessionSurvivesProcessRestart','lifecycleRestartAfterExpiryRemainsUnauthenticated','graphSavedSessionSurvivesProcessRestartThroughActualSplashGraph','graphRestartAfterExpiryNeverRestoresProtectedRoute','accountProfileAfterProcessRestartUsesIsolatedRestore','accountSettingsAfterProcessRestartUsesIsolatedRestore','accountRoleAfterProcessRestartUsesExistingHomeRestoreSemantics','accountRestartAfterExpiryCannotShowProfileOrSettings','warningAfterProcessRestartUsesExistingHomeRestore','notificationAfterExpiredProcessRestartCannotRestoreRows'}).lower(),'-e','expectedGraphSha256',hashlib.sha256(GZIP).hexdigest(),
                PKG+'.test/com.example.bai1.fixture.IsolatedInstrumentationRunner']
            started=time.monotonic();logcat=None;pid=None;start_events=len(server.events)
            with (folder/'instrumentation.txt').open('w') as stdout,(folder/'owned-process-logcat.txt').open('w') as logs:
                proc=subprocess.Popen(command,stdout=stdout,stderr=subprocess.STDOUT)
                deadline=time.monotonic()+90
                try:
                    while proc.poll() is None:
                        if logcat is None:
                            p=adb('shell','pidof',PKG).stdout.strip()
                            if p.isdigit():pid=p;logcat=subprocess.Popen([ADB,'-s',serial,'logcat','--pid='+pid,'-v','threadtime','-T',since],stdout=logs,stderr=subprocess.STDOUT)
                        if time.monotonic()>deadline:
                            adb('shell','am','force-stop',PKG);proc.terminate();raise RuntimeError('Owned UI instrumentation deadline exceeded')
                        time.sleep(.2)
                finally:
                    if proc.poll() is None:
                        adb('shell','am','force-stop',PKG);proc.terminate();proc.wait(timeout=10)
                    if logcat:logcat.terminate();logcat.wait(timeout=10)
            assert all(e['destination_host']==server.fixture_host+':'+str(server.server_port) for e in server.events[start_events:]), 'Unexpected request destination'
            text=(folder/'instrumentation.txt').read_text();prefix='INSTRUMENTATION_STATUS: p5e01XmlBase64='
            encoded=next((line[len(prefix):] for line in text.splitlines() if line.startswith(prefix)),None)
            assert encoded is not None,'No JUnit runtime export'
            xml=base64.b64decode(encoded);(folder/'TEST-ui.xml').write_bytes(xml);t=ET.fromstring(xml)
            assert t.get('junitRunCount')=='1' and t.get('tests')=='1'
            for case in t.findall('testcase'):suite.append(case)
            if not args.physical_usb and name.startswith('grantedPermissionSurvivesProcessRestart'):
                assert pid is not None and pid != result['cases'][-1]['pid'], 'Actual restarted process must have a new PID'
            if name in {'authSavedSessionSurvivesProcessRestart','lifecycleRestartAfterExpiryRemainsUnauthenticated','graphSavedSessionSurvivesProcessRestartThroughActualSplashGraph','graphRestartAfterExpiryNeverRestoresProtectedRoute','accountProfileAfterProcessRestartUsesIsolatedRestore','accountSettingsAfterProcessRestartUsesIsolatedRestore','accountRoleAfterProcessRestartUsesExistingHomeRestoreSemantics','accountRestartAfterExpiryCannotShowProfileOrSettings','warningAfterProcessRestartUsesExistingHomeRestore','notificationAfterExpiredProcessRestartCannotRestoreRows'}:assert pid and pid!=result['cases'][-1]['pid'], 'Restart must use a new process'
            result['cases'].append({'name':name,'test_class':classes[name],'command':[x if x!=serial else 'REDACTED' for x in command],'duration_seconds':round(time.monotonic()-started,3),'adb_exit_code':proc.returncode,'pid':pid,'since':since,
                'tests':int(t.get('tests')),'failures':int(t.get('failures')),'errors':int(t.get('errors')),'skipped':int(t.get('skipped')),
                'fixture_events':server.events[start_events:],'metrics':re.findall(r'INSTRUMENTATION_STATUS: p5e03Metric=([^\r\n]+)',text)})
            if name=='genuineProductionAboutScreenshot' and t.get('failures')=='0':
                shot=subprocess.run([ADB,'-s',serial,'exec-out','run-as',PKG,'cat','files/production-about.png'],capture_output=True)
                assert shot.returncode==0 and shot.stdout.startswith(b'\x89PNG');(out/'production-about.png').write_bytes(shot.stdout)
            print(name,'tests',t.get('tests'),'failures',t.get('failures'),flush=True)
            (out/'results.json').write_text(json.dumps(result,indent=2))
        suite.set('tests',str(len(suite)))
        for key in ['failures','errors','skipped']:suite.set(key,str(sum(c[key] for c in result['cases'])))
        ET.ElementTree(suite).write(out/'TEST-production-ui.xml',encoding='utf-8',xml_declaration=True)
    finally:
        cleanup_errors=[]
        if owned_reverse and adb('reverse','--remove',remote).returncode!=0:cleanup_errors.append('owned reverse removal failed')
        with server.barrier_lock:
            for gate in server.barriers.values():gate["release"].set()
        server.shutdown();server.server_close();thread.join(timeout=5)
        for pkg in reversed(installed):
            if adb('uninstall',pkg).returncode!=0:cleanup_errors.append('owned package removal failed: '+pkg)
        result['cleanup_errors']=cleanup_errors
        (out/'results.json').write_text(json.dumps(result,indent=2))
        assert not cleanup_errors, 'Task cleanup requires attention'
    result['owned_reverse_removed']=remote not in adb('reverse','--list').stdout if args.physical_usb else True
    result['owned_packages_remaining']=[pkg for pkg in [PKG,PKG+'.test'] if 'package:' in adb('shell','pm','path',pkg).stdout]
    result['device_assertions']='PASS' if len(suite)==len(names) and all(suite.get(k)=='0' for k in ['failures','errors','skipped']) and all(c['adb_exit_code']==0 for c in result['cases']) else 'FAIL'
    (out/'results.json').write_text(json.dumps(result,indent=2))
    assert not result['owned_packages_remaining'] and result['device_assertions']=='PASS','Real UI assertions failed'
    print('DEVICE UI PASS: '+str(len(suite))+' real JUnit cases',flush=True)

if __name__=='__main__':main()
