package com.carlosbuss;

import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.geometry.VPos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.FontWeight;
import javafx.scene.text.FontPosture;
import javafx.application.Application;
import javafx.scene.control.ScrollPane;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;                 // Dialog, ButtonType, Label, ComboBox included
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;                  // VBox, HBox, GridPane, Pane, etc.
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Transform;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;                            // Map, List, ArrayList, LinkedHashMap, Arrays
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.input.MouseButton;
import javafx.scene.Cursor;

public class Magmaflow extends Application {

private static String escapeJson(String s) {
    if (s == null) return "";
    StringBuilder out = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        switch (c) {
            case '\"': out.append("\\\""); break;
            case '\\': out.append("\\\\"); break;
            case '\b': out.append("\\b"); break;
            case '\f': out.append("\\f"); break;
            case '\n': out.append("\\n"); break;
            case '\r': out.append("\\r"); break;
            case '\t': out.append("\\t"); break;
            default:
                if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                else out.append(c);
        }
    }
    return out.toString();
}

private static String fmt(double d) {
    if (Double.isNaN(d) || Double.isInfinite(d)) return "0.0";
    return String.format(java.util.Locale.US, "%.12g", d);
}

// --- Project + persistence ---
private File currentProjectFile = null;
private File lastCSVFile = null;

// --- Controls we need to update after loading a project ---
private TextField logFCThresholdField;
private TextField pValueThresholdField;
private TextField topNUpField;
private TextField topNDownField;

private CheckBox useAdjPCheck;
private CheckBox annotateCheck;
private CheckBox showTargetsCheck;
private CheckBox gridCheck;
private CheckBox zeroLineCheck;
private CheckBox hoverInfoCheck;
private CheckBox transparentDotsCheck;

private ColorPicker upColorPicker;
private ColorPicker downColorPicker;
private ColorPicker nsColorPicker;

private ComboBox<String> colorStyleCombo;

private Slider opacitySlider;
private Label opacityLabel;

// Prompt to save on close if there are unsaved changes.
// Returns true if it's OK to close, false if the close should be cancelled.
private boolean maybePromptSaveOnClose(Stage stage) {
    if (!dirty) return true;  // nothing to save

    String name = (currentProjectFile == null) ? "Untitled" : currentProjectFile.getName();

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.initOwner(stage);
    alert.setTitle("Save changes?");
    alert.setHeaderText("Save changes to \"" + name + "\" before closing?");
    alert.setContentText("Your recent changes will be lost if you don’t save.");

    ButtonType saveBtn   = new ButtonType("Save", ButtonBar.ButtonData.YES);
    ButtonType dontBtn   = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
    ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
    alert.getButtonTypes().setAll(saveBtn, dontBtn, cancelBtn);

    Optional<ButtonType> res = alert.showAndWait();
    if (!res.isPresent() || res.get() == cancelBtn) {
        return false;              // cancel close
    } else if (res.get() == saveBtn) {
        // If user cancels inside the Save dialog (e.g., closes chooser), keep the app open.
        int before = genes.size(); // just to have a reference
        saveProject(stage);
        // If user aborted save (e.g., closed chooser) your saveProject() returns early and dirty stays true.
        return !dirty;             // close only if save cleared the dirty flag
    } else { // Don't Save
        return true;               // proceed with close
    }
}

// --- Title updater (only once!) ---
private void updateWindowTitle() {
    String name = (currentProjectFile == null) ? "Untitled" : currentProjectFile.getName();
    String dot = dirty ? " •" : "";
    if (primaryStageRef != null) {
        primaryStageRef.setTitle(baseTitle + " — " + name + dot);
    }
}

// --- Percentile helper ---
private static double percentile(double[] sorted, double q) {
    if (sorted.length == 0) return 0.0;
    if (q <= 0) return sorted[0];
    if (q >= 1) return sorted[sorted.length - 1];
    double idx = q * (sorted.length - 1);
    int i = (int) Math.floor(idx);
    int j = Math.min(i + 1, sorted.length - 1);
    double frac = idx - i;
    return sorted[i] * (1 - frac) + sorted[j] * frac;
}

// --- Logo loader ---
private javafx.scene.image.Image tryLoadLogo() {
    String[] classpathCandidates = {
        "/MF_logo.png",
        "/volcano_icon.png",
        "/icons/MF_logo.png",
        "/icons/volcano_icon.png"
    };
    for (String p : classpathCandidates) {
        java.net.URL u = Magmaflow.class.getResource(p);
        if (u != null) {
            javafx.scene.image.Image img = new javafx.scene.image.Image(u.toExternalForm(), true);
            if (!img.isError()) return img;
        }
    }
    String[] files = { "file:MF_logo.png", "file:volcano_icon.png" };
    for (String f : files) {
        try {
            javafx.scene.image.Image img = new javafx.scene.image.Image(f, true);
            if (!img.isError()) return img;
        } catch (Exception ignore) {}
    }
    return null;
}

// --- Reflect current state into dashboard controls ---
private void reflectStateIntoControls() {
    // text fields
    logFCThresholdField.setText(String.valueOf(logFCThreshold));
    pValueThresholdField.setText(String.valueOf(pValueThreshold));
    topNUpField.setText(String.valueOf(topNUp));
    topNDownField.setText(String.valueOf(topNDown));

    // checkboxes
    useAdjPCheck.setSelected(useAdjustedP);
    annotateCheck.setSelected(annotate);
    showTargetsCheck.setSelected(showTargets);
    gridCheck.setSelected(showGrid);
    zeroLineCheck.setSelected(showZeroLine);
    hoverInfoCheck.setSelected(showHoverInfo);

    // colors
    upColorPicker.setValue(colUp);
    downColorPicker.setValue(colDown);
    nsColorPicker.setValue(colNS);

    // color style mapping
    switch (colorStyle) {
        case "MAGMA1":   colorStyleCombo.getSelectionModel().select(8); break;
        case "MAGMA2":   colorStyleCombo.getSelectionModel().select(7); break;
        case "MAGMA3":   colorStyleCombo.getSelectionModel().select(9); break;
        case "MAGMA4":   colorStyleCombo.getSelectionModel().select(5); break;
        case "MAGMA5":   colorStyleCombo.getSelectionModel().select(4); break;
        case "MAGMA6":   colorStyleCombo.getSelectionModel().select(6); break;
        case "MAGMA_STD1": colorStyleCombo.getSelectionModel().select(2); break;
        case "MAGMA_STD2": colorStyleCombo.getSelectionModel().select(1); break;
        case "MAGMA_STD3": colorStyleCombo.getSelectionModel().select(3); break;
        default: colorStyleCombo.getSelectionModel().select(0); break; // CLASSIC
    }

    // opacity
    boolean transparent = dotOpacity < 0.999;
    transparentDotsCheck.setSelected(transparent);
    opacitySlider.setDisable(!transparent);
    opacitySlider.setValue(dotOpacity);
    opacityLabel.setText(String.format("Dot Opacity: %.0f%%", dotOpacity * 100));
}

// --- Color & parsing helpers (only one version, not multiple!) ---
private static String colorToWeb(Color c) { return c.toString(); } // simple fallback
private static Color parseColor(String s, Color fallback) {
    try { return (s == null || s.isEmpty()) ? fallback : Color.web(s); }
    catch (Exception ex) { return fallback; }
}
private static double parseDouble(String s, double fallback) {
    try { return (s == null) ? fallback : Double.parseDouble(s); }
    catch (Exception ex) { return fallback; }
}

private static boolean parseBool(String s, boolean fallback) {
    if (s == null) return fallback;
    return Boolean.parseBoolean(s);
}
private static String escape(String s) {
    return s == null ? "" : s.replace("\\","\\\\").replace("\n","\\n").replace("\t","\\t").replace("=","\\=");
}
private static String unescape(String s) {
    if (s == null) return "";
    return s.replace("\\n","\n").replace("\\t","\t").replace("\\=","=").replace("\\\\","\\");
}

    private static final boolean CANONICAL_LISTS_ENABLED = false;
     private Label scaleLabel;
     private Slider scaleSlider;
     private ScrollPane plotScrollPane;
     private SplitPane mainSplit;
     private boolean panning = false;
     private double panAnchorSceneX, panAnchorSceneY;
     private double panStartHPixel, panStartVPixel;
     private Group zoomGroup;
     private StackPane workbench;
     private static final String APP_VERSION = "Version v10.1.0-beta1 Prerelease #2 2025ñ2026";
     private static final String RELEASE_URL = "https://github.com/carlosbuss1/MagmaFlow/releases/tag/magmaflow";
    private Node buildControlsHeader() {
    // --- Logo
    ImageView logoView = new ImageView();
    logoView.setFitHeight(56);          // bigger logo
    logoView.setPreserveRatio(true);
    logoView.setSmooth(true);
    javafx.scene.image.Image logoImg = tryLoadLogo();
    if (logoImg != null) logoView.setImage(logoImg);

    // --- Title + subtitle
    Label title = new Label("MagmaFlow");
    title.setStyle("-fx-font-size: 28px; -fx-font-weight: plain;"); // bigger title

    Label subtitle = new Label("An Interactive Volcano Plot Application");
    subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;");
    subtitle.setWrapText(true);

    // --- First row layout: logo (col 0), title (col 1), subtitle on next row (col 1)
    GridPane top = new GridPane();
    top.setHgap(12);
    top.setVgap(2);

    ColumnConstraints c0 = new ColumnConstraints();
    c0.setMinWidth(56);
    c0.setPrefWidth(56);                 // reserve space so title aligns cleanly
    ColumnConstraints c1 = new ColumnConstraints();
    c1.setHgrow(Priority.ALWAYS);        // let title/subtitle take remaining width
    top.getColumnConstraints().addAll(c0, c1);

    top.add(logoView, 0, 0, 1, 2);       // logo spans 2 rows (title + subtitle)
    GridPane.setValignment(logoView, VPos.TOP); // align logo top with title

    top.add(title,    1, 0);
    top.add(subtitle, 1, 1);

    // --- Version & link below the top grid
    Label version = new Label("by Buss, Carlos E., Version v10.1 Prerelease Sep-2025");
    version.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

    Hyperlink link = new Hyperlink("Github carlosbuss1/MagmaFlow");
    link.setStyle("-fx-font-size: 11px;");
    link.setOnAction(e -> getHostServices().showDocument(
        "https://github.com/carlosbuss1/MagmaFlow/releases/tag/magmaflow"
    ));

    VBox header = new VBox(6, top, version, link);
    header.setAlignment(Pos.TOP_LEFT);
    header.setFillWidth(true);
    header.setMaxWidth(Double.MAX_VALUE);
    header.setStyle(
        "-fx-background-color: linear-gradient(#fafafa, #f0f0f0);" +
        "-fx-padding: 10 12 10 12;" +
        "-fx-border-color: #dddddd;" +
        "-fx-border-width: 0 0 1 0;"
    );

    return header;
}

      
     // Unsaved-changes tracking
    private boolean dirty = false;
    private String baseTitle = "MagmaFlow";
    private Stage primaryStageRef;
     
     private double clamp(double v, double min, double max) {
    return (v < min) ? min : (v > max) ? max : v;
}

// Temporary stub while Canonical Targets are disabled
private void showCanonicalTargetsDialog(Stage stage) {
    new Alert(Alert.AlertType.INFORMATION,
        "Canonical Target Lists are temporarily disabled.").show();
}

private void markDirty() {
    if (!dirty) {
        dirty = true;
        updateWindowTitle();
    }
}
private void markClean() {
    dirty = false;
    updateWindowTitle();
}


/** Enable SHIFT+drag panning of the plot inside the ScrollPane. */
/** Enable SHIFT+drag panning of the plot inside the ScrollPane. */
/** Enable panning: SHIFT+Left anywhere, Middle mouse anywhere, or Left on gray only. */
private void enablePanDrag() {
    if (workbench == null || plotScrollPane == null) return;

    workbench.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
        boolean isMiddle = e.getButton() == MouseButton.MIDDLE;
        boolean isShiftLeft = e.getButton() == MouseButton.PRIMARY && e.isShiftDown();
        boolean isLeftOnGray = e.getButton() == MouseButton.PRIMARY && e.getTarget() == workbench;

        if (isMiddle || isShiftLeft || isLeftOnGray) {
            var vp = plotScrollPane.getViewportBounds();
            var content = workbench.getLayoutBounds();

            double hMax = Math.max(0, content.getWidth()  - vp.getWidth());
            double vMax = Math.max(0, content.getHeight() - vp.getHeight());
            if (hMax == 0 && vMax == 0) return; // nothing to pan

            panning = true;
            panAnchorSceneX = e.getSceneX();
            panAnchorSceneY = e.getSceneY();
            panStartHPixel = plotScrollPane.getHvalue() * hMax;
            panStartVPixel = plotScrollPane.getVvalue() * vMax;

            workbench.setCursor(Cursor.CLOSED_HAND);
            e.consume();
        }
    });

    workbench.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
        if (!panning) return;

        var vp = plotScrollPane.getViewportBounds();
        var content = workbench.getLayoutBounds();

        double hMax = Math.max(0, content.getWidth()  - vp.getWidth());
        double vMax = Math.max(0, content.getHeight() - vp.getHeight());

        double dx = e.getSceneX() - panAnchorSceneX;
        double dy = e.getSceneY() - panAnchorSceneY;

        double newLeft = panStartHPixel - dx;
        double newTop  = panStartVPixel - dy;

        plotScrollPane.setHvalue(hMax == 0 ? 0 : clamp(newLeft / hMax, 0, 1));
        plotScrollPane.setVvalue(vMax == 0 ? 0 : clamp(newTop  / vMax, 0, 1));

        e.consume();
    });

    workbench.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
        if (panning) {
            panning = false;
            workbench.setCursor(Cursor.DEFAULT);
            e.consume();
        }
    });
}

/** Ask confirmation before removing target annotations. Returns true if user confirms. */
private boolean confirmRemoveTargetAnnotations(Stage stage) {
    if (targetedGenes.isEmpty()) {
        new Alert(Alert.AlertType.INFORMATION,
                "There are no Target Genes to remove.").show();
        return false;
    }

    long enabledCount = targetedGenes.stream()
            .filter(g -> targetGeneSelections.getOrDefault(g, new SimpleBooleanProperty(true)).get())
            .count();

    StringBuilder msg = new StringBuilder();
    msg.append(String.format(
        "This will remove %d Target Gene(s) (%d currently enabled) and delete their labels from the plot.",
        targetedGenes.size(), enabledCount));
    if (annotate) {
        msg.append("\n\nNote: Genes that also qualify as Top-N may remain visible while ìShow top genesî is enabled.");
    }
    msg.append("\n\nProceed?");

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.initOwner(stage);
    alert.setTitle("Remove Target Annotations");
    alert.setHeaderText("Confirm removal of Target annotations");
    alert.setContentText(msg.toString());

    ButtonType removeBtn = new ButtonType("Remove", ButtonBar.ButtonData.OK_DONE);
    ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
    alert.getButtonTypes().setAll(removeBtn, cancelBtn);

    Optional<ButtonType> res = alert.showAndWait();
    return res.isPresent() && res.get() == removeBtn;
}

/** Center the current content in the viewport. */
private void centerContent() {
    if (plotScrollPane == null || workbench == null) return;
    var vp = plotScrollPane.getViewportBounds();
    var content = workbench.getLayoutBounds();

    double hMax = Math.max(0, content.getWidth()  - vp.getWidth());
    double vMax = Math.max(0, content.getHeight() - vp.getHeight());

    double hVal = (hMax == 0) ? 0 : ((content.getWidth()  - vp.getWidth())  / 2.0) / hMax;
    double vVal = (vMax == 0) ? 0 : ((content.getHeight() - vp.getHeight()) / 2.0) / vMax;

    plotScrollPane.setHvalue(clamp(hVal, 0, 1));
    plotScrollPane.setVvalue(clamp(vVal, 0, 1));
}

