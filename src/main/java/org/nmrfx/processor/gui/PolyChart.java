/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmrfx.processor.gui;

import org.nmrfx.processor.gui.spectra.NMRAxis;
import org.nmrfx.processor.gui.spectra.DrawSpectrum;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.RegionData;
import org.nmrfx.processor.datasets.peaks.Multiplet;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.gui.spectra.CrossHairManager;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.DatasetAttributes.AXMODE;
import org.nmrfx.processor.gui.spectra.DrawPeaks;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.processor.gui.spectra.SliceAttributes;
import org.nmrfx.processor.gui.spectra.SpectrumWriter;
import org.nmrfx.processor.gui.controls.ConsoleUtil;
import java.io.File;
import java.util.ArrayList;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.chart.XYChart.Data;
import javafx.collections.ObservableList;
import javafx.scene.shape.Path;
import javafx.scene.shape.Line;
import javafx.beans.NamedArg;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.RotateEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.paint.Color;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.ValueAxis;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.scene.shape.StrokeLineCap;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.processor.datasets.peaks.PeakEvent;
import org.nmrfx.processor.datasets.peaks.PeakFitException;
import org.nmrfx.processor.datasets.peaks.PeakListener;
import org.nmrfx.processor.datasets.peaks.PeakNeighbors;
import static org.nmrfx.processor.gui.PolyChart.DISDIM.TwoD;
import org.nmrfx.processor.gui.graphicsio.GraphicsIOException;
import org.nmrfx.processor.gui.spectra.GestureBindings;
import org.nmrfx.processor.gui.spectra.KeyBindings;
import org.nmrfx.processor.gui.spectra.MouseBindings;

public class PolyChart<X, Y> extends XYChart<X, Y> implements PeakListener {

    /**
     * @return the hasMiddleMouseButton
     */
    public boolean getHasMiddleMouseButton() {
        return hasMiddleMouseButton;
    }

    /**
     * @param hasMiddleMouseButton the hasMiddleMouseButton to set
     */
    public void setHasMiddleMouseButton(boolean hasMiddleMouseButton) {
        this.hasMiddleMouseButton = hasMiddleMouseButton;
    }

    /**
     * @return the mouseX
     */
    public double getMouseX() {
        return mouseBindings.getMouseX();
    }

    /**
     * @return the mouseY
     */
    public double getMouseY() {
        return mouseBindings.getMouseY();
    }

    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;
    public static final int CROSSHAIR_TOL = 25;
    static CrossHairManager crossHairManager = new CrossHairManager();
    public static final ObservableList<PolyChart> charts = FXCollections.observableArrayList();
    static PolyChart activeChart = null;

    ArrayList<Double> dList = new ArrayList<>();
    ArrayList<Double> nList = new ArrayList<>();
    ArrayList<Double> bcList = new ArrayList<>();
    Canvas canvas;
    Canvas peakCanvas;
    Canvas annoCanvas = null;
    Path bcPath = new Path();
    Line[][] crossHairLines = new Line[2][2];
    double[][] crossHairPositions = new double[2][2];
    boolean[][] crossHairStates = new boolean[2][2];
    int crossHairNumH = 0;
    int crossHairNumV = 0;
    private boolean hasMiddleMouseButton = false;
    final NMRAxis xAxis;
    final NMRAxis yAxis;
    NMRAxis[] axes = new NMRAxis[2];
    final Region plotBackground;
    final Group plotContent;
    final DrawSpectrum drawSpectrum;
    final DrawPeaks drawPeaks;
    SliceAttributes sliceAttributes = new SliceAttributes();
    DatasetAttributes lastDatasetAttr = null;
    List<CanvasAnnotation> canvasAnnotations = new ArrayList<>();
    private static int lastId = 0;
    private final int id;

    int iVec = 0;
//    Vec vec;
    FileProperty datasetFileProp = new FileProperty();
//    DatasetAttributes datasetAttributes = null;
    ObservableList<DatasetAttributes> datasetAttributesList = FXCollections.observableArrayList();
    ObservableList<PeakListAttributes> peakListAttributesList = FXCollections.observableArrayList();
    FXMLController controller;
    ProcessorController processorController = null;
    BooleanProperty sliceStatus = new SimpleBooleanProperty(true);
    BooleanProperty peakStatus = new SimpleBooleanProperty(true);
    double level = 1.0;
// fixme 15 should be set automatically and correctly
    double[][] chartPhases = new double[2][15];
    double[] chartPivots = new double[15];
    int phaseDim = 0;
    int phaseAxis = 0;
    double phaseFraction = 0.0;

    @Override
    public void peakListChanged(final PeakEvent peakEvent) {
        if (Platform.isFxApplicationThread()) {
            respondToPeakListChange(peakEvent);
        } else {
            Platform.runLater(() -> {
                respondToPeakListChange(peakEvent);
            }
            );
        }

    }

    public void setPeakStatus(boolean state) {
        peakStatus.set(state);
    }

    private void respondToPeakListChange(PeakEvent peakEvent) {
        // if mouse down we could be dragging peak which will itself cause redraw
        //   no need to call this
        if (mouseBindings.isMouseDown()) {
            return;
        }
        Object source = peakEvent.getSource();
        boolean draw = false;
        PeakListAttributes activeAttr = null;
        if (peakStatus.get()) {
            if (source instanceof PeakList) {
                PeakList peakList = (PeakList) source;
                for (PeakListAttributes peakListAttr : peakListAttributesList) {
                    if (peakListAttr.getPeakList() == peakList) {
                        activeAttr = peakListAttr;
                        draw = true;
                    }
                }
            }
        }
        if (true) {
            drawPeakLists(false);
            Set<Peak> peaks = activeAttr.getSelectedPeaks();
            if (!peaks.isEmpty()) {
                drawSelectedPeaks(activeAttr);
            }
        }
    }

    public enum DISDIM {
        OneDX, OneDY, TwoD;
    };
    ObjectProperty<DISDIM> disDimProp = new SimpleObjectProperty(TwoD);
    SpectrumMenu specMenu;
    KeyBindings keyBindings;
    MouseBindings mouseBindings;
    GestureBindings gestureBindings;

    AXMODE axModes[] = {AXMODE.PPM, AXMODE.PPM};
    Map<String, Integer> syncGroups = new HashMap<>();
    static int nSyncGroups = 0;

    public static double overlapScale = 1.0;

    /**
     * Construct a new PolyChart with the given axis.
     *
     * @param xAxis The x axis to use
     * @param yAxis The y axis to use
     */
    public PolyChart() {
        super((Axis) new NMRAxis(0, 2048, 16), (Axis) new NMRAxis(-1, 1, 0.5));
        setData(FXCollections.<Series<X, Y>>observableArrayList());
        setVerticalZeroLineVisible(false);
        setHorizontalZeroLineVisible(false);
        xAxis = (NMRAxis) getXAxis();
        yAxis = (NMRAxis) getYAxis();
        axes[0] = xAxis;
        axes[1] = yAxis;
        plotBackground = (Region) lookup(".chart-plot-background");
        plotContent = (Group) lookup(".plot-content");
        crossHairLines[0][0] = new Line(0, 50, 400, 50);
        crossHairLines[0][1] = new Line(100, 0, 100, 400);
        crossHairLines[1][0] = new Line(0, 50, 400, 50);
        crossHairLines[1][1] = new Line(100, 0, 100, 400);
        crossHairStates[0][0] = false;
        crossHairStates[0][1] = true;
        crossHairStates[1][0] = false;
        crossHairStates[1][1] = true;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                plotContent.getChildren().add(crossHairLines[i][j]);
                crossHairLines[i][j].setVisible(false);
                crossHairLines[i][j].setStrokeWidth(0.5);
                crossHairLines[i][j].setMouseTransparent(true);
                if (i == 0) {
                    crossHairLines[i][j].setStroke(Color.BLACK);
                } else {
                    crossHairLines[i][j].setStroke(Color.RED);

                }
            }
        }
        loadData();
        drawSpectrum = new DrawSpectrum(axes, canvas);
        drawPeaks = new DrawPeaks(this, peakCanvas);
        setHandlers(canvas);
        setDragHandlers(this);
        xAxis.lowerBoundProperty().addListener(new AxisChangeListener(this, 0, 0));
        xAxis.upperBoundProperty().addListener(new AxisChangeListener(this, 0, 1));
        yAxis.lowerBoundProperty().addListener(new AxisChangeListener(this, 1, 0));
        yAxis.upperBoundProperty().addListener(new AxisChangeListener(this, 1, 1));
        charts.add(this);
        activeChart = this;
        setCursor(Cursor.CROSSHAIR);
        id = getNextId();
        MapChangeListener<String, PeakList> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> {
            purgeInvalidPeakListAttributes();
        };
        PeakList.peakListTable.addListener(mapChangeListener);
        keyBindings = new KeyBindings(this);
        mouseBindings = new MouseBindings(this);
        gestureBindings = new GestureBindings(this);
        specMenu = new SpectrumMenu(this);

    }

    /**
     * Construct a new PolyChart with the given axis.
     *
     * @param xAxis The x axis to use
     * @param yAxis The y axis to use
     */
    public PolyChart(@NamedArg("xAxis") Axis<X> xAxis, @NamedArg("yAxis") Axis<Y> yAxis) {
        this(xAxis, yAxis, FXCollections.<Series<X, Y>>observableArrayList());
    }

    /**
     * Construct a new PolyChart with the given axis and data.
     *
     * @param xAxis The x axis to use
     * @param yAxis The y axis to use
     * @param data The data to use, this is the actual list used so any changes to it will be reflected in the chart
     */
    public PolyChart(@NamedArg("xAxis") Axis<X> xAxis, @NamedArg("yAxis") Axis<Y> yAxis, @NamedArg("data") ObservableList<Series<X, Y>> data) {
        super(xAxis, yAxis);
        setData(data);
        setVerticalZeroLineVisible(false);
        setHorizontalZeroLineVisible(false);
        plotBackground = (Region) lookup(".chart-plot-background");
        plotContent = (Group) lookup(".plot-content");

        this.xAxis = (NMRAxis) getXAxis();
        this.yAxis = (NMRAxis) getYAxis();
        axes[0] = this.xAxis;
        axes[1] = this.yAxis;
        drawSpectrum = new DrawSpectrum(axes, canvas);
        drawPeaks = new DrawPeaks(this, peakCanvas);

        charts.add(this);
        activeChart = this;
        setCursor(Cursor.CROSSHAIR);
        id = getNextId();
        keyBindings = new KeyBindings(this);
    }

    private synchronized int getNextId() {
        lastId++;
        return (lastId);
    }

    public String getName() {
        return String.valueOf(id);
    }

    public FXMLController getFXMLController() {
        return controller;
    }

    public void close() {
        charts.remove(this);
        controller.removeChart(this);
        if (this == activeChart) {
            if (charts.isEmpty()) {
                activeChart = null;
            } else {
                activeChart = charts.get(0);
            }
        }
    }

    public void focus() {
        getController().stage.requestFocus();
        setFocused(true);
    }

