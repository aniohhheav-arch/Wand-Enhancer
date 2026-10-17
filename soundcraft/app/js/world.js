'use strict';
/* The living studio: the window follows your local clock and season, a wall clock shows the real time,
   cars pass at night, shooting stars and parcel deliveries happen now and then; studio levels and a daily
   quest board reward making music and playing; focus mode hides the fun so only the DAW is left.
   Progress never locks anything in Soundcraft. */
SKY.day=['#6fa8dc','#cfe4f2'];WEATHERS.live='Live (follows your clock and the season)';
function liveWeather(){if(PREF.weather!=='live')return PREF.weather;const d=new Date(),h=d.getHours(),m=d.getMonth(),winter=m===11||m<2;
  if(h>=21||h<6)return winter&&d.getDate()%2?'snow':'night';if(h<10)return 'morning';if(h<17)return 'day';return 'evening'}
/* ───────── progression ───────── */
const LEVELS=[[1,'Bedroom Producer'],[3,'Late-Night Tinkerer'],[5,'Bedroom Legend'],[10,'Underground Artist'],[15,'Beat Scientist'],[20,'Studio Architect'],[30,'Living Legend']];
const xpFor=l=>Math.round(60*Math.pow(l,1.45));
function progData(){if(!ARC.prog)arcLoad();const p=ARC.prog;p.xp=p.xp||0;p.daily=p.daily||{};return p}
function levelOf(xp){let l=1,need=xpFor(1);while(xp>=need){xp-=need;l++;need=xpFor(l)}return {level:l,into:xp,need}}
function titleOf(l){let t=LEVELS[0][1];LEVELS.forEach(([n,name])=>{if(l>=n)t=name});return t}
function addXP(n,why){const p=progData(),before=levelOf(p.xp).level;p.xp+=n;arcSave();const after=levelOf(p.xp).level;
  if(after>before)notify('Level '+after+'! You are now a '+titleOf(after)+'.');else if(why)notify('+'+n+' XP · '+why);renderQuestChip()}
function notify(msg){if(document.body.classList.contains('focus'))return;toast(msg)}
/* daily quests (the same three for everyone on a given day) */
const QUESTS=[['play','Play 40 notes on any instrument',40,15],['record','Record a phrase into a clip',1,25],['export','Export a song or a loop',1,30],['modulate','Connect a modulation source to a control',1,20],
  ['preview','Preview 5 sounds in the Sounds browser',5,10],['sample','Import or record a sample',1,20],['game','Finish 2 arcade games',2,20],['fish','Catch 3 fish',3,15],['beat','Score 10,000 points in Beat Blaster',1,25]];
function todayKey(){const d=new Date();return d.getFullYear()+'-'+(d.getMonth()+1)+'-'+d.getDate()}
function todaysQuests(){const k=todayKey();let h=0;for(const ch of k)h=(h*31+ch.charCodeAt(0))>>>0;const pool=QUESTS.slice(),out=[];while(out.length<3){out.push(pool.splice(h%pool.length,1)[0]);h=(h*1103515245+12345)>>>0}return out}
function questProgress(id,n=1){const p=progData(),k=todayKey();if(p.daily.day!==k)p.daily={day:k,c:{},done:{}};const q=todaysQuests().find(x=>x[0]===id);
  p.daily.c[id]=(p.daily.c[id]||0)+n;if(q&&!p.daily.done[id]&&p.daily.c[id]>=q[2]){p.daily.done[id]=1;p.coins+=10;arcSave();addXP(q[3],'Quest done: '+q[1]+' (+10 coins)')}else arcSave();renderQuestChip()}
/* the app already calls quest(kind) at creative moments; those now feed the quest board and give a little XP */
const XP_SMALL={record:4,export:10,modulate:3,sample:4};
quest=function(kind){try{questProgress(kind);if(XP_SMALL[kind])addXP(XP_SMALL[kind])}catch(e){errLog.push('quest: '+e.message)}};
function renderQuestChip(){const b=$('#btnQuests');if(!b)return;const p=progData(),L=levelOf(p.xp),done=Object.keys((p.daily.day===todayKey()&&p.daily.done)||{}).length;b.textContent='Lv '+L.level+' · Quests '+done+'/3'}
function openQuests(){const p=progData(),L=levelOf(p.xp),k=todayKey(),d=p.daily.day===k?p.daily:{c:{},done:{}};
  $('#questBody').innerHTML='<div class="q-level"><b>Level '+L.level+' · '+esc(titleOf(L.level))+'</b><div class="q-bar"><i style="width:'+Math.round(L.into/L.need*100)+'%"></i></div><span class="hint">'+L.into+' / '+L.need+' XP to the next level · '+p.coins+' coins</span></div>'+
    '<h3>Today\'s quests</h3><ul class="list">'+todaysQuests().map(([id,txt,n,xp])=>{const c=Math.min(n,d.c[id]||0),ok=d.done[id];return '<li class="'+(ok?'got':'')+'"><span class="grow">'+(ok?'★ ':'☆ ')+esc(txt)+'</span><span class="hint">'+c+' / '+n+' · '+xp+' XP</span></li>'}).join('')+'</ul>'+
    '<h3>Levels</h3><ul class="list">'+LEVELS.map(([n,name])=>'<li class="'+(L.level>=n?'got':'')+'"><span class="grow">Level '+n+' · '+esc(name)+'</span></li>').join('')+'</ul>'+
    '<p class="hint">Levels are a little celebration of what you make and play. Nothing in Soundcraft is ever locked behind them.</p>';openModal('questModal')}
