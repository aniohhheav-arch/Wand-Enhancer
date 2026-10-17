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
  const W=PREF.weather,day=W==='morning'||W==='evening',rm=reduced(),beat=S.playing?A.bass:0;
  const sh=(x,y,w,h,a)=>{rc.fillStyle='rgba(10,8,30,'+a+')';rc.fillRect(Math.round(x),Math.round(y),w,h)};
  // wallpaper: two-tone vertical stripes with a small diamond motif
  const wA=day?'#4d4580':'#2e2a5c',wB=day?'#554d8a':'#332f66',wC=day?'#6a60a0':'#3e3a78';
  px(0,0,RW,FLOOR,wA);for(let x=0;x<RW;x+=16)px(x+8,0,8,FLOOR-22,wB);for(let x=4;x<RW;x+=16)for(let y=8;y<FLOOR-26;y+=14){px(x+8,y,1,1,wC);px(x+7,y+1,3,1,wC);px(x+8,y+2,1,1,wC)}
  // wainscoting + rail
  px(0,FLOOR-22,RW,22,day?'#5b3f37':'#45302d');for(let x=2;x<RW;x+=20){px(x,FLOOR-19,16,15,day?'#674940':'#4f3733');px(x,FLOOR-19,16,1,day?'#7a5a4e':'#5d433d')}
  px(0,FLOOR-23,RW,2,day?'#8b6447':'#6e4f3a');px(0,FLOOR-21,RW,1,'#3a2722');
  // floor: shaded planks
  for(let y=FLOOR;y<RH;y+=6){const k=((y-FLOOR)/6)|0;px(0,y,RW,6,k%2?'#4f3631':'#563b35');px(0,y+5,RW,1,'#3b2824');for(let x=(k*23)%40;x<RW;x+=40)px(x,y,1,5,'#3b2824')}
  px(0,FLOOR,RW,2,'#2a1c1a');
  // window light pool on the floor
  if(day||W==='night'||W==='snow'){rc.fillStyle=day?'rgba(255,220,160,.10)':'rgba(170,190,255,.07)';rc.beginPath();rc.moveTo(18,FLOOR);rc.lineTo(70,FLOOR);rc.lineTo(110,RH);rc.lineTo(30,RH);rc.fill()}
  // rug with border pattern
  rc.fillStyle='#4a3366';rc.beginPath();rc.ellipse(118,127,72,10,0,0,Math.PI*2);rc.fill();rc.fillStyle='#6b4f8a';rc.beginPath();rc.ellipse(118,127,66,8,0,0,Math.PI*2);rc.fill();
  rc.fillStyle='#8a6aa6';rc.beginPath();rc.ellipse(118,127,50,5,0,0,Math.PI*2);rc.fill();for(let i=0;i<16;i++){const a=i/16*Math.PI*2;px(118+Math.cos(a)*58,127+Math.sin(a)*7,2,1,'#e8a0bb')}px(108,126,20,2,'#ffb454');
  // window: sky, skyline, weather, frame, curtains, sill
  const sky=SKY[W],g=rc.createLinearGradient(0,14,0,58);g.addColorStop(0,sky[0]);g.addColorStop(1,sky[1]);rc.fillStyle=g;rc.fillRect(18,14,52,44);
  if(W==='night'||W==='snow'||W==='rain'){for(let i=0;i<14;i++){const sx=20+(i*29)%48,sy=16+(i*17)%30;if(W!=='night'&&i%2)continue;px(sx,sy,1,1,(Math.floor(now/600+i)%5)?'#f4ead6':'#8a86b8')}px(58,19,6,6,'#f4ead6');px(60,19,4,4,sky[0])}
  if(W==='evening'){px(26,40,10,10,'#ffd28a');px(24,42,14,6,'#ffd28a')}if(W==='morning'){px(52,22,8,8,'#fff3cf');px(50,24,12,4,'#fff3cf')}
  for(let i=0;i<7;i++){const bx=18+i*8,bh=8+((i*13)%11);px(bx,58-bh,7,bh,day?'#6a5c8c':'#191d3d');px(bx,58-bh,7,1,day?'#7d70a0':'#262b52');if(!day)for(let j=0;j<3;j++)if((i+j)%2)px(bx+2,58-bh+2+j*3,1,1,'#ffb454')}
  if(W==='rain'||W==='storm'){rc.fillStyle='rgba(180,200,255,.55)';const n=W==='storm'?26:16,sp=rm?0.25:1;for(let i=0;i<n;i++){const x=18+((i*53+Math.floor(now*0.05*sp))%52),y=14+((i*31+now*0.09*sp)%44);rc.fillRect(Math.floor(x),Math.floor(y),1,3)}}
  if(W==='snow'){rc.fillStyle='#f4ead6';const sp=rm?0.2:1;for(let i=0;i<18;i++){const x=18+((i*23+Math.sin(now/900+i)*3*sp)%52+52)%52,y=14+((i*19+now*0.012*sp)%44);rc.fillRect(Math.floor(x),Math.floor(y),1,1)}}
  if(W==='storm'&&!rm){if(Math.random()<dt*0.12)flash=1;flash=Math.max(0,flash-dt*3)}else flash=0;
  const fr='#8a6248',frD='#5e412f';px(16,12,56,3,fr);px(16,57,56,3,fr);px(16,12,3,48,fr);px(69,12,3,48,fr);px(43,12,2,48,fr);px(16,35,56,2,fr);px(16,15,56,1,frD);px(19,15,1,42,frD);
  // curtains with folds
  const cur=day?'#c86b7e':'#9c4f66',curD=day?'#a9566a':'#7d3d52';px(8,8,64+16,3,'#6e4f3a');[[8,10],[70,10]].forEach(([cx],k)=>{for(let i=0;i<10;i++)px(cx+i,10,1,56-((i*3)%5),i%3===0?curD:cur);px(cx+(k?0:8),40,3,2,'#ffb454')});
  px(10,60,70,4,'#9b7354');px(10,64,70,1,'#5e412f');px(56,55,3,5,'#f4ead6');if(!rm&&(W==='night'||W==='rain'||W==='storm'||W==='snow')){px(57,53-((now/120|0)%2),1,2,'#ffcf6e')}
  // framed poster + vinyls on the wall
  px(81,17,22,28,'#2a1f1a');px(82,18,20,26,'#e8a0bb');px(84,20,16,22,'#f4ead6');px(86,22,12,9,'#ffb454');px(87,26,10,5,'#ef9a6a');px(88,33,8,1,'#2c2858');px(86,36,12,1,'#2c2858');px(88,39,8,1,'#2c2858');
  [[150,22,'#e8a0bb'],[166,16,'#79c3e0']].forEach(([x,y,c])=>{rc.fillStyle='#16142b';rc.beginPath();rc.arc(x,y,6,0,7);rc.fill();rc.fillStyle='#24214a';rc.beginPath();rc.arc(x,y,4,0,7);rc.fill();px(x-1,y-1,3,3,c);px(x,y-8,1,2,'#6e4f3a')});
  // fairy lights (pulse with the music)
  for(let i=0;i<24;i++){const x=78+i*7,y=8+Math.sin(i*0.55)*3+3;px(x,y,7,1,'#1c1a3d');const on=(Math.sin(now/400+i*1.7)+1)/2*0.5+0.4+beat*1.2,c=['#ffcf6e','#e8a0bb','#8fcaa2','#79c3e0'][i%4];rc.globalAlpha=Math.min(1,on);px(x+3,y+1,2,2,c);rc.globalAlpha=Math.min(0.3,on*0.22);px(x+2,y+1,4,2,c);px(x+3,y,2,4,c);rc.globalAlpha=1}
  // shelves with books, tapes, groovebox, plant
  px(188,34,60,3,'#9b7354');px(188,37,60,1,'#5e412f');px(188,50,60,3,'#9b7354');px(188,53,60,1,'#5e412f');sh(188,38,60,2,0.25);sh(188,54,60,2,0.25);
  const tapeCols=Object.values(LIB).slice(0,9).map(e=>e.color);for(let i=0;i<9;i++){const c=tapeCols[i]||'#463f6e',h=10+(i%3);px(190+i*6,34-h,5,h,c);px(190+i*6,34-h,5,1,'#ffffff33');px(191+i*6,36-h,3,2,'#f4ead6')}
  px(192,40,22,10,'#3b3768');px(192,40,22,1,'#5a5590');for(let i=0;i<4;i++)for(let j=0;j<2;j++){const on=S.playing&&posAtNow()!=null&&Math.floor(posAtNow())%4===i;px(194+i*5,42+j*4,3,2,on?'#ef9a6a':'#5a5590')}
  px(220,43,7,7,'#a5603f');px(219,42,9,2,'#bd7350');for(let i=0;i<4;i++)px(219+i*2,37+(i%2)*2,2,6,i%2?'#6b9b78':'#8fcaa2');px(232,44,10,6,'#79c3e0');px(234,42,6,2,'#f4ead6');px(244,41,3,9,'#e9e0cc');
  // hanging plant
  px(140,0,1,20,'#6e4f3a');px(135,20,11,6,'#a5603f');px(134,19,13,2,'#bd7350');const sw=rm?0:Math.sin(now/1500);for(let i=0;i<6;i++)px(133+i*2+Math.round(sw*(i%2)),24+(i%3)*4,2,6+(i%2)*4,i%2?'#6b9b78':'#8fcaa2');
  // floor plant
  sh(5,103,15,2,0.35);px(6,90,12,14,'#a5603f');px(6,90,12,1,'#c9845e');px(5,88,14,3,'#bd7350');const sway=rm?0:Math.sin(now/1300)*1;for(let i=0;i<6;i++)px(5+i*2+sway*(i%2),70+(i%3)*5,3,18-(i%3)*4,i%2?'#6b9b78':'#8fcaa2');px(9,68,2,4,'#e8a0bb');
  // couch with cushions and a throw
  sh(194,102,58,2,0.4);px(196,76,54,12,'#5c4d8c');px(196,76,54,1,'#7a6ab0');px(194,86,58,13,'#7d6cb0');px(194,86,58,1,'#9585c8');px(193,84,7,18,'#5c4d8c');px(246,84,7,18,'#5c4d8c');
  px(200,99,46,3,'#4a3d78');px(198,102,3,2,'#2a1c1a');px(245,102,3,2,'#2a1c1a');px(203,80,13,8,'#e8a0bb');px(203,80,13,1,'#f2bfd1');px(230,81,12,7,'#ffb454');px(230,81,12,1,'#ffcf86');
  for(let i=0;i<5;i++)px(222+i*3,86,2,12,i%2?'#8fcaa2':'#6b9b78');
  // record cabinet + turntable
  sh(74,103,34,2,0.4);px(76,86,30,18,'#6b4a3a');px(76,86,30,1,'#8b6447');px(78,90,12,12,'#5a3d30');px(92,90,12,12,'#5a3d30');px(88,94,1,4,'#c9a86a');px(93,94,1,4,'#c9a86a');px(74,84,34,3,'#9b7354');
  for(let i=0;i<5;i++)px(80+i*2,92,1,9,['#e8a0bb','#79c3e0','#ffb454','#8fcaa2','#a796e8'][i]);
  px(78,78,26,6,'#3a3460');px(78,78,26,1,'#4e4880');const rot=S.playing&&!rm?now/180:0;rc.fillStyle='#14122a';rc.beginPath();rc.arc(88,79,6,Math.PI,0);rc.fill();px(87,77,2,2,'#e8a0bb');
  if(S.playing){px(88+Math.round(Math.cos(rot)*4),78+Math.round(Math.sin(rot)*1),1,1,'#8a86b8')}px(99,74,1,6,'#c9c5dc');px(96,74,4,1,'#c9c5dc');
  // desk
  sh(110,103,72,2,0.4);px(110,80,72,4,'#9b7354');px(110,80,72,1,'#b98d68');px(110,84,72,1,'#5e412f');px(112,85,4,19,'#6b4a3a');px(176,85,4,19,'#6b4a3a');px(160,85,16,12,'#6b4a3a');px(162,88,12,1,'#5e412f');px(167,91,2,1,'#c9a86a');
  // chair
  sh(132,106,16,2,0.35);px(132,96,16,3,'#3d3a6a');px(139,99,2,7,'#2a2850');px(134,106,12,2,'#2a2850');px(134,86,12,10,'#46428a');px(134,86,12,1,'#5c58a8');
  // speakers (cones move with the bass)
  const cone=A.bass*(rm?0.4:1.6);[[112,62],[160,62]].forEach(([sx,sy])=>{px(sx,sy,10,18,'#211e3a');px(sx+1,sy+1,8,16,'#35315a');px(sx+1,sy+1,8,1,'#4a4678');rc.fillStyle='#16142b';rc.beginPath();rc.arc(sx+5,sy+11,2.5+cone,0,Math.PI*2);rc.fill();px(sx+4,sy+3,2,2,'#16142b');px(sx+4,sy+10,1,1,'#5a5690')});
  // monitor with a glow and the real waveform
  if(!day){rc.fillStyle='rgba(121,195,224,.10)';rc.beginPath();rc.ellipse(140,64,26,18,0,0,7);rc.fill()}
  px(124,52,32,22,'#16142b');px(126,54,28,17,'#0e1830');px(126,54,28,1,'#1c2a4a');px(138,74,4,4,'#16142b');px(134,78,12,2,'#16142b');
  if(GR.analyser&&A.level>0.001){rc.fillStyle='#ffb454';for(let x=0;x<28;x++){const v=tbuf[x*16]||0;rc.fillRect(126+x,62+Math.round(clamp(v*7,-7,7)),1,1)}}
  else{px(128,57,10,2,'#8fcaa2');px(128,61,16,2,'#e8a0bb');px(128,65,7,2,'#79c3e0');px(140,57,2,2,'#ffb454')}
  // keyboard, mug, figurine
  px(122,78,36,4,'#e9e0cc');px(122,81,36,1,'#a89c88');for(let i=0;i<12;i++)px(123+i*3,78,1,3,'#a89c88');for(let i=0;i<12;i++)if([1,2,4,5,6].includes(i%7))px(124+i*3,78,1,2,'#16142b');
  if(keyCounts.size||held.size){px(122+((studioClock*7)|0)%34,78,2,3,selTrack().color)}
  px(116,75,4,5,'#e8a0bb');px(120,76,1,2,'#e8a0bb');if(!rm){px(117,72+Math.sin(now/400)*0.5,1,2,'rgba(244,234,214,.4)')}
  if(A.kick&&!rm)figBounce=1;figBounce=Math.max(0,figBounce-dt*6);const fb=Math.round(Math.sin(figBounce*Math.PI)*3);px(150,76-fb,4,4,'#8fcaa2');px(150,74-fb,4,2,'#79c3e0');
  // lamp
  px(166,78,10,2,'#3b3768');px(170,62,2,16,'#3b3768');px(164,56,12,7,PREF.lamp?'#ffb454':'#5a5590');px(164,56,12,1,PREF.lamp?'#ffe0a8':'#6a66a0');px(166,54,8,2,PREF.lamp?'#ffcf86':'#4a4678');
  // light, atmosphere, vignette
  const dark=day?0.06:(W==='storm'?0.4:0.3);rc.fillStyle='rgba(8,7,24,'+dark+')';rc.fillRect(0,0,RW,RH);
  lampPulse+=((PREF.lamp?0.5+A.bass*(rm?0.15:0.6):0)-lampPulse)*Math.min(1,dt*12);
  if(lampPulse>0.01){const lg=rc.createRadialGradient(170,66,2,170,82,80);lg.addColorStop(0,'rgba(255,190,110,'+(0.45*lampPulse)+')');lg.addColorStop(1,'rgba(255,190,110,0)');rc.fillStyle=lg;rc.fillRect(80,10,176,134)}
  if(A.rev>0.02){rc.fillStyle='rgba(172,164,207,'+Math.min(0.15,A.rev*0.25)+')';for(let i=0;i<3;i++){const y=30+i*26+(rm?0:Math.sin(now/2000+i)*3);rc.fillRect(0,Math.round(y),RW,6)}}
  const vg=rc.createRadialGradient(RW/2,RH/2,RH*0.45,RW/2,RH/2,RW*0.62);vg.addColorStop(0,'rgba(0,0,0,0)');vg.addColorStop(1,'rgba(6,5,20,.45)');rc.fillStyle=vg;rc.fillRect(0,0,RW,RH);
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

