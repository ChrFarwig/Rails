/* $Header: /Users/blentz/rails_rcs/cvs/18xx/rails/game/state/BooleanState.java,v 1.2 2008/06/04 19:00:36 evos Exp $
 * 
 * Created on May 19, 2007
 * Change Log:
 */
package rails.game.state;

/**
 * @author Erik Vos
 */
public class BooleanState extends State {

    /**
     * @param name
     * @param clazz
     */
    public BooleanState(String name) {
        super(name, Boolean.FALSE);
    }

    /**
     * @param name
     * @param object
     */
    public BooleanState(String name, boolean value) {
        super(name, new Boolean(value));
    }

    public void set(boolean value) {
        super.set(new Boolean(value));
    }

    public boolean booleanValue() {
        return ((Boolean) object).booleanValue();
    }
}