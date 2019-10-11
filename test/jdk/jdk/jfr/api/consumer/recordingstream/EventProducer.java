package jdk.jfr.api.consumer.recordingstream;

import jdk.jfr.api.consumer.recordingstream.TestStart.StartEvent;

class EventProducer extends Thread {
    private final Object lock = new Object();
    private boolean killed = false;
    public void run() {
        while (true) {
            StartEvent s = new StartEvent();
            s.commit();
            synchronized (lock) {
                try {
                    lock.wait(10);
                    if (killed) {
                        return; // end thread
                    }
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }
    public void kill()  {
        synchronized (lock) {
            this.killed = true;
            lock.notifyAll();
            try {
                join();
            } catch (InterruptedException e) {
            }
        }
    }
}