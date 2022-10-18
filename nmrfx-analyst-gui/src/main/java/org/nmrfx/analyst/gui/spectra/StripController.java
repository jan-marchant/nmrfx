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
package org.nmrfx.analyst.gui.spectra;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import org.nmrfx.analyst.gui.tools.StripsTable;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.ControllerTool;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.MainApp;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.project.ProjectBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author brucejohnson
 */
public class StripController implements ControllerTool {

    static final int X = 0;
    static final int Z = 1;
    FXMLController controller;
    Consumer<StripController> closeAction;
    ToolBar toolBar;
    ToolBar setupToolBar;
    ChoiceBox<PeakList> peakListChoiceBox;
    MenuButton[] dimMenus = new MenuButton[2];
    MenuButton actionMenu = new MenuButton("Actions");
    VBox vBox;
    TextField widthBox = new TextField();
    Slider posSlider = new Slider();
    Slider nSlider = new Slider();

    Spinner<Integer> itemSpinner;
    ChoiceBox<Integer> offsetBox;
    ChoiceBox<Integer> rowBox;
    ChoiceBox<PeakList> itemPeakListChoiceBox;
    ChoiceBox<Dataset> itemDatasetChoiceBox;

    List<StripItem> items = new ArrayList<>();

    List<Cell> cells = new ArrayList<>();
    String[] dimNames = new String[2];
    ChangeListener<Number> limitListener;
    Pattern resPat = Pattern.compile("([A-Z]*)([0-9]+)\\.(.*)");
    PeakList controlList = null;
    int currentRows = 0;
    int currentColumns = 0;
    int currentLow = 0;
    int currentHigh = 0;
    int frozen = -1;
    double xWidth = 0.2;
    StripsTable stripsTable;
    ObservableList<Peak> sortedPeaks;

    public StripController(FXMLController controller, Consumer<StripController> closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
    }

    public VBox getBox() {
        return vBox;
    }

    public void close() {
        closeAction.accept(this);
    }

    public void initialize(VBox vBox) {
        this.vBox = vBox;
        this.toolBar = new ToolBar();
        this.setupToolBar = new ToolBar();
        this.vBox.getChildren().addAll(toolBar, setupToolBar);

        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "Close", MainApp.ICON_SIZE_STR, MainApp.ICON_FONT_SIZE_STR, ContentDisplay.TOP);
        closeButton.setOnAction(e -> close());
        toolBar.getItems().add(closeButton);

        peakListChoiceBox = new ChoiceBox<>();
        toolBar.getItems().addAll(new Label("Control List:"), peakListChoiceBox);

