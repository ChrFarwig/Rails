package ui;

import game.*;
import game.move.MoveSet;
import game.special.ExchangeForShare;
import game.special.SpecialSRProperty;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import java.util.List;

import util.LocalText;

/**
 * This is the Window used for displaying nearly all of the game status. This is
 * also from where the ORWindow and StartRoundWindow are triggered.
 */
public class StatusWindow extends JFrame implements ActionListener, KeyListener
{
    protected static final String QUIT_CMD = "Quit";
    protected static final String SAVE_CMD = "Save";
    protected static final String UNDO_CMD = "Undo";
    protected static final String REDO_CMD = "Redo";
    protected static final String MARKET_CMD = "Market";
    protected static final String MAP_CMD = "Map";
    protected static final String REPORT_CMD = "Report";
    protected static final String BUY_CMD = "Buy";
    protected static final String SELL_CMD = "Sell";
    protected static final String DONE_CMD = "Done";
    protected static final String SWAP_CMD = "Swap";

	private JPanel buttonPanel;
	private GameStatus gameStatus;
	private JButton buyButton, sellButton, passButton, extraButton;
	private Player player;
	private PublicCompanyI[] companies;
	private PublicCompanyI company;
	private CompanyManagerI cm;
	private Portfolio ipo, pool;
	private int compIndex, playerIndex;

	/*----*/
	private GameManager gmgr;
	private RoundI currentRound;
	private RoundI previousRound = null;
	private StockRound stockRound;
	private List buyableCertificates;
	private List sellableCertificates;
	private StartRound startRound;
	private StartRoundWindow startRoundWindow;
	private OperatingRound operatingRound;
	// private ORWindow orWindow;
	private int np = GameManager.getNumberOfPlayers();
	private int nc;

	JPanel pane = new JPanel(new BorderLayout());

	private JMenuBar menuBar;
	private static JMenu fileMenu, optMenu, moveMenu;
	private JMenuItem menuItem, undoItem, redoItem;

	/**
	 * Selector for the pattern to be used in keeping the individual UI fields
	 * up-to-date: <br> - true: push changes (via the Observer/Observable
	 * pattern), <br> - false: pull everything on repaint. NOTE: currently,
	 * 'false' does not work for the stock chart.
	 */
	public static boolean useObserver = true;

