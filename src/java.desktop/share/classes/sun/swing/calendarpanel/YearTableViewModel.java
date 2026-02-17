/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.swing.calendarpanel;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import javax.swing.table.AbstractTableModel;

/**
 * Abstract table model that supports selecting a Year from
 * respective panel
 *
 * @since 99
 */

@SuppressWarnings("serial")
class YearTableViewModel extends AbstractTableModel {
    private final List<List<Integer>> yearData = new ArrayList<>();
    private SortedSet<Integer> yearViewLimit;
    private int noOfColumns = 4;

    /**
     * Creates an instance of {@code YearTableViewModel}
     *
     * @param yearViewLimit Range used to limit the year selection
     */
    public YearTableViewModel(SortedSet<Integer> yearViewLimit) {
        this.yearViewLimit = yearViewLimit;
        updateYearViewLimit(yearViewLimit);
    }

    @Override
    public int getRowCount() {
        return yearData.size();
    }

    @Override
    public int getColumnCount() {
        return noOfColumns;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return yearData.get(rowIndex).get(columnIndex);
    }

    /**
     * Updates year data on start year value.
     * @param yearLimit year limit range
     */
    public void updateYearViewLimit(SortedSet<Integer> yearLimit) {
        yearViewLimit = yearLimit;
        yearData.clear();
        int yearMin = yearViewLimit.first();
        int yearMax = yearViewLimit.last();
        int year = yearMin;
        int totalYears = yearMax - yearMin + 1;

        int maxRows = (int) Math.ceil((double) totalYears / noOfColumns);
        for (int row = 0; row < maxRows; row++) {
            List<Integer> rowData = new ArrayList<>();
            for (int col = 0; col < noOfColumns; col++) {
                if (year <= yearMax) {
                    rowData.add(year);
                    year++;
                } else {
                    rowData.add(null);
                }
            }
            yearData.add(rowData);
        }

            fireTableDataChanged();
        }

        @Override
    public Class<?> getColumnClass(int columnIndex) {
        return (columnIndex == 0 && getValueAt(0, columnIndex) != null)
                ? String.class : Object.class;
    }

    @Override
    public String getColumnName(int column) {
        return "";
    }
}