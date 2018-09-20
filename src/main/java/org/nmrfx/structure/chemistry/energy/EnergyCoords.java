/*
 * NMRFx Structure : A Program for Calculating Structures 
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
package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.structure.chemistry.Atom;
import static org.nmrfx.structure.chemistry.energy.AtomMath.RADJ;
import org.nmrfx.structure.fastlinear.FastVector3D;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.math3.util.FastMath;
import org.nmrfx.structure.chemistry.Point3;

/**
 *
 * @author Bruce Johnson
 */
public class EnergyCoords {

    private static final int[][] offsets = {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}, {-1, 1, 0}, {0, 0, 1},
    {1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {-1, 1, 1}, {-1, 0, 1},
    {-1, -1, 1}, {0, -1, 1}, {1, -1, 1}
    };

    final static private int DEFAULTSIZE = 160000;
    FastVector3D[] vecCoords = null;
    int[] resNums = null;
    Atom[] atoms = null;
    int[] mAtoms = null;
    boolean[] swapped = null;
    int[] hBondable = null;
    boolean[] hasBondConstraint = null;
    double[] contactRadii = null;
    int[] cellIndex = null;
    int[] iGroups = new int[DEFAULTSIZE];
    int[] groupSizes = new int[DEFAULTSIZE];
    int[] iAtoms = new int[DEFAULTSIZE];
    int[] jAtoms = new int[DEFAULTSIZE];
    int[] iUnits = new int[DEFAULTSIZE];
    int[] jUnits = new int[DEFAULTSIZE];
    double[] disSq = new double[DEFAULTSIZE];
    double[] rLow2 = new double[DEFAULTSIZE];
    double[] rLow = new double[DEFAULTSIZE];
    double[] rUp2 = new double[DEFAULTSIZE];
    double[] rUp = new double[DEFAULTSIZE];
    double[] viol = new double[DEFAULTSIZE];
    double[] weights = new double[DEFAULTSIZE];

    double[] derivs = new double[DEFAULTSIZE];
    int repelStart = 20000;
    int repelEnd = repelStart;
    int disEnd = 0;
    int nAtoms = 0;
    boolean[][] fixed;
    Map<Integer, Set<Integer>> kSwap = null;

    private static double hbondDelta = 0.60;

    public FastVector3D[] getVecCoords(int size) {
        if ((vecCoords == null) || (vecCoords.length != size)) {
            vecCoords = new FastVector3D[size];
            resNums = new int[size];
            atoms = new Atom[size];
            mAtoms = new int[size];
            swapped = new boolean[size];
            contactRadii = new double[size];
            hasBondConstraint = new boolean[size];
            hBondable = new int[size];
            cellIndex = new int[size];
            for (int i = 0; i < size; i++) {
                vecCoords[i] = new FastVector3D();
            }
        }
        nAtoms = size;

        return vecCoords;
    }

    public FastVector3D[] getVecCoords() {
        return vecCoords;
    }

    public void setCoords(int i, double x, double y, double z, int resNum, Atom atomType) {
        vecCoords[i].set(x, y, z);
        resNums[i] = resNum;
        atoms[i] = atomType;
        atomType.eAtom = i;
    }

    public void clear() {
        repelEnd = repelStart;
    }

    public void clearDist() {
        disEnd = 0;
    }

    public int getNNOE() {
        return disEnd;
    }

    public int getNContacts() {
        return repelEnd - repelStart;
    }

    public void addPair(int i, int j, int iUnit, int jUnit, double r0) {
        iAtoms[repelEnd] = i;
        jAtoms[repelEnd] = j;
        iUnits[repelEnd] = iUnit;
        jUnits[repelEnd] = jUnit;

        this.rLow[repelEnd] = r0;
        rLow2[repelEnd] = r0 * r0;
        rUp2[repelEnd] = Double.MAX_VALUE;
        weights[repelEnd] = 1.0;
        repelEnd++;
    }