/** Compute a scale so the white plot fits in the viewport (with a small margin), apply it, and center. */
private void fitPlotToViewport() {
    if (plotScrollPane == null || plotPane == null) return;

    plotScrollPane.applyCss();
    plotScrollPane.layout();

    var vp = plotScrollPane.getViewportBounds();
    var plotBounds = plotPane.getLayoutBounds();
    if (vp.getWidth() <= 0 || vp.getHeight() <= 0 || plotBounds.getWidth() <= 0 || plotBounds.getHeight() <= 0) {
        return;
    }

    double margin = 40; // pixels of breathing space
    double sx = (vp.getWidth()  - margin) / plotBounds.getWidth();
    double sy = (vp.getHeight() - margin) / plotBounds.getHeight();
    double scale = Math.max(scaleSlider.getMin(), Math.min(scaleSlider.getMax(), Math.min(sx, sy)));

    // Move slider (fires applyZoom which scales zoomGroup and preserves view)
    scaleSlider.setValue(scale);

    // After zoom is applied, center the content once
    Platform.runLater(this::centerContent);
}

    // keep exactly ONE main() in the class
    

    // Do NOT put a start(Stage ...) here ó keep your full start(...) later in the file.

    class Gene {
        String name;
        double logFC;
        double pValue;
        double pAdj;
        double dotX;
        double dotY;
        double labelX;
        double labelY;
        boolean userPosition;
        boolean isTargetGene;
        Circle dotNode;
        Line lineNode;
        Text labelNode;

        Gene(String name, double logFC, double pValue, double pAdj) {
            this.name = name;
            this.logFC = logFC;
            this.pValue = pValue;
            this.pAdj = pAdj;
            this.labelX = 0;
            this.labelY = 0;
            this.isTargetGene = false;
        }
    }

    private Map<String, List<String>> getCanonicalTargetCategories() {
        Map<String, List<String>> categories = new LinkedHashMap<>();
  
    categories.put("Protein Tyrosine Phosphatases (PTPs)", Arrays.asList(
        "PTPN1", "PTPN2", "PTPN6", "PTPN11", "PTPRC", "PTPRA", "PTPRB", 
        "PTPRD", "PTPRF", "PTPRG", "PTPRJ", "PTPRK", "PTPRM", "PTPRO", "PTPRS"
    ));
    
    categories.put("Steatosis (Fatty Liver Disease)", Arrays.asList(
        "PPARA", "PPARG", "SREBF1", "FASN", "ACACA", "SCD", "CPT1A", "MTTP", 
        "PNPLA3", "DGAT2", "NR1H3", "NR1H4", "CD36", "LEPR", "ADIPOQ"
    ));
    
    categories.put("MASH (Metabolic Dysfunction-Associated Steatohepatitis)", Arrays.asList(
        "TGFB1", "COL1A1", "ACTA2", "TIMP1", "IL6", "TNF", "TLR4", "NFKB1", 
        "CXCL8", "CCL2", "NLRP3", "CASP1", "SREBF1", "PNPLA3", "HIF1A"
    ));
    
    categories.put("Cirrhosis", Arrays.asList(
        "TGFB1", "COL1A1", "COL3A1", "ACTA2", "TIMP1", "MMP2", "MMP9", 
        "PDGFRA", "VEGFA", "IL10", "IL17A", "SMAD3", "CTGF", "HGF"
    ));
    
    categories.put("Hepatocellular Carcinoma (HCC)", Arrays.asList(
        "TP53", "CTNNB1", "AXIN1", "TERT", "MYC", "MET", "VEGFA", "AFP", 
        "CDKN2A", "ARID1A", "PTEN", "AKT1", "IL6", "JAK1"
    ));
    
    categories.put("Blood Cancer (Leukemia/Lymphoma)", Arrays.asList(
        "BCR", "ABL1", "PML", "RARA", "FLT3", "NPM1", "RUNX1", "MYC", "TP53", 
        "NOTCH1", "JAK2", "STAT3", "KRAS", "NRAS", "IDH1", "IDH2", "CD19", "MS4A1"
    ));
    
    categories.put("Main Pediatric Cancers", Arrays.asList(
        "MYCN", "ALK", "PAX3", "FOXO1", "EWSR1", "FLI1", "WT1", "RB1", 
        "TP53", "BCOR", "CTNNB1", "NF1"
    ));
    
    categories.put("Insulin Resistance", Arrays.asList(
        "IRS1", "IRS2", "AKT2", "SLC2A4", "PPARG", "ADIPOQ", "LEPR", "TNF", 
        "IL6", "PRKAA1", "MTOR", "PTPN1", "SREBF1"
    ));
    
    categories.put("Mature Beta-like Cells (Pancreatic Islets)", Arrays.asList(
        "INS", "PDX1", "MAFA", "NKX6-1", "GCK", "ABCC8", "KCNJ11", 
        "SLC2A2", "IAPP", "UCN3"
    ));
    
    categories.put("Immature Beta-like Cells", Arrays.asList(
        "NGN3", "NEUROD1", "FOXA2", "SOX9", "HES1", "RFX6", "ARX", 
        "PAX4", "NKX2-2"
    ));
    
    categories.put("Glycolysis-Related Genes", Arrays.asList(
        "HK2", "GPI", "PFKM", "ALDOA", "TPI1", "GAPDH", "PGK1", 
        "PGAM1", "ENO1", "PKM2", "LDHA"
    ));
    
    categories.put("Lung Fibrosis (IPF & Fibrotic Pathways)", Arrays.asList(
        "TGFB1", "COL1A1", "ACTA2", "FN1", "MMP7", "MMP9", "TIMP1", 
        "PDGFRA", "VEGFA", "IL13", "STAT3"
    ));
    
    categories.put("Breast Cancer", Arrays.asList(
        "ESR1", "PGR", "ERBB2", "BRCA1", "BRCA2", "TP53", "PIK3CA", "AKT1", 
        "PTEN", "GATA3", "MYC", "CCND1", "CDH1", "KRAS", "FGFR1", "AR"
    ));
    
    return categories;
}

/** Zoom the plot around the viewport center so it doesn't slip under the left controls. */
/** Zoom the plot around the left edge (and keep vertical center in view). */
private void applyZoom(double newScale) {
    if (zoomGroup == null || plotScrollPane == null) return;

    // If viewport not ready yet, just set scale and label.
    if (plotScrollPane.getViewportBounds().getWidth() == 0) {
        zoomGroup.setScaleX(newScale);
        zoomGroup.setScaleY(newScale);
        if (scaleLabel != null)
            scaleLabel.setText(String.format("Magnification: %.0f%%", newScale * 100));
        return;
    }

    double oldScale = (zoomGroup.getScaleX() == 0 ? 1.0 : zoomGroup.getScaleX());

    // OLD content size (already includes padding + current zoom due to Group)
    var vp = plotScrollPane.getViewportBounds();
    var oldBounds = workbench.getLayoutBounds();
    double oldW = oldBounds.getWidth();
    double oldH = oldBounds.getHeight();

    // Current scroll offsets (in content pixels)
    double hPixel = plotScrollPane.getHvalue() * Math.max(0, oldW - vp.getWidth());
    double vPixel = plotScrollPane.getVvalue() * Math.max(0, oldH - vp.getHeight());

    // Anchor at left edge horizontally, keep vertical center
    double anchorX = 0;
    double anchorY = vPixel + vp.getHeight() / 2.0;

    // Apply scale to the GROUP (not the Pane)
    zoomGroup.setScaleX(newScale);
    zoomGroup.setScaleY(newScale);

    // NEW content size (padding + new zoom)
    var newBounds = workbench.getLayoutBounds();
    double newW = newBounds.getWidth();
    double newH = newBounds.getHeight();

    // Keep anchor position stable using content-size ratio
    double scaleRatioW = (oldW == 0) ? 1.0 : (newW / oldW);
    double scaleRatioH = (oldH == 0) ? 1.0 : (newH / oldH);

    double newLeft = anchorX * scaleRatioW - vp.getWidth() / 2.0;
    double newTop  = anchorY * scaleRatioH - vp.getHeight() / 2.0;

    double hMax = Math.max(0, newW - vp.getWidth());
    double vMax = Math.max(0, newH - vp.getHeight());

    plotScrollPane.setHvalue(hMax == 0 ? 0 : clamp(newLeft / hMax, 0, 1));
    plotScrollPane.setVvalue(vMax == 0 ? 0 : clamp(newTop  / vMax, 0, 1));

    if (scaleLabel != null) {
        scaleLabel.setText(String.format("Magnification: %.0f%%", newScale * 100));
    }
}


/**
 * Show dialog for selecting canonical target gene categoriesprivate void showCanonicalTargetsDialog(Stage stage) {
    // Fast-exit when the feature is disabled
    if (!CANONICAL_LISTS_ENABLED) {
        new Alert(Alert.AlertType.INFORMATION,
                "Canonical Target Lists are temporarily disabled.").show();
        return;
    }

    // FIX 1: Change Dialog<Void> to Dialog<ButtonType>
    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.setTitle("Select Canonical Target Gene Categories");
    dialog.setHeaderText("Choose gene categories to add to your target list");
    dialog.initOwner(stage);
    
    VBox vbox = new VBox(10);
    vbox.setPadding(new Insets(15));
    
    Map<String, List<String>> categories = getCanonicalTargetCategories();
    Map<String, CheckBox> categoryCheckBoxes = new HashMap<>();
    
    // Create checkboxes for each category
    for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
        String categoryName = entry.getKey();
        List<String> genes = entry.getValue();
        
        CheckBox categoryCheckBox = new CheckBox(categoryName + " (" + genes.size() + " genes)");
        categoryCheckBox.setStyle("-fx-font-weight: bold;");
        categoryCheckBoxes.put(categoryName, categoryCheckBox);
        
        // Create a collapsible view of genes in this category
        VBox geneList = new VBox(2);
        geneList.setPadding(new Insets(5, 0, 5, 25));
        
        Label geneLabel = new Label(String.join(", ", genes));
        geneLabel.setWrapText(true);
        geneLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666;");
        geneLabel.setMaxWidth(500);
        
        geneList.getChildren().add(geneLabel);
        geneList.setVisible(false);
        geneList.setManaged(false);
        
        // Toggle gene list visibility when category is clicked
        categoryCheckBox.setOnAction(e -> {
            boolean visible = geneList.isVisible();
            geneList.setVisible(!visible);
            geneList.setManaged(!visible);
        });
        
        vbox.getChildren().addAll(categoryCheckBox, geneList);
    }
    
    // Add control buttons
    HBox buttonBox = new HBox(10);
    buttonBox.setPadding(new Insets(15, 0, 0, 0));
    
    Button selectAllBtn = new Button("Select All");
    selectAllBtn.setOnAction(e -> categoryCheckBoxes.values().forEach(cb -> cb.setSelected(true)));
    
    Button clearAllBtn = new Button("Clear All");
    clearAllBtn.setOnAction(e -> categoryCheckBoxes.values().forEach(cb -> cb.setSelected(false)));
    
    buttonBox.getChildren().addAll(selectAllBtn, clearAllBtn);
    vbox.getChildren().add(buttonBox);
    
    ScrollPane scrollPane = new ScrollPane(vbox);
    scrollPane.setFitToWidth(true);
    scrollPane.setPrefViewportHeight(400);
    scrollPane.setPrefViewportWidth(550);
    
    dialog.getDialogPane().setContent(scrollPane);
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    
    // FIX 2: Add result converter to properly return ButtonType
    dialog.setResultConverter(dialogButton -> dialogButton);
    
    // Now this line works correctly - returns Optional<ButtonType>
    Optional<ButtonType> result = dialog.showAndWait();
    if (result.isPresent() && result.get() == ButtonType.OK) {
        // Add selected genes to target list
        int totalAdded = 0;
        Set<String> newGenes = new HashSet<>();
        
        for (Map.Entry<String, CheckBox> entry : categoryCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                String categoryName = entry.getKey();
                List<String> categoryGenes = categories.get(categoryName);
                for (String gene : categoryGenes) {
                    if (!targetedGenes.contains(gene)) {
                        newGenes.add(gene);
                    }
                }
            }
        }
        
        // Add new genes to the target list
        targetedGenes.addAll(newGenes);
        totalAdded = newGenes.size();
        
        // Initialize selection properties for new genes
        for (String gene : newGenes) {
            targetGeneSelections.put(gene, new SimpleBooleanProperty(true));
        }
        
        // Update UI and redraw
        updateTargetGeneListView();
        updateAnnotations();
        drawPlot();
        
        new Alert(Alert.AlertType.INFORMATION,
            "Added " + totalAdded + " new genes. Total target genes: " + targetedGenes.size()).show();
    }
}

    /** Column mapping for CSV import. */
    class ColumnMapping {
        int geneNameCol;
        int logFCCol;
        int pValCol;
        int pValAdjCol;

        ColumnMapping(int geneNameCol, int logFCCol, int pValCol, int pValAdjCol) {
            this.geneNameCol = geneNameCol;
            this.logFCCol = logFCCol;
            this.pValCol = pValCol;
            this.pValAdjCol = pValAdjCol;
        }
    }

    /** Wrapper for axis and title labels. */
    class LabelSettings {
        String title;
        String xLabel;
        String yLabel;
        LabelSettings(String title, String xLabel, String yLabel) {
            this.title = title;
            this.xLabel = xLabel;
            this.yLabel = yLabel;
        }
    }

    /** Display options for configurable aspects of the plot. */
    class DisplaySettings {
    double edgeWidth;
    double thresholdLineWidth;
    double dotSize;
    double canvasWidth;
    double canvasHeight;
    double fontSize;
    double dotOpacity;
    boolean showDotOutline;
    Color dotOutlineColor;
    double dotOutlineWidth;
    double xLabelDistance;  
    double yLabelDistance;  
    boolean showHoverInfo;
    
    DisplaySettings(double edgeWidth, double thresholdLineWidth, double dotSize,
                  double canvasWidth, double canvasHeight, double fontSize,
                  double dotOpacity, boolean showDotOutline, 
                  Color dotOutlineColor, double dotOutlineWidth,
                  double xLabelDistance, double yLabelDistance,
                  boolean showHoverInfo) {
        this.edgeWidth = edgeWidth;
        this.thresholdLineWidth = thresholdLineWidth;
        this.dotSize = dotSize;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.fontSize = fontSize;
        this.dotOpacity = dotOpacity;
        this.showDotOutline = showDotOutline;
        this.dotOutlineColor = dotOutlineColor;
        this.dotOutlineWidth = dotOutlineWidth;
        this.xLabelDistance = xLabelDistance;  
        this.yLabelDistance = yLabelDistance;
        this.showHoverInfo = showHoverInfo;  
    }
}
    /** Settings used during image export. */
    class ExportSettings {
        int dpi;
        boolean transparent;
        boolean pdf;
        ExportSettings(int dpi, boolean transparent, boolean pdf) {
            this.dpi = dpi;
            this.transparent = transparent;
            this.pdf = pdf;
        }
    }

    // Data and state
    private final List<Gene> genes = new ArrayList<>();
    private final Set<String> targetedGenes = new HashSet<>();
    private final Label geneInfo = new Label("");

    // List view and selection map for targeted genes.  Each gene can be
    // individually enabled or disabled for annotation via a checkbox.
    private javafx.scene.control.ListView<String> targetedGeneListView;
    private final java.util.Map<String, javafx.beans.property.BooleanProperty> targetGeneSelections = new java.util.HashMap<>();

    // Reference to the controls panel so that the theme can be updated
    private VBox controlsPanel;

    // Plot configuration
    private double logFCThreshold = 1.0;
    private double pValueThreshold = 0.05;
    private double dotOpacity = 1.0;
    private Color colUp = Color.RED;
    private Color colDown = Color.BLUE;
    private Color colNS = Color.BLACK;
    private int topNUp = 5;
    private int topNDown = 5;
    private boolean annotate = true;
    private boolean useAdjustedP = true;
    private boolean showTargets = true;
    private boolean showDotOutline = false;
    private Color dotOutlineColor = Color.BLACK;
    private double dotOutlineWidth = 0.5;
    private double xLabelDistance = 50;  // Distance of X-axis label from the axis
    private double yLabelDistance = 50;  // Distance of Y-axis label from the axis
    private boolean showHoverInfo = true; // Option to enable/disable hover tooltips



    // Color style for significant genes.  "CLASSIC" uses the Gurzov style
    // (blue for down, red for up, black for non-significant).  "MAGMA1",
    // "MAGMA2" and "MAGMA3" correspond to the MagmaFlow styles: gradient
    // based on log2FC, p-value intensity, and a combination of both,
    // respectively.  See drawPlot() for details.
    private String colorStyle = "CLASSIC";

    // Show vertical line at log2 fold change = 0
    private boolean showZeroLine = false;
    // Whether to draw grid lines across the plot background
    private boolean showGrid = false;
    // Default widths for annotation edges and threshold lines set to 0.5
    private double edgeWidth = 0.5;
    private double thresholdLineWidth = 0.5;
    private double dotSize = 8.0;
    // Default canvas size 700x600 (approx 7x6 units) for a more compact plot
    private double canvasWidth = 700;
    private double canvasHeight = 600;
    /**
     * Base font size used for tick labels, gene labels and axis labels.  The
     * title will be rendered slightly larger than this value.  Users can
     * configure this in the display options dialog.
     */
    // Base font size; default increased to 18 for better readability
    private double fontSize = 24.0;
    private double marginLeft = 80;
    private double marginRight = 50;
    private double marginTop = 80;
    // Increase bottom margin to provide space between the x-axis and the lowest points
    private double marginBottom = 100;
    private String xLabel = "log2 Fold Change";
    private String yLabel = "-log10(adj p-value)";
    private String plotTitle = "Volcano Plot";
    private double xMin;
    private double xMax;
    private double yMin = 0.5;
    private double yMax;
    private Pane plotPane;
    // Keep reference to the currently dragged gene and its drag offset
    private Gene draggedGene;
    private double dragOffsetX;
    private double dragOffsetY;

    // Persistent offsets for the y-axis label position when the user drags it.
    private double yLabelOffsetX = 0;
    private double yLabelOffsetY = 0;
    private boolean draggingYLabel = false;
    private double yLabelDragAnchorX;
    private double yLabelDragAnchorY;

    // Persistent offsets and drag state for the x-axis label
    private double xLabelOffsetX = 0;
    private double xLabelOffsetY = 0;
    private boolean draggingXLabel = false;
    private double xLabelDragAnchorX;
    private double xLabelDragAnchorY;

    // Persistent offsets and drag state for the plot title
    private double titleOffsetX = 0;
    private double titleOffsetY = 0;
    private boolean draggingTitle = false;
    private double titleDragAnchorX;
    private double titleDragAnchorY;

    // Multiplier to control how far labels move in response to drag gestures.
    // Increasing this value makes dragging more responsive and allows larger
    // movements per mouse drag.  Users found the default drag sensitivity
    // too low, requiring multiple small drags to reposition labels.  A value
    // of 2.0 doubles the movement distance.
    // Multiplier for drag sensitivity.  A higher value means that a small
    // mouse movement will result in a larger repositioning of the label.
    // Users reported that dragging the y-axis label was too slow, so the
    // default has been increased substantially.  Feel free to adjust this
    // value if you need even more responsiveness.
    private double labelDragMultiplier = 30.0;


    public static void main(String[] args) {
        launch(args);
    }

