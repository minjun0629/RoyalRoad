# 오리지널 몬스터 · 필드 보스 · 스킬 · 직업
import yaml, sys, math
sys.path.insert(0, __file__.rsplit("/", 1)[0])
from balance import Canon, DANGER_LEVEL
REPO = sys.argv[2] if len(sys.argv) > 2 else "."

class NoAlias(yaml.SafeDumper):
    def ignore_aliases(self, data):
        return True


def dump(data, f, **kw):
    yaml.dump(data, f, Dumper=NoAlias, **kw)

OUT = sys.argv[1]
mon = {}
def m(id, name, ent, hp, dmg, lv, tags, danger, drops, regions=None, hostile=True, weight=3, pack=None, kinds=None, baby=False, desc=""):
    e = {"name": name, "entity": ent}
    if baby: e["baby"] = True
    e.update({"hp": hp, "damage": dmg, "level": lv})
    if regions: e["regions"] = regions
    e["tags"] = tags; e["danger"] = danger
    if not hostile: e["hostile"] = False
    e["weight"] = weight
    if pack: e["pack"] = pack
    if kinds: e["kinds"] = kinds
    e["drops"] = drops; e["source"] = "ORIGINAL"
    if desc: e["desc"] = desc
    assert id not in mon; mon[id] = e
