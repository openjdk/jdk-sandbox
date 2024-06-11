#!/bin/sh

# Runs the renaissance benchmark suite until it fails
# Usage: run_renaissance_till_fail.sh <GC, default is G1GC>
# Run the command with a timeout of 5 seconds (configure via TIMEOUT env) every iteration
# run this in a loop until the command fails or MAX_TRIES (configure via MAX_TRIES env) is reached

GC=${1:-G1GC}

# function to print things red
red() {
  echo "\e[31m$1\e[0m"
}

ABS_SCRIPT_FOLDER=$(dirname $(readlink -f $0))

# if renaissance.jar is not installed, download it
if [ ! -f renaissance.jar ]; then
  wget https://github.com/renaissance-benchmarks/renaissance/releases/download/v0.15.0/renaissance-gpl-0.15.0.jar -O $ABS_SCRIPT_FOLDER/renaissance.jar
fi

# run java -XX:StartFlightRecording=filename=fligt_and_10.jfr,jdk.CPUTimeExecutionSample#enabled=true,jdk.CPUTimeExecutionSample#period=1ms -jar renaissance.jar all
# in a tmp folder until it fails
# then print head -n40

# use ./run_till_err.sh to run the command in a loop until it fails

# create tmp folder to run in
TMP=$(mktemp -d)
(cd $TMP; $ABS_SCRIPT_FOLDER/run_till_err.sh java -XX:StartFlightRecording=filename=flight.jfr,jdk.CPUTimeExecutionSample#enabled=true,jdk.CPUTimeExecutionSample#period=1ms -XX:+Use$GC -jar $ABS_SCRIPT_FOLDER/renaissance.jar all)

# success if no $TMP/hs_err_pid*.log exists

if [ ! -f $TMP/hs_err_pid*.log ]; then
  echo "Command succeeded in folder $TMP"
  rm -rf $TMP
  exit 0
fi

log_file=$(ls $TMP/hs_err_pid*.log)

# if failed: print the first 40 lines of the hs_err_pid file
echo "Command failed in folder $TMP"

# Extract the error line (always the 4th line)
error_line=$(sed -n '4p' $log_file)

# Extract the current thread line
current_thread_line=$(grep "^Current thread" $log_file)

# Extract the first 5 frames
first_5_frames=$(awk '/Stack:/ {found=1} found && /^V/ {print; if (++count == 5) exit}' $log_file)

# print the error_line in red
red "$error_line"
red "$current_thread_line"
red
red "$first_5_frames"

exit $?
