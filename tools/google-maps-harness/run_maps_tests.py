"""Real OPPO instrumentation with preinstall gates, own-PID logs and key redaction."""
import sys
sys.dont_write_bytecode=True
from runtime_common import *
from gate_runtime import gate
import argparse,json,time,threading,base64,hashlib,xml.etree.ElementTree as E
POSITIVE=['actualGoogleMapLoadsTilesAtSyntheticCamera','actualRescuerMarkerTapShowsCorrectInfo','actualSosMarkerTapShowsCorrectInfo','actualDoubleTapZoomAndDragPanChangeSdkCamera','actualActivityRecreationReloadsSdkWithoutCrash','noLocationPermissionsAndSdkMyLocationDisabled','isolatedDemoKeepsF06AndStrictDisabledBanner']
SHOTS={'actualGoogleMapLoadsTilesAtSyntheticCamera':'tiles-loaded.png','actualRescuerMarkerTapShowsCorrectInfo':'rescuer-info.png','actualSosMarkerTapShowsCorrectInfo':'sos-info.png','actualDoubleTapZoomAndDragPanChangeSdkCamera':'zoom-pan.png','invalidApiKeyCannotProduceLoadedSuccess':'invalid-key-no-tiles.png'}
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--evidence-dir',type=Path,required=True);ap.add_argument('--tests');ap.add_argument('--negative-only',action='store_true');args=ap.parse_args();out=args.evidence_dir.resolve();assert not out.exists();out.mkdir(parents=True)
 import shutil
 assert shutil.disk_usage('/').free>=2_000_000_000
 serial,adb=phone();initial=[PKG,PKG+'.test',PKG+'.negative',PKG+'.negative.test']
 for p in initial:assert 'package:' not in adb('shell','pm','path',p).stdout,'Existing namespace: no overwrite'
 gates=[gate(v) for v in ['debug','negative']];(out/'gates.json').write_text(json.dumps(gates,indent=2))
 result={'serial':'REDACTED','model':'CPH2159','api':adb('shell','getprop','ro.build.version.sdk').stdout.strip(),'status':'RUNNING','cases':[],'installs':[]};suite=E.Element('testsuite',name='M01GoogleMapsPhysical');installed=[];error=None
 def record(): (out/'results.json').write_text(json.dumps(result,indent=2))
 def sanitized_line(line):return redact(line).replace(serial,'REDACTED')
 def pump(proc,file):
  for line in proc.stdout:file.write(sanitized_line(line));file.flush()
 try:
  for variant,names in ([('negative',['invalidApiKeyCannotProduceLoadedSuccess'])] if args.negative_only else [('debug',POSITIVE),('negative',['invalidApiKeyCannotProduceLoadedSuccess'])]):
   if args.tests:names=[n for n in names if n in args.tests.split(',')]
   if not names:continue
   pkg=PKG+('.negative' if variant=='negative' else '')
   for target,name in [(pkg,f'gmaps01-{variant}.apk'),(pkg+'.test',f'gmaps01-{variant}-test.apk')]:
    apk=ROOT/'docs/demo/gmaps01/artifacts'/name;p=adb('install','-t',str(apk),timeout=120);result['installs'].append({'package':target,'command':['adb','-s','REDACTED','install','-t',str(apk)],'exit':p.returncode,'output':sanitized_line(p.stdout+p.stderr)});record();assert p.returncode==0;installed.append(target)
   uids={}
   for target in [pkg,pkg+'.test']:
    text=adb('shell','dumpsys','package',target).stdout;uid=int(re.search(r'userId=(\d+)',text).group(1));assert uid>=10000;uids[target]=uid
   assert len(set(uids.values()))==2;(out/(variant+'-owned-uids.json')).write_text(json.dumps(uids,indent=2))
   for name in names:
    assert adb('shell','am','force-stop',pkg).returncode==0;time.sleep(.5);folder=out/name;folder.mkdir();since=adb('shell','date "+%m-%d %H:%M:%S.000"').stdout.strip();pid=None;logs=None;thread=None
    cmd=[ADB,'-s',serial,'shell','am','instrument','-w','-r','-e','class','com.example.bai1.fixture.MapsPhoneTest#'+name,'-e','listener','com.example.bai1.fixture.DeviceXmlListener','-e','physicalUsb','true',pkg+'.test/com.example.bai1.fixture.IsolatedInstrumentationRunner']
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
    outputs=[json.loads(s) for s in re.findall(r'INSTRUMENTATION_STATUS: m01Output=([^\r\n]+)',text)]
    row={'name':name,'variant':variant,'tests':1,'failures':int(x.get('failures')),'errors':int(x.get('errors')),'skipped':int(x.get('skipped')),'command':['REDACTED' if v==serial else v for v in cmd],'adb_exit':proc.returncode,'pid':pid,'actual_outputs':outputs}
    if name in SHOTS and row['failures']==row['errors']==0:
     shot=SHOTS[name];p=subprocess.run([ADB,'-s',serial,'exec-out','run-as',pkg,'cat','files/'+shot],capture_output=True);assert p.returncode==0 and p.stdout.startswith(b'\x89PNG');(folder/shot).write_bytes(p.stdout);row['screenshot']=shot
    result['cases'].append(row);record();print(name,'failures',row['failures'],'exit',row['adb_exit'],flush=True)
    assert proc.returncode==0 and row['failures']==row['errors']==row['skipped']==0,'Actual physical case failed: '+name
   for target in [pkg+'.test',pkg]:
    p=adb('uninstall',target);assert p.returncode==0;installed.remove(target)
 except BaseException as e:error=redact(type(e).__name__+': '+str(e));result['exception']=error
 finally:
  cleanup=[]
  for target in reversed(installed):
   p=adb('uninstall',target)
   if p.returncode:cleanup.append(target)
  state=adb('get-state');connected=state.returncode==0 and state.stdout.strip()=='device';remaining=[]
  if connected:
   for p in initial:
    if 'package:' in adb('shell','pm','path',p).stdout:remaining.append(p)
  result.update(cleanup_errors=cleanup,device_authorized_after=connected,remaining_owned_packages=remaining if connected else 'UNKNOWN_OFFLINE',status='PASS' if not error and not cleanup and connected and not remaining else 'FAIL')
  suite.set('tests',str(len(suite)))
  for k in ['failures','errors','skipped']:suite.set(k,str(sum(c[k] for c in result['cases'])))
  E.ElementTree(suite).write(out/'TEST-maps.xml',encoding='utf-8',xml_declaration=True);record()
 print('PHYSICAL MAPS',result['status'],len(result['cases']),'unique JUnit cases',flush=True)
 if result['status']!='PASS':raise SystemExit(1)
if __name__=='__main__':main()
