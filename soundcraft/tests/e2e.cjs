const { chromium } = require(process.env.PLAYWRIGHT_MODULE||'playwright');
const path='file://'+require('path').resolve(__dirname,'../index.html');
const OUT=process.argv[2]||require('os').tmpdir();
const results=[];const ok=(name,cond,info='')=>{results.push((cond?'PASS ':'FAIL ')+name+(info?' — '+info:''));};
(async()=>{
  const browser=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome',args:['--autoplay-policy=no-user-gesture-required','--use-fake-ui-for-media-stream']}).catch(async()=>chromium.launch({args:['--autoplay-policy=no-user-gesture-required']}));
  const ctx=await browser.newContext({viewport:{width:1280,height:900},acceptDownloads:true});
  const page=await ctx.newPage();const errors=[];
  page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error'&&!/fonts\.g/.test(m.text()))errors.push(m.text())});
  await page.goto(path);await page.waitForTimeout(800);
  ok('boots in Studio view',await page.evaluate(()=>document.body.classList.contains('v-studio')));
  await page.screenshot({path:OUT+'/shot-studio.png'});
  // 1 create project
  await page.click('#btnLibrary');await page.click('#libNew');
  ok('create project',await page.evaluate(()=>Object.keys(LIB).length===2&&LIB[curId].title.startsWith('Untitled')));
  // 2 piano roll edit: switch to arrange, select pad track (index 2), draw note
  await page.click('#tabArrange');await page.waitForTimeout(200);
  await page.evaluate(()=>selectTrack(2));await page.waitForTimeout(100);
  const box=await page.locator('#roll').boundingBox();const wrapBox=await page.locator('#rollWrap').boundingBox();
  const before=await page.evaluate(()=>selTrack().notes.length);
  const x0=wrapBox.x+80, y0=wrapBox.y+150;
  await page.mouse.move(x0,y0);await page.mouse.down();await page.mouse.move(x0+90,y0,{steps:6});await page.mouse.up();
  const after=await page.evaluate(()=>selTrack().notes.map(n=>({...n})));
  ok('draw note with drag length',after.length===before+1&&after[after.length-1].l>1,JSON.stringify(after[after.length-1]));
  // move note
  const n0=after[after.length-1];
  await page.mouse.move(x0+5,y0);await page.mouse.down();await page.mouse.move(x0+5+60,y0-28,{steps:6});await page.mouse.up();
  const moved=await page.evaluate(()=>{const n=selTrack().notes;return n[n.length-1]});
  ok('move note (time + pitch)',moved.s!==n0.s&&moved.p!==n0.p,JSON.stringify(moved));
  await page.keyboard.press('Control+z');
  const undone=await page.evaluate(()=>{const n=selTrack().notes;return n[n.length-1]});
  ok('undo restores note position',undone.s===n0.s&&undone.p===n0.p);
  await page.keyboard.press('Control+Shift+z');
  ok('redo',await page.evaluate(m=>{const n=selTrack().notes;return n[n.length-1].s===m.s},moved));
  // 3 play instrument: hear audio
  await page.keyboard.down('a');await page.waitForTimeout(400);
  const lvl=await page.evaluate(()=>A.level);await page.keyboard.up('a');
  ok('playing a key produces real audio output',lvl>0.005,'rms='+lvl.toFixed(4));
  // 4 sequencer plays notes; add drum note to new project drums and play
  await page.evaluate(()=>{S.tracks[0].notes.push({p:36,s:0,l:1,v:1},{p:36,s:4,l:1,v:1},{p:36,s:8,l:1,v:1},{p:36,s:12,l:1,v:1});});
  await page.click('#btnPlay');await page.waitForTimeout(1500);
  const pl=await page.evaluate(()=>({lvl:A.level,pos:posAtNow(),lcd:document.querySelector('#lcdPos').textContent}));
  ok('transport plays sequenced notes',pl.lvl>0.005&&pl.pos!=null,JSON.stringify(pl));
  // 5 mixer: mute all tracks -> silence
  await page.evaluate(()=>{S.tracks.forEach(t=>t.mute=true);applyMix()});await page.waitForTimeout(300);
  const trk=await page.evaluate(()=>Math.max(...S.tracks.map(t=>{t._an.getFloatTimeDomainData(mbuf);let s=0;for(const v of mbuf)s+=v*v;return Math.sqrt(s/mbuf.length)})));
  ok('mute zeroes every track signal immediately',trk<1e-6,'max track rms='+trk);
  await page.waitForTimeout(4300);const muted=await page.evaluate(()=>A.level);
  await page.evaluate(()=>{S.tracks.forEach(t=>t.mute=false);applyMix()});
  ok('master is silent once the reverb tail ends',muted<0.002,'rms='+muted.toFixed(5));
  await page.click('#btnStop');await page.waitForTimeout(200);
  // 7 export WAV real file
  const dl=page.waitForEvent('download');await page.click('#btnExport');const d=await dl;const fp=OUT+'/export.wav';await d.saveAs(fp);
  const fs=require('fs');const buf=fs.readFileSync(fp);let peak=0;for(let i=44;i<buf.length;i+=2)peak=Math.max(peak,Math.abs(buf.readInt16LE(i)));
  ok('export writes a playable WAV',buf.toString('ascii',0,4)==='RIFF'&&buf.toString('ascii',8,12)==='WAVE'&&peak>1000,`bytes=${buf.length} peak=${peak} file=${d.suggestedFilename()}`);
  // zip helper sanity
  const zipOk=await page.evaluate(async()=>{const b=zipOne('a.wav',new Uint8Array([1,2,3,4]));const u=new Uint8Array(await b.arrayBuffer());return u[0]===0x50&&u[1]===0x4b&&u.length===30+5+4+46+5+22});
  ok('zip container well formed',zipOk);
  // 6 save, close, reopen
  await page.evaluate(()=>saveNow());const sig=await page.evaluate(()=>JSON.stringify(S.tracks.map(t=>t.notes.length))+curId);
  await page.close();const p2=await ctx.newPage();p2.on('pageerror',e=>errors.push(e.message));await p2.goto(path);await p2.waitForTimeout(600);
  const sig2=await p2.evaluate(()=>JSON.stringify(S.tracks.map(t=>t.notes.length))+curId);
  ok('project reopens with its notes intact',sig===sig2,sig2);
  ok('reopens in last view (arrange)',await p2.evaluate(()=>S.view==='arrange'));
  await p2.screenshot({path:OUT+'/shot-arrange.png',fullPage:true});
  // 8/9 studio: click computer walks + opens arrange; visuals react
  await p2.click('#tabStudio');await p2.evaluate(()=>openProject(Object.values(LIB).find(e=>e.title==='Midnight Rain').id));
  await p2.click('#btnPlay');await p2.waitForTimeout(1200);
  const vis=await p2.evaluate(()=>({bass:A.bass,lamp:lampPulse,rev:A.rev}));
  ok('studio visuals read real analysis',vis.bass>0.1&&vis.lamp>0.3,JSON.stringify(vis));
  await p2.screenshot({path:OUT+'/shot-studio-playing.png'});
  // 10 animation does not break scheduling: measure note timing jitter via stepLog spacing
  const jitter=await p2.evaluate(()=>{const l=stepLog.slice(-20);let m=0;for(let i=1;i<l.length;i++)m=Math.max(m,Math.abs((l[i][1]-l[i-1][1])-l[i-1][2]));return m});
  ok('step clock stays sample-accurate during studio animation',jitter<1e-6,'max drift '+jitter);
  const rb=await p2.locator('#room').boundingBox();
  await p2.mouse.click(rb.x+140/256*rb.width,rb.y+62/144*rb.height);await p2.waitForTimeout(2500);
  ok('clicking the computer walks over and opens Arrange',await p2.evaluate(()=>S.view==='arrange'));
  await p2.click('#tabStudio');
  // pet cat
  const cp=await p2.evaluate(()=>[cat.x,cat.y-3]);await p2.mouse.click(rb.x+cp[0]/256*rb.width,rb.y+cp[1]/144*rb.height);await p2.waitForTimeout(3000);
  ok('cat can be petted',await p2.evaluate(()=>cat.petT>0||particles.some(p=>p.kind==='heart')));
  ok('audio still playing after studio interactions',await p2.evaluate(()=>S.playing&&A.level>0.005));
  // lamp toggle
  await p2.evaluate(()=>OBJ.find(o=>o.id==='lamp').act());ok('lamp toggles and persists',await p2.evaluate(()=>JSON.parse(localStorage.getItem(PREF_KEY)).lamp===false));
  // invalid import is rejected
  const bad=await p2.evaluate(()=>{try{migrate({foo:1});return false}catch(e){return e.message}});ok('invalid project rejected with a clear error',!!bad,bad);
  await p2.click('#btnStop');
  // phone width overflow
  await p2.setViewportSize({width:390,height:844});await p2.waitForTimeout(300);
  const ov=await p2.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);ok('no horizontal scroll at phone width (studio)',ov<=0,'overflow='+ov);
  await p2.click('#tabArrange');await p2.waitForTimeout(300);const ov2=await p2.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);ok('no horizontal scroll at phone width (arrange)',ov2<=0,'overflow='+ov2);
  await p2.screenshot({path:OUT+'/shot-phone.png'});
  ok('no page errors',errors.length===0,errors.join(' | '));
  console.log(results.join('\n'));await browser.close();
})().catch(e=>{console.log(results.join('\n'));console.error('TEST CRASH',e);process.exit(1)});
