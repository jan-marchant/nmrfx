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

 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import java.io.IOException;
import org.nmrfx.processor.datasets.Dataset;
import java.text.DecimalFormat;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.math.VecBase;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;

/**
 *
 * @author Bruce Johnson
 */
public class SpectrumMeasureBar {

    static final DecimalFormat formatter = new DecimalFormat();

    static {
        formatter.setMaximumFractionDigits(2);
    }
    TextField[][][] crossText = new TextField[3][2][2];
    TextField[] intensityField = new TextField[2];
    TextField sdevField = new TextField();
    TextField snField = new TextField();
    FXMLController controller;
    GridPane gridPane;
    boolean[][] iconStates = new boolean[2][2];
    ChangeListener<String> vecNumListener;
    Pane filler1 = new Pane();
    Pane filler2 = new Pane();
    static Background errorBackground = new Background(new BackgroundFill(Color.ORANGE, CornerRadii.EMPTY, Insets.EMPTY));
    Background defaultBackground = null;
    Consumer closeAction = null;
    ToggleButton absModeButton;
    ToggleButton gridModeButton;
    PolyChart chart = null;
    DatasetBase dataset = null;
    Double sDev = null;

    public SpectrumMeasureBar(FXMLController controller, Consumer closeAction) {
        this.controller = controller;
        this.closeAction = closeAction;
    }

    public void buildBar(GridPane gridPane) {
        this.gridPane = gridPane;
        String iconSize = "12px";
        String fontSize = "7pt";
        Font font = new Font(10);
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        closeButton.setOnAction(e -> close());
        absModeButton = new ToggleButton("SF");
        gridModeButton = new ToggleButton("Grid");
        absModeButton.setFont(font);
        gridModeButton.setFont(font);
        absModeButton.setOnAction(e -> update());
        gridModeButton.setOnAction(e -> update());
        Button sdevButton = new Button("SD");
        sdevButton.setStyle("-fx-font-weight: normal; -fx-font-size:10pt;");
        sdevButton.setFont(font);
        sdevButton.setOnAction(e -> measureSDev());

        gridPane.add(closeButton, 0, 0);
        gridPane.add(absModeButton, 0, 1);
        gridPane.add(gridModeButton, 0, 2);
        double[] prefWidths = {75.0, 120.0};
        String[] rowNames = {"1", "2", "\u0394"};

        String[] xys = {"x", "y"};
        for (int row = 0; row < rowNames.length; row++) {
            for (int col = 0; col < xys.length; col++) {
                Label label = new Label(rowNames[row] + xys[col] + ":");
                label.setFont(font);
                label.setPrefWidth(40);
                label.setTextAlignment(TextAlignment.RIGHT);
                label.setAlignment(Pos.CENTER_RIGHT);
                gridPane.add(label, col * 3 + 1, row);

            }
        }

        for (int iCross = 0; iCross < 3; iCross++) {
            for (int jOrient = 1; jOrient >= 0; jOrient--) {
                int jDim = jOrient == 0 ? 1 : 0;
                for (int kType = 0; kType < 2; kType++) {
                    crossText[iCross][jOrient][kType] = new TextField();
                    crossText[iCross][jOrient][kType].setPrefWidth(prefWidths[kType]);
                    crossText[iCross][jOrient][kType].setFont(font);
                    gridPane.add(crossText[iCross][jOrient][kType], 2 + jDim * 3 + kType, iCross);
                }
            }
        }
        for (int i = 0; i < intensityField.length; i++) {
            intensityField[i] = new TextField();
            intensityField[i].setPrefWidth(100.0);
            intensityField[i].setFont(font);
            Label label = new Label("Int " + (i + 1) + ":");
            label.setFont(font);
            label.setPrefWidth(60);
            label.setTextAlignment(TextAlignment.RIGHT);
            label.setAlignment(Pos.CENTER_RIGHT);
            gridPane.add(label, 7, i);
            gridPane.add(intensityField[i], 8, i);
        }
        sdevField.setFont(font);
        sdevField.setPrefWidth(100.0);
        snField.setFont(font);
        snField.setPrefWidth(100.0);
        Label snLabel = new Label("S/N:");
        snLabel.setFont(font);
        snLabel.setPrefWidth(60);
        snLabel.setTextAlignment(TextAlignment.RIGHT);
        snLabel.setAlignment(Pos.CENTER_RIGHT);

        GridPane.setHalignment(sdevButton, HPos.RIGHT);
        gridPane.add(sdevButton, 9, 0);
        gridPane.add(sdevField, 10, 0);
        gridPane.add(snLabel, 9, 1);
        gridPane.add(snField, 10, 1);

    }

    public GridPane getToolBar() {
        return gridPane;
    }

    public void close() {
        closeAction.accept(this);
    }

