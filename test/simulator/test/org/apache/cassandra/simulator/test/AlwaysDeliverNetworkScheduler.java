/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.simulator.test;

import java.util.concurrent.TimeUnit;

import org.apache.cassandra.simulator.FutureActionScheduler;
import org.apache.cassandra.simulator.RandomSource;
import org.apache.cassandra.simulator.systems.SimulatedTime;
import org.apache.cassandra.simulator.utils.LongRange;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Action scheduler that simulates ideal networking conditions. Useful to rule out
 * timeouts in some of the verbs to test only a specific subset of Cassandra.
 */
public class AlwaysDeliverNetworkScheduler implements FutureActionScheduler
{
    private final SimulatedTime time;
    private final RandomSource randomSource;
    private final LongRange schedulerDelay = new LongRange(0, 50, MICROSECONDS, NANOSECONDS);

    private final long delayNanos;

    AlwaysDeliverNetworkScheduler(SimulatedTime time, RandomSource randomSource)
    {
        this(time, randomSource, TimeUnit.MILLISECONDS.toNanos(10));
    }
    AlwaysDeliverNetworkScheduler(SimulatedTime time, RandomSource randomSource, long dealayNanos)
    {
        this.time = time;
        this.randomSource = randomSource;
        this.delayNanos = dealayNanos;
    }
    public Deliver shouldDeliver(int from, int to)
    {
        return Deliver.DELIVER;
    }

    public long messageDeadlineNanos(int from, int to)
    {
        return time.nanoTime() + delayNanos;
    }

    public long messageTimeoutNanos(long expiresAfterNanos, long expirationIntervalNanos)
    {
        return expiresAfterNanos + 1;
    }

    public long messageFailureNanos(int from, int to)
    {
        throw new IllegalStateException();
    }

    public long schedulerDelayNanos()
    {
        return 1;
    }
}
