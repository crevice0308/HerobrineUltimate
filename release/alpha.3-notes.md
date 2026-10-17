0.1.0-alpha.3 prerelease for Minecraft 1.20.1 / Java 17.

Fixes restrictive configuration fallback, Forge configuration reload on each server start, and Fabric state snapshots beyond the old 8 KiB request limit. Normal valid configuration retains the intended non-OP defaults.

Release assets come only from the successful baseline Forge47.4.10 and FabricLoader0.16.14/FabricAPI0.92.2+1.20.1 jobs in this exact workflow run. SHA256SUMS.txt and provenance.json identify the immutable source commit and build run. Candidate loader artifacts are excluded.

Verification: settings and config regression tests, structural wiring checks, Forge/Fabric production builds, and real Minecraft NBT/Netty packet framing tests on Fabric. This is not a fresh in-game client/world-reopen or authenticated multiplayer test. alpha.2's supplied game-test evidence is not claimed for this version. Back up worlds before use.
