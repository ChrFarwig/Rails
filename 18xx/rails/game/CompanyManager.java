package rails.game;import java.util.*;import org.w3c.dom.*;import rails.util.*;public class CompanyManager implements CompanyManagerI, ConfigurableComponentI{	/** A map with all company types, by type name */	//private Map mCompanyTypes = new HashMap();	/** A List with all companies */	//private List lCompanies = new ArrayList();	/** A List with all private companies */	private List<PrivateCompanyI> lPrivateCompanies 		= new ArrayList<PrivateCompanyI>();	/** A List with all public companies */	private List<PublicCompanyI> lPublicCompanies 		= new ArrayList<PublicCompanyI>();	/** A map with all private companies by name */	private Map<String, PrivateCompanyI> mPrivateCompanies 		= new HashMap<String, PrivateCompanyI>();	/** A map with all public (i.e. non-private) companies by name */	private Map<String, PublicCompanyI> mPublicCompanies 		= new HashMap<String, PublicCompanyI>();	/** A map of all type names to lists of companies of that type */	//private Map<String, ArrayList<CompanyI>> mCompaniesByType 	//	= new HashMap<String, ArrayList<CompanyI>>();	/** A map of all type names to maps of companies of that type by name */	// TODO Redundant, current usage can be replaced.	private Map<String, HashMap<String, CompanyI>> mCompaniesByTypeAndName 		= new HashMap<String, HashMap<String, CompanyI>>();	/** A list of all start packets (usually one) */	// TODO Currently not used (but some newer games have more than one)	private List<StartPacket> startPackets = new ArrayList<StartPacket>();	/** An array of base token layong costs */	private int[] baseTokenLayCost;		/*	 * NOTES: 1. we don't have a map over all companies, because some games have	 * duplicate names, e.g. B&O in 1830. 2. we have both a map and a list of	 * private/public companies to preserve configuration sequence while	 * allowing direct access.	 */	/**	 * No-args constructor.	 */	public CompanyManager()	{		// Nothing to do here, everything happens when configured.	}	/**	 * @see rails.game.ConfigurableComponentI#configureFromXML(org.w3c.dom.Element)	 */	public void configureFromXML(Element el) throws ConfigurationException	{		/** A map with all company types, by type name */		// Localised here as it has no permanent use		Map<String, CompanyTypeI> mCompanyTypes 			= new HashMap<String, CompanyTypeI>();		/* Read and configure the company types */		NodeList types = el.getElementsByTagName(CompanyTypeI.ELEMENT_ID);		for (int i = 0; i < types.getLength(); i++)		{			Element compElement = (Element) types.item(i);			NamedNodeMap nnp = compElement.getAttributes();			// Extract the attributes of the Component			String name = XmlUtils.extractStringAttribute(nnp,					CompanyTypeI.NAME_TAG);			if (name == null)			{				throw new ConfigurationException(				        LocalText.getText("UnnamedCompanyType"));			}			String className = XmlUtils.extractStringAttribute(nnp,					CompanyTypeI.CLASS_TAG);			if (className == null)			{				throw new ConfigurationException(				        LocalText.getText("CompanyTypeHasNoClass", name));			}			if (mCompanyTypes.get(name) != null)			{				throw new ConfigurationException(				        LocalText.getText("CompanyTypeConfiguredTwice", name));			}			CompanyTypeI companyType = new CompanyType(name, className);			mCompanyTypes.put(name, companyType);			// Further parsing is done within CompanyType			companyType.configureFromXML(compElement);		}		/* Read and configure the companies */		NodeList children = el.getElementsByTagName(CompanyI.COMPANY_ELEMENT_ID);		for (int i = 0; i < children.getLength(); i++)		{			Element compElement = (Element) children.item(i);			NamedNodeMap nnp = compElement.getAttributes();			// Extract the attributes of the Component			String name = XmlUtils.extractStringAttribute(nnp,					CompanyI.COMPANY_NAME_TAG);			if (name == null)			{				throw new ConfigurationException(LocalText.getText("UnnamedCompany"));			}			String type = XmlUtils.extractStringAttribute(nnp,					CompanyI.COMPANY_TYPE_TAG);			if (type == null)			{				throw new ConfigurationException(				        LocalText.getText("CompanyHasNoType", name));			}			CompanyTypeI cType = (CompanyTypeI) mCompanyTypes.get(type);			if (cType == null)			{				throw new ConfigurationException(				        LocalText.getText("CompanyHasUnknownType", new String[] {				                name,				                type				        }));			}			try			{				CompanyI company = cType.createCompany(name, compElement);				/* Add company to the various lists */				//lCompanies.add(company);				/* Private or public */				if (company instanceof PrivateCompanyI)				{					mPrivateCompanies.put(name, (PrivateCompanyI)company);					lPrivateCompanies.add((PrivateCompanyI)company);					// bank.getIpo().addPrivate((PrivateCompanyI)company);				}				else if (company instanceof PublicCompanyI)				{					mPublicCompanies.put(name, (PublicCompanyI)company);					lPublicCompanies.add((PublicCompanyI)company);				}				/* By type */				//if (!mCompaniesByType.containsKey(type))				//	mCompaniesByType.put(type, new ArrayList<CompanyI>());				//((List<CompanyI>) mCompaniesByType.get(type)).add(company);				/* By type and name */				if (!mCompaniesByTypeAndName.containsKey(type))					mCompaniesByTypeAndName.put(type, new HashMap<String, CompanyI>());				((Map<String, CompanyI>) mCompaniesByTypeAndName.get(type)).put(name, company);			}			catch (Exception e)			{				throw new ConfigurationException(LocalText.getText("ClassCannotBeInstantiated", cType.getClassName()), e);			}		}		// Release all held company type DOM elements		/*		 * Iterator it = mCompanyTypes.keySet().iterator(); while (it.hasNext()) {		 * ((CompanyTypeI)mCompanyTypes.get((String)it.next())).releaseDomElement(); }		 */		/* Read and configure the start packets */		children = el.getElementsByTagName("StartPacket");		for (int i = 0; i < children.getLength(); i++)		{			Element packetElement = (Element) children.item(i);			NamedNodeMap nnp = packetElement.getAttributes();			// Extract the attributes of the Component			String name = XmlUtils.extractStringAttribute(nnp, "name");			if (name == null)				name = "Initial";			String roundClass = XmlUtils.extractStringAttribute(nnp,					"roundClass");			if (roundClass == null)			{				throw new ConfigurationException(				        LocalText.getText("StartPacketHasNoClass", name));			}			StartPacket sp = new StartPacket(name, roundClass);			startPackets.add(sp);			sp.configureFromXML(packetElement);		}		/* Read and configure additional rules */		/* This part may move later to a GameRules or GameManager XML */		NodeList rules = el.getElementsByTagName("StockRoundRules");		if (rules.getLength() > 0)		{			NodeList stockRules = rules.item(0).getChildNodes();			for (int i = 0; i < stockRules.getLength(); i++)			{				Node rule = stockRules.item(i);				if (rule.getNodeName().equals("NoSaleInFirstSR"))				{					StockRound.setNoSaleInFirstSR();				}				else if (rule.getNodeName().equals("NoSaleIfNotOperated"))				{					StockRound.setNoSaleIfNotOperated();				}			}		}		/* BaseToken cost */		Element baseTokenElement = (Element) el.getElementsByTagName("BaseTokens")				.item(0);		/* Cost of laying a token */		Element layCostElement = (Element) baseTokenElement.getElementsByTagName("LayCost")				.item(0);		NamedNodeMap nnp = layCostElement.getAttributes();		String costMethod = XmlUtils.extractStringAttribute(nnp, "method");		// Must validate the cost method!		baseTokenLayCost = XmlUtils.extractIntegerArrayAttribute(nnp, "cost");		/* Cost of buying a token (mutually exclusive with laying cost) */		Element buyCostElement = (Element) baseTokenElement.getElementsByTagName("BuyCost")				.item(0);		// We don't have this yet - ignore for now.	}	// Post XML parsing initialisations	public void initCompanies() throws ConfigurationException	{		Iterator it = lPublicCompanies.iterator();		while (it.hasNext())		{			((PublicCompanyI) it.next()).init2();		}	}	/**	 * @see rails.game.CompanyManagerI#getCompany(java.lang.String)	 * 	 */	public PrivateCompanyI getPrivateCompany(String name)	{		return (PrivateCompanyI) mPrivateCompanies.get(name);	}	public PublicCompanyI getPublicCompany(String name)	{		return (PublicCompanyI) mPublicCompanies.get(name);	}	/**	 * @see rails.game.CompanyManagerI#getAllNames()	 */	public List getAllPrivateNames()	{		return new ArrayList<String>(mPrivateCompanies.keySet());	}	public List getAllPublicNames()	{		return new ArrayList<String>(mPublicCompanies.keySet());	}	/**	 * @see rails.game.CompanyManagerI#getAllCompanies()	 */	//public List getAllCompanies()	//{	//	return (List) lCompanies;	//}	public List<PrivateCompanyI> getAllPrivateCompanies()	{		return lPrivateCompanies;	}	public List<PublicCompanyI> getAllPublicCompanies()	{		return lPublicCompanies;	}	//public List getCompaniesByType(String type)	//{	//	return (List) mCompaniesByType.get(type);	//}	public PublicCompanyI getCompanyByName(String name)	{		for (int i = 0; i < lPublicCompanies.size(); i++)		{			PublicCompany co = (PublicCompany) lPublicCompanies.get(i);			if (name.equalsIgnoreCase(co.getName()))			{				return (PublicCompanyI) lPublicCompanies.get(i);			}		}		return null;	}	public CompanyI getCompany(String type, String name)	{		if (mCompaniesByTypeAndName.containsKey(type))		{			return (CompanyI) ((Map) mCompaniesByTypeAndName.get(type)).get(name);		}		else		{			return null;		}	}	public List<PublicCompanyI> getCompaniesWithExcessTrains()	{		List<PublicCompanyI> list = new ArrayList<PublicCompanyI>();		Iterator it = lPublicCompanies.iterator();		int phaseIndex = PhaseManager.getInstance().getCurrentPhaseIndex();		while (it.hasNext())		{			PublicCompanyI comp = (PublicCompanyI) it.next();			if (comp.getPortfolio().getTrains().length > comp.getTrainLimit(phaseIndex))			{				list.add(comp);			}		}		return list;	}	/**	 * Calculate the cost of laying a token. Currently hardcoded for the	 * "sequence" method. The other token layong costing methods will be	 * implemented later.	 * 	 * @param index	 *            The sequence number of the token that the company is laying.	 * @return The cost of laying that token.	 */	public int getBaseTokenLayCostBySequence(int index)	{		if (index >= baseTokenLayCost.length)		{			index = baseTokenLayCost.length - 1;		}		else if (index < 0)		{			index = 0;		}		return baseTokenLayCost[index];	}	public void closeAllPrivates()	{		if (lPrivateCompanies == null)			return;		Iterator it = lPrivateCompanies.iterator();		while (it.hasNext())		{			((PrivateCompanyI) it.next()).setClosed();		}	}	public List<PrivateCompanyI> getPrivatesOwnedByPlayers()	{		List<PrivateCompanyI> privatesOwnedByPlayers 			= new ArrayList<PrivateCompanyI>();		PrivateCompanyI priv;		for (Iterator it = getAllPrivateCompanies().iterator(); it.hasNext();)		{			priv = (PrivateCompanyI) it.next();			if (priv.getPortfolio().getOwner() instanceof Player)			{				privatesOwnedByPlayers.add(priv);			}		}		return privatesOwnedByPlayers;	}}