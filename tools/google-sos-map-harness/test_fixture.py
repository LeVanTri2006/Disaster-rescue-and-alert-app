"""Host contract checks. Device tests separately prove exact production Android client."""
import sys
sys.dont_write_bytecode=True
import unittest,threading,json,urllib.request,urllib.error,uuid
from fixture import server,TOKENS
class ContractTest(unittest.TestCase):
 def setUp(self):
  self.s=server();self.t=threading.Thread(target=self.s.serve_forever,daemon=True);self.t.start();self.base='http://127.0.0.1:'+str(self.s.server_port)
 def tearDown(self):self.s.shutdown();self.s.server_close();self.t.join(5)
 def request(self,path,actor=None,body=None):
  headers={'Content-Type':'application/json'}
  if actor:headers['Authorization']='Bearer '+TOKENS[actor]
  req=urllib.request.Request(self.base+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
  try:
   with urllib.request.urlopen(req,timeout=2) as r:return r.status,json.load(r)
  except urllib.error.HTTPError as e:return e.code,None
 def create(self,actor='citizen'):
  code,r=self.request('/sos',actor,dict(adults=1,children=0,elderly=0,neededItems=[],condition='SYNTHETIC_TEST_DATA',description='SYNTHETIC_TEST_DATA host contract',latitude=16.051,longitude=108.203,urgency='HIGH',timestamp='2026-11-10T00:00:00Z'))
  self.assertEqual(201,code);return r['data']
 def test_detail_same_record_and_ownership(self):
  row=self.create();uuid.UUID(row['eventId']);self.assertEqual('ACTIVE',row['status'])
  for actor in ['citizen','rescuer']:self.assertEqual((200,{'success':True,'data':row}),self.request('/sos/'+row['eventId'],actor))
  self.assertEqual(403,self.request('/sos/'+row['eventId'],'citizen2')[0]);self.assertEqual(401,self.request('/sos/'+row['eventId'])[0]);self.assertEqual(404,self.request('/sos/'+str(uuid.uuid4()),'rescuer')[0])
 def test_active_conflict_and_multiple_independent_owners(self):
  a=self.create();b=self.create('citizen2');self.assertNotEqual(a['eventId'],b['eventId'])
  self.assertEqual(409,self.request('/sos','citizen',dict(a))[0])
  self.assertEqual([a,b],self.request('/sos/active','rescuer')[1]['data'])
 def test_unknown_paths_and_unauthorized_reads(self):
  self.assertEqual(401,self.request('/sos/active')[0]);self.assertEqual(404,self.request('/rescue-missions/my','rescuer')[0]);self.assertEqual(404,self.request('/production','rescuer',{})[0])
 def test_concurrent_duplicate_post_commits_only_once(self):
  body=dict(adults=1,children=0,elderly=0,condition='SYNTHETIC_TEST_DATA',description='SYNTHETIC_TEST_DATA concurrent',latitude=16.051,longitude=108.203)
  results=[];threads=[threading.Thread(target=lambda:results.append(self.request('/sos','citizen',body)[0])) for _ in range(6)]
  for t in threads:t.start()
  for t in threads:t.join(5)
  self.assertEqual([201]+[409]*5,sorted(results));self.assertEqual(1,len(self.s.store))
 def test_committed_timeout_is_visible_without_retry(self):
  import time,socket
  body=dict(adults=1,children=0,elderly=0,condition='SYNTHETIC_TEST_DATA',description='SYNTHETIC_TEST_DATA unknown outcome',latitude=16.051,longitude=108.203)
  req=urllib.request.Request(self.base+'/sos',data=json.dumps(body).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+TOKENS['citizen'],'X-Demo-Fault':'commit_timeout'})
  with self.assertRaises((TimeoutError,socket.timeout)):urllib.request.urlopen(req,timeout=.3)
  self.assertEqual(1,len(self.s.store));rows=self.request('/sos/active','rescuer')[1]['data'];self.assertEqual(list(self.s.store.values()),rows)
  self.assertEqual(1,len([e for e in self.s.events if e['method']=='POST' and e['path']=='/sos']))
if __name__=='__main__':
 import xml.etree.ElementTree as E,time
 class XmlResult(unittest.TextTestResult):
  def __init__(self,*a,**k):super().__init__(*a,**k);self.rows=[]
  def startTest(self,t):super().startTest(t);self.started=time.monotonic()
  def addSuccess(self,t):super().addSuccess(t);self.rows.append((t,None))
  def addFailure(self,t,e):super().addFailure(t,e);self.rows.append((t,self._exc_info_to_string(e,t)))
  def addError(self,t,e):super().addError(t,e);self.rows.append((t,self._exc_info_to_string(e,t)))
 r=unittest.TextTestRunner(resultclass=XmlResult,verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(ContractTest))
 root=E.Element('testsuite',name='P5M02FixtureHost',tests=str(r.testsRun),failures=str(len(r.failures)),errors=str(len(r.errors)),skipped=str(len(r.skipped)))
 for t,e in r.rows:
  c=E.SubElement(root,'testcase',classname='ContractTest',name=t._testMethodName)
  if e:E.SubElement(c,'failure').text=e
 if len(sys.argv)>1:E.ElementTree(root).write(sys.argv[1],encoding='utf-8',xml_declaration=True)
 sys.exit(0 if r.wasSuccessful() else 1)
