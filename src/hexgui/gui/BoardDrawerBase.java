//----------------------------------------------------------------------------
// $Id$
//----------------------------------------------------------------------------

package hexgui.gui;

import hexgui.hex.HexColor;
import hexgui.hex.HexPoint;

import javax.swing.*;          
import java.awt.*;
import java.awt.event.*;
import java.net.URL;

//----------------------------------------------------------------------------

/** Base class for board drawing.

    <p>Board drawers are responsible for drawing the background,
    labels, field outlines, and stone shadows.  In addition, they are
    also responsible for determining the the actual position of each
    field in the window.  Field contents (i.e. stones, markers,
    numerical values, etc) are not drawn, they are drawn with the
    GuiField class.

    <p>Board sizes supported are <code>m x n</code> where
    <code>m</code> and <code>n</code> range from 1 to 26.  By default,
    black connects top and bottom and should be labeled with letters.
    White connects left and right and should be labeled with numbers.
*/
public abstract class BoardDrawerBase
{
    public BoardDrawerBase()
    {
	m_background = null;
	m_aspect_ratio = 1.0;
    }

    /** Loads the image in <code>filename</code> and sets it as the background.
	If <code>filename</code> does not exist no background image is 
	displayed.  Image will be scaled to fit the window.
	@param filename filename of the image to use as a background.
    */
    public void loadBackground(String filename)
    {
        ClassLoader classLoader = getClass().getClassLoader();
        URL url = classLoader.getResource(filename);
        if (url == null) {
	    System.out.println("loadBackground: could not load '" + 
			       filename + "'!");
            m_background = null;            
	} else {
	    m_background = new ImageIcon(url).getImage();
	}
    }

    /** Gets the field containing the specified point.
	NOTE: uses the position of fields from the last call to draw().
	Also assumes the set of fields given are the same as those in the
	last call to draw(). 
	@param p the point
	@param field the set of fields to search through.
	@return the field in the set that p is in or <code>null</code> if
                p is not in any field.
    */
    public GuiField getFieldContaining(Point p, GuiField field[])
    {
	if (m_outline == null)
	    return null;
	for (int x=0; x<field.length; x++) {
	    if (m_outline[x].contains(p)) 
		return field[x];
	}
	return null;
    }

    /** Draws the board.
	The size of the region to draw to, the size of the board, and the
	field to draw must be given.  The position of each field is 
	then calculated and the board drawn. 
	FIXME: switch "flipped" parameter to a HexColor?
	@param g graphics context to draw to
	@param w the width of the region to draw in
	@param h the height of the region to draw in
	@param alphaontop true if letters are on top, otherwise numbers
	@param bw the width of the board (in fields)
	@param bh the height of the board (in fields)
    */
    public void draw(Graphics g, 
		     int w, int h, int bw, int bh, 
		     boolean alphaontop,
		     GuiField field[])
    {
	m_width = w;
	m_height = h;

	m_bwidth = bw;
	m_bheight = bh;

        m_alphaontop = alphaontop;

	computeFieldPlacement();
	m_outline = calcCellOutlines(field);

	setAntiAliasing(g);
	drawBackground(g);
	drawCells(g, field);
	drawLabels(g, alphaontop);
	drawShadows(g, field);
	drawFields(g, field);
        drawAlpha(g, field);
    }

    //------------------------------------------------------------

    /** Returns the location in the window of the field with
	coordinates <code>(x,y)</code>.  Coordinates increase to the
	right and down, with the top left of the board having
	coordinates <code>(0,0)</code>.  Negative values are acceptable.
	@param x the x coordinate of the field.
	@param y the y coordinate of the field.
	@return the center of the field at <code>(x,y)</code>.
    */
    protected abstract Point getLocation(int x, int y);

    /** Returns the location of the field with HexPoint pos. */
    protected Point getLocation(HexPoint pos)
    {
        if (m_alphaontop) {
            if (pos == HexPoint.NORTH)
                pos = HexPoint.SOUTH;
            else if (pos == HexPoint.SOUTH)
                pos = HexPoint.NORTH;
            else if (pos == HexPoint.EAST)
                pos = HexPoint.WEST;
            else if (pos == HexPoint.WEST)
                pos = HexPoint.EAST;
        }

	if (pos == HexPoint.NORTH) {
	    return getLocation(m_bwidth+1, m_bheight/2-1);
	} else if (pos == HexPoint.SOUTH) { 
	    return getLocation(-2, m_bheight/2+1);
	} else if (pos == HexPoint.EAST) { 
	    return getLocation(m_bwidth/2-1, m_bheight+1);
	} else if (pos == HexPoint.WEST) { 
	    return getLocation(m_bwidth/2+1, -2);
	}
	return getLocation(pos.x, pos.y);
    }

    /** Calculates the width of a field given the dimensions of the
	window and board.
	@param w width of window
	@param h height of window
	@param bw width of board
	@param bh height of board
    */
    protected abstract int calcFieldWidth(int w, int h, int bw, int bh);

    /** Calculates the height of a field given the dimensions of the
	window and board.
	@see calcFieldWidth
    */
    protected abstract int calcFieldHeight(int w, int h, int bw, int bh);

    
    protected abstract int calcStepSize();

    /** Calculates the width of the board in pixels. 
	@requires calcFieldWidth and calcFieldHeight to have been called.
    */
    protected abstract int calcBoardWidth();
    
    /** Calculates the height of the board in pixels.
	@requires calcFieldWidth and calcFieldHeight to have been called.
    */
    protected abstract int calcBoardHeight();

