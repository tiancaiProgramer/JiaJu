# JiaJu development

- Read README.md and docs/DEVELOPMENT.md before continuing feature work.
- Keep `dev.homecontrol.app`, the existing database identifiers and signing identity compatible with earlier 家控 installations.
- Build with `python3 tools/Build-JiaJu.py`; `--check` validates portable configuration and signing first. The normal build runs the JVM checks.
- `private-signing/`, `local/`, `_backup/`, `.local/` and private continuation archives must stay out of Git, source exports and Release uploads. Never print signing passwords, GitHub credentials or phone sessions.
- Scope cloud controls to the current account, region and exact model. Never auto-retry a write or synthesize missing state as off/zero.
- Keep device controls inside this app. Official apps may be used for authorized research, not as runtime dependencies.
- Do not treat a menu, API definition or cloud acknowledgement as proof of physical operation. Record real tests separately. Real cooking, lock/password/user operations and other physical effects require explicit testing authorization.
- Keep MiLink / HyperFusionWidget separate. Do not change that project as part of JiaJu work.
