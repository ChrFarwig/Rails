package net.sf.rails.game;

/**
 * A BaseToken object represents a token that a operating public company can
 * place on the map to act as a rail building and train running starting point.
 * <p> The "Base" qualifier is used (more or less) consistently in this
 * rails.game program as it most closely the function of such a token: to act as
 * a base from which a company can operate. Other names used in various games
 * and discussions are "railhead", "station", "garrison", or just "token".
 */

public class BaseToken extends Token<BaseToken> {

    private BaseToken(PublicCompany parent, String id) {
        super(parent, id, BaseToken.class);
    }
    
    public static BaseToken create(PublicCompany company) {
        String uniqueId = Token.createUniqueId(company);
        return new BaseToken(company, uniqueId);
    }

    @Override
    public PublicCompany getParent() {
        return (PublicCompany)super.getParent();
    }
    
    public boolean isPlaced() {
        return !(getOwner() == getParent());
    }


    // FIXME: Check if this works, previously it returned the parent id
    // However this was invalid for the portfolio storage
//    public String getId() {
//        return getParent().getId();
//    }

}
