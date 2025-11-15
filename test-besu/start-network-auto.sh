#!/bin/bash

# Complete automated setup for Besu IBFT2 network
# This will setup nodes and start them automatically

set -e

BESU_BIN="/home/phongnh/projects/besu/build/install/besu/bin/besu"
BASE_DIR="/home/phongnh/projects/besu/test-besu"
GENESIS_FILE="$BASE_DIR/genesis.json"

echo "🔧 Besu IBFT2 Network - Complete Setup"
echo "========================================"
echo ""

# Kill any existing Besu processes
echo "🛑 Stopping any existing Besu nodes..."
pkill -f "besu.*--data-path" || true
sleep 2

# Setup nodes with validator keys
echo ""
echo "📦 Setting up validator nodes..."
echo ""

# Node 1 - Validator 0x2290591790fe17335b20758a0b3e50b63c0f6f4e
echo "   Node-1: 0x2290591790fe17335b20758a0b3e50b63c0f6f4e"
rm -rf "$BASE_DIR/Node-1/data"
mkdir -p "$BASE_DIR/Node-1/data"
cp "$BASE_DIR/networkFiles/keys/0x2290591790fe17335b20758a0b3e50b63c0f6f4e/key" "$BASE_DIR/Node-1/data/key"
cp "$BASE_DIR/networkFiles/keys/0x2290591790fe17335b20758a0b3e50b63c0f6f4e/key.pub" "$BASE_DIR/Node-1/data/key.pub"

# Node 2 - Validator 0x0db6f08428985475eea475da8775fccbe12a005d
echo "   Node-2: 0x0db6f08428985475eea475da8775fccbe12a005d"
rm -rf "$BASE_DIR/Node-2/data"
mkdir -p "$BASE_DIR/Node-2/data"
cp "$BASE_DIR/networkFiles/keys/0x0db6f08428985475eea475da8775fccbe12a005d/key" "$BASE_DIR/Node-2/data/key"
cp "$BASE_DIR/networkFiles/keys/0x0db6f08428985475eea475da8775fccbe12a005d/key.pub" "$BASE_DIR/Node-2/data/key.pub"

# Node 3 - Validator 0xe085816a0fa53c57c6acf4d9efb5538e36cbe443
echo "   Node-3: 0xe085816a0fa53c57c6acf4d9efb5538e36cbe443"
rm -rf "$BASE_DIR/Node-3/data"
mkdir -p "$BASE_DIR/Node-3/data"
cp "$BASE_DIR/networkFiles/keys/0xe085816a0fa53c57c6acf4d9efb5538e36cbe443/key" "$BASE_DIR/Node-3/data/key"
cp "$BASE_DIR/networkFiles/keys/0xe085816a0fa53c57c6acf4d9efb5538e36cbe443/key.pub" "$BASE_DIR/Node-3/data/key.pub"

# Node 4 - Validator 0x91817b736305054b2e71a2afb1c1a2e32ea13123
echo "   Node-4: 0x91817b736305054b2e71a2afb1c1a2e32ea13123"
rm -rf "$BASE_DIR/Node-4/data"
mkdir -p "$BASE_DIR/Node-4/data"
cp "$BASE_DIR/networkFiles/keys/0x91817b736305054b2e71a2afb1c1a2e32ea13123/key" "$BASE_DIR/Node-4/data/key"
cp "$BASE_DIR/networkFiles/keys/0x91817b736305054b2e71a2afb1c1a2e32ea13123/key.pub" "$BASE_DIR/Node-4/data/key.pub"

echo ""
echo "✅ All nodes configured with validator keys"
echo ""

# Create log directory
mkdir -p "$BASE_DIR/logs"

# Start Node-1 (bootnode)
echo "🚀 Starting Node-1 (Bootnode + Validator)..."
cd "$BASE_DIR/Node-1"
$BESU_BIN \
  --data-path=data \
  --genesis-file=$GENESIS_FILE \
  --rpc-http-enabled \
  --rpc-http-api=ETH,NET,IBFT,ADMIN,DEBUG,TXPOOL \
  --host-allowlist="*" \
  --rpc-http-cors-origins="all" \
  --rpc-http-port=8545 \
  --p2p-port=30303 \
  --min-gas-price=0 \
  --miner-enabled \
  --miner-coinbase=0xfe3b557e8fb62b89f4916b721be55ceb828dbd73 \
  > "$BASE_DIR/logs/node1.log" 2>&1 &

NODE1_PID=$!
echo "   ✓ Node-1 started (PID: $NODE1_PID)"

# Wait for Node-1 to initialize and get enode
echo "   ⏳ Waiting for Node-1 to initialize..."
sleep 5

# Extract enode from logs
BOOTNODE_ENODE=""
for i in {1..10}; do
    if [ -f "$BASE_DIR/logs/node1.log" ]; then
        BOOTNODE_ENODE=$(grep -oP 'Enode URL \K.*' "$BASE_DIR/logs/node1.log" | head -1)
        if [ ! -z "$BOOTNODE_ENODE" ]; then
            break
        fi
    fi
    sleep 2
