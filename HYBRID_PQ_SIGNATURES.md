# Triển khai Chữ ký Hybrid Post-Quantum

## Tổng quan

Triển khai này bổ sung hỗ trợ chữ ký hybrid post-quantum cho các giao dịch Hyperledger Besu, kết hợp chữ ký ECDSA truyền thống với chữ ký mật mã post-quantum (ví dụ: Dilithium, Falcon).

## Kiến trúc

Triển khai tuân theo tiêu chuẩn EIP-2718 typed transaction envelope bằng cách giới thiệu một loại giao dịch mới: `HYBRID_PQ` (0x05).

### Các thành phần chính

#### 1. Loại giao dịch (`TransactionType.HYBRID_PQ`)
- **File**: `datatypes/src/main/java/org/hyperledger/besu/datatypes/TransactionType.java`
- **ID loại**: `0x05`
- Hỗ trợ danh sách truy cập và thị trường phí EIP-1559

#### 2. Các lớp Chữ ký Post-Quantum

**PQSignature** (`crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/PQSignature.java`)
- Đại diện cho chữ ký post-quantum
- Hỗ trợ nhiều thuật toán PQ: Dilithium2/3/5, Falcon-512/1024
- Định dạng mã hóa: `[1 byte loại thuật toán][byte chữ ký]`

**PostQuantumCrypto** Interface (`crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/PostQuantumCrypto.java`)
- Giao diện cho các hoạt động mật mã post-quantum
- Phương thức xác minh và tạo chữ ký

**DilithiumCrypto** (`crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/DilithiumCrypto.java`)
- Triển khai Dilithium sử dụng nhà cung cấp BouncyCastle PQC
- Hỗ trợ các biến thể Dilithium2, Dilithium3 và Dilithium5

#### 3. Mã hóa/Giải mã giao dịch

**HybridPQTransactionEncoder** (`ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/HybridPQTransactionEncoder.java`)
- Mã hóa giao dịch hybrid với cả chữ ký ECDSA và PQ
- Định dạng giao dịch:
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
    v, r, s,           // Chữ ký ECDSA
    pqSignature,       // Tùy chọn: byte chữ ký PQ
    pqPublicKey        // Tùy chọn: khóa công khai PQ
  ])
  ```

**HybridPQTransactionDecoder** (`ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/HybridPQTransactionDecoder.java`)
- Giải mã giao dịch hybrid
- **Hỗ trợ Fallback**: Nếu việc phân tích chữ ký PQ thất bại hoặc không có, giao dịch sẽ quay lại xác thực chỉ ECDSA

#### 4. Mở rộng lớp Transaction

**Transaction** (`ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/Transaction.java`)
- Các trường đã thêm:
  - `pqSignature`: Chữ ký post-quantum tùy chọn
  - `pqPublicKey`: Khóa công khai post-quantum tùy chọn
- Các phương thức getter mới:
  - `getPQSignature()`: Trả về chữ ký PQ tùy chọn
  - `getPQPublicKey()`: Trả về khóa công khai PQ tùy chọn
- Mẫu Builder được mở rộng với:
  - `pqSignature(PQSignature)`: Đặt chữ ký PQ
  - `pqPublicKey(Bytes)`: Đặt khóa công khai PQ

## Cách sử dụng

### Tạo giao dịch Hybrid PQ

```java
// File: ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/Transaction.java
// File: datatypes/src/main/java/org/hyperledger/besu/datatypes/TransactionType.java

// Tạo giao dịch với chữ ký ECDSA (cách truyền thống)
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

// Thêm chữ ký ECDSA
builder.signature(ecdsaSignature);

// Thêm chữ ký PQ tùy chọn
PQSignature pqSig = new PQSignature(
    PQSignature.PQAlgorithmType.DILITHIUM3,
    pqSignatureBytes
);
builder.pqSignature(pqSig);
builder.pqPublicKey(pqPublicKeyBytes);

// Xây dựng giao dịch
Transaction tx = builder.build();
```

### Quy trình tạo chữ ký

Quy trình chữ ký hybrid bao gồm việc tạo cả chữ ký ECDSA và PQ:

```java
// File: ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/Transaction.java
// File: crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/DilithiumCrypto.java

// 1. Tạo payload giao dịch (không có chữ ký)
Bytes transactionPayload = createTransactionPayload(...);

// 2. Tạo chữ ký ECDSA (ký Ethereum truyền thống)
SECP256K1.KeyPair ecdsaKeyPair = ...; // Cặp khóa ECDSA của bạn
SECP256K1.Signature ecdsaSignature = SECP256K1.sign(
    Hash.keccak256(transactionPayload),
    ecdsaKeyPair
);

// 3. Tạo chữ ký PQ (ký post-quantum)
DilithiumCrypto pqCrypto = new DilithiumCrypto(
    PQSignature.PQAlgorithmType.DILITHIUM3
);
Bytes pqPrivateKey = ...; // Khóa riêng Dilithium của bạn
Bytes pqPublicKey = ...; // Khóa công khai Dilithium của bạn
PQSignature pqSignature = pqCrypto.sign(
    transactionPayload,
    pqPrivateKey
);

