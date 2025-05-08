package citu.jisaz.brightminds_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GamificationConfig {
    @Value("${gamification.base-xp-for-level-2:100}")
    private long baseXpForLevel2;

    @Value("${gamification.level-xp-multiplier:1.25}")
    private double levelXpMultiplier;

    @Value("${gamification.xp-increment-flat:50}")
    private long xpIncrementFlat;

    public long calculateXpForNextLevel(int currentLevel) {
        if (currentLevel <= 0) {
            return baseXpForLevel2;
        }
        return (long) (baseXpForLevel2 * Math.pow(levelXpMultiplier, currentLevel -1 ));

        // Alternative simpler formula:
        // return baseXpForLevel2 + ((long)currentLevel * xpIncrementFlat);
    }
}