# 짐승
m("moss_boar", "이끼 멧돼지", "RAVAGER", 34, 4, [8, 15], ["forest", "swamp", "valley"], [1, 3], ["raw_meat:1.0:2", "hide:0.6", "bog_moss:0.4"], ["hollowroot_marsh", "verdant_hollow"], hostile=False, weight=3, desc="등에 이끼가 자라는 멧돼지. 건드리면 들이받는다")
m("thorn_hare", "가시토끼", "RABBIT", 8, 2, [2, 6], ["highland", "frontier", "plains"], [0, 2], ["raw_meat:0.8", "thorn_berry:0.4"], ["thornwall_heath"], hostile=False, weight=5, pack=[1, 3], desc="털이 가시처럼 곤두선 토끼")
m("frostfang_wolf", "서리송곳니 늑대", "WOLF", 30, 6, [18, 26], ["frozen", "mountain"], [3, 5], ["wolf_pelt:1.0", "frost_essence:0.08"], ["frostfang_pass"], weight=4, pack=[2, 4], desc="입김에 서리가 서리는 늑대 무리")
m("cave_bear", "동굴곰", "POLAR_BEAR", 60, 8, [20, 30], ["mountain", "underground", "forest"], [3, 5], ["bear_pelt:1.0", "raw_meat:1.0:2"], weight=2, desc="겨울잠을 깨우면 몹시 사납다")
m("giant_bee", "왕벌", "VEX", 12, 3, [6, 12], ["valley", "farmland", "forest"], [1, 3], ["honey_comb:0.6", "venom_sac:0.1"], ["verdant_hollow"], weight=3, pack=[2, 4], desc="손바닥만 한 벌. 벌집을 건드리면 떼로 몰려온다")
m("salt_crab", "소금 집게", "SPIDER", 22, 4, [12, 20], ["salt", "coast", "desert"], [2, 4], ["lake_crab:0.8", "sea_salt:0.6"], ["saltglass_flats"], weight=3, desc="소금 결정이 등딱지에 붙은 커다란 게")
m("lake_snapper", "호수 자라", "DROWNED", 28, 5, [10, 18], ["lake", "river"], [1, 3], ["lake_crab:0.5", "pearl:0.05"], ["drowned_bell_lake"], weight=2, desc="물가에서 발목을 무는 자라")
m("dune_scorpion", "사구 전갈", "CAVE_SPIDER", 20, 5, [16, 24], ["desert", "salt", "badlands"], [3, 5], ["venom_sac:0.5", "monster_bone:0.4"], weight=3, pack=[1, 2], desc="모래 속에 숨었다가 꼬리로 찌른다")
m("storm_hawk", "폭풍매", "PHANTOM", 18, 5, [22, 32], ["sky", "mountain", "canyon"], [4, 6], ["griffin_feather:0.15", "storm_essence:0.06"], ["skyreach_spires", "glass_bloom_canyon"], weight=3, pack=[1, 3], desc="천둥이 칠 때만 사냥하는 매")
m("griffin_chick", "어린 그리폰", "PHANTOM", 40, 7, [28, 36], ["sky", "mountain"], [5, 6], ["griffin_feather:0.6", "raw_meat:0.5"], ["skyreach_spires"], weight=2, desc="날개가 덜 자란 그리폰. 어미가 근처에 있다")
m("ash_jackal", "잿불 승냥이", "WOLF", 26, 6, [24, 32], ["badlands", "volcano"], [4, 6], ["wolf_pelt:0.8", "ember_stone:0.08"], ["cinder_ash_wastes"], weight=4, pack=[2, 3], desc="잿더미 속에서 사는 회색 승냥이")
m("jungle_panther", "밀림 표범", "FOX", 36, 8, [30, 40], ["swamp", "frontier"], [5, 6], ["hide:1.0:2", "raw_meat:0.6"], ["bloodthorn_jungle"], weight=3, desc="붉은 덩굴 사이를 소리 없이 다니는 표범")
# 벌레 · 식물
m("bog_leech", "늪 거머리", "CAVE_SPIDER", 10, 3, [6, 12], ["swamp"], [1, 3], ["slime_gel:0.5", "troll_blood:0.02"], ["hollowroot_marsh"], weight=4, pack=[2, 4], desc="물속에서 다리에 달라붙는 거머리")
m("silk_spider", "명주거미", "SPIDER", 24, 4, [12, 20], ["forest", "ruins", "underground"], [2, 4], ["spider_silk:0.8:2", "venom_sac:0.2"], weight=3, desc="은빛 실을 뽑는 큰 거미. 거미줄을 걷으면 명주실이 된다")
m("brood_mother", "알 품은 독거미", "SPIDER", 50, 7, [26, 34], ["swamp", "frontier", "underground"], [4, 6], ["venom_sac:1.0", "spider_silk:1.0:3"], ["bloodthorn_jungle"], weight=1, desc="등에 새끼 수백 마리를 업은 거미")
m("glow_slug", "빛민달팽이", "RABBIT", 14, 2, [4, 10], ["underground", "swamp", "deep"], [1, 3], ["slime_gel:1.0", "glowcap:0.3"], weight=4, hostile=False, desc="몸에서 푸른 빛이 나는 커다란 민달팽이")
m("mandrake", "울음뿌리", "ZOMBIE", 16, 3, [10, 16], ["forest", "swamp", "valley"], [2, 3], ["moonleaf:0.6", "spirit_sap:0.05"], baby=True, weight=3, desc="뽑으면 비명을 지르는 뿌리 괴물")
m("strangler_vine", "목조르기 덩굴", "SPIDER", 30, 6, [28, 36], ["swamp", "frontier"], [5, 6], ["bloodthorn_vine:1.0:2", "spirit_sap:0.08"], ["bloodthorn_jungle"], weight=3, desc="지나가는 짐승의 목을 감는 붉은 덩굴")
m("treant_sapling", "어린 나무정령", "IRON_GOLEM", 70, 7, [22, 30], ["forest", "mist"], [3, 5], ["birch_timber:1.0:3", "spirit_sap:0.2"], ["whispering_birchwood"], weight=2, hostile=False, desc="숲을 해치는 자에게만 손을 든다")
# 정령 · 원소
m("ember_sprite", "불씨 정령", "VEX", 22, 6, [18, 26], ["volcano", "valley"], [3, 5], ["ember_stone:0.5", "fire_essence:0.1"], ["ember_vale"], weight=4, pack=[1, 3], desc="땅불에서 피어오른 작은 불꽃 정령")
m("frost_wisp", "서리 도깨비불", "VEX", 16, 5, [20, 28], ["frozen", "mountain"], [4, 5], ["frost_crystal:0.4", "frost_essence:0.1"], ["frostfang_pass"], weight=3, pack=[2, 3], desc="눈보라 속에서 길을 잃게 하는 푸른 불빛")
m("storm_elemental", "폭풍 정령", "VEX", 34, 8, [26, 34], ["canyon", "badlands", "sky"], [4, 6], ["storm_glass:0.4", "storm_essence:0.15"], ["glass_bloom_canyon"], weight=2, pack=[1, 2], desc="번개가 사람 모양으로 뭉친 정령")
m("salt_wraith", "소금 망령", "STRAY", 30, 6, [22, 30], ["salt", "desert"], [4, 5], ["sea_salt:1.0:2", "ghost_essence:0.06"], ["saltglass_flats"], kinds=["UNDEAD"], weight=3, desc="사막에서 목말라 죽은 대상인의 혼")
m("mist_phantom", "안개 환영", "VEX", 24, 6, [20, 30], ["mist", "coast", "sea"], [3, 5], ["ghost_essence:0.1", "pearl:0.05"], ["mistveil_isles", "whispering_birchwood"], kinds=["UNDEAD"], weight=3, desc="안개 속에서 아는 사람의 목소리로 부르는 환영")
m("lava_golem", "용암 거인", "IRON_GOLEM", 120, 12, [40, 50], ["volcano", "canyon"], [6, 6], ["obsidian_shard:1.0:2", "fire_essence:0.3", "ember_stone:0.5"], ["obsidian_teeth"], weight=2, kinds=["LARGE"], desc="흑요석 껍질 속에 용암이 흐르는 거인")
m("moonlit_guardian", "달빛 수호석상", "IRON_GOLEM", 90, 9, [30, 40], ["crater", "ruins"], [4, 6], ["moonstone:0.4", "sky_metal:0.2"], ["shattered_moon_crater"], weight=2, kinds=["LARGE"], desc="떨어진 달조각을 지키는 돌 기사")
# 언데드
m("drowned_bellringer", "물에 잠긴 종지기", "DROWNED", 30, 6, [14, 22], ["lake", "ruins"], [2, 4], ["pearl:0.1", "ghost_essence:0.05", "monster_bone:0.5"], ["drowned_bell_lake"], kinds=["UNDEAD"], weight=3, desc="종탑과 함께 가라앉은 종지기. 밤마다 종을 치러 간다")
m("ash_revenant", "잿불 망령병", "WITHER_SKELETON", 44, 9, [30, 40], ["badlands", "volcano"], [5, 6], ["monster_bone:0.8", "ghost_essence:0.1", "sulfur:0.4"], ["cinder_ash_wastes"], kinds=["UNDEAD"], weight=3, pack=[1, 3], desc="옛 전쟁터에서 아직도 싸우는 불탄 병사")
m("bone_archer", "해골 궁수", "SKELETON", 24, 5, [12, 22], ["ruins", "underground", "badlands"], [2, 4], ["monster_bone:1.0", "bone_dust:0.5"], kinds=["UNDEAD"], weight=4, pack=[1, 3], desc="녹슨 활을 든 해골")
m("ink_wraith", "잉크 망령", "VEX", 22, 6, [18, 26], ["scholar", "ruins", "underground"], [3, 5], ["ink_sac:1.0:2", "fine_ink:0.2", "parchment:0.4"], ["sunken_archive"], kinds=["UNDEAD"], weight=4, desc="쏟아진 잉크가 원한을 품고 일어선 것")
m("living_tome", "살아 있는 책", "VEX", 18, 4, [16, 24], ["scholar", "ruins"], [3, 4], ["parchment:1.0:3", "mana_dust:0.2"], ["sunken_archive"], weight=4, pack=[1, 3], desc="책장을 펄럭이며 날아다니는 마법서")
# 인간형
m("heath_bandit", "황야 도적", "PILLAGER", 30, 5, [12, 22], ["highland", "frontier", "plains"], [2, 4], ["copper_ingot:0.4", "trail_ration:0.4", "region_map:0.05"], ["thornwall_heath"], kinds=["HUMAN"], weight=3, pack=[2, 3], desc="가시담 사이에 숨어 대상을 터는 도적")
m("mist_pirate", "안개 해적", "VINDICATOR", 40, 7, [22, 32], ["coast", "sea", "mist"], [4, 5], ["pearl:0.15", "barley_ale:0.3", "treasure_map:0.02"], ["mistveil_isles"], kinds=["HUMAN"], weight=3, pack=[2, 4], desc="안개 군도를 근거지로 삼은 해적")
m("cultist_acolyte", "공허 신도", "WITCH", 36, 7, [26, 34], ["ruins", "underground", "crater"], [4, 6], ["mana_dust:0.5", "ghost_essence:0.08"], kinds=["HUMAN"], weight=2, desc="달조각에서 공허의 목소리를 들었다는 광신도")
m("nomad_raider", "초원 약탈자", "PILLAGER", 34, 6, [18, 28], ["plains", "highland"], [3, 4], ["star_shard:0.01", "hide:0.6", "trail_ration:0.4"], ["starfall_steppe"], kinds=["HUMAN"], weight=3, pack=[2, 3], desc="말 위에서 활을 쏘는 약탈자")
# 기계 · 기타
m("clockwork_sentry", "태엽 파수병", "IRON_GOLEM", 60, 8, [20, 30], ["ruins", "underground"], [3, 5], ["old_gear:1.0:3", "spring_coil:0.4", "clockwork_core:0.02"], ["rust_gear_ruins"], weight=3, desc="아직도 태엽이 돌며 유적을 지키는 기계 병사")
m("gear_rat", "톱니쥐", "CAVE_SPIDER", 8, 2, [8, 14], ["ruins", "underground"], [1, 3], ["old_gear:0.6"], ["rust_gear_ruins"], weight=5, pack=[2, 5], desc="톱니를 갉아 먹고 사는 쥐")
m("star_beetle", "별딱정벌레", "CAVE_SPIDER", 12, 3, [14, 22], ["crater", "plains"], [2, 4], ["star_shard:0.02", "mana_dust:0.2"], ["shattered_moon_crater", "starfall_steppe"], weight=4, pack=[2, 4], desc="별조각을 먹어 껍질이 반짝이는 딱정벌레")
m("glass_golem", "유리 골렘", "IRON_GOLEM", 70, 9, [28, 36], ["canyon", "badlands"], [5, 6], ["storm_glass:0.6", "glass_sand:1.0:2"], ["glass_bloom_canyon"], weight=2, kinds=["LARGE"], desc="번개 맞은 모래가 엉겨 붙은 투명한 거인")
m("swamp_troll", "늪 트롤", "RAVAGER", 90, 10, [30, 40], ["swamp", "frontier"], [5, 6], ["troll_blood:0.3", "monster_bone:1.0", "bog_moss:0.6"], ["hollowroot_marsh", "bloodthorn_jungle"], weight=2, kinds=["LARGE"], desc="베어도 다시 붙는 늪의 트롤")
# ---- 밸런스: 위험도 → 레벨 범위 (원작 몬스터의 위험도별 레벨) · 레벨 → 체력 · 공격 (원작 몬스터 곡선)
#      역할: 무리 짐승 · 순한 짐승은 약하게, 혼자 다니는 사나운 것은 조금 세게, LARGE 는 원작 큰 몸 곡선
CANON = Canon(REPO)
REGION_DANGER = {k: v["danger"] for k, v in yaml.safe_load(open(REPO + "/src/main/resources/content/regions.yml"))["regions"].items()}
try:
    REGION_DANGER.update({k: v["danger"] for k, v in yaml.safe_load(open(REPO + "/src/main/resources/content/original/regions.yml"))["regions"].items()})
