/* $Header: /Users/blentz/rails_rcs/cvs/18xx/game/Attic/TileI.java,v 1.5 2005/11/18 23:24:55 wakko666 Exp $
 * 
 * Created on 23-Oct-2005
 * Change Log:
 */
package game;

import java.util.*;

import org.w3c.dom.Element;

/**
 * @author Erik Vos
 */
public interface TileI extends TokenHolderI
{

	public void configureFromXML(Element se, Element te)
			throws ConfigurationException;

	public String getColour();

	/**
	 * @return Returns the id.
	 */
	public int getId();

	/**
	 * @return Returns the name.
	 */
	public String getName();

	public boolean hasTracks(int sideNumber);

	public boolean isUpgradeable();

	public boolean isUpgradeableNow();

	public List getUpgrades(MapHex hex);

	public String getUpgradesString(MapHex hex);

	public boolean hasStations();

	public List getStations();
}
