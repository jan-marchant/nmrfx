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
package org.nmrfx.processor.gui.controls;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.table.TableFilter;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.gui.ChartProcessor;
import org.nmrfx.processor.gui.ProcessorController;
import org.python.util.PythonInterpreter;

/**
 *
 * @author Bruce Johnson
 */
public class ScanTable {

    ProcessorController processorController;
    TableView<FileTableItem> tableView;
    TableFilter fileTableFilter;
    TableFilter.Builder builder = null;
    String scanDir = null;
    String scanOutputDir = null;
    PopOver popOver = new PopOver();

    public ScanTable(ProcessorController controller, TableView<FileTableItem> tableView) {
        this.processorController = controller;
        this.tableView = tableView;
    }

    void init() {
        ObservableList<FileTableItem> data = FXCollections.observableArrayList();
        tableView.itemsProperty().setValue(data);
        TableColumn<FileTableItem, String> fileColumn = new TableColumn<>("FileName");
        TableColumn<FileTableItem, String> seqColumn = new TableColumn<>("Sequence");
        TableColumn<FileTableItem, String> nDimColumn = new TableColumn<>("nDim");
        TableColumn<FileTableItem, Long> dateColumn = new TableColumn<>("Date");

        fileColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getFileName()));
        seqColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getSeqName()));
        nDimColumn.setCellValueFactory((e) -> new SimpleStringProperty(String.valueOf(e.getValue().getNDim())));
        dateColumn.setCellValueFactory(new PropertyValueFactory<FileTableItem, Long>("Date"));

        tableView.getColumns().addAll(fileColumn, seqColumn, nDimColumn, dateColumn);
