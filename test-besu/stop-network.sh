#!/bin/bash

# Stop all Besu nodes gracefully

echo "🛑 Stopping Besu network..."

# Try to read PIDs from file
if [ -f "/home/phongnh/projects/besu/test-besu/.besu_pids" ]; then
    PIDS=$(cat /home/phongnh/projects/besu/test-besu/.besu_pids)
    for PID in $PIDS; do
        if kill -0 $PID 2>/dev/null; then
            echo "   Stopping node (PID: $PID)..."
            kill $PID
        fi
    done
    rm /home/phongnh/projects/besu/test-besu/.besu_pids
fi

# Fallback: kill all besu processes
pkill -f "besu.*--data-path" || true

echo "✅ All nodes stopped"
