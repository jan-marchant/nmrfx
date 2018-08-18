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
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.MolFilter;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.SpatialSet;
import java.util.HashMap;
import java.util.List;

/**
 * This class determines if the angle boundary is valid - angle boundary for
 * each bond. The angle must be between -180 degrees and 360 degrees
 */
public class AngleBoundary {

    /**
     * Upper Angle Bound
     */
    final double upper;
    /**
     * Lower Angle Bound
     */
    final double lower;
    
    
    private Atom[] atoms = null;
    /**
     * Scale
     */
    final double scale;
    /**
     * Index to list of angles
     */
    private int index = -1;
    final static double toRad = Math.PI / 180.0;

    public AngleBoundary(final String atomName, double lower, double upper, final double scale) throws InvalidMoleculeException {
        /*Changed from Original*/
        if (((lower < -180.0) && (upper < 0.0)) || (upper > 360.0) || (upper < lower)) {
            throw new IllegalArgumentException("Invalid angle bounds: " + lower + " " + upper);
        }
        if ((lower > 180) && (upper > 180)) {
            lower = lower - 360.0;
            upper = upper - 360.0;
        }

        this.lower = lower * toRad;
        this.upper = upper * toRad;
        this.scale = scale;
        MolFilter molFilter = new MolFilter(atomName);
        List<SpatialSet> spatialSets = Molecule.matchAtoms(molFilter);
        if (spatialSets.size() == 0) {
            throw new IllegalArgumentException("Invalid atom " + atomName);
        }
        SpatialSet spatialSet = spatialSets.get(0);
        atoms = new Atom[1];
        atoms[0] = spatialSet.atom;
    }

    public AngleBoundary(final List<String> atomNames, double lower, double upper, final double scale) throws InvalidMoleculeException {
        /*Changed from Original*/
        if (((lower < -180.0) && (upper < 0.0)) || (upper > 360.0) || (upper < lower)) {
            throw new IllegalArgumentException("Invalid angle bounds: " + lower + " " + upper);
        }
        if ((lower > 180) && (upper > 180)) {
            lower = lower - 360.0;
            upper = upper - 360.0;
        }

        this.lower = lower * toRad;
        this.upper = upper * toRad;
        this.scale = scale;
        atoms = new Atom[atomNames.size()];
        int i = 0;
        for (String atomName : atomNames) {
            MolFilter molFilter = new MolFilter(atomName);
            List<SpatialSet> spatialSets = Molecule.matchAtoms(molFilter);
            if (spatialSets.size() == 0) {
                throw new IllegalArgumentException("Invalid atom " + atomName);
            }
            SpatialSet spatialSet = spatialSets.get(0);
            atoms[i++] = spatialSet.atom;
        }
    }

    public void setIndex(final int index) {
        this.index = index;
    }
    
    public int getIndex() {
        return index;
    }

    public Atom getAtom() {
        return atoms[atoms.length - 1];
    }

    public Atom[] getAtoms() {
        return atoms;
    }
}