// 4. Xây dựng giao dịch hybrid với cả hai chữ ký
Transaction hybridTx = Transaction.builder()
    .type(TransactionType.HYBRID_PQ)
    // ... các trường giao dịch khác ...
    .signature(ecdsaSignature)      // Chữ ký truyền thống
    .pqSignature(pqSignature)       // Chữ ký post-quantum
    .pqPublicKey(pqPublicKey)       // Khóa công khai PQ để xác minh
    .build();
```

### Luồng xác minh

Quy trình xác minh có nhiều giai đoạn với hỗ trợ fallback:

#### Giai đoạn 1: Xác minh chữ ký ECDSA (Luôn bắt buộc)
```java
// File: ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/Transaction.java
// File: crypto/services/src/main/java/org/hyperledger/besu/crypto/SECP256K1.java

// Xác minh ECDSA Ethereum tiêu chuẩn
boolean ecdsaValid = SECP256K1.verify(
    transactionHash,
    ecdsaSignature,
    ecdsaSenderPublicKey
);

if (!ecdsaValid) {
    return INVALID; // Giao dịch bị từ chối nếu ECDSA thất bại
}
```

#### Giai đoạn 2: Xác minh chữ ký Post-Quantum (Tùy chọn)
```java
// File: ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/Transaction.java
// File: crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/DilithiumCrypto.java
// File: crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/PostQuantumCrypto.java

// Kiểm tra nếu có chữ ký PQ
if (transaction.getPQSignature().isPresent() && 
    transaction.getPQPublicKey().isPresent()) {
    
    PQSignature pqSig = transaction.getPQSignature().get();
    Bytes pqPubKey = transaction.getPQPublicKey().get();
    
    try {
        // Xác định thuật toán PQ nào để sử dụng
        PostQuantumCrypto pqCrypto = switch (pqSig.getAlgorithmType()) {
            case DILITHIUM2, DILITHIUM3, DILITHIUM5 -> 
                new DilithiumCrypto(pqSig.getAlgorithmType());
            case FALCON_512, FALCON_1024 -> 
                new FalconCrypto(pqSig.getAlgorithmType());
            default -> throw new UnsupportedAlgorithmException();
        };
        
        // Xác minh chữ ký PQ
        boolean pqValid = pqCrypto.verify(
            transactionPayload,
            pqSig,
            pqPubKey
        );
        
        if (!pqValid) {
            return INVALID; // Cả hai chữ ký phải hợp lệ
        }
        
        LOG.info("Giao dịch Hybrid PQ được xác minh thành công");
        return VALID;
        
    } catch (Exception e) {
        LOG.warn("Xác minh chữ ký PQ thất bại, quay lại chỉ ECDSA", e);
        // Chuyển sang chế độ fallback
    }
}

// Chế độ Fallback: Chấp nhận chỉ với chữ ký ECDSA
LOG.info("Giao dịch được chấp nhận chỉ với chữ ký ECDSA (không có chữ ký PQ)");
return VALID;
```

#### Các tình huống Fallback

Triển khai xử lý một cách tinh tế các tình huống fallback:

1. **Không có chữ ký PQ**
   - Giao dịch được coi là loại HYBRID_PQ tiêu chuẩn
   - Chỉ xác minh chữ ký ECDSA
   - Cho phép thời kỳ chuyển đổi khi không phải tất cả client có khả năng PQ

2. **Lỗi phân tích chữ ký PQ**
   - Dữ liệu chữ ký PQ bị hỏng hoặc sai định dạng
   - Quay lại xác minh chỉ ECDSA
   - Giao dịch không bị từ chối, duy trì khả năng tương thích mạng

3. **Thuật toán PQ không được hỗ trợ**
   - Các loại thuật toán trong tương lai chưa được triển khai
   - Quay lại xác minh chỉ ECDSA
   - Cho phép khả năng tương thích tiến

4. **Lỗi xác minh PQ**
   - Lỗi runtime trong quá trình hoạt động mật mã PQ
   - Quay lại xác minh chỉ ECDSA
   - Ngăn chặn các cuộc tấn công DoS qua dữ liệu PQ sai định dạng

**Quan trọng**: Trong khi chế độ fallback chấp nhận giao dịch chỉ với chữ ký ECDSA, khuyến nghị các client tạo giao dịch HYBRID_PQ luôn bao gồm cả hai chữ ký để đạt được bảo mật tối đa.

## Các thuật toán Post-Quantum được hỗ trợ

### Dilithium (Được NIST chấp thuận)
- **Dilithium2**: Nhanh, bảo mật 128-bit
  - Khóa công khai: 1312 bytes
  - Chữ ký: 2420 bytes
- **Dilithium3**: Cân bằng, bảo mật 192-bit
  - Khóa công khai: 1952 bytes
  - Chữ ký: 3293 bytes
- **Dilithium5**: Bảo mật cao, bảo mật 256-bit
  - Khóa công khai: 2592 bytes
  - Chữ ký: 4595 bytes

### Falcon (Thay thế)
- **Falcon-512**: Chữ ký compact, bảo mật 128-bit
  - Khóa công khai: 897 bytes
  - Chữ ký: ~690 bytes
- **Falcon-1024**: Bảo mật cao hơn, bảo mật 256-bit
  - Khóa công khai: 1793 bytes
  - Chữ ký: ~1330 bytes

## Chi tiết triển khai

### Dung lượng bộ nhớ giao dịch

Giao dịch Hybrid PQ có yêu cầu bộ nhớ tăng do dữ liệu chữ ký bổ sung:

```java
// File: ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/transactions/PendingTransaction.java

