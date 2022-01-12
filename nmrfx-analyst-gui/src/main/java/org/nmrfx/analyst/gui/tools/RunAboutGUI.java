package org.nmrfx.analyst.gui.tools;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import org.controlsfx.control.CheckListView;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.io.AtomParser;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.CanvasAnnotation;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.PeakNavigable;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.annotations.AnnoLine;
import org.nmrfx.processor.gui.annotations.AnnoText;
import org.nmrfx.processor.gui.project.GUIProject;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters;
import org.nmrfx.processor.gui.spectra.PeakListAttributes;
import org.nmrfx.processor.gui.utils.ToolBarUtils;
import org.nmrfx.processor.project.Project;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.seqassign.*;
import org.nmrfx.structure.seqassign.FragmentScoring.AAScore;
import org.nmrfx.structure.seqassign.RunAbout.TypeInfo;
import org.nmrfx.structure.seqassign.SpinSystem.AtomPresent;
import org.nmrfx.structure.seqassign.SpinSystem.PeakMatch;
import org.nmrfx.utils.GUIUtils;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.nmrfx.processor.gui.spectra.DatasetAttributes.AXMODE.PPM;



/**
 * @author Bruce Johnson
 */
public class RunAboutGUI implements PeakListener {

    FXMLController controller;
    VBox vBox;
    ToolBar navigatorToolBar;
    TextField peakIdField;
    MenuButton peakListMenuButton;
    MenuButton arrangeMenuButton;
    ToggleButton deleteButton;
    PeakNavigable peakNavigable;
    PeakList refPeakList;
    Peak currentPeak;
    static Background deleteBackground = new Background(new BackgroundFill(Color.RED, CornerRadii.EMPTY, Insets.EMPTY));
    Background defaultBackground = null;
    List<Peak> matchPeaks = new ArrayList<>();
    int matchIndex = 0;
    Consumer<RunAboutGUI> closeAction = null;
    boolean showAtoms = false;
    Label atomXFieldLabel;
    Label atomYFieldLabel;
    Label intensityFieldLabel;
    Label atomXLabel;
    Label atomYLabel;
    Label intensityLabel;
    RunAbout runAbout = new RunAbout();
    int currentSpinSystem = -1;
    SpinStatus spinStatus;
    ClusterStatus clusterStatus;
    SeqPane seqPane;
    Group drawingGroup;
    ClusterPane clusterPane;
    Group clusterGroup;
    Map<String, ResidueLabel> residueLabelMap = new HashMap<>();
    Map<Integer, String> chartTypes = new HashMap<>();
    RunAboutArrangements runAboutArrangements;
    CheckListView<PeakList> peakListCheckView;
    boolean useSpinSystem = false;
    Double[][] widths;
    int[] resOffsets = null;
    List<List<String>> winPatterns = new ArrayList<>();
    boolean[] intraResidue = null;
    int minOffset = 0;

    private RunAboutGUI(PeakNavigable peakNavigable) {
        this.peakNavigable = peakNavigable;
    }

    public static RunAboutGUI create() {
        FXMLController controller = FXMLController.create();
        TabPane tabPane = new TabPane();
        tabPane.setSide(Side.LEFT);
        RunAboutGUI runAbout = new RunAboutGUI(controller);
        runAbout.controller = controller;
        runAbout.initialize(tabPane);
        return runAbout;
    }

    public ToolBar getToolBar() {
        return navigatorToolBar;
    }

    public void close() {
        closeAction.accept(this);
    }

    class SeqPane extends Pane {

        double resWidth = 15.0;

        int getNRows() {
            int totalRows = 0;
            Molecule mol = Molecule.getActive();
            for (Polymer polymer : mol.getPolymers()) {
                if (polymer.isPeptide()) {
                    int nRes = polymer.getResidues().size();
                    double width = (resWidth * (10 + 1)) * nRes / 10.0;
                    int rows = (int) Math.ceil(width / vBox.getWidth());
                    totalRows += rows;

                }
            }
            return totalRows;

        }

        @Override
        public void layoutChildren() {
            setWidth(vBox.getWidth());
            int rows = getNRows();
            setHeight(rows * resWidth);
            super.layoutChildren();
            updateSeqCanvas();
        }

    }

    class ClusterPane extends Pane {

        double resWidth = 12.0;

        int getNRows() {
            int nSys = runAbout.getSpinSystems().getSize();
            int rows = 0;
            if (nSys > 0) {
                double width = (resWidth * (10 + 1)) * nSys / 10.0;
                rows = (int) Math.ceil(width / vBox.getWidth());
            }
            return rows;

        }

        @Override
        public void layoutChildren() {
            setWidth(vBox.getWidth());
            int rows = getNRows();
            setHeight(rows * resWidth);
            super.layoutChildren();
            updateClusterCanvas();
        }

    }

    void initialize(TabPane tabPane) {
        Tab helmTab = new Tab("Helm");
        helmTab.setClosable(false);
        tabPane.getTabs().add(helmTab);
        VBox vBox = new VBox();
        helmTab.setContent(vBox);
        initHelm(vBox);

        Tab prefTab = new Tab("Prefs");
        prefTab.setClosable(false);
        tabPane.getTabs().add(prefTab);
        HBox hBox = new HBox();
        prefTab.setContent(hBox);
        initPreferences(hBox);

        controller.getBottomBox().getChildren().add(tabPane);

        try {
            runAboutArrangements = RunAboutYamlReader.loadYaml();
            setupArrangements();
        } catch (IOException e) {
            GUIUtils.warn("RunAbout:load yaml", e.getMessage());
        }
    }

    void initPreferences(HBox hBox) {
        GUIProject.getActive().getPeakLists();
        ObservableList<PeakList> peakLists = FXCollections.observableArrayList(new ArrayList<>(PeakList.peakLists()));
        VBox listsBox = new VBox();
        VBox.setVgrow(listsBox,Priority.ALWAYS);
        CheckBox allBox = new CheckBox("PeakLists");
        peakListCheckView = new CheckListView<>(peakLists);
        peakListCheckView.setMinHeight(100);
        peakListCheckView.setPrefHeight(100);
        VBox.setVgrow(peakListCheckView,Priority.ALWAYS);
        listsBox.getChildren().addAll(allBox, peakListCheckView);
        hBox.getChildren().add(listsBox);
        allBox.setOnAction(e -> {
            boolean allOn = allBox.isSelected();
            for (var item : peakListCheckView.getItems()) {
                if (!allOn) {
                    peakListCheckView.getCheckModel().clearCheck(item);
                } else {
                    peakListCheckView.getCheckModel().check(item);
                }
            }
        });
        peakListCheckView.getCheckModel().getCheckedItems().addListener((ListChangeListener) e ->
                allBox.setSelected(peakListCheckView.getCheckModel().getCheckedItems().size() == peakListCheckView.getItems().size()));
    }

