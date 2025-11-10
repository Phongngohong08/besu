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

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class PQSignatureTest {

  @Test
  public void testPQSignatureEncodeDecode() {
    // Create a test signature
    Bytes testSignatureBytes = Bytes.random(2420); // Dilithium2 signature size
    PQSignature original =
        new PQSignature(PQSignature.PQAlgorithmType.DILITHIUM2, testSignatureBytes);

    // Encode
    Bytes encoded = original.encode();

    // Decode
    PQSignature decoded = PQSignature.decode(encoded);

    // Verify
    assertThat(decoded.getAlgorithmType()).isEqualTo(PQSignature.PQAlgorithmType.DILITHIUM2);
    assertThat(decoded.getSignatureBytes()).isEqualTo(testSignatureBytes);
  }

  @Test
  public void testPQSignatureTypes() {
    // Test all algorithm types
    assertThat(PQSignature.PQAlgorithmType.DILITHIUM2.getTypeId()).isEqualTo(0x01);
    assertThat(PQSignature.PQAlgorithmType.DILITHIUM2.getSignatureSize()).isEqualTo(2420);

    assertThat(PQSignature.PQAlgorithmType.DILITHIUM3.getTypeId()).isEqualTo(0x02);
    assertThat(PQSignature.PQAlgorithmType.DILITHIUM3.getSignatureSize()).isEqualTo(3293);

    assertThat(PQSignature.PQAlgorithmType.DILITHIUM5.getTypeId()).isEqualTo(0x03);
    assertThat(PQSignature.PQAlgorithmType.DILITHIUM5.getSignatureSize()).isEqualTo(4595);

    assertThat(PQSignature.PQAlgorithmType.FALCON512.getTypeId()).isEqualTo(0x04);
    assertThat(PQSignature.PQAlgorithmType.FALCON512.getSignatureSize()).isEqualTo(690);

    assertThat(PQSignature.PQAlgorithmType.FALCON1024.getTypeId()).isEqualTo(0x05);
    assertThat(PQSignature.PQAlgorithmType.FALCON1024.getSignatureSize()).isEqualTo(1330);
  }

  @Test
  public void testFromTypeId() {
    assertThat(PQSignature.PQAlgorithmType.fromTypeId(0x01))
        .isEqualTo(PQSignature.PQAlgorithmType.DILITHIUM2);
    assertThat(PQSignature.PQAlgorithmType.fromTypeId(0x02))
        .isEqualTo(PQSignature.PQAlgorithmType.DILITHIUM3);
    assertThat(PQSignature.PQAlgorithmType.fromTypeId(0x03))
        .isEqualTo(PQSignature.PQAlgorithmType.DILITHIUM5);
  }
}