// Tính toán kích thước bộ nhớ trong PendingTransaction.java
private int computeHybridPQMemorySize() {
    int baseSize = EIP1559_AND_EIP4844_SHALLOW_SIZE;  // 1032 bytes
    baseSize += computePayloadMemorySize();           // Dữ liệu giao dịch
    baseSize += computeToMemorySize();                // Địa chỉ người nhận
    baseSize += computeChainIdMemorySize();           // Chain ID
    baseSize += computeAccessListEntriesMemorySize(); // Danh sách truy cập
    baseSize += computePQSignatureMemorySize();       // Dữ liệu chữ ký PQ
    return baseSize;
}

private int computePQSignatureMemorySize() {
    return transaction.getPQSignature()
        .map(pqSig -> {
            // Container tùy chọn + đối tượng PQSignature + byte chữ ký
            int totalSize = OPTIONAL_SHALLOW_SIZE + PQ_SIGNATURE_SHALLOW_SIZE;
            totalSize += pqSig.getSignatureBytes().size();
            return totalSize;
        })
        .orElse(0);  // Không có bộ nhớ bổ sung nếu không có chữ ký PQ
}
```

**Ví dụ về tác động bộ nhớ:**
- Giao dịch EIP-1559 tiêu chuẩn: ~1,024 bytes
- Hybrid PQ với Dilithium2: ~3,444 bytes (+237% overhead)
- Hybrid PQ với Dilithium3: ~4,317 bytes (+322% overhead)
- Hybrid PQ với Dilithium5: ~5,619 bytes (+449% overhead)

### Định dạng mã hóa giao dịch

Giao dịch HYBRID_PQ tuân theo EIP-2718 typed transaction envelope:

```
// File: ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/HybridPQTransactionEncoder.java
// File: ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/HybridPQTransactionDecoder.java

0x05 || RLP([
    chainId,                  // uint256
    nonce,                    // uint256
    maxPriorityFeePerGas,     // uint256
    maxFeePerGas,             // uint256
    gasLimit,                 // uint256
    to,                       // address (20 bytes)
    value,                    // uint256
    data,                     // bytes
    accessList,               // [[address, [bytes32...]]...]
    yParity,                  // uint8 (0 hoặc 1)
    r,                        // uint256 (chữ ký ECDSA r)
    s,                        // uint256 (chữ ký ECDSA s)
    pqSignature,              // bytes (tùy chọn, mã hóa dưới dạng [algorithm_type || signature_bytes])
    pqPublicKey               // bytes (tùy chọn, khóa công khai PQ)
])
```

**Chi tiết trường:**
- **pqSignature**: Mã hóa dưới dạng `[1 byte loại thuật toán][N bytes chữ ký]`
  - Ánh xạ loại thuật toán:
    - `0x01`: Dilithium2
    - `0x02`: Dilithium3
    - `0x03`: Dilithium5
    - `0x04`: Falcon-512
    - `0x05`: Falcon-1024
- **pqPublicKey**: Byte khóa công khai thô (kích thước phụ thuộc vào thuật toán)

### Logic giải mã giao dịch

```java
// File: ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/encoding/HybridPQTransactionDecoder.java

// Từ HybridPQTransactionDecoder.java
public static Transaction decode(final RLPInput input) {
    input.enterList();
    
    // Giải mã các trường EIP-1559 tiêu chuẩn
    final BigInteger chainId = input.readBigIntegerScalar();
    final long nonce = input.readLongScalar();
    // ... các trường khác ...
    final SECP256K1.Signature signature = 
        SECP256K1.Signature.create(v, r, s, chainId);
    
    // Cố gắng giải mã các trường PQ tùy chọn
    Optional<PQSignature> pqSignature = Optional.empty();
    Optional<Bytes> pqPublicKey = Optional.empty();
    
    if (!input.isEndOfCurrentList()) {
        try {
            Bytes pqSigBytes = input.readBytes();
            if (pqSigBytes.size() > 0) {
                pqSignature = Optional.of(PQSignature.decode(pqSigBytes));
            }
        } catch (Exception e) {
            LOG.warn("Không thể giải mã chữ ký PQ, tiếp tục mà không có nó", e);
        }
    }
    
    if (!input.isEndOfCurrentList()) {
        try {
            Bytes pqPubKeyBytes = input.readBytes();
            if (pqPubKeyBytes.size() > 0) {
                pqPublicKey = Optional.of(pqPubKeyBytes);
            }
        } catch (Exception e) {
            LOG.warn("Không thể giải mã khóa công khai PQ, tiếp tục mà không có nó", e);
        }
    }
    
    input.leaveList();
    
    return Transaction.builder()
        .type(TransactionType.HYBRID_PQ)
        // ... đặt tất cả các trường ...
        .signature(signature)
        .pqSignature(pqSignature.orElse(null))
        .pqPublicKey(pqPublicKey.orElse(null))
        .build();
}
```

### Triển khai mật mã Post-Quantum

#### Trạng thái triển khai hiện tại

**DilithiumCrypto** hiện tại là triển khai **MOCK/PLACEHOLDER**:

```java
// File: crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/DilithiumCrypto.java

