//----------------------------------------------------------------------------
// $Id$
//----------------------------------------------------------------------------

package hexgui.gui;

import hexgui.hex.HexColor;
import hexgui.game.Clock;

import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.*;

/** Displays info about the current game. */
public class GameInfoPanel
    extends JPanel
{
    public GameInfoPanel(Clock blackClock, Clock whiteClock)
    {
        JPanel panel = new JPanel();
        add(panel, BorderLayout.CENTER);
        
        JPanel bpanel = new JPanel();
        bpanel.setLayout(new BoxLayout(bpanel, BoxLayout.Y_AXIS));
        JLabel blab = new JLabel(new ImageIcon("hexgui/images/black-24x24.png"));
        blab.setAlignmentX(Component.CENTER_ALIGNMENT);
        bpanel.add(blab);
        bpanel.add(new GuiClock(HexColor.BLACK, blackClock));

        JPanel wpanel = new JPanel();
        wpanel.setLayout(new BoxLayout(wpanel, BoxLayout.Y_AXIS));
        JLabel wlab = new JLabel(new ImageIcon("hexgui/images/white-24x24.png"));
        wlab.setAlignmentX(Component.CENTER_ALIGNMENT);
        wpanel.add(wlab);
        wpanel.add(new GuiClock(HexColor.WHITE, whiteClock));
       
        panel.add(bpanel);
        panel.add(wpanel);

        //setPreferredSize(new Dimension(200, 150));
    }

};

class GuiClock
    extends JTextField
    implements Clock.Listener
{
    public GuiClock(HexColor color, Clock clock)
    {
        super(COLUMNS);
        
        m_clock = clock;
        m_clock.addListener(this);

        //Monspace font doesn't center correctly on the Mac
        //GuiUtil.setMonospacedFont(this);
        setEditable(false);
        setHorizontalAlignment(SwingConstants.CENTER);
        //setMinimumSize(getPreferredSize());
        m_color = color;
        setText("00:00");
    }

    public final void setText(String text)
    {
        super.setText(text);
        String toolTip;
        if (m_color == HexColor.BLACK)
            toolTip = "Time for Black";
        else
            toolTip = "Time for White";
        if (text.length() > COLUMNS)
            toolTip = toolTip + " (" + text + ")";
        setToolTipText(toolTip);
    }

    public void clockChanged()
    {
        int elapsed = m_clock.elapsed();
        int minutes = elapsed / 60000;
        int seconds = (elapsed % 60000) / 1000;
        String min, sec;
        
        min = (minutes < 10) ? "0"+minutes : "" + minutes;
        sec = (seconds < 10) ? "0"+seconds : "" + seconds;
        setText(min+":"+sec);
    }

    /** Serial version to suppress compiler warning.
        Contains a marker comment for serialver.sf.net
    */
    private static final long serialVersionUID = 0L; // SUID

    private static final int COLUMNS = 8;

    private final HexColor m_color;

    private Clock m_clock;
}

//----------------------------------------------------------------------------
