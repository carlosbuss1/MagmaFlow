module com.carlosbuss.magmaflow {
    // JavaFX modules your app uses
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.swing;

    // Optional but typical for AWT/Swing interop
    requires java.desktop;

    // Export your main package (where Magmaflow.java lives)
    exports com.carlosbuss;

    // If you use reflection (FXML/controllers), also open the package:
    // opens com.carlosbuss to javafx.graphics, javafx.fxml;
}

