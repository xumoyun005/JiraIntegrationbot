package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import com.google.gson.Gson;
import okhttp3.*;

import java.io.IOException;
import java.util.*;

public class JiraTelegramBot extends TelegramLongPollingBot {
    private static final String TELEGRAM_BOT_TOKEN = "7359327916:AAFtj-RWkiTkFYueCF0eBMhhjmL97vPVtDU";
    private static final String JIRA_BASE_URL = "https://cineramauzb.atlassian.net";
    private static final String JIRA_API_TOKEN = "ATATT3xFfGF0tl5NLGh2m4lGj0dHVogGIc7Jw5XPk-tMRih2dSNL45mZdG5t89gp4TINeGbF274dX_AstX01XUCRW6weN4O17p3LlzMdaKQVsm9fw3tdI2Cyvlqz4dzRK-2jBlfXT5kp-yxRAvW7ktQQ9DZMqrwcNUWb57UfZrEeIaRDswBN__Q=0BA2AFAD";
    private static final String JIRA_EMAIL = "xumoyiddinxolmuminov858@gmail.com";
    private static final String JIRA_PROJECT_KEY = "CR";
    private String currentStep = "START";
    private String taskName;
    private String taskDescription;
    private String taskImageUrl;
    private String taskDeadlineDate;
    private String taskDeadlineTime;
    private final Map<String, String> developerNameToCustomFieldId = new HashMap<>();
    private final Map<String, String> developerNameToAccountId = new HashMap<>();

    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new JiraTelegramBot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return "cineramajirabot";
    }

    @Override
    public String getBotToken() {
        return TELEGRAM_BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();

            if (message.hasText()) {
                String messageText = message.getText();
                switch (currentStep) {
                    case "START":
                        if (messageText.equals("/newtask")) {
                            sendMessage(chatId, "Please enter the task name:");
                            currentStep = "TASK_NAME";
                        }
                        break;
                    case "TASK_NAME":
                        taskName = messageText;
                        sendMessage(chatId, "Please enter the task description:");
                        currentStep = "TASK_DESCRIPTION";
                        break;
                    case "TASK_DESCRIPTION":
                        taskDescription = messageText;
                        sendMessage(chatId, "Please send the task image (JPG or PNG format):");
                        currentStep = "TASK_IMAGE";
                        break;
                    case "TASK_IMAGE":
                        sendMessage(chatId, "Please enter the task deadline date (e.g., 2024-08-15):");
                        currentStep = "TASK_DEADLINE_DATE";
                        break;
                    case "TASK_DEADLINE_DATE":
                        taskDeadlineDate = messageText;
                        sendMessage(chatId, "Please enter the task deadline time (e.g., 15:30):");
                        currentStep = "TASK_DEADLINE_TIME";
                        break;
                    case "TASK_DEADLINE_TIME":
                        taskDeadlineTime = messageText;
                        sendDeveloperSelectionMessage(chatId);
                        currentStep = "SELECT_DEVELOPER";
                        break;
                    default:
                        break;
                }
            } else if (message.hasPhoto() && currentStep.equals("TASK_IMAGE")) {
                List<PhotoSize> photos = message.getPhoto();
                PhotoSize largestPhoto = photos.stream()
                        .max(Comparator.comparing(PhotoSize::getFileSize))
                        .orElse(null);
                if (largestPhoto != null) {
                    try {
                        String filePath = execute(new GetFile(largestPhoto.getFileId())).getFilePath();
                        taskImageUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath;
                        sendMessage(chatId, "Please enter the task deadline date (e.g., 2024-08-15):");
                        currentStep = "TASK_DEADLINE_DATE";
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (currentStep.equals("SELECT_DEVELOPER") && callbackData.startsWith("assign:")) {
                String assignee = callbackData.split(":")[1];
                String customFieldId = "customfield_10032";
                createJiraTask(taskName, taskDescription, taskImageUrl, taskDeadlineDate, taskDeadlineTime, assignee, customFieldId);
                sendMessage(chatId, "Task successfully created and assigned to " + assignee);
                currentStep = "START";
            }
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendDeveloperSelectionMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Please select a developer to assign the task:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        // Get developers from Jira
        List<String> developers = getDevelopers();
        for (String developer : developers) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(developer);
            button.setCallbackData("assign:" + developer);
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            buttons.add(row);
        }

        markup.setKeyboard(buttons);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private List<String> getDevelopers() {
        List<String> developers = new ArrayList<>();
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(JIRA_BASE_URL + "/rest/api/3/users/search?query=&accountType=atlassian&active=true")
                .header("Authorization", Credentials.basic(JIRA_EMAIL, JIRA_API_TOKEN))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            Gson gson = new Gson();
            Developer[] developerArray = gson.fromJson(Objects.requireNonNull(response.body()).charStream(), Developer[].class);

            for (Developer developer : developerArray) {
                if ("atlassian".equals(developer.accountType) && developer.active) {
                    developers.add(developer.displayName);
                    developerNameToAccountId.put(developer.displayName, developer.accountId);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return developers;
    }

    private void createJiraTask(String summary, String description, String imageUrl, String deadlineDate, String deadlineTime, String assignee, String customFieldId) {
        OkHttpClient client = new OkHttpClient();

        // Convert time to ISO 8601 format
        String isoDateTime = deadlineDate + "T" + deadlineTime + "+05:00"; // Adjust the time zone offset as needed

        String adfDescription = "{"
                + "\"type\": \"doc\","
                + "\"version\": 1,"
                + "\"content\": ["
                + "    {\"type\": \"paragraph\",\"content\": [{\"type\": \"text\",\"text\": \"" + description + "\"}]},"
                + "    {\"type\": \"paragraph\",\"content\": [{\"type\": \"text\",\"text\": \"Image: " + imageUrl + "\"}]},"
                + "    {\"type\": \"paragraph\",\"content\": [{\"type\": \"text\",\"text\": \"Deadline time: " + deadlineTime + "\"}]}"
                + "]"
                + "}";

        String json = "{"
                + "\"fields\": {"
                + "\"project\": {\"key\": \"" + JIRA_PROJECT_KEY + "\"},"
                + "\"summary\": \"" + summary + "\","
                + "\"description\": " + adfDescription + ","
                + "\"assignee\": {\"accountId\": \"" + developerNameToAccountId.get(assignee) + "\"},"
                + "\"issuetype\": {\"name\": \"Task\"},"
                + "\"duedate\": \"" + deadlineDate + "\","
                + "\"" + customFieldId + "\": \"" + isoDateTime + "\""
                + "}}";

        System.out.println("JSON Data: " + json); // Log the JSON data

        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(JIRA_BASE_URL + "/rest/api/3/issue")
                .header("Authorization", Credentials.basic(JIRA_EMAIL, JIRA_API_TOKEN))
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.out.println("Response: " + response.body().string()); // Log the response body for error details
                throw new IOException("Unexpected code " + response);
            }
            System.out.println("Task created: " + response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class Developer {
        String displayName;
        String accountId;
        String accountType;
        boolean active;
    }
}
