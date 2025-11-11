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

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;

/**
 * Post-Quantum Signature representation (e.g., Dilithium, Falcon, etc.) This class holds the raw
 * bytes of a post-quantum signature.
 */
public class PQSignature {

  private final Bytes signatureBytes;
  private final PQAlgorithmType algorithmType;

  /** Post-Quantum algorithm types supported */
  public enum PQAlgorithmType {
    /** Dilithium2 algorithm (128-bit security, 2420 byte signature). */
    DILITHIUM2(0x01, 2420),
    /** Dilithium3 algorithm (192-bit security, 3309 byte signature). */
    DILITHIUM3(0x02, 3309),
    /** Dilithium5 algorithm (256-bit security, 4627 byte signature). */
    DILITHIUM5(0x03, 4627),
    /** Falcon-512 algorithm (128-bit security, 690 byte signature). */
    FALCON512(0x04, 690),
    /** Falcon-1024 algorithm (256-bit security, 1330 byte signature). */
    FALCON1024(0x05, 1330);

    private final int typeId;
    private final int signatureSize;

    PQAlgorithmType(final int typeId, final int signatureSize) {
      this.typeId = typeId;
      this.signatureSize = signatureSize;
    }

    /**
     * Get the algorithm type identifier.
     *
     * @return the type ID
     */
    public int getTypeId() {
      return typeId;
    }

    /**
     * Get the expected signature size in bytes.
     *
     * @return the signature size
     */
    public int getSignatureSize() {
      return signatureSize;
    }

    /**
     * Get algorithm type from type ID.
     *
     * @param typeId the type identifier
     * @return the corresponding algorithm type
     * @throws IllegalArgumentException if type ID is unknown
     */
    public static PQAlgorithmType fromTypeId(final int typeId) {
      for (PQAlgorithmType type : values()) {
        if (type.typeId == typeId) {
          return type;
        }
      }
      throw new IllegalArgumentException("Unknown PQ algorithm type: " + typeId);
    }
  }

  /**
   * Create a new PQSignature
   *
   * @param algorithmType the post-quantum algorithm type
   * @param signatureBytes the signature bytes
   */
  public PQSignature(final PQAlgorithmType algorithmType, final Bytes signatureBytes) {
    this.algorithmType = algorithmType;
    this.signatureBytes = signatureBytes;
  }

  /**
   * Create a PQSignature from encoded bytes Format: [1 byte algorithm type][signature bytes]
   *
   * @param encoded the encoded signature
   * @return the PQSignature
   */
  public static PQSignature decode(final Bytes encoded) {
    if (encoded.size() < 2) {
      throw new IllegalArgumentException("Invalid PQ signature encoding");
    }

    int typeId = encoded.get(0) & 0xFF;
    PQAlgorithmType algorithmType = PQAlgorithmType.fromTypeId(typeId);
    Bytes signatureBytes = encoded.slice(1);

    return new PQSignature(algorithmType, signatureBytes);
  }

  /**
   * Encode the signature to bytes Format: [1 byte algorithm type][signature bytes]
   *
   * @return the encoded signature
   */
  public Bytes encode() {
    return Bytes.concatenate(Bytes.of((byte) algorithmType.getTypeId()), signatureBytes);
  }

  /**
   * Get the signature bytes.
   *
   * @return the signature bytes
   */
  public Bytes getSignatureBytes() {
    return signatureBytes;
  }

  /**
   * Get the algorithm type.
   *
   * @return the algorithm type
   */
  public PQAlgorithmType getAlgorithmType() {
    return algorithmType;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PQSignature that = (PQSignature) o;
    return algorithmType == that.algorithmType
        && Objects.equals(signatureBytes, that.signatureBytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(signatureBytes, algorithmType);
  }

  @Override
  public String toString() {
    return "PQSignature{"
        + "algorithmType="
        + algorithmType
        + ", signatureBytes="
        + signatureBytes.toHexString()
        + '}';
  }
}
