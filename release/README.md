# Release provenance

`original-alpha.2/` preserves the two user-supplied JARs byte-for-byte, with SHA-256 checksums. Their source is commit `e6eb189b932e0050c0cedfe53f77ea0ff003320b`. They have known issues described in `alpha.2-notes.md`; prefer the fixed alpha.3 prerelease.

GitHub rejected release creation for this historical commit because its workflow differs from the default branch. The release job's token cannot receive the additional Workflows permission required for that operation. Historical alpha.2 release creation is therefore paused; the workflow does not retry it or change its source target.

Current alpha prereleases are built from the workflow's exact main commit. Only baseline Forge/Fabric artifacts from the same successful run are distributed. The job downloads published assets and verifies their bytes and SHA-256. Existing tags/assets are never overwritten. Candidate builds do not imply runtime compatibility.
