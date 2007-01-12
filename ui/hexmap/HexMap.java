package ui.hexmap;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import javax.swing.*;

import org.apache.log4j.Logger;

import game.*;
import game.action.LayTile;
import game.action.LayToken;
import ui.*;

/**
 * Base class that stores common info for HexMap independant of Hex
 * orientations.
 */
public abstract class HexMap extends JComponent implements MouseListener,
		MouseMotionListener
{

	protected static Logger log = Logger.getLogger(HexMap.class.getPackage().getName());

	// Abstract Methods
	protected abstract void setupHexesGUI();

	// GUI hexes need to be recreated for each object, since scale varies.
	protected GUIHex[][] h;
	MapHex[][] hexArray;
	protected static ArrayList hexes;

	protected int scale = 2 * Scale.get();
	protected int cx;
	protected int cy;

	protected static GUIHex selectedHex = null;
	protected Dimension preferredSize;

	//protected ArrayList extraTileLays = new ArrayList();
	//protected ArrayList unconnectedTileLays = new ArrayList();
	/** A list of all allowed tile lays */ /*(may be redundant) */
	protected List allowedTileLays = null;
	/** A Map linking tile allowed tiles to each map hex */
	protected Map allowedTilesPerHex = null;
	/** A list of all allowed token lays */ /*(may be redundant) */
	protected List allowedTokenLays = null;
	/** A Map linking tile allowed tiles to each map hex */
	protected Map allowedTokensPerHex = null;

	public void setupHexes()
	{
		setupHexesGUI();
		addMouseListener(this);
		addMouseMotionListener(this);
	}

	/**
	 * Return the GUIBattleHex that contains the given point, or null if none
	 * does.
	 */
	GUIHex getHexContainingPoint(Point2D.Double point)
	{
		Iterator it = hexes.iterator();
		while (it.hasNext())
		{
			GUIHex hex = (GUIHex) it.next();
			if (hex.contains(point))
			{
				return hex;
			}
		}

		return null;
	}

	GUIHex getHexContainingPoint(Point point)
	{
		Iterator it = hexes.iterator();
		while (it.hasNext())
		{
			GUIHex hex = (GUIHex) it.next();
			if (hex.contains(point))
			{
				return hex;
			}
		}

		return null;
	}

	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		try
		{
			// Abort if called too early.
			Rectangle rectClip = g.getClipBounds();
			if (rectClip == null)
			{
				return;
			}

			Iterator it = hexes.iterator();
			while (it.hasNext())
			{
				GUIHex hex = (GUIHex) it.next();
				Rectangle hexrect = hex.getBounds();

				if (g.hitClip(hexrect.x,
						hexrect.y,
						hexrect.width,
						hexrect.height))
				{
					hex.paint(g);
				}
			}
		}
		catch (NullPointerException ex)
		{
			// If we try to paint before something is loaded, just retry later.
		}
	}

	public Dimension getMinimumSize()
	{
		Dimension dim = new Dimension();
		Rectangle r = ((GUIHex) h[h.length][h[0].length]).getBounds();
		dim.height = r.height + 40;
		dim.width = r.width + 100;
		return dim;
	}

	public Dimension getPreferredSize()
	{
		return preferredSize;
	}

	public void selectHex(GUIHex clickedHex)
	{
		log.debug ("selecthex called for hex "
				+ (clickedHex != null ? clickedHex.getName() : "null")
				+ ", selected was "
				+ (selectedHex != null ? selectedHex.getName() : "null"));

		if (selectedHex == clickedHex)
			return;
		if (selectedHex != null)
		{
			selectedHex.setSelected(false);
			repaint(selectedHex.getBounds());
			log.debug ("Hex " + selectedHex.getName()
					+ " deselected and repainted");
		}

		if (clickedHex != null)
		{
			clickedHex.setSelected(true);
			repaint(clickedHex.getBounds());
			log.debug ("Hex " + clickedHex.getName()
					+ " selected and repainted");
		}
		selectedHex = clickedHex;

	}

	public GUIHex getSelectedHex()
	{
		return selectedHex;
	}

	public boolean isAHexSelected() // Not used
	{
		return selectedHex != null;
	}

	public void setAllowedTileLays (List allowedTileLays) {
	    
	    this.allowedTileLays = allowedTileLays;
	    LayTile allowance;
	    MapHex location;
	    allowedTilesPerHex = new HashMap();
	    
	    /* Build the per-hex allowances map */
	    for (Iterator it = this.allowedTileLays.iterator(); it.hasNext(); ) {
	        allowance = (LayTile) it.next();
	        location = allowance.getLocation(); 
	        /* The location may be null, which means: anywhere.
	         * This is intended to be a temporary fixture, to be replaced
	         * by a detailed allowed-tiles-per-hex specification later.  
	         */
	        allowedTilesPerHex.put(location, allowance);
	    }
	}
	
	public LayTile getTileAllowanceForHex (MapHex hex) {
	    if (allowedTilesPerHex.containsKey(hex)) {
	        return (LayTile) allowedTilesPerHex.get(hex);
	    } else if (allowedTilesPerHex.containsKey(null)) {
	        return (LayTile) allowedTilesPerHex.get(null);
	    } else {
	        return null;
	    }
	}

	public void setAllowedTokenLays (List allowedTokenLays) {
	    
	    this.allowedTokenLays = allowedTokenLays;
	    LayToken allowance;
	    MapHex location;
	    allowedTokensPerHex = new HashMap();
	    
	    /* Build the per-hex allowances map */
	    for (Iterator it = this.allowedTokenLays.iterator(); it.hasNext(); ) {
	        allowance = (LayToken) it.next();
	        location = allowance.getLocation(); 
	        /* The location may be null, which means: anywhere.
	         * This is intended to be a temporary fixture, to be replaced
	         * by a detailed allowed-tiles-per-hex specification later.  
	         */
	        allowedTokensPerHex.put(location, allowance);
	    }
	}
	
	public LayToken getTokenAllowanceForHex (MapHex hex) {
	    if (allowedTokensPerHex.containsKey(hex)) {
	        return (LayToken) allowedTokensPerHex.get(hex);
	    } else if (allowedTokensPerHex.containsKey(null)) {
	        return (LayToken) allowedTokensPerHex.get(null);
	    } else {
	        return null;
	    }
	}

	public void mouseClicked(MouseEvent arg0)
	{
		Point point = arg0.getPoint();
		GUIHex clickedHex = getHexContainingPoint(point);

		if (ORWindow.baseTokenLayingEnabled)
		{
			selectHex(clickedHex);

			if (selectedHex != null)
			{
				GameUILoader.orWindow.setSubStep(ORWindow.CONFIRM_TOKEN);
			}
			else
			{
				GameUILoader.orWindow.setSubStep(ORWindow.SELECT_HEX_FOR_TOKEN);
			}
		}
		else if (ORWindow.tileLayingEnabled)
		{
			if (GameUILoader.orWindow.getSubStep() == ORWindow.ROTATE_OR_CONFIRM_TILE
					&& clickedHex == selectedHex)
			{
				selectedHex.rotateTile();
				repaint(selectedHex.getBounds());
				
				return; // No further actions, in particular no upgrades panel repaint!
			}
			else
			{

				if (selectedHex != null && clickedHex != selectedHex)
				{
					selectedHex.removeTile();
					selectHex(null);
				}
				if (clickedHex != null)
				{
					if (clickedHex.getHexModel().isUpgradeableNow())
					    /* Direct call to Model to be replaced later by use of
					     * allowedTilesPerHex. Would not work yet.
					     */ 
					{
						selectHex(clickedHex);
						GameUILoader.orWindow.setSubStep(ORWindow.SELECT_TILE);
					}
					else
					{
						JOptionPane.showMessageDialog(this,
								"This hex cannot be upgraded now");
					}
				}
			}
		}

		// repaint();
		GameUILoader.orWindow.repaintUpgradePanel();
		GameUILoader.orWindow.repaintORPanel();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
	 */
	public void mouseDragged(MouseEvent arg0)
	{
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseMotionListener#mouseMoved(java.awt.event.MouseEvent)
	 */
	public void mouseMoved(MouseEvent arg0)
	{
		Point point = arg0.getPoint();
		GUIHex hex = getHexContainingPoint(point);
		setToolTipText(hex != null ? hex.getToolTip() : "");
	}

	public void mouseEntered(MouseEvent arg0)
	{
	}

	public void mouseExited(MouseEvent arg0)
	{
	}

	public void mousePressed(MouseEvent arg0)
	{
	}

	public void mouseReleased(MouseEvent arg0)
	{
	}

	public static void updateOffBoardToolTips() {
	    
	    GUIHex hex;
	    for (Iterator it = hexes.iterator(); it.hasNext(); ) {
	        hex = (GUIHex)it.next();
	        if (hex.getHexModel().hasOffBoardValues()) {
	            hex.setToolTip();
	        }
	    }
	}
}
