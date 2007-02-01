//----------------------------------------------------------------------------
// $Id$
//----------------------------------------------------------------------------

package hexgui.gui;

import javax.swing.*;          
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

import hexgui.hex.*;
import hexgui.util.*;

//----------------------------------------------------------------------------

public class GuiField
{
    public static final int DRAW_CELL_OUTLINE = 1;
    public static final int LAST_PLAYED = 2;
    public static final int SWAP_PLAYED = 4;
    public static final int DRAW_ALPHA = 8;

    private static final Color COLOR_STONE_BLACK = Color.decode("#030303");
    private static final Color COLOR_STONE_BLACK_BRIGHT = Color.decode("#666666");
    private static final Color COLOR_STONE_WHITE = Color.decode("#d7d0c9");
    private static final Color COLOR_STONE_WHITE_BRIGHT = Color.decode("#ffffff");


    public GuiField(HexPoint p)
    {
	this(p, HexColor.EMPTY, 0, null);
    }

    public GuiField(HexPoint p, HexColor c, int attributes, Color alpha)
    {
	m_point = p;
	m_color = c;
	m_alpha_color = alpha;
	m_attributes = attributes;
    }

    /** Creates a copy of the given field. */
    public GuiField(GuiField f)
    {
	this(f.getPoint(), f.getColor(), f.getAttributes(), 
	     f.getAlphaColor());
    }

    public static int getStoneMargin(int width)
    {
	return width / 17 + 1;
    }

    public void clearAttributes() { m_attributes = 0; }
    public void clearAttributes(int f) { m_attributes &= ~f; };
    public void setAttributes(int f)   { m_attributes |= f; }
    public int getAttributes() { return m_attributes; }
 
    public void setColor(HexColor c) { m_color = c; }
    public HexColor getColor() { return m_color; }

    public void setAlphaColor(Color c)
    {
	m_alpha_color = c;
	if (c == null) 
	    clearAttributes(DRAW_ALPHA);
	else 
	    setAttributes(DRAW_ALPHA);
    }

    public Color getAlphaColor() { return m_alpha_color; }

    public void setPoint(HexPoint p) { m_point = p; }
    public HexPoint getPoint() { return m_point; }
    
    public void clear()
    {
	setColor(HexColor.EMPTY);
    }

    private RadialGradientPaint getPaint(int width, 
					 int height,
                                         Color colorNormal,
                                         Color colorBright)
    {
        RadialGradientPaint paint;
        int paintSize;
	int size = (width < height) ? width : height;
        int radius = Math.max(size / 3, 1);
        Point2D.Double centerPoint =
            new Point2D.Double(width/2 - size/6, height/2 - size/6);
        Point2D.Double radiusPoint =
            new Point2D.Double(radius, radius);
        paint = new RadialGradientPaint(centerPoint, colorBright,
                                        radiusPoint, colorNormal);
        return paint;
    }

    public void draw(Graphics g, int x, int y, int w, int h)
    {
	if (!g.hitClip(x, y, w, h))
            return;

	m_width = w;
	m_height = h;

	m_radius = (h < w) ? h/2 : w/2;
	m_margin = getStoneMargin(m_radius*2);

	m_graphics = g.create(x-w/2,y-h/2,w,h);
	if (m_graphics instanceof Graphics2D)
	    m_graphics2D = (Graphics2D)m_graphics;
	else 
	    m_graphics2D = null;
	
	if (m_color == HexColor.WHITE) 
	    drawStone(COLOR_STONE_WHITE, COLOR_STONE_WHITE_BRIGHT);
	else if (m_color == HexColor.BLACK)
	    drawStone(COLOR_STONE_BLACK, COLOR_STONE_BLACK_BRIGHT);

	if ((m_attributes & LAST_PLAYED) != 0) 
	    drawLastPlayed();

        if ((m_attributes & SWAP_PLAYED) != 0)
            drawSwapPlayed();

	if ((m_attributes & DRAW_ALPHA) != 0) 
	    drawAlpha();
	
    }
    
    private void drawStone(Color normal, Color bright)
    {
	if (m_graphics2D != null) {
	    RadialGradientPaint paint = getPaint(m_width, m_height, 
						 normal, bright);
	    m_graphics2D.setPaint(paint);
	} else {
	    m_graphics.setColor(normal);
	}

	int size = m_radius - m_margin;
	m_graphics.fillOval(m_width/2 - size, m_height/2 - size,
			    size*2, size*2);

	m_graphics.setPaintMode();
    }

    private void drawLastPlayed()
    {
	m_graphics.setColor(Color.gray);
	m_graphics.fillOval(m_width/2 - 2, m_height/2 - 2, 4, 4);
    }

    private void drawSwapPlayed()
    {
        m_graphics.setColor(Color.white);
        m_graphics.drawString("S", m_width/2-3, m_height/2+3);
    }

    private void drawAlpha()
    {
	if (m_alpha_color == null)
	    return;
	if (m_graphics2D == null)
	    return;

	m_graphics2D.setComposite(AlphaComposite.
				  getInstance(AlphaComposite.SRC_OVER, 0.3f));
	m_graphics.setColor(m_alpha_color);
	m_graphics.fillRect(m_width/2 - m_width/4, m_height/2 - m_height/4,
			    m_width/2, m_height/2);

    }

    private HexPoint m_point;
    private HexColor m_color;
    private int m_attributes;

    private Color m_alpha_color;

    private int m_width;
    private int m_height;
    private int m_radius;
    private int m_margin;

    private Graphics m_graphics;
    private Graphics2D m_graphics2D;
}

//----------------------------------------------------------------------------