@Override
public void start(Stage primaryStage) {
    try {
        updateWindowTitle();
        // Get screen dimensions to prevent cropping
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double screenWidth = screenBounds.getWidth();
        double screenHeight = screenBounds.getHeight();
        
        // Calculate safe window dimensions (75% of screen size to ensure margins)
        double windowWidth = Math.min(1400, screenWidth * 0.75);
        double windowHeight = Math.min(900, screenHeight * 0.75);
        
        // Set reasonable minimum size
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(650);
        
        // Set calculated window size
        primaryStage.setWidth(windowWidth);
        primaryStage.setHeight(windowHeight);
        primaryStage.setResizable(true);
        
        // Position window safely on screen with margins
        primaryStage.setX(50); // Left margin
        primaryStage.setY(30); // Top margin
        
        this.primaryStageRef = primaryStage;   // keep a reference for the dirty-title dot
        primaryStage.setTitle(baseTitle);      // use the shared base title

        // ? Set window icon(s) BEFORE show()
        try {
        // Put MF_logo.ico in src/main/resources/
        InputStream is = getClass().getResourceAsStream("/MF_logo.png");
        if (is != null) {
            primaryStage.getIcons().add(new Image(is));
        } else {
            // Fallback to PNG if ICO not found or not supported
            primaryStage.getIcons().add(new Image(
                getClass().getResourceAsStream("/MF_logo.jpg")
            ));
        }
    } catch (Exception ignored) {}
        
        // Initialize controls panel with better spacing
        controlsPanel = new VBox(8); // Reduced spacing
        controlsPanel.setPadding(new Insets(8)); // Reduced padding
        controlsPanel.setPrefWidth(320);         // was ~280ñ320
        controlsPanel.setMinWidth(300);          // donít let it collapse too small
        controlsPanel.setMaxWidth(400);

        // ADD HEADER AT THE TOP (index 0)
        Node header = buildControlsHeader();
        controlsPanel.getChildren().add(0, header);
        controlsPanel.getChildren().add(1, new Separator());
        
        // App icon
        try {
            javafx.scene.image.Image appIcon = new javafx.scene.image.Image("file:volcano_icon.png");
            primaryStage.getIcons().add(appIcon);
        } catch (Exception ex) {
            // Ignore icon load failure
        }
        
        // UI scaling controls - assign to instance variables
        scaleLabel = new Label("Magnification: 100%");
        scaleSlider = new Slider(0.5, 3.0, 1.0);
        scaleSlider.setShowTickLabels(true);
        scaleSlider.setShowTickMarks(true);
        scaleSlider.setMajorTickUnit(0.5);
        scaleSlider.setMinorTickCount(4);
        scaleSlider.setPrefWidth(200); // Set preferred width for slider
       scaleSlider.valueProperty().addListener((obs, oldVal, newVal) -> applyZoom(newVal.doubleValue()));


        // *** CRITICAL CHANGE: Create plot pane BEFORE setting up the layout ***
plotPane = new Pane();
plotPane.setPrefSize(canvasWidth, canvasHeight);
plotPane.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
plotPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
plotPane.setStyle("-fx-background-color: white;");
plotPane.setPickOnBounds(true); // clicks on empty white area register

zoomGroup = new Group(plotPane);          // transforms affect bounds
workbench = new StackPane(zoomGroup);     // centers content when smaller
// Keep the plot in the top-left, not vertically centered
StackPane.setAlignment(zoomGroup, Pos.TOP_LEFT);

workbench.setPadding(new Insets(200));    // gray margin around plot
workbench.setStyle("-fx-background-color: #e5e5e5;");
workbench.setPickOnBounds(true);          // allow dragging on gray

plotScrollPane = new ScrollPane(workbench);
plotScrollPane.setFitToWidth(false);
plotScrollPane.setFitToHeight(false);
plotScrollPane.setPrefSize(800, 600);
plotScrollPane.setMinSize(400, 300);
plotScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
plotScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
plotScrollPane.setPannable(false); 

// we implement our own SHIFT-drag pan
       // --------------------------------------------------------------------
// ROOT + PANES: create these FIRST, then wire them into the layout
// --------------------------------------------------------------------

// 1) Root layout
BorderPane root = new BorderPane();
root.setPadding(new Insets(5)); // Add padding around entire layout

// 2) Controls panel (left) + its ScrollPane
controlsPanel.setPrefWidth(280);
controlsPanel.setMinWidth(250);
controlsPanel.setMaxWidth(350);

ScrollPane controlsScrollPane = new ScrollPane(controlsPanel);
controlsScrollPane.setPrefWidth(300);
controlsScrollPane.setFitToWidth(true);
controlsScrollPane.setFitToHeight(false);
controlsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
controlsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
controlsScrollPane.setPannable(true);

root.setLeft(controlsScrollPane);

// 3) Plot pane (center) + its ScrollPane
plotPane.setPrefSize(canvasWidth, canvasHeight);
plotPane.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
plotPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
plotPane.setStyle("-fx-background-color: white;");

// SplitPane with a vertical divider (moves horizontally)
mainSplit = new SplitPane();
mainSplit.setOrientation(Orientation.HORIZONTAL);

// Prevent the left panel from collapsing too small
controlsScrollPane.setMinWidth(220);
controlsScrollPane.setPrefWidth(300);   // starting size
SplitPane.setResizableWithParent(controlsScrollPane, true);

// Ensure the plot area has a reasonable minimum
plotScrollPane.setMinWidth(400);

// Add items and set initial divider position (0.30 = 30% for left)
mainSplit.getItems().addAll(controlsScrollPane, plotScrollPane);
mainSplit.setDividerPositions(0.30);

// ? Clamp the divider range HERE
final double MIN = 0.18;   // 18% minimum for the left panel
final double MAX = 0.60;   // 60% maximum for the left panel
mainSplit.getDividers().get(0).positionProperty().addListener((obs, oldV, newV) -> {
    double p = newV.doubleValue();
    if (p < MIN) {
        mainSplit.setDividerPositions(MIN);
    } else if (p > MAX) {
        mainSplit.setDividerPositions(MAX);
    }
});

// Put the split into the center; no need for root.setLeft now
root.setCenter(mainSplit);

// 5) Create and set the scene (do NOT call show() here; weíll keep your later show)
double sceneWidth = windowWidth - 20;  // Account for window decorations
double sceneHeight = windowHeight - 40; // Account for title bar
Scene scene = new Scene(root, sceneWidth, sceneHeight);
primaryStage.setScene(scene);
primaryStage.show();

primaryStage.setOnCloseRequest(ev -> {
    if (!maybePromptSaveOnClose(primaryStage)) {
        ev.consume(); // stop the window from closing
    }
});

Platform.runLater(() -> {
    // Build CSS/layout so sizes are real
    if (workbench != null) { workbench.applyCss(); workbench.layout(); }

    // Fit the whole volcano into view (sets slider + zoom, then centers)
    fitPlotToViewport();

    // Enable panning (SHIFT+Left anywhere, Middle anywhere, or left on gray)
    enablePanDrag();

    // Gray viewport background (optional)
    Node viewport = plotScrollPane.lookup(".viewport");
    if (viewport != null) viewport.setStyle("-fx-background-color: #e5e5e5;");
    if (workbench != null) workbench.setStyle("-fx-background-color: #e5e5e5;");

    // Make the SplitPane divider thicker + double-click reset
    for (Node divider : mainSplit.lookupAll(".split-pane-divider")) {
        divider.setStyle(
            "-fx-padding: 0 6 0 6;" +
            "-fx-background-color: linear-gradient(#d9d9d9, #bfbfbf);"
        );
        divider.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) mainSplit.setDividerPositions(0.30);
        });
    }
});

// Make sure the current slider value is applied to the plot at startup
applyZoom(scaleSlider.getValue());
        
        // Debug output to console (will show in debug build)
        System.out.println("Screen size: " + screenWidth + "x" + screenHeight);
        System.out.println("Window size: " + windowWidth + "x" + windowHeight);
        System.out.println("Available area: " + screenBounds);
        System.out.println("Header added. Children count = " + controlsPanel.getChildren().size());

    } catch (Exception e) {
        e.printStackTrace();
        // Fallback to safe defaults if screen detection fails
        primaryStage.setWidth(1100);
        primaryStage.setHeight(750);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(650);
        primaryStage.setX(50);
        primaryStage.setY(30);
    }
        
    // Buttons
                Button loadButton = new Button("Load CSV      ");
             Button exportButton = new Button("Export Image");
     Button saveProjectButton = new Button("Save Project  ");
     Button loadProjectButton = new Button("Load Project  ");
     Button loadTargetGenesButton = new Button("Load List");
    Button enterTargetGenesButton = new Button("Type or Paste");
      Button setLabelsButton = new Button("Set Labels ");
    Button setDisplayButton = new Button("Set Display");
    Button refreshTargetsButton = new Button("Refresh");
    refreshTargetsButton.setOnAction(e -> refreshTargetGenes(primaryStage));
    Button canonicalTargetsButton = new Button("Canonical Lists");
canonicalTargetsButton.setOnAction(e -> showCanonicalTargetsDialog(primaryStage));

if (!CANONICAL_LISTS_ENABLED) {
    // Hide and remove from layout calculations
    canonicalTargetsButton.setManaged(false);
    canonicalTargetsButton.setVisible(false);
    // If you prefer to keep it visible but inactive, use:
    // canonicalTargetsButton.setDisable(true);
}

    // Color pickers

    upColorPicker = new ColorPicker(colUp);
    upColorPicker.setPrefWidth(80);
    upColorPicker.setPrefHeight(25);    // Prevent flattening
    upColorPicker.setMinHeight(25);     // Minimum height
    upColorPicker.setMaxHeight(35);     // Maximum height

    downColorPicker = new ColorPicker(colDown);
    downColorPicker.setPrefWidth(80);
    downColorPicker.setPrefHeight(25);
    downColorPicker.setMinHeight(25);
    downColorPicker.setMaxHeight(35);

    nsColorPicker = new ColorPicker(colNS);
    nsColorPicker.setPrefWidth(80);
    nsColorPicker.setPrefHeight(25);
    nsColorPicker.setMinHeight(25);
    nsColorPicker.setMaxHeight(35);

    // Threshold fields
    logFCThresholdField = new TextField(String.valueOf(logFCThreshold));
    pValueThresholdField = new TextField(String.valueOf(pValueThreshold));


    // Checkboxes
    useAdjPCheck = new CheckBox("Use adjusted p-values");
    useAdjPCheck.setSelected(true);
    
    annotateCheck = new CheckBox("Show top genes");
    annotateCheck.setSelected(true);
    
    showTargetsCheck = new CheckBox("Show Target Genes");
    showTargetsCheck.setSelected(true);
    
    gridCheck = new CheckBox("Show grid lines");
    gridCheck.setSelected(showGrid);
    
    zeroLineCheck = new CheckBox("Show 0 fold-change line");
    zeroLineCheck.setSelected(showZeroLine);
    
    hoverInfoCheck = new CheckBox("Show gene info on hover");
    hoverInfoCheck.setSelected(showHoverInfo);
    
    transparentDotsCheck = new CheckBox("Transparent dots");
    transparentDotsCheck.setSelected(false);
    

// Add a slider for transparency control
opacityLabel = new Label("Dot Opacity: 100%");
opacitySlider = new Slider(0.1, 1.0, 1.0);
opacitySlider.setShowTickLabels(true);
opacitySlider.setShowTickMarks(true);
opacitySlider.setMajorTickUnit(0.2);
opacitySlider.setMinorTickCount(4);
opacitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
    dotOpacity = newVal.doubleValue();
    opacityLabel.setText(String.format("Dot Opacity: %.0f%%", dotOpacity * 100));
    drawPlot();
});

// Initially disable the slider
opacitySlider.setDisable(true);

    // Top N fields
    topNUpField = new TextField(String.valueOf(topNUp));
    topNDownField = new TextField(String.valueOf(topNDown));
    
    

    // Color style combo
    colorStyleCombo = new ComboBox<>();
