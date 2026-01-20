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

package javax.swing.plaf.basic;

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;

import javax.swing.AbstractCalendarPanel;
import javax.swing.DateSelectionModel;
import javax.swing.InputMap;
import javax.swing.JCalendarPanel;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.CalendarPanelUI;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;

import jdk.internal.javac.PreviewFeature;
import sun.swing.DefaultLookup;
import sun.swing.calendarpanel.CalendarPanelComponentFactory;

/**
 * Provides the basic look and feel for a JCalendarPanel.
 *
 * @since 99
 */

@PreviewFeature(feature = PreviewFeature.Feature.JDATEPICKER)
@SuppressWarnings("serial")
public class BasicCalendarPanelUI extends CalendarPanelUI {
    // Preferred and Minimum sizes for the dialog box
    private AbstractCalendarPanel abstractCalendarPanel;
    private BasicCalendarPanelUI.Handler handler;

    /**
     * The instance of {@code JCalendarPanel}
     */
    protected JCalendarPanel calendarPanel;

    /**
     * The instance of {@code ChangeListener}.
     */
    protected ChangeListener previewListener;

    /**
     * The instance of {@code PropertyChangeListener}.
     */
    protected PropertyChangeListener propertyChangeListener;

    /**
     * Constructs a {@code BasicCalendarPanelUI}.
     */
    public BasicCalendarPanelUI() {
    }

    /**
     * Returns a new instance of {@code BasicCalendarPanelUI}.
     *
     * @param c component
     *
     * @return a new instance of {@code BasicCalendarPanelUI}.
     */
    public static ComponentUI createUI(JComponent c) {
        return new BasicCalendarPanelUI();
    }

    /**
     * Install {@code BasicCalendarPanelUI} UI component
     * @param c component
     *
     */
    @Override
    public void installUI(JComponent c) {
        this.calendarPanel = (JCalendarPanel) c;
        super.installUI(c);
        installDefaults();
        installListeners();
        for (Map.Entry<DateSelectionModel.CalendarPanelType, AbstractCalendarPanel> panel :
                CalendarPanelComponentFactory.getDefaultCalendarPanel().entrySet()) {
            calendarPanel.setSelectionPanel(panel.getKey(), panel.getValue());
        }

        abstractCalendarPanel = calendarPanel.getSelectionPanel(
                DateSelectionModel.CalendarPanelType.DAY_SELECTION);
        this.calendarPanel.setLayout(new BorderLayout());
        this.calendarPanel.setCalendarPanel(abstractCalendarPanel);
        this.calendarPanel.applyComponentOrientation(c.getComponentOrientation());
    }

    /**
     * Uninstall {@code BasicCalendarPanelUI} UI component
     * @param c component
     *
     */
    public void uninstallUI(JComponent c) {
        uninstallListeners();
        uninstallDefaults();
        if (calendarPanel.getSelectionPanel(
                DateSelectionModel.CalendarPanelType.DAY_SELECTION) != null) {
            calendarPanel.getSelectionPanel(
                    DateSelectionModel.CalendarPanelType.DAY_SELECTION).removeAll();
        }
        if (calendarPanel.getSelectionPanel(
                DateSelectionModel.CalendarPanelType.MONTH_SELECTION) != null) {
            calendarPanel.getSelectionPanel(
                    DateSelectionModel.CalendarPanelType.MONTH_SELECTION).removeAll();
        }
        if (calendarPanel.getSelectionPanel(
                DateSelectionModel.CalendarPanelType.YEAR_SELECTION) != null) {
            calendarPanel.getSelectionPanel(
                    DateSelectionModel.CalendarPanelType.YEAR_SELECTION).removeAll();
        }
        abstractCalendarPanel = null;
        calendarPanel = null;
        handler = null;
        c.removeAll();
    }

    /**
     * Update {@code BasicCalendarPanelUI} UI component
     * @param g graphics
     * @param c component
     *
     */

