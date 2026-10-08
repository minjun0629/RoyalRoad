# 오리지널 지형 (원작에 없는 곳) — 큰 원작 지역 안의 빈 땅을 찾아 둔다
import yaml, sys, random

class NoAlias(yaml.SafeDumper):
    def ignore_aliases(self, data):
        return True


def dump(data, f, **kw):
    yaml.dump(data, f, Dumper=NoAlias, **kw)


REPO = sys.argv[1]
d = yaml.safe_load(open(REPO + '/src/main/resources/content/regions.yml'))['regions']
R = []
for k, v in d.items():
    if v.get('world', 'world') != 'world': continue
    R.append((k, v['min'][0], v['min'][2], v['max'][0], v['max'][2], v.get('priority', 0)))
def free(x0, z0, x1, z1, parent, pad=300):
    for k, a, b, c, e, p in R:
        if k == parent or k == 'outer_ocean': continue
        if p <= d[parent].get('priority', 0) and k in ('north_reach','central_plains','east_coast_waters'): continue
        if a - pad < x1 and c + pad > x0 and b - pad < z1 and e + pad > z0:
            # 부모보다 우선순위가 낮은 큰 바탕 지역은 무시
            big = (c - a) * (e - b) > 4000 * 4000
            if big and k != parent and not (a <= x0 and c >= x1 and b <= z0 and e >= z1): return False
            if not big: return False
    return True
