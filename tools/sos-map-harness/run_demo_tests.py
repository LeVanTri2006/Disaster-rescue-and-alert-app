#!/usr/bin/env python3
import sys
sys.dont_write_bytecode=True
import argparse,base64,hashlib,json,os,re,subprocess,threading,time,zipfile,shutil
from pathlib import Path
import xml.etree.ElementTree as E
from fixture import server
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
ADB='/home/vantri/Android/Sdk/platform-tools/adb';AAPT='/home/vantri/Android/Sdk/build-tools/36.0.0/aapt2';PKG='com.example.bai1.p5e10a.demo';A='{http://schemas.android.com/apk/res/android}'
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--evidence-dir',type=Path,required=True);ap.add_argument('--tests');args=ap.parse_args();out=args.evidence_dir.resolve();assert not out.exists();out.mkdir(parents=True)
 assert shutil.disk_usage('/').free>=2_000_000_000
 lines=subprocess.check_output([ADB,'devices','-l'],text=True).splitlines()[1:];phones=[l.split()[0] for l in lines if 'usb:' in l and not l.startswith('emulator')];assert len(phones)==1;serial=phones[0]
 def adb(*a):return subprocess.run([ADB,'-s',serial,*a],capture_output=True,text=True)
 assert adb('get-state').stdout.strip()=='device' and adb('shell','getprop','sys.boot_completed').stdout.strip()=='1'
 assert adb('shell','getprop','ro.kernel.qemu').stdout.strip()!='1';assert adb('shell','getprop','ro.build.version.sdk').stdout.strip()=='33'
 for p in [PKG,PKG+'.test']:assert 'package:' not in adb('shell','pm','path',p).stdout,'Refuse existing package'
 env=dict(os.environ,JAVA_HOME='/usr/lib/jvm/java-25-openjdk-amd64',ANDROID_HOME='/home/vantri/Android/Sdk');env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
 cmd=[str(ROOT/'gradlew'),'-p',str(HERE),'--offline','--no-daemon','assembleDebug','assembleDebugAndroidTest']
 with (out/'build.log').open('w') as f:code=subprocess.run(cmd,cwd=ROOT,env=env,stdout=f,stderr=subprocess.STDOUT).returncode
 assert code==0,'Build rejected before install'
 apk=next((HERE/'build/outputs/apk/debug').glob('*.apk'));test=next((HERE/'build/outputs/apk/androidTest/debug').glob('*.apk'))
 m=E.parse(HERE/'build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml').getroot();app=m.find('application');assert m.get('package')==PKG and m.get(A+'sharedUserId') is None
 assert app.get(A+'allowBackup')=='false' and app.get(A+'name') is None and app.get(A+'usesCleartextTraffic')=='false'
 assert {p.get(A+'name') for p in m.findall('uses-permission')}<={'android.permission.INTERNET',PKG+'.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
 assert all(app.find(t) is None for t in ['provider','receiver','service','meta-data'])
 acts=app.findall('activity');assert len(acts)==1 and acts[0].get(A+'name')=='com.example.bai1.demo.DemoActivity' and acts[0].get(A+'exported')=='true';shutil.copy2(HERE/'build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml',out/'target-manifest.xml')
 tm=HERE/'build/intermediates/packaged_manifests/debugAndroidTest/processDebugAndroidTestManifest/AndroidManifest.xml';t=E.parse(tm).getroot();assert t.get('package')==PKG+'.test' and t.find('instrumentation').get(A+'targetPackage')==PKG
 ta=t.find('application');assert ta.get(A+'name') is None and all(ta.find(v) is None for v in ['provider','receiver','service','meta-data']);assert {a.get(A+'name') for a in ta.findall('activity')}=={'androidx.test.core.app.InstrumentationActivityInvoker$BootstrapActivity','androidx.test.core.app.InstrumentationActivityInvoker$EmptyActivity','androidx.test.core.app.InstrumentationActivityInvoker$EmptyFloatingActivity'};shutil.copy2(tm,out/'test-manifest.xml')
 def dex(path):
  with zipfile.ZipFile(path) as z:return b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex'))
 d=dex(apk);td=dex(test)
 forbidden=['Lcom/google/firebase/','Lcom/google/android/gms/','Lcom/google/maps/','Landroidx/credentials/','Lcom/example/bai1/DisasterApplication;','Lcom/example/bai1/auth/AuthAppDependencies;','Lcom/example/bai1/sos/SosAppDependencies;','Lcom/example/bai1/presentation/location/AndroidLocationProvider;','Lcom/example/bai1/rescue/RescueMissionAppDependencies;']
 for v in forbidden:assert v.encode() not in d and v.encode() not in td,v
 for v in ['Lcom/example/bai1/rescue/routing/algorithm/UniformCostSearchRouter;','Lcom/example/bai1/rescue/routing/parser/StreamingRoadGraphParser;','Lcom/example/bai1/sos/network/OkHttpSosApiClient;','Lcom/example/bai1/sos/transport/ApiSosTransport;','Lcom/example/bai1/auth/network/OkHttpAuthApiClient;']:assert v.encode() in d
 # Real configuration values must never enter either demo DEX.
 values=[]
 def visit(v):
  if isinstance(v,dict):
   for k,x in v.items():
    if k in {'current_key','client_id','project_id','firebase_url','storage_bucket','mobilesdk_app_id'} and isinstance(x,str):values.append(x)
    else:visit(x)
  elif isinstance(v,list):
   for x in v:visit(x)
 g=ROOT/'app/google-services.json'
 if g.exists():visit(json.loads(g.read_text()))
 assert not any(v.encode() in d or v.encode() in td for v in values if len(v)>8)
 copies={}
 for p in (HERE/'build/generated/exact-source').rglob('*.kt'):
  rel=p.relative_to(HERE/'build/generated/exact-source');assert p.read_bytes()==(ROOT/'app/src/main/java'/rel).read_bytes();copies[str(rel)]=hashlib.sha256(p.read_bytes()).hexdigest()
 for kind in ['algorithm','model','parser','loader','network','download','repository']:
  for p in (ROOT/'app/src/main/java/com/example/bai1/rescue/routing'/kind).glob('*.kt'):copies[str(p.relative_to(ROOT/'app/src/main/java'))]=hashlib.sha256(p.read_bytes()).hexdigest()
 sources='\n'.join(p.read_text() for p in (HERE/'src/main/java').rglob('*.kt'))
 for v in ['getDeclaredMethod','isAccessible','executeSearch(','VERIFIED_COMPATIBLE','RoadCondition.CLEAR','canNavigateSafely=true']:assert v not in sources
 for v in ['Proxy.NO_PROXY','followRedirects(false)','followSslRedirects(false)','retryOnConnectionFailure(false)','u.host=="127.0.0.1"','SYNTHETIC_TEST_DATA','NAVIGATION ALLOWED = FALSE']:assert v in sources
 (out/'source-hashes.json').write_text(json.dumps(copies,indent=2));(out/'gate.json').write_text(json.dumps({'status':'PASS','package_absent_before_install':True,'production_configuration_values_checked':len(values),'matches':0,'exact_original_sources':len(copies),'private_kernel_called':False,'gps_cloud_permissions':False,'launcher_exported_intentionally_for_demo':True},indent=2))
 policy=subprocess.check_output([AAPT,'dump','xmltree',str(apk),'--file','res/xml/demo_network_security.xml'],text=True);assert policy.count('cleartextTrafficPermitted=true')==1 and "T: '127.0.0.1'" in policy;(out/'compiled-policy.txt').write_text(policy)
 artifacts=ROOT/'docs/demo/p5e10a/artifacts';artifacts.mkdir(parents=True,exist_ok=True)
 for path,name in [(apk,'p5e10a-demo.apk'),(test,'p5e10a-demo-test.apk')]:shutil.copy2(path,artifacts/name)
 (artifacts/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in [artifacts/'p5e10a-demo.apk',artifacts/'p5e10a-demo-test.apk']))
 print('PREINSTALL SECURITY PASS',len(copies),'exact sources; artifacts preserved',flush=True)
 names=re.findall(r'@Test fun (\w+)\(', (HERE/'src/androidTest/java/com/example/bai1/fixture/DemoPhoneTest.kt').read_text());assert len(names)==len(set(names))
 if args.tests:chosen=args.tests.split(',');assert set(chosen)<=set(names);names=chosen
 s=server();thread=threading.Thread(target=s.serve_forever,daemon=True);thread.start();remote='tcp:'+str(s.server_port);owned=False;installed=[]
 result={'build_command':cmd,'build_exit':code,'isolation_gate':'PASS','serial':'REDACTED','model':adb('shell','getprop','ro.product.model').stdout.strip(),'api':33,'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'test_apk_sha256':hashlib.sha256(test.read_bytes()).hexdigest(),'cases':[]};suite=E.Element('testsuite',name='P5E10APhysicalDemo')
 try:
  reverse_attempts=[]
  for attempt in range(3):
   assert remote not in adb('reverse','--list').stdout
   rev=adb('reverse',remote,remote)
   reverse_attempts.append({'owned_port':s.server_port,'exit':rev.returncode,'stdout':rev.stdout.replace(serial,'REDACTED'),'stderr':rev.stderr.replace(serial,'REDACTED')})
   (out/'reverse-attempts.json').write_text(json.dumps(reverse_attempts,indent=2))
   if rev.returncode==0:owned=True;break
   if remote in adb('reverse','--list').stdout:assert adb('reverse','--remove',remote).returncode==0
   s.shutdown();s.server_close();thread.join(timeout=5)
   s=server();thread=threading.Thread(target=s.serve_forever,daemon=True);thread.start();remote='tcp:'+str(s.server_port)
  assert owned,'Owned reverse mapping failed; no install attempted'
  for pkg,path in [(PKG,apk),(PKG+'.test',test)]:assert adb('install','-t',str(path)).returncode==0;installed.append(pkg)
  uids={}
  for pkg in installed:
   x=adb('shell','dumpsys','package',pkg);assert x.returncode==0;uid=int(re.search(r'userId=(\d+)',x.stdout).group(1));assert uid>=10000;uids[pkg]=uid
  assert len(set(uids.values()))==2;(out/'owned-uids.json').write_text(json.dumps(uids,indent=2))
  for name in names:
   with s.lock:s.store.clear();s.owners.clear() # Test-case isolation only; never reset across roles/recreation within a case
   assert adb('shell','am','force-stop',PKG).returncode==0;time.sleep(.75)
   if name=='demoRestartUsesOwnPortButNoAuthSession':assert result['cases'][-1]['name']=='demoPrepareForProcessRestart' and result['cases'][-1]['failures']==0
   folder=out/name;folder.mkdir();since=adb('shell','date "+%m-%d %H:%M:%S.000"').stdout.strip();start_events=len(s.events);pid=None;logcat=None
   command=[ADB,'-s',serial,'shell','am','instrument','-w','-r','-e','class','com.example.bai1.fixture.DemoPhoneTest#'+name,'-e','listener','com.example.bai1.fixture.DeviceXmlListener','-e','hostFixturePort',str(s.server_port),'-e','physicalUsb','true',PKG+'.test/com.example.bai1.fixture.IsolatedInstrumentationRunner']
   with (folder/'instrumentation.txt').open('w') as f,(folder/'own-pid-logcat.txt').open('w') as logs:
    proc=subprocess.Popen(command,stdout=f,stderr=subprocess.STDOUT);deadline=time.monotonic()+90
    try:
     while proc.poll() is None:
      if pid is None:
       v=adb('shell','pidof',PKG).stdout.strip()
       if v.isdigit():pid=v;logcat=subprocess.Popen([ADB,'-s',serial,'logcat','--pid='+pid,'-v','threadtime','-T',since],stdout=logs,stderr=subprocess.STDOUT)
      assert time.monotonic()<deadline,'Owned test deadline';time.sleep(.2)
    finally:
     if proc.poll() is None:adb('shell','am','force-stop',PKG);proc.terminate();proc.wait(timeout=10)
     if logcat:logcat.terminate();logcat.wait(timeout=10)
   text=(folder/'instrumentation.txt').read_text();prefix='INSTRUMENTATION_STATUS: p5e01XmlBase64=';line=next(l[len(prefix):] for l in text.splitlines() if l.startswith(prefix));raw=base64.b64decode(line);(folder/'TEST-ui.xml').write_bytes(raw);x=E.fromstring(raw);assert x.get('junitRunCount')=='1' and x.get('tests')=='1'
   events=s.events[start_events:];assert all(v['host']=='127.0.0.1:'+str(s.server_port) for v in events)
   with s.lock:store=dict(s.store)
   (folder/'shared-store.json').write_text(json.dumps(store,indent=2))
   (folder/'fixture-events.json').write_text(json.dumps(events,indent=2))
   if name=='demoRestartUsesOwnPortButNoAuthSession':assert pid and pid!=result['cases'][-1]['pid']
   for c in x.findall('testcase'):suite.append(c)
   result['cases'].append({'name':name,'tests':1,'failures':int(x.get('failures')),'errors':int(x.get('errors')),'skipped':int(x.get('skipped')),'adb_exit':proc.returncode,'pid':pid,'command':[v if v!=serial else 'REDACTED' for v in command],'fixture_events':events,'actual_outputs':re.findall(r'INSTRUMENTATION_STATUS: p5e10aOutput=([^\r\n]+)',text)});(out/'results.json').write_text(json.dumps(result,indent=2));print(name,'failures',x.get('failures'),flush=True)
  suite.set('tests',str(len(suite)))
  for k in ['failures','errors','skipped']:suite.set(k,str(sum(c[k] for c in result['cases'])))
  E.ElementTree(suite).write(out/'TEST-demo.xml',encoding='utf-8',xml_declaration=True)
 finally:
  cleanup=[]
  if owned and adb('reverse','--remove',remote).returncode!=0:cleanup.append('owned reverse')
  s.shutdown();s.server_close();thread.join(timeout=5)
  for pkg in reversed(installed):
   if adb('uninstall',pkg).returncode!=0:cleanup.append(pkg)
  result['cleanup_errors']=cleanup;result['remaining_owned_packages']=[p for p in [PKG,PKG+'.test'] if 'package:' in adb('shell','pm','path',p).stdout];result['owned_reverse_removed']=remote not in adb('reverse','--list').stdout
  (out/'results.json').write_text(json.dumps(result,indent=2))
 result['status']='PASS' if len(result['cases'])==len(names) and all(c['adb_exit']==0 and all(c[k]==0 for k in ['failures','errors','skipped']) for c in result['cases']) and not cleanup and not result['remaining_owned_packages'] and result['owned_reverse_removed'] else 'FAIL';(out/'results.json').write_text(json.dumps(result,indent=2));assert result['status']=='PASS';print('PHYSICAL DEMO PASS',len(names),'unique runtime cases',flush=True)
if __name__=='__main__':main()
