package com.appmetr.s2s;

import com.appmetr.s2s.events.Action;
import com.appmetr.s2s.persister.BatchPersister;
import com.appmetr.s2s.persister.FileBatchPersister;
import com.appmetr.s2s.persister.MemoryBatchPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class AppMetr {
    protected static final Logger logger = LoggerFactory.getLogger(AppMetr.class);

    protected final ReentrantLock flushLock = new ReentrantLock();
    protected final ReentrantLock uploadLock = new ReentrantLock();

    private final String token;
    private final String url;
    private boolean stopped = false;

    private final AtomicInteger eventsSize = new AtomicInteger(0);
    private final ArrayList<Action> actionList = new ArrayList<Action>();

    protected AppMetrTimer eventFlushTimer;
    protected AppMetrTimer httpUploadTimer;

    private static final int MILLIS_PER_MINUTE = 1000 * 60;
    private static final long FLUSH_PERIOD = MILLIS_PER_MINUTE / 2;
    private static final long UPLOAD_PERIOD = MILLIS_PER_MINUTE / 2;

    private static final int MAX_EVENTS_SIZE = FileBatchPersister.REBATCH_THRESHOLD_FILE_SIZE;
    private static final int MAX_EVENTS_COUNT = FileBatchPersister.REBATCH_THRESHOLD_ITEM_COUNT;

    private BatchPersister batchPersister;

    public AppMetr(String token, String url, BatchPersister persister) {
        this.url = url;
        this.token = token;
        this.batchPersister = persister == null ? new MemoryBatchPersister() : persister;

        eventFlushTimer = new AppMetrTimer(FLUSH_PERIOD, new Runnable() {
            @Override public void run() {
                flush();
            }
        }, "FlushTask");
        new Thread(eventFlushTimer).start();

        httpUploadTimer = new AppMetrTimer(UPLOAD_PERIOD, new Runnable() {
            @Override public void run() {
                upload();
            }
        }, "UploadTask");
        new Thread(httpUploadTimer).start();
    }

    public AppMetr(String token, String url) {
        this(token, url, null);
    }

    public void track(Action newAction) {
        if (stopped) {
            throw new RuntimeException("Trying to track after stop!");
        }

        try {
            boolean flushNeeded;
            synchronized (actionList) {
                eventsSize.addAndGet(newAction.calcApproximateSize());
                actionList.add(newAction);

                flushNeeded = isNeedToFlush();
            }
            if (flushNeeded) {
                eventFlushTimer.trigger();
            }
        } catch (Exception error) {
            logger.error("Track failed", error);
        }
    }

    protected void flush() {
        flushLock.lock();
        try {
            logger.debug(String.format("Flushing started for %s actions", actionList.size()));

            ArrayList<Action> copyAction;
            synchronized (actionList) {
                copyAction = new ArrayList<Action>(actionList);
                actionList.clear();
                eventsSize.set(0);
            }

            if (copyAction.size() > 0) {
                batchPersister.persist(copyAction);
                httpUploadTimer.trigger();
            } else {
                logger.info("Nothing to flush");
            }

            logger.info("Flushing completed");
        } finally {
            flushLock.unlock();
        }
    }

    protected boolean isNeedToFlush() {
        return eventsSize.get() >= MAX_EVENTS_SIZE
                || actionList.size() >= MAX_EVENTS_COUNT;
    }

    protected void upload() {
        uploadLock.lock();
        try {
            logger.info("Upload starting");

            Batch batch;
            int uploadedBatchCounter = 0;
            int allBatchCounter = 0;
            long sendBatchesBytes = 0;
            while ((batch = batchPersister.getNext()) != null) {
                allBatchCounter++;

                boolean result;
                try {
                    final long batchReadStart = System.currentTimeMillis();
                    final byte[] batchBytes = SerializationUtils.serializeJsonGzip(batch, false);
                    final long batchReadEnd = System.currentTimeMillis();

                    logger.trace(String.format("Batch %s read time: %d ms", batch.getBatchId(), batchReadEnd - batchReadStart));

                    final long batchUploadStart = System.currentTimeMillis();
                    result = HttpRequestService.sendRequest(url, token, batchBytes);
                    final long batchUploadEnd = System.currentTimeMillis();


                    if (result) {
                        logger.trace(String.format("Batch %s successfully uploaded", batch.getBatchId()));
                        batchPersister.remove();
                        uploadedBatchCounter++;
                        sendBatchesBytes += batchBytes.length;
                    } else {
                        logger.error(String.format("Error while upload batch %s. Took %d ms", batch.getBatchId(), batchUploadEnd - batchUploadStart));
                    }
                    logger.info(String.format("Batch %d %s finished. Took %d ms", batch.getBatchId(), result ? "" : "NOT", batchUploadEnd - batchUploadStart));

                    if (!result) break;

                } catch (IOException e) {
                    logger.error("IOException while sending request", e);
                    break;
                }
            }

            logger.info(String.format("%s from %s batches uploaded. (%d bytes)", uploadedBatchCounter, allBatchCounter, sendBatchesBytes));
        } finally {
            uploadLock.unlock();
        }
    }

    public void stop() {
        stopped = true;

        uploadLock.lock();
        try {
            httpUploadTimer.stop();
        } finally {
            uploadLock.unlock();
        }

        flushLock.lock();
        try {
            eventFlushTimer.stop();
        } finally {
            flushLock.unlock();
        }

        flush();
    }
}
