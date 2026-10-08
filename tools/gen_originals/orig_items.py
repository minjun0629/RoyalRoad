# 오리지널 아이템 · 시세 · 제작법 · 자원 · 분야 (원작에 없는 이 게임의 것)
import yaml, sys

class NoAlias(yaml.SafeDumper):
    def ignore_aliases(self, data):
        return True


def dump(data, f, **kw):
    yaml.dump(data, f, Dumper=NoAlias, **kw)

OUT = sys.argv[1]
REPO = sys.argv[2] if len(sys.argv) > 2 else "."
sys.path.insert(0, __file__.rsplit("/", 1)[0])
items, prices, recipes, resources = {}, {}, {}, {}
BASE_PRICE, DESIGN = {}, {}   # 손으로 정한 기준 시세 · 밸런스 단계에 넘길 설계 (공격력 · 옵션의 비율)

def item(id, name, cat, mat, tags=None, weight=1, dur=0, stats=None, req=None, lore=None, set_=None, price=None):
    e = {"name": name, "category": cat, "material": mat}
    if dur: e["durability"] = dur
    e["weight"] = weight
    if tags: e["tags"] = tags
    if stats: e["stats"] = stats
    if req: e["requires"] = req
    if set_: e["set"] = set_
    e["source"] = "ORIGINAL"
    if lore: e["lore"] = lore
    assert id not in items, id
    items[id] = e
    if price is not None: BASE_PRICE[id] = price

def recipe(id, name, disc, lv, out, slots, tool=None, xp=None, count=1, discovery=None, ticks=None):
    e = {"name": name, "discipline": disc, "min_level": lv, "action_level": lv + 2, "output": out}
    if count > 1: e["output_count"] = count
    if tool: e["tool"] = tool
    e["xp"] = xp if xp is not None else 6 + lv * 3
    if ticks: e["time_ticks"] = ticks
    if discovery: e["discovery"] = discovery
    sl = {}
    for k, v in slots.items():
        acc, cnt = v[0], v[1]
        s = {"accepts": acc, "count": cnt}
        if len(v) > 2 and v[2]: s["weight"] = v[2]
        if len(v) > 3 and v[3]: s["optional"] = True; s["bonus"] = v[3]
        sl[k] = s
    e["slots"] = sl
    e["source"] = "ORIGINAL"
    assert id not in recipes, id
    recipes[id] = e

def res(id, name, disc, lv, blocks, yld, rich, tool=None, amount=1, bonus=1, respawn=120, xp=None):
    e = {"name": name, "discipline": disc, "min_level": lv, "action_level": lv + 2, "blocks": blocks, "yield": yld, "amount": amount,
         "bonus": bonus, "rich_in": rich, "respawn_seconds": respawn}
    if tool: e["tool"] = tool
    e["xp"] = xp if xp is not None else 3 + lv * 2
    e["source"] = "ORIGINAL"
    resources[id] = e

T = lambda x: "tag:" + x
I = lambda x: "type:" + x

# ======================================================== 재료
M = "MATERIAL"
for id, name, mat, tags, p in [
    ("mithril_ore", "미스릴 원석", "RAW_IRON", ["ore", "mithril"], 40),
    ("copper_ingot", "구리 주괴", "COPPER_INGOT", ["metal", "mineral"], 14),
    ("bronze_ingot", "청동 주괴", "COPPER_INGOT", ["metal", "mineral"], 26), ("gold_ore", "금 원석", "RAW_GOLD", ["ore"], 30), ("gold_ingot", "금 주괴", "GOLD_INGOT", ["metal", "mineral", "precious"], 80),
    ("coal_lump", "석탄", "COAL", ["fuel", "mineral"], 4), ("sulfur", "유황", "GLOWSTONE_DUST", ["mineral", "powder"], 12), ("saltpeter", "초석", "SUGAR", ["mineral", "powder"], 10),
    ("ember_stone", "불씨돌", "MAGMA_CREAM", ["gem", "fire_stone"], 45), ("obsidian_shard", "흑요석 조각", "OBSIDIAN", ["stone", "mineral", "obsidian"], 35),
    ("storm_glass", "폭풍 유리", "GLASS", ["glass", "gem", "storm"], 55), ("star_shard", "별조각", "NETHER_STAR", ["gem", "sky", "star"], 140),
    ("moonstone", "월석", "QUARTZ", ["gem", "moon"], 70), ("ruby", "루비", "REDSTONE", ["gem", "cut_gem"], 90), ("sapphire", "사파이어", "LAPIS_LAZULI", ["gem", "cut_gem"], 90),
    ("emerald_gem", "에메랄드", "EMERALD", ["gem", "cut_gem"], 95), ("amber", "호박", "HONEYCOMB", ["gem", "amber"], 40), ("pearl", "진주", "ENDER_PEARL", ["gem", "pearl"], 60),
    ("old_gear", "녹슨 톱니", "IRON_NUGGET", ["part", "metal_scrap"], 18), ("spring_coil", "태엽 용수철", "IRON_NUGGET", ["part"], 30), ("clockwork_core", "태엽 심장", "CLOCK", ["part", "core"], 160),
    ("glass_pane", "유리판", "GLASS_PANE", ["glass"], 8), ("birch_timber", "자작나무 목재", "BIRCH_LOG", ["wood"], 9), ("jungle_timber", "밀림 목재", "JUNGLE_LOG", ["wood", "exotic_wood"], 14),
    ("dark_timber", "검은참나무 목재", "DARK_OAK_LOG", ["wood", "dark_wood"], 16), ("hollow_wood", "속빈뿌리 목재", "MANGROVE_LOG", ["wood", "swamp"], 15),
    ("spirit_sap", "정령 수액", "HONEY_BOTTLE", ["herb", "essence", "sap"], 50), ("bog_moss", "늪 이끼", "MOSS_BLOCK", ["herb", "swamp"], 8),
    ("bloodthorn_vine", "핏빛 가시덩굴", "VINE", ["herb", "fiber", "poison_herb"], 22), ("thorn_berry", "가시 열매", "SWEET_BERRIES", ["fruit", "herb"], 6),
    ("honey_comb", "벌집", "HONEYCOMB", ["sweet", "wax"], 12), ("beeswax", "밀랍", "HONEYCOMB_BLOCK", ["wax"], 14),
    ("spider_silk", "거미 명주실", "COBWEB", ["fiber", "silk"], 20), ("silk_cloth", "명주 비단", "WHITE_WOOL", ["cloth", "silk"], 55), ("wool_cloth", "모직 천", "LIGHT_GRAY_WOOL", ["cloth"], 16),
    ("wolf_pelt", "늑대 모피", "RABBIT_HIDE", ["hide", "fur"], 18), ("bear_pelt", "곰 모피", "RABBIT_HIDE", ["hide", "fur"], 30), ("thick_leather", "두꺼운 가죽", "LEATHER", ["leather", "thick"], 40),
    ("monster_bone", "몬스터 뼈", "BONE", ["bone"], 8), ("bone_dust", "뼛가루", "BONE_MEAL", ["powder", "bone"], 6), ("slime_gel", "점액 젤", "SLIME_BALL", ["gel"], 9),
    ("venom_sac", "독주머니", "FERMENTED_SPIDER_EYE", ["poison", "organ"], 25), ("troll_blood", "트롤의 피", "REDSTONE", ["blood", "regen_mat"], 70),
    ("ghost_essence", "망령의 정수", "GHAST_TEAR", ["essence", "undead"], 85), ("fire_essence", "화염 정수", "BLAZE_POWDER", ["essence", "fire_mat"], 75),
    ("frost_essence", "서리 정수", "SNOWBALL", ["essence", "frost"], 75), ("storm_essence", "폭풍 정수", "GLOWSTONE_DUST", ["essence", "storm"], 75),
    ("mana_dust", "마나 가루", "GLOWSTONE_DUST", ["powder", "mana"], 30), ("parchment", "양피지", "PAPER", ["paper"], 6), ("ink_sac", "먹물 주머니", "INK_SAC", ["ink", "dye"], 5),
    ("fine_ink", "고운 잉크", "BLACK_DYE", ["ink"], 14), ("gold_leaf", "금박", "GOLD_NUGGET", ["precious", "leaf_gold"], 35), ("candle_wax", "초", "CANDLE", ["wax", "light"], 8),
    ("barley", "보리", "WHEAT", ["grain", "barley"], 3), ("hops", "홉", "FERN", ["herb", "hops"], 5), ("grapes", "포도", "SWEET_BERRIES", ["fruit", "grape"], 5), ("apple", "사과", "APPLE", ["fruit"], 4),
    ("carrot", "당근", "CARROT", ["vegetable"], 3), ("potato", "감자", "POTATO", ["vegetable"], 3), ("beet", "비트", "BEETROOT", ["vegetable"], 3), ("onion", "양파", "BEETROOT", ["vegetable", "spice"], 4),
    ("mushroom", "버섯", "BROWN_MUSHROOM", ["herb", "mushroom"], 4), ("glowcap", "빛버섯", "RED_MUSHROOM", ["herb", "mushroom", "glow"], 18), ("pepper", "불고추", "NETHER_WART", ["spice", "seasoning"], 10),
    ("milk_jug", "우유 단지", "MILK_BUCKET", ["dairy"], 6), ("cheese", "치즈", "YELLOW_WOOL", ["dairy", "food_mat"], 18), ("egg", "달걀", "EGG", ["egg"], 2), ("flour", "밀가루", "SUGAR", ["flour"], 4),
    ("sugar_cane", "사탕수수", "SUGAR_CANE", ["sweet"], 3), ("cocoa", "카카오", "COCOA_BEANS", ["sweet", "exotic"], 12), ("raw_fowl", "새고기", "CHICKEN", ["meat"], 6), ("trout", "송어", "COD", ["fish"], 8),
    ("pufferfish", "복어", "PUFFERFISH", ["fish", "poison"], 15), ("lake_crab", "호수 게", "COD", ["fish", "shellfish"], 12),
]:
    item(id, name, M, mat, tags, weight=1 if p < 50 else 1, price=p)

