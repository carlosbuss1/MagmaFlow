import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.print.PrinterJob;
import javafx.scene.SnapshotParameters;
import javafx.scene.transform.Transform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;
import java.util.Arrays;
import javafx.collections.FXCollections;

/**
 * A completely refactored implementation of the volcano plot using JavaFX node
 * primitives instead of a single canvas.  Each gene is represented by a
 * {@link Circle} for the dot, and annotated genes have a {@link Line}
 * connecting to a draggable {@link Text} label.  Because the plot never
 * redraws the entire scene during interactive operations, the view remains
 * completely stable (no flicker) when hovering or dragging labels.
 */
public class VolcanoPlotJavaFX extends Application {

    /**
     * Internal representation of a single gene and its associated display
     * elements.  The label and connecting line are created on demand when
     * the gene is selected for annotation.  The x and y fields store the
     * coordinate of the gene dot within the plot pane.
     */
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
    boolean isTargetGene;  // <-- ADD THIS LINE
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
        this.isTargetGene = false;  // <-- ADD THIS INITIALIZATION
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
        DisplaySettings(double edgeWidth, double thresholdLineWidth, double dotSize,
                        double canvasWidth, double canvasHeight, double fontSize) {
            this.edgeWidth = edgeWidth;
            this.thresholdLineWidth = thresholdLineWidth;
            this.dotSize = dotSize;
            this.canvasWidth = canvasWidth;
            this.canvasHeight = canvasHeight;
            this.fontSize = fontSize;
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
    private Color colUp = Color.RED;
    private Color colDown = Color.BLUE;
    private Color colNS = Color.BLACK;
    private int topNUp = 5;
    private int topNDown = 5;
    private boolean annotate = true;
    private boolean useAdjustedP = true;
    private boolean showTargets = true;

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
    private double dotSize = 6.0;
    // Default canvas size 700x600 (approx 7x6 units) for a more compact plot
    private double canvasWidth = 700;
    private double canvasHeight = 600;
    /**
     * Base font size used for tick labels, gene labels and axis labels.  The
     * title will be rendered slightly larger than this value.  Users can
     * configure this in the display options dialog.
     */
    // Base font size; default increased to 18 for better readability
    private double fontSize = 18.0;
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
    primaryStage.setTitle("MagmaFlow: Drag, Explore, Discover – Publication-Ready Volcano Plots");

    // Initialize controls panel
    controlsPanel = new VBox(10);
    controlsPanel.setPadding(new Insets(10));

    // App icon
    try {
        javafx.scene.image.Image appIcon = new javafx.scene.image.Image("file:volcano_icon.png");
        primaryStage.getIcons().add(appIcon);
    } catch (Exception ex) {
        // Ignore icon load failure
    }

    // UI scaling controls
    Label scaleLabel = new Label("Magnification: 100%");
    Slider scaleSlider = new Slider(0.5, 3.0, 1.0);
    scaleSlider.setShowTickLabels(true);
    scaleSlider.setShowTickMarks(true);
    scaleSlider.setMajorTickUnit(0.5);
    scaleSlider.setMinorTickCount(4);
    scaleSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
        double scale = newVal.doubleValue();
        scaleLabel.setText(String.format("Magnification: %.0f%%", scale * 100));
        if (plotPane != null) {
            plotPane.setScaleX(scale);
            plotPane.setScaleY(scale);
        }
    });

    // Buttons
    Button loadButton = new Button("Load CSV");
    Button exportButton = new Button("Export Image");
    Button saveProjectButton = new Button("Save Project");
    Button loadProjectButton = new Button("Load Project");
    Button loadTargetGenesButton = new Button("Load Target Genes");
    Button enterTargetGenesButton = new Button("Enter Target Genes");
    Button setLabelsButton = new Button("Set Labels");
    Button setDisplayButton = new Button("Set Display Options");
    Button refreshTargetsButton = new Button("Refresh Targets");
    refreshTargetsButton.setOnAction(e -> refreshTargetGenes(primaryStage));

    // Color pickers
    ColorPicker upColorPicker = new ColorPicker(colUp);
    ColorPicker downColorPicker = new ColorPicker(colDown);
    ColorPicker nsColorPicker = new ColorPicker(colNS);

    // Threshold fields
    TextField logFCThresholdField = new TextField(String.valueOf(logFCThreshold));
    TextField pValueThresholdField = new TextField(String.valueOf(pValueThreshold));

    // Checkboxes
    CheckBox useAdjPCheck = new CheckBox("Use adjusted p-values");
    useAdjPCheck.setSelected(true);

    CheckBox annotateCheck = new CheckBox("Show top genes");
    annotateCheck.setSelected(true);

    CheckBox showTargetsCheck = new CheckBox("Show Target Genes");
    showTargetsCheck.setSelected(true);

    CheckBox gridCheck = new CheckBox("Show grid lines");
    gridCheck.setSelected(showGrid);

    CheckBox zeroLineCheck = new CheckBox("Show 0 fold-change line");
    zeroLineCheck.setSelected(showZeroLine);

    // Top N fields
    TextField topNUpField = new TextField(String.valueOf(topNUp));
    TextField topNDownField = new TextField(String.valueOf(topNDown));

    // Color style combo
    ComboBox<String> colorStyleCombo = new ComboBox<>();
    colorStyleCombo.getItems().addAll(
        "Classic Gurzov",
        "MagmaFlow Style 1 (log2FC)",
        "MagmaFlow Style 2 (p-value)",
        "MagmaFlow Style 3 (combined)",
        "MagmaFlow Style 4 (log2FC, viridis)",
        "MagmaFlow Style 5 (p-value, viridis)",
        "MagmaFlow Style 6 (combined, viridis)",
        "MagmaFlow Standard (log2FC)",
        "MagmaFlow Standard (p-value)",
        "MagmaFlow Standard (combined)"
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
        new HBox(5, loadTargetGenesButton, enterTargetGenesButton, refreshTargetsButton),
        targetedGeneListView,
        showTargetsCheck,
        new Separator(),
        setLabelsButton,
        setDisplayButton,
        new Separator(),
        new Label("Color Settings:"),
        colorStyleCombo, // ✅ moved here
        new HBox(5, new Label("Up:"), upColorPicker),
        new HBox(5, new Label("Down:"), downColorPicker),
        new HBox(5, new Label("NS:"), nsColorPicker),
        new Separator(),
        new Label("Threshold Settings:"),
        new HBox(5, new Label("logFC:"), logFCThresholdField),
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
        new Separator(),
        geneInfo
    );

    // Event handlers
    loadProjectButton.setOnAction(e -> loadProject(primaryStage));
    loadButton.setOnAction(e -> loadCSV(primaryStage));
    exportButton.setOnAction(e -> showExportDialog(primaryStage));
    saveProjectButton.setOnAction(e -> saveProject(primaryStage));
    loadTargetGenesButton.setOnAction(e -> loadTargetGenes(primaryStage));
    setLabelsButton.setOnAction(e -> showLabelDialog(primaryStage));
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

    annotateCheck.setOnAction(e -> { annotate = annotateCheck.isSelected(); drawPlot(); });
    showTargetsCheck.setOnAction(e -> { showTargets = showTargetsCheck.isSelected(); updateAnnotations(); });

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
            case 1: colorStyle = "MAGMA1"; break;
            case 2: colorStyle = "MAGMA2"; break;
            case 3: colorStyle = "MAGMA3"; break;
            case 4: colorStyle = "MAGMA4"; break;
            case 5: colorStyle = "MAGMA5"; break;
            case 6: colorStyle = "MAGMA6"; break;
            case 7: colorStyle = "MAGMA_STD1"; break;
            case 8: colorStyle = "MAGMA_STD2"; break;
            case 9: colorStyle = "MAGMA_STD3"; break;
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

    // Plot pane
    plotPane = new Pane();
    plotPane.setPrefSize(canvasWidth, canvasHeight);
    plotPane.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    plotPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    plotPane.setStyle("-fx-background-color: transparent;");

    // Root layout
    BorderPane root = new BorderPane();
    root.setLeft(controlsPanel);
    root.setCenter(plotPane);

    primaryStage.setScene(new Scene(root));
    primaryStage.show();

    // Initial draw
    calculateAxisRanges();
    drawPlot();
    updateTargetGeneListView();
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
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine();
            if (header == null) {
                return;
            }
            String[] columns = header.split(",");
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
            // Choose reasonable defaults based on column names
            for (int i = 0; i < columns.length; i++) {
                String col = columns[i].toLowerCase();
                if ((col.contains("gene") || col.contains("symbol") || col.contains("name")) && geneNameCombo.getSelectionModel().isEmpty()) {
                    geneNameCombo.getSelectionModel().select(i);
                } else if ((col.contains("logfc") || col.contains("fold") || col.contains("lfc")) && logFCCombo.getSelectionModel().isEmpty()) {
                    logFCCombo.getSelectionModel().select(i);
                } else if ((col.contains("p_val") || col.contains("pvalue")) && !col.contains("adj") && pValCombo.getSelectionModel().isEmpty()) {
                    pValCombo.getSelectionModel().select(i);
                } else if ((col.contains("p_val_adj") || col.contains("p.adj") || col.contains("fdr")) && pAdjCombo.getSelectionModel().isEmpty()) {
                    pAdjCombo.getSelectionModel().select(i);
                }
            }
            if (geneNameCombo.getSelectionModel().isEmpty() && !geneNameCombo.getItems().isEmpty()) geneNameCombo.getSelectionModel().select(0);
            if (logFCCombo.getSelectionModel().isEmpty() && !logFCCombo.getItems().isEmpty()) logFCCombo.getSelectionModel().select(0);
            if (pValCombo.getSelectionModel().isEmpty() && !pValCombo.getItems().isEmpty()) pValCombo.getSelectionModel().select(0);
            if (pAdjCombo.getSelectionModel().isEmpty() && !pAdjCombo.getItems().isEmpty()) pAdjCombo.getSelectionModel().select(0);
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
            try (BufferedReader br2 = new BufferedReader(new FileReader(file))) {
                br2.readLine(); // skip header
                String line;
                while ((line = br2.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length <= Math.max(Math.max(mapping.geneNameCol, mapping.logFCCol), Math.max(mapping.pValCol, mapping.pValAdjCol))) {
                        continue;
                    }
                    try {
                        String geneName = parts[mapping.geneNameCol].replace("\"", "").trim();
                        double logFC = Double.parseDouble(parts[mapping.logFCCol].trim());
                        double pVal = Double.parseDouble(parts[mapping.pValCol].trim());
                        double pAdj = mapping.pValAdjCol >= 0 && mapping.pValAdjCol < parts.length ?
                                Double.parseDouble(parts[mapping.pValAdjCol].trim()) : pVal;
                        genes.add(new Gene(geneName, logFC, pVal, pAdj));
                    } catch (Exception ignored) {
                    }
                }
            }
            calculateAxisRanges();
            drawPlot();
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Error reading file: " + ex.getMessage()).show();
        }
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
                    return new DisplaySettings(ew, tw, ds, w, h, fs);
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
            plotPane.setPrefSize(canvasWidth, canvasHeight);
            plotPane.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            plotPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            geneInfo.setFont(Font.font("Arial", fontSize));
            drawPlot();
        }
    }
