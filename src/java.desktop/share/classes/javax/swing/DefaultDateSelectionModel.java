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

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

/**
 * Default implementation of {@code DateSelectionModel}
 *
 * @since 99
 */

@PreviewFeature(feature = PreviewFeature.Feature.JDATEPICKER)
public class DefaultDateSelectionModel implements DateSelectionModel {

    /**
     * Only one {@code ChangeEvent} is needed per button
     * instance since the
     * event's only state is the source property.  The source of events
     * generated is always "this".
     */
    protected transient ChangeEvent changeEvent = null;
    private final Locale locale;
    private SelectionMode selectionMode;
    private SortedSet<LocalDate> selectionDateRange;
    private EventType eventType;
    private Calendar calendar;
    /**
     * The list of listeners.
     */
    protected EventListenerList listenerList = new EventListenerList();

    /**
     * Creates instance of DefaultDateSelectionModel class with default Locale
     */
    public DefaultDateSelectionModel() {
        this(Locale.getDefault());
    }

    /**
     * Creates instance of DefaultDateSelectionModel class with initial Locale
     * @param locale locale
     */
    public DefaultDateSelectionModel(Locale locale) {
        this.locale = locale;
        this.selectionMode = SelectionMode.SINGLE_SELECTION;
        this.selectionDateRange = new TreeSet<>();
        this.calendar = Calendar.getInstance();
    }

    @Override
    public SelectionMode getDateSelectionMode() {
        return selectionMode;
    }

    @Override
    public void setDateSelectionMode(SelectionMode mode) {
        this.selectionMode = mode;
        fireStateChanged();
    }

    @Override
    public void setSelectedDates(LocalDate startDate, LocalDate endDate,
                                 boolean commit) {
        if (startDate.isAfter(endDate)) {
            return;
        }
        if (SelectionMode.SINGLE_SELECTION.equals(selectionMode)) {
            if (isSelected(startDate)) {
                return;
            }
            endDate = startDate;

        } else {
            if ((!commit) && isIntervalSelected(startDate, endDate)) {
                return;
            }
        }
        resetSelectedDates();
        if (startDate.isEqual(endDate)) {
            selectionDateRange.add(startDate);
        } else {
            LocalDate date = startDate;
            while (!date.isAfter(endDate)) {
                selectionDateRange.add(date);
                date = date.plusDays(1);
            }
        }
        if (commit) {
            calendar.set(selectionDateRange.first().getYear(),
                    selectionDateRange.first().getMonthValue() - 1,
                    selectionDateRange.first().getDayOfMonth());
            setEventType(EventType.DATE_SELECTION);
            fireStateChanged();
        }
    }

    @Override
    public void resetSelectedDates() {
        if (selectionDateRange != null && !selectionDateRange.isEmpty()) {
            selectionDateRange.clear();
        }
    }

    private boolean isSelected(final LocalDate date) {
        if (isSelectionEmpty()) {
            return false;
        }
        return selectionDateRange.contains(date);
    }

    /**
     * Return true if the date selection range is empty.
     *
     * @return true if the date selection range is empty, else false
     */
    public boolean isSelectionEmpty() {
        return selectionDateRange.isEmpty();
    }

    private boolean isIntervalSelected(LocalDate startDate, LocalDate endDate) {
        if (isSelectionEmpty()) {
            return false;
        }
        return selectionDateRange.first().equals(startDate)
                && selectionDateRange.last().equals(endDate);
    }

    @Override
    public void setSelectedDate(LocalDate date, boolean commit) {
        if (!getSelectedDates().contains(date)) {
            selectionDateRange.clear();
            selectionDateRange.add(date);
        }
        if (commit) {
            calendar.set(selectionDateRange.first().getYear(),
                    selectionDateRange.first().getMonthValue() - 1,
                    selectionDateRange.first().getDayOfMonth());
            setEventType(EventType.DATE_SELECTION);
            fireStateChanged();
        }
    }

    @Override
    public LocalDate getSelectedDate() {
        return getFirstSelectionDate();
    }

    @Override
    public SortedSet<LocalDate> getSelectedDates() {
        if (selectionDateRange != null) {
            return new TreeSet<>(selectionDateRange);
        } else {
            return null;
        }
    }

    private LocalDate getFirstSelectionDate() {
        if (selectionDateRange != null) {
            return selectionDateRange.first();
        } else {
            return null;
        }
    }

    @Override
    public void setCalendar(Calendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public Calendar getCalendar() {
        return (Calendar) calendar.clone();
    }

    @Override
    public void setFirstDayOfWeek(int firstDayOfWeek) {
        calendar.setFirstDayOfWeek(firstDayOfWeek);
    }

    @Override
    public int getFirstDayOfWeek() {
        return calendar.getFirstDayOfWeek();
    }

    @Override
    public void setEventType(final EventType eventType) {
        this.eventType = eventType;
    }

    @Override
    public EventType getEventType() {
        return this.eventType;
    }

    @Override
    public Locale getLocale() {
        return locale;
    }


    @Override
    public void addChangeListener(ChangeListener l) {
        listenerList.add(ChangeListener.class, l);
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        listenerList.remove(ChangeListener.class, l);
    }

    /**
     * Returns an array of all the {@code ChangeListeners} added
     * to this DefaultDateSelectionModel with addChangeListener().
     *
     * @return all of the {@code ChangeListeners} added or an empty
     *         array if no listeners have been added
     * @since 1.4
     */
    public ChangeListener[] getChangeListeners() {
        return listenerList.getListeners(ChangeListener.class);
    }

    /**
     * Runs each <code>ChangeListener</code>'s
     *      <code>stateChanged</code> method.
     * @see #addChangeListener
     * @see #removeChangeListener
     * @see EventListenerList
     */
    protected void fireStateChanged() {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ChangeListener.class) {
                if (changeEvent == null) {
                    changeEvent = new ChangeEvent(this);
                }
                ((ChangeListener) listeners[i + 1]).stateChanged(changeEvent);
            }
        }
    }
}
