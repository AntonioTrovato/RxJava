/*
 * Copyright (c) 2016-present, RxJava Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */
package io.reactivex.rxjava3.listener;

import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class JacocoCoverageRunListener extends RunListener {

    private final JacocoCoverageListener coverageListener = new JacocoCoverageListener();

    @Override
    public void testStarted(Description description) {
        // Any setup before each test starts, if needed
        System.out.println("Starting test: " + description.getDisplayName());
    }

    @Override
    public void testFailure(Failure failure) {
        // Simulate calling `failed` from JacocoCoverageListener
        coverageListener.failed(failure.getException(), failure.getDescription());
    }

    @Override
    public void testFinished(Description description) {
        // Simulate calling `succeeded` from JacocoCoverageListener if the test passed
        System.out.println("Finished test: " + description.getDisplayName());
        coverageListener.succeeded(description);
    }
}
