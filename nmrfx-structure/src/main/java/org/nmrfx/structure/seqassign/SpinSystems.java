package org.nmrfx.structure.seqassign;

import org.nmrfx.chemistry.io.NMRStarWriter;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;
import org.nmrfx.star.Loop;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author brucejohnson
 */
public class SpinSystems {
   public enum ClusterModes {
        ALL,
        CORRECT,
        LONELY,
        MISSING,
        MISSING_PPM,
        EXTRA;
    }
    int spinSystemID = 1;
    RunAbout runAbout;
    private List<SpinSystem> systems = new ArrayList<>();
    Map<PeakList, Integer> peakMap = new HashMap<>();
    Map<PeakList, double[]> sums;

    public SpinSystems(RunAbout runAbout) {
        this.runAbout = runAbout;
    }

    public int getSize() {
        return systems.size();
    }

    public void add(SpinSystem spinSystem) {
        systems.add(spinSystem);
    }

    public void remove(SpinSystem spinSystem) {
        systems.remove(spinSystem);
    }

    public Optional<SpinSystem> find(int idNum) {
        return systems.stream().filter(s -> s.getId() == idNum).findFirst();
    }
    public SpinSystem get(int i) {
        return systems.get(i);
    }

    public SpinSystem firstSpinSystem() {
        return systems.get(0);
    }

    public SpinSystem lastSpinSystem() {
        int lastIndex = systems.size() - 1;
        return systems.get(lastIndex);
    }

    public SpinSystem nextSpinSystem(SpinSystem currentSpinSystem) {
        int index = systems.indexOf(currentSpinSystem);
        if (index >= 0) {
            index++;
            if (index >= systems.size()) {
                index = systems.size() - 1;
            }
        } else {
            index = 0;
        }
        return systems.get(index);
    }

    public SpinSystem previousSpinSystem(SpinSystem currentSpinSystem) {
        int index = systems.indexOf(currentSpinSystem);
        if (index >= 0) {
            index--;
            if (index < 0) {
                index = 0;
            }
        } else {
            index = 0;
        }
        return systems.get(index);
    }

    public SpinSystem get(SpinSystem spinSystem, int dir, int pIndex, int sIndex) {
        if (dir == -1) {
            if (!spinSystem.spinMatchP.isEmpty()) {
                if (pIndex >= spinSystem.spinMatchP.size()) {
                    pIndex = spinSystem.spinMatchP.size() - 1;
                }
                spinSystem = spinSystem.spinMatchP.get(pIndex).spinSystemA;
            } else {
                spinSystem = null;
            }
        } else if (dir == 1) {
            if (!spinSystem.spinMatchS.isEmpty()) {
                if (sIndex >= spinSystem.spinMatchS.size()) {
                    sIndex = spinSystem.spinMatchS.size() - 1;
                }
                spinSystem = spinSystem.spinMatchS.get(sIndex).spinSystemB;
            } else {
                spinSystem = null;
            }
        }

        return spinSystem;

    }

    public static int[] matchDims(PeakList peakListA, PeakList peakListB) {
        int nDimA = peakListA.getNDim();
        int nDimB = peakListB.getNDim();
        int[] aMatch = new int[nDimA];
        for (int i = 0; i < nDimA; i++) {
            aMatch[i] = -1;
            SpectralDim sDimA = peakListA.getSpectralDim(i);
            for (int j = 0; j < nDimB; j++) {
                SpectralDim sDimB = peakListB.getSpectralDim(j);
                if (sDimA.getPattern().equals(sDimB.getPattern())) {
                    aMatch[i] = j;
                }
            }
        }
        return aMatch;
    }

    public static double comparePeaks(Peak peakA, Peak peakB, int[] aMatch) {
        boolean ok = true;
        double sum = 0.0;
        for (int i = 0; i < aMatch.length; i++) {
            if (aMatch[i] != -1) {
                double tolA = peakA.getPeakList().getSpectralDim(i).getIdTol();
                Float valueA = peakA.peakDims[i].getChemShift();
                Float valueB = peakB.peakDims[aMatch[i]].getChemShift();
                if ((valueA != null) && (valueB != null)) {
                    double delta = Math.abs(valueA - valueB);
                    if (delta > 2.0 * tolA) {
                        ok = false;
                        break;
                    } else {
                        delta /= tolA;
                        sum += delta * delta;
                    }
                } else {
                    ok = false;
                }
            }
        }
        double result = 0.0;
        if (ok) {
            double dis = Math.sqrt(sum);
            result = Math.exp(-dis);
        }
        return result;
    }

