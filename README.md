# SIFT Dimension 2.0 — Sculkite Expansion

Source project for **Minecraft Java 1.21.1, Fabric, Java 21**. Includes the original Ancient City portal dimension plus three climates, 18 new blocks, three ores, tools, swords, a four-piece armor set, materials, crafting/smelting, additional ruin loot, and a custom data-driven enchantment **Sculk Hunter** (I–III).

**Set bonus:** Sculkite Sword deals 10 base attack damage. When wearing all four Sculkite armor pieces, the sword receives +10 attack damage through a temporary player attribute modifier (20 base attack damage, before critical hits, enchantments and target mitigation). The bonus vanishes when the sword is unequipped or the armor set breaks.

**Testing commands (OP):** `/sift visit`, `/sift gear`, `/sift kit`. Enter naturally through the Ancient City reinforced-deepslate frame using an echo shard or Echo Key.

**Build:** Upload the entire contents of this project to the root of your GitHub repo. Go to Actions → Build Sift Dimension 1.21.1 → successful build → download the artifact ZIP → extract the mod `.jar`. Install Fabric API.

**Verification status:** JSON syntax, local file references and ZIP packaging checked; Gradle compile and live Minecraft run not tested in this environment (offline dependencies). See README_RU.md for full instructions and compatibility notes. Always use a backup world when upgrading.


## Compact GitHub upload (13 source files)

This is the **compact version** of the same v2.0 mod. No textures, models, recipes, biomes, or data files were removed.
The original 211 files under `src/main/resources/assets/` and `src/main/resources/data/`
are now stored in **one** `src/main/resources/sift-content.zip`.

**Do not unpack `sift-content.zip` before uploading to GitHub.** The Gradle
`processResources` task unpacks it inside the build automatically. The compiled
JAR contains the normal Minecraft resource paths, not the embedded ZIP.

Run: `gradle --no-daemon clean build` or use GitHub Actions.

The source archive has fewer than 100 files, but build/launch must still be tested.
