/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;

public class ChevronIcon implements Icon {
    public enum Direction {
        LEFT, RIGHT
    }
    private final Direction direction;
    private final int chevronCount;
    private final int width;
    private final int height;
    private final Color color;
    private final float strokeWidth;

    public ChevronIcon(Direction direction,
                       int chevronCount,
                       int width,
                       int height,
                       Color color,
                       float strokeWidth) {
        this.direction = direction;
        this.chevronCount = chevronCount;
        this.width = width;
        this.height = height;
        this.color = color;
        this.strokeWidth = strokeWidth;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.translate(x, y);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(
                    strokeWidth,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));

            int gap = Math.max(2, width / 12);
            int singleW = (chevronCount == 1)
                    ? width
                    : (width - gap) / 2;

            for (int i = 0; i < chevronCount; i++) {
                int offsetX = i * (singleW + gap);
                Shape chevron = createChevronShape(offsetX, 0, singleW, height, direction);
                g2.draw(chevron);
            }
        } finally {
            g2.dispose();
        }
    }

    private Shape createChevronShape(int x, int y, int w, int h, Direction dir) {
        float left = x + w * 0.70f;
        float right = x + w * 0.30f;
        float top = y + h * 0.20f;
        float mid = y + h * 0.50f;
        float bottom = y + h * 0.80f;

        Path2D path = new Path2D.Float();
        if (dir == Direction.LEFT) {
            path.moveTo(left, top);
            path.lineTo(right, mid);
            path.lineTo(left, bottom);
        } else {
            path.moveTo(right, top);
            path.lineTo(left, mid);
            path.lineTo(right, bottom);
        }
        return path;
    }

    @Override
    public int getIconWidth() {
        return width;
    }

    @Override
    public int getIconHeight() {
        return height;
    }
}