except FileNotFoundError:
    pass
for id, e in mon.items():
    # 사는 지역의 위험도 (여럿이면 가장 낮은 곳) · 없으면 나오는 위험도 범위의 가운데
    homes = [REGION_DANGER[r] for r in e.get("regions", []) if r in REGION_DANGER]
    d = min(homes) if homes else round((e["danger"][0] + e["danger"][-1]) / 2)
    e["danger"] = [min(e["danger"][0], d), max(e["danger"][-1], d)]
    if not e.get("hostile", True) and e["entity"] in ("RABBIT", "FOX", "GOAT"):
        d = min(d, 1)   # 먹잇감 짐승은 어디 살든 약하다 (원작 토끼 · 여우 · 사슴처럼)
    lo, hi = DANGER_LEVEL[d]
    large = "LARGE" in (e.get("kinds") or [])
    if large or (not e.get("pack") and e.get("hostile", True) and d >= 4): a, b = 0.45, 1.0      # 우두머리 · 큰 몸: 위쪽
    elif e.get("pack") or not e.get("hostile", True): a, b = 0.0, 0.6                              # 무리 · 순한 짐승: 아래쪽
    else: a, b = 0.2, 0.85
    l0 = max(1, round(lo + (hi - lo) * a)); l1 = max(l0 + 1, round(lo + (hi - lo) * b))
    mid = (l0 + l1) / 2
    hp, dmg = CANON.monster(mid, large)
    if e.get("pack"): hp *= 0.85; dmg *= 0.9
    if not e.get("hostile", True): dmg *= 0.75
    e["level"] = [l0, l1]
    e["hp"] = max(4, round(hp))
    e["damage"] = max(1, round(dmg))
