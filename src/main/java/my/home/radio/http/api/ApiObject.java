package my.home.radio.http.api;


import java.io.IOException;

public interface ApiObject<T> {
    T call() throws IOException;
}
