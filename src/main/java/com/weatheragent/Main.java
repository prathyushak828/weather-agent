package com.weatheragent;

import io.github.cdimascio.dotenv.Dotenv;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        // Load .env file explicitly from project root
        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();

        String apiKey = dotenv.get("ANTHROPIC_API_KEY");

        // Fallback: try reading from system environment variable
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("ANTHROPIC_API_KEY");
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: ANTHROPIC_API_KEY not found.");
            System.err.println("Make sure .env file exists in: " + System.getProperty("user.dir"));
            System.exit(1);
        }

        WeatherAgent agent = new WeatherAgent(apiKey);
        Scanner scanner = new Scanner(System.in);

        System.out.println("Weather Agent ready. Ask about weather anywhere!");
        System.out.println("Type 'exit' to quit.\n");

        while (true) {
            System.out.print("You: ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Goodbye!");
                break;
            }

            if (input.isBlank()) continue;

            System.out.println("Agent: thinking...");
            try {
                String response = agent.chat(input);
                System.out.println("Agent: " + response);
            } catch (Exception e) {
                System.err.println("Error during chat: " + e.getMessage());
                e.printStackTrace();
                break;
            }
            System.out.println();
        }

        scanner.close();
    }
}