/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.SortedSet;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DateSelectionModel;
import javax.swing.JDatePicker;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.event.ChangeListener;

/*
 * @test
 * @enablePreview
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests the date picker date(s) selection
 * @run main/manual DateSelectionTest
 */

public class DateSelectionTest  {
    public static void main(String[] args) throws Exception {
        String instructions = """
                1. Open Calendar panel through popup button
                2. Select a date using month/year navigation buttons to
                    navigate through different month/year. (Even with month/
                    year panels opened through respective labels).
                3. Click on a date and verify that the selected date is set to
                    DatePicker text field and text label (set Date(s) below
                    the component).
                4. Repeat the same step (1-2), select multiple dates with
                    mouse press + drag + release and verify the start date
                    and end date selected is set to DatePicker text field
                    and text label (set Date(s) below the component).
                """;

        PassFailJFrame.builder()
                .instructions(instructions)
                .testUI(DateSelectionTest::getDatePickerComponent)
                .build()
                .awaitAndCheck();
    }

    private static JPanel getDatePickerComponent() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        GridBagConstraints innerGbc = new GridBagConstraints();
        JDatePicker datePicker = new JDatePicker();
        JLabel label = new JLabel();
        JLabel labelDP = new JLabel();

        Border blackBorder = BorderFactory.createLineBorder(Color.BLACK);
        label.setText("<html><u>JDatePicker</u></html>");
        datePicker.setDateSelectionMode(DateSelectionModel.SelectionMode.RANGE_SELECTION);
        datePicker.setDate(LocalDate.of(2025, Calendar.SEPTEMBER + 1, 20));
        panel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        panel.setPreferredSize(new Dimension(400, 200));
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

        innerGbc.anchor = GridBagConstraints.WEST;
        innerGbc.gridy = 1;

        ChangeListener l2 = e -> {
            SortedSet<LocalDate> dates = datePicker.getDates();
            if (dates.getFirst() == dates.getLast()) {
                labelDP.setText(dates.getFirst().toString());
            } else {
                labelDP.setText(dates.getFirst() + " -- " + dates.getLast());
            }
        };
        datePicker.getCalendarPanel().getDateSelectionModel().addChangeListener(l2);
        verticalPanel.add(datePicker, innerGbc);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.X_AXIS));
        textPanel.add(new JLabel("Set Date(s) : "));
        innerGbc.gridy = 2;

        textPanel.add(labelDP);
        verticalPanel.add(textPanel, innerGbc);
        constraints.gridy = 0;

        panel.add(verticalPanel, constraints);
        return panel;
    }
}
