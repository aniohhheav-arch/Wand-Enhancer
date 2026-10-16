'use strict';
/* Factory content and the shared preset format.
   Every factory instrument preset is a saved configuration of one engine in ENG. Ensembles,
   track presets, effect chains, templates and modulation patches all reference these presets or
   the registry; nothing here contains audio recordings. */
const FAMILIES=[['keys','Keys'],['brass','Brass'],['strings','Strings'],['woodwinds','Woodwinds'],['synths','Synthesizers'],['bass','Bass & guitars'],['drums','Drums & percussion'],['experimental','Experimental'],['fusion','Fusion Lab'],['sampler','Sampler']];
const lfoS=(id,o)=>Object.assign({id,type:'lfo',name:'LFO',color:'#79c3e0',wave:'sine',sync:'free',rate:1,retrig:false,steps:Array.from({length:16},(_,i)=>Math.sin(i/16*Math.PI*2))},o);
const stepS=(id,o)=>Object.assign({id,type:'step',name:'Steps',color:'#ef9a6a',sync:'1/16',rate:4,steps:[1,0,0.5,0,0.8,0,0.3,0,1,0.2,0.6,0,0.8,0,0.4,0.1],smooth:0},o);
const envS=(id,o)=>Object.assign({id,type:'env',name:'Env',color:'#8fcaa2',a:0.01,d:0.4,s:0.3,r:0.4},o);
const rt_=(src,dst,amt,o)=>Object.assign({id:'r'+Math.random().toString(36).slice(2,8),src,dst,amt,on:true,curve:'lin',bip:false,min:0,max:1,smooth:0,cc:1,name:'',color:''},o);
const mac=(id,name,value,maps)=>({id,name,value,cc:null,maps:maps.map(([dst,min,max,inv])=>({dst,min,max,inv:!!inv,curve:'lin'}))});
const modOf=(sources=[],routes=[],macros=[])=>({sources,routes,macros});
const FACTORY=[];
function fp(id,name,engine,family,params,o={}){FACTORY.push(Object.assign({id,name,engine,family,params,tags:[],genre:[],mood:[],desc:'',author:'Soundcraft',version:1,factory:true},o))}
/* Keys */
fp('keys-grand','Concert Grand','piano','keys',{},{tags:['piano','acoustic'],genre:['classical','pop','film'],mood:['warm'],desc:'Bright, open modeled grand.',oct:4});
fp('keys-upright','Upright Piano','piano','keys',{detune:3,bright:0.7,decay:0.8,body:0.55,stretch:0.55},{tags:['piano'],genre:['indie','folk'],mood:['nostalgic'],desc:'Slightly honky, shorter decay, more body.',oct:4});
fp('keys-felt','Soft Felt Piano','piano','keys',{felt:0.85,bright:0.3,hardness:0.15,release:0.6,body:0.5},{tags:['piano','soft'],genre:['ambient','lofi','film'],mood:['intimate','calm'],desc:'Muted felt hammers for quiet, close-up playing.',oct:4,mod:modOf([],[rt_('vel','eng:bright',0.4,{bip:true})])});
fp('keys-ep','Electric Piano','fm','keys',{},{tags:['ep','fm'],genre:['pop','rnb'],mood:['smooth'],desc:'Classic FM tine piano with stereo tremolo.',oct:4});
fp('keys-tine','Tine Keys (Rhodes-inspired)','fm','keys',{ratio:1,index:1.4,indexSus:0.2,indexDecay:2,tine:0.6,tineRatio:14,decay:3.5,trem:0.5,tremRate:3.8},{tags:['ep','tine'],genre:['jazz','neo-soul','lofi'],mood:['warm','mellow'],desc:'Round tine tone that barks when you dig in.',oct:4,
  mod:modOf([lfoS('s1',{name:'Drift',rate:0.15})],[rt_('s1','v:vpan',0.35)],[mac('m1','Bark',0.4,[['eng:index',0.05,0.45],['eng:velBright',0.3,0.9]])])});