colorStyleCombo.getItems().addAll(
    "Gurzov Classic Style",
    "MagmaFlow Classic 1 (p-value)",
    "MagmaFlow Classic 2 (log2FC)",
    "MagmaFlow Classic 3 (combined)",
    "MagmaFlow Viridis 1 (p-value)",
    "MagmaFlow Viridis 2 (log2FC)",
    "MagmaFlow Viridis 3 (combined)",
    "MagmaFlow MasterOfReality 1 (p-value)",
    "MagmaFlow MasterOfReality 2 (log2FC)",
    "MagmaFlow MasterOfReality 3 (combined)"
);


    // Targeted gene list
    targetedGeneListView = new ListView<>();
    targetedGeneListView.setPrefHeight(150);
    targetedGeneListView.setCellFactory(CheckBoxListCell.forListView(item -> {
        BooleanProperty prop = targetGeneSelections.computeIfAbsent(item,
            k -> new SimpleBooleanProperty(true));
        prop.addListener((obs, oldVal, newVal) -> drawPlot());
        return prop;
    }));

    // Gene info label
    geneInfo.setFont(Font.font("Arial", fontSize));

    // Build controls panel
    controlsPanel.getChildren().addAll(
        new VBox(new Label("UI Scale:"), scaleSlider, scaleLabel),
        new Separator(),
        loadButton,
        exportButton,
        saveProjectButton,
        loadProjectButton,
        new Separator(),
        new Label("Target Genes:"),
        new HBox(5, loadTargetGenesButton, enterTargetGenesButton, canonicalTargetsButton, refreshTargetsButton),
        targetedGeneListView,
        showTargetsCheck,
        new Separator(),
        setLabelsButton,
        setDisplayButton,
        new Separator(),
        new Label("Color Settings:"),
        colorStyleCombo, // ? moved here
        new HBox(5, new Label("        Up:"), upColorPicker),
        new HBox(5, new Label("   Down:"), downColorPicker),
        new HBox(5, new Label("        NS:"), nsColorPicker),
        new Separator(),
        new Label("Threshold Settings:"),
        new HBox(5, new Label("   logFC:"), logFCThresholdField),
        new HBox(5, new Label("p-value:"), pValueThresholdField),
        useAdjPCheck,
        new Separator(),
        annotateCheck,
        new HBox(5,
            new VBox(new Label("Top N Up:"), topNUpField),
            new VBox(new Label("Top N Down:"), topNDownField)
        ),
        new Separator(),
        gridCheck,
        zeroLineCheck,
        transparentDotsCheck,
	new VBox(5, opacityLabel, opacitySlider),
        new Separator(),
        hoverInfoCheck,
        geneInfo
         );
 
    // Event handlers
    loadProjectButton.setOnAction(e -> loadProject(primaryStage));
    loadButton.setOnAction(e -> loadCSV(primaryStage));
    exportButton.setOnAction(e -> showExportDialog(primaryStage));
    saveProjectButton.setOnAction(e -> saveProject(primaryStage));
    loadTargetGenesButton.setOnAction(e -> loadTargetGenes(primaryStage));
    setLabelsButton.setOnAction(e -> showLabelSettingsDialog(primaryStage));
    setDisplayButton.setOnAction(e -> showDisplayOptionsDialog(primaryStage));

    upColorPicker.setOnAction(e -> { colUp = upColorPicker.getValue(); drawPlot(); });
    downColorPicker.setOnAction(e -> { colDown = downColorPicker.getValue(); drawPlot(); });
    nsColorPicker.setOnAction(e -> { colNS = nsColorPicker.getValue(); drawPlot(); });

    logFCThresholdField.setOnAction(e -> {
        try { logFCThreshold = Double.parseDouble(logFCThresholdField.getText()); drawPlot(); }
        catch (NumberFormatException ignored) {}
    });

    pValueThresholdField.setOnAction(e -> {
        try { pValueThreshold = Double.parseDouble(pValueThresholdField.getText()); drawPlot(); }
        catch (NumberFormatException ignored) {}
    });

    useAdjPCheck.setOnAction(e -> {
        useAdjustedP = useAdjPCheck.isSelected();
        yLabel = useAdjustedP ? "-log10(adj p-value)" : "-log10(p-value)";
        drawPlot();
    });
    
    hoverInfoCheck.setOnAction(e -> { 
    showHoverInfo = hoverInfoCheck.isSelected(); 
    drawPlot(); // Redraw to apply hover changes
    });

    annotateCheck.setOnAction(e -> { annotate = annotateCheck.isSelected(); drawPlot(); });
   showTargetsCheck.setOnAction(e -> {
    showTargets = showTargetsCheck.isSelected();
    drawPlot();                 // just redraw with current toggles
    });


    enterTargetGenesButton.setOnAction(e -> {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Enter Target Genes");
        dialog.setHeaderText("Enter gene names separated by commas or new lines");
        dialog.setContentText("Genes:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(genesText -> {
            String[] genesArray = genesText.split("[,\\s]+");
            int addedCount = 0;
            for (String gene : genesArray) {
                String trimmed = gene.trim();
                if (!trimmed.isEmpty() && !targetedGenes.contains(trimmed)) {
                    targetedGenes.add(trimmed);
                    targetGeneSelections.put(trimmed, new SimpleBooleanProperty(true));
                    addedCount++;
                }
            }
            if (addedCount > 0) {
                updateTargetGeneListView();
                updateAnnotations();
                drawPlot();
                new Alert(Alert.AlertType.INFORMATION,
                    "Added " + addedCount + " new target gene(s).").show();
            } else {
                new Alert(Alert.AlertType.INFORMATION,
                    "No new genes were added.").show();
            }
        });
    });

    colorStyleCombo.setOnAction(e -> {
        switch (colorStyleCombo.getSelectionModel().getSelectedIndex()) {
            case 8: colorStyle = "MAGMA1"; break;
            case 7: colorStyle = "MAGMA2"; break;
            case 9: colorStyle = "MAGMA3"; break;
            case 5: colorStyle = "MAGMA4"; break;
            case 4: colorStyle = "MAGMA5"; break;
            case 6: colorStyle = "MAGMA6"; break;
            case 2: colorStyle = "MAGMA_STD1"; break;
            case 1: colorStyle = "MAGMA_STD2"; break;
            case 3: colorStyle = "MAGMA_STD3"; break;
            default: colorStyle = "CLASSIC"; break;
        }
        drawPlot();
    });

    gridCheck.setOnAction(e -> { showGrid = gridCheck.isSelected(); drawPlot(); });
    zeroLineCheck.setOnAction(e -> { showZeroLine = zeroLineCheck.isSelected(); drawPlot(); });

    topNUpField.setOnAction(e -> {
        try { topNUp = Integer.parseInt(topNUpField.getText()); drawPlot(); }
        catch (NumberFormatException ignored) {}
    });

    topNDownField.setOnAction(e -> {
        try { topNDown = Integer.parseInt(topNDownField.getText()); drawPlot(); }
        catch (NumberFormatException ignored) {}
    });
    
    // Add the event handler for the transparency checkbox after other event handlers (around line 450):
transparentDotsCheck.setOnAction(e -> {
    boolean isTransparent = transparentDotsCheck.isSelected();
    opacitySlider.setDisable(!isTransparent);
    if (!isTransparent) {
        dotOpacity = 1.0;
        opacitySlider.setValue(1.0);
        opacityLabel.setText("Dot Opacity: 100%");
    }
    drawPlot();
});

// *** CRITICAL CHANGE: Initial draw at the very end ***
calculateAxisRanges();
drawPlot();
updateTargetGeneListView();

// Show the stage
primaryStage.show();

// Final sizing adjustment after the window is shown
Platform.runLater(() -> {
    primaryStage.sizeToScene(); // Ensure everything fits properly
});
}
    /**
     * Load gene data from a CSV file.  Prompts the user to map columns before
     * parsing into {@link Gene} objects, then refreshes the plot.
     */
    private void loadCSV(Stage stage) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Open CSV File");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
    File file = chooser.showOpenDialog(stage);
    if (file == null) {
        return;
    }

    try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
        String header = br.readLine();
        if (header == null) {
            return;
        }

        // Detect delimiter (comma, tab, or semicolon)
        char delimiter = detectDelimiter(header);
        
        // Parse header with proper CSV handling
        List<String> columns = parseCSVLine(header, delimiter);
        
        // Setup column mapping dialog
        Dialog<ColumnMapping> dialog = new Dialog<>();
        dialog.setTitle("Column Mapping");
        dialog.setHeaderText("Map the columns to their appropriate fields");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        ComboBox<String> geneNameCombo = new ComboBox<>();
        ComboBox<String> logFCCombo = new ComboBox<>();
        ComboBox<String> pValCombo = new ComboBox<>();
        ComboBox<String> pAdjCombo = new ComboBox<>();
        
        for (String col : columns) {
            geneNameCombo.getItems().add(col);
            logFCCombo.getItems().add(col);
            pValCombo.getItems().add(col);
            pAdjCombo.getItems().add(col);
        }
        
        // Auto-select likely columns based on common naming patterns
        autoSelectColumns(columns, geneNameCombo, logFCCombo, pValCombo, pAdjCombo);

        grid.add(new Label("Gene Name Column:"), 0, 0);
        grid.add(geneNameCombo, 1, 0);
        grid.add(new Label("LogFC Column:"), 0, 1);
        grid.add(logFCCombo, 1, 1);
        grid.add(new Label("P-Value Column:"), 0, 2);
        grid.add(pValCombo, 1, 2);
        grid.add(new Label("Adj. P-Value Column (optional):"), 0, 3);
        grid.add(pAdjCombo, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                return new ColumnMapping(
                        geneNameCombo.getSelectionModel().getSelectedIndex(),
                        logFCCombo.getSelectionModel().getSelectedIndex(),
                        pValCombo.getSelectionModel().getSelectedIndex(),
                        pAdjCombo.getSelectionModel().getSelectedIndex()
                );
            }
            return null;
        });

        Optional<ColumnMapping> result = dialog.showAndWait();
        if (!result.isPresent()) return;
        ColumnMapping mapping = result.get();
        
        genes.clear();
        String line;
        while ((line = br.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            
            List<String> parts = parseCSVLine(line, delimiter);
            if (parts.size() <= Math.max(Math.max(mapping.geneNameCol, mapping.logFCCol), 
                    Math.max(mapping.pValCol, mapping.pValAdjCol))) {
                continue;
            }
            
            try {
                String geneName = parts.get(mapping.geneNameCol).trim();
                double logFC = parseDoubleSafe(parts.get(mapping.logFCCol));
                double pVal = parseDoubleSafe(parts.get(mapping.pValCol));
                double pAdj = mapping.pValAdjCol >= 0 && mapping.pValAdjCol < parts.size() ?
                        parseDoubleSafe(parts.get(mapping.pValAdjCol)) : pVal;
                genes.add(new Gene(geneName, logFC, pVal, pAdj));
            } catch (Exception ignored) {
                // Skip malformed lines
            }
        }
        
        calculateAxisRanges();
        drawPlot();
    } catch (IOException ex) {
        new Alert(Alert.AlertType.ERROR, "Error reading file: " + ex.getMessage()).show();
    }
}

// Detect the delimiter used in the CSV file
private char detectDelimiter(String line) {
    // Count occurrences of common delimiters
    int commaCount = line.length() - line.replace(",", "").length();
    int tabCount = line.length() - line.replace("\t", "").length();
    int semicolonCount = line.length() - line.replace(";", "").length();
    
    // Return the most common delimiter
    if (tabCount > commaCount && tabCount > semicolonCount) return '\t';
    if (semicolonCount > commaCount && semicolonCount > tabCount) return ';';
    return ','; // default to comma
}

// Improved CSV line parser that handles quoted fields and escaped characters
private List<String> parseCSVLine(String line, char delimiter) {
    List<String> values = new ArrayList<>();
    StringBuffer sb = new StringBuffer();
    boolean inQuotes = false;
    boolean escapeNext = false;
    
    for (char c : line.toCharArray()) {
        if (escapeNext) {
            sb.append(c);
            escapeNext = false;
            continue;
        }
        
        if (c == '\\') {
            escapeNext = true;
            continue;
        }
        
        if (c == '"') {
            inQuotes = !inQuotes;
            continue;
        }
        
        if (c == delimiter && !inQuotes) {
            values.add(sb.toString().trim());
            sb = new StringBuffer();
            continue;
        }
        
        sb.append(c);
    }
    values.add(sb.toString().trim());
    
    // Remove surrounding quotes if they exist
    for (int i = 0; i < values.size(); i++) {
        String val = values.get(i);
        if (val.startsWith("\"") && val.endsWith("\"")) {
            values.set(i, val.substring(1, val.length() - 1));
        }
    }
    
    return values;
}

// Helper method to auto-select likely columns based on common naming patterns
private void autoSelectColumns(List<String> columns, ComboBox<String> geneNameCombo, 
                             ComboBox<String> logFCCombo, ComboBox<String> pValCombo, 
                             ComboBox<String> pAdjCombo) {
    for (int i = 0; i < columns.size(); i++) {
        String col = columns.get(i).toLowerCase();
        if ((col.contains("gene") || col.contains("symbol") || col.contains("name")) 
                && geneNameCombo.getSelectionModel().isEmpty()) {
            geneNameCombo.getSelectionModel().select(i);
        } 
        else if ((col.contains("logfc") || col.contains("fold") || col.contains("lfc") 
                || col.contains("log2fold") || col.contains("log2fc")) 
                && logFCCombo.getSelectionModel().isEmpty()) {
            logFCCombo.getSelectionModel().select(i);
        } 
        else if ((col.contains("p_val") || col.contains("pvalue") || col.contains("p.value") 
                || col.contains("p-val")) && !col.contains("adj") 
                && pValCombo.getSelectionModel().isEmpty()) {
            pValCombo.getSelectionModel().select(i);
        } 
        else if ((col.contains("p_val_adj") || col.contains("p.adj") || col.contains("fdr") 
                || col.contains("padj") || col.contains("qvalue")) 
                && pAdjCombo.getSelectionModel().isEmpty()) {
            pAdjCombo.getSelectionModel().select(i);
        }
    }
    
    // Default to first column if no selection was made
    if (geneNameCombo.getSelectionModel().isEmpty() && !geneNameCombo.getItems().isEmpty()) {
        geneNameCombo.getSelectionModel().select(0);
    }
    if (logFCCombo.getSelectionModel().isEmpty() && !logFCCombo.getItems().isEmpty()) {
        logFCCombo.getSelectionModel().select(0);
    }
    if (pValCombo.getSelectionModel().isEmpty() && !pValCombo.getItems().isEmpty()) {
        pValCombo.getSelectionModel().select(0);
    }
    if (pAdjCombo.getSelectionModel().isEmpty() && !pAdjCombo.getItems().isEmpty()) {
        pAdjCombo.getSelectionModel().select(0);
    }
}

// Safer double parsing method
// Safer double parsing that respects commas as decimal separators and keeps exponents.
private double parseDoubleSafe(String value) {
    if (value == null) return 0.0;
    String s = value.trim();

    if (s.isEmpty()) return 0.0;

    // Normalize thousands & decimal separators:
    //   - If both '.' and ',' appear, assume '.' is thousands and ',' is decimal (e.g., "1.234,56" -> "1234.56").
    //   - If only ',' appears and not '.', assume ',' is decimal (e.g., "0,001" -> "0.001").
    if (s.contains(",") && s.contains(".")) {
        // remove thousand separators '.' only when they are thousand-grouping
        s = s.replace(".", "");
        s = s.replace(",", ".");
    } else if (s.contains(",") && !s.contains(".")) {
        s = s.replace(",", ".");
    }

    // Keep digits, sign, dot and exponent letters only; strip spaces and any stray symbols
    s = s.replaceAll("[^0-9eE+\\-\\.]", "");

    if (s.isEmpty() || s.equals("+") || s.equals("-") || s.equals(".")) return 0.0;

    try {
        double d = Double.parseDouble(s);
        // Clamp impossible p-values if we know it's a p-like field (caller decides context)
        if (Double.isNaN(d) || Double.isInfinite(d)) return 0.0;
        return d;
    } catch (NumberFormatException ex) {
        return 0.0;
    }
}


