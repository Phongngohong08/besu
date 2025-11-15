# Hybrid Post-Quantum Transaction - Core Besu Changes

> Tổng hợp các file cần sửa để implement Hybrid Post-Quantum Signatures trong Besu
> 
> **Branch**: `feature/add-hybrid-signature`  
> **Base commits**: 8b51384e9 → b4046d50e (6 commits)

## 📋 Tóm tắt

Tính năng này thêm hỗ trợ chữ ký lai (Hybrid) giữa ECDSA và Post-Quantum Cryptography (DILITHIUM3 hoặc FALCON512) vào Besu để chống lại các cuộc tấn công từ máy tính lượng tử trong tương lai.

**Commit messages**:
1. `8b51384e9` - feat: Implement Hybrid Post-Quantum transaction support
2. `79c09af6b` - feat: Add support for Hybrid Post-Quantum transaction memory size calculations
3. `57f2da81a` - feat: Enhance Hybrid Post-Quantum transaction documentation
4. `5c06449b1` - feat: Implement Post-Quantum Cryptography (PQC) support
5. `e7ec115a6` - feat: Add support for Hybrid Post-Quantum transactions in transaction processing and decoding
6. `b4046d50e` - debug: Add detailed logging for PQ signature verification

---

## 🔧 Các file cần sửa (Core Besu)

### 1. Documentation

#### `HYBRID_PQ_SIGNATURES.md` ✨ NEW
- **Mô tả**: Tài liệu chi tiết về tính năng Hybrid PQ Signatures
- **Nội dung chính**:
  - Giải thích về quantum threat và hybrid signatures
  - Transaction format (type `0x04`)
  - Signature structure: ECDSA (65 bytes) + PQ signature (DILITHIUM3: 3310 bytes hoặc FALCON512: 667 bytes)
  - Hướng dẫn sử dụng và testing
- **Thay đổi**: File mới (1800+ dòng)

---

### 2. Crypto Module - PQC Implementation

#### `crypto/algorithms/build.gradle`
- **Mô tả**: Thêm dependency BouncyCastle PQC
- **Thay đổi**:
  ```gradle
  implementation 'org.bouncycastle:bcprov-jdk18on:1.80'
  implementation 'org.bouncycastle:bcpkix-jdk18on:1.80'
  + implementation 'org.bouncycastle:bcpqc-jdk18on:1.80'  // Post-Quantum Cryptography
  ```

#### `crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/PostQuantumCrypto.java` ✨ NEW
- **Mô tả**: Abstract base class cho Post-Quantum cryptography implementations
- **Chức năng**: Base class với common logic
- **Thay đổi**: File mới (từ commit 8b51384e9)

#### `crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/DilithiumCrypto.java` ✨ NEW
- **Mô tả**: Implementation của DILITHIUM3 signature scheme
- **Chức năng**:
  - `sign(byte[] message, byte[] privateKey)` - Ký message bằng DILITHIUM3
  - `verify(byte[] message, byte[] signature, byte[] publicKey)` - Xác minh chữ ký DILITHIUM3
  - Sử dụng BouncyCastle's `DilithiumSigner`
- **Key details**:
  - Private key: 4032 bytes (NIST FIPS 204 standard)
  - Public key: 1952 bytes
  - Signature: 3309 bytes
  - Type byte: `0x02`
- **Thay đổi**: File mới (276+ dòng)

#### `crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/FalconCrypto.java` ✨ NEW
- **Mô tả**: Implementation của FALCON512 signature scheme
- **Chức năng**: Tương tự DilithiumCrypto
- **Key details**:
  - Signature size: 666 bytes
  - Type byte: `0x01`
- **Thay đổi**: File mới

#### `crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/PQCryptoFactory.java` ✨ NEW
- **Mô tả**: Factory class để tạo PQ crypto instances
- **Chức năng**:
  - `create(byte algorithmType)` - Tạo instance từ type byte
  - Hỗ trợ FALCON512 (0x01) và DILITHIUM3 (0x02)
