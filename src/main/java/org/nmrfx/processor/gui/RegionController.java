/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetRegion;
import org.nmrfx.processor.datasets.peaks.Analyzer;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.gui.spectra.CrossHairs;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.MultipletSelection;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import static org.nmrfx.utils.GUIUtils.affirm;
import static org.nmrfx.utils.GUIUtils.warn;

/**
 *
 * @author brucejohnson
 */
public class RegionController implements Initializable {

    Stage stage = null;
    HBox navigatorToolBar;
    TextField multipletIdField;
    @FXML
    HBox menuBar;
    @FXML
    HBox toolBar;
    @FXML
    HBox regionToolBar;
    @FXML
    HBox peakToolBar;
    @FXML
    HBox fittingToolBar;
    @FXML
    HBox integralToolBar;
    @FXML
    HBox typeToolBar;
    @FXML
    Button splitButton;
    @FXML
    Button splitRegionButton;
    TextField integralField;
    TextField[] couplingFields;
    TextField[] slopeFields;
    private PolyChart chart;
    Optional<DatasetRegion> activeRegion = Optional.empty();
    boolean ignoreCouplingChanges = false;
    ChangeListener<String> patternListener;
    Analyzer analyzer = null;

    public RegionController() {
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        int nCouplings = 5;
        double width1 = 30;
        double width2 = 80;
        double width3 = 60;
        couplingFields = new TextField[nCouplings];
        slopeFields = new TextField[nCouplings];
        initMenus();
        initNavigator(toolBar);
        initTools();
        FXMLController controller = FXMLController.getActiveController();
    }

    public void initMenus() {
        MenuButton menu = new MenuButton("Actions");
        menuBar.getChildren().add(menu);

        MenuItem findRegionsMenuItem = new MenuItem("Find Regions");
        findRegionsMenuItem.setOnAction(e -> findRegions());

        MenuItem pickRegionsMenuItem = new MenuItem("Pick Regions");
        pickRegionsMenuItem.setOnAction(e -> pickRegions());

        MenuItem fitRegionsMenuItem = new MenuItem("Fit Regions");
        fitRegionsMenuItem.setOnAction(e -> fitRegions());

        MenuItem adjustPeakIntegralsMenuItem = new MenuItem("Adjust Peak Integrals");
        adjustPeakIntegralsMenuItem.setOnAction(e -> adjustPeakIntegrals());

        MenuItem clearMenuItem = new MenuItem("Clear");
        clearMenuItem.setOnAction(e -> clearAnalysis());

        MenuItem thresholdMenuItem = new MenuItem("Set Threshold");
        thresholdMenuItem.setOnAction(e -> setThreshold());

        MenuItem clearThresholdMenuItem = new MenuItem("Clear Threshold");
        clearThresholdMenuItem.setOnAction(e -> clearThreshold());

        menu.getItems().addAll(findRegionsMenuItem, pickRegionsMenuItem,
                fitRegionsMenuItem, adjustPeakIntegralsMenuItem,
                clearMenuItem, thresholdMenuItem, clearThresholdMenuItem);
    }

