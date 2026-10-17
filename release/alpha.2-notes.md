Original supplied 0.1.0-alpha.2 prerelease for Minecraft 1.20.1 / Java 17.

These two JARs are the original supplied binaries, retained byte-for-byte and checked against the supplied SHA-256 records. Fabric was built by the supplied manual mapping/remapping process. The source is the alpha.2 baseline commit e6eb189b932e0050c0cedfe53f77ea0ff003320b. GitHub CI also built that baseline successfully, but those newly built JARs are not substituted for these original assets.

Known issues fixed in alpha.3: malformed server configuration can remove configured restrictions; Forge does not reload configuration when reopening a world; large Fabric transformation snapshots can stop synchronizing. Prefer alpha.3 for these fixes. This archive is retained for provenance, not recommended as the newest build.

Supplied historical evidence reports 14 GameTests and 85 client steps per loader. Those are prior results, not new runtime tests during publication. External authenticated multiplayer and candidate-loader runtime compatibility remain unverified. Back up worlds.
