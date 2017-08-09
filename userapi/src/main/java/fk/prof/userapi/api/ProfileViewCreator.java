package fk.prof.userapi.api;

import fk.prof.userapi.model.AggregatedOnCpuSamples;
import fk.prof.userapi.model.AggregatedProfileInfo;
import fk.prof.userapi.model.tree.CallTreeView;
import fk.prof.userapi.model.tree.CalleesTreeView;

/**
 * Created by gaurav.ashok on 08/08/17.
 */
public class ProfileViewCreator {

    public CallTreeView buildCallTreeView(AggregatedProfileInfo profile, String traceName) {
        AggregatedOnCpuSamples samplesData = (AggregatedOnCpuSamples) profile.getAggregatedSamples(traceName).getAggregatedSamples();
        return new CallTreeView(samplesData.getCallTree());
    }

    public CalleesTreeView buildCalleesTreeView(AggregatedProfileInfo profile, String traceName) {
        AggregatedOnCpuSamples samplesData = (AggregatedOnCpuSamples) profile.getAggregatedSamples(traceName).getAggregatedSamples();
        return new CalleesTreeView(samplesData.getCallTree());
    }
}
