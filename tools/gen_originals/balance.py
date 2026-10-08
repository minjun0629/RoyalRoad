"""밸런스 기준 — 원작(CANON) 데이터에서 곡선을 뽑아, 오리지널 수치를 그 곡선에 맞춘다.

전투 공식 (DamageCalculator · CombatListener · ItemOptions):
  주는 피해 = 공격력 × 품질(0.7~1.4) × (1 + 숙련 × 1.5%) × 치명(1.5배, 확률) + 속성 고정 피해 (신성은 언데드 · 악마에 2배)
  받는 피해 = 몬스터 공격 × 100 / (100 + 방어 × 4) × (1 − 직업 방어%) × (1 − 저항%)
  플레이어 체력 = 20 × (1 + 직업 체력%) + 장비 health
그래서 옵션의 값어치를 '공격력 몇 점'(무기) · '방어력 몇 점'(방어구)으로 환산해 한 줄로 비교한다 (WEIGHTS).
같은 표가 자바 io.versaera.domain.balance.Balance 에 있고, BalanceTest 가 원작 곡선 ± 허용 오차를 검사한다.
"""
import math, yaml

# ---------------------------------------------------------------- 옵션 값어치 (공격력 · 방어력 1 점 기준)
# 무기: 숙련 20 · 품질 500 에서 공격력 1 = 피해 약 1.37. 속성 1 = 피해 1.05 → 0.77 점 (신성은 언데드 · 악마 2 배 → 1.0)
# 치명 1% = 피해 +0.5% → 공격력 A 의 0.005 배 · 상대별 % 는 그 상대를 만날 확률(약 1/4)만큼
WEAPON_W = {"fire": 0.8, "ice": 0.8, "lightning": 0.8, "poison": 0.8, "dark": 0.8, "holy": 1.0,
            "crit": ("A", 0.005), "vs_undead": ("A", 0.0025), "vs_demon": ("A", 0.0025), "vs_large": ("A", 0.003),
            "vs_dragon": ("A", 0.0015), "vs_human": ("A", 0.002), "pierce": 0.12, "lifesteal": 1.2, "slow": 0.12, "stun": 0.4,
            "health": 1.0, "resist": 0.6, "speed": 0.2, "regen": 2.0, "thorns": 0.4, "drain": -3.0, "craft": 0.5}
ARMOR_W = {"health": 1.2, "resist": 0.7, "regen": 2.5, "speed": 0.3, "crit": 0.4, "thorns": 0.5, "drain": -3.0, "stun": 0.3,
           "fire": 0.6, "ice": 0.6, "lightning": 0.6, "poison": 0.6, "dark": 0.6, "holy": 0.7, "attack": 1.0, "lifesteal": 1.5,
           "pierce": 0.15, "vs_human": 0.1, "vs_large": 0.1, "vs_undead": 0.1, "vs_demon": 0.1, "vs_dragon": 0.05, "craft": 0.5, "slow": 0.1}
# 한 장비 옵션 상한 (원작 장비에서 본 가장 큰 값 근처) — 옵션 하나에 위력을 몰아주지 않게
CAP = {"crit": 15, "speed": 15, "craft": 4, "resist": 12, "health": 8, "regen": 2, "lifesteal": 6, "pierce": 25, "stun": 10, "slow": 25,
       "fire": 18, "ice": 18, "lightning": 18, "poison": 12, "dark": 14, "holy": 14, "thorns": 8, "attack": 8, "defense": 6,
       "vs_undead": 40, "vs_demon": 40, "vs_large": 25, "vs_dragon": 60, "vs_human": 10}
# 장신구 · 세트 보너스는 다른 장비 위에 더해지므로 더 낮게 (원작 장신구: 치명 8 · 저항 10 · 체력 6 근처)
CAP_ACC = dict(CAP, crit=8, speed=10, resist=10, health=6, attack=6, fire=10, ice=10, lightning=10, poison=8, dark=8, holy=8, craft=3)
CAP_SET = dict(CAP_ACC, defense=8, vs_dragon=25, vs_large=12)
SLOT = {"chest": 1.0, "robe": 1.0, "legs": 0.75, "helmet": 0.6, "boots": 0.45, "shield": 0.65}


