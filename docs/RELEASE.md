# Release guide

## Version and source freeze

Configure the repository Actions secret `POTATO_SYNC_BASE_URL` with the endpoint profile intended for the release. Tag builds stop before packaging when this secret is absent; ordinary CI never publishes its placeholder binaries.

1. Update the application version and release notes.
2. Run `tools/build-release.ps1` locally.
3. Deploy to the persistent and clean test clients.
4. Commit the release-ready source to `main`.
5. Confirm GitHub CI succeeds for that commit.

## Draft build

Create and push an annotated tag:

```powershell
git tag -a v2.2.0 -m "Potato Updater v2.2.0"
git push origin v2.2.0
```

The release workflow builds from the tag and creates a draft GitHub Release containing:

- `Potato_Seed.jar`
- `Potato_Updater.jar`
- `Potato_Seed_Installer.exe`
- `SHA256SUMS.txt`

## Promotion

1. Download the draft assets.
2. Verify hashes and complete release-candidate testing.
3. Publish the GitHub Release.
4. Mirror the exact same bytes to the production object store.
5. Verify remote SHA-256 against the Release.
6. Update remote version metadata last.

The two Java JARs are configured as reproducible archives. The installer is currently built by the legacy .NET Framework compiler included with Windows; its binary timestamp makes hashes differ between builds. Treat the `SHA256SUMS.txt` produced by the same CI run as authoritative, and never mix an installer with checksums from another build.

If deployment fails before metadata changes, clients continue requesting the previous version. If it fails after metadata changes, restore metadata immediately or complete the exact-asset upload; do not rebuild under the same version.

## Rollback

Keep the prior Release assets and metadata values. Rollback means restoring the prior exact assets and then restoring the prior metadata. Never overwrite a published Git tag with different source.

## Prohibited release shortcuts

- Do not publish from an uncommitted working tree.
- Do not upload a locally rebuilt binary under an existing tag.
- Do not use runtime test directories as release sources.
- Do not commit production payloads or release binaries to Git.
- Do not let normal CI automatically update production metadata.
