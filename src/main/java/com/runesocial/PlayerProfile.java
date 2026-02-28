package com.runesocial;

import java.util.Map;

public class PlayerProfile {
    public String username;
    public String nameColor;
    public String colorEffect;
    public String emoticon;
    public String movementEffect;
    public Map<String, PetInfo> pets;

    public static class PetInfo {
        public String name;
        public String color;
    }
}