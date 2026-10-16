'use strict';
/* Studio: the pixel bedroom. Every reactive element reads the live analysis in core.js (A.*):
   the lamp follows bass energy, speakers move with bass, the figurine and cat react to detected
   kicks, sparkles follow the highs, haze follows the reverb return, the monitor shows the output. */
const FURS={ginger:['#e8955a','#b8683a','#f6c99a'],black:['#2e2b45','#1b1930','#4a4668'],grey:['#9d99b8','#6e6a8d','#c9c5dc'],cream:['#f1dcb6','#c9ad85','#fff1d8'],tux:['#2e2b45','#1b1930','#f4ead6']};
const reduced=()=>PREF.motion==='reduced'||matchMedia('(prefers-reduced-motion: reduce)').matches;
const walkKeys=new Set();
const WALK={w:'u',arrowup:'u',s:'d',arrowdown:'d',a:'l',arrowleft:'l',d:'r',arrowright:'r'};

const room=$('#room'),rc=room.getContext('2d');rc.imageSmoothingEnabled=false;
const RW=256,RH=144,FLOOR=104;
const me={x:150,y:122,dir:-1,walk:0,target:null,onArrive:null};
const cat={x:220,y:96,state:'sleep',t:0,next:6,spot:'couch',fromX:220,fromY:96,toX:220,toY:96,hop:0,dur:0,bob:0,petT:0,dir:-1};
const CAT_SPOTS={couch:[222,96],sill:[46,58],desk:[118,80],speakers:[124,124],rug:[100,126],shelf:[214,46]};
let lampPulse=0,figBounce=0,flash=0,particles=[],notesFx=[],hover=null,lastT=performance.now(),studioClock=0;
const OBJ=[
  {id:'window',r:[16,12,56,48],stand:[44,114],label:'Window · change the weather',act:()=>{const ks=Object.keys(WEATHERS);PREF.weather=ks[(ks.indexOf(PREF.weather)+1)%ks.length];savePrefs();startAmbience();toast(WEATHERS[PREF.weather])}},
  {id:'shelf',r:[188,18,60,18],stand:[214,114],label:'Cassette shelf · open your project library',act:openLibrary},
  {id:'groove',r:[192,38,22,10],stand:[204,114],label:'Groovebox · edit the drum pads',act:()=>{const i=S.tracks.findIndex(t=>ENG[t.engine].drum);if(i<0){toast('This project has no drum track yet. Add one from Sounds.');return}selectTrack(i);setView('lab')}},
  {id:'computer',r:[124,52,32,26],stand:[140,114],label:'Computer · sit down and open the DAW',act:()=>setView('arrange')},
  {id:'keys',r:[122,79,36,5],stand:[140,114],label:'Keyboard · open the instrument in the Patch Lab',act:()=>setView('lab')},
  {id:'lamp',r:[164,54,16,26],stand:[170,114],label:'Desk lamp · switch on or off',act:()=>{PREF.lamp=!PREF.lamp;savePrefs()}},
  {id:'record',r:[76,78,30,26],stand:[90,114],label:'Record player · play or stop the song',act:()=>S.playing?stop():play()},
];
const catObj={id:'cat',label:'Cat · give a pet',act:petCat};
function catRect(){return cat.state==='sleep'?[cat.x-7,cat.y-6,14,8]:[cat.x-6,cat.y-10,12,11]}
function inR(x,y,r){return x>=r[0]&&x<r[0]+r[2]&&y>=r[1]&&y<r[1]+r[3]}
function objAt(x,y){if(inR(x,y,catRect()))return catObj;for(let i=OBJ.length-1;i>=0;i--)if(inR(x,y,OBJ[i].r))return OBJ[i];return null}
function roomPt(e){const b=room.getBoundingClientRect();return{x:(e.clientX-b.left)/b.width*RW,y:(e.clientY-b.top)/b.height*RH}}
room.addEventListener('pointermove',e=>{const p=roomPt(e),o=objAt(p.x,p.y);hover=o;room.classList.toggle('hot',!!o);const l=$('#roomLabel');l.hidden=!o;if(o)l.textContent=o.label});
room.addEventListener('pointerleave',()=>{hover=null;$('#roomLabel').hidden=true});
room.addEventListener('click',e=>{ensureAudio();const p=roomPt(e),o=objAt(p.x,p.y);
  if(o){const st=o===catObj?[clamp(cat.x+(me.x<cat.x?-12:12),8,248),clamp(Math.max(cat.y+6,110),110,138)]:o.stand;walkTo(st[0],st[1],()=>o.act())}
  else walkTo(clamp(p.x,6,250),clamp(p.y,110,138))});
