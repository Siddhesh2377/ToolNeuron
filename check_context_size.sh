#!/usr/bin/env bash

# Directory where your llama.cpp binary lives
BINARY_DIR="/home/home/dev/AI/llama.cpp/build/bin"
# Name of the binary (adjust if yours is named differently)
BINARY_NAME="llama-run"

# Check for model file argument
if [ $# -ne 1 ]; then
  echo "Usage: $0 /path/to/your_model.gguf"
  exit 1
fi

MODEL_PATH="$1"

# Make sure binary exists
if [ ! -x "${BINARY_DIR}/${BINARY_NAME}" ]; then
  echo "Error: Cannot find executable at ${BINARY_DIR}/${BINARY_NAME}"
  exit 2
fi

# Run in verbose + auto-ctx mode, capture output
OUTPUT=$(
  "${BINARY_DIR}/${BINARY_NAME}" \
    -m "${MODEL_PATH}" \
    -c 0 \
    -v \
    -p "" 2>&1 \
  | grep -oP 'context size = \K[0-9]+'
)
echo $OUTPUT
if [ -z "$OUTPUT" ]; then
  echo "Failed to detect context size."
  exit 3
else
  echo "Model max context window: $OUTPUT tokens"
fi
