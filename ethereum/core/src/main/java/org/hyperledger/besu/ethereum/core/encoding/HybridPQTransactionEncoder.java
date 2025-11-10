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

import static org.hyperledger.besu.ethereum.core.encoding.AccessListTransactionEncoder.writeAccessList;
import static org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder.writeSignatureAndRecoveryId;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import org.apache.tuweni.bytes.Bytes;

/**
 * Encoder for Hybrid Post-Quantum transactions (ECDSA + PQC signatures). Transaction format:
 * [chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data, accessList,
 * ecdsaSignature (v,r,s), pqSignature (optional)]
 */
public class HybridPQTransactionEncoder {

  public static void encode(final Transaction transaction, final RLPOutput out) {
    out.startList();
    // Chain ID (required for typed transactions)
    out.writeBigIntegerScalar(transaction.getChainId().orElseThrow());
    // Nonce
    out.writeLongScalar(transaction.getNonce());
    // EIP-1559 fee parameters
    out.writeUInt256Scalar(transaction.getMaxPriorityFeePerGas().orElseThrow());
    out.writeUInt256Scalar(transaction.getMaxFeePerGas().orElseThrow());
    // Gas limit
    out.writeLongScalar(transaction.getGasLimit());
    // To address (empty for contract creation)
    out.writeBytes(transaction.getTo().map(Bytes::copy).orElse(Bytes.EMPTY));
    // Value
    out.writeUInt256Scalar(transaction.getValue());
    // Payload/data
    out.writeBytes(transaction.getPayload());
    // Access list (EIP-2930)
    writeAccessList(out, transaction.getAccessList());
    // ECDSA signature (v, r, s)
    writeSignatureAndRecoveryId(transaction, out);

    // Post-Quantum signature (optional)
    // If present, encode as: [algorithmType (1 byte), publicKey, signature]
    if (transaction.getPQSignature().isPresent()) {
      out.writeBytes(transaction.getPQSignature().get().encode());
      // Also write the PQ public key for verification
      if (transaction.getPQPublicKey().isPresent()) {
        out.writeBytes(transaction.getPQPublicKey().get());
      } else {
        out.writeBytes(Bytes.EMPTY);
      }
    } else {
      // No PQ signature - write empty bytes
      out.writeBytes(Bytes.EMPTY);
      out.writeBytes(Bytes.EMPTY);
    }

    out.endList();
  }
}
