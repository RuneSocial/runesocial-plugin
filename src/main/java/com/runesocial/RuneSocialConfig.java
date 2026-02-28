package com.runesocial;

import net.runelite.client.config.*;
import java.awt.Color;

@ConfigGroup("RuneSocial")
public interface RuneSocialConfig extends Config
{
	enum Emoticon
	{
		NONE("None"),
		HAPPY("Happy - :)"),
		SAD("Sad - :("),
		CRYING("Crying - :'("),
		ANGRY("Angry - >:("),
		SURPRISED("Surprised - :O"),
		WINK("Wink - ;)"),
		COOL("Cool - B)"),
		DEAD("Dead - x)"),
		LOVE("Love - :*"),
		SKEPTICAL("Skeptical - :/"),
		HEART("Heart - <3"),
		XD("Laugh - xD"),
		UWU("UWU - uwu"),
		TONGUE("Tongue - :P"),
		BLUSHING("Blushing - :$"),
		SMILE_ALT("Smile - =)"),
		STARSTRUCK("Starstruck - *.*"),
		ANNOYED("Annoyed - -.-"),
		DEADPAN("Deadpan - -_-"),
		SHIFTY("Shifty - >.>"),
		FRUSTRATED("Frustrated - >.<"),
		BEAK("Beak - :v"),
		BLANK("Blank - ._."),
		SLEEPY("Sleepy - zzZ"),
		WOW_LEFT("Wow - o.O"),
		WOW_RIGHT("Wow - o.o"),
		ANXIOUS("Anxious - :S"),
		CHILL("Chill - ~_~");

		private final String display;

		Emoticon(String display) {
			this.display = display;
		}

		@Override
		public String toString() {
			return display;
		}
	}


	enum MovementEffect
	{
		NONE("None"),
		WAVE("Wave"),
		DECODE("Decode"),
		SHAKE("Shake"),
		BOUNCE("Bounce"),
		FLOAT("Float"),
		UPSIDE_DOWN("Upside Down");

		private final String display;

		MovementEffect(String display) {
			this.display = display;
		}

		@Override
		public String toString() {
			return display;
		}
	}

	@ConfigItem(
			keyName = "movementEffect",
			name = "Movement Effect",
			description = "Efecto de movimiento para tu nombre",
			section = "name"
	)
	default MovementEffect movementEffect()
	{
		return MovementEffect.NONE;
	}

	// =====================================
	// MOOD
	// =====================================
	@ConfigSection(
			name = "What's your mood?",
			description = "Elige tu emoji de estado de ánimo",
			position = 0
	)
	String moodSection = "mood";

	@ConfigItem(
			keyName = "emoticon",
			name = "Emoticon",
			description = "Carita que aparece encima de tu nombre",
			section = "mood"
	)
	default Emoticon emoticon()
	{
		return Emoticon.NONE;
	}



	// =====================================
	// NAME EFFECTS
	// =====================================
	@ConfigSection(
			name = "Name Effects",
			description = "Efectos para tu nombre",
			position = 1
	)
	String nameSection = "name";

	@ConfigItem(
			keyName = "nameColor",
			name = "Name Color",
			description = "Color del nombre arriba del jugador",
			section = "name"
	)
	default Color nameColor()
	{
		return Color.CYAN;
	}

	enum ColorEffect
	{
		NONE,
		RAINBOW,
		FIRE,
		ICE,
		PULSE,
		DISCO,
		NEON,
		GRADIENT_BW,
		LAVA,
		GALAXY,
		TOXIC,
		BLOOD,
		MATRIX,
		GRADIENT_VERT,
		GRADIENT_PINK,
		GRADIENT_PURPLE,
		GRADIENT_BLUE,
		GRADIENT_RED,
		GRADIENT_GREEN,
		GRADIENT_YELLOW,
		GRADIENT_DARK
	}


	@ConfigItem(
			keyName = "colorEffect",
			name = "Color Effect",
			description = "Efecto de color para tu nombre",
			section = "name"
	)
	default ColorEffect colorEffect()
	{
		return ColorEffect.NONE;
	}



	// =====================================
	// PET
	// =====================================
	@ConfigSection(
			name = "Pet Name - Hover me for instructions!",
			description = "To assign a nickname to your pet, drop your pet and type ::petname in your game chat.",
			position = 2
	)
	String petSection = "pet";

	@ConfigItem(
			keyName = "petNameColor",
			name = "Pet Name Color",
			description = "Color for your pet's nickname",
			section = "pet"
	)
	default Color petNameColor()
	{
		return Color.MAGENTA;
	}
}