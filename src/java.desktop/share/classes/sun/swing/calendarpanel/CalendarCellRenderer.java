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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.WeekFields;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;
import javax.swing.JCalendarPanel;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * A cell renderer for the {@code JCalendarPanel} Month view.
 *
 * @since 99
 */

@SuppressWarnings("serial")
class CalendarCellRenderer extends DefaultTableCellRenderer {
    private final JCalendarPanel calendarPanel;
    private Calendar calendar;
    private String weekNo;

    /**
     * Creates an instance of {@code CalendarCellRenderer}
     *
     * @param calendarPanel calendar panel
     */
    public CalendarCellRenderer(JCalendarPanel calendarPanel) {
        this.calendarPanel = calendarPanel;
        installStrings();
    }

    private void installStrings() {
        Locale l = calendarPanel.getDateSelectionModel().getLocale();
        weekNo = UIManager.getString("CalendarPanel.WeekNumberText", l);
    }

    private void setCalendar(Calendar calendar) {
        this.calendar = (Calendar) calendar.clone();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
        if (value == null) {
            return super.getTableCellRendererComponent(table, value,
                    isSelected, hasFocus, row, column);
        }

        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value,
                isSelected, hasFocus, row, column);
        LocalDate localDate;
        label.setHorizontalAlignment(JLabel.CENTER);

        setCalendar(calendarPanel.getDateSelectionModel().getCalendar());
        Calendar currentCalendar = Calendar.getInstance();
        Calendar selectedCalendar = (Calendar) calendar.clone();
        selectedCalendar.set(calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH), 1);
        int day = (Integer) value;
        int daysInCurrentMonth = selectedCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        boolean isWeekNumber = calendarPanel.isWeekNumberEnabled() ? ((column == 0) ? true : false) : false;

        if (day < 1 || day > daysInCurrentMonth) {
            localDate = prepareComponentForLeadingTrailingMonth(label, selectedCalendar,
                    day, daysInCurrentMonth, isWeekNumber);
        } else {
            localDate = prepareLabelForCurrentMonth(label, currentCalendar, day, isSelected, isWeekNumber);
        }
        label.setFont(calendarPanel.getTableFont());
        label.setLocale(calendarPanel.getDateSelectionModel().getLocale());
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).
                withLocale(calendarPanel.getLocale());

        label.setEnabled(calendarPanel.isWithinSelectableDateRange(
                getLocalDateFromValue((Integer) value, calendar)));
        if (calendarPanel.isWeekNumberEnabled() && column == 0) {
            label.setText(Integer.toString(localDate.get(WeekFields.of(
                    calendarPanel.getLocale()).weekOfYear())));
            label.getAccessibleContext().setAccessibleName(weekNo + Integer.toString(localDate.get(WeekFields.of(
                    calendarPanel.getLocale()).weekOfYear())));
        } else {
            label.getAccessibleContext().setAccessibleName(localDate.format(formatter));
        }
        return label;
    }

    private LocalDate prepareLabelForCurrentMonth(JLabel label,
                                                  Calendar currentCalendar,
                                                  Integer selectedDay,
                                                  boolean isSelected,
                                                  boolean isWeekNumber) {
        LocalDate localDate = LocalDate.of(calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1, selectedDay);
        if (isWeekNumber) {
            label.setBackground(calendarPanel.getTableBackground());
            label.setForeground(calendarPanel.getWeekNumberForeground());
        } else if (calendarPanel.getDateSelectionModel().getSelectedDates().contains(localDate)) {
            label.setBackground(calendarPanel.getTableSelectionBackground());
            label.setForeground(calendarPanel.getTableSelectionForeground().brighter());
        } else if (!isWeekNumber && currentCalendar.get(Calendar.DATE) == selectedDay
                && currentCalendar.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)
                && currentCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)) {
            label.setForeground(calendarPanel.getTableCurrentDateForeground());
            label.setBackground(calendarPanel.getTableCurrentDateBackground().darker());
        } else {
            label.setBackground(calendarPanel.getTableBackground());
            label.setForeground(calendarPanel.getTableForeground());
        }
        return localDate;
    }

    private LocalDate prepareComponentForLeadingTrailingMonth(JLabel label,
                                                              Calendar selectedCalendar,
                                                              Integer selectedDay,
                                                              int daysInCurrentMonth,
                                                              boolean isWeekNumber) {
        LocalDate localDate;
        if (selectedDay > daysInCurrentMonth) {
            Calendar nextMonth = (Calendar) calendar.clone();
            int day = selectedDay - daysInCurrentMonth;
            nextMonth.set(selectedCalendar.get(Calendar.YEAR),
                    selectedCalendar.get(Calendar.MONTH) + 1, 1);
            label.setText(Integer.toString(day));
            localDate = LocalDate.of(nextMonth.get(Calendar.YEAR),
                    nextMonth.get(Calendar.MONTH) + 1, day);
        } else {
            Calendar lastMonth = (Calendar) calendar.clone();
            lastMonth.set(selectedCalendar.get(Calendar.YEAR),
                    selectedCalendar.get(Calendar.MONTH) - 1, 1);
            int lastDayLastMonth = lastMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
            int day = lastDayLastMonth + selectedDay + 1;
            label.setText(Integer.toString(day));
            localDate = LocalDate.of(lastMonth.get(Calendar.YEAR),
                    lastMonth.get(Calendar.MONTH) + 1, day);
        }

        if (isWeekNumber) {
            label.setBackground(calendarPanel.getTableBackground());
            label.setForeground(calendarPanel.getWeekNumberForeground());
        } else if (calendarPanel.getDateSelectionModel().getSelectedDates().contains(localDate)) {
            label.setBackground(calendarPanel.getTableSelectionBackground());
            label.setForeground(getLighterColor(calendarPanel.getTableSelectionForeground(), 0.5));
        } else {
            label.setBackground(calendarPanel.getTableBackground());
            label.setForeground(getLighterColor(calendarPanel.getTableForeground(), 0.5));
        }
        return localDate;
    }

    private LocalDate getLocalDateFromValue(int day, Calendar cal) {
        LocalDate localDate;
        if (day <= 0) {
            Calendar prevMonth = (Calendar) cal.clone();
            prevMonth.add(Calendar.MONTH, -1);
            int totalDays = prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
            day = totalDays + day + 1;
            localDate = LocalDate.of(prevMonth.get(Calendar.YEAR),
                    prevMonth.get(Calendar.MONTH) + 1, day);
        } else if (day > cal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            int totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            day = day - totalDays;
            localDate = LocalDate.of(cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1, day);
            localDate = localDate.plusMonths(1);
        } else {
            localDate = LocalDate.of(cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1, day);
        }
        return localDate;
    }

    private Color getLighterColor(Color color, double factor) {
        int red = (int) Math.min(255, color.getRed() + (255 - color.getRed()) * factor);
        int green = (int) Math.min(255, color.getGreen() + (255 - color.getGreen()) * factor);
        int blue = (int) Math.min(255, color.getBlue() + (255 - color.getBlue()) * factor);
        return new Color(red, green, blue, color.getAlpha());
    }
}
