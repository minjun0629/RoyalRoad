# 03. Feature Registry 요약

> 정본은 `feature_registry.yml` (모든 필드: 출처 · 플레이어 경험 · 의존 · 구현 · 데이터 · UI · 리소스팩 · 성능 위험 · 보안 위험 · 저장 · 테스트 · 상태).
> `RegistryAuditTest` 가 상태와 실제 테스트 파일을 대조합니다. **VERIFIED 는 아직 0개** — 이 개발 환경에서는 Paper 서버를 내려받을 수 없어 실제 서버 테스트를 못 했습니다.
> IMPLEMENTED = 코드 + 자동 테스트 통과 (서버 연결 코드는 컴파일 · API 시그니처까지만 확인). PARTIAL = 알려진 빈 곳이 있음 (각 항목 description 참고).

| ID | 기능 | 분류 | 상태 | 자동 테스트 |
|---|---|---|---|---|
| CORE-01 | DB · 트랜잭션 · 마이그레이션 | ORIGINAL | **IMPLEMENTED** | MigratorTest |
| CORE-02 | DB 전용 스레드 · 비동기 흐름 | ORIGINAL | **IMPLEMENTED** | TradeServiceTest |
| CORE-03 | 감사 로그 | ORIGINAL | **IMPLEMENTED** | EconomyServiceTest, TradeServiceTest |
| CONT-01 | 데이터 중심 콘텐츠 로더 | ORIGINAL | **IMPLEMENTED** | ContentIntegrityTest |
| ITM-01 | 고유 아이템 인스턴스 | SOURCE-BASED | **IMPLEMENTED** | ItemServiceTest |
| ITM-02 | 배달함 (안전한 지급) | ORIGINAL | **IMPLEMENTED** | ItemServiceTest |
| ITM-03 | 인벤토리 검증 · 격리 (복제 방지) | ORIGINAL | **PARTIAL** | ItemServiceTest |
| ITM-04 | 내구도 · 수리 | SOURCE-BASED | **IMPLEMENTED** | ItemServiceTest |
| ECO-01 | 돈 · 원장 | ORIGINAL | **IMPLEMENTED** | EconomyServiceTest |
| TRD-01 | 1:1 거래 | ORIGINAL | **IMPLEMENTED** | TradeServiceTest |
| TRD-02 | 지역 경매장 · NPC 상점 · 지역 시세 | ORIGINAL | **IMPLEMENTED** | MarketAuctionTest, ContentIntegrityTest |
| SKL-01 | 숙련 (초급 · 중급 · 고급 · 마스터) | SOURCE-BASED | **IMPLEMENTED** | CraftingServiceTest, ContentIntegrityTest, GrowthAndWorldTest |
| SKL-02 | 행동 스탯 (조건 해금) | SOURCE-BASED | **IMPLEMENTED** | GrowthAndWorldTest, ContentIntegrityTest |
| SKL-03 | 행동 스탯의 실제 효과 | ORIGINAL | **IMPLEMENTED** | JobQuestDeathTest, WorldEventGatherTest, CraftingServiceTest |
| CLS-01 | 직업 체계 (전투 1 + 생활 1) | SOURCE-BASED | **IMPLEMENTED** | JobQuestDeathTest, SkillBookTest, ContentIntegrityTest |
| CRF-01 | 제작 공통 엔진 (대장 · 재봉 · 가죽 · 요리 · 연금 · 조각) | SOURCE-BASED | **IMPLEMENTED** | CraftingServiceTest, ContentIntegrityTest |
| CRF-02 | 조각 (여러 재료 조합 · 실제 아이템) | SOURCE-BASED | **IMPLEMENTED** | CraftingServiceTest |
| GAT-01 | 채집 · 채광 · 벌목 · 낚시 | SOURCE-BASED | **IMPLEMENTED** | WorldEventGatherTest, ContentIntegrityTest |
| WLD-01 | 지역 · 위험도 · 공간 인덱스 | ORIGINAL | **IMPLEMENTED** | GrowthAndWorldTest |
| WLD-02 | 지역 데이터로 만드는 지형 · 유적 | ORIGINAL | **PARTIAL** | TerrainModelTest |
| EXP-01 | 발견 기록 · 최초 발견자 | SOURCE-BASED | **IMPLEMENTED** | GrowthAndWorldTest |
| MAP-01 | 탐험 지도 (안개) | ORIGINAL | **IMPLEMENTED** | MapServiceTest |
| NPC-01 | NPC 정의 · 관계 | ORIGINAL | **IMPLEMENTED** | GrowthAndWorldTest |
| NPC-02 | NPC 일과 이동 · 상점 · 의뢰 · 예보 | ORIGINAL | **IMPLEMENTED** | NpcScheduleTest, ContentIntegrityTest |
| QST-01 | 의뢰 (일상 · 숙련 · 험로 · 전설 · 숨은 의뢰) | ORIGINAL | **IMPLEMENTED** | JobQuestDeathTest, SkillBookTest, ContentIntegrityTest |
| HID-01 | 히든 콘텐츠 엔진 (봉인 · 행동 조합 · 최초 발견 · 소문) | ORIGINAL | **IMPLEMENTED** | HiddenServiceTest |
| HID-02 | 서버마다 다른 히든 조건 생성 | ORIGINAL | **IMPLEMENTED** | HiddenGeneratorTest, HiddenServiceTest |
| CMB-01 | 전투 기본 (무기 · 방어구 · 숙련 · 약점 · 마모) | ORIGINAL | **IMPLEMENTED** | BossAndCombatTest |
| CMB-02 | 스킬 · 콤보 · 회피 · 상태 이상 | ORIGINAL | **IMPLEMENTED** | CombatStateTest, SkillBookTest, ContentIntegrityTest |
| BOS-01 | 거대 보스 규칙 (페이즈 · 패턴 · 예고 · 광폭화 · 약점) | ORIGINAL | **IMPLEMENTED** | BossAndCombatTest |
| BOS-02 | 거대 보스 실행 (모델 · 이동 · 판정 상자 · 기여도 보상) | ORIGINAL | **IMPLEMENTED** | BossServiceTest, BossAndCombatTest |
| GLD-01 | 길드 | SOURCE-BASED | **IMPLEMENTED** | GuildServiceTest |
| DUN-01 | 던전 (매번 새 배치) | ORIGINAL | **IMPLEMENTED** | DungeonTest, DungeonServiceTest |
| EVT-01 | 월드 이벤트 (시간표 · 예보) | ORIGINAL | **IMPLEMENTED** | WorldEventGatherTest |
| DTH-01 | 사망 페널티 | SOURCE-BASED | **IMPLEMENTED** | JobQuestDeathTest |
| UI-01 | 전용 MMORPG UI | ORIGINAL | **PARTIAL** | ResourcePackBuilderTest |
| RP-01 | 리소스팩 (코드로 생성 · 배포) | ORIGINAL | **IMPLEMENTED** | ResourcePackBuilderTest, ExternalPackTest |
| SRV-01 | 실제 Paper 서버 테스트 | ORIGINAL | **BLOCKED** | — |

상태 합계: BLOCKED 1 · IMPLEMENTED 34 · PARTIAL 3

## PARTIAL 인 이유

| ID | 남은 것 |
|---|---|
| ITM-03 | 검사 주기 사이의 짧은 창 — 사용 · 거래 시점 검증으로 보완 |
| WLD-02 | 지형 생성기는 있으나 손으로 지은 도시 · 랜드마크 건축물은 없음, 실제 서버에서 생성 확인 전 |
| UI-01 | 아이콘은 바닐라 아이템, 팩 배경 글자 정렬은 실제 클라이언트에서 확인 전 |