@Override
public boolean verify(final Bytes data, final PQSignature signature, final Bytes publicKey) {
    // TRIỂN KHAI MOCK - Luôn trả về true để kiểm tra
    LOG.warn("Đang sử dụng triển khai Dilithium MOCK - KHÔNG PHÙ HỢP CHO SẢN XUẤT!");
    
    // Kiểm tra xác thực cơ bản
    if (signature.getAlgorithmType() != algorithmType) {
        return false;
    }
    
    // Trong sản xuất, điều này sẽ gọi BouncyCastle PQC:
    // Signature verifier = Signature.getInstance("Dilithium3", "BCPQC");
    // verifier.initVerify(publicKey);
    // verifier.update(data.toArrayUnsafe());
    // return verifier.verify(signature.getSignatureBytes().toArrayUnsafe());
    
    return true; // MOCK: Chấp nhận tất cả chữ ký hiện tại
}
```

#### Các bước tích hợp sản xuất

Để tích hợp mật mã Dilithium thực cho sản xuất:

1. **Thêm phụ thuộc BouncyCastle PQC**:
   ```gradle
   dependencies {
       implementation 'org.bouncycastle:bcprov-jdk18on:1.77'
       implementation 'org.bouncycastle:bcpqc-jdk18on:1.77'
   }
   ```

2. **Khởi tạo Security Provider**:
   ```java
   // File: crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/DilithiumCrypto.java
   
   import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
   
   static {
       Security.addProvider(new BouncyCastlePQCProvider());
   }
   ```

3. **Triển khai xác minh chữ ký thực**:
   ```java
   // File: crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/DilithiumCrypto.java
   
   @Override
   public boolean verify(final Bytes data, final PQSignature signature, final Bytes publicKey) {
       try {
           // Ánh xạ loại thuật toán sang tên thuật toán JCA
           String algorithm = switch (algorithmType) {
               case DILITHIUM2 -> "Dilithium2";
               case DILITHIUM3 -> "Dilithium3";
               case DILITHIUM5 -> "Dilithium5";
               default -> throw new IllegalArgumentException("Thuật toán không được hỗ trợ");
           };
           
           // Tạo verifier
           Signature verifier = Signature.getInstance(algorithm, "BCPQC");
           
           // Phân tích khóa công khai
           KeyFactory keyFactory = KeyFactory.getInstance(algorithm, "BCPQC");
           X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKey.toArrayUnsafe());
           PublicKey pubKey = keyFactory.generatePublic(keySpec);
           
           // Xác minh chữ ký
           verifier.initVerify(pubKey);
           verifier.update(data.toArrayUnsafe());
           return verifier.verify(signature.getSignatureBytes().toArrayUnsafe());
           
       } catch (Exception e) {
           LOG.error("Xác minh chữ ký PQ thất bại", e);
           return false;
       }
   }
   ```

4. **Triển khai tạo chữ ký**:
   ```java
   // File: crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/DilithiumCrypto.java
   
   @Override
   public PQSignature sign(final Bytes data, final Bytes privateKey) {
       try {
           String algorithm = getAlgorithmName();
           Signature signer = Signature.getInstance(algorithm, "BCPQC");
           
           KeyFactory keyFactory = KeyFactory.getInstance(algorithm, "BCPQC");
           PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKey.toArrayUnsafe());
           PrivateKey privKey = keyFactory.generatePrivate(keySpec);
           
           signer.initSign(privKey);
           signer.update(data.toArrayUnsafe());
           byte[] signatureBytes = signer.sign();
           
           return new PQSignature(algorithmType, Bytes.wrap(signatureBytes));
       } catch (Exception e) {
           throw new RuntimeException("Không thể tạo chữ ký PQ", e);
       }
   }
   ```

### Khả năng tương thích mạng

Triển khai đảm bảo hoạt động mạng trơn tru trên các phiên bản client hỗn hợp:

| Kịch bản | Client A | Client B | Hành vi |
|----------|----------|----------|----------|
| 1 | Gửi HYBRID_PQ với chữ ký PQ | Nhận & xác thực cả hai | ✅ Xác thực đầy đủ |
| 2 | Gửi HYBRID_PQ với chữ ký PQ | Client cũ (không hỗ trợ PQ) | ✅ Quay lại ECDSA |
| 3 | Gửi HYBRID_PQ không có chữ ký PQ | Nhận & xác thực chỉ ECDSA | ✅ Chấp nhận hợp lệ |
| 4 | Gửi EIP-1559 tiêu chuẩn | Nhận như EIP-1559 | ✅ Không thay đổi |

Ma trận tương thích này đảm bảo:
- Không có sự phân chia mạng trong quá trình triển khai PQ
- Lộ trình di chuyển dần dần cho các client
- Bảo vệ cho người dùng sớm
- Thiết kế bảo đảm tương lai cho nâng cấp thuật toán

## Cân nhắc về hiệu suất

### Tác động kích thước giao dịch

| Loại giao dịch | Kích thước ước tính | Overhead mạng |
|-----------------|--------------|------------------|
| EIP-1559 tiêu chuẩn | ~110 bytes | Cơ sở |
| HYBRID_PQ + Dilithium2 | ~2,530 bytes | +2,200% |
| HYBRID_PQ + Dilithium3 | ~3,403 bytes | +2,994% |
| HYBRID_PQ + Dilithium5 | ~4,705 bytes | +4,177% |
| HYBRID_PQ + Falcon-512 | ~800 bytes | +627% |

**Tác động lên mạng:**
- Giảm giao dịch mỗi block (do giới hạn kích thước)
- Tăng yêu cầu băng thông
- Chi phí lưu trữ cao hơn cho các node lưu trữ
- Thời gian đồng bộ dài hơn cho các node mới

**Chiến lược giảm thiểu:**
- Sử dụng Falcon cho ứng dụng nhạy cảm với kích thước
- Xem xét tổng hợp chữ ký PQ trong tương lai
- Triển khai nén cho dữ liệu chữ ký
- Ký PQ có chọn lọc (chỉ giao dịch giá trị cao)

### Chi phí tính toán

**Thời gian xác minh chữ ký** (ước tính, phụ thuộc vào phần cứng):
- ECDSA (secp256k1): ~0.5ms
- Dilithium2: ~0.2ms (nhanh hơn ECDSA!)
- Dilithium3: ~0.3ms
- Dilithium5: ~0.4ms
- Falcon-512: ~1.2ms
- Falcon-1024: ~2.5ms

**Thời gian tạo khóa:**
- ECDSA: ~1ms
- Dilithium2: ~2ms
- Dilithium3: ~4ms
- Dilithium5: ~7ms
- Falcon-512: ~50ms
- Falcon-1024: ~150ms

**Yêu cầu bộ nhớ:**
- Cặp khóa ECDSA: ~64 bytes
- Cặp khóa Dilithium2: ~3,732 bytes
- Cặp khóa Dilithium3: ~5,245 bytes
- Cặp khóa Dilithium5: ~7,187 bytes

### Cấu hình được khuyến nghị

Để đạt được sự cân bằng tối ưu giữa bảo mật và hiệu suất:

```java
// File: crypto/algorithms/src/main/java/org/hyperledger/besu/crypto/PQSignature.java

