'use strict';
/* Arcade: a small framework for minigames plus progression (coins, high scores, achievements).
   Games draw on one canvas overlay and get input (keyboard, and MIDI notes while a game is running).
   Progress is stored apart from projects. Games never start while recording and pause when the window
   loses focus or a dialog opens. Nothing in the DAW is ever locked behind progress. */
const ARC_KEY='soundcraft.arcade.v1';
const ARC={games:[],cur:null,state:'menu',paused:false,raf:0,t:0,prog:null,keys:new Set()};
function arcLoad(){let p=null;try{p=JSON.parse(read(ARC_KEY)||'null')}catch(e){}ARC.prog=Object.assign({coins:0,best:{},ach:{},plays:0},p||{})}
function arcSave(){store(ARC_KEY,JSON.stringify(ARC.prog))}
const ACHIEVEMENTS={first:['First game','Finish any arcade game'],combo50:['On fire','Reach a 50 combo in Beat Blaster'],fc:['Full combo','Finish a Beat Blaster song with no misses'],aRank:['A rank','Get an A or better in Beat Blaster'],own:['My song','Play Beat Blaster on your own song']};
function arcAchieve(id){if(ARC.prog.ach[id])return;ARC.prog.ach[id]=Date.now();ARC.prog.coins+=25;arcSave();toast('Achievement: '+ACHIEVEMENTS[id][0]+' (+25 coins)')}
function registerGame(g){ARC.games.push(g)}
/* ───────── shell ───────── */
function openArcade(){if(S.rec){toast('The arcade waits until recording has stopped.');return}arcLoad();$('#arcade').hidden=false;ARC.state='menu';ARC.cur=null;arcResize();renderArcMenu();cancelAnimationFrame(ARC.raf);ARC.raf=requestAnimationFrame(arcFrame)}
function closeArcade(){if(ARC.cur&&ARC.cur.exit)ARC.cur.exit();ARC.cur=null;$('#arcade').hidden=true;cancelAnimationFrame(ARC.raf)}
function renderArcMenu(){$('#arcMenu').hidden=false;$('#arcCoins').textContent=ARC.prog.coins+' coins';
  $('#arcList').innerHTML=ARC.games.map(g=>'<li class="arc-game"><div class="grow"><b>'+esc(g.name)+'</b><div class="hint">'+esc(g.desc)+'</div><div class="hint">'+g.modes().map(m=>{const b=ARC.prog.best[g.id+':'+m.id];return esc(m.label)+(b?' · best '+b.score+' ('+b.grade+')':'')}).join('<br>')+'</div></div><div class="arc-modes">'+g.modes().map(m=>'<button class="btn" data-g="'+g.id+'" data-m="'+esc(m.id)+'">'+esc(m.short||m.label)+'</button>').join('')+'</div></li>').join('');
  $('#arcAch').innerHTML=Object.entries(ACHIEVEMENTS).map(([k,[n,d]])=>'<li class="'+(ARC.prog.ach[k]?'got':'')+'"><b>'+(ARC.prog.ach[k]?'★ ':'☆ ')+esc(n)+'</b> <span class="hint">'+esc(d)+'</span></li>').join('')}
function startGame(id,mode){const g=ARC.games.find(x=>x.id===id);if(!g)return;if(S.rec){toast('The arcade waits until recording has stopped.');return}
  if(!ensureAudio())return;$('#arcMenu').hidden=true;ARC.cur=g;ARC.state='play';ARC.paused=false;ARC.prog.plays++;g.start(mode);$('#arcade').focus()}
function finishGame(res){const g=ARC.cur;ARC.state='results';const key=g.id+':'+res.mode,b=ARC.prog.best[key];const isBest=!b||res.score>b.score;if(isBest)ARC.prog.best[key]={score:res.score,grade:res.grade,date:Date.now()};
  const coins=Math.max(1,Math.round(res.score/1000));ARC.prog.coins+=coins;arcSave();arcAchieve('first');(res.ach||[]).forEach(arcAchieve);ARC.result=Object.assign({coins,isBest},res)}
