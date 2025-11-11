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
package org.hyperledger.besu.crypto.tools;

import org.hyperledger.besu.crypto.DilithiumCrypto;
import org.hyperledger.besu.crypto.FalconCrypto;
import org.hyperledger.besu.crypto.PQSignature.PQAlgorithmType;
import org.hyperledger.besu.crypto.SecureRandomProvider;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Locale;

import com.google.common.base.Ascii;
import org.apache.tuweni.bytes.Bytes;

/**
 * Command-line tool to generate Post-Quantum keypairs for Besu.
 *
 * <p>Usage: java -cp ... PQKeyGenerator [algorithm] [output-dir]
 *
 * <p>Algorithms: DILITHIUM2, DILITHIUM3, DILITHIUM5, FALCON512, FALCON1024
 *
 * <p>Output: - [output-dir]/pq-public.key - [output-dir]/pq-private.key (WARNING: Store securely!)
 */
public class PQKeyGenerator {

  public static void main(final String[] args) {
    if (args.length < 1) {
      printUsage();
      System.exit(1);
    }

    final String algorithmName = Ascii.toUpperCase(args[0]);
    final String outputDir = args.length > 1 ? args[1] : ".";

    try {
      final PQAlgorithmType algorithmType = PQAlgorithmType.valueOf(algorithmName);
      generateAndSaveKeyPair(algorithmType, outputDir);
      System.out.println("✅ Successfully generated " + algorithmName + " keypair in: " + outputDir);
      System.out.println("⚠️  WARNING: Keep pq-private-params.txt SECURE! Anyone with this file can sign transactions!");
    } catch (IllegalArgumentException e) {
      System.err.println("❌ Invalid algorithm: " + algorithmName);
      printUsage();
      System.exit(1);
    } catch (IOException e) {
      System.err.println("❌ Failed to write key files: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void generateAndSaveKeyPair(
      final PQAlgorithmType algorithmType, final String outputDir) throws IOException {
    final SecureRandom random = SecureRandomProvider.createSecureRandom();
    final Bytes publicKey;
    final String privateKeyInfo;

    switch (algorithmType) {
      case DILITHIUM2:
      case DILITHIUM3:
      case DILITHIUM5:
        {
          final DilithiumCrypto crypto = new DilithiumCrypto(algorithmType);
          final DilithiumCrypto.KeyPairBytes keyPair = crypto.generateKeyPair(random);
          publicKey = keyPair.getPublicKey();
          privateKeyInfo =
              "⚠️ DILITHIUM PRIVATE KEY PARAMETERS\n"
                  + "This file contains private key parameters in BouncyCastle format.\n"
                  + "Cannot be exported as raw bytes - must use KeyPairBytes object.\n\n"
                  + "Algorithm: "
                  + algorithmType
                  + "\n"
                  + "Public Key: "
                  + publicKey.toHexString()
                  + "\n\n"
                  + "To use this key for signing:\n"
                  + "1. Keep the KeyPairBytes object in memory, OR\n"
                  + "2. Use Java KeyStore to serialize/deserialize, OR\n"
                  + "3. Implement ASN.1 DER encoding for key storage\n\n"
                  + "See HYBRID_PQ_SIGNATURES.md for details.";
          break;
        }
      case FALCON512:
      case FALCON1024:
        {
          final FalconCrypto crypto = new FalconCrypto(algorithmType);
          final FalconCrypto.KeyPairBytes keyPair = crypto.generateKeyPair(random);
          publicKey = keyPair.getPublicKey();
          privateKeyInfo =
              "⚠️ FALCON PRIVATE KEY PARAMETERS\n"
                  + "This file contains private key parameters in BouncyCastle format.\n"
                  + "Cannot be exported as raw bytes - must use KeyPairBytes object.\n\n"
                  + "Algorithm: "
                  + algorithmType
                  + "\n"
                  + "Public Key: "
                  + publicKey.toHexString()
                  + "\n\n"
                  + "To use this key for signing:\n"
                  + "1. Keep the KeyPairBytes object in memory, OR\n"
                  + "2. Use Java KeyStore to serialize/deserialize, OR\n"
                  + "3. Implement ASN.1 DER encoding for key storage\n\n"
                  + "See HYBRID_PQ_SIGNATURES.md for details.";
          break;
        }
      default:
        throw new IllegalArgumentException("Unsupported algorithm: " + algorithmType);
    }

    // Create output directory if needed
    final File dir = new File(outputDir);
    if (!dir.exists()) {
      dir.mkdirs();
    }

    // Write public key (safe to share)
    final File publicKeyFile = new File(dir, "pq-public.key");
    try (Writer writer = Files.newBufferedWriter(publicKeyFile.toPath(), StandardCharsets.UTF_8)) {
      writer.write("# Post-Quantum Public Key\n");
      writer.write("# Algorithm: " + algorithmType + "\n");
      writer.write("# Size: " + publicKey.size() + " bytes\n");
      writer.write("# Format: Hex-encoded\n\n");
      writer.write(publicKey.toHexString());
      writer.write("\n");
    }

    // Write private key info (NOT the actual private key - see note above)
    final File privateKeyInfoFile = new File(dir, "pq-private-params.txt");
    try (Writer writer = Files.newBufferedWriter(privateKeyInfoFile.toPath(), StandardCharsets.UTF_8)) {
      writer.write(privateKeyInfo);
    }

    // Set restrictive permissions on private key file (Unix only)
    if (!Ascii.toLowerCase(System.getProperty("os.name")).contains("win")) {
      privateKeyInfoFile.setReadable(false, false);
      privateKeyInfoFile.setReadable(true, true);
      privateKeyInfoFile.setWritable(false, false);
      privateKeyInfoFile.setWritable(true, true);
    }

    System.out.println("\n📄 Files created:");
    System.out.println("   Public key:  " + publicKeyFile.getAbsolutePath());
    System.out.println("   Private info: " + privateKeyInfoFile.getAbsolutePath());
    System.out.println("\n🔑 Key Details:");
    System.out.println("   Algorithm:    " + algorithmType);
    System.out.println("   Public key size: " + publicKey.size() + " bytes");
    System.out.println("   Public key (hex): " + publicKey.toHexString().substring(0, Math.min(64, publicKey.size() * 2)) + "...");
  }

  private static void printUsage() {
    System.out.println("PQ KeyPair Generator for Besu");
    System.out.println();
    System.out.println("Usage: java -cp besu.jar org.hyperledger.besu.crypto.tools.PQKeyGenerator <algorithm> [output-dir]");
    System.out.println();
    System.out.println("Algorithms:");
    System.out.println("  DILITHIUM2  - Fast, 128-bit security (public: 1312 bytes, sig: 2420 bytes)");
    System.out.println("  DILITHIUM3  - Balanced, 192-bit security (public: 1952 bytes, sig: 3309 bytes) [RECOMMENDED]");
    System.out.println("  DILITHIUM5  - High security, 256-bit (public: 2592 bytes, sig: 4627 bytes)");
    System.out.println("  FALCON512   - Compact signatures, 128-bit (public: 896 bytes, sig: 690 bytes)");
    System.out.println("  FALCON1024  - Compact, high security, 256-bit (public: 1792 bytes, sig: 1330 bytes)");
    System.out.println();
    System.out.println("Examples:");
    System.out.println("  java -cp besu.jar org.hyperledger.besu.crypto.tools.PQKeyGenerator DILITHIUM3");
    System.out.println("  java -cp besu.jar org.hyperledger.besu.crypto.tools.PQKeyGenerator FALCON512 ./keys");
  }
}
