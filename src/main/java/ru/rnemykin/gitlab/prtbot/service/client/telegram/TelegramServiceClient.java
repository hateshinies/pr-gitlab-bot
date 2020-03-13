package ru.rnemykin.gitlab.prtbot.service.client.telegram;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.models.MergeRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.ApiContext;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.rnemykin.gitlab.prtbot.config.properties.TelegramProperties;
import ru.rnemykin.gitlab.prtbot.model.PullRequestUpdateMessage;

import javax.annotation.PostConstruct;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.time.ZoneId.systemDefault;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramServiceClient {
    private static final String UP_VOTERS_MESSAGE_TEMPLATE = "\n\n\uD83D\uDC4D - {0} by {1}";
    private static final String UNRESOLVED_THREADS_MESSAGE_TEMPLATE = "\n\n*Unresolved threads*\n{0}";
    private static final String PR_MESSAGE_TEMPLATE = "[Pull request !{0}]({1})\n`{2}` -> `{3}`\n{4}\nOpened {5} by {6}";
    private static final DateTimeFormatter RU_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM HH:mm");
    private final TelegramProperties properties;
    private TelegramLongPollingBot telegramApi;


    @SneakyThrows
    @PostConstruct
    private void initBotApi() {
        ApiContextInitializer.init();

        DefaultBotOptions options = ApiContext.getInstance(DefaultBotOptions.class);
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(properties.getProxyUser(), properties.getProxyPassword().toCharArray());
            }
        });
        options.setProxyHost(properties.getProxyHost());
        options.setProxyPort(properties.getProxyPort());
        options.setProxyType(DefaultBotOptions.ProxyType.SOCKS5);

        telegramApi = new TelegramLongPollingBot(options) {
            @Override
            public void onUpdateReceived(Update update) {

            }

            @Override
            public String getBotUsername() {
                return properties.getBotName();
            }

            @Override
            public String getBotToken() {
                return properties.getToken();
            }
        };

        new TelegramBotsApi().registerBot(telegramApi);
    }

    public Optional<Message> newPrNotification(MergeRequest pr) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(properties.getChatId());
            message.setText(makePrMessage(pr));
            message.disableWebPagePreview();
            message.setParseMode(ParseMode.MARKDOWN);
            return Optional.of(telegramApi.execute(message));
        } catch (Exception ex) {
            log.error("can't sent message for pr {}", pr, ex);
            return Optional.empty();
        }
    }

    private String makePrMessage(MergeRequest pr) {
        return MessageFormat.format(
                PR_MESSAGE_TEMPLATE,
                pr.getIid(),
                pr.getWebUrl(),
                pr.getSourceBranch(),
                pr.getTargetBranch(),
                pr.getTitle(),
                RU_DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(pr.getCreatedAt().toInstant(), systemDefault())),
                pr.getAuthor().getName()
        );
    }

    public Boolean deleteMessage(int messageId, long chatId) {
        DeleteMessage method = new DeleteMessage(chatId, messageId);
        Boolean result;
        try {
            result = telegramApi.execute(method);
        } catch (TelegramApiException ex) {
            log.error("can't delete message[id={}, chatId={}]", messageId, chatId, ex);
            result = false;
        }
        return Boolean.TRUE.equals(result);
    }

    public void updatePrMessage(PullRequestUpdateMessage data) {
        EditMessageText editMsg = new EditMessageText();
        editMsg.setText(makeUpdatePrMessage(data));
        editMsg.setChatId(data.getTelegramChatId());
        editMsg.setMessageId(data.getTelegramMessageId());
        editMsg.setParseMode(ParseMode.MARKDOWN);
        editMsg.disableWebPagePreview();
        try {
            telegramApi.execute(editMsg);
        } catch (TelegramApiException ex) {
            log.error("can't edit message[id={}, chatId={}]", data.getTelegramMessageId(), data.getTelegramChatId(), ex);
        }
    }

    private String makeUpdatePrMessage(PullRequestUpdateMessage data) {
        String text = makePrMessage(data.getRequest());
        if (!CollectionUtils.isEmpty(data.getUnresolvedThreadsMap())) {
            text += MessageFormat.format(
                    UNRESOLVED_THREADS_MESSAGE_TEMPLATE,
                    data.getUnresolvedThreadsMap().entrySet().stream()
                            .map(e -> "\t\t" + e.getKey() + " - " + e.getValue())
                            .collect(Collectors.joining("\n"))
            );
        }

        List<String> upVoterNames = data.getUpVoterNames();
        if(!CollectionUtils.isEmpty(upVoterNames)) {
            text += MessageFormat.format(UP_VOTERS_MESSAGE_TEMPLATE, upVoterNames.size(), String.join(" ,", upVoterNames));
        }
        return text;
    }
}