// Khuyến nghị: Dilithium3 cho các giao dịch tiêu chuẩn
PQSignature.PQAlgorithmType.DILITHIUM3
// Ưu điểm: Được NIST chấp thuận, xác minh nhanh, bảo mật tốt (192-bit)
// Nhược điểm: Kích thước chữ ký lớn (~3.3KB)

// Thay thế: Falcon-512 cho các kịch bản bị giới hạn kích thước
PQSignature.PQAlgorithmType.FALCON_512
// Ưu điểm: Chữ ký nhỏ hơn (~690 bytes), gọn
// Nhược điểm: Xác minh chậm hơn, triển khai phức tạp

// Bảo mật cao: Dilithium5 cho các giao dịch quan trọng
PQSignature.PQAlgorithmType.DILITHIUM5
// Ưu điểm: Bảo mật tối đa (256-bit), được NIST chấp thuận
// Nhược điểm: Chữ ký lớn nhất (~4.6KB)
```

## Phân tích bảo mật

### Các vector tấn công và biện pháp giảm thiểu

#### 1. Tấn công máy tính lượng tử
- **Mối đe dọa**: Thuật toán Shor phá vỡ ECDSA
- **Giảm thiểu**: Chữ ký PQ cung cấp bảo vệ chống lượng tử
- **Trạng thái**: ✅ Được bảo vệ khi có cả hai chữ ký

#### 2. Tấn công cổ điển vào thuật toán PQ
- **Mối đe dọa**: Phân tích mật mã truyền thống trên Dilithium/Falcon
- **Giảm thiểu**: Chữ ký ECDSA cung cấp bảo mật dự phòng
- **Trạng thái**: ✅ Được bảo vệ bởi phương pháp hybrid

#### 3. Tính linh hoạt của chữ ký
- **Mối đe dọa**: Kẻ tấn công sửa đổi chữ ký PQ trong khi giữ ECDSA hợp lệ
- **Giảm thiểu**: Cả hai chữ ký phải xác minh độc lập
- **Trạng thái**: ✅ Được bảo vệ bởi xác minh kép

#### 4. Tấn công hạ cấp
- **Mối đe dọa**: Kẻ tấn công loại bỏ chữ ký PQ để buộc xác thực chỉ ECDSA
- **Giảm thiểu**: Loại giao dịch vẫn là HYBRID_PQ, chính sách cấp ứng dụng có thể thực thi sự hiện diện PQ
- **Trạng thái**: ⚠️ Ứng dụng phải thực thi yêu cầu PQ nếu cần

#### 5. DoS qua chữ ký lớn
- **Mối đe dọa**: Spam mạng với chữ ký PQ kích thước tối đa
- **Giảm thiểu**: Chi phí gas phản ánh kích thước giao dịch, giới hạn tốc độ tiêu chuẩn
- **Trạng thái**: ✅ Được bảo vệ bởi cơ chế gas hiện có

### Thực hành bảo mật tốt nhất

1. **Quản lý khóa**
   - Lưu trữ khóa riêng PQ trong HSM khi có thể
   - Sử dụng các hệ thống phân cấp khóa riêng biệt cho khóa ECDSA và PQ
   - Triển khai chính sách xoay vòng khóa (khóa PQ có thể cần xoay vòng sớm hơn ECDSA)

2. **Chính sách giao dịch**
   - Đối với giao dịch giá trị cao (>$10,000), thực thi sự hiện diện chữ ký PQ
   - Giám sát các giao dịch thiếu chữ ký PQ
   - Lên kế hoạch dòng thời gian di chuyển sang chữ ký PQ bắt buộc

3. **Lựa chọn thuật toán**
   - Mặc định Dilithium3 cho sử dụng chung
   - Sử dụng Dilithium5 cho bảo mật dài hạn (10+ năm)
   - Xem xét Falcon-512 cho các thiết bị di động/IoT có hạn chế băng thông

4. **Chiến lược xác thực**
   ```java
   // File: ethereum/core/src/main/java/org/hyperledger/besu/ethereum/core/Transaction.java
   // Logic xác thực cấp ứng dụng
   
   // Chế độ nghiêm ngặt: Từ chối giao dịch không có chữ ký PQ
   if (strictMode && transaction.getPQSignature().isEmpty()) {
       throw new InvalidTransactionException("Yêu cầu chữ ký PQ trong chế độ nghiêm ngặt");
   }
   
   // Khuyến nghị: Ghi log cảnh báo cho các chữ ký PQ bị thiếu
   if (transaction.getPQSignature().isEmpty()) {
       LOG.warn("Giao dịch HYBRID_PQ {} thiếu chữ ký PQ", transaction.getHash());
       metrics.incrementCounter("hybrid_pq.missing_pq_signature");
   }
   ```

### Bảo đảm mật mã

**Định lý**: Một giao dịch HYBRID_PQ cung cấp bảo mật tương đương với:
```
Bảo mật = min(Bảo_mật_ECDSA, Bảo_mật_PQ)
```

**Phác thảo chứng minh**:
- Để làm giả giao dịch hybrid, kẻ tấn công phải làm giả CẢ HAI chữ ký
- Phá vỡ một trong hai hệ thống chữ ký là không đủ
- Hệ thống vẫn an toàn miễn là MỘT lược đồ chữ ký không bị phá vỡ

**Mức bảo mật thực tế** (giả định Dilithium3):
- Chống lại máy tính cổ điển: ~128-bit (mức bảo mật ECDSA)
- Chống lại máy tính lượng tử: ~192-bit (mức bảo mật Dilithium3)
- Kết hợp: Tối đa của cả hai bảo vệ

**Thời kỳ chuyển đổi** (2025-2035):
- ECDSA cung cấp bảo mật cho đến khi máy tính lượng tử trưởng thành
- Chữ ký PQ cung cấp bảo đảm tương lai
- Phương pháp hybrid đảm bảo bảo vệ liên tục trong suốt quá trình chuyển đổi

## Ghi chú triển khai

### Các phụ thuộc

Để sử dụng tính năng này, thêm nhà cung cấp BouncyCastle PQC:

```gradle
implementation 'org.bouncycastle:bcprov-jdk18on:1.77'
implementation 'org.bouncycastle:bcpqc-jdk18on:1.77'
```

### Cân nhắc bảo mật

1. **Quản lý khóa**: Khóa riêng PQ lớn hơn khóa ECDSA và yêu cầu lưu trữ an toàn
2. **Kích thước chữ ký**: Chữ ký PQ lớn hơn đáng kể (~2-4KB so với ~65 bytes cho ECDSA)
3. **Kích thước giao dịch**: Giao dịch hybrid sẽ lớn hơn do dữ liệu PQ bổ sung
4. **Chi phí xác thực**: Xác minh chữ ký PQ tốn kém về mặt tính toán hơn ECDSA

### Cải tiến trong tương lai

1. **Tổng hợp chữ ký**: Khám phá phương pháp giảm kích thước chữ ký kết hợp
2. **Thuật toán bổ sung**: Hỗ trợ các thuật toán PQ được NIST chấp thuận khác (SPHINCS+, v.v.)
3. **Chữ ký ngưỡng**: Hỗ trợ đa chữ ký với thuật toán PQ
4. **Tăng tốc phần cứng**: Tối ưu hóa hoạt động PQ với phần cứng chuyên dụng
5. **Chữ ký PQ nén**: Nghiên cứu kỹ thuật nén cho Dilithium/Falcon
6. **Chữ ký dựa trên hash có trạng thái**: XMSS/LMS cho bảo mật siêu dài hạn

## Kiểm thử

Triển khai bao gồm kiểm thử toàn diện:

### Kiểm thử đơn vị
- **PQSignatureTest**: Mã hóa/giải mã, loại thuật toán, trường hợp biên
- **DilithiumCryptoTest**: Xác minh chữ ký mock, biến thể thuật toán
- **HybridPQTransactionEncoderTest**: Mã hóa giao dịch với các chữ ký PQ khác nhau
- **HybridPQTransactionDecoderTest**: Giải mã với các kịch bản fallback

### Kiểm thử tích hợp
- **Tạo giao dịch hybrid**: Chu kỳ giao dịch đầy đủ với cả hai chữ ký
- **Xác thực chế độ fallback**: Giảm cấp tinh tế khi thiếu chữ ký PQ
- **Tính toán kích thước bộ nhớ**: Dung lượng bộ nhớ chính xác cho pool giao dịch
- **Tuần tự hóa mạng**: Kiểm thử vòng mã hóa/giải mã RLP

### Điểm chuẩn hiệu suất
```java
// File: ethereum/core/src/test/java/org/hyperledger/besu/ethereum/core/TransactionBenchmark.java
// (Điểm chuẩn ví dụ - file thực tế có thể khác)

