package fk.prof.storage.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Utility class containing methods for converting
 * data types
 * Created by rohit.patiyal on 17/05/17.
 */
public class DataConverterUtil {
  public static byte[] inputStreamToByteArray(InputStream inputStream) throws IOException {
    StringBuilder sb = new StringBuilder();
    BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
    String line;
    while ((line = br.readLine()) != null) {
      sb.append(line);
    }
    return sb.toString().getBytes();
  }
}
