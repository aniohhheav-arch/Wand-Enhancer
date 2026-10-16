'use strict';
/* Sounds: one browser for instruments (engines), instrument presets, ensembles, track presets,
   effect chains, project templates and modulation patches — factory and your own. */
const SND={cat:'presets',fam:null,genre:null,mood:null,fav:false,recent:false};
const TRACKPRESETS=[
  {id:'tp-lofikeys',name:'Lo-fi Keys track',desc:'Dusty keys with the Lo-fi tape chain and a warm reverb send.',make:()=>{const t=newTrack('exp-tape',{name:'Lo-fi keys',rev:0.35});t.fx=sanitizeFxList(chainById('fc-lofi'));return trackData(t)}},
  {id:'tp-drumbus',name:'Punchy Drum track',desc:'Studio Acoustic kit through the Punchy drums chain.',make:()=>{const t=newTrack('drums-acoustic',{name:'Drums'});t.fx=sanitizeFxList(chainById('fc-drums'));return trackData(t)}},
  {id:'tp-widepad',name:'Wide Pad track',desc:'Warm Pad with a wide chorus and long reverb.',make:()=>{const t=newTrack('syn-pad',{name:'Wide pad'});t.fx=sanitizeFxList(chainById('fc-widepad'));return trackData(t)}}
];
const CATS=[['engines','Instruments'],['presets','Instrument presets'],['ensembles','Ensembles'],['tracks','Track presets'],['fxchains','Effect chains'],['templates','Project templates'],['modpatches','Modulation patches'],['mine','My content']];
function presetItems(){return FACTORY.map(p=>({kind:'preset',id:p.id,name:p.name,desc:p.desc,tags:p.tags,genre:p.genre||[],mood:p.mood||[],family:p.family,engine:p.engine,factory:true,pre:p}))
  .concat(USER.presets.map(u=>({kind:'preset',id:u.id,name:u.name,desc:u.desc,tags:u.tags||[],genre:u.genre||[],mood:u.mood||[],family:u.data.family||(ENG[u.data.engine]||{}).family,engine:u.data.engine,factory:false,user:u,pre:Object.assign({id:u.id,name:u.name},u.data)})))}
function itemsFor(cat){
  if(cat==='engines')return Object.keys(ENG).filter(k=>!ENG[k].hidden).map(k=>({kind:'engine',id:'eng-'+k,name:ENG[k].name,desc:ENG[k].desc,tags:[ENG[k].kind],genre:[],mood:[],family:ENG[k].family,engine:k,factory:true}));
  if(cat==='presets')return presetItems();
  if(cat==='ensembles')return ENSEMBLES.map(e=>({kind:'ensemble',id:e.id,name:e.name,desc:e.desc,tags:e.tags||[],genre:[],mood:[],factory:true,ens:e})).concat(USER.ensembles.map(u=>({kind:'ensemble',id:u.id,name:u.name,desc:u.desc,tags:u.tags||[],genre:[],mood:[],factory:false,user:u,ens:u.data})));
  if(cat==='tracks')return TRACKPRESETS.map(t=>({kind:'track',id:t.id,name:t.name,desc:t.desc,tags:[],genre:[],mood:[],factory:true,tp:t})).concat(USER.tracks.map(u=>({kind:'track',id:u.id,name:u.name,desc:u.desc,tags:u.tags||[],genre:[],mood:[],factory:false,user:u})));
  if(cat==='fxchains')return FXCHAINS.map(c=>({kind:'fxchain',id:c.id,name:c.name,desc:c.desc,tags:[],genre:[],mood:[],factory:true,fc:c})).concat(USER.fxchains.map(u=>({kind:'fxchain',id:u.id,name:u.name,desc:u.desc||(u.data.fx.length+' effects'),tags:[],genre:[],mood:[],factory:false,user:u})));
  if(cat==='templates')return TEMPLATES.map(t=>({kind:'template',id:t.id,name:t.name,desc:t.desc,tags:[],genre:[],mood:[],factory:true,tpl:t})).concat(USER.templates.map(u=>({kind:'template',id:u.id,name:u.name,desc:u.desc,tags:[],genre:[],mood:[],factory:false,user:u,tpl:u.data})));
  if(cat==='modpatches')return MODPATCHES.map(m=>({kind:'modpatch',id:m.id,name:m.name,desc:m.desc,tags:[],genre:[],mood:[],factory:true,mp:m.data})).concat(USER.modpatches.map(u=>({kind:'modpatch',id:u.id,name:u.name,desc:u.desc,tags:[],genre:[],mood:[],factory:false,user:u,mp:u.data})));
  if(cat==='mine')return ['presets','ensembles','tracks','fxchains','templates','modpatches'].flatMap(c=>itemsFor(c).filter(i=>!i.factory));return []}
