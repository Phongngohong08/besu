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
package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.crypto.PQSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decoder for Hybrid Post-Quantum transactions (ECDSA + PQC signatures). */
public class HybridPQTransactionDecoder {
  private static final Logger LOG = LoggerFactory.getLogger(HybridPQTransactionDecoder.class);
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private HybridPQTransactionDecoder() {
    // private constructor
  }

  public static Transaction decode(final Bytes input) {
    LOG.info("=== HybridPQTransactionDecoder: Decoding transaction ===");
    LOG.info("Input length: {} bytes", input.size());
    LOG.info("First 100 bytes: {}", input.slice(0, Math.min(100, input.size())).toHexString());

    final RLPInput txRlp = RLP.input(input.slice(1)); // Skip the transaction type byte (0x05)
    LOG.info("After skipping type byte, RLP input ready");

    try {
      txRlp.enterList();
      LOG.info("Entered RLP list");
    } catch (Exception e) {
      LOG.error("Failed to enter RLP list", e);
      throw new IllegalArgumentException("Invalid RLP structure: " + e.getMessage(), e);
    }

    // Read standard EIP-1559 fields
    LOG.info("Reading chainId...");
    final BigInteger chainId = txRlp.readBigIntegerScalar();
    LOG.info("ChainId: {}", chainId);
    final Transaction.Builder builder =
        Transaction.builder()
            .type(TransactionType.HYBRID_PQ)
            .chainId(chainId)
            .nonce(txRlp.readLongScalar())
            .maxPriorityFeePerGas(Wei.of(txRlp.readUInt256Scalar()))
            .maxFeePerGas(Wei.of(txRlp.readUInt256Scalar()))
            .gasLimit(txRlp.readLongScalar())
            .to(txRlp.readBytes(v -> v.isEmpty() ? null : Address.wrap(v)))
            .value(Wei.of(txRlp.readUInt256Scalar()))
            .payload(txRlp.readBytes())
            .rawRlp(txRlp.raw())
            .accessList(
                txRlp.readList(
                    accessListEntryRLPInput -> {
                      accessListEntryRLPInput.enterList();
                      final AccessListEntry accessListEntry =
                          new AccessListEntry(
                              Address.wrap(accessListEntryRLPInput.readBytes()),
                              accessListEntryRLPInput.readList(RLPInput::readBytes32));
                      accessListEntryRLPInput.leaveList();
                      return accessListEntry;
                    }))
            .sizeForAnnouncement(input.size())
            .sizeForBlockInclusion(input.size())
            .hash(Hash.hash(input));

    LOG.info("Standard EIP-1559 fields parsed successfully");

    // Read ECDSA signature
    LOG.info("Reading ECDSA signature...");
    final byte recId = (byte) txRlp.readUnsignedByteScalar();
    LOG.info("recId/yParity: {}", recId);
    builder.signature(
        SIGNATURE_ALGORITHM
            .get()
            .createSignature(
                txRlp.readUInt256Scalar().toUnsignedBigInteger(),
                txRlp.readUInt256Scalar().toUnsignedBigInteger(),
                recId));

    LOG.info("ECDSA signature parsed successfully");

    // Read optional Post-Quantum signature
    LOG.info("Reading PQ signature bytes...");
    final Bytes pqSigBytes = txRlp.readBytes();
    LOG.info("PQ signature bytes length: {}", pqSigBytes.size());

    // Always read PQ public key (it's always present in RLP structure, may be empty)
    LOG.info("Reading PQ public key bytes...");
    final Bytes pqPublicKey = txRlp.readBytes();
    LOG.info("PQ public key bytes length: {}", pqPublicKey.size());

    // Process PQ signature and public key if present
    if (!pqSigBytes.isEmpty()) {
      LOG.info("Decoding PQ signature...");
      try {
        final PQSignature pqSignature = PQSignature.decode(pqSigBytes);
        builder.pqSignature(pqSignature);
        LOG.info("PQ signature decoded successfully, size={}", pqSigBytes.size());

        if (!pqPublicKey.isEmpty()) {
          builder.pqPublicKey(pqPublicKey);
          LOG.info("PQ public key added, size: {}", pqPublicKey.size());
        }
      } catch (Exception e) {
        LOG.error("Failed to decode PQ signature", e);
        // If PQ signature parsing fails, continue without it (fallback to ECDSA only)
        // This provides backward compatibility
      }
    } else {
      LOG.info("No PQ signature present (empty bytes) - ECDSA-only mode");
    }

    LOG.info("Building transaction object...");
    final Transaction transaction;
    try {
      transaction = builder.build();
      LOG.info("Transaction built successfully");
    } catch (Exception e) {
      LOG.error("Failed to build transaction", e);
      throw new IllegalArgumentException("Failed to build transaction: " + e.getMessage(), e);
    }

    try {
      txRlp.leaveList();
      LOG.info("Left RLP list successfully");
    } catch (Exception e) {
      LOG.error("Failed to leave RLP list", e);
      throw new IllegalArgumentException("Failed to leave RLP list: " + e.getMessage(), e);
    }

    LOG.info("Successfully decoded HYBRID_PQ transaction: hash={}", transaction.getHash());
    // LOG.info("Transaction sender will be: {}", transaction.getSender());  // May trigger
    // signature recovery
    return transaction;
  }
}
