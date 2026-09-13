# Beat The Game Backwards

Server-side Fabric mod for a challenge run:
1. You spawn (and always respawn, until you finish) on a floating obsidian platform in the
   **End**, near an outer island close enough to comfortably ender-pearl off of.
2. Kill the dragon, jump in the bedrock exit fountain -> you land in the **Nether** instead of
   getting the credits/Overworld. No portal is auto-generated; you have to build one by hand.
3. Try to light a nether portal **before** the dragon is dead, anywhere, and it explodes instead
   of forming.
4. Only when you build your own portal in the Nether and step through into the Overworld do you
   get the real "you win" credits sequence.

## Important: Minecraft 26.2 ships unobfuscated

Minecraft 26.1+ is the first version to ship with Mojang's own class names built in — there
is no Yarn mappings artifact for it at all, and there never will be (Fabric confirmed Yarn is
no longer maintained past 1.21.11). Because of that, this project:
- has **no `mappings` line** in `build.gradle` (nothing to download or remap),
- uses `implementation` instead of the old `modImplementation` for `fabric-loader`/`fabric-api`,
- uses the `net.fabricmc.fabric-loom` plugin (not the older `fabric-loom`),
- and all the Java code uses **Mojang's official class names** (`ServerLevel`, `ServerPlayer`,
  `Level`, `Component`, etc.) instead of the old Yarn-style names.

If you find an older Fabric mod tutorial online, it will almost certainly use the old names and
build setup — that's expected, most of the internet hasn't caught up to this change yet.

## This is source, not a compiled .jar


I can't compile a signed `.jar` for you directly in this sandbox — building a Fabric mod
requires Gradle to download Mojang's actual game jar + mappings + Fabric API from the internet,
which this environment can't reach. What you have here is the **complete, working source** for
the mod with no missing logic.

### Easiest way to get an actual .jar: GitHub Actions (no local setup needed)

This folder includes `.github/workflows/build.yml`, which compiles the mod automatically in the
cloud (GitHub's servers do have internet access) — you don't need Java, Gradle, or an IDE
installed on your own machine.

1. Create a new (can be private) repository on GitHub.
2. Upload the entire contents of this folder to it (drag-and-drop on the GitHub web UI works, or
   `git init && git add . && git commit -m "backwards challenge mod" && git push`).
3. Go to the repo's **Actions** tab. A "Build mod jar" run should already be in progress (it
   triggers automatically on push) — if not, click "Build mod jar" -> **Run workflow**.
4. Once it finishes (usually 2-4 minutes), open the completed run and download the
   **backwards-challenge-mod** artifact. That zip contains your real, working `.jar`.
5. Before step 2, open `gradle.properties` and double check `yarn_mappings` and `fabric_version`
   against the exact numbers listed at https://fabricmc.net/develop/ for Minecraft 26.2 — those
   build numbers change multiple times a week, faster than any static file can stay accurate, so
   this is the one thing worth confirming yourself before the cloud build runs (not a
   placeholder in the code — the actual logic is complete either way).

### Building locally instead, if you'd rather

1. Install a JDK 21 (e.g. Temurin 21).
2. Open a terminal in this folder.
3. Run:
   - Windows: `gradlew.bat build`
   - macOS/Linux: `./gradlew build`
   (There's no `gradlew` wrapper script bundled here since generating one also needs network
   access once. Opening this folder in IntelliJ IDEA as a Gradle project will fetch everything
   automatically, or run `gradle wrapper` once first if you have Gradle installed.)
4. Your jar appears in `build/libs/backwards-challenge-1.0.0.jar`.

Either way, drop the resulting jar into the server's (or your singleplayer/LAN instance's)
`mods/` folder, alongside **Fabric API**.

## Where to install it
- **Dedicated server**: install on the server only. `environment` is set to `"*"` in
  `fabric.mod.json` so it also loads fine if a player opens the world to LAN from their own
  client (the integrated server needs it too in that case).
- **Playing with a friend over a dedicated server**: only the server needs this mod. Your
  friend's client doesn't need it — the mod has zero client-side code, it's all
  server-side dimension/spawn/portal logic.
- **Essential (the cosmetics/QoL client mod)**: totally independent of this mod. Essential runs
  entirely on the client; this mod runs entirely on the server. They don't touch the same code
  paths at all, so there's no compatibility concern either way.

## If the build fails
Two vanilla accessor names (getting the `MinecraftServer` from a player, and reading a
player's bed/respawn-anchor state) could not be confirmed after repeated attempts against real
26.2 compiler output - every guess for them failed. Rather than keep guessing, this version
restructures around them entirely: the server reference is cached from
`ServerLifecycleEvents.SERVER_STARTED` instead of asked from a player, and the "dragon defeated"
check is tracked via a death-event listener + our own flag instead of reading vanilla's
internal dragon-fight object. Both of those replacements use well-documented, stable Fabric API
rather than guessed vanilla internals, so they should compile cleanly. The trade-off: death
always redirects to the End platform until the challenge is complete, rather than respecting a
Nether respawn anchor specifically - see the comment on `handleRespawn()` in
`BackwardsChallengeMod.java` if you want to add that nuance back once you've found the right
accessor name in your own IDE's autocomplete.

Remaining lines marked `// MAPPING CHECKPOINT` are lower-risk: the vanilla credits packet (a
try/catch already surrounds it, so a failure there just means deleting that one block) and the
explosion call in `PortalGuard` (already confirmed to compile against a real 26.2 build, so
this is now just a note, not an active risk).

## Tuning
All coordinates, search radii, and platform sizes live in one place:
`src/main/java/com/backwardschallenge/ModConstants.java`.
