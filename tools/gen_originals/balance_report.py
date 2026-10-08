"""docs/BALANCE.md — 원작 곡선 · 오리지널 비교 · 전투 예시 (sh tools/gen_originals/build.sh 가 함께 만든다)"""
import sys, math, yaml
sys.path.insert(0, __file__.rsplit("/", 1)[0])
from balance import Canon, weapon_power, armor_power, req_level, SLOT, DANGER_LEVEL

REPO = sys.argv[1] if len(sys.argv) > 1 else "."
c = Canon(REPO)
O = REPO + "/src/main/resources/content/original/"
oi = yaml.safe_load(open(O + "items.yml"))
items, sets = oi["items"], oi["sets"]
prices = yaml.safe_load(open(O + "market.yml"))["prices"]
mons = yaml.safe_load(open(O + "monsters.yml"))["monsters"]
bosses = yaml.safe_load(open(O + "field_bosses.yml"))["field_bosses"]
canon_items = c.items
out = []
w = out.append

def dmg_out(atk, elem=0, mastery=20, quality=500, crit=5):
    q = 0.7 + 0.7 * quality / 1000
    base = atk * q * (1 + min(31, mastery) * 0.015)
    return base * (1 + crit / 100 * 0.5) + elem * q

def dmg_in(monster_dmg, defense, resist=0):
    return monster_dmg * 100 / (100 + defense * 4) * (1 - min(40, resist) / 100)

w("# 밸런스 기준 (오리지널 콘텐츠)\n")
w("오리지널 수치는 손으로 찍지 않고 **원작(CANON) 데이터에서 뽑은 곡선**에 맞춰 만든다 (`tools/gen_originals/balance.py` · `balance_pass.py`).")
w("`BalanceTest`가 같은 곡선을 자바로 다시 계산해, 오리지널이 곡선 밖으로 나가면 빌드를 실패시킨다. 원작 파일을 고치면 곡선도 함께 움직인다.\n")
w("> 실제 서버에서 사람이 싸워 본 결과가 아니라 **공식과 원작 수치에서 계산한 균형**이다. 실제 테스트로 고칠 곳이 나오면 `balance.py`의 표를 고친다.\n")
w("## 전투 공식 (코드 그대로)\n")
w("- 주는 피해 = 공격력 × 품질 배율(0.7~1.4) × (1 + 숙련 × 1.5%) × 치명(1.5배) + 속성 고정 피해 (신성은 언데드 · 악마에 2배)")
w("- 받는 피해 = 몬스터 공격 × 100 / (100 + 방어 × 4) × (1 − 직업 방어%) × (1 − 저항%, 최대 40%)")
w("- 플레이어 체력 = 20 × (1 + 직업 체력%) + 장비 체력\n")
w("## 옵션 환산표\n")
w("| 옵션 | 무기에서 (공격력 점) | 방어구에서 (방어력 점) | 까닭 |")
w("|---|---|---|---|")
rows = [("속성 피해 1", "0.8 (신성 1.0)", "0.6", "숙련 20 · 품질 500 에서 공격력 1 = 피해 1.37, 속성 1 = 1.05"),
        ("치명 1%", "공격력 × 0.005", "0.4", "치명 1.5배 → 피해 +0.5%"),
        ("상대별 추가 피해 1%", "공격력 × 0.0015~0.003", "0.1", "그 상대를 만날 확률만큼"),
        ("체력 1", "1.0", "1.2", "기본 체력 20 의 5%"),
        ("받는 피해 감소 1%", "0.6", "0.7", "체력 1% 와 비슷"),
        ("10초 회복 1", "2.0", "2.5", "꾸준한 회복"),
        ("생명력 흡수 1%", "1.2", "1.5", ""),
        ("기절 확률 1%", "0.4", "0.3", "최대 25%"),
        ("이동 속도 1%", "0.2", "0.3", ""),
        ("저주(10초마다 체력 감소) 1", "−3", "−3", "단점")]
for r in rows: w("| " + " | ".join(r) + " |")
w("\n한 장비 옵션 상한: 치명 15 · 이동 15 · 저항 12 · 체력 8 · 회복 2 · 흡수 6 · 기절 10 (장신구 · 세트 보너스는 치명 8 · 이동 10 · 저항 10 · 체력 6).\n")

w("## 원작 곡선\n")
w(f"- **무기 위력** = 8 + {c.w_b:.2f} × 착용 레벨 (0 레벨 = 철 단검 · 물푸레 활). 제작품 × 0.92 · 보스 드롭 × 1.05")
w(f"- **방어구 위력 (몸통)** = 8 + {c.a_b:.2f} × 착용 레벨. 부위 비율 투구 0.6 · 각반 0.75 · 장화 0.45 · 방패 0.65")
w("- **장신구 위력** = 3 + 0.5 × 만드는 숙련 (원작 장신구 범위)")
hp_a, hp_b = c.m_hp; d_a, d_b = c.m_dmg
w(f"- **들판 몬스터** 체력 = e^{hp_a:.2f} × 레벨^{hp_b:.2f} · 공격 = e^{d_a:.2f} × 레벨^{d_b:.2f} (큰 몸은 따로)")
w("- **위험도 → 몬스터 레벨**: " + " · ".join(f"{k}: {a}~{b}" for k, (a, b) in DANGER_LEVEL.items()))
w("- **필드 보스**: 체력에서 공격 · 돈 · 숙련 경험 · 명성 · 다시 나타나는 시간 (원작 55 보스의 log-log 곡선)")
w("- **직업 보너스 점수**: 전투 1차 원작 4.5~10 · 2차 ~23 / 생활 1차 40~60 · 2차 60 (명장) — 오리지널 2차 생활 직업은 75 이하")
w("- **스킬 점수** = (피해 배율 × 넓이 + 상태 이상 값) ÷ 재사용 초 — 원작 스킬의 최대값을 넘지 않게, 기본기에는 기절 · 빙결 없음\n")

