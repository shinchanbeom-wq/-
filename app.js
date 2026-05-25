const screens = {
  title: document.getElementById('title-screen'),
  songSelect: document.getElementById('song-select-screen'),
  settings: document.getElementById('settings-screen'),
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
      released: false
    };
  });
}

async function startGame(song) {
  showScreen('game');
  refreshLayout();
  setupLanes();
  const chart = await (await fetch(song.chart)).json();
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
        const top = Math.min(headY, tailY);
        const height = Math.max(10, Math.abs(tailY - headY));
        if (top > laneEl.clientHeight + 40 || top + height < -40) return;

        const tail = document.createElement('div');
        tail.className = 'hold-tail';
        tail.style.top = `${top}px`;
        tail.style.height = `${height}px`;
        laneEl.appendChild(tail);

        const head = document.createElement('div');
        head.className = 'note hold-head';
        head.style.top = `${headY - 8}px`;
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
