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

package sun.swing.calendarpanel;

import java.util.HashMap;
import javax.swing.AbstractCalendarPanel;
import javax.swing.DateSelectionModel;

import jdk.internal.javac.PreviewFeature;

/**
 * A class designed to produce preconfigured "accessory" objects to
 * insert into calendar panels.
 *
 * @since 99
 */

@PreviewFeature(feature = PreviewFeature.Feature.JDATEPICKER)
public class CalendarPanelComponentFactory {
    private CalendarPanelComponentFactory() {
    }

    /**
     * Return the default calendar panels. Day, month and year
     * selection default panels.
     *
     * @return the default calendar panels
     */
    public static HashMap<DateSelectionModel.CalendarPanelType,
            AbstractCalendarPanel> getDefaultCalendarPanel() {
        HashMap<DateSelectionModel.CalendarPanelType, AbstractCalendarPanel>
                calendarPanelHashMap = new HashMap<>();
        calendarPanelHashMap.put(DateSelectionModel.CalendarPanelType.DAY_SELECTION,
                new DefaultDateSelectionPanel());
        calendarPanelHashMap.put(DateSelectionModel.CalendarPanelType.MONTH_SELECTION,
                new DefaultMonthSelectionPanel());
        calendarPanelHashMap.put(DateSelectionModel.CalendarPanelType.YEAR_SELECTION,
                new DefaultYearSelectionPanel());
        return calendarPanelHashMap;
    }
}
