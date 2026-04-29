# tools

Standalone decoders for etchit private-etch backup files. The primary
recovery path is the app itself (paste the backup's etch address — the
app detects the magic header and prompts for your password). These are
for decoding a backup on a machine where etchit isn't installed.

Both match the format in `app/.../BackupCrypto.kt` and always decrypt
the same file the app does.

## decode.html — browser decoder

Open `decode.html` in any modern browser. Pick the backup file, type
the password, click Decode. All crypto runs client-side via WebCrypto;
nothing leaves the page. Works offline from `file://`.

## decode.py — CLI decoder

```bash
pip install cryptography        # or: apt install python3-cryptography
python3 decode.py <backup-file>
```

Writes `backup.json` (a JSON array of `{dm, t, ts}` entries) on success.

## Recovering the actual etch content

The decoded JSON gives you the datamap hex (`dm` field) for each
private etch. To fetch the content off the Autonomi network:

```bash
echo -n '<hex from dm field>' | xxd -r -p > datamap.bin
ant file download --output etch-content datamap.bin
```

## Format

```
magic   17 bytes   "ETCHIT_BACKUP_v1\n"
salt    16 bytes
iv      12 bytes
ct+tag  rest       AES-256-GCM ciphertext, 16-byte tag appended
key = PBKDF2-HMAC-SHA256(password, salt, 600_000, 32)
```