function arcPause(on){if(ARC.state!=='play'||!ARC.cur)return;if(on===ARC.paused)return;ARC.paused=on;if(ARC.cur.pause)ARC.cur.pause(on)}
function arcResize(){const cv=$('#arcCanvas'),dpr=window.devicePixelRatio||1;cv.width=Math.round(cv.clientWidth*dpr);cv.height=Math.round(cv.clientHeight*dpr)}
function arcFrame(now){ARC.raf=requestAnimationFrame(arcFrame);const cv=$('#arcCanvas'),c=cv.getContext('2d'),dpr=window.devicePixelRatio||1,W=cv.width/dpr,H=cv.height/dpr;c.setTransform(dpr,0,0,dpr,0,0);
  const dt=Math.min(0.05,(now-(ARC.t||now))/1000);ARC.t=now;c.fillStyle='#141330';c.fillRect(0,0,W,H);
  if(ARC.state==='play'&&ARC.cur){if(!ARC.paused)ARC.cur.update(dt);ARC.cur.draw(c,W,H);if(ARC.paused)arcBanner(c,W,H,'Paused','Space or Enter resumes · Esc quits to the arcade menu')}
  else if(ARC.state==='results'&&ARC.result){const r=ARC.result;arcBanner(c,W,H,'Grade '+r.grade+(r.isBest?' · new best!':''),'Score '+r.score+' · max combo '+r.maxCombo+' · perfect '+r.perfect+' · good '+r.good+' · miss '+r.miss+' · +'+r.coins+' coins','Enter: play again · Esc: arcade menu')}
  else{c.fillStyle='rgba(255,180,84,.08)';for(let i=0;i<30;i++){const x=(i*97+now/40)%W,y=(i*53)%H;c.fillRect(x,y,3,3)}}}
function arcBanner(c,W,H,a,b,d){c.fillStyle='rgba(10,9,30,.78)';c.fillRect(0,H/2-90,W,180);c.textAlign='center';c.fillStyle='#ffb454';c.font='42px VT323,monospace';c.fillText(a,W/2,H/2-30);
  c.fillStyle='#f4ead6';c.font='20px VT323,monospace';c.fillText(b,W/2,H/2+10);if(d)c.fillText(d,W/2,H/2+44);c.textAlign='left'}
/* input */
function arcKey(e,down){if($('#arcade').hidden)return false;const k=e.key.toLowerCase();
  if(down&&k==='escape'){e.preventDefault();if(ARC.state==='play'&&!ARC.paused){arcPause(true);return true}if(ARC.state==='play'&&ARC.paused){if(ARC.cur.exit)ARC.cur.exit();ARC.state='menu';renderArcMenu();return true}if(ARC.state==='results'){ARC.state='menu';renderArcMenu();return true}closeArcade();return true}
  if(down&&ARC.state==='play'&&ARC.paused&&(k===' '||k==='enter')){e.preventDefault();arcPause(false);return true}
  if(down&&ARC.state==='results'&&k==='enter'){e.preventDefault();startGame(ARC.cur.id,ARC.result.mode);return true}
  if(ARC.state==='play'&&!ARC.paused&&ARC.cur){if(e.repeat){e.preventDefault();return true}const used=down?ARC.cur.keyDown&&ARC.cur.keyDown(k):ARC.cur.keyUp&&ARC.cur.keyUp(k);if(used){e.preventDefault();return true}}
  return ARC.state==='play'}
window.addEventListener('keydown',e=>{if(arcKey(e,true))e.stopImmediatePropagation()},true);
window.addEventListener('keyup',e=>{if(arcKey(e,false))e.stopImmediatePropagation()},true);
window.addEventListener('blur',()=>arcPause(true));window.addEventListener('resize',()=>{if(!$('#arcade').hidden)arcResize()});
/* MIDI notes go to the running game instead of the instruments */
function arcMidiNote(n,v,on){if($('#arcade').hidden||ARC.state!=='play'||ARC.paused||!ARC.cur||!ARC.cur.midiNote)return false;ARC.cur.midiNote(n,v,on);return true}
$('#arcList').addEventListener('click',e=>{const b=e.target.closest('[data-g]');if(b)startGame(b.dataset.g,b.dataset.m)});
$('#arcClose').onclick=closeArcade;$('#btnArcade').onclick=openArcade;

/* ───────── Beat Blaster ───────── */
const BB_LANES=['d','f','j','k'],BB_COL=['#e8a0bb','#ffb454','#8fcaa2','#79c3e0'],BB_WIN={perfect:0.045,good:0.1};
/* Build a chart from a list of {t (seconds from start), n (note)}: thin it by difficulty, assign lanes by pitch. */
function bbChart(events,diff){const minGap={easy:0.42,normal:0.22,hard:0.11}[diff];const out=[];let last=-9;
  events.sort((a,b)=>a.t-b.t).forEach(e=>{if(e.t-last<minGap)return;last=e.t;out.push({t:e.t,lane:diff==='easy'?(e.n%2)*3:((e.n%4)+4)%4,hit:null})});return out}
