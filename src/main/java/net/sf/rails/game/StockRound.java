package net.sf.rails.game;

import java.util.*;

import rails.game.action.*;
import net.sf.rails.common.*;
import net.sf.rails.game.model.PortfolioModel;
import net.sf.rails.game.special.*;
import net.sf.rails.game.state.*;
import net.sf.rails.game.state.Currency;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedMultiset;


/**
 * Implements a basic Stock Round. <p> A new instance must be created for each
 * new Stock Round. At the end of a round, the current instance should be
 * discarded. <p> Permanent memory is formed by static attributes (like who has
 * the Priority Deal).
 */
public class StockRound extends Round {

    /* Transient memory (per round only) */
    protected int numberOfPlayers;
    protected Player currentPlayer;
    protected Player startingPlayer;

    protected final GenericState<PublicCompany> companyBoughtThisTurnWrapper = 
            GenericState.create(this, "companyBoughtThisTurnWrapper");

    protected final BooleanState hasSoldThisTurnBeforeBuying = 
            BooleanState.create(this, "hasSoldThisTurnBeforeBuying");

    protected final BooleanState hasActed = BooleanState.create(this, "hasActed");

    protected final IntegerState numPasses = IntegerState.create(this, "numPasses");

    protected HashMapState<PublicCompany, StockSpace> sellPrices = HashMapState.create(this, "sellPrices");

    /** Records lifted share selling obligations in the current round<p>
     * Example: >60% ownership allowed after a merger in 18EU.
     */
    protected HashSetState<PublicCompany> sellObligationLifted = null;


    /* Rule constants */
    static protected final int SELL_BUY_SELL = 0;
    static protected final int SELL_BUY = 1;
    static protected final int SELL_BUY_OR_BUY_SELL = 2;

    /* Action constants */
    static public final int BOUGHT = 0;
    static public final int SOLD = 1;

    /* Rules */
    protected int sequenceRule;
    protected boolean raiseIfSoldOut = false;

    /* Temporary variables */
    protected boolean isOverLimits = false;
    protected String overLimitsDetail = null;

    /**
     * Constructed via Configure
     */
    public StockRound (GameManager parent, String id) {
        super(parent, id);

        if (numberOfPlayers == 0)
            numberOfPlayers = getRoot().getPlayerManager().getPlayers().size();

        sequenceRule = getGameParameterAsInt(GameDef.Parm.STOCK_ROUND_SEQUENCE);

        guiHints.setVisibilityHint(GuiDef.Panel.MAP, true);
        guiHints.setVisibilityHint(GuiDef.Panel.STOCK_MARKET, true);
        guiHints.setActivePanel(GuiDef.Panel.STATUS);
    }
    
    /** Start the Stock Round. <p>
     * Please note: subclasses that are NOT real stock rounds should NOT call this method
     * (or set raiseIfSoldOut to false after calling this method).
     */
    public void start() {

        ReportBuffer.add(this, LocalText.getText("StartStockRound",
                getStockRoundNumber()));

        playerManager.setCurrentToPriorityPlayer();
        startingPlayer = playerManager.getCurrentPlayer(); // For the Report
        ReportBuffer.add(this, LocalText.getText("HasPriority",
                startingPlayer.getId() ));

        initPlayer();

        raiseIfSoldOut = true;
        
    }

    /*----- General methods -----*/

    public int getStockRoundNumber() {
        return gameManager.getSRNumber();
    }

    @Override
    public boolean setPossibleActions() {

        // fix of the forced undo bug
        currentPlayer = playerManager.getCurrentPlayer();

        boolean passAllowed = false;

        setSellableShares();

        // Certificate limits must be obeyed by selling excess shares
        // before any other action is allowed.
        if (isOverLimits) {
            return true;
        }

        passAllowed = true;

        setBuyableCerts();

        setSpecialActions();

        setGameSpecificActions();

        if (passAllowed) {
            if (hasActed.value()) {
                possibleActions.add(new NullAction(NullAction.Mode.DONE));
            } else {
                possibleActions.add(new NullAction(NullAction.Mode.PASS));
                possibleActions.add(new NullAction(NullAction.Mode.AUTOPASS));
            }
        }

        if (getAutopasses() != null) {
            for (Player player : getAutopasses()) {
                possibleActions.add(new RequestTurn(player));
            }
        }

        return true;
    }

    /** Stub, can be overridden in subclasses */
    protected void setGameSpecificActions() {

    }

