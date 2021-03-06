/*
 * Copyright (C) 2014 University of Toronto, Computational Biology Lab.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.ut.biolab.medsavant.client.view.component;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import org.ut.biolab.medsavant.client.view.util.ViewUtil;

/**
 *
 * @author mfiume
 */
public class MSTabbedPaneUI extends BasicTabbedPaneUI {

    private static final Insets NO_INSETS = new Insets(0, 0, 0, 0);
    
    private Color selectedColor;
    private boolean paintColorSet;

    private Color unselectedColor;
    private Color lineColor = new Color(192, 192, 192);
    private Insets contentInsets = new Insets(10, 10, 10, 10);
    private int lastRollOverTab = -1;

    int underlineHeight = 4;
    int tabHeight = 35;

    public static ComponentUI createUI(JComponent c) {
        return new MSTabbedPaneUI();
    }
    
    public MSTabbedPaneUI() {
        this(ViewUtil.getMedSavantBlueColor());
    }
    
    public void setPaintColorSet(boolean b) {
        this.paintColorSet = b;
    }
    
    public MSTabbedPaneUI(Color selectedColor) {

        Color unselectedColor = Color.white;

        this.selectedColor = selectedColor;
        this.unselectedColor = unselectedColor;

        setContentInsets(0);
    }

    public void setContentInsets(Insets i) {
        contentInsets = i;
    }

    public void setContentInsets(int i) {
        contentInsets = new Insets(i, i, i, i);
    }

    public int getTabRunCount(JTabbedPane pane) {
        return 1;
    }

    protected void installDefaults() {
        super.installDefaults();

        RollOverListener l = new RollOverListener();
        tabPane.addMouseListener(l);
        tabPane.addMouseMotionListener(l);

        tabAreaInsets = NO_INSETS;
        tabInsets = new Insets(0, 0, 0, 1);
    }

    protected boolean scrollableTabLayoutEnabled() {
        return false;
    }

    protected Insets getContentBorderInsets(int tabPlacement) {
        return contentInsets;
    }

    protected int calculateTabHeight(int tabPlacement, int tabIndex,
            int fontHeight) {
        return tabHeight;
    }

    protected int calculateTabWidth(int tabPlacement, int tabIndex,
            FontMetrics metrics) {
        int w = super.calculateTabWidth(tabPlacement, tabIndex, metrics);
        int wid = metrics.charWidth('M');
        w += wid * 2;
        return w;
    }

    protected int calculateMaxTabHeight(int tabPlacement) {
        return 35;
    }

    protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
        Graphics2D g2d = (Graphics2D) g;

        g2d.setColor(unselectedColor);
        g2d.fillRect(0, 0, tabPane.getWidth(), tabPane.getHeight());

        super.paintTabArea(g, tabPlacement, selectedIndex);

        g2d.setColor(lineColor);
        g2d.drawLine(0, tabHeight-1, tabPane.getWidth() - 1, tabHeight);
    }

    protected void paintTabBackground(Graphics g, int tabPlacement,
            int tabIndex, int x, int y, int w, int h, boolean isSelected) {
        Graphics2D g2d = (Graphics2D) g;
        Color color;

        Rectangle rect = rects[tabIndex];

        Color thisColor = selectedColor;
        
        if (paintColorSet) {
            thisColor = ViewUtil.getColor(tabIndex, tabPane.getTabCount());
        }
        
        if (isSelected) {
            color = thisColor;
        } else if (getRolloverTab() == tabIndex) {
            color = new Color(thisColor.getRed(),thisColor.getGreen(),thisColor.getBlue(),150);
        } else {
            color = unselectedColor;
        }

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int width = rect.width;
        int xpos = rect.x;
        if (tabIndex > 0) {
            width--;
            xpos++;
        }

        g2d.setColor(color);
        g2d.fillRect(xpos, h - underlineHeight, width, underlineHeight);
    }

    protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
            int x, int y, int w, int h, boolean isSelected) {
    }

    protected void paintContentBorderTopEdge(Graphics g, int tabPlacement,
            int selectedIndex, int x, int y, int w, int h) {

    }

    protected void paintContentBorderRightEdge(Graphics g, int tabPlacement,
            int selectedIndex, int x, int y, int w, int h) {
        // Do nothing
    }

    protected void paintContentBorderLeftEdge(Graphics g, int tabPlacement,
            int selectedIndex, int x, int y, int w, int h) {
        // Do nothing
    }

    protected void paintContentBorderBottomEdge(Graphics g, int tabPlacement,
            int selectedIndex, int x, int y, int w, int h) {
        // Do nothing
    }

    protected void paintFocusIndicator(Graphics g, int tabPlacement,
            Rectangle[] rects, int tabIndex, Rectangle iconRect,
            Rectangle textRect, boolean isSelected) {
        // Do nothing
    }

    protected int getTabLabelShiftY(int tabPlacement, int tabIndex,
            boolean isSelected) {
        return 0;
    }

    private class RollOverListener implements MouseMotionListener,
            MouseListener {

        public void mouseDragged(MouseEvent e) {
        }

        public void mouseMoved(MouseEvent e) {
            checkRollOver();
        }

        public void mouseClicked(MouseEvent e) {
        }

        public void mousePressed(MouseEvent e) {
        }

        public void mouseReleased(MouseEvent e) {
        }

        public void mouseEntered(MouseEvent e) {
            checkRollOver();
        }

        public void mouseExited(MouseEvent e) {
            tabPane.repaint();
        }

        private void checkRollOver() {
            int currentRollOver = getRolloverTab();
            if (currentRollOver != lastRollOverTab) {
                lastRollOverTab = currentRollOver;
                Rectangle tabsRect = new Rectangle(0, 0, tabPane.getWidth(), tabPane.getHeight());
                tabPane.repaint(tabsRect);
            }
        }
    }

}
