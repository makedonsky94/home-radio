package my.home.radio.help;


import java.util.*;
import java.util.function.UnaryOperator;

public class Set<T, V> {
    private List<T> keyList;
    private List<V> valueList;

    public Set() {
        keyList = new ArrayList<>();
        valueList = new ArrayList<>();
    }

    public void add(T key, V value) {
        if(keyList.indexOf(key) != -1) {
            throw new IllegalArgumentException("The key has already been added");
        }
        keyList.add(key);
        valueList.add(value);
    }

    public void change(T key, V value) {
        if(keyList.indexOf(key) == -1) {
            throw new IllegalArgumentException("The key doesn't exists");
        }
        int index = keyList.indexOf(key);
        valueList.set(index, value);
    }

    public void remove(int index) {
        keyList.remove(index);
        valueList.remove(index);
    }

    public void remove(T key) {
        int index = keyList.indexOf(key);
        this.remove(index);
    }

    public Element<T, V> get(int index) {
        return new Element<> (
                keyList.get(index),
                valueList.get(index)
        );
    }

    public int size() {
        return keyList.size();
    }

    public static class Element<T, V> {
        private T key;
        private V value;

        public Element(T key, V value) {
            this.key = key;
            this.value = value;
        }

        public T getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }
    }
}
