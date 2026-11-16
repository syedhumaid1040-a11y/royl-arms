RoyalArms 2.1 - Paper 26.2 (Java 25)

BUILD
1. Install JDK 25 and Gradle (or open the folder in IntelliJ IDEA).
2. Run:  gradle build
3. Or double-click build.bat (Windows) / run build.sh. Put build/libs/RoyalArms-2.1.0.jar in your server's plugins folder.

ALL IN ONE
The single jar from "gradle build" contains the plugin AND the custom-look packs.
 JAVA:    on first start open plugins/RoyalArms/config.yml and set resource-pack.host to
          your server's public IP or domain, open TCP port 8164 (resource-pack.port),
          restart. The plugin then hosts the pack itself and sends it to players on join.
          Already host files somewhere? Set mode: external and fill url + sha1.
 BEDROCK: if plugins/Geyser-Spigot exists, the plugin copies the Bedrock pack and the
          Geyser custom item mappings into it automatically. Restart once more so Geyser
          loads them. (The files are also in the jar's pack/ folder if you want them.)
No packs at all? Set custom-models.enabled: false (plain vanilla items).
If Minecraft calls the Java pack incompatible, set force: false (default) and accept it
anyway, or ask me to adjust the pack format numbers.

SHRINES (no more altar rewards)
 - You can NOT get a weapon from an altar any more. Weapons are crafted only.
 - Shrines spawn in new chunks (0.4%). Each is dedicated to one weapon and themed:
     Shockwave        copper storm shrine with lightning rods
     Lifestealer      blood-red nether brick shrine
     Warden Battleaxe deepslate shrine with sculk
     Primal Spear     overgrown jungle shrine with froglights
     Afterburner      blackstone and magma shrine
     Void Hammer      end stone and purpur shrine
 - Walk in from the north entrance: the weapon floats over the altar, and the wall
   behind it shows the weapon plus its 3x3 crafting recipe in item frames.
 - Right-click the altar to open the recipe menu for that weapon.
 - The loot chest has a 35% chance of a random artifact.
 - /arms shrine [weapon]  /arms altar [weapon]  to build one where you stand.

RECIPES (all hard; open /recipes to see the layouts)
 Shockwave        2 Heavy Core, 3 Netherite Ingot, 2 Breeze Rod, Mace, Nether Star
 Lifestealer      2 Wither Skeleton Skull, 3 Netherite Ingot, 2 Enchanted Golden Apple, Netherite Sword, Nether Star
 Warden Battleaxe 4 Echo Shard, Sculk Catalyst, Sculk Shrieker, 2 Netherite Ingot, Netherite Axe
 Primal Spear     2 Fermented Spider Eye, Weeping Vines, 2 Netherite Ingot, Netherite Spear, 2 Pufferfish, Nether Star
 Afterburner      4 Blaze Rod, Ghast Tear, 2 Netherite Ingot, Netherite Sword, Nether Star
 Void Hammer      4 Ender Eye, Dragon's Breath, 2 Netherite Ingot, Mace, Nether Star
 Cloak of Shadows 4 Phantom Membrane, 2 Echo Shard, 2 Netherite Ingot, Nether Star
 Jumbo Rocket     4 Firework Rocket, 4 TNT, Gunpowder or Creeper Head (center)
 Phoenix Feather  4 Blaze Rod, 3 Feather, Totem of Undying, Nether Star
 Gale Rod         4 Wind Charge, 2 Breeze Rod, 2 Netherite Ingot, Heavy Core
 Chrono Clock     4 Echo Shard, 3 Netherite Ingot, Clock, Nether Star
 Tracker Compass  4 Ender Eye, 2 Diamond Block, 2 Netherite Ingot, Recovery Compass
 Bucket of Malk   Milk Bucket, 4 Golden Apple, 4 Sugar
 Bucket of Greed  Lava Bucket, 4 Gold Block, 4 Emerald Block

HOW WEAPONS WORK
 Land hits to fill power bars (max 3; a mace smash fills a bar). Right-click = ability 1,
 sneak + right-click = ability 2, or use /ability1 and /ability2 (good for Bedrock).
 Crafted weapons start locked: 1 unique player kill = passive, 2 = ability 1, 3 = ability 2.

COMMANDS (OP)
 /arms give <weapon|artifact> [player] [locked]   /arms power <0-3>
 /arms timer <seconds> <title>   /arms timer stop
 /arms altar [weapon]   /arms shrine [weapon]   /arms gui
Everyone: /arsenal (/weapons), /recipes, /ability1, /ability2

ENCHANTS: RoyalArms never touches enchants, so enchanting, anvils and mending keep working.
