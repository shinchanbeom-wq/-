const screens = {
  title: document.getElementById('title-screen'),
  songSelect: document.getElementById('song-select-screen'),
  settings: document.getElementById('settings-screen'),
  chartDev: document.getElementById('chart-dev-screen'),
  game: document.getElementById('game-screen')
};

const DEFAULT_SETTINGS = { offsetMs: 0, keybinds: ['d', 'f', 'j', 'k'] };
const JUDGE_EFFECT_CLASS = { '완벽':'hit-perfect', '좋음':'hit-good', '나쁨':'hit-bad', '최악':'hit-worst', '놓침':'hit-miss' };
const JUDGE_WINDOWS = [
  { name: '완벽', ms: 40, score: 1000 },
  { name: '좋음', ms: 80, score: 700 },
  { name: '나쁨', ms: 120, score: 400 },
  { name: '최악', ms: 160, score: 100 }
];
const MISS_WINDOW = JUDGE_WINDOWS[JUDGE_WINDOWS.length - 1].ms;
const NOTE_TRAVEL_MS = 1400;

let settings = loadSettings();
let songs = [];
let gameState = null;
let selectedSong = null;
let editorChart = { difficulty: "Custom", notes: [] };

function showScreen(name) {
  Object.values(screens).forEach(s => s.classList.remove('active'));
  screens[name].classList.add('active');
}

function loadSettings() {
  try { return { ...DEFAULT_SETTINGS, ...JSON.parse(localStorage.getItem('rg4_settings') || '{}') }; }
  catch { return { ...DEFAULT_SETTINGS }; }
}

function saveSettings() { localStorage.setItem('rg4_settings', JSON.stringify(settings)); }

function initSettingsUI() {
  const offsetInput = document.getElementById('offset-ms');
  const keybindList = document.getElementById('keybind-list');
  offsetInput.value = settings.offsetMs;
  keybindList.innerHTML = '';
  settings.keybinds.forEach((key, i) => {
    const row = document.createElement('div');
    row.className = 'key-row';
    row.innerHTML = `Lane ${i + 1}: <input data-lane="${i}" value="${key}" maxlength="1" />`;
    keybindList.appendChild(row);
  });
  document.getElementById('save-settings').onclick = () => {
    settings.offsetMs = Number(offsetInput.value || 0);
    keybindList.querySelectorAll('input').forEach(inp => {
      const lane = Number(inp.dataset.lane);
      settings.keybinds[lane] = inp.value.toLowerCase() || DEFAULT_SETTINGS.keybinds[lane];
    });
    saveSettings();
    alert('설정이 저장되었습니다.');
  };
}

async function loadSongList() {
  const index = await (await fetch('songs/index.json')).json();
  songs = await Promise.all(index.map(async path => await (await fetch(path)).json()));
  const list = document.getElementById('song-list');
  list.innerHTML = '';
  songs.forEach(song => {
    const btn = document.createElement('button');
    btn.textContent = `${song.title} - ${song.artist}`;
    btn.onclick = () => startGame(song);
    list.appendChild(btn);
  });

  const songSel = document.getElementById('chart-dev-song-select');
  songSel.innerHTML = '';
  songs.forEach(song => {
    const opt = document.createElement('option');
    opt.value = song.id;
    opt.textContent = `${song.title} - ${song.artist}`;
    songSel.appendChild(opt);
  });

}

function pulseLane(laneIndex) {
  const laneEl = document.querySelector(`.lane[data-lane="${laneIndex}"]`);
  if (!laneEl) return;
  laneEl.classList.remove('flash');
  void laneEl.offsetWidth;
  laneEl.classList.add('flash');
  setTimeout(() => laneEl.classList.remove('flash'), 90);
}

