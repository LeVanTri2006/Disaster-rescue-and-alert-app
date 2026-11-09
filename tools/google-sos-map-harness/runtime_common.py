"""Shared bounded/redacted host helpers. Never print a key."""
import sys
sys.dont_write_bytecode=True
import os,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).resolve().parent
ADB='/home/vantri/Android/Sdk/platform-tools/adb';PKG='com.example.bai1.p5e10m02.demo'
AAPT='/home/vantri/Android/Sdk/build-tools/36.0.0/aapt2';SIGN='/home/vantri/Android/Sdk/build-tools/36.0.0/apksigner'
ENV=dict(os.environ,JAVA_HOME='/usr/lib/jvm/java-25-openjdk-amd64',ANDROID_HOME='/home/vantri/Android/Sdk');ENV['PATH']=ENV['JAVA_HOME']+'/bin:'+ENV['PATH']
# Gradle uses java.util.Properties as the authority. This list plus key-pattern
# redaction protects logs even on a failed manifest/build before XML is available.
SECRETS=[x.split('=',1)[1].strip() for x in (ROOT/'local.properties').read_text().splitlines() if x.startswith('MAPS_API_KEY=') and x.split('=',1)[1].strip() not in ['', 'PLACEHOLDER']]
if os.environ.get('GMAPS01_API_KEY'):SECRETS.append(os.environ['GMAPS01_API_KEY'])
def redact(text):
 for value in SECRETS:text=text.replace(value,'[REDACTED_MAPS_KEY]')
 text=re.sub(r'e10a-synthetic-[A-Za-z0-9-]+','[REDACTED_SYNTHETIC_TOKEN]',text)
 return re.sub(r'AIza[0-9A-Za-z_-]{20,}','[REDACTED_MAPS_KEY]',text)
def log_command(cmd,path,timeout=None):
 p=subprocess.Popen(cmd,cwd=ROOT,env=ENV,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 with Path(path).open('w') as f:
  for line in p.stdout:f.write(redact(line));f.flush()
 return p.wait(timeout=timeout)
def phone():
 lines=subprocess.check_output([ADB,'devices','-l'],text=True).splitlines()[1:];phones=[l.split()[0] for l in lines if 'usb:' in l and not l.startswith('emulator')];assert len(phones)==1,'Exactly one authorized physical USB phone required';s=phones[0]
 def adb(*a,**kwargs):return subprocess.run([ADB,'-s',s,*a],capture_output=True,text=True,**kwargs)
 assert adb('get-state').stdout.strip()=='device' and adb('shell','getprop','sys.boot_completed').stdout.strip()=='1' and adb('shell','getprop','ro.kernel.qemu').stdout.strip()!='1'
 assert adb('shell','getprop','ro.product.model').stdout.strip()=='CPH2159'
 return s,adb