dump({"monsters": mon}, open(OUT + "/monsters.yml", "w"), allow_unicode=True, sort_keys=False, width=220, default_flow_style=None)

# ======================================================== 필드 보스 (오리지널)
fb = {}
def b(id, name, look, size, ent, region, hp, dmg, mech, drops, money, xp, fame, desc, minion=None, kinds=None, respawn=120):
    e = {"look": look, "size": size, "name": name, "entity": ent, "region": region, "hp": hp, "damage": dmg, "respawn_minutes": respawn, "mechanics": mech}
    if minion: e["minion"] = minion
    if kinds: e["kinds"] = kinds
    e.update({"drops": drops, "reward": {"money": money, "xp": xp, "fame": fame}, "description": desc, "source": "ORIGINAL"})
    fb[id] = e
SW = {"swordsmanship": 0}
b("hollowroot_ancient", "속빈뿌리 고목왕", "GOLEM", 2.6, "IRON_GOLEM", "hollowroot_marsh", 900, 11, ["REGEN", "MINIONS", "ENRAGE"], ["hollow_wood:600:1.0", "spirit_sap:600:0.6", "thornwood_bow:600:0.15", "woodsman_bane:550:0.2"], 3000, {"swordsmanship": 250}, 25, "늪 한가운데 천 년 묵은 고목이 일어선 것. 뿌리로 늪 거머리를 부른다", minion="CAVE_SPIDER")
b("cinder_warlord", "잿불 군주 카르바스", "KNIGHT", 2.4, "WITHER_SKELETON", "cinder_ash_wastes", 1300, 14, ["FEAR", "MINIONS", "ENRAGE", "BREATH"], ["emberforged_chest:700:0.15", "emberclaw_axe:650:0.2", "ember_heart:650:0.15", "fire_essence:600:1.0"], 6000, {"swordsmanship": 400}, 50, "옛 전쟁에서 불타 죽고도 군대를 이끄는 장군. 잿불 망령병을 부른다", minion="WITHER_SKELETON", kinds=["UNDEAD"], respawn=180)
b("frostfang_alpha", "서리송곳니 우두머리", "BEAST", 2.4, "WOLF", "frostfang_pass", 800, 12, ["MINIONS", "ENRAGE"], ["frostbeard_axe:650:0.15", "frostwarden_helmet:650:0.15", "frost_tear:650:0.12", "wolf_pelt:600:1.0"], 3500, {"swordsmanship": 280}, 30, "고개의 늑대 무리를 이끄는 은빛 늑대. 울부짖으면 무리가 모인다", minion="WOLF")
b("ember_salamander", "불씨 골짜기의 도롱뇽 여왕", "SALAMANDER", 2.6, "BLAZE", "ember_vale", 1000, 13, ["BREATH", "REGEN", "ENRAGE"], ["ember_brand:650:0.2", "ember_scepter:650:0.18", "ember_stone:600:1.0", "fire_essence:600:0.6"], 4000, {"spellcraft": 300}, 35, "땅불 속에서 알을 품는 거대 도롱뇽. 불길을 내뿜는다")
b("moonfall_sentinel", "달조각 파수꾼", "GOLEM", 3.0, "IRON_GOLEM", "shattered_moon_crater", 1500, 13, ["VESSEL", "ENRAGE"], ["moonsilver_sword:700:0.15", "moonwell_staff:700:0.1", "moonstone:650:1.0", "harvest_moon_scythe:650:0.12"], 6000, {"swordsmanship": 400}, 55, "달조각과 함께 떨어진 돌 거인. 가슴의 월석이 생명의 그릇이다", kinds=["LARGE"], respawn=180)
b("salt_queen", "소금 여왕 사하르", "CASTER", 2.2, "WITCH", "saltglass_flats", 1100, 12, ["MINIONS", "FEAR", "FLIGHT"], ["archmage_chest:650:0.12", "salt_wraith_cloak_placeholder:0:0"], 4500, {"spellcraft": 350}, 40, "신기루로 대상단을 홀려 소금으로 만든 마녀. 소금 망령을 부린다", minion="STRAY")
b("bell_tide_guardian", "종탑의 물귀신", "HYDRA", 2.6, "DROWNED", "drowned_bell_lake", 1000, 11, ["REGEN", "MINIONS"], ["tidecaller_trident:650:0.18", "pearl_necklace:650:0.15", "pearl:600:1.0:"], 3800, {"spearmanship": 300}, 35, "가라앉은 종을 지키는 물귀신. 종이 울리면 물이 치솟는다", minion="DROWNED", kinds=["UNDEAD"])
b("thornheart_beast", "가시심장 짐승", "BEAST", 2.4, "RAVAGER", "thornwall_heath", 850, 11, ["ENRAGE", "REGEN"], ["thorn_whip:600:0.25", "ranger_chest:600:0.15", "bear_gauntlets:600:0.15"], 3000, {"swordsmanship": 250}, 25, "가시덤불이 심장을 감싼 거대 짐승. 상처가 덤불로 아문다")
b("archive_keeper", "가라앉은 서고의 사서장", "CASTER", 2.0, "EVOKER", "sunken_archive", 950, 12, ["MINIONS", "FLIGHT", "FEAR"], ["archivist_staff:650:0.2", "archmage_helmet:650:0.12", "mana_orb:600:0.2", "treasure_map:600:0.1"], 4000, {"spellcraft": 320}, 40, "서고와 함께 가라앉아 책이 된 사서장. 살아 있는 책들을 부린다", minion="VEX", kinds=["UNDEAD"])
b("starfall_khan", "별비 초원의 칸", "KNIGHT", 2.2, "PILLAGER", "starfall_steppe", 1000, 12, ["MINIONS", "ENRAGE"], ["griffin_longbow:650:0.15", "star_shard:650:0.5", "lucky_feather:600:0.2"], 4200, {"archery": 320}, 40, "떨어진 별을 모아 왕관을 만든 약탈자들의 우두머리", minion="PILLAGER", kinds=["HUMAN"])
b("clockwork_colossus", "태엽 거상", "GOLEM", 3.2, "IRON_GOLEM", "rust_gear_ruins", 1400, 14, ["VESSEL", "ENRAGE", "MINIONS"], ["clockwork_core:700:0.6", "clockwork_hammer:650:0.2", "clockwork_crossbow:650:0.15"], 5500, {"swordsmanship": 380}, 50, "유적 한가운데 잠든 거대 태엽 인형. 등의 태엽을 먼저 부숴야 멈춘다", minion="CAVE_SPIDER", kinds=["LARGE"], respawn=180)
b("mist_admiral", "안개 제독 벨로크", "KNIGHT", 2.2, "VINDICATOR", "mistveil_isles", 1100, 13, ["MINIONS", "FLIGHT"], ["mistveil_cloak:650:0.18", "star_chart:650:0.15", "pearl_dirk:600:0.25"], 4800, {"swordsmanship": 340}, 45, "안개 군도 해적들의 제독. 안개 속으로 사라졌다 나타난다", minion="VINDICATOR", kinds=["HUMAN"])
b("bloodthorn_matriarch", "핏빛가시 어미 거미", "BEAST", 2.8, "SPIDER", "bloodthorn_jungle", 1400, 14, ["MINIONS", "REGEN", "ENRAGE"], ["bloodthorn_sword:700:0.18", "venom_fang:650:0.25", "shadowstalker_chest:650:0.12"], 6000, {"swordsmanship": 420}, 55, "밀림의 모든 거미를 낳았다는 어미 거미. 독안개를 뿜는다", minion="CAVE_SPIDER", respawn=180)
b("skyreach_griffin", "하늘닿는 그리폰 왕", "FLYER", 2.8, "PHANTOM", "skyreach_spires", 1600, 15, ["FLIGHT", "BREATH", "ENRAGE"], ["griffin_lance:700:0.18", "griffin_bracer:700:0.2", "griffin_feather:650:1.0"], 7000, {"spearmanship": 450}, 60, "첨탑 꼭대기에 둥지를 튼 그리폰들의 왕. 돌풍으로 사람을 떨어뜨린다", respawn=200)
b("obsidian_titan", "흑요석 거신", "GOLEM", 3.4, "IRON_GOLEM", "obsidian_teeth", 2000, 17, ["VESSEL", "BREATH", "ENRAGE"], ["obsidian_maul:750:0.2", "dragonscale_chest:750:0.06", "void_scepter:700:0.1", "obsidian_shard:650:1.0"], 9000, {"swordsmanship": 550}, 80, "흑요석 이빨 깊은 곳에서 용암을 마시며 자란 거신", kinds=["LARGE"], respawn=240)
b("whisper_dryad", "속삭이는 숲의 드라이어드", "CASTER", 2.0, "WITCH", "whispering_birchwood", 900, 10, ["REGEN", "FLIGHT", "MINIONS"], ["birch_wand:600:0.3", "spirit_sap:600:1.0", "moonwell_staff:650:0.06"], 3500, {"spellcraft": 280}, 30, "숲의 속삭임을 만드는 나무 요정. 길 잃은 사람을 영영 숲에 붙잡는다", minion="VEX")
b("glassbloom_avatar", "유리꽃 폭풍의 화신", "DEMON", 2.6, "BLAZE", "glass_bloom_canyon", 1300, 15, ["BREATH", "FLIGHT", "ENRAGE"], ["stormcaller:700:0.15", "storm_rod:700:0.15", "storm_orb:650:0.2", "storm_glass:600:1.0"], 6000, {"spellcraft": 420}, 55, "협곡에 떨어진 번개가 사라지지 않고 몸을 얻은 것", respawn=180)
b("verdant_queen_bee", "푸른 우묵땅의 여왕벌", "FLYER", 2.0, "VEX", "verdant_hollow", 600, 8, ["MINIONS", "FLIGHT"], ["honey_comb:550:1.0", "honey_cake:550:0.5", "amber_pendant:550:0.2"], 2000, {"archery": 200}, 20, "양봉꾼들이 두려워하는 사람만 한 여왕벌", minion="VEX", respawn=90)
# 자리만 잡은 드롭 정리
for v in fb.values():
    v["drops"] = [d.rstrip(":") for d in v["drops"] if "placeholder" not in d]