    void initHelm(VBox vBox) {
        this.vBox = vBox;

        ToolBar navBar = new ToolBar();
        vBox.getChildren().add(navBar);
        spinStatus = new SpinStatus();
        vBox.getChildren().add(spinStatus.build());

        seqPane = new SeqPane();
        drawingGroup = new Group();
        seqPane.getChildren().add(drawingGroup);
        seqPane.setMinHeight(60.0);
        seqPane.setMinWidth(500.0);

        clusterPane = new ClusterPane();
        clusterGroup = new Group();
        clusterPane.getChildren().add(clusterGroup);
        clusterPane.setMinHeight(60.0);
        clusterPane.setMinHeight(60.0);
        clusterPane.setMinWidth(500.0);

        clusterStatus = new ClusterStatus();

        vBox.getChildren().add(clusterStatus.build());
        vBox.getChildren().add(clusterPane);
        vBox.getChildren().add(seqPane);
        initPeakNavigator(navBar);
    }

    void initPeakNavigator(ToolBar toolBar) {
        this.navigatorToolBar = toolBar;
        peakIdField = new TextField();
        peakIdField.setMinWidth(75);
        peakIdField.setMaxWidth(75);
        RunAboutGUI navigator = this;

        String iconSize = "12px";
        String fontSize = "7pt";
        ArrayList<Button> buttons = new ArrayList<>();
        Button bButton;
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.MINUS_CIRCLE, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        closeButton.setOnAction(e -> close());

        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(navigator::firstPeak);
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.BACKWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(navigator::previousPeak);
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(navigator::nextPeak);
        buttons.add(bButton);
        bButton = GlyphsDude.createIconButton(FontAwesomeIcon.FAST_FORWARD, "", iconSize, fontSize, ContentDisplay.GRAPHIC_ONLY);
        bButton.setOnAction(navigator::lastPeak);
        buttons.add(bButton);
        deleteButton = GlyphsDude.createIconToggleButton(FontAwesomeIcon.BAN, fontSize, iconSize, ContentDisplay.GRAPHIC_ONLY);
        // prevent accidental activation when inspector gets focus after hitting space bar on peak in spectrum
        // a second space bar hit would activate
        deleteButton.setOnKeyPressed(Event::consume);
        deleteButton.setOnAction(e -> delete());

        if (closeAction != null) {
            navigatorToolBar.getItems().add(closeButton);
        }
        peakListMenuButton = new MenuButton("List");
        navigatorToolBar.getItems().add(peakListMenuButton);
        updatePeakListMenu();

        arrangeMenuButton = new MenuButton("Arrangements");
        navigatorToolBar.getItems().add(arrangeMenuButton);

        MenuButton actionMenuButton = new MenuButton("Actions");
        navigatorToolBar.getItems().add(actionMenuButton);
        ToolBarUtils.addFiller(navigatorToolBar, 40, 300);

        MenuItem setupItem = new MenuItem("Setup");
        setupItem.setOnAction(e -> setupRunAbout());

        actionMenuButton.getItems().add(setupItem);

        MenuItem filterItem = new MenuItem("Filter");
        filterItem.setOnAction(e -> runAbout.filterPeaks());
        actionMenuButton.getItems().add(filterItem);

        MenuItem assembleItem = new MenuItem("Assemble");
        assembleItem.setOnAction(e -> {
            assemble();
            clusterStatus.refresh();
        });
        actionMenuButton.getItems().add(assembleItem);
        MenuItem calcCombItem = new MenuItem("Combinations");
        calcCombItem.setOnAction(e -> runAbout.calcCombinations());
        actionMenuButton.getItems().add(calcCombItem);
        MenuItem compareItem = new MenuItem("Compare");
        compareItem.setOnAction(e -> runAbout.compare());
        actionMenuButton.getItems().add(compareItem);

        toolBar.getItems().addAll(buttons);
        toolBar.getItems().add(peakIdField);
        toolBar.getItems().add(deleteButton);
        Button analyzeButton = new Button("Analyze");
        analyzeButton.setOnAction(e -> analyzeSystem());
        toolBar.getItems().add(analyzeButton);
        ToolBarUtils.addFiller(navigatorToolBar, 40, 300);
        ToolBarUtils.addFiller(navigatorToolBar, 70, 70);

        if (showAtoms) {
            atomXFieldLabel = new Label("X:");
            atomYFieldLabel = new Label("Y:");
            intensityFieldLabel = new Label("I:");
            atomXLabel = new Label();
            atomXLabel.setMinWidth(75);
            atomYLabel = new Label();
            atomYLabel.setMinWidth(75);
            intensityLabel = new Label();
            intensityLabel.setMinWidth(75);

            Pane filler2 = new Pane();
            filler2.setMinWidth(20);
            Pane filler3 = new Pane();
            filler3.setMinWidth(20);
            Pane filler4 = new Pane();
            filler4.setMinWidth(20);
            Pane filler5 = new Pane();
            HBox.setHgrow(filler5, Priority.ALWAYS);

            toolBar.getItems().addAll(filler2, atomXFieldLabel, atomXLabel, filler3, atomYFieldLabel, atomYLabel, filler4, intensityFieldLabel, intensityLabel, filler5);

        }

        peakIdField.setOnKeyReleased(kE -> {
            if (null != kE.getCode()) {
                switch (kE.getCode()) {
                    case ENTER:
                        navigator.gotoPeakId(peakIdField);
                        break;
                    case UP:
                        navigator.gotoNextMatch(1);
                        break;
                    case DOWN:
                        navigator.gotoNextMatch(-1);
                        break;
                    default:
                        break;
                }
            }
        });
        MapChangeListener<String, PeakList> mapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> updatePeakListMenu();

        Project.getActive().addPeakListListener(mapChangeListener);
    }

    class ClusterStatus {

        HBox hBox;
        GridPane pane1;
        Pane leftPane;
        Group leftGroup;
        Pane rightPane;
        Group rightGroup;
        Map<String, Label> labelMap = new HashMap<>();
        List<ResidueLabel> leftResidues = new ArrayList<>();
        List<ResidueLabel> rightResidues = new ArrayList<>();

        HBox build() {
            hBox = new HBox();
            pane1 = new GridPane();
            leftPane = new Pane();
            rightPane = new Pane();
            leftPane.setMinWidth(220);
            rightPane.setMinWidth(220);
            leftPane.setPrefHeight(40);
            rightPane.setPrefWidth(40);
            leftGroup = new Group();
            rightGroup = new Group();
            leftPane.getChildren().add(leftGroup);
            rightPane.getChildren().add(rightGroup);
            Pane filler0 = new Pane();
            HBox.setHgrow(filler0, Priority.ALWAYS);
            Pane filler1 = new Pane();
            HBox.setHgrow(filler1, Priority.ALWAYS);
            Pane filler2 = new Pane();
            HBox.setHgrow(filler2, Priority.ALWAYS);
            Pane filler3 = new Pane();
            HBox.setHgrow(filler3, Priority.ALWAYS);
            hBox.getChildren().addAll(filler0, leftPane, filler1, pane1, filler2, rightPane, filler3);
            return hBox;
        }

