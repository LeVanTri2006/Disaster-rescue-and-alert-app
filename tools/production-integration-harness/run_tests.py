"""Real OPPO instrumentation with preinstall gates, own-PID logs and key redaction."""
import sys
sys.dont_write_bytecode=True
from runtime_common import *
from gate_runtime import gate
import argparse,json,time,threading,base64,hashlib,xml.etree.ElementTree as E
from fixture import server
NAMES=re.findall(r'@Test fun (\w+)\(', (HERE/'src/androidTest/java/com/example/bai1/fixture/DemoPhoneTest.kt').read_text())
SHOTS={'productionUiPostLogoutRescuerGetAndNativeMapMarker':'production-sos-map.png','productionEmptyMapLoadsWithoutInventedSos':'production-empty.png','productionFormHttp403ShowsError':'production-form-error.png'}
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--evidence-dir',type=Path,required=True);ap.add_argument('--tests');args=ap.parse_args();out=args.evidence_dir.resolve();assert not out.exists();out.mkdir(parents=True)
 assert len(NAMES)==len(set(NAMES))
 if args.tests:assert set(args.tests.split(','))<=set(NAMES),'Unknown test selection'
 import shutil
 assert shutil.disk_usage('/').free>=2_000_000_000
 serial,adb=phone();initial=[PKG,PKG+'.test']
 for p in initial:assert 'package:' not in adb('shell','pm','path',p).stdout,'Existing namespace: no overwrite'
 gates=[gate()];(out/'gates.json').write_text(json.dumps(gates,indent=2))
 result={'serial':'REDACTED','model':'CPH2159','api':adb('shell','getprop','ro.build.version.sdk').stdout.strip(),'status':'RUNNING','cases':[],'installs':[]};suite=E.Element('testsuite',name='ProductionUiPhysical');installed=[];error=None
 def record(): (out/'results.json').write_text(json.dumps(result,indent=2))
 def sanitized_line(line):return redact(line).replace(serial,'REDACTED')
 def pump(proc,file):
  for line in proc.stdout:file.write(sanitized_line(line));file.flush()
 s=server();fixture_thread=threading.Thread(target=s.serve_forever,daemon=True);fixture_thread.start();remote='tcp:'+str(s.server_port);owned=False
 try:
  assert remote not in adb('reverse','--list').stdout
  reverse=adb('reverse',remote,remote);assert reverse.returncode==0;owned=True
  result['fixture_port']=s.server_port
  for variant,names in [('debug',NAMES)]:
   if args.tests:names=[n for n in names if n in args.tests.split(',')]
   if not names:continue
   pkg=PKG+('.negative' if variant=='negative' else '')
   for target,name in [(pkg,f'e10-production-{variant}.apk'),(pkg+'.test',f'e10-production-{variant}-test.apk')]:
    apk=ROOT/'docs/demo/production-integration/artifacts'/name;p=adb('install','-t',str(apk),timeout=120);result['installs'].append({'package':target,'command':['adb','-s','REDACTED','install','-t',str(apk)],'exit':p.returncode,'output':sanitized_line(p.stdout+p.stderr)});record();assert p.returncode==0;installed.append(target)
   uids={}
   for target in [pkg,pkg+'.test']:
    text=adb('shell','dumpsys','package',target).stdout;uid=int(re.search(r'userId=(\d+)',text).group(1));assert uid>=10000;uids[target]=uid
   assert len(set(uids.values()))==2;(out/(variant+'-owned-uids.json')).write_text(json.dumps(uids,indent=2))
   for name in names:
    if name!='demoRestartUsesOwnPortButNoAuthSession':
     with s.lock:s.store.clear();s.owners.clear()
    start_events=len(s.events)
    assert adb('shell','am','force-stop',pkg).returncode==0;time.sleep(.5);folder=out/name;folder.mkdir();since=adb('shell','date "+%m-%d %H:%M:%S.000"').stdout.strip();pid=None;logs=None;thread=None
    cmd=[ADB,'-s',serial,'shell','am','instrument','-w','-r','-e','class','com.example.bai1.fixture.DemoPhoneTest#'+name,'-e','listener','com.example.bai1.fixture.DeviceXmlListener','-e','hostFixturePort',str(s.server_port),'-e','physicalUsb','true',pkg+'.test/com.example.bai1.fixture.IsolatedInstrumentationRunner']
    with (folder/'instrumentation.txt').open('w') as f,(folder/'own-pid-logcat.txt').open('w') as log:
     proc=subprocess.Popen(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);output_thread=threading.Thread(target=pump,args=(proc,f));output_thread.start();end=time.monotonic()+170
     try:
      while proc.poll() is None:
       if pid is None:
        v=adb('shell','pidof',pkg).stdout.strip()
        if v.isdigit():pid=v;logs=subprocess.Popen([ADB,'-s',serial,'logcat','--pid='+v,'-v','threadtime','-T',since],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);thread=threading.Thread(target=pump,args=(logs,log));thread.start()
       assert time.monotonic()<end,'Owned instrumentation deadline';time.sleep(.2)
     finally:
      if proc.poll() is None:adb('shell','am','force-stop',pkg);proc.terminate();proc.wait(timeout=10)
      output_thread.join(10)
      if logs:logs.terminate();logs.wait(timeout=10);thread.join(10)
    text=(folder/'instrumentation.txt').read_text();prefix='INSTRUMENTATION_STATUS: p5e01XmlBase64=';line=next((x[len(prefix):] for x in text.splitlines() if x.startswith(prefix)),None)
    if not line:raise RuntimeError('Instrumentation did not export actual JUnit XML: '+name)
    xml=redact(base64.b64decode(line).decode());(folder/'TEST-ui.xml').write_text(xml);x=E.fromstring(xml);assert x.get('tests')==x.get('junitRunCount')=='1'
    for case in x.findall('testcase'):suite.append(case)
    outputs=[json.loads(s) for s in re.findall(r'INSTRUMENTATION_STATUS: p5e10aOutput=([^\r\n]+)',text)]
    row={'name':name,'variant':variant,'tests':1,'failures':int(x.get('failures')),'errors':int(x.get('errors')),'skipped':int(x.get('skipped')),'command':['REDACTED' if v==serial else v for v in cmd],'adb_exit':proc.returncode,'pid':pid,'actual_outputs':outputs}
    events=s.events[start_events:]
    assert all(e['host']=='127.0.0.1:'+str(s.server_port) for e in events)
    row['fixture_events']=events
    with s.lock:store=dict(s.store)
    (folder/'fixture-events.json').write_text(json.dumps(events,indent=2));(folder/'shared-store.json').write_text(json.dumps(store,indent=2))
    if name=='demoRestartUsesOwnPortButNoAuthSession':assert result['cases'][-1]['name']=='demoPrepareForProcessRestart' and pid!=result['cases'][-1]['pid']
    if name=='committedPostTimeoutHasUnknownOutcomeAndNeverAutoRetries':assert len([e for e in events if e['method']=='POST' and e['path']=='/sos'])==1 and len(store)==1
    if name=='multipleSosHaveSeparateResponseMarkersAndRefreshNeverPosts':assert len([e for e in events if e['method']=='POST' and e['path']=='/sos'])==2 and len(store)==2
    if name in SHOTS and row['failures']==row['errors']==0:
     shot=SHOTS[name];p=subprocess.run([ADB,'-s',serial,'exec-out','run-as',pkg,'cat','files/'+shot],capture_output=True);assert p.returncode==0 and p.stdout.startswith(b'\x89PNG');(folder/shot).write_bytes(p.stdout);row['screenshot']=shot
    if row['failures'] or row['errors']:
     fshot=subprocess.run([ADB,'-s',serial,'exec-out','run-as',pkg,'cat','files/failure-own-window.png'],capture_output=True)
     if fshot.returncode==0 and fshot.stdout.startswith(b'\x89PNG'):(folder/'failure-own-window.png').write_bytes(fshot.stdout)
    result['cases'].append(row);record();print(name,'failures',row['failures'],'exit',row['adb_exit'],flush=True)
    assert proc.returncode==0 and row['failures']==row['errors']==row['skipped']==0,'Actual physical case failed: '+name
   for target in [pkg+'.test',pkg]:
    p=adb('uninstall',target);assert p.returncode==0;installed.remove(target)
 except BaseException as e:error=redact(type(e).__name__+': '+str(e));result['exception']=error
 finally:
  cleanup=[]
  if owned and adb('reverse','--remove',remote).returncode!=0:cleanup.append('reverse')
  s.shutdown();s.server_close();fixture_thread.join(5)
  for target in reversed(installed):
   p=adb('uninstall',target)
   if p.returncode:cleanup.append(target)
  state=adb('get-state');connected=state.returncode==0 and state.stdout.strip()=='device';remaining=[]
  if connected:
   for p in initial:
    if 'package:' in adb('shell','pm','path',p).stdout:remaining.append(p)
  reverse_removed=owned and remote not in adb('reverse','--list').stdout
  result.update(owned_reverse_removed=reverse_removed,cleanup_errors=cleanup,device_authorized_after=connected,remaining_owned_packages=remaining if connected else 'UNKNOWN_OFFLINE',status='PASS' if not error and not cleanup and connected and not remaining and reverse_removed and result['cases'] else 'FAIL')
  suite.set('tests',str(len(suite)))
  for k in ['failures','errors','skipped']:suite.set(k,str(sum(c[k] for c in result['cases'])))
  E.ElementTree(suite).write(out/'TEST-maps.xml',encoding='utf-8',xml_declaration=True);record()
 print('PHYSICAL PRODUCTION UI',result['status'],len(result['cases']),'unique JUnit cases',flush=True)
 if result['status']!='PASS':raise SystemExit(1)
if __name__=='__main__':main()
