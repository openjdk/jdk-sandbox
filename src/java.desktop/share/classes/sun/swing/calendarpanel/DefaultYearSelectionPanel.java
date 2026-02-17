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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.io.Serializable;
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
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * DefaultYearSelectionPanel creates a panel for Year selection.
 *
 * @since 99
 */

@SuppressWarnings("serial")
public final class DefaultYearSelectionPanel extends AbstractCalendarPanel {
    private static final int HEADER_PANEL_HEIGHT = 40;
    private int startYear;
    private Calendar calendar;
    private DefaultTableCellRenderer defaultTableCellRenderer;
    private JButton backButton;
    private JLabel selectYearLabel;
    private JTable calendarTable;
    private String selectYearText = null;
    private String backButtonText = null;
    private YearTableViewModel tableModel;

    /**
     * Creates a {@code DefaultYearSelectionPanel} instance
     */
    public DefaultYearSelectionPanel() {
    }

    @Override
    public void updateCalendar() {
        calendar = (Calendar) calendarPanel.getDateSelectionModel().getCalendar().clone();
        startYear = Calendar.getInstance().get(Calendar.YEAR);
        tableModel.updateYearsData(startYear);
        setTableScrollPosition();
        setupYearSelection();
        calendarTable.repaint();
        requestFocusInWindow();
    }

