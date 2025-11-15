/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.pqsigner;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumKeyGenerationParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumKeyPairGenerator;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumPublicKeyParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumSigner;

/**
 * Post-Quantum Signature Tool using DILITHIUM3
 *
 * <p>Commands: generate-keypair <output-dir> - Generate new DILITHIUM3 keypair sign
 * <private-key-file> <hash-hex> - Sign a transaction hash verify <public-key-file> <hash-hex>
 * <signature-hex> - Verify signature
 */
public class PQSigner {

  private static final DilithiumParameters DILITHIUM3 = DilithiumParameters.dilithium3;
  private static final byte DILITHIUM3_TYPE = 0x02;

  @SuppressWarnings("DoNotCreateSecureRandomDirectly")
  private static SecureRandom createSecureRandom() {
    try {
      return SecureRandom.getInstanceStrong();
    } catch (Exception e) {
      return new SecureRandom();
    }
  }

  public static void main(final String[] args) {
    if (args.length == 0) {
      printUsage();
      System.exit(1);
    }

    try {
      String command = args[0];
      switch (command) {
        case "generate-keypair":
          if (args.length != 2) {
            System.err.println("Usage: generate-keypair <output-dir>");
            System.exit(1);
          }
          generateKeypair(args[1]);
          break;

        case "sign":
          if (args.length != 3) {
            System.err.println("Usage: sign <private-key-file> <hash-hex>");
            System.exit(1);
          }
          sign(args[1], args[2]);
          break;

        case "verify":
          if (args.length != 4) {
            System.err.println("Usage: verify <public-key-file> <hash-hex> <signature-hex>");
            System.exit(1);
          }
          verify(args[1], args[2], args[3]);
          break;

        case "get-public-key":
          if (args.length != 2) {
            System.err.println("Usage: get-public-key <public-key-file>");
            System.exit(1);
          }
          getPublicKey(args[1]);
          break;

        default:
          System.err.println("Unknown command: " + command);
          printUsage();
          System.exit(1);
      }
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void printUsage() {
    System.err.println("Post-Quantum Signer (DILITHIUM3)");
    System.err.println();
    System.err.println("Commands:");
    System.err.println("  generate-keypair <output-dir>");
    System.err.println("    Generate new DILITHIUM3 keypair and save to files");
    System.err.println();
    System.err.println("  sign <private-key-file> <hash-hex>");
    System.err.println("    Sign a transaction hash (32 bytes hex)");
    System.err.println("    Outputs: signature in hex format with type prefix");
    System.err.println();
    System.err.println("  verify <public-key-file> <hash-hex> <signature-hex>");
    System.err.println("    Verify a signature");
    System.err.println();
    System.err.println("  get-public-key <public-key-file>");
    System.err.println("    Get public key in hex format");
  }

  private static void generateKeypair(final String outputDir) throws IOException {
    System.err.println("Generating DILITHIUM3 keypair...");

    // Create key pair generator
    DilithiumKeyPairGenerator keyPairGenerator = new DilithiumKeyPairGenerator();
    keyPairGenerator.init(new DilithiumKeyGenerationParameters(createSecureRandom(), DILITHIUM3));

    // Generate keypair
    AsymmetricCipherKeyPair keyPair = keyPairGenerator.generateKeyPair();
    DilithiumPrivateKeyParameters privateKey = (DilithiumPrivateKeyParameters) keyPair.getPrivate();
    DilithiumPublicKeyParameters publicKey = (DilithiumPublicKeyParameters) keyPair.getPublic();

    // Encode public key (rho + t1)
    byte[] rho = publicKey.getRho();
    byte[] t1 = publicKey.getT1();
    byte[] publicKeyEncoded = new byte[rho.length + t1.length];
    System.arraycopy(rho, 0, publicKeyEncoded, 0, rho.length);
    System.arraycopy(t1, 0, publicKeyEncoded, rho.length, t1.length);

    // Use BouncyCastle's built-in encoding (4032 bytes for DILITHIUM3 private, 1952 for public)
    byte[] privateKeyEncoded = privateKey.getEncoded();
    byte[] publicKeyEncodedFull = publicKey.getEncoded();

    // Save private key
    String privateKeyPath = outputDir + "/dilithium3-private.key";
    try (FileOutputStream fos = new FileOutputStream(privateKeyPath)) {
      fos.write(privateKeyEncoded);
    }

    // Save companion public key (needed for private key reconstruction)
    String privatePublicKeyPath = outputDir + "/dilithium3-private.key.pubkey";
    try (FileOutputStream fos = new FileOutputStream(privatePublicKeyPath)) {
      fos.write(publicKeyEncodedFull);
    }

    // Save public key
    String publicKeyPath = outputDir + "/dilithium3-public.key";
    try (FileOutputStream fos = new FileOutputStream(publicKeyPath)) {
      fos.write(publicKeyEncoded);
    }

    System.err.println("✅ Keypair generated:");
    System.err.println(
        "  Private key: " + privateKeyPath + " (" + privateKeyEncoded.length + " bytes)");
    System.err.println(
        "  Companion public key: "
            + privatePublicKeyPath
            + " ("
            + publicKeyEncodedFull.length
            + " bytes)");
    System.err.println(
        "  Public key: " + publicKeyPath + " (" + publicKeyEncoded.length + " bytes)");

    // Output for programmatic use
    System.out.println(privateKeyPath);
    System.out.println(publicKeyPath);
  }

  private static void sign(final String privateKeyFile, final String hashHex) throws IOException {
    System.err.println("Signing with DILITHIUM3...");

    // Remove 0x prefix if present
    String hashInput = hashHex;
    if (hashInput.startsWith("0x")) {
      hashInput = hashInput.substring(2);
    }

    // Parse hash
    byte[] hash = hexToBytes(hashInput);
    if (hash.length != 32) {
      throw new IllegalArgumentException("Hash must be 32 bytes, got " + hash.length);
    }

    // Load private key (BouncyCastle's getEncoded() format - 4032 bytes for DILITHIUM3)
    byte[] privateKeyBytes = Files.readAllBytes(Paths.get(privateKeyFile));
    if (privateKeyBytes.length != 4032) {
      throw new IllegalArgumentException(
          "Invalid DILITHIUM3 private key size: "
              + privateKeyBytes.length
              + ", expected 4032");
    }

    // Load companion public key (needed for BouncyCastle's private key reconstruction)
    String companionPublicKeyFile = privateKeyFile + ".pubkey";
    byte[] publicKeyBytes = Files.readAllBytes(Paths.get(companionPublicKeyFile));
    if (publicKeyBytes.length != 1952) {
      throw new IllegalArgumentException(
          "Invalid DILITHIUM3 public key size: "
              + publicKeyBytes.length
              + ", expected 1952");
    }

    // Reconstruct public key from encoded bytes
    byte[] rho = new byte[32];
    byte[] t1 = new byte[publicKeyBytes.length - 32];
    System.arraycopy(publicKeyBytes, 0, rho, 0, 32);
    System.arraycopy(publicKeyBytes, 32, t1, 0, t1.length);
    DilithiumPublicKeyParameters publicKey =
        new DilithiumPublicKeyParameters(DILITHIUM3, rho, t1);

    // Reconstruct private key using BouncyCastle's constructor (params, encoded bytes, public key)
    DilithiumPrivateKeyParameters privateKey =
        new DilithiumPrivateKeyParameters(DILITHIUM3, privateKeyBytes, publicKey);

    // Sign
    DilithiumSigner signer = new DilithiumSigner();
    signer.init(true, privateKey);
    byte[] signatureBytes = signer.generateSignature(hash);

    System.err.println("✅ Signature generated: " + signatureBytes.length + " bytes");

    // Create signature with type prefix
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(DILITHIUM3_TYPE); // Algorithm type
    out.write(signatureBytes); // Signature bytes

    byte[] fullSignature = out.toByteArray();

    // Output signature in hex
    System.out.println(bytesToHex(fullSignature));
  }

  private static void verify(
      final String publicKeyFile, final String hashHex, final String signatureHex)
      throws IOException {
    System.err.println("Verifying DILITHIUM3 signature...");

    // Remove 0x prefix if present
    String hashInput = hashHex;
    String sigInput = signatureHex;
    if (hashInput.startsWith("0x")) {
      hashInput = hashInput.substring(2);
    }
    if (sigInput.startsWith("0x")) {
      sigInput = sigInput.substring(2);
    }

    // Parse inputs
    byte[] hash = hexToBytes(hashInput);
    byte[] fullSignature = hexToBytes(sigInput);

    if (hash.length != 32) {
      throw new IllegalArgumentException("Hash must be 32 bytes");
    }

    // Extract type and signature
    if (fullSignature.length < 2) {
      throw new IllegalArgumentException("Signature too short");
    }

    byte sigType = fullSignature[0];
    if (sigType != DILITHIUM3_TYPE) {
      throw new IllegalArgumentException(
          "Invalid signature type: " + sigType + ", expected " + DILITHIUM3_TYPE);
    }

    byte[] signatureBytes = new byte[fullSignature.length - 1];
    System.arraycopy(fullSignature, 1, signatureBytes, 0, signatureBytes.length);

    // Load public key (rho + t1)
    byte[] publicKeyBytes = Files.readAllBytes(Paths.get(publicKeyFile));
    byte[] rho = new byte[32];
    byte[] t1 = new byte[publicKeyBytes.length - 32];
    System.arraycopy(publicKeyBytes, 0, rho, 0, 32);
    System.arraycopy(publicKeyBytes, 32, t1, 0, t1.length);

    DilithiumPublicKeyParameters publicKey = new DilithiumPublicKeyParameters(DILITHIUM3, rho, t1);

    // Verify
    DilithiumSigner signer = new DilithiumSigner();
    signer.init(false, publicKey);
    boolean valid = signer.verifySignature(hash, signatureBytes);

    if (valid) {
      System.err.println("✅ Signature is VALID");
      System.out.println("true");
    } else {
      System.err.println("❌ Signature is INVALID");
      System.out.println("false");
    }
  }

  private static void getPublicKey(final String publicKeyFile) throws IOException {
    byte[] publicKeyBytes = Files.readAllBytes(Paths.get(publicKeyFile));
    System.out.println(bytesToHex(publicKeyBytes));
  }

  private static byte[] hexToBytes(final String hex) {
    int len = hex.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] =
          (byte)
              ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
    }
    return data;
  }

  private static String bytesToHex(final byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