function bbPractice(diff,bpm){const beat=60/bpm,ev=[],bars=16;for(let b=0;b<bars;b++)for(let s=0;s<16;s++){const t=(b*16+s)*beat/4,k=s%4===0,sn=s===4||s===12,h=s%2===0;
  const r=Math.sin((b*16+s)*12.9898)*43758.5453%1;if(k||sn||(h&&Math.abs(r)>0.45))ev.push({t,n:k?0:sn?1:s%8<4?2:3,drum:k?36:sn?38:42})}return ev}
/* chart from the current song: the selected track (or the first drum track) over the whole arrangement */
function bbSongEvents(){const sd=60/S.bpm/4,end=songEnd()*16,tr=selTrack()&&!isAudio(selTrack())&&selTrack().clips.length?selTrack():S.tracks.find(t=>ENG[t.engine].drum&&t.clips.length)||S.tracks.find(t=>t.clips.length&&!isAudio(t));if(!tr)return {ev:[],tr:null};
  const ev=[];for(let k=0;k<end;k++){const c=clipAt(tr,k);if(!c)continue;const pat=tr.patterns.find(p=>p.id===c.pat);if(!pat)continue;const L=Math.round(pat.len*16),ps=((k-Math.round(c.start*16))%L+L)%L;
    pat.notes.forEach(n=>{if(n.s<L&&Math.floor(n.s)===ps)ev.push({t:(k+(n.s-ps))*sd,n:n.p})})}return {ev,tr}}