function missingDeps(it){const out=[];if(it.kind==='preset'&&it.engine==='fusion')for(let i=0;i<LAYERS;i++){const s=it.pre.params&&it.pre.params['L'+i+'src'];if(it.pre.params&&it.pre.params['L'+i+'on']!==false&&s&&s!=='none'&&!presetById(s))out.push('layer '+(i+1)+' sound "'+s+'"')}
  if(it.kind==='preset'&&!ENG[it.engine])out.push('engine "'+it.engine+'"');if(it.kind==='ensemble')it.ens.members.forEach(m=>{if(!m.track&&!presetById(m.preset))out.push('instrument "'+m.preset+'"')});
  if(it.kind==='preset'&&it.pre.zones)it.pre.zones.forEach(z=>{if(z.sample&&SAMPLE_MISSING.has(z.sample))out.push('sample "'+(z.name||z.sample)+'"')});return out}
function renderSounds(){if(S.view!=='sounds')return;
  $('#catList').innerHTML='<h5>Browse</h5>'+CATS.map(([k,l])=>'<button data-cat="'+k+'" aria-pressed="'+(SND.cat===k&&!SND.fam)+'"><span>'+l+'</span><span class="hint">'+itemsFor(k).length+'</span></button>').join('')+
    '<h5>Instrument families</h5>'+FAMILIES.map(([k,l])=>{const n=presetItems().filter(i=>i.family===k).length;return '<button data-fam="'+k+'" aria-pressed="'+(SND.fam===k)+'"><span>'+l+'</span><span class="hint">'+n+'</span></button>'}).join('');
  let items=SND.fam?presetItems().filter(i=>i.family===SND.fam):itemsFor(SND.cat);
  const q=$('#soundSearch').value.trim().toLowerCase();if(q)items=items.filter(i=>(i.name+' '+i.desc+' '+i.tags.join(' ')+' '+i.genre.join(' ')+' '+i.mood.join(' ')+' '+(i.engine?ENG[i.engine]&&ENG[i.engine].name:'')).toLowerCase().includes(q));
  if(SND.fav)items=items.filter(i=>PREF.favs.includes(i.id));if(SND.recent){items=PREF.recent.map(id=>items.find(i=>i.id===id)).filter(Boolean)}
  const genres=[...new Set(items.flatMap(i=>i.genre))].sort(),moods=[...new Set(items.flatMap(i=>i.mood))].sort();
  $('#genreChips').innerHTML=genres.map(g=>'<button class="chip" data-genre="'+esc(g)+'" aria-pressed="'+(SND.genre===g)+'">'+esc(g)+'</button>').join('')||'<span class="hint">—</span>';
  $('#moodChips').innerHTML=moods.map(g=>'<button class="chip" data-mood="'+esc(g)+'" aria-pressed="'+(SND.mood===g)+'">'+esc(g)+'</button>').join('')||'<span class="hint">—</span>';
  if(SND.genre)items=items.filter(i=>i.genre.includes(SND.genre));if(SND.mood)items=items.filter(i=>i.mood.includes(SND.mood));
  const title=SND.fam?FAMILIES.find(f=>f[0]===SND.fam)[1]:CATS.find(c=>c[0]===SND.cat)[1];
  $('#soundsTitle').textContent=title+(SND.cat==='engines'&&!SND.fam?' · '+Object.keys(ENG).filter(k=>!ENG[k].hidden).length+' sound engines, '+FACTORY.length+' factory presets':'');
  const tr=selTrack(),extra=SND.cat==='templates'&&!SND.fam?'<div class="card" style="--c:var(--accent)"><h4><span>Save this project as a template</span></h4><p>Keeps tracks, sounds, effects, returns, tempo and markers. Notes and clips are left out.</p><div class="acts"><button class="btn primary" data-a="saveTpl">Save as template</button></div></div>'
    :SND.cat==='ensembles'&&!SND.fam?'<div class="card" style="--c:var(--accent)"><h4><span>Save an ensemble</span></h4><p>Saves the selected track'+(tr.input&&tr.input.group?'\'s ensemble':' and every track sharing its keyboard group')+' with zones, layers, effects and channels.</p><div class="acts"><button class="btn primary" data-a="saveEns">Save as ensemble</button></div></div>':'';
  $('#cards').innerHTML=extra+(items.length?items.map(cardHTML).join(''):'<p class="hint" style="padding:10px">Nothing matches. Clear the search or filters.</p>')}
