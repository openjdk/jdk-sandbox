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

import java.text.DateFormatSymbols;
import java.util.Locale;
import javax.swing.table.AbstractTableModel;

/**
 * Abstract Table model that supports month selection from respective panel
 *
 * @since 99
 */

@SuppressWarnings("serial")
class MonthTableViewModel extends AbstractTableModel {
    private static final int ROW = 4;
    private static final int COLUMN = 3;
    private final Locale locale;
    private final String[][] monthData = new String[ROW][COLUMN];

    /**
     * Creates an instance of {@code MonthTableViewModel}
     * @param locale locale
     */
    public MonthTableViewModel(Locale locale) {
        this.locale = locale;
    }

    @Override
    public int getRowCount() {
        return ROW;
    }

    @Override
    public int getColumnCount() {
        return COLUMN;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return monthData[rowIndex][columnIndex];
    }

    /**
     * Updates month table date based on locale
     */
    public void updateMonthData() {
        String[] months = new DateFormatSymbols(this.locale).getShortMonths();
        int i = 0;
        for (int row = 0; row < ROW; row++) {
            for (int col = 0; col < COLUMN; col++) {
                monthData[row][col] = months[i++];
            }
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