# ======================================================== 음식 · 음료 · 소모품
F, C = "FOOD", "CONSUMABLE"
for id, name, mat, tags, p in [
    ("apple_pie", "사과 파이", "PUMPKIN_PIE", ["food"], 30), ("mushroom_soup", "버섯 수프", "MUSHROOM_STEW", ["food"], 18), ("roast_fowl", "통새 구이", "COOKED_CHICKEN", ["food"], 22),
    ("herb_bread", "허브 빵", "BREAD", ["food", "grain"], 14), ("cheese_bread", "치즈 빵", "BREAD", ["food"], 26), ("veg_stew", "채소 스튜", "BEETROOT_SOUP", ["food"], 20),
    ("fish_pie", "생선 파이", "PUMPKIN_PIE", ["food"], 32), ("spicy_stew", "불고추 스튜", "RABBIT_STEW", ["food", "warm"], 34), ("honey_cake", "꿀 케이크", "PUMPKIN_PIE", ["food", "sweet_food"], 45),
    ("trail_ration", "행군 식량", "DRIED_KELP", ["food", "ration"], 12), ("crab_bisque", "게살 수프", "BEETROOT_SOUP", ["food"], 38), ("grilled_trout", "송어 구이", "COOKED_COD", ["food"], 16),
    ("royal_feast", "왕의 만찬", "GOLDEN_CARROT", ["food", "feast"], 180), ("baked_potato", "구운 감자", "BAKED_POTATO", ["food"], 8), ("cookie", "쿠키", "COOKIE", ["food", "sweet_food"], 6),
    ("chocolate", "초콜릿", "COOKIE", ["food", "sweet_food"], 28), ("pufferfish_sashimi", "복어회", "COOKED_SALMON", ["food", "risky"], 60),
    ("barley_ale", "보리 에일", "POTION", ["drink", "ale"], 16), ("dark_stout", "흑맥주", "POTION", ["drink", "ale", "stout"], 28), ("grape_wine", "포도주", "POTION", ["drink", "wine"], 40),
    ("honey_mead", "꿀술", "POTION", ["drink", "mead"], 36), ("fire_brandy", "불꽃 브랜디", "POTION", ["drink", "strong", "fireproof"], 70), ("dwarf_spirit", "드워프 화주", "POTION", ["drink", "strong", "stoneskin"], 90),
    ("herbal_tea", "약초차", "POTION", ["drink", "tea"], 10), ("cider", "사과주", "POTION", ["drink", "cider"], 18),
]:
    item(id, name, F, mat, tags, price=p)
for id, name, mat, tags, p in [
    ("antidote", "해독제", "POTION", ["potion", "potion_t1", "cure"], 30), ("stamina_tonic", "기력 강장제", "POTION", ["potion", "potion_t2", "stamina_potion"], 60),
    ("supreme_draught", "최상급 회복 물약", "POTION", ["potion", "potion_t4"], 260), ("elixir_of_life", "생명의 영약", "POTION", ["potion", "potion_t5"], 700),
    ("fire_resist_potion", "화염 저항 물약", "POTION", ["potion", "potion_t2", "resist_fire"], 90), ("frost_resist_potion", "냉기 저항 물약", "POTION", ["potion", "potion_t2", "resist_frost"], 90),
    ("mana_potion", "마나 물약", "POTION", ["potion", "potion_t2", "mana_potion"], 80), ("greater_mana_potion", "상급 마나 물약", "POTION", ["potion", "potion_t3", "mana_potion"], 180),
    ("fire_bomb", "화염 폭탄", "FIRE_CHARGE", ["bomb", "throwable"], 55), ("smoke_bomb", "연막탄", "GUNPOWDER", ["bomb", "throwable"], 30), ("frost_bomb", "서리 폭탄", "SNOWBALL", ["bomb", "throwable"], 65),
    ("bear_trap", "곰 덫", "TRIPWIRE_HOOK", ["trap"], 40), ("lantern_oil", "등잔 기름", "HONEY_BOTTLE", ["fuel"], 8), ("whetstone_fine", "고운 숫돌", "FLINT", ["whetstone"], 25),
    ("repair_kit", "수리 도구", "IRON_NUGGET", ["repair_kit"], 60), ("scroll_of_return", "귀환 두루마리", "PAPER", ["scroll"], 120), ("treasure_map", "보물 지도", "MAP", ["map_scroll", "treasure"], 200),
    ("region_map", "지역 지도", "MAP", ["map_scroll"], 45), ("star_chart", "별자리 해도", "PAPER", ["map_scroll", "sea_chart"], 150), ("firework_rocket", "축포", "FIREWORK_ROCKET", ["firework"], 20),
]:
    item(id, name, C, mat, tags, price=p)

# ======================================================== 도구 (고유)
for id, name, mat, tags, dur, stats, p in [
    ("jeweler_loupe", "보석 세공 돋보기", "SPYGLASS", ["tool_jewel"], 300, None, 120), ("quill_set", "깃펜 세트", "FEATHER", ["tool_quill"], 300, None, 60),
    ("engineer_wrench", "기계공 렌치", "IRON_HOE", ["tool_wrench"], 400, None, 90), ("brew_paddle", "양조 주걱", "STICK", ["tool_brew"], 300, None, 40),
    ("mithril_hammer", "미스릴 대장 망치", "IRON_AXE", ["tool_hammer"], 900, {"craft": 4}, 600), ("master_carving_set", "명장의 조각 도구", "SHEARS", ["tool_carving"], 800, {"craft": 5}, 700),
    ("silk_needle", "비단 바늘", "SHEARS", ["tool_sewing"], 600, {"craft": 3}, 300), ("crystal_alembic", "수정 증류기", "BOWL", ["tool_alchemy"], 600, {"craft": 4}, 500),
    ("mithril_pickaxe", "미스릴 곡괭이", "DIAMOND_PICKAXE", ["tool_pick"], 1200, {"craft": 2}, 650), ("woodsman_axe", "숲지기 도끼", "DIAMOND_AXE", ["tool_axe"], 1000, {"craft": 2}, 500),
    ("silver_rod", "은빛 낚싯대", "FISHING_ROD", ["tool_rod"], 600, {"craft": 2}, 300),
]:
    item(id, name, "TOOL", mat, tags, weight=2, dur=dur, stats=stats, price=p)

# ======================================================== 무기 (고유) — 오리지널
W = "WEAPON"
def weapon(id, name, mat, tags, atk, extra=None, req=None, dur=800, weight=4, lore=None, set_=None):
    s = {"attack": atk}
    if extra: s.update(extra)
    item(id, name, W, mat, tags, weight=weight, dur=dur, stats=s, req=req, lore=lore, set_=set_)
    DESIGN[id] = ("weapon", None)   # 공격력 · 옵션은 비율 — 크기는 balance_pass 가 착용 레벨의 원작 곡선에 맞춘다
