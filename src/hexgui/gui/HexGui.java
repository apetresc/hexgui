//----------------------------------------------------------------------------
// $Id$
//----------------------------------------------------------------------------

package hexgui.gui;

import hexgui.hex.*;
import hexgui.util.Pair;
import hexgui.game.Node;
import hexgui.game.GameInfo;
import hexgui.sgf.SgfWriter;
import hexgui.sgf.SgfReader;
import hexgui.htp.HtpController;
import hexgui.htp.HtpError;
import hexgui.htp.StreamCopy;
import hexgui.version.Version;

import java.io.*;
import java.util.*;
import javax.swing.*;          
import java.awt.*;
import java.awt.event.*;
import java.net.*;

//----------------------------------------------------------------------------

/** HexGui. */
public final class HexGui 
    extends JFrame 
    implements ActionListener, GuiBoard.Listener, HtpShell.Callback,
               AnalyzeDialog.Callback
{
    public HexGui()
    {
        super("HexGui");

	System.out.println("HexGui v" + Version.id + "; " + Version.date 
			   + "; build " + Version.build + "\n");
	
	// Catch the close action and shutdown nicely
	setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
	addWindowListener(new java.awt.event.WindowAdapter() 
	    {
		public void windowClosing(WindowEvent winEvt) {
		    cmdShutdown();
		}
	    });

        m_selected_cells = new Vector<HexPoint>();

        m_about = new AboutDialog(this);

	m_preferences = new GuiPreferences(getClass());
	
	m_menubar = new GuiMenuBar(this, m_preferences);
	setJMenuBar(m_menubar.getJMenuBar());

	m_toolbar = new GuiToolBar(this, m_preferences);
        getContentPane().add(m_toolbar.getJToolBar(), BorderLayout.NORTH);

        m_statusbar = new StatusBar();
        getContentPane().add(m_statusbar, BorderLayout.SOUTH);

	m_guiboard = new GuiBoard(this, m_preferences);
        getContentPane().add(m_guiboard, BorderLayout.CENTER);

	cmdNewGame();

        pack();
        setVisible(true);
    }

    //------------------------------------------------------------
    public void actionPerformed(ActionEvent e) 
    {
// 	System.out.println("-----------------");
// 	System.out.println("Received Action Event: ");
// 	System.out.println(e.getActionCommand());
// 	System.out.println(e.paramString());

	String cmd = e.getActionCommand();

	//
	// system commands
	//
	if (cmd.equals("shutdown"))
	    cmdShutdown();
	else if (cmd.equals("connect-program"))
	    cmdConnectRemoteProgram();
	else if (cmd.equals("connect-local-program"))
	    cmdConnectLocalProgram();
	else if (cmd.equals("disconnect-program"))
	    cmdDisconnectProgram();
	//
	// file/help commands
	//
	else if (cmd.equals("newgame")) 
	    cmdNewGame();
	else if (cmd.equals("savegame"))
	    cmdSaveGame();
	else if (cmd.equals("savegameas"))
	    cmdSaveGameAs();
	else if (cmd.equals("loadgame"))
	    cmdLoadGame();
	else if (cmd.equals("about"))
	    cmdAbout();
	//
	// gui commands
	//
	else if (cmd.equals("gui_toolbar_visible"))
	    cmdGuiToolbarVisible();
	else if (cmd.equals("gui_shell_visible"))
	    cmdGuiShellVisible();
	else if (cmd.equals("gui_analyze_visible"))
            cmdGuiAnalyzeVisible();
	else if (cmd.equals("gui_board_draw_type"))
	    cmdGuiBoardDrawType();
	else if (cmd.equals("gui_board_orientation"))
	    cmdGuiBoardOrientation();
        else if (cmd.equals("show-preferences"))
            cmdShowPreferences();
	//
        // game navigation commands  
	//
        else if (cmd.equals("game_beginning"))
	    backward(1000);
	else if (cmd.equals("game_backward10"))
	    backward(10);
	else if (cmd.equals("game_back"))
	    backward(1);
	else if (cmd.equals("game_forward"))
	    forward(1);
	else if (cmd.equals("game_forward10"))
	    forward(10);
	else if (cmd.equals("game_end"))
	    forward(1000);
	else if (cmd.equals("game_up"))
	    up();
	else if (cmd.equals("game_down")) 
	    down();
        else if (cmd.equals("game_swap"))
            humanMove(new Move(HexPoint.get("swap-pieces"), m_tomove));
	else if (cmd.equals("genmove")) 
	    htpGenMove(m_tomove);
	else if (cmd.equals("stop")) {

	}
	//
	// unknown command
	//
	else {
	    System.out.println("Unknown command: '" + cmd + "'.");
	}
    }

    //------------------------------------------------------------
    private void cmdShutdown()
    {
	if (gameChanged() && !askSaveGame())
	    return;

	System.out.println("Shutting down...");

	if (m_white_process != null) {
	    System.out.println("Stopping [" + m_white_name + " " + 
			       m_white_version + "] process...");
	    m_white_process.destroy();
	}

	System.exit(0);
    }

    private void cmdConnectRemoteProgram()
    {
	int port = 20000;
	String hostname = "localhost";

        String remote = m_preferences.get("remote-host-name");
        String name = RemoteProgramDialog.show(this, remote);
        if (name == null) // user aborted
            return;

        hostname = name;
	System.out.print("Connecting to HTP program at [" + hostname + 
			 "] on port " + port + "...");
	System.out.flush();

	try {
	    m_white_socket = new Socket(hostname, port);
	}
	catch (UnknownHostException e) {
	    showError("Unknown host: '" + e.getMessage() + "'");
            System.out.println("\nconnection attempt aborted.");
	    return;
	}
	catch (IOException e) {
	    showError("Error creating socket: '" + e.getMessage() + "'");
            System.out.println("\nconnection attempt aborted.");
	    return;
	}
	System.out.println("connected.");


	InputStream in;
	OutputStream out;
	try {
	    in = m_white_socket.getInputStream();
	    out = m_white_socket.getOutputStream();
	}
	catch (IOException e) {
	    showError("Error obtaing socket stream: " + e.getMessage());
	    m_white = null;
	    return;
	}

        m_preferences.put("remote-host-name", hostname);

	connectProgram(in, out);
    }

    private void cmdConnectLocalProgram()
    {
	String defaultCommand = m_preferences.get("path-local-program");
        String prog = LocalProgramDialog.show(this, defaultCommand);
	if (prog == null) // user aborted
	    return;

	Runtime runtime = Runtime.getRuntime();
	String cmd = prog;
	System.out.println("Executing '" + cmd + "'...");
	try {
	    m_white_process = runtime.exec(cmd);
	}
	catch (Throwable e) {
	    showError("Error starting program: '" + e.getMessage() + "'");
	    return;
	}

	m_preferences.put("path-local-program", prog);

 	Process proc = m_white_process;

	///////////////////////////////
	/// FIXME: DEBUGING!!! REMOVE!
	Thread blah = new Thread(new StreamCopy(proc.getErrorStream(),
						System.out));
	blah.start();
	///////////////////////////////

	connectProgram(proc.getInputStream(), proc.getOutputStream());
    }

    private void connectProgram(InputStream in, OutputStream out)
    {
        m_analyze = new AnalyzeDialog(this, this);
	m_analyze.addWindowListener(new WindowAdapter() 
	    {
		public void windowClosing(WindowEvent winEvt) {
		    m_menubar.setAnalyzeVisible(false);
		}
	    });

	m_shell = new HtpShell(this, this);
	m_shell.addWindowListener(new WindowAdapter() 
	    {
		public void windowClosing(WindowEvent winEvt) {
		    m_menubar.setShellVisible(false);
		}
	    });
	m_white = new HtpController(in, out, m_shell);

	htpName();
	htpVersion();

	m_shell.setTitle("HexGui: [" + m_white_name + " " 
                            + m_white_version + "] Shell");

        htpListCommands();   // FIXME: make sure we block until its
                             // callback is finished (when we do stuff
                             // in separate threads. Not an issue now. 
		 
	m_toolbar.setProgramConnected(true);
	m_menubar.setProgramConnected(true);

	htpBoardsize(m_guiboard.getBoardSize());

        // play up to current move
	Node cur = m_root;
	while (cur != m_current) {
	    cur = cur.getChildContainingNode(m_current);
	    htpPlay(cur.getMove());
	}
    }

    private void cmdDisconnectProgram()
    {
	if (m_white == null) 
	    return;

	htpQuit();
	try {
	    if (m_white_process != null) {
		m_white_process.waitFor();
		m_white_process = null;
	    } 
	    if (m_white_socket != null) {
		m_white_socket.close();
		m_white_socket = null;
	    }
	    m_white = null;
	    m_shell.dispose();
	    m_shell = null;
            m_analyze.dispose();
            m_analyze = null;
	    m_menubar.setProgramConnected(false);
	    m_toolbar.setProgramConnected(false);
	}
	catch (Throwable e) {
	    showError("Error: " + e.getMessage());
	}
    }

    //------------------------------------------------------------

    private void cmdNewGame()
    {
	if (gameChanged() && !askSaveGame())
	    return;

	String size = m_menubar.getSelectedBoardSize();
	Dimension dim = new Dimension(-1,-1);
	if (size.equals("Other...")) {
	    size = BoardSizeDialog.show(this, m_guiboard.getBoardSize());
	    if (size == null) return;
	}

	try {
	    StringTokenizer st = new StringTokenizer(size);
	    int w = Integer.parseInt(st.nextToken());
	    st.nextToken();
	    int h = Integer.parseInt(st.nextToken());
	    dim.setSize(w,h);
	}
	catch (Throwable t) {
	    showError("Size should be in format 'w x h'.");
	    return;
	}

	if (dim.width < 1 || dim.height < 1) {
	    showError("Invalid board size.");
	} else {
	    m_tomove = HexColor.BLACK;
	    m_root = new Node();
	    m_current = m_root;
	    m_gameinfo = new GameInfo();
	    m_gameinfo.setBoardSize(dim);
	    m_file = null;
	    setGameChanged(false);
	    setFrameTitle();
	    
	    m_guiboard.initSize(dim.width, dim.height);
	    m_guiboard.repaint();

	    m_preferences.put("gui-board-width", dim.width);
	    m_preferences.put("gui-board-height", dim.height);

	    m_toolbar.updateButtonStates(m_current);
            m_menubar.updateMenuStates(m_current);

            htpBoardsize(m_guiboard.getBoardSize());
	}
    }

    private boolean cmdSaveGame()
    {
	if (m_file == null) 
	    m_file = showSaveAsDialog();
	
	if (m_file != null) {
	    System.out.println("Saving to file: " + m_file.getName());
	    if (save(m_file)) {
		setGameChanged(false);
		setFrameTitle();
		m_preferences.put("path-save-game", m_file.getPath());
		return true;
	    }
	}
	return false;
    }

    private boolean cmdSaveGameAs()
    {
	File file = showSaveAsDialog();
	if (file == null) 
	    return false;

	m_file = file;
	return cmdSaveGame();
    }

    private void cmdLoadGame()
    {
	if (gameChanged() && !askSaveGame())
	    return;

	File file = showOpenDialog();
	if (file == null) return;

	System.out.println("Loading sgf from file: " + file.getName());
	SgfReader sgf = load(file);
	if (sgf != null) {
	    m_root = sgf.getGameTree();
	    m_gameinfo = sgf.getGameInfo();
	    m_current = m_root;

	    m_guiboard.initSize(m_gameinfo.getBoardSize());
	    forward(1000);

	    m_file = file;
	    setGameChanged(false);
	    setFrameTitle();

	    m_preferences.put("path-load-game", file.getPath());
	}
    }

    private void cmdAbout()
    {
        m_about.setVisible(true);
    }

    //------------------------------------------------------------

    private void cmdGuiToolbarVisible()
    {
	boolean visible = m_menubar.getToolbarVisible();
	m_toolbar.setVisible(visible);
    }

    private void cmdGuiShellVisible()
    {
	if (m_shell == null) return;
	boolean visible = m_menubar.getShellVisible();
	m_shell.setVisible(visible);
    }

    private void cmdGuiAnalyzeVisible()
    {
	if (m_analyze == null) return;
	boolean visible = m_menubar.getAnalyzeVisible();
	m_analyze.setVisible(visible);
    }

    private void cmdGuiBoardDrawType()
    {
	String type = m_menubar.getCurrentBoardDrawType();
	System.out.println(type);
	m_guiboard.setDrawType(type);
	m_guiboard.repaint();
    }

    private void cmdGuiBoardOrientation()
    {
	String type = m_menubar.getCurrentBoardOrientation();
	System.out.println(type);
	m_guiboard.setOrientation(type);
	m_guiboard.repaint();
    }

    private void cmdShowPreferences()
    {
        new PreferencesDialog(this, m_preferences);
    }


    //------------------------------------------------------------

    /** Analyze dialog callback; calls the commandEntered method. */
    public void analyzeCommand(String cmd)
    {
        commandEntered(cmd);
    }

    /** HtpShell Callback. */
    // FIXME: do this the right way!!
    public void commandEntered(String cmd)
    {
	String c = cmd.trim();
	if (c.equals("name"))
	    htpName();
	else if (c.equals("version"))
	    htpVersion();
	else if (c.equals("genmove"))
	    htpGenMove(m_tomove);
	else if (c.equals("all_legal_moves"))
	    htpAllLegalMoves();
        else if (c.equals("mohex-show-rollout"))
            htpMohexShowRollout();
	else if (c.equals("quit"))
	    htpQuit();
	else
	    sendCommand(cmd, null);
    }

    private void sendCommand(String cmd, Runnable callback)
    {
	if (m_white == null) 
	    return;

	try {
	    m_white.sendCommand(cmd, callback);
	}
	catch (HtpError e) {
            showError(e.getMessage());
	}
    }

    // FIXME: add callback?
    private void htpQuit()
    {
	sendCommand("quit\n", null);
    }

    // FIXME: handle errors!
    public void cbName()
    {
	String str = m_white.getResponse();
	m_white_name = str.trim();
    }

    private void htpName()
    {
	Runnable callback = new Runnable() 
	    { 
		public void run() { cbName(); } 
	    };
	sendCommand("name\n", callback);
    }

    // FIXME: handle errors!
    public void cbVersion()
    {
	String str = m_white.getResponse();
	m_white_version = str.trim();
    }

    private void htpVersion()
    {
	Runnable callback = new Runnable()
	    {
		public void run() { cbVersion(); }
	    };
	sendCommand("version\n", callback);
    }

    private void cbListCommands()
    {
        if (m_analyze == null) {
            System.out.println("No analyze dialog!!");
            return;
        }
        
        String str = m_white.getResponse();
	Vector<String> cmds = HtpController.parseStringList(str);
        Collections.sort(cmds);
        m_analyze.setCommands(cmds);

    }

    private void htpListCommands()
    {
	Runnable callback = new Runnable()
	    {
		public void run() { cbListCommands(); }
	    };
	sendCommand("list_commands\n", callback);
    }


    // FIXME: check for errors
    public void cbEmptyResponse()
    {
    }

    private void htpPlay(Move move)
    {
	Runnable callback = new Runnable()
	    {
		public void run() { cbEmptyResponse(); }
	    };

	sendCommand("play " + move.getColor().toString() + 
		    " " + move.getPoint().toString() + "\n", 
		    callback);
	sendCommand("showboard\n", null);
    }

    private void htpUndo()
    {
	Runnable callback = new Runnable()
	    {
		public void run() { cbEmptyResponse(); }
	    };

	sendCommand("undo\n", callback);
	sendCommand("showboard\n", null);
    }

    private void htpBoardsize(Dimension size)
    {
	Runnable callback = new Runnable()
	    {
		public void run() { cbEmptyResponse(); }
	    };

        sendCommand("boardsize " + size.width + " " + size.height + "\n",
                    callback);
        sendCommand("showboard\n", null);
    }

    public void cbGenMove()
    {
	String str = m_white.getResponse();
	HexPoint point = HexPoint.get(str.trim());
	if (point == null) {
	    System.out.println("Invalid move!!");
	} else {
	    play(new Move(point, m_tomove));
	}
    }

    private void htpGenMove(HexColor color)
    {
	Runnable callback = new Runnable() 
	    { 
		public void run() { cbGenMove(); } 
	    };
	sendCommand("genmove " + color.toString() + "\n", callback);
	sendCommand("showboard\n", null);
    }

    public void cbAllLegalMoves()
    {
	if (!m_white.wasSuccess()) 
	    return;

	String str = m_white.getResponse();
	Collection<HexPoint> points = HtpController.parsePointList(str);
	Iterator<HexPoint> it = points.iterator();
	while (it.hasNext()) {
	    HexPoint p = it.next();
	    m_guiboard.setAlphaColor(p, Color.green);
	}
	m_guiboard.repaint();
    }

    private void htpAllLegalMoves()
    {
	Runnable callback = new Runnable() 
	    { 
		public void run() { cbAllLegalMoves(); } 
	    };
	sendCommand("all_legal_moves\n", callback);
    }

    //==================================================
    // commands specific to mohex
    //==================================================

    public void cbMohexShowRollout()
    {
	if (!m_white.wasSuccess()) 
	    return;

	String str = m_white.getResponse();
        Vector<Pair<String, String> > pairs = 
            HtpController.parseStringPairList(str);

        for (int i=0; i<pairs.size(); i++) {
	    HexPoint p = HexPoint.get(pairs.get(i).first);
            String value = pairs.get(i).second;
            if (value.equals("#"))
                m_guiboard.setAlphaColor(p, Color.green);
            else
                m_guiboard.setAlphaColor(p, Color.red);

            m_guiboard.setText(p,Integer.toString(i));
	}
	m_guiboard.repaint();
    }

    private void htpMohexShowRollout()
    {
        // FIXME: prompt user to select a cell
        if (m_selected_cells.size() < 1)
            return;

        HexPoint point = m_selected_cells.get(0);

	Runnable callback = new Runnable() 
	    { 
		public void run() { cbMohexShowRollout(); } 
	    };

	sendCommand("mohex-show-rollout " + m_tomove.toString() 
                    + " " + point.toString() + "\n",
                    callback);
    }
    
    //------------------------------------------------------------


    /** Callback from GuiBoard. 
	Handle a mouse click.
    */
    public void fieldClicked(HexPoint point, boolean ctrl, boolean shift)
    {
        if (ctrl) {

            if (!shift) {
                for (int i=0; i<m_selected_cells.size(); i++) {
                    HexPoint p = m_selected_cells.get(i);
                    m_guiboard.setSelected(p, false);
                }
                m_selected_cells.clear();

                m_guiboard.setSelected(point, true);
                m_selected_cells.add(point);
            } else {
                
                int found_at = -1;
                for (int i=0; i<m_selected_cells.size() && found_at == -1; i++) {
                    if (m_selected_cells.get(i) == point) 
                        found_at = i;
                }
           
                if (found_at != -1) {
                    m_guiboard.setSelected(point, false);
                    m_selected_cells.remove(found_at);
                } else {
                    m_guiboard.setSelected(point, true);
                    m_selected_cells.add(point);
                }
            }

            m_guiboard.repaint();
            
        } else {

            if (m_guiboard.getColor(point) == HexColor.EMPTY) {
                humanMove(new Move(point, m_tomove));
            }

        }
    }

    public void humanMove(Move move)
    {
	play(move);
	htpPlay(move);
        if (!m_guiboard.isBoardFull())
            htpGenMove(m_tomove);
    }

    private void play(Move move)
    {
        int variation = -1;
        for (int i=0; i<m_current.numChildren(); i++) {
            if (move.equals(m_current.getChild(i).getMove())) {
                variation = i;
                break;
            }
	}

	if (variation != -1) {

	    m_current = m_current.getChild(variation);

	} else {

            if (move.getPoint() == HexPoint.SWAP_PIECES) {
                if (!m_current.isSwapAllowed()) {
                    showError("Swap move not allowed!");
                    return;
                }
            } else {
                if (m_guiboard.getColor(move.getPoint()) !=  HexColor.EMPTY) {
                    showError("Cell '" + move.getPoint().toString() + 
                              "' already occupied.");
                    return;
                }
            }

	    Node node = new Node(move);
	    m_current.addChild(node);
	    m_current = node;
	}

        if (m_current.getMove().getPoint() != HexPoint.SWAP_PIECES)
            m_tomove = m_tomove.otherColor();

	m_guiboard.setColor(m_current.getMove().getPoint(), 
                            m_current.getMove().getColor());

        m_guiboard.clearMarks();
	markLastPlayedStone();

	m_guiboard.paintImmediately();
	m_toolbar.updateButtonStates(m_current);	
        m_menubar.updateMenuStates(m_current);

	setGameChanged(true);
	setFrameTitle();
    }

    private void forward(int n)
    {
	for (int i=0; i<n; i++) {
	    Node child = m_current.getChild();
	    if (child == null) break;

	    Move move = child.getMove();
	    m_guiboard.setColor(move.getPoint(), move.getColor());
	    htpPlay(move);

            if (move.getPoint() != HexPoint.SWAP_PIECES)
                m_tomove = move.getColor().otherColor();

	    m_current = child;
	}

        m_guiboard.clearMarks();
	markLastPlayedStone();

	m_guiboard.repaint();
	m_toolbar.updateButtonStates(m_current);
        m_menubar.updateMenuStates(m_current);
    }

    private void backward(int n)
    {
	for (int i=0; i<n; i++) {
	    if (m_current == m_root) break;

	    Move move = m_current.getMove();
	    m_guiboard.setColor(move.getPoint(), HexColor.EMPTY);
            htpUndo();

	    m_current = m_current.getParent();
	}

        m_guiboard.clearMarks();
	markLastPlayedStone();

	m_guiboard.repaint();
	m_toolbar.updateButtonStates(m_current);
        m_menubar.updateMenuStates(m_current);
	    
	if (m_current == m_root) 
	    m_tomove = HexColor.BLACK;
	else if (m_current.getMove().getPoint() != HexPoint.SWAP_PIECES)
	    m_tomove = m_current.getMove().getColor().otherColor();
    }

    private void up()
    {
	if (m_current.getNext() != null) {
	    HexPoint point = m_current.getMove().getPoint();
	    m_guiboard.setColor(point, HexColor.EMPTY);
            htpUndo();
	    
	    m_current = m_current.getNext();
	    
	    HexColor color = m_current.getMove().getColor();
	    point = m_current.getMove().getPoint();
	    m_guiboard.setColor(point, color);
	    htpPlay(m_current.getMove());
            if (point != HexPoint.SWAP_PIECES)
                m_tomove = color.otherColor();

            m_guiboard.clearMarks();	    
	    markLastPlayedStone();

	    m_guiboard.repaint();
	    m_toolbar.updateButtonStates(m_current);
            m_menubar.updateMenuStates(m_current);
	}
    }

    private void down()
    {
	if (m_current.getPrev() != null) {
	    HexPoint point = m_current.getMove().getPoint();
	    m_guiboard.setColor(point, HexColor.EMPTY);
            htpUndo();
	    
	    m_current = m_current.getPrev();
	    
	    HexColor color = m_current.getMove().getColor();
	    point = m_current.getMove().getPoint();
	    m_guiboard.setColor(point, color);
	    htpPlay(m_current.getMove());
            if (point != HexPoint.SWAP_PIECES)
                m_tomove = color.otherColor();
	    
            m_guiboard.clearMarks();
	    markLastPlayedStone();

	    m_guiboard.repaint();
	    m_toolbar.updateButtonStates(m_current);
            m_menubar.updateMenuStates(m_current);
	}
    }


    //------------------------------------------------------------

    private void markLastPlayedStone()
    {
        if (m_current == m_root) {
            m_guiboard.markSwapPlayed(null);
	    m_guiboard.markLastPlayed(null);            
            return;
        }
        
        Move move = m_current.getMove();

        if (move.getPoint() == HexPoint.SWAP_PIECES) {
            Node parent = m_current.getParent();
            assert(parent != null);

            m_guiboard.markLastPlayed(null);
            m_guiboard.markSwapPlayed(parent.getMove().getPoint());

        } else {
            m_guiboard.markLastPlayed(move.getPoint());
            m_guiboard.markSwapPlayed(null);
        }
    }	

    private void setGameChanged(boolean changed) 
    {
	m_gameChanged = changed;
    }

    private boolean gameChanged()
    {
	return m_gameChanged;
    }

    private void setFrameTitle()
    {
	String filename = "untitled";
	if (m_file != null) filename = m_file.getName();
	if (gameChanged()) filename = filename + "*";
	String name = "HexGui " + Version.id;
	if (m_white != null) 
	    name += " - [" + m_white_name + " " + m_white_version + "]";
	setTitle(name + " - " + filename);
    }

    /** Returns false if action was aborted. */
    private boolean askSaveGame()
    {
	Object options[] = {"Save", "Discard", "Cancel"};
	int n = JOptionPane.showOptionDialog(this,
					     "Game has changed.  Save changes?",
					     "Save Game?",
					     JOptionPane.YES_NO_CANCEL_OPTION,
					     JOptionPane.QUESTION_MESSAGE,
					     null,
					     options,
					     options[0]);
	if (n == 0) {
	    if (cmdSaveGame()) 
		return true;
	    return false;
	} else if (n == 1) {
	    return true;
	}
	return false;
    }

    /** Save game to file.
	@return true If successful. 
    */
    private boolean save(File file)
    {
	FileOutputStream out;
	try {
	    out = new FileOutputStream(file);
	}
	catch (FileNotFoundException e) {
	    showError("File not found!");
	    return false;
	}
	
	new SgfWriter(out, m_root, m_gameinfo);
	return true;
    }

    /* Load game from file. */
    private SgfReader load(File file)
    {
	FileInputStream in;
	try {
	    in = new FileInputStream(file);
	}
	catch(FileNotFoundException e) {
	    showError("File not found!");
	    return null;
	}

	SgfReader sgf;
	try {
	    sgf = new SgfReader(in);
	}
	catch (SgfReader.SgfError e) {
	    showError("Error reading SGF file:\n \"" + e.getMessage() + "\"");
	    return null;
	}
	
	return sgf;
    }

    //------------------------------------------------------------

    /** Show a simple error message dialog. */
    private void showError(String msg)
    {
	JOptionPane.showMessageDialog(this, msg, "Error",
				      JOptionPane.ERROR_MESSAGE);
    }
    
    /** Show save dialog, return File of selected filename.  
	@return null If aborted.
    */
    private File showSaveAsDialog()
    {
	JFileChooser fc = new JFileChooser(m_preferences.get("path-save-game"));
	if (m_file != null) fc.setSelectedFile(m_file);
	int ret = fc.showSaveDialog(this);
	if (ret == JFileChooser.APPROVE_OPTION)
	    return fc.getSelectedFile();
	return null;
    }

    /** Show open dialog, return File of selected filename.  
	@return null If aborted.
    */
    private File showOpenDialog()
    {
	JFileChooser fc = new JFileChooser(m_preferences.get("path-load-game"));
	int ret = fc.showOpenDialog(this);
	if (ret == JFileChooser.APPROVE_OPTION)
	    return fc.getSelectedFile();
	return null;
    }

    private AboutDialog m_about;
    private GuiPreferences m_preferences;
    private GuiBoard m_guiboard;
    private GuiToolBar m_toolbar;
    private StatusBar m_statusbar;
    private GuiMenuBar m_menubar;
    private HtpShell m_shell;
    private AnalyzeDialog m_analyze;

    private Node m_root;
    private Node m_current;
    private GameInfo m_gameinfo;
    private HexColor m_tomove;
    private boolean m_gameChanged;

    private Vector<HexPoint> m_selected_cells;

    private HtpController m_white;
    private String m_white_name;
    private String m_white_version;
    private Process m_white_process;
    private Socket m_white_socket;

    private File m_file;
}

//----------------------------------------------------------------------------
