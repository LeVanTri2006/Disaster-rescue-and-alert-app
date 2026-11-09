"""Owned E10A fixture: one session-wide SOS store, no cloud/forwarding/dispatch."""
import sys
sys.dont_write_bytecode=True
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import argparse,json,time,threading,copy,uuid
from pathlib import Path
TOKENS={r:'e10a-synthetic-'+r+'-not-a-jwt' for r in ['citizen','citizen2','citizen3','rescuer']}
class Fixture(BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def reply(self,code,value,raw=False):
  body=value.encode() if raw else json.dumps(value).encode();assert len(body)<=65536
  self.send_response(code);self.send_header('Content-Type','text/plain' if raw else 'application/json');self.send_header('Content-Length',str(len(body)));self.send_header('Connection','close');self.end_headers();self.close_connection=True
  try:self.wfile.write(body)
  except (BrokenPipeError,ConnectionResetError):pass
 def event(self):
  token=self.headers.get('Authorization','').removeprefix('Bearer ')
  actor=next((r for r,t in TOKENS.items() if t==token),None)
  role=('citizen' if actor.startswith('citizen') else 'rescuer') if actor else None
  e={'method':self.command,'path':self.path,'host':self.headers.get('Host'),'fault':self.headers.get('X-Demo-Fault','ok'),'role':role,'actor':actor}
  with self.server.lock:self.server.events.append(e)
  return e
 def fault(self,e):
  if e['fault'] in ['403','500']:self.reply(int(e['fault']),{});return True
  if e['fault']=='timeout':
   # Deliberate pre-commit failure, not a claim that real HTTP timeouts cancel writes.
   time.sleep(3);self.reply(503,{});return True
  return False
 def do_GET(self):
  e=self.event()
  if self.path=='/demo/info':return self.reply(200,'P5-E10A-SYNTHETIC-NO-DISPATCH',True)
  if self.path.startswith('/sos/') and self.path!='/sos/active':
   if not e['role']:return self.reply(401,{})
   key=self.path.removeprefix('/sos/')
   try:uuid.UUID(key)
   except ValueError:return self.reply(400,{})
   with self.server.lock:row=copy.deepcopy(self.server.store.get(key));owner=self.server.owners.get(key)
   if not row:return self.reply(404,{})
   if e['role']!='rescuer' and owner!=e['actor']:return self.reply(403,{})
   return self.reply(200,{'success':True,'data':row})
  if self.path!='/sos/active':return self.reply(404,{})
  if not e['role']:return self.reply(401,{})
  if self.fault(e):return
  with self.server.lock:rows=copy.deepcopy(list(self.server.store.values()))
  e['response_ids']=[x['eventId'] for x in rows];e['response']=rows
  return self.reply(200,{'data':rows})
 def do_POST(self):
  e=self.event()
  try:
   n=int(self.headers.get('Content-Length','0'))
   if not 0<n<=4096:return self.reply(413,{})
   body=json.loads(self.rfile.read(n))
  except Exception:return self.reply(400,{})
  if self.path=='/auth/login':
   role=next((r for r in TOKENS if body.get('email')==r+'@example.invalid'),None)
   if not role or body.get('password')!='fixture-only-password':return self.reply(401,{})
   e['synthetic_account_role']=role
   return self.reply(200,{'success':True,'data':{'accessToken':TOKENS[role],'refreshToken':'e10a-synthetic-refresh-not-a-jwt','isNewUser':False}})
  if self.path!='/sos':return self.reply(404,{})
  if e['role']!='citizen':return self.reply(403,{})
  if body.get('condition')!='SYNTHETIC_TEST_DATA' or not 1<=body.get('adults',0)<=10 or not 16.04<=body.get('latitude',0)<=16.06 or not 108.19<=body.get('longitude',0)<=108.21 or not str(body.get('description','')).startswith('SYNTHETIC_TEST_DATA'):return self.reply(400,{})
  e['request']=body # synthetic only; never auth bodies/tokens/passwords
  if self.fault(e):return
  with self.server.lock:
   if e['actor'] in self.server.owners.values():return self.reply(409,{})
   if len(self.server.store)>=32:return self.reply(409,{})
   self.server.counter+=1
   response=dict(body,eventId=str(uuid.uuid4()),createdAt=1794268800000,status='ACTIVE',transportMode='ONLINE')
   self.server.store[response['eventId']]=response
   self.server.owners[response['eventId']]=e['actor']
  e['response_event_id']=response['eventId'];e['response']=response
  return self.reply(201,{'success':True,'data':response})
def server():
 s=ThreadingHTTPServer(('127.0.0.1',0),Fixture);s.events=[];s.counter=0;s.store={};s.owners={};s.lock=threading.RLock();return s
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--state',type=Path,required=True);a=p.parse_args();assert not a.state.exists()
 s=server();a.state.write_text(json.dumps({'bind':'127.0.0.1','port':s.server_port,'fixture':'SYNTHETIC_TEST_DATA'}));print('Owned E10A port',s.server_port,flush=True)
 try:s.serve_forever()
 except KeyboardInterrupt:pass
 finally:s.server_close()
