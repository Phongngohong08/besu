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

import org.junit.jupiter.api.Test;

public class PQCryptoFactoryTest {

  @Test
  public void testGetDilithiumInstances() {
    PostQuantumCrypto crypto2 =
        PQCryptoFactory.getInstance(PQSignature.PQAlgorithmType.DILITHIUM2);
    assertThat(crypto2).isInstanceOf(DilithiumCrypto.class);
    assertThat(crypto2.getAlgorithmType()).isEqualTo(PQSignature.PQAlgorithmType.DILITHIUM2);

    PostQuantumCrypto crypto3 =
        PQCryptoFactory.getInstance(PQSignature.PQAlgorithmType.DILITHIUM3);
    assertThat(crypto3).isInstanceOf(DilithiumCrypto.class);
    assertThat(crypto3.getAlgorithmType()).isEqualTo(PQSignature.PQAlgorithmType.DILITHIUM3);

    PostQuantumCrypto crypto5 =
        PQCryptoFactory.getInstance(PQSignature.PQAlgorithmType.DILITHIUM5);
    assertThat(crypto5).isInstanceOf(DilithiumCrypto.class);
    assertThat(crypto5.getAlgorithmType()).isEqualTo(PQSignature.PQAlgorithmType.DILITHIUM5);
  }

  @Test
  public void testGetFalconInstances() {
    PostQuantumCrypto crypto512 =
        PQCryptoFactory.getInstance(PQSignature.PQAlgorithmType.FALCON512);
    assertThat(crypto512).isInstanceOf(FalconCrypto.class);
    assertThat(crypto512.getAlgorithmType()).isEqualTo(PQSignature.PQAlgorithmType.FALCON512);

    PostQuantumCrypto crypto1024 =
        PQCryptoFactory.getInstance(PQSignature.PQAlgorithmType.FALCON1024);
    assertThat(crypto1024).isInstanceOf(FalconCrypto.class);
    assertThat(crypto1024.getAlgorithmType()).isEqualTo(PQSignature.PQAlgorithmType.FALCON1024);
  }

  @Test
  public void testSingletonBehavior() {
    // Same algorithm type should return same instance
    PostQuantumCrypto instance1 =
        PQCryptoFactory.getInstance(PQSignature.PQAlgorithmType.DILITHIUM2);
    PostQuantumCrypto instance2 =
        PQCryptoFactory.getInstance(PQSignature.PQAlgorithmType.DILITHIUM2);

    assertThat(instance1).isSameAs(instance2);
  }

  @Test
  public void testIsSupported() {
    assertThat(PQCryptoFactory.isSupported(PQSignature.PQAlgorithmType.DILITHIUM2)).isTrue();
    assertThat(PQCryptoFactory.isSupported(PQSignature.PQAlgorithmType.DILITHIUM3)).isTrue();
    assertThat(PQCryptoFactory.isSupported(PQSignature.PQAlgorithmType.DILITHIUM5)).isTrue();
    assertThat(PQCryptoFactory.isSupported(PQSignature.PQAlgorithmType.FALCON512)).isTrue();
    assertThat(PQCryptoFactory.isSupported(PQSignature.PQAlgorithmType.FALCON1024)).isTrue();
  }

  @Test
  public void testGetSupportedAlgorithms() {
    PQSignature.PQAlgorithmType[] supported = PQCryptoFactory.getSupportedAlgorithms();

    assertThat(supported).hasSize(5);
    assertThat(supported).contains(PQSignature.PQAlgorithmType.DILITHIUM2);
    assertThat(supported).contains(PQSignature.PQAlgorithmType.DILITHIUM3);
    assertThat(supported).contains(PQSignature.PQAlgorithmType.DILITHIUM5);
    assertThat(supported).contains(PQSignature.PQAlgorithmType.FALCON512);
    assertThat(supported).contains(PQSignature.PQAlgorithmType.FALCON1024);
  }
}
