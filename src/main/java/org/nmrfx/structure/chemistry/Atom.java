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
package org.nmrfx.structure.chemistry;

import org.nmrfx.structure.chemistry.io.AtomParser;
import java.util.*;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.nmrfx.processor.datasets.peaks.AtomResonance;

public class Atom {

    static final public int SELECT = 0;
    static final public int DISPLAY = 1;
    static final public int SUPER = 2;
    static final public int LABEL = 2;
    static final public double NULL_PPM = -9990.0;
    static int lastAtom = 0;
    public int iAtom = 1;
    public Atom parent = null;
    public String name;
    public String type = "";
    public String label = "";
    public Entity entity = null;
    public List<Bond> bonds;
    public double mass = 0.0;
    private int stereo = 0;
    public byte nPiBonds = 0;
    public byte nonHydrogens = 0;
    public byte hydrogens = 0;
    public float charge = 0.0f;
    public float fcharge = 0.0f;
    public float bondLength = 1.0f;
    public float bndSin = 1.0f;
    public float bndCos = 1.0f;
    public float valanceAngle = (float) (120.0 * Math.PI / 180.0);
    public float dihedralAngle = (float) (109 * Math.PI / 180.0);
    public String stereoStr = null;
    public float radius = 0.9f;
    public int aNum = 0;
    AtomProperty atomProperty = null;
    public String forceFieldCode = null;
    public float value = 0.0f;
    public boolean active = true;
    public ArrayList<AtomEquivalency> equivAtoms = null;
    public final SpatialSet spatialSet;
    AtomResonance resonance = null;
    public int irpIndex = 0;
    public int rotUnit = -1;
    public Atom rotGroup = null;
    public boolean rotActive = true;
    public int canonValue = 0;
    public Atom[] branchAtoms = new Atom[0];
    public Object atomEnergyProp = null;

    public Atom(AtomParser atomParse) {
        spatialSet = new SpatialSet(this);
        initialize(atomParse);
    }

    public Atom(String name) {
        spatialSet = new SpatialSet(this);
        this.name = name;
        aNum = getElementNumber(name);
        atomProperty = AtomProperty.get(name);
        radius = atomProperty.radius;
        setColorByType();
        bonds = new ArrayList<>(2);
        iAtom = lastAtom;
        lastAtom++;
    }

    public Atom(String name, String aType) {
        spatialSet = new SpatialSet(this);
        this.name = name;
        aNum = getElementNumber(aType);
        atomProperty = AtomProperty.get(aType);
        radius = atomProperty.radius;
        setColorByType();
        bonds = new ArrayList<>(2);
        iAtom = lastAtom;
        lastAtom++;
    }

    void initialize(AtomParser atomParse) {
        name = atomParse.atomName;

        if (!atomParse.elemName.equals("")) {
            aNum = getElementNumber(atomParse.elemName);
        } else {
            int len = name.length();

            while ((len > 0)
                    && ((aNum = getElementNumber(name.substring(0, len))) == 0)) {
                len--;
            }
        }

        atomProperty = AtomProperty.get(aNum);
        radius = atomProperty.radius;

        setColorByType();
        charge = (float) atomParse.charge;
        bonds = new ArrayList<>(2);
        iAtom = lastAtom;
        lastAtom++;
    }

    public void changed() {
        if (entity != null) {
            entity.changed();
        }
    }

    public Atom add(String name, String elementName, int order) {
        Atom newAtom = new Atom(name, elementName);
        newAtom.parent = this;
        if (entity != null) {
            Compound compound = (Compound) entity;
            compound.addAtom(this, newAtom);
            addBond(this, newAtom, order, 0, false);
        }
        return newAtom;
    }

    public void remove() {
        remove(false);
    }

    public void remove(final boolean record) {
        removeBonds();
        if (entity != null) {
            Compound compound = (Compound) entity;
            compound.removeAtom(this);
        }
        Molecule.atomList = null;
        if (record) {
            if (entity instanceof Residue) {
                Residue residue = (Residue) entity;
                Polymer polymer = residue.polymer;

                AtomSpecifier atomSp = new AtomSpecifier(residue.getNumber(), residue.getName(), getName());
                polymer.deletedAtoms.add(atomSp);
            }
        }
    }

    public void removeBonds() {
        List<Atom> connected = getConnected();
        for (int i = 0; i < connected.size(); i++) {
            Atom atom = (Atom) connected.get(i);
            atom.removeBondTo(this);
        }
    }

    public void removeBondTo(Atom atom) {
        List<Bond> newBonds = new ArrayList<>(2);
        for (int i = 0; i < bonds.size(); i++) {
            Bond bond = bonds.get(i);
            Atom atomB = bond.begin;
            Atom atomE = bond.end;
            if ((atomB != atom) && (atomE != atom)) {
                newBonds.add(bond);
            } else {
                atom.entity.removeBond(bond);
            }
        }
        bonds = newBonds;
    }

    public List<Atom> getConnected() {
        List<Atom> connected = new ArrayList<>(4);
        for (int i = 0; i < bonds.size(); i++) {
            Bond bond = bonds.get(i);
            Atom atomB = bond.begin;
            Atom atomE = bond.end;

            if (atomB == this) {
                connected.add(atomE);
            } else if (atomE == this) {
                connected.add(atomB);
            }
        }

        return connected;
    }

