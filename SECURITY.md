# Security Policy

## Threat Model

This document describes the security model and threat model for Private AI.

### Security Goals

1. **Complete offline operation**: The application must function without any network connection
2. **Data sovereignty**: All user data (conversations, memories, preferences) remains on-device
3. **Encrypted storage**: Sensitive data is encrypted at rest using hardware-backed keys
4. **No data exfiltration**: The application cannot send data to any external server
5. **User-controlled deletion**: Users can delete all local data at any time

### Assets to Protect

| Asset | Sensitivity | Protection |
|-------|-------------|------------|
| Conversation history | High | Encrypted at rest (AES-256-GCM) |
| Long-term memories | High | Encrypted at rest (AES-256-GCM) |
| User preferences | Medium | Encrypted at rest (AES-256-GCM) |
| Model files | Medium | Stored in app-private storage |
| Encryption keys | Critical | Hardware-backed Android Keystore |

### Threat Categories

#### 1. Network-Based Attacks

**Status: MITIGATED**

- No `INTERNET` permission in AndroidManifest.xml
- No `ACCESS_NETWORK_STATE` permission
- Network security config blocks all cleartext traffic
- No networking libraries in dependencies
- The application cannot make network calls even if compromised

**Remaining risk**: None. The application has no network capability by design.

#### 2. Physical Device Access

**Status: PARTIALLY MITIGATED**

- Data is encrypted at rest using Android Keystore-backed keys
- Keys cannot be extracted from hardware security module
- However, if the device is unlocked, an attacker with physical access can:
  - Read data from the app's private storage
  - Access decrypted data in memory

**Mitigations**:
- Device-level encryption (enabled by default on modern Android)
- Biometric lock on device
- Future: Biometric-gated app access

#### 3. Malicious Application

**Status: PARTIALLY MITIGATED**

- App-private storage is protected by Android's sandboxing
- Rooted devices can bypass sandboxing
- Other apps on the same device cannot access Private AI's data

**Mitigations**:
- Do not install on rooted devices (or use root detection)
- Future: Integrity verification

#### 4. Backup Extraction

**Status: MITIGATED (4-layer defense-in-depth)**

| Layer | Mechanism | Protection |
|-------|-----------|------------|
| 1 | `android:allowBackup="false"` | OS-level block on adb backup AND cloud backup |
| 2 | `android:fullBackupContent="false"` | Explicit block on legacy full backup (API < 31) |
| 3 | `android:dataExtractionRules` | Excludes ALL domains from cloud backup and device transfer |
| 4 | `android:backupAgent=".BackupDenier"` | Programmatic rejection - backs up zero bytes |

Additional manifest flags:
- `android:hasFragileUserData="false"` - No "preserve data?" prompt on uninstall
- `android:killAfterRestore="false"` - App not killed during restore
- `android:restoreAnyVersion="false"` - No cross-version restore

**Zero data leaves this device via backup. Verified by 4 independent layers.**

#### 5. Model File Security

**Status: ACCEPTED RISK**

- Model files are stored in app-private storage
- They are not encrypted (they are large, public model weights)
- If extracted, the model itself does not contain user data
- The model is a public Google Gemma model

### Cryptographic Implementation

#### Encryption Algorithm
- **Algorithm**: AES-256-GCM
- **Key size**: 256 bits
- **Mode**: Galois/Counter Mode (authenticated encryption)
- **IV size**: 96 bits (12 bytes)
- **Tag size**: 128 bits

#### Key Management
- Keys are generated and stored in Android Keystore
- Keys are hardware-backed on supported devices (Pixel 6+)
- Keys cannot be extracted from the Keystore
- Key alias: `private_ai_master_key`

#### Data Format
- Encrypted data format: `[IV (12 bytes)] [Ciphertext] [Auth Tag]`
- Base64 encoding for string storage
- Raw bytes for file storage

### Implementation Status

#### Phase 1 (Current - Initial Architecture)

- [x] No network permissions
- [x] Network security config blocks traffic
- [x] Backup disabled (4-layer defense-in-depth)
  - [x] `allowBackup="false"` (OS-level)
  - [x] `fullBackupContent="false"` (API < 31)
  - [x] `dataExtractionRules` excludes ALL domains (API 31+)
  - [x] `BackupDenier` agent rejects all backup operations
- [x] Android Keystore-backed encryption
- [x] AES-256-GCM for data at rest
- [x] Encrypted SharedPreferences for config
- [x] Encrypted file storage for conversations/memories
- [x] User-controlled data deletion

#### Phase 2 (Future)

- [ ] Biometric-gated key access
- [ ] Key rotation
- [ ] Memory encryption at rest
- [ ] Secure enclave integration
- [ ] Root detection
- [ ] Integrity verification
- [ ] TEE/SE integration

### Known Limitations

1. **No network permission cannot be enforced at runtime**: Android permissions are granted at install time. If the app is repackaged with network permission, it could make network calls.

2. **Model files are not encrypted**: The Gemma model is a public asset. User data is not stored in model files.

3. **In-memory data**: During inference, decrypted data exists in memory. This is protected by Android's process isolation.

4. **Rooted devices**: On rooted devices, the security model is weakened. An attacker with root access can read app data.

5. **Physical forensics**: With physical access and specialized tools, data may be extractable from device storage. Full-disk encryption (enabled by default) mitigates this.

### Security Testing

#### Unit Tests
- Encryption/decryption round-trip tests
- Key generation tests
- Data persistence tests

#### Manual Testing
- Verify no network calls in traffic monitor
- Verify backup exclusion
- Verify data deletion
- Verify encryption at rest

#### Future Security Audits
- Penetration testing
- Code review for security
- Third-party audit

### Reporting Security Issues

If you discover a security vulnerability, please report it responsibly:

1. **Do NOT** open a public GitHub issue
2. Email security@privateai.dev (placeholder)
3. Include detailed description and reproduction steps
4. Allow 90 days for response

### Compliance

This application is designed to support:
- GDPR (data sovereignty, right to deletion)
- CCPA (no data collection, no data sale)
- HIPAA-ready (on-device processing, encryption)

Note: Compliance requires additional legal review beyond technical implementation.

---

**Last updated**: 2026-08-28
**Version**: 0.1.0-alpha
**Status**: Initial security architecture - audit required before production use
