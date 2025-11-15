# Hybrid PQ Transaction Tools

Tools để test **HYBRID_PQ transactions** trên Besu với ECDSA + DILITHIUM3 post-quantum signatures.

## 📁 Cấu trúc

```
hardhat-example/
├── scripts/
│   ├── pq-signer.cjs              # Wrapper cho Java PQ signer
│   ├── demo-pq-signer.cjs         # Demo generate keypair và sign
│   ├── test-ecdsa-only-hybrid.cjs # Test ECDSA-only HYBRID_PQ transaction
│   └── send-hybrid-tx-fixed.cjs   # Send full HYBRID_PQ transaction (ECDSA + PQ)
├── pq-keys/                        # Thư mục chứa DILITHIUM3 keypairs
└── package.json
```

## 🚀 Cài đặt

```bash
npm install
```

## 🔑 1. Generate DILITHIUM3 Keypair

```bash
node scripts/demo-pq-signer.cjs
```

**Output:**
- Private key: `pq-keys/dilithium3-private.key` (4056 bytes)
- Public key: `pq-keys/dilithium3-public.key` (1952 bytes)
- Signature size: 3310 bytes (type byte + 3309 bytes DILITHIUM3 signature)

## 📝 2. Test ECDSA-only HYBRID_PQ Transaction

Test fallback mode - HYBRID_PQ transaction với chỉ ECDSA signature (không có PQ):

```bash
node scripts/test-ecdsa-only-hybrid.cjs
```

**Kết quả:**
- ✅ Transaction decoded thành công
- ✅ ECDSA signature verified
- ⏭️ PQ signature skipped (empty)
- ✅ Transaction mined

## 🔐 3. Send Full HYBRID_PQ Transaction

Send transaction với cả ECDSA **VÀ** DILITHIUM3 signatures:

```bash
node scripts/send-hybrid-tx-fixed.cjs
```

**Flow:**
1. Tạo unsigned HYBRID_PQ transaction
2. Compute transaction hash (keccak256 của 0x05 + RLP(unsigned fields))
3. Sign hash với ECDSA → (r, s, yParity)
4. Sign hash với DILITHIUM3 → PQ signature (3310 bytes)
5. Encode full transaction với cả 2 signatures
6. Send đến network

## 📊 Transaction Structure

```
HYBRID_PQ Transaction (type 0x05):
[
  chainId,
  nonce,
  maxPriorityFeePerGas,
  maxFeePerGas,
  gasLimit,
  to,
  value,
  data,
  accessList,
  yParity,              // ECDSA signature
  r,                    // ECDSA signature
  s,                    // ECDSA signature
  pqSignature,          // DILITHIUM3 signature (0x02 + 3309 bytes)
  pqPublicKey           // DILITHIUM3 public key (1952 bytes)
]
```

## 🔧 API Reference

### PQ Signer (pq-signer.cjs)

```javascript
const pqSigner = require('./pq-signer.cjs');

// Generate keypair
const { privateKeyPath, publicKeyPath } = pqSigner.generateKeypair('./pq-keys');

// Sign transaction hash
const signature = pqSigner.sign(privateKeyPath, '0x123...'); // Returns 0x02... (3310 bytes)

// Verify signature
const isValid = pqSigner.verify(publicKeyPath, '0x123...', signature);

// Get public key hex
const pubKey = pqSigner.getPublicKey(publicKeyPath); // Returns 0x... (1952 bytes)
```

## ✅ Validation Rules

Besu validates HYBRID_PQ transactions:

1. **ECDSA signature**: Always verified (standard secp256k1)
2. **PQ signature**: 
   - If present → verify with DILITHIUM3
   - If empty → skip (fallback to ECDSA-only)
3. **Both must sign the SAME transaction hash**

## 🌐 Network Requirements

- Besu fork: **London** trở lên (cần EIP-1559 support)
- Transaction type: `HYBRID_PQ (0x05)` phải được enable trong protocol spec
- Genesis config:
  ```json
  {
    "config": {
      "londonBlock": 0,
      ...
    }
  }
  ```

## 🔍 Debugging

Check Besu logs:
```bash
tail -f ../test-besu/logs/node1.log | grep -i "HybridPQ\|DILITHIUM"
```

## 📚 Related Files

- Java PQ Signer: `../pq-signer/` (BouncyCastle PQC implementation)
- Besu Decoder: `../ethereum/core/src/main/java/.../HybridPQTransactionDecoder.java`
- Network Config: `../test-besu/genesis.json`

## 🎯 Success Criteria

✅ ECDSA-only transaction accepted (fallback mode)  
✅ Full hybrid transaction với DILITHIUM3 signature accepted  
✅ Invalid signatures rejected  
✅ Transaction mined successfully  

---

**Note:** Đây là implementation demo cho research/testing. Production use cần thêm key management, error handling, và security reviews.
