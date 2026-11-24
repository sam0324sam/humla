/*
 * Copyright (C) 2016 Andrew Comminos <andrew@comminos.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package se.lublin.humla.audio.inputmode;

import android.util.Log;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An input mode that depends on a toggle, such as push to talk.
 * Created by andrew on 13/02/16.
 */
public class ToggleInputMode implements IInputMode {
    private static final String TAG = ToggleInputMode.class.getName();

    private volatile boolean mInputOn;
    private final Lock mToggleLock;
    private final Condition mToggleCondition;

    public ToggleInputMode() {
        mInputOn = false;
        mToggleLock = new ReentrantLock();
        mToggleCondition = mToggleLock.newCondition();
    }

    public void toggleTalkingOn() {
        setTalkingOn(!mInputOn);
    }

    public boolean isTalkingOn() {
        return mInputOn;
    }

    public void setTalkingOn(boolean talking) {
        mToggleLock.lock();
        try {
            mInputOn = talking;
            mToggleCondition.signalAll();
        } finally {
            mToggleLock.unlock();
        }
    }

    @Override
    public boolean shouldTransmit(short[] pcm, int length) {
        return mInputOn;
    }

    /**
     * Waits up to timeoutMs for PTT activation.
     * @return true if PTT became active before timeout; false if timed out or interrupted.
     */
    public boolean waitForInputWithTimeout(long timeoutMs) {
        mToggleLock.lock();
        try {
            if (mInputOn) {
                return true;
            }
            long nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (!mInputOn && nanos > 0L) {
                nanos = mToggleCondition.awaitNanos(nanos);
            }
            return mInputOn;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Timed PTT wait interrupted, likely due to input thread shutdown.");
            return false;
        } finally {
            mToggleLock.unlock();
        }
    }

    @Override
    public void waitForInput() {
        mToggleLock.lock();
        try {
            if (!mInputOn) {
                Log.v(TAG, "PTT: Suspending audio input.");
                long startTime = System.currentTimeMillis();
                try {
                    while (!mInputOn) {
                        mToggleCondition.await();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "Blocking for PTT interrupted, likely due to input thread shutdown.");
                }
                Log.v(TAG, "PTT: Suspended audio input for " + (System.currentTimeMillis() - startTime) + "ms.");
            }
        } finally {
            mToggleLock.unlock();
        }
    }
}