SW, SP, AR, SC = "mastery.swordsmanship", "mastery.spearmanship", "mastery.archery", "mastery.spellcraft"
# 검
weapon("bronze_shortsword", "청동 단검(短劍)", "IRON_SWORD", ["sword"], 9, req={SW: 2}, dur=450, lore="대장간 견습생이 처음 두드려 보는 청동 칼")
weapon("militia_sabre", "민병대 사브르", "IRON_SWORD", ["sword", "curved"], 13, {"speed": 3}, {SW: 5}, lore="국경 민병대가 지급받는 가벼운 굽은 칼")
weapon("mithril_longsword", "미스릴 장검", "DIAMOND_SWORD", ["sword"], 24, {"crit": 4}, {SW: 14}, 1200, lore="미스릴을 접어 두드린 장검. 가볍고 잘 휘지 않는다")
weapon("ember_brand", "불씨의 검", "IRON_SWORD", ["sword", "wavy"], 21, {"fire": 6}, {SW: 12}, 900, lore="불씨 골짜기의 돌을 박아 넣어 칼날이 늘 따뜻하다")
weapon("frostbite_blade", "동상의 칼날", "DIAMOND_SWORD", ["sword", "frost"], 22, {"ice": 6, "slow": 12}, {SW: 13}, 950, lore="서리송곳니 고개의 고드름을 녹이지 않고 벼린 칼")
weapon("stormcaller", "폭풍을 부르는 검", "DIAMOND_SWORD", ["sword", "frost"], 30, {"lightning": 10, "stun": 4}, {SW: 20}, 1300, lore="폭풍 유리를 칼날에 녹여 넣었다. 휘두르면 공기가 찢어지는 소리가 난다")
weapon("dawn_oath", "새벽의 서약", "GOLDEN_SWORD", ["sword", "winged"], 33, {"holy": 10, "vs_undead": 25, "regen": 1}, {SW: 22, "fame": 400}, 1500, lore="성기사단이 서약식에서 받는 검. 동이 트면 칼날이 희게 빛난다")
weapon("nightfall_edge", "밤이 내린 칼날", "NETHERITE_SWORD", ["sword", "demonic"], 34, {"dark": 10, "crit": 8}, {SW: 22}, 1500, lore="흑철로 벼린 칼. 그림자 속에서 휘두르면 소리가 나지 않는다")
weapon("moonsilver_sword", "달은 검", "DIAMOND_SWORD", ["sword", "elven"], 28, {"crit": 10, "speed": 4}, {SW: 18}, 1000, lore="월석을 갈아 넣은 은빛 검. 달밤에 더 날카롭다")
weapon("starfall_greatsword", "별비 대검", "NETHERITE_SWORD", ["sword", "broad", "long"], 48, {"vs_large": 20, "crit": 6}, {SW: 28, "stat.strength": 14}, 2200, 9, lore="별조각을 녹여 만든 대검. 들어 올리는 데만 장정 둘이 필요하다")
weapon("rustgear_cleaver", "녹슨 톱니 식칼", "IRON_SWORD", ["sword", "jagged", "rust"], 16, {"stun": 3}, {SW: 8}, 700, lore="유적의 톱니를 갈아 붙인 칼. 톱처럼 살을 찢는다")
weapon("bloodthorn_sword", "핏빛가시 검", "IRON_SWORD", ["sword", "jagged"], 26, {"poison": 8, "lifesteal": 2}, {SW: 17}, 1000, lore="밀림의 가시덩굴이 손잡이를 감고 자란다")
# 단검
weapon("silk_stiletto", "명주 송곳단검", "IRON_SWORD", ["dagger"], 12, {"crit": 10}, {SW: 8}, 500, 1, lore="손잡이를 명주실로 감은 얇은 단검")
weapon("venom_fang", "독니", "IRON_SWORD", ["dagger"], 15, {"poison": 8}, {SW: 11}, 600, 1, lore="거대 독거미의 송곳니를 그대로 단 단검")
weapon("shadow_kris", "그림자 크리스", "NETHERITE_SWORD", ["dagger", "wavy"], 24, {"dark": 6, "crit": 14}, {SW: 19}, 900, 2, lore="물결 모양 칼날이 그림자를 베어 낸다는 단검")
weapon("pearl_dirk", "진주 단도", "GOLDEN_SWORD", ["dagger"], 14, {"regen": 1, "crit": 6}, {SW: 9}, 500, 1, lore="손잡이 끝에 진주를 박은 귀족의 호신용 단도")
# 도끼
weapon("bronze_hatchet", "청동 전투 손도끼", "IRON_AXE", ["axe"], 11, None, {SW: 4}, 500, 4, lore="나무꾼과 민병이 함께 쓰는 손도끼")
weapon("frostbeard_axe", "서리수염 도끼", "DIAMOND_AXE", ["axe", "broad"], 30, {"ice": 8, "stun": 4}, {SW: 18, "stat.strength": 10}, 1400, 8, lore="북부 산사람들이 서리 늑대를 잡을 때 쓰는 큰 도끼")
weapon("emberclaw_axe", "불발톱 도끼", "IRON_AXE", ["axe"], 24, {"fire": 7}, {SW: 14}, 1000, 6, lore="날이 불씨돌로 덮여 찍은 자리를 태운다")
weapon("twin_moon_axe", "쌍달 도끼", "NETHERITE_AXE", ["axe", "broad"], 42, {"crit": 6, "vs_large": 12}, {SW: 25, "stat.strength": 14}, 1800, 9, lore="양쪽 날이 초승달처럼 휜 대형 도끼")
weapon("woodsman_bane", "숲지기의 재앙", "IRON_AXE", ["axe", "rust"], 18, {"craft": 2}, {SW: 10}, 900, 6, lore="속빈뿌리 늪의 거목을 베어 낸 나무꾼의 도끼")
# 둔기
weapon("iron_flail", "철 도리깨", "IRON_AXE", ["mace"], 15, {"stun": 5}, {"stat.strength": 6}, 800, 7, lore="농기구를 개조한 민병대의 둔기")
weapon("templar_mace", "성당 기사의 철퇴", "GOLDEN_AXE", ["mace"], 27, {"holy": 8, "vs_undead": 20, "stun": 5}, {"stat.strength": 10, "fame": 200}, 1300, 8, lore="성당 기사들이 언데드의 뼈를 부술 때 쓰는 철퇴")
weapon("obsidian_maul", "흑요석 망치", "NETHERITE_AXE", ["mace", "stone_head"], 38, {"stun": 10, "fire": 4}, {"stat.strength": 16}, 1600, 11, lore="흑요석 이빨의 바위를 통째로 깎아 박은 망치")
weapon("clockwork_hammer", "태엽 해머", "IRON_AXE", ["mace"], 31, {"stun": 8, "speed": -4}, {"stat.strength": 12}, 1500, 9, lore="태엽을 감았다 풀면 머리가 튀어나가며 한 번 더 때린다")
# 창
weapon("bronze_pike", "청동 장창", "TRIDENT", ["spear"], 12, {"pierce": 6}, {SP: 4}, 600, 5, lore="청동 촉을 단 보병의 장창")
weapon("griffin_lance", "그리폰 기창", "TRIDENT", ["spear"], 30, {"pierce": 15, "speed": 4}, {SP: 19}, 1300, 6, lore="그리폰 깃털로 술을 단 기사의 창. 돌격하면 바람을 가른다")
weapon("tidecaller_trident", "물결을 부르는 삼지창", "TRIDENT", ["spear", "trident"], 32, {"ice": 6, "slow": 10}, {SP: 20}, 1400, 7, lore="가라앉은 종 호수의 수호자가 들던 삼지창")
weapon("emberwing_glaive", "불날개 언월도", "TRIDENT", ["spear"], 36, {"fire": 10, "crit": 4}, {SP: 23}, 1500, 7, lore="날이 넓은 장병기. 휘두를 때마다 불티가 날린다")
weapon("starpiercer", "별을 꿰는 창", "TRIDENT", ["spear"], 46, {"pierce": 25, "vs_large": 15}, {SP: 28}, 2000, 7, lore="별조각을 촉으로 깎아 만든 창. 갑옷을 종이처럼 뚫는다")
# 지팡이
weapon("birch_wand", "자작나무 지팡이", "STICK", ["staff"], 10, {"regen": 1}, {SC: 4}, 400, 2, lore="속삭이는 자작숲의 가지로 만든 가벼운 지팡이")
weapon("ember_scepter", "불씨의 홀", "BLAZE_ROD", ["staff"], 22, {"fire": 10}, {SC: 13}, 900, 2, lore="불씨돌을 머리에 박은 홀. 불꽃 주문을 키운다")
weapon("frostweave_staff", "서리 엮은 지팡이", "BLAZE_ROD", ["staff"], 23, {"ice": 10, "slow": 10}, {SC: 14}, 900, 2, lore="서리 정수를 실처럼 감아 만든 지팡이")
weapon("storm_rod", "폭풍의 막대", "BLAZE_ROD", ["staff"], 27, {"lightning": 12}, {SC: 18}, 1000, 2, lore="폭풍 유리 구슬이 끝에서 번쩍인다")
weapon("archivist_staff", "서고지기의 지팡이", "BLAZE_ROD", ["staff", "crook"], 25, {"resist": 8, "regen": 1}, {SC: 16}, 1000, 2, lore="가라앉은 서고의 마지막 서고지기가 짚던 굽은 지팡이")
weapon("moonwell_staff", "달샘 지팡이", "BLAZE_ROD", ["staff"], 34, {"holy": 10, "regen": 2}, {SC: 24}, 1300, 2, lore="달 분지의 샘물에 백 일 담가 둔 지팡이")
weapon("void_scepter", "공허의 홀", "BLAZE_ROD", ["staff"], 40, {"dark": 14, "drain": 1}, {SC: 27}, 1400, 2, lore="잡은 손의 온기를 빨아들이는 검은 홀. 그만큼 주문이 강해진다")
# 활
weapon("birch_shortbow", "자작나무 단궁", "BOW", ["bow"], 9, None, {AR: 3}, 350, 1, lore="가볍고 다루기 쉬운 짧은 활")
weapon("hunter_recurve", "사냥꾼의 리커브", "BOW", ["bow"], 18, {"crit": 6}, {AR: 10}, 700, 2, lore="양 끝이 앞으로 휜 사냥용 활. 짧아도 힘이 세다")
weapon("griffin_longbow", "그리폰 장궁", "BOW", ["bow", "elven"], 30, {"crit": 10, "pierce": 8}, {AR: 20}, 1000, 2, lore="그리폰 깃털 화살을 쓰는 장궁")
weapon("thornwood_bow", "가시나무 활", "BOW", ["bow"], 22, {"poison": 6}, {AR: 14}, 800, 2, lore="가시담 황야의 나무로 휜 활. 화살 끝에 독이 밴다")
weapon("clockwork_crossbow", "태엽 석궁", "CROSSBOW", ["bow", "crossbow"], 28, {"pierce": 16}, {AR: 18}, 900, 5, lore="태엽으로 시위를 당기는 연발 석궁")
# 낫 · 채찍
weapon("reaper_scythe", "수확자의 낫", "IRON_HOE", ["scythe"], 26, {"dark": 6, "lifesteal": 2}, {SW: 17}, 1100, 5, lore="밭을 베던 낫이 전쟁에서 사람을 베게 되었다")
weapon("harvest_moon_scythe", "한가위 낫", "IRON_HOE", ["scythe"], 34, {"crit": 8, "holy": 6}, {SW: 22}, 1300, 5, lore="보름달 밤에만 벼린다는 낫. 은빛 날이 둥글다")
weapon("thorn_whip", "가시 채찍", "LEAD", ["whip"], 16, {"poison": 4, "slow": 6}, {SW: 10}, 800, 2, lore="가시덩굴을 엮어 만든 채찍")
weapon("chain_whip", "쇠사슬 채찍", "LEAD", ["whip"], 26, {"stun": 6}, {SW: 17}, 1200, 4, lore="쇠고리를 이어 만든 채찍. 감기면 빠져나오기 어렵다")