    public void addPair(int i, int j, int iUnit, int jUnit, double rLow, double rUp, boolean isBond, int group, double weight) {
        iGroups[disEnd] = group;
        iAtoms[disEnd] = i;
        jAtoms[disEnd] = j;
        iUnits[disEnd] = iUnit;
        jUnits[disEnd] = jUnit;

        this.rLow[disEnd] = rLow;
        rLow2[disEnd] = rLow * rLow;
        this.rUp[disEnd] = rUp;
        rUp2[disEnd] = rUp * rUp;

        hasBondConstraint[i] = isBond;
        hasBondConstraint[j] = isBond;
        weights[disEnd] = weight;
        if (fixed != null) {
            if (isBond) {
                if (i < j) {
                    fixed[i][j - i - 1] = true;
                } else {
                    fixed[j][i - j - 1] = true;
                }
            }
        }
        //if (isBond){
        //    weights[disEnd] = 25.0;
        //}else{
        //    weights[disEnd] = 1.0;
        //}
        disEnd++;
    }

    public double calcRepel(boolean calcDeriv, double weight) {
        double sum = 0.0;
        for (int i = repelStart; i < repelEnd; i++) {
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];
            FastVector3D iV = vecCoords[iAtom];
            FastVector3D jV = vecCoords[jAtom];
            double r2 = iV.disSq(jV);
            disSq[i] = r2;
            derivs[i] = 0.0;
            viol[i] = 0.0;
            if (r2 <= rLow2[i]) {
                double r = FastMath.sqrt(r2);
                double dif = rLow[i] - r;
                viol[i] = weights[i] * weight * dif * dif;
                sum += viol[i];
                if (calcDeriv) {
                    //  what is needed is actually the derivative/r, therefore
                    // we divide by r
                    // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
                    derivs[i] = -2.0 * weights[i] * weight * dif / (r + RADJ);
                }
            }
        }
        return sum;
    }

    public double calcNOE(boolean calcDeriv, double weight) {
        return calcEnergy(calcDeriv, weight, 0);
    }

    public ViolationStats getRepelError(int i, double limitVal, double weight) {
        return getError(i, limitVal, weight, 1);
    }

    public ViolationStats getNOEError(int i, double limitVal, double weight) {
        return getError(i, limitVal, weight, 0);
    }

    class ViolationStats {

        int mode;
        String aName1;
        String aName2;
        double dis;
        double rUp;
        double rLow;
        double energy;
        double constraintDis = 0.0;
        double dif;

        ViolationStats(int mode, String aName1, String aName2, double dis, double rLow, double rUp, double energy) {
            this.mode = mode;
            this.aName1 = aName1;
            this.aName2 = aName2;
            this.dis = dis;
            this.rLow = rLow;
            this.rUp = rUp;
            this.energy = energy;
            dif = 0.0;
            if (mode == 1) {
                constraintDis = rLow;
                if (dis < rLow) {
                    dif = dis - rLow;
                }
            } else {
                if (dis < rLow) {
                    constraintDis = rLow;
                    dif = dis - rLow;
                } else if (dis > rUp) {
                    constraintDis = rUp;
                    dif = dis - rUp;
                }
            }
        }

        double getViol() {
            return dif;
        }

        public String toString() {
            String modeType = "Dis";
            if (mode == 1) {
                modeType = "Rep";
            }
            String result = String.format("%s: %10s %10s %5.2f %5.2f %5.2f %7.3f\n", modeType, aName1, aName2, constraintDis, dis, dif, energy);
            return result;

        }

    }

    public ViolationStats getError(int i, double limitVal, double weight, int mode) {
        String modeType = "Dis";
        if (mode == 1) {
            i += repelStart;
            modeType = "Rep";
        }
        int iAtom = iAtoms[i];
        int jAtom = jAtoms[i];
        double r2 = disSq[i];
        double r = FastMath.sqrt(r2);
        double dif = 0.0;
        double constraintDis = 0.0;
        if (r2 <= rLow2[i]) {
            r = FastMath.sqrt(r2);
            dif = rLow[i] - r;
            constraintDis = rLow[i];
        } else if (r2 >= rUp2[i]) {
            r = FastMath.sqrt(r2);
            dif = rUp[i] - r;
            constraintDis = rUp[i];
        }
        String result = "";
        ViolationStats stat = null;
        if (Math.abs(dif) > limitVal) {
            double energy = weights[i] * weight * dif * dif;
            stat = new ViolationStats(mode, atoms[iAtom].getFullName(), atoms[jAtom].getFullName(), r, rLow[i], rUp[i], energy);
        }

        return stat;
    }

    public double dumpRestraints(boolean calcDeriv, double weight, int mode) {
        double sum = 0.0;
        int start = 0;
        int end = disEnd;
        if (mode != 0) {
            start = repelStart;
            end = repelEnd;
        }
        for (int i = start; i < end; i++) {
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];
            FastVector3D iV = vecCoords[iAtom];
            FastVector3D jV = vecCoords[jAtom];
            double r2 = iV.disSq(jV);
            derivs[i] = 0.0;
            viol[i] = 0.0;
            final double dif;
            final double r;
            if (r2 <= rLow2[i]) {
                r = FastMath.sqrt(r2);
                dif = rLow[i] - r;
            } else if (r2 >= rUp2[i]) {
                r = FastMath.sqrt(r2);
                dif = rUp[i] - r;
            } else {
                continue;
            }
            viol[i] = weights[i] * weight * dif * dif;
            sum += viol[i];
            if (calcDeriv) {
                //  what is needed is actually the derivitive/r, therefore
                // we divide by r
                // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
                derivs[i] = -2.0 * weights[i] * weight * dif / (r + RADJ);
            }

        }
        return sum;
    }

    public void updateGroups() {
        int start = 0;
        int end = disEnd;
        for (int i = start; i < end;) {
            groupSizes[i] = 1;
            int j = i + 1;
            while (iGroups[j] == iGroups[i] && j < end) {
                groupSizes[i]++;
                j++;
            }
            i = j;
        }
        updateSwappable();
    }

    public void updateSwappable() {
        int nPartner = 0;
        for (int i = 0; i < atoms.length; i++) {
            Optional<Atom> partner = atoms[i].getMethylenePartner();
            if (partner.isPresent()) {
                mAtoms[i] = partner.get().eAtom;
                nPartner++;
            } else {
                mAtoms[i] = -1;
            }
        }
        int start = 0;
        int end = disEnd;
        kSwap = new HashMap<>();

        for (int k = start; k < end; k++) {
            int groupSize = groupSizes[k];
            for (int kk = 0; kk < groupSize; kk++) {
                int i = iAtoms[k + kk];
                int j = jAtoms[k + kk];
                int storeIndex;
                if (mAtoms[i] != -1) {
                    if (i < mAtoms[i]) {
                        storeIndex = i;
                    } else {
                        storeIndex = mAtoms[i];
                    }
                    Set<Integer> swaps = kSwap.get(storeIndex);
                    if (swaps == null) {
                        swaps = new HashSet<>();
                        kSwap.put(storeIndex, swaps);
                    }
                    swaps.add(k);
                }
                if (mAtoms[j] != -1) {
                    if (j < mAtoms[j]) {
                        storeIndex = j;
                    } else {
                        storeIndex = mAtoms[j];
                    }
                    Set<Integer> swaps = kSwap.get(storeIndex);
                    if (swaps == null) {
                        swaps = new HashSet<>();
                        kSwap.put(storeIndex, swaps);
                    }
                    swaps.add(k);
                }
            }
            if (groupSizes[k] > 1) {
                k += groupSizes[k] - 1;
            }
        }
    }

    public void doSwaps() {
        for (Entry<Integer, Set<Integer>> entry : kSwap.entrySet()) {
            doSwap(entry.getKey(), entry.getValue());
        }
    }

    public void doSwap(int i, Set<Integer> swaps) {
        double preSwap = swapEnergy(swaps);
        swapIt(i);
        double postSwap = swapEnergy(swaps);
        if (postSwap < preSwap) {
            swapped[i] = !swapped[i];
            swapped[mAtoms[i]] = !swapped[mAtoms[i]];
        } else {
            // restore if swap not lower energy
            swapIt(i);
        }
//        double restoreSwap = swapEnergy(swaps);
//        System.out.printf("%3d %10s %8.3f %8.3f %8.3f\n", i, atoms[i].getFullName(), preSwap, postSwap, restoreSwap);
    }

    double swapEnergy(Set<Integer> swaps) {
        double sum = 0.0;
        for (Integer k : swaps) {
            sum += calcEnergy(false, 2.0, 0, k);
        }
        return sum;
    }

    void swapIt(int origAtom) {
        int swapAtom = mAtoms[origAtom];
        if (swapAtom != -1) {
            Set<Integer> swaps = kSwap.get(origAtom);
            if (swaps != null) {
                for (Integer k : swaps) {
                    int groupSize = groupSizes[k];
                    for (int kk = 0; kk < groupSize; kk++) {
                        int ik = kk + k;
                        if (iAtoms[ik] == origAtom) {
                            iAtoms[ik] = swapAtom;
                        } else if (iAtoms[ik] == swapAtom) {
                            iAtoms[ik] = origAtom;
                        }
                        if (jAtoms[ik] == origAtom) {
                            jAtoms[ik] = swapAtom;
                        } else if (jAtoms[ik] == swapAtom) {
                            jAtoms[ik] = origAtom;
                        }
                    }
                }
            }
        }
    }

    public void dumpSwaps() {
        for (Entry<Integer, Set<Integer>> entry : kSwap.entrySet()) {
            dumpSwap(entry.getKey(), entry.getValue());
        }
    }

    public void dumpSwap(int iAtom, Set<Integer> set) {
        System.out.print(atoms[iAtom].getFullName());
        for (Integer k : set) {
            int groupSize = groupSizes[k];
            for (int kk = 0; kk < groupSize; kk++) {
                int ik = kk + k;
                System.out.print(" " + k + " " + ik + " " + atoms[iAtoms[ik]].getFullName() + " " + atoms[jAtoms[ik]].getFullName());
            }
        }
        System.out.println("");
    }

    public double calcEnergy(boolean calcDeriv, double weight, int mode) {
        double sum = 0.0;
        int start = 0;
        int end = disEnd;
        if (mode != 0) {
            start = repelStart;
            end = repelEnd;
        }
        for (int i = start; i < end; i++) {
            sum += calcEnergy(calcDeriv, weight, mode, i);
            if (groupSizes[i] > 1) {
                i += groupSizes[i] - 1;
            }
        }
        return sum;
    }

    public double calcEnergy(boolean calcDeriv, double weight, int mode, int i) {
        double sum = 0.0;
        int groupSize = groupSizes[i];
        int nMono = 1;
        double r2;
        double r2Min = Double.MAX_VALUE;
        if (groupSize > 1) {
            double sum2 = 0.0;
            for (int j = 0; j < groupSize; j++) {
                int iAtom = iAtoms[i + j];
                int jAtom = jAtoms[i + j];
                FastVector3D iV = vecCoords[iAtom];
                FastVector3D jV = vecCoords[jAtom];
                double r2Temp = iV.disSq(jV);
                double r = FastMath.sqrt(r2Temp);
                sum2 += FastMath.pow(r, -6);
                derivs[i + j] = 0.0;
                viol[i + j] = 0.0;
                if (r2Temp < r2Min) {
                    r2Min = r2Temp;
                }
            }
            sum2 /= nMono;
            double r = FastMath.pow(sum2, -1.0 / 6);
            r2 = r * r;
            for (int j = 0; j < groupSize; j++) {
                disSq[i + j] = r2;
            }
        } else {
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];
            FastVector3D iV = vecCoords[iAtom];
            FastVector3D jV = vecCoords[jAtom];
            r2 = iV.disSq(jV);
            disSq[i] = r2;
            derivs[i] = 0.0;
            viol[i] = 0.0;
            r2Min = r2;
        }
        final double dif;
        final double r;
        if (r2Min <= rLow2[i]) {
            r = FastMath.sqrt(r2Min);
            dif = rLow[i] - r;
        } else if (r2 >= rUp2[i]) {
            r = FastMath.sqrt(r2);
            dif = rUp[i] - r;
        } else {
            return 0.0;
        }
        viol[i] = weights[i] * weight * dif * dif;
        sum += viol[i];
        if (calcDeriv) {
            //  what is needed is actually the derivative/r, therefore
            // we divide by r
            // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
            derivs[i] = -2.0 * weights[i] * weight * dif / (r + RADJ);
        }
        if (groupSize > 1) {
            for (int j = 1; j < groupSize; j++) {
                viol[i + j] = viol[i];
                sum += viol[i + j];
                if (calcDeriv) {
                    //  what is needed is actually the derivative/r, therefore
                    // we divide by r
                    // fixme problems if r near 0.0 so we add small adjustment.  Is there a better way???
                    derivs[i + j] = derivs[i];
                }
            }
        }

        return sum;
    }

    public void addRepelDerivs(AtomBranch[] branches) {
        addDerivs(branches, 1);
    }

    public void addNOEDerivs(AtomBranch[] branches) {
        addDerivs(branches, 0);
    }

    public double calcDihedral(int a, int b, int c, int d) {
        FastVector3D av = vecCoords[a];
        FastVector3D bv = vecCoords[b];
        FastVector3D cv = vecCoords[c];
        FastVector3D dv = vecCoords[d];
        return calcDihedral(av, bv, cv, dv);
    }

    /**
     * Calculates the dihedral angle
     *
     * @param a first point
     * @param b second point
     * @param c third point
     * @param d fourth point
     * @return angle
     */
    public static double calcDihedral(final FastVector3D a, final FastVector3D b, final FastVector3D c, final FastVector3D d) {
        Point3 a3 = new Point3(a.getX(), a.getY(), a.getZ());
        Point3 b3 = new Point3(b.getX(), b.getY(), b.getZ());
        Point3 c3 = new Point3(c.getX(), c.getY(), c.getZ());
        Point3 d3 = new Point3(d.getX(), d.getY(), d.getZ());
        return AtomMath.calcDihedral(a3, b3, c3, d3);
    }

    public void addDerivs(AtomBranch[] branches, int mode) {
        int start = 0;
        int end = disEnd;
        if (mode != 0) {
            start = repelStart;
            end = repelEnd;
        }
        FastVector3D v1 = new FastVector3D();
        FastVector3D v2 = new FastVector3D();
        for (int i = start; i < end; i++) {
            double deriv = derivs[i];
            if (deriv == 0.0) {
                continue;
            }
            int iAtom = iAtoms[i];
            int jAtom = jAtoms[i];

            FastVector3D pv1 = vecCoords[iAtom];
            FastVector3D pv2 = vecCoords[jAtom];

            pv1.crossProduct(pv2, v1);
            v1.multiply(derivs[i]);

            pv1.subtract(pv2, v2);
            v2.multiply(derivs[i]);
            int iUnit = iUnits[i];
            int jUnit = jUnits[i];

            if (iUnit >= 0) {
                branches[iUnit].addToF(v1.getValues());
                branches[iUnit].addToG(v2.getValues());

            }
            if (jUnit >= 0) {
                branches[jUnit].subtractToF(v1.getValues());
                branches[jUnit].subtractToG(v2.getValues());
            }
        }
    }

    double[][] getBoundaries() {
        double[][] bounds = new double[3][2];
        for (int i = 0; i < 3; i++) {
            bounds[i][0] = Double.MAX_VALUE;
            bounds[i][1] = Double.NEGATIVE_INFINITY;
        }
        for (int i = 0; i < nAtoms; i++) {
            double[] data = vecCoords[i].getValues();
            for (int j = 0; j < 3; j++) {
                bounds[j][0] = Math.min(data[j], bounds[j][0]);
                bounds[j][1] = Math.max(data[j], bounds[j][1]);
            }
        }
        // change bounds to be minimum and size
        for (int j = 0; j < 3; j++) {
            bounds[j][1] = bounds[j][1] - bounds[j][0];
        }
        return bounds;
    }

    public void setRadii(double hardSphere, boolean includeH, double shrinkValue, double shrinkHValue) {
        for (int i = 0; i < nAtoms; i++) {
            Atom atom1 = atoms[i];
            AtomEnergyProp iProp = (AtomEnergyProp) atom1.atomEnergyProp;
            if (iProp == null) {
                contactRadii[i] = 0.0;
            } else if (atom1.getType().endsWith("g")) { // coarseGrain
                contactRadii[i] = 0.0;
            } else {
                hBondable[i] = iProp.getHBondMode();
                double rh1 = iProp.getRh();

                if (atom1.getAtomicNumber() != 1) {
                    rh1 -= shrinkValue;
                } else {
                    rh1 -= shrinkHValue;
                }

                if (!includeH) {
                    if (atom1.hydrogens > 0) {
                        rh1 += hardSphere;
                    }
                }
                contactRadii[i] = rh1;
            }
        }
    }

    public void setCells(EnergyLists eList, int deltaEnd, double limit, double hardSphere, boolean includeH, double shrinkValue, double shrinkHValue) {
        double limit2 = limit * limit;
        double[][] bounds = getBoundaries();
        int[] nCells = new int[3];
        setRadii(hardSphere, includeH, shrinkValue, shrinkHValue);
//        System.out.println("set cells");
        clear();

        for (int j = 0; j < 3; j++) {
            nCells[j] = 1 + (int) Math.floor(bounds[j][1] / limit);
        }
        int[] strides = {1, nCells[0], nCells[0] * nCells[1]};
        int nCellsTotal = nCells[0] * nCells[1] * nCells[2];
        int[] cellCounts = new int[nCellsTotal];
        int[] cellStarts = new int[nCellsTotal];
        for (int i = 0; i < nAtoms; i++) {
            double[] data = vecCoords[i].getValues();
            int[] idx = new int[3];
            for (int j = 0; j < 3; j++) {
                idx[j] = (int) Math.floor((data[j] - bounds[j][0]) / limit);
            }
            int index = idx[0] + idx[1] * strides[1] + idx[2] * strides[2];
            cellCounts[index]++;
            cellIndex[i] = index;
//            System.out.println(i + " index " + index + " " + atoms[i].getShortName() + " " + idx[0] + " " + idx[1] + " " + idx[2]);
        }
        int[] offsets1 = new int[offsets.length];
        int start = 0;
        for (int i = 0; i < nCellsTotal; i++) {
            cellStarts[i] = start;
            start += cellCounts[i];
        }
        for (int i = 0; i < offsets1.length; i++) {
            int delta = offsets[i][0] + offsets[i][1] * strides[1] + offsets[i][2] * strides[2];
            offsets1[i] = delta;
        }
        int[] atomIndex = new int[nAtoms];
        int[] nAdded = new int[nCellsTotal];
        for (int i = 0; i < nAtoms; i++) {
            int index = cellIndex[i];
            atomIndex[cellStarts[index] + nAdded[index]] = i;
//            System.out.println("index " + i + " " + index + " " + (cellStarts[index] + nAdded[index]));
            nAdded[index]++;
        }
        for (int ix = 0; ix < nCells[0]; ix++) {
            for (int iy = 0; iy < nCells[1]; iy++) {
                for (int iz = 0; iz < nCells[2]; iz++) {
                    int iCell = ix + iy * strides[1] + iz * strides[2];
                    int iStart = cellStarts[iCell];
                    int iEnd = iStart + cellCounts[iCell];
//                    System.out.println("iCell " + iCell + " " + iStart + " " + iEnd + " " + ix + " " + iy + " " + iz);
                    int jOffset = 0;
                    for (int iOff = 0; iOff < offsets.length; iOff++) {
                        int dX = offsets[iOff][0];
                        int dY = offsets[iOff][1];
                        int dZ = offsets[iOff][2];
                        int jx = ix + dX;
                        int jy = iy + dY;
                        int jz = iz + dZ;
//                        System.out.println(dX + " " + dY + " " + dZ + "iCell jCell");
                        if ((jx < 0) || (jx >= nCells[0])) {
                            continue;
                        }
                        if ((jy < 0) || (jy >= nCells[1])) {
                            continue;
                        }
                        if ((jz < 0) || (jz >= nCells[2])) {
                            continue;
                        }
                        int jCell = jx + jy * strides[1] + jz * strides[2];
//                        System.out.println(iCell + " cell " + jCell + " offset " + iOff + " " + jOffset++);
                        int jStart = cellStarts[jCell];
                        int jEnd = jStart + cellCounts[jCell];
//                        System.out.println("iCell " + iCell + " jCell " + jCell + " " + jStart + " " + jEnd);

                        for (int i = iStart; i < iEnd; i++) {
                            int ip = atomIndex[i];
                            if ((atoms[ip].getAtomicNumber() == 1) && !includeH) {
                                continue;
                            }
                            if (iCell == jCell) {
                                jStart = i + 1;
                            }
                            for (int j = jStart; j < jEnd; j++) {
                                int jp = atomIndex[j];
                                if ((atoms[jp].getAtomicNumber() == 1) && !includeH) {
                                    continue;
                                }
                                if (ip != jp) {
                                    int iAtom;
                                    int jAtom;
                                    if (ip < jp) {
                                        iAtom = ip;
                                        jAtom = jp;
                                    } else {
                                        iAtom = jp;
                                        jAtom = ip;
                                    }
                                    Atom atom1 = atoms[iAtom];
                                    Atom atom2 = atoms[jAtom];
                                    double disSq = vecCoords[iAtom].disSq(vecCoords[jAtom]);
//                                    System.out.println("i " + i + " j " + j + " iCell " + iCell + " " + jCell + " " + iOff + " atom " + iAtom + " " + (jAtom - iAtom - 1) + " " + atom1.getShortName() + " " + atom2.getShortName() + " " + disSq);
                                    if (disSq < limit2) {
                                        int iRes = resNums[iAtom];
                                        int jRes = resNums[jAtom];
                                        int deltaRes = Math.abs(jRes - iRes);
                                        if (deltaRes >= deltaEnd) {
                                            continue;
                                        }
                                        boolean notFixed = true;
                                        double adjustClose = 0.0;
                                        // fixme could we have invalid jAtom-iAtom-1, if res test inappropriate
                                        if ((iRes == jRes) || (deltaRes == 1)) {
                                            if ((iAtom >= fixed.length) || ((jAtom - iAtom - 1) >= fixed[iAtom].length)) {
                                                System.out.println("i " + i + " j " + j + " iCell " + iCell + " " + jCell + " " + iOff + " atom " + iAtom + " " + (jAtom - iAtom - 1) + " " + atom1.getShortName() + " " + atom2.getShortName() + " " + disSq);
                                            }
                                            if (fixed[iAtom][jAtom - iAtom - 1]) {
                                                notFixed = false;
                                            }
                                            if (checkCloseAtoms(atom1, atom2)) {
                                                adjustClose = 0.2;
                                            }
                                        }
                                        boolean interactable1 = (contactRadii[iAtom] > 1.0e-6) && (contactRadii[jAtom] > 1.0e-6);
                                        // fixme  this is fast, but could miss interactions for atoms that are not bonded
                                        // as it doesn't test for an explicit bond between the pairs
                                        boolean notConstrained = !hasBondConstraint[iAtom] || !hasBondConstraint[jAtom];
//                                        System.out.println("        " + notFixed + " " + (fixed[iAtom][jAtom - iAtom - 1]) + " " + deltaRes + " "
//                                                + interactable1 + " " + notConstrained);
                                        if (notFixed && interactable1 && notConstrained) {
                                            int iUnit;
                                            int jUnit;
                                            if (atom1.rotGroup != null) {
                                                iUnit = atom1.rotGroup.rotUnit;
                                            } else {
                                                iUnit = -1;
                                            }
                                            if (atom2.rotGroup != null) {
                                                jUnit = atom2.rotGroup.rotUnit;
                                            } else {
                                                jUnit = -1;
                                            }

                                            //double rH = ePair.getRh();
                                            double rH = contactRadii[iAtom] + contactRadii[jAtom];
                                            if (hBondable[iAtom] * hBondable[jAtom] < 0) {
                                                rH -= hbondDelta;
                                            }
                                            rH -= adjustClose;

                                            addPair(iAtom, jAtom, iUnit, jUnit, rH);

                                        }
                                    }
                                }
//                                System.out.println(iOff + " " + i + " " + j + " " + k + " " + ip + " " + jp + " " + disSq);
                            }
                        }
                    }
                }
            }
        }
//System.out.println("nrep " + (repelEnd-repelStart) + " " + includeH + " " + limit);
    }

    public double[][][] getFixedRange() {
        int lastRes = Integer.MIN_VALUE;
        int nResidues = 0;
        for (int i = 0; i < nAtoms; i++) {
            if (resNums[i] != lastRes) {
                lastRes = resNums[i];
                nResidues++;
            }
        }
        int[] resCounts = new int[nResidues];
        int[] resStarts = new int[nResidues];
        lastRes = Integer.MIN_VALUE;
        int j = 0;
        for (int i = 0; i < nAtoms; i++) {
            resCounts[resNums[i]]++;
            if (resNums[i] != lastRes) {
                lastRes = resNums[i];
                resStarts[j++] = i;
            }
        }
        double[][][] disRange = new double[2][nAtoms][];
        fixed = new boolean[nAtoms][];
        for (int i = 0; i < nAtoms; i++) {
            int resNum = resNums[i];
            int lastAtom = i + 200;
            if (lastAtom >= nAtoms) {
                lastAtom = nAtoms - 1;
            }
            int nResAtoms = lastAtom - i;
//            int nResAtoms = resCounts[resNum];
//            nResAtoms -= (i - resStarts[resNum]) + 1;
//            if (resNum < (nResidues - 1)) {
//                nResAtoms += resCounts[resNum + 1];
//            }
            disRange[0][i] = new double[nResAtoms];
            fixed[i] = new boolean[nResAtoms];
            Arrays.fill(disRange[0][i], Double.MAX_VALUE);
            disRange[1][i] = new double[nResAtoms];
            Arrays.fill(disRange[1][i], Double.NEGATIVE_INFINITY);

        }
        return disRange;
    }

    public void updateRanges(double[][][] disRanges) {
        for (int i = 0; i < nAtoms; i++) {
            FastVector3D v1 = vecCoords[i];
            for (int j = 0, len = disRanges[0][i].length; j < len; j++) {
                FastVector3D v2 = vecCoords[i + j + 1];
                double dis = v1.dis(v2);
                disRanges[0][i][j] = Math.min(dis, disRanges[0][i][j]);
                disRanges[1][i][j] = Math.max(dis, disRanges[1][i][j]);
            }
        }
    }

    public void updateFixed(double[][][] disRanges) {
        double tol = 0.2;
        int nFixed = 0;
        for (int i = 0; i < nAtoms; i++) {
//            System.out.print(i);
            for (int j = 0, len = disRanges[0][i].length; j < len; j++) {
                double delta = disRanges[1][i][j] - disRanges[0][i][j];
                //System.out.println(i + " " + j + " " + atoms[i].getShortName() + " " + atoms[i + j + 1].getShortName() + " " + delta);
                fixed[i][j] = delta < tol;
                if (fixed[i][j]) {
                    nFixed++;
                }
//                if (fixed[i][j]) {
//                    System.out.print(" " + j);
//                }
            }
//            System.out.println("");
        }
        System.out.println("Nfix " + nFixed);
        //dumpFixed();
    }

    public void dumpFixed() {
        System.out.println("dump fixed");
        for (int i = 0; i < nAtoms; i++) {
            System.out.println(atoms[i].getShortName());
        }
        for (int i = 0; i < fixed.length; i++) {
            for (int j = 0; j < fixed[i].length; j++) {
                if (fixed[i][j]) {
                    System.out.println(atoms[i].getShortName() + " " + atoms[i + j + 1].getShortName());
                }
            }
        }
    }

    public boolean fixedCurrent() {
        boolean status = (fixed != null) && (fixed.length == nAtoms);
        return status;
    }

    public boolean checkCloseAtoms(Atom atom1, Atom atom2) {
        boolean close = false;
        if ((atom1.getAtomicNumber() != 1) || (atom2.getAtomicNumber() != 1)) {
            Atom testAtom1;
            Atom testAtom2;
            if (atom1.rotUnit < atom2.rotUnit) {
                testAtom1 = atom1;
                testAtom2 = atom2;
            } else {
                testAtom2 = atom1;
                testAtom1 = atom2;
            }
            Atom rotGroup1 = testAtom1.rotGroup;
            Atom rotGroup2 = testAtom2.rotGroup;
            Atom parent2 = null;
            if (rotGroup2 != null) {
                parent2 = rotGroup2.parent;
            }
            // atoms in close groups are allowed to be a little closer to add a little more flexibility since we don't allow bond angles and lengths to change
            if (parent2 != null && (parent2.parent == testAtom1)) {
                close = true;
            } else if (rotGroup1 != null) {
                Atom parent1 = rotGroup1.parent;
                if (parent1 != null) {
                    if (parent1.parent == testAtom2) {
                        close = true;
                    } else if (parent1 == testAtom2.parent) {
                        close = true;
                    } else if (rotGroup1 == parent2) {
                        close = true;
                    } else if (parent1 == testAtom2.parent) {
                        close = true;
                    } else if (parent2 == testAtom1.parent) {
                        close = true;
                    } else if (parent1 == parent2) {
                        //  rh1 -= 0.1;
                        //  rh2 -= 0.1;
                    }
                }
            }
        }
        return close;

    }

}