private void showLabelSettingsDialog(Stage stage) {
    Dialog<LabelSettings> dialog = new Dialog<>();
    dialog.setTitle("Set Plot Labels");
    dialog.setHeaderText("Configure plot title and axis labels");
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(20, 150, 10, 10));

    TextField titleField = new TextField(plotTitle);
    TextField xLabelField = new TextField(xLabel);
    TextField yLabelField = new TextField(yLabel);

    grid.add(new Label("Plot Title:"), 0, 0);
    grid.add(titleField, 1, 0);
    grid.add(new Label("X-Axis Label:"), 0, 1);
    grid.add(xLabelField, 1, 1);
    grid.add(new Label("Y-Axis Label:"), 0, 2);
    grid.add(yLabelField, 1, 2);

    dialog.getDialogPane().setContent(grid);

    dialog.setResultConverter(button -> {
        if (button == ButtonType.OK) {
            return new LabelSettings(
                titleField.getText(),
                xLabelField.getText(),
                yLabelField.getText()
            );
        }
        return null;
    });

    Optional<LabelSettings> result = dialog.showAndWait();
    result.ifPresent(settings -> {
        plotTitle = settings.title;
        xLabel = settings.xLabel;
        yLabel = settings.yLabel;
        drawPlot(); // Redraw with new labels
    });
}

    /**
     * Load a list of targeted genes from a text file where each line contains one gene name.
     */
private void loadTargetGenes(Stage stage) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Open Target Genes File");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Gene List Files", "*.txt", "*.csv"));
    File file = chooser.showOpenDialog(stage);
    if (file == null) return;
    targetedGenes.clear();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
        String line;
        while ((line = br.readLine()) != null) {
            String[] tokens = line.trim().split("[ ,\t\n\r]+");
            for (String token : tokens) {
                String gene = token.trim();
                if (!gene.isEmpty()) {
                    targetedGenes.add(gene);
                }
            }
        }
        new Alert(Alert.AlertType.INFORMATION, "Loaded " + targetedGenes.size() + " target genes").show();
        updateTargetGeneListView();
        updateAnnotations();  // Add this line
        drawPlot();  // Ensure the plot is redrawn
    } catch (IOException ex) {
        new Alert(Alert.AlertType.ERROR, "Error reading file: " + ex.getMessage()).show();
    }
}



/**
 * Prompt the user to enter targeted gene names separated by commas or whitespace.
 */
private void showTargetGenesDialog(Stage stage) {
    // Prepopulate the input dialog with the existing target genes, separated by new lines
    String existing = String.join("\n", targetedGenes);
    TextInputDialog dialog = new TextInputDialog(existing);
    dialog.setTitle("Enter Target Genes");
    dialog.setHeaderText("Enter gene names, separated by commas or new lines\nExisting genes will be preserved unless removed");
    dialog.setContentText("Genes:");

    // Use a TextArea instead of TextField for better multiline input
    dialog.getEditor().setPrefHeight(200);

    Optional<String> res = dialog.showAndWait();
    if (!res.isPresent()) return;

    // Get the new genes from the input
    String genesText = res.get();
    // Split input by commas or any whitespace (spaces, tabs, newlines)
    String[] tokens = genesText.split("[ ,\\t\\n\\r]+");

    // Create a temporary set to hold the new genes
    Set<String> newGenes = new HashSet<>();
    for (String g : tokens) {
        String gene = g.trim();
        if (!gene.isEmpty()) {
            newGenes.add(gene);
        }
    }

    // Merge with existing genes (preserving existing ones)
    targetedGenes.addAll(newGenes);

    // Update UI lists and redraw plot
    updateTargetGeneListView();
    drawPlot();

    new Alert(Alert.AlertType.INFORMATION,
        "Added " + newGenes.size() + " genes. Total target genes: " + targetedGenes.size()).show();
}

/**
 * Show the target gene selection dialog with checkboxes for display control.
 */
private void showLabelDialog(Stage stage) {
    Dialog<Void> dialog = new Dialog<>();
    dialog.setTitle("Select Target Genes to Display");
    dialog.initOwner(stage);

    VBox vbox = new VBox(5);
    vbox.setPadding(new Insets(10));

    for (String gene : targetedGenes) {
        BooleanProperty selected = targetGeneSelections.computeIfAbsent(gene, g -> new SimpleBooleanProperty(true));
        CheckBox checkBox = new CheckBox(gene);
        checkBox.setSelected(selected.get());

        checkBox.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            selected.set(isNowSelected);
            drawPlot();
        });

        vbox.getChildren().add(checkBox);
    }

    ScrollPane scrollPane = new ScrollPane(vbox);
    scrollPane.setFitToWidth(true);
    scrollPane.setPrefViewportHeight(300);

    dialog.getDialogPane().setContent(scrollPane);
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    dialog.showAndWait();
}

        /**
     * Show the display options dialog which allows customizing sizes and widths.
     */
    private void showDisplayOptionsDialog(Stage stage) {
    Dialog<DisplaySettings> dialog = new Dialog<>();
    dialog.setTitle("Display Options");
    dialog.setHeaderText("Adjust display parameters");
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(20, 150, 10, 10));
    
    TextField edgeField = new TextField(String.valueOf(edgeWidth));
    TextField thresholdField = new TextField(String.valueOf(thresholdLineWidth));
    TextField dotField = new TextField(String.valueOf(dotSize));
    TextField widthField = new TextField(String.valueOf((int) canvasWidth));
    TextField heightField = new TextField(String.valueOf((int) canvasHeight));
    TextField fontSizeField = new TextField(String.valueOf((int) fontSize));
    TextField opacityField = new TextField(String.valueOf(dotOpacity));
    TextField xLabelDistanceField = new TextField(String.valueOf(xLabelDistance));
    TextField yLabelDistanceField = new TextField(String.valueOf(yLabelDistance));
    
    // Dot outline controls
    CheckBox outlineCheck = new CheckBox("Show Dot Outline");
    outlineCheck.setSelected(showDotOutline);
    ColorPicker outlineColorPicker = new ColorPicker(dotOutlineColor);
    outlineColorPicker.setDisable(!showDotOutline);
    TextField outlineWidthField = new TextField(String.valueOf(dotOutlineWidth));
    outlineWidthField.setDisable(!showDotOutline);
    
    // Enable/disable outline controls based on checkbox
    outlineCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
        outlineColorPicker.setDisable(!newVal);
        outlineWidthField.setDisable(!newVal);
    });
    
    // Hover info control
    CheckBox hoverCheck = new CheckBox("Show Gene Info on Hover");
    hoverCheck.setSelected(showHoverInfo);
    
    int row = 0;
    grid.add(new Label("Edge Width:"), 0, row);
    grid.add(edgeField, 1, row++);
    grid.add(new Label("Threshold Line Width:"), 0, row);
    grid.add(thresholdField, 1, row++);
    grid.add(new Label("Dot Size:"), 0, row);
    grid.add(dotField, 1, row++);
    grid.add(new Label("Canvas Width:"), 0, row);
    grid.add(widthField, 1, row++);
    grid.add(new Label("Canvas Height:"), 0, row);
    grid.add(heightField, 1, row++);
    grid.add(new Label("Font Size:"), 0, row);
    grid.add(fontSizeField, 1, row++);
    grid.add(new Label("Dot Opacity (0.1-1.0):"), 0, row);
    grid.add(opacityField, 1, row++);
    grid.add(new Label("X-Label Distance:"), 0, row);
    grid.add(xLabelDistanceField, 1, row++);
    grid.add(new Label("Y-Label Distance:"), 0, row);
    grid.add(yLabelDistanceField, 1, row++);
    grid.add(new Label("Dot Outline:"), 0, row);
    grid.add(outlineCheck, 1, row++);
    grid.add(new Label("Outline Color:"), 0, row);
    grid.add(outlineColorPicker, 1, row++);
    grid.add(new Label("Outline Width:"), 0, row);
    grid.add(outlineWidthField, 1, row++);
    grid.add(new Label("Hover Info:"), 0, row);
    grid.add(hoverCheck, 1, row++);
    
    dialog.getDialogPane().setContent(grid);
    
   dialog.setResultConverter(btn -> {
    if (btn == ButtonType.OK) {
        try {
            double ew = Double.parseDouble(edgeField.getText());
            double tw = Double.parseDouble(thresholdField.getText());
            double ds = Double.parseDouble(dotField.getText());
            double w = Double.parseDouble(widthField.getText());
            double h = Double.parseDouble(heightField.getText());
            double fs = Double.parseDouble(fontSizeField.getText());
            double opacity = Math.max(0.1, Math.min(1.0, Double.parseDouble(opacityField.getText())));
            boolean showOutline = outlineCheck.isSelected();
            Color outlineColor = outlineColorPicker.getValue();
            double outlineWidth = Double.parseDouble(outlineWidthField.getText());
            double xLabelDist = Double.parseDouble(xLabelDistanceField.getText());
            double yLabelDist = Double.parseDouble(yLabelDistanceField.getText());
            boolean showHover = hoverCheck.isSelected();
            
            return new DisplaySettings(ew, tw, ds, w, h, fs, opacity, 
                                    showOutline, outlineColor, outlineWidth,
                                    xLabelDist, yLabelDist, showHover);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    return null;
});
    
    Optional<DisplaySettings> res = dialog.showAndWait();
    if (res.isPresent()) {
        DisplaySettings d = res.get();
        edgeWidth = d.edgeWidth;
        thresholdLineWidth = d.thresholdLineWidth;
        dotSize = d.dotSize;
        canvasWidth = d.canvasWidth;
        canvasHeight = d.canvasHeight;
        fontSize = d.fontSize;
        dotOpacity = d.dotOpacity;
        showDotOutline = d.showDotOutline;
        dotOutlineColor = d.dotOutlineColor;
        dotOutlineWidth = d.dotOutlineWidth;
        xLabelDistance = d.xLabelDistance;
        yLabelDistance = d.yLabelDistance;
        showHoverInfo = d.showHoverInfo;
        
        // Update plot pane dimensions
        plotPane.setPrefSize(canvasWidth, canvasHeight);
        plotPane.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        plotPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        
        // Update gene info font
        geneInfo.setFont(Font.font("Arial", fontSize));
        
        // Redraw plot with new settings
        drawPlot();
    }
}

/**
 * Refresh the target gene list after confirming with the user.
 */
private void refreshTargetGenes(Stage stage) {
    // Ask first
    if (!confirmRemoveTargetAnnotations(stage)) return;

    // Snapshot current targets so we can strip their annotations
    Set<String> previousTargets = new HashSet<>(targetedGenes);

    // Clear target list and selections
    targetedGenes.clear();
    targetGeneSelections.clear();
    updateTargetGeneListView();

    // Remove annotations that originated from targets
    for (Gene g : genes) {
        if (previousTargets.contains(g.name)) {
            // delete label + connector, and unpin
            g.labelNode = null;
            g.lineNode  = null;
            g.userPosition = false;
        }
    }

    drawPlot();
    new Alert(Alert.AlertType.INFORMATION, "Target annotations removed.").show();
}

    /**
     * Show export dialog and save the plot as a PNG with optional transparency
     * and DPI scaling.
     */
    private void showExportDialog(Stage stage) {
        Dialog<ExportSettings> dialog = new Dialog<>();
        dialog.setTitle("Export Image");
        dialog.setHeaderText("Select export options");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        ComboBox<Integer> dpiCombo = new ComboBox<>();
        dpiCombo.getItems().addAll(300, 600, 1200);
        dpiCombo.getSelectionModel().select(Integer.valueOf(300));
        CheckBox transparentCheck = new CheckBox("Transparent background");
        CheckBox pdfCheck = new CheckBox("Export as PDF");
        grid.add(new Label("DPI:"), 0, 0);
        grid.add(dpiCombo, 1, 0);
        grid.add(transparentCheck, 0, 1, 2, 1);
        grid.add(pdfCheck, 0, 2, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Integer dpi = dpiCombo.getValue();
                boolean transparent = transparentCheck.isSelected();
                boolean pdf = pdfCheck.isSelected();
                return new ExportSettings(dpi == null ? 300 : dpi, transparent, pdf);
            }
            return null;
        });
        Optional<ExportSettings> res = dialog.showAndWait();
        if (!res.isPresent()) return;
        ExportSettings settings = res.get();
        if (settings.pdf) {
            exportAsPDF(stage);
        } else {
            // Choose file location
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Image");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Files", "*.png"));
            File file = chooser.showSaveDialog(stage);
            if (file == null) return;
            exportAsPNG(file, settings.dpi, settings.transparent);
        }
    }

    /**
     * Compute reasonable axis ranges based on the data currently loaded.
     */
/**
 * Compute reasonable axis ranges based on the data currently loaded.
 * Adaptive y-lower bound to avoid overlap (when many p≈1) without big gaps.
 * Outlier-resistant y-upper bound to avoid “flat” look from one extreme point.
 */
private void calculateAxisRanges() {
    if (genes.isEmpty()) {
        xMin = -4; xMax = 4;
        yMin = -0.15;   // tiny default gap
        yMax = 10;
        return;
    }

    // ----- X axis: symmetric around 0 (same behavior) -----
    double minLogFC = genes.stream().mapToDouble(g -> g.logFC).min().orElse(-4);
    double maxLogFC = genes.stream().mapToDouble(g -> g.logFC).max().orElse(4);
    double maxAbs = Math.max(Math.abs(minLogFC), Math.abs(maxLogFC));
    double xPad = Math.max(0.5, maxAbs * 0.1);
    xMax = maxAbs + xPad;
    xMin = -xMax;

    // ----- Y data: robust -log10(p) values with clamping -----
    double[] negLogP = genes.stream()
        .mapToDouble(g -> {
            double p = useAdjustedP ? g.pAdj : g.pValue;
            if (p <= 0) p = 1e-300;
            if (p > 1)  p = 1.0;
            return -Math.log10(p);
        })
        .filter(d -> !Double.isNaN(d) && !Double.isInfinite(d))
        .toArray();

    if (negLogP.length == 0) {
        yMin = -0.15;
        yMax = 10;
        return;
    }

    Arrays.sort(negLogP);
    double minNegLogP = negLogP[0];
    double maxNegLogP = negLogP[negLogP.length - 1];

    // Outlier-resistant top: use ~99th percentile, then add a small headroom
    double p99 = percentile(negLogP, 0.99);
    yMax = Math.max(3.0, Math.max(p99, maxNegLogP * 0.95) * 1.06);

    // ===== Adaptive baseline padding (auto anti-overlap, no big gaps) =====

    // Fraction of points near the baseline (e.g., p > ~0.71 → -log10(p) < 0.15)
    double nearZeroCut = 0.15;
    int nearZeroCount = 0;
    for (double v : negLogP) if (v < nearZeroCut) nearZeroCount++;
    double fNearZero = (double) nearZeroCount / negLogP.length; // 0..1

    // Pixel-aware minimal padding: dot radius + a few px, converted to Y units
    double plotHeightPx = Math.max(1.0, (canvasHeight - marginTop - marginBottom));
    double desiredPadPx = (dotSize / 2.0) + 6.0; // dot radius + breathing room
    double ySpanGuess = Math.max(1.0, yMax - Math.max(0.0, minNegLogP));
    double pxToUnits = ySpanGuess / plotHeightPx;
    double padUnitsFromPixels = desiredPadPx * pxToUnits;

    // Spread-aware padding: scales with spread and pile-up near baseline
    double dataSpread = Math.max(0.5, (maxNegLogP - minNegLogP));
    double padUnitsFromSpread = 0.04 * dataSpread * fNearZero;

    // Final adaptive pad (tighter clamps)
    double adaptivePad = Math.max(padUnitsFromPixels, padUnitsFromSpread);
    adaptivePad = Math.min(Math.max(adaptivePad, 0.08), 0.80);

    // Tighter floor: only go notably negative if many points pile up at 0
    // few near zero → ~ -0.08 ; many → down to ~ -0.45 (at most)
    double adaptiveFloor = -(0.08 + 0.37 * fNearZero);
    if (adaptiveFloor < -0.45) adaptiveFloor = -0.45;

    // Final yMin: small gap under lowest dot, but never plunge too far
    yMin = Math.min(minNegLogP - adaptivePad, adaptiveFloor);

    // Safety: keep a minimal vertical span
    if (yMax - yMin < 1.0) yMin = yMax - 1.0;
}

    
    /**
     * Update the targeted gene list view to reflect the current set of
     * targeted genes.  This method populates the list view items and
     * ensures that each gene has an associated BooleanProperty to track
     * whether it is enabled for annotation.  It preserves any existing
     * selection states and sorts genes alphabetically for ease of use.
     */
    private void updateTargetGeneListView() {
        if (targetedGeneListView == null) return;
        // Create selection properties for any new genes
        for (String gene : targetedGenes) {
            targetGeneSelections.computeIfAbsent(gene, k -> new SimpleBooleanProperty(true));
        }
        // Remove properties for genes no longer targeted
        targetGeneSelections.keySet().removeIf(g -> !targetedGenes.contains(g));
        // Create sorted list for display
        javafx.collections.ObservableList<String> items = javafx.collections.FXCollections.observableArrayList(targetedGenes);
        FXCollections.sort(items);
        targetedGeneListView.setItems(items);
    }

    /**
     * Convert a logFC value to an x coordinate within the plot pane.
     */
    private double valueToX(double value) {
        double plotWidth = canvasWidth - marginLeft - marginRight;
        return marginLeft + (value - xMin) / (xMax - xMin) * plotWidth;
    }

    /**
     * Convert a -log10(p) value to a y coordinate within the plot pane.
     */
    private double valueToY(double value) {
        double plotHeight = canvasHeight - marginTop - marginBottom;
        // Higher values plotted up (lower y coordinate)
        return canvasHeight - marginBottom - (value - yMin) / (yMax - yMin) * plotHeight;
    }
       