# ======================================================== 방어구 세트 (오리지널)
A = "ARMOR"
SETS = {}
def armor_set(sid, sname, prefix, mats, base_def, extra, req, lore, bonuses):
    SETS[sid] = {"name": sname, "source": "ORIGINAL", "bonuses": bonuses}
    parts = [("helmet", "투구", mats[0], 0.6), ("chest", "갑옷", mats[1], 1.0), ("legs", "각반", mats[2], 0.8), ("boots", "장화", mats[3], 0.5)]
    for key, pname, mat, k in parts:
        s = {"defense": max(1, round(base_def * k))}
        for ek, ev in extra.items(): s[ek] = max(1, round(ev * k))
        item(f"{sid}_{key}", f"{prefix} {pname}", A, mat, [key] if key != "chest" else ["chest"], weight=max(1, round(4 * k)), dur=int(600 + base_def * 60), stats=s, req=req, lore=lore, set_=sid)
        DESIGN[f"{sid}_{key}"] = ("armor", key)
armor_set("militia", "민병대 차림", "민병대", ["LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS"], 6, {}, None,
          "국경 마을 민병대가 맞춰 입는 가죽 차림", {2: {"defense": 2}, 4: {"health": 3}})
armor_set("bronze_guard", "청동 경비대 갑주", "청동 경비대", ["CHAINMAIL_HELMET", "CHAINMAIL_CHESTPLATE", "CHAINMAIL_LEGGINGS", "CHAINMAIL_BOOTS"], 10, {"resist": 2}, {"stat.strength": 4},
          "성문 경비대의 청동 비늘 갑주", {2: {"defense": 3}, 4: {"health": 4, "resist": 4}})
armor_set("ranger", "순찰자 차림", "순찰자", ["LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS"], 9, {"speed": 4, "crit": 2}, {AR: 10},
          "숲과 황야를 걷는 순찰자의 질긴 가죽옷", {2: {"speed": 4}, 4: {"crit": 6}})
armor_set("mithril_knight", "미스릴 기사 갑주", "미스릴 기사", ["DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS"], 18, {"health": 3, "resist": 4}, {SW: 18, "stat.strength": 10},
          "미스릴 판금을 겹친 기사의 갑주. 무게에 비해 단단하다", {2: {"defense": 6}, 3: {"health": 6}, 4: {"resist": 10, "vs_large": 10}})
armor_set("archmage", "대마법사의 의복", "대마법사의", ["LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS"], 8, {"resist": 6, "regen": 1}, {SC: 18},
          "명주 비단에 마나 가루로 문양을 수놓은 로브", {2: {"resist": 6}, 4: {"regen": 2, "health": 4}})
armor_set("shadowstalker", "그림자 추적자 차림", "그림자 추적자", ["LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS"], 11, {"crit": 4, "speed": 4}, {SW: 16},
          "소리를 먹는 검은 가죽으로 지은 옷", {2: {"crit": 6}, 4: {"speed": 6, "crit": 6}})
armor_set("emberforged", "불씨 단조 갑주", "불씨 단조", ["IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS"], 15, {"resist": 6}, {"stat.strength": 10},
          "불씨돌을 녹여 담금질한 붉은 판금", {2: {"resist": 8}, 4: {"health": 6, "thorns": 4}})
armor_set("frostwarden", "서리 파수꾼 갑주", "서리 파수꾼", ["IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS"], 16, {"resist": 6, "health": 2}, {"stat.endurance": 10},
          "서리송곳니 고개 파수꾼들의 털 안감 판금", {2: {"resist": 8}, 4: {"health": 8}})
armor_set("dragonscale", "비룡 비늘 갑주", "비룡 비늘", ["NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS", "NETHERITE_BOOTS"], 24, {"resist": 10, "health": 4}, {SW: 26, "stat.strength": 16},
          "비룡의 비늘을 한 장씩 꿰매 붙인 갑주. 불길이 미끄러진다", {2: {"defense": 8}, 3: {"health": 8}, 4: {"resist": 14, "vs_dragon": 20}})
# 방패
for id, name, mat, d, ex, req, lore in [
    ("bronze_heater", "청동 기사 방패", "SHIELD", 7, {}, None, "청동 테를 두른 삼각 방패"),
    ("tower_shield", "탑 방패", "SHIELD", 14, {"speed": -6, "resist": 4}, {"stat.strength": 12}, "몸 전체를 가리는 큰 방패"),
    ("mithril_aegis", "미스릴 수호 방패", "SHIELD", 18, {"resist": 8}, {SW: 20}, "미스릴 판을 겹친 가벼운 방패"),
    ("dawn_aegis", "새벽 방패", "SHIELD", 20, {"holy": 6, "regen": 1}, {SW: 22, "fame": 400}, "성기사단의 흰 방패. 언데드의 손톱이 미끄러진다"),
]:
    s = {"defense": d}; s.update(ex)
    item(id, name, A, mat, ["shield"], weight=5, dur=1000 + d * 50, stats=s, req=req, lore=lore)
    DESIGN[id] = ("armor", "shield")
# 장신구
for id, name, mat, kind, st, lore in [
    ("copper_ring", "구리 반지", "IRON_NUGGET", "ring", {"health": 1}, "견습 세공사가 처음 만드는 반지"),
    ("ruby_ring", "루비 반지", "GOLD_NUGGET", "ring", {"attack": 3, "fire": 2}, "불꽃처럼 붉은 루비를 박은 반지"),
    ("sapphire_ring", "사파이어 반지", "GOLD_NUGGET", "ring", {"resist": 6, "ice": 2}, "맑은 물빛 사파이어 반지"),
    ("emerald_ring", "에메랄드 반지", "GOLD_NUGGET", "ring", {"regen": 1, "poison": 2}, "숲빛 에메랄드 반지"),
    ("moonstone_ring", "월석 반지", "IRON_NUGGET", "ring", {"crit": 6}, "달빛을 머금은 은반지"),
    ("star_signet", "별의 인장 반지", "GOLD_NUGGET", "ring", {"crit": 6, "attack": 4, "resist": 4}, "별조각을 깎아 인장으로 새긴 반지"),
    ("pearl_necklace", "진주 목걸이", "GOLD_NUGGET", "necklace", {"regen": 1, "health": 3}, "가라앉은 종 호수의 진주를 꿴 목걸이"),
    ("amber_pendant", "호박 펜던트", "GOLD_NUGGET", "necklace", {"resist": 4, "health": 2}, "벌레가 갇힌 호박 펜던트. 행운을 부른다고 한다"),
    ("ember_heart", "불씨 심장 목걸이", "GOLD_NUGGET", "necklace", {"fire": 6, "attack": 4}, "식지 않는 불씨돌을 금줄에 건 목걸이"),
    ("frost_tear", "서리 눈물 목걸이", "IRON_NUGGET", "necklace", {"ice": 6, "resist": 6}, "녹지 않는 얼음 한 방울"),
    ("griffin_bracer", "그리폰 팔찌", "GOLD_NUGGET", "bracelet", {"speed": 6}, "그리폰 깃털을 엮은 팔찌"),
    ("iron_bracer", "철 손목보호대", "IRON_NUGGET", "bracelet", {"defense": 3}, "검사들이 손목을 보호하려 차는 쇠 팔찌"),
    ("silk_gloves", "명주 장갑", "LEATHER", "gloves", {"craft": 4}, "손끝 감각을 살려 주는 얇은 비단 장갑 — 장인들이 아낀다"),
    ("bear_gauntlets", "곰가죽 건틀릿", "LEATHER", "gloves", {"attack": 4, "defense": 2}, "곰 모피를 두른 두꺼운 장갑"),
    ("wanderer_cloak", "나그네 망토", "LEATHER", "cloak", {"speed": 4, "resist": 2}, "비바람을 막는 잿빛 망토"),
    ("mistveil_cloak", "안개 장막 망토", "LEATHER", "cloak", {"speed": 6, "crit": 4}, "안개 군도의 해적들이 두르는 망토. 안개처럼 흐릿하게 보인다"),
    ("adventurer_belt", "모험가의 허리띠", "LEATHER", "belt", {"health": 3}, "물약을 꽂는 고리가 달린 허리띠"),
    ("champion_belt", "투사의 허리띠", "LEATHER", "belt", {"attack": 3, "health": 4}, "투기장 우승자에게 주는 금장 허리띠"),
    ("mana_orb", "마나 구슬", "ENDER_EYE", "orb", {"regen": 1, "resist": 4}, "마나 가루를 굳혀 만든 구슬"),
    ("storm_orb", "폭풍 구슬", "ENDER_EYE", "orb", {"lightning": 6, "attack": 3}, "안에서 번개가 갇혀 돈다"),
    ("compass_of_ways", "길잡이 나침반", "COMPASS", "compass", {"speed": 4}, "주인이 가고 싶은 곳을 가리킨다는 나침반"),
    ("lucky_feather", "행운의 깃털", "FEATHER", "feather", {"crit": 4}, "별비 초원에서 주운 반짝이는 깃털"),
]:
    item(id, name, A, mat, [kind, "accessory"], weight=1, dur=500, stats=st, lore=lore)
    DESIGN[id] = ("accessory", None)

