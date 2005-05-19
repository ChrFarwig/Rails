/*
 * Rails: an 18xx game system. Copyright (C) 2005 Brett Lentz
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 */

package game;

import java.util.*;

/**
 * Implements a basic Stock Round.
 * <p>A new instance must be created for each new Stock Round.
 * At the end of a round, the current instance should be discarded.
 * <p>Permanent memory is formed by static attributes (like who has the Priority Deal). 
 * @author Erik Vos
 */
public class StockRound implements Round
{
    /* Transient memory (per round only) */
    protected static int numberOfPlayers;
    protected Player currentPlayer;

    protected boolean hasBoughtThisTurn = false;
    protected boolean hasSoldThisTurnBeforeBuying = false;
    protected boolean hasPassed = true; // Is set false on any player action
    protected int numPasses = 0;
    protected StartRoundI startRound = null;
    
    /* Transient data needed for rule enforcing */
    /** HashMap per player containing a HashMap per company */
    protected HashMap playersThatSoldThisRound = new HashMap();
    /** HashMap per player */
    protected HashMap playersThatBoughtThisRound = new HashMap();
    
    /* Rule constants */
    static protected final int SELL_BUY_SELL = 0;
    static protected final int SELL_BUY = 1;
    static protected final int SELL_BUY_OR_BUY_SELL = 2;
    
    /* Permanent memory */
    static protected int stockRoundNumber = 0;
    static protected StockMarketI stockMarket;
    static protected Portfolio ipo;
    static protected Portfolio pool;
    static protected CompanyManagerI companyMgr;
    static protected GameManager gameMgr;
    
    /* Rules */
    static protected int sequenceRule = SELL_BUY_SELL; // Currently fixed
    static protected boolean buySellInSameRound = true;
    
   
    /**
     * The constructor.
     */
    public StockRound() {
        
        if (numberOfPlayers == 0) numberOfPlayers = GameManager.getPlayers().length;
        if (gameMgr == null) gameMgr = GameManager.getInstance();
        if (stockMarket == null) stockMarket = StockMarket.getInstance();
        if (ipo == null) ipo = Bank.getIpo();
        if (pool == null) pool = Bank.getPool();
        if (companyMgr == null) companyMgr = Game.getCompanyManager();
        GameManager.getInstance().setRound(this);
    }
    
    public void start() {
        
        stockRoundNumber++;

        Log.write("Start of Stock Round "+stockRoundNumber);
        
        GameManager.setCurrentPlayerIndex (GameManager.priorityPlayerIndex);
        currentPlayer = GameManager.getCurrentPlayer();	
    }
    
    /*----- General methods -----*/
    
    public int getStockRoundNumber() {
        return stockRoundNumber;
    }
    
    public static int getLastStockRoundNumber() {
        return stockRoundNumber;
    }
   
    /*----- METHODS THAT PROCESS PLAYER ACTIONS -----*/
    
    /**
     * Start a company by buying the President's share only
     * @param company The company to start.
     * @return True if the company could be started.
     */
    public boolean startCompany (String playerName, String companyName, int price) {
        return startCompany(playerName, companyName, price, 1);
    }
    
