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
                    // DESPUÉS
                    if (plugin.activePetName != null && !plugin.activePetName.isEmpty()) {
                        Color petColor = plugin.activePetColor != null ? plugin.activePetColor : config.petNameColor();
                        drawPetName(graphics, npc, plugin.activePetName, petColor);
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
    }
        else if ("TYPEWRITER".equals(movementEffect)) {
            int totalChars = name.length();
            double cycle = time % (totalChars + 8);
            int visible = (int) Math.min(cycle, totalChars);
            String partial = name.substring(0, visible);
            int xOffset = textLocation.getX();
            for (int i = 0; i < partial.length(); i++) {
                char c = partial.charAt(i);
                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + 1);
                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, xOffset, textLocation.getY(), grad[0], grad[1], letterHeight);
                } else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), xOffset, textLocation.getY());
                }
                xOffset += fm.charWidth(c);
            }

        } else if ("SWING".equals(movementEffect)) {
            int xOffset = textLocation.getX();
            int totalWidth = 0;
            for (char c : name.toCharArray()) totalWidth += fm.charWidth(c);
            int centerX = textLocation.getX() + totalWidth / 2;
            double angle = Math.sin(time * 0.08) * 0.25;
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                int charWidth = fm.charWidth(c);
                double relX = xOffset - centerX;
                int dy = (int)(relX * Math.sin(angle));
                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + dy + 1);
                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, xOffset, textLocation.getY() + dy, grad[0], grad[1], letterHeight);
                } else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), xOffset, textLocation.getY() + dy);
                }
                xOffset += charWidth;
            }

        } else if ("GLITCH".equals(movementEffect)) {
            int xOffset = textLocation.getX();
            int glitchSeed = (int)(time / 0.3);
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                java.util.Random r = new java.util.Random(glitchSeed + i * 73);
                boolean doGlitch = r.nextInt(4) == 0;
                int dx = doGlitch ? r.nextInt(7) - 3 : 0;
                int dy = doGlitch ? r.nextInt(7) - 3 : 0;
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

        } else if ("SPIN".equals(movementEffect)) {
            int xOffset = textLocation.getX();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                double phase = time * 0.1 + i * (2 * Math.PI / name.length());
                int dy = (int)(Math.sin(phase) * 6);
                int dx = (int)(Math.cos(phase) * 2);
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

        } else if ("TREMBLE".equals(movementEffect)) {
            int xOffset = textLocation.getX();
            int trembleSeed = (int)(time / 0.1);
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                java.util.Random r = new java.util.Random(trembleSeed + i * 11);
                int dx = r.nextInt(3) - 1;
                int dy = r.nextInt(3) - 1;
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

        } else if ("DRIFT".equals(movementEffect)) {
            int dx = (int)(Math.sin(time * 0.04) * 8);
            int xOffset = textLocation.getX() + dx;
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + 1);
                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, xOffset, textLocation.getY(), grad[0], grad[1], letterHeight);
                } else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), xOffset, textLocation.getY());
                }
                xOffset += fm.charWidth(c);
            }

        } else if ("RUBBER".equals(movementEffect)) {
            int xOffset = textLocation.getX();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                double phase = time * 0.12 + i * 0.4;
                double scaleY = 1.0 + Math.sin(phase) * 0.4;
                int charHeight = (int)(letterHeight * scaleY);
                int dy = letterHeight - charHeight;
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

        } else if ("FADE_LEFT".equals(movementEffect)) {
            int totalChars = name.length();
            double cycle = time % (totalChars * 2);
            int hiddenCount = (int)(cycle < totalChars ? cycle : totalChars * 2 - cycle);

            int xOffset = textLocation.getX();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (i < hiddenCount) {
                    xOffset += fm.charWidth(c);
                    continue;
                }
                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + 1);
                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, xOffset, textLocation.getY(), grad[0], grad[1], letterHeight);
                } else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), xOffset, textLocation.getY());
                }
                xOffset += fm.charWidth(c);
            }
        } else if ("FADE_RIGHT".equals(movementEffect)) {
            int totalChars = name.length();
            double cycle = time % (totalChars * 2);
            int hiddenCount = (int)(cycle < totalChars ? cycle : totalChars * 2 - cycle);

            int xOffset = textLocation.getX();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (i >= totalChars - hiddenCount) {
                    xOffset += fm.charWidth(c);
                    continue;
                }
                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + 1);
                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, xOffset, textLocation.getY(), grad[0], grad[1], letterHeight);
                } else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), xOffset, textLocation.getY());
                }
                xOffset += fm.charWidth(c);
            }

        } else if ("FADE".equals(movementEffect)) {
            double cycle = (Math.sin(time * 0.05) + 1) / 2.0; // 0.0 a 1.0
            int alpha = (int)(cycle * 255);

            int xOffset = textLocation.getX();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                Color base = letterColors[i];

                graphics.setColor(new Color(0, 0, 0, Math.min(alpha, 200)));
                graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + 1);

                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    Color top = new Color(grad[0].getRed(), grad[0].getGreen(), grad[0].getBlue(), alpha);
                    Color bot = new Color(grad[1].getRed(), grad[1].getGreen(), grad[1].getBlue(), alpha);
                    GradientPaint gradient = new GradientPaint(xOffset, textLocation.getY() - letterHeight, top, xOffset, textLocation.getY(), bot);
                    graphics.setPaint(gradient);
                    graphics.drawString(String.valueOf(c), xOffset, textLocation.getY());
                    graphics.setPaint(null);
                } else {
                    graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
                    graphics.drawString(String.valueOf(c), xOffset, textLocation.getY());
                }
                xOffset += fm.charWidth(c);
            }
        } else if ("FRACTURE".equals(movementEffect)) {
            int xOffset = textLocation.getX();
            int halfY = textLocation.getY() - letterHeight / 2;
            int offsetX = (int)(Math.sin(time * 0.04) * 12);

            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                int charWidth = fm.charWidth(c);

                // mitad de abajo - se queda fija
                Shape oldClip = graphics.getClip();
                graphics.setClip(xOffset, halfY, charWidth + 2, letterHeight + 4);
                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + 1);
                graphics.setColor(letterColors[i]);
                graphics.drawString(String.valueOf(c), xOffset, textLocation.getY());
                graphics.setClip(oldClip);

                // mitad de arriba - se desplaza
                graphics.setClip(xOffset + offsetX - 2, textLocation.getY() - letterHeight - 4, charWidth + 4, letterHeight / 2 + 4);
                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + offsetX + 1, textLocation.getY() + 1);
                graphics.setColor(letterColors[i]);
                graphics.drawString(String.valueOf(c), xOffset + offsetX, textLocation.getY());
                graphics.setClip(oldClip);

                xOffset += charWidth;
            }

        } else if ("SLIDE_Y".equals(movementEffect)) {
            double cycle = (time * 0.08) % 1.0;  // ← más rápido y lineal
            int dy = (int)(cycle * letterHeight * 2) - letterHeight;  // ← siempre de arriba hacia abajo

            int totalWidth = fm.stringWidth(name);
            Shape oldClip = graphics.getClip();
            graphics.setClip(textLocation.getX() - 2, textLocation.getY() - letterHeight, totalWidth + 4, letterHeight);

            int xOffset = textLocation.getX();
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

            graphics.setClip(oldClip);
        } else if ("SPIRAL_REVERSE".equals(movementEffect)) {
            FontMetrics fmOrig = graphics.getFontMetrics();
            int totalWidth = 0;
            for (char c : name.toCharArray()) totalWidth += fmOrig.charWidth(c);

            int centerX = textLocation.getX() + totalWidth / 2;
            int centerY = textLocation.getY() - letterHeight / 2;
            double radius = totalWidth * 0.4;

            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                double angle = (2 * Math.PI / name.length()) * i - time * 0.05;  // ← negativo
                int x = centerX + (int)(Math.cos(angle) * radius) - fmOrig.charWidth(c) / 2;
                int y = centerY + (int)(Math.sin(angle) * radius * 0.4);

                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), x + 1, y + 1);

                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, x, y, grad[0], grad[1], letterHeight);
                } else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), x, y);
                }
            }
        } else if ("SPIRAL".equals(movementEffect)) {
            FontMetrics fmOrig = graphics.getFontMetrics();
            int totalWidth = 0;
            for (char c : name.toCharArray()) totalWidth += fmOrig.charWidth(c);

            int centerX = textLocation.getX() + totalWidth / 2;
            int centerY = textLocation.getY() - letterHeight / 2;
            double radius = totalWidth * 0.4;

            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                double angle = (2 * Math.PI / name.length()) * i + time * 0.05;
                int x = centerX + (int)(Math.cos(angle) * radius) - fmOrig.charWidth(c) / 2;
                int y = centerY + (int)(Math.sin(angle) * radius * 0.4);

                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), x + 1, y + 1);

                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, x, y, grad[0], grad[1], letterHeight);
                } else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), x, y);
                }
            }
        } else if ("ZOOM".equals(movementEffect)) {
            int xOffset = textLocation.getX();
            double speed = 0.03;
            int activeIndex = (int)(time * speed * name.length()) % name.length();

            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                Font originalFont = graphics.getFont();

                boolean isActive = i == activeIndex;
                float scale = isActive ? 1.2f : 1.0f;
                Font scaledFont = originalFont.deriveFont(originalFont.getSize2D() * scale);
                graphics.setFont(scaledFont);
                FontMetrics fmScaled = graphics.getFontMetrics();

                int dy = isActive ? (int)(originalFont.getSize2D() * 0.1f) : 0;  // ← baja en vez de subir


                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + dy + 1);

                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, xOffset, textLocation.getY() + dy, grad[0], grad[1], (int)(letterHeight * scale));
                } else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), xOffset, textLocation.getY() + dy);
                }

                xOffset += fmScaled.charWidth(c);
                graphics.setFont(originalFont);
            }
        } else if ("ECHO".equals(movementEffect)) {
            int[][] offsets = {
                    {-16, -12}, {20, -8}, {-10, 16}, {12, 10}
            };
            float[] alphas = {0.55f, 0.65f, 0.60f, 0.50f};
            float[] scales = {0.75f, 0.85f, 0.90f, 0.70f};

            // Dibuja las copias primero
            Font originalFont = graphics.getFont();
            for (int s = 0; s < offsets.length; s++) {
                int dx = offsets[s][0];
                int dy = offsets[s][1];
                float scale = scales[s];
                int alpha = (int)(alphas[s] * 255);

                graphics.setFont(originalFont.deriveFont(originalFont.getSize2D() * scale));
                FontMetrics fmS = graphics.getFontMetrics();
                int xOffset = textLocation.getX() + dx;

                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    Color base = letterColors[i];
                    graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
                    graphics.drawString(String.valueOf(c), xOffset, textLocation.getY() + dy);
                    xOffset += fmS.charWidth(c);
                }
            }

            // Dibuja el nombre principal encima
            graphics.setFont(originalFont);
            int xOffset = textLocation.getX();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + 1);
                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, xOffset, textLocation.getY(), grad[0], grad[1], letterHeight);
                } else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), xOffset, textLocation.getY());
                }
                xOffset += fm.charWidth(c);
            }
            graphics.setFont(originalFont);
        }

        else if ("SCROLL".equals(movementEffect)) {
            int totalWidth = 0;
            for (char c : name.toCharArray()) totalWidth += fm.charWidth(c);

            int range = totalWidth + 40;
            double cycle = time * 1.5 % range;
            int dx = (int) cycle - totalWidth;

            int xOffset = textLocation.getX() + dx;
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                graphics.setColor(Color.BLACK);
                graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + 1);
                if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                    Color[] grad = getGradientColors(colorEffect);
                    drawLetterWithGradient(graphics, c, xOffset, textLocation.getY(), grad[0], grad[1], letterHeight);
                } else {
                    graphics.setColor(letterColors[i]);
                    graphics.drawString(String.valueOf(c), xOffset, textLocation.getY());
                }
                xOffset += fm.charWidth(c);
            }
        } else if ("FLIP".equals(movementEffect)) {
            double cycle = time % 4.0;
            boolean visible = cycle < 2.5;
            if (visible) {
                int xOffset = textLocation.getX();
                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    graphics.setColor(Color.BLACK);
                    graphics.drawString(String.valueOf(c), xOffset + 1, textLocation.getY() + 1);
                    if (colorEffect != null && colorEffect.startsWith("GRADIENT_")) {
                        Color[] grad = getGradientColors(colorEffect);
                        drawLetterWithGradient(graphics, c, xOffset, textLocation.getY(), grad[0], grad[1], letterHeight);
                    } else {
                        graphics.setColor(letterColors[i]);
                        graphics.drawString(String.valueOf(c), xOffset, textLocation.getY());
                    }
                    xOffset += fm.charWidth(c);
                }
            }

        } else if ("CASCADE".equals(movementEffect)) {
            int xOffset = textLocation.getX();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                double phase = time * 0.1 - i * 0.3;
                int dy = (int)(Math.sin(phase) * 10);
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



        else if ("FLOAT".equals(movementEffect)) {
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
                    case "GOLD": {
                        float bri = (float)(0.7 + Math.sin(time * 0.1 + i * 0.2) * 0.3);
                        colors[i] = Color.getHSBColor(0.13f, 1.0f, bri);
                        break;
                    }
                    case "OCEAN": {
                        float hue = (float)(0.5 + Math.sin(time * 0.04 + i * 0.2) * 0.08);
                        float bri = (float)(0.6 + Math.sin(time * 0.08 + i * 0.3) * 0.3);
                        colors[i] = Color.getHSBColor(hue, 0.8f, bri);
                        break;
                    }
                    case "SUNSET": {
                        float hue = (float)((0.05 + Math.sin(time * 0.03 + i * 0.15) * 0.05) % 1.0);
                        float sat = (float)(0.8 + Math.sin(time * 0.05) * 0.2);
                        colors[i] = Color.getHSBColor(hue, sat, 1.0f);
                        break;
                    }
                    case "VOID": {
                        float bri = (float)(0.3 + Math.sin(time * 0.08 + i * 0.5) * 0.2);
                        colors[i] = Color.getHSBColor(0.78f, 0.9f, bri);
                        break;
                    }
                    case "CANDY": {
                        float[] hues = {0.9f, 0.6f, 0.15f};
                        float hue = hues[(int)(time * 0.5 + i) % hues.length];
                        colors[i] = Color.getHSBColor(hue, 0.5f, 1.0f);
                        break;
                    }
                    case "THUNDER": {
                        float bri = (float)(0.7 + Math.sin(time * 0.3 + i * 0.7) * 0.3);
                        colors[i] = Math.random() > 0.95
                                ? Color.WHITE
                                : Color.getHSBColor(0.15f, 0.9f, bri);
                        break;
                    }
                    case "CORRUPTION": {
                        float bri = (float)(0.3 + Math.sin(time * 0.06 + i * 0.4) * 0.2);
                        colors[i] = Color.getHSBColor(0.33f, 1.0f, bri);
                        break;
                    }
                    case "ANGELIC": {
                        float bri = (float)(0.8 + Math.sin(time * 0.08 + i * 0.3) * 0.2);
                        colors[i] = Math.random() > 0.97
                                ? new Color(255, 215, 0)
                                : Color.getHSBColor(0.0f, 0.0f, bri);
                        break;
                    }
                    case "INFERNO": {
                        float phase = (float)((time * 0.06 + i * 0.1) % 1.0);
                        if (phase < 0.33f) {
                            colors[i] = blendColors(new Color(255, 0, 0), new Color(255, 100, 0), phase * 3);
                        } else if (phase < 0.66f) {
                            colors[i] = blendColors(new Color(255, 100, 0), new Color(255, 220, 0), (phase - 0.33f) * 3);
                        } else {
                            colors[i] = blendColors(new Color(255, 220, 0), new Color(255, 0, 0), (phase - 0.66f) * 3);
                        }
                        break;
                    }
                    case "DEEP_SEA": {
                        float hue = (float)(0.55 + Math.sin(time * 0.03 + i * 0.2) * 0.05);
                        float bri = (float)(0.4 + Math.sin(time * 0.1 + i * 0.5) * 0.2);
                        colors[i] = Math.random() > 0.97
                                ? new Color(0, 255, 255)
                                : Color.getHSBColor(hue, 1.0f, bri);
                        break;
                    }
                    case "SAKURA": {
                        float bri = (float)(0.8 + Math.sin(time * 0.06 + i * 0.3) * 0.2);
                        colors[i] = Color.getHSBColor(0.93f, 0.4f, bri);
                        break;
                    }
                    case "ASH": {
                        float bri = (float)(0.4 + Math.sin(time * 0.05 + i * 0.3) * 0.2);
                        colors[i] = Math.random() > 0.97
                                ? Color.WHITE
                                : Color.getHSBColor(0.0f, 0.0f, bri);
                        break;
                    }
                    case "POISON": {
                        float bri = (float)(0.5 + Math.sin(time * 0.2 + i * 0.4) * 0.4);
                        colors[i] = Color.getHSBColor(0.35f, 1.0f, bri);
                        break;
                    }
                    case "COSMIC": {
                        float hue = (float)((0.65 + Math.sin(time * 0.02 + i * 0.5) * 0.1) % 1.0);
                        float bri = (float)(0.5 + Math.sin(time * 0.08 + i * 0.7) * 0.3);
                        colors[i] = Math.random() > 0.97
                                ? Color.WHITE
                                : Color.getHSBColor(hue, 0.8f, bri);
                        break;
                    }
                    case "GLITCH_COLOR": {
                        int seed = (int)(time * 3 + i * 7);
                        java.util.Random r = new java.util.Random(seed);
                        colors[i] = new Color(r.nextInt(256), r.nextInt(256), r.nextInt(256));
                        break;
                    }
                    case "HOLOGRAM": {
                        float bri = (float)(0.7 + Math.sin(time * 0.15 + i * 0.4) * 0.3);
                        colors[i] = Math.random() > 0.97
                                ? Color.WHITE
                                : Color.getHSBColor(0.5f, 0.6f, bri);
                        break;
                    }
                    case "GLOW": {
                        float bri = (float)(0.5 + Math.sin(time * 0.08) * 0.5);
                        colors[i] = blendColors(baseColor, Color.WHITE, bri);
                        break;
                    }
                    case "NEON_PINK": {
                        float bri = (float)(0.7 + Math.sin(time * 0.15 + i * 0.2) * 0.3);
                        colors[i] = Color.getHSBColor(0.9f, 1.0f, bri);
                        break;
                    }
                    case "NEON_GREEN": {
                        float bri = (float)(0.6 + Math.sin(time * 0.15 + i * 0.2) * 0.4);
                        colors[i] = Color.getHSBColor(0.35f, 1.0f, bri);
                        break;
                    }
                    case "NEON_BLUE": {
                        float bri = (float)(0.7 + Math.sin(time * 0.15 + i * 0.2) * 0.3);
                        colors[i] = Color.getHSBColor(0.6f, 1.0f, bri);
                        break;
                    }
                    case "NEON_ORANGE": {
                        float bri = (float)(0.7 + Math.sin(time * 0.15 + i * 0.2) * 0.3);
                        colors[i] = Color.getHSBColor(0.08f, 1.0f, bri);
                        break;
                    }
                case "MATRIX": {
                    float bri = (float) (0.3 + Math.sin(time * 0.2 + i * 0.8) * 0.4);
                    colors[i] = Color.getHSBColor(0.33f, 1.0f, Math.max(0.2f, bri));
                    break;
                }
                case "SOFT_RAINBOW": {
                    float hue = (float)((time * 0.03 + i * 0.12) % 1.0);
                    colors[i] = Color.getHSBColor(hue, 0.35f, 1.0f);
                    break;
                }
                case "SOFT_MINT": {
                    float bri = (float)(0.85 + Math.sin(time * 0.06 + i * 0.3) * 0.1);
                    colors[i] = Color.getHSBColor(0.42f, 0.35f, bri);
                    break;
                }
                case "SOFT_LAVENDER": {
                    float bri = (float)(0.85 + Math.sin(time * 0.06 + i * 0.3) * 0.1);
                    colors[i] = Color.getHSBColor(0.75f, 0.35f, bri);
                    break;
                }
                case "SOFT_PEACH": {
                    float bri = (float)(0.9 + Math.sin(time * 0.06 + i * 0.3) * 0.1);
                    colors[i] = Color.getHSBColor(0.07f, 0.4f, bri);
                    break;
                }
                case "SOFT_SKY": {
                    float bri = (float)(0.85 + Math.sin(time * 0.06 + i * 0.3) * 0.1);
                    colors[i] = Color.getHSBColor(0.57f, 0.35f, bri);
                    break;
                }
                case "SOFT_BUTTER": {
                    float bri = (float)(0.9 + Math.sin(time * 0.06 + i * 0.3) * 0.1);
                    colors[i] = Color.getHSBColor(0.15f, 0.35f, bri);
                    break;
                }
                case "SOFT_ROSE": {
                    float bri = (float)(0.9 + Math.sin(time * 0.06 + i * 0.3) * 0.1);
                    colors[i] = Color.getHSBColor(0.95f, 0.35f, bri);
                    break;
                }
                case "SOFT_LILAC": {
                    float bri = (float)(0.85 + Math.sin(time * 0.06 + i * 0.3) * 0.1);
                    colors[i] = Color.getHSBColor(0.8f, 0.3f, bri);
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