    @Override
    public void buildCalendar() {
        Dimension buttonSize = new Dimension(50, HEADER_PANEL_HEIGHT - 10);
        installStrings();
        DefaultTableCellRenderer cellRenderer = new CalendarYearCellRenderer();
        cellRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        setDefaultTableCellRenderer(cellRenderer);
        calendar = (Calendar) calendarPanel.getDateSelectionModel().getCalendar().clone();

        setLayout(new BorderLayout());
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        headerPanel.setPreferredSize(new Dimension((int) super.getPreferredSize().getWidth(), HEADER_PANEL_HEIGHT));
        headerPanel.setFocusTraversalKeysEnabled(true);

        backButton = new JButton(backButtonText);
        backButton.setName(backButtonText);
        backButton.setBorder(null);
        backButton.setPreferredSize(buttonSize);
        backButton.addActionListener(new BackButtonAction());

        selectYearLabel = new JLabel(selectYearText, SwingConstants.CENTER);

        headerPanel.add(backButton, BorderLayout.WEST);
        headerPanel.add(selectYearLabel, BorderLayout.CENTER);
        startYear = calendar.get(Calendar.YEAR);
        int selectionLimitValue = calendarPanel.getYearSelectionLimit();
        tableModel = new YearTableViewModel(Calendar.getInstance().get(Calendar.YEAR), selectionLimitValue);
        calendarTable = new JTable(tableModel);
        calendarTable.setColumnSelectionAllowed(true);
        calendarTable.setTableHeader(null);
        calendarTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setupYearSelection();
        calendarTable.addMouseListener(new YearSelectionMouseListener());

        add(headerPanel, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(calendarTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
        scrollPane.setPreferredSize(new Dimension((int) getCellDimension().getWidth() * tableModel.getColumnCount(), (int) getCellDimension().getHeight() * 7));
        installKeyboardActions();
        installFocusListeners();
        updateCalendar();
    }

    private void setTableScrollPosition() {
        for (int row = 0; row < calendarTable.getRowCount(); row++) {
            for (int col = 0; col < calendarTable.getColumnCount(); col++) {
                Object value = calendarTable.getValueAt(row, col);
                if (calendar.get(Calendar.YEAR) == (int) value) {
                    Rectangle rect = calendarTable.getCellRect(row, col, true);
                    calendarTable.changeSelection(row, col, false, false);
                    calendarTable.scrollRectToVisible(rect);
                    return;
                }
            }
        }
    }

    private void setupYearSelection() {
        int selectionLimitValue = calendarPanel.getYearSelectionLimit();
        calendarTable.setModel(new YearTableViewModel(Calendar.getInstance().get(Calendar.YEAR), selectionLimitValue));
        calendarTable.setRowHeight((int) getCellDimension().getHeight());
        setLabelAttributes();
        if (calendarPanel.getTableShowGridStatus()) {
            calendarTable.setShowGrid(true);
            calendarTable.setGridColor(calendarPanel.getTableGridColor());
        } else {
            calendarTable.setShowGrid(false);
        }

        DefaultTableCellRenderer tableCellRenderer = getDefaultTableCellRenderer();

        for (int i = 0; i < calendarTable.getColumnCount(); i++) {
            calendarTable.getColumnModel().getColumn(i).setCellRenderer(tableCellRenderer);
        }
    }

    private void  setLabelAttributes() {
        selectYearLabel.setForeground(calendarPanel.getTableForeground());
        selectYearLabel.setFont(calendarPanel.getTableFont());
        selectYearLabel.setLocale(calendarPanel.getDateSelectionModel().getLocale());
    }

    private class YearSelectionMouseListener extends MouseAdapter implements Serializable {
        public void mousePressed(MouseEvent e) {
            confirmSelection();
        }
    }

    private void confirmSelection() {
        if (calendarPanel.isEnabled()) {
            int row = calendarTable.getSelectedRow();
            int col = calendarTable.getSelectedColumn();
            if (row >= 0) {
                Object value = calendarTable.getValueAt(row, col);
                if (value != null) {
                    calendar.set(Integer.parseInt(String.valueOf(value)), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
                    calendarPanel.getDateSelectionModel().setCalendar(calendar);
                    calendarPanel.setCalendarPanel(calendarPanel.getSelectionPanel(DateSelectionModel.CalendarPanelType.DAY_SELECTION));
                }
            }
        }
    }

    /**
     * installKeyboardActions
     */
    private void installKeyboardActions() {
        InputMap inputMap = calendarPanel.getInputMap(JComponent.WHEN_FOCUSED);
        SwingUtilities.replaceUIInputMap(calendarTable, JComponent.
                WHEN_FOCUSED, inputMap);
        SwingUtilities.replaceUIInputMap(backButton, JComponent.
                WHEN_FOCUSED, inputMap);
        ActionMap tableActionMap = calendarTable.getActionMap();
        ActionMap backbuttonActionMap = backButton.getActionMap();

        tableActionMap.put("acceptSelection",
                new TableKeyBoardAction(JCalendarPanel.ACTION_ENTER_KEY));
        tableActionMap.put("cancelSelection",
                new TableKeyBoardAction(JCalendarPanel.ACTION_ESCAPE_KEY));

        tableActionMap.put("navigateUp",
                new TableKeyBoardAction(JCalendarPanel.ACTION_UP_ARROW_KEY));
        tableActionMap.put("navigateDown",
                new TableKeyBoardAction(JCalendarPanel.ACTION_DOWN_ARROW_KEY));
        tableActionMap.put("navigateLeft",
                new TableKeyBoardAction(JCalendarPanel.ACTION_LEFT_ARROW_KEY));
        tableActionMap.put("navigateRight",
                new TableKeyBoardAction(JCalendarPanel.ACTION_RIGHT_ARROW_KEY));

        backbuttonActionMap.put("acceptSelection", new BackButtonAction());
    }

    /**
     * uninstallKeyboardActions
     */
    private void uninstallKeyboardActions() {
        SwingUtilities.replaceUIActionMap(calendarTable, null);
        SwingUtilities.replaceUIActionMap(backButton, null);
        SwingUtilities.replaceUIInputMap(calendarTable, JComponent.
                WHEN_FOCUSED, null);
        SwingUtilities.replaceUIInputMap(backButton, JComponent.
                WHEN_FOCUSED, null);
    }

    @Override
    public void uninstallCalendarPanel(JCalendarPanel setCalendarPanel) {
        super.uninstallCalendarPanel(setCalendarPanel);
        uninstallKeyboardActions();
        removeAll();
    }

    private void installFocusListeners() {
        new CustomFocusListener(backButton);
        new CustomFocusListener(calendarTable);
    }

    private DefaultTableCellRenderer getDefaultTableCellRenderer() {
        return defaultTableCellRenderer;
    }

    @Override
    public void setDefaultTableCellRenderer(DefaultTableCellRenderer cellRenderer) {
        defaultTableCellRenderer = cellRenderer;
    }

    public void paintComponent(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private class CalendarYearCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
            if (value == null) {
                return super.getTableCellRendererComponent(table,
                        value, isSelected, hasFocus, row, column);
            }

            JLabel label = (JLabel) super.getTableCellRendererComponent(table,
                    value, isSelected, hasFocus, row, column);
            label.setHorizontalAlignment(JLabel.CENTER);
            label.setFont(calendarPanel.getTableFont());

            if (label.getText().equals(String.valueOf(getCurrentYear())) && isSelected) {
                label.setForeground(calendarPanel.getTableSelectionForeground());
                label.setBackground(calendarPanel.getTableSelectionBackground());
            } else if (label.getText().equals(String.valueOf(getCurrentYear()))) {
                label.setForeground(calendarPanel.getTableCurrentDateForeground());
                label.setBackground(calendarPanel.getTableCurrentDateBackground().darker());
            } else if (isSelected) {
                label.setBackground(calendarPanel.getTableSelectionBackground());
                label.setForeground(calendarPanel.getTableSelectionForeground());
            } else {
                label.setBackground(calendarPanel.getTableBackground());
                label.setForeground(calendarPanel.getTableForeground());
            }
            return label;
        }
    }

    private int getCurrentYear() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR);
    }