    Map<PeakList, double[]> calcNormalization(List<PeakList> peakLists) {
        PeakList refList = peakLists.get(0);
        Map<PeakList, double[]> sumMap = new HashMap<>();
        int i = 0;
        for (Peak pkA : refList.peaks()) {
            int j = 0;
            for (PeakList peakListB : peakLists) {
                int[] aMatch = matchDims(refList, peakListB);
                if (peakListB != refList) {
                    double sumF = peakListB.peaks().stream().filter(pkB -> pkB.getStatus() >= 0).
                            mapToDouble(pkB -> comparePeaks(pkA, pkB, aMatch)).sum();
                    double[] sumArray = sumMap.get(peakListB);
                    if (sumArray == null) {
                        sumArray = new double[refList.size()];
                        sumMap.put(peakListB, sumArray);
                    }
                    sumArray[pkA.getIndex()] = sumF;
                    j++;
                }
            }
            i++;
        }
        return sumMap;
    }

    public void addPeak(SpinSystem spinSys, Peak pkB) {
        if ((sums == null) || (sums.get(pkB.getPeakList()).length != spinSys.rootPeak.getPeakList().size())) {
            sums = calcNormalization(runAbout.getPeakLists());
        }
        Peak rootPeak = spinSys.rootPeak;
        if (rootPeak != pkB) {
            PeakList peakListB = pkB.getPeakList();
            double[] sumArray = sums.get(peakListB);
            if (rootPeak.getPeakList() != peakListB) {
                int[] aMatch = matchDims(rootPeak.getPeakList(), peakListB);
                double f = comparePeaks(rootPeak, pkB, aMatch);
                if (f >= 0.0) {
                    double p = f / sumArray[rootPeak.getIndex()];
                    spinSys.addPeak(pkB, p);
                }

                for (int iDim = 0; iDim < aMatch.length; iDim++) {
                    if (aMatch[iDim] >= 0) {
                        PeakList.linkPeakDims(spinSys.getRootPeak().getPeakDim(iDim), pkB.getPeakDim(aMatch[iDim]));
                    }
                }
            }
        }
    }

    public static boolean[] getUseDims(PeakList refList, List<PeakList> peakLists) {
        boolean[] useDim = new boolean[refList.getNDim()];
        for (int i = 0; i < useDim.length; i++) {
            useDim[i] = true;
        }
        int nPeakTypes = 0;
        for (PeakList peakList : peakLists) {
            if (peakList != refList) {
                int[] aMatch = matchDims(refList, peakList);
                for (int i = 0; i < aMatch.length; i++) {
                    if (aMatch[i] == -1) {
                        useDim[i] = false;
                    }
                }
            }
        }
        return useDim;
    }

    public void assembleWithClustering(PeakList refList, List<PeakList> peakLists) {
        sums = calcNormalization(peakLists);
        PeakList.clusterOrigin = refList;
        for (PeakList peakList : peakLists) {
            peakList.unLinkPeaks();
        }
        boolean[] useDim = getUseDims(refList, peakLists);
        peakMap.clear();
        int j = 0;
        for (PeakList peakList : peakLists) {
            if (peakList != refList) {
                peakMap.put(peakList, j);
                j++;
            }
            peakList.clearSearchDims();
            int[] aMatch = matchDims(refList, peakList);
            for (int i = 0; i < aMatch.length; i++) {
                if (useDim[i] && (aMatch[i] != -1)) {
                    double tol = peakList.getSpectralDim(aMatch[i]).getIdTol();
                    peakList.addSearchDim(aMatch[i], tol);
                }
            }
        }

        PeakList.clusterPeaks(peakLists);
        int i = 0;
        for (Peak pkA : refList.peaks()) {
            SpinSystem spinSys = new SpinSystem(pkA, this);
            systems.add(spinSys);
            for (Peak pkB : PeakList.getLinks(pkA, 0)) {// fixme calculate correct dim
                if (pkA != pkB) {
                    PeakList peakListB = pkB.getPeakList();
                    double[] sumArray = sums.get(peakListB);
                    if (refList != peakListB) {
                        Integer jList = peakMap.get(peakListB);
                        if (jList == null) {
                            System.out.println("n peakListb " + peakListB);
                        } else {
                            int[] aMatch = matchDims(refList, peakListB);
                            double f = comparePeaks(pkA, pkB, aMatch);
                            if (f >= 0.0) {
                                double p = f / sumArray[pkA.getIndex()];
                                spinSys.addPeak(pkB, p);
                            }
                        }
                    }
                }
            }
            i++;
            int nPeaks = spinSys.peakMatches.size();
        }
    }

