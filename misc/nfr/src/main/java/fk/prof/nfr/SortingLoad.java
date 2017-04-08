package fk.prof.nfr;

import org.apache.commons.lang3.RandomStringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by gaurav.ashok on 08/04/17.
 */
public class SortingLoad {

    int size;
    List<String> list = new ArrayList<>();

    public SortingLoad(int size) {
        this.size = size;
    }

    public int doWork() {
        list.clear();

        for(int i = 0; i < size; ++i) {
            list.add(RandomStringUtils.randomAlphanumeric(64));
        }

        List<String> sortedList = list.stream().sorted().collect(Collectors.toList());

        return findLocation(sortedList, "pointy needle", 0);
    }

    private int findLocation(List<String> haystack, String needle, int i) {
        if(i >= haystack.size()) {
            return i;
        }
        return haystack.get(i).compareTo(needle) >= 0 ? i : findLocation(haystack, needle, i + 1);
    }
}
