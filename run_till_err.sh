#!/bin/sh

# Run the passed command with the passed arguments until it fails in a loop
# Usage: run_till_err.sh <command and args>
# Run the command with a timeout of 5 seconds (configure via TIMEOUT env) every iteration
# run this in a loop until the command fails or MAX_TRIES (configure via MAX_TRIES env) is reached

# Set the timeout
TIMEOUT=${TIMEOUT:-10}
MAX_TRIES=${MAX_TRIES:-100000}

# Check the number of arguments
if [ $# -lt 1 ]; then
  echo "Usage: run_till_err.sh <command and args>"
  exit 1
fi

# Run the command in a loop
for i in $(seq 1 $MAX_TRIES); do
  echo "Running $@"
  timeout --preserve-status $TIMEOUT "$@"
  err_code=$?
  # if err_code is not 0 and not 143
  if [ $err_code -ne 0 ] && [ $err_code -ne 143 ]; then
    echo "Command failed after $i tries with error code $err_code"
    exit 1
  fi
done