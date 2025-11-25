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

import java.util.Calendar;
import javax.swing.JCalendarPanel;
import javax.swing.table.AbstractTableModel;

/**
 * This is an implementation of TableModel that uses an {@code Object} to store the
 * date cell value.
 *
 * @since 99
 */

@SuppressWarnings("serial")
class DateTableViewModel extends AbstractTableModel {
    private static final int ROW = 6;
    private static final int COLUMN = 8;
    private final JCalendarPanel calendarPanel;
    private Calendar calendar;

    /**
     * Creates an instance of {@code DateTableViewModel}
     */
    public DateTableViewModel(JCalendarPanel calendarPanel) {
        this.calendarPanel = calendarPanel;
        setCalendar(calendarPanel.getDateSelectionModel().getCalendar());
    }

    /**
     * Set the {@code Calendar} instance
     * @param calendar calendar
     */
    public void setCalendar(Calendar calendar) {
        this.calendar = (Calendar) calendar.clone();
    }

    @Override
    public int getRowCount() {
        return ROW;
    }

    @Override
    public int getColumnCount() {
        return calendarPanel.isWeekNumberEnabled() ? COLUMN : COLUMN - 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Calendar currentCal = (Calendar) calendar.clone();
        currentCal.set(Calendar.DAY_OF_MONTH, 1);

        int firstDayOfWeekOffset = calendarPanel.getDateSelectionModel().getCalendar().getFirstDayOfWeek();
        int daysOffset = (7 + (currentCal.get(Calendar.DAY_OF_WEEK) - firstDayOfWeekOffset)) % 7;

        if (calendarPanel.isWeekNumberEnabled() && columnIndex > 0) {
            columnIndex = columnIndex - 1;
        }
        int cellIndex = rowIndex * 7 + columnIndex;
        int offset = cellIndex - daysOffset;

        if (offset < 0) {
            return offset;
        } else {
            return offset + 1;
        }
    }

    @Override
    public String getColumnName(int column) {
        int firsDayOfWeek = calendarPanel.getDateSelectionModel().getCalendar().getFirstDayOfWeek();
        if (calendarPanel.isWeekNumberEnabled()) {
            if (column == 0) {
                return "";
            } else {
                column = column - 1;
            }
        }
        int dayOfWeek = (firsDayOfWeek + column) % 7;
        Calendar tempCalendar = Calendar.getInstance(calendarPanel.getDateSelectionModel().getLocale());
        tempCalendar.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        return tempCalendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT,
                calendarPanel.getDateSelectionModel().getLocale());
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return (columnIndex == 0 && getValueAt(0, columnIndex) != null)
                ? String.class : Object.class;
    }
}