# ======================================================== 자원 (오리지널 채집점)
res("coal_seam", "석탄층", "mining", 1, ["COAL_ORE", "DEEPSLATE_COAL_ORE"], "coal_lump", ["mountain", "underground"], "tool_pick", amount=2, respawn=60)
res("gold_vein", "금 광맥", "mining", 10, ["NETHER_GOLD_ORE"], "gold_ore", ["mountain", "deep", "desert"], "tool_pick", respawn=300)
res("mithril_vein", "미스릴 광맥", "mining", 18, ["EMERALD_ORE", "DEEPSLATE_EMERALD_ORE"], "mithril_ore", ["deep", "mountain"], "tool_pick", respawn=900)
res("lapis_vein", "사파이어 원석층", "mining", 14, ["LAPIS_ORE", "DEEPSLATE_LAPIS_ORE"], "sapphire", ["deep", "mountain"], "tool_pick", respawn=600)
res("ruby_vein", "루비 원석층", "mining", 14, ["REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE"], "ruby", ["deep", "volcano"], "tool_pick", respawn=600)
res("sulfur_vent", "유황 분출구", "mining", 8, ["MAGMA_BLOCK"], "sulfur", ["volcano", "badlands"], "tool_pick", respawn=300)
res("obsidian_rock", "흑요석 바위", "mining", 16, ["OBSIDIAN"], "obsidian_shard", ["volcano", "canyon"], "tool_pick", respawn=900)
res("ember_rock", "불씨 바위", "mining", 12, ["NETHERRACK"], "ember_stone", ["volcano", "valley"], "tool_pick", respawn=600)
res("birch_tree", "자작나무", "logging", 3, ["BIRCH_LOG"], "birch_timber", ["forest", "mist"], "tool_axe", amount=1, bonus=2)
res("jungle_tree", "밀림 거목", "logging", 10, ["JUNGLE_LOG"], "jungle_timber", ["swamp", "frontier"], "tool_axe", bonus=2, respawn=240)
res("dark_oak_tree", "검은참나무", "logging", 8, ["DARK_OAK_LOG"], "dark_timber", ["forest", "mist"], "tool_axe", bonus=2, respawn=240)
res("mangrove_tree", "속빈뿌리 나무", "logging", 6, ["MANGROVE_LOG"], "hollow_wood", ["swamp"], "tool_axe", bonus=2, respawn=180)
res("carrot_field", "당근밭", "herbalism", 1, ["CARROTS"], "carrot", ["farmland", "plains"], amount=1, bonus=2, respawn=60)
res("potato_field", "감자밭", "herbalism", 1, ["POTATOES"], "potato", ["farmland", "plains"], amount=1, bonus=2, respawn=60)
res("beet_field", "비트밭", "herbalism", 2, ["BEETROOTS"], "beet", ["farmland"], amount=1, bonus=2, respawn=60)
res("berry_bush", "열매 덤불", "herbalism", 2, ["SWEET_BERRY_BUSH"], "thorn_berry", ["forest", "highland", "frontier"], bonus=2, respawn=90)
res("mushroom_patch", "버섯 군락", "herbalism", 3, ["BROWN_MUSHROOM"], "mushroom", ["forest", "swamp", "underground"], bonus=2, respawn=90)
res("glowcap_patch", "빛버섯 군락", "herbalism", 12, ["RED_MUSHROOM"], "glowcap", ["underground", "deep", "swamp"], respawn=300)
res("moss_bed", "이끼 바닥", "herbalism", 4, ["MOSS_BLOCK", "MOSS_CARPET"], "bog_moss", ["swamp"], bonus=2, respawn=120)
res("vine_tangle", "가시덩굴", "herbalism", 10, ["VINE"], "bloodthorn_vine", ["swamp", "frontier"], respawn=240)
res("cobweb_nest", "거미줄", "herbalism", 6, ["COBWEB"], "spider_silk", ["forest", "ruins", "underground"], respawn=240)
res("cane_patch", "사탕수수밭", "herbalism", 2, ["SUGAR_CANE"], "sugar_cane", ["river", "lake", "coast"], bonus=2, respawn=90)
res("bee_nest", "야생 벌집", "herbalism", 8, ["BEE_NEST", "BEEHIVE"], "honey_comb", ["forest", "farmland", "valley"], respawn=300)
res("cocoa_pod", "카카오 열매", "herbalism", 9, ["COCOA"], "cocoa", ["swamp", "frontier"], respawn=300)
res("lake_fish", "호수 낚시", "fishing", 4, ["WATER"], "trout", ["lake", "river"], "tool_rod", respawn=0)
res("reef_fish", "산호초 낚시", "fishing", 12, ["WATER"], "pufferfish", ["coast", "sea"], "tool_rod", respawn=0)

# ======================================================== 제작법 — 대장 · 재봉 · 가죽 · 요리 · 연금 · 목공 · 조각 · 보석 세공 · 양조 · 지도 제작 · 기계 공학
HAM, SEW, COOK, ALC, CARV = "tool_hammer", "tool_sewing", "tool_cook", "tool_alchemy", "tool_carving"
# 제련
recipe("smelt_copper", "구리 제련", "smithing", 1, "copper_ingot", {"ore": (I("copper_ore"), 2)}, xp=4)
recipe("alloy_bronze", "청동 합금", "smithing", 4, "bronze_ingot", {"copper": (I("copper_ingot"), 3), "tin": (I("iron_ore"), 1)}, xp=10)
recipe("smelt_gold", "금 제련", "smithing", 9, "gold_ingot", {"ore": (I("gold_ore"), 2), "fuel": (T("fuel"), 1)})
recipe("smelt_mithril", "미스릴 제련", "smithing", 18, "mithril", {"ore": (I("mithril_ore"), 3), "fuel": (T("fuel"), 2), "flux": (T("powder"), 1, 0, 40)})
recipe("smelt_black_iron", "흑철 제련", "smithing", 22, "black_iron", {"iron": (I("iron_ingot"), 3), "obsidian": (I("obsidian_shard"), 2), "essence": (I("ghost_essence"), 1)}, discovery="required")
recipe("hammer_gold_leaf", "금박 두드리기", "smithing", 10, "gold_leaf", {"gold": (I("gold_ingot"), 1)}, count=4)
recipe("make_gears", "톱니 깎기", "engineering", 1, "old_gear", {"metal": (T("metal"), 1)}, count=2, xp=5)
SMITH = [
    ("bronze_shortsword", 2, {"blade": (I("bronze_ingot"), 2, 3), "grip": (T("wood"), 1)}),
    ("militia_sabre", 5, {"blade": (I("iron_ingot"), 3, 3), "grip": (T("leather"), 1)}),
    ("mithril_longsword", 14, {"blade": (I("mithril"), 3, 4), "grip": (T("leather"), 1), "gem": (T("cut_gem"), 1, 0, 60)}),
    ("ember_brand", 12, {"blade": (I("iron_ingot"), 3, 3), "core": (I("ember_stone"), 2, 2), "grip": (T("leather"), 1)}),
    ("frostbite_blade", 13, {"blade": (I("iron_ingot"), 3, 3), "core": (I("frost_crystal"), 2, 2), "grip": (T("leather"), 1)}),
    ("stormcaller", 20, {"blade": (I("mithril"), 3, 4), "core": (I("storm_glass"), 2, 2), "essence": (I("storm_essence"), 1)}),
    ("dawn_oath", 22, {"blade": (I("mithril"), 4, 4), "gild": (I("gold_leaf"), 2), "essence": (T("essence"), 1)}),
    ("nightfall_edge", 22, {"blade": (I("black_iron"), 3, 4), "grip": (T("leather"), 1), "essence": (I("ghost_essence"), 1)}),
    ("moonsilver_sword", 18, {"blade": (I("silver_ingot"), 3, 3), "moon": (I("moonstone"), 2, 2), "grip": (T("silk"), 1)}),
    ("starfall_greatsword", 28, {"blade": (I("black_iron"), 5, 4), "star": (I("star_shard"), 2, 3), "grip": (I("thick_leather"), 2)}),
    ("rustgear_cleaver", 8, {"blade": (I("iron_ingot"), 2, 2), "teeth": (I("old_gear"), 3)}),
    ("bloodthorn_sword", 17, {"blade": (I("mithril"), 2, 3), "vine": (I("bloodthorn_vine"), 3), "venom": (I("venom_sac"), 1)}),
    ("silk_stiletto", 8, {"blade": (I("iron_ingot"), 1, 2), "wrap": (I("spider_silk"), 2)}),
    ("venom_fang", 11, {"fang": (I("monster_bone"), 2, 2), "venom": (I("venom_sac"), 2), "grip": (T("leather"), 1)}),
    ("shadow_kris", 19, {"blade": (I("black_iron"), 2, 3), "essence": (I("ghost_essence"), 1)}),
    ("pearl_dirk", 9, {"blade": (I("silver_ingot"), 1, 2), "pearl": (I("pearl"), 1)}),
    ("bronze_hatchet", 4, {"head": (I("bronze_ingot"), 2, 3), "haft": (T("wood"), 1)}),
    ("frostbeard_axe", 18, {"head": (I("mithril"), 3, 3), "frost": (I("frost_essence"), 1), "haft": (I("dark_timber"), 2)}),
    ("emberclaw_axe", 14, {"head": (I("iron_ingot"), 3, 3), "ember": (I("ember_stone"), 2), "haft": (T("wood"), 2)}),
    ("twin_moon_axe", 25, {"head": (I("black_iron"), 4, 4), "moon": (I("moonstone"), 2), "haft": (I("dark_timber"), 2)}),
    ("woodsman_bane", 10, {"head": (I("iron_ingot"), 2, 3), "haft": (I("hollow_wood"), 2)}),
    ("iron_flail", 6, {"head": (I("iron_ingot"), 3, 3), "chain": (I("iron_ingot"), 1), "haft": (T("wood"), 1)}),
    ("templar_mace", 15, {"head": (I("silver_ingot"), 3, 3), "gild": (I("gold_leaf"), 2), "haft": (T("wood"), 1)}),
    ("obsidian_maul", 24, {"head": (I("obsidian_shard"), 5, 4), "bind": (I("black_iron"), 1), "haft": (I("dark_timber"), 2)}),
    ("bronze_pike", 4, {"head": (I("bronze_ingot"), 1, 2), "shaft": (T("wood"), 3)}),
    ("griffin_lance", 19, {"head": (I("mithril"), 2, 3), "shaft": (I("dark_timber"), 3), "plume": (I("griffin_feather"), 2)}),
    ("tidecaller_trident", 20, {"head": (I("silver_ingot"), 3, 3), "pearl": (I("pearl"), 2), "shaft": (T("wood"), 2)}),
    ("emberwing_glaive", 23, {"blade": (I("mithril"), 3, 3), "ember": (I("fire_essence"), 1), "shaft": (I("dark_timber"), 2)}),
    ("starpiercer", 28, {"head": (I("star_shard"), 1, 4), "socket": (I("black_iron"), 2), "shaft": (I("dark_timber"), 3)}),
    ("reaper_scythe", 17, {"blade": (I("iron_ingot"), 3, 3), "essence": (I("ghost_essence"), 1), "haft": (T("wood"), 2)}),
    ("harvest_moon_scythe", 22, {"blade": (I("silver_ingot"), 3, 3), "moon": (I("moonstone"), 2), "haft": (I("birch_timber"), 2)}),
    ("chain_whip", 17, {"links": (I("iron_ingot"), 4, 3), "grip": (T("leather"), 1)}),
    ("mithril_hammer", 18, {"head": (I("mithril"), 2, 3), "haft": (T("wood"), 1)}),
    ("mithril_pickaxe", 18, {"head": (I("mithril"), 3, 3), "haft": (T("wood"), 2)}),
    ("woodsman_axe", 12, {"head": (I("iron_ingot"), 3, 3), "haft": (I("dark_timber"), 1)}),
    ("bronze_heater", 5, {"boards": (T("wood"), 2, 2), "rim": (I("bronze_ingot"), 2, 2)}),
    ("tower_shield", 14, {"plates": (I("iron_ingot"), 6, 3), "boards": (T("wood"), 3)}),
    ("mithril_aegis", 20, {"plates": (I("mithril"), 4, 4), "strap": (I("thick_leather"), 1)}),
    ("dawn_aegis", 22, {"plates": (I("mithril"), 4, 4), "gild": (I("gold_leaf"), 3), "essence": (T("essence"), 1)}),
    ("iron_bracer", 3, {"plate": (I("iron_ingot"), 1, 2), "strap": (T("leather"), 1)}),
]
for out, lv, slots in SMITH:
    recipe("forge_" + out, items[out]["name"], "smithing", lv, out, slots, tool=HAM, ticks=60 + lv * 3)
