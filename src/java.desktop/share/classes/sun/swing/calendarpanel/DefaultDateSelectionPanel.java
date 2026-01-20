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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;
import javax.swing.AbstractAction;
import javax.swing.AbstractCalendarPanel;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.DateSelectionModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCalendarPanel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * DefaultDateSelectionPanel creates calendar table panel which displays
 * dates with navigation buttons for forward/backward movement of month/year.
 * month and year can be selected from the respective label by
 * click which displays respective panels for selection.
 * date(s) shall be selected by mouse click.
 *
 * @since 99
 */

@SuppressWarnings("serial")
public final class DefaultDateSelectionPanel extends AbstractCalendarPanel
        implements CalendarTableHeader {
    private static final int HEADER_PANEL_HEIGHT = 40;
    private Calendar calendar;
    private DefaultTableCellRenderer defaultTableCellRenderer;
    private DefaultTableCellRenderer defaultTableHeaderRenderer;
    private DateTableViewModel tableModel;
    private JLabel monthLabel;
    private JLabel yearLabel;
    private JTable calendarTable;
    private JButton prevMonth;
    private JButton nextMonth;
    private JButton prevYear;
    private JButton nextYear;
    private String previousMonthText;
    private String nextMonthText;
    private String previousYearText;
    private String nextYearText;
    private String selectMonthText;
    private String selectYearText;

    /**
     * Creates a {@code DefaultDateSelectionPanel} instance
     */
    public DefaultDateSelectionPanel() {
    }

    @Override
    public void updateCalendar() {
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM",
                calendarPanel.getDateSelectionModel().getLocale());
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy",
                calendarPanel.getDateSelectionModel().getLocale());
        calendar = (Calendar) calendarPanel.getDateSelectionModel().getCalendar().clone();
        monthLabel.setText(monthFormat.format(calendar.getTime()));
        yearLabel.setText(yearFormat.format(calendar.getTime()));
        tableModel.setCalendar(calendar);
        tableModel.fireTableStructureChanged();
        setupCalendarTable();
        calendarTable.repaint();
    }

    @Override
    public void buildCalendar() {
        installStrings();
        DefaultTableCellRenderer cellRenderer = new CalendarCellRenderer(calendarPanel);
        cellRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        DefaultTableCellRenderer headerRenderer = new CalendarHeaderCellRenderer(calendarPanel);
        headerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        setDefaultTableHeaderRenderer(headerRenderer);
        setDefaultTableCellRenderer(cellRenderer);
        calendar = (Calendar) calendarPanel.getDateSelectionModel().getCalendar().clone();
        setLayout(new BorderLayout());
        tableModel = new DateTableViewModel(calendarPanel);
        calendarTable = new JTable(tableModel);
        add(getHeaderPanel(), BorderLayout.NORTH);
        setupCalendarTable();
        calendarTable.setFillsViewportHeight(true);
        JScrollPane scrollPane = new JScrollPane(calendarTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
        scrollPane.setPreferredSize(new Dimension((int) getCellDimension().getWidth()
                * tableModel.getColumnCount(), ((int) getCellDimension().getHeight()
                * (tableModel.getRowCount() + 1))));
        requestFocusInWindow();
        installListeners();
        updateCalendar();
    }

    private void installListeners() {
        installMouseListeners();
        installKeyboardActions();
        setFocusListeners();
    }

    private void installMouseListeners() {
        CalendarTableMouseListener mouseListener = new CalendarTableMouseListener();
        calendarTable.addMouseMotionListener(mouseListener);
        calendarTable.addMouseListener(mouseListener);
        prevMonth.addActionListener(new PreviousMonthAction());
        nextMonth.addActionListener(new NextMonthAction());
        prevYear.addActionListener(new PreviousYearAction());
        nextYear.addActionListener(new NextYearAction());
        yearLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                AbstractCalendarPanel defaultYearSelection = calendarPanel.getSelectionPanel(
                        DateSelectionModel.CalendarPanelType.YEAR_SELECTION);
                calendarPanel.setCalendarPanel(defaultYearSelection);
            }
        });
        monthLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                AbstractCalendarPanel defaultMonthSelection = calendarPanel.getSelectionPanel(
                        DateSelectionModel.CalendarPanelType.MONTH_SELECTION);
                calendarPanel.setCalendarPanel(defaultMonthSelection);
            }
        });
    }

    /**
     * installKeyboardActions
     */
    private void installKeyboardActions() {
        InputMap inputMap = calendarPanel.getInputMap(JComponent.WHEN_FOCUSED);
        SwingUtilities.replaceUIInputMap(calendarTable, JComponent.
                WHEN_FOCUSED, inputMap);
        SwingUtilities.replaceUIInputMap(prevMonth, JComponent.
                WHEN_FOCUSED, inputMap);
        SwingUtilities.replaceUIInputMap(nextMonth, JComponent.
                WHEN_FOCUSED, inputMap);
        SwingUtilities.replaceUIInputMap(prevYear, JComponent.
                WHEN_FOCUSED, inputMap);
        SwingUtilities.replaceUIInputMap(nextYear, JComponent.
                WHEN_FOCUSED, inputMap);
        SwingUtilities.replaceUIInputMap(yearLabel, JComponent.
                WHEN_FOCUSED, inputMap);
        SwingUtilities.replaceUIInputMap(monthLabel, JComponent.
                WHEN_FOCUSED, inputMap);
        ActionMap tableActionMap = calendarTable.getActionMap();
        ActionMap prevMonthActionMap = prevMonth.getActionMap();
        ActionMap nextMonthActionMap = nextMonth.getActionMap();
        ActionMap prevYearActionMap = prevYear.getActionMap();
        ActionMap nextYearActionMap = nextYear.getActionMap();
        ActionMap yearLabelActionMap = yearLabel.getActionMap();
        ActionMap monthLabelActionMap = monthLabel.getActionMap();

        tableActionMap.put("acceptSelection", new TableKeyBoardAction(JCalendarPanel.ACTION_ENTER_KEY));
        tableActionMap.put("cancelSelection", new TableKeyBoardAction(JCalendarPanel.ACTION_ESCAPE_KEY));

        tableActionMap.put("navigateUp", new TableKeyBoardAction(JCalendarPanel.ACTION_UP_ARROW_KEY));
        tableActionMap.put("navigateDown", new TableKeyBoardAction(JCalendarPanel.ACTION_DOWN_ARROW_KEY));
        tableActionMap.put("navigateLeft", new TableKeyBoardAction(JCalendarPanel.ACTION_LEFT_ARROW_KEY));
        tableActionMap.put("navigateRight", new TableKeyBoardAction(JCalendarPanel.ACTION_RIGHT_ARROW_KEY));

        tableActionMap.put("navigateShiftUp", new TableKeyBoardAction(JCalendarPanel.ACTION_SHIFT_UP_ARROW_KEY));
        tableActionMap.put("navigateShiftDown", new TableKeyBoardAction(JCalendarPanel.ACTION_SHIFT_DOWN_ARROW_KEY));
        tableActionMap.put("navigateShiftLeft", new TableKeyBoardAction(JCalendarPanel.ACTION_SHIFT_LEFT_ARROW_KEY));
        tableActionMap.put("navigateShiftRight", new TableKeyBoardAction(JCalendarPanel.ACTION_SHIFT_RIGHT_ARROW_KEY));
        prevMonthActionMap.put("acceptSelection", new PreviousMonthAction());
        nextMonthActionMap.put("acceptSelection", new NextMonthAction());
        prevYearActionMap.put("acceptSelection", new PreviousYearAction());
        nextYearActionMap.put("acceptSelection", new NextYearAction());
        yearLabelActionMap.put("acceptSelection", new YearLabelAction());
        monthLabelActionMap.put("acceptSelection", new MonthLabelAction());
    }

    private void installStrings() {
        Locale l = calendarPanel.getDateSelectionModel().getLocale();
        previousMonthText = UIManager.getString("CalendarPanel.PreviousMonthText", l);
        nextMonthText = UIManager.getString("CalendarPanel.NextMonthText", l);
        previousYearText = UIManager.getString("CalendarPanel.PreviousYearText", l);
        nextYearText = UIManager.getString("CalendarPanel.NextYearText", l);
        selectMonthText = UIManager.getString("CalendarPanel.MonthSelectionText", l);
        selectYearText = UIManager.getString("CalendarPanel.YearSelectionText", l);
    }

    @Override
    public void uninstallCalendarPanel(JCalendarPanel setCalendarPanel) {
        super.uninstallCalendarPanel(setCalendarPanel);
        uninstallKeyboardActions();
        removeAll();
    }

    /**
     * uninstallKeyboardActions
     */
    private void uninstallKeyboardActions() {
        SwingUtilities.replaceUIActionMap(calendarTable, null);
        SwingUtilities.replaceUIActionMap(prevMonth, null);
        SwingUtilities.replaceUIActionMap(nextMonth, null);
        SwingUtilities.replaceUIActionMap(prevYear, null);
        SwingUtilities.replaceUIActionMap(nextYear, null);
        SwingUtilities.replaceUIActionMap(monthLabel, null);
        SwingUtilities.replaceUIActionMap(yearLabel, null);
        SwingUtilities.replaceUIInputMap(calendarTable, JComponent.
                WHEN_FOCUSED, null);
        SwingUtilities.replaceUIInputMap(prevMonth, JComponent.
                WHEN_FOCUSED, null);
        SwingUtilities.replaceUIInputMap(nextMonth, JComponent.
                WHEN_FOCUSED, null);
        SwingUtilities.replaceUIInputMap(prevYear, JComponent.
                WHEN_FOCUSED, null);
        SwingUtilities.replaceUIInputMap(nextYear, JComponent.
                WHEN_FOCUSED, null);
        SwingUtilities.replaceUIInputMap(monthLabel, JComponent.
                WHEN_FOCUSED, null);
        SwingUtilities.replaceUIInputMap(yearLabel, JComponent.
                WHEN_FOCUSED, null);
    }

    private JPanel getHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setPreferredSize(new Dimension((int) super.getPreferredSize().getWidth(), HEADER_PANEL_HEIGHT));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        Dimension buttonSize = new Dimension(30, HEADER_PANEL_HEIGHT - 10);

        prevMonth = new JButton("<");
        nextMonth = new JButton(">");
        prevYear = new JButton("<");
        nextYear = new JButton(">");
        prevMonth.setName(previousMonthText);
        nextMonth.setName(nextMonthText);
        prevYear.setName(previousYearText);
        nextYear.setName(nextYearText);
        prevMonth.setPreferredSize(buttonSize);
        nextMonth.setPreferredSize(buttonSize);
        prevYear.setPreferredSize(buttonSize);
        nextYear.setPreferredSize(buttonSize);

        prevMonth.setBorder(null);
        nextMonth.setBorder(null);
        prevYear.setBorder(null);
        nextYear.setBorder(null);
        prevMonth.getAccessibleContext().setAccessibleName(previousMonthText);
        nextMonth.getAccessibleContext().setAccessibleName(nextMonthText);
        prevYear.getAccessibleContext().setAccessibleName(previousYearText);
        nextYear.getAccessibleContext().setAccessibleName(nextYearText);

        monthLabel = new JLabel("", SwingConstants.CENTER);
        yearLabel = new JLabel("", SwingConstants.CENTER);
        monthLabel.setName(selectMonthText);
        yearLabel.setName(selectYearText);
        monthLabel.getAccessibleContext().setAccessibleName(selectMonthText);
        yearLabel.getAccessibleContext().setAccessibleName(selectYearText);

        monthLabel.setPreferredSize(new Dimension(80, (int) buttonSize.getHeight()));
        yearLabel.setPreferredSize(new Dimension(50, (int) buttonSize.getHeight()));

        JPanel yearPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JPanel monthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        monthPanel.add(prevMonth);
        monthPanel.add(monthLabel);
        monthPanel.add(nextMonth);
        yearPanel.add(prevYear);
        yearPanel.add(yearLabel);
        yearPanel.add(nextYear);

        headerPanel.add(monthPanel, BorderLayout.WEST);
        headerPanel.add(yearPanel, BorderLayout.EAST);
        headerPanel.setFocusTraversalKeysEnabled(true);

        return headerPanel;
    }

    private void setupCalendarTable() {
        DefaultTableCellRenderer tableCellHeaderRenderer = getDefaultTableHeaderRenderer();
        monthLabel.setForeground(calendarPanel.getTableForeground());
        yearLabel.setForeground(calendarPanel.getTableForeground());
        monthLabel.setFont(calendarPanel.getTableFont());
        yearLabel.setFont(calendarPanel.getTableFont());
        calendarTable.setRowHeight((int) getCellDimension().getHeight());
        if (calendarPanel.getTableShowGridStatus()) {
            calendarTable.setShowGrid(true);
            calendarTable.setGridColor(calendarPanel.getTableGridColor());
        } else {
            calendarTable.setShowGrid(false);
        }

        for (int i = 0; i < calendarTable.getColumnCount(); i++) {
            calendarTable.getColumnModel().getColumn(i).setHeaderRenderer(tableCellHeaderRenderer);
        }

        calendarTable.setColumnSelectionAllowed(true);
        calendarTable.setRowSelectionAllowed(true);

        DefaultTableCellRenderer tableCellRenderer = getDefaultTableCellRenderer();

        for (int i = 0; i < calendarTable.getColumnCount(); i++) {
            calendarTable.getColumnModel().getColumn(i).setCellRenderer(tableCellRenderer);
        }
    }

    private void setFocusListeners() {
        CustomFocusListener.setFocusListener(prevMonth);
        CustomFocusListener.setFocusListener(nextMonth);
        CustomFocusListener.setFocusListener(prevYear);
        CustomFocusListener.setFocusListener(nextYear);
        CustomFocusListener.setFocusListener(monthLabel);
        CustomFocusListener.setFocusListener(yearLabel);
    }

    private class CalendarTableMouseListener extends MouseAdapter implements Serializable {
        boolean isMousePressed = false;
        private LocalDate firstSelectedDate;

        @Override
        public void mousePressed(MouseEvent e) {
            if (calendarPanel.isEnabled()) {
                int row = calendarTable.rowAtPoint(e.getPoint());
                int col = calendarTable.columnAtPoint(e.getPoint());
                if (calendarPanel.isWeekNumberEnabled() && col == 0) {
                    return;
                }
                Object value = calendarTable.getValueAt(row, col);
                Calendar cal = (Calendar) calendar.clone();
                LocalDate selectedDate;
                Integer day = (Integer) value;
                selectedDate = getLocalDateFromValue(day, cal);
                if (calendarPanel.isWithinSelectableDateRange(selectedDate)) {
                    firstSelectedDate = selectedDate;
                }
                isMousePressed = true;
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if ((firstSelectedDate != null) && calendarPanel.isEnabled()
                    && isMousePressed) {
                if (calendarPanel.getDateSelectionModel().getDateSelectionMode()
                        == DateSelectionModel.SelectionMode.SINGLE_SELECTION) {
                    calendar.set(firstSelectedDate.getYear(),
                            firstSelectedDate.getMonthValue() - 1,
                            firstSelectedDate.getDayOfMonth());
                    calendarPanel.getDateSelectionModel().setSelectedDate(firstSelectedDate, true);
                    calendarPanel.getDateSelectionModel().setCalendar(calendar);
                    updateCalendar();
                } else if (calendarPanel.getDateSelectionModel().getDateSelectionMode()
                        == DateSelectionModel.SelectionMode.RANGE_SELECTION) {
                    int row = calendarTable.rowAtPoint(e.getPoint());
                    int col = calendarTable.columnAtPoint(e.getPoint());
                    Object value = calendarTable.getValueAt(row, col);
                    Calendar cal = (Calendar) calendarPanel.getDateSelectionModel().getCalendar().clone();
                    LocalDate lastSelectedDate;
                    Integer day = (Integer) value;
                    lastSelectedDate = getLocalDateFromValue(day, cal);
                    if (calendarPanel.isWithinSelectableDateRange(lastSelectedDate)) {
                        calendar.set(firstSelectedDate.getYear(),
                                firstSelectedDate.getMonthValue() - 1,
                                firstSelectedDate.getDayOfMonth());
                        calendarPanel.getDateSelectionModel().setSelectedDates(firstSelectedDate,
                                lastSelectedDate, true);
                        calendarPanel.getDateSelectionModel().setCalendar(calendar);
                        updateCalendar();
                    }
                }
            }

            isMousePressed = false;
            firstSelectedDate = null;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (calendarPanel.isEnabled() && isMousePressed) {
                if (calendarPanel.getDateSelectionModel().getDateSelectionMode()
                        == DateSelectionModel.SelectionMode.RANGE_SELECTION) {
                    int row = calendarTable.rowAtPoint(e.getPoint());
                    int col = calendarTable.columnAtPoint(e.getPoint());
                    if (calendarPanel.isWeekNumberEnabled() && col == 0) {
                        return;
                    }
                    Object value = calendarTable.getValueAt(row, col);
                    Calendar cal = (Calendar) calendar.clone();
                    LocalDate lastSelectedDate;
                    Integer day = (Integer) value;

                    lastSelectedDate = getLocalDateFromValue(day, cal);
                    if (calendarPanel.isWithinSelectableDateRange(lastSelectedDate)) {
                        calendarPanel.getDateSelectionModel().setSelectedDates(firstSelectedDate,
                                lastSelectedDate, false);
                        calendarTable.repaint();
                    }
                }
            }
        }
    }

    private DefaultTableCellRenderer getDefaultTableCellRenderer() {
        return defaultTableCellRenderer;
    }

    @Override
    public void setDefaultTableCellRenderer(DefaultTableCellRenderer cellRenderer) {
        defaultTableCellRenderer = cellRenderer;
    }

    @Override
    public void setDefaultTableHeaderRenderer(DefaultTableCellRenderer headerRenderer) {
        defaultTableHeaderRenderer = headerRenderer;
    }


    private DefaultTableCellRenderer getDefaultTableHeaderRenderer() {
        return defaultTableHeaderRenderer;
    }

    public void paintComponent(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    @Override
    public String getDisplayName() {
        return UIManager.getString("CalendarPanel.DefaultTableNameText", getLocale());
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

    private boolean validateNavigation(DateSelectionModel.EventType eventType,
                                       DateSelectionModel.NavigationType navigationType) {
        int minYear = Calendar.getInstance().get(Calendar.YEAR) - calendarPanel.getYearSelectionLimit() - 1;
        LocalDate minDate = LocalDate.of(minYear, Calendar.DECEMBER + 1, 31);
        int maxYear = Calendar.getInstance().get(Calendar.YEAR) + calendarPanel.getYearSelectionLimit();
        LocalDate maxDate = LocalDate.of(maxYear, Calendar.JANUARY + 1, 1);
        LocalDate updatedDate = LocalDate.now();
        switch (eventType) {
            case MONTH_NAVIGATION -> {
                if (navigationType.equals(DateSelectionModel.NavigationType.FORWARD)) {
                    if (calendar.get(Calendar.MONTH) == Calendar.DECEMBER) {
                        updatedDate = LocalDate.of(calendar.get(Calendar.YEAR) + 1,
                                Calendar.JANUARY + 1, 1);
                    } else {
                        updatedDate = LocalDate.of(calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH) + 2, 1);
                    }
                } else {
                    if (calendar.get(Calendar.MONTH) == Calendar.JANUARY) {
                        updatedDate = LocalDate.of(calendar.get(Calendar.YEAR) - 1,
                                Calendar.DECEMBER + 1, 31);
                    } else {
                        updatedDate = LocalDate.of(calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH), calendar.get(Calendar.DATE));
                    }
                }
            }
            case YEAR_NAVIGATION -> {
                if (navigationType.equals(DateSelectionModel.NavigationType.BACKWARD)) {
                    updatedDate = LocalDate.of(calendar.get(Calendar.YEAR) - 1,
                            Calendar.DECEMBER + 1, 31);
                } else {
                    updatedDate = LocalDate.of(calendar.get(Calendar.YEAR) + 1,
                            Calendar.JANUARY + 1, 1);
                }
            }
        }
        return updatedDate.isAfter(minDate) && updatedDate.isBefore(maxDate);
    }

    /**
     * Navigation Action class
     */
    protected class TableKeyBoardAction extends AbstractAction {
        private static boolean isShiftKeyPressed = false;
        private static LocalDate startDateSelected;
        String actionKey;

        /**
         * Constructs an {@code TableKeyBoardAction}.
         *
         * @param action action Type
         */
        protected TableKeyBoardAction(String action) {
            actionKey = action;
        }

        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent e) {
            int row = calendarTable.getSelectedRow();
            int col = calendarTable.getSelectedColumn();
            switch (actionKey) {
                case JCalendarPanel.ACTION_ENTER_KEY -> {
                    confirmSelection();
                    isShiftKeyPressed = false;
                    startDateSelected = null;
                }
                case JCalendarPanel.ACTION_ESCAPE_KEY -> {
                    if (e.getSource() instanceof JTable) {
                        prevMonth.requestFocusInWindow();
                    }
                    isShiftKeyPressed = false;
                    startDateSelected = null;
                }
                case JCalendarPanel.ACTION_UP_ARROW_KEY -> {
                    isShiftKeyPressed = false;
                    navigate(row - 1, col);
                    setFirstSelectedDate(false);
                }
                case JCalendarPanel.ACTION_DOWN_ARROW_KEY -> {
                    isShiftKeyPressed = false;
                    navigate(row + 1, col);
                    setFirstSelectedDate(false);
                }
                case JCalendarPanel.ACTION_LEFT_ARROW_KEY -> {
                    isShiftKeyPressed = false;
                    navigate(row, col - 1);
                    setFirstSelectedDate(false);
                }
                case JCalendarPanel.ACTION_RIGHT_ARROW_KEY -> {
                    isShiftKeyPressed = false;
                    navigate(row, col + 1);
                    setFirstSelectedDate(false);
                }
                case JCalendarPanel.ACTION_SHIFT_LEFT_ARROW_KEY -> {
                    isShiftKeyPressed = true;
                    if (startDateSelected == null) {
                        setFirstSelectedDate(false);
                    } else {
                        navigate(row, col - 1);
                        keepMoving();
                    }
                }
                case JCalendarPanel.ACTION_SHIFT_RIGHT_ARROW_KEY -> {
                    isShiftKeyPressed = true;
                    if (startDateSelected == null) {
                        setFirstSelectedDate(false);
                    } else {
                        navigate(row, col + 1);
                        keepMoving();
                    }
                }
                case JCalendarPanel.ACTION_SHIFT_UP_ARROW_KEY -> {
                    isShiftKeyPressed = true;
                    if (startDateSelected == null) {
                        setFirstSelectedDate(false);
                    } else {
                        navigate(row - 1, col);
                        keepMoving();
                    }
                }
                case JCalendarPanel.ACTION_SHIFT_DOWN_ARROW_KEY -> {
                    isShiftKeyPressed = true;
                    if (startDateSelected == null) {
                        setFirstSelectedDate(false);
                    } else {
                        navigate(row + 1, col);
                        keepMoving();
                    }
                }
                default -> System.out.println("Unknow key combinations");
            }
        }

        void setFirstSelectedDate(boolean commitSelection) {
            if (calendarPanel.isEnabled()) {
                int row = calendarTable.getSelectedRow();
                int col = calendarTable.getSelectedColumn();
                if (calendarPanel.isWeekNumberEnabled() && row == 0) {
                    return;
                }
                Object value = calendarTable.getValueAt(row, col);
                Calendar cal = (Calendar) calendar.clone();
                LocalDate selectedDate;
                Integer day = (Integer) value;
                selectedDate = getLocalDateFromValue(day, cal);
                if (calendarPanel.isWithinSelectableDateRange(selectedDate)) {
                    startDateSelected = selectedDate;
                    calendarPanel.getDateSelectionModel().setSelectedDate(startDateSelected,
                            commitSelection);
                    calendarTable.repaint();
                }
            }
        }

        void keepMoving() {
            if (calendarPanel.isEnabled() && isShiftKeyPressed) {
                if (calendarPanel.getDateSelectionModel().getDateSelectionMode()
                        == DateSelectionModel.SelectionMode.RANGE_SELECTION) {
                    int[] rows = calendarTable.getSelectedRows();
                    int[] cols = calendarTable.getSelectedColumns();
                    Object value = calendarTable.getValueAt(rows[rows.length - 1],
                            cols[cols.length - 1]);

                    Calendar cal = (Calendar) calendar.clone();
                    LocalDate curSelectedDate;
                    Integer day = (Integer) value;

                    curSelectedDate = getLocalDateFromValue(day, cal);
                    if (calendarPanel.isWithinSelectableDateRange(curSelectedDate)
                            && (startDateSelected.isBefore(curSelectedDate)
                            || startDateSelected.isEqual(curSelectedDate))) {
                        calendarPanel.getDateSelectionModel().setSelectedDates(startDateSelected,
                                curSelectedDate, false);
                        calendarTable.repaint();
                    }
                }
            }
        }

        private void confirmSelection() {
            if (!calendarPanel.isEnabled()) {
                return;
            }
            if (startDateSelected == null) {
                setFirstSelectedDate(true);
            } else {
                if (calendarPanel.getDateSelectionModel().getDateSelectionMode()
                        == DateSelectionModel.SelectionMode.SINGLE_SELECTION) {
                    calendar.set(startDateSelected.getYear(),
                            startDateSelected.getMonthValue() - 1,
                            startDateSelected.getDayOfMonth());
                    calendarPanel.getDateSelectionModel().setSelectedDate(startDateSelected, false);
                    calendarPanel.getDateSelectionModel().setCalendar(calendar);
                    updateCalendar();
                } else if (calendarPanel.getDateSelectionModel().getDateSelectionMode()
                        == DateSelectionModel.SelectionMode.RANGE_SELECTION) {
                    int[] rows = calendarTable.getSelectedRows();
                    int[] cols = calendarTable.getSelectedColumns();
                    Object value = calendarTable.getValueAt(rows[rows.length - 1], cols[cols.length - 1]);
                    Calendar cal = (Calendar) calendarPanel.getDateSelectionModel().getCalendar().clone();
                    LocalDate lastSelectedDate;
                    Integer day = (Integer) value;
                    lastSelectedDate = getLocalDateFromValue(day, cal);
                    if (calendarPanel.isWithinSelectableDateRange(lastSelectedDate)) {
                        calendar.set(startDateSelected.getYear(),
                                startDateSelected.getMonthValue() - 1,
                                startDateSelected.getDayOfMonth());
                        calendarPanel.getDateSelectionModel().setSelectedDates(startDateSelected,
                                lastSelectedDate, true);
                        calendarPanel.getDateSelectionModel().setCalendar(calendar);
                        updateCalendar();
                    }
                }
            }
            isShiftKeyPressed = false;
            startDateSelected = null;
        }

        private void navigate(int r, int c) {
            if (c < 0) {
                c = calendarTable.getColumnCount() - 1;
                if (r > 0) {
                    r--;
                } else {
                    r = calendarTable.getRowCount() - 1;
                    calendar.add(Calendar.MONTH, -1);
                    calendarPanel.getDateSelectionModel().setCalendar(calendar);
                    updateCalendar();
                    calendarTable.changeSelection(calendarTable.getRowCount() - 2, c, false, false);
                    calendarPanel.getDateSelectionModel().setEventType(DateSelectionModel.EventType.MONTH_NAVIGATION);
                }
            } else if (c > calendarTable.getColumnCount() - 1) {
                c = 0;
                if (r < calendarTable.getRowCount()) {
                    r++;
                } else {
                    r = 0;
                    calendar.add(Calendar.MONTH, 1);
                    calendarPanel.getDateSelectionModel().setCalendar(calendar);
                    updateCalendar();
                    calendarTable.changeSelection(1, c, false, false);
                    calendarPanel.getDateSelectionModel().setEventType(DateSelectionModel.EventType.MONTH_NAVIGATION);
                }
            }
            if (r < 0) {
                r = calendarTable.getRowCount() - 1;
                calendar.add(Calendar.MONTH, -1);
                calendarPanel.getDateSelectionModel().setCalendar(calendar);
                updateCalendar();
                calendarTable.changeSelection(calendarTable.getRowCount() - 2, c, false, false);
                calendarPanel.getDateSelectionModel().setEventType(DateSelectionModel.EventType.MONTH_NAVIGATION);
            }
            if (r > calendarTable.getRowCount() - 1) {
                r = 0;
                calendar.add(Calendar.MONTH, 1);
                calendarPanel.getDateSelectionModel().setCalendar(calendar);
                updateCalendar();
                calendarTable.changeSelection(1, c, false, false);
                calendarPanel.getDateSelectionModel().setEventType(DateSelectionModel.EventType.MONTH_NAVIGATION);
            }
            calendarTable.changeSelection(r, c, false, false);
        }
    }

    private static class CustomFocusListener {
        public static void setFocusListener(JComponent component) {
            component.setFocusable(true);
            component.addFocusListener(new java.awt.event.FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    component.setBorder(BorderFactory.createCompoundBorder(
                            new LineBorder(Color.GRAY, 1, true),
                            new EmptyBorder(4, 8, 4, 8)));
                }

                @Override
                public void focusLost(FocusEvent e) {
                    component.setBorder(BorderFactory.createEmptyBorder());
                }
            });
        }
    }

    private class PreviousMonthAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (validateNavigation(DateSelectionModel.EventType.MONTH_NAVIGATION,
                    DateSelectionModel.NavigationType.BACKWARD)) {
                calendar.add(Calendar.MONTH, -1);
                calendarPanel.getDateSelectionModel().setCalendar(calendar);
                updateCalendar();
                calendarPanel.getDateSelectionModel().setEventType(DateSelectionModel.EventType.MONTH_NAVIGATION);
            }
        }
    }

    private class NextMonthAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (validateNavigation(DateSelectionModel.EventType.MONTH_NAVIGATION,
                    DateSelectionModel.NavigationType.FORWARD)) {
                calendar.add(Calendar.MONTH, 1);
                calendarPanel.getDateSelectionModel().setCalendar(calendar);
                updateCalendar();
                calendarPanel.getDateSelectionModel().setEventType(
                        DateSelectionModel.EventType.MONTH_NAVIGATION);
            }
        }
    }

    private class PreviousYearAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (validateNavigation(DateSelectionModel.EventType.YEAR_NAVIGATION,
                    DateSelectionModel.NavigationType.BACKWARD)) {
                calendar.add(Calendar.YEAR, -1);
                calendarPanel.getDateSelectionModel().setCalendar(calendar);
                updateCalendar();
                calendarPanel.getDateSelectionModel().setEventType(
                        DateSelectionModel.EventType.YEAR_NAVIGATION);
            }
        }
    }

    private class NextYearAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (validateNavigation(DateSelectionModel.EventType.YEAR_NAVIGATION,
                    DateSelectionModel.NavigationType.FORWARD)) {
                calendar.add(Calendar.YEAR, 1);
                calendarPanel.getDateSelectionModel().setCalendar(calendar);
                updateCalendar();
                calendarPanel.getDateSelectionModel().setEventType(
                        DateSelectionModel.EventType.YEAR_NAVIGATION);
            }
        }
    }

    private class MonthLabelAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            AbstractCalendarPanel defaultMonthSelection
                    = calendarPanel.getSelectionPanel(DateSelectionModel.CalendarPanelType.MONTH_SELECTION);
            calendarPanel.setCalendarPanel(defaultMonthSelection);
        }
    }

    private class YearLabelAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            AbstractCalendarPanel defaultYearSelection
                    = calendarPanel.getSelectionPanel(DateSelectionModel.CalendarPanelType.YEAR_SELECTION);
            calendarPanel.setCalendarPanel(defaultYearSelection);
        }
    }

    @Override
    protected Dimension getCellDimension() {
        Dimension dimension = super.getCellDimension();
        return new Dimension(dimension.width, dimension.height);
    }
}