    public void assemble(List<PeakList> peakLists) {
        systems.clear();
        peakLists.forEach(peakListA -> {
            peakListA.unLinkPeaks();
        });
        peakLists.forEach(peakListA -> {
            // set status to 0 for all active (status >= 0) peaks
            peakListA.peaks().stream().filter(p -> p.getStatus() >= 0).forEach(p -> p.setStatus(0));
            int nDim = peakListA.getNDim();
            for (int i = 0; i < nDim; i++) {
                SpectralDim sDim = peakListA.getSpectralDim(i);
            }
        });

        int spinID = 0;
        peakLists.forEach(peakListA -> {
            peakListA.peaks().stream().filter(pkA -> pkA.getStatus() == 0).forEach(pkA -> {
                SpinSystem spinSys = new SpinSystem(pkA, this);
                systems.add(spinSys);
                pkA.setStatus(1);
                peakLists.stream().filter(peakListB -> peakListB != peakListA).forEach(peakListB -> {
                    int[] aMatch = matchDims(peakListA, peakListB);
                    double sumF = peakListB.peaks().stream().filter(pkB -> pkB.getStatus() >= 0).
                            mapToDouble(pkB -> comparePeaks(pkA, pkB, aMatch)).sum();
                    peakListB.peaks().stream().filter(pkB -> pkB.getStatus() == 0).
                            forEach(pkB -> {
                                double f = comparePeaks(pkA, pkB, aMatch);
                                if (f > 0.0) {
                                    double p = f / sumF;
                                    if (p > 0.0) {
                                        spinSys.addPeak(pkB, p);
                                        pkB.setStatus(1);
                                    }
                                }
                            });
                });
            });
        });
    }

    public List<SpinSystemMatch> compare(SpinSystem spinSystemA, boolean prevMode) {
        List<SpinSystemMatch> matches = new ArrayList<>();
        for (SpinSystem spinSysB : systems) {
            if (spinSystemA != spinSysB) {
                Optional<SpinSystemMatch> result = spinSystemA.compare(spinSysB, prevMode);
                result.ifPresent(matches::add);
            }
        }
        return matches;
    }

    public void compare() {
        for (SpinSystem spinSysA : systems) {
            spinSysA.compare();
        }
    }

    public void dump() {
        for (SpinSystem spinSys : systems) {
            System.out.println(spinSys.toString());
        }
    }

    public void calcCombinations() {
        for (SpinSystem spinSys : systems) {
            spinSys.calcCombinations(false);
        }
    }

    public void buildSpinSystems(List<PeakList> peakLists) {
        PeakList refList = peakLists.get(0);
        for (Peak pkA : refList.peaks()) {
            SpinSystem spinSys = new SpinSystem(pkA, this);
            systems.add(spinSys);
            spinSys.getLinkedPeaks();
            spinSys.updateSpinSystem();
        }
        compare();
    }

    public List<SpinSystem> getSystemsByType(ClusterModes clusterMode) {
        return systems.stream().filter(s -> {
                    int extraOrMissing = runAbout.getExtraOrMissing(s);
                    if (clusterMode == ClusterModes.CORRECT) {
                        return extraOrMissing == 0;
                    } else if (clusterMode == ClusterModes.EXTRA) {
                        return (extraOrMissing & 1) != 0;
                    } else if (clusterMode == ClusterModes.MISSING) {
                        return (extraOrMissing & 2) != 0;
                    } else if (clusterMode == ClusterModes.LONELY) {
                        return s.peakMatches.size() < 3;
                    } else if (clusterMode == ClusterModes.MISSING_PPM) {
                        return !runAbout.getHasAllAtoms(s);
                    } else {
                        return true;
                    }
                }
        ).collect(Collectors.toList());
    }

    public List<SpinSystem> getSortedSystems() {
        Set<SeqFragment> fragments = new HashSet<>();
        List<SpinSystem> unconnectedSystems = new ArrayList<>();
        for (SpinSystem spinSys : systems) {
            if (spinSys.fragment.isPresent()) {
                fragments.add(spinSys.fragment.get());
            } else {
                unconnectedSystems.add(spinSys);
            }
        }

        List<SpinSystem> uniqueSystems = fragments.stream().sorted((e1, e2)
                -> Integer.compare(e2.spinSystemMatches.size(),
                        e1.spinSystemMatches.size())).
                map(frag -> frag.spinSystemMatches.get(0).spinSystemA).
                collect(Collectors.toList());

        uniqueSystems.addAll(unconnectedSystems);
        return uniqueSystems;
    }

   public Optional<SpinSystem> findSpinSystem(Peak peak) {
        for (var spinSys:systems) {
            for (var peakMatch:spinSys.peakMatches) {
                if (peak == peakMatch.peak) {
                    return Optional.of(spinSys);
                }
            }
        }
        return Optional.empty();
    }