fp('keys-reed','Reed Keys (Wurlitzer-inspired)','fm','keys',{ratio:1,index:3.5,indexSus:0.4,indexDecay:0.8,tine:0.2,tineRatio:3.5,decay:2,trem:0.25,velBright:0.9},{tags:['ep','reed'],genre:['soul','indie'],mood:['gritty'],desc:'Reedy, nasal electric piano that growls at high velocity.',oct:4});
fp('keys-organ','Tonewheel Organ (Hammond-inspired)','organ','keys',{db0:8,db1:8,db2:8,db3:0,db4:0,db5:0,db6:0,db7:0,db8:0,vib:'C3',leslie:'slow',drive:0.18,click:0.35,tone:0.6,swell:0.85},{tags:['organ'],genre:['rock','soul','pop'],mood:['warm'],desc:"The classic 888 foundation with chorus C3 and a slow rotor. Mod wheel up switches the rotor to fast.",oct:4});
fp('keys-jazzorgan','Jazz Organ','organ','keys',{db0:8,db1:8,db2:8,db3:0,db4:0,db5:0,db6:0,db7:0,db8:0,perc:'3rd',percDecay:0.25,percVol:'normal',click:0.65,vib:'off',leslie:'off',drive:0.05,tone:0.45,swell:0.72,leak:0.25,hornBal:0.45},{tags:['organ'],genre:['jazz'],mood:['cool'],desc:'Bebop registration: 888 with third-harmonic percussion that only speaks on detached notes, a stopped rotor, no scanner and a warm, clean amp. Comp lightly; mod wheel spins the rotor for leads.',oct:4});
fp('keys-gospel','Full Gospel Organ','organ','keys',{db0:8,db1:8,db2:8,db3:8,db4:8,db5:0,db6:0,db7:0,db8:8,vib:'C3',leslie:'fast',drive:0.5,tone:0.75,click:0.5,swell:0.7,hornBal:0.6},{tags:['organ'],genre:['gospel','rock'],mood:['big'],desc:'Shout registration 88 888 000 8 through a hot tube preamp and a fast rotor. The Swell macro (or aftertouch, or CC11) is the expression pedal: ride it for swells and builds.',oct:4,mod:modOf([],[rt_('at','eng:swell',0.35),rt_('cc11','eng:swell',0.6)],[mac('m1','Swell',0.7,[['eng:swell',0.25,1],['eng:drive',0.3,0.65]])])});
fp('keys-clav','Clavinet-style Keys','pluck','keys',{bright:1,pick:0.05,damp:0.1,sustain:0.6,offDamp:0.03,tone:6000,drive:0.25,body:0},{tags:['clav','funk'],genre:['funk'],mood:['bouncy'],desc:'Bright, short struck string for funky comping.',oct:4});
fp('keys-celesta','Celesta','celesta','keys',{},{tags:['mallet','bell'],genre:['film','classical'],mood:['magical'],desc:'Felt hammers on steel bars with wooden resonators: round in the low register, glassy at the top, damped on release.',oct:5});
fp('keys-musicbox','Music Box','musicbox','keys',{},{tags:['bell','toy'],genre:['film','lullaby'],mood:['nostalgic'],desc:'Steel comb tines plucked by pins: bright mechanical attack, long ring, a little wooden case and the damper buzz on repeated notes.',oct:6});
fp('keys-prism','Prism Bells','prism','keys',{},{tags:['bell'],genre:['ambient'],mood:['shimmering'],desc:'Inharmonic bells with sparkles.',oct:5});
fp('keys-glock','Glockenspiel','orch','keys',{inst:'glock',art:'hit',release:1.5},{tags:['mallet','recorded'],genre:['film','pop'],mood:['sparkly'],desc:'Recorded glockenspiel steel bars.',oct:5});
fp('keys-xylo','Xylophone','orch','keys',{inst:'xylo',art:'hit',release:0.6},{tags:['mallet','recorded'],genre:['film','orchestral'],mood:['playful'],desc:'Recorded xylophone: dry, woody and bright.',oct:5,rev:0.28});
fp('keys-marimba','Marimba','orch','keys',{inst:'marimba',art:'hit',release:1},{tags:['mallet','recorded'],genre:['film','world','pop'],mood:['warm'],desc:'Recorded marimba with resonators.',oct:4,rev:0.28});
/* Brass */
const brass=(id,name,params,o)=>fp(id,name,'brass','brass',params,Object.assign({tags:['brass'],genre:['orchestral','film'],mood:[]},o));
fp('brass-trumpet','Trumpet','orch','brass',{inst:'trumpet',art:'auto',dyn:'vel',release:0.25},{tags:['brass','recorded'],genre:['orchestral','film','pop'],mood:['heroic'],desc:'Recorded solo trumpet: bright, focused and forward, with soft and loud takes. Set Dynamics to Mod wheel to swell held notes between them.',oct:4,rev:0.28});
fp('brass-mutedtp','Muted Trumpet','orch','brass',{inst:'trumpet',art:'mute',release:0.2},{tags:['brass','recorded','mute'],genre:['jazz','film'],mood:['cool'],desc:'Recorded trumpet with a straight mute: thin, buzzy and intimate.',oct:4,rev:0.28});
fp('brass-trombone','Trombone','orch','brass',{inst:'trombone',art:'auto',mode:'slide',glide:0.14,release:0.3},{tags:['brass','recorded'],genre:['orchestral','jazz','film'],mood:['warm'],desc:'Recorded tenor trombone with a broad midrange. Playing is set to Slide: overlapping notes glide like a real slide; separate notes start cleanly.',oct:3,rev:0.28});
fp('brass-horn','French Horn','orch','brass',{inst:'horn',art:'sus',mode:'legato',release:0.45,dyn:'mw'},{tags:['brass','recorded'],genre:['orchestral','film'],mood:['noble'],desc:'Recorded French horn: round, warm and blended. Legato connects overlapping notes; the mod wheel moves held notes between soft and loud recordings.',oct:3,rev:0.28});
fp('brass-tuba','Tuba','orch','brass',{inst:'tuba',art:'auto',release:0.3},{tags:['brass','recorded'],genre:['orchestral','film'],mood:['deep'],desc:'Recorded tuba: a weighty fundamental from a real low-brass player, not a pitched-down trumpet.',oct:2,rev:0.28});
brass('brass-lead','Synth Brass Lead (modeled)',{formant:1300,bright:0.85,growl:0.5,vibrato:0.4},{desc:'Edgy lead brass for melodies.',oct:4,genre:['funk','pop']});
fp('brass-section','Brass Section','section','brass',{band:'brass',art:'auto',loose:0.5,width:0.7},{tags:['brass','ensemble','recorded'],genre:['orchestral','film'],mood:['big'],desc:'Four different recorded instruments (trumpet, horn, trombone, tuba). Chords are voiced across them by register and each player enters at its own moment; single notes are doubled in unison.',oct:4,rev:0.28});
fp('brass-cinematic','Cinematic Brass','section','brass',{band:'brass',art:'sus',dyn:'mw',attack:0.35,release:0.9,loose:0.7,width:0.8,tone:-0.3},{tags:['brass','ensemble','recorded'],genre:['film','trailer'],mood:['epic'],desc:'Recorded brass section for swells: the mod wheel crossfades every player from soft to loud recordings. Ride it while holding chords.',oct:3,rev:0.28});
fp('brass-funk','Funk Brass','section','brass',{band:'brass',art:'stac',unison:'double',loose:0.25,release:0.15,tone:0.3},{tags:['brass','ensemble','recorded'],genre:['funk','soul'],mood:['punchy'],desc:'Recorded short brass notes with real round robins, so repeated hits never sound identical.',oct:4,rev:0.28});
fp('brass-jazz','Jazz Brass','section','brass',{band:'brass',art:'mute',loose:0.6,release:0.3},{tags:['brass','ensemble','recorded'],genre:['jazz'],mood:['mellow'],desc:'Section with a muted trumpet on top and open horn, trombone and tuba underneath.',oct:4,rev:0.28});
fp('brass-stabs','Brass Stabs','section','brass',{band:'brass',art:'stac',loose:0.15,release:0.08,tone:0.4},{tags:['brass','ensemble','recorded'],genre:['funk','house'],mood:['punchy'],desc:'Tight recorded staccato section for stabs.',oct:4,rev:0.28});
fp('brass-swell','Brass Swell','section','brass',{band:'brass',art:'sus',attack:1.2,release:0.7,loose:0.6},{tags:['brass','ensemble','recorded'],genre:['film'],mood:['epic'],desc:'Recorded section with a slow entry on every note.',oct:3,rev:0.28});
fp('brass-fanfare','Fanfare','section','brass',{band:'brass',art:'auto',dyn:'vel',loose:0.35,width:0.8,tone:0.35},{tags:['brass','ensemble','recorded'],genre:['orchestral','film'],mood:['triumphant'],desc:'Bright recorded section; play hard for the loud takes.',oct:4,rev:0.28});
/* Strings */
const str=(id,name,params,o)=>fp(id,name,'strings','strings',params,Object.assign({tags:['strings'],genre:['orchestral','film'],mood:[]},o));
fp('str-violin','Solo Violin','orch','strings',{inst:'violin',art:'auto',mode:'legato',release:0.4,dyn:'vel'},{tags:['strings','recorded'],genre:['orchestral','film'],mood:['expressive'],desc:'Recorded solo violin with vibrato. Legato connects overlapping notes into one bow.',oct:5,rev:0.28});
fp('str-viola','Viola Section','orch','strings',{inst:'violas',art:'auto',release:0.5},{tags:['strings','recorded','ensemble'],genre:['orchestral','film'],mood:['warm'],desc:'Recorded viola section: the warm middle of the orchestra. (No solo viola recordings are included yet.)',oct:4,rev:0.28});
fp('str-cello','Cello Section','orch','strings',{inst:'cellos',art:'auto',release:0.6},{tags:['strings','recorded','ensemble'],genre:['orchestral','film'],mood:['rich'],desc:'Recorded cello section: rich low-mid sustain with vibrato. (No solo cello recordings are included yet.)',oct:3,rev:0.28});
fp('str-bass','Double Bass','orch','strings',{inst:'bass',art:'auto',release:0.4},{tags:['strings','recorded'],genre:['orchestral','film'],mood:['deep'],desc:'Recorded solo double bass, bowed with vibrato.',oct:2,rev:0.28});
fp('str-ensemble','String Ensemble','section','strings',{band:'strings',art:'sus',attack:0.12,release:0.7,loose:0.5,width:0.8},{tags:['strings','ensemble','recorded'],genre:['orchestral','film'],mood:['lush'],desc:'Recorded violins, violas, cellos and basses. Chords are voiced across the sections by register, each section with its own seat and entry.',oct:4,rev:0.28});
fp('str-pizz','Pizzicato Strings','section','strings',{band:'strings',art:'pizz',loose:0.4,release:0.4},{tags:['strings','ensemble','recorded'],genre:['orchestral','film'],mood:['playful'],desc:'Recorded pizzicato from every section, with round robins.',oct:4,rev:0.28});
fp('str-short','Short Strings','section','strings',{band:'strings',art:'stac',loose:0.3,release:0.2},{tags:['strings','ensemble','recorded'],genre:['orchestral','film'],mood:['driving'],desc:'Recorded spiccato sections for ostinatos.',oct:4,rev:0.28});
fp('str-sustain','Sustained Strings','section','strings',{band:'strings',art:'sus',dyn:'mw',attack:0.6,release:1.2,loose:0.6,width:0.85},{tags:['strings','ensemble','recorded'],genre:['orchestral','film','ambient'],mood:['lush'],desc:'Slow recorded string pads; the mod wheel crossfades soft and loud bowing.',oct:4,rev:0.28});
str('str-cinematic','Cinematic String Texture',{section:8,attack:2,bright:0.3,bow:0.55,release:2},{desc:'Dark bowed texture with breathing brightness.',oct:3,mood:['tense'],mod:modOf([lfoS('s1',{name:'Breathe',rate:0.12})],[rt_('s1','eng:bright',0.25)])});
fp('str-violins','Violin Section','orch','strings',{inst:'violins',art:'auto',release:0.5},{tags:['strings','recorded','ensemble'],genre:['orchestral','film'],mood:['soaring'],desc:'Recorded violin section with vibrato.',oct:5,rev:0.28});
fp('str-violin-pizz','Violin Pizzicato','orch','strings',{inst:'violin',art:'pizz',release:0.3},{tags:['strings','recorded'],genre:['orchestral'],mood:['playful'],desc:'Recorded solo violin, plucked, with round robins.',oct:5,rev:0.28});
fp('str-cello-short','Short Cellos','orch','strings',{inst:'cellos',art:'stac',release:0.15},{tags:['strings','recorded'],genre:['film'],mood:['driving'],desc:'Recorded spiccato cellos for ostinatos.',oct:3,rev:0.28});
fp('ww-section','Woodwind Section','section','woodwinds',{band:'winds',art:'auto',loose:0.5,width:0.7},{tags:['woodwind','ensemble','recorded'],genre:['orchestral','film'],mood:['pastoral'],desc:'Recorded flute, oboe, clarinet and bassoon voiced across a chord.',oct:4,rev:0.28});
/* Woodwinds */
const ww=(id,name,params,o)=>fp(id,name,'wind','woodwinds',params,Object.assign({tags:['woodwind'],genre:['orchestral','film'],mood:[]},o));
fp('ww-flute','Flute','orch','woodwinds',{inst:'flute',art:'auto',mode:'legato',release:0.3},{tags:['woodwind','recorded'],genre:['orchestral','film'],mood:['airy'],desc:'Recorded concert flute with vibrato.',oct:5,rev:0.28});
ww('ww-piccolo','Piccolo (modeled)',{tone:'flute',breath:0.3,bright:0.7},{desc:'Modeled high flute (no piccolo recordings are included yet).',oct:6});
fp('ww-clarinet','Clarinet','orch','woodwinds',{inst:'clarinet',art:'auto',mode:'legato',release:0.3},{tags:['woodwind','recorded'],genre:['orchestral','film','jazz'],mood:['warm'],desc:'Recorded clarinet: hollow low register, sweet upper register.',oct:4,rev:0.28});
fp('ww-oboe','Oboe','orch','woodwinds',{inst:'oboe',art:'auto',mode:'legato',release:0.25},{tags:['woodwind','recorded'],genre:['orchestral','film'],mood:['plaintive'],desc:'Recorded oboe: a focused, reedy double reed.',oct:4,rev:0.28});
fp('ww-bassoon','Bassoon','orch','woodwinds',{inst:'bassoon',art:'auto',mode:'legato',release:0.3},{tags:['woodwind','recorded'],genre:['orchestral','film'],mood:['dry'],desc:'Recorded bassoon.',oct:3,rev:0.28});
ww('ww-breathy','Breathy Flute',{tone:'flute',breath:0.7,attack:0.15,chiff:0.1,vibrato:0.45},{desc:'Soft, breath-heavy expressive flute.',oct:5,mood:['intimate']});
/* Synths */
const syn=(id,name,engine,params,o)=>fp(id,name,engine,'synths',params,Object.assign({tags:['synth'],genre:['electronic'],mood:[]},o));
syn('syn-poly','Analog Poly','va',{},{desc:'Two saws through a warm filter.',oct:4});
syn('syn-lead','Classic Lead','va',{wave1:'sawtooth',wave2:'square',detune2:9,cutoff:3500,reso:4,mode:'legato',glide:0.06},{desc:'Legato lead with vibrato and a mod-wheel filter.',oct:4,
  mod:modOf([lfoS('s1',{name:'Vibrato',rate:5.5})],[rt_('s1','eng:fine',0.06),rt_('mw','eng:cutoff',0.35)],[mac('m1','Brightness',0.5,[['eng:cutoff',0.35,0.9],['eng:reso',0.1,0.45]])])});