    public void initNavigator(HBox toolBar) {
        this.navigatorToolBar = toolBar;
        multipletIdField = new TextField();
        multipletIdField.setMinWidth(75);
        multipletIdField.setMaxWidth(75);
        multipletIdField.setPrefWidth(75);

        String iconSize = "12px";
        String fontSize = "7pt";
        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> lastRegion());
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> nextRegion());
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> previousRegion());
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(e -> firstRegion());
        buttons.add(bButton);
        Button deleteButton = GlyphsDude.createIconButton(FontAwesomeIcon.BAN, "", fontSize, iconSize, ContentDisplay.GRAPHIC_ONLY);

        // prevent accidental activation when inspector gets focus after hitting space bar on peak in spectrum
        // a second space bar hit would activate
        deleteButton.setOnKeyPressed(e -> e.consume());
        deleteButton.setOnAction(e -> deleteMultiplet());

        toolBar.getChildren().addAll(buttons);
        toolBar.getChildren().add(multipletIdField);
        toolBar.getChildren().add(deleteButton);
        HBox spacer = new HBox();
        toolBar.getChildren().add(spacer);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        multipletIdField.setOnKeyReleased(kE -> {
            if (null != kE.getCode()) {
                switch (kE.getCode()) {
                    case ENTER:
                        gotoClosestRegion(multipletIdField);
                        break;
                    default:
                        break;
                }
            }
        });

    }

    ImageView getIcon(String name) {
        Image imageIcon = new Image("/images/" + name + ".png", true);
        ImageView imageView = new ImageView(imageIcon);
        return imageView;
    }

    void initTools() {
        Font font = new Font(7);
        List<Button> peakButtons = new ArrayList<>();
        List<Button> regionButtons = new ArrayList<>();
        List<Button> multipletButtons = new ArrayList<>();
        List<Button> fitButtons = new ArrayList<>();
        Button button;

        button = new Button("Add", getIcon("region_add"));
        button.setOnAction(e -> addRegion());
        regionButtons.add(button);

        button = new Button("Adjust", getIcon("region_adjust"));
        button.setOnAction(e -> adjustRegion());
        regionButtons.add(button);

        button = new Button("Split", getIcon("region_split"));
        button.setOnAction(e -> splitRegion());
        regionButtons.add(button);

        button = new Button("Delete", getIcon("region_delete"));
        button.setOnAction(e -> removeRegion());
        regionButtons.add(button);

        button = new Button("Add 1", getIcon("peak_add1"));
        button.setOnAction(e -> addPeak());
        peakButtons.add(button);

        button = new Button("Add 2", getIcon("peak_add2"));
        button.setOnAction(e -> addTwoPeaks());
        peakButtons.add(button);

        button = new Button("AutoAdd", getIcon("peak_auto"));
        button.setOnAction(e -> addAuto());
        peakButtons.add(button);

        button = new Button("Delete", getIcon("editdelete"));
        button.setOnAction(e -> removeWeakPeak());
        peakButtons.add(button);

        button = new Button("Fit", getIcon("reload"));
        button.setOnAction(e -> fitSelected());
        fitButtons.add(button);

        button = new Button("BICFit", getIcon("reload"));
        button.setOnAction(e -> objectiveDeconvolution());
        fitButtons.add(button);

        for (Button button1 : regionButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            regionToolBar.getChildren().add(button1);
        }
        for (Button button1 : peakButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            peakToolBar.getChildren().add(button1);
        }
        for (Button button1 : fitButtons) {
            button1.setContentDisplay(ContentDisplay.TOP);
            button1.setFont(font);
            button1.getStyleClass().add("toolButton");
            fittingToolBar.getChildren().add(button1);
        }
        Label integralLabel = new Label("N:");
        integralLabel.setPrefWidth(80);
        integralField = new TextField();
        integralField.setPrefWidth(120);
        integralToolBar.getChildren().addAll(integralLabel, integralField);

        integralField.setOnKeyReleased(k -> {
            if (k.getCode() == KeyCode.ENTER) {
                try {
                    double value = Double.parseDouble(integralField.getText().trim());
                    activeRegion.ifPresent(region -> {
                        double integral = region.getIntegral();
                        Optional<Dataset> datasetOpt = getDataset();
                        datasetOpt.ifPresent(d -> d.setNorm(integral / value));
                        analyzer.normalizePeaks(region, value);
                        refresh();
                    });
                } catch (NumberFormatException nfE) {

                }

            }
        });

        Label peakTypeLabel = new Label("Type: ");
        peakTypeLabel.setPrefWidth(80);

    }

    void adjustPeakIntegrals() {
        activeRegion.ifPresent(region -> {
            chart = PolyChart.getActiveChart();
            Dataset dataset = chart.getDataset();
            double norm = dataset.getNorm() / dataset.getScale();
            double integral = region.getIntegral();
            double value = integral / norm;
            analyzer.normalizePeaks(region, value);
            refresh();
        });
    }

    public Analyzer getAnalyzer() {
        if (analyzer == null) {
            chart = PolyChart.getActiveChart();
            Dataset dataset = chart.getDataset();
            if ((dataset == null) || (dataset.getNDim() > 1)) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("Chart must have a 1D dataset");
                alert.showAndWait();
                return null;
            }
            analyzer = new Analyzer(dataset);
        }
        return analyzer;
    }

    private void analyze1D() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            try {
                analyzer.analyze();
                PeakList peakList = analyzer.getPeakList();
                List<String> peakListNames = new ArrayList<>();
                peakListNames.add(peakList.getName());
                chart.chartProps.setRegions(false);
                chart.chartProps.setIntegrals(true);
                chart.updatePeakLists(peakListNames);
            } catch (IOException ex) {
                Logger.getLogger(AnalystApp.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void findRegions() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            analyzer.calculateThreshold();
            analyzer.getThreshold();
            analyzer.autoSetRegions();
            try {
                analyzer.integrate();
            } catch (IOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
                return;
            }
            chart.chartProps.setRegions(false);
            chart.chartProps.setIntegrals(true);
            chart.refresh();
            lastRegion();
        }
    }

    private void fitRegions() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            try {
                analyzer.fitRegions();
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
                return;
            }
            refresh();
        }
    }

    private void pickRegions() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            analyzer.peakPickRegions();
            PeakList peakList = analyzer.getPeakList();
            List<String> peakListNames = new ArrayList<>();
            peakListNames.add(peakList.getName());
            chart.chartProps.setRegions(false);
            chart.chartProps.setIntegrals(true);
            chart.updatePeakLists(peakListNames);
        }
    }

    private void clearAnalysis() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            if (affirm("Clear Analysis")) {
                PeakList peakList = analyzer.getPeakList();
                PeakList.remove(peakList.getName());
                analyzer.getDataset().setRegions(null);
                chart.chartProps.setRegions(false);
                chart.chartProps.setIntegrals(false);
                chart.refresh();
            }
        }
    }

    private void clearThreshold() {
        if (analyzer != null) {
            analyzer.clearThreshold();
        }
    }

    private void setThreshold() {
        Analyzer analyzer = getAnalyzer();
        if (analyzer != null) {
            CrossHairs crossHairs = chart.getCrossHairs();
            if (!crossHairs.hasCrosshairState("h0")) {
                warn("Threshold", "Must have horizontal crosshair");
                return;
            }
            Double[] pos = crossHairs.getCrossHairPositions(0);
            System.out.println(pos[0] + " " + pos[1]);
            analyzer.setThreshold(pos[1]);
        }
    }

    void deleteMultiplet() {

    }

    public void initMultiplet() {
        TreeSet<DatasetRegion> regions = getRegions();
        if (!regions.isEmpty()) {
            DatasetRegion m = regions.first();
            if (m != null) {
                activeRegion = Optional.of(m);
                RegionController.this.updateRegion(false);
            }
        }
    }

    List<Peak> getPeaks() {
        List<Peak> peaks = Collections.EMPTY_LIST;
        Optional<PeakList> peakListOpt = getPeakList();
        if (peakListOpt.isPresent()) {
            PeakList peakList = peakListOpt.get();
            peaks = peakList.peaks();
        }
        return peaks;
    }

    void updateRegion() {
        RegionController.this.updateRegion(true);
    }

    void updateRegion(boolean resetView) {
        if (activeRegion.isPresent()) {
            DatasetRegion region = activeRegion.get();
            double center = (region.getRegionStart(0) + region.getRegionEnd(0)) / 2;
            multipletIdField.setText(String.format("%.3f", center));
            if (resetView) {
                refreshPeakView(region);
            }
            double scale = getDataset().get().getNorm();
            double value = region.getIntegral() / scale;
            integralField.setText(String.format("%.2f", value));
//            if (multiplet.isGenericMultiplet()) {
//                splitButton.setDisable(true);
//            } else {
//                splitButton.setDisable(false);
//            }
        } else {
            multipletIdField.setText("");
        }
    }

    void firstRegion() {
        TreeSet<DatasetRegion> regions = getRegions();
        if (!regions.isEmpty()) {
            activeRegion = Optional.of(regions.first());
        } else {
            activeRegion = Optional.empty();
        }
        updateRegion();
    }

    void previousRegion() {
        if (activeRegion.isPresent()) {
            TreeSet<DatasetRegion> regions = getRegions();
            DatasetRegion region = regions.lower(activeRegion.get());
            if (region == null) {
                region = regions.first();
            }
            activeRegion = Optional.of(region);
            updateRegion();
        } else {
            lastRegion();
        }
    }

    void nextRegion() {
        if (activeRegion.isPresent()) {
            TreeSet<DatasetRegion> regions = getRegions();
            DatasetRegion region = regions.higher(activeRegion.get());
            if (region == null) {
                region = regions.first();
            }
            activeRegion = Optional.of(region);
            updateRegion();
        } else {
            lastRegion();
        }
    }

    void lastRegion() {
        TreeSet<DatasetRegion> regions = getRegions();
        if (!regions.isEmpty()) {
            activeRegion = Optional.of(regions.last());
        } else {
            activeRegion = Optional.empty();
        }
        updateRegion();
    }

    void gotoClosestRegion(TextField textField) {
        try {
            double center = Double.valueOf(textField.getText());
            DatasetRegion region = DatasetRegion.findClosest(getRegions(), center, 0);
            if (region != null) {
                activeRegion = Optional.of(region);
                updateRegion();
            }

        } catch (NumberFormatException nfe) {

        }

    }

    public static RegionController create() {
        FXMLLoader loader = new FXMLLoader(MinerController.class.getResource("/fxml/RegionsScene.fxml"));
        RegionController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((BorderPane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<RegionController>getController();
            controller.stage = stage;
            stage.setTitle("Regions");
            stage.setScene(scene);
            stage.setMinWidth(200);
            stage.setMinHeight(250);
            stage.show();
            stage.toFront();
            controller.chart = controller.getChart();
            controller.initMultiplet();

        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }
        return controller;
    }

    public Stage getStage() {
        return stage;
    }

    Optional<PeakList> getPeakList() {
        Optional<PeakList> peakListOpt = Optional.empty();
        List<PeakListAttributes> attrs = chart.getPeakListAttributes();
        if (!attrs.isEmpty()) {
            peakListOpt = Optional.of(attrs.get(0).getPeakList());
        } else {
            Analyzer analyzer = getAnalyzer();
            PeakList peakList = analyzer.getPeakList();
            if (peakList != null) {
                List<String> peakListNames = new ArrayList<>();
                peakListNames.add(peakList.getName());
                chart.updatePeakLists(peakListNames);
                attrs = chart.getPeakListAttributes();
                peakListOpt = Optional.of(attrs.get(0).getPeakList());
            }
        }
        return peakListOpt;

    }

    Optional<Dataset> getDataset() {
        Optional<Dataset> datasetOpt = Optional.empty();
        Dataset dataset = getAnalyzer().getDataset();
        if (dataset != null) {
            datasetOpt = Optional.of(dataset);
        }
        return datasetOpt;
    }

    TreeSet<DatasetRegion> getRegions() {
        Optional<Dataset> datasetOpt = getDataset();
        TreeSet<DatasetRegion> regions;
        if (datasetOpt.isPresent()) {
            regions = datasetOpt.get().getRegions();
            if (regions == null) {
                regions = new TreeSet<>();
                datasetOpt.get().setRegions(regions);
            }
        } else {
            regions = new TreeSet<>();
        }
        return regions;
    }

    PolyChart getChart() {
        FXMLController controller = FXMLController.getActiveController();
        PolyChart activeChart = controller.getActiveChart();
        return activeChart;
    }

    void refresh() {
        chart.refresh();
        RegionController.this.updateRegion(false);

    }

    List<MultipletSelection> getMultipletSelection() {
        FXMLController controller = FXMLController.getActiveController();
        List<MultipletSelection> multiplets = chart.getSelectedMultiplets();
        return multiplets;
    }

    public void fitSelected() {
        Analyzer analyzer = getAnalyzer();
        activeRegion.ifPresent(m -> {
            try {
                Optional<Double> result = analyzer.fitRegion(m);
                refresh();
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        });
    }

    public void splitRegion() {
        double ppm = chart.getVerticalCrosshairPositions()[0];
        activeRegion.ifPresent(r -> r.split(ppm - 0.01, ppm + 0.01));
        chart.refresh();
    }

    public void adjustRegion() {
        Analyzer analyzer = getAnalyzer();
        double ppm0 = chart.getVerticalCrosshairPositions()[0];
        double ppm1 = chart.getVerticalCrosshairPositions()[1];
        analyzer.removeRegion((ppm0 + ppm1) / 2);
        analyzer.addRegion(ppm0, ppm1);
        RegionController.this.updateRegion(false);
        chart.refresh();
    }

    public void addRegion() {
        Analyzer analyzer = getAnalyzer();
        double ppm0 = chart.getVerticalCrosshairPositions()[0];
        double ppm1 = chart.getVerticalCrosshairPositions()[1];
        analyzer.addRegion(ppm0, ppm1);
        RegionController.this.updateRegion(false);
        chart.refresh();

    }

    public void removeRegion() {
        activeRegion.ifPresent(region -> {
            TreeSet<DatasetRegion> regions = getRegions();
            DatasetRegion newRegion = regions.lower(region);
            if (newRegion == region) {
                newRegion = regions.higher(region);
            }
            regions.remove(region);
            activeRegion = Optional.of(newRegion);
            chart.refresh();
        });
    }

    public void rms() {
        activeRegion.ifPresent(region -> {
            try {
                Optional<Double> result = analyzer.measureRegion(region, "rms");
                if (result.isPresent()) {
                    System.out.println("rms " + result.get());
                }
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        });
    }

    public void objectiveDeconvolution() {
        activeRegion.ifPresent(region -> {
            try {
                analyzer.objectiveDeconvolution(region);
                chart.refresh();
                refresh();
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        });
    }

    public void addAuto() {
        activeRegion.ifPresent(region -> {
            try {
                Optional<Double> result = analyzer.measureRegion(region, "maxdev");
                if (result.isPresent()) {
                    System.out.println("dev pos " + result.get());
                    analyzer.addPeaksToRegion(region, result.get());
                    chart.refresh();
                    refresh();
                }
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }

        });
    }

    public void addPeak() {
        addPeaks(false);
    }

    public void addTwoPeaks() {
        addPeaks(true);
    }

    public void addPeaks(boolean both) {
        activeRegion.ifPresent(region -> {
            double ppm1 = chart.getVerticalCrosshairPositions()[0];
            double ppm2 = chart.getVerticalCrosshairPositions()[1];
            try {
                if (both) {
                    analyzer.addPeaksToRegion(region, ppm1, ppm2);
                } else {
                    analyzer.addPeaksToRegion(region, ppm1);
                }
                chart.refresh();
                refresh();
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        });
    }

    void removeWeakPeak() {
        activeRegion.ifPresent(region -> {
            try {
                analyzer.removeWeakPeaksInRegion(region, 1);
                refresh();
            } catch (Exception ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        });
    }

    public void refreshPeakView(DatasetRegion region) {
        boolean resize = false;
        if (region != null) {
            double start = region.getRegionStart(0);
            double end = region.getRegionEnd(0);
            double center = (start + end) / 2.0;
            double bounds = Math.abs(start - end);
            double widthScale = 2.5;
            if ((chart != null) && !chart.getDatasetAttributes().isEmpty()) {
                DatasetAttributes dataAttr = (DatasetAttributes) chart.getDatasetAttributes().get(0);
                Double[] ppms = {center};
                Double[] widths = {bounds * widthScale};
                if (resize && (widthScale > 0.0)) {
                    chart.moveTo(ppms, widths);
                } else {
                    chart.moveTo(ppms);
                }
            }
        }
    }

}
