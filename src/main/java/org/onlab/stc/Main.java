/*
 * Copyright 2015-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onlab.stc;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.log.Logger;
import org.onlab.stc.Coordinator.Status;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.lang.System.currentTimeMillis;
import static org.onlab.stc.Coordinator.Status.*;
import static org.onlab.stc.Coordinator.print;

/**
 * Main program for executing system test coordinator.
 */
public final class Main {

    private static final String NONE = "\u001B[0m";
    private static final String GRAY = "\u001B[1;30m";
    private static final String RED = "\u001B[1;31m";
    private static final String GREEN = "\u001B[1;32m";
    private static final String LIGHT_GREEN = "\u001B[0;32m";
    private static final String BLUE = "\u001B[0;36m";

    private static final String TITLE = "\033]0;%sScenario %s; %d steps; %d passed, %d failed, %d skipped\007";

    private static final String SUCCESS_SUMMARY =
            "%s %sPassed! %d steps succeeded%s";
    private static final String MIXED_SUMMARY =
            "%s%d steps succeeded; %s%d steps failed; %s%d steps skipped%s";
    private static final String FAILURE_SUMMARY = "%s %sFailed! " + MIXED_SUMMARY;
    private static final String ABORTED_SUMMARY = "%s %sAborted! " + MIXED_SUMMARY;

    private boolean isReported = false;

    private enum Command {
        LIST, LIST_FAILED, RUN, RUN_RANGE, VALIDATE
    }

    private final String scenarioFile;

    private Command command = Command.RUN;
    private String runFromPatterns = "";
    private String runToPatterns = "";

    private Coordinator coordinator;
    private Compiler compiler;
    private Listener delegate = new Listener();

    private static final String stcTitle = System.getenv("stcTitle");
    private static final String stcColor = System.getenv("stcColor");
    private static boolean darkColor = Objects.equals("dark", stcColor);
    private static boolean lightColor = Objects.equals("light", stcColor);
    private static boolean useColor = darkColor || lightColor || Objects.equals("true", stcColor);
    private static boolean dumpLogs = Objects.equals("true", System.getenv("stcDumpLogs"));
    private static boolean haltOnError = Objects.equals("true", System.getenv("stcHaltOnError"));

    // usage: stc [<scenario-file>] [run]
    // usage: stc [<scenario-file>] run [from <from-patterns>] [to <to-patterns>]]
    // usage: stc [<scenario-file>] list
    // usage: stc [<scenario-file>] listFailed

    // Public construction forbidden
    private Main(String[] args) {
        this.scenarioFile = args[0];
        String cmd = args.length >= 2 ? args[1] : "";

        if (isHelpFlag(cmd) || isHelpFlag(this.scenarioFile)) {
            printHelp();
        } else if (args.length <= 1 || args.length == 2 && cmd.equals("run")) {
            command = Command.RUN;
        } else if (args.length == 2 && cmd.equals("validate")) {
            command = Command.VALIDATE;
        } else if (args.length == 2 && cmd.equals("list")) {
            command = Command.LIST;
        } else if (args.length == 2 && cmd.equals("listFailed")) {
            command = Command.LIST_FAILED;
        } else if (args.length >= 4 && cmd.equals("run")) {
            int i = 2;
            if (args[i].equals("from")) {
                command = Command.RUN_RANGE;
                runFromPatterns = args[i + 1];
                i += 2;
            }

            if (args.length >= i + 2 && args[i].equals("to")) {
                command = Command.RUN_RANGE;
                runToPatterns = args[i + 1];
            }
        }
    }

    private boolean isHelpFlag(String cmd) {
        return cmd.equals("-?") || cmd.equals("-h") || cmd.equals("--help");
    }