- **Thay đổi**: File mới

#### `crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/PQSignature.java`
- **Mô tả**: Interface cho Post-Quantum signatures
- **Thay đổi**: 
  - Thay đổi return type của `verify()` từ `void` sang `boolean`
  - Lý do: Để match với BouncyCastle API và dễ sử dụng trong validation

#### `crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/tools/PQKeyGenerator.java` ✨ NEW
- **Mô tả**: CLI tool để generate PQ keypairs
- **Chức năng**: Generate DILITHIUM3 hoặc FALCON512 keys
- **Thay đổi**: File mới

---

### 3. Crypto Module - Tests

#### `crypto/algorithms/src/test/java/org/hyperledger/besu/crypto/DilithiumCryptoTest.java` ✨ NEW
- **Mô tả**: Unit tests cho DilithiumCrypto
- **Test cases**:
  - Sign and verify
  - Invalid signature detection
  - Invalid public key handling
- **Thay đổi**: File mới (148+ dòng)

#### `crypto/algorithms/src/test/java/org/hyperledger/besu/crypto/FalconCryptoTest.java` ✨ NEW
- **Mô tả**: Unit tests cho FalconCrypto
- **Thay đổi**: File mới

#### `crypto/algorithms/src/test/java/org/hyperledger/besu/crypto/PQCryptoDebug.java` ✨ NEW
- **Mô tả**: Debug utility để test PQ crypto
- **Thay đổi**: File mới

#### `crypto/algorithms/src/test/java/org/hyperledger/besu/crypto/PQCryptoFactoryTest.java` ✨ NEW
- **Mô tả**: Unit tests cho PQCryptoFactory
- **Thay đổi**: File mới

#### `crypto/algorithms/src/test/java/org/hyperledger/besu/crypto/PQSignatureTest.java`
- **Mô tả**: Tests cho PQSignature interface
- **Thay đổi**: Update tests theo interface thay đổi (verify returns boolean)

---

### 4. Transaction Processing

#### `datatypes/src/main/java/org/hyperledger/besu/datatypes/TransactionType.java`
- **Mô tả**: Enum định nghĩa các loại transaction types
- **Thay đổi**: 
  - Thêm `HYBRID_PQ((byte) 0x04)` vào enum
  - Đây là transaction type mới cho hybrid signatures
- **Code**:
  ```java
  public enum TransactionType {
    FRONTIER((byte) 0x00),
    ACCESS_LIST((byte) 0x01),
    EIP1559((byte) 0x02),
    BLOB((byte) 0x03),
    HYBRID_PQ((byte) 0x04);  // ✨ NEW
  }
  ```

#### `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/Transaction.java`
- **Mô tả**: Transaction model class
- **Thay đổi**:
  - Thêm method `getEncodedTransactionSize()` để tính size của encoded transaction
  - Dùng để estimate gas cost và memory usage
  - **Lines changed**: ~18 lines
  ```java
  public int getEncodedTransactionSize() {
    return RLP.encode(this::writeTo).encodedSize();
  }
  ```

#### `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/HybridPQTransactionDecoder.java`
- **Mô tả**: Decoder cho HYBRID_PQ transactions (type 0x04)
- **Thay đổi chính**:
  - Parse hybrid signature: ECDSA (65 bytes) + PQ algorithm type (1 byte) + PQ signature
  - Extract và validate cả 2 chữ ký
  - Tính toán chainId từ signature
  - **Lines changed**: 65+ lines
- **Format**:
  ```
  0x04 || RLP([chainId, nonce, gasPrice, gasLimit, to, value, data, hybridSignature])
  hybridSignature = ecdsaSig (65) || pqType (1) || pqSig (variable)
  ```

#### `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/HybridPQTransactionEncoder.java` ✨ NEW
- **Mô tả**: Encoder cho HYBRID_PQ transactions
- **Chức năng**: 
  - Encode transaction thành RLP format
  - Combine ECDSA + PQ signatures vào hybrid signature field
