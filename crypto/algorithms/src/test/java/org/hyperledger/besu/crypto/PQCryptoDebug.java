package org.hyperledger.besu.crypto;

import java.security.SecureRandom;
import org.apache.tuweni.bytes.Bytes;

@SuppressWarnings({"DoNotCreateSecureRandomDirectly", "DefaultCharset", "MethodInputParametersMustBeFinal"})
public class PQCryptoDebug {
  public static void main(String[] args) {
    System.out.println("=== Dilithium2 Debug ===");
    DilithiumCrypto crypto2 = new DilithiumCrypto(PQSignature.PQAlgorithmType.DILITHIUM2);
    DilithiumCrypto.KeyPairBytes keyPair2 = crypto2.generateKeyPair(new SecureRandom());
    System.out.println("Public key size: " + keyPair2.getPublicKey().size());
    System.out.println("Expected: " + crypto2.getPublicKeySize());
    
    Bytes testData = Bytes.wrap("Test".getBytes());
    PQSignature sig2 = crypto2.signWithKeyPair(testData, keyPair2);
    System.out.println("Signature size: " + sig2.getSignatureBytes().size());
    System.out.println("Expected: " + PQSignature.PQAlgorithmType.DILITHIUM2.getSignatureSize());
    
    boolean valid2 = crypto2.verify(testData, sig2, keyPair2.getPublicKey());
    System.out.println("Verification: " + valid2);
    
    System.out.println("\n=== Falcon512 Debug ===");
    FalconCrypto crypto512 = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON512);
    FalconCrypto.KeyPairBytes keyPair512 = crypto512.generateKeyPair(new SecureRandom());
    System.out.println("Public key size: " + keyPair512.getPublicKey().size());
    System.out.println("Expected: " + crypto512.getPublicKeySize());
    
    PQSignature sig512 = crypto512.signWithKeyPair(testData, keyPair512);
    System.out.println("Signature size: " + sig512.getSignatureBytes().size());
    System.out.println("Expected max: " + PQSignature.PQAlgorithmType.FALCON512.getSignatureSize());
    
    boolean valid512 = crypto512.verify(testData, sig512, keyPair512.getPublicKey());
    System.out.println("Verification: " + valid512);
  }
}