syn('syn-pad','Warm Pad','va',{unison:4,spread:18,cutoff:1400,aA:0.8,aR:1.5,fenv:0.15},{desc:'Unison pad with a slow filter sweep.',oct:4,mood:['warm'],mod:modOf([lfoS('s1',{name:'Sweep',rate:0.2})],[rt_('s1','eng:cutoff',0.15)])});
syn('syn-pluck','Bright Pluck','va',{cutoff:900,fenv:0.8,fD:0.25,fS:0,aD:0.4,aS:0,aR:0.3},{desc:'Short filter-enveloped pluck.',oct:4});
syn('syn-sawbrass','Saw Brass','va',{wave2:'sawtooth',detune2:12,cutoff:1200,fenv:0.6,fA:0.05,fD:0.5,fS:0.4,aA:0.03},{desc:'Classic synth brass.',oct:4});
syn('syn-arp','Arp Synth','va',{cutoff:1800,fenv:0.6,fD:0.2,fS:0.1,aD:0.25,aS:0,aR:0.2},{desc:'Plucky synth with the arpeggiator on. Hold a chord.',oct:4,arp:{on:true,mode:'updown',rate:'1/16',oct:2,gate:0.6}});
syn('syn-vocal','Vocal Morph','wt',{table:'vocal',pos:0.3,posEnv:0},{desc:'Vocal wavetable scanned by an LFO.',oct:4,mod:modOf([lfoS('s1',{name:'Scan',sync:'1/2'})],[rt_('s1','eng:pos',0.35)])});
syn('syn-digital','Digital Keys','wt',{table:'digital',pos:0.1,posEnv:0.6,posDec:0.5,unison:1},{desc:'Glassy digital attack that settles.',oct:4});
syn('syn-glass','Glass Scan','wt',{table:'glass',pos:0,unison:2},{desc:'A step modulator walks the wavetable in time.',oct:4,mod:modOf([stepS('s1',{name:'Walk',sync:'1/16',steps:[0,0.3,0.6,1,0.6,0.3,0,0.5,0,0.3,0.6,1,0.8,0.4,0.2,0.1]})],[rt_('s1','eng:pos',1)])});
syn('syn-growltable','Growl Table','wt',{table:'growl',mode:'mono',glide:0.05,cutoff:2000,reso:6},{desc:'Mono growl wavetable.',oct:2});
syn('syn-nebula','Nebula Pad','nebula',{},{desc:'Drifting supersaw pad.',oct:4});
syn('syn-dream','Dream Pad','nebula',{bloom:0.9,drift:0.7,bright:0.35},{desc:'Very slow bloom for ambient beds.',oct:4,mood:['dreamy'],rev:0.7});
syn('syn-vox','Vox Choir','vox',{},{desc:'Formant choir.',oct:4});
syn('syn-chip','8-Bit Lead','chip',{},{desc:'Pulse-wave chiptune lead.',oct:5});
syn('syn-fmbell','FM Bell','fm',{ratio:3.5,index:3,indexDecay:2,tine:0,decay:4,trem:0},{desc:'Inharmonic FM bell.',oct:5});
/* Bass & guitars */
const bg=(id,name,engine,params,o)=>fp(id,name,engine,'bass',params,Object.assign({tags:[],genre:[],mood:[]},o));
bg('bass-electric','Electric Bass','pluck',{bright:0.35,pick:0.25,sustain:4,damp:0.3,tone:2500},{tags:['bass'],desc:'Finger-style electric bass.',oct:2});
fp('bass-upright','Upright Bass','orch','bass',{inst:'bass',art:'pizz',release:0.25},{tags:['bass','recorded'],genre:['jazz'],mood:['woody'],desc:'Recorded double bass plucked pizzicato, with round robins.',oct:2,rev:0.28});
bg('bass-synth','Synth Bass','va',{wave2:'square',sub:0.5,cutoff:600,fenv:0.5,fD:0.3,fS:0.2,mode:'mono',glide:0.03},{tags:['bass','synth'],desc:'Mono analog bass.',oct:2});
bg('bass-sub','Sub Bass','va',{wave1:'sine',mix2:0,cutoff:400,mode:'mono'},{tags:['bass','sub'],desc:'Clean sine sub.',oct:2});
bg('bass-acid','Acid Line','acid',{},{tags:['bass','303'],genre:['acid','techno'],desc:'Resonant mono bass with slide.',oct:2});
bg('bass-growl','Growl Wobble','growl',{},{tags:['bass'],genre:['dubstep'],desc:'Tempo-locked wobble.',oct:2});
bg('bass-fm','FM Bass','fm',{ratio:1,index:4,indexSus:0.3,indexDecay:0.3,tine:0,sustain:0.6,decay:1.5,trem:0},{tags:['bass','fm'],desc:'Punchy FM bass.',oct:2});
bg('gtr-clean','Clean Electric Guitar','pluck',{bright:0.7,pick:0.18,sustain:5,tone:7000,body:0.2},{tags:['guitar'],genre:['indie','pop'],desc:'Clean single-coil pluck.',oct:3});
bg('gtr-funk','Muted Funk Guitar','pluck',{mute:0.85,bright:0.9,drive:0.1,offDamp:0.03},{tags:['guitar'],genre:['funk'],desc:'Palm-muted chicken scratch.',oct:3});
bg('gtr-acoustic','Acoustic Guitar','pluck',{bright:0.8,body:0.8,sustain:6,damp:0.2},{tags:['guitar'],genre:['folk'],desc:'Steel-string with body resonance.',oct:3});
bg('gtr-plucked','Plucked Strings','pluck',{pick:0.08,bright:0.9,sustain:3,damp:0.15,body:0.4},{tags:['plucked'],desc:'Bright koto-like pluck.',oct:4});
fp('gtr-harp','Concert Harp','orch','strings',{inst:'harp',art:'pluck',release:1.2},{tags:['harp','recorded'],genre:['orchestral','film'],mood:['dreamy'],desc:'Recorded concert harp strings ringing out naturally.',oct:4,rev:0.28});
bg('gtr-kalimba','Kalimba','fm',{ratio:7,index:0.8,indexSus:0,indexDecay:0.05,tine:0,decay:1.2,sustain:0,trem:0,release:0.5},{tags:['kalimba','tine'],desc:'Thumb-piano plink.',oct:5});
/* Drums */
Object.keys(KITS).forEach(k=>fp('drums-'+k,KITS[k].name,'kit','drums',{kit:k},{tags:['drums'],genre:{circuit:['hiphop','trap'],electro:['house','techno'],acoustic:['rock','pop'],boombap:['hiphop','lofi'],brushes:['jazz'],break:['dnb','breaks'],hand:['latin','world'],orch:['orchestral','film']}[k],mood:[],desc:'Synthesized '+KITS[k].name.toLowerCase()+' kit.',oct:3,crush:k==='boombap'?0.25:0}));
FACTORY.find(p=>p.id==='drums-boombap').params.crush=0.25;
/* Experimental */
const ex=(id,name,engine,params,o)=>fp(id,name,engine,'experimental',params,Object.assign({tags:['texture'],genre:['ambient','experimental'],mood:[]},o));
ex('exp-glass','Glass Grains','granular',{src:'glass'},{desc:'Granular cloud of bell partials.',oct:4});
ex('exp-choir','Choir Cloud','granular',{src:'choir',density:40,size:0.15,spray:0.4},{desc:'Smeared vocal grains.',oct:4,mood:['ethereal']});
ex('exp-air','Air Drone','granular',{src:'air',attack:3,release:4,density:30,scan:0.4},{desc:'Evolving airy drone.',oct:3,tags:['drone']});
ex('exp-tape','Dusty Keys','tape',{},{desc:'Worn keys with wow, hiss and crackle.',oct:4,genre:['lofi'],mood:['nostalgic']});
ex('exp-cassette','Worn Cassette Pad','tape',{wow:0.75,flutter:0.5,age:0.75,aA:0.6,aS:0.8,aR:1.2,tone:0.7},{desc:'Warbly pad from an old tape.',oct:4,genre:['lofi']});
ex('exp-revbell','Reverse Bell','reverse',{src:'bell',length:1.2},{desc:'Bells that swell in backwards.',oct:5});
ex('exp-revpad','Reverse Pad','reverse',{src:'pad',length:2,sync:1},{desc:'Backwards pad swells locked to the beat.',oct:4});
ex('exp-impact','Cinematic Impact','impact',{},{desc:'Trailer hit with boom and debris.',oct:2,genre:['film']});
ex('exp-thunder','Thunder Hit','impact',{metal:0.7,decay:5,drop:18},{desc:'Long metallic rumble.',oct:2,genre:['film']});
ex('exp-glitch','Glitch Stutter','glitch',{},{desc:'Tempo-locked stutter with random pitch.',oct:4,genre:['idm']});
ex('exp-glitchfm','Glitch FM','glitch',{wave:'fm',rate:'1/32',chaos:12},{desc:'Fast FM chatter.',oct:4,genre:['idm']});
ex('exp-marimba','Wood Marimba','organic',{mode:'wood',decay:0.8},{desc:'Modal marimba bar.',oct:4,tags:['mallet']});
ex('exp-bowl','Singing Bowl','organic',{mode:'bowl',decay:4,motion:0.5},{desc:'Struck and bowed metal bowl.',oct:4,mood:['meditative']});
ex('exp-drops','Water Drops','organic',{mode:'drop'},{desc:'Little droplets.',oct:5});
ex('exp-breeze','Breeze','organic',{mode:'breeze',motion:0.6,decay:1},{desc:'Wind through tuned reeds.',oct:4});
/* Fusion Lab */
const fus=(id,name,layers,o)=>{const params={};layers.forEach((L,i)=>{params['L'+i+'on']=true;params['L'+i+'src']=L.src;Object.keys(L).forEach(k=>{if(k!=='src')params['L'+i+k]=L[k]})});for(let i=layers.length;i<LAYERS;i++)params['L'+i+'on']=false;fp(id,name,'fusion','fusion',params,Object.assign({tags:['layered'],genre:[],mood:[]},o))};
fus('fus-eppad','EP + Analog Pad',[{src:'keys-tine'},{src:'syn-pad',level:0.45,atk:0.4}],{desc:'Tine keys over a slow pad.',oct:4});
fus('fus-brasssaw','Brass + Saw',[{src:'brass-section'},{src:'syn-sawbrass',level:0.5}],{desc:'Section brass reinforced with synth brass.',oct:4});
fus('fus-pianotape','Piano + Tape',[{src:'keys-grand'},{src:'exp-tape',level:0.35,cut:5000}],{desc:'Grand with a worn tape shadow.',oct:4});
fus('fus-harpbell','Harp + Bell',[{src:'gtr-harp'},{src:'keys-prism',level:0.35,tune:12}],{desc:'Harp with a bell an octave up.',oct:4});
fus('fus-strgrains','Strings + Grains',[{src:'str-ensemble'},{src:'exp-choir',level:0.4,atk:1}],{desc:'Section strings with a granular halo.',oct:4});
fus('fus-drumperc','Drum + Wood Percussion',[{src:'drums-circuit'},{src:'exp-marimba',level:0.35,tune:24}],{desc:'Every drum hit also strikes a tuned wood bar.',oct:3});
fus('fus-velbrass','Velocity Brass',[{src:'brass-mutedtp',vhi:80,xf:15},{src:'brass-trumpet',vlo:81,xf:15}],{desc:'Soft playing is muted, hard playing opens the bell.',oct:4});
fus('fus-woodwinds','Woodwind Ensemble',[{src:'ww-bassoon',khi:47},{src:'ww-clarinet',klo:48,khi:59},{src:'ww-oboe',klo:60,khi:71},{src:'ww-flute',klo:72}],{desc:'Bassoon, clarinet, oboe and flute split across the keyboard.',oct:4});
fp('sampler-empty','Empty Sampler','sampler','sampler',{},{desc:'Import your own recordings and map them across the keyboard.',oct:4,tags:['sampler']});
const FACTORY_BY_ID=Object.fromEntries(FACTORY.map(p=>[p.id,p]));
/* Fusion layer sources: every factory/user preset whose engine can run inside a layer. */
function fusionSources(){return [['none','None']].concat(FACTORY.concat(USER.presets.map(u=>u.data&&Object.assign({id:u.id,name:u.name},u.data))).filter(p=>p&&ENG[p.engine]&&!ENG[p.engine].setup&&!ENG[p.engine].mono&&p.engine!=='fusion'&&p.engine!=='sampler').map(p=>[p.id,p.name]))}
function refreshFusionChoices(){const ch=fusionSources();ENG.fusion.params.filter(p=>/^L\dsrc$/.test(p.k)).forEach(p=>p.choices=ch)}

