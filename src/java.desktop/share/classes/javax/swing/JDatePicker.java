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

import java.beans.BeanProperty;
import java.beans.JavaBean;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Locale;
import java.util.SortedSet;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.plaf.DatePickerUI;

/**
 * Date Picker component which allows a user to select a date,
 * either by picking it in the UI or entering a date directly into a validated
 * formatted entry field.
 * <p>
 * (To-do - needs to be expanded)
 *
 * @since 99
 */
@PreviewFeature(feature = PreviewFeature.Feature.JDATEPICKER)
@JavaBean(defaultProperty = "UI", description = "A component that supports selecting a Date.")
@SwingContainer(false)
@SuppressWarnings("serial")
public class JDatePicker extends JComponent implements Accessible {
    private static final String uiClassID = "DatePickerUI";
    /**
     * Approve entered date action
     */
    public static final String ACTION_APPROVE_DATE = "approveDate";
    /**
     * Approve Popup button action
     */
    public static final String ACTION_POPUP_BUTTON = "popupButton";
    /**
     * * Set DateTime formatter property name.
     *
     */
    public static final String DATETIME_FORMATTER_ATTRIBUTES_PROPERTY = "DateTimeFormatterAttributes";
    /**
     * * Set popup icon property name.
     *
     */
    public static final String POPUP_ICON_ATTRIBUTES_PROPERTY = "popupButtonIconAttributes";

    /** The calendar icon. */
    protected Icon calendarIcon;
    private volatile JCalendarPanel calendarPanel;
    private boolean dragEnabled;
    private DateTimeFormatter textFieldFormatter;
    private LocalDate setDate;

    /**
     * Creates a Date picker with current date.
     */
    public JDatePicker() {
        this(LocalDate.now(), Locale.getDefault());
    }

    /**
     * Creates a Date picker with provided date.
     * @param date initial date
     * @param locale set locale
     */
    public JDatePicker(LocalDate date, Locale locale) {
        setDate = date;
        if (locale == null) {
            setLocale(Locale.getDefault());
        } else {
            setLocale(locale);
        }
        calendarPanel = getCalendarPanel();
        updateUI();
        dragEnabled = false;
    }

    /**
     * Notification from the <code>UIManager</code> that the L&amp;F has changed.
     * Replaces the current UI object with the latest version from the
     * <code>UIManager</code>.
     *
     * @see JComponent#updateUI
     */
    public void updateUI() {
        setUI((DatePickerUI) UIManager.getUI(this));
    }

    /**
     * Returns the L&amp;F object that renders this component.
     *
     * @return the <code>DatePickerUI</code> object that renders
     * this component
     */
    public DatePickerUI getUI() {
        return (DatePickerUI) ui;
    }

    /**
     * Returns {@code JCalendarPanel} used to select a date
     * @return calendarPanel
     *
     * @see JCalendarPanel#JCalendarPanel()
     */
    public JCalendarPanel getCalendarPanel() {
        if (calendarPanel == null) {
            synchronized (this) {
                if (calendarPanel == null) {
                    calendarPanel = new JCalendarPanel(setDate, getLocale());
                }
            }
        }
        return calendarPanel;
    }

    /**
     * Sets the L&amp;F object that renders this component.
     *
     * @param ui the <code>DatePickerUI</code> L&amp;F object
     * @see UIDefaults#getUI
     */
    public void setUI(final DatePickerUI ui) {
        super.setUI(ui);
    }

    /**
     * Returns the name of the L&amp;F class that renders this component.
     *
     * @return the string "DatePickerUI"
     * @see JComponent#getUIClassID
     * @see UIDefaults#getUI
     */
    @Override
    public String getUIClassID() {
        return uiClassID;
    }

    /**
     * Sets the date for {@code JDatePicker}. The set date
     * shall be highlighted in calendar panel.
     * Used in SINGLE SELECTION MODE
     *
     * @param date date
     */
    public void setDate(LocalDate date) {
        getCalendarPanel().setDate(date);
    }

    /**
     * Returns a single date selected from {@code JDatePicker}.
     * Used in SINGLE SELECTION MODE
     *
     * @return set date
     */
    public LocalDate getDate() {
        return getCalendarPanel().getDate();
    }

    /**
     * Sets the Date Range of calendar Panel in {@code JDatePicker}.
     * The set date(s) shall be highlighted in calendar panel.
     * Used in RANGE SELECTION MODE
     *
     * @param startDate start Date
     * @param endDate end Date
     */
    public void setDates(LocalDate startDate, LocalDate endDate) {
        getCalendarPanel().setDates(startDate, endDate);
    }

    /**
     * Returns the selected start and end date Range from calendar panel.
     * Used in RANGE SELECTION MODE
     *
     * @return Selected date Range
     */
    public SortedSet<LocalDate> getDates() {
        return getCalendarPanel().getDates();
    }