w("## 레벨별 무기 · 방어구 (원작 vs 오리지널)\n")
w("| 레벨 | 원작 곡선 무기 위력 | 원작 무기 예 | 오리지널 무기 예 | 몸통 곡선 | 오리지널 몸통 예 |")
w("|---|---|---|---|---|---|")
for L in (5, 10, 15, 20, 25, 28):
    cw = [f"{v['name']}({weapon_power(v['stats']):.0f})" for k, v in canon_items.items() if v.get("source") == "CANON" and v["category"] == "WEAPON" and abs(req_level(v) - L) <= 2][:2]
    ow = [f"{v['name']}({weapon_power(v['stats']):.0f})" for k, v in items.items() if v["category"] == "WEAPON" and abs(req_level(v) - L) <= 2][:2]
    oa = [f"{v['name']}({armor_power(v['stats']):.0f})" for k, v in items.items() if v["category"] == "ARMOR" and "chest" in (v.get("tags") or []) and abs(req_level(v) - L) <= 3][:2]
    w(f"| {L} | {c.weapon_target(L):.0f} | {', '.join(cw) or '-'} | {', '.join(ow) or '-'} | {c.armor_target(L, 'chest'):.0f} | {', '.join(oa) or '-'} |")

w("\n## 전투 예시 (숙련 20 · 품질 500 · 치명 5% 기준 평균)\n")
w("| 상황 | 한 번 피해 | 몇 번 만에 |")
w("|---|---|---|")
def wp(id): return items[id]["stats"]
for wid, mid in [("mithril_longsword", "salt_crab"), ("stormcaller", "frost_wisp"), ("starfall_greatsword", "lava_golem"), ("bronze_shortsword", "thorn_hare"), ("griffin_longbow", "storm_hawk")]:
    s = wp(wid); m = mons[mid]
    elem = sum(v for k, v in s.items() if k in ("fire", "ice", "lightning", "poison", "dark", "holy"))
    d = dmg_out(s["attack"], elem, mastery=min(31, req_level(items[wid])), crit=5 + s.get("crit", 0))
    w(f"| {items[wid]['name']} → {m['name']} (Lv.{m['level'][0]}~{m['level'][1]}, 체력 {m['hp']}) | {d:.1f} | {math.ceil(m['hp'] / d)} |")
for sid, mid in [("militia", "heath_bandit"), ("mithril_knight", "ash_revenant"), ("dragonscale", "lava_golem")]:
    pieces = [items[f"{sid}_{p}"]["stats"] for p in ("helmet", "chest", "legs", "boots")]
    de = sum(p.get("defense", 0) for p in pieces); res = sum(p.get("resist", 0) for p in pieces); hp = 20 + sum(p.get("health", 0) for p in pieces)
    for n, b in sets[sid]["bonuses"].items():
        de += b.get("defense", 0); res += b.get("resist", 0); hp += b.get("health", 0)
    m = mons[mid]
    d = dmg_in(m["damage"], de, res)
    w(f"| {m['name']}(공격 {m['damage']}) → {sets[sid]['name']} 한 벌 (방어 {de} · 저항 {res}% · 체력 {hp}) | {d:.1f} | {math.ceil(hp / d)} |")

w("\n## 필드 보스\n")
w("| 보스 | 체력 | 공격 | 돈 | 명성 | 다시 나타남(분) |")
w("|---|---|---|---|---|---|")
for k, v in bosses.items():
    w(f"| {v['name']} | {v['hp']} | {v['damage']} | {v['reward']['money']} | {v['reward']['fame']} | {v['respawn_minutes']} |")

w("\n## 경제\n")
w("- 제작품 시세 = 재료값 × 1.5 + 4 × 레벨² + 30 (장비) / 재료값 × 1.3 (그 밖) — 태그 칸은 가장 싼 재료로 계산. 만들어 팔면 조금 남고, 공짜 돈이 되지 않게 테스트가 위아래를 막는다")
w("- 채집물 = 원작 기본 재료 곡선 (구리 6 · 철 10 · 은 28 · 보석 원석 120 · 서리 수정 180) 에 맞춘 5 × 1.2^숙련")
w("- 회복 물약 바닥값: t1 40 · t2 160 · t3 520 (원작) → t4 1600 · t5 4800 — 높은 등급이 더 싸지 않게")
w("- 해독 · 저항 · 마나 · 기력 물약과 음료는 회복력을 올리지 않는다 (`Consumable`) — 회복 물약을 더 싸게 대신하지 못하게")
w("- 원작에 이미 있는 재료(미스릴 · 흑철 · 와이번 비늘)는 새로 만들지 않고 원작 아이템을 쓴다\n")
open(REPO + "/docs/BALANCE.md", "w").write("\n".join(out) + "\n")
print("docs/BALANCE.md")