//pic.addEventHandler(MouseEvent.MOUSE_CLICKED,
//    new EventHandler<MouseEvent>() {
//        @Override public void handle(MouseEvent e) {
//            if (e.getButton() == MouseButton.SECONDARY)  
//                cm.show(pic, e.getScreenX(), e.getScreenY());
//        }
//});
    final protected void setHandlers(Node mouseNode) {
        setFocusTraversable(false);
        mouseNode.setOnContextMenuRequested((ContextMenuEvent event) -> {
            specMenu.show(mouseNode.getScene().getWindow(), event.getScreenX(), event.getScreenY());
        });
        mouseNode.setOnKeyPressed((KeyEvent keyEvent) -> {
            keyBindings.keyPressed(keyEvent);
        });

        mouseNode.setOnKeyReleased((KeyEvent keyEvent) -> {
            keyBindings.keyReleased(keyEvent);
        });

        mouseNode.setOnKeyTyped((KeyEvent keyEvent) -> {
            keyBindings.keyTyped(keyEvent);
        });

        mouseNode.setOnMouseDragged((MouseEvent mouseEvent) -> {
            mouseBindings.mouseDragged(mouseEvent);
        });

        mouseNode.setOnMousePressed((MouseEvent mouseEvent) -> {
            mouseBindings.mousePressed(mouseEvent);
        });

        mouseNode.setOnMouseMoved((MouseEvent mouseEvent) -> {
            mouseBindings.mouseMoved(mouseEvent);
        });

        mouseNode.setOnMouseReleased((MouseEvent mouseEvent) -> {
            mouseBindings.mouseReleased(mouseEvent);
        });

        mouseNode.setOnRotate((RotateEvent rEvent) -> {
            gestureBindings.rotate(rEvent);
        });
        mouseNode.setOnRotationFinished((RotateEvent rEvent) -> {
            gestureBindings.rotationFinished(rEvent);
        });
        mouseNode.setOnZoom((Event event) -> {
            gestureBindings.zoom(event);
        });
        mouseNode.setOnScroll((ScrollEvent event) -> {
            gestureBindings.scroll(event);
        });
        mouseNode.focusedProperty().addListener(e -> setActiveChart());
    }

    final protected void setDragHandlers(Node mouseNode) {

        mouseNode.setOnDragOver((DragEvent event) -> {
            mouseDragOver(event);
        });
        mouseNode.setOnDragDropped((DragEvent event) -> {
            mouseDragDropped(event);
        });
        mouseNode.setOnDragExited((DragEvent event) -> {
            mouseNode.setStyle("-fx-border-color: #C6C6C6;");
        });
    }

    private void mouseDragDropped(final DragEvent e) {
        final Dragboard db = e.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            success = true;
            // Only get the first file from the list
            final List<File> files = db.getFiles();
            controller.setActiveChart(this);
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    try {
                        boolean isDataset = NMRDataUtil.isDatasetFile(files.get(0).getAbsolutePath()) != null;
                        PolyChart dropChart = (PolyChart) e.getGestureTarget();
                        controller.setActiveChart(dropChart);
                        if (isDataset) {
                            boolean appendFile = true;

                            for (File file : files) {
                                controller.openFile(file.getAbsolutePath(), false, appendFile);
                                appendFile = true;
                            }
                        } else {
                            controller.openFile(files.get(0).getAbsolutePath(), true, false);
                        }
                    } catch (Exception e) {

                    }
                }
            });
        } else if (db.hasString()) {
            String contentString = db.getString();
            String[] items = contentString.split("\n");
            if (items.length > 0) {
                Dataset dataset = Dataset.getDataset(items[0]);
                if (dataset != null) {
                    success = true;
                    Platform.runLater(() -> {
                        PolyChart dropChart = (PolyChart) e.getGestureTarget();
                        controller.setActiveChart(dropChart);
                        for (String item : items) {
                            Dataset dataset1 = Dataset.getDataset(item);
                            if (dataset1 != null) {
                                controller.addDataset(dataset1, true, false);
                            }
                        }
                    });
                }
            }
        } else {
            System.out.println("no files");
        }
        e.setDropCompleted(success);
        e.consume();
    }

    private void mouseDragOver(final DragEvent e) {
        final Dragboard db = e.getDragboard();

        List<File> files = db.getFiles();
        if (db.hasFiles()) {
            if (files.size() > 0) {
                boolean isAccepted;
                try {
                    isAccepted = NMRDataUtil.isFIDDir(files.get(0).getAbsolutePath()) != null;
                    if (!isAccepted) {
                        isAccepted = NMRDataUtil.isDatasetFile(files.get(0).getAbsolutePath()) != null;
                    }
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                    isAccepted = false;
                }
                if (isAccepted) {
                    this.setStyle("-fx-border-color: green;"
                            + "-fx-border-width: 1;");
                    e.acceptTransferModes(TransferMode.COPY);
                }
            }
        } else if (db.hasString()) {
            String contentString = db.getString();
            String[] items = contentString.split("\n");
            if (items.length > 0) {
                Dataset dataset = Dataset.getDataset(items[0]);
                if (dataset != null) {
                    this.setStyle("-fx-border-color: green;"
                            + "-fx-border-width: 1;");
                    e.acceptTransferModes(TransferMode.COPY);
                }
            }
        } else {
            e.consume();
        }
    }

    public void setActiveChart() {
        activeChart = this;
        controller.setActiveChart(this);
    }

    public static PolyChart getActiveChart() {
        return activeChart;
    }

    public FXMLController getController() {
        return controller;
    }

    public void handleCrossHair(MouseEvent mEvent, boolean selectCrossNum) {
        if (mEvent.isPrimaryButtonDown()) {
            if (selectCrossNum) {
                if (!hasMiddleMouseButton) {
                    crossHairNumH = getCrossHairNum(mEvent.getX(), mEvent.getY(), HORIZONTAL);
                    crossHairNumV = getCrossHairNum(mEvent.getX(), mEvent.getY(), VERTICAL);
                } else {
                    crossHairNumH = 0;
                    crossHairNumV = 0;
                }
            }
            moveCrosshair(crossHairNumH, HORIZONTAL, mEvent.getY());
            moveCrosshair(crossHairNumV, VERTICAL, mEvent.getX());
        } else if (mEvent.isMiddleButtonDown()) {
            hasMiddleMouseButton = true;
            moveCrosshair(1, HORIZONTAL, mEvent.getY());
            moveCrosshair(1, VERTICAL, mEvent.getX());
        }
    }

    public void dragBox(double[] dragStart, double x, double y) {
        GraphicsContext annoGC = annoCanvas.getGraphicsContext2D();
        double width = annoCanvas.getWidth();
        double height = annoCanvas.getHeight();
        double xStart = dragStart[0];
        double yStart = dragStart[1];
        System.out.println(xStart + " " + yStart + " " + x + " " + y);
        annoGC.clearRect(0, 0, width, height);
        double x1, y1, x2, y2, w, h;
        if (x > xStart) {
            x1 = xStart;
            w = x - x1;
        } else {
            x1 = x;
            w = xStart - x;
        }
        if (y > yStart) {
            y1 = yStart;
            h = y - y1;
        } else {
            y1 = y;
            h = yStart - y;
        }
        Color color = new Color(1.0, 1.0, 0.0, 0.3);
        annoGC.setFill(color);
        annoGC.fillRect(x1, y1, w, h);
    }

    private void swapDouble(double[] values) {
        if (values[0] > values[1]) {
            double hold = values[0];
            values[0] = values[1];
            values[1] = hold;
        }
    }

    public void finishBox(boolean selectMode, double[] dragStart, double x, double y) {
        GraphicsContext annoGC = annoCanvas.getGraphicsContext2D();
        double width = annoCanvas.getWidth();
        double height = annoCanvas.getHeight();
        annoGC.clearRect(0, 0, width, height);
        ValueAxis xAxis = (ValueAxis) getXAxis();
        double[][] limits;
        if (is1D()) {
            limits = new double[1][2];
        } else {
            limits = new double[2][2];
        }
        limits[0][0] = xAxis.getValueForDisplay(dragStart[0]).doubleValue();
        limits[0][1] = xAxis.getValueForDisplay(x).doubleValue();
        swapDouble(limits[0]);
        if (!is1D()) {
            ValueAxis yAxis = (ValueAxis) getYAxis();
            limits[1][0] = yAxis.getValueForDisplay(y).doubleValue();
            limits[1][1] = yAxis.getValueForDisplay(dragStart[1]).doubleValue();
            swapDouble(limits[1]);
        }

        if (!selectMode) {
            setAxis(0, limits[0][0], limits[0][1]);
            if (!is1D()) {
                setAxis(1, limits[1][0], limits[1][1]);
            }
        } else {
            drawPeakLists(false);
            List<Peak> selPeaks = new ArrayList<>();
            for (PeakListAttributes peakAttr : peakListAttributesList) {
                List<Peak> peaks = peakAttr.selectPeaksInRegion(limits);
                drawSelectedPeaks(peakAttr);
                selPeaks.addAll(peaks);
            }
            if (controller == FXMLController.activeController) {
                controller.selPeaks.set(selPeaks);
            }

        }
    }

    public NMRAxis getAxis(int iDim) {
        return iDim < axes.length ? axes[iDim] : null;
    }

    public AXMODE getAxMode(int iDim) {
        return iDim < axModes.length ? axModes[iDim] : null;
    }

    public void setController(FXMLController controller) {
        this.controller = controller;
        drawSpectrum.setController(controller);
    }

    public void setProcessorController(ProcessorController controller) {
        this.processorController = controller;
    }

    public void setCrossHairState(boolean h1, boolean v1, boolean h2, boolean v2) {
        crossHairStates[0][0] = h1;
        crossHairStates[0][1] = v1;
        crossHairStates[1][0] = h2;
        crossHairStates[1][1] = v2;
    }

    public Optional<DatasetAttributes> getFirstDatasetAttributes() {
        Optional<DatasetAttributes> firstDatasetAttr = Optional.empty();
        if (!datasetAttributesList.isEmpty()) {
            firstDatasetAttr = Optional.of(datasetAttributesList.get(0));
        }
        return firstDatasetAttr;
    }

    public Dataset getDataset() {
        Dataset dataset = null;
        if (!datasetAttributesList.isEmpty()) {
            dataset = datasetAttributesList.get(0).getDataset();
        }
        return dataset;
    }

    void removeAllDatasets() {
        if (!datasetAttributesList.isEmpty()) {
            lastDatasetAttr = (DatasetAttributes) ((DatasetAttributes) datasetAttributesList.get(0)).clone();
        }
        datasetAttributesList.clear();
    }

    void remove(Dataset dataset) {
        for (Iterator<DatasetAttributes> iterator = datasetAttributesList.iterator(); iterator.hasNext();) {
            DatasetAttributes dataAttr = iterator.next();
            if (dataset == dataAttr.getDataset()) {
                lastDatasetAttr = (DatasetAttributes) dataAttr.clone();
                iterator.remove();
            }
        }
    }

    File getDatasetFile() {
        File file = null;
        Dataset dataset = getDataset();
        if (dataset != null) {
            file = dataset.getFile();
        }
        return file;
    }

    protected int getDataSize(int dimNum) {
        int dataSize = 0;
        Dataset dataset = getDataset();
        if (dataset != null) {
            dataSize = dataset.getSize(dimNum);
        }
        return dataSize;
    }

    public void zoom(double factor) {
        ConsoleUtil.runOnFxThread(() -> {
            Dataset dataset = getDataset();
            if (dataset != null) {
                xZoom(factor);
                if (!is1D()) {
                    yZoom(factor);
                }
                layoutPlotChildren();
            }
        }
        );
    }

    protected void xZoom(double factor) {
        double min = xAxis.getLowerBound();
        double max = xAxis.getUpperBound();
        double range = max - min;
        double center = (max + min) / 2.0;
        if (factor > 2.0) {
            factor = 2.0;
        } else if (factor < 0.5) {
            factor = 0.5;
        }
        range = range / factor;
        // fixme add check for too small range
        min = center - range / 2.0;
        max = center + range / 2.0;
        double[] limits = getRange(0, min, max);
        setXAxis(limits[0], limits[1]);

//        double[] limits = datasetAttributes.checkLimits(axModes[0], 0, min, max);
//        setXAxis(limits[0], limits[1]);
    }

    protected double[] getRange(int axis, double min, double max) {
        double[] limits = getRange(axis);
        if (min > limits[0]) {
            limits[0] = min;
        }
        if (max < limits[1]) {
            limits[1] = max;
        }
        return limits;
    }

    protected double[] getRange(int axis) {
        double[] limits = {Double.MAX_VALUE, Double.NEGATIVE_INFINITY};
        datasetAttributesList.stream().forEach(d -> d.checkRange(axModes[axis], axis, limits));
        return limits;
    }

    public void scroll(double x, double y) {

        scrollXAxis(x);
        scrollYAxis(y);

        layoutPlotChildren();
    }

    protected void scrollXAxis(double x) {
        double scale = xAxis.getScale();
        if (axModes[0] == AXMODE.PPM) {
            scale *= -1.0;
        }
        double min = xAxis.getLowerBound();
        double max = xAxis.getUpperBound();
        double range = max - min;
        double center = (max + min) / 2.0;
        center -= x / scale;
        // fixme add check for too small range
        min = center - range / 2.0;
        max = center + range / 2.0;
        double[] limits = getRange(0, min, max);

        setXAxis(limits[0], limits[1]);
    }

    protected void scrollYAxis(double y) {
        double scale = yAxis.getScale();
        double min = yAxis.getLowerBound();
        double max = yAxis.getUpperBound();
        double range = max - min;
        double center = (max + min) / 2.0;

        if (is1D()) {
            center -= y / scale;
            min = center - range / 2.0;
            max = center + range / 2.0;
            double f = (0.0 - min) / (max - min);
            datasetAttributesList.stream().forEach(dataAttr -> {
                dataAttr.setOffset(f);
            });

            setYAxis(min, max);
        } else {
            center += y / scale;
            min = center - range / 2.0;
            max = center + range / 2.0;
            double[] limits = getRange(1, min, max);
            setYAxis(limits[0], limits[1]);
        }
    }

    protected void adjustScale(double factor) {
        datasetAttributesList.stream().forEach(dataAttr -> {
            adjustScale(dataAttr, factor);
        });
        layoutPlotChildren();

    }

    protected void adjustScale(DatasetAttributes dataAttr, double factor) {
        Dataset dataset = dataAttr.getDataset();
        if (is1D()) {
            double min = yAxis.getLowerBound();
            double max = yAxis.getUpperBound();
            double range = max - min;
            double f = (0.0 - min) / range;
            range = range * factor;
            min = -f * range;
            max = min + range;
            double oldLevel = dataAttr.getLvl();
            dataAttr.setLvl(oldLevel * factor);
            setYAxis(min, max);
        } else if (dataset != null) {
            double scale = factor;
            if (scale > 2.0) {
                scale = 2.0;
            } else if (scale < 0.5) {
                scale = 0.5;
            }
            double oldLevel = dataAttr.getLvl();
            dataAttr.setLvl(oldLevel * scale);
        }
    }

    public void scaleY(double y) {
        datasetAttributesList.stream().forEach(dataAttr -> {
            Dataset dataset = dataAttr.getDataset();
            if (is1D()) {
                double min = yAxis.getLowerBound();
                double max = yAxis.getUpperBound();
                double scale = yAxis.getScale();
                double range = max - min;
                double f = (0.0 - min) / range;
                range -= y / scale;
                min = -f * range;
                max = min + range;
                double oldLevel = dataAttr.getLvl();
                dataAttr.setLvl(oldLevel * scale);
                setYAxis(min, max);
            } else if (dataset != null) {
                double scale = (y / 100.0 + 1.0);
                if (scale > 2.0) {
                    scale = 2.0;
                } else if (scale < 0.5) {
                    scale = 0.5;
                }
                double oldLevel = dataAttr.getLvl();
                dataAttr.setLvl(oldLevel * scale);
            }
        });
        layoutPlotChildren();
    }

    protected void yZoom(double factor) {
        double min = yAxis.getLowerBound();
        double max = yAxis.getUpperBound();
        double range = max - min;
        double center = (max + min) / 2.0;
        if (factor > 2.0) {
            factor = 2.0;
        } else if (factor < 0.5) {
            factor = 0.5;
        }
        range = range / factor;
        // fixme add check for too small range
        min = center - range / 2.0;
        max = center + range / 2.0;
        double[] limits = getRange(1, min, max);

        setYAxis(limits[0], limits[1]);

    }

    public int getAxisNum(String ax) {
        int axNum = -1;
        char ch = ax.charAt(0);
        if ((ax.length() == 1) && Character.isLetter(ch) && Character.isLowerCase(ch)) {
            axNum = ch - 'x';
            if (axNum < 0) {
                axNum = ch - 'a' + 3;
            }
        }
        if (axNum == -1) {
            throw new IllegalArgumentException("Invalid axis name: \"" + ax + "\"");
        }

        return axNum;
    }

    public int getAxisForLabel(String ax) {
        int axNum = getDimNames().indexOf(ax);
        if (axNum == -1) {
            throw new IllegalArgumentException("Invalid axis name: \"" + ax + "\"");
        }
        return axNum;
    }

    protected void setXAxis(double min, double max) {
        double range = max - min;
        double delta = range / 10;
        xAxis.setLowerBound(min);
        xAxis.setUpperBound(max);
        xAxis.setTickUnit(delta);
    }

    protected void setYAxis(double min, double max) {
        double range = max - min;
        double delta = range / 10;
        yAxis.setLowerBound(min);
        yAxis.setUpperBound(max);
        yAxis.setTickUnit(delta);
    }

    public void setAxis(int iAxis, double min, double max) {
        if (axes.length > iAxis) {
            NMRAxis axis = axes[iAxis];
            double range = max - min;
            double delta = range / 10;
            axis.setLowerBound(min);
            axis.setUpperBound(max);
            axis.setTickUnit(delta);
        }
    }

    public void incrementRow(int amount) {
        DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
        Dataset dataset = getDataset();
        if (dataset.getNDim() < 2) {
            return;
        }
        int[] drawList = datasetAttributes.drawList;
        if ((drawList == null) || (drawList.length == 0)) {
            setDrawlist(0);
        } else {
            int value = drawList[0] + amount;

            if (value < 0) {
                value = 0;
            }
            if (value >= dataset.getSize(1)) {
                value = dataset.getSize(1) - 1;
            }
            setDrawlist(value);
        }
        layoutPlotChildren();
    }

    public void incrementPlane(int axis, int amount) {
        if (axes.length > axis) {
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
            Dataset dataset = getDataset();
            int indexL = axModes[axis].getIndex(datasetAttributes, axis, axes[axis].getLowerBound());
            int indexU = axModes[axis].getIndex(datasetAttributes, axis, axes[axis].getUpperBound());
            int[] maxLimits = datasetAttributes.getMaxLimitsPt(axis);

            indexL += amount;
            indexU += amount;
            if (indexL < maxLimits[0]) {
                indexL = maxLimits[0];
            }
            if (indexU < maxLimits[0]) {
                indexU = maxLimits[0];
            }

            if (indexL > maxLimits[1]) {
                indexL = maxLimits[1];
            }
            if (indexU > maxLimits[1]) {
                indexU = maxLimits[1];
            }

            axes[axis].setLowerBound(indexL);
            axes[axis].setUpperBound(indexU);
            layoutPlotChildren();
        }
    }

    public void full() {
        ConsoleUtil.runOnFxThread(() -> {
            if (!datasetAttributesList.isEmpty()) {
                double[] limits = getRange(0);
                setXAxis(limits[0], limits[1]);
                if (disDimProp.get() == DISDIM.TwoD) {
                    limits = getRange(1);
                    setYAxis(limits[0], limits[1]);
                }
            }
        });
    }

    public void full(int axis) {
        if (axes.length > axis) {
            if (!datasetAttributesList.isEmpty()) {
                double[] limits = getRange(axis);
                setAxis(axis, limits[0], limits[1]);
                updateDatasetAttributeBounds();
            }
        }
    }

    public void center(int axis) {
        if (axes.length > axis) {
            if (!datasetAttributesList.isEmpty()) {
                double[] limits = getRange(axis);
                double center = (limits[0] + limits[1]) / 2.0;
                if (axModes[axis] == AXMODE.PTS) {
                    center = Math.ceil(center);
                }
                setAxis(axis, center, center);
            }
        }
    }

    protected void moveTo(Double[] positions) {
        for (int axis = 0; axis < positions.length; axis++) {
            if (positions[axis] != null) {
                if (axis > 1) {
                    DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
                    int plane = AXMODE.PPM.getIndex(datasetAttributes, axis, positions[axis]);
                    setAxis(axis, plane, plane);
                } else {
                    double[] limits = getRange(axis);
                    double lower = axes[axis].getLowerBound();
                    double upper = axes[axis].getUpperBound();
                    double range = Math.abs(upper - lower);
                    double newLower = positions[axis] - range / 2;
                    double newUpper = positions[axis] + range / 2;
                    setAxis(axis, newLower, newUpper);
                }
            }
        }
    }

    public void firstPlane(int axis) {
        if (axes.length > axis) {
            if (!datasetAttributesList.isEmpty()) {
                double[] limits = getRange(axis);
                setAxis(axis, limits[0], limits[0]);
            }
        }
    }

    public void lastPlane(int axis) {
        if (axes.length > axis) {
            if (!datasetAttributesList.isEmpty()) {
                double[] limits = getRange(axis);
                setAxis(axis, limits[1], limits[1]);
            }
        }
    }

    protected int[] getPlotLimits(DatasetAttributes datasetAttributes, int iDim) {
        Dataset dataset = datasetAttributes.getDataset();

        int min = axModes[iDim].getIndex(datasetAttributes, iDim, axes[iDim].getLowerBound());
        int max = axModes[iDim].getIndex(datasetAttributes, iDim, axes[iDim].getUpperBound());
        if (min > max) {
            int hold = min;
            min = max;
            max = hold;
        }
        int[] limits = {min, max};
        return limits;
    }

    public boolean hasData() {
        Dataset dataset = getDataset();
        return dataset != null;
    }

    public boolean is1D() {
        Dataset dataset = getDataset();
        return ((dataset != null) && (dataset.getNDim() == 1) || (disDimProp.get() != DISDIM.TwoD));
    }

    public int getNDim() {
        int nDim = 0;
        Dataset dataset = getDataset();
        if (dataset != null) {
            nDim = dataset.getNDim();
        }
        if (is1D()) {
            nDim = 1;
        }
        return nDim;
    }

    public void autoScale() {
        datasetAttributesList.stream().forEach(dataAttr -> {
            autoScale(dataAttr);
        });
        layoutPlotChildren();

    }

    protected void autoScale(DatasetAttributes dataAttr) {
        if (is1D()) {
            Dataset dataset = dataAttr.getDataset();
            double max = Double.NEGATIVE_INFINITY;
            double min = Double.MAX_VALUE;

            int[] limits = getPlotLimits(dataAttr, 0);
            int nDim = dataset.getNDim();
            int[][] pt = new int[nDim][2];
            int[] cpt = new int[nDim];
            int[] dim = new int[nDim];
            double[] width = new double[nDim];
            pt[0][0] = limits[0];
            pt[0][1] = limits[1];
            for (int i = 0; i < nDim; i++) {
                dim[i] = i;
                cpt[i] = (pt[i][0] + pt[i][1]) / 2;
                width[i] = (double) Math.abs(pt[i][0] - pt[i][1]);
            }
            RegionData rData;
            try {
                rData = dataset.analyzeRegion(pt, cpt, width, dim);
            } catch (IOException ioE) {
                return;
            }
            min = rData.getMin();
            max = rData.getMax();

            double range = max - min;
            if (max == min) {
                if (max == 0.0) {
                    range = 1.0;
                } else {
                    range = max;
                }
            }
            max += range / 20.0;
            min -= range / 20.0;
            double delta = max - min;
            dataAttr.setLvl(delta / 10.0);
            double offset = (0.0 - min) / delta;
            dataAttr.setOffset(offset);
            setYAxis(min, max);
        } else {
            Dataset dataset = dataAttr.getDataset();
            Double sdev = dataset.guessNoiseLevel();
            if (sdev != null) {
                dataAttr.setLvl(sdev * 5.0);
                level = sdev * 5.0;
            }
        }
    }

    protected void setPhaseDim(int phaseDim) {
        String vecDimName = controller.chartProcessor.getVecDimName();
        this.phaseDim = phaseDim;
        phaseAxis = 0;
        if (is1D() || vecDimName.equals("D1")) {
            phaseAxis = 0;
        } else if (phaseDim > 0) {
            phaseAxis = 1;
        }
    }

    protected void setPhasePivot() {
        Dataset dataset = getDataset();
        if (dataset == null) {
            return;
        }
        setPivot(crossHairPositions[0][(phaseAxis + 1) % 2]);
    }

    protected void autoPhaseFlat(boolean doFirst) {
        Dataset dataset = getDataset();

        if ((dataset == null) || (dataset.getVec() == null)) {
            return;
        }
        Vec vec = dataset.getVec();
        double[] phases = vec.autoPhase(doFirst, 0, 0, 0, 45.0, 1.0);
        setPh0(phases[0]);
        setPh1(0.0);
        if (phases.length == 2) {
            setPh1(phases[1]);
        }
        System.out.println("ph0 " + getPh0() + " ph1 " + getPh1());

        double sliderPH0 = getPh0();
        if (vec != null) {
            sliderPH0 = getPh0() + vec.getPH0();
        }
        double sliderPH1 = getPh1();
        if (vec != null) {
            sliderPH1 = getPh1() + vec.getPH1();
        }
        controller.handlePh1Reset(sliderPH1);
        controller.handlePh0Reset(sliderPH0);
        layoutPlotChildren();
    }

    protected void autoPhaseMax() {
        Dataset dataset = getDataset();

        if ((dataset == null) || (dataset.getVec() == null)) {
            return;
        }
        Vec vec = dataset.getVec();
        setPh0(vec.autoPhaseByMax());
        double sliderPH0 = getPh0();
        if (vec != null) {
            sliderPH0 = getPh0() + vec.getPH0();
        }
        double sliderPH1 = getPh1();
        if (vec != null) {
            sliderPH1 = getPh1() + vec.getPH1();
        }
        controller.handlePh1Reset(sliderPH1);
        controller.handlePh0Reset(sliderPH0);

        layoutPlotChildren();
    }

    protected void expand(int cNum) {
        if (!datasetAttributesList.isEmpty()) {
            int dNum = 1;
            if (cNum == 1) {
                dNum = 0;
            }
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
            double[] limits = datasetAttributes.checkLimits(axModes[dNum], dNum, crossHairPositions[0][cNum], crossHairPositions[1][cNum]);
            setAxis(dNum, limits[0], limits[1]);
        }
    }

    public void expand() {

        expand(VERTICAL);
        Dataset dataset = getDataset();
        if (dataset != null) {
            if (disDimProp.get() == DISDIM.TwoD) {
                expand(HORIZONTAL);
            }
        }
        layoutPlotChildren();
        hideCrossHairs();
    }

    public double[] getVerticalCrosshairPositions() {
        double[] positions = new double[2];
        positions[0] = crossHairPositions[0][1];
        positions[1] = crossHairPositions[1][1];
        return positions;
    }

    protected double getRefPositionFromCrossHair(double newPPM) {
        Dataset dataset = getDataset();
        if (dataset == null) {
            return 0.0;
        }
        DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
        if (controller.chartProcessor == null) {
            return 0.0;
        }
        String vecDimName = controller.chartProcessor.getVecDimName();
        int vecDim = controller.chartProcessor.getVecDim();
        double position;
        double ppmPosition;
        double refPoint;
        double refPPM;
        int size;
        double centerPPM;
        if (is1D() || vecDimName.equals("D1")) {
            position = axModes[0].getIndex(datasetAttributes, 0, crossHairPositions[0][1]);
            size = dataset.getSize(datasetAttributes.dim[0]);
            refPoint = dataset.getRefPt(datasetAttributes.dim[0]);
            refPPM = dataset.getRefValue(datasetAttributes.dim[0]);
            ppmPosition = dataset.pointToPPM(0, position);
            centerPPM = dataset.pointToPPM(0, size / 2);
        } else {
            position = axModes[vecDim].getIndex(datasetAttributes, vecDim, crossHairPositions[0][0]);
            size = dataset.getSize(datasetAttributes.dim[vecDim]);
            refPoint = dataset.getRefPt(datasetAttributes.dim[0]);
            refPPM = dataset.getRefValue(datasetAttributes.dim[0]);
            ppmPosition = dataset.pointToPPM(datasetAttributes.dim[vecDim], position);
            centerPPM = dataset.pointToPPM(datasetAttributes.dim[vecDim], size / 2);
        }
        double newCenter = (newPPM - ppmPosition) + centerPPM;

        double f1 = position / (size - 1);
        System.out.println(vecDim + " " + size + " " + position + " " + ppmPosition + " " + f1 + " " + refPoint + " " + refPPM + " " + newCenter);
        return newCenter;
    }

    protected void addRegionRange() {
        Dataset dataset = getDataset();
        if (dataset == null) {
            return;
        }
        DatasetAttributes datasetAttributes = datasetAttributesList.get(0);

        if (controller.chartProcessor == null) {
            return;
        }
        String vecDimName = controller.chartProcessor.getVecDimName();
        int vecDim = controller.chartProcessor.getVecDim();
        double min;
        double max;
        int size;
        if (is1D() || vecDimName.equals("D1")) {
            min = axModes[0].getIndex(datasetAttributes, 0, crossHairPositions[0][1]);
            max = axModes[0].getIndex(datasetAttributes, 0, crossHairPositions[1][1]);
            size = dataset.getSize(datasetAttributes.dim[0]);
        } else {
            min = axModes[vecDim].getIndex(datasetAttributes, vecDim, crossHairPositions[0][0]);
            max = axModes[vecDim].getIndex(datasetAttributes, vecDim, crossHairPositions[1][0]);
            size = dataset.getSize(datasetAttributes.dim[vecDim]);
        }
        int[] currentRegion = controller.getExtractRegion(vecDimName, size);
        //System.out.printf("%.3f %.3f %d %d %d\n", min, max, size, currentRegion[0], currentRegion[1]);
        if (min > max) {
            double hold = min;
            min = max;
            max = hold;
        }
        if (min < 0) {
            min = 0.0;
        }
        min += currentRegion[0];
        max += currentRegion[0];
        /*
         if (max >= size) {
         max = size - 1;
         }
         */
        double f1 = min / (size - 1);
        double f2 = max / (size - 1);
        //System.out.printf("%.3f %.3f %d %d %d %.3f %.3f\n", min, max, size, currentRegion[0], currentRegion[1], f1, f2);
        double mul = Math.pow(10.0, Math.ceil(Math.log10(size)));
        f1 = Math.round(f1 * mul) / mul;
        f2 = Math.round(f2 * mul) / mul;
        processorController.propertyManager.addExtractRegion(min, max, f1, f2);
    }

    protected void addBaselineRange(boolean clearMode) {
        Dataset dataset = getDataset();
        if (dataset == null) {
            return;
        }
        DatasetAttributes datasetAttributes = datasetAttributesList.get(0);

        if (controller.chartProcessor == null) {
            return;
        }
        String vecDimName = controller.chartProcessor.getVecDimName();
        int vecDim = controller.chartProcessor.getVecDim();
        double min;
        double max;
        int size;
        if (is1D() || vecDimName.equals("D1")) {
            min = axModes[0].getIndex(datasetAttributes, 0, crossHairPositions[0][1]);
            max = axModes[0].getIndex(datasetAttributes, 0, crossHairPositions[1][1]);
            size = dataset.getSize(datasetAttributes.dim[0]);
        } else {
            min = axModes[vecDim].getIndex(datasetAttributes, vecDim, crossHairPositions[0][0]);
            max = axModes[vecDim].getIndex(datasetAttributes, vecDim, crossHairPositions[1][0]);
            size = dataset.getSize(datasetAttributes.dim[vecDim]);
        }

        ArrayList<Double> currentRegions = controller.getBaselineRegions(vecDimName);
        if (min > max) {
            double hold = min;
            min = max;
            max = hold;
        }
        if (min < 0) {
            min = 0.0;
        }
        double f1 = min / (size - 1);
        double f2 = max / (size - 1);
        double mul = Math.pow(10.0, Math.ceil(Math.log10(size)));
        f1 = Math.round(f1 * mul) / mul;
        f2 = Math.round(f2 * mul) / mul;
        processorController.propertyManager.addBaselineRegion(currentRegions, f1, f2, clearMode);
    }

    protected void clearBaselineRanges() {
        processorController.propertyManager.clearBaselineRegions();
    }

    public void updateDatasets(List<String> targets) {
        ObservableList<DatasetAttributes> datasetAttrs = getDatasetAttributes();
        List<DatasetAttributes> newList = new ArrayList<>();
        boolean updated = false;
        int iTarget = 0;
        for (String s : targets) {
            int n = newList.size();
            int jData = 0;
            int addAt = -1;
            for (DatasetAttributes datasetAttr : datasetAttrs) {
                if (datasetAttr.getDataset().getName().equals(s)) {
                    newList.add(datasetAttr);
                    addAt = jData;
                }
                jData++;
            }
            if (iTarget != addAt) {
                updated = true;
            }
            // if didn't add one, then create new DatasetAttributes
            if (newList.size() == n) {
                Dataset dataset = Dataset.getDataset(s);
                int nDim = dataset.getNDim();
                // fixme kluge as not all datasets that are freq domain have attribute set
                for (int i = 0; i < nDim; i++) {
                    dataset.setFreqDomain(i, true);
                }
                DatasetAttributes newAttr = new DatasetAttributes(dataset);
                newList.add(newAttr);
                updated = true;
            }
            iTarget++;
        }
        if (newList.size() != datasetAttrs.size()) {
            updated = true;
        }
        if (updated) {
            if (!newList.isEmpty() && datasetAttrs.isEmpty()) {
                // if no datsets present already must use addDataset once to set up
                // various parameters
                controller.addDataset(newList.get(0).getDataset(), false, false);
                newList.remove(0);
                datasetAttrs.addAll(newList);
            } else {
                datasetAttrs.clear();
                datasetAttrs.addAll(newList);
            }
        }

    }

    void setDataset(Dataset dataset) {
        setDataset(dataset, false);
    }

    DatasetAttributes setDataset(Dataset dataset, boolean append) {
        SpectrumStatusBar statusBar = controller.getStatusBar();
        DatasetAttributes datasetAttributes = null;
        if (dataset != null) {
            System.out.println("set dataset with " + dataset.getNDim() + " dims " + dataset.getNFreqDims() + " freq dims");
            if ((dataset.getNDim() == 1) || (dataset.getNFreqDims() == 1)) {
                System.out.println("ndb " + dataset.getNDim() + " " + dataset.getNFreqDims());
                disDimProp.set(DISDIM.OneDX);
                //statusBar.sliceStatus.setSelected(false);
                setSliceStatus(false);
            } else {
                disDimProp.set(DISDIM.TwoD);
            }
            if (append) {
                datasetAttributes = new DatasetAttributes(dataset);
                if (datasetAttributes.getDataset().isLvlSet()) {
                    datasetAttributes.setLvl(datasetAttributes.getDataset().getLvl());
                    datasetAttributes.setHasLevel(true);
                }
                datasetAttributesList.add(datasetAttributes);
            } else {
                peakListAttributesList.clear();;
                if (datasetAttributesList.isEmpty()) {
                    if ((lastDatasetAttr != null) && (lastDatasetAttr.getDataset().getName().equals(dataset.getName()))) {
                        datasetAttributes = lastDatasetAttr;
                        double oldLevel = datasetAttributes.getLvl();
                        datasetAttributes.setDataset(dataset);
                        datasetAttributes.setLvl(oldLevel);
                        datasetAttributes.setHasLevel(true);
                    } else {
                        datasetAttributes = new DatasetAttributes(dataset);
                        datasetAttributes.setLvl(dataset.getLvl());
                        datasetAttributes.setHasLevel(true);
                    }
                } else {
                    datasetAttributes = datasetAttributesList.get(0);
                    Dataset existingDataset = datasetAttributes.getDataset();
                    double oldLevel = datasetAttributes.getLvl();
                    datasetAttributes.setDataset(dataset);
                    if ((existingDataset == null) || !existingDataset.getName().equals(dataset.getName())) {
                        datasetAttributes.setLvl(dataset.getLvl());
                    } else if ((existingDataset != null) && existingDataset.getName().equals(dataset.getName())) {
                        datasetAttributes.setLvl(oldLevel);
                        datasetAttributes.setHasLevel(true);
                    }
                }
                datasetAttributesList.setAll(datasetAttributes);
            }
            // fixme should we do this
            for (int i = 0; i < datasetAttributes.dim.length; i++) {
                datasetAttributes.dim[i] = i;
            }

            //System.out.println("set dataset " + dataset.getName() + " " + dataset.getNDim() + " " + dataset.getFreqDomain(0));
            updateAxisType();
            datasetFileProp.set(dataset.getFile());
        } else {
            //statusBar.sliceStatus.setSelected(false);
            setSliceStatus(false);

            datasetFileProp.setValue(null);
        }
        if (controller.specAttrWindowController != null) {
            controller.specAttrWindowController.updateDims();
        }
        hideCrossHairs();
        datasetAttributes.drawList = null;
        return datasetAttributes;
    }

    public void setDrawlist(int selected) {
        if (!datasetAttributesList.isEmpty()) {
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
            datasetAttributes.setDrawListSize(1);
            datasetAttributes.drawList[0] = selected;
        }
    }

    public void setDrawlist(List<Integer> selected) {
        if (!datasetAttributesList.isEmpty()) {
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
            datasetAttributes.setDrawListSize(selected.size());
            for (int i = 0, n = selected.size(); i < n; i++) {
                datasetAttributes.drawList[i] = selected.get(i);
            }
        }

    }

    public ArrayList<String> getDimNames() {
        ArrayList<String> names = new ArrayList<>();
        if (!datasetAttributesList.isEmpty()) {
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
            int nDim = datasetAttributes.nDim;
            for (int i = 0; i < nDim; i++) {
                String label = datasetAttributes.getLabel(i);
                names.add(label);
            }
        }
        return names;
    }

    void updateDatasetAttributeBounds() {
        for (DatasetAttributes datasetAttributes : datasetAttributesList) {
            datasetAttributes.updateBounds(axModes, axes, disDimProp.getValue());
        }
    }

    public void setAxisState(boolean leftEdge, boolean bottomEdge) {
        yAxis.setShowTicsAndLabels(leftEdge);
        xAxis.setShowTicsAndLabels(bottomEdge);
    }

    void setAxisState(NMRAxis axis, String axisLabel) {
        boolean state = axis.getShowTicsAndLabels();
        axis.setTickLabelsVisible(state);
        axis.setTickMarkVisible(state);
        axis.setVisible(true);
        if (!state) {
            axis.setLabel("");
        } else {
            if (!axisLabel.equals(axis.getLabel())) {
                axis.setLabel(axisLabel);
            }
        }
    }

    void updateAxisType() {
        Dataset dataset = getDataset();
        DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
        int nDim = dataset.getNDim();
        int nAxes = nDim;
        if (is1D()) {
            nAxes = 2;
        }
        if (axes.length != nAxes) {
            axes = new NMRAxis[nAxes];
            axes[0] = xAxis;
            axes[1] = yAxis;
            datasetAttributes.dim[0] = 0;
            if (datasetAttributes.dim.length > 1) {
                datasetAttributes.dim[1] = 1;
            }
            axModes = new AXMODE[nAxes];
            axModes[0] = AXMODE.PPM;
            axModes[1] = AXMODE.PPM;
            for (int i = 2; i < nAxes; i++) {
                axes[i] = new NMRAxis(dataset.getSize(i) / 2, dataset.getSize(i) / 2, 4);
                datasetAttributes.dim[i] = i;
                axModes[i] = AXMODE.PTS;
                axes[i].lowerBoundProperty().addListener(new AxisChangeListener(this, i, 0));
                axes[i].upperBoundProperty().addListener(new AxisChangeListener(this, i, 1));

            }
            drawSpectrum.setAxes(axes);
            drawSpectrum.setDisDim(disDimProp.getValue());
        }
        if (dataset.getFreqDomain(0)) {
            axModes[0] = AXMODE.PPM;
        } else {
            axModes[0] = AXMODE.TIME;
        }
        boolean reversedAxis = (axModes[0] == AXMODE.PPM);
        boolean autoScale = false;
        if (reversedAxis != xAxis.getReverse()) {
            autoScale = true;
        }
        xAxis.setReverse(reversedAxis);
        String xLabel = axModes[0].getLabel(datasetAttributes, 0);
        setAxisState(xAxis, xLabel);
        if (!is1D()) {
            if (dataset.getFreqDomain(1)) {
                axModes[1] = AXMODE.PPM;
            } else {
                axModes[1] = AXMODE.TIME;
            }
            reversedAxis = (axModes[1] == AXMODE.PPM);
            if (reversedAxis != xAxis.getReverse()) {
                autoScale = true;
            }

            yAxis.setReverse(reversedAxis);
            String yLabel = axModes[0].getLabel(datasetAttributes, 1);
            setAxisState(yAxis, yLabel);

        } else {
            yAxis.setReverse(false);
            setAxisState(yAxis, "Intensity");
        }
        if (autoScale) {
            full();
            if (!datasetAttributes.getHasLevel()) {
                autoScale();
            }
        }
    }

    public void refresh() {
        layoutPlotChildren();
    }

    public void draw() {
        if (Platform.isFxApplicationThread()) {
            refresh();
        } else {
            Platform.runLater(() -> {
                refresh();
            }
            );
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    protected void layoutPlotChildren() {
        bcPath.getElements().clear();
        bcPath.setStroke(Color.ORANGE);
        bcPath.setStrokeWidth(3.0);
        bcList.clear();
        double width = xAxis.getWidth();
        double height = yAxis.getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);
        GraphicsContext gC = canvas.getGraphicsContext2D();
        gC.clearRect(0, 0, width, height);
        peakCanvas.setWidth(width);
        peakCanvas.setHeight(height);
        GraphicsContext peakGC = peakCanvas.getGraphicsContext2D();
        peakGC.clearRect(0, 0, width, height);

        if (annoCanvas != null) {
            annoCanvas.setWidth(width);
            annoCanvas.setHeight(height);
            GraphicsContext annoGC = annoCanvas.getGraphicsContext2D();
            annoGC.clearRect(0, 0, width, height);
        }

//        datasetAttributesList.clear();
        ArrayList<DatasetAttributes> draw2DList = new ArrayList<>();
        datasetAttributesList.stream().forEach(datasetAttributes -> {
            DatasetAttributes firstAttr = datasetAttributesList.get(0);
            Dataset dataset = datasetAttributes.getDataset();
            if (dataset != null) {
//                datasetAttributes.setLvl(level);
                datasetAttributes.setDrawReal(true);
                if (datasetAttributes != firstAttr) {
                    datasetAttributes.syncDims(firstAttr);
                } else {
                    updateAxisType();

                    if (controller.getStatusBar() != null) {
                        SpectrumStatusBar statusBar = controller.getStatusBar();
                        for (int iDim = 2; iDim < datasetAttributes.nDim; iDim++) {
                            int[] maxLimits = datasetAttributes.getMaxLimitsPt(iDim);
                            controller.getStatusBar().setPlaneRanges(iDim, maxLimits[1]);
                        }
                    }
                }
                if (disDimProp.get() != DISDIM.TwoD) {
                    for (int iMode = 0; iMode < 2; iMode++) {
                        if (iMode == 0) {
                            datasetAttributes.setDrawReal(true);
                        } else {
                            if (!controller.getStatusBar().complexStatus.isSelected()) {
                                break;
                            }
                            datasetAttributes.setDrawReal(false);
                        }
                        bcList.clear();
                        drawSpectrum.setToLastChunk(datasetAttributes);
                        boolean ok;
                        do {
                            ok = drawSpectrum.draw1DSpectrum(datasetAttributes, HORIZONTAL, axModes[0], getPh0(), getPh1(), bcPath);
                            double[][] xy = drawSpectrum.getXY();
                            int nPoints = drawSpectrum.getNPoints();
                            int rowIndex = drawSpectrum.getRowIndex();
                            drawSpecLine(datasetAttributes, gC, iMode, rowIndex, nPoints, xy);
                        } while (ok);
                    }
                    drawSpectrum.drawVecAnno(datasetAttributes, HORIZONTAL, axModes[0]);
                    double[][] xy = drawSpectrum.getXY();
                    int nPoints = drawSpectrum.getNPoints();
                    drawSpecLine(datasetAttributes, gC, 0, -1, nPoints, xy);

                } else {
                    draw2DList.add(datasetAttributes);
                }
            }
        }
        );
        if (!draw2DList.isEmpty()) {
            drawSpectrum.drawSpectrum(draw2DList, axModes, false);
        }
        if (!datasetAttributesList.isEmpty()) {
            drawPeakLists(true);
        }
        double[][] bounds = {{0, canvas.getWidth() - 1}, {0, canvas.getHeight() - 1}};
        double[][] world = {{axes[0].getLowerBound(), axes[0].getUpperBound()},
        {axes[1].getLowerBound(), axes[1].getUpperBound()}};
        canvasAnnotations.forEach((anno) -> {
            anno.draw(peakCanvas, bounds, world);
        });

        refreshCrossHairs();
    }

    public void addAnnotation(CanvasAnnotation anno) {
        canvasAnnotations.add(anno);
    }

    void drawSpecLine(DatasetAttributes datasetAttributes, GraphicsContext gC, int iMode, int rowIndex, int nPoints, double[][] xy) {
        if (nPoints > 1) {
            if (iMode == 0) {
                gC.setStroke(datasetAttributes.getPosColor(rowIndex));
                gC.setLineWidth(datasetAttributes.getPosWidth());
            } else {
                gC.setStroke(datasetAttributes.getNegColor());
                gC.setLineWidth(datasetAttributes.getNegWidth());
            }
            gC.setLineCap(StrokeLineCap.BUTT);
            gC.strokePolyline(xy[0], xy[1], nPoints);
        }
    }

    void purgeInvalidPeakListAttributes() {
        Iterator<PeakListAttributes> iterator = peakListAttributesList.iterator();
        while (iterator.hasNext()) {
            PeakList peakList = iterator.next().getPeakList();
            if (!peakList.valid()) {
                iterator.remove();
            }
        }

    }

    void setupPeakListAttributes(PeakList peakList) {
        purgeInvalidPeakListAttributes();
        boolean present = false;
        String listName = peakList.getName();
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            if (peakListAttr.peakListNameProperty().get().equals(listName)) {
                if (peakListAttr.getPeakList().peaks() == null) {
                    peakListAttr.setPeakList(peakList);
                }
                present = true;
                break;
            }
        }
        if (!present) {
            if (isPeakListCompatible(peakList)) {
                DatasetAttributes matchData = null;
                for (DatasetAttributes dataAttr : datasetAttributesList) {
                    Dataset dataset = dataAttr.getDataset();
                    String datasetName = dataset.getName();
                    if (datasetName.length() != 0) {
                        if (peakList.getDatasetName().equals(datasetName)) {
                            matchData = dataAttr;
                            break;
                        }
                    }
                }
                if (matchData == null) {
                    matchData = datasetAttributesList.get(0);
                }
                PeakListAttributes peakListAttr = new PeakListAttributes(this, matchData, peakList);
                peakListAttributesList.add(peakListAttr);
                peakList.registerListener(this);
            }
        }
    }

    public void removeUnusedPeakLists(List<String> targets) {
        ObservableList<PeakListAttributes> peakAttrs = getPeakListAttributes();
        List<PeakListAttributes> newList = new ArrayList<>();
        boolean removeSome = false;
        for (PeakListAttributes peakAttr : peakAttrs) {
            boolean found = false;
            for (String s : targets) {
                if (peakAttr.getPeakListName().equals(s)) {
                    newList.add(peakAttr);
                    found = true;
                    break;
                }
            }
            if (!found) {
                peakAttr.getPeakList().removeListener(this);
            }
            removeSome = !found;
        }
        if (removeSome) {
            peakAttrs.clear();
            peakAttrs.addAll(newList);
        }
    }

    public void updatePeakLists(List<String> targets) {
        removeUnusedPeakLists(targets);
        for (String s : targets) {
            PeakList peakList = PeakList.get(s);
            if (peakList != null) {
                setupPeakListAttributes(peakList);
            }
        }
    }

    public void setCursor() {
        this.setCursor(Cursor.CROSSHAIR);
    }

    public void deleteSelectedPeaks() {
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            Set<Peak> peaks = peakListAttr.getSelectedPeaks();
            for (Peak peak : peaks) {
                peak.setStatus(-1);
            }
        }

    }

    public List<Peak> getSelectedPeaks() {
        List<Peak> selectedPeaks = new ArrayList<>();
        peakListAttributesList.stream().forEach(peakListAttr -> {
            Set<Peak> peaks = peakListAttr.getSelectedPeaks();
            selectedPeaks.addAll(peaks);
        });
        return selectedPeaks;
    }

    public void dragPeak(double[] dragStart, double x, double y, boolean widthMode) {
        boolean draggedAny = false;
        double[] dragPos = {x, y};
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            Set<Peak> peaks = peakListAttr.getSelectedPeaks();
            for (Peak peak : peaks) {
                draggedAny = true;
                if (widthMode) {
                    peakListAttr.resizePeak(peak, dragStart, dragPos);
                } else {
                    peakListAttr.movePeak(peak, dragStart, dragPos);
                }
            }
        }
        dragStart[0] = dragPos[0];
        dragStart[1] = dragPos[1];
        drawPeakLists(false);
        for (PeakListAttributes peakListAttr : peakListAttributesList) {
            Set<Peak> peaks = peakListAttr.getSelectedPeaks();
            if (!peaks.isEmpty()) {
                drawSelectedPeaks(peakListAttr);
            }
        }
    }

    void fitPeaks() {
        peakListAttributesList.forEach((peakListAttr) -> {
            Set<Peak> peaks = peakListAttr.getSelectedPeaks();
            if (!peaks.isEmpty()) {
                Dataset dataset = peakListAttr.getDatasetAttributes().getDataset();
                if (dataset != null) {
                    try {
                        peakListAttr.getPeakList().peakFit(dataset, peaks);
                    } catch (IllegalArgumentException | IOException | PeakFitException ex) {
                        Logger.getLogger(PolyChart.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        });
//        drawPeakLists(false);
//        peakListAttributesList.forEach((peakListAttr) -> {
//            List<Peak> peaks = peakListAttr.getSelectedPeaks();
//            if (!peaks.isEmpty()) {
//                drawSelectedPeaks(peakListAttr);
//            }
//        });
    }

    void fitPeakLists() {
        peakListAttributesList.forEach((peakListAttr) -> {
            Dataset dataset = peakListAttr.getDatasetAttributes().getDataset();
            if (dataset != null) {
                try {
                    peakListAttr.getPeakList().peakFit(dataset);
                } catch (IllegalArgumentException | IOException | PeakFitException ex) {
                    Logger.getLogger(PolyChart.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
//        drawPeakLists(false);
//        peakListAttributesList.forEach((peakListAttr) -> {
//            List<Peak> peaks = peakListAttr.getSelectedPeaks();
//            if (!peaks.isEmpty()) {
//                drawSelectedPeaks(peakListAttr);
//            }
//        });
    }

    void tweakPeaks() {
        peakListAttributesList.forEach((peakListAttr) -> {
            Set<Peak> peaks = peakListAttr.getSelectedPeaks();
            if (!peaks.isEmpty()) {
                Dataset dataset = peakListAttr.getDatasetAttributes().getDataset();
                if (dataset != null) {
                    try {
                        peakListAttr.getPeakList().tweakPeaks(dataset, peaks);

                    } catch (IllegalArgumentException ex) {
                        Logger.getLogger(PolyChart.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        });
//        drawPeakLists(false);
//        peakListAttributesList.forEach((peakListAttr) -> {
//            List<Peak> peaks = peakListAttr.getSelectedPeaks();
//            if (!peaks.isEmpty()) {
//                drawSelectedPeaks(peakListAttr);
//            }
//        });
    }

    void tweakPeakLists() {
        peakListAttributesList.forEach((peakListAttr) -> {
            Dataset dataset = peakListAttr.getDatasetAttributes().getDataset();
            if (dataset != null) {
                try {
                    peakListAttr.getPeakList().tweakPeaks(dataset);

                } catch (IllegalArgumentException ex) {
                    Logger.getLogger(PolyChart.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
//        drawPeakLists(false);
//        peakListAttributesList.forEach((peakListAttr) -> {
//            List<Peak> peaks = peakListAttr.getSelectedPeaks();
//            if (!peaks.isEmpty()) {
//                drawSelectedPeaks(peakListAttr);
//            }
//        });
    }

    public void drawPeakLists(boolean clear) {
        if (peakCanvas != null) {
            double width = xAxis.getWidth();
            double height = yAxis.getHeight();
            peakCanvas.setWidth(width);
            peakCanvas.setHeight(height);
            GraphicsContext peakGC = peakCanvas.getGraphicsContext2D();
            peakGC.clearRect(0, 0, width, height);
        }
        final Iterator<PeakListAttributes> peakListIterator = peakListAttributesList.iterator();
        while (peakListIterator.hasNext()) {
            PeakListAttributes peakListAttr = peakListIterator.next();
            if (peakListAttr.getPeakList().peaks() == null) {
                peakListAttr.getPeakList().removeListener(this);
                peakListIterator.remove();
            }
        }
        if (peakStatus.get()) {
            for (PeakListAttributes peakListAttr : peakListAttributesList) {
                if (clear) {
                    peakListAttr.clearPeaksInRegion();
                }
                if (peakListAttr.getDrawPeaks()) {
                    drawPeakList(peakListAttr);
                }
//                drawSelectedPeaks(peakListAttr);
            }
        }
    }

    public void hitPeak(double pickX, double pickY) {
        Optional<Peak> hit = Optional.empty();
        if (peakStatus.get()) {
            drawPeakLists(false);
            for (PeakListAttributes peakListAttr : peakListAttributesList) {
                if (peakListAttr.getDrawPeaks()) {
                    hit = peakListAttr.hitPeak(drawPeaks, pickX, pickY);
                    if (hit.isPresent()) {
                        break;
                    }
                }
            }
        }
        if (hit.isPresent()) {
            FXMLController.getActiveController().showPeakAttr();
            FXMLController.peakAttrController.gotoPeak(hit.get());
        }
    }

    public void selectPeaks(double pickX, double pickY, boolean append) {
        List<Peak> selPeaks = new ArrayList<>();
        drawPeakLists(false);
        if (peakStatus.get()) {
            for (PeakListAttributes peakListAttr : peakListAttributesList) {
                if (peakListAttr.getDrawPeaks()) {
                    peakListAttr.selectPeak(drawPeaks, pickX, pickY, append);
                    Set<Peak> peaks = peakListAttr.getSelectedPeaks();
                    if (!peaks.isEmpty()) {
                        selPeaks.addAll(peaks);
                    }
                    drawSelectedPeaks(peakListAttr);
                }
            }
        }
        if (controller == FXMLController.activeController) {
            controller.selPeaks.set(selPeaks);
        }
    }

    double[][] getRegionLimits(DatasetAttributes dataAttr) {
        int nDataDim = dataAttr.nDim;
        double[][] limits = new double[nDataDim][2];
        for (int i = 0; (i < axes.length); i++) {
            if (axModes[i] == AXMODE.PPM) {
                limits[i][0] = axes[i].getLowerBound();
                limits[i][1] = axes[i].getUpperBound();
            } else {
                double lb = axes[i].getLowerBound();
                double ub = axes[i].getUpperBound();
                if (Math.abs(lb - ub) < 0.5) {
                    lb = lb - 1.4;
                    ub = ub + 1.4;
                }
                limits[i][1] = AXMODE.PPM.indexToValue(dataAttr, i, lb);
                limits[i][0] = AXMODE.PPM.indexToValue(dataAttr, i, ub);
            }
        }
        return limits;

    }

    public boolean isPeakListCompatible(PeakList peakList) {
        boolean result = false;
        if (!datasetAttributesList.isEmpty()) {
            DatasetAttributes dataAttr = datasetAttributesList.get(0);
            int[] peakDim = getPeakDim(dataAttr, peakList);
            if (peakDim[0] != -1) {
                if ((peakDim.length == 1) || (peakDim[1] != -1)) {
                    result = true;
                }
            }
        }
        return result;
    }

    int[] getPeakDim(DatasetAttributes dataAttr, PeakList peakList) {
        int nPeakDim = peakList.nDim;
        int nDataDim = dataAttr.nDim;
        int[] dim = new int[nDataDim];
        for (int i = 0; (i < axes.length); i++) {
            dim[i] = -1;
            for (int j = 0; j < nPeakDim; j++) {
                if (dataAttr.getLabel(i).equals(peakList.getSpectralDim(j).getDimName())) {
                    dim[i] = j;
                }
            }
        }
        return dim;
    }

    void drawPeakList(PeakListAttributes peakListAttr) {
        if (peakListAttr.getDrawPeaks()) {
            GraphicsContext gC = peakCanvas.getGraphicsContext2D();
            List<Peak> peaks = peakListAttr.getPeaksInRegion();
            int[] dim = peakListAttr.getPeakDim();
            double[] offsets = new double[dim.length];
            peaks.stream().filter(peak -> peak.getStatus() >= 0).forEach((peak) -> {
                drawPeaks.drawPeak(peakListAttr, gC, peak, dim, offsets, false);
            });
            List<Multiplet> multiplets = peakListAttr.getMultipletsInRegion();
            List<Peak> roots = new ArrayList<>();
            multiplets.stream().forEach((multiplet) -> {
                drawPeaks.drawMultiplet(peakListAttr, gC, multiplet, dim, offsets);
                roots.add(multiplet.getPeakDim().getPeak());
            });
            if (false) {
                PeakList.sortPeaks(roots, 0, true);
                ArrayList overlappedPeaks = new ArrayList();
                for (int iPeak = 0, n = roots.size(); iPeak < n; iPeak++) {
                    Peak aPeak = (Peak) roots.get(iPeak);
                    overlappedPeaks.add(aPeak);
                    for (int jPeak = (iPeak + 1); jPeak < n; jPeak++) {
                        Peak bPeak = (Peak) roots.get(jPeak);
                        if (aPeak.overlaps(bPeak, 0, overlapScale)) {
                            overlappedPeaks.add(bPeak);
                            iPeak++;
                        } else {
                            break;
                        }
                    }
                    drawPeaks.drawSimSum(gC, overlappedPeaks, dim);
                    overlappedPeaks.clear();
                }

            }
        }

    }

    void drawSelectedPeaks(PeakListAttributes peakListAttr) {
        if (peakListAttr.getDrawPeaks()) {
            GraphicsContext gC = peakCanvas.getGraphicsContext2D();
            Set<Peak> peaks = peakListAttr.getSelectedPeaks();
            int[] dim = peakListAttr.getPeakDim();
            double[] offsets = new double[dim.length];
            peaks.stream().forEach((peak) -> {
                drawPeaks.drawPeak(peakListAttr, gC, peak, dim, offsets, true);
                int nPeakDim = peak.peakList.nDim;
                if (peak.getPeakList().isSlideable() && (nPeakDim > 1)) {
                    drawPeaks.drawLinkLines(peakListAttr, gC, peak, dim);
                }
            });
        }
    }

//    void pickPeakList(DatasetAttributes dataAttr, PeakList peakList, final double pickX, final double pickY) {
//        GraphicsContext gC = peakCanvas.getGraphicsContext2D();
//        final int nDataDim = dataAttr.nDim;
//        final double[] offsets = new double[nDataDim];
//        final int[] dim = getPeakDim(dataAttr, peakList);
//        final double[][] limits = getRegionLimits(dataAttr);
//
//        List<Peak> selectedPeaks = peakList.peaks().stream().parallel()
//                .filter(peak -> peak.inRegion(limits, null, dim))
//                .filter((peak) -> drawPeaks.pickPeak(dataAttr, gC, peak, dim, offsets, pickX, pickY))
//                .collect(Collectors.toList());
//        drawSelectedPeaks(dataAttr, peakList, selectedPeaks);
//
//    }
//
//    void drawSelectedPeaks(DatasetAttributes dataAttr, PeakList peakList, List<Peak> selectedPeaks) {
//        GraphicsContext gC = peakCanvas.getGraphicsContext2D();
//        int nPeakDim = peakList.nDim;
//        int nDataDim = dataAttr.nDim;
//        int[] dim = new int[nDataDim];
//        double[] offsets = new double[nDataDim];
//        for (int i = 0; (i < axes.length); i++) {
//            dim[i] = -1;
//            for (int j = 0; j < nPeakDim; j++) {
//                if (dataAttr.getLabel(i).equals(peakList.getSpectralDim(j).getDimName())) {
//                    dim[i] = j;
//                }
//            }
//        }
//        selectedPeaks.stream().forEach((peak) -> {
//            drawPeaks.drawPeak(gC, peak, dim, offsets, true);
//        });
//    }
    public ObservableList<DatasetAttributes> getDatasetAttributes() {
        return datasetAttributesList;
    }

    public ObservableList<PeakListAttributes> getPeakListAttributes() {
        return peakListAttributesList;
    }

    public double getDataPH0() {
        Dataset dataset = getDataset();
        double value = 0.0;
        if ((dataset != null) && (controller.chartProcessor != null)) {
            int mapDim = controller.chartProcessor.mapToDataset(phaseDim);
            if (mapDim != -1) {
                value = dataset.getPh0(mapDim);
            }
        }
        return value;
    }

    public double getDataPH1() {
        Dataset dataset = getDataset();
        double value = 0.0;
        if ((dataset != null) && (controller.chartProcessor != null)) {
            int mapDim = controller.chartProcessor.mapToDataset(phaseDim);
            if (mapDim != -1) {
                value = dataset.getPh1(mapDim);
            }
        }
        return value;
    }

    public void setPh0(double ph0) {
        if (controller.chartProcessor == null) {
            return;
        }
        int mapDim = controller.chartProcessor.mapToDataset(phaseDim);
        if (mapDim != -1) {
            chartPhases[0][mapDim] = ph0;
        }
    }

    public void setPh1(double ph1) {
        if (controller.chartProcessor == null) {
            return;
        }
        int mapDim = controller.chartProcessor.mapToDataset(phaseDim);
        if (mapDim != -1) {
            chartPhases[1][mapDim] = ph1;
        }
    }

    public double getPh0() {
        double value = 0.0;
        if (controller.chartProcessor == null) {
            return 0.0;
        }
        int mapDim = controller.chartProcessor.mapToDataset(phaseDim);
        if (mapDim != -1) {
            value = chartPhases[0][mapDim];
        }
        return value;
    }

    public double getPh1() {
        double value = 0.0;
        if (controller.chartProcessor == null) {
            return 0.0;
        }
        int mapDim = controller.chartProcessor.mapToDataset(phaseDim);
        if (mapDim != -1) {
            value = chartPhases[1][mapDim];
        }
        return value;
    }

    public double getPh0(int iDim) {
        double value = 0.0;
        if (iDim == 0) {
            value = chartPhases[0][0];
        } else {
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);

            int datasetDim = datasetAttributes.dim[iDim];
            value = chartPhases[0][datasetDim];
        }
        return value;
    }

    public double getPh1(int iDim) {
        double value = 0.0;
        if (iDim == 0) {
            value = chartPhases[1][0];
        } else {
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);

            int datasetDim = datasetAttributes.dim[iDim];
            value = chartPhases[1][datasetDim];
        }
        return value;
    }

    public double getPivot() {
        if (controller.chartProcessor == null) {
            return 0.0;
        }
        int mapDim = controller.chartProcessor.mapToDataset(phaseDim);
        if (mapDim != -1) {
            return chartPivots[mapDim];
        } else {
            return 0.0;
        }
    }

    /**
     * @param pivot the pivot to set
     */
    public void setPivot(double pivot) {
        if (controller.chartProcessor == null) {
            return;
        }
        Dataset dataset = getDataset();
        int mapDim = controller.chartProcessor.mapToDataset(phaseDim);
        int datasetDim = 0;
        double position = 0;
        int size = 0;
        String vecDimName = controller.chartProcessor.getVecDimName();
        DatasetAttributes datasetAttributes = datasetAttributesList.get(0);

        if (is1D() || vecDimName.equals("D1")) {
            datasetDim = datasetAttributes.dim[0];
            position = axModes[0].getIndex(datasetAttributes, 0, pivot);
            size = dataset.getSize(datasetDim);
            phaseFraction = position / (size - 1.0);
        } else if (mapDim > 0) {
            datasetDim = datasetAttributes.dim[phaseAxis];
            position = axModes[phaseAxis].getIndex(datasetAttributes, phaseAxis, pivot);
            size = dataset.getSize(datasetDim);
            phaseFraction = position / (size - 1.0);
        }
        //System.out.printf("pivot %.3f map %d dDim %d size %d pos %.3f frac %.3f\n",pivot, mapDim,datasetDim,size,position,phaseFraction);
    }

    public double getPivotFraction() {
        return phaseFraction;
    }

    protected void dataItemAdded(Series series, int itemIndex, Data item) {
    }

    protected void dataItemRemoved(Data item, Series series) {
    }

    protected void dataItemChanged(Data item) {
    }

    protected void seriesAdded(Series series, int seriesIndex) {
//        int nData = 512;
//        for (int i = 0; i < nData; i++) {
//            double x = i;
//            double y = 50.0 * Math.sin(Math.PI * i / 100.0) + 50;
//            dList.add(x);
//            dList.add(y);
//        }
//        polyLine = new Polyline();
//        getPlotChildren().add(polyLine);
    }

    protected void seriesRemoved(Series series) {
    }

    protected void loadData() {
        bcPath = new Path();
        //xSliceLine.setMouseTransparent(true);
        //ySliceLine.setMouseTransparent(true);
        bcPath.setMouseTransparent(true);
        getPlotChildren().add(0, bcPath);
        double width = xAxis.getWidth();
        double height = yAxis.getHeight();

        canvas = new Canvas(width, height);
        canvas.setCache(true);
        peakCanvas = new Canvas(width, height);
        peakCanvas.setCache(true);
        peakCanvas.setMouseTransparent(true);
        annoCanvas = new Canvas(width, height);
        annoCanvas.setMouseTransparent(true);

        getPlotChildren().add(1, canvas);
        getPlotChildren().add(2, peakCanvas);
        getPlotChildren().add(3, annoCanvas);
        layoutChildren();
    }

    public void addAnnoCanvas() {
        if (annoCanvas != null) {
            double width = xAxis.getWidth();
            double height = yAxis.getHeight();
            annoCanvas = new Canvas(width, height);
            annoCanvas.setCache(true);
            annoCanvas.setMouseTransparent(true);
        }
    }

    public void hideCrossHairs() {
        SpectrumStatusBar statusBar = controller.getStatusBar();
        for (int iCross = 0; iCross < 2; iCross++) {
            for (int jOrient = 0; jOrient < 2; jOrient++) {
                crossHairLines[iCross][jOrient].setVisible(false);
                int iAxis = jOrient == 0 ? 1 : 0;
                double value = iCross == 1 ? axes[iAxis].getLowerBound() : axes[iAxis].getUpperBound();
                statusBar.setCrossText(jOrient, iCross, value, true);
            }
        }
    }

    public void setCrossHairState(boolean value) {
        for (int iCross = 0; iCross < 2; iCross++) {
            for (int jOrient = 0; jOrient < 2; jOrient++) {
                crossHairStates[iCross][jOrient] = value;
            }
        }
        if (!value) {
            hideCrossHairs();
        }
    }

    public void refreshCrossHairs() {
        SpectrumStatusBar statusBar = controller.getStatusBar();
        for (int iCross = 0; iCross < 2; iCross++) {
            for (int jOrient = 0; jOrient < 2; jOrient++) {
                int iAxis = jOrient == 0 ? 1 : 0;
                if (crossHairStates[iCross][jOrient] && crossHairLines[iCross][jOrient].isVisible()) {
                    drawCrossHair(iCross, jOrient);
                } else {
                    double value = iCross == 1 ? axes[iAxis].getLowerBound() : axes[iAxis].getUpperBound();
                    statusBar.setCrossText(jOrient, iCross, value, true);
                }
                statusBar.crossText[iCross][jOrient].setMin(axes[iAxis].getLowerBound());
                statusBar.crossText[iCross][jOrient].setMax(axes[iAxis].getUpperBound());
            }
        }
        drawSlices();
    }

    public void moveCrosshair(int iCross, int iOrient, double value) {
        if (datasetAttributesList.isEmpty()) {
            return;
        }
        value = crossHairInRange(iCross, iOrient, value);
        setCrossHairPosition(iCross, iOrient, value);
        drawCrossHair(iCross, iOrient);
        double aValue = crossHairPositions[iCross][iOrient];
        DatasetAttributes dataAttr = datasetAttributesList.get(0);
        String label;
        int axisDim = iOrient == VERTICAL ? 0 : 1;
        label = dataAttr.getLabel(axisDim);
        crossHairManager.updatePosition(this, iCross, iOrient, aValue, label);
        drawSlices();
    }

    public void syncCrosshair(int iCross, int iOrient, String dimLabel, double value) {
        if (datasetAttributesList.isEmpty()) {
            return;
        }
        DatasetAttributes dataAttr = datasetAttributesList.get(0);
        int jOrient = -1;
        if (dataAttr.getLabel(0).equals(dimLabel)) {
            if (value >= xAxis.getLowerBound() && (value <= xAxis.getUpperBound())) {
                jOrient = VERTICAL;
            }
        } else if (dataAttr.getLabel(1).equals(dimLabel)) {
            if (value >= yAxis.getLowerBound() && (value <= yAxis.getUpperBound())) {
                jOrient = HORIZONTAL;
            }
        }
        if (jOrient >= 0) {
            crossHairPositions[iCross][jOrient] = value;
            drawCrossHair(iCross, jOrient);
            if (iCross == 0) {
                drawSlices();
            }
        }
    }

    public double crossHairInRange(int iCross, int iOrient, double value) {
        if (value < 0) {
            value = 1;
        }
        double width = xAxis.getWidth();
        double height = yAxis.getHeight();

        if (iOrient == HORIZONTAL) {
            if (value > height) {
                value = height - 1;
            }
        } else if (value > width) {
            value = width - 1;
        }
        return value;

    }

    public void setCrossHairPosition(int iCross, int iOrient, double value) {
        if (iOrient == HORIZONTAL) {
            ValueAxis yAxis = (ValueAxis) getYAxis();
            value = yAxis.getValueForDisplay(value).doubleValue();
        } else {
            ValueAxis xAxis = (ValueAxis) getXAxis();
            value = xAxis.getValueForDisplay(value).doubleValue();
        }
        crossHairPositions[iCross][iOrient] = value;
    }

    public boolean hasCrosshairRegion() {
        boolean horizontalRegion = crossHairStates[0][VERTICAL] && crossHairLines[0][VERTICAL].isVisible()
                && crossHairStates[1][VERTICAL] && crossHairLines[1][VERTICAL].isVisible();
        boolean verticalRegion = crossHairStates[0][HORIZONTAL] && crossHairLines[0][HORIZONTAL].isVisible()
                && crossHairStates[1][HORIZONTAL] && crossHairLines[1][HORIZONTAL].isVisible();
        boolean hasRegion = false;
        if (is1D()) {
            hasRegion = horizontalRegion;
        } else {
            hasRegion = horizontalRegion && verticalRegion;
        }
        return hasRegion;
    }

    int getCrossHairNum(double x, double y, int iOrient) {
        int crossHairNum = 0;
        if (crossHairStates[1][iOrient] && crossHairLines[1][iOrient].isVisible()) {
            if (iOrient == HORIZONTAL) {
                double delta0 = Math.abs(crossHairLines[0][iOrient].getStartY() - y);
                double delta1 = Math.abs(crossHairLines[1][iOrient].getStartY() - y);
                if (delta1 < delta0) {
                    crossHairNum = 1;
                }
            } else {
                double delta0 = Math.abs(crossHairLines[0][iOrient].getStartX() - x);
                double delta1 = Math.abs(crossHairLines[1][iOrient].getStartX() - x);
                if (delta1 < delta0) {
                    crossHairNum = 1;
                }
            }
        } else if (!crossHairLines[0][iOrient].isVisible()) {
            crossHairNum = 0;
        } else if (iOrient == HORIZONTAL) {
            double delta0 = Math.abs(crossHairLines[0][iOrient].getStartY() - y);
            if (delta0 > CROSSHAIR_TOL) {
                crossHairNum = 1;
            }
        } else {
            double delta0 = Math.abs(crossHairLines[0][iOrient].getStartX() - x);
            if (delta0 > CROSSHAIR_TOL) {
                crossHairNum = 1;
            }
        }
        return crossHairNum;
    }

    public void setSliceStatus(boolean state) {
        refreshCrossHairs();
    }

    public void drawCrossHair(int iCross, int iOrient) {
        Dataset dataset = getDataset();
        if (dataset == null) {
            return;
        }
        double width = xAxis.getWidth();
        double height = yAxis.getHeight();
        if (crossHairStates[iCross][iOrient]) {
            double value = crossHairPositions[iCross][iOrient];
            controller.getStatusBar().setCrossText(iOrient, iCross, value, false);
            Bounds bounds = plotBackground.getBoundsInParent();
            if (iOrient == HORIZONTAL) {
                value = yAxis.getDisplayPosition(value);
                crossHairLines[iCross][iOrient].setStartX(0);
                crossHairLines[iCross][iOrient].setEndX(width);
                crossHairLines[iCross][iOrient].setStartY(value);
                crossHairLines[iCross][iOrient].setEndY(value);
            } else {
                value = xAxis.getDisplayPosition(value);
                crossHairLines[iCross][iOrient].setStartY(0);
                crossHairLines[iCross][iOrient].setEndY(height);
                crossHairLines[iCross][iOrient].setStartX(value);
                crossHairLines[iCross][iOrient].setEndX(value);
            }
            crossHairLines[iCross][iOrient].setVisible(true);
            crossHairLines[iCross][iOrient].setVisible(true);
            nList.clear();
            bcList.clear();
        }
    }

    public void drawSlices() {
        double width = xAxis.getWidth();
        double height = yAxis.getHeight();
        GraphicsContext annoGC = annoCanvas.getGraphicsContext2D();
        annoGC.clearRect(0, 0, width, height);
        if (sliceAttributes.slice1StateProperty().get()) {
            drawSlice(0, VERTICAL);
            drawSlice(0, HORIZONTAL);
        }
        if (sliceAttributes.slice2StateProperty().get()) {
            drawSlice(1, VERTICAL);
            drawSlice(1, HORIZONTAL);
        }
    }

    public void drawSlice(int iCross, int iOrient) {
        Dataset dataset = getDataset();
        if (dataset == null) {
            return;
        }
        int nDim = dataset.getNDim();
        Bounds bounds = plotBackground.getBoundsInParent();
        boolean xOn = false;
        boolean yOn = false;
        if (controller.sliceStatus.get() && sliceStatus.get()) {
            xOn = true;
            yOn = true;
        }
        if (controller.isPhaseSliderVisible()) {
            if (phaseAxis == 0) {
                yOn = false;
            } else {
                xOn = false;
            }
        }
        GraphicsContext annoGC = annoCanvas.getGraphicsContext2D();

        if ((nDim > 1) && controller.sliceStatus.get() && sliceStatus.get()) {
            if (((iOrient == HORIZONTAL) && xOn) || ((iOrient == VERTICAL) && yOn)) {
                for (DatasetAttributes datasetAttributes : datasetAttributesList) {
                    if (iOrient == HORIZONTAL) {
                        drawSpectrum.drawSlice(datasetAttributes, sliceAttributes, HORIZONTAL, crossHairPositions[iCross][VERTICAL], crossHairPositions[iCross][HORIZONTAL], bounds, getPh0(0), getPh1(0));
                    } else {
                        drawSpectrum.drawSlice(datasetAttributes, sliceAttributes, VERTICAL, crossHairPositions[iCross][VERTICAL], crossHairPositions[iCross][HORIZONTAL], bounds, getPh0(1), getPh1(1));
                    }
                    double[][] xy = drawSpectrum.getXY();
                    int nPoints = drawSpectrum.getNPoints();
                    if (sliceAttributes.useDatasetColorProperty().get()) {
                        annoGC.setStroke(datasetAttributes.getPosColor());
                    } else {
                        if (iCross == 0) {
                            annoGC.setStroke(sliceAttributes.getSlice1Color());
                        } else {
                            annoGC.setStroke(sliceAttributes.getSlice2Color());
                        }
                    }
                    annoGC.strokePolyline(xy[0], xy[1], nPoints);
                }
            }
        }
    }

    public void gotoMaxPlane() {
        Dataset dataset = getDataset();
        if (dataset != null) {
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);

            int nDim = dataset.getNDim();
            double cross1x = crossHairPositions[0][VERTICAL];
            double cross1y = crossHairPositions[0][HORIZONTAL];
            if (nDim == 3) {
                int[][] pt = new int[nDim][2];
                int[] cpt = new int[nDim];
                int[] dim = new int[nDim];
                double[] width = new double[nDim];
                for (int i = 0; i < nDim; i++) {
                    dim[i] = datasetAttributes.getDim(i);
                    if (i == 0) {
                        pt[0][0] = axModes[0].getIndex(datasetAttributes, 0, cross1x);
                        pt[0][1] = pt[0][0];
                    } else if (i == 1) {
                        pt[1][0] = axModes[1].getIndex(datasetAttributes, 1, cross1y);
                        pt[1][1] = pt[1][0];
                    } else {
                        pt[i][0] = axModes[i].getIndex(datasetAttributes, i, axes[i].getLowerBound());
                        pt[i][1] = axModes[i].getIndex(datasetAttributes, i, axes[i].getUpperBound());
                    }
                    cpt[i] = (pt[i][0] + pt[i][1]) / 2;
                    width[i] = (double) Math.abs(pt[i][0] - pt[i][1]);
                }
                RegionData rData;
                try {
                    rData = dataset.analyzeRegion(pt, cpt, width, dim);
                } catch (IOException ioE) {
                    return;
                }
                int[] maxPoint = rData.getMaxPoint();
                System.out.println(rData.getMax() + " " + maxPoint[2]);
                axes[2].setLowerBound(maxPoint[2]);
                axes[2].setUpperBound(maxPoint[2]);

            }
        }
    }

    class UpdateCrossHair implements java.util.function.DoubleFunction {

        final int crossHairNum;
        final int orientation;

        public UpdateCrossHair(int crossHairNum, int orientation) {
            this.crossHairNum = crossHairNum;
            this.orientation = orientation;
        }

        @Override
        public Object apply(double value) {
            crossHairPositions[crossHairNum][orientation] = value;
            refreshCrossHairs();

            return null;
        }

    }

    DoubleFunction getCrossHairUpdateFunction(int crossHairNum, int orientation) {
        UpdateCrossHair function = new UpdateCrossHair(crossHairNum, orientation);
        return function;
    }

    protected void exportVectorGraphics(String fileName, String fileType) throws IOException {
        Dataset dataset = getDataset();
        if (dataset == null) {
            System.out.println("no dataset");
            return;
        }
        double width = getWidth();
        double height = getHeight();
        if (is1D()) {
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);

            Vec specVec = null;
            int iChunk = 1;
            if (dataset.getVec() != null) {
                specVec = dataset.getVec();
            } else {
                specVec = new Vec(32);
                try {
                    datasetAttributes.Vector(specVec, iChunk--);
                } catch (IOException ioE) {
                    specVec = null;
                }
            }
            if (specVec != null) {
                Vec[] vecs = {specVec};
                try {
                    SpectrumWriter.writeVec(datasetAttributes, width, height, vecs, fileName, axes, axModes, fileType);
                } catch (GraphicsIOException ex) {
                    ExceptionDialog eDialog = new ExceptionDialog(ex);
                    eDialog.showAndWait();
                }
            }
        } else {
            try {
                SpectrumWriter.writeNDSpectrum(drawSpectrum, width, height, fileName, fileType);
            } catch (GraphicsIOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        }
    }

    public void printSpectrum() throws IOException {
        Dataset dataset = getDataset();
        if (dataset == null) {
            return;
        }
        if (is1D()) {
            if (dataset.getVec() == null) {
                return;
            }
            Vec vec = dataset.getVec();
            Vec[] vecs = {vec};
            DatasetAttributes datasetAttributes = datasetAttributesList.get(0);
            try {
                SpectrumWriter.printVec(datasetAttributes, vecs, axes, axModes);
            } catch (GraphicsIOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        } else {
            try {
                SpectrumWriter.printNDSpectrum(drawSpectrum);
            } catch (GraphicsIOException ex) {
                ExceptionDialog eDialog = new ExceptionDialog(ex);
                eDialog.showAndWait();
            }
        }
    }

    public Vec getVec() {
        Dataset dataset = getDataset();
        if (dataset == null) {
            return null;
        } else {
            return dataset.getVec();
        }
    }

    public void addSync(String name, int group) {
        if (getDimNames().contains(name)) {
            syncGroups.put(name, group);
        }
    }

    public void addSync(int iDim, int group) {
        if (iDim < getDimNames().size()) {
            String label = getDimNames().get(iDim);
            syncGroups.put(label, group);
        }
    }

    public int getSyncGroup(String name) {
        Integer result = syncGroups.get(name);
        return result == null ? 0 : result;
    }

    public int getSyncGroup(int iDim) {
        if (iDim < getDimNames().size()) {
            String label = getDimNames().get(iDim);
            return getSyncGroup(label);
        }
        return 0;
    }

    public static int getNSyncGroups() {
        return nSyncGroups;
    }

    public void syncSceneMates() {
        Map<String, Integer> syncMap = new HashMap<>();
        // get sync names for this chart
        for (String name : getDimNames()) {
            int iSync = getSyncGroup(name);
            if (iSync != 0) {
                syncMap.put(name, iSync);
            }
        }
        // add sync names from other charts if not already added
        for (PolyChart chart : getSceneMates(false)) {
            chart.getDimNames().stream().forEach((obj) -> {
                String name = (String) obj;
                int iSync = chart.getSyncGroup(name);
                if (iSync != 0) {
                    if (!syncMap.containsKey(name)) {
                        syncMap.put(name, iSync);
                    }
                }
            });
        }
        // now add new group for any missing names
        for (String name : getDimNames()) {
            if (!syncMap.containsKey(name)) {
                nSyncGroups++;
                syncMap.put(name, nSyncGroups);
            }
            addSync(name, syncMap.get(name));
        }
        for (PolyChart chart : getSceneMates(false)) {
            for (String name : getDimNames()) {
                if (chart.getDimNames().contains(name)) {
                    chart.addSync(name, getSyncGroup(name));
                }
            }
        }
    }

    List<PolyChart> getSceneMates(boolean includeThis) {
        List<PolyChart> sceneMates = new ArrayList<>();
        for (PolyChart chart : charts) {
            if (chart.getScene() == getScene()) {
                if (includeThis || (chart != this)) {
                    sceneMates.add(chart);
                }
            }
        }
        return sceneMates;
    }

    public void adjustLabels() {
        if (!datasetAttributesList.isEmpty()) {
            String[] dimNames;
            if (is1D()) {
                dimNames = new String[1];
                dimNames[0] = datasetAttributesList.get(0).getLabel(0);
            } else {
                dimNames = new String[2];
                dimNames[0] = datasetAttributesList.get(0).getLabel(0);
                dimNames[1] = datasetAttributesList.get(0).getLabel(1);
            }
            for (PeakListAttributes peakAttr : peakListAttributesList) {
                PeakList peakList = peakAttr.getPeakList();
                // double[] limits = {0.1, 0.8};
                int nCells = 25;
                try {
                    PeakNeighbors peakNeighbors = new PeakNeighbors(peakList, nCells, dimNames);
                    peakNeighbors.optimizePeakLabelPositions();
                } catch (IllegalArgumentException iAE) {
                    ExceptionDialog dialog = new ExceptionDialog(iAE);
                    dialog.showAndWait();
                }
            }
            if (!peakListAttributesList.isEmpty()) {
                refresh();
            }
        }

    }
}
