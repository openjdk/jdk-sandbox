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

import java.awt.Color;
import java.awt.Font;
import java.beans.BeanProperty;
import java.beans.JavaBean;
import java.time.LocalDate;
import java.time.Month;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.CalendarPanelUI;
import javax.swing.table.DefaultTableCellRenderer;

import jdk.internal.javac.PreviewFeature;
import sun.swing.calendarpanel.CalendarPanelComponentFactory;
import sun.swing.calendarpanel.CalendarUtilities;
import sun.swing.calendarpanel.DefaultDateSelectionPanel;

/**
 * {@code JCalendarPanel} provides a pane of controls designed to allow
 * a user to select a date from Calendar.
 * <p>
 * (To-do - needs to be expanded)
 *
 * @since 99
 */

@PreviewFeature(feature = PreviewFeature.Feature.JDATEPICKER)
@JavaBean(defaultProperty = "UI",
        description = "A component that supports selecting a Date.")
@SwingContainer(false)
@SuppressWarnings("serial")
public class JCalendarPanel extends JComponent implements Accessible {
    private static final String uiClassID = "CalendarPanelUI";
    /**
     * The selection model property name.
     */
    public static final String DATE_SELECTION_MODEL_PROPERTY = "dateSelectionModel";
    /**
     * The panel property name.
     */
    public static final String CALENDAR_PANEL_PROPERTY = "calendarPanel";
    /**
     * The FirstDayOfWeek property name.
     */
    public static final String FIRST_DAY_OF_WEEK_PROPERTY = "FirstDayOfWeek";
    /**
     * Set Date property name.
     */
    public static final String SET_DATE_PROPERTY = "SetDate";
    /**
     * * Set Panel attributes property name.
     *
     */
    public static final String PANEL_ATTRIBUTES_PROPERTY = "PanelAttributes";
    /**
     * * Set Table font attribute property name.
     *
     */
    private static final int DEFAULT_LIMIT_VALUE = 100;
    /**
     * enter key action
     */
    public static final String ACTION_ENTER_KEY = "enter";
    /**
     * escape button action
     */
    public static final String ACTION_ESCAPE_KEY = "escape";
    /**
     * left key action
     */
    public static final String ACTION_LEFT_ARROW_KEY = "left";
    /**
     * right key action
     */
    public static final String ACTION_RIGHT_ARROW_KEY = "right";
    /**
     * left key action
     */
    public static final String ACTION_UP_ARROW_KEY = "up";
    /**
     * right key action
     */
    public static final String ACTION_DOWN_ARROW_KEY = "down";

    /**
     * left key action
     */
    public static final String ACTION_SHIFT_LEFT_ARROW_KEY = "shiftLeft";
    /**
     * right key action
     */
    public static final String ACTION_SHIFT_RIGHT_ARROW_KEY = "shiftRight";
    /**
     * left key action
     */
    public static final String ACTION_SHIFT_UP_ARROW_KEY = "shiftUp";
    /**
     * right key action
     */
    public static final String ACTION_SHIFT_DOWN_ARROW_KEY = "shiftDown";
    /** The grid font. */
    protected Font gridFont;
    /** The grid foreground color */
    protected Color gridForeground;
    /** The grid background color */
    protected Color gridBackground;
    /** The grid header cell font. */
    protected Font gridHeaderCellFont;
    /** The grid header foreground color */
    protected Color gridHeaderForeground;
    /** The grid header background color */
    protected Color gridHeaderBackground;
    /** The grid selection foreground color */
    protected Color gridSelectionForeground;
    /** The grid selection background color */
    protected Color gridSelectionBackground;
    /** The grid current date foreground color */
    protected Color gridCurrentDateForeground;
    /** The grid current date background color */
    protected Color gridCurrentDateBackground;
    /** The grid week number foreground color */
    protected Color gridWeekNumberForeground;
    /** The grid color */
    protected Color gridColor;
    /** The table grid visible state */
    protected boolean showGridLines;

    private final HashMap<DateSelectionModel.CalendarPanelType,
            AbstractCalendarPanel> calendarPanelHashMap = new HashMap<>();
    private AbstractCalendarPanel abstractCalendarPanel;
    private DateSelectionModel selectionModel;
    private SortedSet<LocalDate> selectableDateRange;
    private SortedSet<Integer> selectableYearRange;
    private boolean enableWeekNumber;

