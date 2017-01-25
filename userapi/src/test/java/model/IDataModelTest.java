package model;

import fk.prof.storage.D42AsyncStorage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

/**
 * Tests for DataModel Interface
 * Created by rohit.patiyal on 24/01/17.
 */
@RunWith(MockitoJUnitRunner.class)
public class IDataModelTest {

    @InjectMocks
    IDataModel iDataModel = new D42Model();

    @Mock
    D42AsyncStorage asyncStorage;

    @Test
    public void TestGetAppIdsWithPrefix() throws Exception {
        String prefix = "v001";
        String delimiter = "_";
        String bucket = "bck1/";

        given(asyncStorage.getCommonPrefixes(bucket + prefix + delimiter)).willReturn(new HashSet<>());
        iDataModel.getAppIdsWithPrefix(prefix);

        then(asyncStorage).should(times(1)).getCommonPrefixes(bucket + prefix + delimiter);
        then(asyncStorage).shouldHaveNoMoreInteractions();
    }

    @Test
    public void TestGetClusterIdsWithPrefix() throws Exception {

    }
}