    /**
     * Create a list of certificates that a player may buy in a Stock Round,
     * taking all rules into account.
     *
     * @return List of buyable certificates.
     */
    public void setBuyableCerts() {
        if (!mayCurrentPlayerBuyAnything()) return;

        ImmutableSet<PublicCertificate> certs;
        PublicCertificate cert;
        StockSpace stockSpace;
        PortfolioModel from;
        int price;
        int number;
        int unitsForPrice;

        int playerCash = currentPlayer.getCashValue();

        /* Get the next available IPO certificates */
        // Never buy more than one from the IPO
        PublicCompany companyBoughtThisTurn =
            (PublicCompany) companyBoughtThisTurnWrapper.value();
        if (companyBoughtThisTurn == null) {
            from = ipo;
            ImmutableSetMultimap<PublicCompany, PublicCertificate> map =
                from.getCertsPerCompanyMap();
            int shares;

            for (PublicCompany comp : map.keySet()) {
                certs = map.get(comp);
                // if (certs.isEmpty()) continue; // TODO: is this removal correct?

                /* Only the top certificate is buyable from the IPO */
                // TODO: This is code that should be deprecated
                int lowestIndex = 99;
                cert = null;
                int index;
                for (PublicCertificate c : certs) {
                    index = c.getIndexInCompany();
                    if (index < lowestIndex) {
                        lowestIndex = index;
                        cert = c;
                    }
                }

                unitsForPrice = comp.getShareUnitsForSharePrice();
                if (currentPlayer.hasSoldThisRound(comp)) continue;
                if (maxAllowedNumberOfSharesToBuy(currentPlayer, comp,
                        cert.getShare()) < 1 ) continue;

                /* Would the player exceed the total certificate limit? */
                stockSpace = comp.getCurrentSpace();
                if ((stockSpace == null || !stockSpace.isNoCertLimit()) && !mayPlayerBuyCertificate(
                        currentPlayer, comp, cert.getCertificateCount())) continue;

                shares = cert.getShares();

                if (!cert.isPresidentShare()) {
                    price = comp.getIPOPrice() / unitsForPrice;
                    if (price <= playerCash) {
                        possibleActions.add(new BuyCertificate(comp, cert.getShare(),
                                from.getParent(), price));
                    }
                } else if (!comp.hasStarted()) {
                    if (comp.getIPOPrice() != 0) {
                        price = comp.getIPOPrice() * cert.getShares() / unitsForPrice;
                        if (price <= playerCash) {
                            possibleActions.add(new StartCompany(comp, price));
                        }
                    } else {
                        List<Integer> startPrices = new ArrayList<Integer>();
                        for (int startPrice : stockMarket.getStartPrices()) {
                            if (startPrice * shares <= playerCash) {
                                startPrices.add(startPrice);
                            }
                        }
                        if (startPrices.size() > 0) {
                            int[] prices = new int[startPrices.size()];
                            Arrays.sort(prices);
                            for (int i = 0; i < prices.length; i++) {
                                prices[i] = startPrices.get(i);
                            }
                            possibleActions.add(new StartCompany(comp, prices));
                        }
                    }
                }

            }
        }

        /* Get the unique Pool certificates and check which ones can be bought */
        from = pool;
        ImmutableSetMultimap<PublicCompany, PublicCertificate> map =
            from.getCertsPerCompanyMap();
        /* Allow for multiple share unit certificates (e.g. 1835) */
        PublicCertificate[] uniqueCerts;
        int[] numberOfCerts;
        int shares;
        int shareUnit;
        int maxNumberOfSharesToBuy;

        for (PublicCompany comp : map.keySet()) {
            certs = map.get(comp);
            // if (certs.isEmpty()) continue; // TODO: Is this removal correct?

            stockSpace = comp.getCurrentSpace();
            unitsForPrice = comp.getShareUnitsForSharePrice();
            price = stockSpace.getPrice() / unitsForPrice;
            shareUnit = comp.getShareUnit();
            maxNumberOfSharesToBuy
            = maxAllowedNumberOfSharesToBuy(currentPlayer, comp, shareUnit);

            /* Checks if the player can buy any shares of this company */
            if (maxNumberOfSharesToBuy < 1) continue;
            if (currentPlayer.hasSoldThisRound(comp)) continue;
            if (companyBoughtThisTurn != null) {
                // If a cert was bought before, only brown zone ones can be
                // bought again in the same turn
                if (comp != companyBoughtThisTurn) continue;
                if (!stockSpace.isNoBuyLimit()) continue;
            }

            /* Check what share multiples are available
             * Normally only 1, but 1 and 2 in 1835. Allow up to 4.
             */
            uniqueCerts = new PublicCertificate[5];
            numberOfCerts = new int[5];
            for (PublicCertificate cert2 : certs) {
                shares = cert2.getShares();
                if (maxNumberOfSharesToBuy < shares) continue;
                numberOfCerts[shares]++;
                if (uniqueCerts[shares] != null) continue;
                uniqueCerts[shares] = cert2;
            }

            /* Create a BuyCertificate action per share size */
            for (shares = 1; shares < 5; shares++) {
                /* Only certs in the brown zone may be bought all at once */
                number = numberOfCerts[shares];
                if (number == 0) continue;

                if (!stockSpace.isNoBuyLimit()) {
                    number = 1;
                    /* Would the player exceed the per-company share hold limit? */
                    if (!checkAgainstHoldLimit(currentPlayer, comp, number)) continue;

                    /* Would the player exceed the total certificate limit? */
                    if (!stockSpace.isNoCertLimit()
                            && !mayPlayerBuyCertificate(currentPlayer, comp,
                                    number * uniqueCerts[shares].getCertificateCount()))
                        continue;
                }

                // Does the player have enough cash?
                while (number > 0 && playerCash < number * price * shares) {
                    number--;
                }

                if (number > 0) {
                    possibleActions.add(new BuyCertificate(comp,
                            uniqueCerts[shares].getShare(),
                            from.getParent(), price,
                            number));
                }
            }
        }

        // Get any shares in company treasuries that can be bought
        if (gameManager.canAnyCompanyHoldShares()) {

            for (PublicCompany company : companyManager.getAllPublicCompanies()) {
                // TODO: Has to be rewritten (director)
                certs = company.getPortfolioModel().getCertificates(company);
                if (certs.isEmpty()) continue;
                cert = Iterables.get(certs, 0);
                if (currentPlayer.hasSoldThisRound(company)) continue;
                if (!checkAgainstHoldLimit(currentPlayer, company, 1)) continue;
                if (maxAllowedNumberOfSharesToBuy(currentPlayer, company,
                        cert.getShare()) < 1) continue;
                stockSpace = company.getCurrentSpace();
                if (!stockSpace.isNoCertLimit()
                        && !mayPlayerBuyCertificate(currentPlayer, company, 1)) continue;
                if (company.getMarketPrice() <= playerCash) {
                    possibleActions.add(new BuyCertificate(company, cert.getShare(),
                            company, company.getMarketPrice()));
                }
            }
        }
    }

    /**
     * Create a list of certificates that a player may sell in a Stock Round,
     * taking all rules taken into account.
     *
     * @return List of sellable certificates.
     */