# 필드 보스: 설계 체력(600 ~ 2048) → 공격 · 돈 · 숙련 경험 · 명성 · 다시 나타나는 시간 (원작 보스 log-log 곡선)
B = CANON.bosses
def ll(f):
    xs = [math.log(v["hp"]) for v in B.values()]; ys = [math.log(max(1, f(v))) for v in B.values()]
    n = len(xs); mx = sum(xs) / n; my = sum(ys) / n
    b = sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / sum((x - mx) ** 2 for x in xs)
    return lambda hp: math.exp(my - b * mx + b * math.log(hp))
BF = {"damage": ll(lambda v: v["damage"]), "money": ll(lambda v: v["reward"]["money"]), "xp": ll(lambda v: sum(v["reward"].get("xp", {}).values())),
      "fame": ll(lambda v: v["reward"].get("fame", 0)), "respawn": ll(lambda v: v["respawn_minutes"])}
for id, e in fb.items():
    hp = max(600, min(2048, e["hp"]))
    e["hp"] = hp
    e["damage"] = round(BF["damage"](hp))
    e["respawn_minutes"] = int(round(BF["respawn"](hp) / 10) * 10)
    e["reward"] = {"money": int(round(BF["money"](hp) / 100) * 100), "xp": {k: int(round(BF["xp"](hp) / 10) * 10) for k in e["reward"]["xp"]},
                   "fame": int(round(BF["fame"](hp) / 5) * 5)}
