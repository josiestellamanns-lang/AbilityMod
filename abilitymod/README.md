# Abilities (Fabric, Minecraft 1.21.1)

## Commands (ops only)
- `/ability give <player> <ability|all>`  /  `/ability remove <player> <ability|all>`  /  `/ability list <player>`
- `/abilities settings` opens the settings screen
- `/abilities` and `/abitity` work as aliases for `/ability`

Everyone starts with no abilities. Keybinds are under Options > Controls > Key Binds > "Abilities".

| Ability | Key | What it does |
|---|---|---|
| Block Morph | G | Become the block you look at. Press again (look anywhere) to change back. No shadow while morphed. |
| Mob Tamer | H | Look at any mob. Free and unlimited. Tamed mobs follow you, fight for you and never target you. Peaceful mobs fight like wolves. |
| Sabotage | K | Menu with Players / Entities / Tamed tabs. Pick a target and a harmful or helpful effect, press GO. |
| Decoy | B | A slim-model fake copy of you (skin, name, armor, items) stands still, facing the way you did, while you are hidden for 10 s. |
| Freeze | V | Everything alive within 5 blocks (not you, not your pets) is frozen in ice for 5 s. |
| Grapple | C | Aim at a block and get pulled to it at ~20 blocks/s. Sneak to let go. |
| Shockwave | R | Launches everything within 20 blocks (changeable) away from you. |
| Summon | Y | Calls a flank of monsters (husks, pillagers, spiders, vindicators) that fight for you for 60 s. |
| Double Jump | Z (toggle) | Press jump again in mid-air to jump once more. Z turns it on or off. |
| Locator | N | A pet allay appears and a screen lists every structure. Pick one and the allay leads you there. |

## Settings screen
`/abilities settings` shows every ability on the left and its settings on the right (cooldown, range, speed, duration...).
Type a number and press Enter, or use - / +. "Reset" puts a setting back to its default. Changes apply to everyone on the server.

## Build
Push to GitHub; the Actions tab builds the jar (see .github/workflows). Or run `gradle build` with JDK 21.
Needs Fabric Loader 0.16+ and Fabric API for 1.21.1, on the server and every client.
