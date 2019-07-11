package jdk.jfr.consumer;

import jdk.jfr.internal.Type;

final class ConstantLookup {

    private final Type type;
    private ConstantMap current;
    private ConstantMap previous = ConstantMap.EMPTY;

    ConstantLookup(ConstantMap current, Type type) {
        this.current = current;
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public ConstantMap getLatestPool() {
        return current;
    }

    public void newPool() {
        previous = current;
        current = new ConstantMap(current.factory, current.name);
     //   previous =  new ConstantMap(); // disable cache
    }

    public Object getPreviousResolved(long key) {
        return previous.getResolved(key);
    }

    public Object getCurrentResolved(long key) {
        return current.getResolved(key);
    }

    public Object getCurrent(long key) {
        return current.get(key);
    }

}