    public SpectrumMeasureBar onClose(Consumer closeAction) {
        this.closeAction = closeAction;
        return this;
    }

    private StackPane makeIcon(int i, int j, boolean boundMode) {
        StackPane stackPane = new StackPane();
        stackPane.setPadding(Insets.EMPTY);
        Rectangle rect = new Rectangle(10, 10);
        rect.setFill(Color.LIGHTGREY);
        rect.setStroke(Color.LIGHTGREY);
        Line line = new Line();
        if (j == 0) {
            line.setStartX(0.0f);
            line.setStartY(8.0f);
            line.setEndX(10.0f);
            line.setEndY(8.0f);
            if (boundMode) {
                if (i == 0) {
                    line.setTranslateY(4);
                } else {
                    line.setTranslateY(-4);

                }
            }
        } else {
            line.setStartX(8.0f);
            line.setStartY(0.0f);
            line.setEndX(8.0f);
            line.setEndY(10.0f);
            if (boundMode) {
                if (i == 0) {
                    line.setTranslateX(-4);
                } else {
                    line.setTranslateX(4);

                }
            }
        }
        stackPane.getChildren().add(rect);
        stackPane.getChildren().add(line);
        if (i == 1) {
            line.setStroke(Color.RED);
        } else {
            line.setStroke(Color.BLACK);
        }
        return stackPane;
    }

    public void update() {
        if (chart != null) {
            for (int iOrient = 0; iOrient < 2; iOrient++) {
                Double value0 = chart.crossHairStates[0][iOrient] ? chart.crossHairPositions[0][iOrient] : null;
                Double value1 = chart.crossHairStates[1][iOrient] ? chart.crossHairPositions[1][iOrient] : null;
                setCrossText(chart, dataset, iOrient, value0, value1);
            }
        }
    }

    public void getIntensity(PolyChart chart, DatasetBase dataset, int iCross) {
        int nDim = dataset.getNDim();
        int[] pts = new int[nDim];
        boolean ok = true;
        for (int i = 0; i < dataset.getNDim(); i++) {
            int disDim0 = chart.getDatasetAttributes().get(0).getDim(i);
            int jOrient = i == 0 ? 1 : 0;
            Double value = null;
            if (i > 1) {
                int pt1 = (int) chart.axes[i].getLowerBound();
                int pt2 = (int) chart.axes[i].getUpperBound();
                pts[disDim0] = (pt1 + pt2) / 2;
            } else {
                value = chart.crossHairStates[iCross][jOrient] ? chart.crossHairPositions[iCross][jOrient] : null;
                if (value == null) {
                    ok = false;
                    break;
                }
                pts[disDim0] = dataset.ppmToPoint(disDim0, value);
            }
        }
        if (ok) {
            String strValue;
            try {
                double value = dataset.readPoint(pts);
                strValue = String.format("%.7f", value);
                intensityField[iCross].setText(strValue);
                if ((iCross == 0) && (sDev != null)) {
                    double sn = value / sDev;
                    snField.setText(String.format("%.3f", sn));

                }
            } catch (IOException | IllegalArgumentException ex) {
                strValue = "";
            }
            intensityField[iCross].setText(strValue);
        }
    }

    public void setCrossText(PolyChart chart, DatasetBase dataset, int iOrient, Double... values) {
        this.chart = chart;
        this.dataset = dataset;
        double[] pts = new double[2];
        double[] hzs = new double[2];
        double[] mHzs = new double[2];
        VecBase vec = dataset.getVec();
        boolean gridMode = gridModeButton.isSelected();
        boolean absMode = absModeButton.isSelected();

        for (int iCross = 0; iCross < 3; iCross++) {
            String strValue = "";
            String strPtValue = "";
            int chartDim = iOrient == 0 ? 1 : 0;
            if ((chartDim == 0) || (chart.getNDim() > 1)) {
                int disDim = chart.getDatasetAttributes().get(0).getDim(chartDim);
                boolean freqMode = dataset.getFreqDomain(disDim);
                if (iCross < values.length) {
                    Double value = iCross < values.length ? values[iCross] : 0.0;
                    if (freqMode) {
                        double hz;
                        int pt = dataset.ppmToPoint(disDim, value);
                        if (gridMode) {
                            pts[iCross] = pt;
                            hz = dataset.pointToHz(disDim, pts[iCross]);
                            hz = -(hz - dataset.getSw(disDim) / 2.0);
                            strPtValue = String.format("%d pts", pt);
                        } else {
                            pts[iCross] = dataset.ppmToDPoint(disDim, value);
                            hz = dataset.pointToHz(disDim, pts[iCross]);
                            hz = -(hz - dataset.getSw(disDim) / 2.0);
                            strPtValue = String.format("%.1f pts", pts[iCross]);
                        }

                        hzs[iCross] = hz;
                        mHzs[iCross] = dataset.getSf(disDim) * 1.0e6 + hz;
                        if (absMode) {
                            strValue = String.format("%,.1f Hz", mHzs[iCross]);
                        } else {
                            strValue = String.format("%.1f Hz", hz);
                        }
                    } else {
                        pts[iCross] = (int) Math.round(value * dataset.getSw(disDim));
                        hzs[iCross] = value;
                        mHzs[iCross] = value;
                        strPtValue = String.valueOf(pts[iCross]) + " pts";
                        strValue = String.format("%.1f Hz", value);
                    }
                } else {
                    if ((values.length == 2) && (values[0] != null) && (values[1] != null)) {
                        strValue = String.format("%.1f Hz", Math.abs(hzs[1] - hzs[0]));
                        strPtValue = String.format("%.0f pts", Math.abs(pts[1] - pts[0]));
                    }
                }
            }
            crossText[iCross][iOrient][0].setText(strPtValue);
            crossText[iCross][iOrient][1].setText(strValue);
        }
        getIntensity(chart, dataset, 0);
        getIntensity(chart, dataset, 1);

    }