    // FIXME Rails 2.0: 
    // This is rewritten taken into account that actions will not be changed for now
    // A change of action will allow to simplify this strongly
    public void setSellableShares() {

        if (!mayCurrentPlayerSellAnything()) return;

        boolean choiceOfPresidentExchangeCerts = false;
        isOverLimits = false;
        overLimitsDetail = null;

        StringBuilder violations = new StringBuilder();
        PortfolioModel playerPortfolio = currentPlayer.getPortfolioModel();

        /*
         * First check of which companies the player owns stock, and what
         * maximum percentage he is allowed to sell.
         */
        for (PublicCompany company : companyManager.getAllPublicCompanies()) {

            // Check if shares of this company can be sold at all
            if (!mayPlayerSellShareOfCompany(company)) {
                continue;
            }

            int ownedShare = playerPortfolio.getShareNumber(company);
            if (ownedShare == 0) {
                continue;
            }

            /* May not sell more than the Pool can accept */
            int poolAllowsShares = PlayerShareUtils.poolAllowsShareNumbers(company);
            log.debug("company = " + company);
            log.debug("poolAllowShares = "+ poolAllowsShares);
            int maxShareToSell = Math.min(ownedShare, poolAllowsShares );
            
            // if no share can be sold
            if (maxShareToSell == 0) {
                continue;
            }

            // Is player over the hold limit of this company?
            if (!checkAgainstHoldLimit(currentPlayer, company, 0)) {
                // The first time this happens, remove all non-over-limits sell options
                if (!isOverLimits) possibleActions.clear();
                isOverLimits = true;
                violations.append(LocalText.getText("ExceedCertificateLimitCompany",
                        company.getId(),
                        playerPortfolio.getShare(company),
                        getGameParameterAsInt(GameDef.Parm.PLAYER_SHARE_LIMIT)
                ));

            } else {
                // If within limits, but an over-limits situation exists: correct that first.
                if (isOverLimits) continue;
            }

            /*
             * If the current Player is president, check if there is a play to dump on
             * => dumpThreshold = how many shareNumbers have to be sold for dump
             * => possibleSharesToSell = list of shareNumbers that can be sold 
             *    (includes check for swapping the presidency)
             * => dumpIsPossible = true
             */
            int dumpThreshold = 0;
            SortedSet<Integer> possibleSharesToSell = null;
            boolean dumpIsPossible = false;
            if (company.getPresident() == currentPlayer) {
                Player potential = company.findPlayerToDump();
                if (potential != null) {
                    dumpThreshold = ownedShare - potential.getPortfolioModel().getShareNumber(company) + 1;
                    possibleSharesToSell = PlayerShareUtils.sharesToSell(company, currentPlayer);
                    dumpIsPossible = true;
                    log.debug("dumpThreshold = " + dumpThreshold);
                    log.debug("possibleSharesToSell = " + possibleSharesToSell);
                    log.debug("dumpIsPossible = " + dumpIsPossible);
                }
            }

            /*
             * Check what share units the player actually owns. In some games
             * (e.g. 1835) companies may have different ordinary shares: 5% and
             * 10%, or 10% and 20%. The president's share counts as a multiple
             * of the smallest ordinary share unit type.
             */
            
           
            // Check the price. If a cert was sold before this turn, the original price is still valid.
            int price = getCurrentSellPrice(company);

            /* Allow for different share units (as in 1835) */
            SortedMultiset<Integer> certCount = playerPortfolio.getCertificateTypeCounts(company);
            
            // Make sure that single shares are always considered (due to possible dumping)
            SortedSet<Integer> certSizeElements =Sets.newTreeSet(certCount.elementSet());
            certSizeElements.add(1);
            
            for (int shareSize:certSizeElements) {
                int number = certCount.count(shareSize);

                // If you can dump a presidency, you add the shareNumbers of the presidency
                // to the single shares to be sold
                if (dumpIsPossible && shareSize == 1 && number + company.getPresidentsShare().getShares() >= dumpThreshold) {
                    number += company.getPresidentsShare().getShares();
                    // but limit this to the pool 
                    number = Math.min(number, poolAllowsShares);
                    log.debug("Dump is possible increased single shares to " + number);
                }
                
                if (number == 0) {
                    continue;
                }

                /* In some games (1856), a just bought share may not be sold */
                // This code ignores the possibility of different share units
                if ((Boolean)gameManager.getGameParameter(GameDef.Parm.NO_SALE_OF_JUST_BOUGHT_CERT)
                        && company.equals(companyBoughtThisTurnWrapper.value())
                        /* An 1856 clarification by Steve Thomas (backed by Bill Dixon) states that
                         * in this situation a half-presidency may be sold
                         * (apparently even if a dump would otherwise not be allowed),
                         * as long as the number of shares does not become zero.
                         * So the rule "can't sell a just bought share" only means,
                         * that the number of shares may not be sold down to zero.
                         * Added 4jun2012 by EV */
                        && number == ownedShare) {
                    number--;
                }
                
                if (number <= 0) {
                    continue;
                }

                // Check against the maximum share that can be sold
                number = Math.min(number, maxShareToSell / shareSize);
                
                if (number <= 0) {
                    continue;
                }

                for (int i=1; i<=number; i++) {
                    // check if selling would dump the company
                    if (dumpIsPossible && i*shareSize >= dumpThreshold) {
                        // dumping requires that the total is in the possibleSharesToSell list and that shareSize == 1
                        // multiple shares have to be sold separately
                        if (shareSize == 1 && possibleSharesToSell.contains(i*shareSize)) {
                            possibleActions.add(new SellShares(company, shareSize, i, price, 1));
                        }
                    } else {
                        // ... no dumping: standard sell
                        possibleActions.add(new SellShares(company, shareSize, i, price, 0));
                    }
                }
            }
        }

        // Is player over the total certificate hold limit?
        float certificateCount = playerPortfolio.getCertificateCount();
        int certificateLimit = gameManager.getPlayerCertificateLimit(currentPlayer);
        if (certificateCount > certificateLimit) {
            violations.append(LocalText.getText("ExceedCertificateLimitTotal",
                    certificateCount,
                    certificateLimit));
            isOverLimits = true;
        }

        if (isOverLimits) {
            DisplayBuffer.add(this, LocalText.getText("ExceedCertificateLimit"
                    , currentPlayer.getId()
                    , violations.toString()
            ));
        }
    }

    protected void setSpecialActions() {

        List<SpecialProperty> sps =
            currentPlayer.getPortfolioModel().getSpecialProperties(
                    SpecialProperty.class, false);
        for (SpecialProperty sp : sps) {
            if (sp.isUsableDuringSR()) {
                possibleActions.add(new UseSpecialProperty(sp));
            }
        }
    }

    /*----- METHODS THAT PROCESS PLAYER ACTIONS -----*/

    @Override
    public boolean process(PossibleAction action) {

        boolean result = false;
        String playerName = action.getPlayerName();
        currentPlayer = playerManager.getCurrentPlayer();

        if (action instanceof NullAction) {

            NullAction nullAction = (NullAction) action;
            switch (nullAction.getMode()) {
            case PASS:
            case DONE:
                result = done((NullAction)action, playerName, false);
                break;
            case AUTOPASS:
                result = done(null, playerName, true);
                break;
            default:
                break;
            }

        } else if (action instanceof StartCompany) {

            StartCompany startCompanyAction = (StartCompany) action;

            result = startCompany(playerName, startCompanyAction);

        } else if (action instanceof BuyCertificate) {

            result = buyShares(playerName, (BuyCertificate) action);

        } else if (action instanceof SellShares) {

            result = sellShares((SellShares) action);

        } else if (action instanceof UseSpecialProperty) {

            result = useSpecialProperty((UseSpecialProperty) action);

        } else if (action instanceof RequestTurn) {

            result = requestTurn ((RequestTurn)action);

        } else if (!!(result = processGameSpecificAction(action))) {

        } else {

            DisplayBuffer.add(this, LocalText.getText("UnexpectedAction",
                    action.toString()));
        }

        return result;
    }