        void refresh() {
            pane1.getChildren().clear();
            labelMap.clear();
            int nTypes = SpinSystem.getNAtomTypes();
            int col = 0;
            for (int k = 0; k < 2; k++) {
                for (int i = 0; i < nTypes; i++) {
                    int j = k == 1 ? i : nTypes - i - 1;
                    int n = SpinSystem.getNPeaksForType(k, j);
                    if (n != 0) {
                        String aName = SpinSystem.getAtomName(j);
                        if (k == 0) {
                            aName = aName.toLowerCase();
                        } else {
                            aName = aName.toUpperCase();
                        }
                        Label label = new Label(aName);
                        label.setMinWidth(45.0);
                        pane1.add(label, col, 0);
                        Label labelPPM = new Label(String.valueOf(n));
                        labelPPM.setMinWidth(45.0);
                        labelMap.put(aName, labelPPM);
                        pane1.add(labelPPM, col, 1);
                        col++;
                    }
                }
            }
            double x = 10.0;
            double y = 15.0;
            int i = 0;
            for (String aaName : AtomParser.getAANames()) {
                String aaChar = AtomParser.convert3To1(aaName);
                ResidueLabel leftLabel = new ResidueLabel(drawingGroup, aaChar.charAt(0));
                ResidueLabel rightLabel = new ResidueLabel(drawingGroup, aaChar.charAt(0));
                leftResidues.add(leftLabel);
                rightResidues.add(rightLabel);
                leftGroup.getChildren().add(leftLabel);
                rightGroup.getChildren().add(rightLabel);
                leftLabel.place(x, y);
                rightLabel.place(x, y);
                x += leftLabel.width;
                i++;
                if (i >= 10) {
                    i = 0;
                    x = 10.0;
                    y = y + 20.0;
                }
            }

        }

        void clearLabels() {
            for (Label label : labelMap.values()) {
                label.setText("");
                Tooltip toolTip = label.getTooltip();
                if (toolTip == null) {
                    toolTip = new Tooltip();
                    label.setTooltip(toolTip);
                    toolTip.setText("");
                }
            }
        }

        void setLabel(String aName, double value, double range, int nValues) {
            Label label = labelMap.get(aName);
            if (!Double.isNaN(value)) {
                label.setText(String.format("%.1f", value));
                Tooltip toolTip = label.getTooltip();
                if (toolTip == null) {
                    toolTip = new Tooltip();
                    label.setTooltip(toolTip);
                }
                toolTip.setText(String.format("%.1f : %2d", range, nValues));
            }
        }

        void setLabels() {
            clearLabels();
            SpinSystem spinSystem = runAbout.getSpinSystems().get(currentSpinSystem);
            int nTypes = SpinSystem.getNAtomTypes();
            double[][] ppms = new double[2][2];
            for (int k = 0; k < 2; k++) {
                for (int i = 0; i < nTypes; i++) {
                    int n = SpinSystem.getNPeaksForType(k, i);
                    if (n != 0) {
                        String aName = SpinSystem.getAtomName(i);
                        if (k == 0) {
                            aName = aName.toLowerCase();
                        } else {
                            aName = aName.toUpperCase();
                        }
                        double value = spinSystem.getValue(k, i);
                        double range = spinSystem.getRange(k, i);
                        int nValues = spinSystem.getNValues(k, i);
                        if (!Double.isNaN(value)) {
                            if (aName.equalsIgnoreCase("ca")) {
                                ppms[k][0] = value;
                            } else if (aName.equalsIgnoreCase("cb")) {
                                ppms[k][1] = value;
                            }
                            setLabel(aName, value, range, nValues);
                        }
                    }
                }
                List<AAScore> scores = FragmentScoring.scoreAA(ppms[k]);
                int iScore = 0;
                for (AAScore aaScore : scores) {
                    String name = AtomParser.convert3To1(aaScore.getName());
                    ResidueLabel resLabel = k == 0 ? leftResidues.get(iScore) : rightResidues.get(iScore);
                    Color color = Color.WHITE;
                    if (aaScore.getNorm() < 1.0e-3) {
                        name = name.toLowerCase();
                        color = Color.LIGHTGRAY;
                    }
                    resLabel.setText(name);
                    resLabel.setColor(color);
                    iScore++;
                }
            }
        }
    }

    class SpinStatus {

        int nFields = 2;
        HBox hBox;
        Spinner<Integer>[] spinners = new Spinner[2];
        Label[] scoreFields = new Label[nFields];
        Button[] sysFields = new Button[nFields];
        Label[] nMatchFields = new Label[nFields];
        Label[] recipLabels = new Label[nFields];
        Label[] availLabels = new Label[nFields];
        Label[] viableLabels = new Label[nFields];
        CheckBox[] selectedButtons = new CheckBox[nFields];

        List<SpinSystem> spinSystems;
        SpinSystem spinSys;

        void valueChanged(Spinner<Integer> spinner) {
            gotoSpinSystems(spinners[0].getValue(), spinners[1].getValue());
        }

        void activationChanged(CheckBox checkBox, Spinner<Integer> spinner, boolean prevState) {
            List<SpinSystemMatch> matches = prevState ? spinSys.getMatchToPrevious() : spinSys.getMatchToNext();
            int index = spinner.getValue();
            SpinSystemMatch spinMatch = matches.get(index);
            if (checkBox.isSelected()) {
                spinSys.confirm(spinMatch, prevState);
            } else {
                spinSys.unconfirm(spinMatch, prevState);
            }
            updateFragment(spinSys);
            updateClusterCanvas();
        }

        void updateFragment(SpinSystem spinSys) {
            for (ResidueLabel resLabel : residueLabelMap.values()) {
                resLabel.setColor(Color.WHITE);
            }
            Optional<SeqFragment> fragmentOpt = spinSys.getFragment();
            fragmentOpt.ifPresent(frag -> {
                Molecule molecule = Molecule.getActive();
                for (Polymer polymer : molecule.getPolymers()) {
                    List<ResidueSeqScore> resSeqScores = frag.scoreFragment(polymer);
                    for (ResidueSeqScore resSeqScore : resSeqScores) {
                        System.out.println(resSeqScore.getFirstResidue().getNumber() + " " + resSeqScore.getScore());
                        Residue residue = resSeqScore.getFirstResidue();
                        for (int iRes = 0; iRes < resSeqScore.getNResidues(); iRes++) {
                            String key = polymer.getName() + residue.getNumber();
                            ResidueLabel resLabel = residueLabelMap.get(key);
                            resLabel.setColor(Color.LIGHTGREEN);
                            residue = residue.getNext();
                        }
                    }
                }
            });
        }

        void gotoSystem(int index) {
            currentSpinSystem = Integer.parseInt(sysFields[index].getText());
            gotoSpinSystems();
        }