- **Thay đổi**: File mới (từ commit 8b51384e9)

#### `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/TransactionDecoder.java`
- **Mô tả**: Main transaction decoder dispatcher
- **Thay đổi**: 
  - Thêm case để handle transaction type `0x04`
  - Route đến `HybridPQTransactionDecoder`
- **Code**:
  ```java
  case 0x04 -> HybridPQTransactionDecoder.decode(transaction);
  ```

#### `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/TransactionEncoder.java`
- **Mô tả**: Main transaction encoder dispatcher  
- **Thay đổi**:
  - Thêm case để handle `TransactionType.HYBRID_PQ`
  - Route đến `HybridPQTransactionEncoder`
- **Code**:
  ```java
  case HYBRID_PQ -> HybridPQTransactionEncoder.encode(transaction, encodingContext);
  ```

#### `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/mainnet/MainnetProtocolSpecs.java`
- **Mô tả**: Protocol specifications cho mỗi hard fork
- **Thay đổi**:
  - Thêm `HYBRID_PQ` transaction type vào danh sách supported types
  - Enable từ London fork onwards
  - **Lines changed**: ~8 lines
  ```java
  TransactionType.FRONTIER,
  TransactionType.ACCESS_LIST,
  TransactionType.EIP1559,
  TransactionType.BLOB,
  TransactionType.HYBRID_PQ  // ✨ NEW
  ```

#### `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/mainnet/MainnetTransactionValidator.java`
- **Mô tả**: Transaction validation logic
- **Thay đổi chính**:
  - Thêm method `validateHybridPQTransactionSignature()` để validate cả ECDSA và PQ signatures
  - Update `validateTransactionSignature()` để handle HYBRID_PQ type
  - Verify ECDSA signature (như transaction thông thường)
  - Extract PQ signature và verify với public key
  - **CRITICAL FIX (commit b4046d50e)**: Đổi signed data từ `transaction.getHash()` sang `Hash.hash(transaction.encodedPreimage())`
    - **Lý do**: PQ signature phải ký trên preimage hash (unsigned transaction), không phải transaction hash (đã có signature)
    - **Debug logs**: Thêm extensive logging để trace verification process
  - **Lines changed**: 110+ lines (81 original + 29 debug)
- **Validation flow**:
  ```
  1. Validate ECDSA signature → recover sender address
  2. Extract PQ algorithm type from signature
  3. Create appropriate PQCrypto instance
  4. Get transaction preimage (unsigned transaction)
  5. Hash preimage to get signed data
  6. Verify PQ signature against preimage hash
  7. Return validation result
  ```

---

### 5. Transaction Pool & Memory Management

#### `ethereum/eth/build.gradle`
- **Mô tả**: Build configuration cho ethereum/eth module
- **Thay đổi**: Có thể có dependencies hoặc test configuration updates
- **Lines changed**: Minor

#### `ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/transactions/PendingTransaction.java`
- **Mô tả**: Đại diện cho pending transaction trong mempool
- **Thay đổi**:
  - Update để handle HYBRID_PQ transaction type
  - Memory size calculation cho hybrid signatures
- **Purpose**: Đảm bảo pending pool tính toán memory footprint chính xác

---

### 6. Test Support & Test Files

#### `ethereum/core/src/test-support/java/org/hyperledger/besu/ethereum/core/BlockDataGenerator.java`
- **Mô tả**: Test utility để generate block và transaction data
- **Thay đổi**: Thêm support để generate HYBRID_PQ transactions cho tests

#### `ethereum/core/src/test-support/java/org/hyperledger/besu/ethereum/core/TransactionTestFixture.java`
- **Mô tả**: Test fixture để tạo transactions cho testing
- **Thay đổi**: Thêm methods để create HYBRID_PQ transaction fixtures

#### `ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/transactions/PendingTransactionEstimatedMemorySizeTest.java`
- **Mô tả**: Tests cho memory size estimation
- **Thay đổi**: Thêm test cases cho HYBRID_PQ transaction memory calculation

