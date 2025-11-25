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
import javax.swing.JCalendarPanel;
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
 * @run main/manual EmbeddedCalendarPanelTest
 */

public class EmbeddedCalendarPanelTest {
    public static void main(String[] args) throws Exception {
        String instructions = """
                1. Calendar Panel embedded to a frame is opened.
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
                .testUI(EmbeddedCalendarPanelTest::getCalendarPanel)
                .build()
                .awaitAndCheck();
    }

    private static JPanel getCalendarPanel() {
        JPanel panel = new JPanel();
        GridBagConstraints constraints = new GridBagConstraints();
        JLabel label = new JLabel();
        JLabel labelCP;
        labelCP = new JLabel();
        JCalendarPanel calendarPanel = new JCalendarPanel();
        labelCP.setPreferredSize(new Dimension(300, 20));
        calendarPanel.setDateSelectionMode(DateSelectionModel.SelectionMode.RANGE_SELECTION);
        calendarPanel.setDate(LocalDate.of(2023, Calendar.JULY + 1, 13));
        calendarPanel.setPreferredSize(new Dimension(300, 250));
        Border blackBorder = BorderFactory.createLineBorder(Color.BLACK);
        label.setText("<html><u><b>JCalendarPanel Embedded</b></u></html>");
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        panel.setPreferredSize(new Dimension(400, 400));

        constraints.anchor = GridBagConstraints.CENTER;
        constraints.insets = new Insets(0, 0, 20, 0);
        constraints.gridy++;

        ChangeListener l = e -> {
            SortedSet<LocalDate> dates = calendarPanel.getDates();
            System.out.println("First date : " + dates.getFirst());
            System.out.println("Last date : " + dates.getLast());
            if (dates.getFirst() == dates.getLast()) {
                labelCP.setText(dates.getFirst().toString());
            } else {
                labelCP.setText(dates.getFirst() + " -- " + dates.getLast());
            }
        };
        calendarPanel.getDateSelectionModel().addChangeListener(l);
        panel.add(label, constraints);

        constraints.anchor = GridBagConstraints.CENTER;
        constraints.insets = new Insets(0, 0, 10, 0);
        constraints.gridy++;
        calendarPanel.setBorder(blackBorder);
        panel.add(calendarPanel, constraints);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.X_AXIS));
        textPanel.add(new JLabel("Set Date(s) : "));
        constraints.anchor = GridBagConstraints.ABOVE_BASELINE;
        constraints.insets = new Insets(0, 0, 10, 0);
        constraints.gridy++;
        textPanel.add(labelCP);
        panel.add(textPanel, constraints);
        return panel;
    }
}