# 판금 · 사슬 세트
for sid, lv, mat, cnt in [("bronze_guard", 6, "bronze_ingot", 1), ("mithril_knight", 18, "mithril", 1), ("emberforged", 15, "iron_ingot", 1), ("frostwarden", 16, "iron_ingot", 1), ("dragonscale", 26, "black_iron", 1)]:
    for key, n in [("helmet", 3), ("chest", 5), ("legs", 4), ("boots", 2)]:
        slots = {"plates": (I(mat), n * cnt, 3), "lining": (T("cloth"), 1)}
        if sid == "emberforged": slots["temper"] = (I("ember_stone"), 1)
        if sid == "frostwarden": slots["fur"] = (I("wolf_pelt"), 1)
        if sid == "dragonscale": slots["scales"] = (I("wyvern_scale"), max(1, n - 1), 2)
        if sid == "mithril_knight": slots["gem"] = (T("cut_gem"), 1, 0, 50)
        recipe(f"forge_{sid}_{key}", items[f"{sid}_{key}"]["name"], "smithing", lv, f"{sid}_{key}", slots, tool=HAM, ticks=60 + lv * 3)
# 가죽 · 재봉 세트
for sid, lv, disc, main, cnt in [("militia", 2, "leatherwork", "leather", 1), ("ranger", 10, "leatherwork", "thick_leather", 1), ("shadowstalker", 16, "leatherwork", "thick_leather", 1), ("archmage", 18, "tailoring", "silk_cloth", 1)]:
    for key, n in [("helmet", 2), ("chest", 4), ("legs", 3), ("boots", 2)]:
        slots = {"main": (I(main), n, 3), "thread": (T("fiber"), 1)}
        if sid == "shadowstalker": slots["shadow"] = (I("ghost_essence"), 1)
        if sid == "archmage": slots["mana"] = (I("mana_dust"), 2); slots["dye"] = (T("dye"), 1, 0, 40)
        if sid == "ranger": slots["fur"] = (I("wolf_pelt"), 1, 0, 30)
        recipe(f"sew_{sid}_{key}", items[f"{sid}_{key}"]["name"], disc, lv, f"{sid}_{key}", slots, tool=SEW, ticks=50 + lv * 3)
recipe("tan_thick_leather", "두꺼운 가죽 무두질", "leatherwork", 8, "thick_leather", {"pelt": (T("fur"), 2, 2), "salt": (T("seasoning"), 1), "oil": (I("lantern_oil"), 1, 0, 30)})
recipe("weave_silk", "비단 짜기", "tailoring", 10, "silk_cloth", {"silk": (I("spider_silk"), 4, 3)})
recipe("weave_wool", "모직 짜기", "tailoring", 3, "wool_cloth", {"fiber": (T("fiber"), 3)})
recipe("sew_silk_gloves", "명주 장갑", "tailoring", 12, "silk_gloves", {"silk": (I("silk_cloth"), 2, 3), "thread": (I("spider_silk"), 1)}, tool=SEW)
recipe("sew_wanderer_cloak", "나그네 망토", "tailoring", 6, "wanderer_cloak", {"cloth": (I("wool_cloth"), 3, 3), "dye": (T("dye"), 1, 0, 40)}, tool=SEW)
recipe("sew_mistveil_cloak", "안개 장막 망토", "tailoring", 17, "mistveil_cloak", {"cloth": (I("silk_cloth"), 3, 3), "essence": (I("ghost_essence"), 1)}, tool=SEW)
recipe("stitch_adventurer_belt", "모험가의 허리띠", "leatherwork", 4, "adventurer_belt", {"leather": (T("leather"), 2, 3), "buckle": (T("metal"), 1)}, tool=SEW)
recipe("stitch_champion_belt", "투사의 허리띠", "leatherwork", 15, "champion_belt", {"leather": (I("thick_leather"), 2, 3), "gild": (I("gold_leaf"), 2)}, tool=SEW)
recipe("stitch_bear_gauntlets", "곰가죽 건틀릿", "leatherwork", 12, "bear_gauntlets", {"pelt": (I("bear_pelt"), 2, 3), "plate": (I("iron_ingot"), 1)}, tool=SEW)
recipe("braid_thorn_whip", "가시 채찍", "leatherwork", 10, "thorn_whip", {"vine": (I("bloodthorn_vine"), 4, 3), "grip": (T("leather"), 1)}, tool=SEW)
# 목공
for out, lv, slots in [
    ("birch_wand", 4, {"shaft": (I("birch_timber"), 2, 3), "focus": (T("gem"), 1, 0, 60)}),
    ("ember_scepter", 13, {"shaft": (I("dark_timber"), 2, 3), "focus": (I("ember_stone"), 2, 2), "essence": (I("fire_essence"), 1)}),
    ("frostweave_staff", 14, {"shaft": (I("birch_timber"), 2, 3), "focus": (I("frost_crystal"), 2, 2), "essence": (I("frost_essence"), 1)}),
    ("storm_rod", 18, {"shaft": (I("dark_timber"), 2, 3), "focus": (I("storm_glass"), 2, 2), "essence": (I("storm_essence"), 1)}),
    ("archivist_staff", 16, {"shaft": (I("dark_timber"), 3, 3), "ink": (I("fine_ink"), 2), "focus": (T("gem"), 1)}),
    ("moonwell_staff", 24, {"shaft": (I("birch_timber"), 3, 3), "focus": (I("moonstone"), 3, 3), "sap": (I("spirit_sap"), 2)}),
    ("void_scepter", 27, {"shaft": (I("dark_timber"), 2, 3), "focus": (I("obsidian_shard"), 2, 2), "essence": (I("ghost_essence"), 2)}),
    ("birch_shortbow", 3, {"limb": (I("birch_timber"), 2, 3), "string": (T("fiber"), 2)}),
    ("hunter_recurve", 10, {"limb": (T("wood"), 3, 3), "horn": (I("monster_bone"), 2), "string": (T("fiber"), 2)}),
    ("griffin_longbow", 20, {"limb": (I("jungle_timber"), 3, 3), "string": (I("spider_silk"), 2), "fletch": (I("griffin_feather"), 2)}),
    ("thornwood_bow", 14, {"limb": (I("hollow_wood"), 3, 3), "vine": (I("bloodthorn_vine"), 2), "string": (T("fiber"), 2)}),
    ("quill_set", 2, {"feather": (I("griffin_feather"), 1), "nib": (T("metal"), 1)}),
    ("brew_paddle", 1, {"wood": (T("wood"), 2)}),
]:
    recipe("carve_" + out, items[out]["name"], "woodworking", lv, out, slots, ticks=50 + lv * 3)
