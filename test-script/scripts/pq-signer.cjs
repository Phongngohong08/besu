const { execSync } = require('child_process');
const path = require('path');
const fs = require('fs');

// Path to PQ signer JAR
const PQ_SIGNER_JAR = path.join(__dirname, '../../pq-signer/build/libs/pq-signer-25.11-develop-e7ec115.jar');

/**
 * Generate a new DILITHIUM3 keypair
 * @param {string} outputDir - Directory to save keys
 * @returns {{privateKeyPath: string, publicKeyPath: string}}
 */
function generateKeypair(outputDir) {
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }

  console.log('🔑 Generating DILITHIUM3 keypair...');
  
  const result = execSync(`java -jar "${PQ_SIGNER_JAR}" generate-keypair "${outputDir}"`, {
    encoding: 'utf-8'
  });

  const lines = result.trim().split('\n');
  const privateKeyPath = lines[lines.length - 2];
  const publicKeyPath = lines[lines.length - 1];

  return { privateKeyPath, publicKeyPath };
}

/**
 * Sign a transaction hash with DILITHIUM3
 * @param {string} privateKeyPath - Path to private key file
 * @param {string} hash - Transaction hash (32 bytes hex, with or without 0x prefix)
 * @returns {string} Signature hex (with 0x02 type prefix)
 */
function sign(privateKeyPath, hash) {
  console.log('✍️  Signing with DILITHIUM3...');
  
  const result = execSync(`java -jar "${PQ_SIGNER_JAR}" sign "${privateKeyPath}" "${hash}"`, {
    encoding: 'utf-8'
  });

  const signatureHex = result.trim().split('\n').pop();
  return '0x' + signatureHex;
}

/**
 * Verify a DILITHIUM3 signature
 * @param {string} publicKeyPath - Path to public key file
 * @param {string} hash - Transaction hash (32 bytes hex)
 * @param {string} signature - Signature hex (with type prefix)
 * @returns {boolean} True if signature is valid
 */
function verify(publicKeyPath, hash, signature) {
  console.log('🔍 Verifying DILITHIUM3 signature...');
  
  const result = execSync(
    `java -jar "${PQ_SIGNER_JAR}" verify "${publicKeyPath}" "${hash}" "${signature}"`,
    { encoding: 'utf-8' }
  );

  const isValid = result.trim().split('\n').pop() === 'true';
  return isValid;
}

/**
 * Get public key as hex string
 * @param {string} publicKeyPath - Path to public key file
 * @returns {string} Public key hex (with 0x prefix)
 */
function getPublicKey(publicKeyPath) {
  const result = execSync(`java -jar "${PQ_SIGNER_JAR}" get-public-key "${publicKeyPath}"`, {
    encoding: 'utf-8'
  });

  const publicKeyHex = result.trim().split('\n').pop();
  return '0x' + publicKeyHex;
}

module.exports = {
  generateKeypair,
  sign,
  verify,
  getPublicKey
};