done

if [ -z "$BOOTNODE_ENODE" ]; then
    echo "   ❌ Failed to get bootnode enode!"
    echo "   Check logs: tail -f $BASE_DIR/logs/node1.log"
    exit 1
fi

echo "   ✓ Bootnode enode: $BOOTNODE_ENODE"
echo ""

# Start Node-2
echo "🚀 Starting Node-2 (Validator)..."
cd "$BASE_DIR/Node-2"
$BESU_BIN \
  --data-path=data \
  --genesis-file=$GENESIS_FILE \
  --bootnodes=$BOOTNODE_ENODE \
  --rpc-http-enabled \
  --rpc-http-api=ETH,NET,IBFT,ADMIN \
  --host-allowlist="*" \
  --rpc-http-cors-origins="all" \
  --rpc-http-port=8546 \
  --p2p-port=30304 \
  --min-gas-price=0 \
  > "$BASE_DIR/logs/node2.log" 2>&1 &

NODE2_PID=$!
echo "   ✓ Node-2 started (PID: $NODE2_PID)"
sleep 2

# Start Node-3
echo "🚀 Starting Node-3 (Validator)..."
cd "$BASE_DIR/Node-3"
$BESU_BIN \
  --data-path=data \
  --genesis-file=$GENESIS_FILE \
  --bootnodes=$BOOTNODE_ENODE \
  --p2p-port=30305 \
  --min-gas-price=0 \
  > "$BASE_DIR/logs/node3.log" 2>&1 &

NODE3_PID=$!
echo "   ✓ Node-3 started (PID: $NODE3_PID)"
sleep 2

# Start Node-4
echo "🚀 Starting Node-4 (Validator)..."
cd "$BASE_DIR/Node-4"
$BESU_BIN \
  --data-path=data \
  --genesis-file=$GENESIS_FILE \
  --bootnodes=$BOOTNODE_ENODE \
  --p2p-port=30306 \
  --min-gas-price=0 \
  > "$BASE_DIR/logs/node4.log" 2>&1 &

NODE4_PID=$!
echo "   ✓ Node-4 started (PID: $NODE4_PID)"

echo ""
echo "✨ Network is starting up!"
echo ""
echo "📊 Node Status:"
echo "   Node-1 (RPC: 8545): PID $NODE1_PID"
echo "   Node-2 (RPC: 8546): PID $NODE2_PID"
echo "   Node-3: PID $NODE3_PID"
echo "   Node-4: PID $NODE4_PID"
echo ""
echo "📝 Log files:"
echo "   tail -f $BASE_DIR/logs/node1.log"
echo "   tail -f $BASE_DIR/logs/node2.log"
echo "   tail -f $BASE_DIR/logs/node3.log"
echo "   tail -f $BASE_DIR/logs/node4.log"
echo ""
echo "🔍 Check if network is producing blocks (wait ~10 seconds):"
echo "   curl -X POST --data '{\"jsonrpc\":\"2.0\",\"method\":\"eth_blockNumber\",\"params\":[],\"id\":1}' http://localhost:8545"
echo ""
echo "🛑 To stop all nodes:"
echo "   pkill -f 'besu.*--data-path'"
echo ""
echo "⏳ Waiting for consensus (need 3/4 validators)..."
sleep 10

# Check block number
BLOCK_NUM=$(curl -s -X POST --data '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' http://localhost:8545 | grep -oP '"result":"\K[^"]*')
if [ ! -z "$BLOCK_NUM" ]; then
    BLOCK_DEC=$((16#${BLOCK_NUM#0x}))
    echo ""
    echo "✅ Network is producing blocks!"
    echo "   Current block: $BLOCK_DEC (0x$BLOCK_NUM)"
    echo ""
    echo "🎉 Success! Your IBFT2 network is running!"
    echo ""
    echo "📡 RPC Endpoints:"
    echo "   Node-1: http://localhost:8545"
    echo "   Node-2: http://localhost:8546"
    echo ""
    echo "💰 Test Accounts (with balance):"
    echo "   0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
    echo "   0x627306090abaB3A6e1400e9345bC60c78a8BEf57"
    echo "   0xf17f52151EbEF6C7334FAD080c5704D77216b732"
    echo ""
    echo "🔐 Now you can test hybrid PQ transactions!"
    echo "   cd ../hardhat-example"
    echo "   npm run send-hybrid-tx"
    echo ""
else
    echo ""
    echo "⚠️  Network may still be starting up..."
    echo "   Check logs: tail -f $BASE_DIR/logs/node1.log"
    echo "   Try the curl command above in a few seconds"
    echo ""
fi

# Save PIDs for later
echo "$NODE1_PID $NODE2_PID $NODE3_PID $NODE4_PID" > "$BASE_DIR/.besu_pids"

echo "💡 Tip: Keep this terminal open or save the PIDs to stop nodes later"
