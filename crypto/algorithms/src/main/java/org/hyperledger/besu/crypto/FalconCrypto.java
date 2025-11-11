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
import org.bouncycastle.pqc.crypto.falcon.FalconKeyGenerationParameters;
import org.bouncycastle.pqc.crypto.falcon.FalconKeyPairGenerator;
import org.bouncycastle.pqc.crypto.falcon.FalconParameters;
import org.bouncycastle.pqc.crypto.falcon.FalconPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.falcon.FalconPublicKeyParameters;
import org.bouncycastle.pqc.crypto.falcon.FalconSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Falcon post-quantum signature implementation using BouncyCastle PQC.
 *
 * <p>Falcon (Fast Fourier Lattice-based Compact Signatures over NTRU) is a NIST-standardized
 * post-quantum digital signature algorithm. It provides compact signatures and fast verification:
 * - Falcon-512: ~128-bit security, 690 byte signature
 * - Falcon-1024: ~256-bit security, 1330 byte signature
 *
 * <p>Falcon is notable for having smaller signatures compared to Dilithium, making it suitable
 * for bandwidth-constrained applications.
 *
 * <p>Reference: https://falcon-sign.info/
 * <p>NIST: https://csrc.nist.gov/projects/post-quantum-cryptography
 */
public class FalconCrypto implements PostQuantumCrypto {

  private static final Logger LOG = LoggerFactory.getLogger(FalconCrypto.class);
  private final PQSignature.PQAlgorithmType algorithmType;
  private final FalconParameters falconParams;

  /**
   * Create a new FalconCrypto instance.
   *
   * @param algorithmType the Falcon algorithm type (FALCON512 or FALCON1024)
   */
  public FalconCrypto(final PQSignature.PQAlgorithmType algorithmType) {
    this.algorithmType = algorithmType;
    this.falconParams = getFalconParameters(algorithmType);
    LOG.info("Initialized Falcon crypto with algorithm: {}", algorithmType);
  }

  /**
   * Get the BouncyCastle Falcon parameters for the given algorithm type.
   *
   * @param algorithmType the algorithm type
   * @return the corresponding Falcon parameters
   */
  private FalconParameters getFalconParameters(final PQSignature.PQAlgorithmType algorithmType) {
    switch (algorithmType) {
      case FALCON512:
        return FalconParameters.falcon_512;
      case FALCON1024:
        return FalconParameters.falcon_1024;
      default:
        throw new IllegalArgumentException("Unsupported Falcon algorithm type: " + algorithmType);
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

    try {
      // Create Falcon public key parameters from bytes
      // The publicKey bytes should be the h polynomial
      FalconPublicKeyParameters publicKeyParams =
          new FalconPublicKeyParameters(falconParams, publicKey.toArrayUnsafe());

      // Create and initialize the signer for verification
      FalconSigner signer = new FalconSigner();
      signer.init(false, publicKeyParams); // false = verify mode

      // Verify the signature
      boolean isValid =
          signer.verifySignature(data.toArrayUnsafe(), signature.getSignatureBytes().toArrayUnsafe());

      LOG.debug("Falcon signature verification result: {}", isValid);
      return isValid;

    } catch (Exception e) {
      LOG.error("Error verifying Falcon signature", e);
      return false;
    }
  }

  @Override
  public PQSignature sign(final Bytes data, final Bytes privateKey) {
    if (data == null || privateKey == null) {
      throw new IllegalArgumentException("Data and private key must not be null");
    }

    // Falcon private key reconstruction from bytes is not straightforward
    // This method should not be used directly - use signWithKeyPair() instead
    throw new UnsupportedOperationException(
        "Direct private key decoding not supported. " +
        "Use generateKeyPair() to create keys and signWithKeyPair() for signing.");
  }

  @Override
  public PQSignature.PQAlgorithmType getAlgorithmType() {
    return algorithmType;
  }

  @Override
  public int getPublicKeySize() {
    switch (algorithmType) {
      case FALCON512:
        return 896; // Falcon-512 public key size (h polynomial)
      case FALCON1024:
        return 1792; // Falcon-1024 public key size (h polynomial)
      default:
        throw new IllegalArgumentException("Unknown Falcon type");
    }
  }

  /**
   * Generate a new Falcon key pair.
   *
   * @param secureRandom the secure random generator
   * @return a key pair container with public and private key bytes
   */
  public KeyPairBytes generateKeyPair(final SecureRandom secureRandom) {
    try {
      FalconKeyPairGenerator keyPairGenerator = new FalconKeyPairGenerator();
      keyPairGenerator.init(new FalconKeyGenerationParameters(secureRandom, falconParams));

      org.bouncycastle.crypto.AsymmetricCipherKeyPair keyPair = keyPairGenerator.generateKeyPair();

      FalconPublicKeyParameters publicKeyParams = (FalconPublicKeyParameters) keyPair.getPublic();
      FalconPrivateKeyParameters privateKeyParams =
          (FalconPrivateKeyParameters) keyPair.getPrivate();

      // For Falcon, the public key is the polynomial h
      // We need to extract this properly from the parameters
      byte[] hBytes = publicKeyParams.getH();
      Bytes publicKey = Bytes.wrap(hBytes);

      LOG.info(
          "Generated {} key pair: public={} bytes",
          algorithmType,
          publicKey.size());

      return new KeyPairBytes(publicKey, publicKeyParams, privateKeyParams);

    } catch (Exception e) {
      LOG.error("Error generating Falcon key pair", e);
      throw new RuntimeException("Failed to generate Falcon key pair", e);
    }
  }

  /**
   * Simple container for key pair bytes and objects.
   */
  public static class KeyPairBytes {
    private final Bytes publicKey;
    private final FalconPublicKeyParameters publicKeyParams;
    private final FalconPrivateKeyParameters privateKeyParams;

    public KeyPairBytes(final Bytes publicKey,
                        final FalconPublicKeyParameters publicKeyParams,
                        final FalconPrivateKeyParameters privateKeyParams) {
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
      throw new UnsupportedOperationException(
          "Private key export not supported. Use signWithKeyPair() for signing.");
    }

    /**
     * Get the public key parameters for verification.
     */
    public FalconPublicKeyParameters getPublicKeyParams() {
      return publicKeyParams;
    }

    /**
     * Get the private key parameters for signing.
     */
    public FalconPrivateKeyParameters getPrivateKeyParams() {
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
      FalconSigner signer = new FalconSigner();
      signer.init(true, keyPair.getPrivateKeyParams()); // true = sign mode

      // Generate the signature
      byte[] signatureBytes = signer.generateSignature(data.toArrayUnsafe());

      LOG.debug("Generated {} signature of {} bytes", algorithmType, signatureBytes.length);
      return new PQSignature(algorithmType, Bytes.wrap(signatureBytes));

    } catch (Exception e) {
      LOG.error("Error signing with Falcon", e);
      throw new RuntimeException("Failed to sign data with Falcon", e);
    }
  }
}
