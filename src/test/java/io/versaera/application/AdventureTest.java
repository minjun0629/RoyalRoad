package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.application.port.AdventureRepository;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.party.Parties;
import io.versaera.domain.skill.Mastery;
import io.versaera.domain.travel.Route;
import io.versaera.domain.weather.WeatherKind;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** V7 모험 확장: 업적 · 칭호 · 기록 · 파티 · 길드 창고/의뢰 · 펫 · 이동 · 날씨 · 레이드 · 대형 조각 */
class AdventureTest {
    private static void level(TestWorld w, String uuid, String discipline, int lv) {
        w.s.progress.setMasteryXp(uuid, discipline, Mastery.cumulative(lv));
    }

    private static String npcWith(TestWorld w, String service) {
        return w.s.npcWorld.profiles().stream().filter(p -> !p.wanderer() && p.rare() == null && w.s.npcWorld.offers(p.id(), service))
                .map(p -> p.id()).findFirst().orElseThrow(() -> new AssertionError("NPC 없음: " + service));
    }

    // ------------------------------------------------------------------ 업적 · 칭호 · 기록
    @Test
    void achievementsPayOnceGrantTitlesAndFillTheRecord() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            assertTrue(w.s.achievements.all().size() >= 40, "업적 40개 이상");
            w.s.growth.record(p, "kill.monster", 1);
            var first = w.s.achievements.achievement("first_blood");
            assertEquals(first.money(), w.s.economy.balance(p), "첫 사냥 업적 보상");
            assertEquals(1, w.eventsOf(GameEvents.AchievementEarned.class).stream().filter(e -> e.achievementId().equals("first_blood")).count());
            w.s.growth.record(p, "kill.monster", 1);
            assertEquals(first.money(), w.s.economy.balance(p), "같은 업적은 한 번");
            // 칭호: 지역 5곳 → '첫 새벽'
            assertThrows(DomainException.class, () -> w.s.achievements.equip(p, "first_light"), "얻기 전엔 달 수 없다");
            w.s.growth.record(p, "discover.region", 5);
            assertTrue(w.s.achievements.titles(p).stream().anyMatch(t -> t.id().equals("first_light")));
            w.s.achievements.equip(p, "first_light");
            assertEquals("첫 새벽", w.s.achievements.equipped(p).orElseThrow().name());
            w.s.achievements.equip(p, null);
            assertTrue(w.s.achievements.equipped(p).isEmpty());
            // 숙련 업적은 레벨업 이벤트로
            w.s.growth.addXp(p, "swordsmanship", Mastery.cumulative(31), 60);
            assertTrue(w.s.progress.discovered(p, "achievement", "sword_adept"), "검술 30 업적");
            // 명성처럼 행동 기록 밖에서 오르는 값은 checkAll (접속 · 주기)
            w.s.reputation.addFame(p, 3000);
            assertTrue(w.s.achievements.checkAll(p).stream().anyMatch(a -> a.id().equals("famous")));
            assertTrue(w.s.achievements.checkAll(p).isEmpty(), "다시 확인해도 새로 주지 않는다");
            // 숨은 업적은 목록에서 earned=false
            assertTrue(w.s.achievements.list(p).stream().anyMatch(r -> r.achievement().hidden() && !r.earned()));
            var rec = w.s.achievements.record(p);
            assertTrue(rec.achievements() >= 4 && rec.points() > 0 && rec.worldFirsts() >= 1);
            assertEquals(w.s.achievements.all().size(), rec.achievementTotal());
            assertEquals(2L, rec.counters().get("kill.monster"));
            assertEquals(31, rec.topMastery().get("swordsmanship"));
            assertTrue(rec.byCategory().containsKey("전투"));
        }
    }

    // ------------------------------------------------------------------ 파티
    @Test
    void partyLootRotatesXpSharesAndRaidGroupsJoinParties() {
        Parties ps = new Parties();
        ps.invite("A", "B");
        ps.accept("B");
        ps.invite("A", "C");
        ps.accept("C");
        assertNull(ps.looter("B", List.of("A", "B", "C"), 0.5), "기본은 자유");
        assertThrows(DomainException.class, () -> ps.loot("B", Parties.Loot.ROUND_ROBIN), "파티장만");
        ps.loot("A", Parties.Loot.ROUND_ROBIN);
        assertEquals("A", ps.looter("C", List.of("A", "B", "C"), 0));
        assertEquals("B", ps.looter("C", List.of("A", "B", "C"), 0));
        assertEquals("C", ps.looter("A", List.of("A", "C"), 0), "근처에 없는 사람은 건너뛴다");
        ps.loot("A", Parties.Loot.RANDOM);
        assertEquals("C", ps.looter("A", List.of("A", "B", "C"), 0.99));
        assertEquals(100, Parties.share(100, 1));
        assertEquals(58, Parties.share(100, 2), "둘이면 115 를 나눔");
        assertTrue(Parties.share(100, 8) * 8 > 100, "같이 하면 손해 보지 않는다");
        // 공격대: 파티 둘을 묶는다
        ps.invite("D", "E");
        ps.accept("E");
        ps.raidInvite("A", "D");
        assertEquals("A", ps.raidAccept("D"));
        assertEquals(Set.of("A", "B", "C", "D", "E"), new HashSet<>(ps.raidMembers("E")));
        assertEquals("A", ps.raidHead("E"));
        ps.leave("A");   // 공격대장이 파티를 떠나면 B 가 파티장 · 공격대장
        assertEquals("B", ps.raidHead("D"));
        assertEquals(Parties.Loot.RANDOM, ps.loot("C"), "방식은 새 파티장에게 넘어간다");
    }

    // ------------------------------------------------------------------ 길드 창고 · 의뢰
    @Test
    void guildStorageLimitsWithdrawalsAndWeeklyQuestsPayTheTreasury() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String lead = TestWorld.player(), mem = TestWorld.player();
            w.s.economy.deposit(lead, 10_000, "test", "t1");
            var g = w.s.guilds.create(lead, "새벽단", "DAWN", "g1");
            w.s.guilds.invite(lead, mem);
            w.s.guilds.accept(mem, g.id());
            assertEquals(320, w.s.guildVault.deposit(mem, "iron_ore", 500, 320, "d1"));
            assertThrows(DomainException.class, () -> w.s.guildVault.deposit(mem, "iron_ore", 500, 1, "d1"), "같은 요청 두 번은 안 된다");
            assertTrue(w.s.items.pendingBulk(mem).stream().anyMatch(b -> b.typeId().equals("iron_ore") && b.amount() == 1), "실패한 넣기는 배달함으로 돌려준다");
            w.s.guildVault.withdraw(mem, "iron_ore", 500, 100, "w1");
            assertEquals(28, w.s.guildVault.withdrawLeft(mem));
            assertThrows(DomainException.class, () -> w.s.guildVault.withdraw(mem, "iron_ore", 500, 29, "w2"), "길드원 하루 한도 128");
            assertEquals(-1, w.s.guildVault.withdrawLeft(lead), "길드장은 제한 없음");
            assertEquals(100, w.s.guildVault.withdraw(lead, "iron_ore", 500, 120, "w3"));
            assertThrows(DomainException.class, () -> w.s.guildVault.withdraw(lead, "iron_ore", 500, 101, "w4"), "창고에 있는 만큼만");
            // 이번 주 의뢰 3개 (결정적)
            var qs = w.s.guildVault.quests(g.id());
            assertEquals(3, qs.size());
            assertEquals(qs, w.s.guildVault.quests(g.id()));
            var q = qs.stream().filter(d -> !d.deposit()).findFirst();
            if (q.isPresent()) {
                long before = w.s.economy.balance(GuildService.wallet(g.id()));
                w.s.growth.record(mem, q.get().counter(), q.get().target());
                var st = w.s.guildVault.questStates(lead).stream().filter(x -> x.def().id().equals(q.get().id())).findFirst().orElseThrow();
                assertTrue(st.done());
                assertEquals(before + q.get().money(), w.s.economy.balance(GuildService.wallet(g.id())), "길드 금고로");
                assertEquals(1, w.s.growth.counter(mem, "guild.quest"));
                w.s.growth.record(mem, q.get().counter(), q.get().target());
                assertEquals(before + q.get().money(), w.s.economy.balance(GuildService.wallet(g.id())), "한 주에 한 번");
            }
            // 다음 주엔 새 의뢰
            w.now.addAndGet(7L * 86_400_000L);
            assertNotEquals(-1, w.s.guildVault.week());
            assertThrows(DomainException.class, () -> w.s.guildVault.storage(TestWorld.player()), "길드 없는 사람");
        }
    }

    // ------------------------------------------------------------------ 펫
    @Test
    void tamingNeedsSkillFoodAndHabitatThenPetsGrowAndFaint() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            Set<String> forest = Set.of("forest");
            assertEquals("wolf", w.s.pets.speciesFor("WOLF", forest).orElseThrow().id());
            assertEquals("frost_wolf", w.s.pets.speciesFor("WOLF", Set.of("frozen")).orElseThrow().id(), "같은 엔티티라도 사는 곳이 다르면");
            assertThrows(DomainException.class, () -> w.s.pets.tame(p, "wolf", forest, Set.of("fish"), 500, 0), "먹이가 다르다");
            assertThrows(DomainException.class, () -> w.s.pets.tame(p, "wolf", Set.of("desert"), Set.of("meat"), 500, 0), "사는 곳이 다르다");
            assertThrows(DomainException.class, () -> w.s.pets.tame(p, "bear", Set.of("frozen"), Set.of("fish"), 500, 0), "길들이기 숙련 부족");
            var fail = w.s.pets.tame(p, "wolf", forest, Set.of("meat"), 0, 0.99);
            assertFalse(fail.success());
            assertTrue(w.s.growth.xp(p, "taming") > 0, "실패해도 배운다");
            var ok = w.s.pets.tame(p, "wolf", forest, Set.of("meat"), 1000, 0.0);
            assertTrue(ok.success());
            String id = ok.pet().id();
            assertEquals(1, w.s.growth.counter(p, "pet.tamed"));
            assertTrue(w.s.progress.discovered(p, "achievement", "first_pet"));
            var st = w.s.pets.summon(p, id);
            assertEquals(20, st.maxHealth());
            assertTrue(st.skills().contains("BITE"));
            w.s.pets.rename(p, id, "바람");
            assertThrows(DomainException.class, () -> w.s.pets.rename(p, id, "&c빨강"), "색 코드 금지");
            var grown = w.s.pets.gainXp(p, id, 20_000);
            assertTrue(grown.level() >= 10, "레벨 " + grown.level());
            assertTrue(w.s.pets.summon(p, id).skills().contains("HOWL"));
            assertEquals(grown.level(), w.s.growth.counter(p, "pet.level"), "가장 높은 펫 레벨 기록");
            assertTrue(w.s.pets.feed(p, id, Set.of("meat"), 1000) >= 70);
            w.s.pets.faint(p, id);
            assertThrows(DomainException.class, () -> w.s.pets.summon(p, id), "쓰러지면 5분 쉰다");
            w.now.addAndGet(5 * 60_000L + 1);
            w.s.pets.summon(p, id);
            assertThrows(DomainException.class, () -> w.s.pets.summon(TestWorld.player(), id), "남의 펫");
            // 데리고 다닐 수 있는 수: 2 + 길들이기/10
            w.s.pets.tame(p, "cat", Set.of("city"), Set.of("fish"), 1000, 0.0);
            assertThrows(DomainException.class, () -> w.s.pets.tame(p, "wolf", forest, Set.of("meat"), 1000, 0.0), "가득 찼다");
            w.s.pets.release(p, id);
            assertEquals(1, w.s.pets.pets(p).size());
        }
    }

    // ------------------------------------------------------------------ 이동
    @Test
    void mountsCarriagesAndShipsCostMoneyTakeTimeAndRespectWeather() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            var net = w.s.travel.network();
            assertTrue(net.all().stream().filter(r -> r.kind() == Route.Kind.CARRIAGE).count() >= 60, "마차 노선");
            assertTrue(net.all().stream().filter(r -> r.kind() == Route.Kind.SHIP).count() >= 4, "배 노선");
            for (Route r : net.all()) assertTrue(net.route(Route.id(r.kind(), r.to(), r.from())).isPresent(), "양방향: " + r.id());
            // 항구마다 선장이 산다
            for (String port : net.ports())
                assertTrue(w.s.content.npcs().stream().anyMatch(n -> n.region().equals(port) && w.s.npcWorld.offers(n.id(), "SHIP")), "선장 없는 항구: " + port);
            // 탈것
            String stable = npcWith(w, "STABLE");
            w.s.economy.deposit(p, 100_000, "test", "m0");
            var m = w.s.travel.buy(p, stable, "war_horse", "m1");
            assertEquals(100_000 - w.s.travel.kind("war_horse").price(), w.s.economy.balance(p));
            assertThrows(DomainException.class, () -> w.s.travel.buy(p, npcWith(w, "CARRIAGE"), "pony", "m2"), "마부는 말을 팔지 않는다");
            assertThrows(DomainException.class, () -> w.s.travel.summon(p, m.id()), "승마 15 부족");
            level(w, p, "riding", 31);
            var ride = w.s.travel.summon(p, m.id());
            assertTrue(ride.speed() > ride.kind().speed(), "숙련만큼 빠르다");
            w.s.travel.rode(p, 3);
            assertEquals(3, w.s.growth.counter(p, "ride.distance"));
            // 마차
            Route c = net.all().stream().filter(r -> r.kind() == Route.Kind.CARRIAGE).findFirst().orElseThrow();
            String coach = w.s.content.npcs().stream().filter(n -> n.region().equals(c.from()) && w.s.npcWorld.offers(n.id(), "CARRIAGE"))
                    .map(n -> n.id()).findFirst().orElse(npcWith(w, "CARRIAGE"));
            assertThrows(DomainException.class, () -> w.s.travel.depart(p, coach, c.id(), "nowhere", "t0"), "출발 도시에서만");
            long bal = w.s.economy.balance(p);
            var j = w.s.travel.depart(p, coach, c.id(), c.from(), "t1");
            assertEquals(bal - c.fare() + w.s.achievements.achievement("first_carriage").money(), w.s.economy.balance(p), "요금 (첫 마차 업적 보상은 따로)");
            assertEquals(c.durationMs(), j.arriveAt() - j.departAt());
            assertThrows(DomainException.class, () -> w.s.travel.depart(p, coach, c.id(), c.from(), "t2"), "이미 여행 중");
            assertTrue(w.s.travel.arrive(p).isEmpty(), "아직 도착 전");
            w.now.addAndGet(c.durationMs());
            assertEquals(c.to(), w.s.travel.arrive(p).orElseThrow());
            assertTrue(w.s.travel.journey(p).isEmpty());
            assertEquals(1, w.s.growth.counter(p, "travel.carriage"));
            // 배: 폭풍 · 눈보라 · 모래폭풍이면 뜨지 않는다
            Route s = net.all().stream().filter(r -> r.kind() == Route.Kind.SHIP).findFirst().orElseThrow();
            String captain = w.s.content.npcs().stream().filter(n -> n.region().equals(s.from()) && w.s.npcWorld.offers(n.id(), "SHIP")).map(n -> n.id()).findFirst().orElseThrow();
            boolean sawStorm = false, sailed = false;
            for (int i = 0; i < 400 && !(sawStorm && sailed); i++) {
                WeatherKind k = w.s.weather.at(s.from());
                String key = "s" + i;
                if (k.indoor()) {
                    assertThrows(DomainException.class, () -> w.s.travel.depart(p, captain, s.id(), s.from(), key));
                    sawStorm = true;
                } else if (!sailed) {
                    var sj = w.s.travel.depart(p, captain, s.id(), s.from(), key);
                    w.now.set(sj.arriveAt());
                    assertEquals(s.to(), w.s.travel.arrive(p).orElseThrow());
                    sailed = true;
                }
                w.now.addAndGet(w.s.weather.clock().windowMs());
            }
            assertTrue(sailed, "맑은 날엔 배가 뜬다");
            assertTrue(sawStorm, "폭풍인 날도 온다");
        }
    }

    // ------------------------------------------------------------------ 날씨
    @Test
    void weatherIsDeterministicRegionalAndChangesGathering() throws Exception {
        try (TestWorld w = new TestWorld()) {
            var desert = w.s.regions.byId("sand_sea");
            assertEquals("desert", w.s.weather.clock().climate(desert).id());
            assertEquals("temperate", w.s.weather.clock().climate(w.s.regions.byId("dawn_city")).id());
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 200; i++) {
                seen.add(w.s.weather.at("sand_sea").id());
                w.now.addAndGet(w.s.weather.clock().windowMs());
            }
            assertTrue(seen.contains("sandstorm") && seen.contains("clear"), "사막: " + seen);
            assertFalse(seen.contains("snow"), "사막엔 눈이 오지 않는다");
            var next = w.s.weather.next("sand_sea");
            assertTrue(next.getValue() > w.now.get());
            assertNotEquals(w.s.weather.at("sand_sea").id(), next.getKey().id(), "예보는 다른 날씨");
            String before = w.s.weather.at("sand_sea").id();
            w.s.weather.reseed(12345);
            String other = w.s.weather.at("sand_sea").id();
            w.s.weather.reseed(12345);
            assertEquals(other, w.s.weather.at("sand_sea").id(), "같은 시드 = 같은 날씨");
            assertNotNull(before);
            // 비 오는 날 낚시가 잘 된다
            assertEquals(1.3, w.s.weather.clock().kind("rain").gather("fishing"), 1e-9);
            assertTrue(w.s.weather.clock().kind("storm").ranged() < 1, "폭풍엔 활이 약하다");
            assertTrue(w.s.weather.clock().kind("storm").indoor(), "폭풍엔 NPC 가 집으로");
        }
    }

    // ------------------------------------------------------------------ 레이드
    @Test
    void raidsCheckRosterLockWeeklyAndRecordFastestClear() throws Exception {
        try (TestWorld w = new TestWorld()) {
            var d = w.s.raids.raid("colossus_ruins");
            List<String> team = new ArrayList<>();
            Map<String, String> where = new HashMap<>(), names = new HashMap<>();
            for (int i = 0; i < d.minPlayers(); i++) {
                String u = TestWorld.player();
                team.add(u);
                where.put(u, d.region());
                names.put(u, "P" + i);
                level(w, u, "swordsmanship", d.mastery());
            }
            assertThrows(DomainException.class, () -> w.s.raids.start(d.id(), team.get(0), team.subList(0, 2), where, names), "인원 부족");
            where.put(team.get(1), "dawn_city");
            assertThrows(DomainException.class, () -> w.s.raids.start(d.id(), team.get(0), team, where, names), "지역 밖");
            where.put(team.get(1), d.region());
            level(w, team.get(2), "swordsmanship", 1);
            assertThrows(DomainException.class, () -> w.s.raids.start(d.id(), team.get(0), team, where, names), "숙련 부족");
            level(w, team.get(2), "archery", d.mastery());
            var run = w.s.raids.start(d.id(), team.get(0), team, where, names);
            assertThrows(DomainException.class, () -> w.s.raids.start(d.id(), team.get(0), team, where, names), "한 번에 한 공격대");
            w.now.addAndGet(5 * 60_000L);
            assertTrue(w.s.raids.cleared(run.runId()), "첫 공략 = 최고 기록");
            for (String u : team) {
                assertTrue(w.s.raids.lockedOut(u, d.id()));
                assertTrue(w.s.economy.balance(u) >= d.money());
                assertTrue(w.s.progress.discovered(u, "title", d.title()), "레이드 칭호");
                assertEquals(1, w.s.growth.counter(u, "raid.cleared"));
            }
            assertEquals(5 * 60_000L, w.s.raids.best(d.id(), 3).get(0).durationMs());
            assertThrows(DomainException.class, () -> w.s.raids.start(d.id(), team.get(0), team, where, names), "이번 주 귀속");
            w.now.addAndGet(7L * 86_400_000L);
            var again = w.s.raids.start(d.id(), team.get(0), team, where, names);
            w.now.addAndGet(d.timeLimitMs() + 1);
            assertEquals(List.of(again), w.s.raids.expired(), "시간이 다 되면 물러난다");
            assertThrows(DomainException.class, () -> w.s.raids.cleared(again.runId()));
            assertFalse(w.s.raids.lockedOut(team.get(0), d.id()), "실패는 귀속되지 않는다");
        }
    }

    // ------------------------------------------------------------------ 대형 조각
    @Test
    void artworksUseSeveralMaterialsRefundOnFailureAndAreAdmiredDaily() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), v = TestWorld.player();
            var k = w.s.artworks.kind("statue");
            List<ArtworkService.Pick> picks = List.of(new ArtworkService.Pick("pedestal", "sandstone_block", 600, 8),
                    new ArtworkService.Pick("body", "marble_block", 800, 16), new ArtworkService.Pick("accent", "silver_ingot", 900, 3));
            assertThrows(DomainException.class, () -> w.s.artworks.create(a, "statue", picks, "world", 0, 70, 0, 0, "새벽의 기사", "dawn_city", 0.5));
            assertEquals(3, w.s.items.pendingBulk(a).size(), "실패하면 재료를 모두 돌려준다");
            level(w, a, "sculpting", 31);
            List<ArtworkService.Pick> wrong = List.of(picks.get(0), new ArtworkService.Pick("body", "oak_timber", 800, 16), picks.get(2));
            assertThrows(DomainException.class, () -> w.s.artworks.create(a, "statue", wrong, "world", 0, 70, 0, 0, "x", null, 0.5), "몸체에 나무는 안 된다");
            var art = w.s.artworks.create(a, "statue", picks, "world", 100, 70, 100, 90, "새벽의 기사", "dawn_city", 0.5);
            assertTrue(art.quality() > 750 && art.quality() <= 1000, "품질 " + art.quality());
            assertEquals(Map.of("pedestal", "sandstone", "body", "marble", "accent", "silver"), ArtworkService.looks(art), "재료마다 모양");
            assertEquals(1, w.s.growth.counter(a, "art.created"));
            assertTrue(w.s.reputation.standing(a).fame() >= Math.round(k.fame() * art.quality() / 1000.0));
            assertThrows(DomainException.class, () -> w.s.artworks.create(a, "statue", picks, "world", 103, 70, 100, 0, "옆", null, 0.5), "너무 가깝다");
            // 감상: 하루 한 번
            long fame = w.s.reputation.standing(a).fame();
            var view = w.s.artworks.view(v, art.id(), "dawn_city");
            assertTrue(view.fresh() && view.buffMinutes() >= 10 && view.buffLevel() >= 2);
            assertFalse(w.s.artworks.view(v, art.id(), "dawn_city").fresh(), "하루 한 번");
            assertFalse(w.s.artworks.view(a, art.id(), "dawn_city").fresh(), "자기 작품은 효과 없음");
            assertEquals(fame + 1, w.s.reputation.standing(a).fame(), "감상받으면 명성 +1");
            w.now.addAndGet(86_400_000L);
            assertTrue(w.s.artworks.view(v, art.id(), null).fresh(), "다음 날 다시");
            // 이름 짓기: 만든 사람만, 저장 · 목록에도 반영
            assertThrows(DomainException.class, () -> w.s.artworks.rename(v, art.id(), "남의 작품"));
            assertThrows(DomainException.class, () -> w.s.artworks.rename(a, art.id(), "&c색"));
            assertEquals("달빛 아래의 기사", w.s.artworks.rename(a, art.id(), "  달빛 아래의 기사 ").title());
            assertEquals("달빛 아래의 기사", w.s.artworks.all().stream().filter(x -> x.id().equals(art.id())).findFirst().orElseThrow().title());
            // 달빛 조각품: 표시가 남고, 모양 · 허물기 환급에는 끼지 않는다
            assertFalse(ArtworkService.moonlit(art));
            var moonArt = w.s.artworks.markMoonlit(a, art.id());
            assertTrue(ArtworkService.moonlit(moonArt));
            assertEquals(Map.of("pedestal", "sandstone", "body", "marble", "accent", "silver"), ArtworkService.looks(moonArt));
            assertEquals(1, w.s.artworks.mine(a).size());
            assertTrue(w.s.artworks.mine(v).isEmpty());
            // 허물기: 만든 사람만, 재료 절반
            assertThrows(DomainException.class, () -> w.s.artworks.remove(v, art.id(), false));
            int before = w.s.items.pendingBulk(a).size();
            w.s.artworks.remove(a, art.id(), false);
            assertEquals(before + 3, w.s.items.pendingBulk(a).size(), "재료 세 가지의 절반");
            assertTrue(w.s.artworks.all().isEmpty());
        }
    }
}