        HBox build() {
            hBox = new HBox();
            for (int i = 0; i < nFields; i++) {
                final int index = i;
                Pane pane1 = new Pane();
                HBox.setHgrow(pane1, Priority.ALWAYS);
                hBox.getChildren().add(pane1);
                Spinner<Integer> spinner = new Spinner<>(0, 0, 0);
                spinners[i] = spinner;
                spinners[i].valueProperty().addListener(e -> valueChanged(spinner));
                scoreFields[i] = new Label();
                sysFields[i] = new Button();
                sysFields[i].setOnAction(e -> gotoSystem(index));
                nMatchFields[i] = new Label();
                recipLabels[i] = new Label();
                recipLabels[i].setTextAlignment(TextAlignment.CENTER);
                recipLabels[i].setAlignment(Pos.CENTER);
                availLabels[i] = new Label();
                availLabels[i].setTextAlignment(TextAlignment.CENTER);
                availLabels[i].setAlignment(Pos.CENTER);
                viableLabels[i] = new Label();
                viableLabels[i].setTextAlignment(TextAlignment.CENTER);
                viableLabels[i].setAlignment(Pos.CENTER);
                scoreFields[i].setTextAlignment(TextAlignment.CENTER);
                nMatchFields[i].setTextAlignment(TextAlignment.CENTER);
                CheckBox checkBox = new CheckBox();
                selectedButtons[i] = checkBox;
                final boolean prevState = i == 0;
                selectedButtons[i].setOnAction(e -> activationChanged(checkBox, spinner, prevState));

                spinners[i].setMaxWidth(60);

                sysFields[i].setMaxWidth(50);
                scoreFields[i].setMaxWidth(50);
                nMatchFields[i].setMaxWidth(25);
                recipLabels[i].setMaxWidth(40);
                availLabels[i].setMaxWidth(20);
                viableLabels[i].setMaxWidth(20);

                sysFields[i].setMinWidth(50);
                scoreFields[i].setMinWidth(50);
                nMatchFields[i].setMinWidth(25);
                recipLabels[i].setMinWidth(40);
                availLabels[i].setMinWidth(20);
                viableLabels[i].setMinWidth(20);

                hBox.getChildren().add(spinners[i]);
                Label sysLabel = new Label(" Sys:");
                hBox.getChildren().addAll(sysLabel, sysFields[i]);
                Label scoreLabel = new Label(" Score:");
                hBox.getChildren().addAll(scoreLabel, scoreFields[i]);
                Label nLabel = new Label(" N:");
                hBox.getChildren().addAll(nLabel, nMatchFields[i]);
                Label recipLabel = new Label(" RAV:");
                hBox.getChildren().addAll(recipLabel, recipLabels[i], availLabels[i], viableLabels[i]);
                hBox.getChildren().add(selectedButtons[i]);

                Pane pane2 = new Pane();
                HBox.setHgrow(pane2, Priority.ALWAYS);
                hBox.getChildren().add(pane2);
            }

            return hBox;
        }

        void showScore(List<SpinSystem> spinSystems) {
            this.spinSystems = spinSystems;
            if (!spinSystems.isEmpty()) {
                spinSys = spinSystems.size() > 2 ? spinSystems.get(2) : spinSystems.get(0);
                for (int i = 0; i < resOffsets.length; i++) {
                    System.out.println(i + " " + resOffsets[i]);
                }
                for (int i = 0; i < nFields; i++) {
                    if (spinSys != null) {
                        List<SpinSystemMatch> matches = i == 0 ? spinSys.getMatchToPrevious() : spinSys.getMatchToNext();
                        SpinnerValueFactory.IntegerSpinnerValueFactory factory = (SpinnerValueFactory.IntegerSpinnerValueFactory) spinners[i].getValueFactory();
                        factory.setMax(matches.size() - 1);

                    }
                }
                for (int i = 0; i < nFields; i++) {
                    boolean ok = false;
                    if (spinSys != null) {
                        List<SpinSystemMatch> matches = i == 0 ? spinSys.getMatchToPrevious() : spinSys.getMatchToNext();
                        if (!matches.isEmpty()) {
                            selectedButtons[i].setDisable(false);
                            sysFields[i].setDisable(false);
                            spinners[i].setDisable(false);
                            SpinSystem otherSys;
                            SpinSystem matchSys;
                            int index = spinners[i].getValue();
                            System.out.println("i " + i + " index " + index);
                            SpinSystemMatch spinMatch = matches.get(index);

                            if (i == 0) {
                                otherSys = spinMatch.getSpinSystemA();
                                matchSys = otherSys.getMatchToNext().get(0).getSpinSystemB();
                            } else {
                                otherSys = spinMatch.getSpinSystemB();
                                matchSys = otherSys.getMatchToPrevious().get(0).getSpinSystemA();
                            }
                            boolean available = !otherSys.confirmed(i == 0);
                            boolean confirmed = spinSys.confirmed(spinMatch, i == 0);
                            selectedButtons[i].setSelected(confirmed);

                            boolean reciprocal = matchSys == spinSys;
                            if (reciprocal) {
                                recipLabels[i].setText("R");
                                recipLabels[i].setStyle("-fx-background-color:LIGHTGREEN");
                            } else {
                                String matchLabel = "";
                                if (matchSys != null) {
                                    matchLabel = String.valueOf(matchSys.getRootPeak().getIdNum());
                                }
                                recipLabels[i].setText(matchLabel);
                                recipLabels[i].setStyle("-fx-background-color:YELLOW");
                            }
                            if (available) {
                                availLabels[i].setText("A");
                                availLabels[i].setStyle("-fx-background-color:LIGHTGREEN");
                            } else {
                                availLabels[i].setText("a");
                                availLabels[i].setStyle("-fx-background-color:YELLOW");
                            }
                            viableLabels[i].setText("V");
                            viableLabels[i].setStyle("-fx-background-color:LIGHTGREEN");

                            sysFields[i].setText(String.valueOf(otherSys.getRootPeak().getIdNum()));
                            nMatchFields[i].setText(String.valueOf(spinMatch.getN()));
                            scoreFields[i].setText(String.format("%4.2f", spinMatch.getScore()));
                            ok = true;
                        } else {
                            sysFields[i].setText("");
                            sysFields[i].setDisable(true);
                            nMatchFields[i].setText("0");
                            scoreFields[i].setText("0.0");
                            selectedButtons[i].setSelected(false);
                            selectedButtons[i].setDisable(true);
                            spinners[i].setDisable(true);
                            recipLabels[i].setText("");
                            recipLabels[i].setStyle("");
                            availLabels[i].setText("");
                            availLabels[i].setStyle("");
                            viableLabels[i].setText("");
                            viableLabels[i].setStyle("");
                        }
                    }
                    if (!ok) {
                        sysFields[i].setText("");
                    }
//scoreField[i].setText(String.format("%4.2f", spinMatch.score));
                }
            }

        }
    }

    static class ResidueLabel extends StackPane {

        double fontSize = 14;
        double width = 20;
        Rectangle rect;
        Text textItem;

        ResidueLabel(Group group, Residue residue) {
            this(group, residue.getOneLetter());
        }

        ResidueLabel(Group group, char resChar) {
            this(group, String.valueOf(resChar));
        }

        ResidueLabel(Group group, String label) {
            super();
            StackPane stack = this;
            textItem = new Text(label);
            rect = new Rectangle(width, width, Color.WHITE);
            rect.setStroke(null);
            rect.setFill(Color.WHITE);
            textItem.setFill(Color.BLACK);
            textItem.setFont(Font.font(fontSize));
            textItem.setMouseTransparent(true);
            stack.getChildren().addAll(rect, textItem);
            stack.setAlignment(Pos.CENTER);
            group.getChildren().add(stack);
        }

        void place(double x, double y) {
            setTranslateX(x - width / 2 + 1);
            setTranslateY(y - width / 2 + 1);
        }

