package model;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

/**
 * Connects with the D42 Simple Storage Service
 * Created by rohit.patiyal on 18/01/17.
 */
public class D42Verticle extends AbstractVerticle {
    private static final String ACCESS_KEY = "66ZX9WC7ZRO6S5BSO8TG";
    private static final String SECRET_KEY = "fGEJrdiSWNJlsZTiIiTPpUntDm0wCRV4tYbwu2M+";
    private static AmazonS3 conn;

    public static void ShowBuckets() {
        for (Bucket bucket : conn.listBuckets()) {
            System.out.println(bucket.getName() + " | " + bucket.getCreationDate() + " | " + bucket.getOwner());
        }
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        //super.start(startFuture);
        vertx.executeBlocking(future -> {

        }, asyncResult -> {
            if (asyncResult.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(asyncResult.cause());
            }
        });
    }

}
