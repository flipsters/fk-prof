package fk.prof.userapi.api.cache;

import fk.prof.userapi.ServiceUnavailableException;

/**
 * Created by gaurav.ashok on 03/08/17.
 */
public class ZkStoreNotConnectedException extends ServiceUnavailableException {

    public ZkStoreNotConnectedException(String msg) {
        super(msg);
    }

    public ZkStoreNotConnectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
