from __future__ import annotations
import csv, datetime as dt, html, json, math, os, pathlib, sqlite3, sys, threading, time, urllib.parse, urllib.request, webbrowser
from zoneinfo import ZoneInfo
UTC=dt.timezone.utc
AU=149597870.7
LD=384400.0
MU=398600.4418
BASE=pathlib.Path(os.getenv("LOCALAPPDATA",str(pathlib.Path.home())))/"PlanetaryWatch"
BASE.mkdir(parents=True,exist_ok=True)
REPORTS=BASE/"reports"; REPORTS.mkdir(exist_ok=True)
DB=BASE/"watch.sqlite3"
JPL="https://ssd-api.jpl.nasa.gov/"
SOURCES={
 "CAD":(JPL+"cad.api",1800),
 "Sentry":(JPL+"sentry.api",600),
 "Scout":(JPL+"scout.api",300),
 "Fireball":(JPL+"fireball.api",3600),
 "ESA":("https://neo.ssa.esa.int/PSDB-portlet/download",1800)
}
def now(): return dt.datetime.now(UTC)
def stamp(): return now().isoformat()
def number(v):
 try:
  n=float(v)
  return n if math.isfinite(n) else None
 except (ValueError,TypeError): return None
def norm(v): return math.sqrt(sum(x*x for x in v))
def interp(a,b,t):
 # Cubic Hermite position and derivative, km and seconds.
 h=(b[0]-a[0])*86400
 if h<=0: raise ValueError("invalid interval")
 u=(t-a[0])*86400/h
 if not 0<=u<=1: raise ValueError("outside ephemeris")
 p=[]; v=[]
 for i in range(3):
  x,y=a[1+i],b[1+i]; f,g=a[4+i],b[4+i]
  p.append((2*u**3-3*u*u+1)*x+(u**3-2*u*u+u)*h*f+(-2*u**3+3*u*u)*y+(u**3-u*u)*h*g)
  v.append(((6*u*u-6*u)*x+(-6*u*u+6*u)*y)/h+(3*u*u-4*u+1)*f+(3*u*u-2*u)*g)
 return p,v
def rows(d):
 if not isinstance(d,dict) or "error" in d: raise ValueError(str(d)[:300])
 if "data" not in d:
  if str(d.get("count"))=="0": return []
  raise ValueError("missing data")
 items=d["data"]
 if not isinstance(items,list): raise ValueError("invalid data")
 if not items: return []
 if isinstance(items[0],dict): return items
 fields=d.get("fields")
 if not fields or any(len(r)!=len(fields) for r in items): raise ValueError("unexpected schema")
 return [dict(zip(fields,r)) for r in items]
def parse_vectors(d):
 text=d.get("result","")
 if "$$SOE" not in text or "$$EOE" not in text: raise ValueError(text[-350:] or "No ephemeris")
 out=[]
 for r in csv.reader(text.split("$$SOE")[1].split("$$EOE")[0].strip().splitlines()):
  if len(r)<8: continue
  vals=[number(r[0])]+[number(x) for x in r[2:8]]
  if any(x is None for x in vals): raise ValueError("Invalid vector")
  out.append(vals)
 if len(out)<2 or any(b[0]<=a[0] for a,b in zip(out,out[1:])): raise ValueError("Insufficient ephemeris")
 return out
def connect():
 c=sqlite3.connect(DB,timeout=20); c.execute("PRAGMA journal_mode=WAL")
 c.execute("CREATE TABLE IF NOT EXISTS snapshots (id INTEGER PRIMARY KEY, source TEXT, at TEXT, payload TEXT)")
 c.execute("CREATE INDEX IF NOT EXISTS snapshot_source ON snapshots(source,id)")
 return c
