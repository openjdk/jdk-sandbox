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
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JDatePicker;
import javax.swing.JLabel;
import javax.swing.JPanel;

/*
 * @test
 * @enablePreview
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests the date picker date(s) selection
 * @run main/manual LocalizationTest
 */

public class LocalizationTest  {
    public static void main(String[] args) throws Exception {
        String instructions = """
                1. Four DatePickers with different locales is opened.
                2. Verify the respective localizations w.r.t month names,
                    week names and TextField formatter.
                """;

        PassFailJFrame.builder()
                .instructions(instructions)
                .testUI(LocalizationTest::getLocalisationSample)
                .build()
                .awaitAndCheck();
    }

    private static JPanel getLocalisationSample() {

        GridBagConstraints gbc = new GridBagConstraints();
        JPanel mainPanel = new JPanel(new GridBagLayout());

        JDatePicker datePicker1 = new JDatePicker(LocalDate.now(), Locale.ENGLISH);
        JDatePicker datePicker2 = new JDatePicker(LocalDate.now(), Locale.GERMAN);
        JDatePicker datePicker3 = new JDatePicker(LocalDate.now(), Locale.CHINESE);
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

    private static JPanel createLocalisationSampleRow(JDatePicker datePicker) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setPreferredSize(new Dimension(250, 80));
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel label = new JLabel(datePicker.getLocale().getDisplayName());
        panel.setBorder(BorderFactory.createLineBorder(Color.black));
        label.setPreferredSize(new Dimension(50, label.getPreferredSize().height));
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
