/* ============ TileShell — core launcher ============ */
(function(){
  const KEY = 'tileshell.v3';
  const $  = s => document.querySelector(s);
  const colorVal = id => (window.TILE_COLORS.find(c=>c.id===id)||{v:'#2b78e4'}).v;

  const L = window.L = {};
  let scale = 1;

  /* ---------- state ---------- */
  function fresh(){
    return { theme:'dark', accent:'#2b78e4', glass:true, blur:false,
             wall:'aurora', transparency:0.55,
             tiles: window.DEFAULT_TILES(), dock: window.DEFAULT_DOCK.slice() };
  }
  function load(){
    try{ const s = JSON.parse(localStorage.getItem(KEY)); if(s && s.tiles) return s; }catch(e){}
    return fresh();
  }
  L.save = function(){ try{ localStorage.setItem(KEY, JSON.stringify(L.state)); }catch(e){} };
  L.state = load();

  /* ---------- theme / wallpaper application ---------- */
  L.applyChrome = function(){
    const scr = $('#screen');
    scr.classList.toggle('light', L.state.theme==='light');
    scr.classList.toggle('glass', !!L.state.glass);
    scr.classList.toggle('blur', !!L.state.blur);
    scr.style.setProperty('--accent', L.state.accent);
    const wp = window.WALLPAPERS.find(w=>w.id===L.state.wall) || window.WALLPAPERS[0];
    $('#wall').style.background = L.state.customWall ? `url(${L.state.customWall}) center/cover` : wp.css;
    if(!L.state.glass) $('#screen').style.setProperty('--bg', L.state.theme==='light' ? '#ece9e4' : '#0a0a0d');
    applyTransparency();
  };
  function applyTransparency(){
    const t = L.state.transparency;
    const a = (0.62*(1-t)+0.05).toFixed(3);
    const rgb = L.state.theme==='light' ? '250,250,252' : '18,18,24';
    $('#screen').style.setProperty('--glass', `rgba(${rgb},${a})`);
  }
  L.applyTransparency = applyTransparency;

  /* ---------- tile element ---------- */
  function tileEl(t, i){
    const app = !t.group && window.appById(t.app);
    const live = (app && app.live && t.size!=='small') ? app.live : null;
    const r = window.renderTileInner(t);
    const el = document.createElement('div');
    el.className = 'tile '+t.size + (live?' has-live':'') + (L.dragId===t.id?' dragging':'') + (L.selectId===t.id?' selected':'');
    el.dataset.id = t.id;
    if(live) el.dataset.live = live;
    el.style.setProperty('--tcol', L.state.accent);
    el.style.setProperty('--i', i);
    el.innerHTML = r.html;
    return el;
  }

  /* ---------- render home grid ---------- */
  L.render = function(){
    const grid = $('#grid');
    grid.innerHTML = '';
    L.state.tiles.forEach((t,i)=> grid.appendChild(tileEl(t,i)));
  };

  /* ---------- navigation ---------- */
  L.go = function(page){ $('#screen').classList.toggle('apps', page==='apps'); };

  /* ---------- launch / toast ---------- */
  let toastT;
  L.toast = function(msg){
    const el=$('#toast'); el.textContent=msg; el.classList.add('on');
    clearTimeout(toastT); toastT=setTimeout(()=>el.classList.remove('on'),1400);
  };
  L.launch = function(id){
    if(id==='settings'){ L.openSettings(); return; }
    const a=window.appById(id);
    L.toast('opening '+(a?a.name:id));
  };

  /* ---------- group overlay ---------- */
  L.openGroup = function(t){
    const ov=$('#groupOv');
    ov.querySelector('.gtitle').textContent = t.name||'folder';
    const g = ov.querySelector('.ggrid'); g.innerHTML='';
    (t.children||[]).forEach(id=>{
      const a=window.appById(id); if(!a) return;
      el.dataset.launch=id;
      el.style.setProperty('--tcol', L.state.accent);
      el.innerHTML = `<div class="tile-pad"><svg class="ico tile-icon" viewBox="0 0 24 24">${window.ICONS[a.ic]||window.ICONS.app}</svg><div class="tile-name">${a.name}</div></div>`;
      g.appendChild(el);
    });
    ov.classList.add('on');
  };
  L.closeGroup = function(){ $('#groupOv').classList.remove('on'); };

  /* ---------- settings sheet ---------- */
  L.openSettings = function(){ $('#setSheet').classList.add('on'); $('#setScrim').classList.add('on'); };
  L.closeSettings = function(){ $('#setSheet').classList.remove('on'); $('#setScrim').classList.remove('on'); };

  /* ---------- nav bar actions ---------- */
  L.goHome = function(){
    if($('#groupOv').classList.contains('on')) L.closeGroup();
    if($('#recentsOv').classList.contains('on')) L.closeRecents();
    if($('#screen').classList.contains('edit')) L.exitEdit();
    L.closeSettings(); L.go('home');
    const hs=document.querySelector('.home-scroll'); if(hs) hs.scrollTop=0;
  };
  L.openSearch = function(){ L.go('apps'); const i=$('#searchInput'); if(i) setTimeout(()=>i.focus(),80); };
  L.openRecents = function(){
    const ov=$('#recentsOv'); const row=ov.querySelector('.recents-row'); row.innerHTML='';
    ['browser','maps','music','photos','mail','phone'].forEach(id=>{
      const a=window.appById(id); if(!a) return;
      const c=document.createElement('div'); c.className='rcard'; c.dataset.launch=id;
      c.innerHTML=`<div class="rhead"><span class="ic" style="background:${L.state.accent}">${window.icon(a.ic)}</span><span class="rnm">${a.name}</span></div><div class="rbody"></div>`;
      row.appendChild(c);
    });
    ov.classList.add('on');
  };
  L.closeRecents = function(){ $('#recentsOv').classList.remove('on'); };

  /* ---------- edit mode ---------- */
  L.enterEdit = function(){ $('#screen').classList.add('edit'); window.stopFlips(); };
  L.exitEdit  = function(){ $('#screen').classList.remove('edit'); L.selectId=null; closePops(); L.render(); L.save(); window.startFlips(); };
  L.select = function(id){ L.selectId=id; closePops(); L.render(); };

  function closePops(){ document.querySelectorAll('.resize-pop').forEach(p=>p.remove()); }

  /* click the size handle to cycle size: small -> medium -> wide -> large -> small */
  function cycleSize(tile){
    const t = L.state.tiles.find(x=>x.id===tile.dataset.id); if(!t) return;
    const order=['small','medium','wide','large'];
    t.size = order[(order.indexOf(t.size)+1)%order.length];
    L.render(); L.save();
  }

  L.unpinTile = function(id){
    L.state.tiles = L.state.tiles.filter(t=>t.id!==id);
    L.render(); L.save();
  };
  L.pinApp = function(appId){
    if(L.state.tiles.some(t=>t.app===appId)) { L.toast('already on start'); return; }
    const a=window.appById(appId);
    L.state.tiles.push({ id:'t-'+appId+'-'+Date.now(), app:appId, size:'medium', color:a?a.col:'blue' });
    L.save(); L.go('home'); L.render(); L.toast('pinned '+(a?a.name:appId));
  };

  /* ---------- merge into group ---------- */
  function doMerge(dragId, targetId){
    const tiles=L.state.tiles;
    const di=tiles.findIndex(t=>t.id===dragId), ti=tiles.findIndex(t=>t.id===targetId);
    if(di<0||ti<0) return;
    const drag=tiles[di], target=tiles[ti];
    const appsOf = t => t.group ? t.children.slice() : [t.app];
    if(target.group){
      target.children = [...new Set([...target.children, ...appsOf(drag)])];
      tiles.splice(di,1);
    } else {
      const kids=[...new Set([...appsOf(target), ...appsOf(drag)])];
      const grp={ id:'g-'+Date.now(), group:true, name:'folder', size:target.size==='small'?'medium':target.size, color:target.color, children:kids };
      tiles.splice(ti,1,grp);
      const ndi=tiles.findIndex(t=>t.id===dragId); if(ndi>=0) tiles.splice(ndi,1);
    }
    L.render(); L.save(); L.toast('grouped');
  }

  /* ---------- pointer interactions (tap / long-press / drag) ---------- */
  let press=null;
  function onDown(e){
    const grid=$('#grid');
    const ctrl = e.target.closest('.tile-controls button');
    const tileEl = e.target.closest('.tile');
    const scr=$('#screen');
    // control buttons in edit
    if(ctrl && tileEl){
      e.preventDefault();
      const id=tileEl.dataset.id;
      if(ctrl.dataset.act==='unpin') L.unpinTile(id);
      else if(ctrl.dataset.act==='resize') cycleSize(tileEl);
      return;
    }
    if(!tileEl) { // tapping empty space in edit → exit
      if(scr.classList.contains('edit') && e.target.closest('#page-home')) L.exitEdit();
      return;
    }
    const id=tileEl.dataset.id;
    press = { id, x:e.clientX, y:e.clientY, t:Date.now(), moved:false, dragging:false, longTimer:null, lastTarget:null };
    if(scr.classList.contains('edit')){
      // start drag candidate immediately
    } else {
      press.longTimer = setTimeout(()=>{ if(press && !press.moved){ navigator.vibrate&&navigator.vibrate(8); press.enteredEdit=true; L.selectId=press.id; L.enterEdit(); L.render(); } }, 430);
    }
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp, {once:true});
  }
  function onMove(e){
    if(!press) return;
    const dx=e.clientX-press.x, dy=e.clientY-press.y;
    if(!press.moved && Math.hypot(dx,dy)>7){ press.moved=true; if(press.longTimer) clearTimeout(press.longTimer); }
    const scr=$('#screen');
    if(!scr.classList.contains('edit')) return; // allow scroll when not editing
    if(!press.moved) return;
    e.preventDefault();
    if(!press.dragging){ press.dragging=true; L.dragId=press.id; markDragging(); }
    // find target under pointer
    const el=document.elementFromPoint(e.clientX,e.clientY);
    const tgt = el && el.closest('.tile');
    clearMerge();
    if(tgt && tgt.dataset.id!==press.id){
      const r=tgt.getBoundingClientRect();
      const cx=(e.clientX-r.left)/r.width, cy=(e.clientY-r.top)/r.height;
      const inCenter = cx>0.22 && cx<0.78 && cy>0.22 && cy<0.78;
      if(inCenter){ tgt.classList.add('merge-target'); press.merge=tgt.dataset.id; press.lastTarget=null; }
      else {
        press.merge=null;
        if(press.lastTarget!==tgt.dataset.id){ press.lastTarget=tgt.dataset.id; reorder(press.id, tgt.dataset.id); }
      }
    } else { press.merge=null; }
  }
  function onUp(e){
    window.removeEventListener('pointermove', onMove);
    if(!press){ return; }
    const scr=$('#screen');
    if(press.dragging){
      if(press.merge) doMerge(press.id, press.merge);
      L.dragId=null; clearMerge(); L.render(); L.save();
    } else if(!press.moved){
      // tap
      const t=L.state.tiles.find(x=>x.id===press.id);
      if(scr.classList.contains('edit')){
        // a fresh tap on a tile (not the long-press that entered edit) dismisses edit mode
        if(!press.enteredEdit) L.exitEdit();
      } else {
        if(t && t.group) L.openGroup(t);
        else if(t) L.launch(t.app);
      }
    }
    press=null;
  }
  function markDragging(){
    const el=$('#grid .tile[data-id="'+CSS.escape(L.dragId)+'"]'); if(el) el.classList.add('dragging');
  }
  function clearMerge(){ document.querySelectorAll('.merge-target').forEach(t=>t.classList.remove('merge-target')); }
  function reorder(dragId, targetId){
    const tiles=L.state.tiles;
    const di=tiles.findIndex(t=>t.id===dragId), ti=tiles.findIndex(t=>t.id===targetId);
    if(di<0||ti<0||di===ti) return;
    const [m]=tiles.splice(di,1);
    tiles.splice(ti,0,m);
    L.render(); markDragging();
  }

  /* ---------- scaling ---------- */
  function fit(){
    const dev=$('#device');
    const availW=window.innerWidth-32, availH=window.innerHeight-104;
    scale=Math.min(availW/dev.offsetWidth, availH/dev.offsetHeight, 1.05);
    dev.style.transform='scale('+scale+')';
  }
  L.getScale=()=>scale;

  /* ---------- global click delegation (dock, buttons, nav) ---------- */
  function onClick(e){
    const launch=e.target.closest('[data-launch]');
    if(launch){
      const id=launch.dataset.launch;
      if($('#groupOv').classList.contains('on')){ L.closeGroup(); }
      if($('#recentsOv').classList.contains('on')){ L.closeRecents(); }
      L.launch(id); return;
    }
    if(e.target.closest('#navHome')){ L.goHome(); return; }
    if(e.target.closest('#navSearch')){ L.openSearch(); return; }
    if(e.target.closest('#navRecents')){ L.openRecents(); return; }
    if(e.target.id==='recentsOv'){ L.closeRecents(); return; }
    if(e.target.closest('#rClear')){ L.closeRecents(); L.toast('cleared'); return; }
    if(e.target.closest('#allAppsBtn')) { L.go('apps'); return; }
    if(e.target.closest('#backHome')) { L.go('home'); return; }
    if(e.target.closest('#gClose')) { L.closeGroup(); return; }
    if(e.target.closest('#groupOv') && e.target.id==='groupOv'){ L.closeGroup(); return; }
    if(e.target.closest('#setClose') || e.target.id==='setScrim'){ L.closeSettings(); return; }
    if(e.target.closest('#editDone')) { L.exitEdit(); return; }
    if(e.target.closest('#editPersonalize')) { L.openSettings(); return; }
    if(e.target.closest('#editAdd')) { L.exitEdit(); L.go('apps'); L.toast('long-press an app to pin'); return; }
  }

  /* ---------- init ---------- */
  L.init = function(){
    const scr=$('#screen');
    scr.style.setProperty('--u','90px');
    scr.style.setProperty('--gap','3px');
    scr.style.setProperty('--side','9px');
    L.applyChrome();
    L.render();
    if(window.Screens) window.Screens.init(L);
    window.startFlips();
    fit(); window.addEventListener('resize', fit);
    const grid=$('#grid');
    grid.addEventListener('pointerdown', onDown);
    document.addEventListener('click', onClick);
    // interactive horizontal swipe between home <-> apps (finger-following)
    const pagesEl=$('.pages'), homeEl=$('#page-home'), appsEl=$('#page-apps');
    let pg=null;
    function swBlocked(){ return scr.classList.contains('edit') || $('#setSheet').classList.contains('on') || $('#groupOv').classList.contains('on') || $('#jumpGrid').classList.contains('on') || $('#recentsOv').classList.contains('on'); }
    scr.addEventListener('pointerdown', e=>{ if(swBlocked()) { pg=null; return; } pg={x:e.clientX,y:e.clientY,onApps:scr.classList.contains('apps'),active:false,f:0}; pg.f=pg.onApps?1:0; });
    scr.addEventListener('pointermove', e=>{
      if(!pg) return;
      const dx=e.clientX-pg.x, dy=e.clientY-pg.y;
      if(!pg.active){
        if(Math.abs(dx)>12 && Math.abs(dx)>Math.abs(dy)*1.2){ pg.active=true; pagesEl.classList.add('dragging-pages'); }
        else if(Math.abs(dy)>12){ pg=null; return; }
        else return;
      }
      const w=pagesEl.getBoundingClientRect().width||360;
      let f = pg.onApps ? (1 - dx/w) : (-dx/w);
      f=Math.max(0,Math.min(1,f)); pg.f=f;
      homeEl.style.transform=`translateX(${-22*f}%)`;
      homeEl.style.opacity=`${1-0.45*f}`;
      appsEl.style.transform=`translateX(${100-100*f}%)`;
    });
    scr.addEventListener('pointerup', ()=>{
      if(pg && pg.active){
        pagesEl.classList.remove('dragging-pages');
        homeEl.style.transform=''; homeEl.style.opacity=''; appsEl.style.transform='';
        if(pg.f>=0.5) scr.classList.add('apps'); else scr.classList.remove('apps');
      }
      pg=null;
    });
    // clock face initial
    document.querySelectorAll('#grid .tile[data-live="clock"]').forEach(()=>{});
  };

  document.addEventListener('DOMContentLoaded', L.init);
})();