// FIXED VERSION - The issue is in the drawPlot() method around line 1100-1200
// Missing closing brace for the for-loop that draws gene dots

private void drawPlot() {
    calculateAxisRanges();
    plotPane.getChildren().clear();

    // ---- Title (centered, not draggable)
    Text titleNode = new Text(plotTitle);
    titleNode.setFont(Font.font("Arial", fontSize + 2));
    double titleWidth = computeTextWidth(titleNode);
    double plotRegionWidth = canvasWidth - marginLeft - marginRight;
    double titleBaseX = marginLeft + plotRegionWidth / 2 - titleWidth / 2;
    double titleBaseY = marginTop - 20;
    titleNode.setX(titleBaseX);
    titleNode.setY(titleBaseY);
    plotPane.getChildren().add(titleNode);

    // ---- Axes
    double plotWidth  = canvasWidth  - marginLeft - marginRight;
    double plotHeight = canvasHeight - marginTop   - marginBottom;

    Line xAxis = new Line(marginLeft, canvasHeight - marginBottom, marginLeft + plotWidth, canvasHeight - marginBottom);
    Line yAxis = new Line(marginLeft, canvasHeight - marginBottom, marginLeft,               marginTop);
    xAxis.setStroke(Color.BLACK);
    xAxis.setStrokeWidth(1.5);
    yAxis.setStroke(Color.BLACK);
    yAxis.setStrokeWidth(1.5);
    plotPane.getChildren().addAll(xAxis, yAxis);

    // ---- Optional grid
    if (showGrid) {
        double xRange = xMax - xMin;
        double xInterval = calculateNiceInterval(xRange);
        for (double xVal = Math.ceil(xMin / xInterval) * xInterval; xVal <= xMax; xVal += xInterval) {
            double x = valueToX(xVal);
            Line vGrid = new Line(x, marginTop, x, canvasHeight - marginBottom);
            vGrid.setStroke(Color.LIGHTGRAY);
            vGrid.setOpacity(0.3);
            plotPane.getChildren().add(vGrid);
        }

        double yRange = yMax - yMin;
        double yInterval = calculateNiceInterval(yRange);
        for (double yVal = Math.ceil(yMin / yInterval) * yInterval; yVal <= yMax; yVal += yInterval) {
            double y = valueToY(yVal);
            Line hGrid = new Line(marginLeft, y, marginLeft + plotWidth, y);
            hGrid.setStroke(Color.LIGHTGRAY);
            hGrid.setOpacity(0.3);
            plotPane.getChildren().add(hGrid);
        }
    }

    // ---- Axis labels
    Text xLabelNode = new Text(xLabel);
    xLabelNode.setFont(Font.font("Arial", fontSize));
    double xLabelWidth = computeTextWidth(xLabelNode);
    double xLabelBaseX = marginLeft + plotWidth / 2 - xLabelWidth / 2;
    double xLabelBaseY = canvasHeight - marginBottom + xLabelDistance;
    xLabelNode.setX(xLabelBaseX);
    xLabelNode.setY(xLabelBaseY);
    plotPane.getChildren().add(xLabelNode);

    Text yLabelNode = new Text(yLabel);
    yLabelNode.setFont(Font.font("Arial", fontSize));
    double plotHeightActual = canvasHeight - marginTop - marginBottom;
    double baseX = marginLeft - yLabelDistance;
    double baseY = marginTop + plotHeightActual / 2;
    double labelWidth = computeTextWidth(yLabelNode);
    double transY = baseY + labelWidth / 2.0;
    Group yLabelGroup = new Group(yLabelNode);
    yLabelGroup.getTransforms().add(new javafx.scene.transform.Translate(baseX, transY));
    yLabelGroup.getTransforms().add(new javafx.scene.transform.Rotate(-90));
    plotPane.getChildren().add(yLabelGroup);

    // ---- Ticks
    drawXAxisTicks(plotPane);
    drawYAxisTicks(plotPane);

    // ---- Threshold lines
    if (logFCThreshold > xMin && -logFCThreshold < xMax) {
        Line leftThreshold  = new Line(valueToX(-logFCThreshold), marginTop, valueToX(-logFCThreshold), canvasHeight - marginBottom);
        Line rightThreshold = new Line(valueToX( logFCThreshold), marginTop, valueToX( logFCThreshold), canvasHeight - marginBottom);
        leftThreshold.getStrokeDashArray().setAll(5.0, 5.0);
        rightThreshold.getStrokeDashArray().setAll(5.0, 5.0);
        leftThreshold.setStrokeWidth(thresholdLineWidth);
        rightThreshold.setStrokeWidth(thresholdLineWidth);
        plotPane.getChildren().addAll(leftThreshold, rightThreshold);
    }

    double pValY = valueToY(-Math.log10(pValueThreshold));
    if (pValY >= marginTop && pValY <= canvasHeight - marginBottom) {
        Line pThreshold = new Line(marginLeft, pValY, marginLeft + plotWidth, pValY);
        pThreshold.getStrokeDashArray().setAll(5.0, 5.0);
        pThreshold.setStrokeWidth(thresholdLineWidth);
        plotPane.getChildren().add(pThreshold);
    }

    if (showZeroLine) {
        double zeroX = valueToX(0.0);
        Line zeroLine = new Line(zeroX, marginTop, zeroX, canvasHeight - marginBottom);
        zeroLine.getStrokeDashArray().setAll(5.0, 5.0);
        zeroLine.setStrokeWidth(thresholdLineWidth);
        zeroLine.setStroke(Color.BLACK);
        plotPane.getChildren().add(zeroLine);
    }

    // ---- Which genes to annotate
    List<Gene> significantGenes = new ArrayList<>();
    for (Gene g : genes) {
        double pValToUse = useAdjustedP ? g.pAdj : g.pValue;
        if (Math.abs(g.logFC) >= logFCThreshold && pValToUse <= pValueThreshold) {
            significantGenes.add(g);
        }
    }

    Set<Gene> annotateGenes = new HashSet<>();

    // (A) Targets (if enabled)
    if (showTargets) {
        for (Gene g : genes) {
            if (targetedGenes.contains(g.name)) {
                BooleanProperty prop = targetGeneSelections.get(g.name);
                if (prop == null || prop.get()) {
                    annotateGenes.add(g);
                }
            }
        }
    }

    // (B) Top-N (if enabled)
    if (annotate && !significantGenes.isEmpty()) {
        significantGenes.sort((a, b) -> {
            double p1 = useAdjustedP ? a.pAdj : a.pValue;
            double p2 = useAdjustedP ? b.pAdj : b.pValue;
            int cmp = Double.compare(p1, p2);
            if (cmp == 0) return Double.compare(Math.abs(b.logFC), Math.abs(a.logFC));
            return cmp;
        });

        int upCount = 0, downCount = 0;
        for (Gene g : significantGenes) {
            if (g.logFC > 0 && upCount < topNUp) {
                annotateGenes.add(g); upCount++;
            } else if (g.logFC < 0 && downCount < topNDown) {
                annotateGenes.add(g); downCount++;
            }
            if (upCount >= topNUp && downCount >= topNDown) break;
        }
    }

    // ---- Compute dot positions (clamped p)
    for (Gene g : genes) {
        double pValToUse = useAdjustedP ? g.pAdj : g.pValue;
        if (pValToUse <= 0) pValToUse = 1e-300;
        if (pValToUse > 1)  pValToUse = 1.0;
        g.dotX = valueToX(g.logFC);
        g.dotY = valueToY(-Math.log10(pValToUse));
    }

    // Initial label placement for any new labels (keeps userPosition pins intact)
    assignInitialLabelPositions(annotateGenes);

    // ---- Precompute scales for styles
    double maxAbsLogFC = genes.stream().mapToDouble(g -> Math.abs(g.logFC)).max().orElse(1.0);
    double maxNegLogP = genes.stream()
        .mapToDouble(g -> {
            double p = useAdjustedP ? g.pAdj : g.pValue;
            if (p <= 0) p = 1e-300;
            if (p > 1)  p = 1.0;
            return -Math.log10(p);
        })
        .max().orElse(1.0);
    double thresholdNegLogP = -Math.log10(pValueThreshold);

    // ---- Draw all dots
    for (Gene g : genes) {
        double pValToUse = useAdjustedP ? g.pAdj : g.pValue;
        if (pValToUse <= 0) pValToUse = 1e-300;
        if (pValToUse > 1)  pValToUse = 1.0;

        Circle circle = new Circle(g.dotX, g.dotY, dotSize / 2);
        Color geneColor = colNS;
        boolean isSig = Math.abs(g.logFC) >= logFCThreshold && pValToUse <= pValueThreshold;

        if (isSig) {
            switch (colorStyle) {
                case "MAGMA1": {
                    double t = (maxAbsLogFC - logFCThreshold <= 0) ? 1.0
                            : (Math.abs(g.logFC) - logFCThreshold) / (maxAbsLogFC - logFCThreshold);
                    t = Math.max(0, Math.min(1, t));
                    geneColor = magma(t);
                    break;
                }
                case "MAGMA2": {
                    double logScore = -Math.log10(pValToUse);
                    double t = (maxNegLogP - thresholdNegLogP <= 0) ? 1.0
                            : (logScore - thresholdNegLogP) / (maxNegLogP - thresholdNegLogP);
                    t = Math.max(0, Math.min(1, t));
                    geneColor = magma(t);
                    break;
                }
                case "MAGMA3": {
                    double t1 = (maxAbsLogFC - logFCThreshold <= 0) ? 1.0
                            : (Math.abs(g.logFC) - logFCThreshold) / (maxAbsLogFC - logFCThreshold);
                    t1 = Math.max(0, Math.min(1, t1));
                    double logScore = -Math.log10(pValToUse);
                    double t2 = (maxNegLogP - thresholdNegLogP <= 0) ? 1.0
                            : (logScore - thresholdNegLogP) / (maxNegLogP - thresholdNegLogP);
                    t2 = Math.max(0, Math.min(1, t2));
                    geneColor = magma((t1 + t2) / 2.0);
                    break;
                }
                case "MAGMA4": {
                    double t = (maxAbsLogFC - logFCThreshold <= 0) ? 1.0
                            : (Math.abs(g.logFC) - logFCThreshold) / (maxAbsLogFC - logFCThreshold);
                    t = Math.max(0, Math.min(1, t));
                    geneColor = viridis(t);
                    break;
                }
                case "MAGMA5": {
                    double logScore = -Math.log10(pValToUse);
                    double t = (maxNegLogP - thresholdNegLogP <= 0) ? 1.0
                            : (logScore - thresholdNegLogP) / (maxNegLogP - thresholdNegLogP);
                    t = Math.max(0, Math.min(1, t));
                    geneColor = viridis(t);
                    break;
                }
                case "MAGMA6": {
                    double t1 = (maxAbsLogFC - logFCThreshold <= 0) ? 1.0
                            : (Math.abs(g.logFC) - logFCThreshold) / (maxAbsLogFC - logFCThreshold);
                    t1 = Math.max(0, Math.min(1, t1));
                    double logScore = -Math.log10(pValToUse);
                    double t2 = (maxNegLogP - thresholdNegLogP <= 0) ? 1.0
                            : (logScore - thresholdNegLogP) / (maxNegLogP - thresholdNegLogP);
                    t2 = Math.max(0, Math.min(1, t2));
                    geneColor = viridis((t1 + t2) / 2.0);
                    break;
                }
                case "MAGMA_STD1": {
                    double t = (maxAbsLogFC - logFCThreshold <= 0) ? 1.0
                            : (Math.abs(g.logFC) - logFCThreshold) / (maxAbsLogFC - logFCThreshold);
                    t = Math.max(0, Math.min(1, t));
                    geneColor = (g.logFC > 0) ? degradeRed(t) : degradeBlue(t);
                    break;
                }
                case "MAGMA_STD2": {
                    double logScore = -Math.log10(pValToUse);
                    double t = (maxNegLogP - thresholdNegLogP <= 0) ? 1.0
                            : (logScore - thresholdNegLogP) / (maxNegLogP - thresholdNegLogP);
                    t = Math.max(0, Math.min(1, t));
                    geneColor = (g.logFC > 0) ? degradeRed(t) : degradeBlue(t);
                    break;
                }
                case "MAGMA_STD3": {
                    double t1 = (maxAbsLogFC - logFCThreshold <= 0) ? 1.0
                            : (Math.abs(g.logFC) - logFCThreshold) / (maxAbsLogFC - logFCThreshold);
                    t1 = Math.max(0, Math.min(1, t1));
                    double logScore = -Math.log10(pValToUse);
                    double t2 = (maxNegLogP - thresholdNegLogP <= 0) ? 1.0
                            : (logScore - thresholdNegLogP) / (maxNegLogP - thresholdNegLogP);
                    t2 = Math.max(0, Math.min(1, t2));
                    geneColor = (g.logFC > 0) ? degradeRed((t1 + t2) / 2.0) : degradeBlue((t1 + t2) / 2.0);
                    break;
                }
                case "CLASSIC":
                default:
                    geneColor = (g.logFC > 0) ? colUp : colDown;
                    break;
            }
        }

        circle.setFill(geneColor);
        circle.setOpacity(dotOpacity);

        if (showDotOutline) {
            circle.setStroke(dotOutlineColor);
            circle.setStrokeWidth(dotOutlineWidth);
        } else {
            circle.setStroke(null);
        }

        if (showHoverInfo) {
            javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(
                String.format("%s\nlogFC: %.3f\np-value: %.2e\nadj.p: %.2e",
                              g.name, g.logFC, g.pValue, g.pAdj));
            tooltip.setShowDelay(javafx.util.Duration.millis(200));
            tooltip.setHideDelay(javafx.util.Duration.millis(100));
            javafx.scene.control.Tooltip.install(circle, tooltip);

            circle.setOnMouseEntered(ev ->
                geneInfo.setText(String.format("%s: logFC=%.3f, p=%.2e, p.adj=%.2e",
                                               g.name, g.logFC, g.pValue, g.pAdj)));
            circle.setOnMouseExited(ev -> geneInfo.setText(""));
        } else {
            javafx.scene.control.Tooltip.uninstall(circle, null);
            circle.setOnMouseEntered(null);
            circle.setOnMouseExited(null);
        }

        plotPane.getChildren().add(circle);
        g.dotNode = circle; // keep reference if you use it elsewhere
    }

    // ---- Draw annotations on top
    for (Gene g : genes) {
        if (!annotateGenes.contains(g)) {
            g.labelNode = null;
            g.lineNode  = null;
            continue;
        }

        Text labelNodeTxt = new Text(g.name);
        labelNodeTxt.setFont(Font.font("Arial", fontSize));
        labelNodeTxt.setX(g.labelX);
        labelNodeTxt.setY(g.labelY);
        plotPane.getChildren().add(labelNodeTxt);
        labelNodeTxt.applyCss();

        double textW = labelNodeTxt.getLayoutBounds().getWidth();
        double endX  = (g.logFC < 0) ? g.labelX + textW : g.labelX;

        Line connector = new Line(g.dotX, g.dotY, endX, g.labelY);
        connector.setStroke(Color.BLACK);
        connector.setStrokeWidth(edgeWidth);
        plotPane.getChildren().add(connector);

        g.labelNode = labelNodeTxt;
        g.lineNode  = connector;

        // Drag handlers
        labelNodeTxt.addEventHandler(MouseEvent.MOUSE_PRESSED, ev -> {
            draggedGene = g;
            dragOffsetX = ev.getX() - labelNodeTxt.getX();
            dragOffsetY = ev.getY() - labelNodeTxt.getY();
            ev.consume();
        });
        labelNodeTxt.addEventHandler(MouseEvent.MOUSE_DRAGGED, ev -> {
            if (draggedGene != null) {
                double newX = ev.getX() - dragOffsetX;
                double newY = ev.getY() - dragOffsetY;
                draggedGene.labelX = newX;
                draggedGene.labelY = newY;
                updateAnnotationPosition(draggedGene);
            }
            ev.consume();
        });
        labelNodeTxt.addEventHandler(MouseEvent.MOUSE_RELEASED, ev -> {
            if (draggedGene != null) {
                draggedGene.userPosition = true;
                markDirty();
            }
            draggedGene = null;
            ev.consume();
        });
    }
}
 // END OF drawPlot() method - make sure this closing brace is here

    /**
     * Update a single gene's annotation position and connecting line when the
     * user drags its label.  This modifies only the label and line nodes
     * associated with the gene.
     */
    private void updateAnnotationPosition(Gene gene) {
        if (gene.labelNode == null || gene.lineNode == null) return;
        Text label = gene.labelNode;
        Line line = gene.lineNode;
        label.setX(gene.labelX);
        label.setY(gene.labelY);
        label.applyCss();
        double textWidth = label.getLayoutBounds().getWidth();
        // Determine new connection end based on sign of logFC
        double endX;
        if (gene.logFC < 0) {
            endX = gene.labelX + textWidth;
        } else {
            endX = gene.labelX;
        }
        // Update line end point
        line.setStartX(gene.dotX);
        line.setStartY(gene.dotY);
        line.setEndX(endX);
        line.setEndY(gene.labelY);
    }

    /**
     * Export the current plot pane to a PNG file.  The caller is responsible
     * for providing a valid {@link File} object.  The image is rendered at
     * the specified DPI and with an optional transparent background.
     *
     * @param file the destination file (must not be null)
     * @param dpi the desired resolution in dots per inch
     * @param transparent whether the background should be transparent
     */
    private void exportAsPNG(File file, int dpi, boolean transparent) {
        if (file == null) return;
        // Ensure the file has a .png extension
        String name = file.getName();
        if (!name.toLowerCase().endsWith(".png")) {
            file = new File(file.getParentFile(), name + ".png");
        }
        try {
            // Adjust for plotPane scaling so that the full image is captured even when
            // the user has magnified the plot.  We temporarily reset the node
            // scaling and incorporate the current scale into the snapshot transform.
            double nodeScaleX = plotPane.getScaleX();
            double nodeScaleY = plotPane.getScaleY();
            double exportScaleX = (dpi / 96.0) * nodeScaleX;
            double exportScaleY = (dpi / 96.0) * nodeScaleY;
            // Temporarily reset scale to 1 while snapshotting
            plotPane.setScaleX(1.0);
            plotPane.setScaleY(1.0);
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(transparent ? Color.TRANSPARENT : Color.WHITE);
            params.setTransform(Transform.scale(exportScaleX, exportScaleY));
            int width = (int) Math.round(plotPane.getWidth() * exportScaleX);
            int height = (int) Math.round(plotPane.getHeight() * exportScaleY);
            WritableImage snapshot = new WritableImage(width, height);
            WritableImage fxImage = plotPane.snapshot(params, snapshot);
            // Restore original scaling
            plotPane.setScaleX(nodeScaleX);
            plotPane.setScaleY(nodeScaleY);
            java.awt.image.BufferedImage bufferedImage = SwingFXUtils.fromFXImage(fxImage, null);
            ImageIO.write(bufferedImage, "png", file);
            new Alert(Alert.AlertType.INFORMATION, "Successfully exported to: " + file.getAbsolutePath()).show();
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Error exporting: " + ex.getMessage()).show();
        }
    }

    /**
     * Export the current plot pane to a PDF by invoking the JavaFX printing
     * system.  This opens a print dialog where the user can choose a PDF
     * printer.  The method returns immediately after the print job is
     * submitted.
     *
     * @param stage the current application stage, used to position the print dialog
     */
    private void exportAsPDF(Stage stage) {
    javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
    if (job != null) {
        boolean proceed = job.showPrintDialog(stage);
        if (proceed) {
            boolean success = job.printPage(plotPane);
            if (success) {
                job.endJob();
                new Alert(Alert.AlertType.INFORMATION, "Exported plot to PDF via print dialog.").show();
            }
        }
    }
}

