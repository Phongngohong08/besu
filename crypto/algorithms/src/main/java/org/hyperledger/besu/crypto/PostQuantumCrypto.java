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

import org.apache.tuweni.bytes.Bytes;

/**
 * Interface for Post-Quantum cryptographic algorithms. This provides verification capabilities for
 * post-quantum signatures.
 */
public interface PostQuantumCrypto {

  /**
   * Verify a post-quantum signature
   *
   * @param data the data that was signed
   * @param signature the post-quantum signature
   * @param publicKey the public key bytes
   * @return true if the signature is valid, false otherwise
   */
  boolean verify(final Bytes data, final PQSignature signature, final Bytes publicKey);

  /**
   * Get the algorithm type supported by this implementation
   *
   * @return the PQ algorithm type
   */
  PQSignature.PQAlgorithmType getAlgorithmType();

  /**
   * Get the public key size in bytes
   *
   * @return the public key size
   */
  int getPublicKeySize();

  /**
   * Sign data with a post-quantum private key
   *
   * @param data the data to sign
   * @param privateKey the private key bytes
   * @return the post-quantum signature
   */
  PQSignature sign(final Bytes data, final Bytes privateKey);
}