function walkTo(x,y,cb){me.target=[x,clamp(y,110,138)];me.onArrive=cb||null}
function useNearest(){let best=null,bd=28;[...OBJ,catObj].forEach(o=>{const st=o===catObj?[cat.x,cat.y]:o.stand,d=Math.hypot(st[0]-me.x,(st[1]-me.y)*0.6);if(d<bd){bd=d;best=o}});if(best)best.act();else toast('Walk up to something first, then press E.')}
function petCat(){cat.petT=2.5;if(cat.state==='sleep'||cat.state==='walk'){cat.state='sit';cat.x=cat.toX;cat.y=cat.toY}cat.next=Math.max(cat.next,5);for(let i=0;i<5;i++)particles.push({x:cat.x+(Math.random()*8-4),y:cat.y-10,vy:-12-Math.random()*8,vx:Math.random()*6-3,life:1.4,kind:'heart'});purr()}
function studioNote(v){if(S.view!=='studio'||reduced())return;notesFx.push({x:140+Math.random()*16-8,y:76,vy:-14-v*10,vx:Math.random()*10-5,life:1.6,c:selTrack().color})}

function catThink(dt){
  cat.t+=dt;cat.petT=Math.max(0,cat.petT-dt);
  if(cat.state==='walk'){const k=Math.min(1,cat.t/cat.dur);cat.x=cat.fromX+(cat.toX-cat.fromX)*k;const lin=cat.fromY+(cat.toY-cat.fromY)*k;cat.y=lin-Math.sin(k*Math.PI)*cat.hop;if(k>=1){cat.state=cat.spot==='couch'&&!musicOn()?'sleep':'sit';cat.t=0;cat.next=8+Math.random()*12}return}
  cat.next-=dt;if(cat.next>0||cat.petT>0)return;
  const loud=musicOn(),opts=loud?['speakers','speakers','desk','rug','sill']:['couch','couch','sill','desk','shelf','rug'];let spot=opts[Math.floor(Math.random()*opts.length)];if(spot===cat.spot)spot=loud?'rug':'couch';
  const [tx,ty]=CAT_SPOTS[spot];cat.spot=spot;cat.fromX=cat.x;cat.fromY=cat.y;cat.toX=tx;cat.toY=ty;cat.dir=tx<cat.x?-1:1;cat.hop=Math.abs(ty-cat.y)>6?14+Math.abs(ty-cat.y)*0.3:0;
  cat.dur=Math.max(0.8,Math.hypot(tx-cat.x,ty-cat.y)/38);cat.state='walk';cat.t=0;
}
const musicOn=()=>S.playing&&A.level>0.01;
function stepMe(dt){
  let dx=0,dy=0;if(walkKeys.has('l'))dx--;if(walkKeys.has('r'))dx++;if(walkKeys.has('u'))dy--;if(walkKeys.has('d'))dy++;
  if(!dx&&!dy&&me.target){const ex=me.target[0]-me.x,ey=me.target[1]-me.y,d=Math.hypot(ex,ey);if(d<1.5){me.target=null;const cb=me.onArrive;me.onArrive=null;me.walk=0;if(cb)cb();return}dx=ex/d;dy=ey/d}
  if(dx||dy){const d=Math.hypot(dx,dy),sp=58;me.x=clamp(me.x+dx/d*sp*dt,6,250);me.y=clamp(me.y+dy/d*sp*0.7*dt,110,138);if(dx)me.dir=dx<0?-1:1;me.walk+=dt*8}else me.walk=0;
}
/* drawing helpers: everything is whole logical pixels */
function px(x,y,w,h,c){rc.fillStyle=c;rc.fillRect(Math.round(x),Math.round(y),w,h)}
const SKY={rain:['#1d2a4d','#2b3763'],storm:['#141a33','#232a4a'],snow:['#29304f','#3d4670'],night:['#0f1433','#232a5a'],evening:['#c9737e','#f2b070'],morning:['#9fc6e0','#e7d9c3']};
function drawRoom(dt,now){
  const W=PREF.weather,day=W==='morning'||W==='evening',rm=reduced();
  // wall + floor
  px(0,0,RW,FLOOR,day?'#4a4378':'#2c2858');for(let y=6;y<FLOOR;y+=12)px(0,y,RW,1,day?'#544c86':'#332f64');
  px(0,FLOOR-3,RW,3,'#5a3d39');px(0,FLOOR,RW,RH-FLOOR,'#4a3330');for(let y=FLOOR+8;y<RH;y+=9){px(0,y,RW,1,'#3d2a28');}for(let i=0;i<14;i++)px((i*37+(Math.floor(i/2)%2)*18)%RW,FLOOR+1+(i%4)*9,1,8,'#3d2a28');
  // rug
  rc.fillStyle='#6b4f8a';rc.beginPath();rc.ellipse(118,127,70,9,0,0,Math.PI*2);rc.fill();rc.fillStyle='#8a6aa6';rc.beginPath();rc.ellipse(118,127,58,6,0,0,Math.PI*2);rc.fill();rc.fillStyle='#e8a0bb';rc.fillRect(60,127,116,1);
  // window
  const sky=SKY[W],g=rc.createLinearGradient(0,14,0,58);g.addColorStop(0,sky[0]);g.addColorStop(1,sky[1]);rc.fillStyle=g;rc.fillRect(18,14,52,44);
  if(W==='night'||W==='snow'||W==='rain'){for(let i=0;i<14;i++){const sx=20+(i*29)%48,sy=16+(i*17)%30;if(W!=='night'&&i%2)continue;px(sx,sy,1,1,(Math.floor(now/600+i)%5)?'#f4ead6':'#8a86b8')}px(58,19,6,6,'#f4ead6');px(60,19,4,4,sky[0])}
  if(W==='evening'){px(26,40,10,10,'#ffd28a');px(24,42,14,6,'#ffd28a')}
  if(W==='morning'){px(52,22,8,8,'#fff3cf')}
  // skyline
  for(let i=0;i<7;i++){const bx=18+i*8,bh=8+((i*13)%11);px(bx,58-bh,7,bh,day?'#6a5c8c':'#191d3d');if(!day)for(let j=0;j<3;j++)if((i+j)%2)px(bx+2,58-bh+2+j*3,1,1,'#ffb454')}
  // weather particles
  if(W==='rain'||W==='storm'){rc.fillStyle='rgba(180,200,255,.55)';const n=W==='storm'?26:16,sp=rm?0.25:1;for(let i=0;i<n;i++){const x=18+((i*53+Math.floor(now*0.05*sp))%52),y=14+((i*31+now*0.09*sp)%44);rc.fillRect(Math.floor(x),Math.floor(y),1,3)}}
  if(W==='snow'){rc.fillStyle='#f4ead6';const sp=rm?0.2:1;for(let i=0;i<18;i++){const x=18+((i*23+Math.sin(now/900+i)*3*sp)%52+52)%52,y=14+((i*19+now*0.012*sp)%44);rc.fillRect(Math.floor(x),Math.floor(y),1,1)}}
  if(W==='storm'&&!rm){if(Math.random()<dt*0.12)flash=1;flash=Math.max(0,flash-dt*3)}else flash=0;
  // window frame + sill
  px(16,12,56,2,'#7a5640');px(16,58,56,2,'#7a5640');px(16,12,2,48,'#7a5640');px(70,12,2,48,'#7a5640');px(43,12,2,48,'#7a5640');px(16,35,56,2,'#7a5640');px(12,60,64,3,'#8b6447');
  // poster
  px(82,18,20,26,'#e8a0bb');px(84,20,16,22,'#f4ead6');px(88,26,2,10,'#2c2858');px(90,26,5,2,'#2c2858');px(86,34,4,3,'#2c2858');px(92,32,4,3,'#2c2858');px(93,26,2,8,'#2c2858');
  // shelves
  px(188,34,60,2,'#8b6447');px(188,50,60,2,'#8b6447');
  const tapeCols=Object.values(LIB).slice(0,9).map(e=>e.color);for(let i=0;i<9;i++){const c=tapeCols[i]||'#463f6e';px(190+i*6,22,5,12,c);px(191+i*6,24,3,3,'#f4ead6')}
  px(192,40,22,10,'#3b3768');for(let i=0;i<4;i++)for(let j=0;j<2;j++){const on=S.playing&&posAtNow()!=null&&Math.floor(posAtNow())%4===i;px(194+i*5,42+j*4,3,2,on?'#ef9a6a':'#5a5590')}
  px(220,42,6,8,'#5c7b5e');px(219,40,8,3,'#8fcaa2');px(232,44,10,6,'#79c3e0');px(234,42,6,2,'#f4ead6');
  // plant
  px(6,90,12,14,'#a5603f');px(5,88,14,3,'#bd7350');const sway=rm?0:Math.sin(now/1300)*1;for(let i=0;i<5;i++)px(7+i*2+sway*(i%2),72+(i%3)*5,3,16-(i%3)*4,i%2?'#6b9b78':'#8fcaa2');
  // couch
  px(196,78,54,10,'#6a5a9a');px(194,86,58,12,'#7d6cb0');px(194,86,6,16,'#6a5a9a');px(246,86,6,16,'#6a5a9a');px(200,98,46,4,'#5a4b86');px(198,102,3,2,'#3a2c28');px(245,102,3,2,'#3a2c28');px(204,84,14,6,'#e8a0bb');
  // record cabinet + turntable
  px(76,86,30,18,'#6b4a3a');px(78,90,26,1,'#5a3d30');px(90,92,2,4,'#c9a86a');px(74,84,34,3,'#8b6447');
  px(78,78,26,6,'#3a3460');const rot=S.playing&&!rm?now/180:0;rc.fillStyle='#14122a';rc.beginPath();rc.arc(88,79,6,Math.PI,0);rc.fill();px(87,77,2,2,'#e8a0bb');
  if(S.playing){const a=rot;px(88+Math.round(Math.cos(a)*4),78+Math.round(Math.sin(a)*1),1,1,'#8a86b8')}
  px(99,74,1,6,'#c9c5dc');px(96,74,4,1,'#c9c5dc');
  // desk
  px(110,80,72,4,'#8b6447');px(112,84,4,20,'#6b4a3a');px(176,84,4,20,'#6b4a3a');px(116,84,60,2,'#6b4a3a');
  // chair
  px(132,96,16,3,'#3d3a6a');px(139,99,2,7,'#2a2850');px(134,106,12,2,'#2a2850');px(134,86,12,10,'#46428a');
  // speakers (react to bass)
  const cone=A.bass*(rm?0.4:1.6);[[112,62],[160,62]].forEach(([sx,sy])=>{px(sx,sy,10,18,'#2a2744');px(sx+1,sy+1,8,16,'#35315a');rc.fillStyle='#1b1930';rc.beginPath();rc.arc(sx+5,sy+11,2.5+cone,0,Math.PI*2);rc.fill();px(sx+4,sy+3,2,2,'#1b1930');px(sx+4,sy+10,2,2,'#4a4678')});
  // monitor showing real output waveform
  px(124,52,32,22,'#1b1930');px(126,54,28,17,'#100f26');px(138,74,4,4,'#1b1930');px(134,78,12,2,'#1b1930');
  if(GR.analyser&&A.level>0.001){rc.fillStyle='#ffb454';for(let x=0;x<28;x++){const v=tbuf[x*16]||0;rc.fillRect(126+x,62+Math.round(clamp(v*7,-7,7)),1,1)}}
  else{px(128,57,10,2,'#8fcaa2');px(128,61,16,2,'#e8a0bb');px(128,65,7,2,'#79c3e0');px(140,57,2,2,'#ffb454')}
  // keyboard on desk
  px(122,78,36,4,'#e9e0cc');for(let i=0;i<12;i++)px(123+i*3,78,1,3,'#a89c88');for(let i=0;i<12;i++)if([1,2,4,5,6].includes(i%7))px(124+i*3,78,1,2,'#1b1930');
  if(keyCounts.size||held.size){px(122+((studioClock*7)|0)%34,78,2,3,selTrack().color)}
  // mug + figurine (figurine hops on detected kicks)
  px(116,75,4,5,'#e8a0bb');px(120,76,1,2,'#e8a0bb');if(!rm){px(117,72+Math.sin(now/400)*0.5,1,2,'rgba(244,234,214,.4)')}
  if(A.kick&&!rm)figBounce=1;figBounce=Math.max(0,figBounce-dt*6);const fb=Math.round(Math.sin(figBounce*Math.PI)*3);px(150,76-fb,4,4,'#8fcaa2');px(150,74-fb,4,2,'#79c3e0');
  // lamp
  px(166,78,10,2,'#3b3768');px(170,62,2,16,'#3b3768');px(164,56,12,7,PREF.lamp?'#ffb454':'#5a5590');px(166,54,8,2,PREF.lamp?'#ffcf86':'#4a4678');
  // light + atmosphere
  const dark=day?0.08:(W==='storm'?0.42:0.34);rc.fillStyle='rgba(8,7,24,'+dark+')';rc.fillRect(0,0,RW,RH);
  lampPulse+=((PREF.lamp?0.5+A.bass*(rm?0.15:0.6):0)-lampPulse)*Math.min(1,dt*12);
  if(lampPulse>0.01){const lg=rc.createRadialGradient(170,66,2,170,80,70);lg.addColorStop(0,'rgba(255,190,110,'+(0.42*lampPulse)+')');lg.addColorStop(1,'rgba(255,190,110,0)');rc.fillStyle=lg;rc.fillRect(90,20,166,124)}
  if(A.rev>0.02){rc.fillStyle='rgba(172,164,207,'+Math.min(0.18,A.rev*0.3)+')';for(let i=0;i<3;i++){const y=30+i*26+(rm?0:Math.sin(now/2000+i)*3);rc.fillRect(0,Math.round(y),RW,6)}}
  if(flash>0){rc.fillStyle='rgba(220,225,255,'+(flash*0.35)+')';rc.fillRect(0,0,RW,RH)}
}
function drawMe(){
  const x=Math.round(me.x),y=Math.round(me.y),f=Math.floor(me.walk)%2,leg=me.walk?(f?1:-1):0;
  rc.fillStyle='rgba(0,0,0,.25)';rc.fillRect(x-4,y,9,2);
  px(x-2+leg,y-4,2,4,'#2c2858');px(x+1-leg,y-4,2,4,'#2c2858');px(x-2+leg,y-1,2,1,'#f4ead6');px(x+1-leg,y-1,2,1,'#f4ead6');
  px(x-3,y-11,7,7,'#a796e8');px(x-3,y-11,7,1,'#c3b5f2');px(me.dir<0?x-4:x+4,y-10,1,4,'#a796e8');
  px(x-3,y-17,7,6,'#f2c9a0');px(x-3,y-18,7,3,'#4a3040');px(me.dir<0?x+2:x-3,y-16,2,3,'#4a3040');
  px(me.dir<0?x-2:x+2,y-14,1,1,'#2c2858');
  px(x-4,y-15,1,3,'#e8a0bb');px(x+4,y-15,1,3,'#e8a0bb');px(x-3,y-19,7,1,'#e8a0bb');
}
function drawCat(dt,now){
  const [c1,c2,c3]=FURS[PREF.fur],x=Math.round(cat.x),rm=reduced();
  if(cat.state==='walk'||cat.state==='sit'){if(A.kick&&musicOn()&&!rm&&PREF.sens>0.05)cat.bob=1}
  cat.bob=Math.max(0,cat.bob-dt*6);const bob=Math.round(Math.sin(cat.bob*Math.PI)*1.5),y=Math.round(cat.y);
  rc.fillStyle='rgba(0,0,0,.22)';rc.fillRect(x-6,y,12,1);
  if(cat.state==='sleep'){
    px(x-6,y-5,12,5,c1);px(x-5,y-6,10,1,c1);px(x-6,y-2,12,2,c2);px(x+3,y-7,4,4,c1);px(x+3,y-8,1,1,c1);px(x+6,y-8,1,1,c1);px(x+4,y-5,2,1,c2);
    px(x-7,y-3,2,3,c2);if(PREF.fur==='tux')px(x+3,y-4,3,2,c3);
    if(!rm){const z=(now/700)%3;rc.fillStyle='rgba(244,234,214,'+(1-z/3)+')';rc.font='bold 6px monospace';rc.fillText('z',x+6+z*2,y-9-z*3)}
    return}
  const d=cat.dir,walking=cat.state==='walk',f=walking?Math.floor(now/120)%2:0;
  if(walking){px(x-5,y-6,10,4,c1);px(x-5,y-3,2,3-f,c2);px(x+3,y-3,2,2+f,c2);px(x-2,y-3,2,2+f,c2);px(x+1,y-3,2,3-f,c2);
    const hx=d>0?x+4:x-8;px(hx,y-9,5,5,c1);px(hx,y-10,1,1,c1);px(hx+4,y-10,1,1,c1);px(d>0?hx+3:hx+1,y-7,1,1,'#14122a');px(d>0?x-7:x+5,y-9,2,4,c2);return}
  // sitting, head bobs on real detected kicks
  px(x-4,y-7,8,7,c1);px(x-3,y-1,2,1,c2);px(x+1,y-1,2,1,c2);if(PREF.fur==='tux')px(x-1,y-6,3,4,c3);
  const tailWag=rm?0:Math.round(Math.sin(now/(musicOn()?220:600)));px(d>0?x-6:x+4,y-4+tailWag,2,4,c2);
  const hy=y-12+bob;px(x-3,hy,7,6,c1);px(x-3,hy-1,1,1,c1);px(x+3,hy-1,1,1,c1);px(x-3,hy-2,1,1,c2);px(x+3,hy-2,1,1,c2);
  const blink=Math.floor(now/2600)%7===0;px(x-2,hy+2,1,blink?0:1,'#14122a');px(x+2,hy+2,1,blink?0:1,'#14122a');px(x,hy+3,1,1,'#e8a0bb');
  if(PREF.fur==='tux')px(x-1,hy+4,3,1,c3);
}
function drawFx(dt){
  const rm=reduced();
  if(!rm&&musicOn()&&A.high>0.08&&Math.random()<A.high*dt*40)particles.push({x:112+Math.random()*58,y:60+Math.random()*10,vy:-6,vx:Math.random()*4-2,life:0.8,kind:'spark'});
  if(!rm&&musicOn()&&A.mid>0.25&&Math.random()<(A.mid-0.2)*dt*8)notesFx.push({x:Math.random()<0.5?117:165,y:62,vy:-12,vx:Math.random()*8-4,life:1.5,c:S.tracks[Math.floor(Math.random()*S.tracks.length)].color});
  for(const p of particles){p.life-=dt;p.x+=p.vx*dt;p.y+=p.vy*dt;if(p.kind==='heart'){px(p.x,p.y,1,1,'#ef7a85');px(p.x+2,p.y,1,1,'#ef7a85');px(p.x,p.y+1,3,1,'#ef7a85');px(p.x+1,p.y+2,1,1,'#ef7a85')}else{rc.fillStyle='rgba(244,234,214,'+clamp(p.life,0,1)+')';rc.fillRect(Math.round(p.x),Math.round(p.y),1,1)}}
  particles=particles.filter(p=>p.life>0);
  for(const n of notesFx){n.life-=dt;n.x+=n.vx*dt+Math.sin(n.life*6)*0.2;n.y+=n.vy*dt;rc.globalAlpha=clamp(n.life,0,1);px(n.x,n.y,2,2,n.c);px(n.x+1,n.y-4,1,4,n.c);px(n.x+2,n.y-4,2,1,n.c);rc.globalAlpha=1}
  notesFx=notesFx.filter(n=>n.life>0);if(notesFx.length>40)notesFx.splice(0,notesFx.length-40);
}
function drawStudio(dt,now){
  studioClock+=dt;stepMe(dt);catThink(dt);drawRoom(dt,now);
  const depth=[{y:cat.y,f:()=>drawCat(dt,now)},{y:me.y,f:drawMe}].sort((a,b)=>a.y-b.y);depth.forEach(d=>d.f());drawFx(dt);
  if(hover){const r=hover===catObj?catRect():hover.r;rc.strokeStyle='rgba(255,180,84,.8)';rc.lineWidth=1;rc.strokeRect(r[0]-1.5,r[1]-1.5,r[2]+3,r[3]+3)}
}

