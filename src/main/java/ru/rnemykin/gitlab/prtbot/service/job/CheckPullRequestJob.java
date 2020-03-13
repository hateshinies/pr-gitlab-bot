package ru.rnemykin.gitlab.prtbot.service.job;

import lombok.RequiredArgsConstructor;
import org.gitlab4j.api.Constants;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.rnemykin.gitlab.prtbot.service.job.strategy.PullRequestProcessStrategyFactory;

@Component
@RequiredArgsConstructor
public class CheckPullRequestJob {
    private final PullRequestProcessStrategyFactory strategyFactory;


    @Scheduled(cron = "${app.job.notifyAboutOpenedPr}")
    public void notifyAboutOpenedPr() {
        strategyFactory.get(Constants.MergeRequestState.OPENED).process();
    }

    @Scheduled(cron = "${app.job.notifyAboutMergedPr}")
    public void notifyAboutMergedPr() {
        strategyFactory.get(Constants.MergeRequestState.MERGED).process();
    }

}