    @Override
    public void update(Graphics g, JComponent c) {
        paint(g, c);
        paintBackground(g);
    }

    /**
     * Paints background {@code BasicCalendarPanelUI} UI component
     * @param g Graphics
     *
     */
    protected void paintBackground(Graphics g) {
        if (calendarPanel.isOpaque()) {
            g.setColor(calendarPanel.getBackground());
            g.fillRect(0, 0, calendarPanel.getWidth(),
                    calendarPanel.getHeight());
        }
    }

    /**
     * Installs default properties.
     */
    protected void installDefaults() {
        LookAndFeel.installColorsAndFont(calendarPanel,
                "DatePicker.tableBackground",
                "DatePicker.tableForeground",
                "DatePicker.tableFont");
        LookAndFeel.installProperty(calendarPanel, "opaque", Boolean.TRUE);
        calendarPanel.setTableGridColor(UIManager.getColor("DatePicker.tableGridColor"));
        calendarPanel.setTableShowGridStatus(UIManager.getBoolean("DatePicker.tableShowGrid"));
        calendarPanel.setTableFont(UIManager.getFont("DatePicker.tableFont"));
        calendarPanel.setTableForeground(UIManager.getColor("DatePicker.tableForeground"));
        calendarPanel.setTableBackground(UIManager.getColor("DatePicker.tableBackground"));
        calendarPanel.setTableSelectionForeground(UIManager.getColor("DatePicker.tableSelectionForeground"));
        calendarPanel.setTableSelectionBackground(UIManager.getColor("DatePicker.tableSelectionBackground"));
        calendarPanel.setTableCurrentDateForeground(UIManager.getColor("DatePicker.tableCurrentDateForeground"));
        calendarPanel.setTableCurrentDateBackground(UIManager.getColor("DatePicker.tableCurrentDateBackground"));
        calendarPanel.setTableHeaderCellFont(UIManager.getFont("DatePicker.tableHeaderCellFont"));
        calendarPanel.setTableHeaderForeground(UIManager.getColor("DatePicker.tableHeaderForeground"));
        calendarPanel.setTableHeaderBackground(UIManager.getColor("DatePicker.tableHeaderBackground"));
        calendarPanel.setTableWeekNumberForeground(UIManager.getColor("DatePicker.weekNumberForeground"));
    }

    /**
     * Uninstalls default properties.
     */
    protected void uninstallDefaults() {
        if (calendarPanel.getTransferHandler() instanceof UIResource) {
            calendarPanel.setTransferHandler(null);
        }
    }

    /**
     * Registers listeners.
     */
    protected void installListeners() {
        propertyChangeListener = createPropertyChangeListener();
        calendarPanel.addPropertyChangeListener(propertyChangeListener);

        previewListener = getHandler();
        calendarPanel.getDateSelectionModel().addChangeListener(previewListener);
        InputMap inputMap = getInputMap(JComponent.WHEN_FOCUSED);
        SwingUtilities.replaceUIInputMap(calendarPanel, JComponent.
                WHEN_FOCUSED, inputMap);
    }

    InputMap getInputMap(int condition) {
        if (condition == JComponent.WHEN_FOCUSED) {
            return (InputMap) DefaultLookup.get(calendarPanel, this,
                    "DatePicker.ancestorInputMap.calendarPanel");
        }
        return null;
    }

    private BasicCalendarPanelUI.Handler getHandler() {
        if (handler == null) {
            handler = new BasicCalendarPanelUI.Handler();
        }
        return handler;
    }

    /**
     * Returns an instance of {@code PropertyChangeListener}.
     *
     * @return a property change listener
     */
    protected PropertyChangeListener createPropertyChangeListener() {
        return getHandler();
    }

    /**
     * Unregisters listeners.
     */
    protected void uninstallListeners() {
        calendarPanel.removePropertyChangeListener(propertyChangeListener);
        calendarPanel.getDateSelectionModel().removeChangeListener(previewListener);
        previewListener = null;
    }