function spawnHitBurst(laneIndex, judgeName) {
  const laneEl = document.querySelector(`.lane[data-lane="${laneIndex}"]`);
  if (!laneEl) return;
  const burst = document.createElement('div');
  burst.className = `hit-burst ${JUDGE_EFFECT_CLASS[judgeName] || 'hit-good'}`;
  laneEl.appendChild(burst);
  setTimeout(() => burst.remove(), 320);
}

function refreshLayout() {
  const lanes = document.getElementById('lanes');
  lanes.style.height = `${Math.max(300, window.innerHeight - 170)}px`;
}


function getCustomChartKey(songId) { return `rg4_custom_chart_${songId}`; }

async function resolveChart(song) {
  const custom = localStorage.getItem(getCustomChartKey(song.id));
  if (custom) {
    try { return JSON.parse(custom); } catch {}
  }
  return await (await fetch(song.chart)).json();
}

async function loadChartIntoEditor(song) {
  const chart = await resolveChart(song);
  editorChart = JSON.parse(JSON.stringify(chart));
  redrawTimelineEditor();
}

function readEditorChart() {
  return JSON.parse(JSON.stringify(editorChart));
}

function validateChart(chart) {
  if (!chart || !Array.isArray(chart.notes)) return 'notes 배열이 필요합니다.';
  for (const n of chart.notes) {
    if (typeof n.timeMs !== 'number' || typeof n.lane !== 'number') return '모든 노트에 timeMs/lane 숫자가 필요합니다.';
    if (n.lane < 0 || n.lane > 3) return 'lane은 0~3만 허용됩니다.';
    if (n.endTimeMs != null && !(n.endTimeMs > n.timeMs)) return '홀드노트 endTimeMs는 timeMs보다 커야 합니다.';
  }
  return null;
}


let editorState = { selectedNoteId: null, mode: 'tap', timelineMaxMs: 8000 };

function getEditorChartObj() {
  if (!editorChart || typeof editorChart !== 'object') editorChart = { difficulty: 'Custom', notes: [] };
  if (!Array.isArray(editorChart.notes)) editorChart.notes = [];
  return editorChart;
}

function getEditorBpm() {
  if (selectedSong && Number(selectedSong.bpm) > 0) return Number(selectedSong.bpm);
  return 120;
}

function redrawTimelineEditor() {
  const host = document.getElementById('timeline-editor');
  if (!host) return;
  host.innerHTML = '';
  const chart = getEditorChartObj();
  if (!chart) return;

  for (let lane = 0; lane < 4; lane++) {
    const laneEl = document.createElement('div');
    laneEl.className = 'tl-lane';
    laneEl.dataset.lane = lane;
    const inner = document.createElement('div');
    inner.className = 'tl-lane-inner';
    const laneHeight = inner.offsetHeight || 1600;
    inner.onclick = (e) => {
      if (e.target !== inner) return;
      const rect = inner.getBoundingClientRect();
      const y = Math.max(0, Math.min(rect.height, e.clientY - rect.top));
      const timeMs = Math.round((y / rect.height) * editorState.timelineMaxMs);
      if (editorState.mode === 'hold') {
        chart.notes.push({ timeMs, lane, endTimeMs: Math.min(editorState.timelineMaxMs, timeMs + 1000) });
      } else {
        chart.notes.push({ timeMs, lane });
      }
      chart.notes.sort((a,b)=>a.timeMs-b.timeMs);
      redrawTimelineEditor();
    };

    const bpm = getEditorBpm();
    const beatMs = 60000 / bpm;
    for (let t = 0; t <= editorState.timelineMaxMs; t += beatMs) {
      const y = (t / editorState.timelineMaxMs) * 100;
      const line = document.createElement('div');
      line.className = 'tl-beat';
      line.style.top = `${y}%`;
      inner.appendChild(line);
      if (lane === 3) {
        const lbl = document.createElement('div');
        lbl.className = 'tl-beat-label';
        lbl.style.top = `${y}%`;
        lbl.textContent = `${Math.round(t)}ms`;
        inner.appendChild(lbl);
      }
    }

    chart.notes.forEach((n, idx) => {
      if (n.lane !== lane) return;
      if (typeof n.endTimeMs === 'number' && n.endTimeMs > n.timeMs) {
        const hold = document.createElement('div');
        hold.className = 'tl-hold' + (editorState.selectedNoteId === idx ? ' selected' : '');
        hold.style.top = `${(n.timeMs / editorState.timelineMaxMs) * 100}%`;
        hold.style.height = `${((n.endTimeMs - n.timeMs) / editorState.timelineMaxMs) * 100}%`;
        hold.onclick = (ev) => { ev.stopPropagation(); editorState.selectedNoteId = idx; redrawTimelineEditor(); };
        inner.appendChild(hold);
      }
      const note = document.createElement('div');
      note.className = 'tl-note' + (editorState.selectedNoteId === idx ? ' selected' : '');
      note.style.top = `${(n.timeMs / editorState.timelineMaxMs) * 100}%`;
      note.onclick = (ev) => { ev.stopPropagation(); editorState.selectedNoteId = idx; redrawTimelineEditor(); };
      inner.appendChild(note);
    });
    laneEl.appendChild(inner);
    host.appendChild(laneEl);
  }
}

