#!/usr/bin/env python3
"""User-invoked presentation session. Never replaces an installed package."""
import sys
sys.dont_write_bytecode=True
import argparse,hashlib,json,subprocess,threading,time,signal
from pathlib import Path
from fixture import server
ROOT=Path(__file__).resolve().parents[2];ADB='/home/vantri/Android/Sdk/platform-tools/adb';PKG='com.example.bai1.p5e10a.demo'
def main():
 p=argparse.ArgumentParser();p.add_argument('--install',action='store_true',help='Install only if namespace is absent');p.add_argument('--keep-demo',action='store_true',help='Explicitly retain a package installed by this session');args=p.parse_args()
 apk=ROOT/'docs/demo/p5e10a/artifacts/p5e10a-demo.apk';assert apk.exists();digest=hashlib.sha256(apk.read_bytes()).hexdigest();expected=next(l.split()[0] for l in (apk.parent/'SHA256SUMS').read_text().splitlines() if l.endswith('p5e10a-demo.apk'));assert digest==expected
 lines=subprocess.check_output([ADB,'devices','-l'],text=True).splitlines()[1:];phones=[l.split()[0] for l in lines if 'usb:' in l and not l.startswith('emulator')];assert len(phones)==1,'Connect exactly one authorized USB phone';serial=phones[0]
 def adb(*x):return subprocess.run([ADB,'-s',serial,*x],capture_output=True,text=True)
 assert adb('get-state').stdout.strip()=='device' and adb('shell','getprop','sys.boot_completed').stdout.strip()=='1' and adb('shell','getprop','ro.kernel.qemu').stdout.strip()!='1'
 path=adb('shell','pm','path',PKG).stdout.strip();installed=False
 if path:
  assert path.startswith('package:');actual=adb('shell','sha256sum',path[len('package:'):]);assert actual.returncode==0 and actual.stdout.split()[0]==digest,'Installed package differs: refusing overwrite or uninstall'
 else:
  assert args.install,'Package absent: rerun with --install after reviewing APK'
  assert adb('install','-t',str(apk)).returncode==0;installed=True
 s=server();t=threading.Thread(target=s.serve_forever,daemon=True);t.start();remote='tcp:'+str(s.server_port);owned=False
 try:
  assert remote not in adb('reverse','--list').stdout;assert adb('reverse',remote,remote).returncode==0;owned=True
  assert adb('shell','am','start','-W','-n',PKG+'/com.example.bai1.demo.DemoActivity','--ei','fixturePort',str(s.server_port)).returncode==0
  print('DEMO READY (shared SOS fixture only). Phone:',adb('shell','getprop','ro.product.model').stdout.strip(),flush=True)
  print('Loopback fixture port:',s.server_port,'| no cloud/GPS/dispatch. Keep this terminal and USB connected.',flush=True)
  print('Người dân demo → SOS synthetic → Gửi → Đăng xuất → Đội cứu hộ demo → Refresh SOS → marker. Ctrl+C ends session.',flush=True)
  while True:time.sleep(1)
 except KeyboardInterrupt:print('Ending owned session.',flush=True)
 finally:
  if owned:assert adb('reverse','--remove',remote).returncode==0
  s.shutdown();s.server_close();t.join(timeout=5)
  if installed and not args.keep_demo:assert adb('uninstall',PKG).returncode==0
  elif installed:print('Demo retained by explicit --keep-demo. Rerun this session for shared SOS fixture; server state ends with this session.',flush=True)
  else:print('Pre-existing matching demo package preserved.',flush=True)
if __name__=='__main__':main()