        void add(Group group) {
            group.getChildren().add(this);
        }

        void setColor(Color color) {
            rect.setFill(color);
        }

        void setText(String text) {
            textItem.setText(text);
        }
    }

    void gotoResidue(ResidueLabel resLabel) {

    }

    void gotoCluster(ResidueLabel resLabel) {
        currentSpinSystem = Integer.parseInt(resLabel.textItem.getText());
        gotoSpinSystems();

    }

    void updateCluster(ResidueLabel resLabel, int i, SpinSystem spinSys) {
        resLabel.setText(String.valueOf(spinSys.getRootPeak().getIdNum()));
        resLabel.setOnMouseClicked(e -> gotoCluster(resLabel));
        double width = 20.0;
        double paneWidth = clusterPane.getWidth();
        int nFit = (int) Math.floor(paneWidth / width) - 4;
        int iX = i % nFit;
        int jY = i / nFit;
        double x = 25.0 + iX * width;
        double y = 25.0 + jY * width;
        resLabel.place(x, y);

    }

    int countSpinSysItems(List<SpinSystem> sortedSystems) {
        int n = 0;
        for (SpinSystem spinSys : sortedSystems) {
            Optional<SeqFragment> fragmentOpt = spinSys.getFragment();
            if (fragmentOpt.isPresent()) {
                SeqFragment fragment = fragmentOpt.get();
                n += fragment.getSpinSystemMatches().size() + 1;
            } else {
                n++;
            }
        }
        return n;
    }

    void updateClusterCanvas() {
        List<SpinSystem> sortedSystems = runAbout.getSpinSystems().getSortedSystems();
        int n = countSpinSysItems(sortedSystems);
        if (clusterGroup.getChildren().size() != n) {
            clusterGroup.getChildren().clear();
            for (int i = 0; i < n; i++) {
                ResidueLabel resLabel = new ResidueLabel(clusterGroup, String.valueOf(i));
            }
        }
        List<Node> nodes = clusterGroup.getChildren();

        int i = 0;
        Color color = Color.YELLOW;
        for (SpinSystem spinSys : sortedSystems) {
            Optional<SeqFragment> fragmentOpt = spinSys.getFragment();
            if (fragmentOpt.isPresent()) {
                SeqFragment fragment = fragmentOpt.get();
                List<SpinSystemMatch> spinMatches = fragment.getSpinSystemMatches();
                ResidueLabel resLabel = (ResidueLabel) nodes.get(i);
                updateCluster(resLabel, i++, spinMatches.get(0).getSpinSystemA());
                resLabel.setColor(color);
                for (SpinSystemMatch spinMatch : spinMatches) {
                    resLabel = (ResidueLabel) nodes.get(i);
                    updateCluster(resLabel, i++, spinMatch.getSpinSystemB());
                    resLabel.setColor(color);
                }
                color = color == Color.YELLOW ? Color.ORANGE : Color.YELLOW;
            } else {
                color = Color.WHITE;
                ResidueLabel resLabel = (ResidueLabel) nodes.get(i);
                updateCluster(resLabel, i++, spinSys);
                resLabel.setColor(color);
            }
        }
    }

    void updateSeqCanvas() {
        Molecule molecule = Molecule.getActive();
        if (molecule != null) {
            double width = 20;
            double height = 20;
            if (drawingGroup.getChildren().isEmpty()) {
                for (Polymer polymer : molecule.getPolymers()) {
                    if (polymer.isPeptide()) {
                        for (Residue residue : polymer.getResidues()) {
                            ResidueLabel resLabel = new ResidueLabel(drawingGroup, residue);
                            String key = polymer.getName() + residue.getNumber();
                            residueLabelMap.put(key, resLabel);
                        }
                    }

                }
            }
            double y = 25.0;
            double x = 25.0;
            int i = 0;
            for (Node node : drawingGroup.getChildren()) {
                ResidueLabel resLabel = (ResidueLabel) node;
                resLabel.setOnMouseClicked(e -> gotoResidue(resLabel));
                if ((i % 10) == 0) {
                    x += width / 2.0;
                }
                resLabel.place(x, y);

                x += width;
                if (x > (seqPane.getWidth() - 2.0 * width)) {
                    x = 25.0;
                    y += height;
                }
                i++;
            }

        }
    }

    void assemble() {
        runAbout.assemble();
        updatePeakListMenu();
        useSpinSystem = true;
    }

    public void updatePeakListMenu() {
        peakListMenuButton.getItems().clear();
        if (runAbout.getSpinSystems().getSize() > 0) {
            MenuItem spinSysMenuItem = new MenuItem("spinsystems");
            spinSysMenuItem.setOnAction(e -> RunAboutGUI.this.useSpinSystem = true);
            peakListMenuButton.getItems().add(spinSysMenuItem);
        }

        for (String peakListName : Project.getActive().getPeakListNames()) {
            MenuItem menuItem = new MenuItem(peakListName);
            menuItem.setOnAction(e -> {
                RunAboutGUI.this.setPeakList(peakListName);
                RunAboutGUI.this.useSpinSystem = false;
            });
            peakListMenuButton.getItems().add(menuItem);
        }
    }

    public void setPeakList() {
        if (refPeakList == null) {
            PeakList testList = null;
            FXMLController controller = FXMLController.getActiveController();
            PolyChart chart = controller.getActiveChart();
            if (chart != null) {
                ObservableList<PeakListAttributes> attr = chart.getPeakListAttributes();
                if (!attr.isEmpty()) {
                    testList = attr.get(0).getPeakList();
                }
            }
            Optional<PeakList> firstListOpt = PeakList.getFirst();
            if (firstListOpt.isPresent()) {
                testList = firstListOpt.get();
            }
            setPeakList(testList);
        }
    }

    public void removePeakList() {
        if (refPeakList != null) {
            refPeakList.removeListener(this);
        }
        refPeakList = null;
        currentPeak = null;
    }

    public void setPeakList(String listName) {
        refPeakList = PeakList.get(listName);
        PeakList.clusterOrigin = refPeakList;
        RunAboutGUI.this.setPeakList(refPeakList);
    }

    public void setPeakList(PeakList newPeakList) {
        refPeakList = newPeakList;
        if (refPeakList != null) {
            currentPeak = refPeakList.getPeak(0);
            setPeakIdField();
            refPeakList.registerListener(this);
        }
        peakNavigable.refreshPeakView(currentPeak);
        peakNavigable.refreshPeakListView(refPeakList);
    }

    public Peak getPeak() {
        return currentPeak;
    }

    void analyzeSystem() {
        SpinSystem spinSys = runAbout.getSpinSystems().get(currentSpinSystem);
        spinSys.calcCombinations(true);
    }

    public void setSpinSystems(List<SpinSystem> spinSystems) {
        drawSpinSystems(spinSystems);
        setPeakIdField();
    }

    public void setPeaks(List<Peak> peaks) {
        if ((peaks == null) || peaks.isEmpty()) {
            currentPeak = null;
            updateAtomLabels(null);
        } else {
            int iPeak = peaks.size() > 1 ? 1 : 0;
            currentPeak = peaks.get(iPeak);
            if (peaks != null) {
                drawWins(peaks);
                updateDeleteStatus();
            }
            updateAtomLabels(peaks.get(iPeak));
        }
        setPeakIdField();
    }

