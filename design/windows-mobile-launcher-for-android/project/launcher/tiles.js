/* ============ live tile faces + flip scheduler ============ */
(function(){
  const colorVal = id => (window.TILE_COLORS.find(c=>c.id===id)||{v:'#2b78e4'}).v;

  // demo photos for slideshow / photos tile (gradient "photos")
  const PHOTOS = window.WALLPAPERS.map(w=>w.css);

  function avatar(name, big){
    const c = (window.CONTACTS.find(x=>x.n===name)||{}).c || '#555';
    return `<div class="av${big?' big':''}" style="background:${c}">${window.initials(name)}</div>`;
  }

  // build the live face HTML for a tile of given app + size
  function liveFace(live, size){
    const big = (size==='wide'||size==='large');
    switch(live){
      case 'clock': {
        const t = clockNow();
        return faces(
          `<div class="lc" style="align-items:flex-end;text-align:right;justify-content:center;white-space:nowrap"><div class="xl" style="font-size:${big?64:42}px">${t.hm}</div><div class="md" style="margin-top:4px">${t.weekday}</div><div class="sm">${t.fulldate}</div></div>`,
          `<div class="lc" style="align-items:flex-end;text-align:right;justify-content:center;white-space:nowrap"><div class="lg">${t.fulldate}</div><div class="sm" style="margin-top:4px">alarm ${t.alarm}</div></div>`,
          'clock','alarm');
      }
      case 'weather':
        return faces(
          `<div class="lc"><div class="row"><span class="xl" style="font-size:${big?60:40}px">23°</span>${window.icon('weather')}</div><div class="sm">partly cloudy</div><div class="name">weather</div></div>`,
          `<div class="lc"><div class="sm" style="opacity:.9">today</div><div class="row" style="gap:14px;margin-top:6px"><span class="md">26° / 17°</span></div><div class="sm" style="margin-top:auto">rain by 6pm · 40%</div></div>`,
          'weather','weather');
      case 'calendar':
        return faces(
          `<div class="lc"><div class="sm">next</div><div class="md" style="margin-top:3px">Standup</div><div class="sm">10:00 · 30m</div><div class="name">calendar</div></div>`,
          `<div class="lc"><div class="sm">today</div><div class="md" style="margin-top:3px">Design review</div><div class="sm">2:30 · with Aria, Dev</div><div class="name">calendar</div></div>`,
          'calendar','calendar');
      case 'mail':
        return faces(
          `<div class="lc"><div class="row" style="gap:8px;align-items:center">${avatar('Aria Cole')}<span class="sm" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">Aria Cole</span></div><div class="md" style="margin-top:6px">Q3 deck draft</div><div class="sm" style="opacity:.8">Sharing the latest cut for…</div><div class="name">mail</div></div>`,
          `<div class="lc"><div class="lg">7</div><div class="sm">unread</div><div class="name">mail</div></div>`,
          'mail','mail');
      case 'messages':
        return faces(
          `<div class="lc"><div class="row" style="gap:8px;align-items:center">${avatar('Ben Ito')}<span class="sm" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">Ben Ito</span></div><div class="md" style="margin-top:6px">on my way 👍</div><div class="sm" style="opacity:.8">see you at the cafe</div><div class="name">messages</div></div>`,
          `<div class="lc"><div class="lg">3</div><div class="sm">new</div><div class="name">messages</div></div>`,
          'messages','messages');
      case 'people': {
        const names = window.CONTACTS;
        const cols = big?4:2, rows = big?2:2, n = cols*rows;
        const grid = `<div class="avgrid" style="grid-template-columns:repeat(${cols},1fr);grid-template-rows:repeat(${rows},1fr)">${
          names.slice(0,n).map(c=>avatar(c.n)).join('')}</div>`;
        return faces(grid,
          `<div class="lc center" style="padding:0">${avatar(names[2].n,true)}<div class="sm" style="position:absolute;bottom:9px;left:11px">${names[2].n.split(' ')[0]} posted</div></div>`,
          'people','people');
      }
      case 'photos': {
        const slabs = PHOTOS.map((p,i)=>`<div class="photoslab${i===0?' on':''}" data-ph="${i}" style="background-image:${p}"></div>`).join('');
        return `<div class="faces" data-noflip="1">${slabs}<div class="lc" style="justify-content:flex-end;z-index:2"><div class="name" style="text-shadow:0 1px 4px rgba(0,0,0,.6)">photos</div></div></div>`;
      }
      case 'music':
        return faces(
          `<div class="lc"><div class="eqbars">${'<i></i>'.repeat(5)}</div><div class="md" style="margin-top:7px">Midnight City</div><div class="sm">M83</div><div class="name">music</div></div>`,
          `<div class="lc center"><div class="md">paused</div><div class="sm">tap to resume</div></div>`,
          'music','music');
    }
    return '';
  }

  function avatarLine(name){ return `${avatar(name)}`; }

  function faces(front, back){
    return `<div class="faces"><div class="face front">${front}</div><div class="face back">${back}</div></div>`;
  }

  function clockNow(){
    const d = new Date();
    let h = d.getHours(); const m = d.getMinutes();
    const hm = `${h}:${String(m).padStart(2,'0')}`;
    const days=['sunday','monday','tuesday','wednesday','thursday','friday','saturday'];
    const mons=['january','february','march','april','may','june','july','august','september','october','november','december'];
    return { hm, weekday: days[d.getDay()],
             fulldate:`${d.getDate()} ${mons[d.getMonth()]} ${d.getFullYear()}`, alarm:'7:00' };
  }

  /* ---- public: render tile inner content ---- */
  window.renderTileInner = function(t){
    const col = colorVal(t.color);
    // group / folder tile
    if(t.group){
      const kids = (t.children||[]).slice(0,4);
      const mini = kids.map(id=>{ const a=window.appById(id); return `<div class="gm">${window.icon(a?a.ic:'app')}</div>`; }).join('');
      return { col, html:
        `<div class="group-mini">${mini}</div>
         <div class="tile-pad"><div class="tile-name" style="margin-top:auto">${t.name||'folder'}</div></div>
         ${controls()}` };
    }
    const app = window.appById(t.app) || {name:t.app, ic:'app'};
    let body;
    const isLive = app.live && t.size!=='small';
    if(isLive){
      body = liveFace(app.live, t.size);
    } else {
      // static icon tile
      body = `<div class="tile-pad">
                <svg class="ico tile-icon" viewBox="0 0 24 24">${window.ICONS[app.ic]||window.ICONS.app}</svg>
                <div class="tile-name">${app.name}</div>
              </div>
              <span class="accentdot"></span>`;
    }
    const badge = (app.badge && t.size!=='small') ? `<span class="badge">${app.badge}</span>` :
                  (app.badge && t.size==='small') ? `<span class="badge" style="min-width:18px;height:18px;font-size:11px;top:5px;right:5px">${app.badge}</span>` : '';
    return { col, html: body + badge + controls() };
  };

  function controls(){
    return `<div class="tile-controls">
      <button class="tc-pin" data-act="unpin" aria-label="unpin">${window.icon('close')}</button>
      <button class="tc-resize" data-act="resize" aria-label="resize">${window.icon('resize')}</button>
    </div>`;
  }

  /* ---- live schedulers (flip / photo slideshow / people mosaic) ---- */
  let timers=[];
  function editing(){ const s=document.getElementById('screen'); return !s || s.classList.contains('edit'); }

  function flipOne(){
    if(editing()) return;
    const lives = [...document.querySelectorAll('#grid .tile.has-live')].filter(el=>el.offsetParent && !el.querySelector('.faces[data-noflip]'));
    if(!lives.length) return;
    lives[Math.floor(Math.random()*lives.length)].classList.toggle('flipped');
  }
  function slideshowStep(){
    document.querySelectorAll('#grid .faces[data-noflip]').forEach(f=>{
      const slabs=[...f.querySelectorAll('.photoslab')];
      if(slabs.length<2) return;
      const cur=slabs.findIndex(s=>s.classList.contains('on'));
      slabs[cur].classList.remove('on');
      slabs[(cur+1)%slabs.length].classList.add('on');
    });
  }
  function peopleStep(){
    if(editing()) return;
    document.querySelectorAll('#grid .tile[data-live="people"] .avgrid').forEach(g=>{
      const cells=[...g.querySelectorAll('.av')]; if(!cells.length) return;
      const cell=cells[Math.floor(Math.random()*cells.length)];
      const c=window.CONTACTS[Math.floor(Math.random()*window.CONTACTS.length)];
      cell.style.opacity='0'; cell.style.transform='scale(.6)';
      setTimeout(()=>{ cell.style.background=c.c; cell.textContent=window.initials(c.n); cell.style.opacity='1'; cell.style.transform='scale(1)'; }, 300);
    });
  }
  window.startFlips = function(){
    stopFlips();
    timers.push(setInterval(flipOne, 2600));
    timers.push(setInterval(slideshowStep, 3000));
    timers.push(setInterval(peopleStep, 2100));
  };
  window.stopFlips = function(){ timers.forEach(clearInterval); timers=[]; };

  /* update clock tiles every 20s */
  setInterval(()=>{
    document.querySelectorAll('#grid .tile[data-live="clock"] .face.front .xl').forEach(el=>{
      el.textContent = clockNow().hm;
    });
  }, 20000);
})();
