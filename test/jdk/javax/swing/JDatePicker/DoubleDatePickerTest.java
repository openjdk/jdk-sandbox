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
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DateSelectionModel;
import javax.swing.JDatePicker;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;

/*
 * @test
 * @enablePreview
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests the date picker date(s) selection
 * @run main/manual DoubleDatePickerTest
 */

public class DoubleDatePickerTest {
    public static void main(String[] args) throws Exception {
        String instructions = """
                1. Two DatePickers are shown.
                2. Select start date from first DatePicker and end date from
                    second DatePicker.
                3. Verify that the dates allowed for selection from first
                    DatePicker is before the date selected from second DatePicker
                    and the dates allowed for selection from second DatePicker
                    is after the date selected from first DatePicker.
                """;

        PassFailJFrame.builder()
                .instructions(instructions)
                .testUI(DoubleDatePickerTest::doubleDatePicker)
                .build()
                .awaitAndCheck();
    }

    private static JPanel doubleDatePicker() {
        JLabel label1 = new JLabel("Start Date ");
        JLabel label2 = new JLabel("End Date ");
        JPanel panel1 = new JPanel(new GridBagLayout());
        JPanel panel2 = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        JPanel mainPanel = new JPanel(new GridBagLayout());
        JLabel setDateLabel1 = new JLabel();
        JLabel setDateLabel2 = new JLabel();
        JLabel setSelectableLabel1 = new JLabel("setSelectableDateRange(null, null);");
        JLabel setSelectableLabel2 = new JLabel("setSelectableDateRange(null, null);");

        JDatePicker datePicker1 = new JDatePicker();
        JDatePicker datePicker2 = new JDatePicker();
        datePicker1.setDateSelectionMode(DateSelectionModel.SelectionMode.SINGLE_SELECTION);
        datePicker2.setDateSelectionMode(DateSelectionModel.SelectionMode.SINGLE_SELECTION);
        datePicker2.setDate(datePicker1.getDate().plusMonths(1));

        ChangeListener l = e -> {
            if (((DateSelectionModel) e.getSource()).getEventType() == DateSelectionModel.EventType.DATE_SELECTION) {
                datePicker2.setSelectableDateRange(datePicker1.getDate(), null);
                setDateLabel1.setText(datePicker1.getDate().toString());
                setSelectableLabel1.setText("datePicker2.setSelectableDateRange(" + datePicker1.getDate()+", null);");
            }
        };

        ChangeListener l2 = e -> {
            if (((DateSelectionModel) e.getSource()).getEventType() == DateSelectionModel.EventType.DATE_SELECTION) {
                datePicker1.setSelectableDateRange(null, datePicker2.getDate());
                setDateLabel2.setText(datePicker2.getDate().toString());
                setSelectableLabel2.setText("datePicker1.setSelectableDateRange(" + datePicker2.getDate()+", null);");
            }
        };
        datePicker1.getCalendarPanel().getDateSelectionModel().addChangeListener(l);
        datePicker2.getCalendarPanel().getDateSelectionModel().addChangeListener(l2);

        System.out.println(datePicker1.getDates());
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
        textPanel1.add(new JLabel("Set Date(s) : "));
        textPanel1.add(setDateLabel1);
        JPanel textPanel2 = new JPanel();
        textPanel2.setLayout(new BoxLayout(textPanel2, BoxLayout.X_AXIS));
        textPanel2.add(new JLabel("Set Date(s) : "));
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
}
