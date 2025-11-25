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

package sun.swing.calendarpanel.icons;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.Serializable;
import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;

/**
 * Calendar icon class.
 *
 */
@SuppressWarnings("serial")// JDK-implementation class
public class CalendarIcon implements Icon, UIResource, Serializable {
    private static final int HEIGHT = 16;
    private static final int WIDTH = 16;

    // The Color to use, may be null indicating colorKey should be used
    private Color color;

    // If non-null indicates the color should come from the UIManager with this key
    private String colorKey;

    /**
     * Creates a <code>CalendarIcon</code>.
     *
     * @param color the color to render the icon
     */
    public CalendarIcon(Color color) {
        this.color = color;
        if (color == null) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Creates a <code>CalendarIcon</code>.
     *
     * @param colorKey the key used to find color in UIManager
     */
    public CalendarIcon(String colorKey) {
        this.colorKey = colorKey;
        if (colorKey == null) {
            throw new IllegalArgumentException();
        }
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getIconWidth();
        int height = getIconHeight();

        g2.setColor(getColor());
        g2.fillRoundRect(x, y, width, height, 4, 4);
        g2.setColor(color);
        g2.fillRoundRect(x, y, width, height, 4, 4);

        int headerHeight = height / 5;
        g2.setColor(getColor());
        g2.fillRect(x, y, width, headerHeight);
        g2.drawLine(x, y + headerHeight, x + width, y + headerHeight);

        int padding = 2;
        int cellSize = (width - 2 * padding) / 3;
        int gridY = y + headerHeight + padding;
        for (int row = 0; row < 3; row++) {
            int gridX = x + padding + 1;
            for (int col = 0; col < 3; col++) {
                g2.setColor(Color.WHITE);
                g2.fillRect(gridX, gridY, cellSize - 2, cellSize - 2);
                gridX += cellSize;
            }
            gridY += cellSize;
        }
        g2.dispose();
    }

    public int getIconWidth() {
        return WIDTH;
    }

    public int getIconHeight() {
        return HEIGHT;
    }

    private Color getColor() {
        if (color != null) {
            return color;
        }
        return UIManager.getColor(colorKey);
    }
}
