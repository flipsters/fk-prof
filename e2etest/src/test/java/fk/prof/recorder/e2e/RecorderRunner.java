package fk.prof.recorder.e2e;

import fk.prof.recorder.main.Burn20And80PctCpu;
import fk.prof.recorder.utils.AgentRunner;
import org.junit.Test;

/**
 * Created by gaurav.ashok on 08/03/17.
 */
public class RecorderRunner {
    @Test
    public void testRunRecorder() throws Exception {
        AgentRunner recorder = null;
        try {
            recorder = new AgentRunner(Burn20And80PctCpu.class.getCanonicalName(), "service_endpoint=http://127.0.0.1:2491," +
                    "ip=10.20.30.40," +
                    "host=foo-host," +
                    "appid=bar-app," +
                    "igrp=baz-grp," +
                    "cluster=quux-cluster," +
                    "instid=corge-iid," +
                    "proc=grault-proc," +
                    "vmid=garply-vmid," +
                    "zone=waldo-zone," +
                    "ityp=c0.small," +
                    "backoffStart=2," +
                    "backoffMax=5," +
                    "logLvl=debug"
            );

            recorder.start();
            System.out.println("Starting recorder and going to sleep");

            Thread.sleep(Integer.MAX_VALUE);
        }
        finally {
            if(recorder != null) {
                System.out.println("Stopping recorder");
                recorder.stop();
            }
        }
    }
}
