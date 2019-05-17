/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "jfr/recorder/repository/jfrChunk.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTimeConverter.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/os.inline.hpp"

static const u2 JFR_VERSION_MAJOR = 2;
static const u2 JFR_VERSION_MINOR = 0;

static const int64_t MAGIC_OFFSET = 0;
static const int64_t MAGIC_LEN = 4;
static const int64_t VERSION_OFFSET = MAGIC_LEN;
static const int64_t SIZE_OFFSET = 8;
static const int64_t SLOT_SIZE = 8;
static const int64_t CHECKPOINT_OFFSET = SIZE_OFFSET + SLOT_SIZE;
static const int64_t METADATA_OFFSET = CHECKPOINT_OFFSET + SLOT_SIZE;
static const int64_t START_NANOS_OFFSET = METADATA_OFFSET + SLOT_SIZE;
static const int64_t DURATION_NANOS_OFFSET = START_NANOS_OFFSET + SLOT_SIZE;
static const int64_t START_TICKS_OFFSET = DURATION_NANOS_OFFSET + SLOT_SIZE;
static const int64_t CPU_FREQUENCY_OFFSET = START_TICKS_OFFSET + SLOT_SIZE;
static const int64_t GENERATION_OFFSET = CPU_FREQUENCY_OFFSET + SLOT_SIZE;
static const int64_t CAPABILITY_OFFSET = GENERATION_OFFSET + 2;
static const int64_t HEADER_SIZE = CAPABILITY_OFFSET + 2;
static const int64_t RESERVE_SIZE = GENERATION_OFFSET - (4 * SIZE_OFFSET);
static const int64_t VOLATILE_FIELD_SIZE = SLOT_SIZE * 2;

static const u1 COMPLETE = 0;
static const u1 GUARD = 0xff;
static const u1 PAD = 0;
static const size_t GENERATION_SIZE = sizeof(u2);
static const size_t HEAD_BUFFER_SIZE = HEADER_SIZE + SLOT_SIZE;

typedef NoOwnershipAdapter JfrHeadBuffer; // stack local array as buffer
typedef StreamWriterHost<JfrHeadBuffer, StackObj> JfrBufferedHeadWriter;
typedef WriterHost<BigEndianEncoder, BigEndianEncoder, JfrBufferedHeadWriter> JfrHeadWriterBase;

static uint8_t head_buffer[HEAD_BUFFER_SIZE] = {0};

static fio_fd open_chunk(const char* path) {
  return path != NULL ? os::open(path, O_CREAT | O_RDWR, S_IREAD | S_IWRITE) : invalid_fd;
}

class JfrChunkHeadWriter : public StackObj {
  friend class JfrChunkWriter;
 private:
  JfrChunkWriter* _writer;
  JfrChunk* _chunk;

  void write_magic() {
    assert(MAGIC_OFFSET == _writer->current_offset(), "invariant");
    _writer->bytes("FLR", MAGIC_LEN);
  }

  void write_version() {
    assert(VERSION_OFFSET == _writer->current_offset(), "invariant");
    _writer->be_write((u2)JFR_VERSION_MAJOR);
    _writer->be_write((u2)JFR_VERSION_MINOR);
  }

  void write_size(int64_t size) {
    assert(SIZE_OFFSET == _writer->current_offset(), "invariant");
    _writer->be_write(size);
  }

  void write_checkpoint() {
    assert(CHECKPOINT_OFFSET == _writer->current_offset(), "invariant");
    _writer->be_write(_chunk->last_checkpoint_offset());
  }

  void write_metadata() {
    assert(METADATA_OFFSET == _writer->current_offset(), "invariant");
    _writer->be_write(_chunk->last_metadata_offset());
  }

  void write_time(bool finalize) {
    assert(_writer->is_valid(), "invariant");
    assert(_chunk != NULL, "invariant");
    assert(START_NANOS_OFFSET == _writer->current_offset(), "invariant");
    if (finalize) {
      _writer->be_write(_chunk->previous_start_nanos());
      _writer->be_write(_chunk->last_chunk_duration());
      _writer->be_write(_chunk->previous_start_ticks());
      return;
    }
    _writer->be_write(_chunk->start_nanos());
    _writer->be_write(_chunk->duration());
    _writer->be_write(_chunk->start_ticks());
  }

  void write_cpu_frequency() {
    assert(CPU_FREQUENCY_OFFSET == _writer->current_offset(), "invariant");
    static const jlong frequency = JfrTime::frequency();
    _writer->be_write(frequency);
  }

  void write_capabilities() {
    assert(CAPABILITY_OFFSET == _writer->current_offset(), "invariant");
    // chunk capabilities, CompressedIntegers etc
    static bool compressed_integers = JfrOptionSet::compressed_integers();
    _writer->be_write(compressed_integers ? (u2)1 : (u2)0);
  }