class Monitor:
 def __init__(self):
  self.lock=threading.RLock(); self.net=threading.Lock(); self.stop=threading.Event()
  self.data={}; self.status={}; self.changes=[]; self.next={}; self.pending=set(); self.details={}
  with connect() as c:
   for s in SOURCES:
    row=c.execute("SELECT at,payload FROM snapshots WHERE source=? ORDER BY id DESC LIMIT 1",(s,)).fetchone()
    if row:
     self.data[s]=json.loads(row[1]); self.status[s]={"at":row[0],"state":"cache"}
 def fetch(self,url,params=None,raw=False):
  with self.net:
   req=urllib.request.Request(url+("?" + urllib.parse.urlencode(params) if params else ""),headers={"User-Agent":"PlanetaryWatch/0.1 (desktop scientific monitor)","Accept":"*/*"})
   with urllib.request.urlopen(req,timeout=25) as f:
    b=f.read(25_000_001)
   if len(b)>25_000_000: raise ValueError("response too large")
   time.sleep(.3)
   txt=b.decode("utf-8-sig",errors="strict")
   return txt if raw else json.loads(txt)
 def get(self,s):
  url,_=SOURCES[s]
  if s=="CAD":
   return rows(self.fetch(url,{"date-min":now().date().isoformat(),"date-max":(now()+dt.timedelta(days=365)).date().isoformat(),"dist-max":"0.05","diameter":"true","fullname":"true","sort":"date"}))
  if s=="Sentry": return rows(self.fetch(url))
  if s=="Scout":
   d=self.fetch(url)
   if "data" not in d and "count" not in d: raise ValueError("Scout schema")
   return rows(d)
  if s=="Fireball": return rows(self.fetch(url,{"date-min":(now()-dt.timedelta(days=30)).date().isoformat()}))
  txt=self.fetch(url,{"file":"esa_risk_list"},raw=True)
  if not txt.strip() or "<html" in txt.lower() or "<!doctype" in txt.lower(): raise ValueError("ESA: unexpected response")
  return txt
 def refresh(self,s):
  try:
   data=self.get(s); at=stamp()
   with self.lock:
    old=self.data.get(s); changed=old is not None and old!=data
    if changed and s in ("CAD","Sentry","Scout"): self.details.clear()
    if changed:
     self.changes.insert(0,{"source":s,"at":at,"message":"Новая версия опубликованных данных; предыдущая сохранена в SQLite."})
     self.changes=self.changes[:100]
    if old!=data:
     with connect() as c: c.execute("INSERT INTO snapshots(source,at,payload) VALUES(?,?,?)",(s,at,json.dumps(data,ensure_ascii=False)))
    self.data[s]=data; self.status[s]={"at":at,"state":"ok"}
   self.next[s]=time.monotonic()+SOURCES[s][1]
  except Exception as e:
   with self.lock:
    old=self.status.get(s,{}); self.status[s]={"at":old.get("at"),"state":"error","error":str(e)[:300]}
   self.next[s]=time.monotonic()+max(300,SOURCES[s][1])
 def loop(self):
  while not self.stop.is_set():
   for s in SOURCES:
    if self.stop.is_set(): break
    if time.monotonic()>=self.next.get(s,0): self.refresh(s)
   try:
    local=now().astimezone(ZoneInfo("Asia/Yekaterinburg"))
    if local.hour>=9 and not (REPORTS/(local.date().isoformat()+".html")).exists(): self.report()
   except Exception: pass
   self.stop.wait(5)
 def state(self):
  with self.lock: return {"data":self.data.copy(),"status":self.status.copy(),"changes":self.changes[:],"at":stamp()}
 def request_detail(self,des,center="now"):
  des=str(des).strip()
  if not des or len(des)>80 or any(x in des for x in "\r\n;'\""): return {"error":"Недопустимое обозначение"}
  key=des+"|"+str(center)
  with self.lock:
   if key in self.pending: return {"state":"loading"}
   existing=self.details.get(key)
   if existing and time.monotonic()-existing.get("_clock",0)<900: return existing
   self.pending.add(key)
  threading.Thread(target=self._detail,args=(key,des,center),daemon=True).start()
  return {"state":"loading"}
 def _detail(self,key,des,center):
  result={"state":"ok","des":des,"at":stamp()}
  try:
   sb=self.fetch(JPL+"sbdb.api",{"sstr":des,"phys-par":"true","full-prec":"true","cov":"mat"})
   if "object" not in sb: raise ValueError(str(sb)[:400])
   result["sbdb"]=sb
   try:
    risk=self.fetch(JPL+"sentry.api",{"des":des})
    if "summary" in risk: result["sentry"]=risk
   except Exception as err: result["sentry_error"]=str(err)
  except Exception as e: result["sbdb_error"]=str(e)
  try:
   from astropy.time import Time
   mid=now()
   if center!="now":
    try: mid=dt.datetime.strptime(str(center),"%Y-%b-%d %H:%M").replace(tzinfo=UTC)
    except ValueError: pass
   start=Time(mid-dt.timedelta(days=2)).tdb.jd; end=Time(mid+dt.timedelta(days=2)).tdb.jd
   command=des+";"
   d=self.fetch("https://ssd.jpl.nasa.gov/api/horizons.api",{"format":"json","COMMAND":"'"+command+"'","OBJ_DATA":"'NO'","MAKE_EPHEM":"'YES'","EPHEM_TYPE":"'VECTORS'","CENTER":"'500@399'","START_TIME":"'JD"+str(start)+"'","STOP_TIME":"'JD"+str(end)+"'","STEP_SIZE":"'30 m'","OUT_UNITS":"'KM-S'","REF_PLANE":"'FRAME'","REF_SYSTEM":"'ICRF'","VEC_CORR":"'NONE'","VEC_TABLE":"'2'","CSV_FORMAT":"'YES'"})
   result["vectors"]=parse_vectors(d); result["frame"]="Earth center / ICRF / TDB / km, km/s"; result["jd_now"]=Time(now()).tdb.jd
  except Exception as e: result["vector_error"]=str(e)
  result["_clock"]=time.monotonic()
  with self.lock:
   self.details[key]=result; self.pending.discard(key)
 def report(self):
  st=self.state(); day=now().astimezone(ZoneInfo("Asia/Yekaterinburg")).date().isoformat()
  p=REPORTS/(day+".html"); j=REPORTS/(day+".json")
  with self.lock: detail={k:v for k,v in self.details.items()}
  st["ephemerides"]=detail
  j.write_text(json.dumps(st,ensure_ascii=False,indent=2),encoding="utf-8")
  esc=lambda x:html.escape(str(x))
  sections=[]
  for source,dat in st["data"].items():
   sections.append("<h2>"+esc(source)+"</h2><pre>"+esc(json.dumps(dat,ensure_ascii=False,indent=2) if not isinstance(dat,str) else dat)+"</pre>")
  p.write_text("<!doctype html><meta charset=utf-8><title>Planetary Watch</title><style>body{font:16px system-ui;max-width:1100px;margin:auto}pre{white-space:pre-wrap;overflow-wrap:anywhere}</style><h1>Planetary Watch — "+day+"</h1><p>Исследовательский монитор. Не официальное предупреждение. Время UTC: "+esc(st["at"])+"</p><h2>Свежесть</h2><pre>"+esc(json.dumps(st["status"],ensure_ascii=False,indent=2))+"</pre><h2>Формулы</h2><p>d=√(x²+y²+z²), v=√(vx²+vy²+vz²), a=398600.4418/d² км/с²; d_LD=d/384400, d_AU=d/149597870.7; интерполяция кубическая Эрмита между опубликованными векторами Horizons; не является независимым интегратором.</p>"+"".join(sections)+"<h2>Векторы и параметры выбранных объектов</h2><pre>"+esc(json.dumps(detail,ensure_ascii=False,indent=2))+"</pre>",encoding="utf-8")
  return str(p)