registerGame({id:'beatblaster',name:'Beat Blaster',desc:'Hit the notes as they reach the line: D F J K, or pads/keys on your MIDI controller. Perfect ±45 ms, good ±100 ms. Combos multiply your score.',
  modes(){return [{id:'practice-easy',label:'Practice beat · easy',short:'Practice easy'},{id:'practice-normal',label:'Practice beat · normal',short:'Practice normal'},{id:'practice-hard',label:'Practice beat · hard',short:'Practice hard'},{id:'song-normal',label:'Your song ('+(LIB[curId]?LIB[curId].title:'current project')+') · normal',short:'My song'},{id:'song-hard',label:'Your song · hard',short:'My song hard'}]},
  start(mode){const [src,diff]=mode.split('-');this.mode=mode;this.diff=diff;this.score=0;this.combo=0;this.maxCombo=0;this.perfect=0;this.good=0;this.miss=0;this.fx=[];this.lit=[0,0,0,0];this.src=src;
    const lead=2.0;this.t0=ctx.currentTime+lead;
    if(src==='song'){stop();const {ev,tr}=bbSongEvents();if(!ev.length){toast('Your song has no notes yet, so a practice beat starts instead.');return this.start('practice-'+diff)}
      this.chart=bbChart(ev,diff);this.song=true;S.cursor=0;const was=S.loop.on;this.restoreLoop=was;S.loop.on=false;this.playAt=setTimeout(()=>{if(ARC.cur===this&&!ARC.paused){play();this.t0=stepLog.length?stepLog[0][1]:ctx.currentTime}},lead*1000-80);this.trackName=tr.name}
    else{const bpm={easy:92,normal:108,hard:128}[diff],ev=bbPractice(diff,bpm);this.chart=bbChart(ev,diff);this.song=false;this.kit=newTrack('drums-circuit');buildTrack(this.kit);applyMixOne&&applyMixOne(this.kit);
      ev.forEach(e=>{const h=trigger(this.kit,e.drum,0.8,this.t0+e.t);if(h)h.off(this.t0+e.t+0.2)})}
    this.end=(this.chart.length?this.chart[this.chart.length-1].t:4)+2},
  now(){return ctx.currentTime-this.t0},
  pause(on){if(on){this.pausedAt=ctx.currentTime;if(this.song)stop();else if(this.kit)disposeTrack(this.kit)}else{/* resuming restarts from the menu: rhythm games cannot resume mid-song cleanly */ARC.state='menu';this.exit();renderArcMenu()}},
  exit(){clearTimeout(this.playAt);if(this.song){stop();S.loop.on=this.restoreLoop!=null?this.restoreLoop:S.loop.on}if(this.kit){disposeTrack(this.kit);this.kit=null}},
  judge(lane){const t=this.now();let best=null,bd=1;for(const n of this.chart){if(n.hit||n.lane!==lane)continue;const d=Math.abs(n.t-t);if(d<bd){bd=d;best=n}if(n.t-t>0.3)break}
    this.lit[lane]=0.15;if(!best||bd>BB_WIN.good)return;const p=bd<=BB_WIN.perfect;best.hit=p?'perfect':'good';this.combo++;this.maxCombo=Math.max(this.maxCombo,this.combo);if(p)this.perfect++;else this.good++;
    const mult=1+Math.min(3,Math.floor(this.combo/10));this.score+=(p?300:100)*mult;this.fx.push({lane,txt:p?'PERFECT':'GOOD',life:0.5});if(this.combo===50)arcAchieve('combo50')},
  keyDown(k){const i=BB_LANES.indexOf(k);if(i<0)return false;this.judge(i);return true},
  midiNote(n,v,on){if(on)this.judge(((n%4)+4)%4)},
  update(dt){const t=this.now();this.lit=this.lit.map(x=>Math.max(0,x-dt));this.fx.forEach(f=>f.life-=dt);this.fx=this.fx.filter(f=>f.life>0);
    for(const n of this.chart){if(n.hit)continue;if(t-n.t>BB_WIN.good){n.hit='miss';this.miss++;this.combo=0;this.fx.push({lane:n.lane,txt:'MISS',life:0.5})}else if(n.t>t)break}
    if(t>this.end){const total=this.chart.length||1,acc=(this.perfect+this.good*0.5)/total,grade=acc>0.95?'S':acc>0.85?'A':acc>0.7?'B':acc>0.5?'C':'D',ach=[];
      if(this.miss===0&&total>10)ach.push('fc');if(grade==='S'||grade==='A')ach.push('aRank');if(this.song)ach.push('own');this.exit();finishGame({mode:this.mode,score:this.score,grade,maxCombo:this.maxCombo,perfect:this.perfect,good:this.good,miss:this.miss,ach})}},
  draw(c,W,H){const lw=Math.min(110,W/6),x0=W/2-lw*2,hitY=H-110,speed=H*0.55,t=this.now();
    for(let i=0;i<4;i++){c.fillStyle=i%2?'#1c1a3d':'#201e45';c.fillRect(x0+i*lw,0,lw,H);if(this.lit[i]>0){c.fillStyle=BB_COL[i];c.globalAlpha=this.lit[i]*3;c.fillRect(x0+i*lw,hitY-30,lw,60);c.globalAlpha=1}}
    c.fillStyle='#f4ead6';c.fillRect(x0,hitY,lw*4,3);
    for(const n of this.chart){const y=hitY-(n.t-t)*speed;if(y<-30)break;if(y>H+30||n.hit==='perfect'||n.hit==='good')continue;c.fillStyle=n.hit==='miss'?'#555':BB_COL[n.lane];c.fillRect(x0+n.lane*lw+8,y-10,lw-16,20)}
    c.font='22px VT323,monospace';c.textAlign='center';for(let i=0;i<4;i++){c.fillStyle='#aca4cf';c.fillText(BB_LANES[i].toUpperCase(),x0+i*lw+lw/2,hitY+40)}
    this.fx.forEach(f=>{c.globalAlpha=Math.min(1,f.life*2);c.fillStyle=f.txt==='MISS'?'#ef7a85':'#ffb454';c.fillText(f.txt,x0+f.lane*lw+lw/2,hitY-50-(0.5-f.life)*60)});c.globalAlpha=1;
    c.textAlign='left';c.fillStyle='#f4ead6';c.font='28px VT323,monospace';c.fillText('Score '+this.score,24,40);c.fillText('Combo '+this.combo+(this.combo>=10?' ×'+(1+Math.min(3,Math.floor(this.combo/10))):''),24,72);
    c.font='18px VT323,monospace';c.fillStyle='#aca4cf';c.fillText(this.song?'Playing your song · chart from "'+this.trackName+'"':'Practice beat · '+this.diff,24,100);if(t<0){c.textAlign='center';c.font='60px VT323,monospace';c.fillStyle='#ffb454';c.fillText(Math.ceil(-t),W/2,H/2);c.textAlign='left'}}});

/* ───────── Lo-Fi Fishing ───────── */
const FISH=[['Minor Minnow','common',1,'any'],['Swing Sardine','common',1,'any'],['Bassline Bass','common',2,'any'],['Hi-Hat Herring','common',1,'any'],['Pentatonic Perch','uncommon',3,'any'],['Reverb Ray','uncommon',3,'rain'],
  ['Arpeggio Eel','uncommon',4,'any'],['Vinyl Carp','rare',6,'any'],['Moonlight Sonata Koi','rare',7,'night'],['Tape-Hiss Trout','rare',6,'rain'],['Sidechain Salmon','rare',8,'day'],['Golden Groove Goldfish','epic',12,'any'],
  ['Midnight Synthfin','epic',14,'night'],['The Legendary Lo-Fi Leviathan','legendary',30,'night']];
