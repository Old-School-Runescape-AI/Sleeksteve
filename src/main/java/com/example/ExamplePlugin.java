package com.example;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.*;
import com.google.gson.Gson;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@PluginDescriptor(
		name = "Sleeksteve",
		description = "Responds to 'Sleeksteve:' messages using AI",
		enabledByDefault = false
)
public class ExamplePlugin extends Plugin {
	@Inject
	private Client client;

	@Inject
	private ExampleConfig config;

	private static final String TRIGGER = "sleeksteve:";
	private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final Gson GSON = new Gson();
	private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

	private boolean sendMessageActive = false;
	private int currentIndex = 0;
	private String currentMessage = "";
	private long lastMessageTime = 0;
	private static final long MESSAGE_COOLDOWN = 10000; // 10 seconds in milliseconds

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage) {
		if(chatMessage == null || chatMessage.getMessage().isEmpty()) return;
		String message = chatMessage.getMessage();

		if (!message.toLowerCase().startsWith(TRIGGER)) return;

		long currentTime = System.currentTimeMillis();
		if (currentTime - lastMessageTime < MESSAGE_COOLDOWN) {
			return;
		}

		String query = message.substring(TRIGGER.length()).trim();
		if (query.isEmpty()) return;

		lastMessageTime = currentTime;
		sendToAI(query, chatMessage.getName());
	}

	@Subscribe
	public void onClientTick(ClientTick clientTick) {
		if (!sendMessageActive) return;
		sendLetter();
	}

	private void sendLetter() {
		if (currentIndex == currentMessage.length()) {
			sendKey(KeyEvent.VK_ENTER);
			sendMessageActive = false;
			currentIndex = 0;
			return;
		}
		sendKey(currentMessage.charAt(currentIndex));
		currentIndex++;
	}

	private void sendKey(int key) {
		KeyEvent pressed = new KeyEvent(client.getCanvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, key, (char) key);
		KeyEvent typed = new KeyEvent(client.getCanvas(), KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, 0, (char) key);
		KeyEvent released = new KeyEvent(client.getCanvas(), KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, key, (char) key);

		client.getCanvas().dispatchEvent(pressed);
		client.getCanvas().dispatchEvent(typed);
		client.getCanvas().dispatchEvent(released);
	}

	private void sendToAI(String query, String sender) {
		Map<String, Object> messageObj = Map.of(
				"role", "user",
				"content", query
		);

		Map<String, Object> systemMsg = Map.of(
				"role", "system",
				"content", "YOUR WHOLE RESPONSE MUST BE UNDER 70 CHARACTERS. You are sleeksteve, a helpful AI assistant in Old School Runescape. Keep responses concise and game-appropriate. Start all responses with '" + sender + ":'. Total response must be under 70 characters including the name prefix."
		);

		Map<String, Object> requestBody = Map.of(
				"model", "gpt-4",
				"messages", List.of(systemMsg, messageObj),
				"temperature", 1.5,
				"max_tokens", 50
		);

		RequestBody body = RequestBody.create(
				JSON,
				GSON.toJson(requestBody)
		);

		Request request = new Request.Builder()
				.url(OPENAI_API_URL)
				.addHeader("Authorization", "Bearer " + "YOUR API KEY")
				.addHeader("Content-Type", "application/json")
				.post(body)
				.build();

		HTTP_CLIENT.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				log.error("Failed to get AI response", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				try (ResponseBody responseBody = response.body()) {
					if (!response.isSuccessful() || responseBody == null) {
						log.error("Unsuccessful AI response: " + response);
						return;
					}

					Map<String, Object> jsonResponse = GSON.fromJson(
							responseBody.string(),
							Map.class
					);

					List<Map<String, Object>> choices = (List<Map<String, Object>>) jsonResponse.get("choices");
					Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
					currentMessage = (String) message.get("content");
					sendMessageActive = true;
				}
			}
		});
	}

	@Provides
	ExampleConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(ExampleConfig.class);
	}
}