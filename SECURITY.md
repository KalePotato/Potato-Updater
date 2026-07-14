# Security policy

## Supported code

Only the latest tagged release is supported. Historical snapshots and untagged builds are not security-supported distributions.

## Reporting

Do not publish exploit details, production credentials, private object-storage information, or affected user logs in a public GitHub thread. Contact the maintainer privately through the repository owner's published contact method.

## Trust boundaries

- Managed file paths must remain below `game_core_dir` or `launcher_dir`.
- Production downloads must use HTTPS and be verified against release metadata.
- Secrets must never be committed to this repository or embedded in GitHub Actions workflow files.
- Production deployment is intentionally separate from CI; a build passing CI is not authorization to publish it.