/* Ensembles */
const mem=(preset,o)=>Object.assign({preset,lo:0,hi:127,vlo:0,vhi:127,ch:0,tr:0,vol:0.8,pan:0},o);
const ENSEMBLES=[
  {id:'ens-brass',name:'Vintage Brass Section',desc:'Trumpet, French horn and trombone layered across overlapping ranges, with plate-style reverb.',members:[mem('brass-trumpet',{lo:58,pan:0.25}),mem('brass-horn',{lo:46,hi:74,vol:0.7,pan:-0.3}),mem('brass-trombone',{hi:62,pan:-0.1})],tags:['brass']},
  {id:'ens-jazzkeys',name:'Dreamy Jazz Keys',desc:'Tine keys, a quiet warm pad and a dusty tape layer, all played together.',members:[mem('keys-tine',{fx:[['chorus',{mix:0.3}]]}),mem('syn-pad',{vol:0.4}),mem('exp-tape',{vol:0.35})],tags:['keys','lofi']},
  {id:'ens-orch',name:'Cinematic Orchestra',desc:'Orchestral percussion below C2, strings everywhere, brass enters with harder playing, flutes on top.',members:[mem('drums-orch',{hi:35}),mem('str-ensemble',{lo:36}),mem('brass-cinematic',{lo:36,vlo:85,vol:0.75}),mem('ww-flute',{lo:72,vol:0.6})],tags:['orchestral']},
  {id:'ens-band',name:'Indie Band',desc:'Drums on the lowest two octaves, bass next, then guitar with a soft piano layer.',members:[mem('drums-acoustic',{hi:47}),mem('bass-electric',{lo:48,hi:59,tr:-12}),mem('gtr-clean',{lo:60}),mem('keys-felt',{lo:60,vol:0.45})],tags:['band']},
  {id:'ens-neon',name:'Neon Synth Stack',desc:'Synth bass low, pad and arp in the middle, lead on top.',members:[mem('bass-synth',{hi:47}),mem('syn-pad',{lo:48,hi:71,vol:0.55}),mem('syn-arp',{lo:48,hi:71,vol:0.6}),mem('syn-lead',{lo:72})],tags:['synth']},
  {id:'ens-lofi',name:'Lo-fi Bedroom',desc:'Boom-bap kit below C3; dusty keys, felt piano and an air drone above.',members:[mem('drums-boombap',{hi:47}),mem('exp-tape',{lo:48}),mem('keys-felt',{lo:48,vol:0.5}),mem('exp-air',{lo:48,vol:0.25})],tags:['lofi']}
];
/* Project templates: structure only, no notes or recordings. */
const tt=(preset,name,o)=>Object.assign({preset,name},o);
const TEMPLATES=[
  {id:'tpl-beat',name:'Beatmaking',desc:'Boom-bap kit, synth bass, dusty keys and an empty sampler track at 90 BPM, with a bus compressor on the master.',bpm:90,markers:['Intro','Verse','Hook','Verse','Hook'],tracks:[tt('drums-boombap','Drums'),tt('bass-synth','Bass'),tt('exp-tape','Keys'),tt('sampler-empty','Samples')],masterFx:[['comp',{threshold:-14,ratio:3}]]},
  {id:'tpl-orch',name:'Orchestral Composition',desc:'Strings, pizzicato, brass, woodwinds and percussion at 80 BPM with a large hall.',bpm:80,markers:['Opening','Theme','Development','Climax','Coda'],tracks:[tt('str-ensemble','Strings'),tt('str-pizz','Pizzicato'),tt('brass-cinematic','Brass'),tt('ww-flute','Flute'),tt('ww-clarinet','Clarinet'),tt('drums-orch','Percussion')],returns:{rev:{size:4.5,mix:1}}},
  {id:'tpl-song',name:'Songwriting',desc:'Piano, bass, acoustic drums and a pad at 100 BPM with verse and chorus markers.',bpm:100,markers:['Intro','Verse','Chorus','Verse','Chorus','Bridge','Chorus'],tracks:[tt('keys-grand','Piano'),tt('bass-electric','Bass'),tt('drums-acoustic','Drums'),tt('syn-pad','Pad')]},
  {id:'tpl-ambient',name:'Ambient',desc:'Dream pad, granular air, bells and a singing bowl at 70 BPM with long reverb.',bpm:70,markers:['Dawn','Drift','Bloom'],tracks:[tt('syn-dream','Pad'),tt('exp-air','Texture'),tt('keys-prism','Bells'),tt('exp-bowl','Bowl')],returns:{rev:{size:8,mix:1}}},
  {id:'tpl-electronic',name:'Electronic',desc:'909 kit, acid bass, lead, pad and arp at 124 BPM.',bpm:124,markers:['Intro','Build','Drop','Break','Drop'],tracks:[tt('drums-electro','Drums'),tt('bass-acid','Acid'),tt('syn-lead','Lead'),tt('syn-pad','Pad'),tt('syn-arp','Arp')],masterFx:[['comp',{threshold:-12,ratio:2.5}],['limiter',{}]]}
];
/* Modulation patches */
const MODPATCHES=[
  {id:'mp-drift',name:'Slow stereo drift',desc:'A slow LFO moves each note across the stereo field.',data:modOf([lfoS('s1',{name:'Drift',rate:0.12})],[rt_('s1','v:vpan',0.45)])},
  {id:'mp-vib',name:'Vibrato',desc:'Gentle pitch vibrato on every voice.',data:modOf([lfoS('s1',{name:'Vibrato',rate:5.5})],[rt_('s1','v:vpitch',0.01)])},
  {id:'mp-velbright',name:'Velocity brightness',desc:'Soft notes are darker, hard notes open up.',data:modOf([],[rt_('vel','v:vcut',0.35,{bip:true})])},
  {id:'mp-pump',name:'Beat pump',desc:'A synced ramp ducks the track volume on every beat.',data:modOf([lfoS('s1',{name:'Pump',wave:'sawtooth',sync:'1/4'})],[rt_('s1','trk:vol',0.3)])},
  {id:'mp-wobble',name:'Filter wobble',desc:'An 1/8 LFO sweeps a per-voice filter.',data:modOf([lfoS('s1',{name:'Wobble',sync:'1/8'})],[rt_('s1','v:vcut',0.45)])},
  {id:'mp-gate',name:'Trance gate',desc:'A 16-step pattern chops the voice level.',data:modOf([stepS('s1',{name:'Gate',steps:[1,0,1,1,0,1,0,1,1,0,1,0,1,1,0,1]})],[rt_('s1','v:level',-1)])},
  {id:'mp-random',name:'Note-to-note variation',desc:'Every note gets a slightly different pan and brightness.',data:modOf([],[rt_('rand','v:vpan',0.3),rt_('rand','v:vcut',0.12)])},
  {id:'mp-swell',name:'Envelope swell filter',desc:'A per-voice envelope opens a filter at the start of each note, then lets it settle.',data:modOf([envS('s1',{name:'Swell',a:0.3,d:1.2,s:0.2,r:0.5})],[rt_('s1','v:vcut',-0.5,{bip:true})])},
  {id:'mp-macros',name:'Performance macros',desc:'Space and Warmth macros wired to sends and a per-voice filter.',data:modOf([],[rt_('macro:m2','v:vcut',-0.5)],[mac('m1','Space',0.3,[['trk:rev',0.05,0.8],['trk:dly',0,0.5]]),mac('m2','Warmth',0,[])])}
];
/* ───────── user content and the shared file format ───────── */
const USER_KEY='soundcraft.user.v1',FORMAT='soundcraft',FORMAT_VERSION=1;
const CONTENT_TYPES={instrument:'Instrument preset',ensemble:'Ensemble',track:'Track preset',fxchain:'Effect chain',template:'Project template',modpatch:'Modulation patch'};
let USER={presets:[],ensembles:[],tracks:[],fxchains:[],templates:[],modpatches:[]};
const USER_LIST={instrument:'presets',ensemble:'ensembles',track:'tracks',fxchain:'fxchains',template:'templates',modpatch:'modpatches'};
function loadUser(){try{const u=JSON.parse(read(USER_KEY)||'null');if(u&&typeof u==='object')Object.keys(USER).forEach(k=>{if(Array.isArray(u[k]))USER[k]=u[k]})}catch(e){errLog.push('user library: '+e.message)}refreshFusionChoices()}
function saveUser(){if(!store(USER_KEY,JSON.stringify(USER))){toast('Could not save to your library: browser storage is blocked or full.');return false}refreshFusionChoices();return true}
function USER_PRESET_BY_ID(){const o={};USER.presets.forEach(u=>o[u.id]=Object.assign({id:u.id,name:u.name},u.data));return o}
function hashJSON(o){const s=JSON.stringify(o);let h=2166136261;for(let i=0;i<s.length;i++)h=Math.imul(h^s.charCodeAt(i),16777619);return (h>>>0).toString(36)}
function addUserItem(type,name,data,meta={}){const list=USER[USER_LIST[type]],h=hashJSON(data),dup=list.find(x=>x.hash===h);if(dup)return {dup};
  const item=Object.assign({id:uid('u'),type,name:String(name||'Untitled').slice(0,80),desc:'',tags:[],author:'Me',version:1,created:Date.now(),hash:h,data},meta);list.unshift(item);return saveUser()?{item}:{error:true}}
