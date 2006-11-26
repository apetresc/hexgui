//----------------------------------------------------------------------------
// $Id$ 
//----------------------------------------------------------------------------

package hexgui.sgf;

import hexgui.hex.HexColor;
import hexgui.hex.HexPoint;
import hexgui.hex.Move;
import hexgui.game.Node;
import hexgui.game.GameInfo;

import java.io.*;
import java.awt.Dimension;
import java.lang.StringBuilder;
import java.lang.NumberFormatException;


//----------------------------------------------------------------------------

/** SGF reader. 
    NOTE: Uses StringBuilder which requires Java 1.5
*/
public final class SgfReader
{
    /** Sgf exception. */
    public static class SgfError
	extends Exception
    {
	public SgfError(String message)
	{
	    super(message);
	}
    }

    public final static int GM_HEXGAME = 11;

    /** Constructor. 
	Parse the input stream in sgf format. 
    */
    public SgfReader(InputStream in) throws SgfError
    {
	InputStreamReader reader = new InputStreamReader(in);
	m_reader = new LineNumberReader(reader);
	m_tokenizer = new StreamTokenizer(m_reader);
	m_gameinfo = new GameInfo();

	try {
	    findGameTree();
	    m_gametree = parseGameTree(null, true);
	}
	catch (IOException e) {
	    throw sgfError("IO error occured while parsing file.");
	}
    }

    public Node getGameTree()
    {
	return m_gametree;
    }

    public GameInfo getGameInfo()
    {
	return m_gameinfo;
    }

    //------------------------------------------------------------

    private void findGameTree() throws SgfError, IOException
    {
	while (true) {
	    int ttype = m_tokenizer.nextToken();
	    if (ttype == StreamTokenizer.TT_EOF)
		throw sgfError("No game tree found!");
	    
	    if (ttype == '(') {
		m_tokenizer.pushBack();
		break;
	    }		
	}
    }

    private Node parseGameTree(Node parent, boolean isroot) 
	throws SgfError, IOException
    {
	int ttype = m_tokenizer.nextToken();
	if (ttype != '(') 
	    throw sgfError("Missing '(' at head of game tree.");

	Node node = parseNode(parent, isroot);
	
	ttype = m_tokenizer.nextToken();
	if (ttype != ')') 
	    throw sgfError("Game tree not closed!");

	return node;
    }

    private Node parseNode(Node parent, boolean isroot) 
	throws SgfError, IOException
    {
	int ttype = m_tokenizer.nextToken();
	if (ttype != ';') 
	    throw sgfError("Error at head of node!");

	Node node = new Node();
	node.setParent(parent);
	if (parent != null) 
	    parent.addChild(node);

	boolean done = false;
	while (!done) {
	    ttype = m_tokenizer.nextToken();
	    switch(ttype) {
	    case '(':
		m_tokenizer.pushBack();
		parseGameTree(node, false);
		break;

	    case ';':
		m_tokenizer.pushBack();
		parseNode(node, false);
		done = true;
		break;

	    case ')':
		m_tokenizer.pushBack();
		done = true;
		break;

	    case StreamTokenizer.TT_WORD:
		parseProperty(node, isroot);
		break;

	    case StreamTokenizer.TT_EOF:
		throw sgfError("Unexpected EOF in node!");

	    default:
		throw sgfError("Error in SGF file.");
	    }
	}

	return node;
    }

    private void parseProperty(Node node, boolean isroot) 
	throws SgfError, IOException
    {
	int x,y;
	String name = m_tokenizer.sval;
	String val = parseValue();
	node.setSgfProperty(name, val);
	System.out.println(name + "[" + node.getSgfProperty(name) + "]");
	
	if (name.equals("W")) {
	    HexPoint point = new HexPoint(val);
	    node.setMove(new Move(point, HexColor.WHITE));
	} 
	else if (name.equals("B")) {
	    HexPoint point = new HexPoint(val);
	    node.setMove(new Move(point, HexColor.BLACK));
	} 
	else if (name.equals("FF")) {
	    if (!isroot) throw sgfError("FF property in non-root node!");
	    x = parseInt(val);
	    if (x < 1 || x > 4)
		throw sgfError("Invalid SGF Version! (" + x + ")");
	}
	else if (name.equals("GM")) {
	    if (!isroot) throw sgfError("GM property in non-root node!");
	    if (parseInt(val) != GM_HEXGAME) throw sgfError("Not a Hex game!");
	}
	else if (name.equals("SZ")) {
	    if (!isroot) throw sgfError("GM property in non-root node!");
	    Dimension dim = new Dimension();
	    String sp[] = val.split(":");
	    if (sp.length == 1) {
		x = parseInt(sp[0]);
		dim.setSize(x,x);
	    } else if (sp.length == 2) {
		x = parseInt(sp[0]);
		y = parseInt(sp[1]);
		dim.setSize(x,y);
	    } else {
		throw sgfError("Malformed boardsize!");
	    }
	    m_gameinfo.setBoardSize(dim);
	}
    }

    private String parseValue() throws SgfError, IOException
    {
	int ttype = m_tokenizer.nextToken();
	if (ttype != '[')
	    throw sgfError("Property missing opening '['.");

	StringBuilder sb = new StringBuilder(256);
	boolean quoted = false;
	while (true) {
	    int c = m_reader.read();
	    if (c < 0)
		throw sgfError("Property runs to EOF.");

	    if (!quoted) {
		if (c == ']') break;
		if (c == '\\') 
		    quoted = true;
		else {
		    if (c != '\r' && c != '\n')
			sb.append((char)c);
		}
	    } else {
		quoted = false;
		sb.append(c);
	    }
	}

	return sb.toString();
    }

    private int parseInt(String str) throws SgfError
    {
	int ret;
	try {
	    ret = Integer.parseInt(str);
	}
	catch (NumberFormatException e) {
	    throw sgfError("Error parsing integer.");
	}
	return ret;
    }

    //----------------------------------------------------------------------

    private void verifyGame(Node root) throws SgfError
    {
	if (m_gameinfo.getBoardSize()==null)
	    throw sgfError("Missing SZ property.");
    }

    private SgfError sgfError(String msg)
    {
	return new SgfError("Line " + m_reader.getLineNumber() + ":" + msg);
    }
    
    private StreamTokenizer m_tokenizer;
    private LineNumberReader m_reader;
    private Node m_gametree;
    private GameInfo m_gameinfo;
}

//----------------------------------------------------------------------------