const RAR={common:[0.5,'#c9c3e0'],uncommon:[0.27,'#8fcaa2'],rare:[0.14,'#79c3e0'],epic:[0.07,'#a796e8'],legendary:[0.02,'#ffb454']};
ACHIEVEMENTS.fish10=['Angler','Catch 10 fish'];ACHIEVEMENTS.fishAll=['Aquarium','Catch every common and uncommon fish'];ACHIEVEMENTS.legend=['Legend','Catch the Lo-Fi Leviathan'];
registerGame({id:'fishing',name:'Lo-Fi Fishing',desc:'Hold Space to cast, press Space when the float dips, then hold Space to keep the bar on the fish while it fights. Weather and time of day change as you play. Q ends the session.',
  modes(){const n=Object.keys(ARC.prog.fish||{}).length;return [{id:'lake',label:'Moonlit lake · '+n+' of '+FISH.length+' fish discovered',short:'Go fishing'}]},
  start(mode){this.mode=mode;this.phase='idle';this.power=0;this.t=0;this.caught=[];this.msg='Hold Space to cast';this.clock=Math.random()*24;this.weather='clear';this.wT=40;this.rain=[];this.score=0;
    ARC.prog.fish=ARC.prog.fish||{};this.keys=newTrack('keys-tine');this.keys.vol=0.35;this.keys.rev=0.6;buildTrack(this.keys);applyMixOne(this.keys);this.nextChord=0;this.ci=0},
  exit(){if(this.keys){disposeTrack(this.keys);this.keys=null}},pause(on){if(on&&this.keys&&this.keys._rt)[...this.keys._rt.live].forEach(h=>h.off(ctx.currentTime))},
  night(){return this.clock<6||this.clock>20},
  pick(){const cond=f=>f[3]==='any'||(f[3]==='night'&&this.night())||(f[3]==='day'&&!this.night())||(f[3]==='rain'&&this.weather==='rain');
    const pool=FISH.filter(cond);let r=Math.random()*pool.reduce((a,f)=>a+RAR[f[1]][0],0);for(const f of pool){r-=RAR[f[1]][0];if(r<=0)return f}return pool[0]},
  keyDown(k){if(k==='q'){this.finish();return true}if(k!==' ')return false;this.space=true;
    if(this.phase==='idle'){this.phase='charge';this.power=0}else if(this.phase==='bite'){this.phase='reel';this.fish=this.pick();const hard={common:1,uncommon:1.4,rare:1.8,epic:2.3,legendary:3}[this.fish[1]];this.hard=hard;this.prog=0.3;this.bar=0.5;this.barV=0;this.fx=0.5;this.fv=0;this.msg='Hold Space to keep the bar on the fish!'}
    else if(this.phase==='wait'){this.phase='idle';this.msg='Too early, the fish swam off. Hold Space to cast'}else if(this.phase==='show'){this.phase='idle';this.msg='Hold Space to cast'}return true},
  keyUp(k){if(k!==' ')return false;this.space=false;if(this.phase==='charge'){this.dist=0.2+this.power*0.75;this.phase='wait';this.waitT=2+Math.random()*6;this.msg='Waiting for a bite…';this.splash(0)}return true},
  midiNote(n,v,on){this.keyDown(on?' ':'');if(!on)this.keyUp(' ')},
  splash(){},
  finish(){this.exit();const n=this.caught.length,score=this.score,grade=n>=10?'S':n>=6?'A':n>=3?'B':n>=1?'C':'D',ach=[];const tot=Object.values(ARC.prog.fish).reduce((a,b)=>a+b,0);if(tot>=10)ach.push('fish10');
    if(FISH.filter(f=>f[1]==='common'||f[1]==='uncommon').every(f=>ARC.prog.fish[f[0]]))ach.push('fishAll');if(ARC.prog.fish['The Legendary Lo-Fi Leviathan'])ach.push('legend');
    finishGame({mode:this.mode,score,grade,maxCombo:n,perfect:n,good:0,miss:this.lost||0,ach})},
  update(dt){this.t+=dt;this.clock=(this.clock+dt*0.2)%24;this.wT-=dt;if(this.wT<0){this.wT=30+Math.random()*40;this.weather=['clear','clear','rain','fog'][Math.floor(Math.random()*4)]}
    // gentle lo-fi chords on the app's own electric piano
    if(this.keys&&ctx&&ctx.currentTime>this.nextChord){const prog=[[57,60,64,67],[53,57,60,64],[55,59,62,65],[52,55,59,62]],ch=prog[this.ci++%4],t0=Math.max(ctx.currentTime+0.05,this.nextChord);ch.forEach((n,i)=>{const h=trigger(this.keys,n,0.35+Math.random()*0.1,t0+i*0.03);if(h)h.off(t0+3.8)});this.nextChord=t0+4}
    if(this.weather==='rain'){for(let i=0;i<3;i++)this.rain.push({x:Math.random(),y:0});this.rain.forEach(r=>r.y+=dt*1.2);this.rain=this.rain.filter(r=>r.y<1)}else this.rain=[];
    if(this.phase==='charge')this.power=Math.min(1,this.power+dt*0.7);
    if(this.phase==='wait'){this.waitT-=dt;if(this.waitT<0){this.phase='bite';this.biteT=0.9;this.msg='!!! Press Space !!!'}}
    if(this.phase==='bite'){this.biteT-=dt;if(this.biteT<0){this.phase='idle';this.lost=(this.lost||0)+1;this.msg='It got away. Hold Space to cast'}}
    if(this.phase==='reel'){this.fv+=(Math.random()-0.5)*dt*8*this.hard;this.fv*=0.96;this.fx=clamp(this.fx+this.fv*dt,0.05,0.95);if(this.fx<=0.05||this.fx>=0.95)this.fv*=-0.8;
      this.barV+=(this.space?1.6:-1.6)*dt;this.barV*=0.92;this.bar=clamp(this.bar+this.barV*dt,0.06,0.94);if(this.bar<=0.06||this.bar>=0.94)this.barV=0;
      const on=Math.abs(this.bar-this.fx)<0.09;this.prog+=dt*(on?0.25:-0.18/this.hard*1.6);
      if(this.prog>=1){const f=this.fish;this.caught.push(f);ARC.prog.fish[f[0]]=(ARC.prog.fish[f[0]]||0)+1;const pts=f[2]*100;this.score+=pts;ARC.prog.coins+=f[2];arcSave();this.phase='show';this.msg='Caught: '+f[0]+' ('+f[1]+') +'+f[2]+' coins · Space to continue';
        if(f[1]==='legendary'||f[1]==='epic')toast('Wow! '+f[0]+'!')}
      else if(this.prog<=0){this.phase='idle';this.lost=(this.lost||0)+1;this.msg='The line went slack. Hold Space to cast'}}},
  /* Pixel art: everything is drawn on a 320×180 buffer and scaled up with crisp pixels. */
  draw(c,W,H){const PW=320,PH=180;if(!this.buf){this.buf=document.createElement('canvas');this.buf.width=PW;this.buf.height=PH}const g=this.buf.getContext('2d'),t=this.t,R=(x,y,w,h,col)=>{g.fillStyle=col;g.fillRect(Math.round(x),Math.round(y),Math.round(w),Math.round(h))};
    const hr=this.clock,night=hr<5.5||hr>20.5,dusk=!night&&(hr>17.5||hr<7.5),rain=this.weather==='rain',fog=this.weather==='fog';
    const sky=night?['#0d0b24','#141236','#1c1a48','#26235a']:dusk?['#2a1f4e','#5a3a6e','#b0607a','#f2a46a']:rain?['#4b5068','#5b6178','#6a7088','#7a8098']:['#3b78b5','#5b95c8','#83b3d8','#b4d3e8'];
    const HZ=104;for(let i=0;i<4;i++)R(0,i*26,PW,26,sky[i]);
    for(let i=0;i<3;i++)for(let x=0;x<PW;x+=2)if(((x>>1)+i)%2===0)R(x,(i+1)*26-1,1,1,sky[i+1]);// dithered band edges
    if(night){g.fillStyle='#f4ead6';for(let i=0;i<60;i++){const x=(i*73)%PW,y=(i*37)%70;if((Math.sin(t*2+i)+1)>0.4)g.fillRect(x,y,1,1)}}
    const bx=night?250:dusk?230:70,by=night?28:dusk?80:26,bc=night?'#f4ead6':dusk?'#ffb06a':'#ffe39a';if(!rain){R(bx-6,by-4,12,8,bc);R(bx-4,by-6,8,12,bc)}if(night){R(bx-3,by-4,3,3,'#d9cfb8');R(bx+1,by+1,2,2,'#d9cfb8')}
    // far mountains, near hills, pines
    const mtn=(base,amp,f,col,off)=>{g.fillStyle=col;for(let x=0;x<PW;x++){const y=base-Math.abs(Math.sin(x*f+off))*amp-Math.sin(x*f*2.7+off)*amp*0.3;g.fillRect(x,Math.round(y),1,HZ-Math.round(y))}};
    mtn(HZ-8,26,0.018,night?'#211f4a':dusk?'#4a3460':rain?'#555b72':'#6f8fb5',1);mtn(HZ-2,14,0.035,night?'#191739':dusk?'#3a2850':rain?'#474c62':'#4f7396',4);
    for(let i=0;i<14;i++){const x=200+i*9+(i%3)*2,h=10+(i*7)%9;g.fillStyle=night?'#120f2c':dusk?'#2a1d3a':'#2f5a4a';for(let k=0;k<h;k++)g.fillRect(x-Math.floor(k/3)+1,HZ-k,Math.floor(k/3)*2+1>5?5:Math.floor(k/3)*2+1,1)}
    // water with reflection and ripples
    const wc=night?['#141a3f','#18204a','#1d2655']:dusk?['#3b2b55','#4d3563','#5e406f']:rain?['#465068','#4f5a73','#58637d']:['#2f6a96','#3878a6','#4384b3'];
    for(let y=HZ;y<PH;y++)R(0,y,PW,1,wc[Math.min(2,Math.floor((y-HZ)/25))]);
    for(let i=0;i<(rain||bx<110?0:10);i++){const y=HZ+3+i*4,w=12-i*0.6,o=Math.round(Math.sin(t*2+i)*2);R(bx-w/2+o,y,w,1,bc+'88')}
    for(let i=0;i<22;i++){const y=HZ+4+((i*13)%70),x=((i*47)+t*6*(1+(i%3)))%(PW+30)-30;R(x,y,8+(i%4)*3,1,night?'#2a3466':'#ffffff22')}
    // lily pads and reeds
    [[150,150],[176,160],[230,140],[290,166]].forEach(([x,y],i)=>{R(x,y,9,3,'#3f7a4f');R(x+2,y-1,5,1,'#4f8f5f');if(i===1){R(x+3,y-2,2,1,'#e8a0bb')}});
    for(let i=0;i<9;i++){const x=104+i*3,h=8+(i%3)*4,sw=Math.round(Math.sin(t*1.5+i)*1);R(x+sw,HZ+12-h,1,h,'#3d6b45');if(i%3===0)R(x+sw,HZ+12-h,2,3,'#6b4a2f')}
    // dock, lantern, cat
    R(0,HZ+4,98,5,'#6b4a33');R(0,HZ+9,98,2,'#4a3324');for(let x=4;x<98;x+=14)R(x,HZ+11,3,18,'#4a3324');for(let x=0;x<98;x+=7)R(x,HZ+4,1,5,'#5a3d2a');
    R(86,HZ-12,3,16,'#4a3324');R(84,HZ-16,7,6,'#2a1d14');const lg=night||dusk?'#ffcf6e':'#c9a050';R(85,HZ-15,5,4,lg);if(night||dusk){g.fillStyle='rgba(255,207,110,.12)';g.beginPath();g.arc(87,HZ-13,14,0,7);g.fill()}
    const cx=58,cy=HZ-8,fur=(PREF&&PREF.fur)==='black'?'#2b2733':PREF.fur==='grey'?'#8c8a96':PREF.fur==='cream'?'#e9dcc2':'#e0914a';
    R(cx,cy,12,12,fur);R(cx+1,cy-6,10,7,fur);R(cx+1,cy-8,3,3,fur);R(cx+8,cy-8,3,3,fur);R(cx+3,cy-4,2,2,'#1a1630');R(cx+7,cy-4,2,2,'#1a1630');R(cx+5,cy-2,2,1,'#e8a0bb');
    const tail=Math.round(Math.sin(t*2)*2);R(cx-3,cy+6+tail,4,3,fur);R(cx-5,cy+3+tail,3,4,fur);
    // rod and line
    const rx0=cx+11,ry0=cy+2,rtx=cx+30,rty=cy-14;g.strokeStyle='#8a5a3a';g.lineWidth=1;g.beginPath();g.moveTo(rx0,ry0);g.lineTo(rtx,rty);g.stroke();
    if(['wait','bite','reel','show'].includes(this.phase)){const fx=110+this.dist*190,bob=this.phase==='bite'?(Math.sin(t*28)>0?3:0):Math.round(Math.sin(t*2)),fy=HZ+18+bob+(this.phase==='reel'?Math.round(Math.sin(t*12)*2):0);
      g.strokeStyle='rgba(244,234,214,.7)';g.beginPath();g.moveTo(rtx,rty);g.quadraticCurveTo((rtx+fx)/2,rty-10+(this.phase==='reel'?18:0),fx,fy);g.stroke();
      R(fx-1,fy-2,3,2,'#ef7a85');R(fx-1,fy,3,1,'#f4ead6');if(this.phase==='bite'){R(fx-6,fy+2,13,1,'#ffffff88');R(fx-1,fy-10,3,5,'#ffb454');R(fx-1,fy-4,3,1,'#ffb454')}
      if(this.phase==='reel'){const sx=fx-20+this.fx*40,col=RAR[this.fish[1]][1];g.globalAlpha=0.45;R(sx-6,fy+8,12,4,'#0a0a1a');R(sx+6,fy+9,3,2,'#0a0a1a');g.globalAlpha=1;if(Math.random()<0.3)R(fx-4+Math.random()*8,fy-1,1,1,'#ffffff')}}
    // weather and fireflies
    if(rain){g.fillStyle='#b8c6e8';this.rain.forEach(r=>{g.fillRect(Math.round(r.x*PW),Math.round(r.y*PH),1,4)});for(let i=0;i<6;i++){const x=(i*61+Math.floor(t*10)*17)%PW,y=HZ+10+(i*29)%60;R(x,y,3,1,'#c9d4f0')}}
    if(night&&!rain){for(let i=0;i<10;i++){const x=40+((i*53)+Math.sin(t*0.7+i)*20)%240,y=HZ-20+Math.sin(t*1.3+i*2)*12;if(Math.sin(t*3+i*1.7)>0.2)R(x,y,1,1,'#f6e27a')}}
    if(fog){for(let i=0;i<4;i++){g.fillStyle='rgba(225,225,240,.18)';g.fillRect(((t*4+i*90)%(PW+120))-120,HZ-18+i*10,140,8)}}
    // pixel UI
    const panel=(x,y,w,h)=>{R(x,y,w,h,'#1a1736');R(x,y,w,1,'#ffb454');R(x,y+h-1,w,1,'#ffb454');R(x,y,1,h,'#ffb454');R(x+w-1,y,1,h,'#ffb454')};
    if(this.phase==='charge'){panel(58,cy-30,52,9);R(60,cy-28,48*this.power,5,this.power>0.85?'#ef7a85':'#ffb454')}
    if(this.phase==='reel'){panel(292,30,20,112);const bh=106,top=33;R(295,top,8,bh,'#0f0d26');R(295,top+(1-this.bar)*bh-10,8,20,'#8fcaa2');const col=RAR[this.fish[1]][1];R(296,top+(1-this.fx)*bh-3,6,6,col);R(297,top+(1-this.fx)*bh-1,1,1,'#000');
      R(305,top,4,bh,'#0f0d26');R(305,top+bh*(1-this.prog),4,bh*this.prog,'#ffb454')}
    if(this.phase==='show'&&this.caught.length){const f=this.caught[this.caught.length-1],col=RAR[f[1]][1];panel(110,40,100,56);
      const fx=146,fy=56;R(fx,fy,22,9,col);R(fx-6,fy+1,6,7,col);R(fx-9,fy-1,3,11,col);R(fx+16,fy+2,2,2,'#1a1630');R(fx+4,fy-2,10,2,col);R(fx+3,fy+9,4,2,col);for(let i=0;i<4;i++)R(fx+3+i*4,fy+3,1,3,'#00000033')}
    // blit
    c.imageSmoothingEnabled=false;const sc=Math.max(1,Math.floor(Math.min(W/PW,H/PH))),dw=PW*sc,dh=PH*sc,ox=Math.floor((W-dw)/2),oy=Math.floor((H-dh)/2);c.fillStyle='#0d0b24';c.fillRect(0,0,W,H);c.drawImage(this.buf,ox,oy,dw,dh);
    // crisp text on top (pixel font)
    c.textAlign='center';c.font=Math.round(9*sc)+'px VT323,monospace';c.fillStyle='#1a1736';c.fillText(this.msg,W/2+sc,oy+dh-10*sc+sc);c.fillStyle=this.phase==='bite'?'#ffb454':'#f4ead6';c.fillText(this.msg,W/2,oy+dh-10*sc);
    if(this.phase==='show'&&this.caught.length){const f=this.caught[this.caught.length-1];c.font=Math.round(7*sc)+'px VT323,monospace';c.fillStyle=RAR[f[1]][1];c.fillText(f[0],ox+160*sc,oy+80*sc);c.fillStyle='#aca4cf';c.fillText(f[1].toUpperCase()+' · +'+f[2]+' coins',ox+160*sc,oy+89*sc)}
    c.textAlign='left';c.font=Math.round(7*sc)+'px VT323,monospace';c.fillStyle='#f4ead6';c.fillText(String(Math.floor(hr)).padStart(2,'0')+':'+String(Math.floor((hr%1)*60)).padStart(2,'0')+'  '+(rain?'☂ rain':fog?'≋ fog':night?'☾ clear':'☀ clear')+'   🐟 '+this.caught.length+'   Q: end session',ox+6*sc,oy+10*sc);
    this.caught.slice(-4).reverse().forEach((f,i)=>{c.fillStyle=RAR[f[1]][1];c.fillText('· '+f[0],ox+6*sc,oy+(20+i*8)*sc)})
}});