    // Return value indicates whether the action has been processed.
    protected boolean processGameSpecificAction(PossibleAction action) {

        return false;
    }

    /**
     * Start a company by buying one or more shares (more applies to e.g. 1841)
     *
     * @param player The player that wants to start a company.
     * @param company The company to start.
     * @param price The start (par) price (ignored if the price is fixed).
     * @param shares The number of shares to buy (can be more than 1 in e.g.
     * 1841).
     * @return True if the company could be started. False indicates an error.
     */
    public boolean startCompany(String playerName, StartCompany action) {
        PublicCompany company = action.getCompany();
        int price = action.getPrice();
        int shares = action.getNumberBought();

        String errMsg = null;
        StockSpace startSpace = null;
        int numberOfCertsToBuy = 0;
        PublicCertificate cert = null;
        String companyName = company.getId();
        int cost = 0;

        currentPlayer = playerManager.getCurrentPlayer();

        // Dummy loop to allow a quick jump out
        while (true) {

            // Check everything
            // Only the player that has the turn may buy
            if (!playerName.equals(currentPlayer.getId())) {
                errMsg = LocalText.getText("WrongPlayer", playerName, currentPlayer.getId());
                break;
            }

            // The player may not have bought this turn.
            if (companyBoughtThisTurnWrapper.value() != null) {
                errMsg = LocalText.getText("AlreadyBought", playerName);
                break;
            }

            // Check company
            if (company == null) {
                errMsg = LocalText.getText("CompanyDoesNotExist", companyName);
                break;
            }
            // The company may not have started yet.
            if (company.hasStarted()) {
                errMsg =
                    LocalText.getText("CompanyAlreadyStarted", companyName);
                break;
            }

            // Find the President's certificate
            cert = ipo.findCertificate(company, true);
            // Make sure that we buy at least one!
            if (shares < cert.getShares()) shares = cert.getShares();

            // Determine the number of Certificates to buy
            // (shortcut: assume that any additional certs are one share each)
            numberOfCertsToBuy = shares - (cert.getShares() - 1);
            // Check if the player may buy that many certificates.
            if (!mayPlayerBuyCertificate(currentPlayer, company, numberOfCertsToBuy)) {
                errMsg = LocalText.getText("CantBuyMoreCerts");
                break;
            }

            // Check if the company has a fixed par price (1835).
            startSpace = company.getStartSpace();
            if (startSpace != null) {
                // If so, it overrides whatever is given.
                price = startSpace.getPrice();
            } else {
                // Else the given price must be a valid start price
                if ((startSpace = stockMarket.getStartSpace(price)) == null) {
                    errMsg = LocalText.getText("InvalidStartPrice",
                            Bank.format(this, price),
                            company.getId() );
                    break;
                }
            }

            // Check if the Player has the money.
            cost = shares * price;
            if (currentPlayer.getCashValue() < cost) {
                errMsg = LocalText.getText("NoMoney");
                break;
            }

            break;
        }

        if (errMsg != null) {
            DisplayBuffer.add(this, LocalText.getText("CantStart",
                    playerName,
                    companyName,
                    Bank.format(this, price),
                    errMsg ));
            return false;
        }

        

        // All is OK, now start the company
        company.start(startSpace);

        MoneyOwner priceRecipient = getSharePriceRecipient (company, ipo.getParent(), price);

        // Transfer the President's certificate
        cert.moveTo(currentPlayer);

        // If more than one certificate is bought at the same time, transfer
        // these too.
        for (int i = 1; i < numberOfCertsToBuy; i++) {
            cert = ipo.findCertificate(company, false);
            cert.moveTo(currentPlayer);
        }

        // Pay for these shares
        String costText = Currency.wire(currentPlayer, cost, priceRecipient);

        ReportBuffer.add(this, LocalText.getText("START_COMPANY_LOG",
                playerName,
                companyName,
                bank.getCurrency().format(price), // TODO: Do this nicer
                costText,
                shares,
                cert.getShare(),
                priceRecipient.getId() ));
        ReportBuffer.getAllWaiting(this);

        checkFlotation(company);

        companyBoughtThisTurnWrapper.set(company);
        hasActed.set(true);
        setPriority();

        // Check for any game-specific consequences
        // (such as making another company available in the IPO)
        gameSpecificChecks(ipo, company);

        return true;
    }

