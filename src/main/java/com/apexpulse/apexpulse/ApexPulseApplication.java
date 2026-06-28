package com.apexpulse.apexpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.awt.Desktop;
import java.net.URI;

@SpringBootApplication
public class ApexPulseApplication {

    public static void main(String[] args) {
        // 1. Configure Java's Abstract Window Toolkit to cooperate with Spring's headless engine default
        System.setProperty("java.awt.headless", "false");

        // 2. Start the underlying core system server pipeline
        ConfigurableApplicationContext context = SpringApplication.run(ApexPulseApplication.class, args);

        // 3. Automatically trigger the native desktop window manager to open our engine view
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                // Point directly to your local active web context instance
                URI uri = new URI("http://localhost:8080");

                System.out.println("🚀 Triage OS Engine Online. Initializing Desktop Window Manager...");
                desktop.browse(uri);
            } else {
                // Runtime Fallback mechanism if operating system prevents native desktop hooks
                Runtime runtime = Runtime.getRuntime();
                String os = System.getProperty("os.name").toLowerCase();

                if (os.contains("mac")) {
                    runtime.exec("open http://localhost:8080");
                } else if (os.contains("win")) {
                    runtime.exec("rundll32 url.dll,FileProtocolHandler http://localhost:8080");
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Desktop UI shell failed to auto-initialize: " + e.getMessage());
        }
    }
}