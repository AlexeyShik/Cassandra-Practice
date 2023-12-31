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
package org.apache.cassandra.concurrent;

public interface ResizableThreadPoolMXBean extends ResizableThreadPool
{
    /**
     * Returns core pool size of thread pool.
     * @deprecated use getCorePoolSize instead. See CASSANDRA-15277
     */
    @Deprecated(since = "4.0")
    public int getCoreThreads();

    /**
     * Allows user to resize core pool size of the thread pool.
     * @deprecated  use setCorePoolSize instead. See CASSANDRA-15277
     */
    @Deprecated(since = "4.0")
    public void setCoreThreads(int number);

    /**
     * Returns maximum pool size of thread pool.
     * @deprecated use getMaximumThreads instead. See CASSANDRA-15277
     */
    @Deprecated(since = "4.0")
    public int getMaximumThreads();

    /**
     * Allows user to resize maximum size of the thread pool.
     * @deprecated use setMaximumThreads instead. See CASSANDRA-15277
     */
    @Deprecated(since = "4.0")
    public void setMaximumThreads(int number);
}
