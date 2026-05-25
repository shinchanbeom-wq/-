const screens = {
  title: document.getElementById('title-screen'),
  songSelect: document.getElementById('song-select-screen'),
  settings: document.getElementById('settings-screen'),
  game: document.getElementById('game-screen')
};

const DEFAULT_SETTINGS = { offsetMs: 0, keybinds: ['d', 'f', 'j', 'k'] };
const JUDGE_EFFECT_CLASS = { '완벽':'hit-perfect', '좋음':'hit-good', '나쁨':'hit-bad', '최악':'hit-worst' };
const JUDGE_WINDOWS = [
  { name: '완벽', ms: 40, score: 1000 },
  { name: '좋음', ms: 80, score: 700 },
  { name: '나쁨', ms: 120, score: 400 },
  { name: '최악', ms: 160, score: 100 }
];

let settings = loadSettings();
let songs = [];
let gameState = null;

function showScreen(name) {
  Object.values(screens).forEach(s => s.classList.remove('active'));
  screens[name].classList.add('active');
}

function loadSettings() {
  try {
    return { ...DEFAULT_SETTINGS, ...JSON.parse(localStorage.getItem('rg4_settings') || '{}') };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

function saveSettings() {
  localStorage.setItem('rg4_settings', JSON.stringify(settings));
}

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
  lanes.style.height = `${Math.max(280, window.innerHeight - 180)}px`;
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

async function startGame(song) {
  showScreen('game');
  setupLanes();

  const chart = await (await fetch(song.chart)).json();
  const audio = new Audio(song.audio);

  gameState = {
    song,
    chart,
    audio,
    startTime: 0,
    score: 0,
    combo: 0,
    notes: chart.notes.map(n => ({ ...n, hit: false, judged: false }))
  };

  document.getElementById('song-title').textContent = `${song.title} [${chart.difficulty}]`;
  document.getElementById('score').textContent = '0';
  document.getElementById('combo').textContent = '0';

  audio.addEventListener('ended', () => {
    alert(`플레이 종료! 점수: ${gameState.score}`);
    showScreen('title');
  });

  await audio.play().catch(() => alert('오디오 파일을 찾을 수 없습니다. sample.ogg를 넣어주세요.'));
  gameState.startTime = performance.now();
  requestAnimationFrame(renderLoop);
}

function getSongTimeMs() {
  return (performance.now() - gameState.startTime) + settings.offsetMs;
}

function judgeHit(lane) {
  if (!gameState) return;
  const t = getSongTimeMs();
  const candidates = gameState.notes.filter(n => !n.judged && n.lane === lane);
  if (!candidates.length) return;

  let target = candidates[0];
  let bestDelta = Math.abs(target.timeMs - t);

  for (const n of candidates) {
    const d = Math.abs(n.timeMs - t);
    if (d < bestDelta) { target = n; bestDelta = d; }
  }

  let result = null;
  for (const w of JUDGE_WINDOWS) {
    if (bestDelta <= w.ms) { result = w; break; }
  }

  if (result) {
    target.judged = true;
    target.hit = true;
    gameState.score += result.score;
    gameState.combo += 1;
    pulseLane(lane);
    spawnHitBurst(lane, result.name);
    updateHUD(result.name);
  }
}

function markMisses() {
  const t = getSongTimeMs();
  gameState.notes.forEach(n => {
    if (!n.judged && t - n.timeMs > JUDGE_WINDOWS[JUDGE_WINDOWS.length - 1].ms) {
      n.judged = true;
      gameState.combo = 0;
      updateHUD('놓침');
    }
  });
}

function updateHUD(judge) {
  document.getElementById('score').textContent = gameState.score;
  document.getElementById('combo').textContent = gameState.combo;
  document.getElementById('judge-text').textContent = judge;
}

function renderLoop() {
  if (!gameState || gameState.audio.paused) return;

  markMisses();
  const t = getSongTimeMs();
  const lanes = document.querySelectorAll('.lane');
  lanes.forEach(l => l.querySelectorAll('.note').forEach(n => n.remove()));

  gameState.notes.forEach(n => {
    if (n.judged) return;
    const diff = n.timeMs - t;
    const y = 460 - diff * 0.35;
    if (y < -20 || y > 520) return;
    const el = document.createElement('div');
    el.className = 'note';
    el.style.top = `${y}px`;
    lanes[n.lane].appendChild(el);
  });

  requestAnimationFrame(renderLoop);
}

window.addEventListener('keydown', (e) => {
  if (!gameState) return;
  const lane = settings.keybinds.indexOf(e.key.toLowerCase());
  if (lane >= 0) {
    pulseLane(lane);
    judgeHit(lane);
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
