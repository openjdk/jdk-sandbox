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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Calendar;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.DateSelectionModel;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDatePicker;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.DatePickerUI;
import javax.swing.plaf.ComponentUI;

import jdk.internal.javac.PreviewFeature;
import sun.swing.DefaultLookup;

/**
 * Provides basic look and feel for JDatePicker
 *
 * @since 99
 *
 */

@PreviewFeature(feature = PreviewFeature.Feature.JDATEPICKER)
@SuppressWarnings("serial")
public class BasicDatePickerUI extends DatePickerUI {
    private static final int MIN_WIDTH = 150;
    private static final int MIN_HEIGHT = 40;
    private final Action approveDateAction = new ApproveDateAction();
    private final Action approvePopupAction = new ApprovePopupAction();
    private BasicDatePickerPopup popup;
    private BasicDatePickerUI.Handler handler;
    private ChangeListener calendarPanelChangeListener;
    private JButton popupButton;
    private JFormattedTextField formattedTextField;

    /**
     * {@code JDatePicker} object instance
     */
    protected JDatePicker datePicker;

    /**
     * The instance of {@code ChangeListener}.
     */
    protected ChangeListener previewListener;
    /**
     * The instance of {@code PropertyChangeListener}.
     */
    protected PropertyChangeListener propertyChangeListener;
    /**
     * The instance of {@code MouseListener}.
     */
    protected MouseListener mouseListener;

    /**
     * Construct a new instance of {@code BasicDatePickerUI}
     */
    public BasicDatePickerUI() {
    }

    /**
     * Returns a new instance of {@code BasicDatePickerUI}.
     *
     * @param c a component
     * @return a new instance of {@code BasicDatePickerUI}
     */
    public static ComponentUI createUI(JComponent c) {
        return new BasicDatePickerUI();
    }

    /**
     * Configures the {@code JDatePicker} component appropriately for the look and feel.
     * @param c component
     */
    public void installUI(JComponent c) {
        JPanel panel = new JPanel(new BorderLayout());
        this.datePicker = (JDatePicker) c;
        super.installUI(c);
        installDefaults();
        panel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        datePicker.setLayout(new BorderLayout());
        datePicker.setTextFieldFormatter(DateTimeFormatter.ofLocalizedDate(
                FormatStyle.LONG));
        formattedTextField = createDateFormatter();
        panel.add(formattedTextField, BorderLayout.CENTER);
        popupButton = createPopupButton();
        JComboBox<String> box = new JComboBox<>();
        Object preventHide = box.getClientProperty("doNotCancelPopup");
        popupButton.putClientProperty("doNotCancelPopup", preventHide);
        popupButton.setMargin(new Insets(0, 0, 0, 0));
        popupButton.setBorder(null);
        panel.add(popupButton, BorderLayout.EAST);
        popupButton.setEnabled(datePicker.isEnabled());
        popupButton.setInheritsPopupMenu(true);
        popupButton.addMouseListener(mouseListener);
        panel.setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        panel.setFocusTraversalKeysEnabled(true);
        datePicker.add(panel, BorderLayout.CENTER);
        datePicker.applyComponentOrientation(c.getComponentOrientation());
        installListeners();
    }

    /**
     * Reverses configuration which was done on the {@code JDatePicker} component during
     *
     * @param c component
     */
    public void uninstallUI(JComponent c) {
        uninstallListeners();
        uninstallDefaults();
        uninstallKeyboardActions();
        datePicker = null;
        handler = null;
        c.removeAll();
    }

    private JFormattedTextField createDateFormatter() {
        JFormattedTextField formattedTextField = new JFormattedTextField();
        if (datePicker.getDate() != null) {
            formattedTextField.setText(datePicker.getDate().format(
                    datePicker.getTextFieldFormatter().withLocale(datePicker.getLocale())));
        }
        formattedTextField.setMargin(new Insets(0, 0, 0, 0));
        formattedTextField.setBorder(null);
        return formattedTextField;
    }

    private JButton createPopupButton() {
        JButton b = new JButton(new ApprovePopupAction());
        b.setName("popupButton");
        b.setRolloverEnabled(false);
        b.setPreferredSize(new Dimension(35, 35));
        b.setIcon(datePicker.getCalendarIcon());
        b.setFocusable(true);
        b.requestFocusInWindow();
        b.setMargin(new Insets(0, 0, 0, 0));
        return b;
    }