# 요리
for out, lv, slots, cnt in [
    ("apple_pie", 5, {"fruit": (I("apple"), 2, 2), "flour": (I("flour"), 2), "sweet": (T("sweet"), 1, 0, 40)}, 2),
    ("mushroom_soup", 2, {"mushroom": (T("mushroom"), 2, 2), "base": (T("vegetable"), 1)}, 2),
    ("roast_fowl", 3, {"meat": (I("raw_fowl"), 1, 2), "herb": (T("herb"), 1, 0, 40)}, 1),
    ("herb_bread", 3, {"flour": (I("flour"), 2, 2), "herb": (T("herb"), 1)}, 2),
    ("cheese_bread", 8, {"flour": (I("flour"), 2, 2), "cheese": (I("cheese"), 1)}, 2),
    ("veg_stew", 4, {"veg": (T("vegetable"), 3, 2), "spice": (T("spice"), 1, 0, 40)}, 2),
    ("fish_pie", 9, {"fish": (T("fish"), 2, 2), "flour": (I("flour"), 2), "egg": (I("egg"), 1)}, 2),
    ("spicy_stew", 12, {"meat": (T("meat"), 2, 2), "pepper": (I("pepper"), 2), "veg": (T("vegetable"), 1)}, 2),
    ("honey_cake", 14, {"flour": (I("flour"), 3, 2), "honey": (I("honey_comb"), 2), "egg": (I("egg"), 2), "milk": (I("milk_jug"), 1)}, 1),
    ("trail_ration", 2, {"grain": (T("grain"), 2), "fruit": (T("fruit"), 1)}, 3),
    ("crab_bisque", 13, {"crab": (I("lake_crab"), 2, 2), "milk": (I("milk_jug"), 1), "onion": (I("onion"), 1)}, 2),
    ("grilled_trout", 2, {"fish": (I("trout"), 1, 2), "salt": (T("seasoning"), 1, 0, 50)}, 1),
    ("baked_potato", 1, {"potato": (I("potato"), 1)}, 1),
    ("cookie", 3, {"flour": (I("flour"), 1), "sweet": (T("sweet"), 1)}, 4),
    ("chocolate", 11, {"cocoa": (I("cocoa"), 2, 2), "sweet": (I("sugar_cane"), 1), "milk": (I("milk_jug"), 1)}, 2),
    ("pufferfish_sashimi", 20, {"fish": (I("pufferfish"), 1, 3), "knife": (T("seasoning"), 1)}, 1),
    ("royal_feast", 26, {"meat": (T("meat"), 3, 2), "fish": (T("fish"), 2, 2), "cake": (I("honey_cake"), 1), "wine": (I("grape_wine"), 1), "gold": (I("gold_leaf"), 1, 0, 80)}, 1),
    ("cheese", 6, {"milk": (I("milk_jug"), 2, 2), "salt": (T("seasoning"), 1)}, 1),
    ("flour", 1, {"grain": (T("grain"), 2)}, 2),
]:
    recipe("cook_" + out, items[out]["name"], "cooking", lv, out, slots, tool=COOK if lv >= 4 else None, count=cnt)
# 연금
for out, lv, slots, cnt in [
    ("antidote", 3, {"herb": (T("herb"), 2, 2), "base": (I("glass_sand"), 1), "moss": (I("bog_moss"), 1)}, 2),
    ("stamina_tonic", 8, {"herb": (T("herb"), 2, 2), "sweet": (T("sweet"), 1), "base": (I("minor_tonic"), 1)}, 1),
    ("supreme_draught", 26, {"base": (I("greater_draught"), 1), "blood": (I("troll_blood"), 1), "sap": (I("spirit_sap"), 1)}, 1),
    ("elixir_of_life", 30, {"base": (I("supreme_draught"), 2), "star": (I("star_shard"), 1), "sap": (I("spirit_sap"), 2)}, 1),
    ("fire_resist_potion", 12, {"herb": (T("herb"), 2), "frost": (I("frost_essence"), 1), "base": (I("minor_tonic"), 1)}, 1),
    ("frost_resist_potion", 12, {"herb": (T("herb"), 2), "fire": (I("fire_essence"), 1), "base": (I("minor_tonic"), 1)}, 1),
    ("mana_potion", 10, {"dust": (I("mana_dust"), 2, 2), "herb": (T("herb"), 1), "base": (I("glass_sand"), 1)}, 1),
    ("greater_mana_potion", 20, {"dust": (I("mana_dust"), 3, 2), "glow": (I("glowcap"), 2), "base": (I("mana_potion"), 1)}, 1),
    ("mana_dust", 8, {"gem": (T("gem"), 1, 2), "glow": (I("glowcap"), 1)}, 3),
    ("lantern_oil", 2, {"fat": (T("meat"), 1), "wax": (T("wax"), 1, 0, 30)}, 3),
    ("bone_dust", 1, {"bone": (T("bone"), 1)}, 2),
    ("fine_ink", 5, {"ink": (I("ink_sac"), 2), "dust": (I("bone_dust"), 1)}, 2),
    ("fire_essence", 16, {"ember": (I("ember_stone"), 2, 2), "sulfur": (I("sulfur"), 1)}, 1),
    ("frost_essence", 16, {"frost": (I("frost_crystal"), 2, 2), "salt": (T("seasoning"), 1)}, 1),
    ("storm_essence", 18, {"glass": (I("storm_glass"), 2, 2), "copper": (I("copper_ingot"), 1)}, 1),
    ("herbal_tea", 2, {"herb": (T("herb"), 2)}, 2),
]:
    recipe("brew_" + out if not out.startswith("brew") else out, items[out]["name"], "alchemy", lv, out, slots, tool=ALC if lv >= 3 else None, count=cnt)
# 양조 (통)
BREW = "tool_brew"
for out, lv, slots in [
    ("barley_ale", 1, {"barley": (I("barley"), 3, 2), "hops": (I("hops"), 1, 0, 50)}),
    ("dark_stout", 8, {"barley": (I("barley"), 4, 2), "hops": (I("hops"), 2), "roast": (I("coal_lump"), 1)}),
    ("grape_wine", 10, {"grapes": (I("grapes"), 5, 3), "sweet": (T("sweet"), 1, 0, 30)}),
    ("honey_mead", 9, {"honey": (I("honey_comb"), 3, 3), "spice": (T("spice"), 1, 0, 40)}),
    ("fire_brandy", 18, {"wine": (I("grape_wine"), 2, 2), "pepper": (I("pepper"), 2), "ember": (I("ember_stone"), 1)}),
    ("dwarf_spirit", 24, {"stout": (I("dark_stout"), 2, 2), "glow": (I("glowcap"), 1), "ember": (I("fire_essence"), 1)}),
    ("cider", 3, {"apple": (I("apple"), 4, 2)}),
]:
    recipe("brew_" + out, items[out]["name"], "brewing", lv, out, slots, tool=BREW if lv >= 5 else None, count=2, ticks=80 + lv * 4)
