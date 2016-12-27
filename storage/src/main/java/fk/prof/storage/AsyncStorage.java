package fk.prof.storage;

import java.io.InputStream;
import java.util.concurrent.Future;

/**
 * @author gaurav.ashok
 */
public interface AsyncStorage {

    void store(String path, InputStream content);

    Future<InputStream> fetch(String path);
}