    /**
     * Buying one or more single or double-share certificates (more is sometimes
     * possible)
     *
     * @param player The player that wants to buy shares.
     * @param action The executed BuyCertificates action
     * @return True if the certificates could be bought. False indicates an
     * error.
     */
    public boolean buyShares(String playerName, BuyCertificate action) {

        PublicCompany company = action.getCompany();
        PortfolioModel from = action.getFromPortfolio();
        String companyName = company.getId();
        int number = action.getNumberBought();
        int shareUnit = company.getShareUnit();
        int sharePerCert = action.getSharePerCertificate();
        int share = number * sharePerCert;
        int shares = share / shareUnit;

        String errMsg = null;
        int price = 0;
        int cost = 0;

        currentPlayer = playerManager.getCurrentPlayer();

        // Dummy loop to allow a quick jump out
        while (true) {

            // Check everything
            // Only the player that has the turn may buy
            if (!playerName.equals(currentPlayer.getId())) {
                errMsg = LocalText.getText("WrongPlayer", playerName, currentPlayer.getId());
                break;
            }

            // Check company
            company = companyManager.getPublicCompany(companyName);
            if (company == null) {
                errMsg = LocalText.getText("CompanyDoesNotExist", companyName);
                break;
            }

            // The player may not have sold the company this round.
            if (currentPlayer.hasSoldThisRound(company)) {
                errMsg =
                    LocalText.getText("AlreadySoldThisTurn",
                            currentPlayer.getId(),
                            companyName );
                break;
            }

            if (!company.isBuyable()) {
                errMsg = LocalText.getText("NotYetStarted", companyName);
                break;
            }

            // The player may not have bought this turn, unless the company
            // bought before and now is in the brown area.
            PublicCompany companyBoughtThisTurn =
                (PublicCompany) companyBoughtThisTurnWrapper.value();
            if (companyBoughtThisTurn != null
                    && (companyBoughtThisTurn != company || !company.getCurrentSpace().isNoBuyLimit())) {
                errMsg = LocalText.getText("AlreadyBought", playerName);
                break;
            }

            // Check if that many shares are available
            if (shares > from.getShare(company)) {
                errMsg =
                    LocalText.getText("NotAvailable",
                            companyName,
                            from.getId() );
                break;
            }

            StockSpace currentSpace;
            if (from == ipo && company.hasParPrice()) {
                currentSpace = company.getStartSpace();
            } else {
                currentSpace = company.getCurrentSpace();
            }

            // Check if it is allowed to buy more than one certificate (if
            // requested)
            if (number > 1 && !currentSpace.isNoBuyLimit()) {
                errMsg = LocalText.getText("CantBuyMoreThanOne", companyName);
                break;
            }

            // Check if player would not exceed the certificate limit.
            // (shortcut: assume 1 cert == 1 certificate)
            PublicCertificate cert = from.findCertificate(company, sharePerCert/shareUnit, false);
            if (cert == null) {
                log.error("Cannot find "+sharePerCert+"% of "+company.getId()+" in "+from.getId());
            }
            if (!currentSpace.isNoCertLimit()
                    && !mayPlayerBuyCertificate(currentPlayer, company, number * cert.getCertificateCount())) {
                errMsg =
                    currentPlayer.getId()
                    + LocalText.getText("WouldExceedCertLimit",
                            String.valueOf(gameManager.getPlayerCertificateLimit(currentPlayer)));
                break;
            }

            // Check if player would exceed the per-company share limit
            if (!currentSpace.isNoHoldLimit()
                    && !checkAgainstHoldLimit(currentPlayer, company, shares)) {
                errMsg = LocalText.getText("WouldExceedHoldLimit",
                        currentPlayer.getId(),
                        GameDef.Parm.PLAYER_SHARE_LIMIT.defaultValueAsInt());
                break;
            }

            price = getBuyPrice (action, currentSpace);
            cost = shares * price / company.getShareUnitsForSharePrice();

            // Check if the Player has the money.
            if (currentPlayer.getCashValue() < cost) {
                errMsg = LocalText.getText("NoMoney");
                break;
            }

            break;
        }

        if (errMsg != null) {
            DisplayBuffer.add(this, LocalText.getText("CantBuy",
                    playerName,
                    shares,
                    companyName,
                    from.getId(),
                    errMsg ));
            return false;
        }

        // All seems OK, now buy the shares.
        

        MoneyOwner priceRecipient = getSharePriceRecipient(company, from.getParent(), cost);

        if (number == 1) {
            ReportBuffer.add(this, LocalText.getText("BUY_SHARE_LOG",
                    playerName,
                    share,
                    companyName,
                    from.getName(),
                    Bank.format(this, cost) ));
        } else {
            ReportBuffer.add(this, LocalText.getText("BUY_SHARES_LOG",
                    playerName,
                    number,
                    share,
                    shares,
                    companyName,
                    from.getName(),
                    Bank.format(this, cost) ));
        }
        ReportBuffer.getAllWaiting(this );

        PublicCertificate cert2;
        for (int i = 0; i < number; i++) {
            cert2 = from.findCertificate(company, sharePerCert/shareUnit, false);
            if (cert2 == null) {
                log.error("Cannot find " + companyName + " " + shareUnit*sharePerCert
                        + "% share in " + from.getId());
            }
            cert2.moveTo(currentPlayer);
        }

        String costText = Currency.wire(currentPlayer, cost, priceRecipient);
        if (priceRecipient != from.getMoneyOwner()) {
            ReportBuffer.add(this, LocalText.getText("PriceIsPaidTo",
                    costText,
                    priceRecipient.getId() ));
        }

        companyBoughtThisTurnWrapper.set(company);
        hasActed.set(true);
        setPriority();

        // Check if presidency has changed
        company.checkPresidencyOnBuy(currentPlayer);

        // Check if the company has floated
        if (!company.hasFloated()) checkFlotation(company);

        // Check for any game-specific consequences
        // (such as making another company available in the IPO)
        gameSpecificChecks(from, company);

        return true;
    }

    /** Stub, may be overridden in subclasses */
    protected void gameSpecificChecks(PortfolioModel boughtFrom,
            PublicCompany company) {

    }

    /** Allow different price setting in subclasses (i.e. 1835 Nationalisation) */
    protected int getBuyPrice (BuyCertificate action, StockSpace currentSpace) {
        return currentSpace.getPrice();
    }

    /**
     * Who receives the cash when a certificate is bought.
     * With incremental capitalization, this can be the company treasure.
     * This method must be called <i>before</i> transferring the certificate.
     * @param cert
     * @return
     */
    protected MoneyOwner getSharePriceRecipient (PublicCompany comp,
            Owner from, int price) {

        MoneyOwner recipient;
        if (comp.hasFloated()
                && from == ipo.getParent()
                && comp.getCapitalisation() == PublicCompany.CAPITALISE_INCREMENTAL) {
            recipient = comp;
        } else if (from instanceof BankPortfolio) {
            recipient = bank;
        } else {
            recipient = (MoneyOwner)from;
        }
        return recipient;
    }

    /** Make the certificates of one company available for buying
     * by putting these in the IPO.
     * @param company The company to be released.
     */
    protected void releaseCompanyShares(PublicCompany company) {
        Portfolio.moveAll(unavailable.getCertificates(company), ipo.getParent());
    }

