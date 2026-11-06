# Embedded camera pipe

Self-contained arm64 Android executable; Java exchanges authorization and Annex-B frames over private process pipes. It does not open an HTTP service, receive the account session, log raw packets, or save video. Exact model scope: `chuangmi.camera.069a01`, MISS vendor 4/CS2. Tries on-subnet LAN first; other networks start with ticket-derived directory servers and P2P/relay media, with a LAN fallback after cloud failure. Media candidates can start negotiation after 250 ms while direct probes continue; absent candidates retain the original 1.5 s window. With explicit user permission, cross-network HEVC decoding and continuous real-Activity rendering were verified on the named camera. LAN returned zero frames on a different subnet. Audio/PTZ, other models, HD and long-session stability remain unverified.

Sources:
- `internal/cs2/conn.go`: adapted from AlexxIT/go2rtc commit `990f4de3cc21d1cca917d02d0bc40f2fabe75a49`, MIT (`LICENSE-go2rtc.txt`).
- `internal/cs2/cloud.go`: adapted from kisharora/go2rtc commit `76b3e456c2f1dda97cce9f2797a668818e165488`, MIT; builds on glissmam's cloud handshake. https://github.com/kisharora/go2rtc/commit/76b3e456c2f1dda97cce9f2797a668818e165488
- PPPP initializer wire format reference: https://github.com/nosoop/pppp_api/blob/master/decode_string.cpp ; fixed protocol table and interoperability description: https://github.com/eugeneRover/micam#5-init-string-decode . `directory.go` is a local bounded implementation of that wire format, with no imported Java source.
- X25519/NaCl box precomputation and ChaCha20 use golang.org/x/crypto. Dependency source and licenses are vendored; license notices are also packaged in APK resources/META-INF.

Local changes: ticket-derived server list, eight-byte UID prefix, removal of all packet/session logging, bounded header/frame validation, UDP source checks, matching command acknowledgements, one-second keepalive, protocol-close notification and lifecycle cleanup. Media ACKs remain per-packet for the validated exact model. There is no camera wake/config write, PTZ or talk-back in this bridge.

The binary emits a 13-byte frame header: type (1 byte), payload size (4-byte big-endian), timestamp in milliseconds (8-byte big-endian). Type 1 is a bounded JSON event; types 4/5 are H.264/H.265 Annex-B access units. The first event contains an ephemeral public key. Java writes one newline-terminated JSON configuration; EOF stops media. Private keys stay inside the process.

Build runs local Go interoperability/bounds tests and cross-compiles with 16 KB ELF page alignment. No network dependency download is needed because the verified modules are vendored. Set JIAJU_GO_HOME and JIAJU_ANDROID_NDK via the portable local build configuration.


0.0.10: drain CH0 replies throughout the session to prevent an unread 10-item reply queue from terminating healthy media. Complete relay session-link requests are answered and renewed without logging their contents. A three-minute native decode test consumed 17 replies and rendered 3596 frames. Process-control input now supports bounded private JSON pause/resume/stats commands for the retained reuse experiment; default release UI does not enable reuse/recovery. No recording, audio or camera configuration commands were added.
