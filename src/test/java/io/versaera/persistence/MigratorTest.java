package io.versaera.persistence;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MigratorTest {
    @Test
    void appliesRealMigrationsOnceAndIsIdempotent() throws Exception {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            Migrator m = new Migrator(db);
            List<Migrator.Migration> list = Migrator.fromClasspath(getClass().getClassLoader());
            assertEquals(list.size(), m.migrate(list));
            assertEquals(0, m.migrate(list), "두 번째 실행은 아무것도 적용하지 않아야 함");
            assertEquals(list.size(), m.currentVersion());
        }
    }

    /** V8: 옛 곡선(100 × L^1.6)의 경험치가 새 곡선의 같은 레벨 · 같은 진행률로 옮겨진다 */
    @Test
    void progressionCurveMigrationKeepsLevels() throws Exception {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            Migrator m = new Migrator(db);
            List<Migrator.Migration> all = Migrator.fromClasspath(getClass().getClassLoader());
            m.migrate(all.stream().filter(x -> x.version() < 8).toList());
            long[] oldCum = new long[32];
            for (int lv = 1; lv < 31; lv++) oldCum[lv + 1] = oldCum[lv] + Math.round(100 * Math.pow(lv, 1.6));
            try (var st = db.connection().createStatement()) {
                for (int lv : new int[]{1, 5, 10, 11, 20, 21, 30})
                    st.execute("INSERT INTO mastery (uuid, discipline, xp) VALUES ('p" + lv + "', 'swordsmanship', " + (oldCum[lv] + (oldCum[lv + 1] - oldCum[lv]) / 2) + ")");
                st.execute("INSERT INTO mastery (uuid, discipline, xp) VALUES ('master', 'swordsmanship', " + oldCum[31] + ")");
            }
            m.migrate(all);
            try (var st = db.connection().createStatement(); var rs = st.executeQuery("SELECT uuid, xp FROM mastery")) {
                while (rs.next()) {
                    String u = rs.getString(1);
                    long xp = rs.getLong(2);
                    int want = u.equals("master") ? 31 : Integer.parseInt(u.substring(1));
                    assertEquals(want, io.versaera.domain.skill.Mastery.levelOf(xp), u + " xp " + xp);
                    if (want < 31) assertEquals(0.5, io.versaera.domain.skill.Mastery.progress(xp), 0.02, u + " 진행률");
                }
            }
        }
    }

    @Test
    void moneyUnitsMigrationKeepsValue() throws Exception {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            Migrator m = new Migrator(db);
            List<Migrator.Migration> all = Migrator.fromClasspath(getClass().getClassLoader());
            m.migrate(all.stream().filter(x -> x.version() < 10).toList());
            try (var st = db.connection().createStatement()) {
                st.execute("INSERT INTO wallet (uuid, balance) VALUES ('p', 123)");
            }
            m.migrate(all);
            try (var st = db.connection().createStatement(); var rs = st.executeQuery("SELECT balance FROM wallet WHERE uuid = 'p'")) {
                assertTrue(rs.next());
                assertEquals(123 * io.versaera.domain.economy.Money.SILVER, rs.getLong(1), "예전 123 = 123 실버");
            }
        }
    }

    @Test
    void refusesWhenAppliedMigrationWasEdited() throws Exception {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            Migrator m = new Migrator(db);
            m.migrate(List.of(new Migrator.Migration(1, "V1__a.sql", "CREATE TABLE a (x INTEGER);")));
            Migrator.MigrationException ex = assertThrows(Migrator.MigrationException.class,
                    () -> m.migrate(List.of(new Migrator.Migration(1, "V1__a.sql", "CREATE TABLE a (x INTEGER, y INTEGER);"))));
            assertTrue(ex.getMessage().contains("바뀌었습니다"));
        }
    }

    @Test
    void failedMigrationRollsBackCompletely() throws Exception {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            Migrator m = new Migrator(db);
            assertThrows(Migrator.MigrationException.class, () -> m.migrate(List.of(
                    new Migrator.Migration(1, "V1__bad.sql", "CREATE TABLE ok (x INTEGER); CREATE TABLE broken ("))));
            assertEquals(0, m.currentVersion());
            assertEquals(1, m.migrate(List.of(new Migrator.Migration(1, "V1__good.sql", "CREATE TABLE ok (x INTEGER);"))),
                    "실패한 마이그레이션의 앞부분(테이블 ok)이 남아 있으면 안 됨");
        }
    }

    @Test
    void rejectsGapsInNumbering() {
        Map<String, String> files = Map.of("migrations.txt", "V1__a.sql\nV3__c.sql\n", "V1__a.sql", "SELECT 1;", "V3__c.sql", "SELECT 1;");
        assertThrows(Migrator.MigrationException.class, () -> Migrator.load(n -> files.containsKey(n)
                ? new ByteArrayInputStream(files.get(n).getBytes(StandardCharsets.UTF_8)) : null));
    }
}