    public void setActive(boolean state) {
        active = state;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isBonded(Atom atom) {
        boolean bonded = false;
        for (int i = 0; i < bonds.size(); i++) {
            Bond bond = bonds.get(i);
            Atom atomB = bond.begin;
            Atom atomE = bond.end;

            if (atomB == this) {
                if (atomE == atom) {
                    bonded = true;
                    break;
                }
            } else if (atomE == this) {
                if (atomB == atom) {
                    bonded = true;
                    break;
                }
            }
        }
        return bonded;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String name) {
        type = name;
    }

    public String getType() {
        return type;
    }

    public int getAtomicNumber() {
        return aNum;
    }

    public void setAtomicNumber(int num) {
        aNum = num;
        atomProperty = AtomProperty.get(num);
        setColorByType();
    }

    public void setAtomicNumber(String name) {
        atomProperty = AtomProperty.get(name);
        aNum = atomProperty.aNum;
        setColorByType();
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return iAtom;
    }

    public String getShortName() {
        if (entity instanceof Residue) {
            return ((Residue) entity).number + "." + name;
        } else {
            return name;
        }
    }

    public Entity getEntity() {
        return entity;
    }

    public String getFullName() {
        if (entity instanceof Residue) {
            return ((Residue) entity).polymer.name + ":"
                    + ((Residue) entity).number + "." + name;
        } else {
            return entity.name + ":." + name;
        }
    }

    public int getResidueNumber() {
        Integer result = 0;
        if ((entity != null) && (entity instanceof Compound)) {
            result = ((Compound) entity).getResNum();
            if (result == null) {
                result = 0;
            }
        }
        return result;
    }

    public String getResidueName() {
        String result = "";
        if ((entity != null) && (entity instanceof Compound)) {
            result = ((Compound) entity).getName();
        }
        return result;
    }

    public String getPolymerName() {
        String result = "";
        if (entity instanceof Residue) {
            result = ((Residue) entity).polymer.getName();
        } else {
            result = ((Compound) entity).getName();
        }
        return result;
    }

    public void addBond(Bond bond) {
        bonds.add(bond);
    }

    public SpatialSet getSpatialSet() {
        return spatialSet;
    }

    public void setResonance(AtomResonance resonance) {
        AtomResonance current = this.resonance;
        if ((resonance == null) && (current != null)) {
            current.setAtom(null);
        }
        this.resonance = resonance;
        if (resonance != null) {
            resonance.setAtom(this);
        }
    }

    public AtomResonance getResonance() {
        return resonance;
    }

    public void setSelected(int value) {
        spatialSet.selected = value;
    }

    public int getSelected() {
        return spatialSet.getSelected();
    }

    public void setDisplayStatus(int value) {
        spatialSet.displayStatus = value;
    }

    public void setLabelStatus(int value) {
        spatialSet.labelStatus = value;
    }

    public void setColorByType() {
        setRed(atomProperty.getRed());
        setGreen(atomProperty.getGreen());
        setBlue(atomProperty.getBlue());
    }

    public void setColor(float red, float green, float blue) {
        spatialSet.red = red;
        spatialSet.green = green;
        spatialSet.blue = blue;
    }

    public void setRed(float red) {
        spatialSet.red = red;
    }

    public void setGreen(float green) {
        spatialSet.green = green;
    }

    public void setBlue(float blue) {
        spatialSet.blue = blue;
    }

    public float getRed() {
        return spatialSet.red;
    }

    public float getGreen() {
        return spatialSet.green;
    }

    public float getBlue() {
        return spatialSet.blue;
    }

    public Point3 getPoint() {
        return spatialSet.getPoint();
    }

    public Point3 getPoint(int i) {
        return spatialSet.getPoint(i);
    }

    public List<String> dumpCoords() {
        List<String> list = new ArrayList<>();
        list.add(spatialSet.getFullName());

        for (int i = 0; i < spatialSet.getPointCount(); i++) {
            if (spatialSet.getPoint(i) != null) {
                list.add(String.valueOf(i));

                Point3 p3 = spatialSet.getPoint(i);
                list.add(String.valueOf(p3.getX()));
                list.add(String.valueOf(p3.getY()));
                list.add(String.valueOf(p3.getZ()));
            }
        }
        return list;
    }

    public void setPoint(int i, Point3 pt) {
        spatialSet.setPoint(i, pt);
    }

    public void setPoint(Point3 pt) {
        spatialSet.setPoint(pt);
    }

    public void setPointValidity(int i, boolean validity) {
        spatialSet.setPointValidity(i, validity);
    }

    public void setPointValidity(boolean validity) {
        spatialSet.setPointValidity(validity);
    }

    public boolean getPointValidity(int i) {
        boolean valid = false;
        if (spatialSet != null) {
            valid = spatialSet.getPointValidity(i);
        }
        return valid;
    }

    public boolean getPointValidity() {
        boolean valid = false;
        if (spatialSet != null) {
            valid = spatialSet.getPointValidity();
        }
        return valid;
    }

    public Double getPPM() {
        PPMv ppmV = getPPM(0);
        if ((ppmV != null) && ppmV.isValid()) {
            return ppmV.getValue();
        } else {
            return null;
        }
    }

    public PPMv getPPM(int i) {
        PPMv ppmV = null;
        if ((spatialSet == null) && isMethyl()) {
        } else if (spatialSet != null) {
            ppmV = spatialSet.getPPM(i);
        }

        return ppmV;
    }

    public Double getRefPPM() {
        PPMv ppmV = getRefPPM(0);
        if ((ppmV != null) && ppmV.isValid()) {
            return ppmV.getValue();
        } else {
            return null;
        }
    }

    public PPMv getRefPPM(int i) {
        PPMv ppmV = null;
        if (spatialSet == null) {
        } else if (spatialSet != null) {
            ppmV = spatialSet.getRefPPM();
        }
        return ppmV;
    }

    public void setPPM(double value) {
        spatialSet.setPPM(0, value, false);
    }

    public void setPPM(int i, double value) {
        spatialSet.setPPM(i, value, false);
    }

    public void setRefPPM(double value) {
        spatialSet.setRefPPM(0, value);
    }

    public void setRefPPM(int i, double value) {
        spatialSet.setRefPPM(i, value);
    }

    public void setPPMValidity(int i, boolean validity) {
        spatialSet.setPPMValidity(i, validity);
    }

    public float getBFactor() {
        return spatialSet.getBFactor();
    }

    public void setBFactor(float bfactor) {
        spatialSet.setBFactor(bfactor);
    }

    public float getOccupancy() {
        return spatialSet.getOccupancy();
    }

    public void setOccupancy(float occupancy) {
        spatialSet.setOccupancy(occupancy);
    }

    public float getOrder() {
        return spatialSet.getOrder();
    }

    public void setOrder(float order) {
        spatialSet.setOrder(order);
    }

    public void setProperty(int propIndex) {
        spatialSet.setProperty(propIndex);
    }

    public void unsetProperty(int propIndex) {
        spatialSet.unsetProperty(propIndex);
    }

    public boolean getProperty(int propIndex) {
        return spatialSet.getProperty(propIndex);
    }

    public static double calcDistance(Point3 pt1, Point3 pt2) {
        double x;
        double y;
        double z;
        x = pt1.getX() - pt2.getX();
        y = pt1.getY() - pt2.getY();
        z = pt1.getZ() - pt2.getZ();

        return (Math.sqrt((x * x) + (y * y) + (z * z)));
    }

    public double calcDistance(Atom atom2) {
        Point3 pt1 = spatialSet.getPoint();
        Point3 pt2 = atom2.spatialSet.getPoint();
        double x;
        double y;
        double z;
        x = pt1.getX() - pt2.getX();
        y = pt1.getY() - pt2.getY();
        z = pt1.getZ() - pt2.getZ();

        return (Math.sqrt((x * x) + (y * y) + (z * z)));
    }

    public static double calcAngle(Point3 pt1, Point3 pt2, Point3 pt3) {
        double x;
        double y;
        double z;
        x = pt1.getX() - pt2.getX();
        y = pt1.getY() - pt2.getY();
        z = pt1.getZ() - pt2.getZ();
        Vector3D v12 = new Vector3D(x, y, z);
        x = pt3.getX() - pt2.getX();
        y = pt3.getY() - pt2.getY();
        z = pt3.getZ() - pt2.getZ();
        Vector3D v32 = new Vector3D(x, y, z);
        return Vector3D.angle(v12, v32);
    }

    public static double volume(Vector3D a, Vector3D b, Vector3D c, Vector3D d) {
        Vector3D i = a.subtract(d);
        Vector3D j = b.subtract(d);
        Vector3D k = c.subtract(d);
        // triple product
        double volume = Vector3D.dotProduct(i, Vector3D.crossProduct(j, k));
        return volume;
    }

    public static double calcDihedral(Atom[] atoms) {
        return calcDihedral(atoms, 0);
    }

    public static double calcDihedral(Atom[] atoms, int structureNum) {
        SpatialSet[] spSets = new SpatialSet[4];
        Point3[] pts = new Point3[4];
        int i = 0;
        for (Atom atom : atoms) {
            spSets[i] = atom.spatialSet;
            pts[i] = spSets[i].getPoint(structureNum);
            if (pts[i] == null) {
                throw new IllegalArgumentException("No coordinates for atom " + atom.getFullName());
            }
            i++;
        }
        return calcDihedral(pts[0], pts[1], pts[2], pts[3]);
    }

    public static double calcDihedral(Point3 pt1, Point3 pt2, Point3 pt3, Point3 pt4) {
        double x;
        double y;
        double z;
        Vector3D a = new Vector3D(pt1.getX(), pt1.getY(), pt1.getZ());
        Vector3D b = new Vector3D(pt2.getX(), pt2.getY(), pt2.getZ());
        Vector3D c = new Vector3D(pt3.getX(), pt3.getY(), pt3.getZ());
        Vector3D d = new Vector3D(pt4.getX(), pt4.getY(), pt4.getZ());

        double d12 = Vector3D.distance(a, b);
        double sd13 = Vector3D.distanceSq(a, c);
        double sd14 = Vector3D.distanceSq(a, d);
        double sd23 = Vector3D.distanceSq(b, c);
        double sd24 = Vector3D.distanceSq(b, d);
        double d34 = Vector3D.distance(c, d);
        double ang123 = Vector3D.angle(a.subtract(b), c.subtract(b));
        double ang234 = Vector3D.angle(b.subtract(c), d.subtract(c));
        double cosine = (sd13 - sd14 + sd24 - sd23 + 2.0 * d12 * d34 * Math.cos(ang123) * Math.cos(ang234))
                / (2.0 * d12 * d34 * Math.sin(ang123) * Math.sin(ang234));

        double volume = volume(a, b, c, d);

        double sgn = (volume < 0.0) ? 1.0 : -1.0;
        double angle = 0.0;
        if (cosine > 1.0) {
            angle = 0.0;
        } else if (cosine < -1.0) {
            angle = Math.PI;
        } else {
            angle = sgn * Math.acos(cosine);
        }
        return (angle);

    }

    public static double calcWeightedDistance(SpatialSetGroup spg1, SpatialSetGroup spg2, int iStruct, double expNum, final boolean throwNullPoint, final boolean sumAvg) {
        double x;
        double y;
        double z;
        double sum = 0.0;
        int n = 0;
        double distance = 0.0;
        Set<SpatialSet> spSet1 = spg1.getSpSets();
        Set<SpatialSet> spSet2 = spg2.getSpSets();
        if ((spSet1.size() == 1) && (spSet2.size() == 1)) {
            Point3 pt1 = spg1.getFirstSet().getPoint(iStruct);
            Point3 pt2 = spg2.getFirstSet().getPoint(iStruct);
            if (pt1 == null) {
                if (throwNullPoint) {
                    throw new IllegalArgumentException("Point null \"" + spg1.getFirstSet().getFullName());
                } else {
                    System.out.println("Point null \"" + spg1.getFirstSet().getFullName());
                    return 0.0;
                }
            }
            if (pt2 == null) {
                if (throwNullPoint) {
                    throw new IllegalArgumentException("Point null \"" + spg2.getFirstSet().getFullName());
                } else {
                    System.out.println("Point null \"" + spg2.getFirstSet().getFullName());
                    return 0.0;
                }
            }
            distance = calcDistance(pt1, pt2);

        } else {
            double minDis = Double.MAX_VALUE;
            for (SpatialSet sp1 : spSet1) {
                Point3 pt1 = sp1.getPoint(iStruct);
                if (pt1 == null) {
                    if (throwNullPoint) {
                        throw new IllegalArgumentException("Point null \"" + sp1.atom.getFullName());
                    } else {
                        System.out.println("null point " + sp1.atom.getFullName());
                        return 0.0;
                    }
                }
                for (SpatialSet sp2 : spSet2) {
                    Point3 pt2 = sp2.getPoint(iStruct);
                    if (pt2 == null) {
                        if (throwNullPoint) {
                            throw new IllegalArgumentException("Point null \"" + sp2.atom.getFullName());
                        } else {
                            System.out.println("null point " + sp2.atom.getFullName());
                            return 0.0;
                        }
                    }
                    x = pt1.getX() - pt2.getX();
                    y = pt1.getY() - pt2.getY();
                    z = pt1.getZ() - pt2.getZ();
                    double dis = (Math.sqrt((x * x) + (y * y) + (z * z)));
                    if (dis < minDis) {
                        minDis = dis;
                    }
                    sum += Math.pow(dis, -expNum);
                    n++;
                }
            }
            int nMonomers = 1;
            if (!sumAvg) {
                nMonomers = n;
            }
            distance = Math.pow((sum / nMonomers), -1.0 / expNum);
        }
        return distance;
    }

    public static void getDistances(SpatialSetGroup spg1, SpatialSetGroup spg2, int iStruct, ArrayList<Double> dArray) {
        double x;
        double y;
        double z;
        int n = 0;
        double distance = 0.0;
        Set<SpatialSet> spSet1 = spg1.getSpSets();
        Set<SpatialSet> spSet2 = spg2.getSpSets();
        if ((spSet1.size() == 1) && (spSet2.size() == 1)) {
            Point3 pt1 = spg1.getFirstSet().getPoint(iStruct);
            if (pt1 == null) {
                System.out.println("Point null \"" + spg1.getFirstSet().getFullName());
                return;
            }
            Point3 pt2 = spg2.getFirstSet().getPoint(iStruct);
            if (pt2 == null) {
                System.out.println("Point null \"" + spg2.getFirstSet().getFullName());
                return;
            }
            distance = calcDistance(pt1, pt2);
            dArray.add(distance);

        } else {
            for (SpatialSet sp1 : spSet1) {
                Point3 pt1 = sp1.getPoint(iStruct);
                if (pt1 == null) {
                    System.out.println("null point " + sp1.atom.getFullName());
                    return;
                }
                for (SpatialSet sp2 : spSet2) {
                    Point3 pt2 = sp2.getPoint(iStruct);
                    if (pt2 == null) {
                        System.out.println("null point " + sp2.atom.getFullName());
                        return;
                    }
                    x = pt1.getX() - pt2.getX();
                    y = pt1.getY() - pt2.getY();
                    z = pt1.getZ() - pt2.getZ();
                    double dis = (Math.sqrt((x * x) + (y * y) + (z * z)));
                    dArray.add(dis);
                }
            }
        }

    }

    public static Point3 avgAtom(List<SpatialSet> selected) {
        return avgAtom(selected, 0);
    }

    public static Point3 avgAtom(List<SpatialSet> selected, int structureNum) {
        int i;
        Vector3D pt;
        Vector3D pt1 = new Vector3D(0.0, 0.0, 0.0);
        int nPoints = 0;
        SpatialSet spatialSet = null;

        for (i = 0; i < selected.size(); i++) {
            spatialSet = selected.get(i);
            pt = spatialSet.getPoint(structureNum);

            if (pt != null) {
                nPoints++;
                pt1 = pt1.add(pt);
            }
        }

        if (nPoints == 0) {
            return (null);
        }
        pt1 = pt1.scalarMultiply(1.0 / nPoints);
        return new Point3(pt1);
    }

    public double rmsAtom() {
        return rmsAtom(spatialSet);
    }

    public double rmsAtom(SpatialSet spatialSet) {
        int i;
        Point3 pt;
        Vector3D pt1 = new Point3(0.0, 0.0, 0.0);
        int nPoints = 0;
        if (entity == null) {
            System.out.println("null entity for " + getFullName());
            return 0.0;
        } else {
            Molecule molecule = entity.molecule;
            if (molecule == null) {
                System.out.println("null molecule for " + getFullName());
                return 0.0;
            }

        }
        int structures[] = entity.molecule.getActiveStructures();
        for (int iStruct : structures) {
            pt = spatialSet.getPoint(iStruct);

            if (pt != null) {
                nPoints++;
                pt1 = pt1.add(pt);
            }
        }

        if (nPoints == 0) {
            return (0.0);
        }
        pt1 = pt1.scalarMultiply(1.0 / nPoints);

        double sumSq = 0.0;
        double delX;
        double delY;
        double delZ;

        for (int iStruct : structures) {
            pt = spatialSet.getPoint(iStruct);
            if (pt != null) {
                sumSq += Vector3D.distanceSq(pt, pt1);
            }
        }

        return (Math.sqrt(sumSq / nPoints));
    }

    public String xyzToString(int iStruct, int iAtom) {
        return xyzToString(spatialSet, iStruct, iAtom);
    }

    public String xyzToString(SpatialSet spatialSet,
            int iStruct, int iAtom) {
        Point3 pt;
        pt = (Point3) spatialSet.getPoint(iStruct);

        if (pt == null) {
            return null;
        }

        String result = "";
        if (entity instanceof Residue) {
            // what if iStruct < 0
            String strStruct = "";
            if (iStruct < 0) {
                strStruct = "\"" + ((Residue) entity).number + "\"";
            } else {
                strStruct = String.valueOf(iStruct);
            }
            result = String.format("%5d %3s %s %5s %6s %-5s %c %8.3f %8.3f %8.3f",
                    iAtom, strStruct, ((Residue) entity).polymer.name, ((Residue) entity).number, ((Residue) entity).name,
                    name, name.substring(0, 1), pt.getX(), pt.getY(), pt.getZ());
        }

        return result;
    }

    public String ppmToString(int iStruct, int iAtom) {
        return ppmToString(spatialSet, iStruct, iAtom);
    }

    public String ppmToString(SpatialSet spatialSet,
            int iStruct, int iAtom) {
        PPMv ppmv = spatialSet.getPPM(iStruct);

        if (ppmv == null) {
            return null;
        }
        StringBuilder sBuilder = new StringBuilder();
        char sepChar = '\t';
        if (entity instanceof Residue) {
            String result = String.format("%5d %5s", iAtom, ((Residue) entity).number, ((Residue) entity).entityID);
            //"%5d %5s %6s %-5s %c %8.3f  .  %2d\n"
            //j, atoms[i].rnum, atoms[i].rname, atoms[i].aname, atoms[i].aname[0], NvGetAtomPPM (istPPM, i), AtmAmbig (i));
            // _Atom_shift_assign_ID
            sBuilder.append(iAtom);
            sBuilder.append(sepChar);

            // _Residue_author_seq_code
            sBuilder.append(((Residue) entity).number);
            sBuilder.append(sepChar);

            // _Residue_seq_code
            sBuilder.append(((Residue) entity).entityID);
            sBuilder.append(sepChar);

            // _Residue_label
            sBuilder.append(((Residue) entity).name);
            sBuilder.append(sepChar);

            // _Atom_name
            sBuilder.append(name);
            sBuilder.append(sepChar);

            // _Atom_type
            sBuilder.append(name.substring(0, 1));
            sBuilder.append(sepChar);

            // _Chem_shift_value
            sBuilder.append(ppmv.getValue());
            sBuilder.append(sepChar);

            // _Chem_shift_value_error
            sBuilder.append(ppmv.getError());
            sBuilder.append(sepChar);

            // _Chem_shift_value_ambiguity_code
            sBuilder.append(ppmv.getAmbigCode());
            sBuilder.append(sepChar);
        }

        return (sBuilder.toString());
    }

    public String xyzToXMLString(int iStruct, int iAtom) {
        return xyzToXMLString(spatialSet, iStruct, iAtom);
    }

    public String xyzToXMLString(SpatialSet spatialSet,
            int iStruct, int iAtom) {
        StringBuffer result = new StringBuffer();
        Point3 pt;
        pt = (Point3) spatialSet.getPoint(iStruct);

        if (pt == null) {
            return null;
        }

        result.append("<coord ");

        if (entity instanceof Residue) {
            //  "%5d %3d %s %5s %6s %-5s %c %8.3f %8.3f %8.3f\n"
            // _Atom_ID
            result.append(" _Atom_ID=\"" + iAtom + "\"");

            if (iStruct < 0) {
                result.append(" _Conformer_number=\""
                        + ((Residue) entity).number + "\"");
            }

            result.append(" _Mol_system_component_name=\""
                    + ((Residue) entity).polymer.name + "\"");
            result.append(" _Residue_seq_code=\"" + ((Residue) entity).number
                    + "\"");
            result.append(" _Residue_label=\"" + ((Residue) entity).name
                    + "\"");
            result.append(" _Atom_name=\"" + name + "\"");
            result.append(" _Atom_type=\"" + name.substring(0, 1) + "\"");
            result.append(" >\n");
            result.append("    <_Atom_coord_x>" + pt.getX() + "</_Atom_coord_x>\n");
            result.append("    <_Atom_coord_y>" + pt.getY() + "</_Atom_coord_y>\n");
            result.append("    <_Atom_coord_z>" + pt.getZ() + "</_Atom_coord_z>\n");
            result.append("</coord>");
        }

        return (result.toString());
    }

    public String ppmToXMLString(int iStruct, int iAtom) {
        return ppmToXMLString(spatialSet, iStruct, iAtom);
    }

    public String ppmToXMLString(SpatialSet spatialSet,
            int iPPM, int iAtom) {
        StringBuffer result = new StringBuffer();

        PPMv ppmv = spatialSet.getPPM(iPPM);

        if (ppmv == null) {
            return null;
        }

        result.append("<Chem_shift ");

        if (entity instanceof Residue) {
            result.append(" _Atom_shift_assign_ID=\"" + iAtom + "\"");
            result.append(" _Residue_seq_code=\"" + ((Residue) entity).number
                    + "\"");
            result.append(" _Residue_label=\"" + ((Residue) entity).name
                    + "\"");
            result.append(" _Atom_name=\"" + name + "\"");
            result.append(" _Atom_type=\"" + name.substring(0, 1) + "\"");
            result.append(" >\n");
            result.append("    <_Chem_shift_value>" + ppmv.getValue()
                    + "</_Chem_shift_value>\n");
            result.append("    <_Chem_shift_value_error>" + ppmv.getError()
                    + "</_Chem_shift_value_error>\n");
            result.append("    <_Chem_shift_ambiguity_code>" + ppmv.getAmbigCode()
                    + "</_Chem_shift_ambiguity_code>\n");
        }

        result.append("</Chem_shift>");

        return (result.toString());
    }

    public Atom getParent() {
        if (parent != null) {
            return parent;
        } else if ((bonds == null) || (bonds.size() == 0)) {
            return null;
        } else {
            for (Object bondObject : bonds) {
                Bond bond = (Bond) bondObject;
                //if ((bond.begin != null) && (bond.begin != this) && (bond.begin.aNum != 1) && (this.aNum == 1)) {
                if ((bond.begin != null) && (bond.begin != this) && (bond.begin.aNum != 1)) {
                    if (bond.begin.iAtom < iAtom) {
                        return bond.begin;
                    }
                    //} else if ((bond.end != null) && (bond.end != this) && (bond.end.aNum != 1) && (this.aNum == 1)) {
                } else if ((bond.end != null) && (bond.end != this) && (bond.end.aNum != 1)) {
                    if (bond.end.iAtom < iAtom) {
                        return bond.end;
                    }
                }
            }
            return null;
        }
    }

    public List<Atom> getChildren() {
        List<Atom> children = new ArrayList<>(4);

        for (int i = 0; i < bonds.size(); i++) {
            Bond bond = bonds.get(i);
            Atom atomB = bond.begin;
            Atom atomE = bond.end;

            if (atomB == this) {
                if (atomE.iAtom > iAtom) {
                    children.add(atomE);
                }
            } else if (atomE == this) {
                if (atomB.iAtom > iAtom) {
                    children.add(atomB);
                }
            }
        }

        return children;
    }

    public Atom getAngleChild() {
        Atom child = null;
        for (int i = 0; i < bonds.size(); i++) {
            Bond bond = bonds.get(i);
            Atom atomB = bond.begin;
            Atom atomE = bond.end;

            if (atomB == this) {
                if (atomE.iAtom > iAtom) {
                    child = atomE;
                    break;
                }
            } else if (atomE == this) {
                if (atomB.iAtom > iAtom) {
                    child = atomB;
                    break;
                }
            }
        }
        return child;
    }

    public static int calcBond(Atom atom1, Atom atom2, int order) {
        return calcBond(atom1, atom2, order, 0);
    }

    public static int calcBond(Atom atom1, Atom atom2, int order, int stereo) {
        int iRes;
        int jRes;
        Point3 pt1;
        Point3 pt2;
        Bond bond;
        double dis;

        if (atom1.entity != atom2.entity) {
            if (!(atom1.entity instanceof Residue)) {
                return 2;
            } else if (!(atom2.entity instanceof Residue)) {
                return 2;
            } else {
                iRes = Integer.parseInt(((Residue) atom1.entity).number);
                jRes = Integer.parseInt(((Residue) atom2.entity).number);

                if (Math.abs(iRes - jRes) > 1) {
                    return 2;
                }
            }
        }

        if (atom1.name.startsWith("H") && atom2.name.startsWith("H")) {
            return 1;
        }

        double dLim = 1.8;

        if (atom1.name.startsWith("H") && atom2.name.startsWith("H")) {
            return 1;
        }

        if (atom1.name.startsWith("S") || atom2.name.startsWith("S")) {
            dLim = 2.0;
        }

        if (atom1.name.startsWith("H") || atom2.name.startsWith("H")) {
            dLim = 1.2;
        }

        pt1 = atom1.getPoint();
        pt2 = atom2.getPoint();

        if ((pt1 != null) && (pt2 != null)) {
            dis = Atom.calcDistance(pt1, pt2);

            if (dis < dLim) {
                bond = new Bond(atom1, atom2);
                bond.order = order;
                bond.stereo = stereo;

                atom1.addBond(bond);
                atom2.addBond(bond);
                atom1.entity.addBond(bond);

                return 0;
            }
        }

        return 1;
    }

    public static int addBond(Atom atom1, Atom atom2, int order, final boolean record) {
        return (addBond(atom1, atom2, order, 0, record));
    }

    public static int addBond(Atom atom1, Atom atom2, int order, int stereo, final boolean record) {
        Bond bond;

        bond = new Bond(atom1, atom2);
        bond.order = order;
        bond.stereo = stereo;
        if (atom1.aNum == 1) {
            atom1.parent = atom2;
        } else if (atom2.aNum == 1) {
            atom2.parent = atom1;
        }

        atom1.addBond(bond);
        atom2.addBond(bond);

        atom1.entity.addBond(bond);
        if (record) {
            Entity entity1 = atom1.getEntity();
            Entity entity2 = atom2.getEntity();
            if (entity1 instanceof Residue) {
                Residue residue1 = (Residue) entity1;
                Polymer polymer = residue1.polymer;
                Residue residue2 = (Residue) entity2;

                AtomSpecifier atomSp1 = new AtomSpecifier(residue1.getNumber(), residue1.getName(), atom1.getName());
                AtomSpecifier atomSp2 = new AtomSpecifier(residue2.getNumber(), residue2.getName(), atom2.getName());
                BondSpecifier bondSp = new BondSpecifier(atomSp1, atomSp2, order);
                polymer.addedBonds.add(bondSp);
            }
        }

        return 0;
    }

    public static String getElementName(int eNum) {
        return AtomProperty.getElementName(eNum);
    }

    public String getElementName() {
        return AtomProperty.getElementName(aNum);
    }

    public byte getElementNumber() {
        return getElementNumber(name);
    }

    public static byte getElementNumber(String elemName) {
        return AtomProperty.getElementNumber(elemName);
    }

    public static void resetLastAtom() {
        lastAtom = 0;
    }

    public int getStereo() {
        return stereo;
    }

    public void setStereo(int stereo) {
        this.stereo = stereo;
    }

    public boolean getRotActive() {
        return rotActive;
    }

    public void setRotActive(boolean rotActive) {
        this.rotActive = rotActive;
    }

    public void setFormalCharge(float charge) {
        this.fcharge = charge;
    }

    public float getFormalCharge() {
        return fcharge;
    }

    public List<Object> getEquivalency() {
        if (!entity.hasEquivalentAtoms()) {
            Molecule.findEquivalentAtoms(entity);
        }
        List<Object> list = new ArrayList<>();
        if ((equivAtoms == null) || (equivAtoms.size() == 0)) {
            return list;
        } else {

            for (int i = 0; i < equivAtoms.size(); i++) {
                AtomEquivalency aEquiv = equivAtoms.get(i);
                List<String> list2 = new ArrayList<>();
                list.add("c");
                list.add(aEquiv.getIndex());
                list.add(aEquiv.getShell());

                for (int j = 0; j < aEquiv.getAtoms().size(); j++) {
                    Atom atom = (Atom) aEquiv.getAtoms().get(j);

                    if (!atom.getName().equals(getName())) {
                        list2.add(atom.getName());
                    }
                }

                list.add(list2);
            }

            return list;
        }
    }

    public boolean isFirstInMethyl() {
        boolean result = false;
        if (isMethyl()) {
            AtomEquivalency aEquiv = equivAtoms.get(0);
            ArrayList<Atom> atoms = aEquiv.getAtoms();
            Atom firstAtom = atoms.get(0);
            for (Atom atom : atoms) {
                if (atom.name.compareToIgnoreCase(firstAtom.name) < 0) {
                    firstAtom = atom;
                }
            }
            result = this == firstAtom;
        }
        return result;
    }

    public boolean isMethyl() {
        if (!entity.hasEquivalentAtoms()) {
            Molecule.findEquivalentAtoms(entity);
        }

        if ((aNum != 1) || (equivAtoms == null) || (equivAtoms.size() == 0)) {
            return false;
        } else {
            AtomEquivalency aEquiv = equivAtoms.get(0);
            if (aEquiv.getAtoms().size() == 3) {
                return aEquiv.shareParent();
            } else {
                return false;
            }
        }
    }

    public Point3 getMethylCenter(int structNum) {
        Atom parent = getParent();
        List<Atom> children = parent.getChildren();
        Vector3D pt1 = ((Atom) children.get(0)).getPoint(structNum);
        pt1 = pt1.add(((Atom) children.get(1)).getPoint(structNum));
        pt1 = pt1.add(((Atom) children.get(2)).getPoint(structNum));
        pt1 = pt1.scalarMultiply(1.0 / 3.0);

        return new Point3(pt1);
    }

    public Atom[] getPartners(int targetANum, int shells) {
        Atom[] result = new Atom[0];
        if (!entity.hasEquivalentAtoms()) {
            Molecule.findEquivalentAtoms(entity);
        }

        if ((aNum == targetANum) && (equivAtoms != null) && (equivAtoms.size() > 0)) {
            int nAtoms = 0;
            for (int i = 0; (i < equivAtoms.size()) && (i < shells); i++) {
                AtomEquivalency aEquiv = equivAtoms.get(i);
                nAtoms += aEquiv.getAtoms().size();
            }
            result = new Atom[nAtoms - 1];
            int j = 0;
            for (int i = 0; (i < equivAtoms.size()) && (i < shells); i++) {
                AtomEquivalency aEquiv = equivAtoms.get(i);
                for (Atom eAtom : aEquiv.getAtoms()) {
                    if (!eAtom.getName().equals(getName())) {
                        result[j++] = eAtom;
                    }
                }
            }
        }
        return result;
    }

    public boolean isMethylene() {
        if (!entity.hasEquivalentAtoms()) {
            Molecule.findEquivalentAtoms(entity);
        }

        if ((aNum != 1) || (equivAtoms == null) || (equivAtoms.size() == 0)) {
            return false;
        } else {
            AtomEquivalency aEquiv = equivAtoms.get(0);

            if (aEquiv.getAtoms().size() == 2) {
                if (aEquiv.getAtoms().get(0).parent == aEquiv.getAtoms().get(1).parent) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }
//###################################################################
//#       Chemical Shift Ambiguity Index Value Definitions          #
//#                                                                 #
//#   Index Value            Definition                             #
//#                                                                 #
//#      1             Unique (geminal atoms and geminal methyl     #
//#                         groups with identical chemical shifts   #
//#                         are assumed to be assigned to           #
//#                         stereospecific atoms)                   #
//#      2             Ambiguity of geminal atoms or geminal methyl #
//#                         proton groups                           #
//#      3             Aromatic atoms on opposite sides of          #
//#                         symmetrical rings (e.g. Tyr HE1 and HE2 #
//#                         protons)                                #
//#      4             Intraresidue ambiguities (e.g. Lys HG and    #
//#                         HD protons or Trp HZ2 and HZ3 protons)  #
//#      5             Interresidue ambiguities (Lys 12 vs. Lys 27) #
//#      9             Ambiguous, specific ambiguity not defined    #
//#                                                                 #
//###################################################################

    public int getBMRBAmbiguity() {
        if (!entity.hasEquivalentAtoms()) {
            Molecule.findEquivalentAtoms(entity);
        }

        int aType = 0;

        if ((equivAtoms == null) || (equivAtoms.size() == 0)) {
            aType = 1;
        } else if (equivAtoms.size() == 1) {
            AtomEquivalency aEquiv = equivAtoms.get(0);

            if (aEquiv.getAtoms().size() == 3) {
                aType = 1;
            } else if (aEquiv.getShell() == 2) {
                aType = 2;
            } else {
                aType = 3;
            }
        } else {
            AtomEquivalency aEquiv = equivAtoms.get(0);

            if (aEquiv.getAtoms().size() == 3) {
                aType = 2;
            } else {
                aType = 9;
            }
        }

        return aType;
    }

    public String getEquivIndices() {
        if (!entity.hasEquivalentAtoms()) {
            Molecule.findEquivalentAtoms(entity);
        }

        if ((equivAtoms == null) || (equivAtoms.size() == 0)) {
            return "";
        } else {
            char[] resultCh = new char[(equivAtoms.size() * 2) - 1];
            int j = 0;

            for (int i = 0; i < equivAtoms.size(); i++) {
                AtomEquivalency aEquiv = equivAtoms.get(i);

                if (i > 0) {
                    resultCh[j] = ' ';
                    j++;
                }

                resultCh[j++] = (char) (('a' + aEquiv.getIndex()) - 1);
            }

            return new String(resultCh);
        }
    }

    public String getPseudoName(int level) {
        String pseudoName = name;
        if (isMethyl()) {
            if (entity instanceof Residue) {
                Residue residue = (Residue) entity;
                if (residue.getName().equals("LYS")) {
                    pseudoName = 'Q' + name.substring(1, name.length() - 1);
                } else if (residue.getName().equals("ILE") || residue.getName().equals("THR")) {
                    pseudoName = 'M' + name.substring(1, name.length() - 2);
                } else if ((level == 1) || (name.length() < 4)) {
                    pseudoName = 'M' + name.substring(1, name.length() - 1);
                } else {
                    pseudoName = 'Q' + name.substring(1, name.length() - 2);
                }
            }
        } else {
            if (!entity.hasEquivalentAtoms()) {
                Molecule.findEquivalentAtoms(entity);
            }
            if (equivAtoms != null) {
                AtomEquivalency aEquiv = equivAtoms.get(0);
                if (aEquiv.getAtoms().size() == 2) {
                    pseudoName = 'Q' + name.substring(1, name.length() - 1);
                }
            }

        }
        return pseudoName;

    }

    // fixme this could return other atoms that end in c
    public boolean isCoarse() {
        int nameLen = name.length();
        return name.charAt(nameLen - 1) == 'c';
    }
}