    private class BasicDatePickerPopup extends JPopupMenu {
        public BasicDatePickerPopup() {
            setLayout(new BorderLayout());
            add(datePicker.getCalendarPanel(), BorderLayout.CENTER);
        }
    }

    /**
     * Updates this component.
     *
     * @param g the <code>Graphics</code> context in which to paint
     * @param c the component being painted;
     *          this argument is often ignored,
     *          but might be used if the UI object is stateless
     *          and shared by multiple components
     * @see #paint
     * @see javax.swing.plaf.ComponentUI
     */

    @Override
    public void update(Graphics g, JComponent c) {
        paint(g, c);
        paintBackground(g);
    }

    /**
     * Paints a background for the view.  This will only be
     * called if isOpaque() on the associated component is
     * true.  The default is to paint the background color
     * of the component.
     *
     * @param g the graphics context
     */
    protected void paintBackground(Graphics g) {
        if (datePicker.isOpaque()) {
            g.setColor(datePicker.getBackground());
            g.fillRect(0, 0, datePicker.getWidth(), datePicker.getHeight());
        }
    }

    /**
     * Installs default properties.
     */
    protected void installDefaults() {
        LookAndFeel.installColorsAndFont(datePicker, "DatePicker.background",
                "DatePicker.foreground",
                "DatePicker.font");
        LookAndFeel.installProperty(datePicker, "opaque", Boolean.TRUE);
        datePicker.setCalendarIcon(UIManager.getIcon("DatePicker.calendarIcon"));
    }

    /**
     * Uninstalls default properties.
     */
    protected void uninstallDefaults() {
    }

    /**
     * Registers listeners.
     */
    protected void installListeners() {
        propertyChangeListener = createPropertyChangeListener();
        mouseListener = createMouseListener();
        datePicker.addPropertyChangeListener(propertyChangeListener);
        datePicker.getCalendarPanel().getDateSelectionModel().addChangeListener(getCalendarPanelChangeListener());
        previewListener = getHandler();
        formattedTextField.getDocument().addDocumentListener(getTextFieldDocumentListener());
        installKeyboardActions();
        CustomFocusListener.setFocusListener(popupButton);
    }

    /**
     * installKeyboardActions
     */
    protected void installKeyboardActions() {
        InputMap inputMap = getInputMap(JComponent.WHEN_FOCUSED);
        SwingUtilities.replaceUIInputMap(formattedTextField, JComponent.
                WHEN_FOCUSED, inputMap);
        SwingUtilities.replaceUIInputMap(popupButton, JComponent.
                WHEN_FOCUSED, inputMap);

        ActionMap formattedTextFieldActionMap = formattedTextField.getActionMap();
        formattedTextFieldActionMap.put("acceptSelection", new ApproveDateAction());
        ActionMap popupButtonFieldActionMap = popupButton.getActionMap();
        popupButtonFieldActionMap.put("acceptSelection", new ApprovePopupAction());
    }

    /**
     * uninstallKeyboardActions
     */
    protected void uninstallKeyboardActions() {
        SwingUtilities.replaceUIInputMap(formattedTextField, JComponent.
                WHEN_FOCUSED, null);
        SwingUtilities.replaceUIInputMap(popupButton, JComponent.
                WHEN_FOCUSED, null);
    }

    private BasicDatePickerUI.Handler getHandler() {
        if (handler == null) {
            handler = new BasicDatePickerUI.Handler();
        }
        return handler;
    }

    /**
     * Returns an instance of {@code PropertyChangeListener}.
     *
     * @return an instance of {@code PropertyChangeListener}
     */
    protected PropertyChangeListener createPropertyChangeListener() {
        return getHandler();
    }

    /**
     * Creates a listener that will watch for mouse-press and
     * release events on the popup button of {@code JDatePicker}.
     * <p>
     * <strong>Warning:</strong>
     * When overriding this method, make sure to maintain the existing
     * behavior.
     *
     * @return a <code>MouseListener</code> which will be added to
     * the popup button
     */
    protected MouseListener createMouseListener() {
        return getHandler();
    }

    /**
     * Unregisters listeners.
     */
    protected void uninstallListeners() {
        datePicker.removePropertyChangeListener(propertyChangeListener);
        datePicker.getCalendarPanel().getDateSelectionModel().
                removeChangeListener(previewListener);
        datePicker.getCalendarPanel().getDateSelectionModel().
                removeChangeListener(getCalendarPanelChangeListener());
        previewListener = null;
    }

