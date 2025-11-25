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

package javax.swing;

import jdk.internal.javac.PreviewFeature;

import java.awt.Dimension;
import java.beans.PropertyChangeListener;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * This is the abstract superclass for calendar panel.
 * To add a custom calendar panel into a {@code JCalendarPanel}, subclass this class.
 *
 * @since 99
 */

@PreviewFeature(feature = PreviewFeature.Feature.JDATEPICKER)
@SuppressWarnings("serial")
public abstract class AbstractCalendarPanel extends JPanel {
    static final int CELL_WIDTH = 50;
    static final int CELL_HEIGHT = 30;

    /**
     * The instance of {@code PropertyChangeListener}.
     */
    private final PropertyChangeListener enabledListener = event -> {
        Object value = event.getNewValue();
        if (value instanceof Boolean) {
            setEnabled((Boolean) value);
        }
    };

    /**
     * The instance of {@code JCalendarPanel}
     */
    protected JCalendarPanel calendarPanel;

    /**
     * Creates an abstract calendar panel
     */
    protected AbstractCalendarPanel() {
    }

    /**
     * Invoked automatically when the model's state changes.
     * It is also called by {@code installCalendarPanel} to allow
     * you to set up the initial state of your Calendar.
     * Override this method to update your {@code installCalendarPanel}
     */
    public abstract void updateCalendar();

    /**
     * Builds a new calendar panel.
     */
    protected abstract void buildCalendar();

    /**
     * Set custom cell renderer
     *
     * @param cellRenderer custom cell renderer
     */
    public abstract void setDefaultTableCellRenderer(DefaultTableCellRenderer cellRenderer);

    /**
     * Invoked when the panel is added to the Calendar Pane.
     * If you override this, be sure to call <code>super</code>.
     *
     * @param setCalendarPanel the calendar to which the panel is to be added
     * @throws RuntimeException if the calendar panel has already been
     *                          installed
     */
    public void installCalendarPanel(JCalendarPanel setCalendarPanel) {
        if (calendarPanel == null) {
            calendarPanel = setCalendarPanel;
            calendarPanel.addPropertyChangeListener("enabled", enabledListener);
            setEnabled(calendarPanel.isEnabled());
            buildCalendar();
        } else {
            updateCalendar();
        }
    }

    /**
     * Invoked when the panel is removed from the Calendar Pane.
     * If override this, be sure to call <code>super</code>.
     *
     * @param setCalendarPanel the calendar pane from which the panel is to be removed
     */
    public void uninstallCalendarPanel(JCalendarPanel setCalendarPanel) {
        calendarPanel.removePropertyChangeListener("enabled", enabledListener);
        calendarPanel = null;
    }

    /**
     * Returns the model that the calendar panel is editing.
     *
     * @return the {@code DateSelectionModel} this panel
     * is editing
     */
    public DateSelectionModel getDateSelectionModel() {
        return (this.calendarPanel != null)
                ? this.calendarPanel.getDateSelectionModel()
                : null;
    }

    /**
     * Returns date cell dimension
     *
     * @return cell dimension
     */
    protected Dimension getCellDimension() {
        return new Dimension(CELL_WIDTH, CELL_HEIGHT);
    }

    /**
     * Returns a string containing the display name of the panel.
     *
     * @return the name of the display panel
     */
    public abstract String getDisplayName();
}