    /**
     * Main entry point for coordinating test scenario execution.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        Main main = new Main(args);
        main.run();
    }

    // Runs the scenario processing
    private void run() {
        try {
            // Load scenario
            Scenario scenario = Scenario.loadScenario(new FileInputStream(scenarioFile));

            if (command == Command.VALIDATE) {
                return;
            }

            // Elaborate scenario
            compiler = new Compiler(scenario);
            compiler.compile();

            // Setup the process flow coordinator
            coordinator = new Coordinator(scenario, compiler.processFlow(),
                                          compiler.logDir());
            coordinator.setHaltOnError(haltOnError);
            coordinator.addListener(delegate);

            // Execute process flow
            processCommand();

        } catch (FileNotFoundException e) {
            print("Unable to find scenario file %s", scenarioFile);
        }
    }

     // Processes the appropriate command
    private void processCommand() {
        switch (command) {
            case RUN:
                processRun();
                break;
            case LIST:
                processList();
                break;
            case LIST_FAILED:
                processListFailed();
                break;
            case RUN_RANGE:
                processRunRange();
                break;
            default:
                print("Unsupported command %s", command);
                printHelp();
                break;
        }
    }

    private void printHelp() {
        print("usage: stc [scenario [run*|list|listFailed]");
        print("\n" +
              "Commands:\n" +
              " - run              runs the specified scenario\n" +
              " - list             lists result from the prior run\n" +
              " - listFailed       lists results of failed steps from the prior run\n" +
              " - validate         checks that the XML for the scenario is valid\n" +
              "\n" +
              "Environment Variables:\n" +
              "  - stcHaltOnError  true|false*     stop when a step fails\n" +
              "  - stcDumpLogs     true|false*     dump logs for failed steps to console\n" +
              "  - stcColor        dark*|light     use colors for dark or light terminals\n" +
              "  - stcTitle                        terminal title prefix\n");
        System.exit(0);
    }

    // Processes the scenario 'run' command.
    private void processRun() {
        coordinator.reset();
        runCoordinator();
    }

    // Processes the scenario 'run' command for range of steps.
    private void processRunRange() {
        coordinator.reset(list(runFromPatterns), list(runToPatterns));
        runCoordinator();
    }

    // Processes the scenario 'list' command.
    private void processList() {
        coordinator.getRecords()
                .forEach(event -> logStatus(event.time(), event.name(), event.status(), event.command()));
        printSummary(0, false);
        System.exit(0);
    }

    // Processes the scenario 'listFailed' command.
    private void processListFailed() {
        coordinator.getRecords().stream()
                .filter(event -> event.status() == FAILED)
                .forEach(event -> logStatus(event.time(), event.name(), event.status(), event.command()));
        System.exit(0);
    }

    // Runs the coordinator and waits for it to finish.
    private void runCoordinator() {
        try {
            printTitle();
            Runtime.getRuntime().addShutdownHook(new ShutdownHook());
            coordinator.start();
            int exitCode = coordinator.waitFor();
            pause(100); // allow stdout to flush
            printSummary(exitCode, false);
            System.exit(exitCode);
        } catch (InterruptedException e) {
            print("Unable to execute scenario %s", scenarioFile);
        }
    }

    // Sets the terminal title to indicate progress.
    private synchronized void printTitle() {
        if (useColor) {
            Set<Step> steps = coordinator.getSteps();
            long success = steps.stream().filter(s -> coordinator.getStatus(s) == SUCCEEDED).count();
            long failed = steps.stream().filter(s -> coordinator.getStatus(s) == FAILED).count();
            long skipped = steps.stream().filter(s -> coordinator.getStatus(s) == SKIPPED).count();
            System.out.print(String.format(TITLE, stcTitle, compiler.scenario().name(),
                                           steps.size(), success, failed, skipped));
        }
    }

    // Prints the summary line at the end of execution.
    private synchronized void printSummary(int exitCode, boolean isAborted) {
        if (!isReported) {
            isReported = true;
            Set<Step> steps = coordinator.getSteps();
            String duration = formatDuration((int) (coordinator.duration() / 1_000));
            int count = steps.size();
            if (exitCode == 0) {
                print(SUCCESS_SUMMARY, duration, color(SUCCEEDED), count, color(null));
            } else {
                long success = steps.stream().filter(s -> coordinator.getStatus(s) == SUCCEEDED).count();
                long failed = steps.stream().filter(s -> coordinator.getStatus(s) == FAILED).count();
                long skipped = steps.stream().filter(s -> coordinator.getStatus(s) == SKIPPED).count();
                print(isAborted ? ABORTED_SUMMARY : FAILURE_SUMMARY, duration,
                      color(FAILED), color(SUCCEEDED), success,
                      color(FAILED), failed, color(SKIPPED), skipped, color(null));
            }
        }
    }

    /**
     * Internal delegate to monitor the process execution.
     */
    private class Listener implements StepProcessListener {
        @Override
        public void onStart(Step step, String command) {
            logStatus(currentTimeMillis(), step.name(), IN_PROGRESS, command);
        }

