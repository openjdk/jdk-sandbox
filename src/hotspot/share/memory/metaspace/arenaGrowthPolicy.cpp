#include "precompiled.hpp"

#include "memory/metaspace/arenaGrowthPolicy.hpp"
#include "memory/metaspace/chunkLevel.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {

// Todo: simplify?
// This used to contain more logic in the first prototypes, but now it is basically
// a set of hard-wired integer arrays. We may do away with the implementation hiding.

// A chunk allocation sequence which can be encoded with a simple const array.
class ConstantArenaGrowthPolicy : public ArenaGrowthPolicy {

  // integer array specifying chunk level allocation progression.
  // Last chunk is to be an endlessly repeated allocation.
  const chunklevel_t* const _entries;
  const int _num_entries;

public:

  ConstantArenaGrowthPolicy(const chunklevel_t* array, int num_entries)
    : _entries(array)
    , _num_entries(num_entries)
  {
    assert(_num_entries > 0, "must not be empty.");
  }

  chunklevel_t get_level_at_step(int num_allocated) const {
    if (num_allocated >= _num_entries) {
      // Caller shall repeat last allocation
      return _entries[_num_entries - 1];
    }
    return _entries[num_allocated];
  }

};

// hard-coded chunk allocation sequences for various space types
// (Note: no sudden jumps please)

static const chunklevel_t g_sequ_standard_non_class[] = {
    chunklevel::CHUNK_LEVEL_4K,
    chunklevel::CHUNK_LEVEL_4K,
    chunklevel::CHUNK_LEVEL_4K,
    chunklevel::CHUNK_LEVEL_8K,
    chunklevel::CHUNK_LEVEL_16K
    // .. repeat last
};

static const chunklevel_t g_sequ_standard_class[] = {
    chunklevel::CHUNK_LEVEL_2K,
    chunklevel::CHUNK_LEVEL_2K,
    chunklevel::CHUNK_LEVEL_4K,
    chunklevel::CHUNK_LEVEL_8K,
    chunklevel::CHUNK_LEVEL_16K
    // .. repeat last
};

static const chunklevel_t g_sequ_anon_non_class[] = {
   chunklevel::CHUNK_LEVEL_1K,
   // .. repeat last
};

static const chunklevel_t g_sequ_anon_class[] = {
    chunklevel::CHUNK_LEVEL_1K,
    // .. repeat last
};

static const chunklevel_t g_sequ_refl_non_class[] = {
    chunklevel::CHUNK_LEVEL_2K,
    chunklevel::CHUNK_LEVEL_1K
    // .. repeat last
};

static const chunklevel_t g_sequ_refl_class[] = {
    chunklevel::CHUNK_LEVEL_1K,
    // .. repeat last
};

// Boot class loader: give it large chunks: beyond commit granule size
// (typically 64K) the costs for large chunks largely diminishes since
// they are committed on the fly.
static const chunklevel_t g_sequ_boot_non_class[] = {
    chunklevel::CHUNK_LEVEL_4M,
    chunklevel::CHUNK_LEVEL_1M
    // .. repeat last
};

static const chunklevel_t g_sequ_boot_class[] = {
    chunklevel::CHUNK_LEVEL_1M,
    chunklevel::CHUNK_LEVEL_256K
    // .. repeat last
};

#define DEFINE_CLASS_FOR_ARRAY(what) \
  static ConstantArenaGrowthPolicy g_chunk_alloc_sequence_##what (g_sequ_##what, sizeof(g_sequ_##what)/sizeof(chunklevel_t));

DEFINE_CLASS_FOR_ARRAY(standard_non_class)
DEFINE_CLASS_FOR_ARRAY(standard_class)
DEFINE_CLASS_FOR_ARRAY(anon_non_class)
DEFINE_CLASS_FOR_ARRAY(anon_class)
DEFINE_CLASS_FOR_ARRAY(refl_non_class)
DEFINE_CLASS_FOR_ARRAY(refl_class)
DEFINE_CLASS_FOR_ARRAY(boot_non_class)
DEFINE_CLASS_FOR_ARRAY(boot_class)

const ArenaGrowthPolicy* ArenaGrowthPolicy::policy_for_space_type(MetaspaceType space_type, bool is_class) {

  if (is_class) {
    switch(space_type) {
    case StandardMetaspaceType:          return &g_chunk_alloc_sequence_standard_class;
    case ReflectionMetaspaceType:        return &g_chunk_alloc_sequence_refl_class;
    case ClassMirrorHolderMetaspaceType: return &g_chunk_alloc_sequence_anon_class;
    case BootMetaspaceType:              return &g_chunk_alloc_sequence_boot_class;
    default: ShouldNotReachHere();
    }
  } else {
    switch(space_type) {
    case StandardMetaspaceType:          return &g_chunk_alloc_sequence_standard_non_class;
    case ReflectionMetaspaceType:        return &g_chunk_alloc_sequence_refl_non_class;
    case ClassMirrorHolderMetaspaceType: return &g_chunk_alloc_sequence_anon_non_class;
    case BootMetaspaceType:              return &g_chunk_alloc_sequence_boot_non_class;
    default: ShouldNotReachHere();
    }
  }

  return NULL;

}

} // namespace