  void write_generation(bool finalize) {
    assert(GENERATION_OFFSET == _writer->current_offset(), "invariant");
    _writer->be_write(finalize ? COMPLETE : _chunk->generation());
    _writer->be_write(PAD);
  }

  void write_guard() {
    assert(GENERATION_OFFSET == _writer->current_offset(), "invariant");
    _writer->be_write(GUARD);
    _writer->be_write(PAD);
  }

  void write_guard_flush() {
    assert(GENERATION_OFFSET == _writer->current_offset(), "invariant");
    write_guard();
    _writer->flush();
  }

  void initialize() {
    assert(_writer->is_valid(), "invariant");
    assert(_chunk != NULL, "invariant");
    assert(0 == _writer->current_offset(), "invariant");
    write_magic();
    write_version();
    write_size(HEADER_SIZE);
    write_checkpoint();
    write_metadata();
    write_time(false);
    write_cpu_frequency();
    write_generation(false);
    write_capabilities();
    assert(HEADER_SIZE == _writer->current_offset(), "invariant");
    _writer->flush();
  }

  void flush(int64_t size, bool finalize) {
    assert(_writer->is_valid(), "invariant");
    assert(_chunk != NULL, "invariant");
    assert(SIZE_OFFSET == _writer->current_offset(), "invariant");
    write_size(size);
    write_checkpoint();
    write_metadata();
    write_time(finalize);
    write_cpu_frequency();
    write_generation(finalize);
    // no need to write capabilities
    _writer->seek(size); // implicit flush
  }

  JfrChunkHeadWriter(JfrChunkWriter* writer, int64_t offset) : _writer(writer), _chunk(writer->_chunk) {
    assert(_writer != NULL, "invariant");
    assert(_writer->is_valid(), "invariant");
    assert(_chunk != NULL, "invariant");
    if (0 == _writer->current_offset()) {
      assert(HEADER_SIZE == offset, "invariant");
      initialize();
    } else {
      _writer->seek(GENERATION_OFFSET);
      write_guard();
      _writer->seek(offset);
    }
    assert(offset == _writer->current_offset(), "invariant");
  }
};

JfrChunkWriter::JfrChunkWriter() : JfrChunkWriterBase(NULL), _chunk(new JfrChunk()) {}

JfrChunkWriter::~JfrChunkWriter() {
  assert(_chunk != NULL, "invariant");
  delete _chunk;
}

void JfrChunkWriter::set_path(const char* path) {
  assert(_chunk != NULL, "invariant");
  _chunk->set_path(path);
}

void JfrChunkWriter::time_stamp_chunk_now() {
  assert(_chunk != NULL, "invariant");
  _chunk->update_time_to_now();
}

int64_t JfrChunkWriter::flushpoint(bool finalize) {
  assert(_chunk != NULL, "invariant");
  const int64_t sz_written = size_written();
  if (!finalize) {
    _chunk->update();
  }
  JfrChunkHeadWriter head(this, SIZE_OFFSET);
  head.flush(sz_written, finalize);
  return sz_written;
}

int64_t JfrChunkWriter::size_written() const {
  return this->is_valid() ? this->current_offset() : 0;
}

int64_t JfrChunkWriter::last_checkpoint_offset() const {
  assert(_chunk != NULL, "invariant");
  return _chunk->last_checkpoint_offset();
}

int64_t JfrChunkWriter::current_chunk_start_nanos() const {
  assert(_chunk != NULL, "invariant");
  return this->is_valid() ? _chunk->start_nanos() : invalid_time;
}

void JfrChunkWriter::set_last_checkpoint_offset(int64_t offset) {
  assert(_chunk != NULL, "invariant");
  _chunk->set_last_checkpoint_offset(offset);
}

bool JfrChunkWriter::is_initial_flushpoint_for_chunk() const {
  assert(_chunk != NULL, "invariant");
  assert(_chunk->is_started(), "invariant");
  assert(!_chunk->is_finished(), "invariant");
  return _chunk->is_initial_flush();
}

void JfrChunkWriter::set_last_metadata_offset(int64_t offset) {
  assert(_chunk != NULL, "invariant");
  _chunk->set_last_metadata_offset(offset);
}

bool JfrChunkWriter::has_metadata() const {
  assert(_chunk != NULL, "invariant");
  return _chunk->has_metadata();
}

bool JfrChunkWriter::open() {
  assert(_chunk != NULL, "invariant");
  JfrChunkWriterBase::reset(open_chunk(_chunk->path()));
  const bool is_open = this->has_valid_fd();
  if (is_open) {
    assert(0 == this->current_offset(), "invariant");
    _chunk->reset();
    JfrChunkHeadWriter head(this, HEADER_SIZE);
  }
  return is_open;
}

int64_t JfrChunkWriter::close() {
  assert(this->has_valid_fd(), "invariant");
  const int64_t size_written = flushpoint(true);
  this->close_fd();
  assert(!this->is_valid(), "invariant");
  return size_written;
}