    public void setPeak(Peak peak) {
        currentPeak = peak;
        if (peak != null) {
            drawWins(Collections.singletonList(peak));
            updateDeleteStatus();
        }
        updateAtomLabels(peak);
        setPeakIdField();
    }

    void updateAtomLabels(Peak peak) {
        if (showAtoms) {
            if (peak != null) {
                atomXLabel.setText(peak.getPeakDim(0).getLabel());
                intensityLabel.setText(String.format("%.2f", peak.getIntensity()));
                if (peak.getPeakDims().length > 1) {
                    atomYLabel.setText(peak.getPeakDim(1).getLabel());
                }
            } else {
                if (showAtoms) {
                    atomXLabel.setText("");
                    atomYLabel.setText("");
                    intensityLabel.setText("");
                }
            }
        }
    }

    void updateDeleteStatus() {
        if (defaultBackground == null) {
            defaultBackground = peakIdField.getBackground();
        }
        if (!useSpinSystem && (currentPeak == null)) {
            if (currentPeak.getStatus() < 0) {
                deleteButton.setSelected(true);
                peakIdField.setBackground(deleteBackground);
            } else {
                deleteButton.setSelected(false);
                peakIdField.setBackground(defaultBackground);
            }
        }
    }

    private void setPeakIdField() {
        if (useSpinSystem) {
            if (currentSpinSystem < 0) {
                peakIdField.setText("");
            } else {
                peakIdField.setText(String.valueOf(currentSpinSystem));
            }
        } else {
            if (currentPeak == null) {
                peakIdField.setText("");
            } else {
                peakIdField.setText(String.valueOf(currentPeak.getIdNum()));
            }

        }

    }

    public void firstPeak(ActionEvent event) {
        if (useSpinSystem) {
            firstSpinSystem();
        } else {
            firstPeak();
        }
    }

    public void previousPeak(ActionEvent event) {
        if (useSpinSystem) {
            previousSpinSystem();
        } else {
            previousPeak();
        }
    }

    public void nextPeak(ActionEvent event) {
        if (useSpinSystem) {
            nextSpinSystem();
        } else {
            nextPeak();
        }
    }

    public void lastPeak(ActionEvent event) {
        if (useSpinSystem) {
            lastSpinSystem();
        } else {
            lastPeak();
        }
    }

    public void firstSpinSystem() {
        currentSpinSystem = 0;
        gotoSpinSystems();

    }

    public void lastSpinSystem() {
        currentSpinSystem = runAbout.getSpinSystems().getSize() - 1;
        gotoSpinSystems();
    }

    public void nextSpinSystem() {
        if (currentSpinSystem >= 0) {
            currentSpinSystem++;
            if (currentSpinSystem >= runAbout.getSpinSystems().getSize()) {
                currentSpinSystem = runAbout.getSpinSystems().getSize() - 1;
            }
        } else {
            currentSpinSystem = 0;
        }
        gotoSpinSystems();
    }

    public void previousSpinSystem() {
        List<SpinSystem> spinSystems = new ArrayList<>();
        if (currentSpinSystem >= 0) {
            currentSpinSystem--;
            if (currentSpinSystem < 0) {
                currentSpinSystem = 0;
            }
        } else {
            currentSpinSystem = 0;
        }
        gotoSpinSystems();
    }

    public void gotoSpinSystems() {
        gotoSpinSystems(0, 0);
        clusterStatus.setLabels();
        SpinSystem spinSys = runAbout.getSpinSystems().get(currentSpinSystem);
        spinStatus.updateFragment(spinSys);

    }

    public void gotoSpinSystems(int pIndex, int sIndex) {
        List<SpinSystem> spinSystems = new ArrayList<>();
        for (int resOffset : resOffsets) {
            SpinSystem spinSystem = runAbout.getSpinSystems().get(currentSpinSystem, resOffset, pIndex, sIndex);
            spinSystems.add(spinSystem);
        }
        setSpinSystems(spinSystems);
    }

    void delete() {
        if (!useSpinSystem) {
            PeakList currList = currentPeak.getPeakList();
            int peakIndex = currentPeak.getIndex();
            currentPeak.setStatus(-1);
            currList.compress();
            currList.reNumber();
            if (peakIndex >= refPeakList.size()) {
                peakIndex = refPeakList.size() - 1;
            }
            setPeak(currList.getPeak(peakIndex));
        }
    }

    void firstPeak() {
        if (refPeakList != null) {
            Peak peak = refPeakList.getPeak(0);
            setPeak(peak);
        }
    }

