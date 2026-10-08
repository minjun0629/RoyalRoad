"""오리지널 아이템의 능력치 · 시세를 원작 곡선에 맞춘다 (orig_items.py 가 마지막에 부른다).

- 무기: 착용 레벨 L 의 원작 위력 곡선 × 등급 (제작품 0.92 · 보스 드롭 1.05). 설계에서 정한 '공격력 : 옵션' 비율은 지키고 크기만 맞춘다.
- 방어구: 원작 몸통 곡선 × 부위 비율 (투구 0.6 · 각반 0.75 · 장화 0.45 · 방패 0.65) × 등급.
- 장신구: 3 + 0.5 × L (원작 장신구 범위 안).
- 세트 보너스: 몸통 목표값의 2벌 25% · 3벌 15% · 4벌 30%.
- 시세: 제작품 = 재료값 × 1.5 + 4 L² + 30 (장비) / 재료값 × 1.3 (그 밖) — 만들어 팔면 조금 남게.
        채집물 = 원작 기본 재료 곡선 (구리 6 · 철 10 · 은 28 · 원석 120 · 서리 수정 180) · 드롭 전용 장비 = 원작 드롭 장비의 위력-시세 곡선.
        물약은 등급마다 바닥값 (원작 t1 40 · t2 160 · t3 520 → t4 1600 · t5 4800) — 등급이 높은 물약이 더 싸지 않게.
"""
import math
from balance import Canon, weapon_power, armor_power, req_level, gather_price, SLOT, ARMOR_W, CAP, CAP_ACC, CAP_SET

POTION_FLOOR = {1: 40, 2: 160, 3: 520, 4: 1600, 5: 4800}


def _scale(opts, budget, value_of, caps=CAP):
    """옵션을 같은 비율로 키우거나 줄여 값어치 합이 budget 이 되게 (음수 옵션 = 단점은 그대로).
    상한(CAP)에 닿은 옵션은 멈추고 나머지가 이어서 자란다 — 모두 상한이면 위력이 조금 모자란 채로 둔다 (넘치는 것보다 낫다)"""
    neg = {k: v for k, v in opts.items() if v <= 0}
    fixed = {}
    free = {k: v for k, v in opts.items() if v > 0}
    while True:
        if not free:
            break
        lo, hi = 0.0, 40.0
        for _ in range(60):
            f = (lo + hi) / 2
            cur = {k: max(1, round(v * f)) for k, v in free.items()}
            cur.update(fixed); cur.update(neg)
            if value_of(cur) > budget: hi = f
            else: lo = f
        over = [k for k, v in free.items() if round(v * lo) > caps.get(k, 999)]
        if not over:
            fixed.update({k: max(1, round(v * lo)) for k, v in free.items()})
            break
        for k in over:
            fixed[k] = caps[k]
            del free[k]
    out = dict(fixed)
    out.update(neg)
    return out


def equip_req(v, L, kind):
    """만드는 숙련이 착용 조건보다 높으면 착용 조건을 그만큼 올린다 — 낮은 조건으로 높은 레벨의 위력을 쓰지 못하게.
    (장신구는 원작처럼 조건 없음)"""
    if kind == "accessory" or L <= req_level(v):
        return False
    r = dict(v.get("requires") or {})
    keys = [k for k in r if k.startswith("mastery.") or k.startswith("stat.")]
    if keys:
        top = max(keys, key=lambda k: r[k])
        r[top] = L
    else:
        tags = v.get("tags", [])
        r["mastery." + ("archery" if "bow" in tags else "spearmanship" if "spear" in tags else "spellcraft" if "staff" in tags or "robe" in tags else "swordsmanship")] = L
    v["requires"] = r
    return True