def weapon_power(stats):
    a = stats.get("attack", 0)
    p = a
    for k, v in stats.items():
        if k == "attack": continue
        w = WEAPON_W.get(k, 0)
        p += (a * w[1] * v) if isinstance(w, tuple) else w * v
    return p


def armor_power(stats):
    p = stats.get("defense", 0)
    for k, v in stats.items():
        if k != "defense": p += ARMOR_W.get(k, 0) * v
    return p


def req_level(v):
    r = v.get("requires") or {}
    m = [x for k, x in r.items() if k.startswith("mastery.") or k.startswith("stat.")]
    return max(m) if m else 0


def fit_anchor(xs, ys, a):
    """절편을 a 로 고정한 직선 (0 레벨 = 기본 제작품 수준)"""
    return a, sum(x * (y - a) for x, y in zip(xs, ys)) / max(1e-9, sum(x * x for x in xs))


def fit(xs, ys):
    n = len(xs); mx = sum(xs) / n; my = sum(ys) / n
    b = sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / max(1e-9, sum((x - mx) ** 2 for x in xs))
    return my - b * mx, b


class Canon:
    """원작 파일에서 곡선을 뽑는다"""

    def __init__(self, repo):
        c = repo + "/src/main/resources/content/"
        self.items = yaml.safe_load(open(c + "items.yml"))["items"]
        self.prices = yaml.safe_load(open(c + "market.yml"))["prices"]
        self.monsters = yaml.safe_load(open(c + "monsters.yml"))["monsters"]
        self.bosses = yaml.safe_load(open(c + "field_bosses.yml"))["field_bosses"]
        self.regions = yaml.safe_load(open(c + "regions.yml"))["regions"]
        canon = {k: v for k, v in self.items.items() if v.get("source") == "CANON"}
        # 무기: 착용 레벨이 있는 것 (레벨 없는 원작 무기는 이야기용이라 들쭉날쭉 → 곡선에서 뺀다)
        W = [(req_level(v), weapon_power(v.get("stats", {}))) for v in canon.values()
             if v["category"] == "WEAPON" and req_level(v) > 0 and v.get("stats", {}).get("attack", 0) > 0 and "cursed" not in v.get("tags", [])]
        # 0 레벨 = 철 단검 · 물푸레 활 (8) — 원작 무기는 거의 다 착용 레벨이 있어 낮은 쪽 곡선을 기본 장비로 붙든다
        self.w_a, self.w_b = fit_anchor([x for x, _ in W], [y for _, y in W], 8.0)
        self.w_pts = W
        # 방어구 (몸통 기준으로 환산)
        A = []
        for v in canon.values():
            if v["category"] != "ARMOR" or req_level(v) <= 0: continue
            slot = self.slot(v)
            if slot is None: continue
            A.append((req_level(v), armor_power(v.get("stats", {})) / SLOT[slot]))
        # 0 레벨 몸통 = 가죽 조끼 6 · 철 사슬 갑옷 12 의 가운데
        self.a_a, self.a_b = fit_anchor([x for x, _ in A], [y for _, y in A], 8.0)
        self.a_pts = A
        # 몬스터: 레벨 → 체력 · 공격 (log-log, 큰 몸 · 보통 따로)
        nm = [(v["level"][0] + v["level"][-1]) / 2 for v in self.monsters.values()]
        def ll(sel, key):
            xs, ys = [], []
            for v in self.monsters.values():
                if sel(v):
                    xs.append(math.log((v["level"][0] + v["level"][-1]) / 2)); ys.append(math.log(v[key]))
            return fit(xs, ys)
        big = lambda v: "LARGE" in (v.get("kinds") or [])
        self.m_hp = ll(lambda v: not big(v), "hp"); self.m_dmg = ll(lambda v: not big(v), "damage")
        self.m_hp_big = ll(big, "hp"); self.m_dmg_big = ll(big, "damage")
        # 위험도 → 몬스터 레벨 (원작 몬스터의 위험도 범위 가운데에서)
        # 필드 보스: 체력 → 공격 · 돈 · 숙련 경험 · 명성 · 다시 나타나는 시간
        hp = [v["hp"] for v in self.bosses.values()]
        self.b_dmg = fit(hp, [v["damage"] for v in self.bosses.values()])
        self.b_money = fit([v["hp"] * v["damage"] for v in self.bosses.values()], [v["reward"]["money"] for v in self.bosses.values()])
        self.b_xp = fit([v["hp"] * v["damage"] for v in self.bosses.values()], [sum(v["reward"].get("xp", {}).values()) for v in self.bosses.values()])
        self.b_fame = fit([v["hp"] * v["damage"] for v in self.bosses.values()], [v["reward"].get("fame", 0) for v in self.bosses.values()])
        self.b_respawn = fit([v["hp"] * v["damage"] for v in self.bosses.values()], [v["respawn_minutes"] for v in self.bosses.values()])
        # 드롭 무기 시세: log(가격) ~ 위력
        D = [(weapon_power(v.get("stats", {})), self.prices[k]) for k, v in canon.items()
             if v["category"] == "WEAPON" and k in self.prices and req_level(v) > 0]
        self.p_a, self.p_b = fit([x for x, _ in D], [math.log(y) for _, y in D])
        DA = [(armor_power(v.get("stats", {})) / SLOT[self.slot(v)], self.prices[k]) for k, v in canon.items()
              if v["category"] == "ARMOR" and k in self.prices and req_level(v) > 0 and self.slot(v)]
        self.pa_a, self.pa_b = fit([x for x, _ in DA], [math.log(y) for _, y in DA])

    @staticmethod
    def slot(v):
        tags = v.get("tags", []) or []
        for s in ("shield", "helmet", "robe", "chest"):
            if s in tags: return s
        m = v["material"]
        if m.endswith("_CHESTPLATE"): return "chest"
        if m.endswith("_LEGGINGS"): return "legs"
        if m.endswith("_BOOTS"): return "boots"
        if m.endswith("_HELMET"): return "helmet"
        return None

    # ------------------------------------------------ 목표값
    def weapon_target(self, L, tier=1.0):
        """착용 레벨 L 무기의 위력 (tier: 제작품 0.9 · 보스 드롭 1.05 · 전설 1.15)"""
        return (self.w_a + self.w_b * L) * tier

    def armor_target(self, L, slot, tier=1.0):
        return (self.a_a + self.a_b * L) * SLOT[slot] * tier

    def monster(self, level, large=False):
        a, b = self.m_hp_big if large else self.m_hp
        c, d = self.m_dmg_big if large else self.m_dmg
        return math.exp(a + b * math.log(level)), math.exp(c + d * math.log(level))

    def boss(self, hp):
        dmg = self.b_dmg[0] + self.b_dmg[1] * hp
        P = hp * dmg
        lin = lambda f: f[0] + f[1] * P
        return dmg, lin(self.b_money), lin(self.b_xp), lin(self.b_fame), lin(self.b_respawn)

    def drop_price(self, power):
        return math.exp(self.p_a + self.p_b * power)

    def armor_drop_price(self, power_chest):
        return math.exp(self.pa_a + self.pa_b * power_chest)


# 위험도 → 들판 몬스터 레벨 범위 (원작 monsters.yml 의 위험도별 레벨에서 정함: 0~1 초보 · 6 대륙 끝)
DANGER_LEVEL = {0: (1, 6), 1: (4, 16), 2: (12, 40), 3: (30, 120), 4: (90, 240), 5: (200, 420), 6: (320, 560)}

# 채집물 시세: 원작 기본 재료 (구리 6 · 철 10 · 은 28 · 원석 120 · 서리 수정 180) 에 맞춘 곡선 — 채집 숙련 L
def gather_price(L, rarity=1.0):
    return max(2, round(5 * 1.2 ** L * rarity))