function envelope(type,name,data,extra={}){return Object.assign({format:FORMAT,formatVersion:FORMAT_VERSION,app:'Soundcraft '+APP_VERSION,type,name,author:'Me',created:new Date().toISOString(),data},extra)}
/* Validate imported content. Never evaluates code; unknown fields are reported. */
function validateImport(obj){
  if(!obj||typeof obj!=='object')throw new Error('The file is not a Soundcraft file.');
  if(obj.format!==FORMAT)throw new Error('The file is not a Soundcraft preset or template.');
  if(typeof obj.formatVersion!=='number')throw new Error('The file has no format version.');
  if(obj.formatVersion>FORMAT_VERSION)throw new Error('This file was made with a newer Soundcraft (format '+obj.formatVersion+'). This version reads format '+FORMAT_VERSION+'. Update Soundcraft to open it.');
  if(!CONTENT_TYPES[obj.type])throw new Error('Unknown content type "'+String(obj.type).slice(0,30)+'".');
  const warn=[],deps=[];const d=obj.data;if(!d||typeof d!=='object')throw new Error('The file has no data.');
  const checkInst=(x,where)=>{if(!ENG[x.engine])throw new Error(where+'uses an instrument engine this version does not have ("'+String(x.engine).slice(0,30)+'").');
    const s=sanitizeParams(ENG[x.engine].params,x.params);if(s.bad.length)warn.push(where+'had out-of-range values for '+s.bad.join(', ')+'; defaults were used.');if(s.unknown.length)warn.push(where+'contains settings this version does not use: '+s.unknown.slice(0,6).join(', ')+'.');
    x.params=s.o;x.mod=sanitizeMod(x.mod,warn);if(x.zones){x.zones=x.zones.slice(0,256).map(sanitizeZone);x.zones.forEach(z=>z.sample&&deps.push({kind:'sample',id:z.sample,name:z.name}))}
    if(x.engine==='fusion')for(let i=0;i<LAYERS;i++){const s2=x.params['L'+i+'src'];if(x.params['L'+i+'on']&&s2!=='none'&&!FACTORY_BY_ID[s2]&&!USER_PRESET_BY_ID()[s2])deps.push({kind:'preset',id:s2,name:'Fusion layer '+(i+1)})}};
  if(obj.type==='instrument')checkInst(d,'This preset ');
  if(obj.type==='track'){checkInst(d,'This track ');d.fx=sanitizeFxList(d.fx,warn)}
  if(obj.type==='fxchain')d.fx=sanitizeFxList(d.fx,warn);
  if(obj.type==='modpatch'){const m=sanitizeMod(d,warn);obj.data=m}
  if(obj.type==='ensemble'){if(!Array.isArray(d.members)||!d.members.length)throw new Error('The ensemble has no instruments.');d.members=d.members.slice(0,16);d.members.forEach((m,i)=>{if(m.track){checkInst(m.track,'Ensemble instrument '+(i+1)+' ');m.track.fx=sanitizeFxList(m.track.fx,warn)}else if(!FACTORY_BY_ID[m.preset]&&!USER_PRESET_BY_ID()[m.preset])deps.push({kind:'preset',id:m.preset,name:'Ensemble instrument '+(i+1)})})}
  if(obj.type==='template'){if(!Array.isArray(d.tracks))throw new Error('The template has no tracks.');d.tracks.forEach((t,i)=>{checkInst(t,'Template track '+(i+1)+' ');t.fx=sanitizeFxList(t.fx,warn);t.patterns=[];t.clips=[]})}
  if(obj.samples&&typeof obj.samples==='object')Object.keys(obj.samples).forEach(id=>{const e=obj.samples[id];if(!e||typeof e.b64!=='string'||!/^smp_[\w]+$/.test(id))warn.push('An embedded sample was malformed and skipped.')});
  return {obj,warn,deps};
}
function sanitizeFxList(list,warn){if(!Array.isArray(list))return [];return list.slice(0,16).filter(f=>{if(!f||!FX[f.type]){warn&&warn.push('An unknown effect "'+String(f&&f.type).slice(0,20)+'" was left out.');return false}return true}).map(f=>({id:uid('f'),type:f.type,on:f.on!==false,params:sanitizeParams(FX[f.type].params,f.params).o}))}
function sanitizeMod(m,warn){const out=newModState();if(!m||typeof m!=='object')return out;
  (Array.isArray(m.sources)?m.sources:[]).slice(0,32).forEach(s=>{if(!s||!SRC_TYPES[s.type]){warn&&warn.push('An unknown modulation source was left out.');return}const b=newSource(s.type);
    ['name','color','wave','sync','retrig','a','d','s','r','smooth','release','gain','track'].forEach(k=>{if(s[k]!==undefined)b[k]=s[k]});b.id=String(s.id||b.id).slice(0,40);
    if(typeof b.rate!=='undefined')b.rate=clamp(+s.rate||b.rate,0.01,50);if(Array.isArray(s.steps))b.steps=s.steps.slice(0,64).map(x=>clamp(+x||0,-1,1));
    if(Array.isArray(s.points))b.points=s.points.slice(0,16).map(p=>[clamp(+p[0]||0,0,30),clamp(+p[1]||0,0,1)]);if(!SYNC_DIVS.some(d=>d[0]===b.sync))b.sync='free';
    ['a','d','s','r'].forEach(k=>{if(b[k]!==undefined)b[k]=clamp(+b[k]||0,0,30)});b.name=String(b.name).slice(0,40);out.sources.push(b)});
  (Array.isArray(m.routes)?m.routes:[]).slice(0,128).forEach(r=>{if(!r||typeof r.src!=='string'||typeof r.dst!=='string')return;const n=newRoute(r.src.slice(0,60),r.dst.slice(0,80),clamp(+r.amt||0,-1,1));
    n.on=r.on!==false;n.curve=CURVES[r.curve]?r.curve:'lin';n.bip=!!r.bip;n.min=clamp(r.min==null?0:+r.min,0,1);n.max=clamp(r.max==null?1:+r.max,0,1);n.smooth=clamp(+r.smooth||0,0,2000);n.cc=clamp(Math.round(+r.cc||1),0,127);n.name=String(r.name||'').slice(0,40);n.color=String(r.color||'').slice(0,9);if(r.id)n.id=String(r.id).slice(0,40);out.routes.push(n)});
  (Array.isArray(m.macros)?m.macros:[]).slice(0,16).forEach(x=>{if(!x)return;const n=newMacro(String(x.name||'Macro').slice(0,40));if(x.id)n.id=String(x.id).slice(0,40);n.value=clamp(+x.value||0,0,1);n.cc=x.cc==null?null:clamp(Math.round(+x.cc),0,127);
    n.maps=(Array.isArray(x.maps)?x.maps:[]).slice(0,32).filter(mp=>mp&&typeof mp.dst==='string').map(mp=>({dst:mp.dst.slice(0,80),min:clamp(+mp.min||0,0,1),max:clamp(mp.max==null?1:+mp.max,0,1),inv:!!mp.inv,curve:CURVES[mp.curve]?mp.curve:'lin'}));out.macros.push(n)});
  return out}
/* Preset data for a track (instrument preset). */
function instrumentData(tr){return JSON.parse(JSON.stringify({engine:tr.engine,params:tr.params,mod:tr.mod,arp:tr.arp,poly:tr.poly,bendRange:tr.bendRange,zones:tr.zones||undefined,layerOver:tr.layerOver||undefined,oct:tr.oct}))}
function trackData(tr){const d=instrumentData(tr);return Object.assign(d,JSON.parse(JSON.stringify({name:tr.name,color:tr.color,vol:tr.vol,pan:tr.pan,rev:tr.rev,dly:tr.dly,fx:tr.fx.map(f=>({type:f.type,on:f.on,params:f.params})),input:tr.input,preset:tr.preset},(k,v)=>k[0]==='_'?undefined:v)))}
function presetById(id){return FACTORY_BY_ID[id]||USER_PRESET_BY_ID()[id]||null}