dump({"field_bosses": fb}, open(OUT + "/field_bosses.yml", "w"), allow_unicode=True, sort_keys=False, width=220, default_flow_style=None)
# ======================================================== 스킬 · 콤보
sk = {}
def s(id, name, kind, weapon, disc, lv, res, cost, cd, shape=None, radius=3.0, width=None, dmg=1.0, effect=None, es=0, basic=False):
    e = {"name": name, "kind": kind}
    if weapon: e["weapon"] = weapon
    e.update({"discipline": disc, "min_level": lv, "resource": res, "cost": cost, "cooldown_ms": cd})
    if shape: e["shape"] = shape
    e["radius"] = radius
    if width is not None: e["width"] = width
    e["damage"] = dmg
    if effect: e["effect"] = effect; e["effect_seconds"] = es
    if basic: e["basic"] = True
    e["source"] = "ORIGINAL"
    sk[id] = e
ST, MA = "STAMINA", "MANA"
# 도끼
s("cleave", "쪼개기", "AREA", "axe", "swordsmanship", 1, ST, 18, 3500, "CONE", 3.0, 100, 1.5, basic=True)
s("whirlwind", "회오리 도끼", "AREA", "axe", "swordsmanship", 8, ST, 35, 10000, "CIRCLE", 3.5, None, 1.6, "BLEED", 4)
s("skull_splitter", "두개골 쪼개기", "AREA", "axe", "swordsmanship", 15, ST, 40, 12000, "CONE", 3.0, 50, 2.6, "STUN", 2)
s("berserk_roar", "광전사의 포효", "SELF", None, "swordsmanship", 12, ST, 30, 30000, effect="HASTE", es=8)
s("earthsplitter", "대지 가르기", "AREA", "axe", "swordsmanship", 22, ST, 50, 16000, "LINE", 8.0, 2.0, 2.4, "SLOW", 3)
# 둔기
s("crush", "내려찍기", "AREA", "mace", "swordsmanship", 1, ST, 18, 3500, "CONE", 2.8, 70, 1.6, "SLOW", 2, basic=True)   # 기본기에 기절을 달면 3.5초마다 묶는다 → 둔화
s("shockwave", "충격파", "AREA", "mace", "swordsmanship", 10, ST, 35, 11000, "RING", 5.0, None, 1.5, "SLOW", 3)
s("holy_smite", "신성한 일격", "AREA", "mace", "swordsmanship", 14, ST, 35, 9000, "CIRCLE", 2.5, None, 2.0, "WEAKEN", 5)
# 낫 · 채찍
s("reap", "거두기", "AREA", "scythe", "swordsmanship", 1, ST, 18, 3500, "CONE", 3.5, 150, 1.0, basic=True)
s("soul_harvest", "영혼 수확", "AREA", "scythe", "swordsmanship", 12, ST, 35, 12000, "CIRCLE", 4.0, None, 1.7, "WEAKEN", 6)
s("lash", "채찍질", "AREA", "whip", "swordsmanship", 1, ST, 12, 2500, "LINE", 5.0, 1.0, 1.2, "SLOW", 2, basic=True)
s("entangle", "휘감기", "PROJECTILE", "whip", "swordsmanship", 12, ST, 25, 14000, None, 8, None, 1.0, "FREEZE", 2)   # 발 묶기(궁수)와 같은 값
# 검 · 단검 추가
s("piercing_lunge", "꿰뚫는 돌진", "DASH", "sword", "swordsmanship", 10, ST, 30, 9000, "LINE", 5.0, 1.2, 1.8)
s("blade_dance", "칼날 춤", "AREA", "sword", "swordsmanship", 18, ST, 45, 14000, "CIRCLE", 3.5, None, 2.2, "BLEED", 5)
s("riposte", "받아치기", "SELF", "sword", "swordsmanship", 14, ST, 25, 15000, effect="GUARD", es=4)
s("holy_blade", "성검 내려치기", "AREA", "sword", "swordsmanship", 20, ST, 40, 12000, "LINE", 6.0, 2.0, 2.4, "WEAKEN", 5)
s("venom_strike", "독침 찌르기", "AREA", "dagger", "swordsmanship", 6, ST, 18, 5000, "CONE", 2.5, 50, 1.3, "POISON", 8)
s("fan_of_knives", "칼날 부채", "AREA", "dagger", "swordsmanship", 16, ST, 35, 11000, "CONE", 6.0, 120, 1.4, "BLEED", 4)
# 창
s("sweeping_spear", "창 휘두르기", "AREA", "spear", "spearmanship", 5, ST, 22, 6000, "CONE", 4.5, 140, 1.5)
s("dragoon_leap", "용기병 도약", "DASH", "spear", "spearmanship", 15, ST, 40, 14000, "CIRCLE", 3.0, None, 2.4, "STUN", 1)
s("impale", "꿰어 박기", "AREA", "spear", "spearmanship", 20, ST, 40, 12000, "LINE", 6.5, 1.0, 2.8, "SLOW", 4)
s("phalanx", "밀집 대형", "SELF", "spear", "spearmanship", 12, ST, 30, 25000, effect="GUARD", es=8)
# 활
s("multishot", "다중 사격", "AREA", "bow", "archery", 12, ST, 30, 9000, "CONE", 14, 30, 1.4)
s("fire_arrow", "불화살", "PROJECTILE", "bow", "archery", 10, ST, 25, 7000, None, 26, None, 1.8, "BURN", 4)
s("hunters_mark", "사냥꾼의 표식", "PROJECTILE", "bow", "archery", 18, ST, 25, 15000, None, 30, None, 1.2, "WEAKEN", 8)
s("rain_of_thorns", "가시비", "AREA", "bow", "archery", 22, ST, 45, 16000, "CIRCLE", 5.0, None, 1.6, "POISON", 6)
# 지팡이
s("chain_lightning", "연쇄 번개", "PROJECTILE", "staff", "spellcraft", 12, MA, 28, 7000, None, 22, None, 2.0, "STUN", 1)
s("meteor", "유성 낙하", "AREA", "staff", "spellcraft", 24, MA, 60, 22000, "CIRCLE", 5.0, None, 3.0, "BURN", 5)
s("blizzard", "눈보라", "AREA", "staff", "spellcraft", 20, MA, 50, 18000, "CIRCLE", 6.0, None, 1.6, "FREEZE", 3)
s("healing_light", "치유의 빛", "SELF", "staff", "spellcraft", 8, MA, 30, 15000, effect="GUARD", es=6)
s("holy_nova", "성스러운 파동", "AREA", "staff", "spellcraft", 16, MA, 40, 14000, "CIRCLE", 5.0, None, 1.8, "WEAKEN", 6)
s("void_bolt", "공허탄", "PROJECTILE", "staff", "spellcraft", 18, MA, 30, 6000, None, 22, None, 2.4, "WEAKEN", 4)
s("entangling_roots", "얽히는 뿌리", "AREA", "staff", "spellcraft", 10, MA, 30, 12000, "CIRCLE", 4.0, None, 1.0, "FREEZE", 3)
s("swift_wind", "순풍", "SELF", None, "exploration", 5, ST, 25, 30000, effect="HASTE", es=10)
combos = {
    "axe_rampage": {"sequence": ["HEAVY", "HEAVY", "LIGHT"], "window_ms": 1800, "finisher": "combo_rampage"},
    "spear_thrust_chain": {"sequence": ["LIGHT", "HEAVY", "LIGHT"], "window_ms": 1600, "finisher": "combo_triple_thrust"},
    "quick_triple": {"sequence": ["LIGHT", "LIGHT", "LIGHT", "HEAVY"], "window_ms": 2000, "finisher": "combo_storm_of_blades"},
}
csk = {
    "combo_rampage": {"name": "난도질", "kind": "AREA", "discipline": "swordsmanship", "min_level": 8, "resource": "STAMINA", "cost": 15, "cooldown_ms": 4000, "shape": "CIRCLE", "radius": 3.0, "damage": 1.7, "effect": "BLEED", "effect_seconds": 3, "basic": True, "source": "ORIGINAL"},
    "combo_triple_thrust": {"name": "삼단 찌르기", "kind": "AREA", "discipline": "spearmanship", "min_level": 5, "resource": "STAMINA", "cost": 12, "cooldown_ms": 3000, "shape": "LINE", "radius": 5.0, "width": 1.2, "damage": 2.1, "basic": True, "source": "ORIGINAL"},
    "combo_storm_of_blades": {"name": "칼바람", "kind": "AREA", "discipline": "swordsmanship", "min_level": 12, "resource": "STAMINA", "cost": 20, "cooldown_ms": 5000, "shape": "CONE", "radius": 4.0, "width": 140, "damage": 2.6, "basic": True, "source": "ORIGINAL"},
}
dump({"skills": sk, "combos": combos, "combo_skills": csk}, open(OUT + "/skills.yml", "w"), allow_unicode=True, sort_keys=False, width=220, default_flow_style=None)