function cardHTML(it){const miss=missingDeps(it),E=it.engine&&ENG[it.engine],col=FAMILY_COLOR[it.family]||'var(--accent)',fav=PREF.favs.includes(it.id);
  const kind=it.kind==='engine'?'Engine':it.kind==='preset'?(E?E.kind:'Preset'):CONTENT_TYPES[{ensemble:'ensemble',track:'track',fxchain:'fxchain',template:'template',modpatch:'modpatch'}[it.kind]];
  const acts={engine:'<button class="btn" data-a="load">Load on selected track</button><button class="btn" data-a="add">Add as new track</button><button class="btn" data-a="preview">▶ Preview</button>',
    preset:'<button class="btn" data-a="preview">▶ Preview</button><button class="btn primary" data-a="load">Load on selected</button><button class="btn" data-a="add">Add as new track</button>',
    ensemble:'<button class="btn" data-a="preview">▶ Preview</button><button class="btn primary" data-a="addEns">Add ensemble</button>',track:'<button class="btn" data-a="preview">▶ Preview</button><button class="btn primary" data-a="addTrack">Add track</button>',
    fxchain:'<button class="btn primary" data-a="chainTrack">Load on selected track</button><button class="btn" data-a="chainMaster">Load on master</button>',template:'<button class="btn primary" data-a="useTpl">New project from template</button>',
    modpatch:'<button class="btn primary" data-a="applyMp">Apply to selected track</button>'}[it.kind];
  return '<div class="card" data-id="'+esc(it.id)+'" data-kind="'+it.kind+'" style="--c:'+col+'"><h4><span title="'+esc(it.name)+'">'+esc(it.name)+'</span><button class="star" data-a="fav" aria-pressed="'+fav+'" aria-label="Favorite">★</button></h4>'+
    '<div class="meta"><span class="badge">'+esc(kind||'')+'</span>'+(E&&it.kind==='preset'?'<span class="badge">'+esc(E.name)+'</span>':'')+'<span class="badge">'+(it.factory?'Factory':'Mine')+'</span>'+(miss.length?'<span class="badge warn" title="Missing: '+esc(miss.join(', '))+'">Missing content</span>':'')+'</div>'+
    '<p>'+esc(it.desc||'')+'</p>'+(miss.length?'<p style="color:var(--rec)">Needs '+esc(miss.join(', '))+'. Those parts stay silent; nothing is substituted.</p>':'')+
    '<div class="acts">'+acts+(it.kind!=='engine'?'<button class="btn" data-a="export">Export</button>':'')+(it.factory&&it.kind==='preset'?'<button class="btn" data-a="copy">Copy to mine</button>':'')+(!it.factory?'<button class="btn" data-a="rename">Rename</button><button class="btn danger" data-a="del">Delete</button>':'')+'</div></div>'}
