package fk.prof.userapi.util;

import com.amazonaws.util.StringUtils;
import io.vertx.core.http.HttpServerRequest;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by gaurav.ashok on 07/08/17.
 */
public class HttpRequestUtil {
    public static <T> T getParam(HttpServerRequest request, String param, Class<T> clazz, boolean required) {
        String value = request.getParam(param);
        if(required && StringUtils.isNullOrEmpty(value)) {
            throw new IllegalArgumentException(param + " is a required request parameter");
        }
        if(!StringUtils.isNullOrEmpty(value)) {
            try {
                if(String.class.equals(clazz)) {
                    return (T) value;
                }
                else if(Integer.class.equals(clazz)) {
                    return (T) Integer.valueOf(value);
                }
                else if(ZonedDateTime.class.equals(clazz)) {
                    return (T) ZonedDateTime.parse(value, DateTimeFormatter.ISO_ZONED_DATE_TIME);
                }
                else if(Boolean.class.equals(clazz)) {
                    return (T) Boolean.valueOf(value);
                }
            }
            catch (Exception e) {
                throw new IllegalArgumentException("Illegal request parameter \"" + param + "\". " + e.getMessage());
            }
            throw new IllegalArgumentException("Request parameter \"" + param + "\" of type " + clazz.getName() + " is not yet supported");
        }
        return null;
    }

    public static String getParam(HttpServerRequest request, String param) {
        return getParam(request, param, String.class);
    }

    public static <T> T getParam(HttpServerRequest request, String param, Class<T> clazz) {
        return getParam(request, param, clazz, true);
    }
}
