package rails.game.state;

final class PortfolioChange<T extends OwnableItem<T>> implements Change {

    private final Portfolio<T> in;
    private final Portfolio<T> out; // can be null
    private final T item;

    PortfolioChange(Portfolio<T> in, Portfolio<T> out, T item) {
        this.in = in;
        this.out = out;
        this.item = item;
        
        ChangeStack.add(this);
    }
    
    public void execute() {
        in.change(item, true);
        if (out != null) {
            out.change(item,  false);
        }
    }

    public void undo() {
        in.change(item,  false);
        if (out != null) {
            out.change(item, true);
        }
    }

    public Portfolio<? super T> getState() {
        return in;
    }
    
    @Override
    public String toString() {
        if (out == null) {
            return "PortfolioChange: Adds item " + item + " to portfolio " + in.getId();
        } else {
            return "PortfolioChange: Moves item " + item + " from portfolio " + out.getId() + " to portfolio " + in.getId();
        }
    }

}