    /**
     * Creates a calendar pane with a month view of current date
     */
    public JCalendarPanel() {
        this(LocalDate.now(), Locale.getDefault());
    }

    /**
     * Creates a calendar pane with a month view of specified date
     * @param date initial date
     */
    public JCalendarPanel(LocalDate date) {
        this(date, Locale.getDefault());
    }

    /**
     * Creates a calendar pane with a month view of specified locale
     * @param locale set locale
     */
    public JCalendarPanel(Locale locale) {
        this(LocalDate.now(), locale);
    }

    /**
     * Creates a calendar pane with a month view of specified date and locale
     * @param date initial date
     * @param locale set locale
     */
    public JCalendarPanel(LocalDate date, Locale locale) {
        setLocale(locale);
        this.selectableDateRange = new TreeSet<>();
        this.selectableYearRange = new TreeSet<>();
        setDateSelectionModel(new DefaultDateSelectionModel(locale));
        Calendar calendar = Calendar.getInstance(getLocale());
        abstractCalendarPanel = CalendarPanelComponentFactory.
                getDefaultCalendarPanel().get(DateSelectionModel.
                        CalendarPanelType.DAY_SELECTION);
        calendar.setTime(CalendarUtilities.getDateFromLocalDate(date));
        getDateSelectionModel().setCalendar(calendar);
        setFirstDayOfWeek(calendar.getFirstDayOfWeek());
        setDate(date);
        setYearSelectionLimit(LocalDate.now().getYear() - DEFAULT_LIMIT_VALUE,
                LocalDate.now().getYear() + DEFAULT_LIMIT_VALUE);
        updateUI();
    }

    /**
     * Notification from the <code>UIManager</code> that the L&amp;F has changed.
     * Replaces the current UI object with the latest version from the
     * <code>UIManager</code>.
     *
     * @see JComponent#updateUI
     */
    public void updateUI() {
        setUI((CalendarPanelUI) UIManager.getUI(this));
    }

    /**
     * Returns the L&amp;F object that renders this component.
     *
     * @return the {@code CalendarPanelUI} object that renders
     * this component
     */
    @Override
    public CalendarPanelUI getUI() {
        return (CalendarPanelUI) ui;
    }

    /**
     * Returns the selected start and end date range.
     * @return selected dates
     */
    public SortedSet<LocalDate> getDates() {
        if (!selectionModel.getSelectedDates().isEmpty()) {
            SortedSet<LocalDate> dates = new TreeSet<>();
            dates.add(selectionModel.getSelectedDates().first());
            dates.add(selectionModel.getSelectedDates().last());
            return dates;
        }
        return selectionModel.getSelectedDates();
    }

    /**
     * Returns the selected date
     * @return selected date
     */
    public LocalDate getDate() {
        return selectionModel.getSelectedDate();
    }

    /**
     * Sets the L&amp;F object that renders this component.
     *
     * @param ui the {@code CalendarPanelUI} L&amp;F object
     * @see UIDefaults#getUI
     */
    public void setUI(final CalendarPanelUI ui) {
        super.setUI(ui);
    }


    /**
     * Returns the name of the L&amp;F class that renders this component.
     *
     * @return the string "CalendarPanelUI"
     * @see JComponent#getUIClassID
     * @see UIDefaults#getUI
     */
    @Override
    public String getUIClassID() {
        return uiClassID;
    }

    /**
     * Sets the selection date for {@code JCalendarPanel}. The set date
     * shall be highlighted in calendar panel.
     *
     * @param date date
     */
    public void setDate(LocalDate date) {
        if (isValidDate(date)) {
            getDateSelectionModel().getCalendar().setTime(
                    CalendarUtilities.getDateFromLocalDate(date));
            selectionModel.setSelectedDate(date, true);
            firePropertyChange(JCalendarPanel.SET_DATE_PROPERTY,
                    null, date);
        }
    }

