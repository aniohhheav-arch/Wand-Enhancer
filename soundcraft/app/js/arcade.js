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
