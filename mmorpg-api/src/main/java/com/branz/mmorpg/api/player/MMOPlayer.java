package com.branz.mmorpg.api.player;

import com.branz.mmorpg.api.stat.AttributeType;
import java.util.UUID;

/**
 * Interface representing a player profile in Branz MMORPG.
 */
public interface MMOPlayer {

    UUID getUniqueId();

    String getName();

    int getLevel();

    long getExperience();

    double getAttributeValue(AttributeType attributeType);

    double getCurrentHealth();

    void setCurrentHealth(double health);

    double getCurrentMana();

    void setCurrentMana(double mana);
}