function setupLanes() {
  const lanes = document.getElementById('lanes');
  lanes.innerHTML = '';
  for (let i = 0; i < 4; i++) {
    const lane = document.createElement('div');
    lane.className = 'lane';
    lane.dataset.lane = i;
    const judgeLine = document.createElement('div');
    judgeLine.className = 'judge-line';
    lane.appendChild(judgeLine);
    lanes.appendChild(lane);
  }
}

function normalizeChartNotes(notes) {
  return notes.map((n, idx) => {
    const isHold = Number(n.endTimeMs) > Number(n.timeMs);
    return {
      id: idx,
      lane: Number(n.lane),
      timeMs: Number(n.timeMs),
      endTimeMs: isHold ? Number(n.endTimeMs) : null,
      type: isHold ? 'hold' : 'tap',
      judged: false,
      holding: false,
      holdHeadJudge: null,
      released: false,
      holdTickMs: 0
    };
  });
}

async function startGame(song, injectedChart = null) {
  showScreen('game');
  refreshLayout();
  setupLanes();
  const chart = injectedChart || await resolveChart(song);
  const audio = new Audio(song.audio);
  gameState = {
    song, chart, audio, startTime: 0, score: 0, combo: 0,
    notes: normalizeChartNotes(chart.notes),
    activeHolds: new Map(),
    heldKeys: new Set()
  };
  document.getElementById('song-title').textContent = `${song.title} [${chart.difficulty}]`;
  updateHUD('READY');
  audio.addEventListener('ended', () => {
    alert(`플레이 종료! 점수: ${gameState.score}`);
    showScreen('title');
  });
  await audio.play().catch(() => alert('오디오 파일을 찾을 수 없습니다. sample.ogg를 넣어주세요.'));
  gameState.startTime = performance.now();
  requestAnimationFrame(renderLoop);
}

function getSongTimeMs() { return (performance.now() - gameState.startTime) + settings.offsetMs; }

function findNearestNote(lane) {
  const t = getSongTimeMs();
  const candidates = gameState.notes.filter(n => !n.judged && !n.holding && n.lane === lane);
  if (!candidates.length) return null;
  return candidates.reduce((best, cur) => {
    const bd = Math.abs(best.timeMs - t);
    const cd = Math.abs(cur.timeMs - t);
    return cd < bd ? cur : best;
  });
}

function judgeFromDelta(delta) {
  for (const w of JUDGE_WINDOWS) if (delta <= w.ms) return w;
  return null;
}