    /**
     * Start a company by buying one or more shares (more applies to e.g. 1841)
     * @param player The player that wants to start a company.
     * @param company The company to start.
     * @param price The start (par) price (ignored if the price is fixed).
     * @param shares The number of shares to buy (can be more than 1 in e.g. 1841).
     * @return True if the company could be started. False indicates an error.
     */
    public boolean startCompany (String playerName, String companyName, int price, int shares) {
        
        String errMsg = null;
        StockSpaceI startSpace = null;
        int numberOfCertsToBuy = 0;
        PublicCertificateI cert = null;
        PublicCompanyI company = null;
        
        currentPlayer = GameManager.getCurrentPlayer();
        
        // Dummy loop to allow a quick jump out
        while (true) {

	        // Check everything
	        // Only the player that has the turn may buy
	        if (!playerName.equals(currentPlayer.getName())) {
	            errMsg = "Wrong player "+playerName;
	            break;
	        }
	            
	        // The player may not have bought this turn.
	        if (hasBoughtThisTurn) { 
	            errMsg = "Already bought this turn";
	            break;
	        }
	        
	        // Check company
	        company = companyMgr.getPublicCompany(companyName);
	        if (company == null) {
	            errMsg = "Company does not exist";
	            break;
	        }
	        // The company may not have started yet.
	        if (company.hasStarted()) {
	            errMsg = company.getName()+" was started before";
	            break;
	        }
	        
	        // Find the President's certificate
	        cert = ipo.findCertificate(company, true);
	        // Make sure that we buy at least one!
	        if (shares < cert.getShares()) shares = cert.getShares();
	        
	        // Determine the number of Certificates to buy
	        // (shortcut: assume that any additional certs are one share each) 
	        numberOfCertsToBuy = shares - (cert.getShares()-1);
	        // Check if the player may buy that many certificates.
	        if (!currentPlayer.mayBuyCertificates(numberOfCertsToBuy)) {
	            errMsg = "Cannot buy more certificates";
	            break;
	        }
	        
	        // Check if the company has a fixed par price (1835).
	        startSpace = company.getParPrice();
	        if (startSpace != null) {
	            // If so, it overrides whatever is given.
	            price = startSpace.getPrice();
	        } else {
	            // Else the given price must be a valid start price
	            if ((startSpace = stockMarket.getStartSpace(price)) == null) {
	                errMsg = "Invalid start price: "+price;
	                break;
	            }
	        }
	        
	        // Check if the Player has the money.
	        if (currentPlayer.getCash() < shares * price) {
	            errMsg = "Not enough money";
	            break;
	        }
	        
	        break;
	    }
        
        if (errMsg != null) {
            Log.error (playerName+" cannot start "
                    + companyName+": "+errMsg);
            return false;
        }
        
        // All is OK, now start the company
        company.start(startSpace);
        
        // Transfer the President's certificate
        currentPlayer.getPortfolio().buyCertificate (cert, ipo, cert.getCertificatePrice());
        
        // If more than one certificate is bought at the same time, transfer these too.
        for (int i=1; i<numberOfCertsToBuy; i++) {
            cert = ipo.findCertificate(company, false);
            currentPlayer.getPortfolio().buyCertificate (cert, ipo, cert.getCertificatePrice());
        }
        Log.write(playerName + " starts "+companyName +" at "+price+" and buys " 
                + shares+" share(s) ("+cert.getShare() + "%) for " 
                + Bank.format(shares*price)  + ".");
       
        hasBoughtThisTurn = true;
        hasPassed = false;
        setPriority();
        
        return true;
    }
    
    /**
     * Buying one or more shares (more is sometimes possible)
     * @param player The player that wants to buy shares.
     * @param portfolio The portfolio from which to buy shares. 
     * @param company The company of which to buy shares.
     * @param shares The number of shares to buy.
     * @return True if the company could be started. False indicates an error.
     * TODO Does not yet cater for double non-president shares as in 1835. 
     * @return False if an error is found.
     */
    public boolean buyShare (String playerName, Portfolio from, String companyName, int shares) {

        String errMsg = null;
        int price = 0;
        PublicCompanyI company = null;
        
        currentPlayer = GameManager.getCurrentPlayer();
        
       // Dummy loop to allow a quick jump out
        while (true) {

            // Check everything
	        // Only the player that has the turn may buy
	        if (!playerName.equals(currentPlayer.getName())) {
	            errMsg = "Wrong player "+playerName;
	            break;
	        }
	                	
	        // The player may not have bought this turn (shortcut: shares in brown disregarded)
	        if (hasBoughtThisTurn) {
	            errMsg = currentPlayer.getName()+" already bought this turn"; 
	            break;
	        }
	        
	        // The player may not have sold the company this round.
	        if (playersThatSoldThisRound.containsKey(currentPlayer) &&
	                ((HashMap)playersThatSoldThisRound.get(currentPlayer)).containsKey(companyName)) {
	            errMsg =  currentPlayer.getName()+" already sold "+companyName+" this turn";
	            break;
	        }
	        
	        // Check company
	        company = companyMgr.getPublicCompany(companyName);
	        if (company == null) {
	            errMsg = "Company does not exist";
	            break;
	        }
	        // The company must have started before
	        if (!company.hasStarted()) { 
	            errMsg = "Company "+companyName+" is not yet started";
	            break;
	        }
	        
	        // Check if that many shares are available
	        if (shares > from.ownsShare(company)) { 
	            errMsg =  companyName+" share(s) not available";
	            break;
	        }
	
	        StockSpaceI currentSpace = company.getCurrentPrice();
	
	        // Check if it is allowed to buy more than one certificate (if requested)
	        if (shares > 1 && !currentSpace.isNoBuyLimit()) {
	            errMsg = "Cannot buy more than 1 "+companyName+ " share";
	            break;
	        }
	        
	        // Check if player would not exceed the certificate limit.
	        // (shortcut: assume 1 cert == 1 certificate)
	        if (!currentSpace.isNoCertLimit() && !currentPlayer.mayBuyCertificates(shares)) {
	            errMsg = currentPlayer.getName()+" would exceed certificate limit";
	            break;
	        }
	        
	        // Check if player would exceed the per-company share limit
	        if (!currentSpace.isNoHoldLimit() && !currentPlayer.mayBuyCompanyShare(company, shares)) { 
	            errMsg = currentPlayer.getName()+" would exceed holding limit";
	            break;
	        }
	
	        price = currentSpace.getPrice();
	        
	        // Check if the Player has the money.
	        if (currentPlayer.getCash() < shares * price) { 
	            errMsg = currentPlayer.getName()+" does not have enough money";
	            break;
	        }
	        
	        break;
        }
        
        if (errMsg != null) {
            Log.error (playerName+" cannot buy "+shares+" share(s) of "
                    + companyName+" from "
                    +from.getName()+": "+errMsg);
            return false;
        }
        
        // All seems OK, now buy the shares.
        PublicCertificateI cert;
        for (int i=0; i<shares; i++) {
            cert = from.findCertificate(company, false);
            currentPlayer.getPortfolio().buyCertificate (cert, from, price * cert.getShares());
            Log.write(playerName + " buys " + shares+" share(s) ("+cert.getShare() + "%) of "
                    + companyName + " from " + from.getName()
                    + " for " + Bank.format(shares*price)  + ".");
       }

        hasBoughtThisTurn = true;
        hasPassed = false;
        setPriority();

        // Check if the company has floated
        /* Shortcut: float level and capitalisation hardcoded */
		if (from == ipo && !company.hasFloated() 
		        && from.ownsShare(company) <= (100 - company.getFloatPercentage())) {
			// Float company (limit and capitalisation to be made configurable)
			company.setFloated(10*price);
			Log.write (companyName+ " floats and receives "+Bank.format(company.getCash()));
		}

        return true;
    }
    
