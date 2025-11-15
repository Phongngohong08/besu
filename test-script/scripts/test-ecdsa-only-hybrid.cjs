/**
 * Test HYBRID_PQ transaction với CHỈ ECDSA signature (không có PQ)
 * Mục đích: Verify rằng ECDSA signing flow hoạt động đúng
 */

const { ethers } = require('ethers');
const crypto = require('crypto');

// RLP encoding helpers (FIXED version)
function encodeLength(len, offset) {
  if (len < 56) {
    return Buffer.from([offset + len]);
  }
  const hexLen = len.toString(16);
  const paddedHexLen = hexLen.length % 2 ? '0' + hexLen : hexLen;
  const lLen = paddedHexLen.length / 2;
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

function encodeScalar(value) {
  if (typeof value === 'number') {
    if (value === 0) return Buffer.from([0x80]);
    const hex = value.toString(16);
    return encodeBytes(Buffer.from(hex.padStart(hex.length % 2 ? hex.length + 1 : hex.length, '0'), 'hex'));
  }
  
  let hex = typeof value === 'bigint' ? value.toString(16) : value.replace(/^0x/, '');
  hex = hex.replace(/^0+/, '') || '0';
  
  if (hex === '0') {
    return Buffer.from([0x80]);
  }
  
  if (hex.length % 2) hex = '0' + hex;
  
  const bytes = Buffer.from(hex, 'hex');
  return encodeBytes(bytes);
}

function encodeList(items) {
  const encoded = Buffer.concat(items);
  return Buffer.concat([encodeLength(encoded.length, 0xc0), encoded]);
}

async function main() {
  console.log('🧪 Testing HYBRID_PQ with ECDSA-only signature\n');
  console.log('═'.repeat(60));
  
  const provider = new ethers.JsonRpcProvider('http://localhost:8545');
  const privateKey = '0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63';
  const wallet = new ethers.Wallet(privateKey, provider);
  
  console.log('📍 Sender:', wallet.address);
  
  const nonce = await provider.getTransactionCount(wallet.address);
  console.log('📍 Nonce:', nonce);
  
  // Transaction parameters
  const txParams = {
    chainId: 1337,
    nonce: nonce,
    maxPriorityFeePerGas: 1000000000n,  // 1 gwei
    maxFeePerGas: 2000000000n,          // 2 gwei
    gasLimit: 100000n,
    to: wallet.address,
    value: ethers.parseEther('0.001'),
    data: '0x',
    accessList: []
  };
  
  console.log('\n📝 Transaction params:');
  console.log('  Chain ID:', txParams.chainId);
  console.log('  To:', txParams.to);
  console.log('  Value:', ethers.formatEther(txParams.value), 'ETH');
  
  // BƯỚC 1: Tạo UNSIGNED HYBRID_PQ transaction để tính hash
  console.log('\n🔨 BƯỚC 1: Creating unsigned transaction...');
  
  const unsignedFields = [
    encodeScalar(txParams.chainId),
    encodeScalar(txParams.nonce),
    encodeScalar(txParams.maxPriorityFeePerGas),
    encodeScalar(txParams.maxFeePerGas),
    encodeScalar(txParams.gasLimit),
    encodeBytes(Buffer.from(txParams.to.slice(2), 'hex')),
    encodeScalar(txParams.value),
    encodeBytes(Buffer.from(txParams.data.slice(2), 'hex')),
    encodeList([])  // empty accessList
  ];
  
  const unsignedRLP = encodeList(unsignedFields);
  const typeByte = Buffer.from([0x05]); // HYBRID_PQ
  const unsignedTx = Buffer.concat([typeByte, unsignedRLP]);
  
  console.log('  ✅ Unsigned tx created:', unsignedTx.length, 'bytes');
  console.log('  Type: 0x05 (HYBRID_PQ)');
  
  // BƯỚC 2: Tính transaction hash
  console.log('\n🔨 BƯỚC 2: Computing transaction hash...');
  
  const txHash = ethers.keccak256(unsignedTx);
  console.log('  ✅ TX Hash:', txHash);
  console.log('  (This is what ECDSA will sign)');
  
  // BƯỚC 3: Sign với ECDSA
  console.log('\n🔨 BƯỚC 3: Signing with ECDSA...');
  
  const messageHash = ethers.getBytes(txHash);
  const signingKey = new ethers.SigningKey(privateKey);
  const ecdsaSig = signingKey.sign(messageHash);
  
  // Convert v to yParity (0 or 1)
  const yParity = ecdsaSig.yParity;
  
  console.log('  ✅ ECDSA signature:');
  console.log('    r:', ecdsaSig.r);
  console.log('    s:', ecdsaSig.s);
  console.log('    yParity:', yParity);
  
  // BƯỚC 4: Encode SIGNED HYBRID_PQ transaction (ECDSA only, no PQ)
  console.log('\n🔨 BƯỚC 4: Encoding signed transaction...');
  
  const signedFields = [
    encodeScalar(txParams.chainId),
    encodeScalar(txParams.nonce),
    encodeScalar(txParams.maxPriorityFeePerGas),
    encodeScalar(txParams.maxFeePerGas),
    encodeScalar(txParams.gasLimit),
    encodeBytes(Buffer.from(txParams.to.slice(2), 'hex')),
    encodeScalar(txParams.value),
    encodeBytes(Buffer.from(txParams.data.slice(2), 'hex')),
    encodeList([]),  // accessList
    encodeScalar(yParity),
    encodeScalar(ecdsaSig.r),
    encodeScalar(ecdsaSig.s),
    // NO PQ signature - testing ECDSA-only fallback
    encodeBytes(Buffer.from([])),  // empty PQ sig
    encodeBytes(Buffer.from([]))   // empty PQ pubkey
  ];
  
  const signedRLP = encodeList(signedFields);
  const signedTx = Buffer.concat([typeByte, signedRLP]);
  const signedTxHex = '0x' + signedTx.toString('hex');
  
  console.log('  ✅ Signed transaction:');
  console.log('    Total size:', signedTx.length, 'bytes');
  console.log('    Type: 0x05 (HYBRID_PQ)');
  console.log('    First 100 bytes:', signedTxHex.slice(0, 100) + '...');
  
  // BƯỚC 5: Gửi transaction
  console.log('\n🚀 BƯỚC 5: Sending transaction...');
  console.log('═'.repeat(60));
  
  try {
    const txHashResponse = await provider.send('eth_sendRawTransaction', [signedTxHex]);
    
    console.log('\n✅ ✅ ✅ SUCCESS! ✅ ✅ ✅');
    console.log('Transaction accepted by network!');
    console.log('TX Hash:', txHashResponse);
    
    console.log('\n⏳ Waiting for confirmation...');
    const receipt = await provider.waitForTransaction(txHashResponse);
    
    console.log('\n🎉 Transaction mined!');
    console.log('  Block:', receipt.blockNumber);
    console.log('  Gas used:', receipt.gasUsed.toString());
    console.log('  Status:', receipt.status === 1 ? '✅ Success' : '❌ Failed');
    
    console.log('\n' + '═'.repeat(60));
    console.log('📊 VERIFICATION RESULT:');
    console.log('  ECDSA signature: ✅ VALID');
    console.log('  PQ signature: ⏭️  SKIPPED (empty)');
    console.log('  Transaction: ✅ ACCEPTED');
    console.log('═'.repeat(60));
    
  } catch (error) {
    console.log('\n❌ Transaction rejected!');
    console.log('Error:', error.message);
    
    if (error.message.includes('Invalid signature')) {
      console.log('\n🔍 Analysis:');
      console.log('  ❌ ECDSA signature verification FAILED');
      console.log('  Possible causes:');
      console.log('    1. Wrong transaction hash calculation');
      console.log('    2. Incorrect RLP encoding');
      console.log('    3. Wrong signature format');
      
      console.log('\n💡 Debug info:');
      console.log('  Unsigned TX hash:', txHash);
      console.log('  Signature r:', ecdsaSig.r);
      console.log('  Signature s:', ecdsaSig.s);
      console.log('  yParity:', yParity);
      console.log('  Recovered address should be:', wallet.address);
      
    } else if (error.message.includes('Invalid params')) {
      console.log('\n🔍 Analysis:');
      console.log('  ❌ Transaction structure invalid');
      console.log('  RLP encoding issue - decoder cannot parse transaction');
      
    } else {
      console.log('\n🔍 Unknown error:', error);
    }
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error('💥 Fatal error:', error);
    process.exit(1);
  });