    /**
     * Sets the selection date range for {@code JCalendarPanel}. The set dates
     * shall be highlighted in calendar panel.
     * @param startDate start date
     * @param endDate end date
     */
    public void setDates(LocalDate startDate, LocalDate endDate) {
        if (startDate.isEqual(endDate)) {
            setDate(startDate);
            return;
        }
        if (isValidDateRange(startDate, endDate)) {
            getDateSelectionModel().getCalendar().setTime(
                    CalendarUtilities.getDateFromLocalDate(startDate));
            getDateSelectionModel().setSelectedDates(startDate, endDate, true);
        }
    }

    private boolean isValidDate(LocalDate date) {
        if (!selectableDateRange.isEmpty()) {
            return !date.isBefore(selectableDateRange.first()) &&
                    !date.isAfter(selectableDateRange.last());
        }
        return true;
    }

    private boolean isValidDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            return false;
        }
        if (!selectableDateRange.isEmpty()) {
            return !startDate.isBefore(selectableDateRange.first()) &&
                    !endDate.isAfter(selectableDateRange.last());
        }
        return true;
    }

    /**
     * Returns date selection Model
     *
     * @return DateSelectionModel
     * @see DateSelectionModel
     */
    public DateSelectionModel getDateSelectionModel() {
        return selectionModel;
    }

    /**
     * Sets a date selection model for {@code JCalendarPanel}
     * @param dateSelectionModel dateSelectionModel
     */
    public void setDateSelectionModel(DateSelectionModel dateSelectionModel) {
        DateSelectionModel oldModel = selectionModel;
        selectionModel = dateSelectionModel;
        firePropertyChange(JCalendarPanel.DATE_SELECTION_MODEL_PROPERTY,
                oldModel, dateSelectionModel);
    }

    /**
     * Sets the calendar panel to the {@code JCalendarPanel}.
     * Default date selection panel is provided with Basic Look and Feel.
     * User can provide custom implementation of {@code AbstractCalendarPanel}
     * <p>
     * This will fire a {@code PropertyChangeEvent} for the property
     * named "calendarPanel"
     *
     * @param panel AbstractCalendarPanel
     * @see JComponent#addPropertyChangeListener
     */
    @BeanProperty(hidden = true, description
            = "An array of different calendar panel types.")
    public void setCalendarPanel(AbstractCalendarPanel panel) {
        AbstractCalendarPanel oldValue = abstractCalendarPanel;
        abstractCalendarPanel = panel;
        firePropertyChange(CALENDAR_PANEL_PROPERTY, oldValue, abstractCalendarPanel);
    }

    /**
     * Sets the user selectable date range for {@code JCalendarPanel}
     * which allow user to enable a range of dates for selection.
     * dates other than selectable date shall be disabled.
     * @param startDate start date
     * @param endDate end date
     */
    public void setSelectableDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null || endDate != null) {
            if (startDate == null) {
                startDate = LocalDate.of(getYearSelectionLimit().first(), Month.JANUARY, 1);
            }
            if (endDate == null) {
                endDate = LocalDate.of(getYearSelectionLimit().last(), Month.DECEMBER, 31);
            }
            if (startDate.isAfter(endDate)) {
                return;
            }
            resetSelectableDateRange();
            selectableDateRange.add(startDate);
            selectableDateRange.add(endDate);
            setYearSelectionLimit(startDate.getYear(), endDate.getYear());
            if (!checkIfSetDateIsWithinRange(startDate, endDate)) {
                setDate(startDate);
            }
            firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY,
                    null, selectableDateRange);
        }
    }

    private boolean checkIfSetDateIsWithinRange(LocalDate start, LocalDate endDate) {
        switch (getDateSelectionMode()) {
            case SINGLE_SELECTION -> {
                LocalDate setDate = getDate();
                if (setDate.isBefore(start) || setDate.isAfter(endDate)) {
                    return false;
                }
            }
            case RANGE_SELECTION -> {
                LocalDate setStartDate = getDates().first();
                LocalDate setEndDate = getDates().last();

                if (setStartDate.equals(setEndDate)) {
                    if (setStartDate.isBefore(start) || setStartDate.isAfter(endDate)) {
                        return false;
                    }
                } else {
                    if (setStartDate.isBefore(start) || setStartDate.isAfter(endDate)) {
                        return false;
                    }
                    if (setEndDate.isBefore(start) || setEndDate.isAfter(endDate)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Sets the year selection scroll limit range of year selection control
     * of calendar Pane.
     * @param min minimum year. The default is current year - 100;
     * @param max maximum year. The default is current year + 100;
     */
    public void setYearSelectionLimit(int min, int max) {
        if (min < LocalDate.MIN.getYear() || max > LocalDate.MAX.getYear()) {
            return;
        }
        if (min > max) {
            return;
        }
        resetYearSelectionLimit();
        selectableYearRange.add(min);
        selectableYearRange.add(max);
        LocalDate minDate;
        if (min == LocalDate.MIN.getYear()) {
            minDate = LocalDate.of(min, Month.JANUARY, 1);
        } else {
            minDate = LocalDate.of(min - 1, Month.DECEMBER, 31);
        }
        LocalDate maxDate =  LocalDate.of(max, Month.DECEMBER, 31);
        if (!checkIfSetDateIsWithinRange(minDate, maxDate)) {
            setDate(LocalDate.of(min, Month.JANUARY, 1));
        }
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY,
                null, selectableYearRange);
    }

    /**
     * Returns the year selection limit range of Year selection control
     * from calendar Pane
     * @return year selection limit range
     */
    public SortedSet<Integer> getYearSelectionLimit() {
        if (selectableYearRange != null) {
            return new TreeSet<>(selectableYearRange);
        } else {
            return null;
        }
    }

    /**
     * Returns date Selection Mode Type
     * @return selection Mode Type
     */
    public DateSelectionModel.SelectionMode getDateSelectionMode() {
        return getDateSelectionModel().getDateSelectionMode();
    }

    /**
     * Set date selection mode type - SINGLE or RANGE selection
     * @param mode selection mode
     */
    public void setDateSelectionMode(DateSelectionModel.SelectionMode mode) {
        getDateSelectionModel().setDateSelectionMode(mode);
    }

    /**
     * Return first day-of-week
     * @return first day-of-week
     */
    public int getFirstDayOfWeek() {
        return getDateSelectionModel().getCalendar().getFirstDayOfWeek();
    }

    /**
     * Sets the day-of-week
     * @param firstDayOfWeek first day-of-week
     */
    public void setFirstDayOfWeek(int firstDayOfWeek) {
        if (firstDayOfWeek == getFirstDayOfWeek()) {
            return;
        }
        getDateSelectionModel().setFirstDayOfWeek(firstDayOfWeek);
        firePropertyChange(JCalendarPanel.FIRST_DAY_OF_WEEK_PROPERTY,
                null, firstDayOfWeek);

    }

    /**
     * Clears the selectable Date Range.
     */
    public void resetSelectableDateRange() {
        if (selectableDateRange != null && !selectableDateRange.isEmpty()) {
            selectableDateRange.clear();
        }
    }

    /**
     * Clears the year selection limit Range.
     */
    public void resetYearSelectionLimit() {
        if (selectableYearRange != null && !selectableYearRange.isEmpty()) {
            selectableYearRange.clear();
        }
    }

    /**
     * Returns selectable date range
     *
     * @return selectable date range
     */
    public SortedSet<LocalDate> getSelectableDateRange() {
        if (selectableDateRange != null) {
            return new TreeSet<>(selectableDateRange);
        } else {
            return null;
        }
    }

    /**
     * set Date panel Table cell renderer
     * @param calendarPanelType Panel type
     * @param cellRenderer custom cell renderer
     */
    public void setDatePanelCellRenderer(
            DateSelectionModel.CalendarPanelType calendarPanelType,
            DefaultTableCellRenderer cellRenderer) {
        calendarPanelHashMap.get(calendarPanelType).
                setDefaultTableCellRenderer(cellRenderer);
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY,
                null, cellRenderer);
    }

    /**
     * set Date panel Table header renderer
     * @param calendarPanelType Panel type
     * @param headerRenderer custom header renderer
     */
    public void setDatePanelHeaderRenderer(
            DateSelectionModel.CalendarPanelType calendarPanelType,
            DefaultTableCellRenderer headerRenderer) {
        if (calendarPanelHashMap.get(calendarPanelType) instanceof
                DefaultDateSelectionPanel dateSelectionPanel) {
            dateSelectionPanel.setDefaultTableHeaderRenderer(headerRenderer);
            firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY,
                    null, headerRenderer);
        }
    }

    /**
     * Returns true is the date is within Selectable date range.
     * @param date date
     * @return true if date is within selectable date range. Else false.
     */
    public boolean isWithinSelectableDateRange(LocalDate date) {
        if (selectableDateRange.isEmpty()) {
            return true;
        } else return !(date.isAfter(selectableDateRange.last()) ||
                date.isBefore(selectableDateRange.first()));
    }

    /**
     * Get calendar Panel according to type
     *
     * @param calendarPanelType panel type
     * @param calendarPanel     calendar panel
     */
    public void setSelectionPanel(
            DateSelectionModel.CalendarPanelType calendarPanelType,
            AbstractCalendarPanel calendarPanel) {
        calendarPanelHashMap.put(calendarPanelType, calendarPanel);
        calendarPanel.installCalendarPanel(this);
    }

    /**
     * Get calendar Panel according to type
     *
     * @param calendarPanelType panel type
     * @return calendar panel
     */
    public AbstractCalendarPanel getSelectionPanel(
            DateSelectionModel.CalendarPanelType calendarPanelType) {
        return calendarPanelHashMap.get(calendarPanelType);
    }

    /**
     * Enable week number show status
     * @param status enable status
     */
    public void enableWeekNumber(boolean status) {
        enableWeekNumber = status;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY,
                null, enableWeekNumber);
    }

    /**
     * Get week number enabled status
     * @return week number enabled status
     */
    public boolean isWeekNumberEnabled() {
        return enableWeekNumber;
    }

    /**
     * Sets the grid text font. Cell renderers and labels
     * can use this font to render text and graphics.
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param font the <code>Font</code> to use for grid cells
     */
    @BeanProperty(description
            = "A default font type for grid cells.")
    public void setGridFont(Font font) {
        Font old = this.gridFont;
        this.gridFont = font;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY, old, font);
    }

    /**
     * Returns the font of calendar grid.
     *
     * @return the <code>Font</code> used for the grid cell
     */
    public Font getGridFont() {
        return gridFont;
    }

    /**
     * Sets the grid foreground color for all the cells. Cell renderers
     * can use this color to render text and graphics for all the
     * cells.
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param foreground  the <code>Color</code> to use in the foreground
     *                             for all the grid cells
     * @see #getGridForeground
     */
    @BeanProperty(description
            = "A default foreground color for all the grid cells.")
    public void setGridForeground(Color foreground) {
        Color old = this.gridForeground;
        this.gridForeground = foreground;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY, old, foreground);
    }

    /**
     * Returns the foreground color for all the grid cells.
     *
     * @return the <code>Color</code> used for the foreground of all the grid
     * cells
     */
    public Color getGridForeground() {
        return gridForeground;
    }

    /**
     * Sets the grid background color for all the cells. Cell renderers
     * can use this color to render text and graphics for all the
     * cells.
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param background  the <code>Color</code> to use in the background
     *                             for all the grid cells
     * @see #getGridBackground
     */
    @BeanProperty(description
            = "A default background color for all the cells.")
    public void setGridBackground(Color background) {
        Color old = this.gridBackground;
        this.gridBackground = background;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY, old, background);
    }

    /**
     * Returns the background color for all the cells.
     *
     * @return the <code>Color</code> used for the background of all the cells
     */
    public Color getGridBackground() {
        return gridBackground;
    }

    /**
     * Sets the grid header text font. Cell header renderers
     * can use this font to render text and graphics for
     * grid header cells.
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param headerCellFont the <code>Font</code> to use for grid header cell
     */
    @BeanProperty(description
            = "A default font type for grid header cell.")
    public void setGridHeaderCellFont(Font headerCellFont) {
        Font old = this.gridHeaderCellFont;
        this.gridHeaderCellFont = headerCellFont;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY, old, headerCellFont);
    }

    /**
     * Returns the font for grid header cells.
     *
     * @return the <code>Font</code> used for the grid header cell
     */
    public Font getGridHeaderCellFont() {
        return gridHeaderCellFont;
    }

    /**
     * Sets the grid header foreground color for all the cells. Cell header
     * renderers can use this color to render text and graphics for header
     * cells.
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param headerForeground the <code>Color</code> to use in the foreground
     *                             for grid header cell.
     * @see #getGridHeaderForeground
     */
    @BeanProperty(description
            = "A default foreground color for grid header cell.")
    public void setGridHeaderForeground(Color headerForeground) {
        Color old = this.gridHeaderForeground;
        this.gridHeaderForeground = headerForeground;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY, old, headerForeground);
    }

    /**
     * Returns the foreground color for grid header cell.
     *
     * @return the <code>Color</code> used for the foreground of grid header cell
     */
    public Color getGridHeaderForeground() {
        return gridHeaderForeground;
    }

    /**
     * Sets the grid header background color for all the cells. Cell header
     * renderers can use this color to render text and graphics for header
     * cells.
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param headerBackground the <code>Color</code> to use in the background
     *                             for grid header cell
     * @see #getGridHeaderBackground
     */
    @BeanProperty(description
            = "A default background color for grid header cells.")
    public void setGridHeaderBackground(Color headerBackground) {
        Color old = this.gridHeaderBackground;
        this.gridHeaderBackground = headerBackground;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY, old, headerBackground);
    }

    /**
     * Returns the background color for header cells.
     *
     * @return the <code>Color</code> used for the background of header cells
     */
    public Color getGridHeaderBackground() {
        return gridHeaderBackground;
    }

    /**
     * Sets the grid foreground color for selected cells. Cell renderers
     * can use this color to render text and graphics for selected
     * cells.
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param selectionForeground the <code>Color</code> to use in the foreground
     *                             for selected cells
     * @see #getGridForeground
     */
    @BeanProperty(description
            = "A default foreground color for selected cells.")
    public void setGridSelectionForeground(Color selectionForeground) {
        Color old = this.gridSelectionForeground;
        this.gridSelectionForeground = selectionForeground;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY, old, selectionForeground);
    }

    /**
     * Returns the foreground color for selected cells.
     *
     * @return the <code>Color</code> used for the foreground of selected cells
     */
    public Color getGridSelectionForeground() {
        return gridSelectionForeground;
    }

    /**
     * Sets the grid background color for selected cells. Cell renderers
     * can use this color to render text and graphics for selected
     * cells.
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param selectionBackground  the <code>Color</code> to use in the background
     *                             for selected cells
     * @see #getGridSelectionBackground
     */
    @BeanProperty(description
            = "A default background color for selected cells.")
    public void setGridSelectionBackground(Color selectionBackground) {
        Color old = this.gridSelectionBackground;
        this.gridSelectionBackground = selectionBackground;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY, old, selectionBackground);
    }

    /**
     * Returns the background color for selected cells.
     *
     * @return the <code>Color</code> used for the background of selected cells
     */
    public Color getGridSelectionBackground() {
        return gridSelectionBackground;
    }

    /**
     * Sets the grid foreground color for current date cell. Cell renderers
     * can use this color to render text and graphics for current date
     * cell.
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param currentDateForeground  the <code>Color</code> to use in the foreground
     *                             for current date cell
     * @see #getGridCurrentDateForeground
     */
    @BeanProperty(description
            = "A default foreground color for current date cell.")
    public void setGridCurrentDateForeground(Color currentDateForeground) {
        Color old = this.gridCurrentDateForeground;
        this.gridCurrentDateForeground = currentDateForeground;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY, old, currentDateForeground);
    }

    /**
     * Returns the foreground color for current date cell.
     *
     * @return the <code>Color</code> used for the foreground of current date cell
     */
    public Color getGridCurrentDateForeground() {
        return gridCurrentDateForeground;
    }

    /**
     * Sets the grid background color for current date cell. Cell renderers
     * can use this color to render text and graphics for current date
     * cell.
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param currentDateBackground the <code>Color</code> to use in the background
     *                             for current date cell
     * @see #getGridCurrentDateBackground
     */
    @BeanProperty(description
            = "A default background color for current date cell.")
    public void setGridCurrentDateBackground(Color currentDateBackground) {
        Color old = this.gridCurrentDateBackground;
        this.gridCurrentDateBackground = currentDateBackground;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY, old, currentDateBackground);
    }

    /**
     * Returns the background color for current date cell.
     *
     * @return the <code>Color</code> used for the background of current cell
     */
    public Color getGridCurrentDateBackground() {
        return gridCurrentDateBackground;
    }

    /**
     * Sets the grid foreground color for week number column. Cell renderers
     * can use this color to render text and graphics for week number column
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param weekNumberForeground the <code>Color</code> to use in the foreground
     *                             for week number column
     * @see #getWeekNumberForeground
     */
    @BeanProperty(description
            = "A default foreground color for week number column.")
    public void setGridWeekNumberForeground(Color weekNumberForeground) {
        Color old = this.gridWeekNumberForeground;
        this.gridWeekNumberForeground = weekNumberForeground;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY, old, weekNumberForeground);
    }

    /**
     * Returns the foreground color for week number column.
     *
     * @return the <code>Color</code> used for the foreground of week number column
     */
    public Color getWeekNumberForeground() {
        return gridWeekNumberForeground;
    }

    /**
     * Sets the grid color.
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param gridColor  the <code>Color</code> to use in the grid
     * @see #getGridColor
     */
    @BeanProperty(description
            = "A default grid color.")
    public void setGridColor(Color gridColor) {
        Color old = this.gridColor;
        this.gridColor = gridColor;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY, old, gridColor);
    }

    /**
     * Returns the grid color.
     *
     * @return the <code>Color</code> used for the grid.
     */
    public Color getGridColor() {
        return gridColor;
    }
    /**
     * Sets the visible state of grid lines.
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param showGridLines  if true, shows the grid lines status; otherwise, hides it
     * @see #isGridLinesVisible
     */
    @BeanProperty(description
            = "A default state to show/hide table grid.")
    public void showGridLines(boolean showGridLines) {
        boolean old = this.showGridLines;
        this.showGridLines = showGridLines;
        firePropertyChange(JCalendarPanel.PANEL_ATTRIBUTES_PROPERTY, old, showGridLines);
    }

    /**
     * Returns the visible state of table grid lines.
     *
     * @return true if grid lines are visible; otherwise, false
     */
    public boolean isGridLinesVisible() {
        return showGridLines;
    }

    /**
     * Adds a listener to the list that is notified each time a change
     * to the model occurs. The source of <code>ChangeEvents</code>
     * delivered to <code>ChangeListeners</code> will be
     * <code>DefaultDateSelectionModel</code>.
     * Applications can add listeners to  the model directly.
     * The source of the event would be the
     * <code>DefaultDateSelectionModel</code>.
     *
     * @param listener the <code>ChangeListener</code> to add
     * @see #removeChangeListener
     * @see #getDateSelectionModel
     */
    public void addChangeListener(ChangeListener listener) {
        getDateSelectionModel().addChangeListener(listener);
    }
    /**
     * Removes a <code>ChangeListener</code> from <code>DefaultDateSelectionModel</code>.
     *
     * @param listener the <code>ChangeListener</code> to remove
     * @see #addChangeListener
     */
    public void removeChangeListener(ChangeListener listener) {
        getDateSelectionModel().removeChangeListener(listener);
    }

    /******************Accessibility support******************/

    /**
     * Gets the AccessibleContext associated with this JCalendarPanel.
     * For CalendarPanel, the AccessibleContext takes the form of an
     * AccessibleJCalendarPanel.
     * A new AccessibleJCalendarPanel instance is created if necessary.
     *
     * @return an AccessibleJCalendarPanel that serves as the
     *         AccessibleContext of this JCalendarPanel
     */
    @BeanProperty(bound = false)
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new JCalendarPanel.AccessibleJCalendarPanel();
        }
        return accessibleContext;
    }

    /**
     * This class implements accessibility support for the
     * <code>JCalendarPanel</code> class. It provides an implementation of the
     * Java Accessibility API appropriate to CalendarPanel user-interface
     * elements.
     */
    protected class AccessibleJCalendarPanel extends AccessibleJComponent {

        /**
         * Constructs an {@code AccessibleJCalendarPanel}.
         */
        protected AccessibleJCalendarPanel() {}

        /**
         * Get the role of this object.
         *
         * @return an instance of AccessibleRole describing the role of the
         * object
         * @see AccessibleRole
         */
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.CALENDAR_PANEL;
        }

    }
}