//        TableFilter.Builder builder = TableFilter.forTableView(tableView);
//        fileTableFilter = builder.apply();
        setDragHandlers(tableView);

    }

    final protected void setDragHandlers(Node mouseNode) {

        mouseNode.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                mouseDragOver(event);
            }
        }
        );
        mouseNode.setOnDragDropped(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                mouseDragDropped(event);
            }
        }
        );
        mouseNode.setOnDragExited(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                mouseNode.setStyle("-fx-border-color: #C6C6C6;");
            }
        }
        );
    }

    private void mouseDragDropped(final DragEvent e) {
        final Dragboard db = e.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            success = true;

            // Only get the first file from the list
            final File file = db.getFiles().get(0);
            if (file.isDirectory()) {
                scanDir = file.getAbsolutePath();
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        int beginIndex = scanDir.length() + 1;
                        ArrayList<String> nmrFiles = NMRDataUtil.findNMRDirectories(scanDir);
                        String[] headers = {};
                        updateTable(headers);
                        loadScanFiles(nmrFiles, beginIndex);
                    }
                });
            } else {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        loadScanTable(file);
                    }
                });

            }
        }
        e.setDropCompleted(success);
        e.consume();
    }

    private void mouseDragOver(final DragEvent e) {
        final Dragboard db = e.getDragboard();

        List<File> files = db.getFiles();
        if (db.hasFiles()) {
            if (files.size() > 0) {
                boolean isAccepted = false;
                if (files.get(0).isDirectory()) {
                    isAccepted = true;
                } else if (files.get(0).toString().endsWith(".txt")) {
                    isAccepted = true;
                }
                if (isAccepted) {
                    tableView.setStyle("-fx-border-color: green;"
                            + "-fx-border-width: 1;");
                    e.acceptTransferModes(TransferMode.COPY);
                }
            }
        } else {
            e.consume();
        }
    }

    public void loadScanFiles(Stage stage) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Scan Directory");
        File selectedDir = directoryChooser.showDialog(stage);
        if (selectedDir != null) {
            scanDir = selectedDir.getPath();
            int beginIndex = scanDir.length() + 1;
            ArrayList<String> nmrFiles = NMRDataUtil.findNMRDirectories(scanDir);
            String[] headers = {};
            updateTable(headers);
            loadScanFiles(nmrFiles, beginIndex);
        }
    }

    public void processScanDir(Stage stage, ChartProcessor chartProcessor, boolean combineFileMode) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Scan Output Directory");
        File selectedDir = directoryChooser.showDialog(stage);
        if (selectedDir != null) {
            scanOutputDir = selectedDir.getPath();
            ObservableList<FileTableItem> fileTableItems = tableView.getItems();
            ArrayList<String> fileNames = new ArrayList<>();
            for (FileTableItem fileTableItem : fileTableItems) {
                fileNames.add(fileTableItem.getFileName());
            }
            String script = chartProcessor.buildMultiScript(scanDir, scanOutputDir, fileNames, combineFileMode);
            System.out.println(script);
            PythonInterpreter processInterp = new PythonInterpreter();
            processInterp.exec("from pyproc import *");
            processInterp.exec("useProcessor()");
            processInterp.exec(script);
            processorController.viewDatasetInApp();
        }
    }

    public void openSelectedListFile(String scriptString) {
        int selItem = tableView.getSelectionModel().getSelectedIndex();
        if (selItem >= 0) {
            FileTableItem fileTableItem = (FileTableItem) tableView.getItems().get(selItem);
            String fileName = fileTableItem.getFileName();
            String filePath = Paths.get(scanDir, fileName).toString();

            processorController.getChart().getFXMLController().openFile(filePath, false, false);
            processorController.parseScript(scriptString);
        }
    }

    private void loadScanFiles(ArrayList<String> nmrFiles, int beginIndex) {
        fileTableFilter.getBackingList().clear();
        ObservableList<FileTableItem> fileListItems = FXCollections.observableArrayList();
        long firstDate = Long.MAX_VALUE;
        for (String filePath : nmrFiles) {
            NMRData nmrData = null;
            try {
                nmrData = NMRDataUtil.getNMRData(filePath);
            } catch (IOException ioE) {

            }
            if (nmrData != null) {
                long date = nmrData.getDate();
                if (date < firstDate) {
                    firstDate = date;
                }
                fileListItems.add(new FileTableItem(filePath.substring(beginIndex), nmrData.getSequence(), nmrData.getNDim(), nmrData.getDate()));
            }
        }
        for (FileTableItem item : fileListItems) {
            item.setDate(item.getDate() - firstDate);
        }
        fileTableFilter.getBackingList().addAll(fileListItems);
    }

    public void loadScanTable() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(("Open Table File"));
        File file = fileChooser.showOpenDialog(popOver);
        loadScanTable(file);
    }

    private void loadScanTable(File file) {
        ObservableList<FileTableItem> fileListItems = FXCollections.observableArrayList();
        scanDir = file.getParent();
        long firstDate = Long.MAX_VALUE;
        int iLine = 0;
        String[] headers = null;
        HashMap<String, String> fieldMap = new HashMap();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (iLine == 0) {
                    headers = line.split("\t");
                    updateTable(headers);
                } else {
                    String[] fields = line.split("\t");
                    for (int iField = 0; iField < fields.length; iField++) {
                        fieldMap.put(headers[iField], fields[iField]);
                    }
                    String fileName = fieldMap.get("FileName");
                    if (fileName == null) {
                        System.out.println("No FileName");
                        return;
                    }
                    fieldMap.remove("fid");
                    Path filePath = FileSystems.getDefault().getPath(scanDir, fileName);
                    NMRData nmrData = null;
                    try {
                        nmrData = NMRDataUtil.getNMRData(filePath.toString());
                    } catch (IOException ioE) {

                    }
                    if (nmrData != null) {
                        long date = nmrData.getDate();
                        if (date < firstDate) {
                            firstDate = date;
                        }
                        fileListItems.add(new FileTableItem(fileName, nmrData.getSequence(), nmrData.getNDim(), nmrData.getDate(), fieldMap));
                    }
                }

                iLine++;
            }
        } catch (IOException ioE) {

        }
        for (FileTableItem item : fileListItems) {
            item.setDate(item.getDate() - firstDate);
        }
        fileTableFilter.getBackingList().addAll(fileListItems);

    }

    private void addTableColumn(String name) {

    }

    private void updateTable(String[] headers) {
        ObservableList<FileTableItem> data = FXCollections.observableArrayList();
        tableView.itemsProperty().setValue(data);

        TableColumn<FileTableItem, String> fileColumn = new TableColumn<>("FileName");
        TableColumn<FileTableItem, String> seqColumn = new TableColumn<>("Sequence");
        TableColumn<FileTableItem, String> nDimColumn = new TableColumn<>("nDim");
        TableColumn<FileTableItem, Long> dateColumn = new TableColumn<>("Date");

        fileColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getFileName()));
        seqColumn.setCellValueFactory((e) -> new SimpleStringProperty(e.getValue().getSeqName()));
        nDimColumn.setCellValueFactory((e) -> new SimpleStringProperty(String.valueOf(e.getValue().getNDim())));
        dateColumn.setCellValueFactory(new PropertyValueFactory<FileTableItem, Long>("Date"));
        tableView.getColumns().clear();
        tableView.getColumns().addAll(fileColumn, seqColumn, nDimColumn, dateColumn);
        for (String header : headers) {
            if (header.equalsIgnoreCase("FileName") || header.equalsIgnoreCase("Sequence") || header.equalsIgnoreCase("nDim") || header.equalsIgnoreCase("Date") || header.equalsIgnoreCase("fid")) {
                continue;
            }
            TableColumn<FileTableItem, String> extraColumn = new TableColumn<>(header);
            extraColumn.setCellValueFactory((e) -> new SimpleStringProperty(String.valueOf(e.getValue().getExtra(header))));
            tableView.getColumns().add(extraColumn);
        }
        builder = TableFilter.forTableView(tableView);
        fileTableFilter = builder.apply();
        fileTableFilter.resetFilter();
    }

}