# 보석 세공 (숫돌)
JEW = "tool_jewel"
recipe("cut_ruby", "루비 연마", "jewelcraft", 6, "ruby", {"rough": (I("raw_gem"), 2, 2)}, xp=12)
recipe("cut_sapphire", "사파이어 연마", "jewelcraft", 6, "sapphire", {"rough": (I("raw_gem"), 2, 2), "frost": (I("frost_crystal"), 1, 0, 40)}, xp=12)
recipe("cut_emerald", "에메랄드 연마", "jewelcraft", 8, "emerald_gem", {"rough": (I("raw_gem"), 2, 2), "moss": (I("bog_moss"), 1, 0, 40)}, xp=14)
recipe("polish_moonstone", "월석 다듬기", "jewelcraft", 12, "moonstone", {"rough": (I("raw_gem"), 2, 2), "silver": (I("silver_ingot"), 1)})
recipe("press_amber", "호박 굳히기", "jewelcraft", 4, "amber", {"sap": (T("sap"), 1), "resin": (I("honey_comb"), 2)})
for out, lv, slots in [
    ("copper_ring", 1, {"band": (I("copper_ingot"), 1, 2)}),
    ("ruby_ring", 9, {"band": (I("gold_ingot"), 1, 2), "gem": (I("ruby"), 1, 3)}),
    ("sapphire_ring", 9, {"band": (I("gold_ingot"), 1, 2), "gem": (I("sapphire"), 1, 3)}),
    ("emerald_ring", 10, {"band": (I("gold_ingot"), 1, 2), "gem": (I("emerald_gem"), 1, 3)}),
    ("moonstone_ring", 13, {"band": (I("silver_ingot"), 1, 2), "gem": (I("moonstone"), 1, 3)}),
    ("star_signet", 26, {"band": (I("gold_ingot"), 2, 2), "gem": (I("star_shard"), 1, 4), "inlay": (I("mithril"), 1)}),
    ("pearl_necklace", 12, {"chain": (I("gold_ingot"), 1, 2), "pearls": (I("pearl"), 3, 3)}),
    ("amber_pendant", 6, {"chain": (I("silver_ingot"), 1, 2), "gem": (I("amber"), 1, 3)}),
    ("ember_heart", 16, {"chain": (I("gold_ingot"), 1, 2), "gem": (I("ember_stone"), 2, 3), "essence": (I("fire_essence"), 1)}),
    ("frost_tear", 16, {"chain": (I("silver_ingot"), 1, 2), "gem": (I("frost_crystal"), 2, 3), "essence": (I("frost_essence"), 1)}),
    ("griffin_bracer", 18, {"band": (I("gold_ingot"), 1, 2), "feather": (I("griffin_feather"), 2, 3)}),
    ("mana_orb", 14, {"dust": (I("mana_dust"), 3, 3), "glass": (T("glass"), 1)}),
    ("storm_orb", 20, {"glass": (I("storm_glass"), 2, 3), "essence": (I("storm_essence"), 1)}),
    ("lucky_feather", 8, {"feather": (I("griffin_feather"), 1, 2), "gild": (I("gold_leaf"), 1)}),
]:
    recipe("jewel_" + out, items[out]["name"], "jewelcraft", lv, out, slots, tool=JEW if lv >= 5 else None, ticks=60 + lv * 3)
recipe("grind_jeweler_loupe", "보석 세공 돋보기", "jewelcraft", 3, "jeweler_loupe", {"lens": (T("glass"), 1, 2), "frame": (I("copper_ingot"), 1)})
recipe("grind_whetstone_fine", "고운 숫돌", "jewelcraft", 5, "whetstone_fine", {"stone": (I("obsidian_shard"), 1, 2), "sand": (I("glass_sand"), 1)}, count=2)
# 지도 제작
QUILL = "tool_quill"
recipe("press_parchment", "양피지 만들기", "cartography", 1, "parchment", {"hide": (T("hide"), 1)}, count=3, xp=4)
recipe("draw_region_map", "지역 지도", "cartography", 3, "region_map", {"paper": (I("parchment"), 2, 2), "ink": (T("ink"), 1)}, tool=QUILL)
recipe("draw_treasure_map", "보물 지도", "cartography", 15, "treasure_map", {"paper": (I("parchment"), 3, 2), "ink": (I("fine_ink"), 2), "gild": (I("gold_leaf"), 1)}, tool=QUILL, discovery="required")
recipe("draw_star_chart", "별자리 해도", "cartography", 20, "star_chart", {"paper": (I("parchment"), 3, 2), "ink": (I("fine_ink"), 2), "star": (I("star_shard"), 1)}, tool=QUILL)
recipe("scribe_return_scroll", "귀환 두루마리", "cartography", 10, "scroll_of_return", {"paper": (I("parchment"), 1, 2), "ink": (I("fine_ink"), 1), "dust": (I("mana_dust"), 1)}, tool=QUILL)
recipe("assemble_compass", "길잡이 나침반", "cartography", 12, "compass_of_ways", {"needle": (I("iron_ingot"), 1, 2), "stone": (I("moonstone"), 1), "case": (I("copper_ingot"), 1)}, tool=QUILL)
# 기계 공학 (용광로)
WR = "tool_wrench"
recipe("forge_engineer_wrench", "기계공 렌치", "engineering", 1, "engineer_wrench", {"metal": (I("iron_ingot"), 2, 2)}, xp=6)
recipe("wind_spring_coil", "태엽 용수철", "engineering", 4, "spring_coil", {"metal": (I("iron_ingot"), 1, 2)}, tool=WR, count=2)
recipe("build_clockwork_core", "태엽 심장", "engineering", 18, "clockwork_core", {"gears": (I("old_gear"), 4, 2), "springs": (I("spring_coil"), 3, 2), "gem": (T("cut_gem"), 1)}, tool=WR)
recipe("build_clockwork_hammer", "태엽 해머", "engineering", 15, "clockwork_hammer", {"head": (I("iron_ingot"), 4, 3), "core": (I("spring_coil"), 2), "haft": (T("wood"), 1)}, tool=WR)
recipe("build_clockwork_crossbow", "태엽 석궁", "engineering", 18, "clockwork_crossbow", {"frame": (T("wood"), 2, 2), "springs": (I("spring_coil"), 3, 2), "core": (I("clockwork_core"), 1, 0, 80)}, tool=WR)
recipe("pack_fire_bomb", "화염 폭탄", "engineering", 8, "fire_bomb", {"powder": (I("sulfur"), 2, 2), "niter": (I("saltpeter"), 1), "shell": (T("glass"), 1)}, tool=WR, count=2)
recipe("pack_smoke_bomb", "연막탄", "engineering", 4, "smoke_bomb", {"powder": (I("saltpeter"), 1), "ash": (I("bone_dust"), 1)}, count=2)
recipe("pack_frost_bomb", "서리 폭탄", "engineering", 12, "frost_bomb", {"essence": (I("frost_essence"), 1, 2), "shell": (T("glass"), 1)}, tool=WR, count=2)
recipe("build_bear_trap", "곰 덫", "engineering", 6, "bear_trap", {"jaws": (I("iron_ingot"), 2, 2), "spring": (I("spring_coil"), 1)}, tool=WR)
recipe("build_repair_kit", "수리 도구", "engineering", 10, "repair_kit", {"metal": (T("metal"), 2), "oil": (I("lantern_oil"), 1), "whet": (T("whetstone"), 1)}, tool=WR)
recipe("blow_glass_pane", "유리판 불기", "engineering", 2, "glass_pane", {"sand": (I("glass_sand"), 2), "fuel": (T("fuel"), 1)}, count=4)
recipe("blow_storm_glass", "폭풍 유리 불기", "engineering", 14, "storm_glass", {"sand": (I("glass_sand"), 3, 2), "essence": (I("storm_essence"), 1, 0, 60), "fuel": (T("fuel"), 2)})
recipe("build_firework", "축포", "engineering", 6, "firework_rocket", {"powder": (I("saltpeter"), 1), "paper": (I("parchment"), 1), "color": (T("dye"), 1, 0, 40)}, count=3)
recipe("forge_crystal_alembic", "수정 증류기", "engineering", 16, "crystal_alembic", {"glass": (I("storm_glass"), 2, 3), "frame": (I("copper_ingot"), 2)}, tool=WR)
recipe("forge_silk_needle", "비단 바늘", "engineering", 8, "silk_needle", {"metal": (I("silver_ingot"), 1, 2)}, tool=WR)
recipe("forge_silver_rod", "은빛 낚싯대", "engineering", 10, "silver_rod", {"reel": (I("silver_ingot"), 1, 2), "pole": (I("birch_timber"), 2), "line": (I("spider_silk"), 1)}, tool=WR)
recipe("forge_master_carving_set", "명장의 조각 도구", "engineering", 20, "master_carving_set", {"blades": (I("mithril"), 2, 3), "grip": (I("dark_timber"), 1)}, tool=WR)
# 조각 (오리지널)
recipe("carve_candle", "초 빚기", "sculpting", 1, "candle_wax", {"wax": (T("wax"), 1)}, count=2, xp=4)
recipe("render_beeswax", "밀랍 녹이기", "cooking", 2, "beeswax", {"comb": (I("honey_comb"), 2)})

import balance_pass
balance_pass.finalize(REPO, items, prices, recipes, resources, SETS, BASE_PRICE, DESIGN)
dump({"items": items, "sets": SETS}, open(OUT + "/items.yml", "w"), allow_unicode=True, sort_keys=False, width=220, default_flow_style=None)
dump({"prices": prices}, open(OUT + "/market.yml", "w"), allow_unicode=True, sort_keys=False, width=220, default_flow_style=None)
dump({"recipes": recipes}, open(OUT + "/recipes.yml", "w"), allow_unicode=True, sort_keys=False, width=220, default_flow_style=None)
dump({"resources": resources}, open(OUT + "/resources.yml", "w"), allow_unicode=True, sort_keys=False, width=220, default_flow_style=None)
DISC = {
    "jewelcraft": {"name": "보석 세공", "category": "PRODUCTION", "hand": True, "source": "ORIGINAL"},
    "brewing": {"name": "양조", "category": "PRODUCTION", "hand": True, "source": "ORIGINAL"},
    "cartography": {"name": "지도 제작", "category": "PRODUCTION", "hand": True, "source": "ORIGINAL"},
    "engineering": {"name": "기계 공학", "category": "PRODUCTION", "hand": True, "source": "ORIGINAL"},
}
dump({"disciplines": DISC}, open(OUT + "/disciplines.yml", "w"), allow_unicode=True, sort_keys=False, width=220, default_flow_style=None)
print(len(items), "items", len(recipes), "recipes", len(resources), "resources")
