package rails.game;


import java.util.*;

import org.w3c.dom.*;

import rails.game.move.CashMove;
import rails.game.move.CertificateMove;
import rails.game.special.SpecialPropertyI;
import rails.util.LocalText;
import rails.util.Util;
import rails.util.XmlUtils;


public class PrivateCompany extends Company implements PrivateCompanyI
{

	protected static int numberOfPrivateCompanies = 0;
	protected int privateNumber; // For internal use

	protected int basePrice = 0;
	protected int revenue = 0;
	protected List<SpecialPropertyI> specialProperties = null;
	protected String auctionType;
	protected int closingPhase;

	protected List<MapHex> blockedHexes = null;

	public PrivateCompany()
	{
		super();
		this.privateNumber = numberOfPrivateCompanies++;
	}

	/**
	 * @see rails.game.ConfigurableComponentI#configureFromXML(org.w3c.dom.Element)
	 */
	public void configureFromXML(Element element) throws ConfigurationException
	{
		NamedNodeMap nnp = element.getAttributes();
		NamedNodeMap nnp2;

		/* Configure private company features */
		try
		{
			basePrice = Integer.parseInt(XmlUtils.extractStringAttribute(nnp,
					"basePrice",
					"0"));
			revenue = Integer.parseInt(XmlUtils.extractStringAttribute(nnp,
					"revenue",
					"0"));

			// Blocked hexes (until bought by a company)
			Element blEl = (Element) element.getElementsByTagName("Blocking")
					.item(0);
			if (blEl != null)
			{
				String[] hexes = XmlUtils.extractStringAttribute(blEl.getAttributes(),
						"hex")
						.split(",");
				if (hexes != null && hexes.length > 0)
				{
					blockedHexes = new ArrayList<MapHex>();
					for (int i = 0; i < hexes.length; i++)
					{
						MapHex hex = MapManager.getInstance().getHex(hexes[i]);
						blockedHexes.add(hex);
						hex.setBlocked(true);
					}
				}
			}

			// Special properties
			Element spsEl = (Element) element.getElementsByTagName("SpecialProperties")
					.item(0);
			if (spsEl != null)
			{
				specialProperties = new ArrayList<SpecialPropertyI>();
				NodeList spsNl = spsEl.getElementsByTagName("SpecialProperty");
				Element spEl;
				String condition, className;
				for (int i = 0; i < spsNl.getLength(); i++)
				{
					spEl = (Element) spsNl.item(i);
					nnp2 = spEl.getAttributes();
					condition = XmlUtils.extractStringAttribute(nnp2,
							"condition");
					if (!Util.hasValue(condition))
						throw new ConfigurationException("Missing condition in private special property");
					className = XmlUtils.extractStringAttribute(nnp2, "class");
					if (!Util.hasValue(className))
						throw new ConfigurationException("Missing class in private special property");

					SpecialPropertyI sp = (SpecialPropertyI) Class.forName(className)
							.newInstance();
					sp.setCompany(this);
					sp.setUsableIfOwnedByPlayer(condition.matches("(?i).*ifOwnedByPlayer.*"));
					sp.setUsableIfOwnedByCompany(condition.matches("(?i).*ifOwnedByCompany.*"));
					specialProperties.add(sp);
					sp.configureFromXML(spEl);

				}
			}

		}
		catch (Exception e)
		{
			throw new ConfigurationException("Configuration error for Private "
					+ name, e);
		}

		/*
		 * Complete configuration by adding features from the Private
		 * CompanyType
		 */
		Element typeElement = element;
		if (typeElement != null)
		{
			NodeList properties = typeElement.getChildNodes();

			for (int j = 0; j < properties.getLength(); j++)
			{

				String propName = properties.item(j).getLocalName();
				if (propName == null)
					continue;

				if (propName.equalsIgnoreCase("AllClose"))
				{
					nnp2 = properties.item(j).getAttributes();
					closingPhase = XmlUtils.extractIntegerAttribute(nnp2,
							"phase",
							0);
				}

			}
		}
	}

	/**
	 * @return Private Company Number
	 */
	public int getPrivateNumber()
	{
		return privateNumber;
	}

	/**
	 * @return Base Price
	 */
	public int getBasePrice()
	{
		return basePrice;
	}

	/**
	 * @return Revenue
	 */
	public int getRevenue()
	{
		return revenue;
	}

	/**
	 * @return Phase this Private closes
	 */
	public int getClosingPhase()
	{
		return closingPhase;
	}

	/**
	 * @return Portfolio of this Private
	 */
	public Portfolio getPortfolio()
	{
		return portfolio;
	}

	/**
	 * @param b
	 */
	public void setClosed()
	{
		if (!isClosed())
		{
			super.setClosed();
			unblockHexes();
			//Portfolio.transferCertificate(this,
			//		portfolio,
			//		Bank.getUnavailable());
			new CertificateMove (getPortfolio(), Bank.getScrapHeap(), 
			        (Certificate)this);
			ReportBuffer.add(LocalText.getText("PrivateCloses", name));
		}
	}

	/**
	 * @param i
	 */
	public void setClosingPhase(int i)
	{
		closingPhase = i;
	}

	/**
	 * @param portfolio
	 */
	public void setHolder(Portfolio portfolio)
	{
		this.portfolio = portfolio;

		/*
		 * If this private is blocking map hexes, unblock these hexes as soon as
		 * it is bought by a company.
		 */
		if (portfolio.getOwner() instanceof CompanyI)
		{
			unblockHexes();
		}
	}

	protected void unblockHexes()
	{
		if (blockedHexes != null)
		{
			//Iterator it = blockedHexes.iterator();
			//while (it.hasNext())
			for (MapHex hex : blockedHexes)
			{
				//((MapHex) it.next()).setBlocked(false);
				hex.setBlocked(false);
			}
		}
	}

	public void payOut()
	{
		if (portfolio.getOwner() != Bank.getInstance()) {
			ReportBuffer.add(
					LocalText.getText("ReceivesFor", new String[] {
							portfolio.getOwner().getName(),
							Bank.format(revenue),
							name
					}));
			//Bank.transferCash(null, portfolio.getOwner(), revenue);
			new CashMove (null, portfolio.getOwner(), revenue);
		}
	}

	public String toString()
	{
		return "Private: "+name;
	}

	public Object clone()
	{

		Object clone = null;
		try
		{
			clone = super.clone();
		}
		catch (CloneNotSupportedException e)
		{
			log.fatal ("Cannot clone company " + name);
			return null;
		}
		return clone;
	}

	public List<SpecialPropertyI> getSpecialProperties()
	{
		return specialProperties;
	}

	public List getBlockedHexes()
	{
		return blockedHexes;
	}
	
	public void closeIfExcercised () {
	    
	}

}
