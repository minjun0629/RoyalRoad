"""regions.yml 생성기 — 나무위키 「로열 로드/지리」 목차 · 본문의 위치 관계를 좌표로 옮긴다.
사용: python3 tools/gen_regions.py  (src/main/resources/content/regions.yml 을 덮어쓴다)
좌표: x 동쪽(+) · z 남쪽(+). 아래 숫자는 16000 판 기준이고, 세계 world 는 출력할 때 SCALE(3.125)배 → 50000 × 50000 (-25000 ~ 25000).
베르사 대륙 ±6000 → ±18750. 다른 차원(versa_realms)은 늘리지 않는다.
"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "content", "regions.yml")
KEEP = {}   # 기존 id 의 세력 · 자원 (콘텐츠 참조 유지)
_keep_path = os.path.join(HERE, "regions_keep.json")
if os.path.exists(_keep_path):
    KEEP = json.load(open(_keep_path, encoding="utf-8"))

R = []   # (section, dict)
SECTION = [""]

def sec(title):
    SECTION[0] = title

SCALE = 50000 / 16000   # 세계 world 의 가로 · 세로 배율 (높이는 그대로)

def _lo(v):
    return int(round(v * SCALE))

def _hi(v):
    # 이웃 상자가 v + 1 에서 시작하면 늘린 뒤에도 틈 없이 맞닿도록
    return 25000 if v >= 8000 else int(round((v + 1) * SCALE)) - 1

def r(id, name, src, danger, box, prio, tags, purpose, parent=None, changed=None, y=(-64, 320), resources=None, factions=None, world="world"):
    x1, z1, x2, z2 = box
    if world == "world":
        x1, z1, x2, z2 = _lo(x1), _lo(z1), _hi(x2), _hi(z2)
    d = dict(id=id, name=name, source=src, danger=danger, min=[x1, y[0], z1], max=[x2, y[1], z2], priority=prio,
             parent=parent, tags=tags, purpose=purpose, changed=changed, world=world)
    k = KEEP.get(id, {})
    d["resources"] = resources if resources is not None else k.get("resources")
    d["factions"] = factions if factions is not None else k.get("factions")
    if src == "CANON" and not changed:
        raise SystemExit("CANON 지역에는 changed 필요: " + id)
    R.append((SECTION[0], d))

CITY = (40, 320)
SKY = (200, 320)

# ===================================================================== 북부 (아르펜 제국 · 옛 니플하임)
sec("북부 — 아르펜 제국 · 옛 니플하임 제국 (나무위키 §6)")
r("north_reach", "북부 (옛 니플하임 · 아르펜 땅)", "CANON", 2, (-5400, -5000, 5000, -3200), 0, ["highland", "forest"],
  "잿빛 겨울 뒤 다시 사람이 사는 서늘한 고지대. 목재 · 사냥 · 북부 탐험",
  changed="원작에서 세르비안의 구슬로 얼었다가 풀려 아르펜 왕국 → 제국이 된 북부가, 수백 년 뒤 잿빛 겨울로 다시 비었다가 개척민이 돌아오는 땅이 됨")
r("niflheim_wastes", "니플하임 빙원 폐허", "CANON", 5, (-5400, -6000, 5000, -5001), 5, ["frozen", "ruins"],
  "얼음에 묻힌 옛 제국 도시. 서리 수정 · 고대 생물 · 거대 보스 「서리 군주의 껍데기」",
  changed="니플하임 마지막 황제가 세르비안의 구슬로 얼린 북부의 북쪽 끝이 잿빛 겨울로 다시 빙원이 됨. 얼음 위로 첨탑 꼭대기만 보인다")
r("jigolas", "지골라스 (열화의 땅)", "CANON", 6, (1200, -6000, 2600, -5300), 15, ["volcano"],
  "대륙 극북부의 화산 금역. 지진 · 분화 · 극열 몬스터, 보석과 희귀 금속",
  changed="원작의 10대 금역. 대마법사 임벌의 마법진이 약해져 분화가 잦아지고, 얼지 않는 강이 빙원까지 흘러내림")
r("unfreezing_river", "얼지 않는 강", "CANON", 5, (1250, -5450, 2550, -5310), 18, ["river"],
  "지골라스에서 흘러내리는 따뜻한 강. 빙원 한가운데의 물길", parent="jigolas",
  changed="원작 지골라스의 강이 후대에 빙원 남쪽까지 길게 흘러내림")
r("inferno", "인페르노", "CANON", 6, (1600, -6000, 1900, -5800), 20, ["volcano"],
  "지골라스 북쪽의 작은 화산 분지", parent="jigolas", changed="원작 지골라스의 지명. 후대에는 용암 호수가 굳어 검은 평원이 됨")
r("imbel_circle", "임벌의 마법진", "CANON", 5, (1400, -5900, 1500, -5800), 22, ["landmark"],
  "화산 분화를 억누르던 대마법사 임벌의 마법진 터", parent="jigolas", changed="원작에서 지골라스의 마나를 다스리던 마법진이 후대에 반쯤 꺼져 돌 고리만 남음")
r("volcano_heart", "화산 심장", "CANON", 6, (1800, -5700, 2000, -5550), 22, ["dungeon_site"],
  "지골라스 깊은 곳 최상위 던전의 입구", parent="jigolas", changed="원작의 레벨 400~500대 던전. 입구가 분화로 반쯤 묻힘")
r("silent_tower", "영웅을 기다리는 고요한 탑", "CANON", 6, (2300, -5900, 2400, -5800), 22, ["landmark"],
  "생명을 받은 거대 조각 탑. 덩치 때문에 지골라스에 남은 필드 보스", parent="jigolas", changed="원작에서 생명이 부여된 탑이 후대에는 움직이지 않는 거대한 석상처럼 서 있음")
r("sendeim_valley", "센데임 계곡 (죽음의 계곡)", "CANON", 5, (-2600, -5000, -1600, -4300), 12, ["valley", "ruins"],
  "마지막 황제가 쓰러진 계곡. 무덤 돌 고리 · 언데드", parent="north_reach",
  changed="니플하임 마지막 황제가 최후를 맞은 계곡이, 얼음이 녹으며 깊게 패여 순례지 겸 위험 지대가 됨")
r("furghol_ruins", "푸르골 요새 터", "CANON", 4, (-5300, -4900, -4700, -4300), 12, ["ruins"],
  "북부 북서쪽 끝의 무너진 요새", parent="north_reach", changed="북서쪽 요새가 잿빛 겨울에 버려져 반쯤 얼음에 묻힘 (번역명 확인 필요)")
r("morata_free_city", "모라타 자유시", "CANON", 0, (-400, -4300, 400, -3700), 20, ["city", "artisan"],
  "장인과 예술가의 도시. 생산 길드 · 염료 · 대리석 거래, 북부 탐험 보급", parent="north_reach", y=CITY,
  changed="니플하임 말기에 왕비를 배출하고 아르펜 왕국의 중심이 된 도시가, 후대에는 어느 나라에도 속하지 않은 자유시가 됨. 옛 광장 조각상은 「풍화의 정원」")
r("mist_forest", "안개 협곡 숲", "CANON", 3, (-1500, -4300, -500, -3500), 10, ["canyon", "forest"],
  "모라타 서쪽의 안개 낀 협곡", parent="north_reach", changed="모라타 서쪽 협곡에 숲이 바닥까지 내려와 덮음 (번역명 확인 필요)")
r("dawn_city", "새벽의 도시", "CANON", 0, (1000, -4100, 1600, -3500), 20, ["city", "market"],
  "아르펜 제국이 세운 새 수도. 북부 교역 · 기록 보관", parent="north_reach", y=CITY,
  changed="아르펜 제국의 신도시 수도가, 제국이 사라진 뒤 북부 상인들의 자치 도시가 됨")
r("barna_port", "항구 바르나", "CANON", 0, (4400, -4500, 5000, -4000), 20, ["city", "coast"],
  "북부 동해안의 항구. 북해 어업 · 해상 교역", parent="north_reach", y=CITY,
  changed="아르펜 제국의 항구가 잿빛 겨울 뒤 얼지 않는 항구로 다시 열림")
r("bent_castle", "벤트 성", "CANON", 0, (-2400, -3900, -2000, -3500), 20, ["city", "fortress"],
  "북부 서쪽의 성", parent="north_reach", y=CITY, changed="아르펜 제국의 성이 후대에 개척민의 보급 성이 됨")
r("bargo_fortress", "바르고 성채", "CANON", 1, (2600, -3700, 3000, -3300), 20, ["fortress"],
  "북부 남동쪽을 지키는 성채", parent="north_reach", y=CITY, changed="아르펜 제국의 성채가 후대에 북부 관문 요새가 됨")
r("lavias", "천공도시 라비아스", "CANON", 3, (1300, -4700, 2100, -3900), 25, ["sky"],
  "북부 하늘에 떠 있는 조인족의 섬 도시", parent="north_reach", y=SKY,
  changed="원작에서 로자임 하늘에 있다가 아르펜 왕국으로 옮겨 온 하늘섬. 옮겨 오던 길에 떨어진 파편이 로자임의 추락 분화구가 됨")
r("light_tower", "빛의 탑", "CANON", 0, (1700, -3700, 1760, -3640), 25, ["landmark"], "새벽의 도시 남동쪽의 하얀 탑", parent="north_reach",
  changed="아르펜 제국의 랜드마크가 후대에는 등대처럼 길잡이 불을 밝힘")
r("freya_statue", "프레야 여신상", "CANON", 0, (500, -3650, 560, -3590), 25, ["landmark"], "모라타 동쪽 들판의 여신상", parent="north_reach",
  changed="아르펜 제국의 랜드마크가 풍화되었지만 여전히 순례자를 맞음")
r("morata_art_hall", "모라타 예술회관", "CANON", 0, (-560, -3700, -480, -3620), 25, ["landmark"], "모라타 서쪽의 예술회관", parent="north_reach",
  changed="아르펜 제국의 예술회관이 후대에는 장인 동맹의 전시관이 됨")
r("garden_of_gods", "신들의 정원", "CANON", 0, (-200, -3620, 200, -3450), 25, ["landmark"], "모라타 남쪽의 정원과 샘", parent="north_reach",
  changed="아르펜 제국의 정원이 야생화 들판으로 남음")
r("alcazar_bridge", "알카사르의 다리", "CANON", 0, (600, -4000, 800, -3960), 25, ["landmark"], "모라타와 새벽의 도시 사이의 큰 다리", parent="north_reach",
  changed="아르펜 제국의 다리가 강이 마른 뒤에도 아치로 남음")
r("northern_sea", "북동쪽 바다", "SOURCE-BASED", 3, (5001, -6000, 5850, -1501), 1, ["sea"], "북부 동해안 밖의 차가운 바다")

# ===================================================================== 중부
sec("중부 — 하벤 · 칼라모르 · 톨렌 · 라살 · 브리튼 · 아이데른 · 리튼 · 토르 · 그라디안 · 기타 (나무위키 §2)")
r("central_plains", "중앙 대륙", "CANON", 1, (-4400, -3199, 4850, 3700), 0, ["plains"],
  "왕국들이 흥하고 망한 대륙의 중심. 인구 · 경제력이 가장 큰 땅",
  changed="하벤 · 아르펜 두 제국의 전쟁 뒤 중앙 대륙은 왕국 대신 도시 · 연합 · 폐허로 나뉨")
# --- 토르 왕국 (울타 · 사이고른 · 노른 산맥)
r("thor_kingdom", "토르 왕국 (드워프 연합)", "CANON", 2, (-1900, -3199, 3199, -2401), 4, ["mountain"],
  "세 산맥에 2,300여 마을이 흩어진 드워프의 나라. 금속 · 보석 · 대장 기술", parent="central_plains",
  changed="드래곤 일곱에게 공물을 바치던 드워프 왕국이, 후대에 공물 계약이 끝나고 광산 도시 연합 「망치 맹약」이 됨")
r("ulta_range", "울타 산맥", "CANON", 3, (-1900, -3199, -200, -2401), 8, ["mountain"], "토르 서쪽 산맥", parent="thor_kingdom",
  changed="원작 토르의 산맥. 깊은 곳 마을들은 지금도 바깥과 거의 왕래가 없다")
r("saigorn_range", "사이고른 산맥", "CANON", 3, (-199, -3199, 1400, -2600), 8, ["mountain"], "토르 가운데 산맥", parent="thor_kingdom",
  changed="원작 토르의 산맥. 남쪽 기슭(우고트)이 후대 광산 개발로 깎임")
r("norn_range", "노른 산맥", "CANON", 3, (1401, -3199, 3199, -2401), 8, ["mountain"], "토르 동쪽 산맥", parent="thor_kingdom",
  changed="원작 토르의 산맥. 드워프 회합 마을 그루터기가 있다")
r("ugot", "우고트", "CANON", 4, (-199, -2599, 1400, -2401), 9, ["highland"], "사이고른 산맥 남쪽, 드워프 전사들이 몬스터를 막는 던전 지대",
  parent="thor_kingdom", changed="원작에서 던전이 많이 발견되던 곳. 후대에도 국경 수비대가 지킨다")
r("thor_deep_hammer", "아이언 핸드 (깊은 망치)", "CANON", 1, (300, -3100, 700, -2700), 20, ["mountain", "city", "deep"],
  "뛰어난 대장장이 마을. 계단식 광산 도시와 대장 기술의 중심", parent="saigorn_range",
  changed="원작에서 대장장이가 많기로 유명한 마을 아이언 핸드가, 산 하나를 통째로 파 들어간 광산 도시 「깊은 망치」로 커짐")
r("kurso", "쿠르소 (지하 도시)", "CANON", 2, (800, -3000, 1200, -2700), 15, ["underground", "deep"], "지하 호수와 어우러진 고대 드워프 도시",
  parent="saigorn_range", y=(-64, 30), changed="네인핸드 마을 폐광 아래의 지하 도시. 후대에 지하 호수가 넓어짐")
r("shaspen_cave", "샤스펜 동굴", "CANON", 4, (1250, -2900, 1310, -2840), 22, ["dungeon_site"], "최고급 루비 광맥 동굴 입구", parent="saigorn_range",
  changed="원작에서 쿠르소 호수 너머의 동굴. 후대에 지상 입구가 새로 뚫림")
r("debrado_village", "데브라도 마을", "CANON", 1, (-1200, -3000, -1000, -2800), 20, ["city"], "울타 산맥 최심부의 비밀 마을", parent="ulta_range", y=CITY,
  changed="원작에서 드래곤 레어의 보물을 노리던 비밀 기지가, 후대에는 평범한 산골 마을이 됨")
r("stump_village", "그루터기 마을", "CANON", 0, (2200, -2900, 2400, -2700), 20, ["city"], "노른 산맥의 드워프 회합 마을", parent="norn_range", y=CITY,
  changed="원작의 회합 마을이 후대 망치 맹약의 의회가 열리는 곳이 됨")
# --- 아골디아 (금역) — 중앙 대륙 깊숙한 산악 지대
r("agoldia", "아골디아 (백야의 땅)", "CANON", 6, (3200, -3199, 4850, -2401), 10, ["mountain"],
  "비가 내리지 않는 깊은 산악 금역. 비행 몬스터 · 키메라 · 루 교단의 성역", parent="central_plains",
  changed="원작의 10대 금역. 다크우드 마법사들이 풀어 놓은 키메라의 후손이 지금도 산다")
r("lu_sanctum", "루의 성역", "CANON", 5, (3900, -2900, 4100, -2700), 22, ["landmark"], "아골디아 한가운데 루 교단의 성역", parent="agoldia",
  changed="원작 루 교단의 성역이 후대에 순례자만 찾는 흰 첨탑으로 남음")
r("dwarf_vault", "드워프들의 특급 창고", "CANON", 5, (3500, -2700, 3560, -2640), 22, ["dungeon_site"], "아골디아 산속 드워프 보물 창고 입구", parent="agoldia",
  changed="원작의 드워프 창고. 후대 망치 맹약이 되찾으려 하는 곳")
# --- 바쿠바 · 브레멘(서부 변경) · 잃어버린 길의 황무지
r("bakuba_kingdom", "바쿠바 왕국", "CANON", 2, (-3600, -3199, -1901, -2401), 4, ["plains"], "중앙 대륙 북서쪽의 작은 왕국 땅", parent="central_plains",
  changed="원작에 이름만 나온 왕국. 후대에는 목축 마을들이 흩어진 들판 (위치는 이 게임의 설정)")
r("west_frontier", "서부 변경 (옛 브레멘 땅)", "SOURCE-BASED", 4, (-3600, -2400, -1901, -801), 4, ["forest", "frontier"],
  "칼라모르 북쪽 · 하벤 서쪽의 옛 왕국 땅이 숲으로 덮인 미개척지", parent="central_plains")
r("risvel_outpost", "개척지 리스벨", "ORIGINAL", 1, (-3200, -1800, -2800, -1400), 20, ["outpost"], "서부 변경의 목책 마을. 개척 의뢰와 보급",
  parent="west_frontier", y=CITY)
r("lost_road_badlands", "잃어버린 길의 황무지", "CANON", 5, (-4400, -3199, -3601, 800), 6, ["badlands"],
  "중앙 대륙과 서부 사이의 거대한 미로 황무지. 길을 모르면 끝없이 헤맨다", parent="central_plains",
  changed="원작의 10대 금역. 후대에 바람이 협곡을 더 깎아 미로가 깊어짐")
# --- 칼라모르
r("kallamore_lands", "칼라모르 옛 영토", "CANON", 2, (-3600, -800, -1901, 1500), 4, ["plains"],
  "기사도의 왕국 땅. 서쪽은 바다, 북서쪽에 몬스터 소굴 센바인 산맥", parent="central_plains",
  changed="하벤에 멸망한 칼라모르 땅이 후대에 서해안 침식으로 바다가 안쪽으로 들어옴")
r("senbain_range", "센바인 산맥", "CANON", 4, (-3550, -780, -2700, -300), 10, ["mountain"], "칼라모르의 핵심 몬스터 소굴", parent="kallamore_lands",
  changed="원작에서 몬스터 대군이 내려오던 산맥. 후대에도 산 아래 평야에 감시탑이 늘어서 있다")
r("calamor_ruins", "칼라모르 폐도", "CANON", 3, (-3400, 0, -2700, 700), 10, ["ruins"],
  "무너진 왕도의 발굴 현장. 역사 기록 발견 · 고고학 의뢰 · 거대 보스 「무너진 파수거상」", parent="kallamore_lands",
  changed="하벤 제국에 멸망한 왕국의 도읍이, 대분열 때 다시 전쟁터가 되어 완전한 폐허가 됨. 새벽 기록관이 발굴 중")
r("evaruk_castle", "에바루크 성", "CANON", 1, (-2600, 800, -2300, 1100), 20, ["city", "fortress"], "칼라모르 남동쪽의 성", parent="kallamore_lands", y=CITY,
  changed="원작 칼라모르의 성이 왕국이 망한 뒤 기사 수도회의 성이 됨")
r("orun_fortress", "오룬 요새", "CANON", 2, (-3350, 1050, -3050, 1350), 20, ["fortress"], "칼라모르 서해안의 요새", parent="kallamore_lands", y=CITY,
  changed="원작 칼라모르의 요새가 해안 침식으로 바다 절벽 위에 서게 됨")
# --- 그라디안 (최서부) · 네스트 · 노튼 · 루비둠
r("gradian_lands", "그라디안 왕국 옛 땅", "CANON", 3, (-4800, 1501, -3301, 3700), 4, ["highland", "forest"],
  "중앙 대륙 최서부. 바다처럼 펼쳐진 산과 숲, 엘프와 이종족", parent="central_plains",
  changed="블랙소드 용병단과 하벤 제국에 차례로 정복된 왕국이, 후대에는 엘프 숲 부족들의 땅으로 돌아감")
r("bergen_castle", "버겐 성", "CANON", 1, (-4300, 2600, -3900, 3000), 20, ["city", "fortress"], "그라디안의 옛 수도", parent="gradian_lands", y=CITY,
  changed="그라디안 수도가 후대에 숲 부족들의 회의 도시가 됨")
r("nod_grape", "노드 그라페", "CANON", 2, (-4250, 2200, -4000, 2450), 20, ["fortress"], "버겐 성 북쪽 절벽 위의 요새 (성벽 40미터)", parent="gradian_lands", y=CITY,
  changed="하벤 총독부였던 절벽 요새가 후대에 버려져 반쯤 무너짐")
r("ruvidum_range", "루비둠 산맥", "CANON", 3, (-3300, 2401, -3001, 3700), 8, ["mountain"], "그라디안과 노튼 사이의 거대한 산맥", parent="central_plains",
  changed="원작에서 전투가 벌어진 산맥. 후대에는 고갯길 하나만 남음")
r("haisaa_peak", "하이사아 봉우리", "CANON", 3, (-3200, 3000, -3100, 3100), 15, ["mountain"], "루비둠 산맥의 높은 봉우리", parent="ruvidum_range",
  changed="원작에서 군단이 배치된 봉우리에 후대 망루가 남음")
r("nest_kingdom", "네스트 왕국", "CANON", 2, (-3000, 1501, -1901, 2400), 4, ["plains"], "그라디안 근처의 왕국 땅", parent="central_plains",
  changed="그라디안 근처의 왕국이 후대에 곡창 마을 연합이 됨 (위치는 이 게임의 설정)")
r("serven_granary", "세르벤 곡창", "ORIGINAL", 1, (-2900, 1600, -2000, 2300), 10, ["farmland", "plains", "river"],
  "대륙의 식량 창고. 요리 재료와 초보 사냥터", parent="nest_kingdom")
r("norton_kingdom", "노튼 왕국", "CANON", 2, (-3000, 2401, -1901, 3700), 4, ["plains"], "루비둠 산맥 동쪽의 왕국 땅", parent="central_plains",
  changed="원작에 이름만 나온 왕국. 후대에는 호수 주변 어촌과 들판")
r("benzen_plains", "벤젠 평원", "CANON", 2, (-3000, 2401, -2500, 3200), 6, ["plains"], "루비둠 산맥 앞의 대평원", parent="norton_kingdom",
  changed="원작에서 연합군이 대패한 평원. 후대에 전사자 비석이 줄지어 있다")
r("tochen_lake", "토첸 호수", "CANON", 1, (-2300, 3000, -2000, 3300), 12, ["lake"], "노튼의 호수", parent="norton_kingdom",
  changed="원작의 매복 장소였던 호수가 후대에 어촌의 호수가 됨")
# --- 하벤
r("haven_lands", "하벤 왕국 옛 영토", "CANON", 1, (-1900, -1500, 400, 1500), 4, ["plains"],
  "넓고 비옥한 영토와 광물 산지를 가진 전통의 강국 땅", parent="central_plains",
  changed="하벤 제국이 가르나프 평원 전투로 무너진 뒤, 옛 수도권은 국경 없는 교역 지대가 됨")
r("harden", "하르덴 교역시", "ORIGINAL", 0, (-1300, -300, -700, 300), 20, ["city", "market"],
  "황제의 길 세 갈래가 만나는 옛 역참이 커진 대륙 최대 시장", parent="haven_lands", y=CITY)
r("aren_castle", "아렌 성", "CANON", 0, (-1700, -1300, -1300, -900), 20, ["city", "fortress"], "하벤의 옛 수도. 투쟁의 길 수련관이 있던 곳", parent="haven_lands", y=CITY,
  changed="케이베른에게 파괴된 수도를 후대에 성벽만 다시 쌓아 기록 도시로 씀")
r("struggle_road", "투쟁의 길 (고급 수련관)", "CANON", 3, (-1260, -1260, -1160, -1160), 25, ["landmark"], "아렌 성 밖, 투신 바탈리 교단의 수련 길 입구",
  parent="haven_lands", changed="원작의 고급 수련관. 후대에는 도전자만 들어가는 돌문으로 남음")
r("octon", "옥턴 (학문의 도시)", "CANON", 0, (-500, 600, -100, 1000), 20, ["city", "scholar"], "지도 제작 · 식물학 · 몬스터 · 역사를 배우는 도시", parent="haven_lands", y=CITY,
  changed="모험가만 찾던 학문의 도시가 후대에 지도 제작 길드의 본부가 됨")
r("valkis_castle", "발키스 성", "CANON", 2, (0, -1000, 300, -700), 20, ["fortress"], "하벤의 핵심 성 중 하나", parent="haven_lands", y=CITY,
  changed="바르칸과의 전투로 초토화된 성을 후대에 요새로 다시 지음")
r("pirta_castle", "피르타 성", "CANON", 1, (-1850, 850, -1500, 1200), 15, ["mountain"], "산꼭대기에 지어진 아름다운 고성", parent="haven_lands",
  changed="산 위 고성이 후대에 관광 순례지가 됨")
r("agolta_region", "아골타 지역", "CANON", 2, (-1900, -200, -1350, 600), 8, ["highland"], "하벤 서쪽의 광물 산지", parent="haven_lands",
  changed="하벤의 광물 산지가 후대에 노천 광산이 됨")
r("anaboress_village", "아나보레스 마을", "CANON", 0, (-850, 800, -650, 1000), 20, ["city"], "하벤 남쪽의 마을", parent="haven_lands", y=CITY,
  changed="하벤의 마을이 후대에 하르덴의 배후 농촌이 됨")
r("frig_region", "프리그 지역", "CANON", 3, (-300, -1500, 400, -1100), 8, ["ruins"], "초토화된 옛 성 지역", parent="haven_lands",
  changed="반헤르메스 성주가 있던 곳이 초토화된 뒤 후대까지 폐허로 남음")
r("garnaf_plains", "가르나프 평원", "CANON", 2, (-1900, -1500, -350, -1320), 6, ["plains"], "아르펜과 하벤의 결전이 벌어진 평원", parent="haven_lands",
  changed="대륙의 운명을 가른 전장. 후대에 전쟁 기념비가 선다")
r("jubelin_dungeon", "쥬벨린 던전", "CANON", 3, (-1100, 1200, -1040, 1260), 22, ["dungeon_site"], "하벤 남쪽 던전 입구", parent="haven_lands",
  changed="원작 하벤의 던전. 후대에 입구가 무너져 새 길이 뚫림")
r("emperor_aqueduct", "황제의 수로", "ORIGINAL", 3, (-2400, -1500, 1000, 3500), 12, ["underground", "ruins", "deep"],
  "통일 제국이 만든 지하 물길 · 창고 · 피난처. 하르덴 · 옥턴 · 아이데른 아래를 잇는다", y=(-64, 39))
# --- 톨렌
r("tolen_lands", "톨렌 왕국 옛 영토", "CANON", 2, (-1900, -2400, 400, -1501), 4, ["plains", "forest"],
  "숲 · 평원 · 산악 지대의 왕국 땅. 흑마법 연구의 흔적", parent="central_plains",
  changed="하벤 제국에 멸망하고 베덴 길드 자치령이었던 땅이, 후대에는 폐허 마을과 광산만 남음")
r("haineff_mountains", "하이네프 산악지대", "CANON", 4, (-1900, -2400, -1100, -1600), 8, ["mountain"], "톨렌의 산악지대", parent="tolen_lands",
  changed="원작의 산악지대. 후대에도 도적과 마수의 소굴")
r("treypeak_fortress", "트레이피크 요새", "CANON", 2, (-1500, -1750, -1250, -1520), 20, ["fortress"], "하이네프를 지키던 요새", parent="haineff_mountains", y=CITY,
  changed="6만 대군이 주둔하던 요새가 후대에는 국경 초소가 됨")
r("complete_citadel", "완전한 성채", "CANON", 4, (-1850, -2350, -1600, -2100), 15, ["ruins"], "반쯤 짓다 버려진 산악 성채, 도둑의 소굴", parent="haineff_mountains",
  changed="원작에서 도둑의 소굴이던 미완성 성채가 후대에 더 무너짐")
r("portmos_castle", "포트모스 성", "CANON", 1, (-400, -2200, -100, -1900), 20, ["city", "fortress"], "톨렌의 성", parent="tolen_lands", y=CITY,
  changed="톨렌의 성이 후대에 광산 상인들의 성이 됨")
r("rakone_fortress", "라코느 요새", "CANON", 2, (450, -1650, 700, -1420), 20, ["fortress"], "톨렌과 라살 경계의 요새", parent="tolen_lands", y=CITY,
  changed="흑사자 길드가 지은 강한 요새가 후대에 국경 세관이 됨")
r("melbourne_mine", "멜버른 광산", "CANON", 3, (-1300, -2000, -1240, -1940), 22, ["dungeon_site"], "대륙 최고의 철광산 · 사파이어", parent="haineff_mountains",
  changed="흑사자 길드 독점이던 광산이 후대에 망치 맹약이 다시 엶")
r("belkain_lair", "벨카인의 은신처", "CANON", 5, (-1700, -1900, -1640, -1840), 22, ["dungeon_site"], "지옥 마수 벨카인이 새끼를 키우던 굴", parent="haineff_mountains",
  changed="원작의 은신처가 후대에도 봉인되지 않은 채 남음")
r("goldmine_dungeon", "골드마인 던전", "CANON", 3, (-900, -2300, -840, -2240), 22, ["dungeon_site"], "대륙 최고의 황금 광산", parent="tolen_lands",
  changed="원작의 금광이 후대에 거의 바닥나 몬스터 굴이 됨")
r("atarog_cave", "아타로그 마굴", "CANON", 4, (-200, -1800, -140, -1740), 22, ["dungeon_site"], "톨렌 왕국의 흑마법 연구소, 악령의 굴", parent="tolen_lands",
  changed="왕국이 망한 뒤 악령이 된 경비병들이 지금도 떠돈다")
r("rotten_foam_swamp", "썩은 거품의 늪", "CANON", 4, (-100, -2400, 400, -2000), 10, ["swamp"], "흑마법 생명체가 사는 늪", parent="tolen_lands",
  changed="원작의 늪이 후대에 조금 맑아졌지만 여전히 위험")
# --- 라살 · 마센 · 리튼 · 하르판
r("rasal_kingdom", "라살 왕국 옛 영토", "CANON", 2, (401, -1500, 1600, -300), 4, ["plains", "forest"], "중앙 대륙의 소왕국 땅", parent="central_plains",
  changed="하벤에 순식간에 정복된 소왕국 땅이 후대에 숲 마을들이 됨")
r("averian_forest", "아베리안 숲", "CANON", 6, (900, -1300, 1500, -700), 10, ["forest"], "라살의 금역. 울창한 숲과 미궁 조드", parent="rasal_kingdom",
  changed="원작의 10대 금역. 악룡 케이베른 연구의 흔적이 지금도 숲을 뒤틀고 있다")
r("maze_zod", "미궁 조드", "CANON", 6, (1150, -1050, 1210, -990), 22, ["dungeon_site"], "아베리안 숲의 미궁 입구", parent="averian_forest",
  changed="하프엘프 비슈르가 사라진 미궁. 후대에도 입구만 알려짐")
r("suakun_fortress", "수어쿤 요새", "CANON", 2, (450, -650, 700, -400), 20, ["fortress"], "공략이 어렵기로 소문난 요새", parent="rasal_kingdom", y=CITY,
  changed="라살의 요새가 후대에 숲 순찰대의 본부가 됨")
r("elnavis_plains", "엘나비스 평원", "CANON", 1, (401, -1500, 850, -800), 6, ["plains"], "라살 서쪽의 평원", parent="rasal_kingdom",
  changed="원작의 평원이 후대에 목초지가 됨")
r("massen_kingdom", "마센 왕국", "CANON", 2, (401, -2400, 1600, -1501), 4, ["plains"], "중앙 대륙 북쪽의 왕국 땅", parent="central_plains",
  changed="원작에 이름만 나온 왕국 (위치는 이 게임의 설정). 오래된 궁전과 미궁이 남음")
r("roderick_labyrinth", "로드릭 미궁", "CANON", 6, (900, -2200, 1000, -2100), 22, ["dungeon_site"],
  "중앙 대륙 약간 북쪽, 옛 궁전 정원의 부서진 동상 아래 지하 계단 — 8대 미궁", parent="massen_kingdom",
  changed="몰락한 왕국의 별궁을 대마법사 로드릭이 지하로 옮긴 미궁. 후대에도 공간 왜곡이 풀리지 않음")
r("litten_kingdom", "리튼 왕국", "CANON", 1, (401, -299, 1600, 1500), 4, ["plains"], "하벤 동쪽 · 브리튼 서쪽의 왕국 땅", parent="central_plains",
  changed="원작에 이름만 나온 왕국 (위치는 이 게임의 설정). 후대에는 교역로 마을")
r("harpan_kingdom", "하르판 왕국", "CANON", 2, (1600, -2400, 3199, -1501), 4, ["plains"], "브리튼 북쪽 · 토르 남쪽의 왕국 땅", parent="central_plains",
  changed="원작에 이름만 나온 왕국 (위치는 이 게임의 설정)")
# --- 브리튼 연합 (중앙 동쪽, 루가 강)
r("britten_alliance", "브리튼 연합 왕국 (루가 강)", "CANON", 1, (1600, -1500, 3199, 2500), 4, ["plains", "river"],
  "중앙 동쪽 일곱 소국의 연합. 동부와 중앙을 잇는 교통 · 교역 중심", parent="central_plains",
  changed="하벤에 흡수됐던 연합이 후대에 루가 강의 자유 도시 동맹으로 되살아남")
r("sisley_castle", "시슬레 성", "CANON", 0, (2250, -350, 2550, -50), 20, ["city", "fortress"], "루가 강 옆의 군사 · 교통 요충지", parent="britten_alliance", y=CITY,
  changed="하루 만에 함락됐던 성이 후대에는 강 항구를 낀 상업 성이 됨")
r("moros_castle", "모로스 성", "CANON", 0, (1900, 500, 2250, 850), 20, ["city", "market"], "가구 · 벨벳 · 향료 거래의 상업 도시", parent="britten_alliance", y=CITY,
  changed="원작의 상업 도시(옛 헬튼 성)가 후대에도 향료 시장으로 이름남")
r("freidal_castle", "프레이달 성", "CANON", 0, (1700, 950, 1950, 1200), 20, ["city"], "모로스 성 가까이의 성", parent="britten_alliance", y=CITY,
  changed="모로스 옆 성이 후대에 직조 공방 거리가 됨")
r("edgar_castle", "에드가 성", "CANON", 0, (2700, -1300, 3000, -1000), 20, ["city", "fortress"], "브리튼 북동쪽의 성", parent="britten_alliance", y=CITY,
  changed="원작의 성이 후대에 사냥꾼 길드의 거점이 됨")
r("rondis_mountain", "론디스 산", "CANON", 3, (2700, -950, 3100, -650), 10, ["mountain"], "에드가 성 근처 붉은 늑대의 산", parent="britten_alliance",
  changed="붉은 늑대 서식지가 후대에도 이어짐")
r("bermer", "베르메르", "CANON", 0, (1650, -1100, 1900, -850), 20, ["city"], "브리튼 북서쪽 도시", parent="britten_alliance", y=CITY,
  changed="구원군이 출발했던 도시가 후대에 작은 성곽 도시로 남음")
r("bermer_mountain", "베르메르 산", "CANON", 2, (1650, -800, 1950, -500), 10, ["mountain"], "베르메르 남쪽의 산", parent="britten_alliance",
  changed="원작의 산이 후대에 채석장이 됨")
r("somren_free_city", "소므렌 자유도시", "CANON", 0, (2600, 1300, 3000, 1700), 20, ["city", "scholar"], "프레야 교단 본거지였던 자유도시", parent="britten_alliance", y=CITY,
  changed="케이베른에게 파괴됐던 자유도시가 순례 · 치유의 도시로 다시 섬")
r("antarosa_ruins", "안타로사 폐허", "CANON", 4, (1700, 1500, 2400, 2200), 10, ["ruins"], "옛 아르펜 제국 수도의 폐허. 지하도와 던전, 고대 서적 발굴지",
  parent="britten_alliance", changed="원작에서 이미 폐허였던 옛 수도가 후대에 발굴 도시와 몬스터 굴이 함께 있는 곳이 됨")
r("water_pit_south", "물구덩이 남쪽", "CANON", 4, (1900, 2000, 2100, 2150), 18, ["ruins"], "안타로사 남쪽 웅덩이 근처의 유적", parent="antarosa_ruins",
  changed="원작 안타로사의 지명")
r("hidram_manor", "히드람 백작 저택", "CANON", 4, (2000, 2060, 2060, 2120), 24, ["dungeon_site"], "물구덩이 남쪽의 저택 유적 입구", parent="water_pit_south",
  changed="원작에서 수색되던 저택 유적")
r("yavolis_fortress", "야볼리스 군사 요새", "CANON", 2, (2800, 2000, 3100, 2300), 20, ["fortress"], "브리튼 남동쪽 군사 요새", parent="britten_alliance", y=CITY,
  changed="포위를 버텼던 요새가 후대에 보급 기지가 됨")
r("odein_fortress", "오데인 요새", "CANON", 1, (1500, 1900, 1750, 2150), 20, ["fortress"], "대륙 최대 요새 중 하나 (아이데른 · 브리튼 접경)", parent="britten_alliance", y=CITY,
  changed="대륙 최대 요새가 후대에는 국경 박물관 겸 수비대 본부가 됨")
r("odein_plains", "오데인 평원", "SOURCE-BASED", 2, (1600, 1600, 2000, 1880), 6, ["plains"], "오데인 요새 앞 들판 (게임판 지명)", parent="britten_alliance")
r("langbot_fortress", "랭봇 요새", "CANON", 2, (2950, -200, 3199, 100), 20, ["fortress"], "브리튼 동쪽 끝 요새", parent="britten_alliance", y=CITY,
  changed="4만 8천이 쓰러진 요새가 후대에 위령탑과 함께 다시 지어짐")
r("hepen_castle", "헤펜 성", "CANON", 0, (2400, 2250, 2650, 2480), 20, ["city"], "브리튼 남쪽의 성", parent="britten_alliance", y=CITY,
  changed="원작의 성이 후대에 남쪽 교역 관문이 됨")
r("drapukin_dungeon", "드라푸킨 던전", "CANON", 5, (2600, 600, 2660, 660), 22, ["dungeon_site"], "흑마법 도마뱀 드라킨의 던전 입구", parent="britten_alliance",
  changed="원작의 고레벨 던전")
r("basra_cave", "바스라 마굴", "SOURCE-BASED", 4, (2200, 1000, 2400, 1200), 14, ["underground", "deep"], "브리튼 땅 아래 몬스터 소굴 (게임판 지명)",
  parent="britten_alliance", y=(-64, 38))
# --- 아이데른 (남쪽 바다를 끼고)
r("aidern_lands", "아이데른 왕국 옛 영토", "CANON", 2, (-1900, 1501, 1599, 3700), 4, ["plains"], "남쪽 바다를 낀 왕국. 일스 대평원 곡창과 네 종족의 기원", parent="central_plains",
  changed="원작의 왕국이 후대에 곡창 도시 연합이 됨")
r("hillshade_castle", "힐쉐이드 성", "CANON", 0, (-400, 2200, 0, 2600), 20, ["city", "market"], "아이데른의 수도. 큰 상점과 사치품", parent="aidern_lands", y=CITY,
  changed="원작의 수도가 후대에도 사치품 시장으로 이름남")
r("ils_plains", "일스 대평원", "CANON", 1, (-1500, 1700, 500, 2900), 6, ["farmland", "plains"], "아이데른의 핵심 곡창지대", parent="aidern_lands",
  changed="원작의 곡창이 후대에 더 넓어짐")
r("pedra_wall", "페드라 성벽", "CANON", 2, (-1500, 2950, 500, 3000), 15, ["wall"], "일스 대평원을 지키려 오크와 드워프가 쌓은 방벽", parent="aidern_lands",
  changed="원작의 방벽이 후대에 곳곳이 무너졌지만 여전히 서 있다")
r("borniss_castle", "보르니스 성", "CANON", 0, (-1100, 1900, -850, 2150), 20, ["city"], "일스 대평원의 옛 성, 여행자의 숙소", parent="ils_plains", y=CITY,
  changed="원작에서 이미 여행자 숙소였던 고성")
r("olgor_plateau", "올고르 고원", "CANON", 2, (600, 1600, 1500, 2300), 8, ["highland"], "일스 대평원 동쪽의 고원", parent="aidern_lands",
  changed="원작의 고원. 뒤편에 옛 왕성이 있었다")
r("vanessa_flower_road", "바네사의 꽃길", "CANON", 0, (0, 1800, 400, 1850), 8, ["plains"], "나비 축제가 열리는 꽃길", parent="aidern_lands",
  changed="원작의 축제 꽃길이 후대에도 봄마다 축제를 연다")
r("tinus_river", "티너스 강", "CANON", 1, (600, 2400, 900, 3700), 12, ["river"], "아이데른 동쪽을 흐르는 강", parent="aidern_lands",
  changed="원작의 강이 후대에 남쪽 만으로 흘러듦")
r("torone_mountain", "토론 산", "CANON", 2, (550, 3020, 1000, 3400), 12, ["mountain"], "일스 대평원 동쪽의 험한 산", parent="aidern_lands",
  changed="거의 방치된 산이 후대에도 사람 발길이 드묾")
r("lachburg", "라체부르그", "CANON", 4, (1100, 3300, 1500, 3650), 12, ["ruins", "desert"], "네 종족이 함께 살았던 최초의 신화 도시, 모래에 묻힘", parent="aidern_lands",
  changed="모래에 깊이 묻힌 신화 도시가 후대에 일부 발굴됨")
r("lachburg_fishing", "라체부르그의 낚시터", "CANON", 1, (900, 3300, 1050, 3420), 14, ["lake"], "인간과 드워프가 물고기를 잡던 곳 (어획 증가)", parent="aidern_lands",
  changed="원작의 낚시터가 후대에도 이어짐")
r("mongbeltroria", "몽벨트로리아", "CANON", 5, (1050, 3450, 1110, 3510), 22, ["dungeon_site"], "네 종족이 흩어지기 전 모여 살던 동굴 (드워프 · 엘프 · 오크 · 인간 동굴)",
  parent="lachburg", changed="대륙에서 가장 오래된 종족의 기원. 후대에 신상 동굴은 성지가 됨")
r("vulcan_seal", "불칸의 봉인", "CANON", 6, (-1700, 3300, -1640, 3360), 22, ["dungeon_site"], "대악마를 가둔 봉인 던전 입구", parent="aidern_lands",
  changed="원작의 봉인이 후대에 조금씩 풀려 수호병이 바깥까지 나온다")
r("dwarf_storehouse", "드워프의 창고", "CANON", 4, (-200, 3400, -140, 3460), 22, ["dungeon_site"], "도굴꾼이 들끓는 창고 던전", parent="aidern_lands",
  changed="원작의 던전")
r("grapes", "그라페스 (괴수의 땅)", "CANON", 6, (1100, 2400, 1500, 2900), 10, ["forest"], "아이데른의 가장 좁은 금역. 수풀 속 괴수와 보석", parent="aidern_lands",
  changed="원작의 10대 금역. 다크 게이머의 집 세 채가 후대에 사냥꾼 쉼터가 됨")
r("karayak_habitat", "카라약의 서식지", "CANON", 6, (1250, 2600, 1310, 2660), 22, ["dungeon_site"], "그라페스의 괴수 서식지 입구", parent="grapes",
  changed="원작 그라페스의 지명")
r("bernert_castle", "베르네르트 성", "CANON", 1, (-900, 3300, -500, 3650), 20, ["city", "coast"], "남쪽 바다를 낀 성", parent="aidern_lands", y=CITY,
  changed="엠비뉴의 침공을 막아 낸 바닷가 성이 후대에 항구가 됨")
r("south_bay", "남쪽 만", "SOURCE-BASED", 2, (-1300, 3660, 100, 4100), 9, ["sea"], "베르네르트 성 앞바다 (아이데른 해안)")

# ===================================================================== 서부
sec("서부 — 부족 국가 · 엠비뉴 총본영 (나무위키 §4 · §1.1)")
r("west_tribes", "서부 부족 지대", "CANON", 4, (-6000, -3199, -4401, 800), 3, ["highland"],
  "큰 왕국 없이 민족별 부족 국가가 선 땅. 바바리안 전사들", changed="원작의 서부 부족들이 후대에도 왕국을 세우지 않고 부족 연맹으로 남음")
r("withered_ruins", "메마른 울부짖는 폐허", "CANON", 6, (-5900, -2300, -5000, -1300), 8, ["badlands", "ruins"],
  "잃어버린 길 너머 숨은 땅. 갈라진 암반과 용암 연기", parent="west_tribes",
  changed="원작에서 엠비뉴 총본영을 감추던 땅. 교단이 사라진 뒤에도 몬스터가 변형된 채 남음")
r("unknown_wall", "알 수 없는 장벽", "CANON", 6, (-5050, -2300, -5000, -1300), 20, ["wall"], "엠비뉴와 세상을 나누던 장벽", parent="withered_ruins",
  changed="원작의 장벽. 신성력이 빠져 지금은 낡은 돌벽")
r("rotten_river", "시커멓게 썩은 강", "CANON", 6, (-5600, -2300, -5480, -1300), 20, ["river"], "시체가 떠다니던 독 안개의 강", parent="withered_ruins",
  changed="아우솔레토가 깨어난 뒤 조금 더러운 강으로 바뀜")
r("slave_bridge", "노예들이 지은 다리", "CANON", 5, (-5600, -1820, -5480, -1780), 23, ["landmark"], "썩은 강을 잇는 석조 다리", parent="withered_ruins",
  changed="7,600명이 희생된 다리. 후대에 위령비가 섬")
r("embinyu_sanctum", "엠비뉴의 성지 (거대한 구멍)", "CANON", 6, (-5450, -2150, -5150, -1850), 22, ["hole"],
  "엠비뉴 교단 총본영이 있던 끝을 알 수 없는 구멍", parent="withered_ruins",
  changed="혼돈의 드래곤 아우솔레토의 '영겁의 대침식'으로 총본영이 사라지고 거대한 구멍만 남음")
r("embinyu_quarry", "채석장", "CANON", 5, (-5900, -1500, -5700, -1350), 20, ["valley"], "하늘로 오르는 탑의 돌을 캐던 채석장", parent="withered_ruins",
  changed="노예들이 일하던 채석장이 후대에 버려진 골짜기가 됨")
r("sky_tower_ruins", "하늘로 오르는 탑 (무너진 밑동)", "CANON", 6, (-5320, -1720, -5220, -1620), 23, ["landmark"], "12km 높이로 짓던 탑의 무너진 밑동",
  parent="withered_ruins", changed="교단이 무너진 뒤 탑은 아래 몇 층만 남음")

# ===================================================================== 동부
sec("동부 — 로자임 · 브렌트 · 절망의 평원 · 오크랜드 · 대수림 (나무위키 §3 · §1.1)")
r("brent_highlands", "브렌트 왕국 옛 땅", "CANON", 2, (3200, -2400, 4850, 300), 4, ["highland", "forest"],
  "로자임 북쪽 왕국. 중앙 대륙에서 로자임으로 가는 유일한 길", parent="central_plains",
  changed="로자임과 앙숙이던 브렌트 왕국은 사라졌고, 고원 숲은 벌목 개척지가 됨")
r("nehales_bastion", "네할레스 성", "CANON", 0, (3900, -900, 4300, -500), 20, ["fortress"], "브렌트의 옛 수도", parent="brent_highlands", y=CITY,
  changed="브렌트 수도였던 성이 왕국이 사라진 뒤 교역로 요새로 바뀜")
r("mirror_lake", "거울의 호수", "CANON", 1, (4300, -1300, 4700, -1000), 12, ["lake"], "브렌트의 맑은 호수", parent="brent_highlands",
  changed="원작의 호수가 후대에도 하늘을 비춘다")
r("baroque_range", "바로크 산맥 (서쪽 줄기)", "CANON", 3, (3200, 301, 3500, 3700), 6, ["mountain"],
  "로자임 서쪽, 브리튼 연합과의 경계를 이루는 험한 산맥", parent="central_plains",
  changed="위드가 뚫었던 바로크 산맥에 후대 상인들이 고갯길 터널을 넓혀 씀")
r("baroque_north", "바로크 산맥 (북쪽 줄기)", "CANON", 3, (3200, 301, 4600, 550), 6, ["mountain"], "로자임 북쪽을 막는 산맥 줄기", parent="central_plains",
  changed="북쪽에서 서쪽으로 휘감는 산맥 — 로자임을 천혜의 요새로 만든 줄기")
r("rosenheim", "로자임 왕국 옛 땅", "CANON", 1, (3501, 551, 4850, 3700), 4, ["plains"],
  "모험가들이 처음 길을 나서던 땅. 세라보그 · 바란 · 조각사의 성지", parent="central_plains",
  changed="시오데른 · 윈스터 · 위드가 다스린 왕국은 사라지고 도시들의 땅이 됨. 라비아스 파편이 떨어져 북동쪽에 분화구가 생김")
r("serabourg", "세라보그 성", "CANON", 0, (4000, 1500, 4400, 1900), 20, ["city", "fortress"], "로자임의 옛 수도. 수련관 터", parent="rosenheim", y=CITY,
  changed="왕가가 끊긴 뒤 시민 의회가 다스리는 성벽 도시가 됨. 수련관은 박물관이 됨")
r("royal_underpass", "왕성의 지하도", "CANON", 4, (4150, 1650, 4250, 1750), 22, ["underground"], "세라보그 궁전 아래 비밀 통로 던전", parent="serabourg", y=(-64, 38),
  changed="왕족의 비상 통로가 몬스터 굴이 됨")
r("serabourg_north", "세라보그 성 북부", "SOURCE-BASED", 1, (3800, 1100, 4400, 1499), 8, ["plains"], "세라보그 북쪽 초보 사냥터 (게임판 지명)", parent="rosenheim")
r("star_palace", "별의 궁전", "CANON", 4, (4450, 1950, 4510, 2010), 22, ["dungeon_site"], "이베인 왕비가 살던 저주받은 궁전", parent="rosenheim",
  changed="원작의 던전이 후대에 저주가 약해져 반쯤 개방됨")
r("baran_village", "바란 마을", "CANON", 0, (4100, 2300, 4300, 2500), 20, ["city"], "세라보그 남쪽의 작은 마을", parent="rosenheim", y=CITY,
  changed="하늘섬이 떠난 뒤 남쪽 미개척지로 가는 쉼터 마을이 됨")
r("lavias_peak", "라비아스 산", "CANON", 2, (3850, 2100, 4050, 2280), 12, ["mountain"], "바란 북서쪽 산 — 하늘섬이 떠 있던 자리 아래", parent="rosenheim",
  changed="하늘섬이 북부로 떠난 뒤 산 이름만 남음 (번역명 확인 필요)")
r("ulken_mountain", "울켄 산", "CANON", 2, (3850, 2520, 4050, 2750), 12, ["mountain"], "바란 남서쪽의 낮은 산", parent="rosenheim",
  changed="후대에 계단식 약초밭이 됨 (번역명 확인 필요)")
r("quiet_plains", "고요한 평원", "SOURCE-BASED", 1, (3550, 2000, 3800, 2900), 8, ["plains"], "로자임 서쪽 풀밭 (게임판 지명)", parent="rosenheim")
r("howling_valley", "울부짖는 골짜기", "SOURCE-BASED", 3, (3550, 1200, 3800, 1600), 12, ["valley"], "바로크 산맥 기슭의 골짜기 (게임판 지명)", parent="rosenheim")
r("baroque_gate", "바로크 산맥 입구", "SOURCE-BASED", 2, (3550, 600, 3750, 800), 12, ["outpost"], "고갯길 입구의 숙영지 (게임판 지명)", parent="rosenheim", y=CITY)
r("hunters_hill", "사냥꾼의 언덕", "SOURCE-BASED", 2, (4400, 650, 4800, 1000), 8, ["highland"], "로자임 북동쪽 언덕 (게임판 지명)", parent="rosenheim")
r("fallen_crater", "추락 분화구", "ORIGINAL", 4, (4400, 1100, 4850, 1550), 10, ["sky", "crater"],
  "라비아스가 북부로 옮겨 갈 때 떨어진 파편의 둥근 호수와 섬 조각. 하늘 금속 산지", parent="rosenheim")
r("birch_lake", "자작나무 호수", "SOURCE-BASED", 1, (4500, 2600, 4800, 2850), 12, ["lake"], "자작나무 숲의 호수 (게임판 지명)", parent="rosenheim")
r("rosenheim_frontier", "로자임 남부 미개척지", "CANON", 3, (3501, 2951, 4850, 3700), 5, ["forest", "frontier"], "로자임 남쪽의 넓은 미개척지", parent="rosenheim",
  changed="원작에서 개척되지 않았던 남쪽이 후대에 바다까지 길이 뚫림")
r("sand_plains", "모래평원", "SOURCE-BASED", 2, (3600, 3300, 4000, 3550), 8, ["desert"], "미개척지의 모래 띠 (게임판 지명)", parent="rosenheim_frontier")
r("rosaim_harbor", "로자임 항", "CANON", 1, (4300, 3300, 4850, 3700), 15, ["city", "coast"],
  "어업 · 해상 교역 · 조선. 남쪽 바다 탐험의 출발점", parent="rosenheim_frontier", y=CITY,
  changed="원작의 로자임은 바다가 없었지만, 후대 개척민이 남부 미개척지를 지나 남동쪽 만에 항구 연합을 세움")
r("rosenheim_bay", "로자임 만", "SOURCE-BASED", 2, (4500, 3701, 5400, 4300), 9, ["sea"], "로자임 항 앞바다")
r("exile_wall", "추방의 장벽", "CANON", 3, (4851, -1500, 4900, 3700), 15, ["wall"], "로자임 · 브렌트 동쪽의 장벽 — 150년 전 죄수를 너머로 추방", parent="central_plains",
  changed="원작의 장벽이 후대에 군데군데 문이 뚫림")
r("plains_of_despair", "절망의 평원", "CANON", 5, (4901, -1500, 5400, 3700), 4, ["plains", "frontier"],
  "로자임과 유로키나 산맥 사이 거대 평원. 추방자의 후손 마을과 몬스터 — 10대 금역",
  changed="백만 오크가 살던 평원이 후대에 몇 부족과 추방자 후손 마을만 남음")
r("yunopu_canyon", "유노프 협곡", "CANON", 4, (4950, -1500, 5300, -900), 12, ["canyon"], "평원 북쪽, 쌍둥이 산이 문처럼 선 협곡", parent="plains_of_despair",
  changed="협곡 바닥이 무너져 더 깊어짐")
r("rotten_lich_dungeon", "썩은 리치 던전", "CANON", 6, (5100, 500, 5160, 560), 22, ["dungeon_site"], "리치 샤이어의 던전 입구", parent="plains_of_despair",
  changed="원작에서 리치 샤이어가 쓰러진 뒤에도 언데드가 남음")
r("yurokina_range", "유로키나 산맥", "CANON", 4, (5401, -1500, 5650, 800), 10, ["mountain"], "절망의 평원 동쪽 산맥. 엘프와 오크가 함께 리치를 막은 곳",
  changed="동쪽이 바다로 무너져 해안 절벽이 됨")
r("orc_land", "오크랜드", "CANON", 4, (5401, 801, 5650, 3700), 10, ["frontier"], "절망의 평원 너머 오크의 주 서식지",
  changed="원작의 오크 땅이 후대에 오크 부족 연맹의 교역 마을이 됨")
r("great_forest", "대수림", "CANON", 6, (5651, -1500, 5850, 3700), 10, ["forest", "frontier"], "오크랜드 너머, 아무도 끝까지 간 적 없는 숲",
  changed="원작에서 아무도 도달하지 못한 숲. 후대에도 끝을 본 사람이 없다")
r("east_coast_waters", "동해안 연안", "SOURCE-BASED", 2, (4851, -3199, 5850, 4300), 0, ["sea"], "브렌트 · 절망의 평원 바깥 연안의 얕은 바다")
r("eastern_sea", "동쪽 바다", "ORIGINAL", 3, (5851, -6000, 6000, 6000), 1, ["sea"], "동쪽 대륙(불의 고리)으로 가는 바다 · 해양 보스 「심해 갑각왕」")
r("wolhof_coral", "울호프 산호지대", "CANON", 2, (5860, -1000, 5995, 2000), 8, ["sea"], "대륙 최대의 산호지대 — 9대 비경", parent="eastern_sea",
  changed="원작에서 초토화될 뻔했다 시간여행으로 지켜진 산호지대가 후대에도 남음 (위치는 이 게임의 설정)")
r("abyss_trench", "심연 해구 (바다의 금역)", "SOURCE-BASED", 6, (5860, 4500, 5995, 5500), 15, ["sea"], "작가가 말한 마지막 금역 '바다'를 이 게임식으로 만든 해구", parent="eastern_sea")

# ===================================================================== 남부
sec("남부 — 공국 지대 · 사막 (나무위키 §5 · §1.1)")
r("southern_duchies", "남부 공국 지대", "CANON", 1, (-4800, 3701, 4499, 4300), 3, ["plains", "scholar"], "중앙과 사막 사이 마법 공국들의 완충 지대",
  changed="원작의 마법 공국들이 후대에 학술 연맹이 됨")
r("borges_duchy", "보르헤스 공국", "CANON", 0, (-1900, 3800, -1500, 4200), 20, ["city", "scholar"], "남부 공국 중 하나", parent="southern_duchies", y=CITY,
  changed="원작의 공국이 후대 학술 연맹의 의장국이 됨")
r("astra_academy", "아스트라 학술도시", "SOURCE-BASED", 0, (-1200, 3800, -400, 4600), 20, ["city", "scholar"], "공국 연맹이 세운 연구 도시. 연금 · 시약 · 유적 연구",
  parent="southern_duchies", y=CITY)
r("desert_of_tranquility", "고요의 사막 (열사의 땅)", "CANON", 6, (2400, 3701, 3500, 4500), 12, ["desert"], "마나가 폭주해 스킬이 막히는 금역 — 신들이 만든 오아시스",
  changed="원작의 10대 금역. 로자임 남서쪽 사막이 후대에도 생명 없는 땅")
r("sand_sea", "남부 사막 (모래바다)", "CANON", 3, (-4800, 4301, 5850, 5700), 2, ["desert"],
  "오아시스 부족들의 사막. 소금 · 유리 모래 · 사막 가죽, 모래폭풍과 보스 「사구 갈퀴웜」",
  changed="위드의 퀘스트로 크게 발전한 사막이 후대에도 대상단의 땅으로 남음")
r("agselia", "아그셀리아", "CANON", 0, (1500, 4600, 2100, 5100), 20, ["city", "desert", "market"], "사막 최고의 도시. 부호의 별장과 사막 전사", parent="sand_sea", y=CITY,
  changed="원작에서 크게 커진 사막 도시가 후대 대상단 연합의 수도가 됨")
r("chakmak", "차크마크", "CANON", 0, (-2500, 4800, -2000, 5200), 20, ["city", "desert"], "오아시스를 중심으로 자란 사막 도시", parent="sand_sea", y=CITY,
  changed="원작의 오아시스 도시")
r("problen", "프로블렌", "CANON", 0, (-500, 4350, 0, 4700), 20, ["city", "fortress", "desert"], "사막 가장자리의 성벽 도시 (해자 · 수로)", parent="sand_sea", y=CITY,
  changed="원작에서 수만 채로 발전한 도시가 후대에도 사막의 관문")
r("oldras", "올드라스", "CANON", 0, (-500, 5000, -150, 5300), 20, ["city", "desert"], "프로블렌 남쪽 도시", parent="sand_sea", y=CITY,
  changed="원작의 사막 도시")
r("azil_oasis", "아질 오아시스", "ORIGINAL", 0, (400, 5000, 1000, 5600), 20, ["city", "desert", "market"], "사막 한가운데 큰 샘에 대상단이 머물며 생긴 도시",
  parent="sand_sea", y=CITY)
r("nukod_oasis", "누코드 오아시스", "CANON", 1, (3000, 4700, 3300, 5000), 12, ["desert"], "사막 동쪽의 오아시스", parent="sand_sea",
  changed="원작의 오아시스")
r("romskute_lake", "로므스커테 호수", "CANON", 1, (3800, 5100, 4300, 5400), 12, ["lake"], "사막 동쪽의 큰 호수", parent="sand_sea",
  changed="원작의 호수")
r("kosoma_river", "코소마 강가", "CANON", 2, (-3500, 4400, -3300, 5700), 12, ["river", "desert"], "사막 서쪽을 흐르는 강", parent="sand_sea",
  changed="원작의 강가")
r("consera_desert", "컨세라 사막", "CANON", 2, (-4600, 4400, -3700, 5000), 8, ["plains"], "흰털 영양이 뛰놀던 사막 목초지", parent="sand_sea",
  changed="전쟁의 시대 목초지가 후대에 다시 풀밭이 됨")
r("heated_desert", "달구어진 사막", "CANON", 4, (1100, 5200, 2400, 5700), 6, ["desert"], "사막 웜이 출몰하는 뜨거운 모래", parent="sand_sea",
  changed="원작의 웜 사막. 후대 「사구 갈퀴웜」의 터")
r("giants_mountain", "거인족의 산", "CANON", 5, (4300, 4500, 5000, 5000), 10, ["mountain"], "사막 동쪽의 거인 산", parent="sand_sea",
  changed="원작의 레벨 600대 사냥터")
r("gordle_habitat", "고르들의 서식지", "CANON", 5, (-1500, 5200, -900, 5600), 8, ["desert"], "고르들 무리의 서식지", parent="sand_sea",
  changed="원작의 레벨 600대 사냥터")
r("sun_altar", "태양의 제단", "CANON", 3, (2600, 5300, 2700, 5400), 22, ["landmark"], "붉은 바위의 전사 유적", parent="sand_sea",
  changed="가장 뛰어난 사막 전사가 태양의 힘을 얻는다는 제단")
r("metapeia", "메타페이아 (신기루 도시)", "CANON", 4, (3400, 5400, 3800, 5650), 15, ["ruins", "desert"], "정오 신기루 때만 들어갈 수 있는 유적 도시", parent="sand_sea",
  changed="원작의 신비 도시. 지금도 게임 시각 정오 무렵에만 입구가 열린다")
r("flame_sanctuary", "화염의 생츄어리", "CANON", 6, (3600, 5500, 3640, 5540), 25, ["dungeon_site"], "불의 근원 — 화염의 크리스탈로 들어가는 곳", parent="metapeia",
  changed="원작에서 이름만 알려진 곳")
r("mutta_tomb", "뭇타의 무덤", "CANON", 3, (3200, 5200, 3260, 5260), 22, ["dungeon_site"], "메타페이아의 단서가 있는 무덤", parent="sand_sea", changed="원작의 무덤")
r("buharess", "부하레스 유적", "CANON", 3, (-3000, 5300, -2600, 5600), 12, ["ruins", "desert"], "전쟁의 시대 사막 도시 유적", parent="sand_sea", changed="원작의 옛 사막 도시가 유적으로 남음")
r("ozalem", "오잘렘 유적", "CANON", 3, (0, 5450, 400, 5650), 12, ["ruins", "desert"], "전쟁의 시대 사막 도시 유적", parent="sand_sea", changed="원작의 옛 사막 도시가 유적으로 남음")
r("lahos", "라호스", "CANON", 2, (-4500, 5100, -4000, 5600), 6, ["desert"], "사막 서쪽 지역", parent="sand_sea", changed="원작의 사막 지명")
r("mald_region", "말드 지역", "CANON", 2, (4500, 5100, 5300, 5600), 6, ["desert"], "사막 동쪽 지역", parent="sand_sea", changed="원작의 사막 지명")
r("urgan_region", "우르간 지역", "CANON", 2, (600, 4350, 1400, 4700), 6, ["desert"], "사막 북쪽 지역", parent="sand_sea", changed="원작의 사막 지명")
r("hot_underground", "뜨거운 땅속 던전", "CANON", 5, (500, 4900, 560, 4960), 22, ["dungeon_site"], "들어가면 입구가 닫히는 오래된 던전", parent="sand_sea", changed="원작의 던전")
r("ibelia_dungeon", "이벨리아 던전", "CANON", 4, (-800, 4900, -740, 4960), 22, ["dungeon_site"], "사막의 던전 입구", parent="sand_sea", changed="원작의 던전")
r("takun_cave", "타쿤 동굴", "CANON", 4, (2400, 4800, 2460, 4860), 22, ["dungeon_site"], "사막의 동굴 입구", parent="sand_sea", changed="원작의 던전")
r("buried_city", "매몰 도시", "ORIGINAL", 5, (1600, 5400, 2400, 5690), 15, ["ruins", "desert", "deep"],
  "사구 아래 묻힌 옛 사막 왕국의 도시. 바람이 모래를 걷어 낼 때만 입구가 드러남", parent="sand_sea", y=(-64, 60))
r("southern_sea", "남쪽 바다", "SOURCE-BASED", 3, (-6000, 5701, 5850, 6000), 1, ["sea"], "남쪽 대륙(메아드의 숲) · 남극으로 가는 바다")

# ===================================================================== 바다 · 금역 (서쪽)
sec("서쪽 바다 · 금역")
r("western_sea", "서쪽 바다 (북)", "SOURCE-BASED", 3, (-6000, -6000, -5401, -3200), 1, ["sea"], "북부 서해안 밖의 바다")
r("western_sea_south", "서쪽 바다 (남)", "SOURCE-BASED", 3, (-6000, 801, -4801, 5700), 1, ["sea"], "칼라모르 · 그라디안 서해안 밖의 바다, 서쪽 신대륙으로 가는 길")
r("kallamore_bay", "칼라모르 만", "SOURCE-BASED", 2, (-4800, 801, -3601, 1500), 2, ["sea"], "칼라모르 서쪽, 침식으로 생긴 만")
r("mist_wall", "안개 장벽", "ORIGINAL", 6, (-6000, 1000, -5800, 4000), 10, ["mist"], "서쪽 바다 끝을 가리는 이상한 안개. 신대륙으로 가는 길을 막는다",
  parent="western_sea_south")
r("todum_depths", "토둠 (뱀파이어의 세계로 가는 굴)", "CANON", 6, (-5950, 1500, -5450, 2500), 15, ["underground", "deep"],
  "서쪽 바다 밑 깊은 동굴 — 뱀파이어 세계 토둠으로 이어진다는 곳", parent="western_sea_south", y=(-64, 20),
  changed="원작의 다른 차원 '뱀파이어 토둠'으로 가는 길이 후대에는 바다 밑 동굴로 전해짐")


# ===================================================================== 원작에 위치가 없어 이 게임이 정한 자리
sec("카올랴 · 이름 없는 미궁 · 비경 · 수련관 (원작에 위치 없음 → 이 게임의 배치)")
r("kaolya_blight", "카올랴의 오염된 땅", "CANON", 6, (-4600, -4200, -3200, -3300), 10, ["badlands", "ruins"],
  "10대 금역 중 최악. 갈라진 땅에서 몬스터가 끝없이 솟는다 — 악마계의 문이 숨은 곳", parent="north_reach",
  changed="원작에 위치가 없어 이 게임은 북부 북서쪽 끝에 둠. 악마 집사장이 숨었던 균열이 지금은 악마계로 열린 문이 됨")
r("devil_gate", "마힐고르타의 은신처 (악마계의 문)", "ORIGINAL", 6, (-3912, -3762, -3888, -3738), 26, ["portal"],
  "카올랴 한가운데 균열 — 악마계로 건너가는 문", parent="kaolya_blight")
# 8대 미궁: 원작은 로드릭만 이름이 나옴. 미궁 조드(아베리안 숲)를 하나로 세고, 나머지 여섯은 이 게임이 만든 미궁 (ORIGINAL)
r("ice_heart_labyrinth", "얼음 심장 미궁", "ORIGINAL", 6, (-2000, -5600, -1940, -5540), 22, ["dungeon_site"], "8대 미궁 (이 게임) — 빙원 아래 얼어붙은 궁전", parent="niflheim_wastes")
r("mirror_corridor_labyrinth", "거울 회랑 미궁", "ORIGINAL", 6, (-2600, -600, -2540, -540), 22, ["dungeon_site"], "8대 미궁 (이 게임) — 길이 비쳐 갈라지는 회랑", parent="kallamore_lands")
r("sunken_archive_labyrinth", "가라앉은 서고 미궁", "ORIGINAL", 6, (200, 400, 260, 460), 22, ["dungeon_site"], "8대 미궁 (이 게임) — 땅속으로 내려앉은 옛 서고", parent="haven_lands")
r("stopped_gear_labyrinth", "멈춘 톱니 미궁", "ORIGINAL", 6, (2000, 900, 2060, 960), 22, ["dungeon_site"], "8대 미궁 (이 게임) — 멈춘 기계 장치의 성", parent="britten_alliance")
r("hourglass_labyrinth", "모래시계 미궁", "ORIGINAL", 6, (-1800, 5000, -1740, 5060), 22, ["dungeon_site"], "8대 미궁 (이 게임) — 모래가 흐르며 방이 바뀌는 지하", parent="sand_sea")
r("root_labyrinth", "뿌리 미궁", "ORIGINAL", 6, (5700, 2500, 5760, 2560), 22, ["dungeon_site"], "8대 미궁 (이 게임) — 대수림 거목의 뿌리 속", parent="great_forest")
# 9대 비경: 원작은 울호프 산호지대만 이름이 나옴. 나머지 여덟은 이 게임이 만든 경치 (ORIGINAL) — 싸움보다 보는 곳
r("starfall_lake", "별이 내려앉는 호수", "ORIGINAL", 1, (-3150, -4750, -2750, -4350), 12, ["lake"], "9대 비경 (이 게임) — 밤하늘이 그대로 비치는 북부의 호수", parent="north_reach")
r("rainbow_falls", "무지개 폭포 골짜기", "ORIGINAL", 2, (2450, -3150, 2750, -2950), 12, ["valley"], "9대 비경 (이 게임) — 노른 산맥 사이 물보라 골짜기", parent="norn_range")
r("thousand_pillars", "천 개의 돌기둥 협곡", "ORIGINAL", 3, (3300, -2300, 3700, -1900), 12, ["canyon"], "9대 비경 (이 게임) — 바람이 깎은 돌기둥 숲", parent="brent_highlands")
r("white_salt_flats", "하얀 소금 평원", "ORIGINAL", 2, (-2900, 4400, -2100, 4750), 12, ["salt"], "9대 비경 (이 게임) — 끝없이 흰 소금 바닥", parent="sand_sea")
r("firefly_forest", "반딧불 숲", "ORIGINAL", 1, (-1880, 2300, -1520, 2900), 12, ["forest"], "9대 비경 (이 게임) — 밤마다 빛이 떠다니는 숲", parent="aidern_lands")
r("cloud_plateau", "구름 위 고원", "ORIGINAL", 3, (-3290, 2450, -3010, 2900), 12, ["mountain"], "9대 비경 (이 게임) — 루비둠 산맥 위 구름 바다를 내려다보는 고원", parent="ruvidum_range")
r("crimson_maple_valley", "붉은 단풍 골짜기", "ORIGINAL", 2, (-1500, -2380, -1150, -2100), 12, ["valley", "forest"], "9대 비경 (이 게임) — 하이네프 산속 단풍 골짜기", parent="haineff_mountains")
r("glacier_grotto", "푸른 빙하 동굴", "ORIGINAL", 3, (2900, -5700, 2960, -5640), 22, ["landmark"], "9대 비경 (이 게임) — 빙원 끝 푸른 얼음 동굴 입구", parent="niflheim_wastes")
# 수련관: 원작 본문에 위치가 없음
r("basic_training_hall", "기초 수련관", "CANON", 0, (4010, 1510, 4060, 1560), 25, ["landmark"], "허수아비를 오래 때리는 첫 수련관", parent="serabourg",
  changed="원작에 위치가 없어 이 게임은 시작 도시 세라보그 성 한쪽에 둠. 후대에는 마을 아이들도 드나드는 훈련장")
r("novice_training_hall", "초급 수련관", "CANON", 1, (4340, 1840, 4390, 1890), 25, ["landmark"], "철인 100명과 겨루는 수련관", parent="serabourg",
  changed="원작에 위치가 없어 이 게임은 세라보그 성 반대편 귀퉁이에 둠")
r("hero_tower", "영웅의 탑 (중급 수련관)", "CANON", 3, (900, 200, 960, 260), 22, ["landmark"], "층을 오르며 겨루는 중급 수련관", parent="litten_kingdom",
  changed="원작에 위치가 없어 이 게임은 대륙 한가운데 리튼 왕국에 둠")
# 다른 차원으로 가는 문 (베르사 대륙 쪽)
r("dead_ferry", "망자의 나루 (거인계로 가는 배터)", "ORIGINAL", 5, (-4790, 2590, -4766, 2614), 26, ["portal"], "그라디안 서해안 — 거인계로 건너가는 배가 닿는 곳", parent="gradian_lands")
r("spirit_spring", "정령의 샘 (정령계의 문)", "ORIGINAL", 3, (-1612, -3492, -1588, -3468), 26, ["portal"], "북부 숲속 샘 — 정령계로 이어진다", parent="north_reach")
r("fairy_ring", "요정의 고리 (요정계의 문)", "ORIGINAL", 4, (5740, -500, 5764, -476), 26, ["portal"], "대수림 깊은 곳 버섯 고리 — 요정계의 문", parent="great_forest")
r("hell_rift", "지옥의 틈", "ORIGINAL", 6, (-1620, -1820, -1596, -1796), 26, ["portal"], "벨카인이 기어 나왔다는 은신처 옆 틈 — 지옥으로 내려간다", parent="haineff_mountains")
r("demonkind_gate", "마계의 문 (구멍 바닥)", "ORIGINAL", 6, (-5312, -2012, -5288, -1988), 26, ["portal"], "엠비뉴 성지의 거대한 구멍 바닥 — 마계로 이어진다", parent="embinyu_sanctum")
r("todum_gate", "토둠으로 가는 굴 끝", "ORIGINAL", 5, (-5712, 1988, -5688, 2012), 26, ["portal"], "바다 밑 굴 끝 — 뱀파이어 토둠의 문", parent="todum_depths")

# ===================================================================== 바깥 바다 · 극지 · 신대륙 (베르사 대륙 밖, -8000 ~ 8000)
sec("바깥 바다 · 북극 · 남극 · 남쪽/동쪽/서쪽 신대륙 (나무위키 §1 · §3.3 · §4.2 · §5.2)")
r("outer_ocean", "먼 바다", "SOURCE-BASED", 4, (-8000, -8000, 8000, 8000), -1, ["sea"], "베르사 대륙과 신대륙 · 극지 사이의 깊은 바다")
r("north_pole", "북극", "CANON", 5, (-8000, -8000, 8000, -7401), 3, ["frozen"], "세상의 북쪽 끝 얼음 땅",
  changed="원작에 존재만 언급. 이 게임은 지도 북쪽 끝 띠로 둠")
r("south_pole", "남극 (혹한의 땅)", "CANON", 6, (-8000, 7401, 8000, 8000), 3, ["frozen"], "10대 금역. 펭귄도 얼어 죽는 추위 · 눈의 정령 · 얼음의 영혼",
  changed="원작에 위치 서술이 없어 이 게임은 지도 남쪽 끝 띠로 둠. 남쪽 대륙 너머")
r("south_continent", "남쪽 대륙", "CANON", 4, (-3000, 6400, 3000, 7200), 3, ["forest", "frontier"], "베르사 남쪽 끝에서 바다를 건너면 나오는 대륙",
  changed="원작에서 저주로 얼었다가 풀린 땅. 후대에는 숲이 다시 우거짐")
r("meard_forest", "메아드의 숲", "CANON", 3, (-700, 6550, 700, 7050), 10, ["forest"], "세계수의 후손이 지내는 숲", parent="south_continent",
  changed="원작의 세계수 후손이 자라 숲 한가운데 거목이 됨")
r("world_tree_scion", "세계수의 후손", "CANON", 2, (-30, 6770, 30, 6830), 25, ["landmark"], "숲 한가운데 거목", parent="meard_forest",
  changed="원작의 세계수 후손 — 후대에 하늘을 덮을 만큼 자람")
r("east_continent", "동쪽 대륙", "CANON", 5, (6400, -3500, 7700, 3500), 3, ["highland", "forest"], "베르사 동쪽 바다 건너 — 높은 산과 숲",
  changed="원작에서 골동품 지도로 알려진 대륙. 이 게임은 동쪽 바다 건너에 둠")
r("east_peaks", "동쪽 대륙 높은 산들", "SOURCE-BASED", 5, (6600, 800, 7500, 3200), 8, ["mountain"], "엄청난 높이의 산들", parent="east_continent")
r("ring_of_fire", "불의 고리", "CANON", 6, (6700, -2500, 7500, -500), 10, ["volcano"], "쉴 새 없이 분화하는 대화산 지대", parent="east_continent",
  changed="원작의 대화산 지대 — 후대에도 분화가 멈추지 않음")
r("randoni_lair", "랜도니의 레어 (대화산)", "CANON", 6, (7070, -1530, 7130, -1470), 22, ["dungeon_site"], "대화산 꼭대기의 레어 입구", parent="ring_of_fire",
  changed="원작의 레어. 후대에는 주인 없는 빈 레어로 전해짐")
r("west_new_continent", "서쪽 신대륙 (거인들이 잊은 땅)", "CANON", 6, (-7700, -3500, -6400, 3500), 3, ["highland"], "거인들이 만들어 놓고 잊어버린 대륙",
  changed="원작에선 이름만 나온 맥거핀. 이 게임은 서쪽 바다 건너에 둠")
r("magic_barrier", "마법의 장벽", "CANON", 6, (-6480, -3500, -6420, 3500), 15, ["wall", "sealed"], "신대륙 동해안을 막은 장벽 — 통로가 없다 (돌아서 북쪽 · 남쪽 해안으로)",
  parent="west_new_continent", changed="원작의 '몬스터와 마법의 장벽'. 후대에도 무너지지 않음")
r("giants_gold_vault", "거인의 황금 창고", "SOURCE-BASED", 6, (-7100, -100, -7040, -40), 22, ["dungeon_site"], "거인들이 베르사에서 캐 간 황금을 쌓았다는 창고 입구",
  parent="west_new_continent")

# ===================================================================== 다른 차원 (세계 versa_realms)
sec("다른 차원 — 세계 versa_realms (나무위키 §4.1 · §7)")
REALM = "versa_realms"
r("realm_rift", "차원의 틈", "ORIGINAL", 6, (-8000, -8000, 8000, 8000), -1, ["sea"], "차원과 차원 사이의 빈 바다", world=REALM)
r("giant_realm", "거인계", "CANON", 6, (-6000, -3000, -3200, -200), 4, ["highland", "forest"], "거인들이 지배하는 세계 — 인간 마을도 있다", world=REALM,
  changed="원작의 거인계. 후대에도 거인이 다스리고, 신들의 영역과 맞닿음")
r("roadseeker_tomb", "로드시커의 무덤", "CANON", 6, (-5000, -2500, -4940, -2440), 22, ["dungeon_site"], "대모험가 로드시커의 무덤", parent="giant_realm", world=REALM,
  changed="원작의 무덤. 깨우는 방법은 옮기지 않음 — 이 게임에서는 입구만 남은 무덤")
r("derrick_village", "마을 데릭", "CANON", 2, (-4300, -1500, -3900, -1100), 20, ["city", "forest"], "깊은 숲 속 인간 · 수인족 마을", parent="giant_realm", world=REALM, y=CITY,
  changed="원작의 마을. 후대에는 차원을 건너온 모험가의 쉼터")
r("giant_return", "거인계 귀환의 배터", "ORIGINAL", 2, (-4612, -812, -4588, -788), 26, ["portal"], "베르사로 돌아가는 배", parent="giant_realm", world=REALM)
r("divine_gate", "신들의 문", "ORIGINAL", 6, (-3312, -1612, -3288, -1588), 26, ["portal"], "거인계 동쪽 끝 — 파수꾼이 지키는 신계의 문", parent="giant_realm", world=REALM)
r("divine_realm", "신계", "CANON", 6, (-3000, -3000, -200, -200), 4, ["divine"], "거인들의 영역 가까운 신들의 영역", world=REALM,
  changed="원작의 신계. 이 게임은 탐험 숙련 마스터만 건너게 함")
r("divine_return", "신계 내려가는 문", "ORIGINAL", 4, (-1612, -1612, -1588, -1588), 26, ["portal"], "거인계로 돌아가는 문", parent="divine_realm", world=REALM)
r("spirit_realm", "정령계", "CANON", 4, (200, -3000, 3000, -200), 4, ["mist"], "정령술사들이 갈 수 있는 세계", world=REALM,
  changed="원작의 정령계. 이 게임은 정령의 샘으로 누구나 (탐험 숙련만 되면) 건넘")
r("spirit_return", "정령계 귀환의 샘", "ORIGINAL", 2, (1588, -1612, 1612, -1588), 26, ["portal"], "베르사로 돌아가는 샘", parent="spirit_realm", world=REALM)
r("fairy_realm", "요정계", "CANON", 4, (3200, -3000, 6000, -200), 4, ["forest"], "요정들의 세계", world=REALM, changed="원작에 이름만 나온 세계 — 모습은 이 게임의 설정")
r("fairy_return", "요정계 귀환의 고리", "ORIGINAL", 2, (4588, -1612, 4612, -1588), 26, ["portal"], "베르사로 돌아가는 고리", parent="fairy_realm", world=REALM)
r("demonkind_realm", "마계", "CANON", 6, (-6000, 200, -3200, 3000), 4, ["badlands"], "마족들의 세계 — 악마와는 사이가 나쁘다", world=REALM,
  changed="원작에 이름만 나온 세계 — 모습은 이 게임의 설정")
r("demonkind_return", "마계 귀환의 문", "ORIGINAL", 4, (-4612, 1588, -4588, 1612), 26, ["portal"], "베르사로 돌아가는 문", parent="demonkind_realm", world=REALM)
r("devil_realm", "악마계", "CANON", 6, (-3000, 200, -200, 3000), 4, ["badlands", "ruins"], "악마들의 세계 — 옛 원정의 폐허", world=REALM,
  changed="원작에서 악마 대공 원정이 있었던 세계. 후대에는 원정대의 무너진 진지가 남음")
r("devil_return", "악마계 귀환의 문", "ORIGINAL", 4, (-1612, 1588, -1588, 1612), 26, ["portal"], "베르사로 돌아가는 문", parent="devil_realm", world=REALM)
r("hell_realm", "지옥", "CANON", 6, (200, 200, 3000, 3000), 4, ["volcano"], "악마와 마물의 세계 — 중간계에서 구하기 힘든 광석", world=REALM,
  changed="원작의 지옥. 이 게임은 지옥의 틈으로 건넘")
r("hell_return", "지옥 귀환의 틈", "ORIGINAL", 4, (1588, 2388, 1612, 2412), 26, ["portal"], "베르사로 돌아가는 틈", parent="hell_realm", world=REALM)
r("todum_realm", "뱀파이어 토둠", "CANON", 5, (3200, 200, 6000, 3000), 4, ["mist", "ruins"], "뱀파이어들의 세계", world=REALM,
  changed="원작의 토둠. 후대에는 바다 밑 굴로만 이어짐")
r("todum_return", "토둠 귀환의 굴", "ORIGINAL", 3, (4588, 1588, 4612, 1612), 26, ["portal"], "베르사로 돌아가는 굴", parent="todum_realm", world=REALM)

# ===================================================================== 출력
def q(v):
    v = str(v)
    if any(c in v for c in ":#{}[],&*?|<>=!%@`\"'") or v.strip() != v:
        return '"' + v.replace('"', "'") + '"'
    return v

def lst(v):
    return "[" + ", ".join(q(x) for x in v) + "]"

lines = ["# 지역 — tools/gen_regions.py 로 생성 (손으로 고치면 다음 생성 때 덮어씀).",
         "# 근거: 나무위키 「로열 로드/지리」 (2026 사용자 제공 본문) · 01_RESEARCH.md. x 동쪽(+) · z 남쪽(+). 겹치면 priority 가 높은 지역이 이긴다.",
         "# 세계 world 는 50000 × 50000 (-25000 ~ 25000, 베르사 대륙 ±18750). versa_realms 는 ±8000.",
         "# 원작 지명(CANON)은 changed(후대의 변화)가 필수. tags: landmark · dungeon_site · wall 은 지형을 바꾸지 않고 구조물만 세운다.",
         "# 원작에 위치가 없는 곳(카올랴 · 신대륙 · 극지 · 이름 없는 미궁/비경 · 수련관 · 다른 차원)은 이 게임이 정한 자리 — purpose · changed 에 그렇게 적었다.",
         "# portal 태그 = 문 (content/gates.yml). 다른 차원은 world: versa_realms.",
         "regions:"]
cur = None
ids = set()
for s, d in R:
    if d["id"] in ids:
        raise SystemExit("id 중복: " + d["id"])
    ids.add(d["id"])
    if s != cur:
        lines.append("")
        lines.append("  # " + "=" * 60 + " " + s)
        cur = s
    lines.append("  %s:" % d["id"])
    lines.append("    name: %s" % q(d["name"]))
    if d["source"] != "ORIGINAL":
        lines.append("    source: %s" % d["source"])
    lines.append("    danger: %d" % d["danger"])
    if d["world"] != "world":
        lines.append("    world: %s" % d["world"])
    lines.append("    min: [%d, %d, %d]" % tuple(d["min"]))
    lines.append("    max: [%d, %d, %d]" % tuple(d["max"]))
    lines.append("    priority: %d" % d["priority"])
    if d["parent"]:
        lines.append("    parent: %s" % d["parent"])
    lines.append("    tags: %s" % lst(d["tags"]))
    lines.append("    purpose: %s" % q(d["purpose"]))
    if d["changed"]:
        lines.append("    changed: %s" % q(d["changed"]))
    if d["resources"]:
        lines.append("    resources: %s" % lst(d["resources"]))
    if d["factions"]:
        lines.append("    factions: %s" % lst(d["factions"]))
for s, d in R:
    if d["parent"] and d["parent"] not in ids:
        raise SystemExit("없는 parent: %s → %s" % (d["id"], d["parent"]))
open(OUT, "w", encoding="utf-8").write("\n".join(lines) + "\n")
print(len(R), "regions →", os.path.normpath(OUT))
