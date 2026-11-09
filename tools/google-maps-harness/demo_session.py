"""Open only the approved Maps demo APK on the physical OPPO. No Cloud changes."""
import sys
sys.dont_write_bytecode=True
from runtime_common import *
import argparse,hashlib,json,time,threading,signal

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--install',action='store_true');ap.add_argument('--keep-demo',action='store_true');ap.add_argument('--seconds',type=int);ap.add_argument('--evidence-dir',type=Path);args=ap.parse_args()
 serial,adb=phone();gate=json.loads((ROOT/'docs/demo/gmaps01/evidence/runtime/debug-gate.json').read_text());apk=ROOT/'docs/demo/gmaps01/artifacts/gmaps01-debug.apk';digest=hashlib.sha256(apk.read_bytes()).hexdigest();assert digest==gate['artifacts'][apk.name] and gate['status']=='PASS'
 out=args.evidence_dir.resolve() if args.evidence_dir else ROOT/'docs/demo/gmaps01/evidence/runtime/sessions'/str(int(time.time()));assert not out.exists();out.mkdir(parents=True)
 installed=False;path=adb('shell','pm','path',PKG).stdout.strip();result={'package':PKG,'serial':'REDACTED','apk_sha256':digest,'preexisting':bool(path),'sdk_loaded_callback':False,'keep_demo':args.keep_demo}
 if path:
  assert path.startswith('package:') and '\n' not in path
  actual=adb('shell','sha256sum',path.removeprefix('package:'));assert actual.returncode==0 and actual.stdout.split()[0]==digest,'Refuse replacing/uninstalling pre-existing different APK'
 else:
  assert args.install,'Package absent; use --install'
  p=adb('install','-t',str(apk),timeout=120);result['install_exit']=p.returncode;assert p.returncode==0;installed=True
 log=None;thread=None
 def collect(proc,f):
  for line in proc.stdout:f.write(redact(line).replace(serial,'REDACTED'));f.flush()
 try:
  since=adb('shell','date "+%m-%d %H:%M:%S.000"').stdout.strip();p=adb('shell','am','start','-W','-n',PKG+'/com.example.bai1.gmaps01.MapsDemoActivity');result['am_start_exit']=p.returncode;result['am_start_output']=redact(p.stdout+p.stderr);assert p.returncode==0
  end=time.monotonic()+15;pid=''
  while time.monotonic()<end:
   pid=adb('shell','pidof',PKG).stdout.strip()
   if pid.isdigit():break
   time.sleep(.2)
  assert pid.isdigit();result['pid']=pid
  with (out/'own-pid-logcat.txt').open('w') as f:
   log=subprocess.Popen([ADB,'-s',serial,'logcat','--pid='+pid,'-v','threadtime','-T',since],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);thread=threading.Thread(target=collect,args=(log,f));thread.start();end=time.monotonic()+65
   while time.monotonic()<end:
    if 'onMapLoaded count=' in (out/'own-pid-logcat.txt').read_text():result['sdk_loaded_callback']=True;break
    time.sleep(.3)
   assert result['sdk_loaded_callback'],'SDK loaded callback unavailable; inspect redacted own-PID log; do not infer billing cause'
   print('GOOGLE MAPS READY — SDK onMapLoaded observed; verify visible real tiles and TEST markers.',flush=True)
   print('RESCUER TEST / SOS TEST; no GPS, business backend or routing. Ctrl+C ends session.',flush=True)
   if args.seconds:time.sleep(args.seconds)
   else:
    while True:time.sleep(1)
 except KeyboardInterrupt:pass
 finally:
  if log:log.terminate();log.wait(10);thread.join(10)
  if installed and not args.keep_demo:result['cleanup_uninstall_exit']=adb('uninstall',PKG).returncode
  result['retained']=bool(args.keep_demo or path);result['status']='PASS' if result['sdk_loaded_callback'] and result.get('cleanup_uninstall_exit',0)==0 else 'FAIL';(out/'session.json').write_text(json.dumps(result,indent=2))
 assert result['status']=='PASS'
if __name__=='__main__':main()
