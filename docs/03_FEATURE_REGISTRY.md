# 03. Feature Registry 요약

> 정본은 `feature_registry.yml` (모든 필드: 출처 · 플레이어 경험 · 의존 · 구현 · 데이터 · UI · 리소스팩 · 성능 위험 · 보안 위험 · 저장 · 테스트 · 상태).
> `RegistryAuditTest` 가 상태와 실제 테스트 파일을 대조합니다. **VERIFIED 는 아직 0개** — 이 개발 환경에서는 Paper 서버를 내려받을 수 없어 실제 서버 테스트를 못 했습니다.
> IMPLEMENTED = 코드 + 자동 테스트 통과 (서버 연결 코드는 컴파일 · API 시그니처까지만 확인).

| ID | 기능 | 분류 | 상태 | 자동 테스트 |
|---|---|---|---|---|
| CORE-01 | DB · 트랜잭션 · 마이그레이션 | ORIGINAL | **IMPLEMENTED** | MigratorTest |
| CORE-02 | DB 전용 스레드 · 비동기 흐름 | ORIGINAL | **IMPLEMENTED** | TradeServiceTest |
| CORE-03 | 감사 로그 | ORIGINAL | **IMPLEMENTED** | EconomyServiceTest, TradeServiceTest |
| CONT-01 | 데이터 중심 콘텐츠 로더 | ORIGINAL | **IMPLEMENTED** | ContentIntegrityTest |
| ITM-01 | 고유 아이템 인스턴스 | SOURCE-BASED | **IMPLEMENTED** | ItemServiceTest |
| ITM-02 | 배달함 (안전한 지급) | ORIGINAL | **IMPLEMENTED** | ItemServiceTest |
| ITM-03 | 인벤토리 검증 · 격리 (복제 방지) | ORIGINAL | **IMPLEMENTED** | ItemServiceTest |
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
| WLD-02 | 지역 데이터로 만드는 지형 · 도시 · 랜드마크 | ORIGINAL | **IMPLEMENTED** | TerrainModelTest, SettlementPlannerTest |
| WLD-03 | 다른 땅 · 다른 차원으로 가는 문 | ORIGINAL | **IMPLEMENTED** | GateServiceTest |
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
| DTH-01 | 사망 페널티 — 완화판 (death.mode: soft) | SOURCE-BASED | **IMPLEMENTED** | JobQuestDeathTest |
| CHR-01 | 캐릭터 만들기 — 종족 · 성별 · 시작 도시 | CANON | **IMPLEMENTED** | CanonRulesTest, CanonDomainTest |
| BEG-01 | 초보 기간 (한 달 동안 성 밖 금지) | CANON | **IMPLEMENTED** | CanonRulesTest |
| TIME-01 | 게임 시간 4배 | CANON | **IMPLEMENTED** | CanonDomainTest |
| DTH-02 | 사망 페널티 — 원작식 (기본, 접속 제한 없음) | CANON | **IMPLEMENTED** | CanonRulesTest |
| REP-01 | 명성 · 악명 · 살인자 | CANON | **IMPLEMENTED** | CanonRulesTest |
| GOD-01 | 신 · 교단 · 신전 기부 | CANON | **IMPLEMENTED** | CanonRulesTest |
| POT-01 | 물약 — 회복력 상승 · 겹침 불가 · 레벨 따라 약해짐 | CANON | **IMPLEMENTED** | CanonDomainTest |
| PTY-01 | 파티 | CANON | **IMPLEMENTED** | CanonDomainTest |
| TRN-01 | 수련관 허수아비 · 힘 스탯 | CANON | **IMPLEMENTED** | CanonRulesTest |
| TRN-02 | 초급 수련관 — 철인 100명 | CANON | **IMPLEMENTED** | CanonRulesTest |
| ART-01 | 비기 · 최후의 비기 | CANON | **IMPLEMENTED** | CanonRulesTest |
| ACC-01 | 캡슐 · 이용료 · 연령 제한 | CANON | **IMPLEMENTED** | CanonRulesTest |
| LND-01 | 땅 | CANON | **IMPLEMENTED** | RealmServiceTest |
| SHP-01 | 개인 상점 | CANON | **IMPLEMENTED** | RealmServiceTest |
| CST-01 | 성 · 공성 · 세금 · 수입 | CANON | **IMPLEMENTED** | RealmServiceTest |
| NAT-01 | 국가 · 황제 | CANON | **IMPLEMENTED** | RealmServiceTest |
| LORE-01 | 연대기 · 신 목록 | CANON | **IMPLEMENTED** | CanonDomainTest |
| UI-01 | 전용 MMORPG UI | ORIGINAL | **IMPLEMENTED** | ResourcePackBuilderTest |
| RP-01 | 리소스팩 (코드로 생성 · 배포) | ORIGINAL | **IMPLEMENTED** | ResourcePackBuilderTest, ExternalPackTest |
| SRV-01 | 실제 Paper 서버 테스트 | ORIGINAL | **BLOCKED** | — |

상태 합계: BLOCKED 1 · IMPLEMENTED 37

남은 것은 SRV-01(실제 Paper 서버 테스트) 하나입니다 — 이 개발 환경에서 서버를 내려받을 수 없어 BLOCKED.
