package net.sf.rails.game.special;

import net.sf.rails.common.LocalText;
import net.sf.rails.common.parser.ConfigurationException;
import net.sf.rails.common.parser.Tag;
import net.sf.rails.game.*;
import net.sf.rails.util.*;

public final class ExchangeForShare extends SpecialProperty {

    /** The public company of which a share can be obtained. */
    protected String publicCompanyName;

    /** The share size */
    protected int share;


    /**
     * Used by Configure (via reflection) only
     */
    public ExchangeForShare(RailsItem parent, String id) {
        super(parent, id);
    }

    @Override
    public void configureFromXML(Tag tag) throws ConfigurationException {

        super.configureFromXML(tag);

        Tag swapTag = tag.getChild("ExchangeForShare");
        if (swapTag == null) {
            throw new ConfigurationException("<ExchangeForShare> tag missing");
        }

        publicCompanyName = swapTag.getAttributeAsString("company");
        if (!Util.hasValue(publicCompanyName))
            throw new ConfigurationException(
                    "ExchangeForShare: company name missing");
        share = swapTag.getAttributeAsInteger("share", 10);
    }

    public boolean isExecutionable() {
        // FIXME: Check if this works correctly
        // IT is better to rewrite this check
        return ((PrivateCompany)originalCompany).getOwner() instanceof Player;
    }

    /**
     * @return Returns the publicCompanyName.
     */
    public String getPublicCompanyName() {
        return publicCompanyName;
    }

    /**
     * @return Returns the share.
     */
    public int getShare() {
        return share;
    }

    @Override
    public String toText() {
        return "Swap " + originalCompany.getId() + " for " + share
               + "% share of " + publicCompanyName;
    }

    @Override
    public String toMenu() {
        return LocalText.getText("SwapPrivateForCertificate",
                originalCompany.getId(),
                share,
                publicCompanyName );
    }

    public String getInfo() {
        return toMenu();
    }
}
