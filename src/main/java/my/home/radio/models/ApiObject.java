package my.home.radio.models;


import java.io.IOException;

public interface ApiObject<T> {
    T call() throws IOException;
}
