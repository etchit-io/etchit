#!/usr/bin/env python3
"""
Decode an etchit private-etch backup file on any machine.

    pip install cryptography      # or: apt install python3-cryptography
    python3 decode.py <backup-file>

Writes the decrypted JSON to backup.json. The JSON is a list of entries
with fields: dm (hex datamap), t (title), ts (timestamp ms). To recover
the actual etch content, write the `dm` hex to a file and pass it to
the Autonomi CLI:

    echo -n '<hex>' | xxd -r -p > datamap.bin
    ant file download --output etch-content datamap.bin

Format (matches BackupCrypto.kt in the app):
    magic 17 bytes     "ETCHIT_BACKUP_v1\\n"
    salt  16 bytes
    iv    12 bytes
    ct+tag rest        AES-256-GCM ciphertext with 16-byte tag appended
    key = PBKDF2-HMAC-SHA256(password, salt, 600000 iterations, 32 bytes)
"""
import sys
import getpass
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"ETCHIT_BACKUP_v1\n"
SALT_LEN = 16
IV_LEN = 12
ITERATIONS = 600_000


def main() -> int:
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} <backup-file>", file=sys.stderr)
        return 2

    data = open(sys.argv[1], "rb").read()
    if not data.startswith(MAGIC):
        print("not an etchit backup (magic header mismatch)", file=sys.stderr)
        return 1
    salt = data[len(MAGIC):len(MAGIC) + SALT_LEN]
    iv = data[len(MAGIC) + SALT_LEN:len(MAGIC) + SALT_LEN + IV_LEN]
    ct = data[len(MAGIC) + SALT_LEN + IV_LEN:]

    password = getpass.getpass("password: ")
    key = PBKDF2HMAC(hashes.SHA256(), 32, salt, ITERATIONS).derive(password.encode())

    try:
        plaintext = AESGCM(key).decrypt(iv, ct, None)
    except Exception:
        print("wrong password or corrupted backup", file=sys.stderr)
        return 1

    open("backup.json", "wb").write(plaintext)
    print("wrote backup.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