$('#catList').addEventListener('click',e=>{const b=e.target.closest('button');if(!b)return;if(b.dataset.cat){SND.cat=b.dataset.cat;SND.fam=null}if(b.dataset.fam){SND.fam=b.dataset.fam;SND.cat='presets'}SND.genre=SND.mood=null;renderSounds()});
$('#genreChips').addEventListener('click',e=>{const b=e.target.closest('[data-genre]');if(b){SND.genre=SND.genre===b.dataset.genre?null:b.dataset.genre;renderSounds()}});
$('#moodChips').addEventListener('click',e=>{const b=e.target.closest('[data-mood]');if(b){SND.mood=SND.mood===b.dataset.mood?null:b.dataset.mood;renderSounds()}});
$('#soundSearch').oninput=renderSounds;
$('#favOnly').onclick=()=>{SND.fav=!SND.fav;$('#favOnly').setAttribute('aria-pressed',SND.fav);renderSounds()};$('#recentOnly').onclick=()=>{SND.recent=!SND.recent;$('#recentOnly').setAttribute('aria-pressed',SND.recent);renderSounds()};
function findItem(id,kind){const cats={engine:'engines',preset:'presets',ensemble:'ensembles',track:'tracks',fxchain:'fxchains',template:'templates',modpatch:'modpatches'};return itemsFor(cats[kind]).find(i=>i.id===id)}
function itemPreset(it){return it.kind==='engine'?{id:null,name:ENG[it.engine].name,engine:it.engine,params:{},family:ENG[it.engine].family,oct:ENG[it.engine].oct}:it.pre}
$('#cards').addEventListener('click',async e=>{const b=e.target.closest('[data-a]');if(!b)return;const a=b.dataset.a;
  if(a==='saveTpl'){saveTemplate();return}if(a==='saveEns'){saveEnsemble();return}
  const card=b.closest('.card'),it=findItem(card.dataset.id,card.dataset.kind);if(!it)return;const miss=missingDeps(it);
  if(a==='fav'){PREF.favs=PREF.favs.includes(it.id)?PREF.favs.filter(x=>x!==it.id):PREF.favs.concat(it.id);savePrefs();b.setAttribute('aria-pressed',PREF.favs.includes(it.id));return}
  if(a==='preview'){previewItem(it);return}
  if(miss.length&&['load','add','addEns'].includes(a))toast('Heads up: '+miss.join(', ')+' is missing, so that part stays silent.');
  if(a==='load'){pushUndo();try{applyInstrument(selTrack(),itemPreset(it))}catch(err){undoStack.pop();toast(err.message);return}markDirty();toast('Loaded '+it.name+' on '+selTrack().name+'.');renderSounds();renderArrangeHeads();return}
  if(a==='add'){const tr=addTrackFrom(itemPreset(it));if(it.pre&&it.pre.id){PREF.recent=[it.pre.id].concat(PREF.recent.filter(x=>x!==it.pre.id)).slice(0,16);savePrefs()}selectTrack(S.tracks.length-1);toast('Added "'+tr.name+'" as a new track.');if(UI.addMode){UI.addMode=false;setView('arrange')}return}
  if(a==='addEns'){const r=loadEnsemble(it.ens);selectTrack(S.tracks.length-r.made.length);toast('Added ensemble "'+it.name+'" ('+r.made.length+' instruments). Play the keyboard to hear all of them.'+(r.miss.length?' Missing: '+r.miss.join(', '):''));return}
  if(a==='addTrack'){const data=it.tp?it.tp.make():it.user.data;pushUndo();const t=newTrack(data,{name:data.name,color:data.color,vol:data.vol,pan:data.pan,rev:data.rev,dly:data.dly});t.fx=sanitizeFxList(data.fx);S.tracks.push(t);if(ctx)applyMix();markDirty();selectTrack(S.tracks.length-1);toast('Added track "'+t.name+'". Existing tracks were not changed.');return}
  if(a==='chainTrack'){loadChainOnto(selTrack().id,it.fc?chainById(it.fc.id):it.user.data.fx);return}if(a==='chainMaster'){loadChainOnto('@master',it.fc?chainById(it.fc.id):it.user.data.fx);return}
  if(a==='useTpl'){saveNow();const d=projectFromTemplate(it.tpl);const id=newEntry(it.name,d);writeLib();openProject(id);setView('arrange');toast('New project from the "'+it.name+'" template. It has tracks and sounds but no notes.');return}
  if(a==='applyMp'){pushUndo();const m=applyModPatch(selTrack(),it.mp);markDirty();toast(m.length?'Applied with '+m.length+' route(s) disabled because this instrument lacks: '+[...new Set(m)].join(', '):'Applied "'+it.name+'" to '+selTrack().name+'.');if(m.length){missingReport.length=0;missingReport.push('Modulation patch "'+it.name+'" targets controls this instrument does not have: '+[...new Set(m)].join(', ')+'. Those routes were kept but switched off.');showMissing()}return}
  if(a==='export'){exportItem(it);return}
  if(a==='copy'){const r=addUserItem('instrument',it.name+' (copy)',JSON.parse(JSON.stringify(Object.assign({},it.pre,{id:undefined,factory:undefined}))),{desc:it.desc,genre:it.genre,mood:it.mood,tags:it.tags});toast(r.dup?'Already in your presets as "'+r.dup.name+'".':'Copied to My content.');renderSounds();return}
  if(a==='rename'){const inp=document.createElement('input');inp.type='text';inp.value=it.name;b.replaceWith(inp);inp.focus();inp.select();const done=()=>{const v=inp.value.trim();if(v){it.user.name=v.slice(0,80);saveUser()}renderSounds()};inp.onkeydown=ev=>{if(ev.key==='Enter')done();if(ev.key==='Escape')renderSounds()};inp.onblur=done;return}
  if(a==='del'){if(!b.classList.contains('confirm')){b.classList.add('confirm');b.textContent='Delete for good?';setTimeout(()=>{if(b.isConnected){b.classList.remove('confirm');b.textContent='Delete'}},3000);return}
    const list=USER[USER_LIST[it.user.type]];list.splice(list.indexOf(it.user),1);saveUser();toast('Deleted "'+it.name+'".');renderSounds()}});
