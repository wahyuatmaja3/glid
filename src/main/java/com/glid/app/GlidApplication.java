package com.glid.app;

import com.glid.persistence.AppDataStore;
import com.glid.ui.MainDashboard;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class GlidApplication extends Application {

    @Override
    public void start(Stage stage) {
        AppContext context = AppContext.bootstrap();
        MainDashboard dashboard = new MainDashboard(context);

        stage.setTitle("Glid - Offline Attendance System");
        stage.setScene(new Scene(dashboard.build(), 1280, 820));
        stage.setMinWidth(1100);
        stage.setMinHeight(760);
        stage.show();
    }

    @Override
    public void stop() {
        AppDataStore.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