# id, 이름, 부모, 크기, 위험, 태그, 목적, 자원
SPECS = [
 ("glass_bloom_canyon", "유리꽃 협곡", "lost_road_badlands", 900, 5, ["canyon", "badlands"], "번개 맞은 모래가 유리꽃으로 굳은 협곡. 유리 공예 재료와 번개 정령", ["glass_sand", "storm_glass"]),
 ("hollowroot_marsh", "속빈뿌리 늪", "west_frontier", 1100, 4, ["swamp", "forest"], "뿌리가 텅 빈 거목들이 물에 잠긴 늪. 독초 · 늪 괴물 · 이끼 군락", ["bog_moss", "hollow_wood"]),
 ("ember_vale", "불씨 골짜기", "west_tribes", 800, 4, ["volcano", "valley"], "식지 않는 땅불이 새어 나오는 골짜기. 화염석 · 불도마뱀 무리", ["ember_stone"]),
 ("whispering_birchwood", "속삭이는 자작숲", "north_reach", 1400, 3, ["forest", "mist"], "바람이 불면 나무껍질이 말소리처럼 운다는 흰 숲. 길을 잃기 쉽다", ["birch_timber", "spirit_sap"]),
 ("shattered_moon_crater", "부서진 달 분지", "central_plains", 1000, 4, ["crater", "ruins"], "하늘에서 떨어진 달조각이 파놓은 둥근 분지. 월석 · 별빛 유물 · 낙하물 수호자", ["moonstone", "sky_metal"]),
 ("saltglass_flats", "소금유리 평원", "sand_sea", 1600, 4, ["salt", "desert"], "마른 바다가 남긴 하얀 소금 평원. 신기루 · 소금 정령 · 대상단의 무덤", ["sea_salt", "glass_sand"]),
 ("thornwall_heath", "가시담 황야", "kallamore_lands", 900, 3, ["highland", "frontier"], "가시덤불이 성벽처럼 자라 길을 막는 황야. 가시 짐승 · 떠돌이 도적", ["thorn_berry", "hide"]),
 ("drowned_bell_lake", "가라앉은 종 호수", "britten_alliance", 900, 3, ["lake", "ruins"], "물속 종탑의 종이 안개 낀 밤마다 울리는 호수. 물귀신 · 진주조개", ["pearl", "salmon"]),
 ("cinder_ash_wastes", "잿가루 황무지", "plains_of_despair", 800, 6, ["badlands", "volcano"], "옛 전쟁의 불길이 땅을 태운 잿빛 황무지. 잿불 망령 · 화약 원석", ["sulfur", "ember_stone"]),
 ("verdant_hollow", "푸른 우묵땅", "aidern_lands", 900, 2, ["forest", "valley", "farmland"], "샘물이 모여 사계절 푸른 우묵한 골짜기. 약초꾼과 양봉꾼이 산다", ["honey_comb", "moonleaf"]),
 ("frostfang_pass", "서리송곳니 고개", "brent_highlands", 900, 5, ["mountain", "frozen"], "고드름이 송곳니처럼 늘어진 높은 고개. 눈사태 · 서리 늑대 무리", ["frost_crystal", "highland_timber"]),
 ("sunken_archive", "가라앉은 서고", "southern_duchies", 700, 4, ["ruins", "scholar", "underground"], "학자 도시의 옛 서고가 땅 밑으로 꺼진 곳. 살아 있는 책 · 잉크 괴물", ["ink_sac", "parchment"]),
 ("starfall_steppe", "별비 초원", "gradian_lands", 1200, 3, ["plains", "highland"], "유성이 자주 떨어지는 초원. 떨어진 별조각을 찾는 유목민", ["star_shard", "hide"]),
 ("rust_gear_ruins", "녹슨 톱니 유적", "haven_lands", 700, 4, ["ruins", "underground"], "이름 모를 장인들이 남긴 거대한 톱니 장치의 폐허. 태엽 인형이 아직 돌아다닌다", ["old_gear", "copper_ore"]),
 ("mistveil_isles", "안개 장막 군도", "east_coast_waters", 1100, 4, ["sea", "mist", "coast"], "늘 안개에 싸인 작은 섬들. 해적 은신처 · 바다 괴물", ["pearl", "deep_eel"]),
 ("bloodthorn_jungle", "핏빛가시 밀림", "south_continent", 1500, 5, ["forest", "swamp", "frontier"], "남쪽 대륙의 붉은 덩굴 밀림. 식인 식물 · 거대 벌레", ["bloodthorn_vine", "jungle_timber"]),
 ("skyreach_spires", "하늘닿는 첨탑", "east_peaks", 900, 6, ["mountain", "sky"], "구름 위로 솟은 바위 첨탑들. 바람 정령 · 그리폰 둥지", ["sky_metal", "griffin_feather"]),
 ("obsidian_teeth", "흑요석 이빨", "west_new_continent", 1200, 6, ["volcano", "canyon"], "서쪽 신대륙의 검은 유리 바위 지대. 용암 거인 · 흑요석", ["obsidian_shard", "ember_stone"]),
]
random.seed(7)
out = {}
for rid, name, parent, size, danger, tags, purpose, res in SPECS:
    p = d[parent]
    x0, z0, x1, z1 = p['min'][0], p['min'][2], p['max'][0], p['max'][2]
    placed = None
    for _ in range(4000):
        cx = random.randint(x0 + size // 2 + 200, x1 - size // 2 - 200)
        cz = random.randint(z0 + size // 2 + 200, z1 - size // 2 - 200)
        a, b, c, e = cx - size // 2, cz - size // 2, cx + size // 2, cz + size // 2
        if free(a, b, c, e, parent):
            placed = (a, b, c, e); break
    if not placed: print("NO SPOT", rid, file=sys.stderr); continue
    a, b, c, e = placed
    R.append((rid, a, b, c, e, 20))
    out[rid] = {"name": name, "source": "ORIGINAL", "danger": danger, "min": [a, -64, b], "max": [c, 320, e], "priority": 20, "parent": parent,
                "tags": tags, "purpose": purpose, "changed": "원작에 없는 이 게임의 땅", "resources": res}
dump({"regions": out}, open(sys.argv[2], 'w'), allow_unicode=True, sort_keys=False, width=200, default_flow_style=None)
print(len(out), "regions")
