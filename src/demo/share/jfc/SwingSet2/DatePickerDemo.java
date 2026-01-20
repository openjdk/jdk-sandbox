/*
 *
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DateSelectionModel;
import javax.swing.JButton;
import javax.swing.JCalendarPanel;
import javax.swing.JCheckBox;
import javax.swing.JDatePicker;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.ChangeListener;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;
import java.util.SortedSet;

import static javax.swing.UIManager.getString;

/**
 * JDatePickerDemo
 *
 */
public class DatePickerDemo extends DemoModule {
    /**
     * main method allows us to run as a standalone demo.
     */
    public static void main(String[] args) {
        DatePickerDemo demo = new DatePickerDemo(null);
        demo.mainImpl();
    }

    /**
     * DatePickerDemo Constructor
     */
    public DatePickerDemo(SwingSet2 swingset) {
        super(swingset, "DatePickerDemo", "toolbar/JDatePicker.gif");

        JPanel demoPanel = getDemoPanel();
        demoPanel.setLayout(new BoxLayout(demoPanel, BoxLayout.Y_AXIS));

        JPanel innerPanel = new JPanel();
        innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.X_AXIS));

        demoPanel.add(Box.createRigidArea(VGAP5));
        demoPanel.add(innerPanel);
        demoPanel.add(Box.createRigidArea(VGAP5));

        innerPanel.add(Box.createRigidArea(HGAP20));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add(getDatePickerComponent(), "JDatePicker");
        tabbedPane.add(getCalendarPanel(), "JCalendarPanel");
        tabbedPane.add(doubleDatePicker(), "DoubleDatePicker");
        tabbedPane.add(getLocalisationSample(), "Localisation");
        tabbedPane.add(getDateFormatterSample(), "Date Formatter Samples");

        getDemoPanel().add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel getCalendarPanel() {
        JPanel panel = new JPanel();
        GridBagConstraints constraints = new GridBagConstraints();
        JLabel label = new JLabel();
        JLabel labelCP;
        labelCP = new JLabel();
        JCalendarPanel calendarPanel = new JCalendarPanel();
        labelCP.setPreferredSize(new Dimension(250, 20));
        calendarPanel.setDateSelectionMode(DateSelectionModel.SelectionMode.RANGE_SELECTION);
        calendarPanel.setDate(LocalDate.of(2023, Calendar.JULY + 1, 13));
        calendarPanel.setFirstDayOfWeek(Calendar.THURSDAY);
        Border blackBorder = BorderFactory.createLineBorder(Color.BLACK);
        label.setText(getString("DatePickerDemo.showEmbeddedCalendarPanel"));
        Font baseFont = label.getFont();
        Font boldLFont = baseFont.deriveFont(Font.BOLD, 18f);
        UIManager.addPropertyChangeListener(e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) {
                SwingUtilities.invokeLater(() -> {
                    label.setFont(boldLFont);
                });
            }
        });

        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.insets = new Insets(0, 0, 20, 0);
        constraints.gridy++;

        ChangeListener l = e -> {
            SortedSet<LocalDate> dates = calendarPanel.getDates();
            if (dates.getFirst() == dates.getLast()) {
                labelCP.setText(dates.getFirst().toString());
            } else {
                labelCP.setText(dates.getFirst() + " -- " + dates.getLast());
            }
        };
        calendarPanel.getDateSelectionModel().addChangeListener(l);
        panel.add(label, constraints);

        constraints.anchor = GridBagConstraints.CENTER;
        constraints.gridy++;

        JCheckBox checkBox = new JCheckBox(getString("DatePickerDemo.showWeekNumber"));
        checkBox.addItemListener(e -> calendarPanel.enableWeekNumber(e.getStateChange() == ItemEvent.SELECTED));

        panel.add(checkBox, constraints);

        constraints.anchor = GridBagConstraints.CENTER;
        constraints.insets = new Insets(0, 0, 10, 0);
        constraints.gridy++;
        calendarPanel.setBorder(blackBorder);
        calendarPanel.setPreferredSize(new Dimension(340,250));
        panel.add(calendarPanel, constraints);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.X_AXIS));
        textPanel.add(new JLabel(getString("DatePickerDemo.selectedDate") + " : "));
        constraints.anchor = GridBagConstraints.LAST_LINE_START;
        constraints.insets = new Insets(0, 0, 10, 0);
        constraints.gridy++;
        textPanel.add(labelCP);
        panel.add(textPanel, constraints);
        return panel;
    }


    private JPanel getDatePickerComponent() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        GridBagConstraints innerGbc = new GridBagConstraints();
        JDatePicker datePicker = new JDatePicker();

        JLabel label = new JLabel();
        JLabel labelDP = new JLabel();

        Border blackBorder = BorderFactory.createLineBorder(Color.BLACK);
        label.setText(getString("DatePickerDemo.name"));
        Font baseFont = label.getFont();
        Font boldLFont = baseFont.deriveFont(Font.BOLD, 18f);
        UIManager.addPropertyChangeListener(e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) {
                SwingUtilities.invokeLater(() -> {
                    label.setFont(boldLFont);
                });
            }
        });
        datePicker.setDateSelectionMode(DateSelectionModel.SelectionMode.RANGE_SELECTION);
        datePicker.setDate(LocalDate.of(2025, Calendar.SEPTEMBER + 1, 20));
        panel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        panel.setPreferredSize(new Dimension(400, 350));
        datePicker.setBorder(blackBorder);
        constraints.gridx = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(10, 10, 10, 10);
        constraints.fill = GridBagConstraints.NONE;

        JPanel verticalPanel = new JPanel(new GridBagLayout());

        innerGbc.anchor = GridBagConstraints.CENTER;
        innerGbc.insets = new Insets(10, 10, 10, 10);
        innerGbc.gridy = 0;
        innerGbc.gridx = 0;

        verticalPanel.add(label, innerGbc);

        innerGbc.anchor = GridBagConstraints.CENTER;
        innerGbc.gridy = 1;

        JCheckBox checkBox = new JCheckBox(getString("DatePickerDemo.showWeekNumber"));
        checkBox.addItemListener(e -> datePicker.getCalendarPanel().enableWeekNumber(e.getStateChange() == ItemEvent.SELECTED));

        verticalPanel.add(checkBox, innerGbc);

        innerGbc.anchor = GridBagConstraints.WEST;
        innerGbc.gridy = 2;

        ChangeListener l2 = e -> {
            SortedSet<LocalDate> dates = datePicker.getDates();
            if (dates.getFirst() == dates.getLast()) {
                labelDP.setText(dates.getFirst().toString());
            } else {
                labelDP.setText(dates.getFirst() + " -- " + dates.getLast());
            }
        };
        datePicker.getCalendarPanel().getDateSelectionModel().addChangeListener(l2);
        //datePicker.getCalendarPanel().setPreferredSize(new Dimension(300,250));
        verticalPanel.add(datePicker, innerGbc);
        //panel.add(datePicker, BorderLayout.CENTER);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.X_AXIS));
        textPanel.add(new JLabel(getString("DatePickerDemo.selectedDate") + " : "));
        innerGbc.gridy = 3;

        textPanel.add(labelDP);
        verticalPanel.add(textPanel, innerGbc);
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.weighty = 1.0;
        constraints.insets = new Insets(0, 0, 80, 0);
        panel.add(verticalPanel, constraints);
        return panel;
    }

    private JPanel doubleDatePicker() {
        JLabel label1 = new JLabel(getString("DatePickerDemo.startDate"));
        JLabel label2 = new JLabel(getString("DatePickerDemo.endDate"));
        JPanel panel1 = new JPanel(new GridBagLayout());
        JPanel panel2 = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        JPanel mainPanel = new JPanel(new GridBagLayout());
        JLabel setDateLabel1 = new JLabel();
        JLabel setDateLabel2 = new JLabel();

        JDatePicker datePicker1 = new JDatePicker();
        JDatePicker datePicker2 = new JDatePicker();
        datePicker1.setDateSelectionMode(DateSelectionModel.SelectionMode.SINGLE_SELECTION);
        datePicker2.setDateSelectionMode(DateSelectionModel.SelectionMode.SINGLE_SELECTION);
        datePicker2.setDate(datePicker1.getDate().plusMonths(1));

        ChangeListener l = e -> {
            if (((DateSelectionModel) e.getSource()).getEventType() == DateSelectionModel.EventType.DATE_SELECTION) {
                datePicker2.setSelectableDateRange(datePicker1.getDate(), null);
                setDateLabel1.setText(datePicker1.getDate().toString());
            }
        };

        ChangeListener l2 = e -> {
            if (((DateSelectionModel) e.getSource()).getEventType() == DateSelectionModel.EventType.DATE_SELECTION) {
                datePicker1.setSelectableDateRange(null, datePicker2.getDate());
                setDateLabel2.setText(datePicker2.getDate().toString());
            }
        };
        datePicker1.getCalendarPanel().getDateSelectionModel().addChangeListener(l);
        datePicker2.getCalendarPanel().getDateSelectionModel().addChangeListener(l2);

        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(10, 10, 10, 10);
        mainPanel.setLayout(new GridBagLayout());
        panel1.setPreferredSize(new Dimension(200, 150));
        panel2.setPreferredSize(new Dimension(200, 150));

        panel1.setBorder(BorderFactory.createLineBorder(Color.black));
        panel2.setBorder(BorderFactory.createLineBorder(Color.black));

        panel1.add(label1, gbc);
        panel2.add(label2, gbc);
        gbc.gridy++;
        panel1.add(datePicker1, gbc);
        panel2.add(datePicker2, gbc);

        gbc.gridy++;
        JPanel textPanel1 = new JPanel();
        textPanel1.setLayout(new BoxLayout(textPanel1, BoxLayout.X_AXIS));
        textPanel1.add(new JLabel(getString("DatePickerDemo.selectedDate") + " : "));
        textPanel1.add(setDateLabel1);
        JPanel textPanel2 = new JPanel();
        textPanel2.setLayout(new BoxLayout(textPanel2, BoxLayout.X_AXIS));
        textPanel2.add(new JLabel(getString("DatePickerDemo.selectedDate") + " : "));
        textPanel2.add(setDateLabel2);
        panel1.add(textPanel1, gbc);
        panel2.add(textPanel2, gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(panel1, gbc);
        gbc.gridx++;
        mainPanel.add(panel2, gbc);
        return mainPanel;
    }

    private JPanel getLocalisationSample() {

        GridBagConstraints gbc = new GridBagConstraints();
        JPanel mainPanel = new JPanel(new GridBagLayout());

        JDatePicker datePicker1 = new JDatePicker(LocalDate.now(), Locale.ENGLISH);
        JDatePicker datePicker2 = new JDatePicker(LocalDate.now(), Locale.GERMAN);
        JDatePicker datePicker3 = new JDatePicker(LocalDate.now(), Locale.CHINA);
        JDatePicker datePicker4 = new JDatePicker(LocalDate.now(), Locale.JAPANESE);

        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.weightx = 1;
        mainPanel.add(createLocalisationSampleRow(datePicker1), gbc);
        gbc.gridy++;
        mainPanel.add(createLocalisationSampleRow(datePicker2), gbc);
        gbc.gridy++;
        mainPanel.add(createLocalisationSampleRow(datePicker3), gbc);
        gbc.gridy++;
        mainPanel.add(createLocalisationSampleRow( datePicker4), gbc);

        return mainPanel;
    }

    private JPanel createLocalisationSampleRow(JDatePicker datePicker) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setPreferredSize(new Dimension(300, 80));
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel label = new JLabel(datePicker.getLocale().getDisplayName());
        panel.setBorder(BorderFactory.createLineBorder(Color.black));
        label.setPreferredSize(new Dimension(100, label.getPreferredSize().height));
        datePicker.setPreferredSize(new Dimension(70, datePicker.getPreferredSize().height));
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;

        panel.add(label, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(datePicker, gbc);
        return panel;
    }

    private JPanel getDateFormatterSample() {

        GridBagConstraints gbc = new GridBagConstraints();
        JPanel mainPanel = new JPanel(new GridBagLayout());

        JDatePicker datePicker1 = new JDatePicker();
        JDatePicker datePicker2 = new JDatePicker();
        JDatePicker datePicker3 = new JDatePicker();
        JDatePicker datePicker4 = new JDatePicker();
        String p1 = "dd-MM-yyyy";
        String p2 = "dd/MM/yyyy";
        String p3 = "yyyy-MM-dd";
        String p4 = "dd MMM yyyy";
        UIManager.addPropertyChangeListener(e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) {
                SwingUtilities.invokeLater(() -> {
                    datePicker1.setTextFieldFormatter(DateTimeFormatter.ofPattern(p1));
                    datePicker1.setDate(LocalDate.now());
                });
            }
        });
        UIManager.addPropertyChangeListener(e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) {
                SwingUtilities.invokeLater(() -> {
                    datePicker2.setTextFieldFormatter(DateTimeFormatter.ofPattern(p2));
                    datePicker2.setDate(LocalDate.now());
                });
            }
        });
        UIManager.addPropertyChangeListener(e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) {
                SwingUtilities.invokeLater(() -> {
                    datePicker3.setTextFieldFormatter(DateTimeFormatter.ofPattern(p3));
                    datePicker3.setDate(LocalDate.now());
                });
            }
        });
        UIManager.addPropertyChangeListener(e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) {
                SwingUtilities.invokeLater(() -> {
                    datePicker4.setTextFieldFormatter(DateTimeFormatter.ofPattern(p4));
                    datePicker4.setDate(LocalDate.now());
                });
            }
        });

        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.weightx = 1;
        mainPanel.add(createDateFormatterSampleRow(datePicker1, p1), gbc);
        gbc.gridy++;
        mainPanel.add(createDateFormatterSampleRow(datePicker2, p2), gbc);
        gbc.gridy++;
        mainPanel.add(createDateFormatterSampleRow(datePicker3, p3), gbc);
        gbc.gridy++;
        mainPanel.add(createDateFormatterSampleRow(datePicker4, p4), gbc);

        return mainPanel;
    }

    private JPanel createDateFormatterSampleRow(JDatePicker datePicker, String pattern) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setPreferredSize(new Dimension(300, 80));
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel label = new JLabel(pattern);
        panel.setBorder(BorderFactory.createLineBorder(Color.black));
        label.setPreferredSize(new Dimension(100, label.getPreferredSize().height));
        datePicker.setPreferredSize(new Dimension(70, datePicker.getPreferredSize().height));
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;

        panel.add(label, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(datePicker, gbc);
        return panel;
    }
}
