/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.crypto;

import java.security.SecureRandom;

import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumKeyGenerationParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumKeyPairGenerator;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumPublicKeyParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dilithium post-quantum signature implementation using BouncyCastle PQC.
 *
 * <p>Dilithium is a NIST-standardized post-quantum digital signature algorithm based on the
 * hardness of lattice problems. It provides three security levels:
 * - Dilithium2: ~128-bit security
 * - Dilithium3: ~192-bit security
 * - Dilithium5: ~256-bit security
 *
 * <p>Reference: https://pq-crystals.org/dilithium/
 * <p>NIST: https://csrc.nist.gov/projects/post-quantum-cryptography
 */
public class DilithiumCrypto implements PostQuantumCrypto {

  private static final Logger LOG = LoggerFactory.getLogger(DilithiumCrypto.class);
  private final PQSignature.PQAlgorithmType algorithmType;
  private final DilithiumParameters dilithiumParams;

  /**
   * Create a new DilithiumCrypto instance.
   *
   * @param algorithmType the Dilithium algorithm type (DILITHIUM2, DILITHIUM3, or DILITHIUM5)
   */
  public DilithiumCrypto(final PQSignature.PQAlgorithmType algorithmType) {
    this.algorithmType = algorithmType;
    this.dilithiumParams = getDilithiumParameters(algorithmType);
    LOG.info("Initialized Dilithium crypto with algorithm: {}", algorithmType);
  }

  /**
   * Get the BouncyCastle Dilithium parameters for the given algorithm type.
   *
   * @param algorithmType the algorithm type
   * @return the corresponding Dilithium parameters
   */
  private DilithiumParameters getDilithiumParameters(
      final PQSignature.PQAlgorithmType algorithmType) {
    switch (algorithmType) {
      case DILITHIUM2:
        return DilithiumParameters.dilithium2;
      case DILITHIUM3:
        return DilithiumParameters.dilithium3;
      case DILITHIUM5:
        return DilithiumParameters.dilithium5;
      default:
        throw new IllegalArgumentException(
            "Unsupported Dilithium algorithm type: " + algorithmType);
    }
  }

  @Override
  public boolean verify(final Bytes data, final PQSignature signature, final Bytes publicKey) {
    if (data == null || signature == null || publicKey == null) {
      LOG.debug("Null input parameters for verification");
      return false;
    }

    // Verify signature algorithm type matches
    if (signature.getAlgorithmType() != algorithmType) {
      LOG.debug(
          "Algorithm type mismatch: expected {}, got {}",
          algorithmType,
          signature.getAlgorithmType());
      return false;
    }

    // Verify public key size
    int expectedPubKeySize = getPublicKeySize();
    if (publicKey.size() != expectedPubKeySize) {
      LOG.debug(
          "Public key size mismatch: expected {}, got {}", expectedPubKeySize, publicKey.size());
      return false;
    }

    // Verify signature size
    int expectedSigSize = algorithmType.getSignatureSize();
    if (signature.getSignatureBytes().size() != expectedSigSize) {
      LOG.debug(
          "Signature size mismatch: expected {}, got {}",
          expectedSigSize,
          signature.getSignatureBytes().size());
      return false;
    }

    try {
      // Extract rho and t1 from the public key bytes
      // The public key is encoded as: rho (32 bytes) || t1 (variable length)
      byte[] publicKeyBytes = publicKey.toArrayUnsafe();
      
      // For Dilithium2: rho=32, t1=1280, total=1312
      // For Dilithium3: rho=32, t1=1920, total=1952  
      // For Dilithium5: rho=32, t1=2560, total=2592
      byte[] rho = new byte[32]; // rho is always 32 bytes
      byte[] t1 = new byte[publicKeyBytes.length - 32];
      
      System.arraycopy(publicKeyBytes, 0, rho, 0, 32);
      System.arraycopy(publicKeyBytes, 32, t1, 0, t1.length);
      
      // Create Dilithium public key parameters from rho and t1
      DilithiumPublicKeyParameters publicKeyParams =
          new DilithiumPublicKeyParameters(dilithiumParams, rho, t1);

      // Create and initialize the signer for verification
      DilithiumSigner signer = new DilithiumSigner();
      signer.init(false, publicKeyParams); // false = verify mode

      // Verify the signature
      boolean isValid =
          signer.verifySignature(data.toArrayUnsafe(), signature.getSignatureBytes().toArrayUnsafe());

      LOG.debug("Dilithium signature verification result: {}", isValid);
      return isValid;

    } catch (Exception e) {
      LOG.error("Error verifying Dilithium signature", e);
      return false;
    }
  }

  @Override
  public PQSignature sign(final Bytes data, final Bytes privateKey) {
    if (data == null || privateKey == null) {
      throw new IllegalArgumentException("Data and private key must not be null");
    }

    try {
      // For signing with BouncyCastle, we need to reconstruct the key pair
      // The private key bytes should contain both private and public key material
      // This is a limitation - in real usage, keys should be managed differently
      
      // For now, we'll use a simplified approach where the privateKey parameter
      // contains the full encoded private key from BouncyCastle
      byte[] privateKeyBytes = privateKey.toArrayUnsafe();
      
      // Decode the private key (BouncyCastle format includes all necessary components)
      DilithiumPrivateKeyParameters privateKeyParams =
          decodeDilithiumPrivateKey(privateKeyBytes);

      // Create and initialize the signer
      DilithiumSigner signer = new DilithiumSigner();
      signer.init(true, privateKeyParams); // true = sign mode

      // Generate the signature
      byte[] signatureBytes = signer.generateSignature(data.toArrayUnsafe());

      LOG.debug("Generated {} signature of {} bytes", algorithmType, signatureBytes.length);
      return new PQSignature(algorithmType, Bytes.wrap(signatureBytes));

    } catch (Exception e) {
      LOG.error("Error signing with Dilithium", e);
      throw new RuntimeException("Failed to sign data with Dilithium", e);
    }
  }