# ======================================================== 직업 (오리지널)
jobs = {}
def j(id, name, slot, tier, req, perks, skills=None, parent=None):
    e = {"name": name, "slot": slot, "tier": tier}
    if parent: e["parent"] = parent
    e["source"] = "ORIGINAL"; e["requires"] = req; e["perks"] = perks
    if skills: e["skills"] = skills
    jobs[id] = e
A = lambda *x: {"all": list(x)}
MS = lambda d, l: {"mastery": d, "level": l}
CT = lambda c, n: {"counter": c, "at_least": n}
j("berserker", "광전사", "COMBAT", 1, A(MS("swordsmanship", 8), CT("kill.monster", 80), CT("hit_taken", 500)), {"attack_pct": 0.10, "max_health_pct": 0.03, "defense_pct": -0.03}, ["whirlwind", "berserk_roar"])
j("warlord", "전쟁군주", "COMBAT", 2, A(MS("swordsmanship", 20), CT("kill.monster", 1500)), {"attack_pct": 0.10, "max_health_pct": 0.08, "stamina": 20}, ["skull_splitter", "earthsplitter"], "berserker")
j("paladin", "성기사", "COMBAT", 2, A(MS("swordsmanship", 18), MS("spellcraft", 8), CT("hit_taken", 3000)), {"defense_pct": 0.10, "max_health_pct": 0.08, "mana": 30}, ["holy_smite", "holy_blade"], "knight")
j("blademaster", "검성", "COMBAT", 2, A(MS("swordsmanship", 24), CT("kill.monster", 2500)), {"attack_pct": 0.10, "crit": 0.06, "stamina": 25}, ["blade_dance", "riposte"], "swordsman")
j("duelist", "결투가", "COMBAT", 2, A(MS("swordsmanship", 18), CT("kill.backstab", 300)), {"crit": 0.10, "attack_pct": 0.08}, ["fan_of_knives", "venom_strike"], "assassin")
j("lancer", "창병", "COMBAT", 1, A(MS("spearmanship", 5), CT("kill.monster", 30)), {"attack_pct": 0.04, "defense_pct": 0.04}, ["sweeping_spear", "phalanx"])
j("dragoon", "용기병", "COMBAT", 2, A(MS("spearmanship", 18), MS("riding", 10)), {"attack_pct": 0.10, "defense_pct": 0.04, "stamina": 20}, ["dragoon_leap", "impale"], "lancer")
j("ranger_job", "순찰자", "COMBAT", 2, A(MS("archery", 15), MS("exploration", 10)), {"crit": 0.05, "attack_pct": 0.06, "stamina": 20}, ["multishot", "fire_arrow"], "archer")
j("beast_hunter", "야수 사냥꾼", "COMBAT", 2, A(MS("archery", 20), CT("kill.monster", 2000)), {"attack_pct": 0.10, "crit": 0.05}, ["hunters_mark", "rain_of_thorns"], "tracker")
j("priest", "사제", "COMBAT", 1, A(MS("spellcraft", 6), MS("bandaging", 5)), {"mana": 40, "max_health_pct": 0.05}, ["healing_light", "holy_nova"])
j("elementalist", "정령술사", "COMBAT", 2, A(MS("spellcraft", 20), CT("kill.monster", 1000)), {"mana": 70, "attack_pct": 0.08}, ["chain_lightning", "blizzard", "meteor"], "mage")
j("warlock", "흑마법사", "COMBAT", 2, A(MS("spellcraft", 18), CT("kill.monster", 800)), {"mana": 60, "attack_pct": 0.12, "max_health_pct": -0.05}, ["void_bolt"], "mage")
j("druid", "드루이드", "COMBAT", 1, A(MS("spellcraft", 6), MS("herbalism", 10)), {"mana": 40, "max_health_pct": 0.03, "gather_bonus.herbalism": 1}, ["entangling_roots"])
j("reaper", "수확자", "COMBAT", 1, A(MS("swordsmanship", 10), CT("kill.monster", 200)), {"attack_pct": 0.06, "crit": 0.03}, ["soul_harvest"])
j("whip_dancer", "채찍 무희", "COMBAT", 1, A(MS("swordsmanship", 8), MS("dexterity", 8)), {"crit": 0.06, "attack_pct": 0.03, "stamina": 20}, ["entangle"])
# 생활
j("jeweler", "보석 세공사", "LIFE", 1, A(MS("jewelcraft", 10), CT("craft.jewelcraft", 50)), {"craft_quality.jewelcraft": 50})
j("brewer", "양조가", "LIFE", 1, A(MS("brewing", 10), CT("craft.brewing", 50)), {"craft_quality.brewing": 45, "price_discount": 0.02})
j("cartographer", "지도 제작자", "LIFE", 1, A(MS("cartography", 10), CT("discover.region", 15)), {"craft_quality.cartography": 50, "stamina": 10})
j("engineer", "기계공", "LIFE", 1, A(MS("engineering", 10), CT("craft.engineering", 50)), {"craft_quality.engineering": 50, "craft_quality.smithing": 10})
j("farmer", "농부", "LIFE", 1, A(MS("herbalism", 10), CT("gather.herbalism", 300)), {"gather_bonus.herbalism": 1})
j("lumberjack", "나무꾼", "LIFE", 1, A(MS("logging", 10), CT("gather.logging", 300)), {"gather_bonus.logging": 1})
j("hunter_life", "사냥꾼", "LIFE", 1, A(MS("butchery", 8), CT("kill.monster", 200)), {"gather_bonus.butchery": 1, "crit": 0.02})
j("master_chef", "궁정 요리장", "LIFE", 2, A(MS("cooking", 22), CT("craft.cooking", 600)), {"craft_quality.cooking": 55, "craft_quality.brewing": 15}, parent="cook")
j("grand_alchemist", "대연금술사", "LIFE", 2, A(MS("alchemy", 22), CT("craft.alchemy", 600)), {"craft_quality.alchemy": 70}, parent="alchemist")
j("couturier", "궁정 재단사", "LIFE", 2, A(MS("tailoring", 22), CT("craft.tailoring", 600)), {"craft_quality.tailoring": 50, "craft_quality.leatherwork": 20}, parent="tailor")
j("artificer", "명공", "LIFE", 2, A(MS("engineering", 22), MS("smithing", 15)), {"craft_quality.engineering": 50, "craft_quality.smithing": 20}, parent="engineer")
j("gem_master", "보석 명인", "LIFE", 2, A(MS("jewelcraft", 22), CT("craft.jewelcraft", 500)), {"craft_quality.jewelcraft": 70}, parent="jeweler")
j("trade_prince", "거상", "LIFE", 2, A(MS("trading", 22), CT("trade.completed", 500)), {"price_discount": 0.08, "auction_fee_cut": 0.6}, parent="merchant")
dump({"jobs": jobs}, open(OUT + "/jobs.yml", "w"), allow_unicode=True, sort_keys=False, width=220, default_flow_style=None)
print(len(mon), "monsters", len(fb), "bosses", len(sk) + len(csk), "skills", len(jobs), "jobs")
