"""Owned E09 fixture. Loopback, synthetic contract only, no forwarding/database/dispatch."""
import sys
sys.dont_write_bytecode=True
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import argparse,json,time,threading
from pathlib import Path
TOKEN='e09-synthetic-access-not-a-jwt'
class Fixture(BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def reply(self,code,value,raw=False):
  body=value.encode() if raw else json.dumps(value).encode();assert len(body)<=4096
  self.send_response(code);self.send_header('Content-Type','text/plain' if raw else 'application/json');self.send_header('Content-Length',str(len(body)));self.send_header('Connection','close');self.end_headers();self.close_connection=True
  try:self.wfile.write(body)
  except (BrokenPipeError,ConnectionResetError):pass
 def do_GET(self):
  self.server.events.append({'method':'GET','path':self.path,'host':self.headers.get('Host')})
  if self.path=='/demo/info':return self.reply(200,'P5-E09-SYNTHETIC-NO-DISPATCH',True)
  self.reply(404,{})
 def do_POST(self):
  size=int(self.headers.get('Content-Length','0'))
  if not 0<=size<=4096:return self.reply(413,{})
  try:body=json.loads(self.rfile.read(size))
  except Exception:return self.reply(400,{})
  event={'method':'POST','path':self.path,'host':self.headers.get('Host'),'fault':self.headers.get('X-Demo-Fault','ok')};self.server.events.append(event)
  if self.path=='/auth/login':
   if body.get('email')!='fixture@example.invalid' or body.get('password')!='fixture-only-password':return self.reply(401,{})
   return self.reply(200,{'success':True,'data':{'accessToken':TOKEN,'refreshToken':'e09-synthetic-refresh-not-a-jwt','isNewUser':False}})
  if self.path!='/sos':return self.reply(404,{})
  if self.headers.get('Authorization')!='Bearer '+TOKEN:return self.reply(401,{})
  if body.get('condition')!='SYNTHETIC_TEST_DATA' or not 1<=body.get('adults',0)<=10 or not 16.04<=body.get('latitude',0)<=16.06 or not 108.19<=body.get('longitude',0)<=108.21:return self.reply(400,{})
  fault=event['fault']
  if fault in ['401','403','500']:return self.reply(int(fault),{})
  if fault=='timeout':time.sleep(3)
  self.server.counter+=1
  response=dict(body,eventId='SYNTHETIC-E09-'+str(self.server.counter),createdAt=1794268800000,status='ACTIVE',transportMode='ONLINE')
  event.update(adults=body['adults'],synthetic=True,latitude=body['latitude'],longitude=body['longitude'],response_event_id=response['eventId'])
  self.reply(201,{'success':True,'data':response})
def server():
 s=ThreadingHTTPServer(('127.0.0.1',0),Fixture);s.events=[];s.counter=0;return s
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--state',type=Path,required=True);args=p.parse_args();assert not args.state.exists(),'Refuse overwriting a state file'
 s=server();args.state.write_text(json.dumps({'bind':'127.0.0.1','port':s.server_port,'fixture':'SYNTHETIC_TEST_DATA','pid':__import__('os').getpid()}));print('Owned E09 fixture port',s.server_port,flush=True)
 try:s.serve_forever()
 except KeyboardInterrupt:pass
 finally:s.server_close()