    /**
     * Sets a date selection model for {@code JDatePicker}
     * @param dateSelectionModel dateSelectionModel
     */
    public void setDateSelectionModel(DateSelectionModel dateSelectionModel) {
        getCalendarPanel().setDateSelectionModel(dateSelectionModel);
    }

    /**
     * Sets the Locale for {@code JDatePicker}
     * @param locale locale
     */
    public void setLocale(Locale locale) {
        super.setLocale(locale);
        getCalendarPanel().setLocale(locale);
    }

    /**
     * Sets the user selectable date Range of calendar Panel in
     * {@code JDatePicker}
     * @param startDate start Date
     * @param endDate end Date
     */
    public void setSelectableDateRange(LocalDate startDate, LocalDate endDate) {
        getCalendarPanel().setSelectableDateRange(startDate, endDate);
    }

    /**
     * Returns date Selection Mode Type of {@code JDatePicker}
     * @return selection Mode Type
     */
    public DateSelectionModel.SelectionMode getDateSelectionMode() {
        return getCalendarPanel().getDateSelectionModel().getDateSelectionMode();
    }

    /**
     * Set date selection mode type of {@code JDatePicker}
     * SINGLE or RANGE selection
     * @param mode selection mode
     */
    public void setDateSelectionMode(DateSelectionModel.SelectionMode mode) {
        getCalendarPanel().getDateSelectionModel().setDateSelectionMode(mode);
    }

    /**
     * Returns the DateTimeFormatter used to format date in the text field.
     *
     * @return the DateTimeFormatter instance
     */
    public DateTimeFormatter getTextFieldFormatter() {
        return textFieldFormatter;
    }

    /**
     * Sets the DateTimeFormatter used to format date in the text field.
     *
     * @param textFieldFormatter the DateTimeFormatter instance to use
     */
    public void setTextFieldFormatter(DateTimeFormatter textFieldFormatter) {
        if (!isDateOnlyFormatter(textFieldFormatter)) {
           throw new IllegalArgumentException("Formatter must be date-only (No time fields)");
        }
        DateTimeFormatter old =  this.textFieldFormatter;
        this.textFieldFormatter = textFieldFormatter;
        firePropertyChange(DATETIME_FORMATTER_ATTRIBUTES_PROPERTY, old, textFieldFormatter);
    }

    private boolean isDateOnlyFormatter(DateTimeFormatter dateTimeFormatter) {
        try {
            dateTimeFormatter.format(LocalDate.now());
            return true;
        } catch (UnsupportedTemporalTypeException e) {
            return false;
        }
    }

    /**
     * Sets the calendar icon for popup button.
     * <p>
     * The default value of this property is defined by the look
     * and feel implementation.
     * <p>
     * This is a <a href="https://docs.oracle.com/javase/tutorial/javabeans/writing/properties.html">JavaBeans</a> bound property.
     *
     * @param icon calendar <code>Icon</code> of popup button
     * @see #getCalendarIcon
     */
    @BeanProperty(description
            = "A default calendar icon for popup button.")
    public void setCalendarIcon(Icon icon) {
        Icon old = this.calendarIcon;
        this.calendarIcon = icon;
        firePropertyChange(POPUP_ICON_ATTRIBUTES_PROPERTY, old, icon);
    }

    /**
     * Returns the Calendar Icon of popup button.
     *
     * @return calendar icon of popup button
     */
    public Icon getCalendarIcon() {
        return calendarIcon;
    }

/******************Accessibility support******************/

    /**
     * The accessible context.
     */
    protected AccessibleContext accessibleContext = null;

    /**
     * Gets the AccessibleContext associated with this JDataPicker.
     * For DatePicker, the AccessibleContext takes the form of an
     * AccessibleJDatePicker.
     * A new AccessibleJDatePicker instance is created if necessary.
     *
     * @return an AccessibleJDatePicker that serves as the
     *         AccessibleContext of this JDatePicker
     */
    @BeanProperty(bound = false)
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new JDatePicker.AccessibleJDatePicker();
        }
        return accessibleContext;
    }

    /**
     * This class implements accessibility support for the
     * <code>JDatePicker</code> class. It provides an implementation of the
     * Java Accessibility API appropriate to DatePicker user-interface
     * elements.
     */
    protected class AccessibleJDatePicker extends AccessibleJComponent {

        /**
         * Constructs an {@code AccessibleJDatePicker}.
         */
        protected AccessibleJDatePicker() {}

        /**
         * Get the role of this object.
         *
         * @return an instance of AccessibleRole describing the role of the
         * object
         * @see AccessibleRole
         */
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.DATE_PICKER;
        }

    }
}