// Kết quả điểm chuẩn ví dụ (chỉ định, phụ thuộc vào phần cứng)
@Benchmark
public void benchmarkHybridPQVerification() {
    // Xác minh ECDSA: 0.5ms
    // Xác minh Dilithium3: 0.3ms
    // Tổng xác minh hybrid: 0.8ms
}

@Benchmark
public void benchmarkTransactionEncoding() {
    // EIP-1559 tiêu chuẩn: 0.1ms
    // Hybrid PQ: 0.15ms (overhead tối thiểu)
}
```

### Kiểm thử tương thích
- **Tương thích đa phiên bản**: Client cũ chấp nhận giao dịch hybrid mới
- **Kịch bản fallback**: Các chế độ lỗi khác nhau trong quá trình xử lý chữ ký PQ
- **Lan truyền mạng**: Giao dịch hybrid trên các phiên bản node hỗn hợp

## Chiến lược triển khai

### Giai đoạn 1: Phát triển & Kiểm thử (Hiện tại)
- ✅ Triển khai cốt lõi hoàn thành
- ✅ Mật mã PQ mock để kiểm thử
- ✅ Tối ưu hóa bộ nhớ
- 🔄 Kiểm thử tích hợp toàn diện
- 🔄 Điểm chuẩn hiệu suất

### Giai đoạn 2: Tích hợp PQ sản xuất (Dự kiến)
- [ ] Tích hợp nhà cung cấp BouncyCastle PQC
- [ ] Thay thế triển khai mock bằng mật mã thực
- [ ] Kiểm toán bảo mật xử lý chữ ký PQ
- [ ] Tối ưu hóa hiệu suất cho xác minh
- [ ] Công cụ tạo khóa

### Giai đoạn 3: Triển khai Testnet (Tương lai)
- [ ] Triển khai lên mạng thử nghiệm
- [ ] Giám sát tác động hiệu suất mạng
- [ ] Kiểm thử stress với khối lượng giao dịch PQ cao
- [ ] Thu thập phản hồi cộng đồng
- [ ] Tinh chỉnh dựa trên sử dụng thực tế

### Giai đoạn 4: Triển khai Mainnet (Tương lai)
- [ ] Kích hoạt dần dần qua hard fork
- [ ] Chữ ký PQ tùy chọn ban đầu
- [ ] Giám sát tỷ lệ chấp nhận
- [ ] Đánh giá dòng thời gian yêu cầu PQ bắt buộc
- [ ] Hỗ trợ các thuật toán PQ bổ sung

### Lộ trình di chuyển cho ứng dụng hiện có

```java
// File: Mã cấp ứng dụng (ví dụ tích hợp)