    /** Perfroms any necessary initializations for drawing the
	outlines of the fields.
	@param the fields it will need to draw
    */
    protected abstract Polygon[] calcCellOutlines(GuiField field[]);

    /** Draws the outlines of the given fields. 
	@param g graphics context to draw to.
	@param field the list of fields to draw.
    */
    protected void drawCells(Graphics g, GuiField field[])
    {
	g.setColor(Color.black);
	for (int i=0; i<m_outline.length; i++) {
	    if ((field[i].getAttributes() & GuiField.DRAW_CELL_OUTLINE) != 0) {
		g.drawPolygon(m_outline[i]);
	    }
	}

	g.setColor(Color.yellow);
	for (int i=0; i<m_outline.length; i++) {
	    if ((field[i].getAttributes() & GuiField.SELECTED) != 0) {
		g.drawPolygon(m_outline[i]);
	    }
	}
    }

    protected void computeFieldPlacement()
    {
	m_fieldWidth = calcFieldWidth(m_width, m_height, m_bwidth, m_bheight);
	m_fieldHeight = calcFieldHeight(m_width, m_height, m_bwidth, m_bheight);

	if (m_fieldHeight >= (int)(m_fieldWidth/m_aspect_ratio)) {
	    m_fieldHeight = (int)(m_fieldWidth/m_aspect_ratio);
	} else {
	    m_fieldWidth = (int)(m_fieldHeight*m_aspect_ratio);
	}

	// If field dimensions are not even then the inner cell lines
	// on the board can be doubled up.  
	// FIXME: lines still get doubled up...why?
	if ((m_fieldWidth & 1) != 0) m_fieldWidth--;
	if ((m_fieldHeight & 1) != 0) m_fieldHeight--;

	m_fieldRadius = (m_fieldWidth < m_fieldHeight) ? 
                         m_fieldWidth : m_fieldHeight;

	m_step = calcStepSize();

	int bw = calcBoardWidth();
	int bh = calcBoardHeight();
	m_marginX = (m_width - bw)/2 + m_fieldWidth/2;
	m_marginY = (m_height - bh)/2 + m_fieldHeight/2;
    }

    //------------------------------------------------------------

    protected int getShadowOffset()
    {
        return (m_fieldRadius - 2*GuiField.getStoneMargin(m_fieldRadius)) / 12;
    }

    protected void drawBackground(Graphics g)
    {
	if (m_background != null) 
	    g.drawImage(m_background, 0, 0, m_width, m_height, null);
    }

    protected void drawLabel(Graphics g, Point p, String string, int xoff)
    {
	FontMetrics fm = g.getFontMetrics();
	int width = fm.stringWidth(string);
	int height = fm.getAscent();
	int x = width/2;
	int y = height/2;
	g.drawString(string, p.x + xoff - x, p.y + y); 
    }

    protected abstract void drawLabels(Graphics g, boolean alphatop);

    protected void drawShadows(Graphics graphics, GuiField[] field)
    {
        if (m_fieldRadius <= 5)
            return;
        Graphics2D graphics2D =
            graphics instanceof Graphics2D ? (Graphics2D)graphics : null;
        if (graphics2D == null)
            return;
        graphics2D.setComposite(COMPOSITE_3);
        int size = m_fieldRadius - 2 * GuiField.getStoneMargin(m_fieldRadius);
        int offset = getShadowOffset();
        for (int pos = 0; pos < field.length; pos++) {
	    if (field[pos].getColor() == HexColor.EMPTY)
		continue;
	    Point location = getLocation(field[pos].getPoint());
	    graphics.setColor(Color.black);
	    graphics.fillOval(location.x - size / 2 + offset,
			      location.y - size / 2 + offset,
			      size, size);
	}
        graphics.setPaintMode();
    }

    protected void drawFields(Graphics g, GuiField field[])
    {
	for (int x=0; x<field.length; x++) {
	    Point p = getLocation(field[x].getPoint());
	    field[x].draw(g, p.x, p.y, m_fieldWidth, m_fieldHeight);
	}
    }

    protected void drawAlpha(Graphics g, GuiField field[])
    {
	if (g instanceof Graphics2D) {
	    Graphics2D g2d = (Graphics2D)g;

            for (int i=0; i<m_outline.length; i++) {
                if ((field[i].getAttributes() & GuiField.DRAW_ALPHA) == 0)
                    continue;

                Color color = field[i].getAlphaColor();
                if (color == null)
                    continue;

                g2d.setComposite(AlphaComposite.
                                 getInstance(AlphaComposite.SRC_OVER, 0.3f));
                
                g2d.setColor(color);
		g2d.fillPolygon(m_outline[i]);
	    }
	}
    }

    protected void setAntiAliasing(Graphics g)
    {
	if (g instanceof Graphics2D) {
	    Graphics2D g2d = (Graphics2D)g;
	    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				 RenderingHints.VALUE_ANTIALIAS_ON);
	}
    }

    protected boolean m_alphaontop;

    protected double m_aspect_ratio;

    protected Image m_background;

    protected int m_width, m_height;
    protected int m_bwidth, m_bheight;
    protected int m_marginX, m_marginY;
    protected int m_fieldWidth, m_fieldHeight, m_fieldRadius, m_step;
    protected Polygon m_outline[];

    protected static final AlphaComposite COMPOSITE_3
        = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f);

}

//----------------------------------------------------------------------------