function onLanePress(lane) {
  if (!gameState) return;
  const t = getSongTimeMs();
  pulseLane(lane);
  const note = findNearestNote(lane);
  if (!note) return;
  const delta = Math.abs(note.timeMs - t);
  const result = judgeFromDelta(delta);
  if (!result) return;

  if (note.type === 'tap') {
    note.judged = true;
  } else {
    note.holding = true;
    note.holdHeadJudge = result;
    note.holdTickMs = note.timeMs;
    gameState.activeHolds.set(lane, note.id);
  }

  gameState.score += result.score;
  gameState.combo += 1;
  spawnHitBurst(lane, result.name);
  updateHUD(result.name);
}

function completeHoldNote(note, lane, judgeName) {
  note.released = true;
  note.judged = true;
  note.holding = false;
  gameState.activeHolds.delete(lane);
  if (judgeName === '놓침') {
    gameState.combo = 0;
  } else {
    const w = JUDGE_WINDOWS.find(x => x.name === judgeName);
    if (w) {
      gameState.score += w.score;
      gameState.combo += 1;
    }
  }
  spawnHitBurst(lane, judgeName);
  updateHUD(judgeName);
}

function onLaneRelease(lane) {
  if (!gameState) return;
  const noteId = gameState.activeHolds.get(lane);
  if (noteId == null) return;
  const note = gameState.notes.find(n => n.id === noteId);
  if (!note || note.type !== 'hold' || note.released) return;

  const t = getSongTimeMs();
  const endMs = note.endTimeMs ?? note.timeMs;
  const deltaEnd = Math.abs(endMs - t);
  const result = judgeFromDelta(deltaEnd);

  if (result) completeHoldNote(note, lane, result.name);
  else completeHoldNote(note, lane, '놓침');
}

function markMisses() {
  const t = getSongTimeMs();
  gameState.notes.forEach(n => {
    if (n.judged) return;
    if (n.type === 'tap' && t - n.timeMs > MISS_WINDOW) {
      n.judged = true;
      gameState.combo = 0;
      updateHUD('놓침');
    }
    if (n.type === 'hold') {
      if (!n.holding && t - n.timeMs > MISS_WINDOW) {
        n.judged = true;
        gameState.combo = 0;
        updateHUD('놓침');
      } else if (n.holding) {
        const endMs = n.endTimeMs ?? n.timeMs;
        if (gameState.heldKeys.has(n.lane)) {
          while (n.holdTickMs + 120 <= Math.min(t, endMs)) {
            n.holdTickMs += 120;
            gameState.score += 40;
          }
        }
        if (t >= endMs && gameState.heldKeys.has(n.lane)) {
          completeHoldNote(n, n.lane, '완벽');
        } else if (t - endMs > MISS_WINDOW) {
          completeHoldNote(n, n.lane, '놓침');
        }
      }
    }
  });
}

function updateHUD(judge) {
  document.getElementById('score').textContent = gameState ? gameState.score : 0;
  document.getElementById('combo').textContent = gameState ? gameState.combo : 0;
  document.getElementById('judge-text').textContent = judge;
}

function renderLoop() {
  if (!gameState || gameState.audio.paused) return;
  markMisses();
  const t = getSongTimeMs();
  const lanes = document.querySelectorAll('.lane');
  lanes.forEach(l => l.querySelectorAll('.note,.hold-tail').forEach(n => n.remove()));

  lanes.forEach((laneEl, idx) => {
    const judgeY = laneEl.clientHeight - 40;
    gameState.notes.forEach(n => {
      if (n.judged && !n.holding) return;
      if (n.lane !== idx) return;

      const headY = judgeY - ((n.timeMs - t) / NOTE_TRAVEL_MS) * judgeY;
      if (n.type === 'tap') {
        if (headY < -20 || headY > laneEl.clientHeight + 20) return;
        const el = document.createElement('div');
        el.className = 'note';
        el.style.top = `${headY - 8}px`;
        laneEl.appendChild(el);
      } else {
        const endMs = n.endTimeMs ?? n.timeMs;
        const tailY = judgeY - ((endMs - t) / NOTE_TRAVEL_MS) * judgeY;
        let renderHeadY = headY;
        if (n.holding) {
          renderHeadY = judgeY;
        }
        const top = Math.min(renderHeadY, tailY);
        const height = Math.max(6, Math.abs(tailY - renderHeadY));
        if (top > laneEl.clientHeight + 40 || top + height < -40) return;

        const tail = document.createElement('div');
        tail.className = 'hold-tail';
        tail.style.top = `${top}px`;
        tail.style.height = `${height}px`;
        laneEl.appendChild(tail);

        const head = document.createElement('div');
        head.className = 'note hold-head';
        head.style.top = `${renderHeadY - 8}px`;
        laneEl.appendChild(head);
      }
    });
  });
  requestAnimationFrame(renderLoop);
}