        dimMenus[X] = new MenuButton("X");
        dimMenus[Z] = new MenuButton("Z");
        widthBox.setMaxWidth(50);
        widthBox.setText(String.format("%.1f", xWidth));
        widthBox.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                xWidth = Double.parseDouble(widthBox.getText());
            }
        });

        toolBar.getItems().add(dimMenus[X]);
        toolBar.getItems().add(dimMenus[Z]);
        toolBar.getItems().add(widthBox);
        toolBar.getItems().add(actionMenu);

        Menu addMenu = new Menu("Add");
        actionMenu.getItems().add(addMenu);

        Menu freezeThawMenu = new Menu("Freeze/Thaw");
        actionMenu.getItems().add(freezeThawMenu);

        MenuItem freezeMenuItem = new MenuItem("Freeze");
        freezeThawMenu.getItems().add(freezeMenuItem);
        freezeMenuItem.setOnAction(e -> freezeChart());

        MenuItem thawMenuItem = new MenuItem("Thaw");
        freezeThawMenu.getItems().add(thawMenuItem);
        thawMenuItem.setOnAction(e -> thawChart());

        Menu sortMenu = new Menu("Sort");

        MenuItem sortByResidueMenuItem = new MenuItem("By Residue");
        sortMenu.getItems().add(sortByResidueMenuItem);
        sortByResidueMenuItem.setOnAction(e -> sortPeaksByResidue());

        MenuItem sortByIndex = new MenuItem("By Index");
        sortMenu.getItems().add(sortByIndex);
        sortByIndex.setOnAction(e -> sortPeaksByIndex());

        actionMenu.getItems().add(sortMenu);

        ToolBarUtils.addFiller(toolBar, 25, 50);
        Label startLabel = new Label("Start:");
        toolBar.getItems().add(startLabel);
        posSlider.setBlockIncrement(1);
        posSlider.setSnapToTicks(true);
        posSlider.setMinWidth(250);
        toolBar.getItems().add(posSlider);

        Label nLabel = new Label("N:");
        toolBar.getItems().add(nLabel);
        nSlider.setBlockIncrement(1);
        nSlider.setMinWidth(100);
        toolBar.getItems().add(nSlider);

        MapChangeListener<String, PeakList> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> updatePeakListMenu();

        limitListener = (observable, oldValue, newValue) -> updateView(false);

        Button addButton = GlyphsDude.createIconButton(FontAwesomeIcon.PLUS);
        addButton.setOnAction(e -> addItem());
        Button removeButton = GlyphsDude.createIconButton(FontAwesomeIcon.REMOVE);
        removeButton.setOnAction(e -> removeItem());

        itemPeakListChoiceBox = new ChoiceBox<>();
        itemDatasetChoiceBox = new ChoiceBox<>();
        itemPeakListChoiceBox.setValue(null);
        itemDatasetChoiceBox.setValue(null);
        itemPeakListChoiceBox.setMinWidth(150);
        itemDatasetChoiceBox.setMinWidth(150);
        itemSpinner = new Spinner<>(0, 0, 0);
        itemSpinner.setMaxWidth(75);
        itemSpinner.getValueFactory().valueProperty().addListener(e -> showItem());
        ProjectBase.getActive().addDatasetListListener((MapChangeListener) (e -> updateDatasetNames()));

        Label offsetLabel = new Label("Offset:");
        offsetBox = new ChoiceBox<>();
        offsetBox.getItems().addAll(0, 1, 2, 3, 4);
        offsetBox.setValue(0);
        offsetBox.setOnAction(e -> updateItem());
        Label rowLabel = new Label("Row:");
        rowBox = new ChoiceBox<>();
        rowBox.getItems().addAll(0, 1, 2, 3, 4);
        rowBox.setValue(0);
        rowBox.setOnAction(e -> updateItem());
        setupToolBar.getItems().addAll(addButton, removeButton, itemSpinner,
                new Label("Peaklist:"), itemPeakListChoiceBox,
                new Label("Dataset:"), itemDatasetChoiceBox,
                offsetLabel, offsetBox, rowLabel, rowBox);

        ProjectBase.getActive().addPeakListListener(mapChangeListener);
        updatePeakListMenu();
        updateDatasetNames();
        StripItem item = new StripItem();
        items.add(item);
        VBox stripsBox = new VBox();
        stripsTable = new StripsTable(controller, this, stripsBox);
        controller.getMainBox().setRight(stripsBox);
        sortedPeaks = stripsTable.getSortedPeaks();
    }

    void updateDimMenus(String rowName, Dataset dataset) {
        int nDim = dataset.getNDim();
        final int jDim = rowName.equals("X") ? X : Z;
        MenuButton dimMenu = dimMenus[jDim];
        dimMenu.getItems().clear();
        for (int iDim = 0; iDim < nDim; iDim++) {
            String dimName = dataset.getLabel(iDim);
            MenuItem menuItem = new MenuItem(iDim + 1 + ":" + dimName);
            menuItem.setOnAction(e -> updateDimMenu(jDim, dimName));
            dimMenu.getItems().add(menuItem);
        }
    }

    void updateDimMenu(int jDim, String dimName) {
        dimNames[jDim] = dimName;
        updateCells();
        currentLow = -1;
        currentHigh = -1;
        currentRows = 0;
        currentColumns = 0;
        updateView(false);
    }

    public void updatePeakListMenu() {
        peakListChoiceBox.getItems().clear();
        itemPeakListChoiceBox.getItems().clear();

        ProjectBase.getActive().getPeakLists().stream().sorted(Comparator.comparing(PeakList::getName)).
                forEach(peakList -> {
                    peakListChoiceBox.getItems().add(peakList);
                    itemPeakListChoiceBox.getItems().add(peakList);
                });
        peakListChoiceBox.setValue(null);
        itemPeakListChoiceBox.setValue(null);
        peakListChoiceBox.setOnAction(e -> setPeakList(peakListChoiceBox.getValue()));
        itemPeakListChoiceBox.setOnAction(e -> setItemPeakList(itemPeakListChoiceBox.getValue()));

    }

    public void updateDatasetNames() {
        itemDatasetChoiceBox.getItems().clear();
        Dataset.datasets().stream().forEach(dataset -> itemDatasetChoiceBox.getItems().add((Dataset) dataset));
        itemDatasetChoiceBox.setValue(null);
        itemDatasetChoiceBox.setOnAction(e -> setItemDataset(itemDatasetChoiceBox.getValue()));
    }

    public PeakList getControlList() {
        return controlList;
    }

    void setPeakList(PeakList peakList) {
        controlList = peakList;
        StripItem item = getCurrentItem();
        if (item.peakList == null) {
            item.peakList = controlList;
            item.dataset = Dataset.getDataset(controlList.getDatasetName());
            item.row = 0;
            item.offset = 0;
        }
        showItem();
        addPeaks(controlList.peaks());
        updateView(true);
    }

    void setItemPeakList(PeakList peakList) {
        StripItem item = getCurrentItem();
        item.peakList = peakList;
        if (item.peakList != null) {
            item.dataset = Dataset.getDataset(item.peakList.getDatasetName());
        }
        itemDatasetChoiceBox.setValue(item.dataset);
    }

    void setItemDataset(Dataset dataset) {
        StripItem item = getCurrentItem();
        item.dataset = dataset;
        item.row = rowBox.getValue();
        item.offset = offsetBox.getValue();
    }

    StripItem getCurrentItem() {
        int item = itemSpinner.getValue();
        return items.get(item);
    }

    void removeItem() {
        int item = itemSpinner.getValue();
        if (items.size() > 1) {
            items.remove(item);
            SpinnerValueFactory.IntegerSpinnerValueFactory factory
                    = (SpinnerValueFactory.IntegerSpinnerValueFactory) itemSpinner.getValueFactory();
            factory.setMax(items.size() - 1);
            if (item >= items.size()) {
                item--;
            }
            factory.setValue(item);
            showItem();
        }
    }

    void addItem() {
        items.add(new StripItem());
        SpinnerValueFactory.IntegerSpinnerValueFactory factory
                = (SpinnerValueFactory.IntegerSpinnerValueFactory) itemSpinner.getValueFactory();
        factory.setMax(items.size() - 1);
        factory.setValue(items.size() - 1);
        clearItem();
    }

    void clearItem() {
        StripItem item = getCurrentItem();
        item.peakList = null;
        item.dataset = null;
        item.row = 0;
        item.offset = 0;
        itemPeakListChoiceBox.setValue(null);
        itemDatasetChoiceBox.setValue(null);
        rowBox.setValue(0);
        offsetBox.setValue(0);

    }

    void showItem() {
        StripItem item = getCurrentItem();
        itemDatasetChoiceBox.setValue(item.dataset);
        itemPeakListChoiceBox.setValue(item.peakList);
        rowBox.setValue(item.row);
        offsetBox.setValue(item.offset);
    }

    void updateItem() {
        StripItem item = getCurrentItem();
        item.peakList = itemPeakListChoiceBox.getValue();
        item.dataset = itemDatasetChoiceBox.getValue();
        item.row = rowBox.getValue();
        item.offset = offsetBox.getValue();
    }

    int getMaxOffset() {
        int maxOffset = 0;
        for (StripItem item : items) {
            int offset = item.offset;
            maxOffset = Math.max(offset, maxOffset);
        }
        return maxOffset;
    }

    int getMaxRow() {
        int maxRow = 0;
        for (StripItem item : items) {
            int row = item.row;
            maxRow = Math.max(row, maxRow);
        }
        return maxRow;
    }

    int getDim(DatasetBase dataset, String dimName) {
        int datasetDim = -1;
        for (int i = 0; i < dataset.getNDim(); i++) {
            if (dimName.equals(dataset.getLabel(i))) {
                datasetDim = i;
            }
        }
        return datasetDim;
    }

    int[] getDims(DatasetBase dataset) {
        int[] dims = new int[dataset.getNDim()];
        if ((dimNames[X] == null) || (dimNames[Z] == null)) {
            for (int i = 0; i < dims.length; i++) {
                dims[i] = i;
            }
        } else {
            Arrays.fill(dims, -1);
            dims[0] = getDim(dataset, dimNames[X]);
            if (dims.length > 2) {
                dims[2] = getDim(dataset, dimNames[Z]);
            }

            for (int i = 0; i < dims.length; i++) {
                if (dims[i] == -1) {
                    for (int k = 0; k < dims.length; k++) {
                        boolean unused = true;
                        for (int dim : dims) {
                            if (dim == k) {
                                unused = false;
                                break;
                            }
                        }
                        if (unused) {
                            dims[i] = k;
                        }
                    }
                }
            }
        }
        return dims;
    }

    double[] getPositions(Peak peak, String[] dimNames) {
        double[] positions = new double[dimNames.length];
        for (int i = 0; i < positions.length; i++) {
            PeakDim peakDim = peak.getPeakDim(dimNames[i]);
            if (peakDim != null) {
                positions[i] = peakDim.getChemShiftValue();
            }
        }
        return positions;
    }

    public void addPeaks(List<Peak> sourcePeaks) {
        ObservableList<Peak> peaks = FXCollections.observableList(sourcePeaks);
        stripsTable.updatePeaks(peaks);
        var matchPeaks = peaks.stream().map(peak -> new PeakMatchResult(peak, 0.0)).toList();
        stripsTable.updatePeakSorterPeaks(FXCollections.observableList(matchPeaks));
        updatePeaks();
    }

    public void updatePeaks() {
        List<Peak> peaks = stripsTable.getSortedPeaks();
        posSlider.valueProperty().removeListener(limitListener);
        nSlider.valueProperty().removeListener(limitListener);
        cells.clear();
        int nPeaks = peaks.size();
        posSlider.setMin(0.0);
        nSlider.setMin(1);
        nSlider.setMax(30);
        if ((nPeaks - 1) != posSlider.getMax()) {
            posSlider.setMax(Math.max(0, nPeaks-1));
            posSlider.setValue(0);
            nSlider.setValue(5);
        }

        if (nPeaks < 10) {
            posSlider.setMajorTickUnit(1);
            posSlider.setMinorTickCount(0);
        } else {
            posSlider.setMajorTickUnit(10);
            posSlider.setMinorTickCount(8);
        }
        int majorTick = peaks.size() < 10 ? peaks.size() - 1 : 10;
        posSlider.setMajorTickUnit(10);
        posSlider.setMinorTickCount(majorTick);
        boolean firstPeak = true;
        for (Peak peak : peaks) {
            Dataset dataset = Dataset.getDataset(peak.getPeakList().getDatasetName());
            if (dataset != null) {
                if (firstPeak) {
                    updateDimMenus("X", dataset);
                    updateDimMenus("Z", dataset);
                    if (dimNames[0] == null) {
                        dimNames[0] = dataset.getLabel(0);
                    }
                    if (dimNames[1] == null) {
                        dimNames[1] = dataset.getLabel(1);
                    }
                    firstPeak = false;
                }
                double[] positions = getPositions(peak, dimNames);
                Cell cell = new Cell(peak, positions);
                cells.add(cell);
            }
        }
        currentLow = 0;
        currentHigh = 0;
        posSlider.valueProperty().addListener(limitListener);
        nSlider.valueProperty().addListener(limitListener);
        updateView(true);
    }

    void updateCells() {
        for (Cell cell : cells) {
            cell.updateCell();
        }
    }

    static class StripItem {

        Dataset dataset;
        PeakList peakList;
        int offset = 0;
        int row = 0;

        public StripItem() {

        }

        public StripItem(Dataset dataset, PeakList peakList, int offset, int row) {
            this.dataset = dataset;
            this.peakList = peakList;
            this.offset = offset;
            this.row = row;
        }
    }

    class Cell {

        Peak peak;
        double[] positions;

        public Cell(Dataset dataset, Peak peak) {

        }

        public Cell(Peak peak, double[] positions) {
            this.peak = peak;
            this.positions = positions;
        }

        void updateCell() {
            positions = getPositions(peak, dimNames);
        }

        void updateChart(PolyChart chart, StripItem item, boolean init) {
            controller.setActiveChart(chart);
            if (item.dataset != null) {
                if (init) {
                    controller.addDataset(item.dataset, false, false);
                }
                chart.setDataset(item.dataset);
                DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                int[] dims = getDims(dataAttr.getDataset());
                for (int i = 0; i < dims.length; i++) {
                    dataAttr.setDim(i, dims[i]);
                }
                chart.setAxis(0, positions[0] - xWidth / 2.0, positions[0] + xWidth / 2.0);
                if (item.peakList != null) {
                    PeakListAttributes peakAttr = chart.setupPeakListAttributes(item.peakList);
                    peakAttr.setLabelType(PeakDisplayParameters.LabelTypes.SglResidue);
                }
                chart.full(1);
                for (int i = 1; i < positions.length; i++) {
                    chart.setAxis(1 + i, positions[i], positions[i]);
                }
            }
            chart.useImmediateMode(true);
        }
    }

    public void setCenter(int index) {
        int nActive = (int) nSlider.getValue();
        int start = index - nActive / 2;
        posSlider.setValue(start);
    }

    public void updateView(boolean forced) {
        int low = (int) posSlider.getValue();
        int nActive = (int) nSlider.getValue();
        if (nActive > cells.size()) {
            nActive = cells.size();
        }
        if (low < 0) {
            low = 0;
        }
        int high = low + nActive - 1;
        if (high >= cells.size()) {
            high = cells.size() - 1;
            low = high - nActive + 1;
        }
        if (forced || (low != currentLow) || (high != currentHigh)) {
            controller.setChartDisable(true);
            int nItems = high - low + 1;
            if (frozen >= 0) {
                nItems++;
            }
            int maxOffset = getMaxOffset();
            int maxRow = getMaxRow();
            int nCols = nItems * (maxOffset + 1);
            if (nCols == 0) {
                nCols = 1;
            }
            boolean updated = grid(maxRow + 1, nCols);
            List<PolyChart> charts = controller.getCharts();
            for (int iCell = low; iCell <= high; iCell++) {
                Cell cell = cells.get(iCell);
                int jCell = iCell - low;
                if ((frozen >= 0) && (jCell >= frozen)) {
                    jCell++;
                }
                for (StripItem item : items) {
                    int iCol = jCell * (maxOffset + 1) + item.offset;
                    int iRow = item.row;
                    int iChart = iRow * nCols + iCol;
                    PolyChart chart = charts.get(iChart);
                    cell.updateChart(chart, item, updated && (iCol == 0));
                    if (iCol == 0) {
                        chart.updateAxisType();
                    }
                }
                updated = false;
            }
            currentLow = low;
            currentHigh = high;
            if (frozen >= 0) {
                charts.get(frozen).setActiveChart();
            }
            controller.setChartDisable(false);
            controller.draw();
        }
    }

    public boolean grid(int rows, int columns) {
        boolean result = false;
        if ((currentRows != rows) || (currentColumns != columns)) {
            int nCharts = rows * columns;
            if (nCharts > 0) {
                controller.setNCharts(nCharts);
                controller.arrange(rows);
                controller.setBorderState(true);
                PolyChart chartActive = controller.getCharts().get(0);
                controller.setActiveChart(chartActive);
                currentRows = rows;
                currentColumns = columns;
                result = true;
            }
        }
        return result;
    }

    public record PeakMatchResult(Peak peak, double score) {}

    public List<PeakMatchResult> matchPeaks(Peak peak) {
        var originPeaks = getMatchPeaks(peak);
        List<PeakMatchResult> result = new ArrayList<>();
        controlList.peaks().forEach(comparePeak -> {
            var comparePeaks = getMatchPeaks(comparePeak);
            double score = scorePeakMatch(originPeaks, comparePeaks);
            PeakMatchResult matchResult = new PeakMatchResult(comparePeak, score);
            result.add(matchResult);
        });
        result.sort(Comparator.comparing(PeakMatchResult::score).reversed());
        return result;
    }

    double scorePeakMatch(List<Peak> originPeaks, List<Peak> comparePeaks) {
        double tol = 0.6;
        double sumScore = 0.0;
        for (Peak originPeak:originPeaks) {
            for (Peak comparePeak: comparePeaks) {
                if (originPeak != comparePeak) {
                    for(PeakDim originPeakDim:originPeak.peakDims) {
                        String dimName = originPeakDim.getDimName();
                        if (!dimName.equals(dimNames[X]) && !dimName.equals(dimNames[Z])) {
                            PeakDim comparePeakDim = comparePeak.getPeakDim(dimName);
                            double delta = Math.abs(originPeakDim.getAdjustedChemShiftValue() - comparePeakDim.getChemShiftValue());
                            if (delta < tol) {
                                 double score = 1.0 - delta/ tol;
                                 sumScore += score;
                            }
                        }
                    }
                }
            }
        }
        return sumScore;
    }

    public List<Peak> getMatchPeaks(Peak peak) {
        PeakDim xPeakDim = peak.getPeakDim(dimNames[X]);
        PeakDim zPeakDim = peak.getPeakDim(dimNames[Z]);
        double[] searchPPMs = {xPeakDim.getChemShiftValue(), zPeakDim.getChemShiftValue()};
        double xTol = 0.05;
        double zTol = 0.3;

        List<Peak> allFoundPeaks = new ArrayList<>();
        for (var item : items) {
            PeakList itemList = item.peakList;
            if (itemList != null) {
                itemList.clearSearchDims();
                itemList.addSearchDim(dimNames[X], xTol);
                itemList.addSearchDim(dimNames[Z], zTol);
                var foundPeaks = itemList.findPeaks(searchPPMs);
                allFoundPeaks.addAll(foundPeaks);
            }
        }
        return allFoundPeaks;
    }

    void freezeChart() {
        PolyChart activeChart = controller.getActiveChart();
        frozen = controller.getCharts().indexOf(activeChart) / (getMaxOffset() + 1);
        currentLow = -1;
        currentHigh = -1;
        updateView(false);
    }

    void thawChart() {
        frozen = -1;
        currentLow = -1;
        currentHigh = -1;
        updateView(false);
    }

    class PeakSortComparator implements Comparator<Cell> {

        @Override
        public int compare(Cell o1, Cell o2) {
            String lab1 = o1.peak.getPeakDim(dimNames[X]).getLabel();
            String lab2 = o2.peak.getPeakDim(dimNames[X]).getLabel();
            Matcher match1 = resPat.matcher(lab1);
            Matcher match2 = resPat.matcher(lab2);
            int res1 = -9999;
            int res2 = -9999;
            if (match1.matches()) {
                res1 = Integer.parseInt(match1.group(2));
            }
            if (match2.matches()) {
                res2 = Integer.parseInt(match2.group(2));
            }
            return Integer.compare(res1, res2);
        }
    }

    class PeakIndexSortComparator implements Comparator<Cell> {

        @Override
        public int compare(Cell o1, Cell o2) {
            int i1 = o1.peak.getIdNum();
            int i2 = o2.peak.getIdNum();
            return Integer.compare(i1, i2);
        }
    }

    void sortPeaksByResidue() {
        cells.sort(new PeakSortComparator());
        updateView(true);
    }

    void sortPeaksByIndex() {
        cells.sort(new PeakIndexSortComparator());
        updateView(true);
    }
}
