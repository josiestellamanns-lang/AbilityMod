# Abilities (Fabric, Minecraft 1.21.1)

Commands (need op / permission level 2):
- `/ability give <player> <block_morph|mob_tamer|sabotage|all>`
- `/ability remove <player> <ability|all>`
- `/ability list <player>`
- `/abitity ...` also works (typo alias)

Everyone starts with no abilities. Keys are in Options > Controls > Key Binds > "Abilities"
(defaults: G = Block Morph, H = Tame Mob, K = Sabotage menu).

## Build the .jar with GitHub (no installs)
1. Make a free GitHub account and a new repository.
2. Upload ALL files from this folder (keep the folder structure). The `.github/workflows/build.yml`
   file must be included. If your PC hides the .github folder, instead click "Add file > Create new file",
   type `.github/workflows/build.yml` as the name and paste the contents.
3. Open the repo's **Actions** tab. The build starts automatically (~2-3 min).
4. Click the finished run, then download **abilitymod-jar** at the bottom. Unzip it: the .jar inside goes in your `mods` folder.

## Build locally instead
Install JDK 21 and Gradle 8.10+, then run `gradle build`. The jar is in `build/libs/abilitymod-1.0.0.jar`.

Needs Fabric Loader 0.16+ and Fabric API for 1.21.1. Install the mod on the server AND on every player's client.

## How the abilities work
- **Block Morph**: press the key while looking at a block to become it. Press the SAME key again (look anywhere) to change back.
- **Mob Tamer**: look at any mob and press the key. Free and unlimited, with a heart animation. Tamed mobs follow you like dogs
  and attack whatever you hit (players included) or whatever hits you. They never hurt you or each other.
- **Sabotage**: press the key to open the menu. Tabs: Players / Entities / Tamed. Pick a target, pick an effect
  (Harmful or Helpful), press GO.

## Tweaks
- Effect types/durations: `SabotageType.java` (20 ticks = 1 second)
- Cooldowns: `Ability.java`
