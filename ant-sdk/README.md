# ant-sdk (trimmed fork)

This is a trimmed vendored copy of [WithAutonomi/ant-sdk](https://github.com/WithAutonomi/ant-sdk),
kept only because we apply local patches to the Rust FFI crate under
`ffi/rust/ant-ffi/`.

## What's here

- `ffi/rust/` — the `ant-ffi` Cargo workspace. This is what
  `scripts/build-ffi.sh` compiles into `libant_ffi.so`.
- `.reference-source/` — preserved snapshot of the upstream FFI source
  the original shipping `libant_ffi.so` was built from. Read-only. Used
  for diff-vs-upstream when future upgrades are considered.
- `.gitignore` — excludes Cargo `target/` output.

## What was removed vs. upstream

The following upstream directories were pruned because nothing in this
app's build path references them:

- `antd/`, `antd-cli/` (standalone daemon)
- `antd-{cpp,csharp,dart,elixir,go,java,js,kotlin,lua,mcp,php,py,ruby,rust,swift,zig}/`
  (non-Rust language SDKs for the daemon)
- `ffi/{csharp,kotlin,scripts,swift}/` (other-target bindings + upstream build scripts)
- `docs/`, top-level `README.md`, `llms*.txt`, `skill.md`, `.github/`

If upstream tooling or bindings are ever needed, clone the full
upstream separately rather than re-vendoring them here.

## Local patches vs. upstream

Upstream reference point: `WithAutonomi/ant-sdk@bf541cc` (as of vendoring
on 2026-04-21). Our diffs live in `ffi/rust/ant-ffi/src/` and
`ffi/rust/ant-ffi/Cargo.toml`. The main deltas:

- `Client::prepare_public_upload`, `Client::finalize_public_upload`,
  `Client::peer_count` added.
- `Client::connect_local` removed (local-devnet only, unused).
- `testnet-patches` Cargo feature removed entirely (production-only).
- `start_node_with_warmup()` and `mobile_client_config()` helpers for
  faster / cellular-friendly connect.
- `ant-core` pinned as `git + rev = "c7d9ee1"`; `ant-node = "=0.10.0"`;
  `evmlib = "=0.8.0"`.

Full rebuild / upgrade instructions: [`../docs/FFI_BUILD.md`](../docs/FFI_BUILD.md).
