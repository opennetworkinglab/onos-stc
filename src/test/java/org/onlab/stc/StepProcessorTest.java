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

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.onlab.stc.Coordinator.Status.SUCCEEDED;

/**
 * Test of the step processor.
 */
public class StepProcessorTest {

    @ClassRule
    public static TemporaryFolder testFolder = new TemporaryFolder();

    private static File dir;

    @BeforeClass
    public static void setUpClass() {
        dir = testFolder.getRoot();
        StepProcessor.launcher = "echo";
        checkState(dir.exists() || dir.mkdirs(), "Unable to create directory");
    }

    @Test
    public void basics() {
        Listener delegate = new Listener();
        Step step = new Step("foo", "ls " + dir.getAbsolutePath(), null, null, null, 0);
        StepProcessor processor = new StepProcessor(step, dir, delegate, step.command());
        processor.run();
        assertTrue("should be started", delegate.started);
        assertTrue("should be stopped", delegate.stopped);
        assertEquals("incorrect status", SUCCEEDED, delegate.status);
        assertTrue("should have output", delegate.output);
    }

    @Test
    public void doubleQuotes() {
        Listener delegate = new Listener();
        // Note the double space in "hello  world", we want that to be preserved.
        Step step = new Step("foo", "echo \"hello  world\"", null, null, null, 0);
        StepProcessor processor = new StepProcessor(step, dir, delegate, step.command());
        processor.run();
        assertEquals("incorrect output", "hello  world", delegate.outputString);
    }

    @Test
    public void singleQuotes() {
        Listener delegate = new Listener();
        Step step = new Step("foo", "echo 'hello  world'", null, null, null, 0);
        StepProcessor processor = new StepProcessor(step, dir, delegate, step.command());
        processor.run();
        assertEquals("incorrect output", "hello  world", delegate.outputString);
    }

    @Test
    public void escapedDoubleQuotes() {
        Listener delegate = new Listener();
        Step step = new Step("foo", "echo \"\\\"hello  world\\\"\"", null, null, null, 0);
        StepProcessor processor = new StepProcessor(step, dir, delegate, step.command());
        processor.run();
        assertEquals("incorrect output", "\"hello  world\"", delegate.outputString);
    }

    @Test
    public void noQuotes() {
        Listener delegate = new Listener();
        Step step = new Step("foo", "echo hello  world", null, null, null, 0);
        StepProcessor processor = new StepProcessor(step, dir, delegate, step.command());
        processor.run();
        // Double space becomes one.
        assertEquals("incorrect output", "hello world", delegate.outputString);
    }

    private class Listener implements StepProcessListener {

        private Coordinator.Status status;
        private boolean started, stopped, output;
        private String outputString;

        @Override
        public void onStart(Step step, String command) {
            started = true;
        }

        @Override
        public void onCompletion(Step step, Coordinator.Status status) {
            stopped = true;
            this.status = status;
        }

        @Override
        public void onOutput(Step step, String line) {
            outputString = line;
            output = true;
        }
    }

}