	public void initMenu()
	{
		menuBar = new JMenuBar();
		fileMenu = new JMenu(LocalText.getText("FILE"));
		optMenu = new JMenu(LocalText.getText("OPTIONS"));
		moveMenu = new JMenu(LocalText.getText("MOVE"));

		fileMenu.setMnemonic(KeyEvent.VK_F);
		optMenu.setMnemonic(KeyEvent.VK_O);
		moveMenu.setMnemonic(KeyEvent.VK_M);

		menuItem = new JMenuItem(LocalText.getText("SAVE"));
		//menuItem.setName(SAVE_CMD);
		menuItem.setActionCommand(SAVE_CMD);
		menuItem.setMnemonic(KeyEvent.VK_S);
		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
				ActionEvent.ALT_MASK));
		menuItem.addActionListener(this);
		menuItem.setEnabled(false); //XXX: Setting to disabled until we implement load/save
		fileMenu.add(menuItem);

		fileMenu.addSeparator();

		menuItem = new JMenuItem(LocalText.getText("QUIT"));
		//menuItem.setName(QUIT_CMD);
		menuItem.setActionCommand(QUIT_CMD);
		menuItem.setMnemonic(KeyEvent.VK_Q);
		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q,
				ActionEvent.ALT_MASK));
		menuItem.addActionListener(this);
		fileMenu.add(menuItem);

		menuBar.add(fileMenu);

		menuItem = new JMenuItem(LocalText.getText("SET_SCALE"));
		//menuItem.setName(LocalText.getText("SET_SCALE"));
		menuItem.setMnemonic(KeyEvent.VK_S);
		menuItem.addActionListener(this);
		menuItem.setEnabled(false); //XXX: Setting to disabled until we implement this
		optMenu.add(menuItem);

		optMenu.addSeparator();

		menuItem = new JCheckBoxMenuItem(LocalText.getText("MARKET"));
		menuItem.setName(MARKET_CMD);
		menuItem.setActionCommand(MARKET_CMD);
		menuItem.setMnemonic(KeyEvent.VK_K);
		menuItem.addActionListener(this);
		optMenu.add(menuItem);

		menuItem = new JCheckBoxMenuItem(LocalText.getText("MAP"));
		menuItem.setName(MAP_CMD);
		menuItem.setActionCommand(MAP_CMD);
		menuItem.setMnemonic(KeyEvent.VK_M);
		menuItem.addActionListener(this);
		optMenu.add(menuItem);

		menuItem = new JCheckBoxMenuItem(LocalText.getText("REPORT"));
		menuItem.setName(LocalText.getText("REPORT"));
		menuItem.setActionCommand(REPORT_CMD);
		menuItem.setMnemonic(KeyEvent.VK_L);
		menuItem.addActionListener(this);
		optMenu.add(menuItem);

		menuBar.add(optMenu);

		undoItem = new JMenuItem(LocalText.getText("UNDO"));
		undoItem.setName(LocalText.getText("UNDO"));
		undoItem.setActionCommand(UNDO_CMD);
		undoItem.setMnemonic(KeyEvent.VK_U);
		undoItem.addActionListener(this);
		undoItem.setEnabled(false);
		moveMenu.add(undoItem);

		redoItem = new JMenuItem(LocalText.getText("REDO"));
		redoItem.setName(LocalText.getText("REDO"));
		redoItem.setActionCommand(REDO_CMD);
		redoItem.setMnemonic(KeyEvent.VK_R);
		redoItem.addActionListener(this);
		redoItem.setEnabled(false);
		moveMenu.add(redoItem);
		
		menuBar.add (moveMenu);

		setJMenuBar(menuBar);
	}

	public StatusWindow()
	{
		cm = Game.getCompanyManager();
		companies = (PublicCompanyI[]) cm.getAllPublicCompanies()
				.toArray(new PublicCompanyI[0]);
		ipo = Bank.getIpo();
		pool = Bank.getPool();

		gameStatus = new GameStatus(this);
		buttonPanel = new JPanel();

		extraButton = new JButton(""); // Normally invisible, for special
										// properties.
		extraButton.setVisible(false);
		buyButton = new JButton(LocalText.getText("BUY"));
		sellButton = new JButton(LocalText.getText("SELL"));
		passButton = new JButton(LocalText.getText("PASS"));

		buyButton.setMnemonic(KeyEvent.VK_B);
		sellButton.setMnemonic(KeyEvent.VK_S);
		passButton.setMnemonic(KeyEvent.VK_P);

		buttonPanel.add(extraButton);
		buttonPanel.add(buyButton);
		buttonPanel.add(sellButton);
		buttonPanel.add(passButton);

		buyButton.setActionCommand(BUY_CMD);
		sellButton.setActionCommand(SELL_CMD);
		passButton.setActionCommand(DONE_CMD);

		extraButton.addActionListener(this);
		buyButton.addActionListener(this);
		sellButton.addActionListener(this);
		passButton.addActionListener(this);

		setSize(800, 300);
		setLocation(25, 450);

		buttonPanel.setBorder(BorderFactory.createEtchedBorder());
		buttonPanel.setOpaque(false);

		setTitle(LocalText.getText("GAME_STATUS_TITLE"));
		pane.setLayout(new BorderLayout());
		init();
		initMenu();
		pane.add(gameStatus, BorderLayout.NORTH);
		pane.add(buttonPanel, BorderLayout.CENTER);
		pane.setOpaque(true);
		setContentPane(pane);
		// refreshStatus();
		setVisible(true);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		/*----*/
		gmgr = GameManager.getInstance();
		currentRound = gmgr.getCurrentRound();

		updateStatus();

		gameStatus.addKeyListener(this);
		buttonPanel.addKeyListener(this);
		addKeyListener(this);
	}

	private void init()
	{
		PublicCompanyI[] companies = (PublicCompanyI[]) Game.getCompanyManager()
				.getAllPublicCompanies()
				.toArray(new PublicCompanyI[0]);
		nc = companies.length;
	}

	public void updateStatus()
	{
		currentRound = GameManager.getInstance().getCurrentRound();

		if (currentRound instanceof StartRound)
		{
			passButton.setEnabled(false);
			startRound = (StartRound) currentRound;
			if (startRoundWindow == null)
				startRoundWindow = new StartRoundWindow(startRound, this);
			startRoundWindow.setSRPlayerTurn(startRound.getCurrentPlayerIndex());

			if (currentRound != previousRound)
			{
				GameUILoader.stockChart.setVisible(false);
				GameUILoader.orWindow.setVisible(false);

				disableCheckBoxMenuItem(MAP_CMD);
				disableCheckBoxMenuItem(MARKET_CMD);
			}
		}
		else if (currentRound instanceof StockRound)
		{

			stockRound = (StockRound) currentRound;
			buyableCertificates = stockRound.getBuyableCerts();
			sellableCertificates = stockRound.getSellableCerts();
			gameStatus.setBuyableCertificates(buyableCertificates);
			gameStatus.setSellableCertificates(sellableCertificates);
			gameStatus.setSRPlayerTurn(GameManager.getCurrentPlayerIndex());
			gameStatus.setPriorityPlayer(GameManager.getPriorityPlayer().getIndex());
			
			undoItem.setEnabled(MoveSet.isUndoable());
			redoItem.setEnabled(MoveSet.isRedoable());

			if ((currentRound instanceof ShareSellingRound))
			{
				passButton.setEnabled(false);
				int cash = ((ShareSellingRound) currentRound).getRemainingCashToRaise();
				if (sellableCertificates.isEmpty())
				{
					JOptionPane.showMessageDialog(this,
							"You must raise "
									+ Bank.format(cash)
									+ ", but you can't sell any more shares, so you are Bankrupt!",
							"",
							JOptionPane.OK_OPTION);
					/*
					 * For now assume that this ends the game (not true in all
					 * games)
					 */
					JOptionPane.showMessageDialog(this,
							GameManager.getInstance().getGameReport(),
							"",
							JOptionPane.OK_OPTION);
					/*
					 * All other wrapping up has already been done when calling
					 * getSellableCertificates, so we can just finish now.
					 */
					finish();
					return;
				}
				else
				{
					JOptionPane.showMessageDialog(this,
							"You must raise " + Bank.format(cash)
									+ " by selling shares",
							"",
							JOptionPane.OK_OPTION);
				}
			}
			else
			{
				passButton.setEnabled(true);
			}

			if (currentRound != previousRound)
			{

				GameUILoader.stockChart.setVisible(true);
				GameUILoader.orWindow.setVisible(false);

				enableCheckBoxMenuItem(MARKET_CMD);
				disableCheckBoxMenuItem(MAP_CMD);
			}

			/* Any special properties in force? */
			player = GameManager.getCurrentPlayer();
			java.util.List specialProperties = stockRound.getSpecialProperties();
			if (specialProperties != null && specialProperties.size() > 0)
			{
				/*
				 * Assume there will only one special property at a time
				 * (because we have only one extra button)
				 */
				SpecialSRProperty sp = (SpecialSRProperty) specialProperties.get(0);
				if (sp instanceof ExchangeForShare)
				{
					extraButton.setText(((ExchangeForShare) sp).getPrivateCompany()
							.getName()
							+ "/"
							+ ((ExchangeForShare) sp).getPublicCompanyName());
					extraButton.setActionCommand(SWAP_CMD);
					extraButton.setVisible(true);
					extraButton.setEnabled(true);
				}
			}
			else
			{
				extraButton.setEnabled(false);
				extraButton.setVisible(false);
			}
			toFront();
		}
		else if (currentRound instanceof OperatingRound)
		{
			passButton.setEnabled(false);
			operatingRound = (OperatingRound) currentRound;

			if (currentRound != previousRound)
			{
				GameUILoader.stockChart.setVisible(false);
				GameUILoader.orWindow.activate();

				enableCheckBoxMenuItem(MAP_CMD);
				disableCheckBoxMenuItem(MARKET_CMD);
			}
		}

		pack();

		previousRound = currentRound;
	}

	private void enableCheckBoxMenuItem(String name)
	{
		for (int x = 0; x < optMenu.getMenuComponentCount(); x++)
		{
			try
			{
				if (optMenu.getMenuComponent(x).getName().equals(name))
				{
					((JCheckBoxMenuItem) optMenu.getMenuComponent(x)).setSelected(true);
				}
			}
			catch (NullPointerException e)
			{
				// The separator has null name. Har Har Har.
			}
		}
	}

	private void disableCheckBoxMenuItem(String name)
	{
		for (int x = 0; x < optMenu.getMenuComponentCount(); x++)
		{
			try
			{
				if (optMenu.getMenuComponent(x).getName().equals(name))
				{
					((JCheckBoxMenuItem) optMenu.getMenuComponent(x)).setSelected(false);
				}
			}
			catch (NullPointerException e)
			{
				// The separator has null name. Har Har Har.
			}
		}
	}

	public void resume(JFrame previous)
	{
		this.requestFocus();
		if (previous instanceof StartRoundWindow)
		{
			startRoundWindow.close();
			startRoundWindow = null;
		}

		currentRound = GameManager.getInstance().getCurrentRound();
		updateStatus();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed(ActionEvent actor)
	{
		int returnVal = 0;
		player = GameManager.getCurrentPlayer();

		if (actor.getActionCommand().equals(BUY_CMD))
		{
			buyButtonClicked();
			passButton.setText(DONE_CMD);
			passButton.setMnemonic(KeyEvent.VK_D);
		}
		else if (actor.getActionCommand().equals(SELL_CMD))
		{
			sellButtonClicked();
			passButton.setText(DONE_CMD);
			passButton.setMnemonic(KeyEvent.VK_D);
		}
		else if (actor.getActionCommand().equals(DONE_CMD))
		{
			if (GameManager.isGameOver())
			{
				System.exit(0);
			}
			stockRound.done(gameStatus.getSRPlayer());
			passButton.setText(LocalText.getText("PASS"));
			passButton.setMnemonic(KeyEvent.VK_P);
		}
		else if (actor.getActionCommand().equals(SWAP_CMD))
		{
			/* Execute a special property (i.e. swap M&H for NYC) */
			SpecialSRProperty sp = (SpecialSRProperty) stockRound.getSpecialProperties()
					.get(0);
			if (sp instanceof ExchangeForShare)
			{
				((ExchangeForShare) sp).execute();
				extraButton.setText("");
				extraButton.setEnabled(false);
				extraButton.setVisible(false);
			}
		}
		else if (actor.getActionCommand().equals(QUIT_CMD))
			System.exit(0);
		// We're not going to actually DO anything with the selected file
		// until the infrastructure for saved games is built
		else if (actor.getActionCommand().equals(SAVE_CMD))
			returnVal = new JFileChooser().showSaveDialog(this);
		else if (actor.getActionCommand().equals(REPORT_CMD))
		{
			GameUILoader.reportWindow.setVisible(((JMenuItem) actor.getSource()).isSelected());
			return;
		}
		else if (actor.getActionCommand().equals(MARKET_CMD))
		{
			GameUILoader.stockChart.setVisible(((JMenuItem) actor.getSource()).isSelected());
			return;
		}
		else if (actor.getActionCommand().equals(MAP_CMD))
		{
			GameUILoader.orWindow.setVisible(((JMenuItem) actor.getSource()).isSelected());
			return;
		} else if (actor.getActionCommand().equals(UNDO_CMD))
		{
		    MoveSet.undo();
		    updateStatus();
			return;
		} else if (actor.getActionCommand().equals(REDO_CMD))
		{
		    MoveSet.redo();
		    updateStatus();
			return;
		} 

		ReportWindow.addLog();

		currentRound = GameManager.getInstance().getCurrentRound();
		if (currentRound instanceof StockRound)
			gameStatus.setSRPlayerTurn(GameManager.getCurrentPlayerIndex());
		else if (currentRound instanceof OperatingRound)
		{
			gameStatus.setSRPlayerTurn(-1);
		}

		updateStatus();
	}

	private void buyButtonClicked()
	{
		playerIndex = GameManager.getCurrentPlayerIndex();

		if ((compIndex = gameStatus.getCompIndexToBuyFromIPO()) >= 0)
		{
			company = companies[compIndex];
			if (company.hasStarted())
			{
				if (!stockRound.buyShare(player.getName(),
						ipo,
						company.getName(),
						1))
				{
					JOptionPane.showMessageDialog(this,
							ReportBuffer.get(),
							"",
							JOptionPane.OK_OPTION);
				}
			}
			else
			{
				startCompany();
			}
		}
		else if ((compIndex = gameStatus.getCompIndexToBuyFromPool()) >= 0)
		{
			company = companies[compIndex];
			if (company.hasStarted())
			{
				if (!stockRound.buyShare(player.getName(),
						pool,
						company.getName(),
						1))
				{
					JOptionPane.showMessageDialog(this,
							ReportBuffer.get(),
							"",
							JOptionPane.OK_OPTION);
				}
			}

		}
		else
		{
			JOptionPane.showMessageDialog(this,
					"Unable to buy share.\r\n"
							+ "You must select a company first.",
					"No share bought.",
					JOptionPane.OK_OPTION);

		}
	}

	private void sellButtonClicked()
	{
		int compIndex;
		int playerIndex = GameManager.getCurrentPlayerIndex();
		if ((compIndex = gameStatus.getCompIndexToSell()) >= 0)
		{
			company = companies[compIndex];
			if (!stockRound.sellShare(player.getName(), company.getName()))
			{
				JOptionPane.showMessageDialog(this, ReportBuffer.get());
			}
		}
		else
		{
			JOptionPane.showMessageDialog(this,
					"Unable to sell share.\r\n"
							+ "You must select a company first.",
					"Share not sold.",
					JOptionPane.OK_OPTION);

		}
	}

	private void startCompany()
	{
		StockMarket stockMarket = (StockMarket) Game.getStockMarket();
		List startOptions = gameStatus.getBuyOrSellOptions();
		String[] options = new String[startOptions.size()];
		for (int i = 0; i < options.length; i++)
		{
			options[i] = Bank.format(((TradeableCertificate) startOptions.get(i)).getPrice());
		}

		if (company != null)
		{
			String sp = (String) JOptionPane.showInputDialog(this,
					"Start company at what price?",
					"What Price?",
					JOptionPane.INFORMATION_MESSAGE,
					null,
					options,
					options[0]);
			int startPrice = Integer.parseInt(sp.replaceAll("\\D", ""));
			if (!stockRound.startCompany(player.getName(),
					company.getName(),
					startPrice))
			{
				JOptionPane.showMessageDialog(this,
						ReportBuffer.get(),
						"",
						JOptionPane.OK_OPTION);
			}
		}
		else
			JOptionPane.showMessageDialog(this,
					"Unable to start company.\r\n"
							+ "You must select a company first.",
					"Company not started.",
					JOptionPane.OK_OPTION);
	}

	public void enableBuyButton(boolean enable)
	{
		buyButton.setEnabled(enable);
		if (enable)
			sellButton.setEnabled(!enable);
	}

	public void enableSellButton(boolean enable)
	{
		sellButton.setEnabled(enable);
		if (enable)
			buyButton.setEnabled(!enable);
	}

	public GameStatus getGameStatus()
	{
		return gameStatus;
	}

	public static void uncheckMenuItemBox(String itemName)
	{
		int count = optMenu.getMenuComponentCount();

		for (int i = 0; i < count; i++)
		{
			try
			{
				if (optMenu.getMenuComponent(i)
						.getName()
						.equalsIgnoreCase(itemName))
				{
					((JCheckBoxMenuItem) optMenu.getMenuComponent(i)).setSelected(false);
					optMenu.invalidate();
				}
			}
			catch (NullPointerException e)
			{
				// Seperators are null
			}
		}
	}

	/**
	 * Finish the application.
	 */
	public void finish()
	{

		/* Complete the log */
		ReportWindow.addLog();

		setVisible(true);
		GameUILoader.reportWindow.setVisible(true);
		GameUILoader.stockChart.setVisible(true);

		/* Disable all buttons */
		passButton.setEnabled(true);
		passButton.setText(LocalText.getText("END_OF_GAME_CLOSE_ALL_WINDOWS"));
		extraButton.setVisible(false);
		buyButton.setVisible(false);
		sellButton.setVisible(false);
		GameUILoader.orWindow.finish();

		toFront();

	}

	public void keyReleased(KeyEvent e)
	{
	}

	public void keyPressed(KeyEvent e)
	{
		if (e.getKeyCode() == KeyEvent.VK_F1)
		{
			HelpWindow.displayHelp(gmgr.getHelp());
			e.consume();
		}
	}

	public void keyTyped(KeyEvent e)
	{
	}
}