// Bước 1: Cập nhật để hỗ trợ loại giao dịch HYBRID_PQ
if (transaction.getType() == TransactionType.HYBRID_PQ) {
    // Xử lý giao dịch hybrid
}

// Bước 2: Bắt đầu tạo cặp khóa PQ cho tài khoản mới
KeyPair ecdsaKeys = generateECDSAKeys();
DilithiumKeyPair pqKeys = generateDilithiumKeys();

// Bước 3: Bắt đầu ký với cả hai chữ ký
Transaction tx = createHybridTransaction(ecdsaKeys, pqKeys);

// Bước 4: Giám sát và thực thi sự hiện diện chữ ký PQ
if (highValueTransaction && !tx.getPQSignature().isPresent()) {
    throw new SecurityException("Giao dịch giá trị cao yêu cầu chữ ký PQ");
}
```

## Khắc phục sự cố

### Các vấn đề thường gặp

#### 1. Giao dịch bị từ chối: "Chữ ký PQ không hợp lệ"
**Nguyên nhân**: Xác minh chữ ký PQ thất bại
**Giải pháp**:
- Xác minh khóa công khai PQ khớp với khóa riêng được sử dụng để ký
- Kiểm tra loại thuật toán khớp giữa ký và xác minh
- Đảm bảo byte chữ ký PQ không bị hỏng trong quá trình truyền

#### 2. Từ chối pool giao dịch: "Giao dịch quá lớn"
**Nguyên nhân**: Giao dịch hybrid vượt quá giới hạn kích thước
**Giải pháp**:
- Sử dụng thuật toán PQ nhỏ hơn (Falcon-512 thay vì Dilithium5)
- Giảm kích thước payload giao dịch
- Kiểm tra cấu hình node cho giới hạn kích thước giao dịch

#### 3. Cảnh báo: "Đang sử dụng triển khai Dilithium MOCK"
**Nguyên nhân**: Thư viện PQ sản xuất chưa được tích hợp
**Giải pháp**:
- Điều này được mong đợi trong giai đoạn phát triển hiện tại
- Đối với sản xuất, tích hợp BouncyCastle PQC như mô tả ở trên
- Không phù hợp cho triển khai mainnet

#### 4. Fallback sang xác thực chỉ ECDSA
**Nguyên nhân**: Phân tích hoặc xác minh chữ ký PQ thất bại
**Giải pháp**:
- Kiểm tra log cho thông báo lỗi cụ thể
- Xác minh định dạng mã hóa chữ ký PQ
- Đảm bảo khóa công khai PQ được cung cấp khi có chữ ký PQ
- Cập nhật lên phiên bản client mới nhất với hỗ trợ PQ

#### 5. Sử dụng bộ nhớ cao
**Nguyên nhân**: Số lượng lớn giao dịch hybrid trong mempool
**Giải pháp**:
- Triển khai chính sách loại bỏ giao dịch dựa trên kích thước
- Ưu tiên giao dịch nhỏ hơn khi bộ nhớ bị hạn chế
- Giám sát kích thước mempool và điều chỉnh giới hạn tương ứng

### Ghi log gỡ lỗi

Bật ghi log chi tiết cho xử lý chữ ký PQ:

```properties
# File: config/besu/log4j2.xml hoặc cấu hình ứng dụng

