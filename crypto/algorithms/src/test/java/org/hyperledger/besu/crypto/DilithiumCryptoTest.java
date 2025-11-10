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

public class DilithiumCryptoTest {

  @Test
  public void testGetAlgorithmType() {
    DilithiumCrypto crypto = new DilithiumCrypto(PQSignature.PQAlgorithmType.DILITHIUM2);
    assertThat(crypto.getAlgorithmType()).isEqualTo(PQSignature.PQAlgorithmType.DILITHIUM2);
  }

  @Test
  public void testGetPublicKeySize() {
    DilithiumCrypto crypto2 = new DilithiumCrypto(PQSignature.PQAlgorithmType.DILITHIUM2);
    assertThat(crypto2.getPublicKeySize()).isEqualTo(1312);

    DilithiumCrypto crypto3 = new DilithiumCrypto(PQSignature.PQAlgorithmType.DILITHIUM3);
    assertThat(crypto3.getPublicKeySize()).isEqualTo(1952);

    DilithiumCrypto crypto5 = new DilithiumCrypto(PQSignature.PQAlgorithmType.DILITHIUM5);
    assertThat(crypto5.getPublicKeySize()).isEqualTo(2592);
  }
}