/**
 * Save the current project (data and settings) to a JSON file.
 */
// === DROP-IN: saveProject that matches your loader ===
private void saveProject(Stage stage) {
    // 1) Choose file if we don't have one yet
    if (currentProjectFile == null) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Project");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        chooser.setInitialFileName("project.json");
        File chosen = chooser.showSaveDialog(stage);
        if (chosen == null) return;
        currentProjectFile = chosen;
    }

    // 2) Build JSON EXACTLY as your loadProject() expects
    StringBuilder sb = new StringBuilder(16384);
    sb.append("{\n");

    // --- genes ---
    sb.append("  \"genes\": [\n");
    for (int i = 0; i < genes.size(); i++) {
        Gene g = genes.get(i);
        sb.append("    {")
          .append("\"name\":\"").append(escapeJson(g.name)).append("\",")
          .append("\"logFC\":").append(fmt(g.logFC)).append(",")
          .append("\"pValue\":").append(fmt(g.pValue)).append(",")
          .append("\"pAdj\":").append(fmt(g.pAdj)).append(",")
          .append("\"labelX\":").append(fmt(g.labelX)).append(",")
          .append("\"labelY\":").append(fmt(g.labelY)).append(",")
          .append("\"userPosition\":").append(g.userPosition)
          .append("}");
        if (i < genes.size() - 1) sb.append(",");
        sb.append("\n");
    }
    sb.append("  ],\n");

    // --- targetedGenes (v2: object array with selection flag; loader supports both) ---
    sb.append("  \"targetedGenes\": [\n");
    int k = 0, n = targetedGenes.size();
    for (String tg : targetedGenes) {
        boolean sel = targetGeneSelections.getOrDefault(tg, new SimpleBooleanProperty(true)).get();
        sb.append("    {")
          .append("\"name\":\"").append(escapeJson(tg)).append("\",")
          .append("\"selected\":").append(sel)
          .append("}");
        if (k++ < n - 1) sb.append(",");
        sb.append("\n");
    }
    sb.append("  ],\n");

    // --- settings ---
    sb.append("  \"settings\": {\n")
      .append("    \"logFCThreshold\":").append(fmt(logFCThreshold)).append(",\n")
      .append("    \"pValueThreshold\":").append(fmt(pValueThreshold)).append(",\n")
      .append("    \"useAdjustedP\":").append(useAdjustedP).append(",\n")
      .append("    \"annotate\":").append(annotate).append(",\n")
      .append("    \"topNUp\":").append(topNUp).append(",\n")
      .append("    \"topNDown\":").append(topNDown).append(",\n")
      .append("    \"showZeroLine\":").append(showZeroLine).append(",\n")
      .append("    \"showGrid\":").append(showGrid).append(",\n")
      .append("    \"showTargets\":").append(showTargets).append(",\n")
      .append("    \"edgeWidth\":").append(fmt(edgeWidth)).append(",\n")
      .append("    \"thresholdLineWidth\":").append(fmt(thresholdLineWidth)).append(",\n")
      .append("    \"dotSize\":").append(fmt(dotSize)).append(",\n")
      .append("    \"canvasWidth\":").append(fmt(canvasWidth)).append(",\n")
      .append("    \"canvasHeight\":").append(fmt(canvasHeight)).append(",\n")
      .append("    \"fontSize\":").append(fmt(fontSize)).append(",\n")
      .append("    \"dotOpacity\":").append(fmt(dotOpacity)).append(",\n")
      .append("    \"showDotOutline\":").append(showDotOutline).append(",\n")
      .append("    \"dotOutlineColor\":\"").append(colorToWeb(dotOutlineColor)).append("\",\n")
      .append("    \"dotOutlineWidth\":").append(fmt(dotOutlineWidth)).append(",\n")
      .append("    \"xLabelDistance\":").append(fmt(xLabelDistance)).append(",\n")
      .append("    \"yLabelDistance\":").append(fmt(yLabelDistance)).append(",\n")
      .append("    \"showHoverInfo\":").append(showHoverInfo).append(",\n")
      .append("    \"colUp\":\"").append(colorToWeb(colUp)).append("\",\n")
      .append("    \"colDown\":\"").append(colorToWeb(colDown)).append("\",\n")
      .append("    \"colNS\":\"").append(colorToWeb(colNS)).append("\",\n")
      .append("    \"colorStyle\":\"").append(escapeJson(colorStyle == null ? "CLASSIC" : colorStyle)).append("\",\n")
      .append("    \"title\":\"").append(escapeJson(plotTitle)).append("\",\n")
      .append("    \"xLabel\":\"").append(escapeJson(xLabel)).append("\",\n")
      .append("    \"yLabel\":\"").append(escapeJson(yLabel)).append("\"\n")
      .append("  }\n");

    sb.append("}\n");

    // 3) Atomic write: write to temp then replace target
    File target = currentProjectFile;
    File dir = target.getParentFile();
    File tmp = new File((dir != null ? dir : new File(".")), target.getName() + ".tmp");

    try (OutputStream os = new FileOutputStream(tmp);
         OutputStreamWriter ow = new OutputStreamWriter(os, StandardCharsets.UTF_8);
         BufferedWriter bw = new BufferedWriter(ow)) {
        bw.write(sb.toString());
    } catch (IOException ex) {
        new Alert(Alert.AlertType.ERROR, "Error saving project (write): " + ex.getMessage()).show();
        return;
    }

    // Replace existing
    if (target.exists() && !target.delete()) {
        new Alert(Alert.AlertType.ERROR, "Error saving project: cannot replace existing file.").show();
        return;
    }
    if (!tmp.renameTo(target)) {
        new Alert(Alert.AlertType.ERROR, "Error saving project: cannot move temp file.").show();
        return;
    }

    markClean();
    updateWindowTitle();
    new Alert(Alert.AlertType.INFORMATION,
            "Project saved.\nGenes: " + genes.size() + "\nTargets: " + targetedGenes.size()).show();
}



private String toHex(Color color) {
    return String.format("#%02X%02X%02X",
        (int)(color.getRed() * 255),
        (int)(color.getGreen() * 255),
        (int)(color.getBlue() * 255));
}

// Helper method to escape strings for saving
private String escapeString(String input) {
    return input.replace("\n", "\\n").replace("\r", "\\r").replace(",", "\\,");
}

private String jsonEscape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
}

private String extractBlock(String json, String key, char open, char close) {
    // extracts content inside {...} or [...] following "key":
    Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\" + open + "(.*?)\\" + close,
            Pattern.DOTALL);
    Matcher m = p.matcher(json);
    return m.find() ? m.group(1) : null;
}

private String extractString(String json, String key) {
    Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    Matcher m = p.matcher(json);
    return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : "";
}

private double extractDouble(String json, String key, double defVal) {
    Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([-+Ee0-9\\.]+)");
    Matcher m = p.matcher(json);
    return m.find() ? Double.parseDouble(m.group(1)) : defVal;
}

private boolean extractBool(String json, String key, boolean defVal) {
    Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
    Matcher m = p.matcher(json);
    return m.find() ? Boolean.parseBoolean(m.group(1)) : defVal;
}

    /**
     * Assigns initial positions to the labels of annotated genes in a way that
     * reduces overlap.  For each side (positive and negative logFC), labels
     * are stacked vertically above their corresponding points.  Only genes
     * without previously set label positions are repositioned; user-dragged
     * labels retain their coordinates.
     *
     * @param annotateGenes the set of genes selected for annotation
     */
    private void assignInitialLabelPositions(Set<Gene> annotateGenes) {
        // Separate genes by sign of logFC and collect those that should be positioned.
        // We skip genes that the user has manually positioned (userPosition flag).
        List<Gene> right = new ArrayList<>();
        List<Gene> left = new ArrayList<>();
        for (Gene g : annotateGenes) {
            // Skip genes that have been manually positioned by the user
            if (g.userPosition) continue;
            // Only assign positions if dot coordinates are available
            if (Double.isNaN(g.dotX) || Double.isNaN(g.dotY) || (g.dotX == 0 && g.dotY == 0)) {
                continue;
            }
            if (g.logFC > 0) {
                right.add(g);
            } else {
                left.add(g);
            }
        }
        // Sort each list by vertical position (top to bottom) using dotY
        Comparator<Gene> byDotY = Comparator.comparingDouble(g -> g.dotY);
        right.sort(byDotY);
        left.sort(byDotY);
        // Vertical spacing between labels based on font size
        double spacing = fontSize + 8;
        // Assign default positions for right side: offset to the right and above the dot
        for (Gene g : right) {
            g.labelX = g.dotX + 30;
            g.labelY = g.dotY - 30;
        }
        // Assign default positions for left side: offset to the left and above the dot
        for (Gene g : left) {
            g.labelX = g.dotX - 30;
            g.labelY = g.dotY - 30;
        }
        // Adjust positions on each side to prevent overlap by enforcing minimum spacing
        adjustLabelOverlap(right, spacing);
        adjustLabelOverlap(left, spacing);
    }

    /**
     * Adjusts the vertical positions of a list of genes so that their labels
     * do not overlap.  The list is sorted by current labelY position (top
     * first), and each subsequent label is moved downward if necessary to
     * maintain at least the specified spacing.  This method operates on
     * the given list directly.
     *
     * @param genesList the list of genes to adjust
     * @param spacing the minimum vertical distance between labels
     */
    private void adjustLabelOverlap(List<Gene> genesList, double spacing) {
        if (genesList == null || genesList.size() < 2) return;
        genesList.sort(Comparator.comparingDouble(g -> g.labelY));
        for (int i = 1; i < genesList.size(); i++) {
            Gene prev = genesList.get(i - 1);
            Gene curr = genesList.get(i);
            if (curr.labelY - prev.labelY < spacing) {
                curr.labelY = prev.labelY + spacing;
            }
        }
    }

    /**
     * Draw tick marks and labels on the X axis with nice intervals.  Ticks are
     * drawn using small lines and numbers at appropriate positions on the plot.
     */
    private void drawXAxisTicks(Pane pane) {
        double range = xMax - xMin;
        double interval = calculateNiceInterval(range);
        for (double xVal = Math.ceil(xMin / interval) * interval; xVal <= xMax; xVal += interval) {
            double x = valueToX(xVal);
            Line tick = new Line(x, canvasHeight - marginBottom, x, canvasHeight - marginBottom + 5);
            tick.setStroke(Color.BLACK);
            pane.getChildren().add(tick);
            String format = (xVal == Math.floor(xVal) && !Double.isInfinite(xVal)) ? "%.0f" : "%.1f";
            Text label = new Text(String.format(format, xVal));
            double tickFont = Math.max(8, fontSize - 2);
            label.setFont(Font.font("Arial", tickFont));
            double labelWidth = computeTextWidth(label);
            label.setX(x - labelWidth / 2);
            label.setY(canvasHeight - marginBottom + 20);
            pane.getChildren().add(label);
        }
    }

    /**
     * Draw tick marks and labels on the Y axis with nice intervals.
     */
    private void drawYAxisTicks(Pane pane) {
        double range = yMax - yMin;
        double interval = calculateNiceInterval(range);
        for (double yVal = Math.ceil(yMin / interval) * interval; yVal <= yMax; yVal += interval) {
            double y = valueToY(yVal);
            Line tick = new Line(marginLeft - 5, y, marginLeft, y);
            tick.setStroke(Color.BLACK);
            pane.getChildren().add(tick);
            String format = (yVal == Math.floor(yVal) && !Double.isInfinite(yVal)) ? "%.0f" : "%.1f";
            Text label = new Text(String.format(format, yVal));
            double tickFont = Math.max(8, fontSize - 2);
            label.setFont(Font.font("Arial", tickFont));
            double labelWidth = computeTextWidth(label);
            label.setX(marginLeft - 10 - labelWidth);
            label.setY(y + 4);
            pane.getChildren().add(label);
        }
    }

    /**
     * Compute a nice interval for axis ticks based on the provided range.  This
     * algorithm chooses from 1, 2, 5 or 10 times a power of ten.
     */
    private double calculateNiceInterval(double range) {
        double exponent = Math.floor(Math.log10(range));
        double fraction = range / Math.pow(10, exponent);
        double niceFraction;
        if (fraction < 1.5) {
            niceFraction = 1;
        } else if (fraction < 3) {
            niceFraction = 2;
        } else if (fraction < 7) {
            niceFraction = 5;
        } else {
            niceFraction = 10;
        }
        return niceFraction * Math.pow(10, exponent - 1);
    }

    /**
     * Utility to compute the width of a {@link Text} node.  The width depends
     * on the current font and text content.
     */
    private double computeTextWidth(Text text) {
        text.applyCss();
        return text.getLayoutBounds().getWidth();
    }

    /**
     * Interpolate a color along a Viridis-inspired gradient.  The gradient
     * progresses from dark purple through green to yellow.  The input t
     * should be in the range [0,1], where 0 corresponds to the starting
     * color and 1 corresponds to the ending color.
     */
    private Color viridis(double t) {
        // Define key colors for the Viridis palette
        Color c1 = Color.web("#440154"); // dark purple
        Color c2 = Color.web("#21908C"); // greenish
        Color c3 = Color.web("#FDE725"); // yellow
        if (t <= 0.5) {
            double tt = t / 0.5;
            return c1.interpolate(c2, tt);
        } else {
            double tt = (t - 0.5) / 0.5;
            return c2.interpolate(c3, tt);
        }
    }

    /**
     * Interpolate a color along a Magma-inspired gradient.  The gradient
     * progresses from dark violet through pink to light yellow.  The input
     * t should be in the range [0,1].  Down-regulated genes use this
     * palette when gradient coloring is enabled.
     */
    private Color magma(double t){
    // Clamp t between 0 and 1
    t = Math.max(0, Math.min(1, t));

    Color c1 = Color.web("#1B0032"); // Deep purple
    Color c2 = Color.web("#7C1D6F"); // Vivid magenta
    Color c3 = Color.web("#FFD000"); // Acid yellow (replaces orange)

    if (t < 0.5) {
        double tt = t / 0.5;
        return c1.interpolate(c2, tt); // purple ? magenta
    } else {
        double tt = (t - 0.5) / 0.5;
        return c2.interpolate(c3, tt); // magenta ? yellow
    }
}
//    /** Gradient from dark blue to light blue for down-regulated genes */
//private Color degradeBlue(double t) {
//    Color c1 = Color.web("#08306B"); // dark navy
//    Color c2 = Color.web("#4292C6"); // medium blue
//    Color c3 = Color.web("#DEEBF7"); // light blue
//    if (t <= 0.5) return c1.interpolate(c2, t / 0.5);
//    else return c2.interpolate(c3, (t - 0.5) / 0.5);
//}
//
///** Gradient from dark red to light red for up-regulated genes */
//private Color degradeRed(double t) {
 //   Color c1 = Color.web("#67000D"); // dark red