    public boolean sellShares(SellShares action)
    // NOTE: Don't forget to keep ShareSellingRound.sellShares() in sync
    {

        PortfolioModel portfolio = currentPlayer.getPortfolioModel();
        String playerName = currentPlayer.getId();
        String errMsg = null;
        String companyName = action.getCompanyName();
        PublicCompany company =
            companyManager.getPublicCompany(action.getCompanyName());
        PublicCertificate presCert = null;
        List<PublicCertificate> certsToSell =
            new ArrayList<PublicCertificate>();
        Player dumpedPlayer = null;
        int presidentShareNumbersToSell = 0;
        int numberToSell = action.getNumber();
        int shareUnits = action.getShareUnits();

        // Dummy loop to allow a quick jump out
        while (true) {

            // Check everything
            if (checkFirstRoundSellRestriction()) {
                errMsg = LocalText.getText("FirstSRNoSell");
                break;
            }
            if (numberToSell <= 0) {
                errMsg = LocalText.getText("NoSellZero");
                break;
            }

            // May not sell in certain cases
            if (!mayCurrentPlayerSellAnything()) {
                errMsg = LocalText.getText("SoldEnough");
                break;
            }

            // Check company
            if (company == null) {
                errMsg = LocalText.getText("NoCompany");
                break;
            }

            // May player sell this company
            if (!mayPlayerSellShareOfCompany(company)) {
                errMsg = LocalText.getText("SaleNotAllowed", companyName);
                break;
            }

            // The player must have the share(s)
            if (portfolio.getShare(company) < numberToSell) {
                errMsg = LocalText.getText("NoShareOwned");
                break;
            }

            // The pool may not get over its limit.
            if (pool.getShare(company) + numberToSell * company.getShareUnit()
                    > getGameParameterAsInt(GameDef.Parm.POOL_SHARE_LIMIT)) {
                errMsg = LocalText.getText("PoolOverHoldLimit");
                break;
            }

            // Find the certificates to sell

            // ... check if there is a dump required
            // Player is president => dump is possible
            if (currentPlayer == company.getPresident() && shareUnits == 1) {
                dumpedPlayer = company.findPlayerToDump();
                if (dumpedPlayer != null) {
                    presidentShareNumbersToSell = PlayerShareUtils.presidentShareNumberToSell(
                            company, currentPlayer, dumpedPlayer, numberToSell);
                    // reduce the numberToSell by the president (partial) sold certificate
                    numberToSell -= presidentShareNumbersToSell;
                }
            }
            
            certsToSell = PlayerShareUtils.findCertificatesToSell(company, currentPlayer, numberToSell, shareUnits);
            
            // reduce numberToSell to double check
            for (PublicCertificate c:certsToSell) {
                numberToSell -= c.getShares();
            }
            
            // Check if we could sell them all
            if (numberToSell > 0) {
                if (presCert != null) {
                    errMsg = LocalText.getText("NoDumping");
                } else {
                    errMsg = LocalText.getText("NotEnoughShares");
                }
                break;
            }

            break;
        }

        int numberSold = action.getNumber();
        if (errMsg != null) {
            DisplayBuffer.add(this, LocalText.getText("CantSell",
                    playerName,
                    numberSold,
                    companyName,
                    errMsg ));
            return false;
        }

        // All seems OK, now do the selling.

        // Selling price
        int price = getCurrentSellPrice (company);
        int cashAmount = numberSold * price * shareUnits;

        // Save original price as it may be reused in subsequent sale actions in the same turn
        boolean soldBefore = sellPrices.containsKey(company);
        if (!soldBefore) {
            sellPrices.put(company, company.getCurrentSpace());
        }

        

        String cashText = Currency.fromBank(cashAmount, currentPlayer);
        if (numberSold == 1) {
            ReportBuffer.add(this, LocalText.getText("SELL_SHARE_LOG",
                    playerName,
                    company.getShareUnit() * shareUnits,
                    companyName,
                    cashText));
        } else {
            ReportBuffer.add(this, LocalText.getText("SELL_SHARES_LOG",
                    playerName,
                    numberSold,
                    company.getShareUnit() * shareUnits,
                    numberSold * company.getShareUnit() * shareUnits,
                    companyName,
                    cashText ));
        }

        adjustSharePrice (company, numberSold, soldBefore);

        if (!company.isClosed()) {
 
            executeShareTransfer (company, certsToSell,
                    dumpedPlayer, presidentShareNumbersToSell);
        }

        // Remember that the player has sold this company this round.
        currentPlayer.setSoldThisRound(company);

        if (companyBoughtThisTurnWrapper.value() == null)
            hasSoldThisTurnBeforeBuying.set(true);
        hasActed.set(true);
        setPriority();

        return true;
    }

    // FIXME: Rails 2.x This has to be rewritten to give the new presidency a choice which shares to swap (if he has multiple share certificates)
    protected final void executeShareTransferTo( PublicCompany company,
            List<PublicCertificate> certsToSell, Player dumpedPlayer, int presSharesToSell,
            BankPortfolio bankTo) {

        // Check if the presidency has changed
        if (dumpedPlayer != null && presSharesToSell > 0) {
       
            PlayerShareUtils.executePresidentTransferAfterDump(company, dumpedPlayer, bankTo, presSharesToSell);

            ReportBuffer.add(this, LocalText.getText("IS_NOW_PRES_OF",
                    dumpedPlayer.getId(),
                    company.getId() ));

        }

        // Transfer the sold certificates
        Portfolio.moveAll(certsToSell, bankTo);
        
    }
    
    protected void executeShareTransfer( PublicCompany company,
            List<PublicCertificate> certsToSell, 
            Player dumpedPlayer, int presSharesToSell) {
        
        executeShareTransferTo(company, certsToSell, dumpedPlayer, presSharesToSell, (BankPortfolio)pool.getParent() );
    }

    protected int getCurrentSellPrice (PublicCompany company) {

        int price;

        if (sellPrices.containsKey(company)
                && GameOption.getAsBoolean(this, "SeparateSalesAtSamePrice")) {
            price = (sellPrices.get(company)).getPrice();
        } else {
            price = company.getCurrentSpace().getPrice();
        }
        // stored price is the previous unadjusted price
        price = price / company.getShareUnitsForSharePrice();
        return price;
    }

    protected void adjustSharePrice (PublicCompany company, int numberSold, boolean soldBefore) {

        if (!company.canSharePriceVary()) return;

        stockMarket.sell(company, numberSold);

        StockSpace newSpace = company.getCurrentSpace();

        if (newSpace.closesCompany() && company.canClose()) {
            company.setClosed();
            ReportBuffer.add(this, LocalText.getText("CompanyClosesAt",
                    company.getId(),
                    newSpace.getId()));
            return;
        }

        // Company is still open

    }

    public boolean useSpecialProperty(UseSpecialProperty action) {

        SpecialProperty sp = action.getSpecialProperty();

        // TODO This should work for all subclasses, but not all have execute()
        // yet.
        if (sp instanceof ExchangeForShare) {

            boolean result = executeExchangeForShare(action, (ExchangeForShare) sp);
            if (result) hasActed.set(true);
            return result;

        } else {
            return false;
        }
    }