    private class Handler implements MouseListener, ChangeListener,
            PropertyChangeListener {
        public void stateChanged(ChangeEvent evt) {
        }

        public void mousePressed(MouseEvent evt) {
            showPopupButton();
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
            if(Objects.equals(prop, JDatePicker.DATETIME_FORMATTER_ATTRIBUTES_PROPERTY)) {
                if (!datePicker.getCalendarPanel().getDateSelectionModel().
                        getSelectedDates().isEmpty()) {
                    validateAndSetDateToTextField();
                    datePicker.revalidate();
                }
            } else if (Objects.equals(prop, JDatePicker.POPUP_ICON_ATTRIBUTES_PROPERTY)) {
                popupButton.setIcon(datePicker.getCalendarIcon());
            } else if (Objects.equals(prop, JDatePicker.DATETIME_FORMATTER_ATTRIBUTES_PROPERTY)) {
                validateAndSetDateToTextField();
            }
        }
    }

    private ChangeListener getCalendarPanelChangeListener() {
        if (calendarPanelChangeListener == null) {
            calendarPanelChangeListener = evt -> {
                if (((DateSelectionModel) evt.getSource()).getEventType()
                        == DateSelectionModel.EventType.DATE_SELECTION) {
                    if (!datePicker.getCalendarPanel().getDateSelectionModel().
                            getSelectedDates().isEmpty()) {
                        validateAndSetDateToTextField();
                        datePicker.revalidate();
                    } else {
                        resetDateToTextField();
                    }
                    if (popup != null) {
                        popup.setVisible(false);
                    }
                }
            };
        }
        return calendarPanelChangeListener;
    }

    private DocumentListener getTextFieldDocumentListener() {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                resize();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                resize();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                resize();
            }