#### `ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/transactions/layered/BaseTransactionPoolTest.java`
- **Mô tả**: Base test class cho transaction pool
- **Thay đổi**: Update để support testing với HYBRID_PQ transactions

#### `ethereum/eth/src/test/java/org/hyperledger/besu/ethereum/eth/transactions/layered/LayersTest.java`
- **Mô tả**: Tests cho transaction pool layers
- **Thay đổi**: Test HYBRID_PQ transaction handling trong pool layers

---

## 📊 Thống kê thay đổi

| Module | Files Changed | Lines Added | Lines Removed |
|--------|--------------|-------------|---------------|
| Documentation | 1 | 1800+ | 0 |
| datatypes | 1 | 10+ | 0 |
| crypto/algorithms | 7 main + 5 test | 1000+ | 20 |
| ethereum/core | 8 main + 2 test-support | 400+ | 50 |
| ethereum/eth | 1 build + 1 main + 3 test | 100+ | 10 |
| **TOTAL** | **29 files** | **~3300 lines** | **~80 lines** |

---

## 🎯 Core Changes Summary

### A. New Transaction Type
- **Type**: `0x04` (HYBRID_PQ)
- **Format**: Standard RLP với hybrid signature field
- **Signature**: ECDSA (65B) + PQ Type (1B) + PQ Signature (variable)

### B. Post-Quantum Crypto Support
- **DILITHIUM3**: NIST FIPS 204, signature 3309 bytes
- **FALCON512**: NIST Round 3 candidate, signature 666 bytes
- **BouncyCastle**: Version 1.80 với bcpqc module

### C. Validation Process
1. Decode HYBRID_PQ transaction
2. Validate ECDSA signature (existing logic)
3. Extract PQ signature components
4. **Get transaction preimage** (unsigned transaction)
5. **Hash preimage** to get signed data
6. Verify PQ signature using appropriate algorithm against preimage hash
7. Accept only if BOTH signatures valid

### D. Critical Fix (Commit b4046d50e)
- **Problem**: PQ signature verification was using `transaction.getHash()` which includes signatures
- **Solution**: Changed to `Hash.hash(transaction.encodedPreimage())` - the unsigned transaction
- **Why**: PQ signature is created by signing the preimage (unsigned tx), not the final hash
- **Impact**: Enables correct verification of externally signed hybrid transactions

### E. Backward Compatibility
- Chỉ enable cho London+ forks
- Không ảnh hưởng đến existing transaction types
- Gracefully reject nếu protocol spec không support

---

## 🔍 Các file CẦN THIẾT cho feature này

### Production Code (Required):
1. ✅ `HYBRID_PQ_SIGNATURES.md` - Documentation
2. ✅ `datatypes/src/main/java/org/hyperledger/besu/datatypes/TransactionType.java` - Define HYBRID_PQ type
3. ✅ `crypto/algorithms/build.gradle` - Dependencies
4. ✅ `crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/PostQuantumCrypto.java` - Base class
5. ✅ `crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/DilithiumCrypto.java` - DILITHIUM3
6. ✅ `crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/FalconCrypto.java` - FALCON512
7. ✅ `crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/PQCryptoFactory.java` - Factory
8. ✅ `crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/PQSignature.java` - Interface
9. ✅ `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/Transaction.java` - Add size calculation
10. ✅ `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/HybridPQTransactionDecoder.java` - Decoder
11. ✅ `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/HybridPQTransactionEncoder.java` - Encoder
12. ✅ `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/TransactionDecoder.java` - Route to decoder
13. ✅ `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/TransactionEncoder.java` - Route to encoder
14. ✅ `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/mainnet/MainnetProtocolSpecs.java` - Enable type
15. ✅ `ethereum/core/src/main/java/org/hyperledger/besu/ethereum/mainnet/MainnetTransactionValidator.java` - Validation
16. ✅ `ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/transactions/PendingTransaction.java` - Pool support