    /**
     * Sell one share (i.e. one share unit, normally 10%).
     * @see sellShare (String playerName, String companyName)
     * @param playerName Name of the selling player.
     * @param companyName Name of the company of which one share is sold.
     * @return False if an error is found.
     */
    public boolean sellShare (String playerName, String companyName) {
        return sellShares (playerName, companyName, 1);
        
    }
    
    /**
     * Sell one or more shares (one or multiple share units, normally 10% each).
     * @param player Name of the selling player.
     * @param company Name of the company of which shares are sold.
     * @param number The number of shares to sell.
     * TODO Does not yet cater for double shares (incl. president).
     * @return False if an error is found.
     */
    public boolean sellShares (String playerName, String companyName, int number) {
        
        Portfolio portfolio = currentPlayer.getPortfolio();
        String errMsg = null;
        PublicCompanyI company = null;
        
        currentPlayer = GameManager.getCurrentPlayer();
        
        // Dummy loop to allow a quick jump out
        while (true) {

           // Check everything
	        if (!playerName.equals(currentPlayer.getName())) {
	            errMsg = "Wrong player "+playerName;
                break;
            }

	        // May not sell in certain cases
	        if (sequenceRule == SELL_BUY_OR_BUY_SELL && hasBoughtThisTurn 
	                && hasSoldThisTurnBeforeBuying
	        	|| sequenceRule == SELL_BUY && hasBoughtThisTurn) {
	            errMsg = "May not sell anymore in this turn";
	            break;
	        }
	        
	        // Check company
	        company = companyMgr.getPublicCompany(companyName);
	        if (company == null) {
	            errMsg = "Company does not exist";
	            break;
	        }
	        
	        // The player must have the share(s)
	        if (portfolio.ownsShare(company) < number) {
	            errMsg = "Does not have the share(s)";
	            break;
	        }
	        
	        // The pool may not get over its limit.
	        if (pool.ownsShare(company) + number*company.getShareUnit() > Bank.getPoolShareLimit()) {
	            errMsg = "Pool would get over its share holding limit";
	            break;
	        }
	        
	        break;
        }
        
        if (errMsg != null) {
            Log.error (playerName+" cannot sell "+number+" share(s) of "
                    + companyName+": "+errMsg);
            return false;
        }
        
        // All seems OK, now do the selling.
        PublicCertificateI cert;
        int price = company.getCurrentPrice().getPrice();
		Log.write(playerName+" sells "+number+" shares ("
		        +(number*company.getShareUnit())
		        +"%) of "+companyName
		        +" for "+ Bank.format(number*price));
       
        for (int i=0; i<number; i++) {
            cert = portfolio.findCertificate(company, false);
            pool.buyCertificate (cert, portfolio, cert.getShares()*price);
        }
        stockMarket.sell(company, number);
       
        // Remember that the player has sold this company this round.
        if (!playersThatSoldThisRound.containsKey(currentPlayer)) {
            playersThatSoldThisRound.put(currentPlayer, new HashMap());
        }
        ((HashMap)playersThatSoldThisRound.get(currentPlayer)).put(company, null);
        
        if (!hasBoughtThisTurn) hasSoldThisTurnBeforeBuying = true;
        hasPassed = false;
        setPriority();

        return true;
    }
    