function itemExport(it){const t={preset:'instrument',ensemble:'ensemble',track:'track',fxchain:'fxchain',template:'template',modpatch:'modpatch'}[it.kind];
  const data=it.kind==='preset'?instrumentLike(it.pre):it.kind==='ensemble'?it.ens:it.kind==='track'?(it.tp?it.tp.make():it.user.data):it.kind==='fxchain'?{fx:it.fc?chainById(it.fc.id):it.user.data.fx}:it.kind==='template'?(it.tpl.tracks&&it.tpl.tracks[0]&&it.tpl.tracks[0].preset?projectToTemplate(projectFromTemplate(it.tpl)):it.tpl):it.mp;
  return envelope(t,it.name,JSON.parse(JSON.stringify(data)),{desc:it.desc,author:it.factory?'Soundcraft':'Me'})}
const instrumentLike=p=>({engine:p.engine,params:p.params,mod:p.mod,arp:p.arp,poly:p.poly,zones:p.zones,layerOver:p.layerOver,oct:p.oct,family:p.family});
async function exportItem(it){const env=itemExport(it);const ids=new Set();(env.data.zones||[]).forEach(z=>z.sample&&ids.add(z.sample));
  if(ids.size){env.samples={};for(const id of ids){const r=await getSample(id).catch(()=>null);if(r)env.samples[id]={name:r.name,b64:b64(r.bytes)}}}
  saveBlob(new Blob([JSON.stringify(env,null,1)],{type:'application/json'}),fileSafe(it.name)+'.soundcraft-'+env.type+'.json')}
function projectToTemplate(d){return {bpm:d.bpm,markers:d.markers,returns:{rev:d.returns.rev.params,dly:d.returns.dly.params},masterFx:d.masterFx.map(f=>({type:f.type,params:f.params})),tracks:d.tracks.map(t=>Object.assign(trackData(t),{patterns:[],clips:[]}))}}
function saveTemplate(){saveNow();const d=projectToTemplate(snapshot());const r=addUserItem('template',$('#projTitle').value+' template',d,{desc:d.tracks.length+' tracks at '+d.bpm+' BPM. No notes.'});toast(r.dup?'That template already exists.':'Saved template "'+$('#projTitle').value+' template".');renderSounds()}
function saveEnsemble(){const tr=selTrack(),grp=tr.input&&tr.input.group,mem=grp?S.tracks.filter(t=>t.input&&t.input.group===grp):[tr];
  const data={name:(tr.input&&tr.input.ens)||tr.name+' ensemble',desc:mem.map(t=>t.name).join(', '),members:mem.map(t=>Object.assign({track:trackData(t)},{lo:t.input.lo,hi:t.input.hi,vlo:t.input.vlo,vhi:t.input.vhi,ch:t.input.ch,tr:t.input.tr,vol:t.vol,pan:t.pan,name:t.name}))};
  const r=addUserItem('ensemble',data.name,data,{desc:data.desc});toast(r.dup?'That ensemble is already saved.':'Saved ensemble "'+data.name+'" with '+mem.length+' instruments.');renderSounds()}
$('#contentImport').onclick=()=>$('#contentFile').click();
$('#contentFile').onchange=async e=>{const f=e.target.files[0];e.target.value='';if(!f)return;if(f.size>60*1024*1024){toast('That file is too large to be a preset.');return}
  let obj;try{obj=JSON.parse(await f.text())}catch(err){toast('That file is not valid JSON, so it was not imported.');return}
  if(obj&&obj.type==='project'){toast('That is a project. Import it from the Cassettes library.');return}
  try{const {warn,deps}=validateImport(obj);if(obj.samples)await importEmbeddedSamples(obj.samples);
    const type=obj.type,r=addUserItem(type,obj.name||f.name.replace(/\.json$/i,''),obj.data,{desc:String(obj.desc||'').slice(0,300),author:String(obj.author||'Unknown').slice(0,60)});
    const stillMissing=deps.filter(d=>d.kind==='preset'?!presetById(d.id):d.kind==='sample'?!(obj.samples&&obj.samples[d.id]):false);
    missingReport.length=0;if(stillMissing.length)missingReport.push('"'+obj.name+'" depends on content that is not installed: '+stillMissing.map(d=>d.name+' ('+d.id+')').join(', ')+'. It was imported; those parts stay silent until the content is available.');warn.forEach(w=>missingReport.push(w));
    toast(r.dup?'Already in your library as "'+r.dup.name+'".':r.item?'Imported '+CONTENT_TYPES[type].toLowerCase()+' "'+r.item.name+'".':'Could not save the import.');if(missingReport.length)showMissing();SND.cat='mine';SND.fam=null;renderSounds()}
  catch(err){toast('Not imported: '+err.message)}};