    private class CustomFocusListener {
        public CustomFocusListener(JComponent component) {
            component.setFocusable(true);
            component.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    if (component instanceof JButton) {
                        component.setBorder(BorderFactory.createCompoundBorder(
                                new LineBorder(Color.GRAY, 1, true),
                                new EmptyBorder(4, 8, 4, 8)));
                    }
                    if (component instanceof JTable) {
                        setTableScrollPosition();
                    }
                }

                @Override
                public void focusLost(FocusEvent e) {
                    if (component instanceof JButton) {
                        component.setBorder(BorderFactory.createEmptyBorder());
                    }
                }
            });
        }
    }

    private void installStrings() {
        Locale l = calendarPanel.getDateSelectionModel().getLocale();
        selectYearText = UIManager.getString("CalendarPanel.YearSelectionText", l);
        backButtonText = UIManager.getString("CalendarPanel.BackButtonText", l);
    }

    @Override
    public String getDisplayName() {
        return UIManager.getString("CalendarPanel.DefaultTableNameText", getLocale());
    }

    /**
     * Navigation Action class
     */
    private class TableKeyBoardAction extends AbstractAction {
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
                case JCalendarPanel.ACTION_ENTER_KEY -> confirmSelection();
                case JCalendarPanel.ACTION_ESCAPE_KEY -> {
                    calendarTable.clearSelection();
                    backButton.requestFocusInWindow();
                }
                case JCalendarPanel.ACTION_UP_ARROW_KEY -> navigate(row - 1, col);
                case JCalendarPanel.ACTION_DOWN_ARROW_KEY -> navigate(row + 1, col);
                case JCalendarPanel.ACTION_LEFT_ARROW_KEY -> navigate(row, col - 1);
                case JCalendarPanel.ACTION_RIGHT_ARROW_KEY -> navigate(row, col + 1);

                default -> System.out.println("Unknow key combinations");
            }
        }

        private void navigate(int r, int c) {
            if (c < 0) {
                c = calendarTable.getColumnCount() - 1;
                if (r > 0) {
                    r--;
                } else {
                    c = 0;
                    r = 0;
                }
            } else if (c > calendarTable.getColumnCount() - 1) {
                c = 0;
                if (r < calendarTable.getRowCount() - 1) {
                    r++;
                } else {
                    r = calendarTable.getRowCount() - 1;
                    c = calendarTable.getColumnCount() - 1;
                }
            }
            if (r >= calendarTable.getRowCount() - 1) {
                r = calendarTable.getRowCount() - 1;
            }
            calendarTable.changeSelection(r, c, false, false);
        }
    }

    class BackButtonAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            calendarPanel.setCalendarPanel(calendarPanel.getSelectionPanel(
                    DateSelectionModel.CalendarPanelType.DAY_SELECTION));
        }
    }

    @Override
    protected Dimension getCellDimension() {
        Dimension dimension = super.getCellDimension();
        return new Dimension((int) (dimension.width * 1.8), dimension.height);
    }
}
