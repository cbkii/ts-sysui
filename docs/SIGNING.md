# Stable signing

Use one persistent keystore for both APKs. Do not commit it.

Create it locally:

```bash
bash tools/create-signing-keystore.sh
```

For GitHub Actions, base64-encode the JKS and add these repository secrets:

- `TS18_KEYSTORE_B64`
- `TS18_KEYSTORE_PASSWORD`
- `TS18_KEY_ALIAS` (normally `ts18-statusbar`)
- `TS18_KEY_PASSWORD`

The release workflow writes the key only to the runner's temporary directory.
The repository and release bundle never contain the private key.