/* previews play through a temporary track that is never part of the project */
let previewTracks=[];
function stopPreview(){previewTracks.forEach(t=>{try{disposeTrack(t)}catch(e){}});previewTracks=[]}
function previewItem(it){if(!ensureAudio())return;const pre=it.kind!=='engine'&&it.kind!=='track'?itemPreset(it):null;
  if(pre&&(pre.engine==='orch'||pre.engine==='section')){const tmp={engine:pre.engine,params:Object.assign(defaultsOf(ENG[pre.engine].params),pre.params)};const need=trackOrchNeeds(tmp);
    if(!need.every(([i,a])=>orchReady(i,a))){toast('Loading the recordings…');Promise.all(need.map(([i,a])=>orchLoad(i,a))).then(()=>previewNow(it));return}}
  previewNow(it)}
function previewNow(it){stopPreview();const t0=ctx.currentTime+0.05;
  const playOn=(pre,notes)=>{const tr=newTrack(pre);tr.vol=0.8;tr.rev=0.2;if(it.kind==='track'){tr.fx=sanitizeFxList(pre.fx)}buildTrack(tr);applyMixOne(tr);previewTracks.push(tr);notes.forEach(([n,s,l,v])=>{const h=trigger(tr,n,v||0.8,t0+s);if(h)h.off(t0+s+l)})};
  const phrase=pre=>{const fam=pre.family||(ENG[pre.engine]||{}).family,b=(pre.oct||4)*12+12;
    if(ENG[pre.engine]&&ENG[pre.engine].drum){const N=i=>DRUM_NOTES[i];return [[N(0),0,0.2],[N(5),0,0.1,0.6],[N(5),0.27,0.1,0.5],[N(2),0.54,0.2],[N(5),0.54,0.1,0.6],[N(0),0.81,0.2],[N(0),1.08,0.2],[N(5),1.08,0.1],[N(2),1.62,0.2],[N(7),1.89,0.2,0.6]]}
    if(fam==='bass')return [[b,0,0.25],[b,0.3,0.25],[b+7,0.6,0.25],[b+5,0.9,0.5]];
    if(['brass','woodwinds','strings'].includes(fam)||pre.engine==='glitch')return [[b,0,0.35],[b+2,0.4,0.35],[b+4,0.8,0.35],[b+7,1.2,0.8]];
    if(fam==='experimental')return [[b,0,1.8],[b+7,0.2,1.6,0.6]];return [[b,0,1.2,0.7],[b+4,0,1.2,0.7],[b+7,0,1.2,0.7],[b+12,1.3,0.6]]};
  if(it.kind==='engine'||it.kind==='preset'){const pre=itemPreset(it);playOn(pre,phrase(pre))}
  else if(it.kind==='track'){const d=it.tp?it.tp.make():it.user.data;playOn(d,phrase(d))}
  else if(it.kind==='ensemble'){it.ens.members.forEach(m=>{const pre=m.track||presetById(m.preset);if(!pre)return;const mid=Math.round(((m.lo||0)+(m.hi==null?127:m.hi))/2),n=clamp(Math.max(m.lo||0,Math.min(m.hi==null?127:m.hi,mid)),0,127);playOn(pre,[[n+(m.tr||0),0,1.4,0.8],[n+(m.tr||0)+4,0.5,0.9,0.7]])})}
  setTimeout(stopPreview,6000);quest('preview')}
function applyMixOne(tr){const rt=tr._rt,now=ctx.currentTime;rt.vol.gain.setTargetAtTime(tr.vol*tr.vol,now,0.01);rt.pan.pan.value=tr.pan;rt.rev.gain.value=tr.rev;rt.dly.gain.value=tr.dly}
