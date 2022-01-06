package net.kenevans.polar.polarecg;

import java.util.LinkedList;

/**
 * Class to implement a LinkedList whose size cannot exceed maxSize.
 * Only the following methods of LinkedList have been modified to do this:
 * add
 * addLast
 * addFirst
 *
 * @param <T>
 */
@SuppressWarnings("unused")
public class FixedSizeList<T> extends LinkedList<T> {
    private final int maxSize;

    /**
     * CTOR for  FixedSizeList.
     *
     * @param size The maximum size. should be > 0;
     */
    public FixedSizeList(int size) {
        this.maxSize = size;
    }

    @Override
    public boolean add(T t) {
        maintainSize();
        return super.add(t);
    }

    @Override
    public void addLast(T t) {
        maintainSize();
        super.addLast(t);
    }

    @Override
    public void addFirst(T t) {
        maintainSize();
        super.addFirst(t);
    }

    /**
     * Sets the last element to this value. The current size should not be
     * zero. Will not be available if the specified type of this List is List
     * (as opposed to FixedSizeList.)
     *
     * @param t The value to set.
     */
    public void setLast(T t) {
        set(size() - 1, t);
    }

    /**
     * Gets the maximum size for this List.
     *
     * @return The maximum size.
     */
    public int maxSize() {
        return maxSize;
    }

    /**
     * Maintains the maxSize of the list. Expected to be called before a new
     * (single) item is added.
     */
    private void maintainSize() {
        int len = size();
        if (len == maxSize) {
            removeFirst();
        }
    }
}
