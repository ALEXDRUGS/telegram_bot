package app.telegram_bot.listener;

import app.telegram_bot.entity.NotificationTask;
import app.telegram_bot.service.NotificationTaskService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TelegramBotUpdatesListener implements UpdatesListener {

    Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);
    private final Pattern pattern =
            Pattern.compile("(\\d{1,2}\\.\\d{1,2}\\.\\d{4} \\d{1,2}:\\d{2})\\s+([А-я\\d\\s,.:;!?]+)");

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final TelegramBot telegramBot;

    private final NotificationTaskService notificationTaskService;

    public TelegramBotUpdatesListener(TelegramBot telegramBot, NotificationTaskService notificationTaskService) {
        this.telegramBot = telegramBot;
        this.notificationTaskService = notificationTaskService;
    }

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    @Override
    public int process(List<Update> updates) {
        try {
            updates.stream()
                    .filter(update -> update.message() != null)
                    .forEach(update -> {
                        logger.info("Handles update: {}", update);
                        Message message = update.message();
                        Long chatId = message.chat().id();
                        String text = message.text();
                        if ("/start".equals(text)) {
                            sendMassage(chatId, """
                                    Бот планировщик задач запущен.
                                    Введите задачу в формате: дд.мм.гггг чч:мм описание задачи
                                    """);
                        } else if (text != null) {
                            Matcher matcher = pattern.matcher(text);
                            if (matcher.find()) {
                                LocalDateTime dateTime = parse(matcher.group(1));
                                if (Objects.isNull(dateTime) || dateTime.isBefore(LocalDateTime.now())) {
                                    sendMassage(chatId, "Некорректный формат даты и/или времени!");
                                } else {
                                    String txt = matcher.group(2);
                                    NotificationTask notificationTask = new NotificationTask();
                                    notificationTask.setChatId(chatId);
                                    notificationTask.setMessage(txt);
                                    notificationTask.setNotificationDateTime(dateTime);
                                    notificationTaskService.save(notificationTask);
                                    sendMassage(chatId, "Задача добавлена!");
                                }
                            } else {
                                sendMassage(chatId, "Некорректный формат сообщения!");
                            }
                        }
                    });
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    @Nullable
    private LocalDateTime parse(String dateTime) {
        try {
            return LocalDateTime.parse(dateTime, formatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void sendMassage(Long chatId, String massage) {
        SendMessage sendMessage = new SendMessage(chatId, massage);
        SendResponse response = telegramBot.execute(sendMessage);
        if (!response.isOk()) {
            logger.error("Error during sending massage: {}", response.description());
        }
    }
}
