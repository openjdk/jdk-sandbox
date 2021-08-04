/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

import java.util.Formatter;
import java.util.Locale;

/**
 * Snippets used in FormatterSnippets.
 */ 

final class FormatterSnippets<balanceDelta> {
   /*
// @start region=snippet1 :
   StringBuilder sb = new StringBuilder();
   // Send all output to the Appendable object sb
   Formatter formatter = new Formatter(sb, Locale.US);

   // Explicit argument indices may be used to re-order output.
   formatter.format("%4$2s %3$2s %2$2s %1$2s", "a", "b", "c", "d")
   // -> " d  c  b  a"

   // Optional locale as the first argument can be used to get
   // locale-specific formatting of numbers.  The precision and width can be
   // given to round and align the value.
   formatter.format(Locale.FRANCE, "e = %+10.4f", Math.E);
   // -> "e =    +2,7183"

   // The '(' numeric flag may be used to format negative numbers with
   // parentheses rather than a minus sign.  Group separators are
   // automatically inserted.
   formatter.format("Amount gained or lost since last statement: $ %(,.2f",
                    balanceDelta);
   // -> "Amount gained or lost since last statement: $ (6,217.58)"
   // @end snippet1
    */
}
