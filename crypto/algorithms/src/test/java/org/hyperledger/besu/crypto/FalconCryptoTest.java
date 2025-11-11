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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"DoNotCreateSecureRandomDirectly", "DefaultCharset"})
public class FalconCryptoTest {

  @Test
  public void testGetAlgorithmType() {
    FalconCrypto crypto = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON512);
    assertThat(crypto.getAlgorithmType()).isEqualTo(PQSignature.PQAlgorithmType.FALCON512);
  }

  @Test
  public void testGetPublicKeySize() {
    FalconCrypto crypto512 = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON512);
    assertThat(crypto512.getPublicKeySize()).isEqualTo(896);

    FalconCrypto crypto1024 = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON1024);
    assertThat(crypto1024.getPublicKeySize()).isEqualTo(1792);
  }

  @Test
  public void testSignAndVerifyFalcon512() {
    FalconCrypto crypto = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON512);
    SecureRandom random = new SecureRandom();

    // Generate key pair
    FalconCrypto.KeyPairBytes keyPair = crypto.generateKeyPair(random);

    // Test data
    Bytes testData = Bytes.wrap("Hello, Falcon Signatures!".getBytes());

    // Sign the data
    PQSignature signature = crypto.signWithKeyPair(testData, keyPair);

    // Verify signature properties
    assertThat(signature.getAlgorithmType()).isEqualTo(PQSignature.PQAlgorithmType.FALCON512);

    // Note: Falcon signatures are variable length, so we check it's within expected range
    assertThat(signature.getSignatureBytes().size())
        .isLessThanOrEqualTo(PQSignature.PQAlgorithmType.FALCON512.getSignatureSize());

    // Verify the signature
    boolean isValid = crypto.verify(testData, signature, keyPair.getPublicKey());
    assertThat(isValid).isTrue();
  }

  @Test
  public void testSignAndVerifyFalcon1024() {
    FalconCrypto crypto = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON1024);
    SecureRandom random = new SecureRandom();

    // Generate key pair
    FalconCrypto.KeyPairBytes keyPair = crypto.generateKeyPair(random);

    // Test data
    Bytes testData = Bytes.wrap("Test Falcon-1024".getBytes());

    // Sign and verify
    PQSignature signature = crypto.signWithKeyPair(testData, keyPair);
    boolean isValid = crypto.verify(testData, signature, keyPair.getPublicKey());

    assertThat(isValid).isTrue();
    assertThat(signature.getSignatureBytes().size())
        .isLessThanOrEqualTo(PQSignature.PQAlgorithmType.FALCON1024.getSignatureSize());
  }

  @Test
  public void testVerifyWithWrongPublicKey() {
    FalconCrypto crypto = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON512);
    SecureRandom random = new SecureRandom();

    // Generate two key pairs
    FalconCrypto.KeyPairBytes keyPair1 = crypto.generateKeyPair(random);
    FalconCrypto.KeyPairBytes keyPair2 = crypto.generateKeyPair(random);

    // Test data
    Bytes testData = Bytes.wrap("Test data".getBytes());

    // Sign with first key
    PQSignature signature = crypto.signWithKeyPair(testData, keyPair1);

    // Verify with wrong public key should fail
    boolean isValid = crypto.verify(testData, signature, keyPair2.getPublicKey());
    assertThat(isValid).isFalse();
  }

  @Test
  public void testVerifyWithModifiedData() {
    FalconCrypto crypto = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON512);
    SecureRandom random = new SecureRandom();

    // Generate key pair
    FalconCrypto.KeyPairBytes keyPair = crypto.generateKeyPair(random);

    // Original data
    Bytes originalData = Bytes.wrap("Original data".getBytes());
    Bytes modifiedData = Bytes.wrap("Modified data".getBytes());

    // Sign original data
    PQSignature signature = crypto.signWithKeyPair(originalData, keyPair);

    // Verify with modified data should fail
    boolean isValid = crypto.verify(modifiedData, signature, keyPair.getPublicKey());
    assertThat(isValid).isFalse();
  }

  @Test
  public void testVerifyWithNullInputs() {
    FalconCrypto crypto = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON512);
    SecureRandom random = new SecureRandom();

    FalconCrypto.KeyPairBytes keyPair = crypto.generateKeyPair(random);
    Bytes testData = Bytes.wrap("Test".getBytes());
    PQSignature signature = crypto.signWithKeyPair(testData, keyPair);

    // Test null inputs
    assertThat(crypto.verify(null, signature, keyPair.getPublicKey())).isFalse();
    assertThat(crypto.verify(testData, null, keyPair.getPublicKey())).isFalse();
    assertThat(crypto.verify(testData, signature, null)).isFalse();
  }

  @Test
  public void testVerifyWithWrongAlgorithmType() {
    FalconCrypto crypto512 = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON512);
    FalconCrypto crypto1024 = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON1024);
    SecureRandom random = new SecureRandom();

    // Generate key pair with Falcon-512
    FalconCrypto.KeyPairBytes keyPair = crypto512.generateKeyPair(random);
    Bytes testData = Bytes.wrap("Test".getBytes());

    // Sign with Falcon-512
    PQSignature signature = crypto512.signWithKeyPair(testData, keyPair);

    // Try to verify with Falcon-1024 instance should fail
    boolean isValid = crypto1024.verify(testData, signature, keyPair.getPublicKey());
    assertThat(isValid).isFalse();
  }

  @Test
  public void testSignWithNullInputs() {
    FalconCrypto crypto = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON512);
    SecureRandom random = new SecureRandom();
    FalconCrypto.KeyPairBytes keyPair = crypto.generateKeyPair(random);

    assertThatThrownBy(() -> crypto.signWithKeyPair(null, keyPair))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> crypto.signWithKeyPair(Bytes.wrap("test".getBytes()), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testKeyPairGeneration() {
    FalconCrypto crypto = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON512);
    SecureRandom random = new SecureRandom();

    FalconCrypto.KeyPairBytes keyPair = crypto.generateKeyPair(random);

    // Verify key sizes
    assertThat(keyPair.getPublicKey().size()).isEqualTo(896); // Falcon-512 public key size
    assertThat(keyPair.getPublicKeyParams()).isNotNull();
    assertThat(keyPair.getPrivateKeyParams()).isNotNull();
  }

  @Test
  public void testMultipleSignaturesWithSameKey() {
    FalconCrypto crypto = new FalconCrypto(PQSignature.PQAlgorithmType.FALCON512);
    SecureRandom random = new SecureRandom();

    FalconCrypto.KeyPairBytes keyPair = crypto.generateKeyPair(random);

    // Sign multiple different messages
    Bytes data1 = Bytes.wrap("Message 1".getBytes());
    Bytes data2 = Bytes.wrap("Message 2".getBytes());
    Bytes data3 = Bytes.wrap("Message 3".getBytes());

    PQSignature sig1 = crypto.signWithKeyPair(data1, keyPair);
    PQSignature sig2 = crypto.signWithKeyPair(data2, keyPair);
    PQSignature sig3 = crypto.signWithKeyPair(data3, keyPair);

    // All should verify correctly
    assertThat(crypto.verify(data1, sig1, keyPair.getPublicKey())).isTrue();
    assertThat(crypto.verify(data2, sig2, keyPair.getPublicKey())).isTrue();
    assertThat(crypto.verify(data3, sig3, keyPair.getPublicKey())).isTrue();

    // Cross-verification should fail
    assertThat(crypto.verify(data1, sig2, keyPair.getPublicKey())).isFalse();
    assertThat(crypto.verify(data2, sig3, keyPair.getPublicKey())).isFalse();
  }
}
