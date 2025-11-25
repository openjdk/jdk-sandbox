package javax.swing;

import jdk.internal.javac.PreviewFeature;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Locale;
import java.util.SortedSet;
import javax.swing.event.ChangeListener;

/**
 * A model that supports selecting a date
 *
 * @since 99
 */

@PreviewFeature(feature = PreviewFeature.Feature.JDATEPICKER)
public interface DateSelectionModel {
    /**
     * Date Selection Modes
     */
    enum SelectionMode {
        /**
         *  SINGLE SELECTION
         */
        SINGLE_SELECTION,
        /**
         *  RANGE SELECTION
         */
        RANGE_SELECTION
    }

    /**
     * Event Type
     */
    enum EventType {
        /**
         *  DATE_SELECTION
         */
        DATE_SELECTION,
        /**
         *  MONTH_NAVIGATION
         */
        MONTH_NAVIGATION,
        /**
         *  YEAR_NAVIGATION
         */
        YEAR_NAVIGATION
    }

    /**
     * Navigation Type
     */
    enum NavigationType {
        /**
         * Forward navigation
         */
        FORWARD,
        /**
         * Backward navigation
         */
        BACKWARD
    }

    /**
     * Calendar panel type
     */
    enum CalendarPanelType {
        /**
         * Day selection
         */
        DAY_SELECTION,
        /**
         * Month selection
         */
        MONTH_SELECTION,
        /**
         * Year selection
         */
        YEAR_SELECTION
    }

    /**
     * Returns date selection Mode Type
     * @return selection mode
     */
    SelectionMode getDateSelectionMode();

    /**
     * Set Event Type
     *
     * @param eventType eventType
     */
    void setEventType(final EventType eventType);

    /**
     * Returns Event Type
     * @return EventType
     */
    EventType getEventType();

    /**
     * Set date selection Mode Type
     * <p>
     * SINGLE SELECTION for single date selection
     * <p>
     * RANGE SELECTION for multiple dates selection
     * @param mode selectionMode
     */
    void setDateSelectionMode(final SelectionMode mode);

    /**
     * sets date range which highlights in calendar panel,
     * used in RANGE SELECTION MODE
     * @param startDate start date
     * @param endDate end date
     * @param commit commit date range
     */
    void setSelectedDates(LocalDate startDate, LocalDate endDate, boolean commit);

    /**
     * Clears selected date range
     */
    void resetSelectedDates();

    /**
     * Sets a single date, used in SINGLE SELECTION MODE
     * @param date date
     * @param commit commit date
     */
    void setSelectedDate(LocalDate date, boolean commit);

    /**
     * Returns a single date, used in SINGLE SELECTION MODE
     * @return single date
     */
    LocalDate getSelectedDate();

    /**
     * Returns selected dates, used in RANGE SELECTION MODE
     * @return sorted date
     */
    SortedSet<LocalDate> getSelectedDates();

    /**
     * Sets the {@code Calendar} instance of JCalendarPanel
     * @param calendar {@code Calendar}
     */
    void setCalendar(Calendar calendar);

    /**
     * Returns the {@code Calendar} instance
     * @return {@code Calendar} instance
     */
    Calendar getCalendar();

    /**
     * Sets the {@code Calendar} first day of the week
     * @param firstDayOfWeek first day of the week
     */
    void setFirstDayOfWeek(int firstDayOfWeek);

    /**
     * Returns the first day of the week of {@code Calendar}
     * @return first day of the week
     */
    int getFirstDayOfWeek();


    /**
     * Returns the set locale.
     * @return set locale
     */
    Locale getLocale();


    /**
     * Adds a ChangeListener to the model's listener list.
     * @param listener the ChangeListener to add
     */
    void addChangeListener(ChangeListener listener);

    /**
     * Removes the ChangeListener from the model's listener list.
     * @param listener the ChangeListener to add
     */
    void removeChangeListener(ChangeListener listener);
}