    /**
     * The current Player passes or is done.
     * @param player Name of the passing player.
     * @return False if an error is found.
     */
    public boolean done (String playerName) {
        
        String errMsg = null;
        
        currentPlayer = GameManager.getCurrentPlayer();
        
       if (!playerName.equals(currentPlayer.getName())) {
            errMsg = "Wrong player "+playerName;
            return false;
        }

        if (hasPassed) {
            numPasses++;
            Log.write (currentPlayer.getName()+" passes.");
        } else {
            numPasses = 0;
        }
        
        if (numPasses >= numberOfPlayers) {
            
            Log.write("All players have passed, end of SR "+stockRoundNumber);
            
            // Check if any companies are sold out.
			Iterator it = companyMgr.getAllPublicCompanies().iterator();
			boolean soldOut;
			PublicCompanyI company;
			while (it.hasNext()) {
				company = (PublicCompanyI)it.next();
				if (company.isSoldOut()) {
					Log.write(company.getName()+" is sold out");
					stockMarket.soldOut(company);}
			}

            // Inform GameManager
            GameManager.getInstance().nextRound(this);
            
        } else {        
            
            setNextPlayer();
            
        }

        return true;
    }
    
    /**
     * Internal method: pass the turn to the next player.
     */
    protected void setNextPlayer() {
        
        GameManager.setNextPlayer();
        currentPlayer = GameManager.getCurrentPlayer();
        hasBoughtThisTurn = false;
        hasSoldThisTurnBeforeBuying = false;
        hasPassed = true;
    }
    
    /**
     * Remember the player that has the Priority Deal.
     * <b>Must be called BEFORE setNextPlayer()!</b>
     */
    protected void setPriority() {
        GameManager.setPriorityPlayer();
    }
    
    
    /*----- METHODS TO BE CALLED TO SET UP THE NEXT TURN -----*/
    
    /**
     * @return The player that has the Priority Deal.
     */
    public static Player getPriorityPlayer() {
        return GameManager.priorityPlayer;
    }
    /**
     * @return The index of the player that has the Priority Deal.
     */
    public static int getPriorityPlayerIndex() {
        return GameManager.priorityPlayerIndex;
    }
   /**
     * @return The player that has the turn.
     */
    public Player getCurrentPlayer() {
        return GameManager.currentPlayer;
    }
    /**
     * @return The index of the player that has the turn.
     */
    public int getCurrentPlayerIndex() {
        return GameManager.currentPlayerIndex;
    }
    
    /**
     * Check if a public company can be started by the player that has the turn.
     * @param companyName Name of the company to be checked. 
     * @return True of false.
     * TODO Check for unstarted companies that may not yet be started.
     * TODO Check if current player has enough money to start at the lowest price.
     */
    public boolean isCompanyStartable(String companyName) {
        
        return !companyMgr.getPublicCompany(companyName).hasStarted();
    }
    
    /**
     * Check if a company can be bought by the current player from a given Portfolio.
     * @param companyName Name of the company to be checked. 
     * @param source The portfolio that is checked for presence of company shares. 
     * TODO Buying from company treasuries if just IPO is specified.
     * TODO Add checks that the current player may buy and has the money.
     * TODO Presidencies in the Pool (rare!) 
     */
    public boolean isCompanyBuyable (String companyName, Portfolio source) {

        PublicCompanyI company = companyMgr.getPublicCompany(companyName);
        if (!company.hasStarted()) return false;
        if (source.findCertificate(company, false) == null) return false;
        return true;
    }
    
    /**
     * Check if the current player can sell shares of a company.
     * @param companyName Name of the company to be checked
     * @return True if the company can be sold.
     * TODO Make Bank Pool share limit configurable.
     */
    public boolean isCompanySellable (String companyName) {
 
        PublicCompanyI company = companyMgr.getPublicCompany(companyName);
        if (currentPlayer.getPortfolio().ownsShare(company) == 0) return false;
        if (pool.ownsShare(company) >= Bank.getPoolShareLimit()) return false;
        return true;
    }
    
    /**
     * Can the current player do any selling?
     * @return True if any selling is allowed.
     */
    public boolean mayCurrentPlayerSellAtAll () {
        if (sequenceRule == SELL_BUY_OR_BUY_SELL && hasBoughtThisTurn 
                && hasSoldThisTurnBeforeBuying
            || sequenceRule == SELL_BUY && hasBoughtThisTurn) return false;
        return true;
    }
    
    /**
     * Can the current player do any buying?
     * @return True if any buying is allowed.
     */
    public boolean mayCurrentPlayerBuyAtAll () {
        return !hasBoughtThisTurn;
   }
 }