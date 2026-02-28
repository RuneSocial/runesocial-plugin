package com.runesocial;

import net.runelite.client.config.ConfigManager;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;

@Singleton
public class PetNameManager
{
    private static final String CONFIG_GROUP = "runesocial";

    @Inject
    private ConfigManager configManager;

    public void setPetName(int npcId, String name)
    {
        configManager.setConfiguration(CONFIG_GROUP, "petname_" + npcId, name);
    }

    public String getPetName(int npcId)
    {
        String name = configManager.getConfiguration(CONFIG_GROUP, "petname_" + npcId);
        return name != null ? name : "";
    }

    public void setPetColor(int npcId, Color color)
    {
        configManager.setConfiguration(CONFIG_GROUP, "petcolor_" + npcId, color.getRGB());
    }

    public Color getPetColor(int npcId, Color defaultColor)
    {
        String val = configManager.getConfiguration(CONFIG_GROUP, "petcolor_" + npcId);
        if (val == null) return defaultColor;
        try {
            return new Color(Integer.parseInt(val), true);
        } catch (NumberFormatException e) {
            return defaultColor;
        }
    }
}