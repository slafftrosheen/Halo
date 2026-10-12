from pathlib import Path
import subprocess,sys,time,xml.etree.ElementTree as E
adb=r'C:\Users\slavd\AppData\Local\Android\Sdk\platform-tools\adb.exe'
def run(*args):
 return subprocess.run([adb,*args],capture_output=True,check=True).stdout
def inspect():
 time.sleep(1.5)
 for attempt in range(3):
  result=run('shell','uiautomator','dump','/sdcard/halo-ux.xml')
  if b'dumped' in result:break
  time.sleep(1)
 else:raise RuntimeError('UI dump unavailable; no stale tree accepted')
 data=run('exec-out','cat','/sdcard/halo-ux.xml');Path('.artifacts/phone-current.xml').write_bytes(data)
 for n in E.fromstring(data).iter('node'):
  label=n.get('text') or n.get('content-desc')
  if label:print(label,n.get('bounds'), 'enabled='+n.get('enabled',''))
 Path('.artifacts/phone-current.png').write_bytes(run('exec-out','screencap','-p'))
mode=sys.argv[1]
if mode=='route':run('shell','am','start','-n','com.aistudio.braintool.xkqzps/com.halo.app.MainActivity','--es','com.halo.app.extra.SCREEN',sys.argv[2])
elif mode=='tap':run('shell','input','tap',sys.argv[2],sys.argv[3])
elif mode=='swipe':run('shell','input','swipe',*sys.argv[2:])
elif mode=='back':run('shell','input','keyevent','4')
elif mode=='text':run('shell','input','text',sys.argv[2].replace(' ','%s'))
elif mode=='capture':
 target=Path(sys.argv[2]);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(run('exec-out','screencap','-p'));print(target);sys.exit(0)
inspect()