/**
 * Refresh the target gene list after confirming with the user.
 */
private void refreshTargetGenes(Stage stage) {
    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    confirm.initOwner(stage);
    confirm.setTitle("Confirm Refresh");
    confirm.setHeaderText("Are you sure you want to clear the Target Gene list?");
    confirm.setContentText("This will remove all target genes and uncheck all selections.");

    Optional<ButtonType> result = confirm.showAndWait();
    if (result.isPresent() && result.get() == ButtonType.OK) {
        targetedGenes.clear();
        targetGeneSelections.clear();
        updateTargetGeneListView();
        drawPlot();

        new Alert(Alert.AlertType.INFORMATION, 
            "Target gene list has been cleared successfully.").show();
    }
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
    private void calculateAxisRanges() {
        if (genes.isEmpty()) {
            xMin = -4;
            xMax = 4;
            yMax = 10;
            return;
        }
        double minLogFC = genes.stream().mapToDouble(g -> g.logFC).min().orElse(-4);
        double maxLogFC = genes.stream().mapToDouble(g -> g.logFC).max().orElse(4);
        // Use symmetric limits around zero for the x-axis
        double maxAbs = Math.max(Math.abs(minLogFC), Math.abs(maxLogFC));
        double padding = Math.max(0.5, maxAbs * 0.1);
        xMax = maxAbs + padding;
        xMin = -xMax;
        // Compute the maximum and minimum -log10(p) values to determine the y-axis range
        double[] logPValues = genes.stream()
                .mapToDouble(g -> {
                    double p = useAdjustedP ? g.pAdj : g.pValue;
                    return -Math.log10(p);
                })
                .filter(d -> !Double.isInfinite(d) && !Double.isNaN(d))
                .toArray();
        double maxLogP = logPValues.length > 0 ? Arrays.stream(logPValues).max().orElse(10) : 10;
        double minLogP = logPValues.length > 0 ? Arrays.stream(logPValues).min().orElse(0) : 0;
        // Compute a dynamic lower bound for the y-axis to prevent points from overlapping
        // the x-axis.  In practice, if the smallest -log10(p) value is close to zero,
        // mapping it directly to the baseline will cause points to touch the x-axis.
        // We therefore ensure the baseline extends at least 1.0 unit below the
        // minimum observed value.  This creates extra space below the data, similar
        // to how ggplot in R offsets the x-axis to avoid overlap.
        double baselinePad = 1.0;
        yMin = Math.min(minLogP - 0.5, -baselinePad);
        yMax = Math.max(10, maxLogP * 1.1);
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

    /**
     * Draw the entire plot.  This method clears the existing children of the
     * plot pane and recreates axes, ticks, points, threshold lines and
     * annotations.  It does not re-render on every mouse event and is called
     * only when data or options change.
     */
private void drawPlot() {
    calculateAxisRanges();
    plotPane.getChildren().clear();
    
    // Draw title (centered, non-draggable)
    Text titleNode = new Text(plotTitle);
    titleNode.setFont(Font.font("Arial", fontSize + 2));
    double titleWidth = computeTextWidth(titleNode);
    double plotRegionWidth = canvasWidth - marginLeft - marginRight;
    double titleBaseX = marginLeft + plotRegionWidth / 2 - titleWidth / 2;
    double titleBaseY = marginTop - 20;
    titleNode.setX(titleBaseX);
    titleNode.setY(titleBaseY);
    plotPane.getChildren().add(titleNode);

    // Draw axes lines
    double plotWidth = canvasWidth - marginLeft - marginRight;
    double plotHeight = canvasHeight - marginTop - marginBottom;
    Line xAxis = new Line(marginLeft, canvasHeight - marginBottom, marginLeft + plotWidth, canvasHeight - marginBottom);
    Line yAxis = new Line(marginLeft, canvasHeight - marginBottom, marginLeft, marginTop);
    xAxis.setStroke(Color.BLACK);
    xAxis.setStrokeWidth(1.5);
    yAxis.setStroke(Color.BLACK);
    yAxis.setStrokeWidth(1.5);
    plotPane.getChildren().addAll(xAxis, yAxis);

    // Optionally draw grid lines on the background
    if (showGrid) {
        // Vertical grid lines
        double xRange = xMax - xMin;
        double xInterval = calculateNiceInterval(xRange);
        for (double xVal = Math.ceil(xMin / xInterval) * xInterval; xVal <= xMax; xVal += xInterval) {
            double x = valueToX(xVal);
            Line vGrid = new Line(x, marginTop, x, canvasHeight - marginBottom);
            vGrid.setStroke(Color.LIGHTGRAY);
            vGrid.setOpacity(0.3);
            plotPane.getChildren().add(vGrid);
        }
        // Horizontal grid lines
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

    // Draw axis labels (non-draggable, centered)
    Text xLabelNode = new Text(xLabel);
    xLabelNode.setFont(Font.font("Arial", fontSize));
    double xLabelWidth = computeTextWidth(xLabelNode);
    double xLabelBaseX = marginLeft + plotWidth / 2 - xLabelWidth / 2;
    double xLabelBaseY = canvasHeight - marginBottom + 40;
    xLabelNode.setX(xLabelBaseX);
    xLabelNode.setY(xLabelBaseY);
    plotPane.getChildren().add(xLabelNode);

    // Y axis label (rotated, centered)
    Text yLabelNode = new Text(yLabel);
    yLabelNode.setFont(Font.font("Arial", fontSize));
    double plotHeightActual = canvasHeight - marginTop - marginBottom;
    double baseX = marginLeft - 40;
    double baseY = marginTop + plotHeightActual / 2;
    double labelWidth = computeTextWidth(yLabelNode);
    double transY = baseY + labelWidth / 2.0;

    Group yLabelGroup = new Group(yLabelNode);
    yLabelGroup.getTransforms().add(new javafx.scene.transform.Translate(baseX, transY));
    yLabelGroup.getTransforms().add(new javafx.scene.transform.Rotate(-90));
    plotPane.getChildren().add(yLabelGroup);

    // Draw tick marks
    drawXAxisTicks(plotPane);
    drawYAxisTicks(plotPane);

    // Draw threshold lines
    if (logFCThreshold > xMin && -logFCThreshold < xMax) {
        Line leftThreshold = new Line(valueToX(-logFCThreshold), marginTop, valueToX(-logFCThreshold), canvasHeight - marginBottom);
        Line rightThreshold = new Line(valueToX(logFCThreshold), marginTop, valueToX(logFCThreshold), canvasHeight - marginBottom);
        leftThreshold.getStrokeDashArray().setAll(5.0, 5.0);
        rightThreshold.getStrokeDashArray().setAll(5.0, 5.0);
        leftThreshold.setStrokeWidth(thresholdLineWidth);
        rightThreshold.setStrokeWidth(thresholdLineWidth);
        plotPane.getChildren().addAll(leftThreshold, rightThreshold);
    }

    // Draw p-value threshold line
    double pValY = valueToY(-Math.log10(pValueThreshold));
    if (pValY >= marginTop && pValY <= canvasHeight - marginBottom) {
        Line pThreshold = new Line(marginLeft, pValY, marginLeft + plotWidth, pValY);
        pThreshold.getStrokeDashArray().setAll(5.0, 5.0);
        pThreshold.setStrokeWidth(thresholdLineWidth);
        plotPane.getChildren().add(pThreshold);
    }

    // Draw zero line if enabled
    if (showZeroLine) {
        double zeroX = valueToX(0.0);
        Line zeroLine = new Line(zeroX, marginTop, zeroX, canvasHeight - marginBottom);
        zeroLine.getStrokeDashArray().setAll(5.0, 5.0);
        zeroLine.setStrokeWidth(thresholdLineWidth);
        zeroLine.setStroke(Color.BLACK);
        plotPane.getChildren().add(zeroLine);
    }
         // Determine which genes to annotate
        List<Gene> significantGenes = new ArrayList<>();
        Set<Gene> annotateGenes = new HashSet<>();
        for (Gene g : genes) {
            double pValToUse = useAdjustedP ? g.pAdj : g.pValue;
            if (Math.abs(g.logFC) >= logFCThreshold && pValToUse <= pValueThreshold) {
                significantGenes.add(g);
            }
// In the drawPlot() method, where you determine which genes to annotate:
if (targetedGenes.contains(g.name)) {
    // Only annotate if the gene is both in targetedGenes AND enabled in selections
    BooleanProperty prop = targetGeneSelections.get(g.name);
    if (prop != null && prop.get()) {  // Only annotate if checkbox is selected
        annotateGenes.add(g);
    }
}
        }
       if (annotate && !significantGenes.isEmpty()) {
    // Sort by p-value ascending, tie by absolute logFC descending
    significantGenes.sort((a, b) -> {
        double p1 = useAdjustedP ? a.pAdj : a.pValue;
        double p2 = useAdjustedP ? b.pAdj : b.pValue;
        int cmp = Double.compare(p1, p2);
        if (cmp == 0) {
            return Double.compare(Math.abs(b.logFC), Math.abs(a.logFC));
        }
        return cmp;
    });
    
    int upCount = 0;
    int downCount = 0;
    for (Gene g : significantGenes) {
        if (g.logFC > 0 && upCount < topNUp && !targetedGenes.contains(g.name)) {
            annotateGenes.add(g);
            upCount++;
        } else if (g.logFC < 0 && downCount < topNDown && !targetedGenes.contains(g.name)) {
            annotateGenes.add(g);
            downCount++;
        }
    }
}
        // Compute dot positions ahead of drawing; required for assigning label positions
        for (Gene g : genes) {
            double pValToUse = useAdjustedP ? g.pAdj : g.pValue;
            g.dotX = valueToX(g.logFC);
            g.dotY = valueToY(-Math.log10(pValToUse));
        }

        // Assign initial label positions for annotated genes.  This must be done after
        // computing dot positions so that g.dotX and g.dotY are available for spacing
        assignInitialLabelPositions(annotateGenes);

        // Compute maximum absolute logFC and maximum -log10(p-value) for gradient scaling
        double maxAbsLogFC = genes.stream().mapToDouble(g -> Math.abs(g.logFC)).max().orElse(1.0);
        double maxNegLogP = genes.stream()
                .mapToDouble(g -> {
                    double p = useAdjustedP ? g.pAdj : g.pValue;
                    return -Math.log10(p);
                }).max().orElse(1.0);
        double thresholdNegLogP = -Math.log10(pValueThreshold);
        // Draw all gene dots
        for (Gene g : genes) {
            double pValToUse = useAdjustedP ? g.pAdj : g.pValue;
            Circle circle = new Circle(g.dotX, g.dotY, dotSize / 2);
            Color geneColor = colNS;
            boolean isSig = Math.abs(g.logFC) >= logFCThreshold && pValToUse <= pValueThreshold;
            if (isSig) {
                switch (colorStyle) {
                    case "MAGMA1": {
                        // Magma gradient based on log2FC intensity (symmetric for up and down)
                        double t;
                        if (maxAbsLogFC - logFCThreshold <= 0) {
                            t = 1.0;
                        } else {
                            t = (Math.abs(g.logFC) - logFCThreshold) / (maxAbsLogFC - logFCThreshold);
                            if (t < 0) t = 0;
                            if (t > 1) t = 1;
                        }
                        geneColor = magma(t);
                        break;
                    }
                    case "MAGMA2": {
                        // Magma gradient based on p-value intensity (symmetric)
                        double logScore = -Math.log10(pValToUse);
                        double t;
                        if (maxNegLogP - thresholdNegLogP <= 0) {
                            t = 1.0;
                        } else {
                            t = (logScore - thresholdNegLogP) / (maxNegLogP - thresholdNegLogP);
                            if (t < 0) t = 0;
                            if (t > 1) t = 1;
                        }
                        geneColor = magma(t);
                        break;
                    }
                    case "MAGMA3": {
                        // Magma gradient based on both log2FC and p-value (symmetric)
                        double t1;
                        if (maxAbsLogFC - logFCThreshold <= 0) {
                            t1 = 1.0;
                        } else {
                            t1 = (Math.abs(g.logFC) - logFCThreshold) / (maxAbsLogFC - logFCThreshold);
                            if (t1 < 0) t1 = 0;
                            if (t1 > 1) t1 = 1;
                        }
                        double logScore = -Math.log10(pValToUse);
                        double t2;
                        if (maxNegLogP - thresholdNegLogP <= 0) {
                            t2 = 1.0;
                        } else {
                            t2 = (logScore - thresholdNegLogP) / (maxNegLogP - thresholdNegLogP);
                            if (t2 < 0) t2 = 0;
                            if (t2 > 1) t2 = 1;
                        }
                        double t = (t1 + t2) / 2.0;
                        geneColor = magma(t);
                        break;
                    }
                    case "MAGMA4": {
                        // Viridis gradient based on log2FC intensity for both directions
                        double t;
                        if (maxAbsLogFC - logFCThreshold <= 0) {
                            t = 1.0;
                        } else {
                            t = (Math.abs(g.logFC) - logFCThreshold) / (maxAbsLogFC - logFCThreshold);
                            if (t < 0) t = 0;
                            if (t > 1) t = 1;
                        }
                        geneColor = viridis(t);
                        break;
                    }
                    case "MAGMA5": {
                        // Viridis gradient based on p-value intensity for both directions
                        double logScore = -Math.log10(pValToUse);
                        double t;
                        if (maxNegLogP - thresholdNegLogP <= 0) {
                            t = 1.0;
                        } else {
                            t = (logScore - thresholdNegLogP) / (maxNegLogP - thresholdNegLogP);
                            if (t < 0) t = 0;
                            if (t > 1) t = 1;
                        }
                        geneColor = viridis(t);
                        break;
                    }
                    case "MAGMA6": {
                        // Viridis gradient based on combined log2FC and p-value intensity
                        double t1;
                        if (maxAbsLogFC - logFCThreshold <= 0) {
                            t1 = 1.0;
                        } else {
                            t1 = (Math.abs(g.logFC) - logFCThreshold) / (maxAbsLogFC - logFCThreshold);
                            if (t1 < 0) t1 = 0;
                            if (t1 > 1) t1 = 1;
                        }
                        double logScore = -Math.log10(pValToUse);
                        double t2;
                        if (maxNegLogP - thresholdNegLogP <= 0) {
                            t2 = 1.0;
                        } else {
                            t2 = (logScore - thresholdNegLogP) / (maxNegLogP - thresholdNegLogP);
                            if (t2 < 0) t2 = 0;
                            if (t2 > 1) t2 = 1;
                        }
                        double t = (t1 + t2) / 2.0;
                        geneColor = viridis(t);
                        break;
                    }
                    case "CLASSIC":
                    default: {
                        geneColor = g.logFC > 0 ? colUp : colDown;
                        break;
                    }
                    case "MAGMA_STD1": { // log2FC-based intensity
    double t;
    if (maxAbsLogFC - logFCThreshold <= 0) t = 1.0;
    else {
        t = (Math.abs(g.logFC) - logFCThreshold) / (maxAbsLogFC - logFCThreshold);
        t = Math.max(0, Math.min(1, t));
    }
    geneColor = (g.logFC > 0) ? degradeRed(t) : degradeBlue(t);
    break;
}
case "MAGMA_STD2": { // p-value-based intensity
    double logScore = -Math.log10(pValToUse);
    double t;
    if (maxNegLogP - thresholdNegLogP <= 0) t = 1.0;
    else {
        t = (logScore - thresholdNegLogP) / (maxNegLogP - thresholdNegLogP);
        t = Math.max(0, Math.min(1, t));
    }
    geneColor = (g.logFC > 0) ? degradeRed(t) : degradeBlue(t);
    break;
}
case "MAGMA_STD3": { // combined log2FC & p-value
    double t1;
    if (maxAbsLogFC - logFCThreshold <= 0) t1 = 1.0;
    else {
        t1 = (Math.abs(g.logFC) - logFCThreshold) / (maxAbsLogFC - logFCThreshold);
        t1 = Math.max(0, Math.min(1, t1));
    }
    double logScore = -Math.log10(pValToUse);
    double t2;
    if (maxNegLogP - thresholdNegLogP <= 0) t2 = 1.0;
    else {
        t2 = (logScore - thresholdNegLogP) / (maxNegLogP - thresholdNegLogP);
        t2 = Math.max(0, Math.min(1, t2));
    }
    double t = (t1 + t2) / 2.0;
    geneColor = (g.logFC > 0) ? degradeRed(t) : degradeBlue(t);
    break;
}
                }
            }
            circle.setFill(geneColor);
            circle.setOnMouseEntered(ev -> {
                geneInfo.setText(String.format("%s: logFC=%.2f, p=%.2e, p.adj=%.2e", g.name, g.logFC, g.pValue, g.pAdj));
            });
            circle.setOnMouseExited(ev -> {
                geneInfo.setText("");
            });
            g.dotNode = circle;
            plotPane.getChildren().add(circle);
        }

        // Draw annotations (lines and labels) on top of dots
        for (Gene g : genes) {
            if (!annotateGenes.contains(g)) {
                // Reset label nodes for non-annotated genes
                g.labelNode = null;
                g.lineNode = null;
                continue;
            }
            // Create or update label
            Text label = new Text(g.name);
            label.setFont(Font.font("Arial", fontSize));
            label.setX(g.labelX);
            label.setY(g.labelY);
            plotPane.getChildren().add(label);
            label.applyCss();
            double textWidth = label.getLayoutBounds().getWidth();
            // Determine connection point on label: connect to left or right depending on sign of logFC
            double endX;
            if (g.logFC < 0) {
                endX = g.labelX + textWidth;
            } else {
                endX = g.labelX;
            }
            Line connector = new Line(g.dotX, g.dotY, endX, g.labelY);
            connector.setStroke(Color.BLACK);
            connector.setStrokeWidth(edgeWidth);
            plotPane.getChildren().add(connector);
            g.labelNode = label;
            g.lineNode = connector;
            // Setup drag handling for label
            label.addEventHandler(MouseEvent.MOUSE_PRESSED, ev -> {
                // Start dragging this gene's label.  Store the offset between
                // the mouse position and label coordinates to maintain relative
                // positioning during the drag.
                draggedGene = g;
                dragOffsetX = ev.getX() - label.getX();
                dragOffsetY = ev.getY() - label.getY();
                ev.consume();
            });
            label.addEventHandler(MouseEvent.MOUSE_DRAGGED, ev -> {
                if (draggedGene != null) {
                    double newX = ev.getX() - dragOffsetX;
                    double newY = ev.getY() - dragOffsetY;
                    draggedGene.labelX = newX;
                    draggedGene.labelY = newY;
                    updateAnnotationPosition(draggedGene);
                }
                ev.consume();
            });
            label.addEventHandler(MouseEvent.MOUSE_RELEASED, ev -> {
                // When the user releases the mouse after dragging, mark this
                // gene's label as user-positioned so it won't be auto-adjusted
                if (draggedGene != null) {
                    draggedGene.userPosition = true;
                }
                draggedGene = null;
                ev.consume();
            });
        }
    }

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
 /**
 * Save the current project (data and settings) to a JSON file.
 */private void saveProject(Stage stage) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Save Project");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
    
    File file = chooser.showSaveDialog(stage);
    if (file == null) return;
    
    try (PrintWriter out = new PrintWriter(file)) {
        out.println("{");
        
        // Save genes
        out.println("  \"genes\": [");
        for (int i = 0; i < genes.size(); i++) {
            Gene g = genes.get(i);
            out.printf(
                "    {\"name\": \"%s\", \"logFC\": %.6f, \"pValue\": %.6e, \"pAdj\": %.6e, " +
                "\"labelX\": %.2f, \"labelY\": %.2f, \"userPosition\": %b}%s%n",
                g.name.replace("\"", "\\\""), g.logFC, g.pValue, g.pAdj,
                g.labelX, g.labelY, g.userPosition,
                (i < genes.size() - 1 ? "," : "")
            );
        }
        out.println("  ],");
        
        // Save targeted genes - only those that are selected/enabled
        out.println("  \"targetedGenes\": [");
        int idx = 0;
        int enabledCount = (int) targetGeneSelections.entrySet().stream()
            .filter(e -> e.getValue().get())
            .count();
        for (String tg : targetedGenes) {
            // Only save genes that are currently selected/enabled
            if (targetGeneSelections.getOrDefault(tg, new SimpleBooleanProperty(true)).get()) {
                out.printf("    \"%s\"%s%n", 
                    tg.replace("\"", "\\\""), 
                    (idx++ < enabledCount - 1 ? "," : ""));
            }
        }
        out.println("  ],");
        
        // Save settings
        out.println("  \"settings\": {");
        out.printf("    \"logFCThreshold\": %.6f,%n", logFCThreshold);
        out.printf("    \"pValueThreshold\": %.6f,%n", pValueThreshold);
        out.printf("    \"useAdjustedP\": %b,%n", useAdjustedP);
        out.printf("    \"annotate\": %b,%n", annotate);
        out.printf("    \"topNUp\": %d,%n", topNUp);
        out.printf("    \"topNDown\": %d,%n", topNDown);
        out.printf("    \"showZeroLine\": %b,%n", showZeroLine);
        out.printf("    \"showGrid\": %b,%n", showGrid);
        out.printf("    \"showTargets\": %b,%n", showTargets);
        out.printf("    \"edgeWidth\": %.2f,%n", edgeWidth);
        out.printf("    \"thresholdLineWidth\": %.2f,%n", thresholdLineWidth);
        out.printf("    \"dotSize\": %.2f,%n", dotSize);
        out.printf("    \"canvasWidth\": %.1f,%n", canvasWidth);
        out.printf("    \"canvasHeight\": %.1f,%n", canvasHeight);
        out.printf("    \"fontSize\": %.1f,%n", fontSize);
        out.printf("    \"colUp\": \"%s\",%n", toHex(colUp));
        out.printf("    \"colDown\": \"%s\",%n", toHex(colDown));
        out.printf("    \"colNS\": \"%s\",%n", toHex(colNS));
        out.printf("    \"colorStyle\": \"%s\",%n", colorStyle);
        out.printf("    \"title\": \"%s\",%n", plotTitle.replace("\"", "\\\""));
        out.printf("    \"xLabel\": \"%s\",%n", xLabel.replace("\"", "\\\""));
        out.printf("    \"yLabel\": \"%s\"%n", yLabel.replace("\"", "\\\""));
        out.println("  }");
        
        out.println("}");

        new Alert(Alert.AlertType.INFORMATION, 
            "Project saved successfully to: " + file.getAbsolutePath()).show();
    } catch (IOException ex) {
        new Alert(Alert.AlertType.ERROR, 
            "Error saving project: " + ex.getMessage()).show();
    }
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
    private Color magma(double t) {
        Color c1 = Color.web("#000004"); // nearly black
        Color c2 = Color.web("#B63679"); // magenta/pink
        Color c3 = Color.web("#FCFDBF"); // light yellow
        if (t <= 0.5) {
            double tt = t / 0.5;
            return c1.interpolate(c2, tt);
        } else {
            double tt = (t - 0.5) / 0.5;
            return c2.interpolate(c3, tt);
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

private void loadProject(Stage stage) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Open Saved Project");
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
    File file = chooser.showOpenDialog(stage);
    if (file == null) return;

    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
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

        // Extract genes
        String genesBlock = json.split("\"genes\"\\s*:\\s*\\[")[1].split("\\]")[0];
        for (String entry : genesBlock.split("\\},\\s*\\{")) {
            entry = entry.replaceAll("[\\{\\}]", "").trim();
            if (entry.isEmpty()) continue;
            
            String name = extractJsonValue(entry, "name");
            double logFC = Double.parseDouble(extractJsonValue(entry, "logFC"));
            double pValue = Double.parseDouble(extractJsonValue(entry, "pValue"));
            double pAdj = Double.parseDouble(extractJsonValue(entry, "pAdj"));
            double labelX = parseDoubleSafe(extractJsonValue(entry, "labelX"));
            double labelY = parseDoubleSafe(extractJsonValue(entry, "labelY"));
            boolean userPos = Boolean.parseBoolean(extractJsonValue(entry, "userPosition"));

            Gene g = new Gene(name, logFC, pValue, pAdj);
            g.labelX = labelX;
            g.labelY = labelY;
            g.userPosition = userPos;
            genes.add(g);
        }

        // Extract targeted genes
        if (json.contains("\"targetedGenes\"")) {
            String targetBlock = json.split("\"targetedGenes\"\\s*:\\s*\\[")[1].split("\\]")[0];
            for (String entry : targetBlock.split("\",\\s*\"")) {
                String gene = entry.replace("\"", "").trim();
                if (!gene.isEmpty()) {
                    targetedGenes.add(gene);
                    // Initialize all loaded genes as selected by default
                    targetGeneSelections.put(gene, new SimpleBooleanProperty(true));
                }
            }
        }

        // Extract settings
        if (json.contains("\"settings\"")) {
            String settingsBlock = json.split("\"settings\"\\s*:\\s*\\{")[1].split("\\}")[0];
            
            // Basic thresholds
            logFCThreshold = parseDoubleSafe(extractJsonValue(settingsBlock, "logFCThreshold"));
            pValueThreshold = parseDoubleSafe(extractJsonValue(settingsBlock, "pValueThreshold"));
            useAdjustedP = Boolean.parseBoolean(extractJsonValue(settingsBlock, "useAdjustedP"));
            annotate = Boolean.parseBoolean(extractJsonValue(settingsBlock, "annotate"));
            topNUp = (int) parseDoubleSafe(extractJsonValue(settingsBlock, "topNUp"));
            topNDown = (int) parseDoubleSafe(extractJsonValue(settingsBlock, "topNDown"));
            
            // Display settings
            showZeroLine = Boolean.parseBoolean(extractJsonValue(settingsBlock, "showZeroLine"));
            showGrid = Boolean.parseBoolean(extractJsonValue(settingsBlock, "showGrid"));
            showTargets = Boolean.parseBoolean(extractJsonValue(settingsBlock, "showTargets"));
            edgeWidth = parseDoubleSafe(extractJsonValue(settingsBlock, "edgeWidth"));
            thresholdLineWidth = parseDoubleSafe(extractJsonValue(settingsBlock, "thresholdLineWidth"));
            dotSize = parseDoubleSafe(extractJsonValue(settingsBlock, "dotSize"));
            canvasWidth = parseDoubleSafe(extractJsonValue(settingsBlock, "canvasWidth"));
            canvasHeight = parseDoubleSafe(extractJsonValue(settingsBlock, "canvasHeight"));
            fontSize = parseDoubleSafe(extractJsonValue(settingsBlock, "fontSize"));
            
            // Color settings
            colUp = Color.web(extractJsonValue(settingsBlock, "colUp"));
            colDown = Color.web(extractJsonValue(settingsBlock, "colDown"));
            colNS = Color.web(extractJsonValue(settingsBlock, "colNS"));
            colorStyle = extractJsonValue(settingsBlock, "colorStyle");
            if (colorStyle.isEmpty()) colorStyle = "CLASSIC";
            
            // Labels
            plotTitle = extractJsonValue(settingsBlock, "title");
            xLabel = extractJsonValue(settingsBlock, "xLabel");
            yLabel = extractJsonValue(settingsBlock, "yLabel");
        }

        // Refresh UI components
        updateTargetGeneListView();
        calculateAxisRanges();
        
        // Clear any existing annotations for genes no longer targeted
        for (Gene g : genes) {
            if (!targetedGenes.contains(g.name)) {
                g.labelNode = null;
                g.lineNode = null;
                g.userPosition = false;
            }
        }
        
        drawPlot();

        new Alert(Alert.AlertType.INFORMATION, 
            "Project loaded successfully!").show();

    } catch (Exception ex) {
        new Alert(Alert.AlertType.ERROR, 
            "Error loading project: " + ex.getMessage()).show();
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

private double parseDoubleSafe(String s) {
    try { 
        return Double.parseDouble(s); 
    } catch (Exception e) { 
        return 0.0; 
    }
}


private void updateAnnotations() {
    // Clear any existing annotations for removed genes
    for (Gene g : genes) {
        if (!targetedGenes.contains(g.name)) {
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