def finalize(repo, items, prices, recipes, resources, sets, base_price, design):
    c = Canon(repo)
    crafted = {}
    for r in recipes.values():
        crafted[r["output"]] = min(crafted.get(r["output"], 99), r["min_level"])
    set_level = {}
    # ------------------------------------------------ 능력치
    for id, (kind, slot) in design.items():
        v = items[id]
        s = dict(v.get("stats", {}))
        L = max(req_level(v), crafted.get(id, 0)) or 10
        if equip_req(v, L, kind): pass
        tier = 0.92 if id in crafted else 1.05
        if kind == "weapon":
            P = c.weapon_target(L, tier)
            share = s["attack"] / max(1e-9, weapon_power(s))
            atk = max(1, round(P * share))
            opts = {k: x for k, x in s.items() if k != "attack"}
            opts = _scale(opts, P - atk, lambda o: weapon_power({"attack": atk, **o}) - atk)
            for _ in range(5):   # 반올림으로 남거나 모자란 만큼 공격력으로 채운다
                atk = max(1, atk + round(P - weapon_power({"attack": atk, **opts})))
            v["stats"] = {"attack": atk, **opts}
        elif kind == "armor":
            P = c.armor_target(L, slot, tier)
            share = s.get("defense", 0) / max(1e-9, armor_power(s))
            d = max(1, round(P * share))
            opts = {k: x for k, x in s.items() if k != "defense"}
            opts = _scale(opts, P - d, lambda o: armor_power(o))
            d = max(1, round(P - armor_power(opts)))   # 반올림 차이는 방어력으로
            v["stats"] = {"defense": d, **opts}
            if v.get("set"): set_level[v["set"]] = max(set_level.get(v["set"], 0), L)
        elif kind == "accessory":
            P = 3 + 0.5 * L
            v["stats"] = _scale(s, P, lambda o: armor_power(o), CAP_ACC)
    for sid, st in sets.items():
        T = c.armor_target(set_level.get(sid, 10), "chest")
        share = {2: 0.25, 3: 0.15, 4: 0.30}
        st["bonuses"] = {n: _scale(b, T * share.get(n, 0.2), lambda o: armor_power(o), CAP_SET) for n, b in st["bonuses"].items()}
    # ------------------------------------------------ 시세
    allp = dict(c.prices)
    tagged = {}
    for src in (c.items, items):
        for k, v in src.items():
            for t in v.get("tags", []) or []: tagged.setdefault(t, []).append(k)
    yields = {r["yield"]: r["min_level"] for r in resources.values()}

    def price_of(acc):
        if acc.startswith("type:"): return allp.get(acc[5:], 10)
        ps = sorted(allp.get(k, 10) for k in tagged.get(acc[4:], []) if allp.get(k, 0) > 0)
        return ps[0] if ps else 10   # 태그 칸에는 가장 싼 재료를 넣는다

    for k, b in base_price.items():
        allp[k] = b
    for k, L in yields.items():
        if k in items: allp[k] = gather_price(L, 1.0 if k not in base_price else max(0.5, min(3.0, base_price[k] / max(1, gather_price(L)))))
    for _ in range(8):
        for id, v in items.items():
            rs = [r for r in recipes.values() if r["output"] == id]
            equip = v["category"] in ("WEAPON", "ARMOR", "TOOL")
            if rs:
                best = None
                for r in rs:
                    cost = sum(price_of(sl["accepts"]) * sl["count"] for sl in r["slots"].values() if not sl.get("optional")) / r.get("output_count", 1)
                    L = r["min_level"]
                    p = cost * 1.5 + 4 * L * L + 30 if equip else cost * 1.3 + 1
                    best = p if best is None else min(best, p)
                allp[id] = best
            elif equip and id in design:
                kind, slot = design[id]
                if kind == "weapon": allp[id] = c.drop_price(weapon_power(v["stats"]))
                elif kind == "armor": allp[id] = c.armor_drop_price(armor_power(v["stats"]) / SLOT[slot])
                else: allp[id] = c.armor_drop_price(armor_power(v["stats"]) * 1.5)
            for t in range(5, 0, -1):
                if f"potion_t{t}" in (v.get("tags") or []):
                    allp[id] = max(allp.get(id, 0), POTION_FLOOR[t] * (0.6 if any(x in v["tags"] for x in ("cure", "mana_potion", "resist_fire", "resist_frost", "stamina_potion")) else 1.0))
                    break
    for id in items:
        prices[id] = max(1, int(round(allp.get(id, base_price.get(id, 10)))))