//    Color c2 = Color.web("#FB6A4A"); // medium red
//    Color c3 = Color.web("#FEE5D9"); // light red
//    if (t <= 0.5) return c1.interpolate(c2, t / 0.5);
//    else return c2.interpolate(c3, (t - 0.5) / 0.5);
//}
/** Inverted gradient: from light blue to dark blue (more extreme = darker) */
//** Gradient for down-regulated genes: 4 levels from light sky blue to deep navy */
/** Gradient for down-regulated genes: weighted toward darker blues */
//private Color degradeBlue(double t) {
//    // Apply weighting so that higher t values (stronger effect) stay dark longer
//    t = Math.pow(t, 0.6); // < 1.0 ? pushes more range toward darker colors
//
//    Color c1 = Color.web("#A6CEE3"); // Light sky blue (lightest)
//    Color c2 = Color.web("#4292C6"); // Medium blue
//    Color c3 = Color.web("#225EA8"); // Strong dark blue
//    Color c4 = Color.web("#08306B"); // Deep navy (most intense)
//
//    if (t <= 0.33) return c1.interpolate(c2, t / 0.33);
//    else if (t <= 0.66) return c2.interpolate(c3, (t - 0.33) / 0.33);
//    else return c3.interpolate(c4, (t - 0.66) / 0.34);
//}
/** Down-regulated: 1 green ? 3 blues (weighted toward darker blues) */
/** Down-regulated: 1 green ? 3 blues (weighted toward darker blues) */
private Color degradeBlue(double t) {
    // More weight on dark tones
    t = Math.pow(t, 0.9);

    Color c1 = Color.web("#66C2A4"); // greenish teal (lightest start)
    Color c2 = Color.web("#4F9FC4"); // light blue
    Color c3 = Color.web("#2166AC"); // medium-dark blue
    Color c4 = Color.web("#08306B"); // deep navy (most intense)

    if (t <= 0.2) return c1.interpolate(c2, t / 0.2);
    else if (t <= 0.66) return c2.interpolate(c3, (t - 0.33) / 0.33);
    else return c3.interpolate(c4, (t - 0.66) / 0.33);
}

/** Up-regulated: 1 orange ? 3 reds (weighted toward darker reds) */
private Color degradeRed(double t) {
    // More weight on dark tones
    t = Math.pow(t, 0.9);

    Color c1 = Color.web("#FDB863"); // orange (lightest start)
    Color c2 = Color.web("#E34A33"); // strong red-orange
    Color c3 = Color.web("#B22222"); // dark red
    Color c4 = Color.web("#67000D"); // deep dark red (most intense)

    if (t <= 0.2) return c1.interpolate(c2, t / 0.2);
    else if (t <= 0.66) return c2.interpolate(c3, (t - 0.33) / 0.33);
    else return c3.interpolate(c4, (t - 0.66) / 0.33);
}
/**
 * Load a saved project from JSON (only JSON; no mflow).
 * This is your original method with three small additions (marked // NEW).
 */
private void loadProject(Stage stage) { 
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Open Saved Project");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
    File file = chooser.showOpenDialog(stage);
    if (file == null) return;

    try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            jsonBuilder.append(line).append("\n");
        }
        String json = jsonBuilder.toString();

        // Clear existing data
        genes.clear();
        targetedGenes.clear();
        targetGeneSelections.clear();

        // ----------------- Extract genes -----------------
        if (json.contains("\"genes\"")) {
            String genesBlock = json.split("\"genes\"\\s*:\\s*\\[", 2)[1].split("\\]", 2)[0];
            for (String entry : genesBlock.split("\\},\\s*\\{")) {
                entry = entry.replaceAll("^[\\s\\{]+", "").replaceAll("[\\s\\}]+$", "");
                if (entry.isEmpty()) continue;

                String name   = extractJsonValue(entry, "name");
                double logFC  = parseDoubleSafe(extractJsonValue(entry, "logFC"));
                double pValue = parseDoubleSafe(extractJsonValue(entry, "pValue"));
                double pAdj   = parseDoubleSafe(extractJsonValue(entry, "pAdj"));

                // Saved annotation coordinates (may be 0 if not set)
                double labelX = parseDoubleSafe(extractJsonValue(entry, "labelX"));
                double labelY = parseDoubleSafe(extractJsonValue(entry, "labelY"));
                boolean userPos = Boolean.parseBoolean(extractJsonValue(entry, "userPosition"));

                Gene g = new Gene(name, logFC, pValue, pAdj);
                g.labelX = labelX;
                g.labelY = labelY;
                g.userPosition = userPos;

                // infer pinned if coords exist but flag missing
                if (!g.userPosition && (g.labelX != 0.0 || g.labelY != 0.0)) {
                    g.userPosition = true;
                }

                genes.add(g);
            }
        }

        // ----------------- Extract targeted genes -----------------
        if (json.contains("\"targetedGenes\"")) {
            String targetBlock = json.split("\"targetedGenes\"\\s*:\\s*\\[", 2)[1].split("\\]", 2)[0].trim();

            if (targetBlock.startsWith("{") || targetBlock.contains("\"name\"")) {
                // v2: array of objects { "name": "...", "selected": true/false }
                for (String it : targetBlock.split("\\},\\s*\\{")) {
                    String entry = it.replaceAll("^[\\s\\{]+", "").replaceAll("[\\s\\}]+$", "");
                    String tgName = extractJsonValue(entry, "name");
                    String selRaw = extractJsonValue(entry, "selected");
                    boolean sel = selRaw.isEmpty() ? true : Boolean.parseBoolean(selRaw);
                    if (!tgName.isEmpty()) {
                        targetedGenes.add(tgName);
                        targetGeneSelections.put(tgName, new SimpleBooleanProperty(sel));
                    }
                }
            } else {
                // legacy: array of strings ["TP53","..."]
                for (String s : targetBlock.split("\",\\s*\"")) {
                    String gene = s.replace("\"", "").trim();
                    if (!gene.isEmpty()) {
                        targetedGenes.add(gene);
                        targetGeneSelections.put(gene, new SimpleBooleanProperty(true));
                    }
                }
            }
        }

        // ----------------- Extract settings -----------------
        if (json.contains("\"settings\"")) {
            String settingsBlock = json.split("\"settings\"\\s*:\\s*\\{", 2)[1].split("\\}", 2)[0];

            // Basic thresholds
            logFCThreshold  = parseDoubleSafe(extractJsonValue(settingsBlock, "logFCThreshold"));
            pValueThreshold = parseDoubleSafe(extractJsonValue(settingsBlock, "pValueThreshold"));
            useAdjustedP    = Boolean.parseBoolean(extractJsonValue(settingsBlock, "useAdjustedP"));
            annotate        = Boolean.parseBoolean(extractJsonValue(settingsBlock, "annotate"));
            topNUp          = (int) parseDoubleSafe(extractJsonValue(settingsBlock, "topNUp"));
            topNDown        = (int) parseDoubleSafe(extractJsonValue(settingsBlock, "topNDown"));

            // Display settings
            showZeroLine       = Boolean.parseBoolean(extractJsonValue(settingsBlock, "showZeroLine"));
            showGrid           = Boolean.parseBoolean(extractJsonValue(settingsBlock, "showGrid"));
            showTargets        = Boolean.parseBoolean(extractJsonValue(settingsBlock, "showTargets"));
            edgeWidth          = parseDoubleSafe(extractJsonValue(settingsBlock, "edgeWidth"));
            thresholdLineWidth = parseDoubleSafe(extractJsonValue(settingsBlock, "thresholdLineWidth"));
            dotSize            = parseDoubleSafe(extractJsonValue(settingsBlock, "dotSize"));
            canvasWidth        = parseDoubleSafe(extractJsonValue(settingsBlock, "canvasWidth"));
            canvasHeight       = parseDoubleSafe(extractJsonValue(settingsBlock, "canvasHeight"));
            fontSize           = parseDoubleSafe(extractJsonValue(settingsBlock, "fontSize"));

            // Label distances
            xLabelDistance = parseDoubleSafe(extractJsonValue(settingsBlock, "xLabelDistance"));
            yLabelDistance = parseDoubleSafe(extractJsonValue(settingsBlock, "yLabelDistance"));
            if (xLabelDistance <= 0) xLabelDistance = 40;
            if (yLabelDistance <= 0) yLabelDistance = 40;

            // Opacity
            dotOpacity = parseDoubleSafe(extractJsonValue(settingsBlock, "dotOpacity"));
            if (dotOpacity <= 0) dotOpacity = 1.0;
            dotOpacity = Math.max(0.1, Math.min(1.0, dotOpacity));

            // Dot outline
            if (json.contains("\"showDotOutline\"")) {
                showDotOutline   = Boolean.parseBoolean(extractJsonValue(settingsBlock, "showDotOutline"));
                String outlineHex = extractJsonValue(settingsBlock, "dotOutlineColor");
                if (!outlineHex.isEmpty()) dotOutlineColor = Color.web(outlineHex);
                dotOutlineWidth  = parseDoubleSafe(extractJsonValue(settingsBlock, "dotOutlineWidth"));
            } else {
                showDotOutline  = false;
                dotOutlineColor = Color.BLACK;
                dotOutlineWidth = 1.0;
            }

            // Hover
            if (json.contains("\"showHoverInfo\"")) {
                showHoverInfo = Boolean.parseBoolean(extractJsonValue(settingsBlock, "showHoverInfo"));
            } else {
                showHoverInfo = true;
            }

            // Colors & style
            String cu = extractJsonValue(settingsBlock, "colUp");   if (!cu.isEmpty()) colUp = Color.web(cu);
            String cd = extractJsonValue(settingsBlock, "colDown"); if (!cd.isEmpty()) colDown = Color.web(cd);
            String cn = extractJsonValue(settingsBlock, "colNS");   if (!cn.isEmpty()) colNS = Color.web(cn);
            String cs = extractJsonValue(settingsBlock, "colorStyle");
            if (!cs.isEmpty()) colorStyle = cs; else if (colorStyle == null) colorStyle = "CLASSIC";

            // Labels
            String ttl = extractJsonValue(settingsBlock, "title"); if (!ttl.isEmpty()) plotTitle = ttl;
            String xl  = extractJsonValue(settingsBlock, "xLabel"); if (!xl.isEmpty()) xLabel = xl;
            String yl  = extractJsonValue(settingsBlock, "yLabel"); if (!yl.isEmpty()) yLabel = yl;
        }

        // Update plot pane size based on loaded canvas dimensions
        plotPane.setPrefSize(canvasWidth, canvasHeight);
        plotPane.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        plotPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        geneInfo.setFont(Font.font("Arial", fontSize));

        // ---- NEW: push loaded state back into the dashboard controls
        reflectStateIntoControls();

        // Refresh UI list of targets, recalc + redraw
        updateTargetGeneListView();
        calculateAxisRanges();

        // Do NOT reset userPosition here; just null UI nodes (fresh draw will recreate).
        for (Gene g : genes) {
            g.labelNode = null;
            g.lineNode  = null;
        }

        drawPlot();

        // ---- NEW: remember file + refresh window title
        currentProjectFile = file;
        markClean(); // start clean after loading
        updateWindowTitle(); // shows filename in title bar

        new Alert(Alert.AlertType.INFORMATION, "Project loaded successfully!").show();

    } catch (Exception ex) {
        new Alert(Alert.AlertType.ERROR, "Error loading project: " + ex.getMessage()).show();
        ex.printStackTrace();
    }
}


private String extractJsonValue(String json, String key) {
    String regex = "\"" + key + "\"\\s*:\\s*\"?([^\",\\}]*)\"?";
    java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(json);
    if (m.find()) {
        String val = m.group(1).trim();
        return val.startsWith("\"") && val.endsWith("\"") ? 
               val.substring(1, val.length() - 1) : val;
    }
    return "";
}

private void updateAnnotations() {
    // Clear annotations only for genes that are neither targeted nor pinned
    for (Gene g : genes) {
        boolean isTargeted = targetedGenes.contains(g.name);
        if (!isTargeted && !g.userPosition) {
            g.labelNode = null;
            g.lineNode = null;
            if (g.dotNode != null) {
                plotPane.getChildren().remove(g.dotNode);
            }
        }
    }
    drawPlot();
}
}
