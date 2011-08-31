/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.util;

import hudson.remoting.Future;
import hudson.util.ThreadPoolExecutorWithCallback.Callback;
import hudson.util.ThreadPoolExecutorWithCallback.FutureWithCallback;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Various {@link Future} implementations.
 * 
 * @author Kohsuke Kawaguchi
 */
public class Futures {
    public static <T> Future<T> withTimeout(final java.util.concurrent.Future<T> base, final long timeout) {
        if (base instanceof FutureWithCallback)
            return new FutureWithTimeoutWithCallback<T>(timeout, (FutureWithCallback<T>)base);
        else
            return new FutureWithTimeout<T>(timeout, base);
    }

    private static class FutureWithTimeoutWithCallback<T> extends FutureWithTimeout<T> implements FutureWithCallback<T> {
        private FutureWithTimeoutWithCallback(long timeout, FutureWithCallback<T> base) {
            super(timeout, base);
        }

        public void addCallback(Callback<T> c) {
            ((FutureWithCallback<T>)base).addCallback(c);
        }
    }

    private static class FutureWithTimeout<T> implements Future<T> {
        final long deadline;
        private final long timeout;
        final java.util.concurrent.Future<T> base;

        public FutureWithTimeout(long timeout, java.util.concurrent.Future<T> base) {
            this.timeout = timeout;
            this.base = base;
            deadline = System.currentTimeMillis() + timeout;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            return base.cancel(mayInterruptIfRunning);
        }

        public boolean isCancelled() {
            return base.isCancelled();
        }

        public boolean isDone() {
            if (remainingTime()<0)
                cancel(true);
            return base.isDone();
        }

        public T get() throws ExecutionException, InterruptedException {
            while (true) {
                long rt = remainingTime();
                if (rt>0) {
                    try {
                        return base.get(rt, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        continue;   // dead line reached. cancel and then wait for the outcome
                    }
                } else {
                    cancel(true);
                    return base.get();
                }
            }
        }

        private long remainingTime() {
            return deadline-System.currentTimeMillis();
        }

        public T get(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException, InterruptedException {
            timeout = Math.min(timeout, unit.convert(remainingTime(), TimeUnit.MILLISECONDS));
            try {
                return base.get(timeout, unit);
            } catch (TimeoutException e) {
                cancel(true);
                throw e;
            }
        }
    }

    /**
     * Casts Future&lt;?> to Future&lt;Void>
     */
    public static java.util.concurrent.Future<Void> adaptToVoid(java.util.concurrent.Future<?> f) {
        // if someone actually assigns the return value of get() to Void, this will fail.
        // but who does that? If that's the concern, we need to wrap.
        return (Future)f;
    }


    /**
     * Creates a {@link Future} instance that already has its value pre-computed.
     */
    public static <T> Future<T> precomputed(final T value) {
        return new Future<T>() {
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            public boolean isCancelled() {
                return false;
            }

            public boolean isDone() {
                return true;
            }

            public T get() {
                return value;
            }

            public T get(long timeout, TimeUnit unit) {
                return value;
            }
        };
    }
}
