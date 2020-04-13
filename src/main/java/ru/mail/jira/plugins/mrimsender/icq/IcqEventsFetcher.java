package ru.mail.jira.plugins.mrimsender.icq;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.exceptions.UnirestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mail.jira.plugins.mrimsender.icq.dto.FetchResponseDto;
import ru.mail.jira.plugins.mrimsender.icq.dto.events.CallbackQueryEvent;
import ru.mail.jira.plugins.mrimsender.icq.dto.events.NewMessageEvent;
import ru.mail.jira.plugins.mrimsender.protocol.BotFaultToleranceProvider;
import ru.mail.jira.plugins.mrimsender.protocol.JiraMessageQueueProcessor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class IcqEventsFetcher {
    private static final Logger log = LoggerFactory.getLogger(BotFaultToleranceProvider.class);
    private static final String THREAD_NAME_PREFIX_FORMAT = "icq-events-fetcher-thread-pool-%d";

    private AtomicBoolean isRunning;
    private ScheduledExecutorService fetcherExecutorService;
    private ScheduledFuture<?> currentFetchJobFuture;
    private long lastEventId = 0;
    private final IcqApiClient icqApiClient;
    private final JiraMessageQueueProcessor jiraMessageQueueProcessor;


    public IcqEventsFetcher(IcqApiClient icqApiClient, JiraMessageQueueProcessor jiraMessageQueueProcessor) {
        isRunning = new AtomicBoolean(false);
        this.icqApiClient = icqApiClient;
        this.jiraMessageQueueProcessor = jiraMessageQueueProcessor;
    }

    public void start() {
        log.debug("IcqEventsFetcher starting ...");
        if (isRunning.compareAndSet(false, true)) {
            fetcherExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat(THREAD_NAME_PREFIX_FORMAT).build());
            currentFetchJobFuture = fetcherExecutorService.scheduleWithFixedDelay(() -> {
                try {
                    this.fetchIcqEvents();
                } catch (Exception e) {
                    log.error("An exception occurred inside fetcher executor service job", e);
                }
            }, 0 , 5, TimeUnit.SECONDS);
            log.debug("IcqEventsFetcher started");
        }
    }

    public void fetchIcqEvents() {
        try {
            log.debug(String.format("IcqEventsFetcher fetch icq events started  lastEventId=%d...", lastEventId));
            HttpResponse<FetchResponseDto> httpResponse = icqApiClient.getEvents(lastEventId, 5);
            if (httpResponse.getStatus() == 200) {
                log.debug("IcqEventsFetcher handle icq events started ...");
                httpResponse.getBody()
                            .getEvents()
                            .forEach(event -> {
                                if (event instanceof NewMessageEvent) {
                                    jiraMessageQueueProcessor.handleNewMessageEvent((NewMessageEvent)event);
                                } else if (event instanceof CallbackQueryEvent) {
                                    jiraMessageQueueProcessor.handleCallbackQueryEvent((CallbackQueryEvent)event);
                                }
                            });
            }
            log.debug("IcqEventsFetcher fetchIcqEvents finished.... ");
            FetchResponseDto fetchResponseDto = httpResponse.getBody();
            if (fetchResponseDto.getEvents() != null && fetchResponseDto.getEvents().size() > 0) {
                int eventsNum = fetchResponseDto.getEvents().size();
                this.lastEventId = fetchResponseDto.getEvents().get(eventsNum - 1).getEventId();
            }
        } catch (UnirestException e) {
            log.debug("unirest exception occurred", e);
            // exception occurred during events fetching, for example http connection timeout
        }

    }

    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            currentFetchJobFuture.cancel(true);
            fetcherExecutorService.shutdownNow();
        }
    }

    public AtomicBoolean getIsRunning() {
        return this.isRunning;
    }
}