    // TODO: Check if this still does work, there is a cast involved now
    public boolean executeExchangeForShare (UseSpecialProperty action, ExchangeForShare sp) {

        PublicCompany publicCompany =
            companyManager.getPublicCompany(sp.getPublicCompanyName());
        PrivateCompany privateCompany = (PrivateCompany)sp.getOriginalCompany();
        Owner owner= privateCompany.getOwner();
        Player player = null;
        String errMsg = null;
        boolean ipoHasShare = ipo.getShare(publicCompany) >= sp.getShare();
        boolean poolHasShare = pool.getShare(publicCompany) >= sp.getShare();

        while (true) {

            /* Check if the private is owned by a player */
            if (!(owner instanceof Player)) {
                errMsg =
                    LocalText.getText("PrivateIsNotOwnedByAPlayer",
                            privateCompany.getId());
                break;
            }

            player = (Player) owner;

            /* Check if a share is available */
            if (!ipoHasShare && !poolHasShare) {
                errMsg =
                    LocalText.getText("NoSharesAvailable",
                            publicCompany.getId());
                break;
            }
            /* Check if the player has room for a share of this company */
            if (!checkAgainstHoldLimit(player, publicCompany, 1)) {
                // TODO: Not nice to use '1' here, should be percentage.
                errMsg =
                    LocalText.getText("WouldExceedHoldLimit",
                            String.valueOf(getGameParameterAsInt(GameDef.Parm.PLAYER_SHARE_LIMIT)));
                break;
            }
            break;
        }
        if (errMsg != null) {
            DisplayBuffer.add(this, LocalText.getText(
                    "CannotSwapPrivateForCertificate",
                    player.getId(),
                    privateCompany.getId(),
                    sp.getShare(),
                    publicCompany.getId(),
                    errMsg ));
            return false;
        }

        

        Certificate cert =
            ipoHasShare ? ipo.findCertificate(publicCompany,
                    false) : pool.findCertificate(publicCompany,
                            false);
            cert.moveTo(player);
            ReportBuffer.add(this, LocalText.getText("SwapsPrivateForCertificate",
                    player.getId(),
                    privateCompany.getId(),
                    sp.getShare(),
                    publicCompany.getId()));
            sp.setExercised();
            privateCompany.setClosed();

            // Check if the company has floated
            if (!publicCompany.hasFloated()) checkFlotation(publicCompany);

            return true;
    }

    /**
     * The current Player passes or is done.
     * @param action TODO
     * @param player Name of the passing player.
     *
     * @return False if an error is found.
     */
    public boolean done(NullAction action, String playerName, boolean hasAutopassed) {

        //currentPlayer = getCurrentPlayer();

        if (!playerName.equals(currentPlayer.getId())) {
            DisplayBuffer.add(this, LocalText.getText("WrongPlayer", playerName, currentPlayer.getId()));
            return false;
        }

        

        if (hasActed.value()) {
            numPasses.set(0);
        } else {
            numPasses.add(1);
            if (hasAutopassed) {
                if (!hasAutopassed(currentPlayer)) {
                    setAutopass (currentPlayer, true);
                    setCanRequestTurn (currentPlayer, true);
                }
                ReportBuffer.add(this, LocalText.getText("Autopasses",
                        currentPlayer.getId()));
            } else {
                ReportBuffer.add(this, LocalText.getText("PASSES",
                        currentPlayer.getId()));
            }
        }

        if (numPasses.value() >= getNumberOfActivePlayers()) {

            finishRound();

        } else {

            finishTurn();

        }
        return true;
    }

    @Override
    protected void finishRound () {

        ReportBuffer.add(this, " ");
        ReportBuffer.add(this, LocalText.getText("END_SR",
                String.valueOf(getStockRoundNumber())));

        if (raiseIfSoldOut) {
            /* Check if any companies are sold out. */
        for (PublicCompany company : gameManager.getCompaniesInRunningOrder()) {
                if (company.hasStockPrice() && company.isSoldOut()) {
                StockSpace oldSpace = company.getCurrentSpace();
                    stockMarket.soldOut(company);
                StockSpace newSpace = company.getCurrentSpace();
                    if (newSpace != oldSpace) {
                        ReportBuffer.add(this, LocalText.getText("SoldOut",
                            company.getId(),
                            Bank.format(this, oldSpace.getPrice()),
                            oldSpace.getId(),
                            Bank.format(this, newSpace.getPrice()),
                            newSpace.getId()));
                    } else {
                        ReportBuffer.add(this, LocalText.getText("SoldOutNoRaise",
                            company.getId(),
                            Bank.format(this, newSpace.getPrice()),
                            newSpace.getId()));
                    }
                }
            }
        }
        
        // reset soldThisRound
        for (Player player:playerManager.getPlayers()) {
            player.resetSoldThisRound();
        }


        super.finishRound();
    }

    protected boolean requestTurn (RequestTurn action) {

        Player requestingPlayer = playerManager.getPlayerByName(action.getRequestingPlayerName());

        boolean result = canRequestTurn(requestingPlayer);

        if (!result) {
            DisplayBuffer.add(this, LocalText.getText("CannotRequestTurn",
                    requestingPlayer.getId()));
            return false;
        }

        
        if (hasAutopassed(requestingPlayer)) {
            setAutopass(requestingPlayer, false);
        } else {
            requestTurn(requestingPlayer); // TODO: Check if this still works, replaces requestTurn.add(..)
        }

        return true;
    }

    protected void finishTurn() {

        setNextPlayer();
        sellPrices.clear();
        if (hasAutopassed(currentPlayer)) {
            if (isPlayerOverLimits(currentPlayer)) {
                // Being over a share/certificate limit undoes an Autopass setting
                setAutopass (currentPlayer, false);
            } else {
                // Process a pass for a player that has set Autopass
        		done (null, currentPlayer.getId(), true);
            }
        }
    }

    /**
     * Internal method: pass the turn to the next player.
     */
    protected void setNextPlayer() {

        getRoot().getPlayerManager().setCurrentToNextPlayer();
        initPlayer();
    }

    protected void initPlayer() {

        currentPlayer = playerManager.getCurrentPlayer();
        companyBoughtThisTurnWrapper.set(null);
        hasSoldThisTurnBeforeBuying.set(false);
        hasActed.set(false);
        if (currentPlayer == startingPlayer) ReportBuffer.add(this, "");
    }

