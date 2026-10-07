package io.versaera.content;

import io.versaera.domain.achievement.Achievement;
import io.versaera.domain.achievement.Title;
import io.versaera.domain.art.ArtworkKind;
import io.versaera.domain.guild.GuildQuestDef;
import io.versaera.domain.pet.Species;
import io.versaera.domain.raid.RaidDefinition;
import io.versaera.domain.travel.MountKind;
import io.versaera.domain.travel.TravelNetwork;
import io.versaera.domain.weather.Climate;
import io.versaera.domain.weather.WeatherKind;

import java.util.List;
import java.util.Map;

/**
 * 모험 확장 콘텐츠 (V7): 업적 · 칭호 · 펫 · 탈것 · 노선망 · 날씨 · 레이드 · 대형 조각 · 길드 의뢰.
 *
 * @param guildWithdraw 길드 계급 → 하루 꺼내기 한도 (-1 = 없음)
 */
public record Expansion(List<Achievement> achievements, Map<String, Title> titles, List<Species> species, List<MountKind> mounts,
                        TravelNetwork travel, List<WeatherKind> weatherKinds, List<Climate> climates, long weatherWindowMs, int weatherCell,
                        List<RaidDefinition> raids, List<ArtworkKind> artworks, List<GuildQuestDef> guildQuests, Map<String, Integer> guildWithdraw,
                        int guildMaxKinds, List<io.versaera.domain.world.FieldMonster> monsters) {
    public static final List<String> FILES = List.of("achievements.yml", "pets.yml", "travel.yml", "weather.yml", "raids.yml", "artworks.yml",
            "guild_quests.yml", "monsters.yml");
}
