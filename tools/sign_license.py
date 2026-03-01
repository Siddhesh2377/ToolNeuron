#!/usr/bin/env python3
"""
ToolNeuron License Signer

Generates .tnlicense files signed with Ed25519 (preferred) or HMAC-SHA256 (legacy).
Private key must be kept secret — never ship it in the app.

Usage:
    # Ed25519 (default, recommended):
    python3 sign_license.py --key path/to/private.pem --user "user@email.com"

    # HMAC-SHA256 (for pre-Android 13 devices):
    python3 sign_license.py --hmac-secret <hex-of-signing-cert-sha256> --user "user@email.com"
"""

import argparse
import base64
import hashlib
import hmac
import json
import time
import sys


def sign_ed25519(private_key_path: str, user_id: str, product: str) -> str:
    try:
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
        from cryptography.hazmat.primitives.serialization import load_pem_private_key
    except ImportError:
        print("Install: pip install cryptography", file=sys.stderr)
        sys.exit(1)

    with open(private_key_path, "rb") as f:
        private_key = load_pem_private_key(f.read(), password=None)

    if not isinstance(private_key, Ed25519PrivateKey):
        print("Error: Key is not Ed25519", file=sys.stderr)
        sys.exit(1)

    issued_at = int(time.time())
    message = f"{user_id}|{product}|{issued_at}"
    signature = private_key.sign(message.encode("utf-8"))
    sig_b64 = base64.b64encode(signature).decode("ascii")

    return json.dumps({
        "userId": user_id,
        "product": product,
        "issuedAt": issued_at,
        "signature": sig_b64,
        "signatureType": "ed25519",
    }, indent=2)


def sign_hmac(secret_hex: str, user_id: str, product: str) -> str:
    secret = bytes.fromhex(secret_hex)
    issued_at = int(time.time())
    message = f"{user_id}|{product}|{issued_at}"
    sig = hmac.new(secret, message.encode("utf-8"), hashlib.sha256).digest()
    sig_b64 = base64.b64encode(sig).decode("ascii")

    return json.dumps({
        "userId": user_id,
        "product": product,
        "issuedAt": issued_at,
        "signature": sig_b64,
        "signatureType": "hmac-sha256",
    }, indent=2)


def main():
    parser = argparse.ArgumentParser(description="ToolNeuron License Signer")
    parser.add_argument("--key", help="Path to Ed25519 private key PEM")
    parser.add_argument("--hmac-secret", help="Hex-encoded HMAC secret (signing cert SHA-256)")
    parser.add_argument("--user", required=True, help="User ID (email or unique ID)")
    parser.add_argument("--product", default="pro", help="Product name (default: pro)")
    parser.add_argument("-o", "--output", help="Output .tnlicense file path")
    args = parser.parse_args()

    if not args.key and not args.hmac_secret:
        parser.error("Either --key (Ed25519) or --hmac-secret (HMAC) is required")

    if args.key:
        license_json = sign_ed25519(args.key, args.user, args.product)
    else:
        license_json = sign_hmac(args.hmac_secret, args.user, args.product)

    if args.output:
        with open(args.output, "w") as f:
            f.write(license_json)
        print(f"License written to {args.output}", file=sys.stderr)
    else:
        print(license_json)


if __name__ == "__main__":
    main()
