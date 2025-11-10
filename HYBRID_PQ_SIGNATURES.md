# Hybrid Post-Quantum Signatures Implementation

## Overview

This implementation adds support for hybrid post-quantum signatures to Hyperledger Besu transactions, combining traditional ECDSA signatures with post-quantum cryptographic signatures (e.g., Dilithium, Falcon).

## Architecture

The implementation follows the EIP-2718 typed transaction envelope standard by introducing a new transaction type: `HYBRID_PQ` (0x05).

### Key Components

#### 1. Transaction Type (`TransactionType.HYBRID_PQ`)
- **File**: `datatypes/src/main/java/org/hyperledger/besu/datatypes/TransactionType.java`
- **Type ID**: `0x05`
- Supports access lists and EIP-1559 fee market

#### 2. Post-Quantum Signature Classes

**PQSignature** (`crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/PQSignature.java`)
- Represents a post-quantum signature
- Supports multiple PQ algorithms: Dilithium2/3/5, Falcon-512/1024
- Encoding format: `[1 byte algorithm type][signature bytes]`

**PostQuantumCrypto** Interface (`crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/PostQuantumCrypto.java`)
- Interface for post-quantum cryptographic operations
- Methods for signature verification and generation

**DilithiumCrypto** (`crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/DilithiumCrypto.java`)
- Dilithium implementation using BouncyCastle PQC provider
- Supports Dilithium2, Dilithium3, and Dilithium5 variants

#### 3. Transaction Encoding/Decoding

**HybridPQTransactionEncoder** (`ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/HybridPQTransactionEncoder.java`)
- Encodes hybrid transactions with both ECDSA and PQ signatures
- Transaction format:
  ```
  TransactionType (0x05) || RLP([
    chainId,
    nonce,
    maxPriorityFeePerGas,
    maxFeePerGas,
    gasLimit,
    to,
    value,
    data,
    accessList,
    v, r, s,           // ECDSA signature
    pqSignature,       // Optional: PQ signature bytes
    pqPublicKey        // Optional: PQ public key
  ])
  ```

**HybridPQTransactionDecoder** (`ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/HybridPQTransactionDecoder.java`)
- Decodes hybrid transactions
- **Fallback Support**: If PQ signature parsing fails or is absent, the transaction falls back to ECDSA-only validation

#### 4. Transaction Class Extensions

**Transaction** (`ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/Transaction.java`)
- Added fields:
  - `pqSignature`: Optional post-quantum signature
  - `pqPublicKey`: Optional post-quantum public key
- New getter methods:
  - `getPQSignature()`: Returns the optional PQ signature
  - `getPQPublicKey()`: Returns the optional PQ public key
- Builder pattern extended with:
  - `pqSignature(PQSignature)`: Set PQ signature
  - `pqPublicKey(Bytes)`: Set PQ public key

## Usage

### Creating a Hybrid PQ Transaction

```java
// Create transaction with ECDSA signature (traditional way)
Transaction.Builder builder = Transaction.builder()
    .type(TransactionType.HYBRID_PQ)
    .chainId(BigInteger.valueOf(1))
    .nonce(0)
    .maxPriorityFeePerGas(Wei.of(1000000000))
    .maxFeePerGas(Wei.of(2000000000))
    .gasLimit(21000)
    .to(Address.fromHexString("0x..."))
    .value(Wei.of(1000000000000000000L))
    .payload(Bytes.EMPTY)
    .accessList(Collections.emptyList());

// Add ECDSA signature
builder.signature(ecdsaSignature);

// Add optional PQ signature
PQSignature pqSig = new PQSignature(
    PQSignature.PQAlgorithmType.DILITHIUM3,
    pqSignatureBytes
);
builder.pqSignature(pqSig);
builder.pqPublicKey(pqPublicKeyBytes);

// Build transaction
Transaction tx = builder.build();
```

### Verification Flow

1. **Hybrid Mode** (PQ signature present):
   - Verify ECDSA signature (traditional Ethereum validation)
   - Verify PQ signature using the provided PQ public key
   - Both signatures must be valid for the transaction to be accepted

2. **Fallback Mode** (No PQ signature or parsing fails):
   - Verify only ECDSA signature
   - Transaction behaves like a standard EIP-1559 transaction
   - Maintains backward compatibility

## Post-Quantum Algorithms Supported

### Dilithium (NIST-approved)
- **Dilithium2**: Fast, 128-bit security
  - Public key: 1312 bytes
  - Signature: 2420 bytes
- **Dilithium3**: Balanced, 192-bit security
  - Public key: 1952 bytes
  - Signature: 3293 bytes
- **Dilithium5**: High security, 256-bit security
  - Public key: 2592 bytes
  - Signature: 4595 bytes

### Falcon (Alternative)
- **Falcon-512**: Compact signatures, 128-bit security
  - Public key: 897 bytes
  - Signature: ~690 bytes
- **Falcon-1024**: Higher security, 256-bit security
  - Public key: 1793 bytes
  - Signature: ~1330 bytes

## Benefits

1. **Quantum Resistance**: Protects against future quantum computer attacks
2. **Backward Compatibility**: Falls back to ECDSA if PQ signatures are not present
3. **Flexible Migration**: Allows gradual transition to post-quantum cryptography
4. **Standards-Based**: Follows EIP-2718 typed transaction envelope standard
5. **Algorithm Agility**: Supports multiple PQ algorithms (Dilithium, Falcon, etc.)

## Implementation Notes

### Dependencies

To use this feature, add BouncyCastle PQC provider:

```gradle
implementation 'org.bouncycastle:bcprov-jdk18on:1.77'
implementation 'org.bouncycastle:bcpqc-jdk18on:1.77'
```

### Security Considerations

1. **Key Management**: PQ private keys are larger than ECDSA keys and require secure storage
2. **Signature Size**: PQ signatures are significantly larger (~2-4KB vs ~65 bytes for ECDSA)
3. **Transaction Size**: Hybrid transactions will be larger due to additional PQ data
4. **Validation Cost**: PQ signature verification is more computationally expensive than ECDSA

### Future Enhancements

1. **Signature Aggregation**: Explore methods to reduce combined signature size
2. **Additional Algorithms**: Support for other NIST-approved PQ algorithms
3. **Threshold Signatures**: Multi-signature support with PQ algorithms
4. **Hardware Acceleration**: Optimize PQ operations with specialized hardware

## Testing

The implementation includes:
- Unit tests for PQSignature encoding/decoding
- Integration tests for hybrid transaction creation and validation
- Compatibility tests for fallback mode
- Performance benchmarks for PQ verification

## References

- [EIP-2718: Typed Transaction Envelope](https://eips.ethereum.org/EIPS/eip-2718)
- [NIST Post-Quantum Cryptography](https://csrc.nist.gov/projects/post-quantum-cryptography)
- [Dilithium Specification](https://pq-crystals.org/dilithium/)
- [Falcon Specification](https://falcon-sign.info/)
- [EJBCA Post-Quantum Support](https://www.ejbca.org/post-quantum-cryptography)

## License

This implementation follows the same Apache 2.0 license as Hyperledger Besu.