    void readSTARSaveFrame(Saveframe saveframe) throws ParseException {
        Loop systemLoop = saveframe.getLoop("_Spin_system");
        Loop peakLoop = saveframe.getLoop("_Spin_system_peaks");
        List<SpinSystem> nextSystems = new ArrayList<>();
        List<SpinSystem> previousSystems = new ArrayList<>();
        if ((systemLoop != null) && (peakLoop != null)) {
            List<Integer> idColumn = systemLoop.getColumnAsIntegerList("Spin_system_ID", -1);
            List<Integer> peakListIDColumn = systemLoop.getColumnAsIntegerList("Spectral_peak_list_ID", -1);
            List<Integer> peakIDColumn = systemLoop.getColumnAsIntegerList("Peak_ID", -1);
            List<Integer> previousIDColumn = systemLoop.getColumnAsIntegerList("Confirmed_previous_ID", -1);
            List<Integer> nextIDColumn = systemLoop.getColumnAsIntegerList("Confirmed_next_ID", -1);
            for (int i = 0; i < idColumn.size(); i++) {
                Optional<PeakList> peakListOpt = PeakList.get(peakListIDColumn.get(i));
                if (peakListOpt.isPresent()) {
                    PeakList peakList = peakListOpt.get();
                    Peak peak = peakList.getPeakByID(peakIDColumn.get(i));
                    SpinSystem spinSystem = new SpinSystem(peak, this);
                    systems.add(spinSystem);
                }
            }
            for (int i = 0; i < idColumn.size(); i++) {
                Optional<PeakList> peakListOpt = PeakList.get(peakListIDColumn.get(i));
                if (peakListOpt.isPresent()) {
                    Integer prev = previousIDColumn.get(i);
                    SpinSystem prevSystem = prev != -1 ? systems.get(prev) : null;
                    Integer next = nextIDColumn.get(i);
                    SpinSystem nextSystem = next != -1 ? systems.get(next) : null;
                    nextSystems.add(nextSystem);
                    previousSystems.add(prevSystem);
                }
            }


            idColumn = peakLoop.getColumnAsIntegerList("ID", -1);
            List<Integer> spinSystemIDColumn = peakLoop.getColumnAsIntegerList("Spin_system_ID", -1);
            peakListIDColumn = peakLoop.getColumnAsIntegerList("Spectral_peak_list_ID", -1);
            peakIDColumn = peakLoop.getColumnAsIntegerList("Peak_ID", -1);
            List<Double> matchScoreColumn = peakLoop.getColumnAsDoubleList("Match_score", 0.0);
            for (int i = 0; i < idColumn.size(); i++) {
                Optional<PeakList> peakListOpt = PeakList.get(peakListIDColumn.get(i));
                if (peakListOpt.isPresent()) {
                    PeakList peakList = peakListOpt.get();
                    Peak peak = peakList.getPeakByID(peakIDColumn.get(i));
                    SpinSystem spinSystem = systems.get(spinSystemIDColumn.get(i));
                    if (peak != spinSystem.rootPeak) {
                        double score = matchScoreColumn.get(i);
                        spinSystem.addPeak(peak, score);
                    }
                }
            }
            for (SpinSystem spinSystem : systems) {
                spinSystem.updateSpinSystem();
            }
            compare();

            for (int i = 0; i < systems.size(); i++) {
                SpinSystem system = systems.get(i);
                SpinSystem previousSystem = previousSystems.get(i);
                SpinSystem nextSystem = nextSystems.get(i);
                if (previousSystem != null) {
                    // find match that is to previous, confirm and add to a fragment
                    for (SpinSystemMatch match : system.getMatchToPrevious()) {
                        if (match.getSpinSystemA() == previousSystem) {
                            system.confirmP = Optional.of(match);
                            SeqFragment.join(match, false);
                        }
                    }
                }
                if (nextSystem != null) {
                    // find match that is to next, confirm and add to a fragment
                    for (SpinSystemMatch match : system.getMatchToNext()) {
                        if (match.getSpinSystemB() == nextSystem) {
                            system.confirmS = Optional.of(match);
                            SeqFragment.join(match, false);
                        }
                    }
                }
            }
        }

    }



    void writeSpinSystems(StringBuilder sBuilder) {
        NMRStarWriter.openLoop(sBuilder, "_Spin_system", SpinSystem.systemLoopTags);
        for (SpinSystem spinSystem : systems) {
            sBuilder.append(spinSystem.getSystemSTARString()).append("\n");
        }
        NMRStarWriter.endLoop(sBuilder);
    }

    void writeSpinSystemPeaks(StringBuilder sBuilder) {
        NMRStarWriter.openLoop(sBuilder, "_Spin_system_peaks", SpinSystem.peakLoopTags);
        int i = 1;
        for (SpinSystem spinSystem : systems) {
            i = spinSystem.getPeakSTARString(sBuilder, i);
        }
        NMRStarWriter.endLoop(sBuilder);
    }
}