    protected Double measureSDev() {
        if (chart == null) {
            return null;
        }
        sdevField.setText("");
        Optional<DatasetAttributes> dataAttrOpt = chart.getFirstDatasetAttributes();
        if (dataAttrOpt.isPresent()) {
            DatasetAttributes dataAttr = dataAttrOpt.get();

            DatasetBase datasetBase = dataAttr.getDataset();
            Dataset dataset = (Dataset) datasetBase;

            int nDim = dataset.getNDim();
            int[][] pt = new int[nDim][2];
            int[] dim = new int[nDim];
            int[] width = new int[nDim];
            for (int iDim = 0; iDim < nDim; iDim++) {
                int[] limits = new int[2];
                limits[0] = chart.axModes[iDim].getIndex(dataAttr, iDim, chart.axes[iDim].getLowerBound());
                limits[1] = chart.axModes[iDim].getIndex(dataAttr, iDim, chart.axes[iDim].getUpperBound());

                if (limits[0] < limits[1]) {
                    pt[iDim][0] = limits[0];
                    pt[iDim][1] = limits[1];
                } else {
                    pt[iDim][0] = limits[1];
                    pt[iDim][1] = limits[0];
                }
                dim[iDim] = dataAttr.dim[iDim];
                width[iDim] = pt[iDim][1] - pt[iDim][0];
            }
            int[][] ptTest = new int[nDim][2];
            for (int iDim = 0; iDim < nDim; iDim++) {
                ptTest[iDim] = pt[iDim].clone();
            }
            try {
                int cols = width[0] / 16;
                if (cols > 512) {
                    cols = 512;
                } else if (cols < 4) {
                    cols = 4;
                }
                int colIncr = 1;
                int rowIncr = 1;
                int m = 1;
                int rows = 1;
                if (nDim > 1) {
                    cols = cols / 4;
                    if (cols < 8) {
                        cols = 8;
                    }
                    rows = width[1] / 16;
                    if (rows > 32) {
                        rows = 32;
                    } else if (rows < 4) {
                        rows = 4;
                    }
                    if (rows > width[1]) {
                        rows = width[1] / 4;
                    }
                    if (rows < 1) {
                        rows = 1;
                    }
                    int nPoints = cols * rows;
                    if (nPoints > 1024) {
                        cols = (int) (cols * Math.sqrt(1.0 * 1024 / nPoints));
                        rows = (int) (rows * Math.sqrt(1.0 * 1024 / nPoints));
                    }
                    if (rows < 1) {
                        rows = 1;
                    }

                    m = pt[1][1] - pt[1][0] - rows;
                    colIncr = cols / 4;
                    colIncr = Math.max(1, colIncr);
                    rowIncr = rows / 4;
                    rowIncr = Math.max(1, rowIncr);
                }
                int n = pt[0][1] - pt[0][0] - cols;
                double sdevMin = Double.MAX_VALUE;
                for (int i = 0; i < n; i += colIncr) {
                    ptTest[0][0] = pt[0][0] + i;
                    ptTest[0][1] = pt[0][0] + i + cols;
                    for (int j = 0; j < m; j += rowIncr) {
                        if (nDim > 1) {
                            ptTest[1][0] = pt[1][0] + j;
                            ptTest[1][1] = pt[1][0] + j + rows;
                        }
                        double value = dataset.measureSDev(ptTest, dim, 0, 0);
                        if (value < sdevMin) {
                            sdevMin = value;
                        }
                    }
                }
                sdevField.setText(String.format("%.6f", sdevMin));
                sDev = sdevMin;
            } catch (IOException ioE) {

            }
        }
        return sDev;
    }

}
