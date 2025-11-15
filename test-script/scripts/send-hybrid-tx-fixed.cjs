const { ethers } = require('ethers');
const fs = require('fs');
const path = require('path');
const { sign, getPublicKey, verify } = require('./pq-signer.cjs');

// RLP encoding helpers
function encodeLength(len, offset) {
  if (len < 56) {
    return Buffer.from([offset + len]);
  }
  // Convert length to hex and ensure proper padding
  const hexLen = len.toString(16);
  const paddedHexLen = hexLen.length % 2 ? '0' + hexLen : hexLen;
  const lLen = paddedHexLen.length / 2; // Number of bytes needed to represent length
  const firstByte = offset + 55 + lLen;
  return Buffer.concat([
    Buffer.from([firstByte]),
    Buffer.from(paddedHexLen, 'hex')
  ]);
}

function encodeBytes(input) {
  if (input.length === 0) {
    return Buffer.from([0x80]);
  }
  if (input.length === 1 && input[0] < 0x80) {
    return input;
  }
  return Buffer.concat([encodeLength(input.length, 0x80), input]);
}

// Encode scalar (removes leading zeros)
function encodeScalar(value) {
  if (typeof value === 'number') {
    if (value === 0) return Buffer.from([0x80]); // Empty for zero
    const hex = value.toString(16);
    return encodeBytes(Buffer.from(hex.padStart(hex.length % 2 ? hex.length + 1 : hex.length, '0'), 'hex'));
  }
  
  // For bigint or hex string
  let hex = typeof value === 'bigint' ? value.toString(16) : value.replace(/^0x/, '');
  
  // Remove leading zeros
  hex = hex.replace(/^0+/, '') || '0';
  
  // If zero, return empty bytes
  if (hex === '0') {
    return Buffer.from([0x80]);
  }
  
  // Ensure even length
  if (hex.length % 2) hex = '0' + hex;
  
  const bytes = Buffer.from(hex, 'hex');
  return encodeBytes(bytes);
}

function encodeList(items) {
  const encoded = Buffer.concat(items);
  return Buffer.concat([encodeLength(encoded.length, 0xc0), encoded]);
}