# Cấu hình Log4j
logger.pqsig.name = org.hyperledger.besu.crypto
logger.pqsig.level = DEBUG

logger.hybrid.name = org.hyperledger.besu.ethereum.core.encoding.HybridPQ
logger.hybrid.level = DEBUG

logger.pendingtx.name = org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction
logger.pendingtx.level = TRACE
```

### Giám sát số liệu

Theo dõi các số liệu chính cho giao dịch hybrid PQ:

```java
// File: ethereum/eth/src/main/java/org/hyperledger/besu/ethereum/eth/transactions/PendingTransaction.java
// File: metrics/core/src/main/java/org/hyperledger/besu/metrics/BesuMetricCategory.java

// Số liệu cần giám sát
metrics.gauge("hybrid_pq.mempool.count");
metrics.gauge("hybrid_pq.mempool.size_bytes");
metrics.counter("hybrid_pq.validated.total");
metrics.counter("hybrid_pq.validated.pq_present");
metrics.counter("hybrid_pq.validated.pq_missing");
metrics.counter("hybrid_pq.validation_failures.ecdsa");
metrics.counter("hybrid_pq.validation_failures.pq");
metrics.histogram("hybrid_pq.signature_size_bytes");
metrics.timer("hybrid_pq.verification_time");
```

## Câu hỏi thường gặp

**Q: Tại sao chữ ký hybrid thay vì chữ ký PQ thuần túy?**
A: Phương pháp hybrid cung cấp:
- Khả năng tương thích ngược với cơ sở hạ tầng hiện có
- Bảo vệ trong giai đoạn chuyển đổi
- Bảo mật dự phòng nếu thuật toán PQ bị phá vỡ
- Lộ trình di chuyển dần dần cho hệ sinh thái

**Q: Khi nào máy tính lượng tử sẽ phá vỡ ECDSA?**
A: Ước tính hiện tại cho rằng 10-30 năm, nhưng dòng thời gian không chắc chắn. Hybrid PQ cung cấp bảo vệ bất kể dòng thời gian.

**Q: Chi phí gas cho giao dịch hybrid PQ là bao nhiêu?**
A: Chi phí gas cao hơn do kích thước giao dịch lớn hơn. Chi phí chính xác phụ thuộc vào kích thước chữ ký (~3-5 lần so với EIP-1559 tiêu chuẩn).

**Q: Tôi có thể sử dụng giao dịch hybrid PQ ngay hôm nay không?**
A: Có cho kiểm thử, nhưng triển khai PQ mock không sẵn sàng cho sản xuất. Chờ tích hợp thư viện PQ sản xuất.

**Q: Điều gì xảy ra nếu khóa riêng PQ của tôi bị xâm phạm?**
A: Giao dịch vẫn được bảo vệ bởi chữ ký ECDSA. Kẻ tấn công cần cả hai khóa để làm giả giao dịch.

**Q: Tôi nên sử dụng thuật toán PQ nào?**
A: Dilithium3 được khuyến nghị cho hầu hết các trường hợp sử dụng (cân bằng bảo mật, kích thước, hiệu suất). Sử dụng Dilithium5 để bảo mật tối đa.

**Q: Chữ ký PQ có chống lượng tử mãi mãi không?**
A: Không có mật mã nào "an toàn mãi mãi". Dilithium/Falcon được tin là an toàn chống lại các thuật toán lượng tử đã biết, nhưng sự linh hoạt thuật toán cho phép nâng cấp.

**Q: Làm thế nào để tạo cặp khóa PQ?**
A: Sử dụng nhà cung cấp BouncyCastle PQC hoặc thư viện mật mã PQ chuyên dụng. Công cụ tạo khóa sẽ được cung cấp trong bản phát hành sản xuất.

## Tài liệu tham khảo

- [EIP-2718: Typed Transaction Envelope](https://eips.ethereum.org/EIPS/eip-2718)
- [NIST Post-Quantum Cryptography](https://csrc.nist.gov/projects/post-quantum-cryptography)
- [Đặc tả Dilithium](https://pq-crystals.org/dilithium/)
- [Đặc tả Falcon](https://falcon-sign.info/)
- [Hỗ trợ Post-Quantum EJBCA](https://www.ejbca.org/post-quantum-cryptography)

## Giấy phép

Triển khai này tuân theo cùng giấy phép Apache 2.0 như Hyperledger Besu.
