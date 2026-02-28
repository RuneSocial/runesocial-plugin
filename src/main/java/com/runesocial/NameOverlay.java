package com.runesocial;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import java.awt.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NameOverlay extends Overlay {


    @Inject private Client client;
    @Inject private RuneSocialConfig config;
    @Inject private PetNameManager petNameManager;
    @Inject private RuneSocialPlugin plugin;




    @Inject
    public NameOverlay(Client client) {
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
    }

    private String getEmoticonText(String emoticon) {
        switch (emoticon != null ? emoticon : "NONE") {
            case "HAPPY":     return ":)";
            case "SAD":       return ":(";
            case "CRYING":    return ":'(";
            case "ANGRY":     return ">:(";
            case "SURPRISED": return ":O";
            case "WINK":      return ";)";
            case "COOL":      return "B)";
            case "DEAD":      return "x)";
            case "LOVE":      return ":*";
            case "SKEPTICAL": return ":/";
            case "HEART":      return "<3";
            case "XD":         return "xD";
            case "UWU":        return "uwu";
            case "TONGUE":     return ":P";
            case "BLUSHING":   return ":$";
            case "SMILE_ALT":  return "=)";
            case "STARSTRUCK": return "*.*";
            case "ANNOYED":    return "-.-";
            case "DEADPAN":    return "-_-";
            case "SHIFTY":     return ">.>";
            case "FRUSTRATED": return ">.<";
            case "BEAK":       return ":v";
            case "BLANK":      return "._. ";
            case "SLEEPY":     return "zzZ";
            case "WOW_LEFT":   return "o.O";
            case "WOW_RIGHT":  return "o.o";
            case "ANXIOUS": return ":s";
            case "CHILL":   return "~_~";
            default:          return null;
        }
    }






    private void drawEmoticon(Graphics2D graphics, String emoticon, Point textLocation, String name) {
        String text = getEmoticonText(emoticon);
        if (text == null) return;

        FontMetrics fm = graphics.getFontMetrics();
        int nameWidth = fm.stringWidth(name);
        int emoticonWidth = fm.stringWidth(text);

        int x = textLocation.getX() + (nameWidth / 2) - (emoticonWidth / 2);
        int y = textLocation.getY() - 12;

        graphics.setColor(Color.BLACK);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(Color.YELLOW);
        graphics.drawString(text, x, y);
    }



    private Color hexToColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return Color.WHITE;
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) return null;

        // =====================================
        // TU PROPIO NOMBRE
        // =====================================
        renderLocalPlayer(graphics, localPlayer);

        // =====================================
        // JUGADORES CERCANOS
        // =====================================
        for (Player player : client.getPlayers()) {
            if (player == null || player == localPlayer) continue;
            String name = player.getName();
            if (name == null) continue;

            PlayerProfile profile;
            synchronized (plugin.nearbyProfiles) {
                profile = plugin.nearbyProfiles.get(name);
            }

            if (profile != null) {
                renderOtherPlayer(graphics, player, profile);
            }
        }

        // =====================================
        // PETS
        // =====================================
        for (NPC npc : client.getNpcs()) {
            if (npc == null) continue;
            NPCComposition composition = npc.getComposition();
            if (composition != null && composition.isFollower()) {
                if (npc.getInteracting() == localPlayer ||
                        (npc.getName() != null && npc.getName().equals(localPlayer.getName()))) {
                    String customPetName = petNameManager.getPetName(npc.getId());
                    if (!customPetName.isEmpty()) {
                        drawPetName(graphics, npc, customPetName, config.petNameColor());
                    }
                }
            }
        }

        return null;
    }

    private void renderLocalPlayer(Graphics2D graphics, Player player) {
        String name = player.getName();
        Point textLocation = player.getCanvasTextLocation(graphics, name, player.getLogicalHeight() + 60);
        if (textLocation == null) return;

        double time = client.getGameCycle() / 5.0;

        drawEmoticon(graphics, config.emoticon().name(), textLocation, name);
        drawName(graphics, name, textLocation, config.nameColor(), config.movementEffect().name(), config.colorEffect().name(), time);
    }

    private void renderOtherPlayer(Graphics2D graphics, Player player, PlayerProfile profile) {
        String name = player.getName();
        Point textLocation = player.getCanvasTextLocation(graphics, name, player.getLogicalHeight() + 60);
        if (textLocation == null) return;

        double time = client.getGameCycle() / 5.0;
        Color nameColor = hexToColor(profile.nameColor != null ? profile.nameColor : "#FFFFFF");

        // Emoji del otro jugador
        if (profile.emoticon != null && !profile.emoticon.equals("NONE")) {
            drawEmoticon(graphics, profile.emoticon, textLocation, name);
        }

        drawName(graphics, name, textLocation, nameColor, profile.movementEffect, profile.colorEffect, time);


        // Pet del otro jugador
        if (profile.pets != null) {
            for (NPC npc : client.getNpcs()) {
                if (npc == null) continue;
                NPCComposition comp = npc.getComposition();
                if (comp != null && comp.isFollower()) {
                    PlayerProfile.PetInfo petInfo = profile.pets.get(String.valueOf(npc.getId()));
                    if (petInfo != null && petInfo.name != null && !petInfo.name.isEmpty()) {
                        Color petColor = hexToColor(petInfo.color != null ? petInfo.color : "#FF00FF");
                        drawPetName(graphics, npc, petInfo.name, petColor);
                    }
                }
            }
        }
    }




    private Color[] getGradientColors(String effect) {
        switch (effect) {
            case "GRADIENT_VERT":    return new Color[]{Color.WHITE,                new Color(80, 80, 80)};
            case "GRADIENT_PINK": return new Color[]{new Color(255, 100, 180), new Color(220, 50, 120)};
            case "GRADIENT_PURPLE":  return new Color[]{new Color(180, 80, 255),    new Color(60, 0, 100)};
            case "GRADIENT_BLUE":    return new Color[]{new Color(100, 180, 255),   new Color(0, 30, 120)};
            case "GRADIENT_RED":     return new Color[]{new Color(255, 60, 60),     new Color(100, 0, 0)};
            case "GRADIENT_GREEN":   return new Color[]{new Color(120, 255, 80),    new Color(0, 80, 0)};
            case "GRADIENT_YELLOW":  return new Color[]{new Color(255, 240, 80),    new Color(180, 120, 0)};
            case "GRADIENT_DARK": return new Color[]{new Color(30, 30, 30), new Color(160, 160, 160)};
            default:                 return new Color[]{Color.WHITE,                Color.GRAY};
        }
    }

    private void drawName(Graphics2D graphics, String name, Point textLocation,
                          Color nameColor, String movementEffect, String colorEffect, double time) {

        Color[] letterColors = getLetterColors(name, nameColor, colorEffect, time);
        FontMetrics fm = graphics.getFontMetrics();
        int letterHeight = fm.getAscent();

        if ("UPSIDE_DOWN".equals(movementEffect)) {
            int yOffset = (int) (Math.sin(time) * 10);
            int xOffset = textLocation.getX();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                int charWidth = fm.charWidth(c);
                int y = textLocation.getY() + yOffset;
                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + 1, y + 1);
                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, xOffset, y, grad[0], grad[1], letterHeight);
                } else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), xOffset, y);
                }
                xOffset += charWidth;

            }
        } else if ("WAVE".equals(movementEffect)) {
            int xOffset = textLocation.getX();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                double phase = time + i * 0.6;
                int letterYOffset = (int) (Math.sin(phase) * 8);
                int y = textLocation.getY() + letterYOffset;
                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + 1, y + 1);
                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, xOffset, y, grad[0], grad[1], letterHeight);
                } else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), xOffset, y);
                }
                xOffset += fm.charWidth(c);
            }

        } else if ("DECODE".equals(movementEffect)) {
            drawDecodeName(graphics, name, textLocation, letterColors, colorEffect, time);


    } else if ("SHAKE".equals(movementEffect)) {
        int xOffset = textLocation.getX();
        int shakeSeed = (int)(time / 0.75);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            java.util.Random r = new java.util.Random(shakeSeed + i * 17);
            int dx = r.nextInt(5) - 2;
            int dy = r.nextInt(5) - 2;
            graphics.setColor(Color.BLACK);
            graphics.drawString(String.valueOf(c), xOffset + dx + 1, textLocation.getY() + dy + 1);
            if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                Color[] grad = getGradientColors(colorEffect);
                drawLetterWithGradient(graphics, c, xOffset + dx, textLocation.getY() + dy, grad[0], grad[1], letterHeight);
            } else {
                graphics.setColor(letterColors[i]);
                graphics.drawString(String.valueOf(c), xOffset + dx, textLocation.getY() + dy);
            }
            xOffset += fm.charWidth(c);
        }
    } else if ("BOUNCE".equals(movementEffect)) {
        int xOffset = textLocation.getX();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            double phase = time * 0.15 + i * 0.8;
            int dy = (int)(Math.abs(Math.sin(phase)) * -3);
            graphics.setColor(Color.BLACK);
            graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + dy + 1);
            if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                Color[] grad = getGradientColors(colorEffect);
                drawLetterWithGradient(graphics, c, xOffset, textLocation.getY() + dy, grad[0], grad[1], letterHeight);
            } else {
                graphics.setColor(letterColors[i]);
                graphics.drawString(String.valueOf(c), xOffset, textLocation.getY() + dy);
            }
            xOffset += fm.charWidth(c);
        }
    } else if ("FLOAT".equals(movementEffect)) {
            int xOffset = textLocation.getX();
            int dy = (int) (Math.sin(time * 0.09) * 6);
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + dy + 1);
                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, xOffset, textLocation.getY() + dy, grad[0], grad[1], letterHeight);
                } else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), xOffset, textLocation.getY() + dy);
                }
                xOffset += fm.charWidth(c);
            }

        }
        else {
            int xOffset = textLocation.getX();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                int charWidth = fm.charWidth(c);
                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + 1);
                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, xOffset, textLocation.getY(), grad[0], grad[1], letterHeight);
                }
                else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), xOffset, textLocation.getY());
                }
                xOffset += charWidth;
            }
        }
    }


    private static final String RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%&";
    private static final java.util.Random random = new java.util.Random();

    private void drawDecodeName(Graphics2D graphics, String name, Point textLocation,
                                Color[] letterColors, String colorEffect, double time) {
        FontMetrics fm = graphics.getFontMetrics();
        int letterHeight = fm.getAscent();

        double cycle = time % 30.0;

        // Cambia letra aleatoria cada 0.2 segundos en vez de cada frame
        int randomSeed = (int)(time / 1.0);

        int xOffset = textLocation.getX();
        for (int i = 0; i < name.length(); i++) {
            char display;

            if (cycle < 9.0) {
                java.util.Random r = new java.util.Random(randomSeed + i * 31);
                display = RANDOM_CHARS.charAt(r.nextInt(RANDOM_CHARS.length()));
            } else if (cycle < 18.0) {
                double revealProgress = (cycle - 9.0) / 9.0;
                int revealedCount = (int) (revealProgress * name.length());
                if (i < revealedCount) {
                    display = name.charAt(i);
                } else {
                    java.util.Random r = new java.util.Random(randomSeed + i * 31);
                    display = RANDOM_CHARS.charAt(r.nextInt(RANDOM_CHARS.length()));
                }
            } else {
                display = name.charAt(i);
            }

            graphics.setColor(Color.BLACK);
            graphics.drawString(String.valueOf(display), xOffset + 1, textLocation.getY() + 1);

            if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                Color[] grad = getGradientColors(colorEffect);
                drawLetterWithGradient(graphics, display, xOffset, textLocation.getY(), grad[0], grad[1], letterHeight);
            } else {
                graphics.setColor(letterColors[i]);
                graphics.drawString(String.valueOf(display), xOffset, textLocation.getY());
            }

            xOffset += fm.charWidth(name.charAt(i));
        }
    }

    private Color[] getLetterColors(String name, Color baseColor, String colorEffect, double time) {
        Color[] colors = new Color[name.length()];

        for (int i = 0; i < name.length(); i++) {
            switch (colorEffect != null ? colorEffect : "NONE") {
                case "RAINBOW": {
                    float hue = (float) ((time * 0.05 + i * 0.1) % 1.0);
                    colors[i] = Color.getHSBColor(hue, 1.0f, 1.0f);
                    break;
                }
                case "FIRE": {
                    float hue = (float) ((time * 0.03 + i * 0.05) % 0.15);
                    colors[i] = Color.getHSBColor(hue, 1.0f, 1.0f);
                    break;
                }
                case "ICE": {
                    float hue = (float) (0.55 + Math.sin(time * 0.05 + i * 0.3) * 0.05);
                    float brightness = (float) (0.7 + Math.sin(time * 0.1 + i * 0.2) * 0.3);
                    colors[i] = Color.getHSBColor(hue, 0.6f, brightness);
                    break;
                }
                case "PULSE": {
                    float alpha = (float) (0.5 + Math.sin(time * 0.1) * 0.5);
                    colors[i] = blendColors(baseColor, Color.WHITE, alpha);
                    break;
                }
                case "DISCO": {
                    float hue = (float) ((Math.sin(time * 0.2 + i * 1.7) + 1) / 2.0);
                    colors[i] = Color.getHSBColor(hue, 1.0f, 1.0f);
                    break;
                }
                case "NEON": {
                    float hue = (float) ((time * 0.02 + i * 0.08) % 1.0);
                    float brightness = (float) (0.8 + Math.sin(time * 0.3 + i) * 0.2);
                    colors[i] = Color.getHSBColor(hue, 1.0f, brightness);
                    break;
                }
                case "GRADIENT_BW": {
                    float ratio = name.length() > 1 ? (float) i / (name.length() - 1) : 0;
                    colors[i] = blendColors(Color.GRAY, Color.WHITE, ratio);
                    break;
                }
                case "LAVA": {
                    float phase = (float) ((time * 0.05 + i * 0.15) % 1.0);
                    if (phase < 0.5f) {
                        colors[i] = blendColors(new Color(180, 0, 0), new Color(255, 80, 0), phase * 2);
                    } else {
                        colors[i] = blendColors(new Color(255, 80, 0), new Color(255, 200, 0), (phase - 0.5f) * 2);
                    }
                    break;
                }
                case "GALAXY": {
                    float hue = (float) ((0.6 + Math.sin(time * 0.03 + i * 0.4) * 0.15) % 1.0);
                    float sat = (float) (0.5 + Math.sin(time * 0.07 + i * 0.3) * 0.3);
                    float bri = (float) (0.7 + Math.sin(time * 0.1 + i * 0.5) * 0.3);
                    colors[i] = Color.getHSBColor(hue, sat, bri);
                    break;
                }
                case "TOXIC": {
                    float bri = (float) (0.6 + Math.sin(time * 0.15 + i * 0.3) * 0.4);
                    colors[i] = Color.getHSBColor(0.33f, 1.0f, bri);
                    break;
                }
                case "BLOOD": {
                    float bri = (float) (0.4 + Math.sin(time * 0.08 + i * 0.2) * 0.3);
                    colors[i] = Color.getHSBColor(0.0f, 1.0f, bri);
                    break;
                }
                case "MATRIX": {
                    float bri = (float) (0.3 + Math.sin(time * 0.2 + i * 0.8) * 0.4);
                    colors[i] = Color.getHSBColor(0.33f, 1.0f, Math.max(0.2f, bri));
                    break;
                }
                case "GRADIENT_VERT":
                case "GRADIENT_PINK":
                case "GRADIENT_PURPLE":
                case "GRADIENT_BLUE":
                case "GRADIENT_RED":
                case "GRADIENT_GREEN":
                case "GRADIENT_YELLOW":
                case "GRADIENT_DARK":
                    colors[i] = baseColor;
                    break;
                default:
                    colors[i] = baseColor;
            }
        }
        return colors;
    }

    private Color blendColors(Color c1, Color c2, float ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        int r = (int) (c1.getRed() * (1 - ratio) + c2.getRed() * ratio);
        int g = (int) (c1.getGreen() * (1 - ratio) + c2.getGreen() * ratio);
        int b = (int) (c1.getBlue() * (1 - ratio) + c2.getBlue() * ratio);
        return new Color(r, g, b);
    }

    private void drawLetterWithGradient(Graphics2D graphics, char c, int x, int y,
                                        Color topColor, Color bottomColor, int letterHeight) {
        GradientPaint gradient = new GradientPaint(x, y - letterHeight, topColor, x, y, bottomColor);
        graphics.setPaint(gradient);
        graphics.drawString(String.valueOf(c), x, y);
        graphics.setPaint(null);
    }




    private void drawPetName(Graphics2D graphics, NPC pet, String petName, Color color) {
        if (petName == null || petName.isEmpty()) return;
        Point loc = pet.getCanvasTextLocation(graphics, petName, pet.getLogicalHeight() + 20);
        if (loc == null) return;
        graphics.setColor(Color.BLACK);
        graphics.drawString(petName, loc.getX() + 1, loc.getY() + 1);
        graphics.setColor(color);
        graphics.drawString(petName, loc.getX(), loc.getY());
    }
}