        @Override
        public void onCompletion(Step step, Status status) {
            printTitle();
            logStatus(currentTimeMillis(), step.name(), status, null);
            if (dumpLogs && !(step instanceof Group) && status == FAILED) {
                dumpLogs(step);
            }
        }

        @Override
        public void onOutput(Step step, String line) {
        }
    }

    // Logs the step status.
    private static void logStatus(long time, String name, Status status, String cmd) {
        if (cmd != null) {
            print("%s  %s%s %s%s -- %s", time(time), color(status), name, action(status), color(null), cmd);
        } else {
            print("%s  %s%s %s%s", time(time), color(status), name, action(status), color(null));
        }
    }

    // Dumps the step logs to standard output.
    private void dumpLogs(Step step) {
        File logFile = new File(compiler.logDir(), step.name() + ".log");
        try {
            print(">>>>>");
            Files.copy(logFile, System.out);
            print("<<<<<");
        } catch (IOException e) {
            print("Unable to dump log file %s", logFile.getName());
        }
    }

    // Produces a description of event using the specified step status.
    private static String action(Status status) {
        return status == IN_PROGRESS ? "started" :
                (status == SUCCEEDED ? "completed" :
                        (status == FAILED ? "failed" :
                                (status == SKIPPED ? "skipped" : "waiting")));
    }

    // Produces an ANSI escape code for color using the specified step status.
    private static String color(Status status) {
        if (!useColor) {
            return "";
        }
        return status == null ? NONE :
                (status == IN_PROGRESS ? BLUE :
                        (status == SUCCEEDED ? (darkColor ? GREEN : LIGHT_GREEN) :
                                (status == FAILED ? RED : GRAY)));
    }

    // Produces a list from the specified comma-separated string.
    private static List<String> list(String patterns) {
        return ImmutableList.copyOf(patterns.split(","));
    }

    // Produces a formatted time stamp.
    private static String time(long time) {
        return new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(new Date(time));
    }

    // Pauses for the specified number of millis.
    private static void pause(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            print("Interrupted!");
        }
    }

    // Formats time duration
    private static String formatDuration(int totalSeconds) {
        int seconds = totalSeconds % 60;
        int totalMinutes = totalSeconds / 60;
        int minutes = totalMinutes % 60;
        int hours = totalMinutes / 60;
        return hours > 0 ?
                String.format("%d:%02d:%02d", hours, minutes, seconds) :
                String.format("%d:%02d", minutes, seconds);
    }

    // Shutdown hook to report status even when aborted.
    private class ShutdownHook extends Thread {
        @Override
        public void run() {
            printSummary(1, true);
        }
    }

    // Logger to quiet Jetty down
    private static class NullLogger implements Logger {
        @Override
        public String getName() {
            return "quiet";
        }

        @Override
        public void warn(String msg, Object... args) {
        }

        @Override
        public void warn(Throwable thrown) {
        }

        @Override
        public void warn(String msg, Throwable thrown) {
        }

        @Override
        public void info(String msg, Object... args) {
        }

        @Override
        public void info(Throwable thrown) {
        }

        @Override
        public void info(String msg, Throwable thrown) {
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void setDebugEnabled(boolean enabled) {
        }

        @Override
        public void debug(String msg, Object... args) {
        }

        @Override
        public void debug(Throwable thrown) {
        }

        @Override
        public void debug(String msg, Throwable thrown) {
        }

        @Override
        public void debug(String msg, long huh) {
        }

        @Override
        public Logger getLogger(String name) {
            return this;
        }

        @Override
        public void ignore(Throwable ignored) {
        }
    }
}
