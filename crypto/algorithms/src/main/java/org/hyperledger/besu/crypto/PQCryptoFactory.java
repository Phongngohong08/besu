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

import java.util.EnumMap;
import java.util.Map;

/**
 * Factory for creating PostQuantumCrypto instances based on algorithm type.
 *
 * <p>This factory provides a centralized way to create and cache PQ crypto implementations,
 * ensuring that only one instance of each algorithm type exists (singleton pattern per algorithm).
 */
public class PQCryptoFactory {

  private static final Map<PQSignature.PQAlgorithmType, PostQuantumCrypto> INSTANCES =
      new EnumMap<>(PQSignature.PQAlgorithmType.class);

  static {
    // Pre-initialize instances for all supported algorithms
    INSTANCES.put(PQSignature.PQAlgorithmType.DILITHIUM2, new DilithiumCrypto(PQSignature.PQAlgorithmType.DILITHIUM2));
    INSTANCES.put(PQSignature.PQAlgorithmType.DILITHIUM3, new DilithiumCrypto(PQSignature.PQAlgorithmType.DILITHIUM3));
    INSTANCES.put(PQSignature.PQAlgorithmType.DILITHIUM5, new DilithiumCrypto(PQSignature.PQAlgorithmType.DILITHIUM5));
    INSTANCES.put(PQSignature.PQAlgorithmType.FALCON512, new FalconCrypto(PQSignature.PQAlgorithmType.FALCON512));
    INSTANCES.put(PQSignature.PQAlgorithmType.FALCON1024, new FalconCrypto(PQSignature.PQAlgorithmType.FALCON1024));
  }

  /**
   * Private constructor to prevent instantiation.
   */
  private PQCryptoFactory() {
    // Utility class
  }

  /**
   * Get a PostQuantumCrypto instance for the specified algorithm type.
   *
   * @param algorithmType the PQ algorithm type
   * @return the corresponding PostQuantumCrypto instance
   * @throws IllegalArgumentException if the algorithm type is not supported
   */
  public static PostQuantumCrypto getInstance(final PQSignature.PQAlgorithmType algorithmType) {
    PostQuantumCrypto instance = INSTANCES.get(algorithmType);
    if (instance == null) {
      throw new IllegalArgumentException("Unsupported PQ algorithm type: " + algorithmType);
    }
    return instance;
  }

  /**
   * Check if an algorithm type is supported.
   *
   * @param algorithmType the algorithm type to check
   * @return true if supported, false otherwise
   */
  public static boolean isSupported(final PQSignature.PQAlgorithmType algorithmType) {
    return INSTANCES.containsKey(algorithmType);
  }

  /**
   * Get all supported algorithm types.
   *
   * @return array of supported algorithm types
   */
  public static PQSignature.PQAlgorithmType[] getSupportedAlgorithms() {
    return INSTANCES.keySet().toArray(new PQSignature.PQAlgorithmType[0]);
  }
}
