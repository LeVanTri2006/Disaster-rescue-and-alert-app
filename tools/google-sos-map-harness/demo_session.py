"""Owned physical M02 fixture/reverse session. Never replaces an installed APK."""
import sys
sys.dont_write_bytecode=True
from runtime_common import *
from fixture import server
import argparse,hashlib,json,threading,time

def main():
 p=argparse.ArgumentParser();p.add_argument('--install',action='store_true');p.add_argument('--keep-demo',action='store_true');p.add_argument('--seconds',type=int,default=0);p.add_argument('--evidence-dir',type=Path);args=p.parse_args()
 out=args.evidence_dir or ROOT/'docs/demo/gmaps02/evidence'/('session-'+time.strftime('%Y%m%d-%H%M%S'));assert not out.exists();out.mkdir(parents=True)
 apk=ROOT/'docs/demo/gmaps02/artifacts/gmaps02-debug.apk';digest=hashlib.sha256(apk.read_bytes()).hexdigest();expected=next(l.split()[0] for l in (apk.parent/'SHA256SUMS').read_text().splitlines() if l.endswith('gmaps02-debug.apk'));assert digest==expected
 serial,adb=phone();path=adb('shell','pm','path',PKG);installed=False
 assert adb('get-state').stdout.strip()=='device'
 if not path.stdout.strip():
  absence=adb('shell','pm','list','packages',PKG);assert absence.returncode==0 and not absence.stdout.strip() and path.returncode in (0,1),'Cannot establish namespace absence'
 else:assert path.returncode==0
 if path.stdout.strip():
  paths=path.stdout.strip().splitlines();assert len(paths)==1 and paths[0].startswith('package:');actual=adb('shell','sha256sum',paths[0][8:]);assert actual.returncode==0 and actual.stdout.split()[0]==digest,'Different installed APK: refusing overwrite/uninstall'
 else:
  assert args.install,'Absent package: use --install';install=adb('install','-t',str(apk));assert install.returncode==0;installed=True
 s=server();thread=threading.Thread(target=s.serve_forever,daemon=True);thread.start();remote='tcp:'+str(s.server_port);owned=False;logs=None;logthread=None
 result={'package':PKG,'apk_sha256':digest,'installed_new':installed,'keep_demo':args.keep_demo,'serial':'REDACTED','fixture_port':s.server_port,'status':'STARTING'}
 def pump(proc):
  with (out/'own-pid-logcat.txt').open('w') as f:
   for line in proc.stdout:f.write(redact(line).replace(serial,'REDACTED'));f.flush()
 try:
  assert remote not in adb('reverse','--list').stdout;rev=adb('reverse',remote,remote);assert rev.returncode==0;owned=True
  since=adb('shell','date "+%m-%d %H:%M:%S.000"').stdout.strip()
  command=['shell','am','start','-W','-n',PKG+'/com.example.bai1.gmaps02.DemoActivity','--ei','fixturePort',str(s.server_port)]
  assert adb('shell','am','force-stop',PKG).returncode==0 # Own verified demo: start a fresh in-memory session.
  started=adb(*command);result['start_command']=['adb','-s','REDACTED',*command];result['start_exit']=started.returncode;result['start_output']=redact(started.stdout+started.stderr);assert started.returncode==0 and 'Status: ok' in started.stdout
  pid=adb('shell','pidof',PKG).stdout.strip();assert pid.isdigit();result['pid']=pid
  logs=subprocess.Popen([ADB,'-s',serial,'logcat','--pid='+pid,'-v','threadtime','-T',since],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);logthread=threading.Thread(target=pump,args=(logs,));logthread.start()
  result['status']='READY_LOGIN';print('M02 READY_LOGIN: shared localhost fixture + real Google Maps. Keep USB/terminal connected.',flush=True)
  print('Người dân demo → SOS synthetic → Gửi → Đăng xuất → Đội cứu hộ demo → Refresh SOS. Ctrl+C kết thúc.',flush=True)
  end=time.monotonic()+args.seconds if args.seconds else None
  while end is None or time.monotonic()<end:time.sleep(.25)
 except KeyboardInterrupt:result['ended_by']='Ctrl+C'
 finally:
  if logs:logs.terminate();logs.wait(10);logthread.join(10)
  result['fixture_events']=s.events;result['shared_store']=s.store
  if owned:result['reverse_remove_exit']=adb('reverse','--remove',remote).returncode
  s.shutdown();s.server_close();thread.join(5)
  if installed and not args.keep_demo:result['uninstall_exit']=adb('uninstall',PKG).returncode
  result['device_authorized_after']=adb('get-state').stdout.strip()=='device';result['owned_reverse_removed']=remote not in adb('reverse','--list').stdout
  result['status']='PASS' if result['status']=='READY_LOGIN' and result.get('reverse_remove_exit')==0 and result['device_authorized_after'] and result['owned_reverse_removed'] and result.get('uninstall_exit',0)==0 else 'FAIL'
  (out/'session.json').write_text(redact(json.dumps(result,indent=2)));print('M02 session',result['status'],flush=True)
 assert result['status']=='PASS'
if __name__=='__main__':main()
