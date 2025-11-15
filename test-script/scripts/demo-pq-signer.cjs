const ethers = require('ethers');
const pqSigner = require('./pq-signer.cjs');
const path = require('path');

/**
 * Demo: Generate DILITHIUM3 keypair and sign a transaction
 */
async function main() {
  console.log('🧪 PQ Signer Demo - DILITHIUM3\n');
  console.log('═'.repeat(60));

  // Step 1: Generate keypair
  const keysDir = path.join(__dirname, '../pq-keys');
  console.log('\n📁 Keys directory:', keysDir);
  
  const { privateKeyPath, publicKeyPath } = pqSigner.generateKeypair(keysDir);
  console.log('✅ Keypair generated!');
  console.log('   Private key:', privateKeyPath);
  console.log('   Public key:', publicKeyPath);

  // Step 2: Create a test transaction hash
  console.log('\n🔨 Creating test transaction hash...');
  const testData = ethers.toUtf8Bytes('Test transaction data for DILITHIUM3 signature');
  const txHash = ethers.keccak256(testData);
  console.log('✅ Test hash:', txHash);

  // Step 3: Sign the hash
  console.log('\n✍️  Signing hash...');
  const signature = pqSigner.sign(privateKeyPath, txHash);
  console.log('✅ Signature generated!');
  console.log('   Length:', (signature.length - 2) / 2, 'bytes');
  console.log('   Type byte:', signature.slice(0, 4), '(DILITHIUM3)');
  console.log('   First 100 chars:', signature.slice(0, 100) + '...');

  // Step 4: Get public key
  console.log('\n🔑 Getting public key...');
  const publicKey = pqSigner.getPublicKey(publicKeyPath);
  console.log('✅ Public key:', publicKey.slice(0, 100) + '...');
  console.log('   Length:', (publicKey.length - 2) / 2, 'bytes (expected 1952 for DILITHIUM3)');

  // Step 5: Verify signature
  console.log('\n🔍 Verifying signature...');
  const isValid = pqSigner.verify(publicKeyPath, txHash, signature);
  console.log(isValid ? '✅ Signature is VALID!' : '❌ Signature is INVALID!');

  // Step 6: Test with wrong hash
  console.log('\n🔍 Testing with wrong hash (should fail)...');
  const wrongHash = ethers.keccak256(ethers.toUtf8Bytes('Wrong data'));
  const isValidWrong = pqSigner.verify(publicKeyPath, wrongHash, signature);
  console.log(isValidWrong ? '❌ UNEXPECTED: Signature verified with wrong hash!' : '✅ Correctly rejected wrong hash');

  console.log('\n' + '═'.repeat(60));
  console.log('🎉 Demo completed successfully!');
  console.log('\n💡 Next steps:');
  console.log('   1. Use these keys to sign real transactions');
  console.log('   2. Run: node scripts/send-full-hybrid-tx.cjs');
  console.log('   3. Keys saved in:', keysDir);
}

main().catch(console.error);