window.addEventListener('keydown', (e) => {
  if (!gameState) return;
  const lane = settings.keybinds.indexOf(e.key.toLowerCase());
  if (lane >= 0 && !gameState.heldKeys.has(lane)) {
    gameState.heldKeys.add(lane);
    onLanePress(lane);
  }
});
window.addEventListener('keyup', (e) => {
  if (!gameState) return;
  const lane = settings.keybinds.indexOf(e.key.toLowerCase());
  if (lane >= 0) {
    gameState.heldKeys.delete(lane);
    onLaneRelease(lane);
  }
});

document.getElementById('to-song-select').onclick = () => showScreen('songSelect');
document.getElementById('to-settings').onclick = () => { initSettingsUI(); showScreen('settings'); };
document.getElementById('back-from-song').onclick = () => showScreen('title');
document.getElementById('back-from-settings').onclick = () => showScreen('title');
document.getElementById('exit-game').onclick = () => {
  if (!gameState) return;
  gameState.audio.pause();
  gameState = null;
  showScreen('title');
};

loadSongList().catch(err => {
  console.error(err);
  alert('곡 데이터를 불러오지 못했습니다. songs/index.json을 확인하세요.');
});
window.addEventListener('resize', refreshLayout);
refreshLayout();


document.getElementById('chart-dev-load').onclick = async () => {
  const id = document.getElementById('chart-dev-song-select').value;
  selectedSong = songs.find(s => s.id === id) || null;
  if (!selectedSong) { alert('곡을 선택하세요.'); return; }
  await loadChartIntoEditor(selectedSong);
};
document.getElementById('chart-dev-save').onclick = () => {
  const id = document.getElementById('chart-dev-song-select').value;
  selectedSong = songs.find(s => s.id === id) || null;
  if (!selectedSong) { alert('곡을 선택하세요.'); return; }
  const chart = readEditorChart();
  if (!chart) return;
  const err = validateChart(chart);
  if (err) { alert(err); return; }
  localStorage.setItem(getCustomChartKey(selectedSong.id), JSON.stringify(chart));
  alert('커스텀 채보 저장 완료');
};
document.getElementById('chart-dev-test').onclick = () => {
  const id = document.getElementById('chart-dev-song-select').value;
  selectedSong = songs.find(s => s.id === id) || null;
  if (!selectedSong) { alert('곡을 선택하세요.'); return; }
  const chart = readEditorChart();
  if (!chart) return;
  const err = validateChart(chart);
  if (err) { alert(err); return; }
  startGame(selectedSong, chart);
};
document.getElementById('add-tap-note').onclick = () => { editorState.mode = 'tap'; };
document.getElementById('add-hold-note').onclick = () => { editorState.mode = 'hold'; };
document.getElementById('clear-selected-note').onclick = () => {
  const chart = getEditorChartObj();
  if (!chart) return;
  if (editorState.selectedNoteId == null) return;
  chart.notes.splice(editorState.selectedNoteId, 1);
  editorState.selectedNoteId = null;
  redrawTimelineEditor();
};

document.getElementById('to-chart-dev').onclick = () => showScreen('chartDev');
document.getElementById('back-from-chart-dev').onclick = () => showScreen('title');