### Optional/Support Files:
- `crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/tools/PQKeyGenerator.java` - CLI tool
- `ethereum/core/src/test-support/**` - Test fixtures (2 files)
- `crypto/algorithms/src/test/**` - Unit tests (5 files)  
- `ethereum/eth/src/test/**` - Integration tests (3 files)
- `ethereum/eth/build.gradle` - Test dependencies

---

## 💡 Notes

### Về test-besu, pq-signer, hardhat-example:
- **test-besu**: Local test network, không cần commit
- **pq-signer**: External signing tool, có thể tách riêng repo
- **hardhat-example**: Demo/example code, có thể tách riêng repo

### Về PQ Key Format (đã fix):
- ✅ DILITHIUM3 private key: 4032 bytes (NIST standard)
- ✅ Sử dụng BouncyCastle's `getEncoded()` + companion public key
- ✅ Signature verification: Đã test và works correctly

### Dependencies:
```gradle
implementation 'org.bouncycastle:bcprov-jdk18on:1.80'
implementation 'org.bouncycastle:bcpkix-jdk18on:1.80'
implementation 'org.bouncycastle:bcpqc-jdk18on:1.80'  // ✨ NEW - PQC support
```

---

## 🚀 Testing Status

- ✅ Unit tests: DilithiumCrypto, FalconCrypto, PQCryptoFactory
- ✅ Integration: Transaction decode/encode
- ✅ End-to-end: Full hybrid transaction với local Besu network
- ✅ NIST compliance: Key sizes match FIPS 204 standard

---

## 📝 Changelog từ commits

### Commit 8b51384e9 - Initial Hybrid PQ Implementation
- Created `TransactionType.HYBRID_PQ` (0x04)
- Implemented `PostQuantumCrypto` base class
- Created initial `DilithiumCrypto` and `PQSignature`
- Built `HybridPQTransactionDecoder` and `HybridPQTransactionEncoder`
- Updated `TransactionDecoder` and `TransactionEncoder` routers
- Added test fixtures in BlockDataGenerator and TransactionTestFixture
- Updated PendingTransaction for pool support
- Added test coverage (BaseTransactionPoolTest, LayersTest)

### Commit 79c09af6b - Memory size calculations
- Added `getEncodedTransactionSize()` in Transaction.java
- Added PendingTransactionEstimatedMemorySizeTest

### Commit 57f2da81a - Documentation
- Created comprehensive HYBRID_PQ_SIGNATURES.md

### Commit 5c06449b1 - Full PQC Implementation  
- Enhanced DilithiumCrypto implementation
- Created FalconCrypto for FALCON512
- Built PQCryptoFactory
- Updated PQSignature interface (verify returns boolean)
- Added BouncyCastle PQC dependency (bcpqc-jdk18on)
- Created comprehensive unit tests
- Added PQCryptoDebug utility
- Added PQKeyGenerator CLI tool

### Commit e7ec115a6 - Transaction Processing Integration
- Refined HybridPQTransactionDecoder
- Implemented MainnetTransactionValidator hybrid signature validation
- Added HYBRID_PQ to MainnetProtocolSpecs supported types
- Updated ethereum/eth build.gradle

### Commit b4046d50e - PQ Signature Verification Fix & Debug Logging
- **CRITICAL FIX**: Changed PQ signature verification to use preimage hash instead of transaction hash
  - Changed from `transaction.getHash()` to `Hash.hash(transaction.encodedPreimage())`
  - **Why**: PQ signature must be verified against the unsigned transaction (preimage), not the final hash with signatures
  - This matches how the signature is created in external tools (pq-signer, hardhat-example)
- Added comprehensive debug logging throughout `validateHybridPQTransactionSignature()`
  - Log transaction hash, PQ algorithm type
  - Log preimage details (length, hash, first 50 bytes)
  - Log each verification step
  - Add exception stack traces for debugging
- **Impact**: This fix makes PQ signature verification work correctly with externally signed transactions

---

**Generated**: November 15, 2025  
**Feature Branch**: `feature/add-hybrid-signature`  
**Base**: Besu mainnet (London+ compatible)