  /**
   * Decode a Dilithium private key from encoded bytes.
   * This method handles the BouncyCastle encoding format.
   */
  @SuppressWarnings("UnusedVariable")
  private DilithiumPrivateKeyParameters decodeDilithiumPrivateKey(final byte[] encoded) {
    // BouncyCastle's Dilithium encoding stores the key material in a specific format
    // For simplicity, we assume the encoded bytes are from a BouncyCastle key generation
    // In production, you'd use proper ASN.1/SubjectPublicKeyInfo encoding
    
    // This is a workaround - the proper way would be to use BouncyCastle's
    // key encoding/decoding facilities or ASN.1 parsing
    throw new UnsupportedOperationException(
        "Direct private key decoding not supported. " +
        "Use generateKeyPair() to create keys and keep the KeyPairBytes object for signing.");
  }

  @Override
  public PQSignature.PQAlgorithmType getAlgorithmType() {
    return algorithmType;
  }

  @Override
  public int getPublicKeySize() {
    switch (algorithmType) {
      case DILITHIUM2:
        return 1312; // Dilithium2 public key size
      case DILITHIUM3:
        return 1952; // Dilithium3 public key size
      case DILITHIUM5:
        return 2592; // Dilithium5 public key size
      default:
        throw new IllegalArgumentException("Unknown Dilithium type");
    }
  }

  /**
   * Generate a new Dilithium key pair.
   *
   * @param secureRandom the secure random generator
   * @return a key pair container with public and private key bytes
   */
  public KeyPairBytes generateKeyPair(final SecureRandom secureRandom) {
    try {
      DilithiumKeyPairGenerator keyPairGenerator = new DilithiumKeyPairGenerator();
      keyPairGenerator.init(
          new DilithiumKeyGenerationParameters(secureRandom, dilithiumParams));

      org.bouncycastle.crypto.AsymmetricCipherKeyPair keyPair = keyPairGenerator.generateKeyPair();

      DilithiumPublicKeyParameters publicKeyParams =
          (DilithiumPublicKeyParameters) keyPair.getPublic();
      DilithiumPrivateKeyParameters privateKeyParams =
          (DilithiumPrivateKeyParameters) keyPair.getPrivate();

      // For Dilithium, we need to encode the public key properly
      // The public key consists of rho (seed) and t1 (polynomial vector)
      // We'll create a proper encoding by packing these together
      byte[] rho = publicKeyParams.getRho();
      byte[] t1 = publicKeyParams.getT1();
      
      // Concatenate rho and t1 to form the public key
      Bytes publicKey = Bytes.concatenate(
          Bytes.wrap(rho), 
          Bytes.wrap(t1));
      
      LOG.info(
          "Generated {} key pair: public={} bytes (rho={}, t1={})",
          algorithmType,
          publicKey.size(),
          rho.length,
          t1.length);

      // Store the actual key parameters, not just bytes
      return new KeyPairBytes(publicKey, publicKeyParams, privateKeyParams);

    } catch (Exception e) {
      LOG.error("Error generating Dilithium key pair", e);
      throw new RuntimeException("Failed to generate Dilithium key pair", e);
    }
  }

  /**
   * Simple container for key pair bytes and objects.
   */
  public static class KeyPairBytes {
    private final Bytes publicKey;
    private final DilithiumPublicKeyParameters publicKeyParams;
    private final DilithiumPrivateKeyParameters privateKeyParams;

    public KeyPairBytes(final Bytes publicKey, 
                        final DilithiumPublicKeyParameters publicKeyParams,
                        final DilithiumPrivateKeyParameters privateKeyParams) {
      this.publicKey = publicKey;
      this.publicKeyParams = publicKeyParams;
      this.privateKeyParams = privateKeyParams;
    }

    public Bytes getPublicKey() {
      return publicKey;
    }

    /**
     * Get private key bytes - this returns an encoded form that can be stored.
     * Note: For actual signing, use the signWithKeyPair method.
     */
    public Bytes getPrivateKey() {
      // Return a marker that indicates this should use the key object
      // In real implementation, you'd properly encode the private key
      throw new UnsupportedOperationException(
          "Private key export not supported. Use signWithKeyPair() for signing.");
    }

    /**
     * Get the public key parameters for verification.
     */
    public DilithiumPublicKeyParameters getPublicKeyParams() {
      return publicKeyParams;
    }

    /**
     * Get the private key parameters for signing.
     */
    public DilithiumPrivateKeyParameters getPrivateKeyParams() {
      return privateKeyParams;
    }
  }

  /**
   * Sign data using a KeyPairBytes object (preferred method).
   *
   * @param data the data to sign
   * @param keyPair the key pair containing the private key
   * @return the PQ signature
   */
  public PQSignature signWithKeyPair(final Bytes data, final KeyPairBytes keyPair) {
    if (data == null || keyPair == null) {
      throw new IllegalArgumentException("Data and key pair must not be null");
    }

    try {
      // Create and initialize the signer
      DilithiumSigner signer = new DilithiumSigner();
      signer.init(true, keyPair.getPrivateKeyParams()); // true = sign mode

      // Generate the signature
      byte[] signatureBytes = signer.generateSignature(data.toArrayUnsafe());

      LOG.debug("Generated {} signature of {} bytes", algorithmType, signatureBytes.length);
      return new PQSignature(algorithmType, Bytes.wrap(signatureBytes));

    } catch (Exception e) {
      LOG.error("Error signing with Dilithium", e);
      throw new RuntimeException("Failed to sign data with Dilithium", e);
    }
  }
}
