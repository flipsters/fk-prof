package fk.prof.recorder.e2e;

import fk.prof.recorder.utils.FileResolver;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableBoolean;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by gaurav.ashok on 06/03/17.
 */
public class UserapiProcess {

    private static final Logger logger = LoggerFactory.getLogger(UserapiProcess.class);

    Path confPath;
    Process process;
    String[] command;

    public UserapiProcess(Path confPath) {
        this.confPath = confPath;
        buildCommand();
    }

    public void start() throws IOException {
        process = new ProcessBuilder()
                .command(command)
                .redirectError(new File("/tmp/fkprof_userapi_stderr.log"))
                .redirectOutput(new File("/tmp/fkprof_userapi_stdout.log"))
                .start();
    }

    private void buildCommand() {
        String java = System.getProperty("java.home") + "/bin/java";

        Mutable<Boolean> isFatJar = new MutableBoolean();

        String dir = "../userapi/target/";
        Path jarFile = FileResolver.jarFile("userapi", dir, "userapi-.*.jar", isFatJar);

        if(isFatJar.getValue()) {
            command = new String[] {java, "-jar", jarFile.toAbsolutePath().toString(), "--conf", confPath.toString()};
        }
        else {
            List<String> classpath = Arrays.asList(jarFile.toAbsolutePath().toString(), dir + "lib/*");
            command = new String[] {java, "-cp", String.join(":", classpath), "io.vertx.core.Launcher", "--conf", confPath.toString()};
        }
    }

    public void stop() {
        process.destroy();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.info(e.getMessage(), e);
        }
    }

    public String[] getCommand() {
        String[] javaParams = new String[command.length - 1];
        System.arraycopy(command, 1, javaParams, 0, javaParams.length);
        return javaParams;
    }
}