    /**
     * Remember the player that has the Priority Deal. <b>Must be called BEFORE
     * setNextPlayer()!</b>
     */
    protected void setPriority() {
        getRoot().getPlayerManager().setPriorityPlayerToNext();
    }

    @Override
    public void setCurrentPlayer(Player player) {
        getRoot().getPlayerManager().setCurrentPlayer(player);
        currentPlayer = player;
    }

    /*----- METHODS TO BE CALLED TO SET UP THE NEXT TURN -----*/

    /**
     * @return The index of the player that has the turn.
     */
    public int getCurrentPlayerIndex() {
        return currentPlayer.getIndex();
    }
    
    /**
     * @return true if first round sell restriction is active
     */
    private boolean checkFirstRoundSellRestriction() {
        if (noSaleInFirstSR() && getStockRoundNumber() == 1) {
            // depending on GameOption restriction is either valid during the first (true) Stock Round or the first Round 
            if (GameOption.getValue(this, "FirstRoundSellRestriction").equals("First Stock Round")) {
                return true;
            } else if (GameOption.getValue(this, "FirstRoundSellRestriction").equals("First Round")) {
                // if all players have passed it is not the first round
                return !gameManager.getFirstAllPlayersPassed();
            }
        }
        return false;
    }

    /**
     * Can the current player do any selling?
     *
     * @return True if any selling is allowed.
     */
    public boolean mayCurrentPlayerSellAnything() {

        if (checkFirstRoundSellRestriction()) {
            return false;
        }
 
        if (companyBoughtThisTurnWrapper.value() != null
                && (sequenceRule == SELL_BUY_OR_BUY_SELL
                        && hasSoldThisTurnBeforeBuying.value() || sequenceRule == SELL_BUY)) {
                return false;
        }
        return true;
    }

    public boolean mayPlayerSellShareOfCompany(PublicCompany company) {

        // Can't sell shares that have no price
        if (!company.hasStarted() || !company.hasStockPrice()) return false;

        // In some games, can't sell shares if not operated
        if (noSaleIfNotOperated()
                && !company.hasOperated()) return false;

        return true;
    }


    /**
     * Can the current player do any buying?
     * <p>Note: requires sellable shares to be checked BEFORE buyable shares
     *
     * @return True if any buying is allowed.
     */
    public boolean mayCurrentPlayerBuyAnything() {
        return !isOverLimits && companyBoughtThisTurnWrapper.value() == null;
    }

    // Only used now to check if Autopass must be reset.
    protected boolean isPlayerOverLimits(Player player) {

        // Over the total certificate hold Limit?
        if (player.getPortfolioModel().getCertificateCount() > gameManager.getPlayerCertificateLimit(player)) {
            return true;
        }

        // Over the hold limit of any company?
        for (PublicCompany company : companyManager.getAllPublicCompanies()) {
            if (company.hasStarted() && company.hasStockPrice()
                    && !checkAgainstHoldLimit(player, company, 0)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a player may buy the given number of certificates.
     *
     * @param number Number of certificates to buy (usually 1 but not always
     * so).
     * @return True if it is allowed.
     */
    public boolean mayPlayerBuyCertificate(Player player, PublicCompany comp, float number) {
        if (comp.hasFloated() && comp.getCurrentSpace().isNoCertLimit())
            return true;
        if (player.getPortfolioModel().getCertificateCount() + number > gameManager.getPlayerCertificateLimit(player))
            return false;
        return true;
    }

    /**
     * Check if a player may buy the given number of shares from a given
     * company, given the "hold limit" per company, that is the percentage of
     * shares of one company that a player may hold (typically 60%).
     *
     * @param player the buying player
     * @param company The company from which to buy
     * @param number The number of shares (usually 1 but not always so)
     * @return True if it is allowed.
     */
    public boolean checkAgainstHoldLimit(Player player, PublicCompany company,
            int number) {
        // Check for per-company share limit
        if (player.getPortfolioModel().getShare(company)
                + number * company.getShareUnit()
                > getGameParameterAsInt(GameDef.Parm.PLAYER_SHARE_LIMIT)
                && !company.getCurrentSpace().isNoHoldLimit()
                && !isSellObligationLifted(company)) return false;
        return true;
    }

    /**
     * Return the number of <i>additional</i> shares of a certain company and
     * of a certain size that a player may buy, given the share "hold limit" per
     * company, that is the percentage of shares of one company that a player
     * may hold (typically 60%). <p>If no hold limit applies, it is taken to be
     * 100%.
     *
     * @param company The company from which to buy
     * @param number The share unit (typically 10%).
     * @return The maximum number of such shares that would not let the player
     * overrun the per-company share hold limit.
     */
    public int maxAllowedNumberOfSharesToBuy(Player player,
            PublicCompany company,
            int shareSize) {

        int limit;
        int playerShareLimit = getGameParameterAsInt (GameDef.Parm.PLAYER_SHARE_LIMIT);
        if (!company.hasStarted()) {
            limit = playerShareLimit;
        } else {
            limit =
                company.getCurrentSpace().isNoHoldLimit() ? 100
                        : playerShareLimit;
        }
        int maxAllowed = (limit - player.getPortfolioModel().getShare(company)) / shareSize;
        //        log.debug("MaxAllowedNumberOfSharesToBuy = " + maxAllowed + " for company =  " + company + " shareSize " + shareSize);
        return maxAllowed;
    }


    protected boolean noSaleInFirstSR() {
        return (Boolean) gameManager.getGameParameter(GameDef.Parm.NO_SALE_IN_FIRST_SR);
    }

    protected boolean noSaleIfNotOperated() {
        return (Boolean) gameManager.getGameParameter(GameDef.Parm.NO_SALE_IF_NOT_OPERATED);
    }

    @Override
    public String getHelp() {
        return LocalText.getText("SRHelpText");
    }

    @Override
    public String getRoundName() {
        return "StockRound " + getStockRoundNumber();
    }

	public boolean isSellObligationLifted(PublicCompany company) {
        return sellObligationLifted != null
        && sellObligationLifted.contains(company);
    }

	public void setSellObligationLifted (PublicCompany company) {
        if (sellObligationLifted == null) {
			sellObligationLifted = HashSetState.create(this, "sellObligationLifted");
        }
        sellObligationLifted.add(company);
    }

}