    private void selectionChanged(DateSelectionModel model) {
        abstractCalendarPanel.updateCalendar();
    }

    private class Handler implements MouseListener, ChangeListener,
            PropertyChangeListener {

        public void stateChanged(ChangeEvent evt) {
            selectionChanged((DateSelectionModel) evt.getSource());
        }

        public void mousePressed(MouseEvent evt) {
        }

        public void mouseReleased(MouseEvent evt) {
        }

        public void mouseClicked(MouseEvent evt) {
        }

        public void mouseEntered(MouseEvent evt) {
        }

        public void mouseExited(MouseEvent evt) {
        }

        public void propertyChange(PropertyChangeEvent evt) {
            String prop = evt.getPropertyName();
            if (Objects.equals(prop, JCalendarPanel.CALENDAR_PANEL_PROPERTY)) {
                AbstractCalendarPanel oldPanel =
                        (AbstractCalendarPanel) evt.getOldValue();
                AbstractCalendarPanel newPanel =
                        (AbstractCalendarPanel) evt.getNewValue();
                if (newPanel != null) {
                    calendarPanel.remove(oldPanel);
                    calendarPanel.setLayout(new BorderLayout());
                    calendarPanel.add(newPanel, BorderLayout.CENTER);
                    newPanel.updateCalendar();
                    calendarPanel.revalidate();
                    calendarPanel.repaint();
                    calendarPanel.doLayout();
                }
            } else if (Objects.equals(prop, "componentOrientation")) {
                ComponentOrientation o =
                        (ComponentOrientation) evt.getNewValue();
                JCalendarPanel cc = (JCalendarPanel) evt.getSource();
                if (o != (ComponentOrientation) evt.getOldValue()) {
                    cc.applyComponentOrientation(o);
                    cc.updateUI();
                }
            } else if (Objects.equals(prop, JCalendarPanel.DATE_SELECTION_MODEL_PROPERTY)) {
                DateSelectionModel oldModel = (DateSelectionModel) evt.getOldValue();
                oldModel.removeChangeListener(previewListener);
                DateSelectionModel newModel = (DateSelectionModel) evt.getNewValue();
                newModel.addChangeListener(previewListener);
                calendarPanel.setDateSelectionModel(newModel);
            } else if (Objects.equals(prop, JCalendarPanel.FIRST_DAY_OF_WEEK_PROPERTY) ||
                    Objects.equals(prop, JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY)) {
                abstractCalendarPanel.updateCalendar();
            } else if (Objects.equals(prop, JCalendarPanel.SET_DATE_PROPERTY)) {
                Calendar cal = (Calendar) calendarPanel.getDateSelectionModel().
                        getCalendar().clone();
                if (calendarPanel.getDateSelectionMode()
                        == DateSelectionModel.SelectionMode.SINGLE_SELECTION) {
                    LocalDate setDate = calendarPanel.getDate();
                    cal.set(setDate.getYear(), setDate.getMonthValue() - 1,
                            setDate.getDayOfMonth());
                } else if (calendarPanel.getDateSelectionMode()
                        == DateSelectionModel.SelectionMode.RANGE_SELECTION) {
                    SortedSet<LocalDate> setDates = calendarPanel.getDates();
                    if (setDates == null) {
                        LocalDate setDate = calendarPanel.getDate();
                        cal.set(setDate.getYear(), setDate.getMonthValue() - 1,
                                setDate.getDayOfMonth());
                    } else if (!setDates.isEmpty()) {
                        LocalDate setDate = setDates.first();
                        cal.set(setDate.getYear(), setDate.getMonthValue() - 1,
                                setDate.getDayOfMonth());
                    }
                }
                calendarPanel.getDateSelectionModel().setCalendar(cal);
                calendarPanel.setCalendarPanel(calendarPanel.getSelectionPanel(
                        DateSelectionModel.CalendarPanelType.DAY_SELECTION));
            } else {
                abstractCalendarPanel.updateCalendar();
            }
        }
    }
}