    public void previousPeak() {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            peakIndex--;
            if (peakIndex < 0) {
                peakIndex = 0;
            }
            Peak peak = refPeakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public void nextPeak() {
        if (currentPeak != null) {
            int peakIndex = currentPeak.getIndex();
            peakIndex++;
            if (peakIndex >= refPeakList.size()) {
                peakIndex = refPeakList.size() - 1;
            }
            Peak peak = refPeakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public void lastPeak() {
        if (refPeakList != null) {
            int peakIndex = refPeakList.size() - 1;
            Peak peak = refPeakList.getPeak(peakIndex);
            setPeak(peak);
        }
    }

    public List<Peak> matchPeaks(String pattern) {
        List<Peak> result;
        if (pattern.startsWith("re")) {
            pattern = pattern.substring(2).trim();
            if (pattern.contains(":")) {
                String[] matchStrings = pattern.split(":");
                result = refPeakList.matchPeaks(matchStrings, true, true);
            } else {
                String[] matchStrings = pattern.split(",");
                result = refPeakList.matchPeaks(matchStrings, true, false);
            }
        } else {
            if (pattern.contains(":")) {
                if (pattern.charAt(0) == ':') {
                    pattern = " " + pattern;
                }
                if (pattern.charAt(pattern.length() - 1) == ':') {
                    pattern = pattern + " ";
                }

                String[] matchStrings = pattern.split(":");
                result = refPeakList.matchPeaks(matchStrings, false, true);
            } else {
                if (pattern.charAt(0) == ',') {
                    pattern = " " + pattern;
                }
                if (pattern.charAt(pattern.length() - 1) == ',') {
                    pattern = pattern + " ";
                }
                String[] matchStrings = pattern.split(",");
                result = refPeakList.matchPeaks(matchStrings, false, false);
            }
        }
        return result;
    }

    public void gotoPeakId(TextField idField) {
        String idString = idField.getText().trim();
        if (useSpinSystem) {
            try {
                currentSpinSystem = Integer.parseInt(idString);
                gotoSpinSystems();
            } catch (NumberFormatException nfE) {

            }
        } else {
            if (refPeakList != null) {
                matchPeaks.clear();
                int id = Integer.MIN_VALUE;
                if (idString.length() != 0) {
                    try {
                        id = Integer.parseInt(idString);
                    } catch (NumberFormatException nfE) {
                        List<Peak> peaks = matchPeaks(idString);
                        if (!peaks.isEmpty()) {
                            setPeak(peaks.get(0));
                            matchPeaks.addAll(peaks);
                            matchIndex = 0;
                        } else {
                            idField.setText("");
                        }
                    }
                    if (id != Integer.MIN_VALUE) {
                        if (id < 0) {
                            id = 0;
                        } else if (id >= refPeakList.size()) {
                            id = refPeakList.size() - 1;
                        }
                        Peak peak = refPeakList.getPeakByID(id);
                        setPeak(peak);
                    }
                }
            }
        }
    }

    void gotoNextMatch(int dir) {
        if (!matchPeaks.isEmpty()) {
            matchIndex += dir;
            if (matchIndex >= matchPeaks.size()) {
                matchIndex = 0;
            } else if (matchIndex < 0) {
                matchIndex = matchPeaks.size() - 1;
            }
            Peak peak = matchPeaks.get(matchIndex);
            setPeak(peak);
        }
    }

    void setDeleteStatus(ToggleButton button) {
        if (currentPeak != null) {
            if (button.isSelected()) {
                currentPeak.setStatus(-1);
            } else {
                currentPeak.setStatus(0);
            }
            peakNavigable.refreshPeakView(currentPeak);
        }
        updateDeleteStatus();
    }

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        if (peakEvent.getSource() instanceof PeakList) {
            PeakList sourceList = (PeakList) peakEvent.getSource();
            if (sourceList == refPeakList) {
                if (Platform.isFxApplicationThread()) {
                    peakNavigable.refreshPeakView();
                } else {
                    Platform.runLater(() -> {
                        peakNavigable.refreshPeakView();
                    });
                }
            }
        }
    }

    void setupArrangements() {
        runAboutArrangements.getArrangements().forEach((name, arrangment) -> {
            MenuItem item = new MenuItem(name);
            arrangeMenuButton.getItems().add(item);
            item.setOnAction(e -> genWin(name));
        });
    }

    List<List<String>> getAtomsFromPatterns(List<String> patElems) {
        List<List<String>> allAtomPats = new ArrayList<>();
        for (String patElem : patElems) {
            String pattern = patElem.trim();
            String[] resAtoms = pattern.split("\\.");
            String[] atomPats = resAtoms[1].split(",");
            List<String> atomPatList = new ArrayList<>();
            allAtomPats.add(atomPatList);
            for (String atomPat : atomPats) {
                if (atomPat.endsWith("-") || atomPat.endsWith("+")) {
                    int len = atomPat.length();
                    atomPat = atomPat.substring(0, len - 1);
                }
                atomPatList.add(atomPat.toUpperCase());
            }
        }
        return allAtomPats;
    }

    Optional<String> getDatasetName(Map<String, String> typeMap, String typeName) {
        Optional<String> dName = Optional.empty();
        if (typeMap.containsKey(typeName)) {
            dName = Optional.of(typeMap.get(typeName));
        }
        return dName;
    }

    Double[] getDimWidth(PeakList peakList, Dataset dataset, List<String> dimNames, int[] iDims, List<String> widthTypes) {
        Double[] widths = new Double[iDims.length];
        for (int i=0;i<dimNames.size();i++) {
            Double width;
            String dimName = dimNames.get(i);
            String widthType = widthTypes.get(i);
            int iDim = iDims[i];
            String dataDimName = dataset.getLabel(iDim);
            if (widthType.equals(("peak"))) {
                SpectralDim sDim = peakList.getSpectralDim(dataDimName);
                if (sDim != null) {
                    var widthStats = peakList.widthStatsPPM(sDim.getDataDim());
                    width = 10.0 * widthStats.getAverage();
                } else {
                    width = null;
                }
            } else if (widthType.equals(("plane"))) {
                width = 0.0;
            } else {
                width = null;
            }
            widths[i] = width;

        }
        return widths;
    }

    void setupRunAbout() {
        if (!runAbout.isActive() ||
                GUIUtils.affirm("Peak lists already setup, Setup again?")) {
            List<PeakList> peakLists = peakListCheckView.getCheckModel().getCheckedItems();
            if (peakLists.isEmpty()) {
                GUIUtils.warn("RunAbout", "No peak lists selected");
            } else {
                runAbout.setPeakLists(peakListCheckView.getCheckModel().getCheckedItems());
                for (var peakList:peakLists) {
                    peakList.registerListener(this);
                }
            }
        }
    }

    void genWin(String arrangeName) {
        if (runAbout.isActive()) {
            RunAboutArrangement arrangement = runAboutArrangements.getArrangements().get(arrangeName);
            List<String> rows = arrangement.getRows();
            List<RunAboutDim> cols = arrangement.getCols();
            int nCharts = rows.size() * cols.size();
            controller.setNCharts(nCharts);
            controller.arrange(rows.size());
            List<PolyChart> charts = controller.getCharts();
            widths = new Double[nCharts][];
            minOffset = 0;
            resOffsets = new int[cols.size()];
            intraResidue = new boolean[cols.size()];
            int iCol = 0;

            for (RunAboutDim col : cols) {
                String dir = col.getDir();
                char resChar = dir.charAt(0);
                int del = resChar - 'i';
                intraResidue[iCol] = !dir.endsWith("-1");
                resOffsets[iCol++] = del;
                minOffset = Math.min(del, minOffset);
            }
            int iChart = 0;
            winPatterns.clear();
            for (String row : rows) {
                for (RunAboutDim col : cols) {
                    PolyChart chart = charts.get(iChart);
                    chart.clearDataAndPeaks();
                    String dir = col.getDir();
                    List<String> widthTypes = col.getWidths();

                    Optional<PeakList> peakListOptional = runAbout.getPeakListForCell(row, dir);
                    final int jChart = iChart;
                    peakListOptional.ifPresent(peakList -> {

                        String datasetName = peakList.getDatasetName();
                        Dataset dataset = Dataset.getDataset(datasetName);
                        String typeName = peakList.getExperimentType();
                        chartTypes.put(jChart, typeName);
                        dataset.setTitle(typeName);
                        String dName = dataset.getName();
                        List<String> datasets = Collections.singletonList(dName);
                        chart.setActiveChart();
                        chart.updateDatasets(datasets);
                        List<String> dimNames = col.getDims();
                        DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                        int[] iDims = runAbout.getIDims(dataset, typeName, dimNames);
                        var sDims = runAbout.getPeakListDims(peakList, dataset, iDims);
                        widths[jChart] = getDimWidth(peakList, dataset, dimNames, iDims, widthTypes);
                        dataAttr.setDims(iDims);
                        List<String> peakLists = Collections.singletonList(peakList.getName());
                        chart.updatePeakLists(peakLists);
                        winPatterns.add(sDims.stream().map(SpectralDim::getPattern).collect(Collectors.toList()));
                    });
                    iChart++;
                }
            }
            controller.setChartDisable(false);

            if (currentPeak != null) {
                drawWins(Collections.singletonList(currentPeak));
            } else {
                firstPeak(null);
            }

        }
    }

    void drawWins(List<Peak> peaks) {
        List<PolyChart> charts = controller.getCharts();
        int iChart = 0;
        for (PolyChart chart : charts) {
            chart.chartProps.setTitles(true);
            if (!chart.getPeakListAttributes().isEmpty()) {
                PeakListAttributes peakAttr = chart.getPeakListAttributes().get(0);
                peakAttr.setLabelType(PeakDisplayParameters.LabelTypes.Number);
            }
            int iCol = iChart % resOffsets.length;
            int resOffset = resOffsets[iCol] - minOffset;
            resOffset = resOffset >= peaks.size() ? 0 : resOffset;
            iCol = iCol >= peaks.size() ? 0 : iCol;
            Peak peak = peaks.get(iCol);
            chart.clearAnnotations();
            if (peak != null && !chart.getDatasetAttributes().isEmpty()) {
                refreshChart(chart, iChart, peak);
            }
            iChart++;
        }
    }

    void drawSpinSystems(List<SpinSystem> spinSystems) {
        updateSeqCanvas();
        List<PolyChart> charts = controller.getCharts();
        int iChart = 0;
        Font font = Font.font(12.0);
        double textWidth = GUIUtils.getTextWidth("CB", font);

        spinStatus.showScore(spinSystems);

        for (PolyChart chart : charts) {
            chart.chartProps.setTopBorderSize(25);
            chart.chartProps.setTitles(true);
            int iCol = iChart % resOffsets.length;
            int resOffset = resOffsets[iCol] - minOffset;
            resOffset = resOffset >= spinSystems.size() ? 0 : resOffset;
            iCol = iCol >= spinSystems.size() ? 0 : iCol;
            SpinSystem spinSystem = spinSystems.get(iCol);
            if (spinSystem == null) {
                iChart++;
                continue;
            }
            if (iChart == 0) {
                spinSystem.dumpPeakMatches();
            }
            // spinSystem.dumpPeakMatches();
            Peak peak = spinSystem.getRootPeak();
            chart.clearAnnotations();
            List<List<String>> atomPatterns = getAtomsFromPatterns(winPatterns.get(iChart));
            if (peak != null && !chart.getDatasetAttributes().isEmpty()) {
                refreshChart(chart, iChart, peak);
                DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
                PeakList currentList = null;
                if (!chart.getPeakListAttributes().isEmpty()) {
                    PeakListAttributes peakAttr = chart.getPeakListAttributes().get(0);
                    peakAttr.setLabelType(PeakDisplayParameters.LabelTypes.Cluster);
                    currentList = peakAttr.getPeakList();
                }
                int nPeaks = spinSystem.getNPeaksWithList(currentList);
                for (PeakMatch peakMatch : spinSystem.peakMatches()) {
                    PeakDim peakDim = peakMatch.getPeak().getPeakDim(dataAttr.getLabel(1));
                    if (peakDim != null) {
                        int iDim = peakDim.getSpectralDim();
                        int atomIndex = peakMatch.getIndex(iDim);
                        String aName = SpinSystem.getAtomName(atomIndex).toUpperCase();
                        if (!atomPatterns.get(iDim).contains(aName)) {
                            continue;

                        }
                        boolean isIntra = peakMatch.getIntraResidue(iDim);
                        final double f1;
                        final double f2;
                        Color color;
                        if (intraResidue[iCol]) {
                            if (isIntra) {
                                color = Color.BLUE;
                                f1 = 0.5;
                                f2 = 1.0;
                            } else {
                                color = Color.GREEN;
                                f1 = 0.0;
                                f2 = 0.5;
                            }

                        } else {
                            if (isIntra) {
                                continue;
                            } else {
                                color = Color.GREEN;
                                f1 = 0.0;
                                f2 = 1.0;
                            }
                        }
                        if (isIntra && !intraResidue[iCol]) {
                            continue;
                        }
                        if (isIntra && intraResidue[iCol]) {

                        }

                        double ppm = spinSystem.getValue(isIntra ? 1 : 0, atomIndex);
                        AnnoLine annoLine = new AnnoLine(f1, ppm, f2, ppm, CanvasAnnotation.POSTYPE.FRACTION, CanvasAnnotation.POSTYPE.WORLD);
                        annoLine.setStroke(color);
                        chart.addAnnotation(annoLine);
                    }
                }
                String typeName = chartTypes.get(iChart);
                if (typeName != null) {
                    int nExpected = runAbout.getTypeCount(typeName);

                    TypeInfo typeInfo = runAbout.getTypeInfo(typeName);
                    int dim = currentList.getSpectralDim(dataAttr.getLabel(1)).getDataDim();
                    List<AtomPresent> typesPresent = spinSystem.getTypesPresent(typeInfo, currentList, dim);
                    double x = 100.0;
                    double delta = textWidth + 5.0;
                    for (AtomPresent typePresent : typesPresent) {
                        String text = typePresent.getName();
                        if (!typePresent.isIntraResidue()) {
                            text = text.toLowerCase();
                        }
                        AnnoText annoText = new AnnoText(x, -8, x + delta, -8, CanvasAnnotation.POSTYPE.PIXEL, CanvasAnnotation.POSTYPE.PIXEL, text);
                        annoText.setFont(font);
                        chart.addAnnotation(annoText);
                        Color presentColor = typePresent.isPresent() ? Color.LIGHTGREEN : Color.RED;
                        AnnoLine annoLine2 = new AnnoLine(x, -2, x + textWidth, -2, CanvasAnnotation.POSTYPE.PIXEL, CanvasAnnotation.POSTYPE.PIXEL);
                        annoLine2.setStroke(presentColor);
                        annoLine2.setLineWidth(6);
                        chart.addAnnotation(annoLine2);
                        x += delta;
                    }
                    AnnoText annoText = new AnnoText(x, -8, x + textWidth, -8, CanvasAnnotation.POSTYPE.PIXEL, CanvasAnnotation.POSTYPE.PIXEL, String.valueOf(nPeaks - nExpected));
                    annoText.setFont(font);
                    chart.addAnnotation(annoText);

                }
                chart.refresh();

            }
            iChart++;
        }
    }

    void refreshChart(PolyChart chart, int iChart, Peak peak) {
        DatasetAttributes dataAttr = chart.getDatasetAttributes().get(0);
        int cDim = chart.getNDim();
        int aDim = dataAttr.nDim;
        Double[] ppms = new Double[cDim];
        for (int i = 0; i < aDim; i++) {
            PeakDim peakDim = peak.getPeakDim(dataAttr.getLabel(i));
            if ((widths[iChart] != null) && (peakDim != null)) {
                ppms[i] = (double) peakDim.getChemShiftValue();
                if (widths[iChart][i] == null) {
                    chart.full(i);
                } else {
                    double pos;
                    if (chart.getAxMode(i) == PPM) {
                        pos = ppms[i];
                    } else {
                        int dDim = dataAttr.getDim(i);
                        pos = dataAttr.getDataset().ppmToDPoint(dDim, ppms[i]);
                    }
                    chart.moveTo(i, pos, widths[iChart][i]);
                }
                if (i == 0) {
                    AnnoLine annoLine = new AnnoLine(ppms[0], 0.0, ppms[0], 1.0, CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.
                            FRACTION);
                    annoLine.setStroke(Color.BLUE);
                    annoLine.setLineWidth(0.0);
                    chart.addAnnotation(annoLine);
                }
            } else {
                chart.full(i);
            }
        }
        chart.refresh();
    }
}