async function main() {
  console.log('🚀 Sending Full HYBRID_PQ Transaction with DILITHIUM3\n');
  console.log('════════════════════════════════════════════════════════════\n');
  
  // Connect to local Besu node
  const provider = new ethers.JsonRpcProvider('http://localhost:8545');
  
  // Test account with private key
  const privateKey = '0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63';
  const wallet = new ethers.Wallet(privateKey, provider);
  
  console.log('📍 Sender:', wallet.address);
  
  // Get current nonce and gas price
  const nonce = await provider.getTransactionCount(wallet.address);
  const feeData = await provider.getFeeData();
  
  console.log('📍 Nonce:', nonce);
  
  // Use reasonable default values for local network
  const maxPriorityFeePerGas = ethers.parseUnits('0', 'gwei'); // No priority fee on local network
  const maxFeePerGas = ethers.parseUnits('2', 'gwei');
  
  // Load PQ keys
  const pqKeysDir = path.join(__dirname, '..', 'pq-keys');
  const privateKeyPath = path.join(pqKeysDir, 'dilithium3-private.key');
  const publicKeyPath = path.join(pqKeysDir, 'dilithium3-public.key');
  
  // Check if keys exist
  if (!fs.existsSync(privateKeyPath) || !fs.existsSync(publicKeyPath)) {
    console.error('\n❌ DILITHIUM3 keys not found!');
    console.error('Run: npm run demo');
    process.exit(1);
  }
  
  console.log('\n📁 Using PQ keys from:', pqKeysDir);
  
  // Transaction parameters
  const txParams = {
    chainId: 1337,
    nonce: nonce,
    maxPriorityFeePerGas: maxPriorityFeePerGas,
    maxFeePerGas: maxFeePerGas,
    gasLimit: 100000n,
    to: wallet.address,
    value: ethers.parseEther('0.001'),
    data: '0x',
    accessList: []
  };
  
  console.log('\nTransaction params:', {
    ...txParams,
    maxPriorityFeePerGas: ethers.formatUnits(txParams.maxPriorityFeePerGas, 'gwei') + ' gwei',
    maxFeePerGas: ethers.formatUnits(txParams.maxFeePerGas, 'gwei') + ' gwei',
    value: ethers.formatEther(txParams.value) + ' ETH'
  });
  
  console.log('\n🔨 BƯỚC 1: Creating unsigned HYBRID_PQ transaction...');
  
  // Build unsigned transaction RLP for hashing
  const unsignedRlpFields = [
    encodeScalar(txParams.chainId),
    encodeScalar(txParams.nonce),
    encodeScalar(txParams.maxPriorityFeePerGas),
    encodeScalar(txParams.maxFeePerGas),
    encodeScalar(txParams.gasLimit),
    encodeBytes(Buffer.from(txParams.to.slice(2), 'hex')),
    encodeScalar(txParams.value),
    encodeBytes(Buffer.from(txParams.data.slice(2), 'hex')),
    encodeList([])  // accessList
  ];
  
  const unsignedRlpList = encodeList(unsignedRlpFields);
  const typeByte = Buffer.from([0x05]); // HYBRID_PQ transaction type
  const unsignedTx = Buffer.concat([typeByte, unsignedRlpList]);
  
  console.log('  ✅ Unsigned tx:', unsignedTx.length, 'bytes');
  console.log('  Type: 0x05 (HYBRID_PQ)');
  
  console.log('\n🔨 BƯỚC 2: Computing transaction hash...');
  const txHash = ethers.keccak256('0x' + unsignedTx.toString('hex'));
  console.log('  ✅ TX Hash:', txHash);
  console.log('  (Both ECDSA and DILITHIUM3 will sign this hash)');
  console.log('  Unsigned tx hex:', '0x' + unsignedTx.toString('hex'));
  
  console.log('\n🔨 BƯỚC 3: Signing with ECDSA...');
  // Sign the hash with ECDSA
  const messageHashBytes = Buffer.from(txHash.slice(2), 'hex');
  const ecdsaSig = wallet.signingKey.sign(messageHashBytes);
  
  // Convert v to yParity (27 -> 0, 28 -> 1)
  const yParity = ecdsaSig.v - 27;
  
  console.log('  ✅ ECDSA signature:');
  console.log('    r:', ecdsaSig.r);
  console.log('    s:', ecdsaSig.s);
  console.log('    yParity:', yParity);
  
  console.log('\n🔨 BƯỚC 4: Signing with DILITHIUM3...');
  const pqSignatureHex = await sign(privateKeyPath, txHash);
  const pqSignature = Buffer.from(pqSignatureHex.slice(2), 'hex');
  console.log('  ✅ PQ Signature:', pqSignature.length, 'bytes');
  
  // Get public key
  console.log('\n🔨 BƯỚC 5: Getting PQ public key...');
  const pqPublicKeyHex = await getPublicKey(publicKeyPath);
  const pqPublicKey = Buffer.from(pqPublicKeyHex.slice(2), 'hex');
  console.log('  ✅ PQ Public Key:', pqPublicKey.length, 'bytes');
  
  // VERIFY signature locally before sending!
  console.log('\n🔍 Verifying PQ signature locally...');
  const isValid = await verify(publicKeyPath, txHash, pqSignatureHex);
  if (!isValid) {
    console.error('❌ ERROR: PQ signature verification FAILED locally!');
    console.error('  This signature will be rejected by Besu.');
    process.exit(1);
  }
  console.log('  ✅ PQ signature is VALID locally');
  
  console.log('\n🔨 BƯỚC 6: Encoding signed transaction...');
  
  // Encode HYBRID_PQ transaction with BOTH signatures
  const rlpFields = [
    encodeScalar(txParams.chainId),
    encodeScalar(txParams.nonce),
    encodeScalar(txParams.maxPriorityFeePerGas),
    encodeScalar(txParams.maxFeePerGas),
    encodeScalar(txParams.gasLimit),
    encodeBytes(Buffer.from(txParams.to.slice(2), 'hex')),
    encodeScalar(txParams.value),
    encodeBytes(Buffer.from(txParams.data.slice(2), 'hex')),
    encodeList([]),                    // accessList
    encodeScalar(yParity),              // yParity
    encodeScalar(ecdsaSig.r),           // r
    encodeScalar(ecdsaSig.s),           // s
    encodeBytes(pqSignature),           // pqSignature
    encodeBytes(pqPublicKey)            // pqPublicKey
  ];
  
  const rlpList = encodeList(rlpFields);
  const rawTx = Buffer.concat([typeByte, rlpList]);
  
  console.log('  ✅ Signed transaction:');
  console.log('    Total size:', rawTx.length, 'bytes');
  console.log('    Type: 0x05 (HYBRID_PQ)');
  console.log('    First 100 bytes:', '0x' + rawTx.slice(0, 100).toString('hex'));
  
  const rawTxHex = '0x' + rawTx.toString('hex');
  
  console.log('\n════════════════════════════════════════════════════════════');
  console.log('🚀 Sending transaction to network...\n');
  
  try {
    const txHash = await provider.send('eth_sendRawTransaction', [rawTxHex]);
    console.log('✅ ✅ ✅ SUCCESS! ✅ ✅ ✅');
    console.log('Transaction accepted by network!');
    console.log('TX Hash:', txHash);
    
    // Wait for transaction receipt
    console.log('\n⏳ Waiting for confirmation...');
    const receipt = await provider.waitForTransaction(txHash);
    console.log('\n✅ Transaction mined in block:', receipt.blockNumber);
    console.log('Gas used:', receipt.gasUsed.toString());
    console.log('Status:', receipt.status === 1 ? '✅ Success' : '❌ Failed');
    
    console.log('\n════════════════════════════════════════════════════════════');
    console.log('🎉 Full HYBRID_PQ transaction with DILITHIUM3 successful!');
    console.log('   Both ECDSA and PQ signatures verified by network');
    console.log('════════════════════════════════════════════════════════════\n');
    
  } catch (error) {
    console.error('\n❌ Error sending transaction:', error.message);
    if (error.data) {
      console.error('Error data:', error.data);
    }
    
    // Try to decode the transaction to verify structure
    console.log('\n=== Decoding transaction locally ===');
    console.log('RLP structure:');
    console.log('  Type: 0x05');
    console.log('  List header:', '0x' + rlpList.slice(0, 3).toString('hex'));
    console.log('  ChainId:', txParams.chainId);
    console.log('  Nonce:', txParams.nonce);
    console.log('  MaxPriorityFee:', ethers.formatUnits(txParams.maxPriorityFeePerGas, 'gwei'), 'gwei');
    console.log('  MaxFee:', ethers.formatUnits(txParams.maxFeePerGas, 'gwei'), 'gwei');
    console.log('  GasLimit:', txParams.gasLimit.toString());
    console.log('  To:', txParams.to);
    console.log('  Value:', ethers.formatEther(txParams.value), 'ETH');
    console.log('  Data: (empty)');
    console.log('  AccessList: []');
    console.log('  yParity:', yParity);
    console.log('  r:', parsedTx.signature.r);
    console.log('  s:', parsedTx.signature.s);
    console.log('  PQ Signature:', pqSignature.length, 'bytes');
    console.log('  PQ Public Key:', pqPublicKey.length, 'bytes');
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