class API:
 def __init__(self,m): self.m=m
 def state(self): return self.m.state()
 def detail(self,des,center="now"): return self.m.request_detail(des,center)
 def refresh(self):
  for s in SOURCES: self.m.next[s]=0
  return True
 def report(self):
  p=self.m.report(); webbrowser.open(pathlib.Path(p).as_uri()); return p
 def open_source(self,name):
  urls={"ESA":"https://neo.ssa.esa.int/imminent-impactors","Sentry":"https://cneos.jpl.nasa.gov/sentry/","Scout":"https://cneos.jpl.nasa.gov/scout/","CAD":"https://cneos.jpl.nasa.gov/ca/","Fireball":"https://cneos.jpl.nasa.gov/fireballs/"}
  if name in urls: webbrowser.open(urls[name])
HTML=r"""<!doctype html><html lang="ru"><meta charset="utf-8"><title>Planetary Watch</title><style>
:root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;background:#090f19;color:#d8e6f3;font:14px 'Segoe UI',sans-serif}header{display:flex;gap:18px;align-items:center;padding:18px 25px;border-bottom:1px solid #24364a}h1{font-size:21px;letter-spacing:2px;margin:0;color:#8be4f2}small,.muted{color:#8ca1b7}button,input,select{background:#17283b;color:#dce9f5;border:1px solid #334a60;padding:8px 12px;border-radius:6px;font:inherit}button{cursor:pointer}button:hover{border-color:#65cde0}main{display:grid;grid-template-columns:minmax(350px,42%) 1fr;gap:12px;padding:12px;height:calc(100vh - 80px)}section{min-width:0;background:#101a28;border:1px solid #23364a;border-radius:9px;padding:14px;overflow:auto}nav{display:flex;gap:5px;flex-wrap:wrap}h2{font-size:15px;color:#9cdce8}table{border-collapse:collapse;width:100%;font-size:12px}td,th{padding:8px;text-align:left;border-bottom:1px solid #26364a}tr[data-row]{cursor:pointer}tr[data-row]:hover{background:#1e3449}canvas{width:100%;height:360px;display:block;background:#050a12;border:1px solid #263c51;border-radius:7px;touch-action:none}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:11px}#status{display:flex;gap:8px;flex-wrap:wrap;margin:10px 0}.chip{font-size:11px;border:1px solid #395168;border-radius:5px;padding:4px 7px}.ok{color:#85d5b2}.error{color:#f2b570}.metrics{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px}.metric{background:#172638;padding:9px;border-radius:5px;overflow-wrap:anywhere}a{color:#8be4f2}#list{max-height:65vh;overflow:auto}#scrub{width:100%}label{display:block;margin:8px 0} .wide{flex:1} @media(max-width:850px){main{grid-template-columns:1fr;height:auto}#list{max-height:400px}}
</style><header><svg width="36" height="36" viewBox="0 0 64 64"><circle cx="32" cy="32" r="20" fill="#23638e"/><ellipse cx="32" cy="32" rx="30" ry="12" fill="none" stroke="#81ecf4" stroke-width="3" transform="rotate(-35 32 32)"/><circle cx="53" cy="17" r="5" fill="#f2b66d"/></svg><div class="wide"><h1>PLANETARY WATCH</h1><small>Исследовательский монитор · v0.1</small></div><span id="clock"></span><button onclick="api.refresh()">Обновить</button><button onclick="api.report()">Отчёт HTML</button></header><main><section><nav id="tabs"></nav><div id="status"></div><p class="muted">Проверяемые источники, а не непрерывная телеметрия. Отсутствие записей не доказывает отсутствие угрозы.</p><input id="search" placeholder="Название / обозначение" style="width:100%"><h2 id="count"></h2><div id="list"></div><h2>Журнал обновлений</h2><pre id="changes"></pre></section><section><h2 id="title">Выберите объект из списка</h2><div id="detailstatus" class="muted">Векторы загружаются по запросу.</div><nav><button onclick="load('now')">Текущая эпоха ±2 суток</button><button onclick="load(selected.cd||'now')">Эпоха сближения ±2 суток</button><button onclick="live=true">Текущее время</button><button onclick="api.open_source('ESA')">Официальные коридоры ESA ↗</button></nav><p class="muted">ICRF, центр Земли, TDB. Движение по опубликованным вектрам Horizons (гравитация учитывается поставщиком). Мышь: вращение; колесо: масштаб. Размер Земли в масштабе орбит.</p><canvas id="cv"></canvas><label>Расчётная эпоха <span id="epoch"></span><input id="scrub" type="range" min="0" max="1000" value="500"></label><div id="metrics" class="metrics"></div><h2>Формулы и подстановки</h2><pre id="math"></pre><h2>Орбитальные и физические параметры (SBDB)</h2><pre id="physical"></pre><h2>Опубликованный риск / координаты</h2><pre id="risk">Выберите объект.</pre></section></main><script>
let api, st={data:{},status:{}}, tab='CAD', selected={}, detail=null, key='now', live=true, generation=0, received=0, yaw=.4,pitch=.5,zoom=1,drag=null, lastDetail=0;
const $=x=>document.getElementById(x),esc=x=>String(x??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),fmt=(x,d=5)=>Number.isFinite(x)?x.toLocaleString('ru-RU',{maximumFractionDigits:d}):'—';
const tabs=['CAD','Sentry','Scout','ESA','Fireball'];
$('tabs').innerHTML=tabs.map(t=>'<button data-tab="'+t+'">'+t+'</button>').join('');
$('tabs').onclick=e=>{if(e.target.dataset.tab){tab=e.target.dataset.tab;render()}};$('search').oninput=render;
function designation(r){return r.des||r.objectName||r.object||r.fullname||r.name||''}
function render(){
 $('status').innerHTML=tabs.map(s=>{let q=st.status[s]||{};return '<span class="chip '+esc(q.state)+'" title="'+esc(q.error||q.at||'нет данных')+'">'+s+' '+esc(q.state||'ожидание')+' '+esc(q.at?.slice(11,16)||'')+'</span>'}).join('');
 let a=st.data[tab],search=$('search').value.toLowerCase();$('changes').textContent=(st.changes||[]).map(x=>x.at+' '+x.source+' '+x.message).join('\n');
 if(typeof a==='string'){$('count').textContent='ESA — исходная таблица';$('list').innerHTML='<pre>'+esc(a.split('\n').filter(r=>r.toLowerCase().includes(search)).join('\n'))+'</pre>';return}
 a=Array.isArray(a)?a:[];let items=a.filter(r=>JSON.stringify(r).toLowerCase().includes(search));$('count').textContent=tab+' · '+items.length+' / '+a.length+' записей';
 let cols=tab==='CAD'?['des','cd','dist','v_rel','diameter']:tab==='Sentry'?['des','range','ip','ts_max','diameter']:tab==='Scout'?['objectName','rating','caDist','lastRun']:['date','lat','lon','energy'];
 $('list').innerHTML='<table><thead><tr>'+cols.map(c=>'<th>'+esc(c)+'</th>').join('')+'</tr></thead><tbody>'+items.map((r,i)=>'<tr data-row="'+i+'">'+cols.map(c=>'<td>'+esc(r[c])+'</td>').join('')+'</tr>').join('')+'</tbody></table>';
 $('list').onclick=e=>{let tr=e.target.closest('[data-row]');if(!tr)return;selected=items[Number(tr.dataset.row)];$('title').textContent=designation(selected)||'Запись';$('risk').textContent=JSON.stringify(selected,null,2)+'\n\nМесто падения: не определено. Проверяйте официальные коридоры. Номинальная траектория не определяет вероятность столкновения.';if(tab!=='Fireball')load('now');else{generation++;detail=null;$('detailstatus').textContent='Исторический болид; орбита не запрашивается.'}};
}
async function load(center){if(!designation(selected))return;key=center;detail=null;live=true;let id=++generation,des=designation(selected);$('detailstatus').textContent='Запрос эфемерид и SBDB: '+des;
 try{for(let i=0;i<100;i++){let d=await api.detail(des,center);if(id!==generation)return;if(d.state!=='loading'){detail=d;received=performance.now();lastDetail=Date.now();$('detailstatus').textContent=d.error||d.vector_error||('Horizons: '+d.at+' · '+d.frame);$('physical').textContent=JSON.stringify(d.sbdb||d.sbdb_error||{},null,2);$('risk').textContent=JSON.stringify({record:selected,sentry:d.sentry||d.sentry_error||'Нет опубликованных подробностей'},null,2)+'\nКоординаты падения: не определены; официальные карты ESA доступны по ссылке.';return}await new Promise(r=>setTimeout(r,1000))}}catch(e){$('detailstatus').textContent=String(e)}}
const cv=$('cv'),ctx=cv.getContext('2d');cv.onpointerdown=e=>{drag=[e.clientX,e.clientY];cv.setPointerCapture(e.pointerId)};cv.onpointermove=e=>{if(drag){yaw+=(e.clientX-drag[0])*.01;pitch+=(e.clientY-drag[1])*.01;drag=[e.clientX,e.clientY]}};cv.onpointerup=()=>drag=null;cv.onwheel=e=>{e.preventDefault();zoom=Math.max(.1,Math.min(200,zoom*Math.exp(-e.deltaY*.001)))};
$('scrub').oninput=()=>live=false;
function hermite(a,b,t){let h=(b[0]-a[0])*86400,u=(t-a[0])*86400/h,p=[],v=[];for(let i=1;i<=3;i++){let x=a[i],y=b[i],f=a[i+3],g=b[i+3];p.push((2*u**3-3*u*u+1)*x+(u**3-2*u*u+u)*h*f+(-2*u**3+3*u*u)*y+(u**3-u*u)*h*g);v.push(((6*u*u-6*u)*x+(-6*u*u+6*u)*y)/h+(3*u*u-4*u+1)*f+(3*u*u-2*u)*g)}return[p,v]}
let last=0;
function draw(tick){requestAnimationFrame(draw);let w=cv.clientWidth,h=cv.clientHeight;if(cv.width!==w||cv.height!==h){cv.width=w;cv.height=h}ctx.fillStyle='#050a12';ctx.fillRect(0,0,w,h);let a=detail?.vectors;if(!a?.length){ctx.fillStyle='#8ca1b7';ctx.fillText('Нет доступных векторов',20,30);if(tick-last>500){$('metrics').innerHTML='';$('math').textContent='';last=tick}return}
 let lo=a[0][0],hi=a[a.length-1][0],t=live?detail.jd_now+(performance.now()-received)/86400000:lo+(hi-lo)*Number($('scrub').value)/1000;
 let scale=zoom*Math.min(w,h)*.43/Math.max(6378,...a.map(r=>Math.hypot(...r.slice(1,4))));
 function proj(p){let x=p[0]*Math.cos(yaw)-p[1]*Math.sin(yaw),y=p[0]*Math.sin(yaw)+p[1]*Math.cos(yaw);return[w/2+x*scale,h/2+(y*Math.cos(pitch)-p[2]*Math.sin(pitch))*scale]}
 ctx.strokeStyle='#64d5e6';ctx.lineWidth=1.5;ctx.beginPath();a.forEach((r,i)=>{let p=proj(r.slice(1,4));i?ctx.lineTo(...p):ctx.moveTo(...p)});ctx.stroke();
 ctx.fillStyle='#3479a8';ctx.beginPath();ctx.arc(w/2,h/2,Math.max(2,6378*scale),0,Math.PI*2);ctx.fill();ctx.fillStyle='#d6e9f8';ctx.fillText('Земля',w/2+8,h/2+14);
 if(t<lo||t>hi){if(tick-last>500){$('epoch').textContent='Вне окна; используйте ползунок';$('metrics').innerHTML='';$('math').textContent='Экстраполяция отключена.';last=tick}return}
 let i=a.findIndex(r=>r[0]>=t);i=Math.max(1,i);let [p,v]=hermite(a[i-1],a[i],t),xy=proj(p);ctx.fillStyle='#ffb96d';ctx.beginPath();ctx.arc(...xy,5,0,Math.PI*2);ctx.fill();ctx.fillText('Объект',xy[0]+8,xy[1]);
 if(tick-last>500){last=tick;if(live)$('scrub').value=(t-lo)/(hi-lo)*1000;$('epoch').textContent='JD '+t.toFixed(8)+' TDB';let d=Math.hypot(...p),speed=Math.hypot(...v),rad=d?p.reduce((s,x,i)=>s+x*v[i],0)/d:NaN,g=398600.4418/d**2;let fields=[['Расстояние, км',fmt(d,2)],['LD / AU',fmt(d/384400)+' / '+fmt(d/149597870.7,8)],['Скорость, км/с',fmt(speed)],['Радиальная скорость, км/с',fmt(rad)],['Ускорение Земли, м/с²',fmt(g*1000,10)],['Вектор X Y Z, км',p.map(x=>fmt(x,2)).join(' / ')],['VX VY VZ, км/с',v.map(x=>fmt(x)).join(' / ')]];$('metrics').innerHTML=fields.map(x=>'<div class="metric"><small>'+esc(x[0])+'</small><br>'+esc(x[1])+'</div>').join('');$('math').textContent='d = √(x²+y²+z²) = √('+p.map(x=>x.toFixed(3)+'²').join(' + ')+') = '+fmt(d)+' км\nv = √(vx²+vy²+vz²) = '+fmt(speed)+' км/с\nv_r = (r·v)/d = '+fmt(rad)+' км/с\n a_E = μ_E/d² = 398600.4418 / '+d.toFixed(3)+'² = '+g.toExponential(7)+' км/с²\nИнтерполяция Эрмита: r(u)=(2u³−3u²+1)r₀+(u³−2u²+u)hv₀+(−2u³+3u²)r₁+(u³−u²)hv₁.\nВращение и координаты падения неизвестны, если не опубликованы.'}}
requestAnimationFrame(draw);
window.addEventListener('pywebviewready',()=>{api=window.pywebview.api;async function poll(){try{st=await api.state();render();if(lastDetail&&Date.now()-lastDetail>900000&&designation(selected)){lastDetail=Date.now();load(key)}}catch(e){$('detailstatus').textContent=String(e)}setTimeout(poll,3000)}poll();setInterval(()=>$('clock').textContent=new Date().toLocaleString('ru-RU',{timeZone:'Asia/Yekaterinburg'})+' Уфа',1000)});
</script></html>"""
def icon():
 from PIL import Image,ImageDraw
 im=Image.new("RGBA",(256,256),(0,0,0,0)); d=ImageDraw.Draw(im)
 d.rounded_rectangle((8,8,248,248),radius=55,fill="#101e30",outline="#3b647d",width=5)
 d.ellipse((65,65,191,191),fill="#256c99",outline="#7ed9e8",width=4)
 ring=Image.new("RGBA",im.size); rd=ImageDraw.Draw(ring);rd.ellipse((24,88,232,168),outline="#8be4f2",width=8);ring=ring.rotate(35,resample=Image.Resampling.BICUBIC)
 im.alpha_composite(ring);d=ImageDraw.Draw(im);d.ellipse((197,52,225,80),fill="#f4b76c")
 im.save("planetary.ico",sizes=[(16,16),(32,32),(48,48),(64,64),(128,128),(256,256)])
def selftest():
 assert rows({"fields":["des","dist"],"data":[["A","1"]]})==[{"des":"A","dist":"1"}]
 assert rows({"count":"0"})==[]
 a=[0,1,2,3,1,0,0];b=[1/86400,2,2,3,1,0,0];p,v=interp(a,b,0.5/86400);assert abs(p[0]-1.5)<1e-9 and abs(v[0]-1)<1e-9
 d={"result":"$$SOE\n2450000, date,1,2,3,4,5,6,\n2450001,date,2,3,4,5,6,7,\n$$EOE"};assert len(parse_vectors(d))==2
 assert abs(MU/6378**2*1000-9.8)<.1
 print("PASS: schema, empty result, Hermite, Horizons CSV, gravity")
if __name__=="__main__":
 if "--icon" in sys.argv: icon();sys.exit()
 if "--self-test" in sys.argv: selftest();sys.exit()
 import webview
 m=Monitor();threading.Thread(target=m.loop,daemon=True).start()
 win=webview.create_window("PLANETARY WATCH",html=HTML,js_api=API(m),width=1280,height=850,min_size=(840,600))
 try: webview.start(gui="edgechromium" if sys.platform=="win32" else None)
 finally: m.stop.set()