            private void resize() {
                FontMetrics fm = formattedTextField.getFontMetrics(
                        formattedTextField.getFont());
                String text = formattedTextField.getText();
                int width = fm.stringWidth(text.isEmpty() ? " " : text) + 20;
                Dimension dimension = formattedTextField.getPreferredSize();
                dimension.width = width;
                formattedTextField.setPreferredSize(dimension);
                formattedTextField.revalidate();
            }
        };
    }

    private void validateAndSetDateToTextField() {
        if (datePicker.getDateSelectionMode().equals(
                DateSelectionModel.SelectionMode.SINGLE_SELECTION)) {
            LocalDate localDate = datePicker.getCalendarPanel().
                    getDateSelectionModel().getSelectedDates().getFirst();
            if (localDate == null) {
                return;
            }
            formattedTextField.setText(localDate.format(
                    datePicker.getTextFieldFormatter().withLocale(
                            datePicker.getLocale())));
        } else if (datePicker.getDateSelectionMode().equals(
                DateSelectionModel.SelectionMode.RANGE_SELECTION)) {
            LocalDate startDate = datePicker.getCalendarPanel().
                    getDateSelectionModel().getSelectedDates().
                    getFirst();
            LocalDate endDate = datePicker.getCalendarPanel().
                            getDateSelectionModel().getSelectedDates().
                            getLast();
            if (startDate == null || endDate == null) {
                return;
            }
            if (startDate.isEqual(endDate)) {
                formattedTextField.setText(startDate.format(
                        datePicker.getTextFieldFormatter().withLocale(
                                datePicker.getLocale())));
                return;
            }
            formattedTextField.setText(startDate.format(
                    datePicker.getTextFieldFormatter().withLocale(
                            datePicker.getLocale()))
                    + " -- " +
                    endDate.format(datePicker.getTextFieldFormatter().withLocale(
                            datePicker.getLocale())));
        }
    }

    private void resetDateToTextField() {
        formattedTextField.setText(null);
    }

    private void showPopupButton() {
        if (popup == null) {
            popup = new BasicDatePickerPopup();
        }
        if (popup.isVisible()) {
            popup.setVisible(false);
        } else {
            formattedTextField.requestFocusInWindow();
            SwingUtilities.invokeLater(() -> {
                datePicker.getCalendarPanel().setCalendarPanel(
                        datePicker.getCalendarPanel().getSelectionPanel(
                                DateSelectionModel.CalendarPanelType.DAY_SELECTION));
                popup.show(datePicker, 0, datePicker.getHeight());
            });
        }
    }

    ActionMap getActionMap() {
        return createActionMap();
    }

    ActionMap createActionMap() {
        ActionMap actionMap = datePicker.getActionMap();
        actionMap.put(JDatePicker.ACTION_APPROVE_DATE, approveDateAction);
        actionMap.put(JDatePicker.ACTION_POPUP_BUTTON, approvePopupAction);

        return actionMap;
    }

    InputMap getInputMap(int condition) {
        if (condition == JComponent.WHEN_FOCUSED) {
            return (InputMap) DefaultLookup.get(datePicker, this,
                    "DatePicker.ancestorInputMap");
        }
        return null;
    }

    /**
     * Approve Date action class
     */
    // Superclass is not serializable across versions
    protected class ApproveDateAction extends AbstractAction {
        /**
         * Constructs an {@code ApproveDateAction}.
         */
        protected ApproveDateAction() {
        }

        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent e) {
            if (datePicker.getDateSelectionMode().equals(
                    DateSelectionModel.SelectionMode.SINGLE_SELECTION)) {
                try {
                    validateAndSetSingleDate();
                } catch (DateTimeParseException exception) {
                    handleDateTimeParseException();
                }
            } else if (datePicker.getDateSelectionMode().equals(
                    DateSelectionModel.SelectionMode.RANGE_SELECTION)) {
                try {
                    String[] values = formattedTextField.getText().split("--");
                    if (values.length == 2) {
                        validateAndSetMultiDates(values);
                    } else if (values.length == 1) {
                        validateAndSetSingleDate();
                    }
                } catch (DateTimeParseException exception) {
                    handleDateTimeParseException();
                }
            }
        }

        private boolean isValidDate(LocalDate localDate) {
            int minYear = Calendar.getInstance().get(Calendar.YEAR) -
                    datePicker.getCalendarPanel().getYearSelectionLimit() - 1;
            LocalDate minDate = LocalDate.of(minYear,
                    Calendar.DECEMBER + 1, 31);
            int maxYear = Calendar.getInstance().get(Calendar.YEAR) +
                    datePicker.getCalendarPanel().getYearSelectionLimit();
            LocalDate maxDate = LocalDate.of(maxYear,
                    Calendar.JANUARY + 1, 1);
            return localDate.isAfter(minDate) && localDate.isBefore(maxDate);
        }

        private void handleDateTimeParseException() {
            datePicker.setDate(LocalDate.now());
        }

        private void validateAndSetSingleDate() {
            String value = formattedTextField.getText();
            LocalDate localDate = LocalDate.parse(value,
                    datePicker.getTextFieldFormatter().withLocale(
                            datePicker.getLocale()));
            if (isValidDate(localDate)) {
                datePicker.setDate(localDate);
            } else {
                if (datePicker.getDate() != null) {
                    datePicker.setDate(datePicker.getDate());
                } else {
                    datePicker.setDate(LocalDate.now());
                }
            }
        }

        private void validateAndSetMultiDates(String[] values) {
            LocalDate startDate = LocalDate.parse(values[0].trim(),
                    datePicker.getTextFieldFormatter().withLocale(
                            datePicker.getLocale()));
            LocalDate endDate = LocalDate.parse(values[1].trim(),
                    datePicker.getTextFieldFormatter().withLocale(
                            datePicker.getLocale()));
            if (endDate.isBefore(startDate)) {
                JOptionPane.showMessageDialog(new JFrame(),
                        "End date should be greater/equal to start date");
                datePicker.setDates(LocalDate.now(), LocalDate.now().plusDays(1));
            }
            if (isValidDate(startDate) && isValidDate(endDate)) {
                datePicker.setDates(startDate, endDate);
            } else {
                if (datePicker.getDates() != null && datePicker.getDates().size() >= 2) {
                    datePicker.setDates(datePicker.getDates().first(),
                            datePicker.getDates().last());
                } else {
                    datePicker.setDates(LocalDate.now(),
                            LocalDate.now().plusDays(1));
                }
            }
        }
    }

    /**
     * Popup button action class
     */
    // Superclass is not serializable across versions
    protected class ApprovePopupAction extends AbstractAction {
        /**
         * Constructs an {@code ApproveDateAction}.
         */
        protected ApprovePopupAction() {
        }

        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent e) {
            showPopupButton();
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
}