/* ───────── living room details (drawn on top of the room) ───────── */
const LIFE={cars:[],star:null,box:null,nextBox:performance.now()+1000*60*(4+Math.random()*6)};
function drawLife(dt,now){const W=liveWeather(),night=W==='night'||W==='snow'||W==='rain'||W==='storm',rm=reduced();
  // wall clock with the real local time
  const d=new Date(),cx=112,cy=24;rc.fillStyle='#2a1f1a';rc.beginPath();rc.arc(cx,cy,7,0,7);rc.fill();rc.fillStyle='#f4ead6';rc.beginPath();rc.arc(cx,cy,6,0,7);rc.fill();
  for(let i=0;i<12;i+=3){const a=i/12*Math.PI*2;px(cx+Math.round(Math.sin(a)*5),cy-Math.round(Math.cos(a)*5),1,1,'#8a6248')}
  const hand=(a,l,c)=>{rc.strokeStyle=c;rc.lineWidth=1;rc.beginPath();rc.moveTo(cx+0.5,cy+0.5);rc.lineTo(cx+0.5+Math.sin(a)*l,cy+0.5-Math.cos(a)*l);rc.stroke()};
  hand(((d.getHours()%12)+d.getMinutes()/60)/12*Math.PI*2,3,'#2c2858');hand(d.getMinutes()/60*Math.PI*2,5,'#2c2858');if(!rm)hand(d.getSeconds()/60*Math.PI*2,5,'#ef7a85');
  // cars passing behind the skyline at night (window spans x 18..70, street at y 56)
  if(night&&!rm){if(Math.random()<dt*0.25)LIFE.cars.push({x:Math.random()<0.5?16:72,v:(Math.random()<0.5?1:-1)*(10+Math.random()*12)});
    LIFE.cars.forEach(c=>{if(c.x<16)c.v=Math.abs(c.v);c.x+=c.v*dt;if(c.x>18&&c.x<69){px(c.x,55,3,1,'#2a2650');px(c.v>0?c.x+3:c.x-1,55,1,1,'#ffe39a');px(c.v>0?c.x-1:c.x+3,55,1,1,'#ef7a85')}});LIFE.cars=LIFE.cars.filter(c=>c.x>=14&&c.x<=74)}
  // a rare shooting star
  if(night&&!rm&&!LIFE.star&&Math.random()<dt*0.02)LIFE.star={x:66,y:16,t:0};
  if(LIFE.star){const s=LIFE.star;s.t+=dt;s.x-=dt*60;s.y+=dt*22;for(let i=0;i<5;i++)px(s.x+i,s.y-i*0.4,1,1,'rgba(255,240,200,'+(1-i/5)+')');if(s.x<20||s.y>50){LIFE.star=null;if(S.view==='studio'){notify('A shooting star! Make a wish (+5 XP)');addXP(5)}}}
  // parcel deliveries: click the box to open it
  if(!LIFE.box&&now>LIFE.nextBox&&S.view==='studio')LIFE.box={x:180,y:118,born:now};
  if(LIFE.box){const b=LIFE.box,bob=rm?0:Math.round(Math.sin(now/300)*0.5);rc.fillStyle='rgba(0,0,0,.25)';rc.fillRect(b.x-1,b.y+7,12,2);px(b.x,b.y+bob,10,8,'#c79a6a');px(b.x,b.y+bob,10,2,'#a87d52');px(b.x+4,b.y+bob,2,8,'#e8d4b0');if(!rm&&Math.floor(now/500)%2)px(b.x+4,b.y-4+bob,2,2,'#ffb454')}}
function openParcel(){const b=LIFE.box;if(!b)return false;LIFE.box=null;LIFE.nextBox=performance.now()+1000*60*(6+Math.random()*10);const coins=5+Math.floor(Math.random()*16);progData().coins+=coins;arcSave();addXP(8);
  const gifts=['a fresh pack of guitar picks','a tiny cactus for the desk','a cassette from a friend','fancy cat treats','a vintage headphone cable','a postcard from a fan'];toast('Delivery! Inside: '+gifts[Math.floor(Math.random()*gifts.length)]+' (+'+coins+' coins, +8 XP)');return true}
/* ───────── focus mode ───────── */
function setFocus(on){document.body.classList.toggle('focus',on);PREF.focus=on;savePrefs();const b=$('#btnFocus');if(b)b.setAttribute('aria-pressed',on);if(on){if(S.view==='studio')setView('arrange');if(PREF.amb){PREF.ambWasOn=1;PREF.amb=false;startAmbience()}}else if(PREF.ambWasOn){PREF.amb=true;PREF.ambWasOn=0;startAmbience()}}
function worldBoot(){progData();renderQuestChip();if(PREF.focus)setFocus(true)}
