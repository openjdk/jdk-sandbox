#ifndef SHARE_RUNTIME_NMETHODGROUPER_HPP
#define SHARE_RUNTIME_NMETHODGROUPER_HPP

#include "memory/allStatic.hpp"
#include "utilities/linkedlist.hpp"
#include "runtime/nonJavaThread.hpp"

class NMethodGrouper : public AllStatic {
 private:
  static void group_nmethods();

  static NonJavaThread *_nmethod_grouper_thread;
 public:
  static LinkedListImpl<const nmethod*> _unregistered_nmethods;
  static void group_nmethods_loop();
  static void initialize();
  static void unregister_nmethod(const nmethod* nm);
};

#endif // SHARE_RUNTIME_NMETHODGROUPER_HPP